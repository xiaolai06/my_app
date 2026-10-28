package com.example.dailylife

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.repository.*
import com.example.dailylife.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeWeatherUiLocalTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun missingStaleAndOfflineWeatherRemainHonestAndControlsWork() {
        val city=WeatherPlace("杭州",30.27,120.15);val now=System.currentTimeMillis()
        var ui by mutableStateOf(WeatherUi(WeatherSettings(true,city)))
        var refreshes=0;var settings=0
        compose.setContent {DailyTheme(Appearance(theme="light",reducedMotion=true)) {Box(Modifier.padding(20.dp)) {HomeWeatherCard(ui,{settings++},{refreshes++})}}}
        compose.onNodeWithText("暂时没有天气数据").assertIsDisplayed()
        compose.onNodeWithText("0°").assertDoesNotExist()
        compose.onNodeWithTag("refreshWeather").performClick();assertEquals(1,refreshes)
        compose.onNodeWithTag("weatherSettings").performClick();assertEquals(1,settings)
        val old=WeatherReading(city,now-5*3600000,now-5*3600000,18.0,17.0,61,12.0,20.0,80)
        compose.runOnIdle {ui=ui.copy(reading=old)}
        compose.onNodeWithText("数据已过期",substring=true).assertIsDisplayed()
        compose.runOnIdle {ui=ui.copy(error="网络不可用")}
        compose.onNodeWithText("更新失败 · 缓存",substring=true).assertIsDisplayed()
        compose.runOnIdle {ui=ui.copy(loading=true,error=null)}
        compose.onNodeWithTag("refreshWeather").assertIsNotEnabled()
        compose.runOnIdle {ui=ui.copy(settings=WeatherSettings())}
        compose.onNodeWithText("18°").assertDoesNotExist()
        compose.onNodeWithText("选择城市并开启").assertIsDisplayed()
    }
    @Test fun artworkCoversNightRainSnowFogAndThunderWithoutExternalAssets() {
        compose.setContent {DailyTheme(Appearance(theme="dark",reducedMotion=true)) {
            Column {listOf(0,2,3,45,61,71,95).forEach {code->Row {WeatherArtwork(code,true,Modifier.size(80.dp));WeatherArtwork(code,false,Modifier.size(80.dp))}}}
        }}
        compose.waitForIdle()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/ui-review/home-weather-artwork.png").apply {parentFile!!.mkdirs()}.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
}
