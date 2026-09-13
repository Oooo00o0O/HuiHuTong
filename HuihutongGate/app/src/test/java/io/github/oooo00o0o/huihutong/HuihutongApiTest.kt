package io.github.oooo00o0o.huihutong

import java.net.HttpURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HuihutongApiTest {
    @Test
    fun `non JSON unauthorized response still expires authentication`() {
        val error = assertThrows(AuthExpiredException::class.java) {
            parseApiResponse(HttpURLConnection.HTTP_UNAUTHORIZED, "<html>Unauthorized</html>")
        }

        assertEquals("认证已失效", error.message)
    }
}
