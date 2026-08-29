package io.github.oooo00o0o.huihutong

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

private abstract class StubGateApi : GateApi {
    override fun login(credentials: Credentials): LoginSession = error("unused")
    override fun loadCodeInfo(session: LoginSession): CodeInfo = error("unused")
    override fun loadQrCode(session: LoginSession): String = error("unused")
    override fun loadPowerWarning(session: LoginSession): String? = error("unused")
    override fun loadRoomBalance(session: LoginSession, room: RoomReference): BigDecimal =
        error("unused")
}

class GateDataLoaderTest {
    @Test
    fun `core load succeeds when the warning endpoint is unavailable`() {
        val session = LoginSession("satoken", "token", loginAtMillis = 1_000L)
        val info = CodeInfo(
            apartment = "A05 201",
            name = "测试用户",
            companyName = "西交利物浦大学",
            permissionText = "通行权限已开启",
            qrCode = "fallback"
        )
        val loader = GateDataLoader(
            api = object : StubGateApi() {
                override fun login(credentials: Credentials) = session
                override fun loadCodeInfo(session: LoginSession) = info
                override fun loadQrCode(session: LoginSession) = "fresh-qr"
            },
            nowMillis = { 1_001L }
        )

        val result = loader.loadCore(
            credentials = Credentials("open-id", "union-id"),
            full = true,
            currentInfo = null
        )

        assertEquals(
            CoreLoad(info = info, qrPayload = "fresh-qr", session = session),
            result
        )
    }

    @Test
    fun `room balance loads after power warning`() {
        val session = LoginSession("satoken", "token", loginAtMillis = 1_000L)
        var warningFinished = false
        val loader = GateDataLoader(
            api = object : StubGateApi() {
                override fun loadPowerWarning(session: LoginSession): String? {
                    warningFinished = true
                    return "10.00"
                }

                override fun loadRoomBalance(
                    session: LoginSession,
                    room: RoomReference
                ): BigDecimal {
                    check(warningFinished) { "room balance started before power warning finished" }
                    return BigDecimal("8.75")
                }
            },
            nowMillis = { 1_234L }
        )

        val updates = loader.loadSupplemental(
            session = session,
            room = RoomReference(apartmentId = "1", roomId = "9626")
        )

        assertEquals(
            listOf(
                SupplementalUpdate.PowerWarningLoaded("10.00"),
                SupplementalUpdate.RoomBalanceLoaded(
                    RoomBalanceSnapshot(amount = BigDecimal("8.75"), queriedAtMillis = 1_234L)
                )
            ),
            updates
        )
    }

    @Test
    fun `room balance still loads when power warning fails`() {
        val session = LoginSession("satoken", "token", loginAtMillis = 1_000L)
        val loader = GateDataLoader(
            api = object : StubGateApi() {
                override fun loadPowerWarning(session: LoginSession): String? = error("warning unavailable")
                override fun loadRoomBalance(session: LoginSession, room: RoomReference) =
                    BigDecimal("8.75")
            },
            nowMillis = { 1_234L }
        )

        val updates = loader.loadSupplemental(
            session = session,
            room = RoomReference(apartmentId = "1", roomId = "9626")
        )

        assertEquals(
            listOf(
                SupplementalUpdate.RoomBalanceLoaded(
                    RoomBalanceSnapshot(amount = BigDecimal("8.75"), queriedAtMillis = 1_234L)
                )
            ),
            updates
        )
    }

    @Test
    fun `room balance failure does not discard a loaded power warning`() {
        val session = LoginSession("satoken", "token", loginAtMillis = 1_000L)
        val loader = GateDataLoader(
            api = object : StubGateApi() {
                override fun loadPowerWarning(session: LoginSession) = "10.00"
                override fun loadRoomBalance(session: LoginSession, room: RoomReference): BigDecimal =
                    error("balance unavailable")
            }
        )

        val updates = loader.loadSupplemental(
            session = session,
            room = RoomReference(apartmentId = "1", roomId = "9626")
        )

        assertEquals(
            listOf(
                SupplementalUpdate.PowerWarningLoaded("10.00")
            ),
            updates
        )
    }

    @Test
    fun `cached session is not reused for different credentials`() {
        val firstCredentials = Credentials("first-open-id", "first-union-id")
        val secondCredentials = Credentials("second-open-id", "second-union-id")
        val loginCalls = mutableListOf<Credentials>()
        val loader = GateDataLoader(
            api = object : StubGateApi() {
                override fun login(credentials: Credentials): LoginSession {
                    loginCalls += credentials
                    return LoginSession(
                        tokenName = "sa-${credentials.openId}",
                        token = "token-${credentials.openId}",
                        loginAtMillis = 1_000L
                    )
                }

                override fun loadCodeInfo(session: LoginSession) = CodeInfo(
                    apartment = "A05 201",
                    name = "User",
                    companyName = "XJTLU",
                    permissionText = "Allowed",
                    qrCode = "fallback"
                )

                override fun loadQrCode(session: LoginSession) = "qr-${session.token}"
            },
            nowMillis = { 1_001L }
        )

        loader.loadCore(firstCredentials, full = true, currentInfo = null)
        val second = loader.loadCore(secondCredentials, full = true, currentInfo = null)

        assertEquals(listOf(firstCredentials, secondCredentials), loginCalls)
        assertEquals("token-second-open-id", second.session.token)
        assertEquals("qr-token-second-open-id", second.qrPayload)
    }

    @Test
    fun `QR only auth retry does not reload profile`() {
        val credentials = Credentials("open-id", "union-id")
        val existingInfo = CodeInfo(
            apartment = "A05 201",
            name = "User",
            companyName = "XJTLU",
            permissionText = "Allowed",
            qrCode = "fallback"
        )
        var loginCalls = 0
        var infoCalls = 0
        val loader = GateDataLoader(
            api = object : StubGateApi() {
                override fun login(credentials: Credentials): LoginSession {
                    loginCalls += 1
                    return LoginSession(
                        tokenName = "satoken",
                        token = "token-$loginCalls",
                        loginAtMillis = 1_000L
                    )
                }

                override fun loadCodeInfo(session: LoginSession): CodeInfo {
                    infoCalls += 1
                    return existingInfo
                }

                override fun loadQrCode(session: LoginSession): String {
                    if (session.token == "token-1") throw AuthExpiredException("expired")
                    return "fresh-qr"
                }
            },
            nowMillis = { 1_001L }
        )

        val result = loader.loadCore(credentials, full = false, currentInfo = existingInfo)

        assertEquals(2, loginCalls)
        assertEquals(0, infoCalls)
        assertEquals(existingInfo, result.info)
        assertEquals("fresh-qr", result.qrPayload)
    }

    @Test
    fun `QR only load requires an existing profile snapshot`() {
        val session = LoginSession("satoken", "token", loginAtMillis = 1_000L)
        val loader = GateDataLoader(
            api = object : StubGateApi() {
                override fun login(credentials: Credentials) = session
                override fun loadCodeInfo(session: LoginSession): CodeInfo {
                    error("QR-only load must not fetch profile data")
                }

                override fun loadQrCode(session: LoginSession) = "fresh-qr"
            },
            nowMillis = { 1_001L }
        )

        assertThrows(IllegalArgumentException::class.java) {
            loader.loadCore(Credentials("open-id", "union-id"), full = false, currentInfo = null)
        }
    }
}
