package com.example.dailylife.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.data.local.PlanReminder
import com.example.dailylife.data.repository.reminderLabel
import com.example.dailylife.feature.plans.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun ReminderCenter(ui:PlansUi,vm:PlansViewModel,focusNotifications:Boolean,setFocusNotifications:(Boolean)->Unit,
    requestNotifications:()->Unit,dismiss:()->Unit,open:(PlanReminder)->Unit) {
    val context=LocalContext.current;val app=context.applicationContext as DailyLifeApplication
    var scope by remember {mutableStateOf("future")};var category by remember {mutableStateOf("all")};var error by remember {mutableStateOf<String?>(null)}
    var showSettings by remember {mutableStateOf(false)}
    fun launch(intent:Intent) {runCatching {context.startActivity(intent)}.onFailure {error="此手机无法打开该设置，可从系统设置中的 My Life 应用信息进入"}}
    GlassSheet("提醒中心",dismiss) {
        Text(if(app.planAlarms.enabled()) "系统通知已开启" else "系统通知未开启",style=MaterialTheme.typography.titleMedium)
        TextButton(onClick={showSettings=!showSettings},modifier=Modifier.semantics {stateDescription=if(showSettings) "已展开" else "已收起"}) {Text("显示与权限")}
        if(showSettings) {
            SettingRow("专注计时通知","在通知栏查看计时，可暂停、继续或收起",focusNotifications,setFocusNotifications)
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                TextButton(onClick=requestNotifications) {Text("允许通知权限")}
            TextButton(onClick={launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) {Text("系统通知与横幅设置")}
            Text(app.planAlarms.timingDescription(),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            if(Build.VERSION.SDK_INT>=31) TextButton(onClick={launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+context.packageName)))}) {Text("管理准点提醒权限")}
            Text("通知声音、横幅和锁屏显示由手机设置控制。省电限制可能延迟提醒。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        }
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            GlassButton(enabled=!ui.busy,onClick={dismiss();vm.begin("task")}) {Text("新建提醒事项")}
            TextButton(enabled=!ui.busy,onClick={vm.refresh()}) {Text("重新核对提醒")}
        }
        FlowChoices(listOf("future" to "接下来","history" to "已到时间"),scope) {scope=it}
        FlowChoices(listOf("all" to "全部","task" to "任务","event" to "日程","habit" to "习惯","focus" to "专注","tool" to "生活"),category) {category=it}
        val now=System.currentTimeMillis()
        val jobs=ui.data.reminders.filter {j->(category=="all" || j.sourceType==category) && if(scope=="future") (j.deferredUntil ?: j.triggerAt)>now && j.state!="cancelled" else (j.deferredUntil ?: j.triggerAt)<=now}
            .let {if(scope=="history") it.sortedByDescending {j->j.deferredUntil ?: j.triggerAt} else it.sortedBy {j->j.deferredUntil ?: j.triggerAt}}
        if(jobs.isEmpty()) Text("暂无提醒；在事项、订阅或物品中打开提醒即可。",color=LocalGlass.current.muted)
        jobs.take(60).forEach {j->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(j.title,style=MaterialTheme.typography.titleMedium)
            Text(reminderLabel(j),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            Text(Instant.ofEpochMilli(j.deferredUntil ?: j.triggerAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")))
            Text(if(j.readAt!=null) "已查看" else mapOf("scheduled" to "已安排","snoozed" to "稍后提醒","posted" to "已交给系统通知","blocked" to "通知权限未开","cancelled" to "已撤销","expired" to "已过期，未补弹","failed" to "排程失败，请重新核对")[j.state] ?: "待安排",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow {TextButton(onClick={open(j);vm.acknowledgeReminder(j.id);dismiss()}) {Text("查看事项")}
                if(j.readAt==null && j.state in setOf("posted","blocked") && (j.deferredUntil ?: j.triggerAt)<=now) {
                    TextButton(enabled=!ui.busy,onClick={vm.snoozeReminder(j.id,15)}) {Text("15 分钟后")}
                    TextButton(enabled=!ui.busy,onClick={vm.snoozeReminder(j.id,60)}) {Text("1 小时后")}
                    TextButton(enabled=!ui.busy,onClick={vm.acknowledgeReminder(j.id)}) {Text("知道了")}
                }
            }
        }}}
        if(jobs.size>60) Text("显示最近 60 条；可按类别筛选。",color=LocalGlass.current.muted)
    }
}
