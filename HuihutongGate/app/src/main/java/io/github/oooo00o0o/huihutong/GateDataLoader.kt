package io.github.oooo00o0o.huihutong

sealed interface SupplementalUpdate {
    data class PowerWarningLoaded(val threshold: String?) : SupplementalUpdate
    data class RoomBalanceLoaded(val snapshot: RoomBalanceSnapshot) : SupplementalUpdate
}

data class CoreLoad(
    val info: CodeInfo,
    val qrPayload: String,
    val session: LoginSession
)

private const val TOKEN_REUSE_MS = 50_000L

class GateDataLoader(
    private val api: GateApi,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class CachedSession(val credentials: Credentials, val session: LoginSession)

    private var cachedSession: CachedSession? = null

    fun loadSupplemental(
        session: LoginSession,
        room: RoomReference?
    ): List<SupplementalUpdate> = buildList {
        try {
            add(SupplementalUpdate.PowerWarningLoaded(api.loadPowerWarning(session)))
        } catch (_: Exception) {
        }
        if (room != null) {
            try {
                val amount = api.loadRoomBalance(session, room)
                add(
                    SupplementalUpdate.RoomBalanceLoaded(
                        RoomBalanceSnapshot(amount = amount, queriedAtMillis = nowMillis())
                    )
                )
            } catch (_: Exception) {
            }
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
            nowMillis() - cached.session.loginAtMillis < TOKEN_REUSE_MS
        ) {
            return cached.session
        }
        return api.login(credentials).also { cachedSession = CachedSession(credentials, it) }
    }
}
