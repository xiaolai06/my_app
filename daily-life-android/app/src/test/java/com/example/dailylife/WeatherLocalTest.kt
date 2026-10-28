package com.example.dailylife

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.repository.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WeatherLocalTest {
    @Test fun automaticRefreshExpiresAfterFifteenMinutesAndParsesNight()=runBlocking {
        val base=ApplicationProvider.getApplicationContext<Context>();val folder=File(base.cacheDir,"weather-interval-${System.nanoTime()}").apply {mkdirs()}
        val context=object:ContextWrapper(base) {override fun getCacheDir()=folder}
        var now=System.currentTimeMillis();var calls=0
        val repo=WeatherRepository(context,nowMillis={now}) {calls++;JSONObject(weather()).apply {getJSONObject("current").put("time",now/1000).put("is_day",0)}.toString()}
        val city=WeatherPlace("杭州",30.27,120.15)
        try {
            assertFalse(repo.load(city).isDay)
            now+=14*60000;repo.load(city);assertEquals(1,calls)
            now+=60000;repo.load(city);assertEquals(2,calls)
            repo.load(city,true);assertEquals(2,calls)
            now+=60000;repo.load(city,true);assertEquals(3,calls)
        } finally {folder.deleteRecursively()}
    }
    private fun weather()="""{"current":{"time":${System.currentTimeMillis()/1000},"temperature_2m":21,"apparent_temperature":20,"weather_code":2},"daily":{"temperature_2m_min":[16],"temperature_2m_max":[24],"precipitation_probability_max":[40]}}"""
    @Test fun weatherCacheIsCityBoundAndRefreshDoesNotBurstRequests()=runBlocking {
        val base=ApplicationProvider.getApplicationContext<Context>();val folder=File(base.cacheDir,"weather-test-${System.nanoTime()}").apply {mkdirs()}
        val context=object:ContextWrapper(base) {override fun getCacheDir()=folder}
        var calls=0;val repo=WeatherRepository(context) {calls++;weather()}
        val city=WeatherPlace("杭州",30.27,120.15);val another=WeatherPlace("南京",32.06,118.79)
        try {
            repo.load(city);repo.load(city,true);assertEquals(1,calls)
            assertNotNull(repo.cached(city));assertNull(repo.cached(another))
            repo.load(another);assertEquals(2,calls);assertNull(repo.cached(city))
        } finally {folder.deleteRecursively()}
    }
    @Test fun incompleteOrOutOfRangeWeatherNeverBecomesARecommendation() {
        val city=WeatherPlace("杭州",30.27,120.15);val now=System.currentTimeMillis()
        val valid=WeatherRepository.parse(city,JSONObject(weather()),now);assertTrue(valid.fresh(now));assertFalse(valid.fresh(now+4*60*60*1000))
        val invalid=JSONObject(weather());invalid.getJSONObject("current").put("temperature_2m",999)
        assertTrue(runCatching {WeatherRepository.parse(city,invalid,now)}.isFailure)
        invalid.getJSONObject("current").remove("temperature_2m")
        assertTrue(runCatching {WeatherRepository.parse(city,invalid,now)}.isFailure)
    }
    @Test fun networkFailureKeepsLastKnownCacheButDifferentCityCannotUseIt()=runBlocking {
        val base=ApplicationProvider.getApplicationContext<Context>();val folder=File(base.cacheDir,"weather-failure-${System.nanoTime()}").apply {mkdirs()}
        val context=object:ContextWrapper(base) {override fun getCacheDir()=folder}
        val city=WeatherPlace("杭州",30.27,120.15)
        try {
            WeatherRepository(context) {weather()}.load(city)
            val failure=WeatherRepository(context) {error("offline")}
            assertTrue(runCatching {failure.load(WeatherPlace("南京",32.06,118.79))}.isFailure)
            assertEquals(21.0,failure.cached(city)!!.temperature,0.01)
        } finally {folder.deleteRecursively()}
    }
}
