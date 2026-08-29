package io.github.oooo00o0o.huihutong

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.TouchDelegate
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val dataLoader = GateDataLoader(HuihutongApi())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coreExecutor = Executors.newSingleThreadExecutor()
    private val supplementalExecutor = Executors.newSingleThreadExecutor()
    private var inFlight = false
    private var destroyed = false
    private var coreGeneration = 0L
    private var supplementalGeneration = 0L
    private val dateFormat = SimpleDateFormat("yyyy\u5e74MM\u6708dd\u65e5 HH:mm:ss", Locale.CHINA)
    private val queryTimeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

    private lateinit var apartmentText: TextView
    private lateinit var nameText: TextView
    private lateinit var companyText: TextView
    private lateinit var verifiedText: TextView
    private lateinit var qrImage: ImageView
    private lateinit var timeText: TextView
    private lateinit var permissionText: TextView
    private lateinit var warningText: TextView
    private lateinit var balanceText: TextView
    private lateinit var hintText: TextView
    private lateinit var refreshButton: Button

    private var settings: AppSettings? = null
    private var currentInfo: CodeInfo? = null
    private var currentSession: LoginSession? = null
    private var previousBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    private val clockRunnable = object : Runnable {
        override fun run() {
            if (::timeText.isInitialized && currentInfo != null) {
                timeText.text = dateFormat.format(Date())
            }
            mainHandler.postDelayed(this, CLOCK_TICK_INTERVAL_MS)
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!destroyed && currentInfo != null) {
                refresh(RefreshMode.QR_ONLY)
            }
            if (!destroyed) mainHandler.postDelayed(this, QR_REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = PRIMARY_BLUE
        window.navigationBarColor = Color.WHITE
        buildUi()
        settings = loadSettings()
        if (settings == null) {
            renderNoCredentials()
            mainHandler.post { showSettingsDialog() }
        } else {
            refresh(RefreshMode.FULL)
        }
    }

    override fun onResume() {
        super.onResume()
        applyHighBrightness()
        startClockLoop()
        if (settings != null) startRefreshLoop()
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.removeCallbacks(clockRunnable)
        restoreBrightness()
    }

    override fun onDestroy() {
        destroyed = true
        coreGeneration += 1
        supplementalGeneration += 1
        mainHandler.removeCallbacksAndMessages(null)
        coreExecutor.shutdownNow()
        supplementalExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(PAGE_BACKGROUND) }
        root.addView(View(this).apply {
            background = bottomRounded(HERO_BLUE, 40.dp())
        }, frame(-1, 350.dp(), Gravity.TOP))
        root.addView(View(this).apply { setBackgroundColor(PRIMARY_BLUE) }, frame(-1, 100.dp(), Gravity.TOP))


        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(), 136.dp(), 24.dp(), 72.dp())
        }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, frame(-1, -1))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 24.dp())
            setPadding(24.dp(), 30.dp(), 24.dp(), 24.dp())
            minimumHeight = 590.dp()
        }
        content.addView(card, linear(-1, -2))

        card.addView(profileBlock(), linear(-1, 140.dp()))
        card.addView(qrBlock(), linear(276.dp(), 276.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 6.dp()
        })

        timeText = label("", 19f, GRAY_TEXT, Typeface.DEFAULT, Gravity.CENTER)
        card.addView(timeText, linear(-1, -2).apply { topMargin = 30.dp() })

        permissionText = label("", 20f, DEEP_BLUE, Typeface.DEFAULT_BOLD, Gravity.CENTER)
        card.addView(permissionText, linear(-1, -2).apply { topMargin = 16.dp() })

        warningText = label("", 16.5f, RED, Typeface.DEFAULT_BOLD, Gravity.START).apply {
            visibility = View.GONE
            setLineSpacing(2.dp().toFloat(), 1.08f)
        }
        card.addView(warningText, linear(-1, -2).apply { topMargin = 12.dp() })

        balanceText = label("", 15f, GRAY_TEXT, Typeface.DEFAULT, Gravity.CENTER).apply {
            visibility = View.GONE
        }
        card.addView(balanceText, linear(-1, -2).apply { topMargin = 8.dp() })

        hintText = label("", 14f, GRAY_TEXT, Typeface.DEFAULT, Gravity.CENTER)
        card.addView(hintText, linear(-1, -2).apply { topMargin = 18.dp() })

        refreshButton = button("\u624b\u52a8\u5237\u65b0", 13f, Color.WHITE, PRIMARY_BLUE).apply {
            setOnClickListener { refresh(RefreshMode.FULL) }
        }
        card.addView(refreshButton, linear(112.dp(), 34.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 8.dp()
        })

        root.addView(
            label("\u6211\u7684\u4e8c\u7ef4\u7801", 20f, Color.WHITE, Typeface.DEFAULT_BOLD, Gravity.CENTER),
            frame(-1, 58.dp(), Gravity.TOP).apply { topMargin = 48.dp() }
        )

        val settings = capsuleButton().apply {
            setOnClickListener { showSettingsDialog() }
        }
        root.addView(settings, frame(96.dp(), 34.dp(), Gravity.TOP or Gravity.END).apply {
            topMargin = 52.dp()
            rightMargin = 14.dp()
        })
        settings.post {
            val touchBounds = Rect().also(settings::getHitRect)
            val verticalExpansion = ((48.dp() - settings.height).coerceAtLeast(0)) / 2
            touchBounds.inset(0, -verticalExpansion)
            root.touchDelegate = TouchDelegate(touchBounds, settings)
        }


        root.addView(bottomNav(), frame(-1, 58.dp(), Gravity.BOTTOM))
        setContentView(root)
    }

    private fun profileBlock(): View {
        val row = FrameLayout(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(column, frame(-1, -1).apply { rightMargin = 82.dp() })

        apartmentText = label("", 19f, DARK_TEXT, Typeface.DEFAULT_BOLD, Gravity.START)
        nameText = label("", 18.5f, DARK_TEXT, Typeface.DEFAULT, Gravity.START)
        companyText = label("", 18.5f, DARK_TEXT, Typeface.DEFAULT, Gravity.START)
        column.addView(apartmentText, linear(-1, -2))
        column.addView(nameText, linear(-1, -2).apply { topMargin = 10.dp() })
        column.addView(companyText, linear(-1, -2).apply { topMargin = 10.dp() })

        verifiedText = label("\u5df2\u9a8c\u8bc1", 19.5f, GREEN, Typeface.DEFAULT_BOLD, Gravity.CENTER)
        row.addView(verifiedText, frame(82.dp(), 60.dp(), Gravity.END or Gravity.CENTER_VERTICAL))
        return row
    }

    private fun qrBlock(): View {
        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(4.dp(), WARNING_YELLOW, 10.dp().toFloat(), 7.dp().toFloat())
                cornerRadius = 8.dp().toFloat()
            }
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        }
        qrImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }
        frame.addView(qrImage, FrameLayout.LayoutParams(-1, -1))
        return frame
    }

    private fun bottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
        }
        nav.addView(navItem("\u9996\u9875", false, R.drawable.ic_nav_home), linear(0, -1, 1f))
        nav.addView(navItem("\u4e8c\u7ef4\u7801", true, R.drawable.ic_nav_qr), linear(0, -1, 1f))
        nav.addView(navItem("\u6211\u7684", false, R.drawable.ic_nav_user), linear(0, -1, 1f))
        return nav
    }

    private fun navItem(text: String, selected: Boolean, iconRes: Int): TextView {
        val color = if (selected) PRIMARY_BLUE else NAV_GRAY
        return label(text, 12f, color, if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT, Gravity.CENTER).apply {
            setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0)
            compoundDrawablePadding = 0
            includeFontPadding = false
            compoundDrawableTintList = ColorStateList.valueOf(color)
        }
    }

    private fun renderNoCredentials() {
        apartmentText.text = "\u8bf7\u5bfc\u5165\u6167\u6e56\u901a\u53c2\u6570"
        nameText.text = "Open ID \u5fc5\u586b\uff0cUnion ID \u53ef\u9009"
        companyText.text = "\u53c2\u6570\u4ec5\u4fdd\u5b58\u5728\u672c\u673a"
        verifiedText.text = "\u672a\u767b\u5f55"
        verifiedText.setTextColor(GRAY_TEXT)
        qrImage.setImageBitmap(null)
        timeText.text = dateFormat.format(Date())
        permissionText.text = "* \u7b49\u5f85\u5bfc\u5165"
        permissionText.setTextColor(GRAY_TEXT)
        warningText.visibility = View.GONE
        balanceText.visibility = View.GONE
        hintText.text = "\u70b9\u53f3\u4e0a\u89d2\u201c\u8bbe\u7f6e\u201d\uff0c\u586b\u5199 Open ID \u540e\u4fdd\u5b58\u3002"
        refreshButton.visibility = View.VISIBLE
        refreshButton.isEnabled = false
    }

    private fun renderCoreSnapshot(snapshot: CoreSnapshot) {
        currentInfo = snapshot.info
        currentSession = snapshot.session
        apartmentText.text = snapshot.info.apartment.ifBlank { "\u6167\u6e56\u901a\u95e8\u7981" }.withoutTrailingComma()
        nameText.text = snapshot.info.name.ifBlank { "\u5df2\u767b\u5f55\u7528\u6237" }
        companyText.text = snapshot.info.companyName.ifBlank { "\u897f\u4ea4\u5229\u7269\u6d66\u5927\u5b66" }
        verifiedText.text = "\u5df2\u9a8c\u8bc1"
        verifiedText.setTextColor(GREEN)
        qrImage.setImageBitmap(snapshot.qrBitmap)
        timeText.text = dateFormat.format(Date(snapshot.fetchedAtMillis))
        permissionText.text = snapshot.info.permissionText.asPermissionLine()
        permissionText.setTextColor(DEEP_BLUE)
        refreshButton.visibility = View.VISIBLE
        refreshButton.isEnabled = true
        hintText.visibility = View.GONE
    }

    private fun renderWarning(threshold: String?) {
        if (threshold.isNullOrBlank()) {
            warningText.visibility = View.GONE
        } else {
            warningText.visibility = View.VISIBLE
            warningText.text = "\u60a8\u6240\u767b\u8bb0\u7684\u623f\u95f4\u4f59\u989d\u5df2\u4f4e\u4e8e${threshold}\u5143\uff0c\u8bf7\u53ca\u65f6\u7f34\u8d39\u3002"
        }
    }

    private fun renderRoomBalance(snapshot: RoomBalanceSnapshot) {
        balanceText.text = snapshot.displayText(
            queriedAt = queryTimeFormat.format(Date(snapshot.queriedAtMillis))
        )
        balanceText.visibility = View.VISIBLE
    }

    private fun loadSupplemental(
        session: LoginSession,
        room: RoomReference?,
        generation: Long
    ) {
        if (destroyed) return
        supplementalExecutor.execute {
            dataLoader.loadSupplemental(session, room).forEach { update ->
                mainHandler.post {
                    if (!destroyed &&
                        generation == supplementalGeneration &&
                        session == currentSession
                    ) {
                        when (update) {
                            is SupplementalUpdate.PowerWarningLoaded -> renderWarning(update.threshold)
                            is SupplementalUpdate.RoomBalanceLoaded -> renderRoomBalance(update.snapshot)
                            SupplementalUpdate.PowerWarningFailed,
                            SupplementalUpdate.RoomBalanceFailed -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun renderError(error: Throwable) {
        val message = error.message ?: error.javaClass.simpleName
        hintText.visibility = View.VISIBLE
        hintText.text = "\u5237\u65b0\u5931\u8d25\uff1a$message"
        permissionText.text = "* \u5237\u65b0\u5931\u8d25"
        permissionText.setTextColor(RED)
        refreshButton.isEnabled = true
    }

    private fun startClockLoop() {
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.post(clockRunnable)
    }

    private fun startRefreshLoop() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, QR_REFRESH_INTERVAL_MS)
    }

    private fun refresh(mode: RefreshMode) {
        if (destroyed) return
        val infoAtRequest = currentInfo
        if (mode == RefreshMode.QR_ONLY && infoAtRequest == null) return
        val requestSettings = settings
        if (requestSettings == null) {
            renderNoCredentials()
            return
        }
        if (inFlight) return
        inFlight = true
        val requestGeneration = coreGeneration
        val needsFullLoad = mode == RefreshMode.FULL
        val nextSupplementalGeneration = if (needsFullLoad) {
            supplementalGeneration += 1
            supplementalGeneration
        } else null
        refreshButton.visibility = View.VISIBLE
        refreshButton.isEnabled = false
        hintText.text = if (needsFullLoad) {
            "\u6b63\u5728\u767b\u5f55\u5e76\u52a0\u8f7d\u4e8c\u7ef4\u7801..."
        } else {
            "\u6b63\u5728\u5237\u65b0\u4e8c\u7ef4\u7801..."
        }

        coreExecutor.execute {
            try {
                val core = dataLoader.loadCore(
                    requestSettings.credentials,
                    needsFullLoad,
                    infoAtRequest
                )
                val snapshot = CoreSnapshot(
                    info = core.info,
                    qrBitmap = QrCodeBitmap.create(core.qrPayload, 760),
                    fetchedAtMillis = System.currentTimeMillis(),
                    session = core.session
                )
                mainHandler.post {
                    inFlight = false
                    if (!isCurrentCoreRequest(requestGeneration, requestSettings)) {
                        if (!destroyed && settings != null) refresh(RefreshMode.FULL)
                        return@post
                    }
                    renderCoreSnapshot(snapshot)
                    if (nextSupplementalGeneration != null) {
                        loadSupplemental(
                            session = snapshot.session,
                            room = requestSettings.roomReference,
                            generation = nextSupplementalGeneration
                        )
                    }
                }
            } catch (error: Throwable) {
                mainHandler.post {
                    inFlight = false
                    if (!isCurrentCoreRequest(requestGeneration, requestSettings)) {
                        if (!destroyed && settings != null) refresh(RefreshMode.FULL)
                        return@post
                    }
                    renderError(error)
                }
            }
        }
    }

    private fun isCurrentCoreRequest(generation: Long, requestSettings: AppSettings): Boolean {
        return !destroyed &&
            generation == coreGeneration &&
            requestSettings == settings
    }

    private fun showSettingsDialog() {
        val current = settings
        val openIdInput = settingsInput(
            hint = "Open ID\uff08\u5fc5\u586b\uff09",
            value = current?.credentials?.openId.orEmpty()
        )
        val unionIdInput = settingsInput(
            hint = "Union ID\uff08\u53ef\u9009\uff09",
            value = current?.credentials?.unionId.orEmpty()
        )
        val apartmentIdInput = settingsInput(
            hint = "Apartment ID",
            value = current?.apartmentId.orEmpty(),
            numeric = true
        )
        val roomIdInput = settingsInput(
            hint = "Room ID",
            value = current?.roomId.orEmpty(),
            numeric = true
        )

        val roomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(apartmentIdInput, linear(0, 48.dp(), 1f).apply { rightMargin = 6.dp() })
            addView(roomIdInput, linear(0, 48.dp(), 1f).apply { leftMargin = 6.dp() })
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 0, 24.dp(), 0)
            addView(openIdInput, linear(-1, 48.dp()))
            addView(unionIdInput, linear(-1, 48.dp()).apply { topMargin = 10.dp() })
            addView(
                label(
                    "\u623f\u95f4\u4f59\u989d\uff08\u586b\u5199\u4e24\u4e2a\u7f16\u53f7\u540e\u542f\u7528\uff09",
                    13f,
                    GRAY_TEXT,
                    Typeface.DEFAULT,
                    Gravity.START
                ),
                linear(-1, -2).apply { topMargin = 18.dp(); bottomMargin = 6.dp() }
            )
            addView(roomRow, linear(-1, 48.dp()))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("\u8bbe\u7f6e\u6167\u6e56\u901a\u53c2\u6570")
            .setMessage("Open ID \u5fc5\u586b\uff0cUnion ID \u53ef\u9009\u3002Apartment ID \u548c Room ID \u540c\u65f6\u586b\u5199\u65f6\u542f\u7528\u623f\u95f4\u4f59\u989d\u3002")
            .setView(form)
            .setNegativeButton("\u53d6\u6d88", null)
            .setPositiveButton("\u4fdd\u5b58", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = try {
                    AppSettings.fromInput(
                        openId = openIdInput.text.toString(),
                        unionId = unionIdInput.text.toString(),
                        apartmentId = apartmentIdInput.text.toString(),
                        roomId = roomIdInput.text.toString()
                    )
                } catch (error: SettingsValidationException) {
                    val invalidInput = when (error.field) {
                        SettingsField.OPEN_ID -> openIdInput
                        SettingsField.APARTMENT_ID -> apartmentIdInput
                        SettingsField.ROOM_ID -> roomIdInput
                    }
                    invalidInput.error = error.message
                    invalidInput.requestFocus()
                    return@setOnClickListener
                }

                dialog.dismiss()
                if (parsed == settings) return@setOnClickListener

                saveSettings(parsed)
                coreGeneration += 1
                supplementalGeneration += 1
                settings = parsed
                warningText.visibility = View.GONE
                balanceText.visibility = View.GONE
                currentInfo = null
                currentSession = null
                refresh(RefreshMode.FULL)
                startRefreshLoop()
                Toast.makeText(this, "\u53c2\u6570\u5df2\u4fdd\u5b58", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun settingsInput(hint: String, value: String, numeric: Boolean = false): EditText {
        return EditText(this).apply {
            isSingleLine = true
            this.hint = hint
            inputType = if (numeric) {
                InputType.TYPE_CLASS_NUMBER
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            setText(value)
            setPadding(12.dp(), 0, 12.dp(), 0)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(1.dp(), INPUT_BORDER)
                cornerRadius = 8.dp().toFloat()
            }
        }
    }

    private fun loadSettings(): AppSettings? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val openId = prefs.getString(KEY_OPEN_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val unionId = prefs.getString(KEY_UNION_ID, "").orEmpty()
        val apartmentId = prefs.getString(KEY_APARTMENT_ID, "").orEmpty()
        val roomId = prefs.getString(KEY_ROOM_ID, "").orEmpty()
        return try {
            AppSettings.fromInput(openId, unionId, apartmentId, roomId)
        } catch (_: SettingsValidationException) {
            AppSettings.fromInput(openId, unionId, "", "")
        }
    }

    private fun saveSettings(value: AppSettings) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPEN_ID, value.credentials.openId)
            .putString(KEY_UNION_ID, value.credentials.unionId ?: "")
            .putString(KEY_APARTMENT_ID, value.apartmentId ?: "")
            .putString(KEY_ROOM_ID, value.roomId ?: "")
            .apply()
    }

    private fun applyHighBrightness() {
        previousBrightness = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 1.0f }
    }

    private fun restoreBrightness() {
        window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
    }

    private fun label(text: String, sizeSp: Float, color: Int, typeface: Typeface, gravityValue: Int): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            gravity = gravityValue
            this.typeface = typeface
            includeFontPadding = true
        }
    }

    private fun capsuleButton(): View {
        return object : View(this) {
            private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x30FFFFFF
                strokeWidth = 1.dp().toFloat()
            }
            private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2.0f.dp()
            }

            init {
                background = RippleDrawable(
                    ColorStateList.valueOf(0x30FFFFFF),
                    rounded(SETTINGS_BLUE, 17.dp()),
                    rounded(Color.WHITE, 17.dp())
                )
                contentDescription = getString(R.string.settings_content_description)
                isClickable = true
                isFocusable = true
            }

            override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cy = height / 2f
                val dotRadius = 1.8f.dp()
                val firstDotX = 17.dp().toFloat()
                val dotGap = 9.dp().toFloat()
                repeat(3) { index ->
                    canvas.drawCircle(firstDotX + dotGap * index.toFloat(), cy, dotRadius, fill)
                }

                val separatorX = 51.dp().toFloat()
                canvas.drawLine(separatorX, cy - 10.dp().toFloat(), separatorX, cy + 10.dp().toFloat(), separatorPaint)

                val ringCenterX = 75.dp().toFloat()
                canvas.drawCircle(ringCenterX, cy, 8.dp().toFloat(), ringPaint)
                canvas.drawCircle(ringCenterX, cy, 2.8f.dp(), fill)
            }
        }
    }

    private fun button(text: String, sizeSp: Float, textColor: Int, backgroundColor: Int): Button {
        return Button(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(textColor)
            background = rounded(backgroundColor, 20.dp())
            transformationMethod = null
        }
    }

    private fun rounded(color: Int, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }
    }

    private fun bottomRounded(color: Int, radiusPx: Int): GradientDrawable {
        val radius = radiusPx.toFloat()
        return GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }
    }

    private fun frame(
        width: Int,
        height: Int,
        gravityValue: Int = Gravity.NO_GRAVITY
    ): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(width, height, gravityValue)
    }

    private fun linear(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(width, height, weight)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun Float.dp(): Float = this * resources.displayMetrics.density

    private fun String.withoutTrailingComma(): String = trim().trimEnd('\uff0c', ',', ' ', '\u3000')

    private fun String.asPermissionLine(): String {
        val value = trim().ifBlank { "\u901a\u884c\u6743\u9650\u5df2\u5f00\u542f" }
        return if (value.startsWith("*")) value else "* $value"
    }

    private enum class RefreshMode { FULL, QR_ONLY }

    private data class CoreSnapshot(
        val info: CodeInfo,
        val qrBitmap: Bitmap,
        val fetchedAtMillis: Long,
        val session: LoginSession
    )

    private companion object {
        const val PREFS_NAME = "huihutong_gate"
        const val KEY_OPEN_ID = "open_id"
        const val KEY_UNION_ID = "union_id"
        const val KEY_APARTMENT_ID = "apartment_id"
        const val KEY_ROOM_ID = "room_id"
        const val QR_REFRESH_INTERVAL_MS = 10_000L
        const val CLOCK_TICK_INTERVAL_MS = 1_000L
        val PRIMARY_BLUE: Int = Color.rgb(43, 130, 254)
        val HERO_BLUE: Int = Color.rgb(52, 139, 255)
        val SETTINGS_BLUE: Int = Color.rgb(32, 104, 203)
        val DEEP_BLUE: Int = Color.rgb(42, 96, 145)
        val PAGE_BACKGROUND: Int = Color.rgb(247, 248, 250)
        val DARK_TEXT: Int = Color.rgb(45, 45, 48)
        val GRAY_TEXT: Int = Color.rgb(113, 116, 122)
        val NAV_GRAY: Int = Color.rgb(145, 150, 158)
        val GREEN: Int = Color.rgb(83, 180, 88)
        val RED: Int = Color.rgb(213, 54, 47)
        val WARNING_YELLOW: Int = Color.rgb(226, 158, 52)
        val INPUT_BORDER: Int = Color.rgb(205, 209, 216)
    }
}
