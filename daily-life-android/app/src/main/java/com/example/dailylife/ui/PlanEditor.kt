package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.PlanRules
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.feature.plans.*
import org.json.*
import java.time.*

@Composable fun PlanEditor(ui:PlansUi,vm:PlansViewModel,records:RecordsSnapshot,addFiles:()->Unit,view:(Attachment)->Unit) {
    val f=ui.editor ?: return
    var leave by remember {mutableStateOf(false)}
    var preview by remember {mutableStateOf(false)}
    var calendar by remember {mutableStateOf<String?>(null)}
    var chooser by remember {mutableStateOf<String?>(null)}
    val child=f.text("pendingChild")
    fun set(k:String,v:String)=vm.edit(f.set(k,v))
    BackHandler(enabled=!ui.busy) {leave=true}
    Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),
        bottomBar={GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp)) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                ui.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                if(ui.error?.contains("保留并保存")==true) TextButton(enabled=!ui.busy,onClick={vm.save(true)}) {Text("确认保留并保存")}
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={preview=!preview}) {Text(if(preview) "编辑" else "预览备注")}
                    if(f.kind!="focus") TextButton(enabled=!ui.busy,onClick=addFiles) {Text("图片与文件")}
                    if(f.kind!="focus") TextButton(enabled=!ui.busy,onClick={chooser="file"}) {Text("已有附件")}
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(ui.saved) "草稿已暂存本机" else "正在暂存…",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.muted)
                    TextButton(enabled=!ui.busy,onClick={vm.keep()}) {Text("稍后继续")}
                    Button(enabled=!ui.busy,onClick={vm.save()},modifier=Modifier.testTag("savePlan")) {Text(if(f.kind=="focus") "开始计时" else "保存")}
                }
            }
        }}) {padding->
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
            Row(Modifier.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(enabled=!ui.busy,onClick={leave=true}) {Text("返回")}
                Text((if(f.revision==0L) "新建" else "编辑")+planNames.first {it.first==f.kind}.second,style=MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("planEditor"),
                verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(f.text("project").isNotBlank()) {
                    Text("保存后加入原专题",color=LocalGlass.current.muted)
                    TextButton(enabled=!ui.busy,onClick={set("project","")}) {Text("取消加入专题")}
                }
                if(f.kind=="focus") PlanField(f,"title","专注名称","专注",vm,tag="planTitle")
                else PlanField(f,"title","名称（必填）",vm,tag="planTitle")
                if(preview) MarkdownBody(f.text("note").ifBlank {"暂无备注"}) else PlanField(f,"note","备注 · 支持 Markdown",vm,single=false)
                when(f.kind) {
                    "task" -> {
                        SectionLabel("安排","计划日期与截止日期分别保存，未设日期不会自动算作今天")
                        DateRow("计划日期",f.text("planned"),{calendar="planned"},{set("planned","")})
                        DateRow("截止日期",f.text("due"),{calendar="due"},{vm.edit(f.set("due","").set("dueTime",""))})
                        PlanField(f,"dueTime","截止时刻（可选，如 18:30）",vm)
                        FlowChoices(listOf("0" to "低优先级","1" to "中优先级","2" to "高优先级"),f.text("priority","1")) {set("priority",it)}
                        PickerRow("分组",ui.data.groups.find {it.id==f.text("group")}?.name ?: "未分组") {chooser="group"}
                        PickerRow("来源记录",records.records.find {it.record.id==f.text("source")}?.let {records.title(it)} ?: "不关联") {chooser="source"}
                        PlanField(f,"minutes","提醒：提前多少分钟（可留空，基于截止或计划日期）",vm)
                        if(f.text("repeat","none")=="none") PlanField(f,"reminder","独立提醒（可选，如 2026-10-25T18:30）",vm)
                        SectionLabel("子任务","一层清单；主任务完成时由你决定是否一并完成")
                        val array=runCatching {JSONArray(f.text("subtasks","[]"))}.getOrDefault(JSONArray())
                        for(i in 0 until array.length()) {val c=array.getJSONObject(i)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                OutlinedTextField(c.getString("title"),{v->val a=JSONArray(f.text("subtasks","[]"));a.getJSONObject(i).put("title",v);set("subtasks",a.toString())},
                                    modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp))
                                TextButton(onClick={val a=JSONArray(f.text("subtasks","[]"));a.remove(i);set("subtasks",a.toString())}) {Text("移除")}
                            }
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            OutlinedTextField(child,{set("pendingChild",it)},label={Text("子任务名称，填好点加入")},modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp))
                            TextButton(enabled=child.isNotBlank(),onClick={val a=JSONArray(f.text("subtasks","[]"));a.put(JSONObject().put("id",newId()).put("title",child));vm.edit(f.set("subtasks",a.toString()).set("pendingChild",""))}) {Text("加入")}
                        }
                        RepeatEditor(f,vm,{calendar=it})
                    }
                    "event" -> {
                        SectionLabel("时间与位置")
                        SettingRow("全天日程","结束日期包含当天",f.text("allDay")=="true") {set("allDay",it.toString())}
                        DateRow("开始日期",f.text("start",LocalDate.now().toString()),{calendar="start"})
                        DateRow("结束日期",f.text("end",f.text("start",LocalDate.now().toString())),{calendar="end"})
                        if(f.text("allDay")!="true") {
                            PlanField(f,"startTime","开始时刻","09:00",vm);PlanField(f,"endTime","结束时刻","10:00",vm)
                            PlanField(f,"zone","时区",ZoneId.systemDefault().id,vm)
                        }
                        PlanField(f,"location","位置（手动填写，可留空）",vm)
                        PlanField(f,"minutes","提醒：提前多少分钟（可留空；0 表示开始时）",vm)
                        PickerRow("关联任务",ui.data.tasks.find {it.id==f.text("task")}?.title ?: "不关联") {chooser="task"}
                        Text("日程结束不会自动完成任务。",color=LocalGlass.current.muted)
                        RepeatEditor(f,vm,{calendar=it})
                    }
                    "habit" -> {
                        SectionLabel("自己的节奏","建立后保留记录方式；规则调整从指定日期起生效")
                        if(f.revision==0L) FlowChoices(listOf("once" to "完成一次","quantity" to "累计数量","duration" to "累计时长"),f.text("tracking","once")) {set("tracking",it)}
                        DateRow("开始日期",f.text("start",LocalDate.now().toString()),{if(f.revision==0L) calendar="start"})
                        DateRow("结束日期",f.text("end"),{calendar="end"},{set("end","")})
                        if(f.text("tracking","once")!="once") {
                            PlanField(f,"target","每日目标","1",vm)
                            if(f.text("tracking")=="quantity") PlanField(f,"unit","数量单位（如 杯 / 页 / 千米）",vm)
                            else Text("时长使用秒保存，例如 20 分钟填 1200。",color=LocalGlass.current.muted)
                        }
                        FlowChoices(listOf("daily" to "每天","weekdays" to "指定星期","weekly" to "每周 N 天"),f.text("schedule","daily")) {set("schedule",it)}
                        if(f.text("schedule")=="weekdays") WeekdayChoices(f,vm)
                        if(f.text("schedule")=="weekly") PlanField(f,"weeklyDays","每周达标天数","3",vm)
                        PlanField(f,"reminder","每天提醒时刻（可选，如 20:00）",vm)
                        SectionLabel("标记颜色")
                        FlowChoices(listOf("0" to "雾蓝","1" to "靛蓝","2" to "紫灰","3" to "玫瑰","4" to "琥珀","5" to "石墨"),f.text("color","0")) {set("color",it)}
                        if(f.revision>0L) DateRow("新规则生效日",f.text("effective",LocalDate.now().toString()),{calendar="effective"})
                    }
                    "focus" -> {
                        SectionLabel("专注方式","单次只运行一个计时；暂停与休息不计入有效专注")
                        FlowChoices(listOf("pomodoro" to "番茄钟","countdown" to "倒计时","stopwatch" to "正计时"),f.text("mode","pomodoro")) {set("mode",it)}
                        if(f.text("mode","pomodoro")!="stopwatch") PlanField(f,"seconds","工作时长（秒）","1500",vm)
                        if(f.text("mode","pomodoro")=="pomodoro") {
                            PlanField(f,"short","短休息（秒）","300",vm);PlanField(f,"long","长休息（秒）","900",vm);PlanField(f,"rounds","每几轮长休息一次","4",vm)
                        }
                        PickerRow("关联任务",ui.data.tasks.find {it.id==f.text("task")}?.title ?: "不关联") {chooser="task"}
                        Text("切到后台后按设备计时基准恢复。设备重启或时间异常会要求核对；当前版本不提供后台结束铃声。",color=LocalGlass.current.muted)
                    }
                    "goal" -> {
                        SectionLabel("进度来源","一个目标只采用一种来源；关联项与里程碑以后可在详情维护")
                        if(f.revision==0L) FlowChoices(listOf("manual" to "手动日志","focus" to "专注时长","tasks" to "关联任务","milestones" to "里程碑"),f.text("source","manual")) {set("source",it)}
                        if(f.revision==0L && f.text("source","manual")=="manual") FlowChoices(listOf("quantity" to "数量目标","duration" to "时长目标"),f.text("goalType","quantity")) {set("goalType",it)}
                        if(f.text("source","manual") in setOf("manual","focus")) {
                            val duration=f.text("source")=="focus" || f.text("goalType")=="duration"
                            PlanField(f,"target",if(duration) "目标时长（秒）" else "目标数量","1",vm)
                            if(!duration && f.text("source","manual")=="manual") PlanField(f,"unit","单位","次",vm)
                        } else Text("以当前有效关联任务 / 里程碑总数为分母，每项等权。",color=LocalGlass.current.muted)
                        if(f.revision==0L && f.text("source") in setOf("tasks","focus")) {
                            PickerRow("进度来源","已选 "+f.text("sources").split(",").count {it.isNotBlank()}+" 项") {chooser="goalSources"}
                        }
                        if(f.revision==0L && f.text("source")=="milestones") PlanField(f,"milestones","初始里程碑（每行一项）",vm,single=false)
                        DateRow("开始日期",f.text("start"),{calendar="start"},{set("start","")})
                        DateRow("结束日期",f.text("end"),{calendar="end"},{set("end","")})
                    }
                }
                run {
                    SectionLabel("标签")
                    MultiChoices(records.tags.map {it.id to it.name},f.text("tags").split(",").filter {it.isNotBlank()}.toSet()) {set("tags",it.joinToString(","))}
                }
                if(f.kind!="focus") {
                    val attachments=records.attachmentsFor("plan_draft",f.id)
                    attachments.forEach {a->Row(verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={view(a)},modifier=Modifier.weight(1f)) {Text(a.displayName)}
                        TextButton(onClick={vm.run("已移除附件引用") {removeFile(f.id,a.id)}}) {Text("移除")}
                    }}
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    calendar?.let {key->CalendarDialog(f.text(key,LocalDate.now().toString()),{calendar=null}) {set(key,it)}}
    chooser?.let {key->GlassSheet("选择"+when(key) {"group"->"分组";"source"->"来源记录";else->"关联任务"},{chooser=null}) {
        if(key=="file") {
            Text("复用本机原件，不创建重复文件")
            records.attachments.forEach {a->TextButton(enabled=!ui.busy,onClick={vm.run("附件已加入草稿") {attachFile(f.id,a.id)}}) {Text(a.displayName)}}
            if(records.attachments.isEmpty()) Text("暂无附件")
            return@GlassSheet
        }
        if(key=="goalSources") {
            val options=if(f.text("source")=="tasks") ui.data.tasks.filter {it.deletedAt==null && ui.data.rules.none {r->r.entityId==it.id}}.map {it.id to it.title}
                else ui.data.sessions.filter {it.deletedAt==null && it.state in setOf("finished","early")}.map {it.id to it.title}
            MultiChoices(options,f.text("sources").split(",").filter {it.isNotBlank()}.toSet()) {set("sources",it.joinToString(","))}
            if(options.isEmpty()) Text("暂无有效来源，请先创建普通任务或结束一次专注")
            Button(onClick={chooser=null}) {Text("选择好了")}
            return@GlassSheet
        }
        TextButton(onClick={set(key,"");chooser=null}) {Text("不选择")}
        val options=when(key) {
            "group"->ui.data.groups.filter {it.archivedAt==null}.map {it.id to it.name}
            "source"->records.records.filter {it.record.deletedAt==null}.map {it.record.id to records.title(it)}
            else->ui.data.tasks.filter {it.deletedAt==null && it.archivedAt==null && ui.data.rules.none {r->r.entityId==it.id}}.map {it.id to it.title}
        }
        options.forEach {(id,name)->GlassChoice(name,f.text(key)==id,Modifier.fillMaxWidth()) {set(key,id);chooser=null}}
        if(options.isEmpty()) Text("暂无可选内容")
    }}
    if(leave) GlassSheet("保留这次编辑？",{leave=false}) {
        Button(enabled=!ui.busy,onClick={vm.keep();leave=false}) {Text("保留草稿并返回")}
        TextButton(enabled=!ui.busy,onClick={vm.discardForm();leave=false}) {Text("丢弃这次草稿")}
        TextButton(onClick={leave=false}) {Text("继续编辑")}
    }
}
@Composable internal fun PlanField(f:PlanForm,key:String,label:String,vm:PlansViewModel,single:Boolean=true,tag:String=key) =
    PlanField(f,key,label,"",vm,single,tag)
@Composable internal fun PlanField(f:PlanForm,key:String,label:String,default:String,vm:PlansViewModel,single:Boolean=true,tag:String=key) {
    OutlinedTextField(f.text(key,default),{vm.edit(f.set(key,it))},label={Text(label)},singleLine=single,
        modifier=Modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(18.dp),minLines=if(single) 1 else 4)
}
@Composable internal fun DateRow(label:String,value:String,click:()->Unit,clear:(()->Unit)?=null) {
    GlassPanel(Modifier.fillMaxWidth(),radius=18) {Row(verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick=click,modifier=Modifier.weight(1f)) {Text(label+"："+value.ifBlank {"未设置"}+" ▾")}
        if(value.isNotBlank() && clear!=null) TextButton(onClick=clear) {Text("清除")}
    }}
}
@Composable internal fun PickerRow(label:String,value:String,click:()->Unit) {
    GlassPanel(Modifier.fillMaxWidth(),radius=18) {TextButton(onClick=click,modifier=Modifier.fillMaxWidth()) {Text(label+"："+value+" ▾")}}
}
@Composable private fun WeekdayChoices(f:PlanForm,vm:PlansViewModel) {
    MultiChoices(listOf("一","二","三","四","五","六","日").mapIndexed {i,s->(i+1).toString() to ("周"+s)},
        f.text("weekdays","1,2,3,4,5").split(",").toSet()) {vm.edit(f.set("weekdays",it.sorted().joinToString(",")))}
}
@Composable private fun RepeatEditor(f:PlanForm,vm:PlansViewModel,date:(String)->Unit) {
    SectionLabel("重复安排","实例按原日期保留身份，移动本次不会挤掉其他日期")
    FlowChoices(listOf("none" to "不重复","daily" to "每天","weekly" to "每周","monthly" to "每月","yearly" to "每年"),f.text("repeat","none")) {vm.edit(f.set("repeat",it))}
    if(f.text("repeat","none")!="none") {
        PlanField(f,"interval","间隔周期数","1",vm)
        if(f.text("repeat")=="weekly") WeekdayChoices(f,vm)
        if(f.text("repeat") in setOf("monthly","yearly")) {
            FlowChoices(listOf("last" to "不存在时取月末","skip" to "跳过不存在日期")+
                if(f.text("repeat")=="yearly") listOf("march" to "2月29日改为3月1日") else emptyList(),f.text("invalid","last")) {vm.edit(f.set("invalid",it))}
        }
        DateRow("重复结束日",f.text("repeatEnd"),{date("repeatEnd")},{vm.edit(f.set("repeatEnd",""))})
        PlanField(f,"count","最多有效实例数（可留空）",vm)
        if(f.revision>0L) DateRow("新规则从此日生效",f.text("repeatFrom",LocalDate.now().toString()),{date("repeatFrom")})
        val next=runCatching {
            val anchor=f.text(if(f.kind=="event") "start" else "planned").ifBlank {f.text("due")}.ifBlank {LocalDate.now().toString()}
            PlanRules.dates(RepeatRule(ownerId="",entityType=f.kind,entityId=f.id,anchorDate=anchor,frequency=f.text("repeat"),
                interval=f.text("interval","1").toInt(),weekdays=f.text("weekdays","1,2,3,4,5"),endDate=f.text("repeatEnd").ifBlank {null},
                count=f.text("count").ifBlank {null}?.toInt(),invalidPolicy=f.text("invalid","last")),LocalDate.now(),3,LocalDate.now()).take(3)
        }.getOrNull()
        Text(if(next==null) "请完善重复规则后预览" else "接下来的日期："+next.joinToString("、"),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
    }
}
