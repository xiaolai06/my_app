package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.PlanRules
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.records.*
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.tools.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val homeCardNames=linkedMapOf("events" to "接下来的安排","tasks" to "要做的事","habits" to "习惯打卡","life" to "近期生活事项","budget" to "本月账本","recent" to "最近记录","inbox" to "待整理")
internal val shortcutNames=mapOf("note" to "写随记","task" to "新任务","event" to "排日程","expense" to "记一笔")

@OptIn(ExperimentalLayoutApi::class)
@Composable fun HomeScreen(records:RecordsUi,recordVm:RecordsViewModel,plans:PlansUi,planVm:PlansViewModel,
    ledger:LedgerSnapshot,tools:ToolsSnapshot,prefs:DashboardPreferences,update:(DashboardPreferences)->Unit,
    quick:(String)->Unit,openPage:(String)->Unit,openTool:(String,String)->Unit) {
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var calendar by remember {mutableStateOf(false)}
    var configure by remember {mutableStateOf(false)}
    var overdue by rememberSaveable {mutableStateOf(false)}
    var showDone by rememberSaveable {mutableStateOf(false)}
    val selected=LocalDate.parse(date);val p=LocalGlass.current
    val tasks=plans.data.todayTasks(selected).sortedWith(compareByDescending<TaskRow>{it.task.priority}.thenBy{it.task.dueTime ?: "23:59"})
    val late=plans.data.taskRows().filter {it.status in setOf("todo","doing") && PlanRules.overdue(it.due,it.task.dueTime)}
    val done=plans.data.taskRows().filter {it.status=="done" && (it.date==date || it.due==date)}
    LazyColumn(Modifier.fillMaxSize().testTag("homeContent"),contentPadding=PaddingValues(20.dp,4.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(prefs.nickname.isBlank()) "把日子过成自己的节奏" else prefs.nickname+"，慢慢来",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                    TextButton(onClick={calendar=true},contentPadding=PaddingValues(vertical=8.dp)) {Text(selected.format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.SIMPLIFIED_CHINESE))+" ▾")}
                }
                IconButton(onClick={configure=true},modifier=Modifier.testTag("editHome")) {Icon(Icons.Default.Edit,"编辑首页卡片")}
            }
            if(date!=LocalDate.now().toString()) TextButton(onClick={date=LocalDate.now().toString()}) {Text("回到今天")}
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                prefs.shortcuts.forEach {key->GlassChoice(shortcutNames[key].orEmpty(),false,Modifier.testTag("homeQuick:"+key)) {quick(key)}}
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(tasks.isEmpty()) (if(selected==LocalDate.now()) "今天可以从容一点" else "这一天可以从容一点") else "还有 "+tasks.size+" 件事，按自己的步调完成",style=MaterialTheme.typography.titleMedium)
                Text("已完成 "+done.size+" 项"+(if(late.isNotEmpty()) " · "+late.size+" 项逾期待处理" else ""),color=p.muted)
                plans.data.sessions.firstOrNull {it.endedAt==null && it.deletedAt==null}?.let {f->TextButton(onClick={planVm.detail("focus",f.id)}) {Text("继续专注 · "+f.title)}}
            }}
        }
        prefs.homeOrder.filter {it !in prefs.hiddenCards}.forEach {key->
            when(key) {
                "events"->item(key) {
                    val events=dayEvents(plans.data,date).filter {(e,o)->(o?.status ?: e.status)=="scheduled"}.sortedBy {it.first.startTime}
                    HomeSection(homeCardNames.getValue(key),"查看计划",{openPage("计划")})
                    if(events.isEmpty()) HomeEmpty("这一天没有安排，留一点自由时间。")
                    else GlassPanel(Modifier.fillMaxWidth()) {events.forEachIndexed {i,(e,o)->
                        HomeRow(o?.title ?: e.title,(if(e.allDay) "全天" else e.startTime+" — "+e.endTime)+(if(e.location.isNotBlank()) " · "+e.location else ""),{planVm.detail("event",e.id,o?.id)})
                        if(i<events.lastIndex) HorizontalDivider(Modifier.padding(horizontal=16.dp),color=p.line.copy(alpha=.25f))
                    }}
                }
                "tasks"->item(key) {
                    HomeSection(homeCardNames.getValue(key),"新任务",{quick("task")})
                    if(tasks.isEmpty()) HomeEmpty("暂无这一天的待办。无日期任务可以在计划中查看。")
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {tasks.forEach {TaskCard(it,plans,planVm)}}
                    if(late.isNotEmpty()) {
                        TextButton(onClick={overdue=!overdue}) {Text((if(overdue) "收起" else "展开")+"逾期事项 · "+late.size)}
                        if(overdue) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {late.forEach {TaskCard(it,plans,planVm)}}
                    }
                    if(done.isNotEmpty()) {
                        TextButton(onClick={showDone=!showDone}) {Text((if(showDone) "收起" else "查看")+"已完成 · "+done.size)}
                        if(showDone) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {done.forEach {TaskCard(it,plans,planVm)}}
                    }
                }
                "habits"-> {
                    val habits=plans.data.habits.filter {it.deletedAt==null && it.archivedAt==null && it.status=="active" && plans.data.habitScheduled(it,date)}
                    if(habits.isNotEmpty()) item(key) {
                        HomeSection(homeCardNames.getValue(key),"查看全部",{openPage("计划")})
                        GlassPanel(Modifier.fillMaxWidth()) {habits.forEach {h->
                            val rule=plans.data.habitRule(h.id,date);val value=plans.data.habitTotal(h.id,date)
                            HomeRow(h.name,PlanRules.display(value)+" / "+PlanRules.display(rule?.targetMilli ?: 1000)+" "+rule?.unit.orEmpty()+(if(value>=(rule?.targetMilli ?: 1000)) " · 已达标" else " · 待打卡"),{planVm.detail("habit",h.id)})
                        }}
                    }
                }
                "life"-> {
                    val until=selected.plusDays(prefs.upcomingDays.toLong()).toString()
                    val payments=tools.periods.filter {it.status=="pending" && it.dueDate<=until && tools.entries.any {e->e.id==it.sourceId && e.deletedAt==null && e.archivedAt==null && e.state in setOf("active","trial")}}
                    val anniversaries=tools.entries.filter {it.kind=="anniversary" && it.deletedAt==null && it.archivedAt==null && it.field("showHome","true")=="true" && tools.nextDate(it,selected)?.let {d->d>=selected && d<=selected.plusDays(prefs.upcomingDays.toLong())}==true}
                    val loans=tools.entries.filter {it.kind=="loan" && it.deletedAt==null && it.archivedAt==null && it.state!="cancelled" && it.field("end").isNotBlank() && it.field("end")<=until && runCatching {tools.remaining(it)>0}.getOrDefault(false)}
                    if(payments.isNotEmpty() || anniversaries.isNotEmpty() || loans.isNotEmpty()) item(key) {
                        HomeSection(homeCardNames.getValue(key),"功能箱",{openPage("功能箱")})
                        GlassPanel(Modifier.fillMaxWidth()) {
                            payments.forEach {period->tools.entries.find {it.id==period.sourceId}?.let {e->HomeRow(e.title,"待处理 · "+period.dueDate,{openTool(e.kind,e.id)})}}
                            anniversaries.forEach {e->HomeRow(e.title,tools.anniversaryText(e,selected),{openTool(e.kind,e.id)})}
                            loans.forEach {e->HomeRow(e.title,"归还日期 "+e.field("end"),{openTool(e.kind,e.id)})}
                        }
                    }
                }
                "budget"->item(key) {
                    HomeSection(if(YearMonth.from(selected)==YearMonth.now()) "本月账本" else "所选月份账本","打开账本",{openTool("ledger_month",YearMonth.from(selected).toString())})
                    val month=YearMonth.from(selected).toString();val id=ledger.defaultLedger()?.id;val sum=ledger.summary(id,month)
                    fun amount(v:Long)=if(prefs.hideAmounts) "••••" else "¥ "+java.math.BigDecimal.valueOf(v,2).toPlainString()
                    GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text(month+" · 净支出",Modifier.weight(1f),color=p.muted)
                            TextButton(onClick={update(prefs.copy(hideAmounts=!prefs.hideAmounts))}) {Text(if(prefs.hideAmounts) "显示金额" else "隐藏金额")}
                        }
                        Text(amount(sum.netExpense),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                        Text("收入 "+amount(sum.income)+" · 退款 "+amount(sum.refunds),color=p.muted)
                        ledger.budgets.firstOrNull {it.ledgerId==id && it.month==month && it.categoryId==null}?.let {b->
                            Text("预算 "+amount(b.limitMinor)+" · 已用 "+amount(ledger.budgetUsed(b)),color=p.muted)
                            LinearProgressIndicator(progress={if(b.limitMinor>0) (ledger.budgetUsed(b).toFloat()/b.limitMinor).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
                        }
                    }}
                }
                "recent"-> {
                    val recent=records.snapshot.records.filter {it.record.deletedAt==null && it.record.archivedAt==null}.sortedByDescending {it.record.updatedAt}.take(3)
                    if(recent.isNotEmpty()) item(key) {
                        HomeSection(homeCardNames.getValue(key),"所有记录",{openPage("记录")})
                        GlassPanel(Modifier.fillMaxWidth()) {recent.forEach {b->HomeRow(records.snapshot.title(b),b.body.take(60).replace('\n',' '),{recordVm.detail(b.record.id)})}}
                    }
                }
                "inbox"->item(key) {
                    val count=records.snapshot.records.count {it.record.deletedAt==null && it.record.archivedAt==null && it.record.organizationStatus=="inbox"}
                    HomeRow("待整理 · "+count+" 条","随手记下的内容，可以慢慢归类",{recordVm.filter(RecordFilter(inbox=true));openPage("记录")})
                }
            }
        }
    }
    if(calendar) CalendarDialog(date,{calendar=false}) {date=it;calendar=false}
    if(configure) HomeConfiguration(prefs,{configure=false},update)
}

@Composable internal fun HomeSection(title:String,action:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        TextButton(onClick=onClick) {Text(action)}
    }
}
@Composable internal fun HomeEmpty(text:String) {Text(text,Modifier.padding(vertical=10.dp),color=LocalGlass.current.muted,style=MaterialTheme.typography.bodyMedium)}
@Composable internal fun HomeRow(title:String,subtitle:String,onClick:()->Unit) {
    Surface(onClick=onClick,color=androidx.compose.ui.graphics.Color.Transparent,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp)) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Medium)
                if(subtitle.isNotBlank()) Text(subtitle,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.KeyboardArrowRight,null,Modifier.size(20.dp),tint=LocalGlass.current.muted)
        }
    }
}
@Composable private fun HomeConfiguration(value:DashboardPreferences,dismiss:()->Unit,save:(DashboardPreferences)->Unit) {
    var draft by remember {mutableStateOf(value)}
    GlassSheet("编辑首页",dismiss) {
        Text("隐藏只影响展示，不会删除内容。用上移、下移调整顺序。",color=LocalGlass.current.muted)
        draft.homeOrder.forEachIndexed {i,key->
            Row(Modifier.testTag("homeCard:"+key).toggleable(key !in draft.hiddenCards,role=Role.Checkbox) {enabled->draft=draft.copy(hiddenCards=if(enabled) draft.hiddenCards-key else draft.hiddenCards+key)},verticalAlignment=Alignment.CenterVertically) {
                Checkbox(key !in draft.hiddenCards,null)
                Text(homeCardNames[key].orEmpty(),Modifier.weight(1f))
                IconButton(enabled=i>0,onClick={val list=draft.homeOrder.toMutableList();java.util.Collections.swap(list,i,i-1);draft=draft.copy(homeOrder=list)}) {Icon(Icons.Default.KeyboardArrowUp,"上移"+homeCardNames[key])}
                IconButton(enabled=i<draft.homeOrder.lastIndex,onClick={val list=draft.homeOrder.toMutableList();java.util.Collections.swap(list,i,i+1);draft=draft.copy(homeOrder=list)}) {Icon(Icons.Default.KeyboardArrowDown,"下移"+homeCardNames[key])}
            }
        }
        Text("快捷新增顺序",style=MaterialTheme.typography.titleMedium)
        draft.shortcuts.forEachIndexed {i,key->Row(verticalAlignment=Alignment.CenterVertically) {
            Text(shortcutNames[key].orEmpty(),Modifier.weight(1f))
            TextButton(enabled=i>0,onClick={val list=draft.shortcuts.toMutableList();java.util.Collections.swap(list,i,i-1);draft=draft.copy(shortcuts=list)}) {Text("上移")}
            TextButton(enabled=i<draft.shortcuts.lastIndex,onClick={val list=draft.shortcuts.toMutableList();java.util.Collections.swap(list,i,i+1);draft=draft.copy(shortcuts=list)}) {Text("下移")}
        }}
        Text("近期事项范围："+draft.upcomingDays+" 天")
        Slider(draft.upcomingDays.toFloat(),{draft=draft.copy(upcomingDays=it.toInt())},valueRange=1f..30f,steps=28)
        Button(onClick={save(draft);dismiss()},modifier=Modifier.fillMaxWidth()) {Text("保存首页设置")}
        TextButton(onClick=dismiss) {Text("取消")}
    }
}
