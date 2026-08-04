package io.github.oooo00o0o.huihutong

sealed interface SupplementalUpdate {
    data class PowerWarningLoaded(val threshold: String?) : SupplementalUpdate
    data object PowerWarningFailed : SupplementalUpdate
    data class RoomBalanceLoaded(val snapshot: RoomBalanceSnapshot) : SupplementalUpdate
    data object RoomBalanceFailed : SupplementalUpdate
}

data class CoreLoad(
    val info: CodeInfo,
    val qrPayload: String,
    val session: LoginSession
)

class GateDataLoader(
    private val api: GateApi,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val tokenReuseMs: Long = 50_000L
) {
    private data class CachedSession(val credentials: Credentials, val session: LoginSession)

    @Volatile
    private var cachedSession: CachedSession? = null

    fun clearSession() {
        cachedSession = null
    }

    fun loadSupplemental(
        session: LoginSession,
        room: RoomReference?
    ): Sequence<SupplementalUpdate> = sequence {
        val warningUpdate = try {
            SupplementalUpdate.PowerWarningLoaded(api.loadPowerWarning(session))
        } catch (_: Exception) {
            SupplementalUpdate.PowerWarningFailed
        }
        yield(warningUpdate)
        if (room != null) {
            val balanceUpdate = try {
                val amount = api.loadRoomBalance(session, room)
                SupplementalUpdate.RoomBalanceLoaded(
                    RoomBalanceSnapshot(amount = amount, queriedAtMillis = nowMillis())
                )
            } catch (_: Exception) {
                SupplementalUpdate.RoomBalanceFailed
            }
            yield(balanceUpdate)
        }
    }

    fun loadCore(
        credentials: Credentials,
        full: Boolean,
        currentInfo: CodeInfo?
    ): CoreLoad {
        return try {
            loadCoreWithSession(ensureSession(credentials), full, currentInfo)
        } catch (expired: AuthExpiredException) {
            cachedSession = null
            loadCoreWithSession(ensureSession(credentials), full, currentInfo)
        }
    }

    private fun loadCoreWithSession(
        activeSession: LoginSession,
        full: Boolean,
        currentInfo: CodeInfo?
    ): CoreLoad {
        val info = if (full) {
            api.loadCodeInfo(activeSession)
        } else {
            requireNotNull(currentInfo) { "QR-only loads require an existing profile snapshot" }
        }
        val qrPayload = api.loadQrCode(activeSession).ifBlank { info.qrCode }
        require(qrPayload.isNotBlank()) { "接口没有返回二维码内容" }
        return CoreLoad(info, qrPayload, activeSession)
    }

    private fun ensureSession(credentials: Credentials): LoginSession {
        val cached = cachedSession
        if (cached != null && cached.credentials == credentials &&
            nowMillis() - cached.session.loginAtMillis < tokenReuseMs
        ) {
            return cached.session
        }
        return api.login(credentials).also { cachedSession = CachedSession(credentials, it) }
    }
}
