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
import java.time.LocalDate

/** The shared picker renders a full native month in both ledger and planning entry points. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers="w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarGridLocalTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("myLife.calendarOutput","build/reports/calendar-grid"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getShownDialogs().last {it.isShowing}.window!!.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }

    @Test fun normalPhoneShowsAllSixWeeksWithoutShrinkingDayTargets() {
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {
            GlassDatePicker("2025-03-08",{},setOf("2025-03-12","2025-03-30")) {}
        }}
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        compose.onNodeWithTag("calendarMonthGrid").assertIsDisplayed()
        (1..31).forEach {day->
            compose.onNodeWithTag("calendarDay:2025-03-"+day.toString().padStart(2,'0'))
                .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithTag("calendarDay:2025-03-08").assertIsSelected()
        val monday=compose.onNodeWithTag("calendarDay:2025-03-03").fetchSemanticsNode().boundsInRoot
        val sunday=compose.onNodeWithTag("calendarDay:2025-03-09").fetchSemanticsNode().boundsInRoot
        assertEquals(monday.center.y,sunday.center.y,1f)
        assertTrue(sunday.center.x>monday.center.x)
        compose.onNodeWithTag("confirmDate").assertIsDisplayed()
        shot("01-complete-six-week-month")
    }

    @Test fun daySelectionStagesUntilConfirmationAndCancelKeepsOriginal() {
        var result="2024-02-28"
        val open=mutableStateOf(true)
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {
            if(open.value) GlassDatePicker(result,{open.value=false},setOf("2024-02-29")) {result=it}
        }}
        compose.onNodeWithTag("calendarDay:2024-02-29").performClick().assertIsSelected()
        assertEquals("2024-02-28",result)
        shot("02-selected-leap-day")
        compose.onNodeWithTag("cancelDate").performClick()
        assertEquals("2024-02-28",result)
        compose.runOnIdle {open.value=true}
        compose.onNodeWithTag("calendarDay:2024-02-28").assertIsSelected()
        compose.onNodeWithTag("calendarDay:2024-02-29").performClick()
        compose.onNodeWithTag("confirmDate").performClick()
        assertEquals("2024-02-29",result)
        assertFalse(open.value)
    }

    @Test fun monthNavigationAndTypedMonthOnlyCommitTheExplicitAction() {
        var day="2026-10-28"
        var month="2026-10"
        val open=mutableStateOf(true)
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {
            if(open.value) GlassDatePicker(day,{open.value=false},confirmLabel="查看当天",chooseMonth={month=it}) {day=it}
        }}
        compose.onNodeWithTag("calendarMonth").performClick()
        compose.onNodeWithTag("monthYear").performTextReplacement("2024")
        compose.onNodeWithText("2 月",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("confirmMonth").performClick()
        assertEquals("2026-10",month)
        assertEquals("2026-10-28",day)
        compose.onNodeWithTag("calendarDay:2024-02-29").assertIsDisplayed()
        shot("03-view-month-without-changing-day")
        compose.onNodeWithTag("calendarChooseMonth").performClick()
        assertEquals("2024-02",month)
        assertEquals("2026-10-28",day)
        compose.runOnIdle {open.value=true}
        compose.onNodeWithTag("calendarInputToggle").performScrollTo().performClick()
        compose.onNodeWithTag("calendarInput").performTextReplacement("2023-02-29")
        compose.onNodeWithTag("confirmDate").assertIsNotEnabled()
        compose.onNodeWithTag("calendarChooseMonth").assertIsNotEnabled()
        compose.onNodeWithTag("calendarInput").performTextReplacement("2025-12-19")
        compose.onNodeWithTag("calendarChooseMonth").performClick()
        assertEquals("2025-12",month)
        assertEquals("2026-10-28",day)
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeTextDefaultsToMonthGridAndAllDaysRemainReachable() {
        var result="2025-03-01"
        compose.runOnIdle {
            val configuration=android.content.res.Configuration(compose.activity.resources.configuration).apply {fontScale=2f}
            compose.activity.resources.updateConfiguration(configuration,compose.activity.resources.displayMetrics)
            compose.activity.recreate()
        }
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {GlassDatePicker(result,{}) {result=it}}}
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        shot("04a-large-font-initial-month")
        // The month has independent vertical and horizontal scrolling at large text sizes.
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarSelectedDate"))
        compose.onNodeWithTag("calendarDay:2025-03-30").performScrollTo()
            .assertIsDisplayed().assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(64.dp).performClick().assertIsSelected()
        assertEquals("2025-03-01",result)
        compose.onNodeWithTag("confirmDate").assertIsDisplayed()
        shot("04-large-font-month-grid")
        compose.onNodeWithTag("calendarMode:list").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:2025-03-31"))
        compose.onNodeWithTag("calendarDay:2025-03-31").performClick().assertIsSelected()
        compose.onNodeWithTag("confirmDate").assertIsDisplayed().performClick()
        assertEquals("2025-03-31",result)
    }

    @Test fun todayIsIdentifiedIndependentlyFromSelectedDayInDarkTheme() {
        val today=LocalDate.now()
        val other=if(today.dayOfMonth==1) today.plusDays(1) else today.minusDays(1)
        compose.setContent {DailyTheme(Appearance(theme="dark",reducedMotion=true)) {
            GlassDatePicker(other.toString(),{},setOf(today.toString())) {}
        }}
        compose.onNodeWithTag("calendarDay:$today").assertIsNotSelected()
            .assert(hasContentDescription("今天",substring=true))
        compose.onNodeWithTag("calendarDay:$other").assertIsSelected()
        shot("05-dark-today-and-selection")
    }
}
