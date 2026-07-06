package cn.ac.xjtlu.huihutong

import android.net.Uri
import org.json.JSONObject

data class Credentials(
    val openId: String,
    val unionId: String?
)

object CredentialParser {
    fun parse(raw: String): Credentials {
        val text = raw.trim()
        require(text.isNotEmpty()) { "请输入 openId 或 openId/unionId 参数" }

        if (text.startsWith("{")) {
            val json = JSONObject(text)
            val openId = json.firstString("openId", "openid", "wxid", "wxId")
            val unionId = json.firstString("unionId", "unionid")
            return Credentials(openId = openId.requiredOpenId(), unionId = unionId.cleanOptional())
        }

        if ("=" in text || text.startsWith("http://") || text.startsWith("https://")) {
            val uri = if (text.startsWith("http://") || text.startsWith("https://")) {
                Uri.parse(text)
            } else {
                Uri.parse("https://local.invalid/?" + text.removePrefix("?"))
            }
            val openId = uri.firstQueryParameter("openId", "openid", "wxid", "wxId")
            val unionId = uri.firstQueryParameter("unionId", "unionid")
            return Credentials(openId = openId.requiredOpenId(), unionId = unionId.cleanOptional())
        }

        val parts = text.split(',', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        val openId = parts.firstOrNull().requiredOpenId()
        val unionId = parts.getOrNull(1).cleanOptional()
        return Credentials(openId = openId, unionId = unionId)
    }

    private fun JSONObject.firstString(vararg names: String): String? {
        for (name in names) {
            val value = optString(name, "").trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun Uri.firstQueryParameter(vararg names: String): String? {
        for (name in names) {
            val value = getQueryParameter(name)?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun String?.requiredOpenId(): String {
        val value = cleanOptional()
        require(!value.isNullOrBlank()) { "没有识别到 openId，也可以粘贴 wxid/openId 原文" }
        return value
    }

    private fun String?.cleanOptional(): String? {
        val value = this?.trim()
        return if (value.isNullOrEmpty() || value.equals("null", ignoreCase = true)) null else value
    }
}
