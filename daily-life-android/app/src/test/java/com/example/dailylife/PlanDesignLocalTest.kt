package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.ui.planReminderSummary
import com.example.dailylife.ui.taskSection
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanDesignLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val vm get()=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
    @Before fun lightTheme() {ready();runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))};compose.waitForIdle()}
    private fun ready() {
        compose.waitUntil(30_000) {vm.ui.value.ready && !vm.ui.value.busy && ViewModelProvider(compose.activity)[RecordsViewModel::class.java].ui.value.ready}
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("dismissMessage").performClick()
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File("build/reports/plan-design/$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    private fun seed():String=runBlocking {
        val today=LocalDate.now();val owner=app.records.owner()
        val group=TaskGroup(ownerId=owner,name="生活",normalizedName="生活");app.plans.dao.put(group)
        val t=Task(ownerId=owner,title="整理周末出行清单",plannedDate=today.toString(),groupId=group.id,reminderAt=System.currentTimeMillis()+3600000)
        app.plans.dao.put(t)
        app.plans.dao.put(Subtask(taskId=t.id,title="确认车票",completedAt=System.currentTimeMillis()))
        app.plans.dao.put(Subtask(taskId=t.id,title="收拾行李"))
        app.plans.dao.put(Task(ownerId=owner,title="预约年度体检",dueDate=today.minusDays(1).toString(),priority=2))
        app.plans.dao.put(Task(ownerId=owner,title="读完喜欢的那本书",plannedDate=today.plusDays(1).toString()))
        app.plans.dao.put(Task(ownerId=owner,title="整理相册里的回忆"))
        app.plans.dao.put(Task(ownerId=owner,title="给书桌换一束花",status="done",completedAt=System.currentTimeMillis()))
        app.planAlarms.reconcile()
        t.id
    }
    @After fun close() {compose.activity.finish();app.database.close();app.backgroundScope.cancel()}

    @Test fun tasksGroupRealDatesAndCompletionCanBeUndoneFromCompletedView() {
        ready();val id=seed();ready()
        compose.onNodeWithTag("root:计划").performClick()
        listOf("任务","日历","习惯","专注","目标").forEach {compose.onNodeWithTag("planTab:$it").assertIsDisplayed()}
        compose.onNodeWithTag("planSearch").assertDoesNotExist()
        compose.onNodeWithText("管理分组").assertDoesNotExist()
        compose.onNodeWithText("草稿").assertDoesNotExist()
        compose.onNodeWithText("已逾期").assertIsDisplayed()
        compose.onNodeWithText("给书桌换一束花").assertDoesNotExist()
        shot("01-tasks-light")
        compose.onNodeWithTag("planTaskList").performScrollToNode(hasText("整理相册里的回忆"))
        compose.onNodeWithText("待安排").assertIsDisplayed();shot("02-unscheduled")
        compose.onNodeWithTag("planTaskList").performScrollToNode(hasTestTag("completeTask:$id"))
        compose.onNodeWithTag("completeTask:$id").performClick()
        compose.onNodeWithText("保留子任务状态").performClick()
        compose.waitUntil(10_000) {vm.ui.value.data.tasks.first {it.id==id}.status=="done"};ready()
        compose.onNodeWithTag("planViews").performClick();compose.onNodeWithText("已完成",substring=false).performClick()
        compose.onNodeWithTag("completeTask:$id").performScrollTo().performClick()
        compose.waitUntil(10_000) {vm.ui.value.data.tasks.first {it.id==id}.status=="todo"}
        assertNull(vm.ui.value.data.subtasks.first {it.taskId==id && it.title=="收拾行李"}.completedAt)
    }

    @Test fun selectionNeverCompletesTasksAndHiddenSelectionsAreCleared() {
        ready();val id=seed();ready();compose.onNodeWithTag("root:计划").performClick()
        val other=vm.ui.value.data.tasks.first {it.title=="预约年度体检"}.id
        compose.onNodeWithTag("taskCard:$id").performSemanticsAction(SemanticsActions.OnLongClick) {it()}
        compose.onNodeWithTag("completeTask:$other").performClick();ready()
        compose.onNodeWithText("取消 2").assertIsDisplayed()
        assertEquals("todo",vm.ui.value.data.tasks.first {it.id==id}.status)
        assertEquals("todo",vm.ui.value.data.tasks.first {it.id==other}.status)
        shot("14-selection")
        compose.onNodeWithContentDescription("搜索任务").performClick()
        compose.onNodeWithTag("planSearch").performTextInput("读完")
        compose.onNodeWithText("取消 2").assertDoesNotExist()
        compose.onNodeWithText("移分组").assertDoesNotExist()
        compose.onNodeWithTag("planSearch").performTextClearance()
        compose.onNodeWithText("取消 2").assertDoesNotExist()
    }

    @Test fun animatedNavigationKeepsAllLabelsInPlace() {
        ready()
        org.robolectric.util.ReflectionHelpers.callStaticMethod<Unit>(android.animation.ValueAnimator::class.java,"setDurationScale",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType!!,1f))
        runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=false))}
        compose.onNodeWithTag("root:计划").performClick();ready()
        val tabs=listOf("任务","日历","习惯","专注","目标")
        val bounds=tabs.associateWith {compose.onNodeWithTag("planTab:$it").fetchSemanticsNode().boundsInRoot}
        listOf("目标","日历","任务").forEach {selected->
            compose.onNodeWithTag("planTab:$selected").performClick();ready()
            compose.onNodeWithTag("planTab:$selected").assertIsSelected()
            tabs.forEach {name->
                compose.onNodeWithTag("planTab:$name").assertIsDisplayed()
                assertEquals(bounds[name],compose.onNodeWithTag("planTab:$name").fetchSemanticsNode().boundsInRoot)
            }
        }
    }

    @Test fun checklistProgressIsAccurateAndDoesNotCompleteParentAutomatically() {
        ready();val id=seed();ready();compose.onNodeWithTag("root:计划").performClick()
        compose.onNodeWithTag("subtaskProgress:$id",useUnmergedTree=true).assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(.5f,0f..1f)))
        compose.onNodeWithTag("expandSubtasks:$id").performClick()
        val child=vm.ui.value.data.subtasks.first {it.taskId==id && it.completedAt==null}.id
        compose.onNodeWithTag("subtask:$child").performScrollTo().performClick();ready()
        compose.waitUntil(10_000) {vm.ui.value.data.subtasks.first {it.id==child}.completedAt!=null}
        compose.onNodeWithTag("subtaskProgress:$id",useUnmergedTree=true).assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(1f,0f..1f)))
        assertEquals("todo",vm.ui.value.data.tasks.first {it.id==id}.status)
        shot("15-checklist-complete")
        compose.onNodeWithTag("subtask:$child").performScrollTo().performClick();ready()
        compose.onNodeWithTag("subtaskProgress:$id",useUnmergedTree=true).assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(.5f,0f..1f)))
    }

    @Test fun completedFilterWorksFromPendingAndUndatedOmitsCompletedTasks() {
        ready();seed();ready();compose.onNodeWithTag("root:计划").performClick()
        compose.onNodeWithTag("planMenu").performClick();compose.onNodeWithText("筛选任务").performClick()
        compose.onNodeWithText("完成",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("查看结果").performScrollTo().performClick()
        compose.onNodeWithText("给书桌换一束花").assertIsDisplayed()
        compose.onNodeWithTag("planViews").performClick();compose.onNodeWithText("无日期",substring=false).performClick()
        compose.onNodeWithText("整理相册里的回忆").assertIsDisplayed()
        compose.onNodeWithText("给书桌换一束花").assertDoesNotExist()
    }

    @Test fun createReminderShowsPermissionStatusAndEditingPreservesIt() {
        ready();compose.onNodeWithTag("root:计划").performClick()
        compose.onNodeWithTag("addPlan").performClick();ready()
        compose.onNodeWithTag("planTitle").performTextInput("晚间散步")
        compose.onNodeWithTag("editPlanReminder").performClick()
        compose.onNodeWithText("指定提醒日期与时间").performScrollTo().performClick()
        shot("11-date-picker")
        compose.onNodeWithTag("cancelPlanDate").assertIsDisplayed().performClick()
        assertTrue(vm.ui.value.editor!!.text("reminder").isBlank())
        compose.onNodeWithText("指定提醒日期与时间").performScrollTo().performClick()
        compose.onNodeWithTag("confirmPlanDate").assertIsDisplayed().performClick()
        val locale=java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            compose.onNodeWithTag("customReminderTime").performScrollTo().performClick()
            compose.onNodeWithText("晚上8点",substring=false).performScrollTo().performClick()
            shot("12-time-picker")
            compose.onNodeWithText("确定时间",substring=false).assertIsDisplayed().performClick()
        } finally {java.util.Locale.setDefault(locale)}
        assertTrue(vm.ui.value.editor!!.text("reminder").endsWith("20:00"))
        compose.onNodeWithTag("editPlanReminder").performScrollTo().performClick()
        shot("03-create-reminder")
        compose.onNodeWithTag("savePlan").performClick()
        compose.waitUntil(15_000) {vm.ui.value.editor==null && vm.ui.value.detailId!=null && !vm.ui.value.busy};ready()
        compose.onNodeWithTag("planReminderStatus").assertIsDisplayed()
        val task=vm.ui.value.data.tasks.single {it.title=="晚间散步"}
        assertNotNull(task.reminderAt)
        assertTrue(vm.ui.value.data.reminders.any {it.sourceId==task.id && it.state in setOf("blocked","scheduled")})
        shot("04-saved-reminder")
        compose.onNodeWithTag("planDetailMenu").performClick()
        compose.onNodeWithTag("editPlanDetail").performClick();ready()
        assertEquals(task.reminderAt,LocalDateTime.parse(vm.ui.value.editor!!.text("reminder")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        compose.onNodeWithTag("savePlan").performClick();ready()
        assertEquals(1,vm.ui.value.data.tasks.count {it.title=="晚间散步"})
    }

    @Test fun invalidRelativeReminderRetainsInputAndDoesNotCreateTask() {
        ready();compose.onNodeWithTag("root:计划").performClick();compose.onNodeWithTag("addPlan").performClick();ready()
        compose.onNodeWithTag("planTitle").performTextInput("尚未决定日期的任务")
        compose.onNodeWithTag("editPlanReminder").performClick()
        compose.onNodeWithText("提前15分钟",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("savePlan").performClick();ready()
        assertNotNull(vm.ui.value.editor);assertEquals("尚未决定日期的任务",vm.ui.value.editor!!.text("title"))
        assertTrue(vm.ui.value.error.orEmpty().contains("日期"));assertTrue(vm.ui.value.data.tasks.none {it.title=="尚未决定日期的任务"})
    }

    @Test fun calendarHabitsGoalsAndFocusRenderRealProgress() {
        ready();seed()
        runBlocking {
            val today=LocalDate.now();val repo=app.plans
            suspend fun save(f:PlanForm) {repo.prepareDraft(f);repo.save(f)}
            save(PlanForm("event").set("title","午后和朋友喝咖啡").set("start",today.toString()).set("end",today.toString()).set("startTime","15:00").set("endTime","16:00").set("location","街角咖啡店").set("minutes","15"))
            val habit=PlanForm("habit").set("title","每天读几页书").set("start",today.minusDays(7).toString()).set("reminder","20:00")
            save(habit);repo.habitLog(habit.id,today.toString(),"1","")
            val goal=PlanForm("goal").set("title","这个月读完 4 本书").set("target","4").set("unit","本").set("end",today.plusDays(20).toString())
            save(goal);repo.goalLog(goal.id,today.toString(),"1","")
            repo.startFocus(PlanForm("focus").set("title","安静阅读的时间").set("mode","countdown").set("seconds","1500"))
            app.planAlarms.reconcile()
        }
        ready();compose.onNodeWithTag("root:计划").performClick()
        listOf("日历","习惯","目标","专注").forEachIndexed {index,tab->
            compose.onNodeWithTag("planTab:$tab").performScrollTo().performClick();ready();shot("0${index+5}-$tab")
        }
        compose.onNodeWithTag("focusTimer").assertIsDisplayed()
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontKeepsContentAndCreateReachable() {
        ready();seed();runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ready();compose.onNodeWithTag("root:计划").performClick()
        compose.onNodeWithTag("addPlan").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("planTaskList").fetchSemanticsNode().boundsInRoot.height>100)
        shot("09-dark-large-font")
        compose.onNodeWithTag("planTab:目标").performScrollTo().performClick()
        compose.onNodeWithTag("planTab:目标").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("planTab:任务").performScrollTo().performClick()
        compose.onNodeWithTag("planTab:任务").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("addPlan").performClick();ready()
        compose.onNodeWithTag("savePlan").assertIsDisplayed();compose.onNodeWithTag("editPlanReminder").performScrollTo().assertIsDisplayed()
        shot("10-editor-large-font")
        compose.onNodeWithTag("editPlanReminder").performClick()
        compose.onNodeWithText("指定提醒日期与时间").performScrollTo().performClick()
        compose.onNodeWithTag("confirmPlanDate").assertIsDisplayed()
        shot("13-date-large-font")
    }

    @Test fun reminderPresentationDistinguishesBlockedFailedAndDifferentInstances() {
        val task=Task(ownerId="test",title="重复任务",reminderMinutes=15)
        val occurrence=Occurrence(ruleId="r",entityType="task",entityId=task.id,originalKey="2030-01-01",scheduledDate="2030-01-01",title="本次",status="todo")
        val jobs=listOf(PlanReminder(sourceType="task",sourceId=task.id,instanceKey="2030-01-01",title=task.title,triggerAt=5000,state="blocked"),
            PlanReminder(sourceType="task",sourceId=task.id,instanceKey="2030-01-02",title=task.title,triggerAt=6000,state="failed"))
        val snapshot=PlansSnapshot(tasks=listOf(task),reminders=jobs)
        assertEquals("通知未开启",planReminderSummary(snapshot,"task",task.id,occurrence,0).label)
        assertEquals("提醒待重试",planReminderSummary(snapshot,"task",task.id,occurrence.copy(originalKey="2030-01-02"),0).label)
        assertEquals("未设提醒",planReminderSummary(snapshot.copy(tasks=listOf(task.copy(reminderMinutes=null))),"task",task.id,occurrence,0).label)
        assertEquals("未设提醒",planReminderSummary(snapshot,"focus","missing",now=0).label)
        val due=TaskRow(task.copy(dueDate="2030-01-01",dueTime="12:00"))
        assertEquals("今天",taskSection(due,LocalDate.of(2030,1,1),LocalTime.of(11,0)))
        assertEquals("已逾期",taskSection(due,LocalDate.of(2030,1,1),LocalTime.of(13,0)))
    }
}
