package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.ui.planQuantity
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
class PlanCalendarLocalTest {
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
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("dailyLife.planReviewDir","build/reports/plan-calendar-review"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    private fun calendar() {compose.onNodeWithTag("root:计划").performClick();compose.onNodeWithTag("planTab:日历").performClick();ready()}
    private fun seedDay(day:LocalDate):String=runBlocking {
        val owner=app.records.owner()
        val task=Task(ownerId=owner,title="准备申请资料",plannedDate=day.toString(),dueDate=day.toString())
        app.plans.dao.put(task)
        app.plans.dao.put(Task(ownerId=owner,title="已经提交的申请",plannedDate=day.toString(),status="done",completedAt=System.currentTimeMillis()))
        app.plans.dao.put(Task(ownerId=owner,title="改天再处理的事项",plannedDate=day.toString(),status="cancelled"))
        app.plans.dao.put(PlanEvent(ownerId=owner,title="下午讨论方案",startDate=day.toString(),endDateExclusive=day.plusDays(1).toString(),zoneId=ZoneId.systemDefault().id,startTime="15:00",endTime="16:00"))
        app.plans.refresh();task.id
    }

    @Test fun fullMonthIsDefaultAndSelectedDayShowsCategorizedRealItems() {
        val month=YearMonth.now();val day=month.atDay(12);val id=seedDay(day);ready();calendar()
        compose.onNodeWithTag("planFullMonth").assertIsDisplayed()
        compose.onNodeWithTag("planQuickWeek").assertDoesNotExist()
        compose.onNodeWithTag("calendarDay:"+month.atDay(1)).assertExists()
        compose.onNodeWithTag("calendarDay:"+month.atEndOfMonth()).assertExists()
        compose.onNodeWithTag("calendarDay:$day").performClick();ready();shot("calendar-full-month")
        compose.onNodeWithTag("planCalendar").performScrollToNode(hasText("下午讨论方案"))
        compose.onNodeWithText("下午讨论方案").assertIsDisplayed()
        compose.onNodeWithTag("planCalendar").performScrollToNode(hasTestTag("taskCard:$id"))
        compose.onNodeWithText("准备申请资料").assertIsDisplayed()
        compose.onNodeWithTag("planCalendar").performScrollToNode(hasText("已经提交的申请"))
        compose.onNodeWithTag("planSection:已完成").assertExists()
        compose.onNodeWithTag("planCalendar").performScrollToNode(hasText("改天再处理的事项"))
        compose.onNodeWithTag("planSection:已取消与跳过").assertExists();shot("calendar-categorized-agenda")
        compose.onNodeWithTag("planCalendar").performScrollToIndex(0)
        compose.onNodeWithTag("planAgendaFilter:task").performClick()
        compose.onNodeWithTag("planCalendar").performScrollToNode(hasTestTag("taskCard:$id"))
        compose.onNodeWithText("下午讨论方案").assertDoesNotExist()
    }

    @Test fun emptyDateMonthNavigationAndQuickWeekNeverLeakOtherDays() {
        val month=YearMonth.now();val day=month.atDay(12);seedDay(day);ready();calendar()
        compose.onNodeWithTag("calendarDay:$day").performClick();ready()
        compose.onNodeWithTag("calendarDay:"+month.atDay(25)).performClick();ready()
        compose.onNodeWithTag("planCalendar").performScrollToNode(hasTestTag("planAgendaEmpty"))
        compose.onAllNodesWithText("暂无事项",substring=false).assertCountEquals(1)
        compose.onNodeWithText("准备申请资料").assertDoesNotExist();shot("calendar-empty-date")
        compose.onNodeWithTag("planCalendar").performScrollToIndex(0)
        compose.onNodeWithTag("planNextMonth").performClick();ready()
        compose.onNodeWithTag("planCalendar").performScrollToNode(hasTestTag("planAgendaEmpty"))
        compose.onNodeWithText("请选择日期").assertIsDisplayed()
        compose.onNodeWithText("准备申请资料").assertDoesNotExist()
        compose.onNodeWithTag("planCalendar").performScrollToIndex(0)
        compose.onNodeWithTag("calendarDay:"+month.plusMonths(1).atDay(1)).performClick()
        compose.onNodeWithTag("planCalendarMode:week").performClick()
        compose.onNodeWithTag("planQuickWeek").assertIsDisplayed();shot("calendar-quick-week")
        compose.onNodeWithTag("planCalendarMode:month").performClick()
        compose.onNodeWithTag("planFullMonth").assertIsDisplayed()
        compose.onNodeWithTag("planChooseDate").performClick()
        compose.onNodeWithTag("cancelPlanDate").performClick()
        compose.onNodeWithTag("calendarDay:"+month.plusMonths(1).atDay(1)).assertIsSelected()
    }

    @Test fun habitsAndGoalsUseGroupedRowsWithReadableDurationAndSeparateActions() {
        ready();val today=LocalDate.now().toString()
        val habit=runBlocking {
            val owner=app.records.owner();val h=Habit(ownerId=owner,name="安静阅读",startDate=today,trackingType="duration")
            app.plans.dao.put(h);app.plans.dao.put(HabitRuleVersion(habitId=h.id,effectiveFrom=today,targetMilli=1800000,unit="秒"))
            app.plans.dao.put(Habit(ownerId=owner,name="暂停的旧习惯",startDate=today,status="paused"))
            val g=Goal(ownerId=owner,name="本月阅读两小时",goalType="duration",targetMilli=7200000,unit="秒",endDate=LocalDate.now().plusDays(10).toString())
            app.plans.dao.put(g);app.plans.dao.put(GoalProgress(goalId=g.id,logDate=today,deltaMilli=3600000))
            val done=Goal(ownerId=owner,name="完成的小目标",state="done",completedAt=System.currentTimeMillis())
            app.plans.dao.put(done);app.plans.dao.put(GoalProgress(goalId=done.id,logDate=today,deltaMilli=1000))
            app.plans.refresh();h
        }
        ready();compose.onNodeWithTag("root:计划").performClick();compose.onNodeWithTag("planTab:习惯").performClick();ready()
        compose.onNodeWithTag("planSection:已暂停").assertExists()
        compose.onNodeWithText("0秒 / 30分钟").assertIsDisplayed();shot("habits-grouped-list")
        compose.onNodeWithTag("logHabit:"+habit.id).performClick();ready()
        assertNull(vm.ui.value.detailId)
        compose.onNodeWithText("习惯打卡",substring=false).assertIsDisplayed()
        compose.onNodeWithText("关闭",substring=false).performClick()
        compose.onNodeWithTag("habitWeek:"+habit.id).performClick();shot("habit-week-expanded")
        compose.onNodeWithTag("planTab:目标").performClick();ready()
        compose.onNodeWithText("1小时 / 2小时",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("planSection:已完成").assertExists();shot("goals-grouped-list")
        assertEquals("1小时5分钟",planQuantity(3900000,"秒",true))
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontKeepsWholeMonthAndDateSelectionAvailable() {
        ready();runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ready();calendar()
        val month=YearMonth.now()
        compose.onNodeWithTag("calendarDay:"+month.atDay(1)).assertExists()
        compose.onNodeWithTag("calendarDay:"+month.atEndOfMonth()).assertExists()
        compose.onNodeWithTag("planQuickWeek").assertDoesNotExist()
        shot("calendar-large-font-month")
        compose.onNodeWithTag("planCalendar").performScrollToNode(hasTestTag("planChooseDate"))
        compose.onNodeWithTag("planChooseDate").performClick()
        compose.onNodeWithTag("confirmPlanDate").assertIsDisplayed()
        compose.onNodeWithTag("cancelPlanDate").performClick()
        compose.onNodeWithTag("addPlan").assertIsDisplayed()
    }
}
