package cn.ac.xjtlu.huihutong

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val api = HuihutongApi()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicBoolean(false)
    private val dateFormat = SimpleDateFormat("yyyy\u5e74MM\u6708dd\u65e5 HH:mm:ss", Locale.CHINA)

    private lateinit var apartmentText: TextView
    private lateinit var nameText: TextView
    private lateinit var companyText: TextView
    private lateinit var verifiedText: TextView
    private lateinit var qrImage: ImageView
    private lateinit var timeText: TextView
    private lateinit var permissionText: TextView
    private lateinit var warningText: TextView
    private lateinit var hintText: TextView
    private lateinit var refreshButton: Button

    @Volatile private var credentials: Credentials? = null
    @Volatile private var session: LoginSession? = null
    @Volatile private var currentInfo: CodeInfo? = null
    @Volatile private var currentWarning: String? = null
    private var previousBrightness: Float = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh(full = currentInfo == null)
            mainHandler.postDelayed(this, QR_REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BLUE
        window.navigationBarColor = Color.WHITE
        buildUi()
        credentials = loadCredentials()
        if (credentials == null) {
            renderNoCredentials()
            mainHandler.post { showCredentialDialog() }
        } else {
            refresh(full = true)
        }
    }

    override fun onResume() {
        super.onResume()
        applyHighBrightness()
        if (credentials != null) startRefreshLoop()
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(refreshRunnable)
        restoreBrightness()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(LIGHT_BACKGROUND) }
        root.addView(View(this).apply { setBackgroundColor(BLUE) }, frame(-1, 330.dp(), Gravity.TOP))

        root.addView(
            label("\u6211\u7684\u4e8c\u7ef4\u7801", 22f, Color.WHITE, Typeface.DEFAULT_BOLD, Gravity.CENTER),
            frame(-1, 72.dp(), Gravity.TOP)
        )

        val settings = button("\u8bbe\u7f6e", 14f, Color.WHITE, 0x3DFFFFFF).apply {
            setOnClickListener { showCredentialDialog() }
        }
        root.addView(settings, frame(86.dp(), 42.dp(), Gravity.TOP or Gravity.END).apply {
            topMargin = 14.dp()
            rightMargin = 16.dp()
        })

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp(), 98.dp(), 24.dp(), 92.dp())
        }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, frame(-1, -1))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 24.dp())
            setPadding(24.dp(), 30.dp(), 24.dp(), 30.dp())
            minimumHeight = 590.dp()
        }
        content.addView(card, linear(-1, -2))

        card.addView(profileBlock(), linear(-1, 150.dp()))
        card.addView(qrBlock(), linear(280.dp(), 280.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 22.dp()
        })

        timeText = label("", 22f, GRAY_TEXT, Typeface.DEFAULT, Gravity.CENTER)
        card.addView(timeText, linear(-1, -2).apply { topMargin = 32.dp() })

        permissionText = label("", 21f, DEEP_BLUE, Typeface.DEFAULT_BOLD, Gravity.CENTER)
        card.addView(permissionText, linear(-1, -2).apply { topMargin = 22.dp() })

        warningText = label("", 20f, RED, Typeface.DEFAULT_BOLD, Gravity.CENTER).apply {
            visibility = View.GONE
            setLineSpacing(0f, 1.05f)
        }
        card.addView(warningText, linear(-1, -2).apply { topMargin = 16.dp() })

        hintText = label("", 14f, GRAY_TEXT, Typeface.DEFAULT, Gravity.CENTER)
        card.addView(hintText, linear(-1, -2).apply { topMargin = 18.dp() })

        refreshButton = button("\u7acb\u5373\u5237\u65b0", 15f, Color.WHITE, BLUE).apply {
            setOnClickListener { refresh(full = true) }
        }
        card.addView(refreshButton, linear(130.dp(), 42.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 16.dp()
        })

        root.addView(bottomNav(), frame(-1, 72.dp(), Gravity.BOTTOM))
        setContentView(root)
    }

    private fun profileBlock(): View {
        val row = FrameLayout(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(column, frame(-1, -1).apply { rightMargin = 110.dp() })

        apartmentText = label("", 22f, DARK_TEXT, Typeface.DEFAULT_BOLD, Gravity.START)
        nameText = label("", 21f, DARK_TEXT, Typeface.DEFAULT, Gravity.START)
        companyText = label("", 21f, DARK_TEXT, Typeface.DEFAULT, Gravity.START)
        column.addView(apartmentText, linear(-1, -2))
        column.addView(nameText, linear(-1, -2).apply { topMargin = 14.dp() })
        column.addView(companyText, linear(-1, -2).apply { topMargin = 14.dp() })

        verifiedText = label("\u5df2\u9a8c\u8bc1", 22f, GREEN, Typeface.DEFAULT_BOLD, Gravity.CENTER)
        row.addView(verifiedText, frame(100.dp(), 70.dp(), Gravity.END or Gravity.CENTER_VERTICAL))
        return row
    }

    private fun qrBlock(): View {
        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(5.dp(), WARNING_YELLOW, 10.dp().toFloat(), 7.dp().toFloat())
                cornerRadius = 8.dp().toFloat()
            }
            setPadding(13.dp(), 13.dp(), 13.dp(), 13.dp())
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
        nav.addView(navItem("\u9996\u9875", false), linear(0, -1, 1f))
        nav.addView(navItem("\u4e8c\u7ef4\u7801", true), linear(0, -1, 1f))
        nav.addView(navItem("\u6211\u7684", false), linear(0, -1, 1f))
        return nav
    }

    private fun navItem(text: String, selected: Boolean): TextView {
        return label(text, 18f, if (selected) BLUE else NAV_GRAY, if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT, Gravity.CENTER)
    }

    private fun renderNoCredentials() {
        apartmentText.text = "\u8bf7\u5bfc\u5165\u6167\u6e56\u901a\u53c2\u6570"
        nameText.text = "\u652f\u6301 openId / unionId"
        companyText.text = "\u53c2\u6570\u4ec5\u4fdd\u5b58\u5728\u672c\u673a"
        verifiedText.text = "\u672a\u767b\u5f55"
        verifiedText.setTextColor(GRAY_TEXT)
        qrImage.setImageBitmap(null)
        timeText.text = dateFormat.format(Date())
        permissionText.text = "* \u7b49\u5f85\u5bfc\u5165"
        permissionText.setTextColor(GRAY_TEXT)
        warningText.visibility = View.GONE
        hintText.text = "\u70b9\u53f3\u4e0a\u89d2\u201c\u8bbe\u7f6e\u201d\uff0c\u7c98\u8d34 openId=...&unionId=... \u6216\u53ea\u7c98\u8d34 openId\u3002"
        refreshButton.isEnabled = false
    }

    private fun renderSnapshot(snapshot: UiSnapshot) {
        currentInfo = snapshot.info
        currentWarning = snapshot.warningThreshold
        apartmentText.text = snapshot.info.apartment.ifBlank { "\u6167\u6e56\u901a\u95e8\u7981" }
        nameText.text = snapshot.info.name.ifBlank { "\u5df2\u767b\u5f55\u7528\u6237" }
        companyText.text = snapshot.info.companyName.ifBlank { "\u897f\u4ea4\u5229\u7269\u6d66\u5927\u5b66" }
        verifiedText.text = "\u5df2\u9a8c\u8bc1"
        verifiedText.setTextColor(GREEN)
        qrImage.setImageBitmap(snapshot.qrBitmap)
        timeText.text = dateFormat.format(Date(snapshot.fetchedAtMillis))
        permissionText.text = snapshot.info.permissionText.asPermissionLine()
        permissionText.setTextColor(DEEP_BLUE)
        refreshButton.isEnabled = true
        hintText.text = "\u4e8c\u7ef4\u7801\u7ea6\u6bcf 10 \u79d2\u5237\u65b0\uff1b\u672c\u9875\u9762\u4f1a\u81ea\u52a8\u8c03\u9ad8\u4eae\u5ea6\u3002"

        val threshold = snapshot.warningThreshold
        if (threshold.isNullOrBlank()) {
            warningText.visibility = View.GONE
        } else {
            warningText.visibility = View.VISIBLE
            warningText.text = "\u60a8\u6240\u767b\u8bb0\u7684\u623f\u95f4\u4f59\u989d\u5df2\u4f4e\u4e8e${threshold}\u5143\uff0c\u8bf7\u53ca\u65f6\u7f34\u8d39\u3002\n\u51cc\u66680\u70b9\u52302\u70b9\u4e3a\u7cfb\u7edf\u7ed3\u7b97\u65f6\u95f4\uff0c\u8bf7\u52ff\u5728\u8be5\u65f6\u6bb5\u8fdb\u884c\u5145\u503c!"
        }
    }

    private fun renderError(error: Throwable) {
        val message = error.message ?: error.javaClass.simpleName
        hintText.text = "\u5237\u65b0\u5931\u8d25\uff1a$message"
        permissionText.text = "* \u5237\u65b0\u5931\u8d25"
        permissionText.setTextColor(RED)
        refreshButton.isEnabled = true
    }

    private fun startRefreshLoop() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, QR_REFRESH_INTERVAL_MS)
    }

    private fun refresh(full: Boolean) {
        val creds = credentials
        if (creds == null) {
            renderNoCredentials()
            return
        }
        if (!inFlight.compareAndSet(false, true)) return
        refreshButton.isEnabled = false
        hintText.text = if (full || currentInfo == null) {
            "\u6b63\u5728\u767b\u5f55\u5e76\u52a0\u8f7d\u4e8c\u7ef4\u7801..."
        } else {
            "\u6b63\u5728\u5237\u65b0\u4e8c\u7ef4\u7801..."
        }

        executor.execute {
            try {
                val snapshot = loadSnapshot(creds, full || currentInfo == null)
                mainHandler.post {
                    inFlight.set(false)
                    renderSnapshot(snapshot)
                }
            } catch (error: Throwable) {
                mainHandler.post {
                    inFlight.set(false)
                    renderError(error)
                }
            }
        }
    }

    private fun loadSnapshot(creds: Credentials, full: Boolean): UiSnapshot {
        return try {
            loadSnapshotWithSession(ensureSession(creds), full)
        } catch (expired: HuihutongApi.AuthExpiredException) {
            session = null
            loadSnapshotWithSession(ensureSession(creds), full = true)
        }
    }

    private fun loadSnapshotWithSession(activeSession: LoginSession, full: Boolean): UiSnapshot {
        val info = if (full) api.loadCodeInfo(activeSession) else currentInfo ?: api.loadCodeInfo(activeSession)
        val qrPayload = api.loadQrCode(activeSession).ifBlank { info.qrCode }
        require(qrPayload.isNotBlank()) { "\u63a5\u53e3\u6ca1\u6709\u8fd4\u56de\u4e8c\u7ef4\u7801\u5185\u5bb9" }
        val warning = if (full) api.loadPowerWarning(activeSession) else currentWarning
        return UiSnapshot(info, warning, QrCodeBitmap.create(qrPayload, 760), System.currentTimeMillis())
    }

    private fun ensureSession(creds: Credentials): LoginSession {
        val cached = session
        if (cached != null && System.currentTimeMillis() - cached.loginAtMillis < TOKEN_REUSE_MS) return cached
        return api.login(creds).also { session = it }
    }

    private fun showCredentialDialog() {
        val input = EditText(this).apply {
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "openId=...&unionId=...\nJSON / openId"
            setText(credentials?.let { if (it.unionId.isNullOrBlank()) it.openId else "openId=${it.openId}&unionId=${it.unionId}" } ?: "")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("\u5bfc\u5165\u6167\u6e56\u901a\u53c2\u6570")
            .setMessage("\u5f53\u524d\u5c0f\u7a0b\u5e8f\u6293\u5305\u5efa\u8bae\u540c\u65f6\u63d0\u4f9b openId \u548c unionId\uff1b\u5982\u679c\u53ea\u6709 openId\uff0c\u4e5f\u53ef\u4ee5\u5148\u4fdd\u5b58\u5c1d\u8bd5\u3002")
            .setView(input)
            .setNegativeButton("\u53d6\u6d88", null)
            .setPositiveButton("\u4fdd\u5b58") { _, _ ->
                try {
                    val parsed = CredentialParser.parse(input.text.toString())
                    saveCredentials(parsed)
                    credentials = parsed
                    session = null
                    currentInfo = null
                    currentWarning = null
                    refresh(full = true)
                    startRefreshLoop()
                    Toast.makeText(this, "\u53c2\u6570\u5df2\u4fdd\u5b58", Toast.LENGTH_SHORT).show()
                } catch (error: IllegalArgumentException) {
                    Toast.makeText(this, error.message ?: "\u53c2\u6570\u683c\u5f0f\u9519\u8bef", Toast.LENGTH_LONG).show()
                    renderNoCredentials()
                }
            }
            .show()
    }

    private fun loadCredentials(): Credentials? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val openId = prefs.getString(KEY_OPEN_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val unionId = prefs.getString(KEY_UNION_ID, null)?.takeIf { it.isNotBlank() }
        return Credentials(openId, unionId)
    }

    private fun saveCredentials(value: Credentials) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPEN_ID, value.openId)
            .putString(KEY_UNION_ID, value.unionId ?: "")
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

    private fun frame(width: Int, height: Int, gravityValue: Int = Gravity.NO_GRAVITY): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(width, height, gravityValue)
    }

    private fun linear(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(width, height, weight)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).roundToInt()

    private fun String.asPermissionLine(): String {
        val value = trim().ifBlank { "\u901a\u884c\u6743\u9650\u5df2\u5f00\u542f" }
        return if (value.startsWith("*")) value else "* $value"
    }

    private data class UiSnapshot(
        val info: CodeInfo,
        val warningThreshold: String?,
        val qrBitmap: Bitmap,
        val fetchedAtMillis: Long
    )

    private companion object {
        const val PREFS_NAME = "huihutong_gate"
        const val KEY_OPEN_ID = "open_id"
        const val KEY_UNION_ID = "union_id"
        const val QR_REFRESH_INTERVAL_MS = 10_000L
        const val TOKEN_REUSE_MS = 50_000L

        val BLUE: Int = Color.rgb(47, 134, 246)
        val DEEP_BLUE: Int = Color.rgb(44, 91, 150)
        val LIGHT_BACKGROUND: Int = Color.rgb(245, 246, 248)
        val DARK_TEXT: Int = Color.rgb(45, 45, 48)
        val GRAY_TEXT: Int = Color.rgb(113, 116, 122)
        val NAV_GRAY: Int = Color.rgb(145, 150, 158)
        val GREEN: Int = Color.rgb(83, 180, 88)
        val RED: Int = Color.rgb(220, 58, 47)
        val WARNING_YELLOW: Int = Color.rgb(240, 186, 92)
    }
}
