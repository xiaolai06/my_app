package com.example.dailylife.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailylife.feature.plans.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun FocusPane(ui:PlansUi,vm:PlansViewModel) {
    var range by rememberSaveable {mutableStateOf("week")}
    val active=ui.data.sessions.firstOrNull {it.deletedAt==null && it.state in setOf("running","paused","review","awaiting")}
    val today=LocalDate.now()
    val start=when(range) {"day"->today;"week"->today.with(DayOfWeek.MONDAY);else->today.withDayOfMonth(1)}
    val history=ui.data.sessions.filter {it.deletedAt==null && it.endedAt!=null && Instant.ofEpochMilli(it.endedAt).atZone(ZoneId.systemDefault()).toLocalDate() in start..today}
        .sortedByDescending {it.endedAt}
    val effective=history.filter {it.state in setOf("finished","early")}.map {it.id}.toSet()
    val largeFont=LocalDensity.current.fontScale>1.3f
    LazyColumn(Modifier.fillMaxSize().testTag("planFocusList"),contentPadding=PaddingValues(18.dp,8.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(active!=null) item(key="active") {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Text(active.title,Modifier.fillMaxWidth(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Medium)
                    FocusControls(active,ui,vm,{})
                    TextButton(onClick={vm.detail("focus",active.id)},modifier=Modifier.fillMaxWidth().testTag("focusDetails")) {Text("记录详情")}
                }
            }
        } else item {PlanQuietEmpty("开始一段专注时间")}
        item(key="range") {PlanInlineTabs(listOf("day" to "今天","week" to "本周","month" to "本月"),range,tagPrefix="focusRange") {range=it}}
        item(key="summary") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("专注记录",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                Text(planQuantity(ui.data.focusRange(effective,start,today.plusDays(1))*1000,"秒",true),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            }
        }
        if(history.isEmpty()) item {PlanQuietEmpty("暂无记录")}
        items(history,key={it.id}) {session->
            GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail("focus",session.id)}.testTag("focusHistory:"+session.id)) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    PlanGlyph("focus")
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(session.title,style=MaterialTheme.typography.titleMedium)
                        val date=Instant.ofEpochMilli(session.endedAt!!).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm",Locale.CHINA))
                        Text(date+" · "+stateName(session.state),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                        if(largeFont) Text(planQuantity(ui.data.focusSeconds(session.id)*1000,"秒",true),style=MaterialTheme.typography.labelLarge,color=LocalGlass.current.accent)
                    }
                    if(!largeFont) Text(planQuantity(ui.data.focusSeconds(session.id)*1000,"秒",true),style=MaterialTheme.typography.labelLarge,color=LocalGlass.current.accent)
                }
            }
        }
    }
}
