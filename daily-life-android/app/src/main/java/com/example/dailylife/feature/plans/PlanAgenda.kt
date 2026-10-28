package com.example.dailylife.feature.plans

import com.example.dailylife.data.local.Occurrence
import com.example.dailylife.data.local.PlanEvent
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** A projection keeps the original business identity, including a moved repeat instance. */
internal data class PlanAgendaEvent(val event: PlanEvent, val occurrence: Occurrence? = null) {
    val key: String get() = occurrence?.id ?: event.id
    val title: String get() = occurrence?.title ?: event.title
    val status: String get() = occurrence?.status ?: event.status
    val startDate: LocalDate get() = LocalDate.parse(occurrence?.scheduledDate ?: event.startDate)
    val endDateExclusive: LocalDate get() = startDate.plusDays(
        ChronoUnit.DAYS.between(LocalDate.parse(event.startDate), LocalDate.parse(event.endDateExclusive))
    )

    fun occursOn(date: LocalDate): Boolean = date >= startDate && date < endDateExclusive
}

internal data class PlanDayAgenda(
    val date: LocalDate,
    val tasks: List<TaskRow>,
    val events: List<PlanAgendaEvent>
) {
    val pendingTasks: List<TaskRow> get() = tasks.filter { it.status in setOf("todo", "doing") }
    val completedTasks: List<TaskRow> get() = tasks.filter { it.status == "done" }
    val cancelledTasks: List<TaskRow> get() = tasks.filter { it.status in setOf("cancelled", "skipped") }
    val count: Int get() = tasks.size + events.size
    val isEmpty: Boolean get() = count == 0
}

/** Both planned and due dates are meaningful; matching both still produces one row. */
internal fun PlansSnapshot.agendaOn(date: LocalDate): PlanDayAgenda {
    val day = date.toString()
    val taskRows = taskRows().filter { it.date == day || it.due == day }.distinctBy { it.key }
        .sortedWith(compareBy<TaskRow> { taskStatusOrder(it.status) }
            .thenByDescending { it.task.priority }
            .thenBy { it.task.dueTime ?: "99:99" }
            .thenBy { it.title }
            .thenBy { it.key })
    val eventRows = agendaEvents().filter { it.occursOn(date) }
        .sortedWith(compareBy<PlanAgendaEvent> { it.status == "cancelled" }
            .thenBy { !it.event.allDay }
            .thenBy { it.event.startTime }
            .thenBy { it.title }
            .thenBy { it.key })
    return PlanDayAgenda(date, taskRows, eventRows)
}

/** Month markers use exactly the same records as the selected-day list. */
internal fun PlansSnapshot.agendaDates(month: YearMonth): Set<String> = buildSet {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    taskRows().forEach { row ->
        listOfNotNull(row.date, row.due).forEach { value ->
            val date = LocalDate.parse(value)
            if (date in first..last) add(value)
        }
    }
    agendaEvents().forEach { row ->
        // Clamp before walking a cross-day span; long events never scan outside this month.
        var date = maxOf(first, row.startDate)
        val until = minOf(last, row.endDateExclusive.minusDays(1))
        while (date <= until) {
            add(date.toString())
            date = date.plusDays(1)
        }
    }
}

private fun taskStatusOrder(status: String): Int = when (status) {
    "doing" -> 0
    "todo" -> 1
    "done" -> 2
    else -> 3
}

private fun PlansSnapshot.agendaEvents(): List<PlanAgendaEvent> {
    val repeated = rules.filter { it.entityType == "event" }.map { it.entityId }.toSet()
    val instances = occurrences.filter { it.entityType == "event" }.groupBy { it.entityId }
    return events.filter { it.deletedAt == null && it.archivedAt == null }.flatMap { event ->
        if (event.id in repeated) instances[event.id].orEmpty().map { PlanAgendaEvent(event, it) }
        else listOf(PlanAgendaEvent(event))
    }.distinctBy { it.key }
}
