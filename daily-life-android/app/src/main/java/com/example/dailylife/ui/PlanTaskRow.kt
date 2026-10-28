package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.PlanRules
import com.example.dailylife.feature.plans.*

@OptIn(ExperimentalFoundationApi::class)
@Composable fun TaskCard(row:TaskRow,ui:PlansUi,vm:PlansViewModel,selected:Boolean=false,toggle:()->Unit={},selecting:Boolean=false) {
    var expanded by rememberSaveable(row.key) {mutableStateOf(false)}
    var complete by remember {mutableStateOf(false)}
    val children=ui.data.subtasks.filter {it.taskId==row.task.id}.sortedBy {it.sortOrder}
    val doneIds=row.occurrence?.subtaskStates?.split(",")?.toSet()
    val completed=children.count {if(doneIds!=null) it.id in doneIds else it.completedAt!=null}
    val editable=row.task.deletedAt==null && row.task.archivedAt==null && row.status !in setOf("cancelled","skipped")
    GlassPanel(Modifier.fillMaxWidth().testTag("taskCard:"+row.key).combinedClickable(
        onClick={if(selecting) {if(!ui.busy) toggle()} else vm.detail("task",row.task.id,row.occurrence?.id)},
        onLongClick={if(!ui.busy) toggle()}),selected=selected) {
        Column(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                GlassCheckbox(if(selecting) selected else row.status=="done",onCheckedChange={
                    if(selecting) toggle() else if(row.status!="done" && completed<children.size) complete=true
                    else vm.task(row,if(row.status=="done") "todo" else "done")
                },enabled=!ui.busy && (selecting || editable),modifier=Modifier.testTag("completeTask:"+row.key).semantics {
                    contentDescription=if(selecting) "选择任务 ${row.title}" else if(row.status=="done") "撤销完成 ${row.title}" else "完成任务 ${row.title}"
                })
                Text(row.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium,
                    textDecoration=if(row.status=="done") TextDecoration.LineThrough else null,
                    color=if(row.status=="done") LocalGlass.current.muted else LocalGlass.current.ink)
                if(row.task.priority==2) PlanGlyph("flag",Modifier.padding(horizontal=4.dp).size(16.dp).semantics {contentDescription="高优先级"},MaterialTheme.colorScheme.error)
                PlanReminderMark(planReminderSummary(ui.data,"task",row.task.id,row.occurrence))
            }
            val meta=buildList {
                row.date?.let {add(planDateLabel(it))}
                row.due?.let {due->add((if(due==row.date) "截止" else planDateLabel(due)+"截止")+row.task.dueTime?.let {" $it"}.orEmpty())}
                ui.data.groups.find {it.id==row.task.groupId}?.let {add(it.name)}
                if(row.occurrence!=null) add("重复")
                if(row.status=="doing") add("进行中")
                if(row.status in setOf("cancelled","skipped")) add(stateName(row.status))
            }
            if(meta.isNotEmpty()) Text(meta.joinToString(" · "),Modifier.padding(start=48.dp,end=8.dp,bottom=4.dp),
                style=MaterialTheme.typography.bodySmall,color=if(row.status in setOf("todo","doing") && PlanRules.overdue(row.due,row.task.dueTime)) MaterialTheme.colorScheme.error else LocalGlass.current.muted)
            if(children.isNotEmpty()) {
                TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().testTag("expandSubtasks:"+row.key),contentPadding=PaddingValues(start=48.dp,end=4.dp)) {
                    PlanStepProgress(completed,children.size,Modifier.weight(1f).testTag("subtaskProgress:"+row.key))
                    Text("$completed / ${children.size}",Modifier.padding(start=12.dp),style=MaterialTheme.typography.labelMedium)
                    PlanGlyph(if(expanded) "up" else "down",Modifier.padding(start=4.dp).size(16.dp))
                }
                if(expanded) children.forEach {child->
                    Row(Modifier.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        val checked=if(doneIds!=null) child.id in doneIds else child.completedAt!=null
                        GlassCheckbox(checked,{vm.run("子任务已更新") {subtask(child.id,row.occurrence?.id)}},
                            enabled=!ui.busy && editable && !selecting,modifier=Modifier.testTag("subtask:"+child.id).semantics {contentDescription="子任务 ${child.title}"})
                        Text(child.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=if(checked) LocalGlass.current.muted else LocalGlass.current.ink)
                    }
                }
            }
        }
    }
    if(complete) GlassSheet("完成任务",{complete=false},dismissEnabled=!ui.busy) {
        Text("还有 ${children.size-completed} 项子任务",style=MaterialTheme.typography.bodyMedium)
        GlassButton(enabled=!ui.busy,onClick={vm.task(row,"done",true);complete=false}) {Text("同时完成子任务")}
        TextButton(enabled=!ui.busy,onClick={vm.task(row,"done",false);complete=false}) {Text("保留子任务状态")}
    }
}
