package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.dailylife.core.PlanRules
import com.example.dailylife.feature.home.HomeViewModel
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.records.*
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.tools.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val homeCardNames=linkedMapOf("weather" to "天气","suggestions" to "生活灵感","favorites" to "我的收藏","events" to "日程","tasks" to "待办","habits" to "习惯打卡","life" to "近期生活","budget" to "本月账本","recent" to "最近记录","inbox" to "待整理")

@Composable fun HomeScreen(records:RecordsUi,recordVm:RecordsViewModel,plans:PlansUi,planVm:PlansViewModel,
    ledger:LedgerSnapshot,tools:ToolsSnapshot,prefs:DashboardPreferences,weather:WeatherUi,homeVm:HomeViewModel,update:(DashboardPreferences)->Unit,
    openPage:(String)->Unit,openTool:(String,String)->Unit) {
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var calendar by rememberSaveable {mutableStateOf(false)}
    var configure by rememberSaveable {mutableStateOf(false)}
    var overdue by rememberSaveable {mutableStateOf(false)}
    var showDone by rememberSaveable {mutableStateOf(false)}
    val selected=LocalDate.parse(date);val p=LocalGlass.current
    var minute by remember {mutableStateOf(LocalDateTime.now())}
    val lifecycle=LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) {lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while(true) {val next=LocalDateTime.now();if(date==minute.toLocalDate().toString()) date=next.toLocalDate().toString();minute=next;kotlinx.coroutines.delay(60000)}
    }}
    val today=minute.toLocalDate()
    // Only the visible, foreground home requests periodic updates. Repository throttles and keeps cache.
    LaunchedEffect(lifecycle,weather.settings,selected==today,"weather" in prefs.hiddenCards) {
        if(weather.settings.enabled && selected==today && "weather" !in prefs.hiddenCards) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(true) {homeVm.refreshWeather();kotlinx.coroutines.delay(15*60000L)}
        }
    }
    val taskRows=remember(plans.data) {plans.data.taskRows()}
    val tasks=remember(plans.data,selected) {plans.data.todayTasks(selected).sortedWith(compareByDescending<TaskRow>{it.task.priority}.thenBy{it.task.dueTime ?: "23:59"})}
    val late=remember(taskRows,minute,tasks) {taskRows.filter {row->row.status in setOf("todo","doing") && PlanRules.overdue(row.due,row.task.dueTime) && tasks.none {it.key==row.key}}}
    val done=remember(taskRows,date) {taskRows.filter {it.status=="done" && (it.date==date || it.due==date)}}
    val events=remember(plans.data,date) {dayEvents(plans.data,date).filter {(e,o)->(o?.status ?: e.status)=="scheduled"}.sortedBy {it.first.startTime}}
    val reading=weather.reading?.takeIf {weather.settings.enabled && it.fresh() && selected==today}
    val suggestions=remember(tools,selected,reading) {tools.suggestions(selected,reading?.apparent,reading?.rainChance)}
    val favorites=remember(tools) {tools.entries.filter {it.isFavorite() && it.deletedAt==null && it.archivedAt==null}.take(5)}
    CompositionLocalProvider(LocalHomeColor provides (prefs.homeStyle=="color")) {
        LazyColumn(Modifier.fillMaxSize().testTag("homeContent"),contentPadding=PaddingValues(20.dp,8.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item("date") {
                val dateButton:@Composable ()->Unit={
                    TextButton(onClick={calendar=true},modifier=Modifier.testTag("homeDate"),contentPadding=PaddingValues(vertical=8.dp,horizontal=0.dp)) {
                        Icon(Icons.Default.DateRange,"选择日期",Modifier.size(20.dp));Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment=Alignment.Start) {
                            Text(selected.format(DateTimeFormatter.ofPattern("M月d日",Locale.CHINA)),style=MaterialTheme.typography.titleLarge,color=p.ink)
                            Text(selected.format(DateTimeFormatter.ofPattern("EEEE",Locale.CHINA)),style=MaterialTheme.typography.bodySmall,color=p.muted)
                        }
                    }
                }
                dateButton()
                if(selected!=today) TextButton(onClick={date=today.toString()}) {Icon(Icons.Default.Refresh,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("回到今天")}
            }
            plans.data.sessions.firstOrNull {it.endedAt==null && it.deletedAt==null}?.let {focus->item("focus") {
                HomeCard("focus","正在专注",onOpen={planVm.detail("focus",focus.id)}) {
                    HomeRow("继续专注 · "+focus.title,if(focus.state=="paused") "已暂停" else "进行中") {planVm.detail("focus",focus.id)}
                }
            }}
            prefs.homeOrder.filter {it !in prefs.hiddenCards}.forEach {key->when(key) {
                "weather"->if(selected==today) item(key) {HomeWeather(weather,homeVm)}
                "suggestions"->if(suggestions.isNotEmpty()) item(key) {
                    HomeCard(key,"生活灵感",onOpen={openTool("tool_feature","wardrobe")}) {suggestions.forEach {s->HomeRow(s.title,s.reason) {openTool(s.kind,s.id)}}}
                }
                "favorites"->if(favorites.isNotEmpty()) item(key) {
                    HomeCard(key,"我的收藏",onOpen={openPage("功能箱")}) {favorites.forEach {e->HomeRow(e.title,toolKinds.find {it.first==e.kind}?.second.orEmpty()) {openTool("tool",e.id)}}}
                }
                "events"->if(events.isNotEmpty()) item(key) {
                    HomeCard(key,if(selected==today) "今日日程" else "当日日程",events.size.toString(),{openPage("计划")}) {
                        events.forEach {(e,o)->
                            Row(Modifier.fillMaxWidth().clickable {planVm.detail("event",e.id,o?.id)}.heightIn(min=64.dp).padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.widthIn(min=52.dp,max=88.dp)) {Text(if(e.allDay) "全天" else e.startTime,color=p.accent,style=MaterialTheme.typography.titleSmall);if(!e.allDay) Text(e.endTime,color=p.muted,style=MaterialTheme.typography.bodySmall)}
                                Box(Modifier.padding(horizontal=12.dp).width(3.dp).height(32.dp).background(p.accent.copy(alpha=.3f),RoundedCornerShape(2.dp)))
                                Column(Modifier.weight(1f)) {Text(o?.title ?: e.title,style=MaterialTheme.typography.titleSmall);if(e.location.isNotBlank()) Text(e.location,color=p.muted,style=MaterialTheme.typography.bodySmall)}
                            }
                        }
                    }
                }
                "tasks"->if(tasks.isNotEmpty() || done.isNotEmpty() || selected==today && late.isNotEmpty()) item(key) {
                    HomeCard(key,if(selected==today) "今日待办" else "当日待办",tasks.size.takeIf {it>0}?.toString(),{openPage("计划")}) {
                        tasks.forEach {HomeTaskRow(it,plans,planVm,date)}
                        if(selected==today && late.isNotEmpty()) {
                            HomeFold("逾期事项",late.size,overdue) {overdue=!overdue}
                            if(overdue) late.filter {row->tasks.none {it.key==row.key}}.forEach {HomeTaskRow(it,plans,planVm,date)}
                        }
                        if(done.isNotEmpty()) {HomeFold("已完成",done.size,showDone) {showDone=!showDone};if(showDone) done.forEach {HomeTaskRow(it,plans,planVm,date)}}
                    }
                }
                "habits"->{
                    val habits=plans.data.habits.filter {it.deletedAt==null && it.archivedAt==null && it.status=="active" && plans.data.habitScheduled(it,date)}
                    if(habits.isNotEmpty()) item(key) {HomeCard(key,"习惯打卡",habits.size.toString(),{openPage("计划")}) {habits.forEach {h->
                        val rule=plans.data.habitRule(h.id,date);val value=plans.data.habitTotal(h.id,date);val target=rule?.targetMilli ?: 1000
                        Row(Modifier.fillMaxWidth().clickable {planVm.detail("habit",h.id)}.padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {
                                CircularProgressIndicator(progress={if(target>0) (value.toFloat()/target).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxSize(),color=homeTint(key),trackColor=homeTint(key).copy(alpha=.12f),strokeWidth=3.dp)
                                if(value>=target) Icon(Icons.Default.Check,null,Modifier.size(18.dp),tint=homeTint(key))
                            }
                            Column(Modifier.weight(1f).padding(start=14.dp)) {Text(h.name,style=MaterialTheme.typography.titleSmall);Text(PlanRules.display(value)+" / "+PlanRules.display(target)+" "+rule?.unit.orEmpty()+(if(value>=target) " · 已达标" else ""),color=p.muted,style=MaterialTheme.typography.bodySmall)}
                        }
                    }}}
                }
                "life"->{
                    val until=selected.plusDays(prefs.upcomingDays.toLong()).toString()
                    val payments=tools.periods.filter {it.status=="pending" && it.dueDate<=until && tools.entries.any {e->e.id==it.sourceId && e.deletedAt==null && e.archivedAt==null && e.state in setOf("active","trial")}}
                    val anniversaries=tools.entries.filter {it.kind=="anniversary" && it.deletedAt==null && it.archivedAt==null && it.field("showHome","true")=="true" && tools.nextDate(it,selected)?.let {d->d>=selected && d<=selected.plusDays(prefs.upcomingDays.toLong())}==true}
                    val loans=tools.entries.filter {it.kind=="loan" && it.deletedAt==null && it.archivedAt==null && it.state!="cancelled" && it.field("end").isNotBlank() && it.field("end")<=until && runCatching {tools.remaining(it)>0}.getOrDefault(false)}
                    if(payments.isNotEmpty() || anniversaries.isNotEmpty() || loans.isNotEmpty()) item(key) {
                        HomeCard(key,"近期生活",onOpen={openPage("功能箱")}) {
                            payments.forEach {period->tools.entries.find {it.id==period.sourceId}?.let {e->HomeRow(e.title,"待处理 · "+period.dueDate) {openTool(e.kind,e.id)}}}
                            anniversaries.forEach {e->HomeRow(e.title,tools.anniversaryText(e,selected)) {openTool(e.kind,e.id)}}
                            loans.forEach {e->HomeRow(e.title,"归还日期 "+e.field("end")) {openTool(e.kind,e.id)}}
                        }
                    }
                }
                "budget"->{
                    val month=YearMonth.from(selected).toString();val id=ledger.defaultLedger()?.id
                    val budget=ledger.budgets.firstOrNull {it.ledgerId==id && it.month==month && it.categoryId==null}
                    val hasEntries=ledger.transactions.any {it.deletedAt==null && (id==null || it.ledgerId==id) && it.occurredOn.startsWith(month)}
                    if(hasEntries || budget!=null) item(key) {
                        val sum=remember(ledger,id,month) {ledger.summary(id,month)}
                        fun amount(v:Long)=if(prefs.hideAmounts) "••••" else "¥ "+java.math.BigDecimal.valueOf(v,2).toPlainString()
                        HomeCard(key,if(YearMonth.from(selected)==YearMonth.from(today)) "本月账本" else "${selected.monthValue}月账本",onOpen={openTool("ledger_month",month)}) {
                            Column(Modifier.padding(horizontal=18.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {Text("净支出",Modifier.weight(1f),color=p.muted,style=MaterialTheme.typography.bodySmall);IconButton(onClick={update(prefs.copy(hideAmounts=!prefs.hideAmounts))},modifier=Modifier.size(48.dp)) {HomeAmountVisibility(prefs.hideAmounts)}}
                                Text(amount(sum.netExpense),style=MaterialTheme.typography.headlineLarge)
                                Text("收入 "+amount(sum.income)+" · 退款 "+amount(sum.refunds),color=p.muted,style=MaterialTheme.typography.bodySmall)
                                budget?.let {b->LinearProgressIndicator(progress={if(b.limitMinor>0) (ledger.budgetUsed(b).toFloat()/b.limitMinor).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth(),color=homeTint(key));Text("预算 "+amount(b.limitMinor)+" · 已用 "+amount(ledger.budgetUsed(b)),color=p.muted,style=MaterialTheme.typography.bodySmall)}
                            }
                        }
                    }
                }
                "recent"->{
                    val recent=records.snapshot.records.filter {it.record.deletedAt==null && it.record.archivedAt==null}.sortedByDescending {it.record.updatedAt}.take(3)
                    if(recent.isNotEmpty()) item(key) {HomeCard(key,"最近记录",onOpen={openPage("记录")}) {recent.forEach {b->HomeRow(records.snapshot.title(b),b.body.take(60).replace('\n',' ')) {recordVm.detail(b.record.id)}}}}
                }
                "inbox"->{
                    val count=records.snapshot.records.count {it.record.deletedAt==null && it.record.archivedAt==null && it.record.organizationStatus=="inbox"}
                    if(count>0) item(key) {HomeCard(key,"待整理","$count",{recordVm.filter(RecordFilter(inbox=true));openPage("记录")}) {}}
                }
            }}
            item("appearance") {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                    TextButton(onClick={configure=true},modifier=Modifier.testTag("editHome")) {Icon(Icons.AutoMirrored.Filled.List,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("卡片与样式")}
                }
            }
        }
    }
    if(calendar) {
        val marked=remember(plans.data,taskRows) {taskRows.flatMap {listOfNotNull(it.date,it.due)}.toSet()+plans.data.events.filter {it.deletedAt==null && it.archivedAt==null && it.status=="scheduled"}.map {it.startDate}+plans.data.occurrences.filter {it.entityType=="event" && it.status=="scheduled"}.map {it.scheduledDate}}
        HomeCalendar(selected,marked,{calendar=false}) {date=it.toString();calendar=false}
    }
    if(configure) HomeConfiguration(prefs,{configure=false},homeVm::saveDashboard)
}

@Composable private fun HomeFold(title:String,count:Int,expanded:Boolean,click:()->Unit) {
    TextButton(onClick=click,modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp)) {
        Text("$title · $count",Modifier.weight(1f));Icon(if(expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,null)
    }
}
@Composable private fun HomeTaskRow(row:TaskRow,ui:PlansUi,vm:PlansViewModel,date:String) {
    var confirm by remember {mutableStateOf(false)}
    val unfinished=ui.data.subtasks.count {it.taskId==row.task.id && (if(row.occurrence!=null) it.id !in row.occurrence.subtaskStates.split(",") else it.completedAt==null)}
    Row(Modifier.fillMaxWidth().padding(start=8.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically) {
        GlassCheckbox(row.status=="done",onCheckedChange={if(row.status!="done" && unfinished>0) confirm=true else vm.task(row,if(row.status=="done") "todo" else "done")},enabled=!ui.busy,modifier=Modifier.testTag("completeTask:"+row.key))
        Column(Modifier.weight(1f).clickable {vm.detail("task",row.task.id,row.occurrence?.id)}.heightIn(min=56.dp).padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(row.title,style=MaterialTheme.typography.titleSmall,color=if(row.status=="done") LocalGlass.current.muted else LocalGlass.current.ink)
            val meta=listOfNotNull(if(row.task.priority==2) "优先" else null,if(row.status=="doing") "进行中" else null,
                row.due?.let {if(PlanRules.overdue(it,row.task.dueTime) && row.status!="done") "已逾期 · $it" else if(it!=date) "截止 $it" else row.task.dueTime},
                if(unfinished>0) "$unfinished 项子任务" else null).filter {it.isNotBlank()}.joinToString(" · ")
            if(meta.isNotBlank()) Text(meta,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
        }
    }
    if(confirm) GlassSheet("还有 $unfinished 项子任务",{confirm=false}) {
        GlassButton(onClick={vm.task(row,"done",true);confirm=false}) {Text("同时完成子任务")}
        TextButton(onClick={vm.task(row,"done",false);confirm=false}) {Text("保留子任务状态")}
    }
}
@Composable private fun HomeConfiguration(value:DashboardPreferences,dismiss:()->Unit,save:(DashboardPreferences,(String?)->Unit)->Unit) {
    var draft by remember {mutableStateOf(value)}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    GlassSheet("卡片与样式",{if(!saving) dismiss()},dismissEnabled=!saving) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf("color" to "柔彩","plain" to "素净").forEach {(key,label)->
                GlassChoice(label,draft.homeStyle==key,Modifier.weight(1f).testTag("homeStyle:$key"),enabled=!saving) {draft=draft.copy(homeStyle=key)}
            }
        }
        Text("有内容时显示，隐藏不删除数据。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        draft.homeOrder.forEachIndexed {i,key->
            Row(Modifier.testTag("homeCard:"+key).toggleable(key !in draft.hiddenCards,enabled=!saving,role=Role.Checkbox) {enabled->draft=draft.copy(hiddenCards=if(enabled) draft.hiddenCards-key else draft.hiddenCards+key)},verticalAlignment=Alignment.CenterVertically) {
                GlassCheckbox(key !in draft.hiddenCards,null)
                Text(homeCardNames[key].orEmpty(),Modifier.weight(1f))
                IconButton(enabled=!saving && i>0,onClick={val list=draft.homeOrder.toMutableList();java.util.Collections.swap(list,i,i-1);draft=draft.copy(homeOrder=list)},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.KeyboardArrowUp,"上移"+homeCardNames[key])}
                IconButton(enabled=!saving && i<draft.homeOrder.lastIndex,onClick={val list=draft.homeOrder.toMutableList();java.util.Collections.swap(list,i,i+1);draft=draft.copy(homeOrder=list)},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.KeyboardArrowDown,"下移"+homeCardNames[key])}
            }
        }
        Text("近期事项 · ${draft.upcomingDays} 天")
        Slider(draft.upcomingDays.toFloat(),{draft=draft.copy(upcomingDays=it.toInt())},enabled=!saving,valueRange=1f..30f,steps=28)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        GlassButton(enabled=!saving,onClick={saving=true;error=null;save(draft) {message->saving=false;if(message==null) dismiss() else error=message}},modifier=Modifier.fillMaxWidth().testTag("saveHomeSettings")) {Text(if(saving) "正在保存…" else "保存首页设置")}
    }
}
