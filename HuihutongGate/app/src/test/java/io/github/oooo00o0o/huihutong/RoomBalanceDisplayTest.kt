package io.github.oooo00o0o.huihutong

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomBalanceDisplayTest {
    @Test
    fun `room balance displays two decimals and query time`() {
        val snapshot = RoomBalanceSnapshot(
            amount = BigDecimal("12.3"),
            queriedAtMillis = 1_000L
        )

        assertEquals(
            "房间余额：¥12.30 · 查询于 14:30",
            snapshot.displayText(queriedAt = "14:30")
        )
    }

    @Test
    fun `negative room balance remains visible`() {
        val snapshot = RoomBalanceSnapshot(
            amount = BigDecimal("-2.5"),
            queriedAtMillis = 1_000L
        )

        assertEquals(
            "房间余额：-¥2.50 · 查询于 14:30",
            snapshot.displayText(queriedAt = "14:30")
        )
    }
}
