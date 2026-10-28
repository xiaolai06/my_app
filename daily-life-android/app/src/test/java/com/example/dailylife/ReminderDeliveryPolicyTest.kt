package com.example.dailylife

import com.example.dailylife.core.ReminderDeliveryPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId

class ReminderDeliveryPolicyTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val trigger=ZonedDateTime.parse("2026-10-26T09:00:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
    @Test fun acceptsOrdinaryInexactAlarmDelays() {
        for(minutes in listOf(0,20,60,180)) for(type in listOf("task","event","tool","habit"))
            assertTrue(ReminderDeliveryPolicy.eligible(trigger,trigger+minutes*60000L,type,zone))
    }
    @Test fun rejectsEarlyAndAncientAlarms() {
        assertFalse(ReminderDeliveryPolicy.eligible(trigger,trigger-1,"task",zone))
        assertFalse(ReminderDeliveryPolicy.eligible(trigger,trigger+ReminderDeliveryPolicy.MAX_DELAY_MILLIS+1,"task",zone))
    }
    @Test fun yesterdayHabitDoesNotCountAsTodayReminder() {
        val late=ZonedDateTime.parse("2026-10-26T23:50:00+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
        assertFalse(ReminderDeliveryPolicy.eligible(late,late+20*60000L,"habit",zone))
        assertTrue(ReminderDeliveryPolicy.eligible(late,late+20*60000L,"task",zone))
    }
}
