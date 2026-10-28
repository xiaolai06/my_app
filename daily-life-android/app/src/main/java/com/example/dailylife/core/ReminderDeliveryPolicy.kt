package com.example.dailylife.core

import java.time.Instant
import java.time.ZoneId

/** Inexact alarms may arrive late. Only an existing alarm is delivered; reconciliation never replays a backlog. */
object ReminderDeliveryPolicy {
    const val MAX_DELAY_MILLIS = 24 * 60 * 60 * 1000L
    fun eligible(trigger: Long, now: Long, sourceType: String, zone: ZoneId): Boolean {
        if (trigger > now || now - trigger > MAX_DELAY_MILLIS) return false
        return sourceType != "habit" ||
            Instant.ofEpochMilli(trigger).atZone(zone).toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    }
}
