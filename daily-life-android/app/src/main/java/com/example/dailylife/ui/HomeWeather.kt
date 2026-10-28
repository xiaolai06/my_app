package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.home.HomeViewModel
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun HomeWeather(ui:WeatherUi,vm:HomeViewModel) {
    var configure by remember {mutableStateOf(false)}
    HomeWeatherCard(ui,{configure=true},{vm.refreshWeather(true)})
    if(configure) WeatherSettingsSheet(ui,vm) {configure=false}
}

@Composable internal fun HomeWeatherCard(ui:WeatherUi,configure:()->Unit,refresh:()->Unit) {
    val p=LocalGlass.current
    val reading=ui.reading?.takeIf {ui.settings.enabled && it.place.key==ui.settings.place?.key}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(reading) {while(true) {now=System.currentTimeMillis();kotlinx.coroutines.delay(60000)}}
    GlassPanel(Modifier.fillMaxWidth().testTag("weatherCard")) {
        Column(Modifier.background(Brush.linearGradient(if(LocalHomeColor.current) listOf(p.accent.copy(alpha=.09f),Color(0xFF9C8BCB).copy(alpha=.08f),Color.Transparent) else listOf(Color.Transparent,Color.Transparent))).padding(start=20.dp,end=12.dp,top=8.dp,bottom=8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.Place,null,Modifier.size(18.dp),tint=p.accent)
                Text(if(ui.settings.enabled) ui.settings.place?.name.orEmpty().substringBefore(" · ") else "选择城市",
                    Modifier.weight(1f).padding(start=6.dp),style=MaterialTheme.typography.titleMedium)
                IconButton(enabled=ui.settings.enabled && !ui.loading,onClick=refresh,modifier=Modifier.size(48.dp).testTag("refreshWeather")) {
                    if(ui.loading) CircularProgressIndicator(Modifier.size(19.dp),strokeWidth=2.dp)
                    else Icon(Icons.Default.Refresh,"刷新天气",Modifier.size(21.dp),tint=p.accent)
                }
            }
            if(reading!=null) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(end=8.dp)) {
                    val compact=LocalDensity.current.fontScale>1.3f || maxWidth<260.dp
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${kotlin.math.round(reading.temperature).toInt()}°",style=MaterialTheme.typography.displayMedium.copy(fontSize=if(compact) 42.sp else 60.sp,lineHeight=if(compact) 52.sp else 68.sp,fontWeight=FontWeight.Light,letterSpacing=(-2).sp))
                            Text(weatherDescription(reading.code),style=MaterialTheme.typography.titleMedium)
                            Text("体感 ${kotlin.math.round(reading.apparent).toInt()}°",color=p.muted,style=MaterialTheme.typography.bodySmall)
                        }
                        WeatherArtwork(reading.code,reading.isDay,Modifier.size(if(compact) 80.dp else 128.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top=16.dp,end=8.dp)) {
                    val metrics:@Composable ()->Unit = {
                        WeatherMetric("今日温度","${kotlin.math.round(reading.low).toInt()}° / ${kotlin.math.round(reading.high).toInt()}°")
                        WeatherMetric("降雨概率","${reading.rainChance}%")
                    }
                    if(LocalDensity.current.fontScale>1.3f) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {metrics()}
                    else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {WeatherMetric("今日温度","${kotlin.math.round(reading.low).toInt()}° / ${kotlin.math.round(reading.high).toInt()}°")}
                        Box(Modifier.weight(1f)) {WeatherMetric("降雨概率","${reading.rainChance}%")}
                    }
                }
            } else Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(!ui.settings.enabled) "让天气陪你安排一天" else if(ui.loading) "正在获取天气…" else "暂时没有天气数据",style=MaterialTheme.typography.titleMedium)
                    if(!ui.settings.enabled) TextButton(onClick=configure,contentPadding=PaddingValues(vertical=8.dp)) {Text("选择城市并开启")}
                }
                // Neutral cloud illustration is decoration, never a fabricated condition or temperature.
                WeatherArtwork(null,true,Modifier.size(80.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically) {
                val time=reading?.let {Instant.ofEpochMilli(it.observedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if(it.fresh(now)) "HH:mm" else "MM-dd HH:mm"))}
                val status=when {
                    !ui.settings.enabled -> "不读取定位"
                    ui.error!=null && reading!=null -> "更新失败 · 缓存 $time"
                    ui.error!=null -> "暂时无法更新，点击右上角重试"
                    reading!=null && !reading.fresh(now) -> "数据已过期 · $time"
                    ui.loading -> "正在更新…"
                    reading!=null -> "$time 更新"
                    else -> "等待更新"
                }
                Text(status,Modifier.weight(1f),color=p.muted,style=MaterialTheme.typography.bodySmall)
                IconButton(onClick=configure,modifier=Modifier.size(48.dp).testTag("weatherSettings")) {Icon(Icons.Default.Settings,"天气设置",Modifier.size(20.dp),tint=p.muted)}
            }
        }
    }
}
@Composable private fun WeatherMetric(label:String,value:String) {
    Column(Modifier.fillMaxWidth().background(LocalGlass.current.sheet.copy(alpha=.55f),RoundedCornerShape(16.dp)).padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
        Text(value,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable internal fun WeatherSettingsSheet(ui:WeatherUi,vm:HomeViewModel,dismiss:()->Unit) {
    var query by remember {mutableStateOf("")};var manual by remember {mutableStateOf(false)}
    var latitude by remember {mutableStateOf("")};var longitude by remember {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)}
    val links=LocalUriHandler.current
    GlassSheet("城市与天气",dismiss) {
        Text("天气为可选联网功能。查询会把城市名称或城市坐标发送给 Open-Meteo；日记、物品和穿搭不会发送。免费接口用于个人非商业用途，断网时显示缓存。",color=LocalGlass.current.muted)
        TextButton(onClick={links.openUri("https://open-meteo.com/en/terms")}) {Text("Open-Meteo · CC BY 4.0 数据与条款")}
        if(ui.settings.enabled) TextButton(onClick={vm.setWeather(ui.settings.copy(enabled=false),dismiss)}) {Text("关闭天气联网")}
        ToolField("城市",query,{query=it},tag="weatherCity")
        GlassButton(enabled=!ui.searching,onClick={vm.searchCities(query)}) {Text(if(ui.searching) "查询中…" else "查询城市")}
        ui.places.forEach {place->TextButton(onClick={vm.setWeather(WeatherSettings(true,place),dismiss)}) {Text(place.name+" · 开启天气")}}
        ui.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        TextButton(onClick={manual=!manual}) {Text("手动填写城市坐标")}
        if(manual) {
            ToolField("城市纬度（-90～90）",latitude,{latitude=it})
            ToolField("城市经度（-180～180）",longitude,{longitude=it})
            GlassButton(onClick={
                runCatching {WeatherPlace(query.trim(),latitude.toDouble(),longitude.toDouble()).also {it.validate()}}
                    .onSuccess {vm.setWeather(WeatherSettings(true,it),dismiss)}.onFailure {error="请填写城市和有效经纬度"}
            }) {Text("保存并开启天气")}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
}
