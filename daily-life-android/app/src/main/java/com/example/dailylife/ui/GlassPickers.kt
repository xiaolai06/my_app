package com.example.dailylife.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Labels stay above values so long names and large fonts never compete for two columns. */
@Composable internal fun GlassSelectorRow(label:String,value:String,modifier:Modifier=Modifier,
    icon:ImageVector=Icons.Outlined.KeyboardArrowDown,enabled:Boolean=true,clear:(()->Unit)?=null,onClick:()->Unit) {
    val p=LocalGlass.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.sheet.copy(alpha=.72f))
        .border(.75.dp,p.accent.copy(alpha=.13f),RoundedCornerShape(16.dp)),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min=64.dp).clickable(enabled=enabled,role=Role.Button,onClick=onClick)
            .padding(horizontal=16.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(label,style=MaterialTheme.typography.bodySmall,color=p.muted)
                Text(value.ifBlank {"未设置"},style=MaterialTheme.typography.bodyLarge,color=if(enabled) p.ink else p.muted)
            }
            Icon(icon,null,Modifier.size(20.dp),tint=p.accent)
        }
        if(clear!=null && value.isNotBlank()) IconButton(onClick=clear,enabled=enabled,modifier=Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Close,"清除"+label,Modifier.size(18.dp),tint=p.muted)
        }
    }
}

@Composable internal fun GlassOptionRow(label:String,selected:Boolean,modifier:Modifier=Modifier,
    multi:Boolean=false,enabled:Boolean=true,onClick:()->Unit) {
    val p=LocalGlass.current
    Row(modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(16.dp))
        .background(if(selected) p.selected.copy(alpha=.65f) else Color.Transparent)
        .then(if(multi) Modifier.toggleable(value=selected,enabled=enabled,role=Role.Checkbox,onValueChange={onClick()})
            else Modifier.selectable(selected=selected,enabled=enabled,role=Role.RadioButton,onClick=onClick))
        .padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),color=if(enabled) p.ink else p.muted,style=MaterialTheme.typography.bodyLarge,
            fontWeight=if(selected) FontWeight.Medium else FontWeight.Normal)
        Box(Modifier.size(22.dp).clip(if(multi) RoundedCornerShape(6.dp) else CircleShape)
            .background(if(selected) p.accent else p.accent.copy(alpha=.05f))
            .border(.75.dp,p.accent.copy(alpha=if(selected) .1f else .28f),if(multi) RoundedCornerShape(6.dp) else CircleShape),contentAlignment=Alignment.Center) {
            if(selected) Icon(Icons.Outlined.Check,null,Modifier.size(16.dp),tint=if(p.dark) p.background else Color.White)
        }
    }
}

/** Lazy results keep a large account/tag/source library cheap to browse. Picking is not saving. */
@Composable internal fun GlassSelectionSheet(title:String,options:List<Pair<String,String>>,selected:String?,dismiss:()->Unit,
    emptyText:String="暂无可选内容",choose:(String)->Unit) {
    var query by rememberSaveable(title) {mutableStateOf("")}
    val matches=remember(options,query) {options.filter {it.second.contains(query.trim(),ignoreCase=true)}}
    GlassSheet(title,dismiss,scrollContent=false) {
        if(options.size>8) GlassTextField(query,{query=it},placeholder={Text("搜索选项")},singleLine=true,
            leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon=if(query.isNotEmpty()) ({IconButton(onClick={query=""}) {Icon(Icons.Outlined.Close,"清除搜索")}}) else null,
            modifier=Modifier.fillMaxWidth().testTag("selectionSearch"))
        if(matches.isEmpty()) Text(if(options.isEmpty()) emptyText else "没有匹配的选项，试试其他关键词",color=LocalGlass.current.muted,modifier=Modifier.padding(vertical=20.dp))
        else LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).selectableGroup().testTag("selectionOptions"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(matches,key={it.first}) {(id,label)->GlassOptionRow(label,id==selected,Modifier.testTag("selectionOption:"+id)) {choose(id)}}
        }
    }
}

@Composable internal fun GlassSelectField(label:String,options:List<Pair<String,String>>,selected:String?,modifier:Modifier=Modifier,
    enabled:Boolean=true,fallback:String="请选择",choose:(String)->Unit) {
    var show by rememberSaveable {mutableStateOf(false)}
    GlassSelectorRow(label,options.firstOrNull {it.first==selected}?.second ?: fallback,modifier,enabled=enabled) {show=true}
    if(show) GlassSelectionSheet(label,options,selected,{show=false}) {choose(it);show=false}
}

/** Multi-select commits only on confirmation; closing, Back and filtering preserve the original set. */
@Composable internal fun GlassMultiSelectField(label:String,options:List<Pair<String,String>>,selected:Set<String>,choose:(Set<String>)->Unit) {
    var show by rememberSaveable {mutableStateOf(false)}
    val names=options.filter {it.first in selected}.map {it.second}
    GlassSelectorRow(label,if(names.isEmpty()) "未选择" else names.take(2).joinToString("、")+if(names.size>2) " 等 ${names.size} 项" else "") {show=true}
    if(show) GlassMultiSelectionSheet(label,options,selected,{show=false}) {choose(it);show=false}
}

@Composable internal fun GlassMultiSelectionSheet(title:String,options:List<Pair<String,String>>,selected:Set<String>,dismiss:()->Unit,
    maximum:Int=Int.MAX_VALUE,busy:Boolean=false,error:String?=null,closeOnConfirm:Boolean=true,choose:(Set<String>)->Unit) {
    var draft by rememberSaveable {mutableStateOf(selected.toList())}
    var query by rememberSaveable {mutableStateOf("")}
    val matches=remember(options,query) {options.filter {it.second.contains(query.trim(),true)}}
    val close={if(!busy) dismiss()}
    GlassSheet(title,close,scrollContent=false,dismissEnabled=!busy,footer={Column {
        error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        PickerActions(close,{choose(draft.toSet());if(closeOnConfirm) dismiss()},"完成选择",enabled=!busy,
            leading={TextButton(enabled=!busy,onClick={draft=emptyList()}) {Text("清空")}})
    }}) {
        Text("已选 ${draft.size} 项"+if(maximum<Int.MAX_VALUE) " · 最多 $maximum 项" else "",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        if(options.size>8) GlassTextField(query,{query=it},placeholder={Text("搜索选项")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("selectionSearch"))
        if(matches.isEmpty()) Text(if(options.isEmpty()) "暂无可选内容" else "没有匹配的选项",color=LocalGlass.current.muted)
        LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("selectionOptions"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(matches,key={it.first}) {(id,text)->GlassOptionRow(text,id in draft,multi=true,enabled=!busy && (id in draft || draft.size<maximum),
                modifier=Modifier.testTag("selectionOption:"+id)) {draft=if(id in draft) draft-id else draft+id}}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PickerActions(cancel:()->Unit,confirm:()->Unit,label:String="确定",enabled:Boolean=true,
    confirmTag:String="pickerConfirm",cancelTag:String="pickerCancel",leading:(@Composable ()->Unit)?=null) {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        leading?.invoke()
        TextButton(onClick=cancel,modifier=Modifier.testTag(cancelTag)) {Text("取消")}
        GlassButton(onClick=confirm,enabled=enabled,modifier=Modifier.testTag(confirmTag)) {Text(label)}
    }
}

internal fun parsedPickerDate(value:String):LocalDate?=runCatching {LocalDate.parse(value).takeIf {it.year in 1..9999}}.getOrNull()

/** Shared calendar for home, records, plans, money and life tools. No caller is changed before Confirm. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable internal fun GlassDatePicker(value:String,dismiss:()->Unit,marked:Set<String> = emptySet(),
    confirmLabel:String="确定",confirmTag:String="confirmDate",cancelTag:String="cancelDate",chooseMonth:((String)->Unit)?=null,choose:(String)->Unit) {
    val initial=remember(value) {parsedPickerDate(value) ?: LocalDate.now()}
    var selectedText by rememberSaveable(value) {mutableStateOf(initial.toString())}
    var monthText by rememberSaveable(value) {mutableStateOf(YearMonth.from(initial).toString())}
    var input by rememberSaveable {mutableStateOf(false)}
    var dateInput by rememberSaveable(value) {mutableStateOf(initial.toString())}
    var monthPicker by rememberSaveable {mutableStateOf(false)}
    var preferredMode by rememberSaveable {mutableStateOf("grid")}
    val density=LocalDensity.current
    val listMode=preferredMode=="list"
    val selected=LocalDate.parse(selectedText)
    val month=YearMonth.parse(monthText)
    val today=LocalDate.now()
    val valid=!input || parsedPickerDate(dateInput)!=null
    fun pick(date:LocalDate) {selectedText=date.toString();dateInput=selectedText}
    val daysScroll=rememberLazyListState()
    LaunchedEffect(monthText,listMode,input) {daysScroll.scrollToItem(0)}
    GlassSheet("选择日期",dismiss,scrollContent=false,contentHorizontalPadding=12.dp,maxHeightFraction=.96f,footer={PickerActions(dismiss,{
        choose(if(input) parsedPickerDate(dateInput)!!.toString() else selectedText);dismiss()
    },confirmLabel,valid,confirmTag,cancelTag,leading={
        TextButton(onClick={pick(today);monthText=YearMonth.from(today).toString()}) {Text("今天")}
        if(chooseMonth!=null) TextButton(enabled=valid,onClick={
            chooseMonth(if(input) YearMonth.from(parsedPickerDate(dateInput)!!).toString() else monthText);dismiss()
        },modifier=Modifier.heightIn(min=48.dp).testTag("calendarChooseMonth")) {Text("查看整月")}
    })}) {
        // A single scroll area lets large text move the heading out of the way instead of
        // squeezing the calendar to one row. The confirmation actions remain outside it.
        LazyColumn(state=daysScroll,modifier=Modifier.fillMaxWidth().weight(1f,fill=false).testTag("calendarDays"),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item(key="heading") {
                Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(enabled=month>YearMonth.of(1,1),onClick={monthText=month.minusMonths(1).toString()},modifier=Modifier.size(48.dp).testTag("calendarPrevious")) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,"上个月")}
                    TextButton(onClick={monthPicker=true},modifier=Modifier.weight(1f).heightIn(min=64.dp).testTag("calendarMonth")) {
                        if(density.fontScale>1.3f) Text("${month.year}年${month.monthValue}月",style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.SemiBold,color=LocalGlass.current.ink)
                        else Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("${month.year} 年",style=MaterialTheme.typography.labelMedium,color=LocalGlass.current.muted)
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("${month.monthValue} 月",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,color=LocalGlass.current.ink)
                                Icon(Icons.Outlined.KeyboardArrowDown,null,Modifier.size(18.dp),tint=LocalGlass.current.muted)
                            }
                        }
                    }
                    IconButton(enabled=month<YearMonth.of(9999,12),onClick={monthText=month.plusMonths(1).toString()},modifier=Modifier.size(48.dp).testTag("calendarNext")) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,"下个月")}
                }
            }
            stickyHeader(key="modes") {
                Row(Modifier.fillMaxWidth().background(LocalGlass.current.sheet).horizontalScroll(rememberScrollState()).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                    GlassChoice("月历",!input&&!listMode,Modifier.testTag("calendarMode:grid"),role=Role.RadioButton) {preferredMode="grid";input=false}
                    GlassChoice("日期列表",!input&&listMode,Modifier.testTag("calendarMode:list"),role=Role.RadioButton) {preferredMode="list";input=false}
                    TextButton(onClick={input=!input},modifier=Modifier.testTag("calendarInputToggle").semantics {stateDescription=if(input) "正在输入日期" else "浏览日期"}) {Text(if(input) "返回选择" else "输入日期")}
                }
            }
            if(input) item(key="input") {
                GlassTextField(dateInput,{dateInput=it},label={Text("日期 · YYYY-MM-DD")},singleLine=true,isError=!valid,
                    supportingText={Text(if(valid) "输入完整年月日，确认后生效" else "请输入有效日期，例如 2026-10-24")},modifier=Modifier.fillMaxWidth().testTag("calendarInput"))
            } else if(listMode) {
                items(month.lengthOfMonth(),key={"day:${it+1}"}) {index->CalendarCell(month.atDay(index+1),selected,today,marked,true,Modifier.fillMaxWidth()) {pick(it)}}
            } else item(key="grid") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val horizontallyScrollable=maxWidth<calendarDayMinimum(density.fontScale)*7
                    Column(Modifier.fillMaxWidth()) {
                        if(horizontallyScrollable) Text("左右滑动浏览一周",Modifier.padding(horizontal=4.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                        GlassCalendarMonth(month,selected,marked,Modifier.testTag("calendarMonthGrid")) {pick(it)}
                    }
                }
            }
            item(key="selection") {
                Text("已选 · "+selected.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE",Locale.CHINA)),style=MaterialTheme.typography.bodySmall,
                    color=LocalGlass.current.accent,modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp).testTag("calendarSelectedDate"))
            }
        }
    }
    if(monthPicker) GlassMonthPicker(monthText,{monthPicker=false}) {monthText=it;monthPicker=false}
}

/** Inline calendars and date sheets share the same dates, cells and selection semantics. */
@Composable internal fun GlassCalendarMonth(month:YearMonth,selected:LocalDate?,marked:Set<String>,modifier:Modifier=Modifier,choose:(LocalDate)->Unit) {
    val density=LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minimum=calendarDayMinimum(density.fontScale)
        val width=maxOf(maxWidth,minimum*7)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("calendarInlineMonth")) {
            Column(Modifier.width(width).selectableGroup()) {
                GlassCalendarWeekHeader()
                repeat(calendarWeekCount(month)) {week->GlassCalendarWeekRow(month,week,selected,LocalDate.now(),marked,minimum,choose)}
            }
        }
    }
}
private fun calendarDayMinimum(fontScale:Float)=if(fontScale>1.3f) maxOf(64.dp,32.dp*fontScale) else 48.dp
private fun calendarWeekCount(month:YearMonth)=(month.atDay(1).dayOfWeek.value-1+month.lengthOfMonth()+6)/7
@Composable private fun GlassCalendarWeekHeader() {
    Row(Modifier.fillMaxWidth()) {listOf("一","二","三","四","五","六","日").forEach {
        Text(it,Modifier.weight(1f).padding(vertical=8.dp),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
    }}
}
@Composable private fun GlassCalendarWeekRow(month:YearMonth,week:Int,selected:LocalDate?,today:LocalDate,marked:Set<String>,minimum:androidx.compose.ui.unit.Dp=48.dp,pick:(LocalDate)->Unit) {
    val first=month.atDay(1).dayOfWeek.value-1
    Row(Modifier.fillMaxWidth()) {repeat(7) {day->
        val number=week*7+day-first+1
        if(number in 1..month.lengthOfMonth()) CalendarCell(month.atDay(number),selected,today,marked,false,Modifier.weight(1f).heightIn(min=minimum),pick)
        else Spacer(Modifier.weight(1f).height(minimum))
    }}
}

@Composable private fun CalendarCell(date:LocalDate,selected:LocalDate?,today:LocalDate,marked:Set<String>,list:Boolean,modifier:Modifier,pick:(LocalDate)->Unit) {
    val p=LocalGlass.current
    val picked=date==selected
    val label=date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE",Locale.CHINA))+(if(date==today) "，今天" else "")+(if(date.toString() in marked) "，有内容" else "")
    val target=modifier.heightIn(min=52.dp).selectable(picked,role=Role.RadioButton) {pick(date)}
        .semantics {contentDescription=label}.testTag("calendarDay:"+date)
    if(list) Row(target.clip(RoundedCornerShape(16.dp)).background(if(picked) p.selected else Color.Transparent)
        .padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(date.format(DateTimeFormatter.ofPattern("d日  EEEE",Locale.CHINA)),color=if(picked || date==today) p.accent else p.ink,
                fontWeight=if(picked || date==today) FontWeight.SemiBold else FontWeight.Normal)
            if(date==today) Text("今天",style=MaterialTheme.typography.bodySmall,color=p.accent)
        }
        if(picked) Icon(Icons.Outlined.Check,null,Modifier.size(20.dp),tint=p.accent)
        else if(date.toString() in marked) Box(Modifier.size(5.dp).background(p.accent,CircleShape))
    } else {
        val fill by animateColorAsState(if(picked) p.accent else Color.Transparent,tween(if(LocalReduceMotion.current) 0 else GlassMotion.SelectionMs),label="calendar day selection")
        val foreground=if(picked) {if(p.dark) p.background else Color.White} else if(date==today) p.accent else p.ink
        val disc=maxOf(40.dp,24.dp*LocalDensity.current.fontScale)
        Box(target,contentAlignment=Alignment.Center) {
            Box(Modifier.size(disc).clip(CircleShape).background(fill)
                .then(if(date==today&&!picked) Modifier.border(1.dp,p.accent,CircleShape) else Modifier),contentAlignment=Alignment.Center) {
                Text(date.dayOfMonth.toString(),color=foreground,fontWeight=if(picked||date==today) FontWeight.SemiBold else FontWeight.Normal)
                if(date.toString() in marked) Box(Modifier.align(Alignment.BottomCenter).padding(bottom=4.dp).size(4.dp).background(foreground,CircleShape))
            }
        }
    }
}

@Composable internal fun GlassMonthPicker(value:String,dismiss:()->Unit,choose:(String)->Unit) {
    val initial=remember(value) {runCatching {YearMonth.parse(value)}.getOrDefault(YearMonth.now())}
    var year by rememberSaveable {mutableStateOf(initial.year.toString())}
    var month by rememberSaveable {mutableIntStateOf(initial.monthValue)}
    val yearNumber=year.toIntOrNull()
    val valid=yearNumber!=null && yearNumber in 1..9999
    GlassSheet("选择月份",dismiss,footer={PickerActions(dismiss,{choose(YearMonth.of(yearNumber!!,month).toString());dismiss()},enabled=valid,confirmTag="confirmMonth")}) {
        GlassTextField(year,{year=it.take(4)},label={Text("年份")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=!valid,modifier=Modifier.fillMaxWidth().testTag("monthYear"))
        val columns=if(LocalDensity.current.fontScale>1.3f) 2 else 3
        (1..12).toList().chunked(columns).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {row.forEach {number->
            GlassChoice("$number 月",number==month,Modifier.weight(1f)) {month=number}
        }}}
    }
}

@Composable internal fun GlassTimePicker(value:String,dismiss:()->Unit,choose:(String)->Unit) {
    val initial=remember(value) {runCatching {LocalTime.parse(value)}.getOrDefault(LocalTime.of(9,0))}
    var hour by rememberSaveable {mutableStateOf(initial.hour.toString().padStart(2,'0'))}
    var minute by rememberSaveable {mutableStateOf(initial.minute.toString().padStart(2,'0'))}
    val valid=hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59
    GlassSheet("选择时间",dismiss,footer={PickerActions(dismiss,{choose(String.format(Locale.ROOT,"%02d:%02d",hour.toInt(),minute.toInt()));dismiss()},"确定时间",valid,"confirmTime","cancelPlanTime")}) {
        Text("24 小时制",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            GlassTextField(hour,{hour=it.take(2)},label={Text("小时")},supportingText={Text("00–23")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=hour.toIntOrNull() !in 0..23,modifier=Modifier.weight(1f).testTag("timeHour"))
            GlassTextField(minute,{minute=it.take(2)},label={Text("分钟")},supportingText={Text("00–59")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=minute.toIntOrNull() !in 0..59,modifier=Modifier.weight(1f).testTag("timeMinute"))
        }
        FlowChoices(listOf("09:00" to "早上9点","12:00" to "中午12点","18:00" to "傍晚6点","20:00" to "晚上8点"),"$hour:$minute") {hour=it.substringBefore(":");minute=it.substringAfter(":")}
    }
}
