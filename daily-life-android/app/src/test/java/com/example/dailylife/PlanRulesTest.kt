package com.example.dailylife
import com.example.dailylife.core.PlanRules
import com.example.dailylife.data.local.RepeatRule
import org.junit.Test
import org.junit.Assert.*
import java.time.*
class PlanRulesTest {
    private fun rule(anchor:String,freq:String="monthly",policy:String="last",count:Int?=null)=
        RepeatRule(ownerId="owner",entityType="task",entityId="task",anchorDate=anchor,frequency=freq,invalidPolicy=policy,count=count)
    @Test fun monthEndNeverDriftsAfterFebruary() {
        assertEquals(listOf("2024-01-31","2024-02-29","2024-03-31","2024-04-30"),
            PlanRules.dates(rule("2024-01-31"),LocalDate.parse("2024-04-30")).map {it.toString()})
    }
    @Test fun skippedNonexistentDatesDoNotConsumeCount() {
        assertEquals(listOf("2026-01-31","2026-03-31","2026-05-31"),
            PlanRules.dates(rule("2026-01-31",policy="skip",count=3),LocalDate.parse("2027-01-01")).map {it.toString()})
    }
    @Test fun leapDayPoliciesRetainOriginalAnchor() {
        val end=LocalDate.parse("2028-03-02")
        assertEquals(listOf("2024-02-29","2025-02-28","2026-02-28","2027-02-28","2028-02-29"),
            PlanRules.dates(rule("2024-02-29","yearly"),end).map {it.toString()})
        assertEquals("2025-03-01",PlanRules.dates(rule("2024-02-29","yearly","march"),end)[1].toString())
        assertEquals(listOf("2024-02-29","2028-02-29"),PlanRules.dates(rule("2024-02-29","yearly","skip"),end).map {it.toString()})
    }
    @Test fun alternateWeeksUseAnchorWeekAndChosenWeekdays() {
        val r=rule("2026-10-21","weekly").copy(interval=2,weekdays="1,5")
        assertEquals(listOf("2026-10-23","2026-11-02","2026-11-06"),
            PlanRules.dates(r,LocalDate.parse("2026-11-08")).map {it.toString()})
    }
    @Test fun stopAndEndDateBoundPreviewEvenWhenMinimumRequested() {
        val r=rule("2026-10-25","daily").copy(stoppedFrom="2026-10-26")
        assertEquals(1,PlanRules.dates(r,LocalDate.parse("2026-10-25"),3).size)
        assertEquals(0,PlanRules.dates(r.copy(stoppedFrom="2026-10-25"),LocalDate.now(),3).size)
    }
    @Test fun annualNextInstanceBeyondNinetyDaysIsStillAvailable() {
        assertEquals(1,PlanRules.dates(rule("2028-02-29","yearly"),LocalDate.parse("2026-10-25"),1).size)
    }
    @Test fun futurePreviewRetainsCountAndSkipsAncientPeriodsEfficiently() {
        assertEquals(listOf("2026-05-31"),PlanRules.dates(rule("2026-01-31",count=5),LocalDate.parse("2026-05-01"),3,LocalDate.parse("2026-05-01")).map {it.toString()})
        assertEquals(listOf("2026-10-25","2026-10-26","2026-10-27"),PlanRules.dates(rule("0001-01-01","daily"),LocalDate.parse("2026-10-25"),3,LocalDate.parse("2026-10-25")).map {it.toString()})
    }
    @Test fun exactFixedPointRejectsSilentRounding() {
        assertEquals(1234567L,PlanRules.milli("1234.567"));assertEquals("0.01",PlanRules.display(10))
        assertTrue(runCatching {PlanRules.milli("0.0001")}.isFailure)
        assertTrue(runCatching {PlanRules.milli("9223372036854776")}.isFailure)
    }
    @Test fun dateOnlyDueMeansEndOfThatDay() {
        assertFalse(PlanRules.overdue("2026-10-25",null,LocalDateTime.parse("2026-10-25T22:00")))
        assertTrue(PlanRules.overdue("2026-10-25",null,LocalDateTime.parse("2026-10-26T00:00")))
    }
    @Test fun timedEventRejectsReversedAndDstGap() {
        val zone=ZoneId.of("America/New_York")
        assertTrue(runCatching {PlanRules.duration(LocalDateTime.parse("2026-03-08T02:30"),LocalDateTime.parse("2026-03-08T04:00"),zone)}.isFailure)
        assertTrue(runCatching {PlanRules.duration(LocalDateTime.parse("2026-10-25T12:00"),LocalDateTime.parse("2026-10-25T11:00"),zone)}.isFailure)
        val (a,b)=PlanRules.duration(LocalDateTime.parse("2026-03-08T01:30"),LocalDateTime.parse("2026-03-08T03:30"),zone)
        assertEquals(3600000L,b-a)
    }
}
