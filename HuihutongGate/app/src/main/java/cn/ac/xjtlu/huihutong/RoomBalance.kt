package cn.ac.xjtlu.huihutong

import java.math.BigDecimal
import java.math.RoundingMode

data class RoomBalanceSnapshot(
    val amount: BigDecimal,
    val queriedAtMillis: Long
) {
    fun displayText(queriedAt: String): String {
        val roundedAmount = amount.setScale(2, RoundingMode.HALF_UP)
        val formattedAmount = if (roundedAmount.signum() < 0) {
            "-¥${roundedAmount.abs().toPlainString()}"
        } else {
            "¥${roundedAmount.toPlainString()}"
        }
        return "房间余额：$formattedAmount · 查询于 $queriedAt"
    }
}
