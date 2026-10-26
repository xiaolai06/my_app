package com.example.dailylife.core

import com.example.dailylife.data.local.RepeatRule
import java.math.RoundingMode
import java.time.*
import java.time.temporal.ChronoUnit

object PlanRules {
    fun title(text: String): String = text.trim().also { require(it.isNotEmpty() && it.length <= 120) { "名称需要 1～120 字" } }
    fun milli(text: String): Long = runCatching {
        text.toBigDecimal().setScale(3, RoundingMode.UNNECESSARY).movePointRight(3).longValueExact()
    }.getOrElse { throw IllegalArgumentException("请输入最多三位小数的有效数值") }
    fun display(value: Long): String = java.math.BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString()
    fun dates(rule: RepeatRule, through: LocalDate, minimum: Int = 0, from:LocalDate?=null): List<LocalDate> {
        val anchor = LocalDate.parse(rule.anchorDate)
        require(rule.frequency in setOf("daily","weekly","monthly","yearly") && rule.interval in 1..365) { "重复间隔需为 1～365" }
        require(rule.count == null || rule.count in 1..10000) { "重复次数需为 1～10000" }
        val weekdays = rule.weekdays.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        require(rule.frequency != "weekly" || weekdays.isNotEmpty() && weekdays.all { it in 1..7 }) { "请选择至少一个星期" }
        require(rule.invalidPolicy in setOf("last","skip","march")) { "无效的日期策略" }
        rule.endDate?.let { require(LocalDate.parse(it) >= anchor) { "重复结束日期不能早于开始" } }
        val end = minOf(rule.endDate?.let(LocalDate::parse) ?: LocalDate.MAX,
            rule.stoppedFrom?.let { LocalDate.parse(it).minusDays(1) } ?: LocalDate.MAX)
        val result = mutableListOf<LocalDate>()
        // Iterate by periods rather than days for month/year rules. No infinite expansion.
        var period = if(from!=null && from>anchor && rule.count==null) when(rule.frequency) {
            "daily"->(ChronoUnit.DAYS.between(anchor,from)/rule.interval-1).coerceAtLeast(0)
            "weekly"->(ChronoUnit.DAYS.between(anchor,from)-7L*rule.interval).coerceAtLeast(0)
            "monthly"->(ChronoUnit.MONTHS.between(YearMonth.from(anchor),YearMonth.from(from))/rule.interval-1).coerceAtLeast(0)
            else->((from.year-anchor.year).toLong()/rule.interval-1).coerceAtLeast(0)
        } else 0L
        var generated=0;var inspected=0
        while (inspected++ < 100000 && generated < (rule.count ?: 100000)) {
            val candidate = when (rule.frequency) {
                "daily" -> anchor.plusDays(period * rule.interval)
                "weekly" -> anchor.plusDays(period)
                "monthly" -> {
                    val month = YearMonth.from(anchor).plusMonths(period * rule.interval)
                    if (anchor.dayOfMonth <= month.lengthOfMonth()) month.atDay(anchor.dayOfMonth)
                    else if (rule.invalidPolicy == "skip") null else month.atEndOfMonth()
                }
                else -> {
                    val year = Math.toIntExact(anchor.year.toLong() + period * rule.interval)
                    if (anchor.monthValue == 2 && anchor.dayOfMonth == 29 && !Year.isLeap(year.toLong())) when (rule.invalidPolicy) {
                        "skip" -> null; "march" -> LocalDate.of(year,3,1); else -> LocalDate.of(year,2,28)
                    } else anchor.withYear(year)
                }
            }
            period++
            val d = candidate ?: continue
            if (d > end || d > through && result.size >= minimum) break
            if (rule.frequency == "weekly") {
                val weeks = ChronoUnit.WEEKS.between(anchor.with(java.time.DayOfWeek.MONDAY), d.with(java.time.DayOfWeek.MONDAY))
                if (weeks % rule.interval != 0L || d.dayOfWeek.value !in weekdays) continue
            }
            generated++
            if(from==null || d>=from) result += d
        }
        return result
    }
    fun overdue(due: String?, time: String?, today: LocalDateTime = LocalDateTime.now()): Boolean =
        due != null && today > LocalDate.parse(due).atTime(time?.let(LocalTime::parse) ?: LocalTime.MAX)
    fun duration(start: LocalDateTime, end: LocalDateTime, zone: ZoneId): Pair<Long,Long> {
        // Reject nonexistent local times at DST boundaries, rather than silently moving appointments.
        require(zone.rules.getValidOffsets(start).isNotEmpty() && zone.rules.getValidOffsets(end).isNotEmpty()) { "此时区在该时刻发生跳时，请调整时间" }
        val a = start.atZone(zone).toInstant().toEpochMilli(); val b = end.atZone(zone).toInstant().toEpochMilli()
        require(b > a) { "结束时刻必须晚于开始" }; return a to b
    }
}
