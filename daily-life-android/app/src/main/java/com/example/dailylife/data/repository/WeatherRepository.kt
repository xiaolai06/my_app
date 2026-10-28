package com.example.dailylife.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.io.File

data class WeatherPlace(val name:String,val latitude:Double,val longitude:Double) {
    val key get()="$latitude,$longitude"
    fun validate() {require(name.isNotBlank() && name.length<=100 && latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {"城市或坐标无效"}}
}
data class WeatherSettings(val enabled:Boolean=false,val place:WeatherPlace?=null)
data class WeatherReading(val place:WeatherPlace,val fetchedAt:Long,val observedAt:Long,val temperature:Double,
    val apparent:Double,val code:Int,val low:Double,val high:Double,val rainChance:Int,val isDay:Boolean=true) {
    fun fresh(now:Long=System.currentTimeMillis())=now>=fetchedAt && now-fetchedAt<=3*60*60*1000L && observedAt<=now+15*60*1000L && now-observedAt<=4*60*60*1000L
}
data class WeatherUi(val settings:WeatherSettings=WeatherSettings(),val reading:WeatherReading?=null,
    val loading:Boolean=false,val error:String?=null,val places:List<WeatherPlace> = emptyList(),val searching:Boolean=false)

/** Optional city-level network access. Never sends records, wardrobe data or device location. */
class WeatherRepository(context:Context,private val nowMillis:()->Long=System::currentTimeMillis,private val fetch:(String)->String=::weatherHttp) {
    private val cache=File(context.cacheDir,"weather-v1.json")
    private val lock=Mutex()
    suspend fun search(query:String):List<WeatherPlace> = withContext(Dispatchers.IO) {
        require(query.trim().length in 2..80) {"请输入 2～80 字的城市名称"}
        val j=JSONObject(fetch("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(query.trim(),"UTF-8")+"&count=6&language=zh&format=json"))
        val a=j.optJSONArray("results") ?: return@withContext emptyList()
        (0 until a.length()).map {a.getJSONObject(it)}.map {r->
            WeatherPlace(listOf(r.getString("name"),r.optString("admin1"),r.optString("country")).filter {it.isNotBlank()}.distinct().joinToString(" · ").take(100),r.getDouble("latitude"),r.getDouble("longitude")).also {it.validate()}
        }
    }
    suspend fun cached(place:WeatherPlace):WeatherReading? = withContext(Dispatchers.IO) {
        runCatching {val j=JSONObject(cache.readText());if(j.getString("placeKey")!=place.key) null else parse(place,j.getJSONObject("data"),j.getLong("fetchedAt"))}.getOrNull()
    }
    suspend fun load(place:WeatherPlace,force:Boolean=false):WeatherReading=lock.withLock {withContext(Dispatchers.IO) {
        place.validate();val now=nowMillis();val old=cached(place)
        // A manual refresh still respects a short interval to prevent accidental request bursts.
        if(old!=null && old.fresh(now) && now-old.fetchedAt<(if(force) 60000 else 15*60000)) return@withContext old
        val raw=fetch("https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,apparent_temperature,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=1&timezone=auto&timeformat=unixtime")
        val data=JSONObject(raw);val reading=parse(place,data,now)
        val saved=JSONObject().put("placeKey",place.key).put("fetchedAt",now).put("data",data)
        val temporary=File(cache.parentFile,"weather-v1.tmp")
        runCatching {temporary.writeText(saved.toString());if(!temporary.renameTo(cache)) {cache.writeText(saved.toString());temporary.delete()}}
        reading
    }}
    companion object {
        fun parse(place:WeatherPlace,j:JSONObject,now:Long):WeatherReading {
            require(!j.optBoolean("error")) {"天气服务暂不可用"}
            val current=j.getJSONObject("current");val daily=j.getJSONObject("daily")
            val t=current.getDouble("temperature_2m");val apparent=current.getDouble("apparent_temperature")
            val low=daily.getJSONArray("temperature_2m_min").getDouble(0);val high=daily.getJSONArray("temperature_2m_max").getDouble(0)
            val rain=daily.getJSONArray("precipitation_probability_max").getInt(0)
            require(listOf(t,apparent,low,high).all {it.isFinite() && it in -100.0..70.0} && low<=high && rain in 0..100) {"天气数据不完整，请稍后重试"}
            return WeatherReading(place,now,current.getLong("time")*1000,t,apparent,current.getInt("weather_code"),low,high,rain,current.optInt("is_day",1)==1)
        }
    }
}
private fun weatherHttp(url:String):String {
    val connection=URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout=10000;connection.readTimeout=10000;connection.instanceFollowRedirects=false
    connection.setRequestProperty("Accept","application/json")
    try {
        require(connection.responseCode==200) {"天气服务暂时无法连接，请稍后重试"}
        return connection.inputStream.use {stream->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val count=stream.read(buffer);if(count<0) break;require(output.size()+count<=256*1024) {"天气响应过大"};output.write(buffer,0,count)}
            output.toString("UTF-8")
        }
    } finally {connection.disconnect()}
}
fun weatherDescription(code:Int)=when(code) {0->"晴";1,2->"少云";3->"阴";45,48->"雾";51,53,55,56,57->"毛毛雨";61,63,65,66,67,80,81,82->"有雨";71,73,75,77,85,86->"有雪";95,96,99->"雷雨";else->"天气变化中"}
