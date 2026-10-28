package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.records.RecordsViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanMobileDetailLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val vm get()=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
    private fun ready() {
        compose.waitUntil(30_000) {vm.ui.value.ready && !vm.ui.value.busy && ViewModelProvider(compose.activity)[RecordsViewModel::class.java].ui.value.ready}
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("dismissMessage").performClick()
    }
    @Before fun setup() {ready();runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))};compose.waitForIdle()}
    @After fun close() {compose.activity.finish();app.database.close();app.backgroundScope.cancel()}
    private fun save(form:PlanForm) {
        runBlocking {app.plans.prepareDraft(form);app.plans.save(form)}
        // A completed Room write can precede its observable snapshot reaching the ViewModel.
        compose.waitUntil(10_000) {
            val data=vm.ui.value.data
            when(form.kind) {
                "task"->data.tasks.any {it.id==form.id && it.revision==form.revision+1} &&
                    (form.text("repeat","none")=="none" || data.occurrences.count {it.entityType=="task" && it.entityId==form.id}>=form.text("count","1").toInt())
                "habit"->data.habits.any {it.id==form.id && it.revision==form.revision+1}
                "goal"->data.goals.any {it.id==form.id && it.revision==form.revision+1}
                else->false
            }
        }
        ready()
    }
    private fun open(kind:String,id:String,instance:String?=null) {compose.runOnIdle {vm.detail(kind,id,instance)};ready()}
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("dailyLife.planReviewDir","build/reports/plan-mobile-review"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    private fun menu() {compose.onNodeWithTag("planDetailMenu").performClick();compose.waitForIdle()}

    @Test fun goalIsReadOnlyUntilARecordOrMenuIsOpenedAndLogUpdatesInPlace() {
        val form=PlanForm("goal").set("title","读完喜欢的四本书").set("target","4").set("unit","本");save(form)
        runBlocking {app.plans.goalLog(form.id,LocalDate.now().toString(),"1","每晚读一点")};ready();open("goal",form.id)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("编辑",substring=false).assertDoesNotExist()
        compose.onNodeWithText("删除此条").assertDoesNotExist()
        compose.onNodeWithTag("planPrimaryAction").assertIsDisplayed();shot("detail-mobile-goal")
        val log=vm.ui.value.data.progress.single {it.goalId==form.id}
        compose.onNodeWithTag("planDetailList").performScrollToNode(hasTestTag("planLog:"+log.id))
        compose.onNodeWithTag("planLog:"+log.id).performClick()
        compose.onNodeWithTag("editPlanLog").performClick()
        compose.onNodeWithTag("planLogValue").performTextReplacement("2")
        compose.onNodeWithTag("savePlanLog").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.progress.single {it.id==log.id}.deltaMilli==2000L}
        assertEquals(1,vm.ui.value.data.progress.count {it.goalId==form.id})
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        menu();compose.onNodeWithTag("editPlanDetail").performClick();ready()
        assertEquals(form.id,vm.ui.value.editor?.id)
    }

    @Test fun linkedSourcesCommitOnlyOnConfirmAndBackReturnsToTheOriginalGoal() {
        val first=PlanForm("task").set("title","确认出行日期");val second=PlanForm("task").set("title","预订住宿")
        save(first);save(second)
        val goal=PlanForm("goal").set("title","准备一趟短途旅行").set("source","tasks").set("sources",first.id);save(goal);ready();open("goal",goal.id)
        menu();compose.onNodeWithTag("manageGoalSources").performClick()
        compose.onNodeWithTag("selectionOption:"+second.id).performClick()
        compose.onNodeWithTag("pickerCancel").performClick();ready()
        assertEquals(setOf(first.id),vm.ui.value.data.goalLinks.filter {it.goalId==goal.id}.map {it.sourceId}.toSet())
        menu();compose.onNodeWithTag("manageGoalSources").performClick()
        compose.onNodeWithTag("selectionOption:"+second.id).performClick()
        compose.onNodeWithTag("pickerConfirm").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.goalLinks.count {it.goalId==goal.id}==2}
        compose.onNodeWithTag("planDetailList").performScrollToNode(hasTestTag("planLinkedSource:"+second.id))
        compose.onNodeWithTag("planLinkedSource:"+second.id).performClick();ready()
        assertEquals(second.id,vm.ui.value.detailId)
        compose.onNodeWithTag("backPlanDetail").performClick();ready()
        assertEquals(goal.id,vm.ui.value.detailId);assertEquals("goal",vm.ui.value.detailKind)
        shot("detail-mobile-linked-goal")
    }

    @Test fun repeatedInstanceHasNoInlineEditorAndRenameChangesOnlyConfirmedInstance() {
        val task=PlanForm("task").set("title","早晨散步").set("planned",LocalDate.now().toString()).set("repeat","daily").set("count","2");save(task);ready()
        val rows=vm.ui.value.data.occurrences.filter {it.entityType=="task" && it.entityId==task.id}.sortedBy {it.originalKey}
        assertEquals(2,rows.size)
        open("task",task.id,rows.first().id)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        shot("detail-mobile-repeat-instance")
        menu();compose.onNodeWithTag("renamePlanInstance").performScrollTo().performClick()
        compose.onNodeWithTag("planInstanceName").performTextReplacement("未确认的名称")
        compose.onNodeWithText("关闭",substring=false).performClick();ready()
        assertEquals(rows.first().title,vm.ui.value.data.occurrences.first {it.id==rows.first().id}.title)
        menu();compose.onNodeWithTag("renamePlanInstance").performScrollTo().performClick()
        compose.onNodeWithTag("planInstanceName").performTextReplacement("去公园散步")
        compose.onNodeWithTag("savePlanInstanceName").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.occurrences.first {it.id==rows.first().id}.title=="去公园散步"}
        assertEquals(rows.last().title,vm.ui.value.data.occurrences.first {it.id==rows.last().id}.title)
        assertEquals("早晨散步",vm.ui.value.data.tasks.first {it.id==task.id}.title)
    }

    @Test fun habitHistoryUsesAnActionSheetInsteadOfRepeatedEditingButtons() {
        val habit=PlanForm("habit").set("title","每天读几页书");save(habit)
        runBlocking {app.plans.habitLog(habit.id,LocalDate.now().toString(),"1","喜欢这一章")};ready();open("habit",habit.id)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("编辑",substring=false).assertDoesNotExist()
        val log=vm.ui.value.data.logs.first {it.habitId==habit.id}
        compose.onNodeWithTag("planDetailList").performScrollToNode(hasTestTag("planLog:"+log.id))
        compose.onNodeWithTag("planLog:"+log.id).performClick()
        compose.onNodeWithTag("editPlanLog").assertIsDisplayed();compose.onNodeWithTag("deletePlanLog").assertIsDisplayed()
        compose.onNodeWithText("关闭",substring=false).performClick();shot("detail-mobile-habit")
    }

    @Test fun durationHabitStartsAtItsDailyTargetAndPreservesExistingLogValues() {
        val habit=PlanForm("habit").set("title","晚间阅读").set("tracking","duration").set("target","1830.5")
        save(habit);ready();open("habit",habit.id)
        compose.onNodeWithTag("planPrimaryAction").performClick()
        compose.onNodeWithTag("planLogValue").performClick()
        compose.onNodeWithTag("durationMinutes:planLogValue").assert(hasText("30"))
        compose.onNodeWithTag("durationSeconds:planLogValue").assert(hasText("30.5"))
        compose.onNodeWithTag("confirmDuration:planLogValue").performClick()
        compose.onNodeWithTag("savePlanLog").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.logs.any {it.habitId==habit.id}}
        val log=vm.ui.value.data.logs.single {it.habitId==habit.id}
        assertEquals(1_830_500L,log.valueMilli)
        runBlocking {app.plans.habitLog(habit.id,log.logDate,"61.125","保留补记",log.id)}
        compose.waitUntil(10_000) {vm.ui.value.data.logs.single {it.id==log.id}.valueMilli==61_125L}
        compose.onNodeWithTag("planDetailList").performScrollToNode(hasTestTag("planLog:"+log.id))
        compose.onNodeWithTag("planLog:"+log.id).performClick()
        compose.onNodeWithTag("editPlanLog").performClick()
        compose.onNodeWithTag("planLogValue").performClick()
        compose.onNodeWithTag("durationMinutes:planLogValue").assert(hasText("1"))
        compose.onNodeWithTag("durationSeconds:planLogValue").assert(hasText("1.125"))
    }

    @Test fun archivedAndDeletedGoalsRemainReadOnlyUntilRestored() {
        val goal=PlanForm("goal").set("title","整理阅读清单").set("target","3");save(goal)
        runBlocking {app.plans.manage("goal",goal.id,"archive")}
        compose.waitUntil(10_000) {vm.ui.value.data.goals.any {it.id==goal.id && it.archivedAt!=null}}
        ready();open("goal",goal.id)
        compose.onNodeWithTag("planPrimaryAction").assertDoesNotExist()
        menu();compose.onNodeWithTag("editPlanDetail").assertDoesNotExist()
        compose.onNodeWithTag("unarchivePlanDetail").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.goals.first {it.id==goal.id}.archivedAt==null}
        compose.onNodeWithTag("planPrimaryAction").assertExists()
        runBlocking {app.plans.manage("goal",goal.id,"trash")}
        compose.waitUntil(10_000) {vm.ui.value.data.goals.any {it.id==goal.id && it.deletedAt!=null}}
        ready()
        compose.onNodeWithTag("planPrimaryAction").assertDoesNotExist()
        menu();compose.onNodeWithTag("restorePlanDetail").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.goals.first {it.id==goal.id}.deletedAt==null}
        compose.onNodeWithTag("planPrimaryAction").assertExists()
    }

    @Test fun milestoneManagementAppearsOnlyOnRequestAndKeepsItsIdentity() {
        val goal=PlanForm("goal").set("title","完成学习计划").set("source","milestones").set("milestones","完成第一章");save(goal);ready();open("goal",goal.id)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        val milestone=vm.ui.value.data.milestones.single {it.goalId==goal.id}
        compose.onNodeWithTag("planDetailList").performScrollToNode(hasTestTag("planMilestone:"+milestone.id))
        compose.onNodeWithContentDescription("完成里程碑 ${milestone.title}").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.milestones.single {it.id==milestone.id}.completedAt!=null}
        compose.onNodeWithContentDescription("管理里程碑 ${milestone.title}").performClick()
        compose.onNodeWithTag("editPlanMilestone").performClick()
        compose.onNodeWithTag("milestoneName").performTextReplacement("读完第一章并整理笔记")
        compose.onNodeWithTag("saveMilestone").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.milestones.single {it.id==milestone.id}.title=="读完第一章并整理笔记"}
        assertEquals(1,vm.ui.value.data.milestones.count {it.goalId==goal.id})
    }

    @Test fun durationProgressCanDeductTimeWithoutLosingPrecision() {
        val goal=PlanForm("goal").set("title","记录有效阅读时间").set("goalType","duration").set("target","7200");save(goal);ready();open("goal",goal.id)
        compose.onNodeWithTag("planPrimaryAction").performClick()
        compose.onNodeWithTag("planLogValue").performClick()
        compose.onNodeWithText("扣减",substring=false).performClick()
        compose.onNodeWithTag("durationHours:planLogValue").performTextReplacement("0")
        compose.onNodeWithTag("durationMinutes:planLogValue").performTextReplacement("1")
        compose.onNodeWithTag("durationSeconds:planLogValue").performTextReplacement("1.125")
        compose.onNodeWithTag("confirmDuration:planLogValue").performClick()
        compose.onNodeWithTag("savePlanLog").performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.progress.any {it.goalId==goal.id}}
        assertEquals(-61125L,vm.ui.value.data.progress.single {it.goalId==goal.id}.deltaMilli)
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontDetailKeepsPrimaryAndLongHistoryReachable() {
        val goal=PlanForm("goal").set("title","每天留一点时间阅读与思考，慢慢完成我的长期目标").set("target","100");save(goal)
        runBlocking {repeat(50) {index->app.plans.goalLog(goal.id,LocalDate.now().minusDays(index.toLong()).toString(),"1","记录 $index")};app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ready();open("goal",goal.id)
        assertTrue(compose.onAllNodes(SemanticsMatcher("log rows") {it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("planLog:")==true},useUnmergedTree=true).fetchSemanticsNodes().size<50)
        shot("detail-mobile-goal-large-font")
        compose.onNodeWithTag("planDetailList").performScrollToNode(hasTestTag("planPrimaryAction"))
        compose.onNodeWithTag("planPrimaryAction").performClick()
        compose.onNodeWithTag("savePlanLog").assertIsDisplayed()
        compose.onNodeWithText("关闭",substring=false).performClick()
        val oldest=vm.ui.value.data.progress.filter {it.goalId==goal.id}.minBy {it.logDate}
        compose.onNodeWithTag("planDetailList").performScrollToNode(hasTestTag("planLog:"+oldest.id))
        compose.onNodeWithTag("planLog:"+oldest.id).assertIsDisplayed()
    }
}
