package com.example.dailylife

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedPickersLocalTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("myLife.componentOutput","build/reports/components"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun calendarStagesLeapDayAndRejectsInvalidTypedDates() {
        var result="2024-02-28";val open=mutableStateOf(true)
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {
            if(open.value) GlassDatePicker(result,{open.value=false},setOf("2024-02-29")) {result=it}
        }}
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        compose.onNodeWithTag("calendarMode:list").performClick().assertIsSelected()
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:2024-02-29"))
        compose.onNodeWithTag("calendarDay:2024-02-29").performClick();assertEquals("2024-02-28",result)
        compose.onNodeWithTag("calendarMode:grid").performClick().assertIsSelected()
        compose.onNodeWithTag("calendarDay:2024-02-29").performScrollTo().assertIsSelected()
        shot("01-calendar-light")
        compose.onNodeWithTag("cancelDate").performClick();assertEquals("2024-02-28",result)
        compose.runOnIdle {open.value=true}
        compose.onNodeWithTag("calendarInputToggle").performScrollTo().performClick()
        compose.onNodeWithTag("calendarInput").performTextReplacement("2023-02-29")
        compose.onNodeWithTag("confirmDate").assertIsNotEnabled()
        compose.onNodeWithTag("calendarInput").performTextReplacement("2024-02-29")
        compose.onNodeWithTag("confirmDate").performClick();assertEquals("2024-02-29",result);assertFalse(open.value)
    }
    @Test fun monthJumpChangesOnlyCalendarUntilConfirmed() {
        var result="2026-10-26"
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {GlassDatePicker(result,{}) {result=it}}}
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        compose.onNodeWithTag("calendarMonth").performClick()
        compose.onNodeWithTag("monthYear").performTextReplacement("2020")
        compose.onNodeWithText("2 月",substring=false).performScrollTo().performClick();shot("02-month-selection")
        compose.onNodeWithTag("confirmMonth").performClick();assertEquals("2026-10-26",result)
        compose.onNodeWithTag("calendarMode:list").performClick().assertIsSelected()
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:2020-02-29"))
        compose.onNodeWithTag("calendarDay:2020-02-29").performClick()
        compose.onNodeWithTag("confirmDate").performClick();assertEquals("2020-02-29",result)
    }
    @Test fun searchableSelectionHandlesLongListsAndEmptyResults() {
        var result="2"
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {
            GlassSelectionSheet("选择账户",(1..300).map {it.toString() to "备用账户 $it"},result,{}) {result=it}
        }}
        compose.onNodeWithTag("selectionSearch").performTextInput("找不到")
        compose.onNodeWithText("没有匹配的选项，试试其他关键词").assertIsDisplayed()
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.onNodeWithTag("selectionSearch").performTextInput("300")
        compose.onNodeWithTag("selectionOption:300").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("300",result);shot("03-search-selection")
    }
    @Test fun multiSelectionRetainsHiddenChoicesAndCancelDoesNotApply() {
        var result=setOf("1");val open=mutableStateOf(true)
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {
            if(open.value) GlassMultiSelectionSheet("选择标签",(1..50).map {it.toString() to "标签 $it"},result,{open.value=false},maximum=3) {result=it}
        }}
        compose.onNodeWithTag("selectionSearch").performTextInput("40")
        compose.onNodeWithTag("selectionOption:40").performClick().assertIsOn()
        compose.onNodeWithTag("pickerCancel").performClick();assertEquals(setOf("1"),result)
        compose.runOnIdle {open.value=true}
        compose.onNodeWithTag("selectionSearch").performTextInput("40")
        compose.onNodeWithTag("selectionOption:40").performClick()
        compose.onNodeWithTag("selectionSearch").performTextReplacement("50")
        compose.onNodeWithTag("selectionOption:50").performClick();shot("04-multi-selection")
        compose.onNodeWithTag("pickerConfirm").performClick();assertEquals(setOf("1","40","50"),result)
    }
    @Test fun timePresetsCancelAndInvalidHoursCannotCommit() {
        var result="09:00";val open=mutableStateOf(true)
        compose.setContent {DailyTheme(Appearance(theme="dark",reducedMotion=true)) {if(open.value) GlassTimePicker(result,{open.value=false}) {result=it}}}
        compose.onNodeWithTag("timeHour").performTextReplacement("24")
        compose.onNodeWithTag("confirmTime").assertIsNotEnabled()
        compose.onNodeWithText("晚上8点").performClick();shot("05-time-dark")
        compose.onNodeWithTag("cancelPlanTime").performClick();assertEquals("09:00",result)
        compose.runOnIdle {open.value=true}
        compose.onNodeWithText("晚上8点").performClick()
        compose.onNodeWithTag("confirmTime").performClick();assertEquals("20:00",result)
    }
    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontKeepsActionsVisibleAndEveryDayReachable() {
        var result="2024-02-01"
        compose.runOnIdle {
            val configuration=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(configuration,compose.activity.resources.displayMetrics)
            compose.activity.recreate()
        }
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {GlassDatePicker(result,{}) {result=it}}}
        compose.onNodeWithTag("calendarMode:grid").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("calendarDay:2024-02-01").performScrollTo().assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp)
        compose.onNodeWithTag("calendarMode:list").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:2024-02-29"))
        compose.onNodeWithTag("calendarDay:2024-02-29").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("confirmDate").assertIsDisplayed();shot("06-large-font-calendar")
        compose.onNodeWithTag("confirmDate").performClick();assertEquals("2024-02-29",result)
    }
}
