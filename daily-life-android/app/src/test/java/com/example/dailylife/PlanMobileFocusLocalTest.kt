package com.example.dailylife

import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.local.FocusSegment
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

@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanMobileFocusLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val vm get()=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
    private fun ready() {
        compose.waitUntil(30_000) {vm.ui.value.ready && !vm.ui.value.busy && ViewModelProvider(compose.activity)[RecordsViewModel::class.java].ui.value.ready}
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("dismissMessage").performClick()
    }
    @Before fun setup() {
        // Real devices expose a boot count; an unset Robolectric value correctly requests clock review.
        Settings.Global.putInt(app.contentResolver,Settings.Global.BOOT_COUNT,1)
        assertEquals("Normal-operation fixture needs a known boot",1,app.plans.clock.boot())
        ready();runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))};compose.waitForIdle()
    }
    @After fun close() {compose.activity.finish();app.database.close();app.backgroundScope.cancel()}
    private fun focus() {compose.onNodeWithTag("root:计划").performClick();compose.onNodeWithTag("planTab:专注").performScrollTo().performClick();ready()}
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("dailyLife.planReviewDir","build/reports/plan-mobile-focus"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    private fun start(mode:String="countdown",title:String="安静读一会儿",seconds:String="900"):String {
        val form=PlanForm("focus").set("mode",mode).set("title",title).set("seconds",seconds)
        runBlocking {app.plans.startFocus(form);app.plans.refresh()}
        compose.waitUntil(10_000) {vm.ui.value.data.sessions.any {it.id==form.id && it.state=="running"}}
        ready()
        return form.id
    }

    @Test fun countdownHasOnePrimaryActionAndEndingRequiresConfirmation() {
        val id=start();ready();focus()
        compose.onNodeWithTag("focusTimer").assertIsDisplayed()
        compose.onNodeWithTag("focusPrimary").assertTextContains("暂停")
        compose.onNodeWithText("提前结束",substring=false).assertDoesNotExist()
        assertNull(vm.ui.value.editor);shot("focus-mobile-running")
        compose.onNodeWithTag("focusPrimary").performClick()
        compose.waitUntil(10_000) {vm.ui.value.data.sessions.first {it.id==id}.state=="paused"};ready()
        compose.onNodeWithTag("focusPrimary").assertTextContains("继续")
        compose.onNodeWithTag("focusPrimary").performClick()
        compose.waitUntil(10_000) {vm.ui.value.data.sessions.first {it.id==id}.state=="running"};ready()
        compose.onNodeWithTag("focusEnd").performClick()
        compose.onNodeWithTag("focusEarly").performClick()
        assertEquals("running",vm.ui.value.data.sessions.first {it.id==id}.state)
        compose.onNodeWithText("结束这次专注？",substring=false).assertIsDisplayed()
        compose.onNodeWithText("确定",substring=false).performClick()
        compose.waitUntil(10_000) {vm.ui.value.data.sessions.first {it.id==id}.state=="early"};ready()
        compose.onNodeWithTag("focusPrimary").assertDoesNotExist()
        compose.onNodeWithTag("focusHistory:$id").assertIsDisplayed();shot("focus-mobile-history")
    }

    @Test fun pausedBreakKeepsItsElapsedTimeAndNextRoundDoesNotCountRest() {
        val id=start("pomodoro",seconds="10")
        runBlocking {
            val repo=app.plans
            val session=repo.dao.sessions().first {it.id==id}
            val segment=repo.dao.segments().first {it.sessionId==id}
            val now=System.currentTimeMillis()
            repo.dao.put(segment.copy(endAt=now,seconds=10))
            repo.dao.put(session.copy(state="paused",phase="break",shortBreak=5))
            repo.dao.put(FocusSegment(sessionId=id,phase="break",startAt=now-3000,endAt=now,elapsedStart=0,bootId=repo.clock.boot(),seconds=3,round=1))
            repo.refresh()
        }
        compose.waitUntil(10_000) {
            vm.ui.value.data.sessions.any {it.id==id && it.state=="paused" && it.phase=="break"} &&
                vm.ui.value.data.segments.any {it.sessionId==id && it.phase=="break" && it.seconds==3L}
        }
        ready();focus()
        compose.onNodeWithTag("focusTimer").assertTextEquals("00:02")
        compose.onNodeWithText("已专注 10秒",substring=false).assertIsDisplayed();shot("focus-mobile-paused-break")
        compose.onNodeWithTag("focusNextRound").performClick()
        compose.waitUntil(10_000) {vm.ui.value.data.sessions.first {it.id==id}.round==2};ready()
        val snapshot=vm.ui.value.data
        assertEquals(10L,snapshot.focusSeconds(id))
        compose.onNodeWithTag("focusPrimary").assertTextContains("暂停")
        compose.onNodeWithTag("focusEnd").performClick()
        compose.onNodeWithTag("focusFinish").assertDoesNotExist()
        compose.onNodeWithTag("focusEarly").assertIsDisplayed()
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontTimerStaysWithinPhoneWidthAndPrimaryActionRemainsReachable() {
        start("stopwatch","为周末出行做一次安静而完整的准备")
        runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ready();focus()
        compose.onNodeWithTag("planFocusList").performScrollToNode(hasTestTag("focusTimer"))
        val clock=compose.onNodeWithTag("focusTimer").fetchSemanticsNode().boundsInRoot
        val list=compose.onNodeWithTag("planFocusList").fetchSemanticsNode().boundsInRoot
        assertTrue(clock.left>=list.left);assertTrue(clock.right<=list.right)
        compose.onNodeWithTag("planFocusList").performScrollToNode(hasTestTag("focusPrimary"))
        compose.onNodeWithTag("focusPrimary").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {vm.ui.value.data.sessions.first().state=="paused"};ready()
        compose.onNodeWithTag("focusPrimary").assertTextContains("继续");shot("focus-mobile-large-font")
    }
}
