package com.example.dailylife.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.data.local.Occurrence
import com.example.dailylife.feature.plans.*
import java.time.*
import java.time.format.DateTimeFormatter

internal data class PlanReminderSummary(val label:String,val detail:String,val attention:Boolean=false)

/** Configuration and actual delivery state are deliberately separate. */
internal fun planReminderSummary(s:PlansSnapshot,kind:String,id:String,occurrence:Occurrence?=null,now:Long=System.currentTimeMillis()):PlanReminderSummary {
    val configured=when(kind) {
        "task"->s.tasks.find {it.id==id}?.let {it.reminderMinutes!=null || occurrence==null && it.reminderAt!=null && s.rules.none {r->r.entityType=="task" && r.entityId==id}}==true
        "event"->s.events.find {it.id==id}?.reminderMinutes!=null
        "habit"->s.habits.find {it.id==id}?.reminderTime!=null
        "focus"->s.sessions.find {it.id==id}?.let {it.mode!="stopwatch"}==true
        else->false
    }
    if(!configured) return PlanReminderSummary(if(kind=="goal") "目标不单独提醒" else "未设提醒",
        if(kind=="goal") "使用关联任务的提醒" else "")
    val all=s.reminders.filter {it.sourceType==kind && it.sourceId==id && (occurrence==null || it.instanceKey==occurrence.originalKey) && it.state!="cancelled"}
    val future=all.filter {(it.deferredUntil ?: it.triggerAt)>now}.sortedBy {it.deferredUntil ?: it.triggerAt}
    val job=future.firstOrNull {it.state in setOf("blocked","failed")} ?: future.firstOrNull() ?: all.maxByOrNull {it.deferredUntil ?: it.triggerAt}
    if(job==null) return PlanReminderSummary("暂无待发提醒","配置已保留，当前没有待发通知")
    val at=Instant.ofEpochMilli(job.deferredUntil ?: job.triggerAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    return when(job.state) {
        "blocked"->PlanReminderSummary("通知未开启","$at · 请允许系统通知",true)
        "failed"->PlanReminderSummary("提醒待重试","$at · 请重新核对",true)
        "scheduled"->PlanReminderSummary("提醒已安排",at)
        "snoozed"->PlanReminderSummary("稍后提醒",at)
        "posted"->PlanReminderSummary("已交给系统","$at · 显示由手机设置控制")
        "expired"->PlanReminderSummary("提醒已过期","$at · 未补发")
        else->PlanReminderSummary("提醒待安排",at)
    }
}

@Composable internal fun PlanNotificationAccess(vm:PlansViewModel,showDescription:Boolean=true) {
    val context=LocalContext.current;val app=context.applicationContext as DailyLifeApplication
    val lifecycle=LocalLifecycleOwner.current
    var revision by remember {mutableIntStateOf(0)};var error by remember {mutableStateOf<String?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {revision++;vm.refresh()}
    DisposableEffect(lifecycle) {
        var leftApp=false
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_PAUSE) leftApp=true
            if(event==Lifecycle.Event.ON_RESUME && leftApp) {leftApp=false;revision++;vm.refresh()}
        }
        lifecycle.lifecycle.addObserver(observer);onDispose {lifecycle.lifecycle.removeObserver(observer)}
    }
    val enabled=remember(revision) {app.planAlarms.enabled()}
    if(showDescription && !enabled) Text("通知未开启，提醒不会弹出",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    if(!enabled) TextButton(onClick={
        if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else runCatching {context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
            .onFailure {error="请在手机设置中打开本应用的通知。"}
    }) {Text("开启系统通知")}
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
}

@Composable internal fun PlanReminderStatus(ui:PlansUi,vm:PlansViewModel,kind:String,id:String,occurrence:Occurrence?) {
    val summary=planReminderSummary(ui.data,kind,id,occurrence)
    GlassPanel(Modifier.fillMaxWidth().testTag("planReminderStatus")) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                PlanGlyph("bell");Text(summary.label,style=MaterialTheme.typography.titleMedium,color=if(summary.attention) MaterialTheme.colorScheme.error else LocalGlass.current.accent)
            }
            if(summary.detail.isNotBlank()) Text(summary.detail,style=MaterialTheme.typography.bodyMedium,color=LocalGlass.current.muted)
            if(summary.label !in setOf("未设提醒","目标不单独提醒")) PlanNotificationAccess(vm,summary.label!="通知未开启")
            if(summary.label in setOf("提醒待重试","提醒待安排")) TextButton(onClick={vm.refresh()},enabled=!ui.busy) {Text("重新核对提醒")}
        }
    }
}

@Composable internal fun PlanReminderEditor(f:PlanForm,vm:PlansViewModel,editable:Boolean=true) {
    if(f.kind !in setOf("task","event","habit")) return
    val focus=LocalFocusManager.current
    var expanded by remember(f.id) {mutableStateOf(false)}
    var customMinutes by remember(f.id) {mutableStateOf(f.text("minutes").isNotBlank() && f.text("minutes") !in setOf("0","15","60"))}
    var datePicker by remember {mutableStateOf(false)};var timePicker by remember {mutableStateOf(false)}
    val enabled=f.text("minutes").isNotBlank() || f.text("reminder").isNotBlank()
    val independent=f.kind=="task" && f.text("repeat","none")=="none" && f.text("reminder").isNotBlank()
    val inactiveIndependent=f.kind=="task" && f.text("repeat","none")!="none" && f.text("reminder").isNotBlank()
    val stamp=runCatching {LocalDateTime.parse(f.text("reminder"))}.getOrNull() ?: LocalDate.now().plusDays(1).atTime(9,0)
    val label=when {
        !enabled->"未开启"
        inactiveIndependent && f.text("minutes").isBlank()->"需要设置重复提醒"
        f.kind=="habit"->"应做日 "+f.text("reminder")
        independent->stamp.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))+(if(f.text("minutes").isNotBlank()) " · 另有相对提醒" else "")
        f.text("minutes")=="0"->"到时间提醒"
        else->"提前 "+f.text("minutes")+" 分钟"
    }
    GlassPanel(Modifier.fillMaxWidth().testTag("planReminderConfig")) {
        Column(Modifier.padding(horizontal=14.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(enabled=editable,onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("editPlanReminder"),contentPadding=PaddingValues(0.dp)) {
                PlanGlyph("bell");Spacer(Modifier.width(12.dp))
                Text("提醒",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=LocalGlass.current.ink)
                Text(label,Modifier.weight(1.4f).padding(end=8.dp),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                PlanGlyph(if(expanded) "up" else "down",Modifier.size(16.dp),LocalGlass.current.muted)
            }
            if(inactiveIndependent) Text("重复任务请设置到点或提前提醒",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            if(expanded) {
                if(f.kind=="habit") {
                    FlowChoices(listOf("" to "不提醒","09:00" to "早上 9:00","20:00" to "晚上 8:00"),f.text("reminder")) {vm.edit(f.set("reminder",it))}
                    TextButton(onClick={focus.clearFocus();timePicker=true},modifier=Modifier.testTag("customReminderTime")) {Text("选择其他时刻")}
                } else {
                    FlowChoices(listOf("" to "不提醒","0" to "到点","15" to "提前15分钟","60" to "提前1小时"),if(independent) "custom" else f.text("minutes")) {
                        customMinutes=false
                        vm.edit(f.set("minutes",it).set("reminder",""))
                    }
                    if(f.text("minutes").isNotBlank()) {
                        if(f.kind=="task" && f.text("due").isBlank() && f.text("planned").isBlank())
                            Text("请先设置计划日期或截止日期",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                        else Text(when {
                            f.kind=="event"->if(f.text("allDay")=="true") "当天 09:00" else "按开始时间"
                            f.text("due").isNotBlank()->"按截止日期 · "+f.text("dueTime").ifBlank {"09:00"}
                            else->"按计划日期 · 09:00"
                        },style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                    }
                    if(customMinutes) PlanField(f,"minutes","提前分钟数",vm)
                    else TextButton(enabled=editable,onClick={customMinutes=true}) {Text("自定义提前时间")}
                    if(f.kind=="task" && f.text("repeat","none")=="none") {
                        TextButton(onClick={focus.clearFocus();datePicker=true}) {Text("指定提醒日期与时间")}
                        if(independent) {
                            DateRow("提醒日期",stamp.toLocalDate().toString(),{focus.clearFocus();datePicker=true})
                            TextButton(onClick={focus.clearFocus();timePicker=true},modifier=Modifier.testTag("customReminderTime")) {Text("提醒时刻 · "+stamp.toLocalTime())}
                        }
                    }
                }
                if(enabled) PlanNotificationAccess(vm)
            }
        }
    }
    if(datePicker) PlanDatePicker(stamp.toLocalDate().toString(),{datePicker=false}) {vm.edit(f.set("minutes","").set("reminder",LocalDate.parse(it).atTime(stamp.toLocalTime()).toString()))}
    if(timePicker) PlanTimeDialog(if(f.kind=="habit") f.text("reminder","20:00") else stamp.toLocalTime().toString(),{timePicker=false}) {
        vm.edit(f.set("reminder",if(f.kind=="habit") it else stamp.toLocalDate().atTime(LocalTime.parse(it)).toString()))
    }
}

@Composable internal fun PlanTimeDialog(value:String,dismiss:()->Unit,select:(String)->Unit) {
    GlassTimePicker(value,dismiss,select)
}

@Composable internal fun PlanTimeRow(label:String,value:String,change:(String)->Unit,clear:(()->Unit)?=null) {
    var open by remember {mutableStateOf(false)}
    DateRow(label,value,{open=true},clear)
    if(open) PlanTimeDialog(value,{open=false},change)
}

@Composable internal fun PlanDatePicker(value:String,dismiss:()->Unit,marked:Set<String> = emptySet(),choose:(String)->Unit) {
    GlassDatePicker(value,dismiss,marked,"确定日期","confirmPlanDate","cancelPlanDate",choose=choose)
}
