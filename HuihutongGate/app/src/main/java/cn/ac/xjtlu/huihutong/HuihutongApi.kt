package cn.ac.xjtlu.huihutong

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

data class LoginSession(
    val tokenName: String,
    val token: String,
    val loginAtMillis: Long = System.currentTimeMillis()
)

data class CodeInfo(
    val apartment: String,
    val name: String,
    val companyName: String,
    val permissionText: String,
    val qrCode: String
)

class HuihutongApi(
    private val baseUrl: String = "https://api.215123.cn"
) {
    class ApiException(message: String) : RuntimeException(message)
    class AuthExpiredException(message: String) : RuntimeException(message)

    fun login(credentials: Credentials): LoginSession {
        val params = linkedMapOf("openId" to credentials.openId)
        val unionId = credentials.unionId
        if (!unionId.isNullOrBlank()) {
            params["unionId"] = unionId
        }

        val json = getJson("/web-app/auth/certificateLogin", params)
        val data = json.optJSONObject("data") ?: throw ApiException("登录响应缺少 token")
        val token = data.optString("token", "").trim()
        if (token.isEmpty()) throw ApiException("登录响应 token 为空")
        return LoginSession(
            tokenName = data.optString("tokenName", "satoken").ifBlank { "satoken" },
            token = token
        )
    }

    fun loadCodeInfo(session: LoginSession): CodeInfo {
        val json = getJson("/pms/welcome/make-code-info", tokenSession = session)
        val data = json.optJSONObject("data") ?: throw ApiException("二维码信息响应为空")
        return CodeInfo(
            apartment = data.optString("apartment", "").trim(),
            name = data.optString("name", "").trim(),
            companyName = data.optString("companyName", "").trim(),
            permissionText = data.firstString("text", "qrCodeStatus", "status").ifBlank { "通行权限已开启" },
            qrCode = data.firstString("qrCode", "mackCode")
        )
    }

    fun loadQrCode(session: LoginSession): String {
        val json = getJson("/pms/welcome/make-qrcode", tokenSession = session)
        val data = json.opt("data")
        return when (data) {
            is String -> data.trim()
            is JSONObject -> data.firstString("qrCode", "mackCode")
            else -> ""
        }
    }

    fun loadPowerWarning(session: LoginSession): String? {
        val json = getJson("/pms/welcome/power-warning", tokenSession = session)
        val data = json.opt("data")
        if (data == null || data == JSONObject.NULL) return null
        val value = data.toString().trim()
        return value.ifBlank { null }
    }

    private fun getJson(
        path: String,
        params: Map<String, String> = emptyMap(),
        tokenSession: LoginSession? = null
    ): JSONObject {
        val url = URL(baseUrl + path + params.toQueryString())
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("content-type", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", REFERER)
            tokenSession?.let { setRequestProperty(it.tokenName, it.token) }
        }

        val httpCode = connection.responseCode
        val body = connection.readBody(httpCode)
        val json = try {
            JSONObject(body)
        } catch (error: Exception) {
            throw ApiException("接口返回不是 JSON：HTTP $httpCode")
        }

        if (httpCode == HttpURLConnection.HTTP_UNAUTHORIZED || json.optInt("code", 0) == 401) {
            throw AuthExpiredException(json.messageOr("认证已失效"))
        }

        val okBySuccess = !json.has("success") || json.optBoolean("success", false)
        val okByCode = !json.has("code") || json.optInt("code", 200) in 200..299
        if (httpCode !in 200..299 || !okBySuccess || !okByCode) {
            throw ApiException(json.messageOr("请求失败：HTTP $httpCode"))
        }
        return json
    }

    private fun HttpsURLConnection.readBody(httpCode: Int): String {
        val stream = if (httpCode in 200..299) inputStream else errorStream ?: inputStream
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            buildString {
                while (true) {
                    val line = reader.readLine() ?: break
                    append(line)
                }
            }
        }
    }

    private fun Map<String, String>.toQueryString(): String {
        if (isEmpty()) return ""
        return entries.joinToString(prefix = "?", separator = "&") { (key, value) ->
            key.urlEncode() + "=" + value.urlEncode()
        }
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun JSONObject.firstString(vararg names: String): String {
        for (name in names) {
            val value = optString(name, "").trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    private fun JSONObject.messageOr(defaultMessage: String): String {
        return firstString("message", "msg", "error").ifBlank { defaultMessage }
    }

    private companion object {
        const val REFERER = "https://servicewechat.com/wx2660b404a3b7575a/158/page-frame.html"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Mobile MicroMessenger/8.0.50 MiniProgramEnv/android"
    }
}
