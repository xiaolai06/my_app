package com.example.dailylife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.dailylife.feature.plans.*
import java.time.LocalDate

/** Related fields share one surface; optional groups retain their values while folded. */
@Composable internal fun PlanEditorFields(f:PlanForm,ui:PlansUi,vm:PlansViewModel,date:(String)->Unit,choose:(String)->Unit) {
    fun set(key:String,value:String)=vm.edit(f.set(key,value))
    val today=LocalDate.now().toString()
    when(f.kind) {
        "task"->{
            PlanEditorGroup("时间",f.id+":task-time") {
                DateRow("计划日期",f.text("planned"),{date("planned")},{set("planned","")})
                DateRow("截止日期",f.text("due"),{date("due")},{vm.edit(f.set("due","").set("dueTime",""))})
                if(f.text("due").isNotBlank()) PlanTimeRow("截止时刻",f.text("dueTime"),{set("dueTime",it)},{set("dueTime","")})
            }
            PlanEditorGroup("归类",f.id+":task-category",collapsible=true,
                initiallyExpanded=f.text("group").isNotBlank() || f.text("priority","1")!="1",
                summary=ui.data.groups.find {it.id==f.text("group")}?.name ?: "未分组") {
                GlassSelectField("优先级",listOf("0" to "低","1" to "中","2" to "高"),f.text("priority","1"),enabled=!ui.busy) {set("priority",it)}
                PickerRow("分组",ui.data.groups.find {it.id==f.text("group")}?.name ?: "未分组") {choose("group")}
            }
            PlanSubtaskEditor(f,ui,vm)
        }
        "event"->{
            PlanEditorGroup("时间",f.id+":event-time") {
                SettingRow("全天","",f.text("allDay")=="true") {set("allDay",it.toString())}
                DateRow("开始日期",f.text("start",today),{date("start")})
                DateRow("结束日期",f.text("end",f.text("start",today)),{date("end")})
                if(f.text("allDay")!="true") {
                    PlanTimeRow("开始时刻",f.text("startTime","09:00"),{set("startTime",it)})
                    PlanTimeRow("结束时刻",f.text("endTime","10:00"),{set("endTime",it)})
                }
            }
            PlanEditorGroup("地点与关联",f.id+":event-links",collapsible=true,initiallyExpanded=f.text("location").isNotBlank() || f.text("task").isNotBlank()) {
                PlanField(f,"location","地点",vm)
                PickerRow("关联任务",ui.data.tasks.find {it.id==f.text("task")}?.title ?: "不关联") {choose("task")}
            }
        }
        "habit"->{
            PlanEditorGroup("打卡目标",f.id+":habit-target") {
                GlassSelectField("记录方式",listOf("once" to "完成一次","quantity" to "累计数量","duration" to "累计时长"),f.text("tracking","once"),enabled=f.revision==0L && !ui.busy) {
                    var next=f.set("tracking",it)
                    if(it=="duration" && !org.json.JSONObject(f.payload).has("target")) next=next.set("target","1800")
                    vm.edit(next)
                }
                if(f.text("tracking","once")!="once") {
                    if(f.text("tracking")=="duration") PlanDurationField(f,"target","每日时长","1",vm)
                    else PlanField(f,"target","每日数量","1",vm)
                    if(f.text("tracking")=="quantity") PlanField(f,"unit","单位","",vm)
                }
            }
            PlanEditorGroup("频率",f.id+":habit-schedule") {
                GlassSelectField("重复",listOf("daily" to "每天","weekdays" to "指定星期","weekly" to "每周几天"),f.text("schedule","daily"),enabled=!ui.busy) {set("schedule",it)}
                if(f.text("schedule")=="weekdays") WeekdayChoices(f,vm)
                if(f.text("schedule")=="weekly") PlanField(f,"weeklyDays","每周天数","3",vm)
            }
            PlanEditorGroup("日期与标记",f.id+":habit-dates",collapsible=true,initiallyExpanded=false,
                summary=if(f.text("end").isBlank()) "开始于 "+f.text("start",today) else f.text("start",today)+" — "+f.text("end")) {
                GlassSelectorRow("开始日期",f.text("start",today),enabled=f.revision==0L && !ui.busy) {date("start")}
                DateRow("结束日期",f.text("end"),{date("end")},{set("end","")})
                GlassSelectField("标记颜色",listOf("0" to "雾蓝","1" to "靛蓝","2" to "紫灰","3" to "玫瑰","4" to "琥珀","5" to "石墨"),f.text("color","0"),enabled=!ui.busy) {set("color",it)}
            }
            if(f.revision>0L) PlanEditorGroup("规则生效",f.id+":habit-effective") {
                DateRow("生效日期",f.text("effective",today),{date("effective")})
            }
        }
        "focus"->{
            PlanEditorGroup("计时",f.id+":focus-timer") {
                GlassSelectField("方式",listOf("pomodoro" to "番茄钟","countdown" to "倒计时","stopwatch" to "正计时"),f.text("mode","pomodoro"),enabled=!ui.busy) {set("mode",it)}
                if(f.text("mode","pomodoro")!="stopwatch") PlanDurationField(f,"seconds","专注时长","1500",vm,integerSeconds=true)
            }
            if(f.text("mode","pomodoro")=="pomodoro") PlanEditorGroup("休息安排",f.id+":focus-breaks",collapsible=true,initiallyExpanded=false) {
                PlanDurationField(f,"short","短休息","300",vm,integerSeconds=true)
                PlanDurationField(f,"long","长休息","900",vm,integerSeconds=true)
                PlanField(f,"rounds","长休息间隔（轮）","4",vm)
            }
            PlanEditorGroup("关联与通知",f.id+":focus-links",collapsible=true,initiallyExpanded=false) {
                PickerRow("关联任务",ui.data.tasks.find {it.id==f.text("task")}?.title ?: "不关联") {choose("task")}
                PlanNotificationAccess(vm)
            }
        }
        "goal"->{
            val source=f.text("source","manual")
            val duration=source=="focus" || f.text("goalType")=="duration"
            PlanEditorGroup("目标",f.id+":goal-target") {
                GlassSelectField("进度来源",listOf("manual" to "手动记录","focus" to "专注时长","tasks" to "关联任务","milestones" to "里程碑"),source,enabled=f.revision==0L && !ui.busy) {
                    var next=f.set("source",it)
                    if(it=="focus" && !org.json.JSONObject(f.payload).has("target")) next=next.set("target","3600")
                    vm.edit(next)
                }
                if(source=="manual") GlassSelectField("计量方式",listOf("quantity" to "数量","duration" to "时长"),f.text("goalType","quantity"),enabled=f.revision==0L && !ui.busy) {
                    var next=f.set("goalType",it)
                    if(it=="duration" && !org.json.JSONObject(f.payload).has("target")) next=next.set("target","3600")
                    vm.edit(next)
                }
                if(source in setOf("manual","focus")) {
                    if(duration) PlanDurationField(f,"target","目标时长","1",vm)
                    else PlanField(f,"target","目标数量","1",vm)
                    if(!duration && source=="manual") PlanField(f,"unit","单位","次",vm)
                }
                if(f.revision==0L && source in setOf("tasks","focus")) {
                    PickerRow("关联内容","已选 "+f.text("sources").split(",").count {it.isNotBlank()}+" 项") {choose("goalSources")}
                }
                if(f.revision==0L && source=="milestones") PlanField(f,"milestones","里程碑 · 每行一项",vm,single=false)
            }
            PlanEditorGroup("日期",f.id+":goal-dates",collapsible=true,initiallyExpanded=f.text("start").isNotBlank() || f.text("end").isNotBlank(),summary="未设置") {
                DateRow("开始日期",f.text("start"),{date("start")},{set("start","")})
                DateRow("结束日期",f.text("end"),{date("end")},{set("end","")})
            }
        }
    }
}

@Composable internal fun PlanSubtaskEditor(f:PlanForm,ui:PlansUi,vm:PlansViewModel) {
    val children=runCatching {org.json.JSONArray(f.text("subtasks","[]"))}.getOrDefault(org.json.JSONArray())
    PlanEditorGroup("子任务",f.id+":subtasks",collapsible=true,initiallyExpanded=children.length()>0,summary=if(children.length()==0) "添加步骤" else "${children.length()} 项") {
        for(index in 0 until children.length()) {
            val child=children.getJSONObject(index)
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                GlassTextField(child.getString("title"),{value->
                    val updated=org.json.JSONArray(f.text("subtasks","[]"));updated.getJSONObject(index).put("title",value)
                    vm.edit(f.set("subtasks",updated.toString()))
                },modifier=Modifier.weight(1f),enabled=!ui.busy)
                TextButton(enabled=!ui.busy,onClick={val updated=org.json.JSONArray(f.text("subtasks","[]"));updated.remove(index);vm.edit(f.set("subtasks",updated.toString()))}) {Text("移除")}
            }
        }
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            GlassTextField(f.text("pendingChild"),{vm.edit(f.set("pendingChild",it))},label={Text("步骤名称")},modifier=Modifier.weight(1f),enabled=!ui.busy)
            TextButton(enabled=!ui.busy && f.text("pendingChild").isNotBlank(),onClick={
                val updated=org.json.JSONArray(f.text("subtasks","[]"))
                updated.put(org.json.JSONObject().put("id",com.example.dailylife.data.local.newId()).put("title",f.text("pendingChild")))
                vm.edit(f.set("subtasks",updated.toString()).set("pendingChild",""))
            }) {Text("加入")}
        }
    }
}
