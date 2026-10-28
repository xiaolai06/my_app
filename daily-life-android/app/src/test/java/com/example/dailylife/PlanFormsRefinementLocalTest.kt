package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
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
class PlanFormsRefinementLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val vm get()=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
    private fun ready() {
        compose.waitUntil(30_000) {vm.ui.value.ready && !vm.ui.value.busy && ViewModelProvider(compose.activity)[RecordsViewModel::class.java].ui.value.ready}
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("dismissMessage").performClick()
    }
    @Before fun setup() {ready();runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))};ready()}
    @After fun close() {compose.activity.finish();app.backgroundScope.cancel();app.database.close()}
    private fun begin(kind:String) {
        compose.runOnIdle {vm.begin(kind)}
        compose.waitUntil(10_000) {vm.ui.value.editor?.kind==kind && !vm.ui.value.busy}
        ready()
    }
    private fun option(field:String,id:String) {
        compose.onNodeWithText(field,substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("selectionOption:$id").performScrollTo().performClick()
        ready()
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("dailyLife.planReviewDir","build/reports/plan-calendar-review"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    private fun saved() {
        compose.waitUntil(15_000) {vm.ui.value.editor==null && !vm.ui.value.busy}
        ready()
    }

    @Test fun habitGroupedFieldsKeepValuesWhenFoldedAndSaveRealRules() {
        begin("habit")
        compose.onNodeWithTag("planTitle").performTextInput("每天喝水")
        option("记录方式","quantity")
        compose.onNodeWithTag("target").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("unit").performScrollTo().performTextInput("杯")
        option("重复","weekly")
        compose.onNodeWithTag("weeklyDays").performScrollTo().performTextReplacement("4")
        val id=vm.ui.value.editor!!.id
        compose.onNodeWithTag("section:$id:habit-dates").performScrollTo().performClick()
        compose.onNodeWithText("结束日期",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("cancelPlanDate").assertIsDisplayed().performClick()
        assertTrue(vm.ui.value.editor!!.text("end").isBlank())
        compose.onNodeWithText("结束日期",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("confirmPlanDate").assertIsDisplayed().performClick()
        option("标记颜色","3")
        compose.onNodeWithTag("section:$id:habit-dates").performScrollTo().performClick()
        compose.onNodeWithText("结束日期",substring=false).assertDoesNotExist()
        assertEquals(LocalDate.now().toString(),vm.ui.value.editor!!.text("end"))
        assertEquals("2",vm.ui.value.editor!!.text("target"))
        shot("habit-grouped-editor")
        compose.onNodeWithTag("planTitle").performScrollTo();shot("habit-editor-overview")
        compose.onNodeWithTag("savePlan").assertIsDisplayed().performClick();saved()
        val habit=vm.ui.value.data.habits.single {it.name=="每天喝水"}
        assertEquals("quantity",habit.trackingType);assertEquals(3,habit.color)
        val rule=vm.ui.value.data.habitRules.single {it.habitId==habit.id}
        assertEquals(2000L,rule.targetMilli);assertEquals("杯",rule.unit)
        assertEquals("weekly",rule.scheduleType);assertEquals(4,rule.weeklyDays)
    }

    @Test fun goalValidationRetainsInputAndOptionalNoteUntilSuccessfulSave() {
        begin("goal")
        compose.onNodeWithTag("planTitle").performTextInput("读完四本书")
        compose.onNodeWithTag("target").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("planMoreFields").performScrollTo().performClick()
        compose.onNodeWithTag("note").performScrollTo().performTextInput("每周读一点")
        compose.onNodeWithTag("planMoreFields").performScrollTo().performClick()
        compose.onNodeWithTag("note").assertDoesNotExist()
        compose.onNodeWithTag("savePlan").performClick();ready()
        assertNotNull(vm.ui.value.editor);assertNotNull(vm.ui.value.error)
        assertEquals("读完四本书",vm.ui.value.editor!!.text("title"))
        assertEquals("每周读一点",vm.ui.value.editor!!.text("note"))
        assertTrue(vm.ui.value.data.goals.none {it.name=="读完四本书"})
        compose.onNodeWithTag("target").performScrollTo().performTextReplacement("4")
        compose.onNodeWithTag("unit").performScrollTo().performTextReplacement("本")
        shot("goal-grouped-editor")
        compose.onNodeWithTag("planTitle").performScrollTo();shot("goal-editor-overview")
        compose.onNodeWithTag("savePlan").assertIsDisplayed().performClick();saved()
        val goal=vm.ui.value.data.goals.single {it.name=="读完四本书"}
        assertEquals(4000L,goal.targetMilli);assertEquals("本",goal.unit);assertEquals("每周读一点",goal.note)
    }

    @Test fun goalSourceSelectionCancelsWithoutChangingCommittedChoices() {
        val task=PlanForm("task").set("title","书单整理")
        runBlocking {app.plans.prepareDraft(task);app.plans.save(task)}
        ready();begin("goal")
        compose.onNodeWithTag("planTitle").performTextInput("读书准备")
        option("进度来源","tasks")
        compose.onNodeWithText("关联内容",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("selectionOption:${task.id}").performClick()
        compose.onNodeWithTag("pickerCancel").assertIsDisplayed().performClick()
        assertTrue(vm.ui.value.editor!!.text("sources").isBlank())
        compose.onNodeWithText("关联内容",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("selectionOption:${task.id}").performClick()
        compose.onNodeWithTag("pickerConfirm").assertIsDisplayed().performClick()
        assertEquals(task.id,vm.ui.value.editor!!.text("sources"))
        compose.onNodeWithTag("savePlan").performClick();saved()
        val goal=vm.ui.value.data.goals.single {it.name=="读书准备"}
        assertEquals("tasks",goal.progressSource)
        assertEquals(task.id,vm.ui.value.data.goalLinks.single {it.goalId==goal.id}.sourceId)
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontKeepsFormGroupsAndSaveReachable() {
        runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ready();begin("habit")
        compose.onNodeWithTag("planTitle").performTextInput("每天散步")
        compose.onNodeWithTag("savePlan").assertIsDisplayed()
        option("记录方式","quantity")
        compose.onNodeWithTag("target").performScrollTo().performTextReplacement("20")
        compose.onNodeWithTag("unit").performScrollTo().performTextInput("分钟")
        compose.onNodeWithTag("editPlanReminder").performScrollTo().assertIsDisplayed()
        shot("habit-editor-large-font")
        compose.onNodeWithTag("savePlan").assertIsDisplayed().performClick();saved()
        assertTrue(vm.ui.value.data.habits.any {it.name=="每天散步" && it.trackingType=="quantity"})
    }
}
