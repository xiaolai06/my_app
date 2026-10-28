package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.tools.ToolsSnapshot
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun dayEvents(s:PlansSnapshot,date:String)=s.agendaOn(LocalDate.parse(date)).events.map {it.event to it.occurrence}

@Composable internal fun CalendarPane(ui:PlansUi,vm:PlansViewModel,date:String,select:(String)->Unit,tools:ToolsSnapshot,openTool:(String)->Unit) {
    val selected=date.takeIf {it.isNotBlank()}?.let(LocalDate::parse)
    var monthText by rememberSaveable {mutableStateOf(YearMonth.from(selected ?: LocalDate.now()).toString())}
    var mode by rememberSaveable {mutableStateOf("month")}
    var filter by rememberSaveable {mutableStateOf("all")}
    var picker by remember {mutableStateOf(false)}
    var monthPicker by remember {mutableStateOf(false)}
    val month=YearMonth.parse(monthText)
    val largeFont=LocalDensity.current.fontScale>1.3f
    LaunchedEffect(date) {if(selected!=null) monthText=YearMonth.from(selected).toString()}
    val agenda=remember(ui.data,selected) {selected?.let(ui.data::agendaOn)}
    val toolRows=remember(tools,selected) {selected?.let {toolCalendarEntries(tools,it)}.orEmpty()}
    val marked=remember(ui.data,tools,month) {ui.data.agendaDates(month)+toolCalendarDates(tools,month.atDay(1),month.atEndOfMonth())}
    fun moveMonth(amount:Long) {monthText=month.plusMonths(amount).toString();select("")}
    val count=(if(filter in setOf("all","task")) agenda?.tasks?.size ?: 0 else 0)+
        (if(filter in setOf("all","event")) agenda?.events?.size ?: 0 else 0)+(if(filter in setOf("all","tool")) toolRows.size else 0)

    LazyColumn(Modifier.fillMaxSize().testTag("planCalendar"),contentPadding=PaddingValues(18.dp,8.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item(key="calendar") {GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(enabled=month>YearMonth.of(1,1),onClick={moveMonth(-1)},modifier=Modifier.testTag("planPreviousMonth")) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,"上个月")}
                    TextButton(onClick={monthPicker=true},modifier=Modifier.weight(1f).testTag("planCalendarMonth")) {
                        if(largeFont) Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("${month.year}年",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                            Text("${month.monthValue}月",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        } else Text("${month.year}年 ${month.monthValue}月",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        PlanGlyph("down",Modifier.padding(start=4.dp).size(16.dp))
                    }
                    IconButton(enabled=month<YearMonth.of(9999,12),onClick={moveMonth(1)},modifier=Modifier.testTag("planNextMonth")) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,"下个月")}
                }
                if(mode=="month") GlassCalendarMonth(month,selected,marked,Modifier.testTag("planFullMonth")) {select(it.toString())}
                else PlanQuickWeek(selected ?: month.atDay(1),selected,marked) {select(it.toString())}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    PlanInlineTabs(listOf("month" to "月历","week" to "周览"),mode,Modifier.weight(1f),"planCalendarMode") {mode=it}
                    TextButton(onClick={select(LocalDate.now().toString())},modifier=Modifier.testTag("planCalendarToday")) {Text("今天")}
                    IconButton(onClick={picker=true},modifier=Modifier.testTag("planChooseDate").semantics {contentDescription="选择日期"}) {PlanGlyph("date")}
                }
            }
        }}
        item(key="filters") {PlanInlineTabs(listOf("all" to "全部","task" to "任务","event" to "日程","tool" to "生活提醒"),filter,tagPrefix="planAgendaFilter") {filter=it}}
        item(key="selected day") {
            Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(selected?.format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.CHINA)) ?: "未选择日期",Modifier.weight(1f),
                    style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                if(selected!=null) Text("$count 项",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            }
        }
        if(selected==null || count==0) item {PlanQuietEmpty(if(selected==null) "请选择日期" else "暂无事项",Modifier.testTag("planAgendaEmpty"))}
        if(selected!=null && agenda!=null) {
            if(filter in setOf("all","event") && agenda.events.isNotEmpty()) {
                item {PlanSectionHeading("日程",agenda.events.size)}
                items(agenda.events,key={"event:"+it.key}) {row->PlanEventRow(row,ui,vm)}
            }
            if(filter in setOf("all","task")) {
                listOf("待办任务" to agenda.pendingTasks,"已完成" to agenda.completedTasks,"已取消与跳过" to agenda.cancelledTasks).forEach {(label,rows)->
                    if(rows.isNotEmpty()) {
                        item {PlanSectionHeading(label,rows.size)}
                        items(rows,key={"task:"+it.key}) {TaskCard(it,ui,vm)}
                    }
                }
            }
            if(filter in setOf("all","tool") && toolRows.isNotEmpty()) {
                item {PlanSectionHeading("生活提醒",toolRows.size)}
                items(toolRows,key={"tool:"+it.key}) {row->GlassPanel(Modifier.fillMaxWidth().clickable {openTool(row.sourceId)}.testTag("calendarTool:"+row.key)) {
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        PlanGlyph("bell")
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(row.title,style=MaterialTheme.typography.titleMedium)
                            Text(row.kind+" · "+row.description,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                        }
                    }
                }}
            }
        }
    }
    if(picker) GlassDatePicker((selected ?: month.atDay(1)).toString(),{picker=false},marked,
        confirmLabel="选择日期",confirmTag="confirmPlanDate",cancelTag="cancelPlanDate") {select(it)}
    if(monthPicker) GlassMonthPicker(monthText,{monthPicker=false}) {monthText=it;select("")}
}

@Composable private fun PlanQuickWeek(anchor:LocalDate,selected:LocalDate?,marked:Set<String>,choose:(LocalDate)->Unit) {
    val start=anchor.with(DayOfWeek.MONDAY)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().testTag("planQuickWeek"),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        repeat(7) {index->val day=start.plusDays(index.toLong())
            Column(Modifier.widthIn(min=48.dp).selectable(day==selected,role=Role.RadioButton) {choose(day)}
                .testTag("planWeekDay:$day").padding(horizontal=8.dp,vertical=10.dp)
                .semantics {contentDescription=day.toString()+(if(day.toString() in marked) " 有事项" else "")},horizontalAlignment=Alignment.CenterHorizontally) {
                Text(listOf("一","二","三","四","五","六","日")[index],style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.muted)
                Text(day.dayOfMonth.toString(),fontWeight=if(day==selected) FontWeight.Bold else FontWeight.Normal,color=if(day==selected) LocalGlass.current.accent else LocalGlass.current.ink)
                Text(if(day.toString() in marked) "•" else " ",style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.accent)
            }
        }
    }
}

@Composable private fun PlanEventRow(row:PlanAgendaEvent,ui:PlansUi,vm:PlansViewModel) {
    val event=row.event
    GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail("event",event.id,row.occurrence?.id)}.testTag("planEvent:"+row.key)) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            PlanGlyph("event")
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(row.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                val time=if(event.allDay) "全天" else event.startTime+"–"+event.endTime
                val span=if(row.endDateExclusive>row.startDate.plusDays(1)) " · "+planDateLabel(row.startDate.toString())+"–"+planDateLabel(row.endDateExclusive.minusDays(1).toString()) else ""
                Text(time+span+event.location.takeIf {it.isNotBlank()}?.let {" · $it"}.orEmpty(),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                if(event.zoneId!=ZoneId.systemDefault().id) Text(event.zoneId,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                if(row.status=="cancelled") Text("已取消",style=MaterialTheme.typography.labelMedium,color=LocalGlass.current.muted)
            }
            PlanReminderMark(planReminderSummary(ui.data,"event",event.id,row.occurrence))
        }
    }
}
