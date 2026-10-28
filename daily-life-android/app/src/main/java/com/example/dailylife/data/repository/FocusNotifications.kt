package com.example.dailylife.data.repository

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.dailylife.MainActivity
import com.example.dailylife.R
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.plans.PlansSnapshot
import kotlinx.coroutines.flow.first

/** The OS draws the chronometer. No per-second service, wake lock or database polling. */
class FocusNotifications(private val context:Context,private val repo:PlansRepository) {
    private val manager=context.getSystemService(NotificationManager::class.java)
    private val preferences=PreferencesRepository(context)
    // Stable channel identity preserves Android's existing sound/visibility preferences.
    companion object {const val ID=-406;const val CHANNEL="focus_live"}
    private fun action(id:String,action:String)=PendingIntent.getBroadcast(context,0,
        Intent(context,PlanAlarmReceiver::class.java).setAction("com.example.dailylife."+action)
            .setData(Uri.parse("daily-life://focus/$id/$action")).putExtra("focusId",id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun hide(id:String) {preferences.hideFocusNotification(id);manager.cancel(ID)}
    suspend fun render(s:PlansSnapshot,canNotify:Boolean) {
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"专注计时",NotificationManager.IMPORTANCE_LOW).apply {
            description="在通知栏显示计时，可暂停或继续，结束后移除";lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        })
        val focus=s.sessions.firstOrNull {it.deletedAt==null && it.endedAt==null && it.state in setOf("running","paused")}
        val display=preferences.focusNotifications.first()
        if(focus==null || !canNotify || !display.first || display.second==focus.id) {manager.cancel(ID);return}
        val seconds=if(focus.state=="running") repo.openSeconds(focus,s.segments) ?: run {manager.cancel(ID);return} else 0
        val used=s.segments.filter {it.sessionId==focus.id && it.round==focus.round && it.phase==focus.phase && it.endAt!=null}.sumOf {it.seconds}+seconds
        val goal=focus.phaseSeconds()
        if(focus.mode!="stopwatch" && used>=goal) {manager.cancel(ID);return}
        val running=focus.state=="running"
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).setAction("com.example.dailylife.OPEN_PLAN")
            .setData(Uri.parse("daily-life://focus/${focus.id}/open")).putExtra("planKind","focus").putExtra("planId",focus.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicNotice=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_daily).setContentTitle("专注计时中").setContentText("解锁后查看").build()
        val builder=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_daily).setContentTitle(focus.title)
            .setContentText(if(!running) "已暂停 · 可选择继续" else if(focus.phase=="break") "休息时间" else "留一点时间给自己")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicNotice)
            .setWhen(System.currentTimeMillis()+(if(focus.mode=="stopwatch") -used else goal-used)*1000)
            .setShowWhen(running).setUsesChronometer(running).setChronometerCountDown(focus.mode!="stopwatch")
            .addAction(Notification.Action.Builder(null,if(running) "暂停" else "继续",action(focus.id,if(running) "FOCUS_PAUSE" else "FOCUS_RESUME")).build())
            .addAction(Notification.Action.Builder(null,"收起通知",action(focus.id,"FOCUS_HIDE")).build())
            .setDeleteIntent(action(focus.id,"FOCUS_HIDE"))
        try {manager.notify(ID,builder.build())} catch(_:SecurityException) {manager.cancel(ID)}
    }
}
fun FocusSession.phaseSeconds()=if(phase=="break") {if(round % rounds==0) longBreak else shortBreak} else plannedSeconds
fun PlansSnapshot.focusDeadline(s:FocusSession):Pair<FocusSegment,Long>? {
    if(s.deletedAt!=null || s.endedAt!=null || s.state!="running" || s.mode=="stopwatch") return null
    val open=segments.lastOrNull {it.sessionId==s.id && it.endAt==null} ?: return null
    val used=segments.filter {it.sessionId==s.id && it.round==s.round && it.phase==s.phase && it.endAt!=null}.sumOf {it.seconds}
    return open to open.startAt+(s.phaseSeconds()-used).coerceAtLeast(0)*1000
}
