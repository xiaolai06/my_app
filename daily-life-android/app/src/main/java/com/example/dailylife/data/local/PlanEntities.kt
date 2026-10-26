package com.example.dailylife.data.local

import androidx.room.*

@Entity(tableName = "task_groups", indices = [Index(value = ["ownerId", "normalizedName"], unique = true)])
data class TaskGroup(@PrimaryKey val id: String = newId(), val ownerId: String, val name: String,
    val normalizedName: String, val sortOrder: Int = 0, val archivedAt: Long? = null)
@Entity(tableName = "tasks", indices = [Index("ownerId"), Index("plannedDate"), Index("dueDate"), Index("groupId")])
data class Task(@PrimaryKey val id: String = newId(), val ownerId: String, val title: String,
    val note: String = "", val groupId: String? = null, val plannedDate: String? = null,
    val dueDate: String? = null, val dueTime: String? = null, val priority: Int = 1,
    val status: String = "todo", val completedAt: Long? = null, val reminderAt: Long? = null,
    val sourceRecordId: String? = null, val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt, val revision: Long = 1, val deletedAt: Long? = null, val archivedAt: Long? = null,
    val reminderMinutes: Int? = null)
@Entity(tableName = "subtasks", indices = [Index("taskId")])
data class Subtask(@PrimaryKey val id: String = newId(), val taskId: String, val title: String,
    val completedAt: Long? = null, val sortOrder: Int = 0)
@Entity(tableName = "events", indices = [Index("ownerId"), Index("startDate")])
data class PlanEvent(@PrimaryKey val id: String = newId(), val ownerId: String, val title: String,
    val allDay: Boolean = false, val startDate: String, val endDateExclusive: String,
    val startTime: String = "09:00", val endTime: String = "10:00", val zoneId: String,
    val startUtc: Long? = null, val endUtc: Long? = null, val location: String = "", val note: String = "",
    val taskId: String? = null, val reminderMinutes: Int? = null, val status: String = "scheduled",
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val revision: Long = 1, val deletedAt: Long? = null, val archivedAt: Long? = null)
@Entity(tableName = "repeat_rules", indices = [Index(value = ["entityType", "entityId"])])
data class RepeatRule(@PrimaryKey val id: String = newId(), val ownerId: String, val entityType: String, val entityId: String,
    val anchorDate: String, val frequency: String = "daily", val interval: Int = 1,
    val weekdays: String = "1,2,3,4,5", val endDate: String? = null, val count: Int? = null,
    val invalidPolicy: String = "last", val stoppedFrom: String? = null)
@Entity(tableName = "occurrences", indices = [Index(value = ["ruleId", "originalKey"], unique = true), Index("entityId")])
data class Occurrence(@PrimaryKey val id: String = newId(), val ruleId: String, val entityType: String,
    val entityId: String, val originalKey: String, val scheduledDate: String, val title: String? = null,
    val status: String = "todo", val completedAt: Long? = null, val overrideVersion: Int = 1,
    val subtaskStates: String = "")
@Entity(tableName = "habits", indices = [Index("ownerId")])
data class Habit(@PrimaryKey val id: String = newId(), val ownerId: String, val name: String,
    val trackingType: String = "once", val startDate: String, val endDate: String? = null,
    val status: String = "active", val note: String = "", val reminderTime: String? = null,
    val color: Int = 0, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val revision: Long = 1, val deletedAt: Long? = null, val archivedAt: Long? = null)
@Entity(tableName = "habit_rules", indices = [Index(value = ["habitId", "effectiveFrom"], unique = true)])
data class HabitRuleVersion(@PrimaryKey val id: String = newId(), val habitId: String, val effectiveFrom: String,
    val targetMilli: Long = 1000, val unit: String = "次", val scheduleType: String = "daily",
    val weekdays: String = "1,2,3,4,5", val weeklyDays: Int = 3)
@Entity(tableName = "habit_logs", indices = [Index("habitId")])
data class HabitLog(@PrimaryKey val id: String = newId(), val habitId: String, val logDate: String,
    val valueMilli: Long = 1000, val note: String = "")
@Entity(tableName = "habit_pauses", indices = [Index("habitId")])
data class HabitPause(@PrimaryKey val id: String = newId(), val habitId: String, val startDate: String, val endDate: String? = null)
@Entity(tableName = "focus_sessions", indices = [Index("ownerId")])
data class FocusSession(@PrimaryKey val id: String = newId(), val ownerId: String, val title: String = "专注",
    val mode: String = "pomodoro", val plannedSeconds: Long = 1500, val shortBreak: Long = 300,
    val longBreak: Long = 900, val rounds: Int = 4, val round: Int = 1, val taskId: String? = null,
    val note: String = "", val state: String = "running", val phase: String = "work",
    val startedAt: Long = System.currentTimeMillis(), val endedAt: Long? = null,
    val revision: Long = 1, val deletedAt: Long? = null, val archivedAt: Long? = null)
@Entity(tableName = "focus_segments", indices = [Index("sessionId")])
data class FocusSegment(@PrimaryKey val id: String = newId(), val sessionId: String,
    val phase: String, val startAt: Long, val endAt: Long? = null, val elapsedStart: Long,
    val bootId: Int, val seconds: Long = 0, val correctionReason: String? = null, val originalSeconds: Long? = null,
    val round: Int = 1)
@Entity(tableName = "goals", indices = [Index("ownerId")])
data class Goal(@PrimaryKey val id: String = newId(), val ownerId: String, val name: String,
    val goalType: String = "quantity", val progressSource: String = "manual",
    val targetMilli: Long = 1000, val unit: String = "次", val startDate: String? = null,
    val endDate: String? = null, val note: String = "", val state: String = "active", val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(), val revision: Long = 1,
    val deletedAt: Long? = null, val archivedAt: Long? = null)
@Entity(tableName = "goal_progress", indices = [Index("goalId")])
data class GoalProgress(@PrimaryKey val id: String = newId(), val goalId: String, val logDate: String,
    val deltaMilli: Long, val note: String = "")
@Entity(tableName = "goal_links", primaryKeys = ["goalId", "sourceType", "sourceId"])
data class GoalSourceLink(val goalId: String, val sourceType: String, val sourceId: String)
@Entity(tableName = "milestones", indices = [Index("goalId")])
data class Milestone(@PrimaryKey val id: String = newId(), val goalId: String, val title: String,
    val dueDate: String? = null, val completedAt: Long? = null, val sortOrder: Int = 0)
@Entity(tableName = "plan_drafts")
data class PlanDraft(@PrimaryKey val id: String, val kind: String, val payload: String, val savedAt: Long = System.currentTimeMillis())
@Entity(tableName = "plan_reminders", indices = [Index(value = ["sourceType", "sourceId", "instanceKey"], unique = true)])
data class PlanReminder(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sourceType: String,
    val sourceId: String, val instanceKey: String, val title: String, val triggerAt: Long,
    val state: String = "pending", val postedAt: Long? = null, val readAt: Long? = null)
