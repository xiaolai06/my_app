package com.example.dailylife.core

import java.time.LocalDate

/** Both boundaries use the original anchor, so a short month never shifts later periods. */
object PaymentPeriodRules {
    fun start(anchor: LocalDate, cycle: String, interval: Int, index: Int): LocalDate {
        require(interval in 1..120 && index >= 0)
        val offset = index.toLong() * interval
        return when (cycle) {
            "daily" -> anchor.plusDays(offset)
            "weekly" -> anchor.plusWeeks(offset)
            "monthly" -> anchor.plusMonths(offset)
            "yearly" -> anchor.plusYears(offset)
            else -> error("不支持的付款周期")
        }
    }

    fun end(anchor: LocalDate, cycle: String, interval: Int, index: Int): LocalDate =
        start(anchor, cycle, interval, Math.addExact(index, 1)).minusDays(1)
}
