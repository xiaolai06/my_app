package com.example.dailylife

import com.example.dailylife.data.local.Occurrence
import com.example.dailylife.data.local.PlanEvent
import com.example.dailylife.data.local.RepeatRule
import com.example.dailylife.data.local.Task
import com.example.dailylife.feature.plans.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlanAgendaTest {
    private fun day(value: String) = LocalDate.parse(value)
    private fun task(id: String, planned: String? = null, due: String? = null, status: String = "todo") =
        Task(id = id, ownerId = "test", title = id, plannedDate = planned, dueDate = due, status = status)
    private fun event(id: String, start: String, until: String, allDay: Boolean = true, time: String = "09:00") =
        PlanEvent(id = id, ownerId = "test", title = id, startDate = start, endDateExclusive = until,
            allDay = allDay, startTime = time, zoneId = "Asia/Shanghai")
    private fun rule(id: String, kind: String, source: String, anchor: String) =
        RepeatRule(id = id, ownerId = "test", entityType = kind, entityId = source, anchorDate = anchor)
    private fun instance(id: String, rule: String, kind: String, source: String, original: String,
                         scheduled: String = original, status: String = "todo") =
        Occurrence(id = id, ruleId = rule, entityType = kind, entityId = source,
            originalKey = original, scheduledDate = scheduled, status = status)

    @Test fun plannedAndDueDatesMatchWithoutDuplicatingTheSameTask() {
        val snapshot = PlansSnapshot(tasks = listOf(
            task("both", "2026-10-28", "2026-10-28"),
            task("twoDays", "2026-10-28", "2026-10-30"),
            task("undated")
        ))
        assertEquals(setOf("both", "twoDays"), snapshot.agendaOn(day("2026-10-28")).tasks.map { it.key }.toSet())
        assertEquals(2, snapshot.agendaOn(day("2026-10-28")).count)
        assertEquals(listOf("twoDays"), snapshot.agendaOn(day("2026-10-30")).tasks.map { it.key })
        assertTrue(snapshot.agendaOn(day("2026-10-29")).isEmpty)
        assertEquals(setOf("2026-10-28", "2026-10-30"), snapshot.agendaDates(YearMonth.of(2026, 10)))
    }

    @Test fun completedAndCancelledRecordsStayInTheirOwnDayCategories() {
        val snapshot = PlansSnapshot(tasks = listOf("todo", "doing", "done", "cancelled", "skipped").map {
            task(it, "2026-10-28", status = it)
        })
        val agenda = snapshot.agendaOn(day("2026-10-28"))
        assertEquals(listOf("doing", "todo"), agenda.pendingTasks.map { it.status })
        assertEquals(listOf("done"), agenda.completedTasks.map { it.status })
        assertEquals(setOf("cancelled", "skipped"), agenda.cancelledTasks.map { it.status }.toSet())
        assertEquals(5, agenda.count)
        assertEquals(setOf("2026-10-28"), snapshot.agendaDates(YearMonth.of(2026, 10)))
    }

    @Test fun movedRepeatTaskUsesInstanceDateAndShiftedDeadlineInsteadOfTemplateDate() {
        val source = task("repeat", "2026-10-01", "2026-10-03", "done")
        val first = instance("first", "r", "task", source.id, "2026-10-01", "2026-10-28", "todo")
        val second = instance("second", "r", "task", source.id, "2026-10-02", "2026-10-28", "done")
        val snapshot = PlansSnapshot(tasks = listOf(source), rules = listOf(rule("r", "task", source.id, "2026-10-01")),
            occurrences = listOf(first, second))
        assertTrue(snapshot.agendaOn(day("2026-10-01")).isEmpty)
        assertTrue(snapshot.agendaOn(day("2026-10-03")).isEmpty)
        assertEquals(listOf("first"), snapshot.agendaOn(day("2026-10-28")).pendingTasks.map { it.key })
        assertEquals(listOf("second"), snapshot.agendaOn(day("2026-10-28")).completedTasks.map { it.key })
        assertEquals(setOf("first", "second"), snapshot.agendaOn(day("2026-10-30")).tasks.map { it.key }.toSet())
        assertEquals(setOf("2026-10-28", "2026-10-30"), snapshot.agendaDates(YearMonth.of(2026, 10)))
    }

    @Test fun crossMonthEventMarksEveryIncludedDateAndHonorsExclusiveEnd() {
        val snapshot = PlansSnapshot(events = listOf(event("trip", "2026-10-30", "2026-11-03")))
        assertEquals(listOf("trip"), snapshot.agendaOn(day("2026-11-02")).events.map { it.key })
        assertTrue(snapshot.agendaOn(day("2026-11-03")).isEmpty)
        assertEquals(setOf("2026-10-30", "2026-10-31"), snapshot.agendaDates(YearMonth.of(2026, 10)))
        assertEquals(setOf("2026-11-01", "2026-11-02"), snapshot.agendaDates(YearMonth.of(2026, 11)))
    }

    @Test fun movedRepeatEventKeepsItsDurationAndCancelledInstanceRemainsVisible() {
        val source = event("repeat", "2026-10-01", "2026-10-03")
        val occurrence = instance("moved", "r", "event", source.id, "2026-10-01", "2026-10-31", "cancelled")
            .copy(title = "本次出行")
        val snapshot = PlansSnapshot(events = listOf(source), rules = listOf(rule("r", "event", source.id, "2026-10-01")),
            occurrences = listOf(occurrence))
        assertTrue(snapshot.agendaOn(day("2026-10-01")).isEmpty)
        val agenda = snapshot.agendaOn(day("2026-11-01"))
        assertEquals(1, agenda.count)
        assertEquals("本次出行", agenda.events.single().title)
        assertEquals("cancelled", agenda.events.single().status)
        assertEquals(day("2026-10-31"), agenda.events.single().startDate)
        assertEquals(day("2026-11-02"), agenda.events.single().endDateExclusive)
        assertEquals(setOf("2026-11-01"), snapshot.agendaDates(YearMonth.of(2026, 11)))
        assertTrue(snapshot.agendaOn(day("2026-11-02")).isEmpty)
    }

    @Test fun deletedAndArchivedSourcesAndWrongOccurrenceTypesNeverAppear() {
        val snapshot = PlansSnapshot(
            tasks = listOf(task("deletedTask", "2026-10-28").copy(deletedAt = 1),
                task("archivedTask", "2026-10-28").copy(archivedAt = 1)),
            events = listOf(event("deletedEvent", "2026-10-28", "2026-10-29").copy(deletedAt = 1),
                event("archivedEvent", "2026-10-28", "2026-10-29").copy(archivedAt = 1),
                event("visibleEvent", "2026-10-01", "2026-10-02")),
            rules = listOf(rule("r1", "event", "deletedEvent", "2026-10-28"),
                rule("r2", "event", "visibleEvent", "2026-10-01")),
            occurrences = listOf(instance("deletedInstance", "r1", "event", "deletedEvent", "2026-10-28"),
                instance("wrongType", "r2", "task", "visibleEvent", "2026-10-28"),
                instance("orphan", "unknown", "event", "missing", "2026-10-28"))
        )
        assertTrue(snapshot.agendaOn(day("2026-10-28")).isEmpty)
        assertTrue(snapshot.agendaDates(YearMonth.of(2026, 10)).isEmpty())
    }

    @Test fun markersStayInsideMonthEvenForLongEventsAndRepeatedDueOnlyTasks() {
        val dueOnly = task("dueOnly", due = "2026-09-01")
        val snapshot = PlansSnapshot(tasks = listOf(dueOnly),
            events = listOf(event("long", "2000-01-01", "2030-01-01")),
            rules = listOf(rule("r", "task", dueOnly.id, "2026-09-01")),
            occurrences = listOf(instance("february", "r", "task", dueOnly.id, "2027-02-28")))
        val days = snapshot.agendaDates(YearMonth.of(2027, 2))
        assertEquals(28, days.size)
        assertTrue(days.all { it.startsWith("2027-02-") })
        val agenda = snapshot.agendaOn(day("2027-02-28"))
        assertEquals(listOf("february"), agenda.tasks.map { it.key })
        assertEquals(2, agenda.count)
    }

    @Test fun dailyEventsAreOrderedAllDayThenByStartTimeWithCancelledLast() {
        val snapshot = PlansSnapshot(events = listOf(
            event("late", "2026-10-28", "2026-10-29", false, "18:00"),
            event("cancelled", "2026-10-28", "2026-10-29").copy(status = "cancelled"),
            event("early", "2026-10-28", "2026-10-29", false, "08:00"),
            event("allDay", "2026-10-28", "2026-10-29")
        ))
        assertEquals(listOf("allDay", "early", "late", "cancelled"),
            snapshot.agendaOn(day("2026-10-28")).events.map { it.key })
    }
}
