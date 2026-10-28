package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.home.HomeViewModel
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.ledger.LedgerViewModel
import com.example.dailylife.feature.tools.ToolsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDesignLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val plans get()=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
    private fun ready() {
        compose.waitUntil(30_000) {ViewModelProvider(compose.activity)[RecordsViewModel::class.java].ui.value.ready &&
            plans.ui.value.ready && ViewModelProvider(compose.activity)[LedgerViewModel::class.java].ui.value.ready &&
            ViewModelProvider(compose.activity)[ToolsViewModel::class.java].ui.value.ready}
        compose.waitForIdle()
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File("build/reports/ui-review/home-$name.png").apply {parentFile!!.mkdirs()}.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    private fun seed() {
        ready()
        runBlocking {
            app.preferences.set(Appearance(theme="light",reducedMotion=true))
            app.preferences.setDashboard(DashboardPreferences(homeOrder=listOf("weather","events","tasks","habits","life","budget","recent","inbox","suggestions","favorites")))
            val city=WeatherPlace("杭州 · 浙江省 · 中国",30.27,120.15)
            WeatherRepository(app) {"""{"current":{"time":${System.currentTimeMillis()/1000},"temperature_2m":23,"apparent_temperature":22,"weather_code":2,"is_day":1},"daily":{"temperature_2m_min":[17],"temperature_2m_max":[25],"precipitation_probability_max":[20]}}"""}.load(city)
            app.preferences.setWeather(WeatherSettings(true,city))
            val owner=app.records.owner();val today=LocalDate.now()
            app.plans.dao.put(Task(id="home-task",ownerId=owner,title="买鲜花，整理书桌",plannedDate=today.toString()))
            app.plans.dao.put(Task(id="home-next",ownerId=owner,title="明天去取快递",plannedDate=today.plusDays(1).toString()))
            app.plans.dao.put(PlanEvent(ownerId=owner,title="傍晚去公园走走",startDate=today.toString(),endDateExclusive=today.plusDays(1).toString(),startTime="17:30",endTime="18:30",zoneId=ZoneId.systemDefault().id,location="家附近的公园"))
        }
        compose.waitUntil(10_000) {compose.waitForIdle();ViewModelProvider(compose.activity)[HomeViewModel::class.java].weather.value.reading!=null && plans.ui.value.data.tasks.any {it.id=="home-task"}}
    }
    @After fun close() {compose.activity.finish();app.database.close();app.backgroundScope.cancel()}

    @Test fun emptyCardsDisappearAndStylePersistsWithoutChangingBusinessData() {
        ready()
        runBlocking {app.preferences.setDashboard(DashboardPreferences());app.preferences.set(Appearance(theme="light",reducedMotion=true))}
        compose.waitUntil(10_000) {compose.waitForIdle();ViewModelProvider(compose.activity)[HomeViewModel::class.java].state.value.homeStyle=="color"}
        listOf("events","tasks","budget","inbox").forEach {compose.onNodeWithTag("homeSection:$it").assertDoesNotExist()}
        compose.onNodeWithText("记一笔").assertDoesNotExist()
        compose.onNodeWithTag("homeQuick:note").assertDoesNotExist()
        shot("empty")
        compose.onNodeWithTag("editHome").performScrollTo().performClick()
        compose.onNodeWithTag("homeStyle:plain").performClick().assertIsSelected()
        compose.onNodeWithTag("homeCard:weather").performClick().assertIsOff()
        shot("configuration")
        compose.onNodeWithTag("saveHomeSettings").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();runBlocking {app.preferences.dashboard.first().homeStyle=="plain"}}
        assertTrue(runBlocking {app.preferences.dashboard.first()}.toString(),runBlocking {"weather" in app.preferences.dashboard.first().hiddenCards})
        compose.activity.recreate();ready()
        compose.onNodeWithTag("weatherCard").assertDoesNotExist()
        assertTrue(plans.ui.value.data.tasks.isEmpty())
    }
    @Test fun calendarConfirmsOrCancelsAndTaskCompletionUsesOriginalTask() {
        seed();shot("light")
        compose.onNodeWithText("城市天气").assertDoesNotExist()
        compose.onNodeWithText("天气：Open-Meteo").assertDoesNotExist()
        compose.onNodeWithTag("refreshWeather").assertHasClickAction().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        compose.onNodeWithTag("homeDate").performClick()
        compose.onNodeWithTag("calendarDays").assertIsDisplayed()
        compose.onNodeWithText("YYYY-MM-DD").assertDoesNotExist()
        shot("calendar")
        val next=LocalDate.now().plusDays(1)
        if(next.month!=LocalDate.now().month) compose.onNodeWithContentDescription("下个月").performClick()
        compose.onNodeWithTag("calendarDay:$next").performScrollTo().performClick()
        compose.onNodeWithTag("cancelDate").performClick()
        compose.onNodeWithTag("weatherCard").assertExists()
        compose.onNodeWithTag("homeDate").performClick()
        if(next.month!=LocalDate.now().month) compose.onNodeWithContentDescription("下个月").performClick()
        compose.onNodeWithTag("calendarDay:$next").performScrollTo().performClick()
        compose.onNodeWithTag("confirmHomeDate").performClick()
        compose.onNodeWithTag("weatherCard").assertDoesNotExist()
        compose.onNodeWithText("明天去取快递").assertExists()
        compose.onNodeWithText("回到今天").performClick()
        compose.onNodeWithTag("homeContent").performScrollToNode(hasTestTag("completeTask:home-task"))
        compose.onNodeWithTag("completeTask:home-task").performClick()
        compose.waitUntil(10_000) {plans.ui.value.data.tasks.first {it.id=="home-task"}.status=="done"}
        assertEquals(2,plans.ui.value.data.tasks.size)
        compose.onNodeWithText("已完成 · 1").performClick()
        compose.onNodeWithTag("completeTask:home-task").performScrollTo().performClick()
        compose.waitUntil(10_000) {plans.ui.value.data.tasks.first {it.id=="home-task"}.status=="todo"}
        compose.onNodeWithTag("homeContent").performScrollToIndex(0)
        runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
        compose.waitUntil(10_000) {ViewModelProvider(compose.activity)[RecordsViewModel::class.java].ui.value.appearance.theme=="dark"}
        shot("dark")
    }
    @Test @Config(qualifiers="w320dp-h760dp-xxhdpi") fun largeFontUsesCalendarListAndKeepsWeatherControlsReachable() {
        seed()
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration);config.fontScale=2f
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ready()
        compose.waitUntil(10_000) {ViewModelProvider(compose.activity)[HomeViewModel::class.java].weather.value.reading!=null}
        shot("large-font")
        compose.onNodeWithTag("weatherSettings").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("城市与天气").assertIsDisplayed()
        compose.onNodeWithText("关闭",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("homeContent").performScrollToIndex(0)
        compose.onNodeWithTag("homeDate").performClick()
        shot("calendar-large-font")
        compose.onNodeWithTag("calendarDay:${LocalDate.now()}").assertIsDisplayed().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        compose.onNodeWithContentDescription("上个月").performClick()
        val previous=LocalDate.now().minusMonths(1).withDayOfMonth(1)
        compose.onNodeWithTag("calendarDay:$previous").assertIsDisplayed().performClick()
        compose.onNodeWithTag("confirmHomeDate").assertIsDisplayed().performClick()
        compose.onNodeWithText("回到今天").assertIsDisplayed()
    }
}
