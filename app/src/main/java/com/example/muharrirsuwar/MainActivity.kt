package com.example.muharrirsuwar

import android.content.ContentValues
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private lateinit var preview: ImageView
    private lateinit var emptyState: TextView
    private lateinit var brightnessBar: SeekBar
    private lateinit var contrastBar: SeekBar
    private lateinit var saturationBar: SeekBar
    private lateinit var saturationBar: SeekBar
    private var loadedBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private var editedBitmap: Bitmap? = null
    private var filter = Filter.NORMAL
    private var overlayText = ""
    private var textColor = Color.WHITE
    private var textSizePx = 54f
    private var brightness = 0
    private var contrast = 100
    private var saturation = 100

    private enum class Filter { NORMAL, GRAYSCALE, SEPIA, WARM, COOL }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        buildInterface()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 249))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val header = TextView(this).apply {
            text = "محرر الصور العربي"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(16)
            setBackgroundColor(Color.rgb(32, 61, 80))
        }
        root.addView(header, LinearLayout.LayoutParams(-1, dp(62)))

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(225, 231, 235))
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "معاينة الصورة"
        }
        emptyState = TextView(this).apply {
            text = "اختر صورة من هاتفك لبدء التعديل"
            textSize = 16f
            setTextColor(Color.rgb(70, 85, 95))
            gravity = Gravity.CENTER
        }
        previewFrame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        previewFrame.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        content.addView(previewFrame, LinearLayout.LayoutParams(-1, dp(300)).apply {
            bottomMargin = dp(12)
        })

        val pickButton = makeButton("اختيار صورة")
        pickButton.setOnClickListener { pickImage.launch("image/*") }
        content.addView(pickButton, fullWidth())

        sectionTitle(content, "الفلاتر")
        val filtersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(
            "طبيعي" to Filter.NORMAL,
            "أبيض وأسود" to Filter.GRAYSCALE,
            "بني قديم" to Filter.SEPIA,
            "دافئ" to Filter.WARM,
            "بارد" to Filter.COOL
        ).forEach { (label, value) ->
            val button = makeSmallButton(label)
            button.setOnClickListener { filter = value; render() }
            filtersRow.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }
        content.addView(filtersRow, fullWidth())

        sectionTitle(content, "ضبط الصورة")
        addSeekControl(content, "السطوع", -100, 100, 0) { brightness = it; render() }
            .also { brightnessBar = it }
        addSeekControl(content, "التباين", 50, 200, 100) { contrast = it; render() }
            .also { contrastBar = it }
        addSeekControl(content, "التشبع", 0, 200, 100) { saturation = it; render() }
            .also { saturationBar = it }

        sectionTitle(content, "إضافة نص عربي")
        val textInput = EditText(this).apply {
            hint = "اكتب النص الذي تريد إضافته"
            textSize = 16f
            setSingleLine(false)
            minLines = 1
            maxLines = 3
            gravity = Gravity.RIGHT
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12))
            background = roundedBackground(Color.WHITE, Color.rgb(210, 218, 224))
        }
        content.addView(textInput, fullWidth().apply { bottomMargin = dp(8) })

        val textOptions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val whiteButton = makeSmallButton("نص أبيض")
        whiteButton.setOnClickListener { textColor = Color.WHITE; overlayText = textInput.text.toString(); render() }
        val yellowButton = makeSmallButton("نص أصفر")
        yellowButton.setOnClickListener { textColor = Color.YELLOW; overlayText = textInput.text.toString(); render() }
        val blackButton = makeSmallButton("نص أسود")
        blackButton.setOnClickListener { textColor = Color.BLACK; overlayText = textInput.text.toString(); render() }
        textOptions.addView(whiteButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        textOptions.addView(yellowButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        textOptions.addView(blackButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        content.addView(textOptions, fullWidth())

        val applyText = makeButton("تطبيق النص على المعاينة")
        applyText.setOnClickListener {
            overlayText = textInput.text.toString()
            render()
        }
        content.addView(applyText, fullWidth().apply { topMargin = dp(8) })

        sectionTitle(content, "قص الصورة وتدويرها")
        val cropRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val squareCrop = makeSmallButton("قص مربع")
        squareCrop.setOnClickListener { cropSquare() }
        val rotateButton = makeSmallButton("تدوير 90°")
        rotateButton.setOnClickListener { rotateImage() }
        cropRow.addView(squareCrop, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(4) })
        cropRow.addView(rotateButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(4) })
        content.addView(cropRow, fullWidth())

        sectionTitle(content, "الحفظ")
        val saveButton = makeButton("حفظ الصورة في المعرض")
        saveButton.setOnClickListener { saveImage() }
        content.addView(saveButton, fullWidth())
        val resetButton = makeButton("إعادة ضبط التعديلات")
        resetButton.setOnClickListener { resetEdits() }
        content.addView(resetButton, fullWidth().apply { topMargin = dp(8); bottomMargin = dp(12) })

        setContentView(root)
        setEnabledEditing(false)
    }

    private fun loadImage(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            loadedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            originalBitmap = loadedBitmap?.copy(Bitmap.Config.ARGB_8888, false)
            resetEdits()
            setEnabledEditing(true)
        } catch (e: Exception) {
            toast("تعذر فتح الصورة")
        }
    }

    private fun render() {
        val source = originalBitmap ?: return
        try {
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val cm = colorMatrixForFilter()
            val adjust = ColorMatrix()
            adjust.setSaturation(saturation / 100f)
            val scale = contrast / 100f
            val translate = 128f * (1f - scale) + brightness * 2.55f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            adjust.postConcat(contrastMatrix)
            cm.postConcat(adjust)
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)

            if (overlayText.isNotBlank()) {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textColor
                    textSize = textSizePx.coerceAtMost(source.width * 0.12f)
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                    setShadowLayer(4f, 2f, 2f, Color.BLACK)
                }
                val margin = source.width * 0.05f
                val lines = overlayText.split("\n")
                var y = source.height - margin - (lines.size - 1) * (textPaint.textSize * 1.25f)
                lines.forEach { line ->
                    canvas.drawText(line, source.width - margin, y, textPaint)
                    y += textPaint.textSize * 1.25f
                }
            }
            editedBitmap = output
            preview.setImageBitmap(output)
            emptyState.visibility = View.GONE
        } catch (e: Exception) {
            toast("حدث خطأ أثناء تعديل الصورة")
        }
    }

    private fun colorMatrixForFilter(): ColorMatrix = when (filter) {
        Filter.NORMAL -> ColorMatrix()
        Filter.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
        Filter.SEPIA -> ColorMatrix(floatArrayOf(
            .393f, .769f, .189f, 0f, 0f,
            .349f, .686f, .168f, 0f, 0f,
            .272f, .534f, .131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        Filter.WARM -> ColorMatrix(floatArrayOf(
            1.08f, 0f, 0f, 0f, 8f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, .90f, 0f, -3f,
            0f, 0f, 0f, 1f, 0f
        ))
        Filter.COOL -> ColorMatrix(floatArrayOf(
            .92f, 0f, 0f, 0f, -2f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1.08f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun cropSquare() {
        val src = originalBitmap ?: return toast("اختر صورة أولاً")
        val side = min(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        originalBitmap = Bitmap.createBitmap(src, x, y, side, side)
        render()
    }

    private fun rotateImage() {
        val src = originalBitmap ?: return toast("اختر صورة أولاً")
        val matrix = Matrix().apply { postRotate(90f) }
        originalBitmap = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        render()
    }

    private fun resetEdits() {
        originalBitmap = loadedBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        filter = Filter.NORMAL
        overlayText = ""
        brightness = 0
        contrast = 100
        saturation = 100
        if (::brightnessBar.isInitialized) brightnessBar.progress = 100
        if (::contrastBar.isInitialized) contrastBar.progress = 50
        if (::saturationBar.isInitialized) saturationBar.progress = 100
        render()
    }

    private fun saveImage() {
        val bitmap = editedBitmap ?: return toast("اختر صورة أولاً")
        val filename = "Muharrir_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/محرر الصور العربي")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) return toast("تعذر إنشاء ملف الصورة")
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("JPEG compression failed")
                }
            } ?: throw IOException("Unable to open output stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            toast("تم حفظ الصورة في مجلد الصور")
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            toast("فشل حفظ الصورة")
        }
    }

    private fun addSeekControl(
        parent: LinearLayout,
        label: String,
        minValue: Int,
        maxValue: Int,
        initial: Int,
        changed: (Int) -> Unit
    ): SeekBar {
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val labelView = TextView(this).apply {
            text = "$label: $initial"
            textSize = 14f
            setTextColor(Color.rgb(40, 55, 65))
        }
        val bar = SeekBar(this).apply {
            max = maxValue - minValue
            progress = initial - minValue
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + minValue
                labelView.text = "$label: $value"
                if (fromUser) changed(value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        holder.addView(labelView)
        holder.addView(bar)
        parent.addView(holder, fullWidth())
        return bar
    }

    private fun sectionTitle(parent: LinearLayout, title: String) {
        val view = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.rgb(32, 61, 80))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        }
        parent.addView(view, fullWidth())
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        background = roundedBackground(Color.rgb(49, 92, 116), Color.rgb(49, 92, 116))
    }

    private fun makeSmallButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setPadding(dp(2))
        minWidth = 0
        setTextColor(Color.rgb(32, 61, 80))
        background = roundedBackground(Color.WHITE, Color.rgb(205, 215, 222))
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun fullWidth() = LinearLayout.LayoutParams(-1, -2)

    private fun setEnabledEditing(enabled: Boolean) {
        // All editing controls are available; actions show a friendly message if no image is selected.
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
