package com.example.dailylife

import android.animation.ValueAnimator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.ui.DailyTheme
import com.example.dailylife.ui.GlassSheet
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

/** The close animation must never hide a modal whose owner has refused dismissal. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassSheetMotionLocalTest {
    @get:Rule val compose=createComposeRule()

    @Test fun busySheetCannotCloseAndExitCannotTriggerAnotherAction() {
        ReflectionHelpers.callStaticMethod<Unit>(ValueAnimator::class.java,"setDurationScale",
            ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType!!,1f))
        val shown=mutableStateOf(true)
        val saving=mutableStateOf(true)
        var actions=0
        compose.setContent {
            DailyTheme(Appearance(theme="light",reducedMotion=false)) {
                TextButton(onClick={shown.value=true}) {Text("重新打开")}
                if(shown.value) GlassSheet("受保护弹层",{if(!saving.value) shown.value=false},dismissEnabled=!saving.value) {
                    TextButton(onClick={actions++}) {Text("内容动作")}
                }
            }
        }
        compose.onNodeWithText("关闭").assertIsNotEnabled()
        assertTrue(shown.value)
        compose.runOnIdle {saving.value=false}
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("关闭").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(32);compose.waitForIdle()
        assertTrue("The modal remains mounted while it exits",shown.value)
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
        compose.onNodeWithText("内容动作").assertDoesNotExist()
        // A save starting during exit must return the sheet, rather than leave a hidden modal.
        compose.runOnIdle {saving.value=true}
        compose.waitForIdle()
        repeat(24) {compose.mainClock.advanceTimeBy(100);compose.waitForIdle()}
        assertTrue(shown.value)
        compose.onNodeWithText("关闭").assertIsNotEnabled()
        compose.onNodeWithText("内容动作").assertIsDisplayed()
        compose.runOnIdle {saving.value=false}
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("关闭").performClick()
        compose.mainClock.advanceTimeBy(1200);compose.waitForIdle()
        assertFalse(shown.value)
        assertEquals(0,actions)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("重新打开").performClick()
        compose.onNodeWithText("内容动作").assertIsDisplayed()
    }

    @Test fun systemBackDuringSaveReturnsTheSheetInsteadOfLeavingAHiddenModal() {
        ReflectionHelpers.callStaticMethod<Unit>(ValueAnimator::class.java,"setDurationScale",
            ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType!!,1f))
        val shown=mutableStateOf(true)
        val saving=mutableStateOf(false)
        compose.setContent {
            DailyTheme(Appearance(theme="light",reducedMotion=false)) {
                if(shown.value) GlassSheet("返回键关闭保护",{if(!saving.value) shown.value=false},dismissEnabled=!saving.value) {
                    TextButton(onClick={}) {Text("原输入仍在")}
                }
            }
        }
        compose.onNodeWithText("原输入仍在").assertIsDisplayed()
        val dialog=ShadowDialog.getLatestDialog()
        assertTrue("The native sheet must dispatch system back through its own window",dialog is androidx.activity.OnBackPressedDispatcherOwner)
        val back=(dialog as androidx.activity.OnBackPressedDispatcherOwner).onBackPressedDispatcher
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread {back.onBackPressed()}
        compose.mainClock.advanceTimeBy(32);compose.waitForIdle()
        assertTrue("Back dismisses only after the exit animation",shown.value)
        compose.onNodeWithText("原输入仍在").assertDoesNotExist()
        compose.runOnIdle {saving.value=true}
        compose.mainClock.advanceTimeBy(2400);compose.waitForIdle()
        assertTrue("A save starting during native dismissal must keep the owner mounted",shown.value)
        assertTrue(dialog.isShowing)
        compose.onNodeWithText("关闭").assertIsNotEnabled()
        compose.onNodeWithText("原输入仍在").assertIsDisplayed()
        compose.runOnIdle {saving.value=false}
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread {back.onBackPressed()}
        compose.mainClock.advanceTimeBy(1200);compose.waitForIdle()
        assertFalse("A subsequent system back still dismisses normally",shown.value)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("原输入仍在").assertDoesNotExist()
    }

    @Test fun reducedMotionClosesImmediatelyAndStillRespectsSaving() {
        val shown=mutableStateOf(true)
        val saving=mutableStateOf(true)
        compose.setContent {
            DailyTheme(Appearance(theme="light",reducedMotion=true)) {
                if(shown.value) GlassSheet("减少动态弹层",{shown.value=false},dismissEnabled=!saving.value) {Text("保留输入")}
            }
        }
        compose.onNodeWithText("关闭").assertIsNotEnabled()
        compose.runOnIdle {saving.value=false}
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.onNodeWithText("关闭").performClick()
        assertFalse(shown.value)
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("保留输入").assertDoesNotExist()
    }
}
