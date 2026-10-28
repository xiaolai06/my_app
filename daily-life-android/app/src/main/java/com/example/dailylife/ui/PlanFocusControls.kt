package com.example.dailylife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailylife.data.local.FocusSession
import com.example.dailylife.feature.plans.*

internal data class FocusReading(val total:Long,val phaseSeconds:Long,val phaseTarget:Long,val roundWork:Long) {
    fun canFinish(session:FocusSession)=session.mode=="stopwatch" || roundWork>=session.plannedSeconds
}

internal fun focusReading(session:FocusSession,ui:PlansUi):FocusReading {
    val running=ui.data.sessions.firstOrNull {it.state=="running" && it.deletedAt==null}
    val live=if(session.state=="running" && running?.id==session.id) ui.liveSeconds.coerceAtLeast(0) else 0
    val segments=ui.data.segments.filter {it.sessionId==session.id}
    val total=segments.filter {it.phase=="work"}.sumOf {it.seconds}+if(session.phase=="work") live else 0
    val phase=segments.filter {it.phase==session.phase && it.round==session.round}.sumOf {it.seconds}+live
    val roundWork=segments.filter {it.phase=="work" && it.round==session.round}.sumOf {it.seconds}+if(session.phase=="work") live else 0
    val target=if(session.phase=="work") session.plannedSeconds else if(session.round%session.rounds.coerceAtLeast(1)==0) session.longBreak else session.shortBreak
    return FocusReading(total,phase,target.coerceAtLeast(1),roundWork)
}

internal fun focusClock(seconds:Long):String {
    val n=seconds.coerceAtLeast(0)
    return if(n<3600) "%02d:%02d".format(java.util.Locale.ROOT,n/60,n%60)
    else "%d:%02d:%02d".format(java.util.Locale.ROOT,n/3600,n/60%60,n%60)
}

/** The primary action follows the current phase; record maintenance stays in a secondary sheet. */
@Composable internal fun FocusControls(s:FocusSession,ui:PlansUi,vm:PlansViewModel,correct:()->Unit) {
    val reading=focusReading(s,ui)
    val active=s.state in setOf("running","paused","review","awaiting")
    val editable=s.deletedAt==null && !ui.busy
    val review=s.state=="review" || s.state=="running" && ui.clockReview
    val largeFont=LocalDensity.current.fontScale>1.3f
    var endSheet by remember(s.id) {mutableStateOf(false)}
    var confirm by remember(s.id) {mutableStateOf<String?>(null)}
    val remaining=(reading.phaseTarget-reading.phaseSeconds).coerceAtLeast(0)
    val display=if(!active || s.mode=="stopwatch") reading.total else remaining
    val timerLabel=if(!active) "有效专注" else if(s.mode=="stopwatch") "已专注" else if(s.phase=="break") "休息剩余" else "剩余时间"
    val phaseLabel=if(!active) stateName(s.state) else if(review) "待核对" else if(s.state=="paused") "已暂停" else if(s.phase=="break") "休息" else if(s.mode=="pomodoro") "第 ${s.round} 轮" else if(s.mode=="stopwatch") "正计时" else "倒计时"
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(phaseLabel,style=MaterialTheme.typography.labelLarge,color=LocalGlass.current.muted)
        Text(focusClock(display),Modifier.fillMaxWidth().testTag("focusTimer").semantics {
            contentDescription="$timerLabel ${focusClock(display)}"
        },fontSize=if(largeFont) 26.sp else 48.sp,lineHeight=if(largeFont) 34.sp else 60.sp,
            fontWeight=FontWeight.Medium,textAlign=TextAlign.Center,color=LocalGlass.current.ink)
        if(active && s.mode!="stopwatch") PlanLinearProgress(reading.phaseSeconds.toFloat()/reading.phaseTarget,"当前阶段进度")
        if(active && s.mode!="stopwatch") Text("已专注 "+planQuantity(reading.total*1000,"秒",true),
            style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        if(review) Text("计时发生变化，请核对后继续",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error,textAlign=TextAlign.Center)
        if(active) {
            val primary=when {
                review->"核对后继续"
                s.mode=="pomodoro" && s.phase=="work" && reading.canFinish(s)->"开始休息"
                s.mode=="pomodoro" && s.phase=="break" && remaining==0L->"开始下一轮"
                s.mode!="stopwatch" && s.mode!="pomodoro" && reading.canFinish(s)->"完成专注"
                s.state=="paused"->"继续"
                s.state=="running"->"暂停"
                else->"结束专注"
            }
            GlassButton(enabled=editable,onClick={
                when(primary) {
                    "核对后继续"->confirm="recover"
                    "开始休息"->vm.focus(s.id,"break")
                    "开始下一轮"->vm.focus(s.id,"next")
                    "完成专注"->confirm="finish"
                    "继续"->vm.focus(s.id,"resume")
                    "暂停"->vm.focus(s.id,"pause")
                    else->endSheet=true
                }
            },modifier=Modifier.fillMaxWidth().testTag("focusPrimary")) {Text(primary)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                if(s.mode=="pomodoro" && s.phase=="break" && remaining>0 && !review) {
                    TextButton(enabled=editable,onClick={vm.focus(s.id,"next")},modifier=Modifier.testTag("focusNextRound")) {Text("开始下一轮")}
                }
                TextButton(enabled=editable,onClick={endSheet=true},modifier=Modifier.testTag("focusEnd")) {Text("结束")}
            }
        }
    }
    if(endSheet) GlassSheet("结束专注",{endSheet=false},dismissEnabled=!ui.busy) {
        if(reading.canFinish(s) && !review) GlassButton(enabled=editable,onClick={endSheet=false;confirm="finish"},modifier=Modifier.fillMaxWidth().testTag("focusFinish")) {Text("完成专注")}
        else GlassButton(enabled=editable && !review,onClick={endSheet=false;confirm="early"},modifier=Modifier.fillMaxWidth().testTag("focusEarly")) {Text("提前结束")}
        TextButton(enabled=editable,onClick={endSheet=false;confirm="abandon"},modifier=Modifier.fillMaxWidth().testTag("focusAbandon")) {Text("放弃本次",color=MaterialTheme.colorScheme.error)}
    }
    confirm?.let {action->
        ConfirmDialog(if(action=="recover") "继续计时？" else "结束这次专注？",when(action) {
            "recover"->"无法确认的时段将舍弃，已记录的专注保留。"
            "abandon"->"记录会保留，不计入有效专注。"
            else->"保留已完成的工作时间。"
        },{confirm=null},{
            if(editable) {
                if(action=="recover" && s.state!="review") vm.run(null) {refresh();focusAction(s.id,"recover")}
                else vm.focus(s.id,action)
            }
        },if(action=="recover") "继续" else "确定")
    }
}
