package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.ui.DailyTheme
import com.example.dailylife.ui.PlanDurationValue
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanMobileCreationLocalTest {
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
    private fun begin(kind:String,id:String?=null) {
        compose.runOnIdle {vm.begin(kind,id=id)}
        compose.waitUntil(10_000) {vm.ui.value.editor?.kind==kind && !vm.ui.value.busy};ready()
    }
    private fun saved() {compose.waitUntil(15_000) {vm.ui.value.editor==null && !vm.ui.value.busy};ready()}
    private fun option(label:String,id:String) {
        compose.onNodeWithText(label,substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("selectionOption:$id").performScrollTo().performClick();ready()
    }
    private fun inputValue(tag:String,value:String) {
        compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("dailyLife.planReviewDir","build/reports/plan-mobile"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }

    @Test fun focusDurationUsesMinutesAndCancelDoesNotChangeTheStoredSeconds() {
        begin("focus")
        compose.onNodeWithTag("planTitle").performTextReplacement("安静读书")
        compose.onNodeWithTag("seconds").performScrollTo().performClick()
        inputValue("durationMinutes:seconds","25")
        compose.onNodeWithTag("durationMinutes:seconds").performTextReplacement("30")
        compose.onNodeWithTag("durationSeconds:seconds").performTextReplacement("0.5")
        compose.onNodeWithTag("confirmDuration:seconds").assertIsNotEnabled()
        compose.onNodeWithTag("cancelDuration:seconds").assertIsDisplayed().performClick()
        assertEquals("1500",vm.ui.value.editor!!.text("seconds","1500"))
        compose.onNodeWithTag("seconds").performScrollTo().performClick()
        compose.onNodeWithTag("durationMinutes:seconds").performTextReplacement("30")
        compose.onNodeWithTag("confirmDuration:seconds").performClick()
        assertEquals("1800",vm.ui.value.editor!!.text("seconds"))
        compose.onNodeWithTag("planTitle").performScrollTo();shot("mobile-focus-create")
        compose.onNodeWithTag("savePlan").performClick();saved()
        val session=vm.ui.value.data.sessions.single {it.title=="安静读书"}
        assertEquals(1800L,session.plannedSeconds)
    }

    @Test fun historicalDurationKeepsFractionalSecondsAcrossOpeningCancelAndConfirm() {
        val form=PlanForm("goal").set("title","精确时长目标").set("goalType","duration").set("target","61.125")
        runBlocking {app.plans.prepareDraft(form);app.plans.save(form)}
        ready();begin("goal",form.id)
        compose.onNodeWithTag("target").performScrollTo().performClick()
        inputValue("durationMinutes:target","1")
        inputValue("durationSeconds:target","1.125")
        compose.onNodeWithTag("cancelDuration:target").performClick()
        assertEquals("61.125",vm.ui.value.editor!!.text("target"))
        compose.onNodeWithTag("target").performScrollTo().performClick()
        compose.onNodeWithTag("confirmDuration:target").performClick()
        assertEquals("61.125",vm.ui.value.editor!!.text("target"))
        compose.onNodeWithTag("planTitle").performScrollTo();shot("mobile-goal-settings")
        compose.onNodeWithTag("savePlan").performClick();saved()
        assertEquals(61125L,vm.ui.value.data.goals.single {it.id==form.id}.targetMilli)
    }

    @Test fun habitDurationCommitsHoursMinutesAndFractionalSecondsAsOneTarget() {
        begin("habit")
        compose.onNodeWithTag("planTitle").performTextInput("晚间阅读")
        option("记录方式","duration")
        assertEquals("1800",vm.ui.value.editor!!.text("target"))
        compose.onNodeWithTag("target").performScrollTo().performClick()
        compose.onNodeWithTag("durationMinutes:target").performTextReplacement("45")
        compose.onNodeWithTag("durationSeconds:target").performTextReplacement("30.5")
        compose.onNodeWithTag("confirmDuration:target").performClick()
        assertEquals("2730.5",vm.ui.value.editor!!.text("target"))
        compose.onNodeWithTag("planTitle").performScrollTo();shot("mobile-habit-create")
        compose.onNodeWithTag("savePlan").performClick();saved()
        val habit=vm.ui.value.data.habits.single {it.name=="晚间阅读"}
        assertEquals("duration",habit.trackingType)
        assertEquals(2730500L,vm.ui.value.data.habitRules.single {it.habitId==habit.id}.targetMilli)
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontDurationSheetKeepsFieldsAndConfirmReachable() {
        runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ready();begin("focus")
        compose.onNodeWithTag("seconds").performScrollTo().performClick()
        compose.onNodeWithTag("durationMinutes:seconds").performScrollTo().performTextReplacement("20")
        compose.onNodeWithTag("confirmDuration:seconds").assertIsDisplayed();shot("mobile-duration-large-font")
        compose.onNodeWithTag("confirmDuration:seconds").performClick()
        assertEquals("1200",vm.ui.value.editor!!.text("seconds"))
        compose.onNodeWithTag("savePlan").assertIsDisplayed()
    }

    @Test fun negativeProgressRemainsExactAndCanBeChangedToAddition() {
        var chosen:String?=null
        compose.runOnIdle {compose.activity.setContent {
            DailyTheme(Appearance(theme="light",reducedMotion=true)) {
                PlanDurationValue("进度","-61.125",{chosen=it},"signedDuration",allowNegative=true)
            }
        }}
        compose.onNodeWithTag("signedDuration").performClick()
        inputValue("durationMinutes:signedDuration","1")
        inputValue("durationSeconds:signedDuration","1.125")
        compose.onNodeWithText("扣减",substring=false).assertIsSelected()
        compose.onNodeWithTag("confirmDuration:signedDuration").performClick()
        assertEquals("-61.125",chosen)
        compose.onNodeWithTag("signedDuration").performClick()
        compose.onNodeWithText("增加",substring=false).performClick()
        compose.onNodeWithTag("confirmDuration:signedDuration").performClick()
        assertEquals("61.125",chosen)
    }

    @Test fun zeroIsAllowedOnlyForTheExplicitCorrectionMode() {
        var chosen:String?=null
        compose.runOnIdle {compose.activity.setContent {
            DailyTheme(Appearance(theme="light",reducedMotion=true)) {
                PlanDurationValue("修正时长","0",{chosen=it},"zeroDuration",integerSeconds=true,allowZero=true)
            }
        }}
        compose.onNodeWithTag("zeroDuration").performClick()
        compose.onNodeWithTag("durationSeconds:zeroDuration").performTextReplacement("0.000")
        compose.onNodeWithTag("confirmDuration:zeroDuration").assertIsEnabled().performClick()
        assertEquals("0",chosen)
    }
}
