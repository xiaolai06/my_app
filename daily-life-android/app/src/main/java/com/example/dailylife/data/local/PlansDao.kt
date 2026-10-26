package com.example.dailylife.data.local

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao interface PlansDao {
    @RawQuery(observedEntities = [Task::class, Subtask::class, TaskGroup::class, PlanEvent::class,
        RepeatRule::class, Occurrence::class, Habit::class, HabitRuleVersion::class, HabitLog::class,
        HabitPause::class, FocusSession::class, FocusSegment::class, Goal::class, GoalProgress::class,
        GoalSourceLink::class, Milestone::class, PlanDraft::class, PlanReminder::class])
    fun changes(query: SupportSQLiteQuery): Flow<Int>
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC") suspend fun tasks(): List<Task>
    @Query("SELECT * FROM subtasks ORDER BY sortOrder") suspend fun subtasks(): List<Subtask>
    @Query("SELECT * FROM task_groups ORDER BY sortOrder,name") suspend fun groups(): List<TaskGroup>
    @Query("SELECT * FROM events ORDER BY startDate,startTime") suspend fun events(): List<PlanEvent>
    @Query("SELECT * FROM repeat_rules") suspend fun rules(): List<RepeatRule>
    @Query("SELECT * FROM occurrences ORDER BY scheduledDate") suspend fun occurrences(): List<Occurrence>
    @Query("SELECT * FROM habits") suspend fun habits(): List<Habit>
    @Query("SELECT * FROM habit_rules ORDER BY effectiveFrom") suspend fun habitRules(): List<HabitRuleVersion>
    @Query("SELECT * FROM habit_logs ORDER BY logDate DESC") suspend fun habitLogs(): List<HabitLog>
    @Query("SELECT * FROM habit_pauses") suspend fun pauses(): List<HabitPause>
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC") suspend fun sessions(): List<FocusSession>
    @Query("SELECT * FROM focus_segments ORDER BY startAt") suspend fun segments(): List<FocusSegment>
    @Query("SELECT * FROM goals ORDER BY createdAt DESC") suspend fun goals(): List<Goal>
    @Query("SELECT * FROM goal_progress ORDER BY logDate DESC") suspend fun progress(): List<GoalProgress>
    @Query("SELECT * FROM goal_links") suspend fun goalLinks(): List<GoalSourceLink>
    @Query("SELECT * FROM milestones ORDER BY sortOrder") suspend fun milestones(): List<Milestone>
    @Query("SELECT * FROM plan_drafts ORDER BY savedAt DESC") suspend fun drafts(): List<PlanDraft>
    @Query("SELECT * FROM plan_reminders ORDER BY triggerAt") suspend fun reminders(): List<PlanReminder>
    @Upsert suspend fun put(value: Task)
    @Upsert suspend fun put(value: Subtask)
    @Upsert suspend fun put(value: TaskGroup)
    @Upsert suspend fun put(value: PlanEvent)
    @Upsert suspend fun put(value: RepeatRule)
    @Upsert suspend fun put(value: Occurrence)
    @Upsert suspend fun put(value: Habit)
    @Upsert suspend fun put(value: HabitRuleVersion)
    @Upsert suspend fun put(value: HabitLog)
    @Upsert suspend fun put(value: HabitPause)
    @Upsert suspend fun put(value: FocusSession)
    @Upsert suspend fun put(value: FocusSegment)
    @Upsert suspend fun put(value: Goal)
    @Upsert suspend fun put(value: GoalProgress)
    @Upsert suspend fun put(value: GoalSourceLink)
    @Upsert suspend fun put(value: Milestone)
    @Upsert suspend fun put(value: PlanDraft)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: PlanReminder): Long
    @Query("DELETE FROM subtasks WHERE taskId=:id") suspend fun removeSubtasks(id: String)
    @Query("DELETE FROM plan_drafts WHERE id=:id") suspend fun removeDraft(id: String)
    @Query("DELETE FROM task_groups WHERE id=:id") suspend fun removeGroup(id: String)
    @Query("DELETE FROM habit_logs WHERE id=:id") suspend fun removeHabitLog(id: String)
    @Query("DELETE FROM goal_progress WHERE id=:id") suspend fun removeProgress(id: String)
    @Query("DELETE FROM goal_links WHERE goalId=:id AND sourceId=:source") suspend fun unlink(id: String, source: String)
    @Query("DELETE FROM milestones WHERE id=:id") suspend fun removeMilestone(id: String)
}
