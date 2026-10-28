package com.example.dailylife.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.PlanRules
import com.example.dailylife.feature.plans.*
import com.example.dailylife.data.local.Habit
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable internal fun PlanInlineTabs(options:List<Pair<String,String>>,selected:String,modifier:Modifier=Modifier,tagPrefix:String="planFilter",choose:(String)->Unit) {
    val palette=LocalGlass.current
    Row(modifier.horizontalScroll(rememberScrollState()).selectableGroup()) {
        options.forEach {(key,title)->
            Box(Modifier.heightIn(min=48.dp).drawBehind {
                if(key==selected) drawRoundRect(palette.accent,Offset(12.dp.toPx(),size.height-3.dp.toPx()),
                    Size((size.width-24.dp.toPx()).coerceAtLeast(0f),2.dp.toPx()),CornerRadius(1.dp.toPx()))
            }.selectable(key==selected,role=Role.Tab) {choose(key)}.testTag("$tagPrefix:$key")
                .padding(horizontal=12.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                Text(title,style=MaterialTheme.typography.labelLarge,color=if(key==selected) palette.accent else palette.muted,
                    fontWeight=if(key==selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable internal fun PlanQuietEmpty(label:String,modifier:Modifier=Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical=30.dp),contentAlignment=Alignment.Center) {
        Text(label,style=MaterialTheme.typography.bodyMedium,color=LocalGlass.current.muted)
    }
}

@Composable internal fun PlanReminderMark(summary:PlanReminderSummary) {
    val color=if(summary.attention) MaterialTheme.colorScheme.error else LocalGlass.current.muted
    Box(Modifier.size(24.dp).semantics {contentDescription=summary.label+"，"+summary.detail},contentAlignment=Alignment.Center) {
        PlanGlyph("bell",Modifier.size(16.dp),color.copy(alpha=if(summary.label=="未设提醒") .45f else 1f))
    }
}

@Composable internal fun PlanLinearProgress(fraction:Float,description:String,modifier:Modifier=Modifier) {
    val palette=LocalGlass.current
    val target=boundedPlanProgress(fraction)
    val animated=animateFloatAsState(target,tween(if(LocalReduceMotion.current) 0 else 320),label="plan list progress")
    Canvas(modifier.fillMaxWidth().height(5.dp).semantics {
        contentDescription=description;progressBarRangeInfo=ProgressBarRangeInfo(target,0f..1f)
    }) {
        val corner=CornerRadius(size.height/2)
        drawRoundRect(palette.accent.copy(alpha=.12f),cornerRadius=corner)
        if(animated.value>0f) drawRoundRect(Brush.horizontalGradient(listOf(palette.accent,if(palette.dark) Color(0xFFCCB5EF) else Color(0xFF8675B8))),
            size=Size(size.width*animated.value,size.height),cornerRadius=corner)
    }
}

/** One navigation surface. Only the active lens moves; the labels never change width. */
@Composable internal fun PlanTabBar(selected:String,onSelect:(String)->Unit) {
    val tabs=listOf("任务","日历","习惯","专注","目标")
    val palette=LocalGlass.current
    val position=animateFloatAsState(tabs.indexOf(selected).coerceAtLeast(0).toFloat(),
        tween(if(LocalReduceMotion.current) 0 else 220),label="plan tab lens")
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp)
        .glassSurface(palette,RoundedCornerShape(20.dp))) {
        val cell=maxOf((maxWidth-8.dp)/5,48.dp+24.dp*(fontScale-1f).coerceAtLeast(0f))
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.width(cell*5+8.dp).selectableGroup().drawBehind {
                val inset=4.dp.toPx()
                drawRoundRect(Brush.linearGradient(listOf(palette.selected,palette.selected.copy(alpha=.55f))),
                    topLeft=Offset(inset+cell.toPx()*position.value,inset),
                    size=Size(cell.toPx(),size.height-2*inset),cornerRadius=CornerRadius(16.dp.toPx()))
            }.padding(4.dp)) {
                tabs.forEach {name->
                    val requester=remember {BringIntoViewRequester()}
                    LaunchedEffect(selected) {if(selected==name) requester.bringIntoView()}
                    Box(Modifier.width(cell).heightIn(min=48.dp).bringIntoViewRequester(requester)
                        .selectable(selected==name,role=Role.Tab,onClick={onSelect(name)})
                        .testTag("planTab:$name").padding(horizontal=6.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                        Text(name,style=MaterialTheme.typography.labelLarge,
                            color=if(selected==name) palette.accent else palette.muted,
                            fontWeight=if(selected==name) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** Small original line drawings, shared across the plan tabs and their content. */
@Composable internal fun PlanGlyph(kind:String,modifier:Modifier=Modifier,color:Color=LocalGlass.current.accent) {
    Canvas(modifier.size(22.dp)) {
        val u=size.minDimension/24f;val stroke=Stroke(1.7f*u,cap=StrokeCap.Round,join=StrokeJoin.Round)
        fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,Offset(x*u,y*u),Offset(x2*u,y2*u),1.7f*u,StrokeCap.Round)
        when(kind) {
            "event","date"->{drawRoundRect(color,Offset(3*u,5*u),Size(18*u,16*u),CornerRadius(3*u),style=stroke)
                line(3f,10f,21f,10f);line(8f,3f,8f,7f);line(16f,3f,16f,7f);drawCircle(color,1.4f*u,Offset(8*u,15*u))}
            "habit"->{drawCircle(color,8.5f*u,Offset(12*u,12*u),style=stroke);line(7f,12f,10.5f,15.5f);line(10.5f,15.5f,17f,8.5f)}
            "goal"->{listOf(9f,5.5f,2f).forEach {drawCircle(color,it*u,Offset(12*u,12*u),style=stroke)}}
            "focus","time"->{drawCircle(color,8.5f*u,Offset(12*u,13*u),style=stroke);line(12f,8f,12f,13f);line(12f,13f,16f,15f);line(9f,1.5f,15f,1.5f)}
            "bell"->{val path=Path().apply {moveTo(5*u,17*u);lineTo(7*u,14*u);lineTo(7*u,9*u);cubicTo(7*u,2*u,17*u,2*u,17*u,9*u);lineTo(17*u,14*u);lineTo(19*u,17*u);close()}
                drawPath(path,color,style=stroke);line(10f,21f,14f,21f)}
            "search"->{drawCircle(color,6.7f*u,Offset(10*u,10*u),style=stroke);line(15f,15f,21f,21f)}
            "more"->listOf(5f,12f,19f).forEach {drawCircle(color,1.6f*u,Offset(it*u,12*u))}
            "filter"->{line(3f,6f,21f,6f);line(6f,12f,18f,12f);line(9f,18f,15f,18f)}
            "flag"->{line(5f,21f,5f,3f);val path=Path().apply {moveTo(5*u,4*u);lineTo(19*u,4*u);lineTo(16*u,8*u);lineTo(19*u,12*u);lineTo(5*u,12*u)};drawPath(path,color,style=stroke)}
            "repeat"->{drawArc(color,25f,280f,false,Offset(4*u,4*u),Size(16*u,16*u),style=stroke);line(18f,3f,18f,8f);line(18f,8f,13f,8f)}
            "check"->{line(5f,12f,10f,17f);line(10f,17f,20f,6f)}
            "down"->{line(6f,9f,12f,15f);line(12f,15f,18f,9f)}
            "up"->{line(6f,15f,12f,9f);line(12f,9f,18f,15f)}
            "right"->{line(9f,6f,15f,12f);line(15f,12f,9f,18f)}
            else->{drawRoundRect(color,Offset(3*u,3*u),Size(18*u,18*u),CornerRadius(4*u),style=stroke);line(7f,11f,10f,14f);line(10f,14f,17f,7f);line(8f,18f,16f,18f)}
        }
    }
}

@Composable internal fun PlanBadge(kind:String,label:String,emphasis:Boolean=false) {
    val color=if(emphasis) MaterialTheme.colorScheme.error else LocalGlass.current.muted
    Row(Modifier.background(color.copy(alpha=.07f),CircleShape).padding(horizontal=9.dp,vertical=5.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        PlanGlyph(kind,Modifier.size(14.dp),color)
        Text(label,style=MaterialTheme.typography.labelMedium,color=color)
    }
}

internal fun boundedPlanProgress(value:Float)=if(value.isFinite()) value.coerceIn(0f,1f) else 0f

@Composable internal fun PlanProgressRing(fraction:Float,label:String,description:String,modifier:Modifier=Modifier,measurable:Boolean=true) {
    val palette=LocalGlass.current
    val progress=boundedPlanProgress(fraction)
    val animated=animateFloatAsState(progress,tween(if(LocalReduceMotion.current) 0 else 360),label="plan ring progress")
    Box(modifier.size(if(LocalDensity.current.fontScale>1.3f) 88.dp else 68.dp).semantics(mergeDescendants=true) {
        contentDescription="$description $label"
        if(measurable) progressBarRangeInfo=ProgressBarRangeInfo(progress,0f..1f)
    },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke=Stroke(5.dp.toPx(),cap=StrokeCap.Round)
            drawCircle(palette.selected.copy(alpha=.3f),radius=size.minDimension*.38f)
            drawArc(palette.accent.copy(alpha=.13f),0f,360f,false,style=stroke)
            if(measurable && animated.value>0f) drawArc(
                Brush.sweepGradient(listOf(palette.accent,if(palette.dark) Color(0xFFD0B9F5) else Color(0xFF8B78BB),palette.accent)),
                -90f,360f*animated.value,false,style=stroke)
        }
        Text(label,Modifier.clearAndSetSemantics {},style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold,color=palette.accent)
    }
}

/** Short checklists show one segment per step; long lists retain a continuous, exact scale. */
@Composable internal fun PlanStepProgress(completed:Int,total:Int,modifier:Modifier=Modifier) {
    val palette=LocalGlass.current
    val target=if(total>0) completed.coerceIn(0,total).toFloat()/total else 0f
    val animated=animateFloatAsState(target,tween(if(LocalReduceMotion.current) 0 else 320),label="plan checklist progress")
    Canvas(modifier.fillMaxWidth().height(8.dp).semantics {
        contentDescription="子任务已完成 $completed 项，共 $total 项"
        progressBarRangeInfo=ProgressBarRangeInfo(target,0f..1f)
    }) {
        val count=if(total in 2..8) total else 1
        val gap=if(count>1) 4.dp.toPx() else 0f
        val width=(size.width-gap*(count-1))/count
        val brush=Brush.horizontalGradient(listOf(palette.accent,if(palette.dark) Color(0xFFCCB5EF) else Color(0xFF8675B8)))
        repeat(count) {index->
            val origin=Offset(index*(width+gap),0f)
            val radius=CornerRadius(size.height/2)
            drawRoundRect(palette.accent.copy(alpha=.12f),origin,Size(width,size.height),radius)
            val fill=(animated.value*count-index).coerceIn(0f,1f)
            if(fill>0f) drawRoundRect(brush,origin,Size(width*fill,size.height),radius)
        }
    }
}

internal fun planDateLabel(value:String,today:LocalDate=LocalDate.now()):String {
    val date=runCatching {LocalDate.parse(value)}.getOrNull() ?: return value
    return when(date) {today->"今天";today.plusDays(1)->"明天";today.minusDays(1)->"昨天";
        else->date.format(DateTimeFormatter.ofPattern(if(date.year==today.year) "M月d日" else "yyyy年M月d日"))}
}

internal fun taskSection(row:TaskRow,today:LocalDate=LocalDate.now(),time:LocalTime=LocalTime.now()):String = when {
    row.status=="done"->"已完成"
    row.status in setOf("cancelled","skipped")->"已取消与跳过"
    PlanRules.overdue(row.due,row.task.dueTime,today.atTime(time))->"已逾期"
    row.date==today.toString() || row.due==today.toString()->"今天"
    row.date==null && row.due==null->"待安排"
    (row.date ?: row.due.orEmpty())<today.toString()->"此前安排"
    else->"接下来"
}

@Composable internal fun PlanSectionHeading(title:String,count:Int) {
    Row(Modifier.fillMaxWidth().testTag("planSection:$title").padding(top=12.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).background(if(title=="已逾期") MaterialTheme.colorScheme.error else LocalGlass.current.accent,CircleShape))
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Text(count.toString(),style=MaterialTheme.typography.labelLarge,color=LocalGlass.current.muted)
    }
}

@Composable internal fun HabitWeek(snapshot:PlansSnapshot,habit:Habit,selected:LocalDate) {
    val monday=selected.with(java.time.DayOfWeek.MONDAY)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        repeat(7) {index->
            val date=monday.plusDays(index.toLong()).toString();val rule=snapshot.habitRule(habit.id,date)
            val done=rule!=null && snapshot.habitTotal(habit.id,date)>=rule.targetMilli
            val scheduled=snapshot.habitScheduled(habit,date)
            val label=if(done) "已达标" else if(!scheduled) "休息日" else if(date>LocalDate.now().toString()) "待打卡" else "未达标"
            Column(Modifier.weight(1f).semantics(mergeDescendants=true) {contentDescription="$date $label"},horizontalAlignment=Alignment.CenterHorizontally) {
                Text(listOf("一","二","三","四","五","六","日")[index],Modifier.clearAndSetSemantics {},style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.muted)
                Box(Modifier.padding(top=5.dp).size(27.dp).background(if(done) LocalGlass.current.accent else LocalGlass.current.selected,CircleShape),contentAlignment=Alignment.Center) {
                    if(done) PlanGlyph("check",Modifier.size(16.dp),if(LocalGlass.current.dark) LocalGlass.current.background else Color.White)
                    else Text(if(scheduled) "·" else "–",Modifier.clearAndSetSemantics {},style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.muted)
                }
            }
        }
    }
}
