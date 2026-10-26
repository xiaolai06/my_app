package com.example.dailylife.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.AutoMigration

@Database(entities = [LocalProfile::class, RecordType::class, Folder::class, Tag::class, Record::class,
    Note::class, Diary::class, Bookmark::class, FileRecord::class, CardField::class, EntityTag::class,
    Attachment::class, AttachmentLink::class, Draft::class, ContentTemplate::class,
    Task::class, Subtask::class, TaskGroup::class, PlanEvent::class, RepeatRule::class, Occurrence::class,
    Habit::class, HabitRuleVersion::class, HabitLog::class, HabitPause::class,
    FocusSession::class, FocusSegment::class, Goal::class, GoalProgress::class, GoalSourceLink::class,
    Milestone::class, PlanDraft::class, PlanReminder::class,
    Ledger::class,LedgerCategory::class,MoneyAccount::class,LedgerTransaction::class,AccountEntry::class,LedgerDraft::class,
    TransactionTemplate::class,MonthlyBudget::class,BudgetAlert::class,ReimbursementClaim::class,ReimbursementItem::class,
    LedgerChangeLog::class,LedgerImportBatch::class,LedgerImportRow::class,
    ToolEntry::class,ToolChild::class,ToolLog::class,ToolLink::class,ToolDraft::class,ToolCalculation::class,
    ToolPaymentPeriod::class,ToolPaymentSettlement::class,FeaturePreference::class], version = 3, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2),AutoMigration(from = 2,to = 3)])
abstract class DailyLifeDatabase : RoomDatabase() {
    abstract fun records(): RecordsDao
    abstract fun plans(): PlansDao
    abstract fun ledger(): LedgerDao
    abstract fun tools(): ToolsDao
}
