package com.example.dailylife.data.repository

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.dailylife.*
import com.example.dailylife.core.ReminderDeliveryPolicy
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.plans.PlansSnapshot
import com.example.dailylife.feature.tools.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.time.*

/** One durable schedule, revalidated against the original source before every delivery/action. */
class PlanAlarms(private val context:Context,private val repo:PlansRepository,private val clock:()->Long=System::currentTimeMillis) {
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    private suspend fun <T> locked(block:suspend ()->T):T=withContext(Dispatchers.IO) {repo.writes.withLock {block()}}
    companion object { const val CHANNEL="life_reminders_v2" }
    fun enabled():Boolean {
        channel()
        return appNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL).importance!=NotificationManager.IMPORTANCE_NONE
    }
    private fun appNotificationsEnabled()=(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && notifications.areNotificationsEnabled()
    fun precise():Boolean=Build.VERSION.SDK_INT<31 || alarms.canScheduleExactAlarms()
    fun timingDescription()=if(precise()) "精确闹钟已允许；仍受手机后台管理影响" else "系统未允许精确闹钟，提醒可能延迟"
    private fun channel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,"生活提醒与横幅",NotificationManager.IMPORTANCE_HIGH).apply {
            description="任务、日程、习惯、订阅、缴费与物品提醒";lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        })
    }
    private fun intent(id:Long,action:String="PLAN_ALARM")=PendingIntent.getBroadcast(context,0,
        Intent(context,PlanAlarmReceiver::class.java).setAction("com.example.dailylife."+action)
            .setData(android.net.Uri.parse("daily-life://reminder/"+id+"/"+action)).putExtra("job",id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancel(job:PlanReminder) { alarms.cancel(intent(job.id)); notifications.cancel(job.id.toInt()) }
    private fun schedule(job:PlanReminder) {
        val time=job.deferredUntil ?: job.triggerAt
        try {
            if(precise()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,intent(job.id))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,intent(job.id))
        } catch(e:SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,intent(job.id)) }
    }
    private fun PlanReminder.key()=Triple(sourceType,sourceId,instanceKey)
    private suspend fun desired(s:PlansSnapshot):List<PlanReminder> {
        val wanted=mutableListOf<PlanReminder>();val zone=ZoneId.systemDefault()
        s.taskRows().filter { it.status in setOf("todo","doing") }.forEach { row ->
            val t=row.task
            if(row.occurrence==null) t.reminderAt?.let { wanted+=PlanReminder(sourceType="task",sourceId=t.id,instanceKey="single",title=t.title,triggerAt=it) }
            t.reminderMinutes?.let { minutes ->
                val date=row.due ?: row.date
                if(date!=null) wanted+=PlanReminder(sourceType="task",sourceId=t.id,instanceKey=row.occurrence?.originalKey ?: "relative",
                    title=row.title,triggerAt=LocalDate.parse(date).atTime(t.dueTime?.let(LocalTime::parse) ?: LocalTime.of(9,0)).atZone(zone).toInstant().toEpochMilli()-minutes*60000L)
            }
        }
        s.events.filter { it.deletedAt==null && it.archivedAt==null && it.status=="scheduled" && it.reminderMinutes!=null }.forEach { e ->
            val repeated=s.rules.any { it.entityType=="event" && it.entityId==e.id }
            val occurrences=if(repeated) s.occurrences.filter { it.entityId==e.id && it.status=="scheduled" } else listOf<Occurrence?>(null)
            occurrences.forEach { o ->
                val date=o?.scheduledDate ?: e.startDate
                val trigger=LocalDate.parse(date).atTime(if(e.allDay) LocalTime.of(9,0) else LocalTime.parse(e.startTime)).atZone(ZoneId.of(e.zoneId)).toInstant().toEpochMilli()-e.reminderMinutes!!*60000L
                wanted+=PlanReminder(sourceType="event",sourceId=e.id,instanceKey=o?.originalKey ?: "single",title=o?.title ?: e.title,triggerAt=trigger)
            }
        }
        s.habits.filter { it.deletedAt==null && it.status=="active" && it.reminderTime!=null }.forEach { h ->
            for(n in 0L..14L) { val date=Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate().plusDays(n).toString()
                val r=s.habitRule(h.id,date)
                if(r!=null && s.habitScheduled(h,date) && s.habitTotal(h.id,date)<r.targetMilli)
                    wanted+=PlanReminder(sourceType="habit",sourceId=h.id,instanceKey=date,title=h.name,
                        triggerAt=LocalDate.parse(date).atTime(LocalTime.parse(h.reminderTime)).atZone(zone).toInstant().toEpochMilli())
            }
        }
        val tools=repo.db.tools().let {d->ToolsSnapshot(entries=d.entries(),logs=d.logs(),periods=d.periods())}
        wanted+=tools.reminders()
        s.sessions.forEach {focus->
            s.focusDeadline(focus)?.let {(segment,deadline)->
                if(repo.openSeconds(focus,s.segments)!=null) wanted+=PlanReminder(sourceType="focus",sourceId=focus.id,instanceKey=segment.id,
                    title=focus.title+(if(focus.phase=="break") " · 休息结束" else " · 本段完成"),triggerAt=deadline)
            }
        }
        return wanted
    }
    suspend fun reconcile()=locked {
        val s=repo.load();val now=clock();val can=enabled()
        val wanted=desired(s).associateBy {it.key()}
        val previous=s.reminders.associateBy {it.key()}
        val candidates=wanted.values.map {next->
            val old=previous[next.key()]
            if(old?.triggerAt==next.triggerAt) old.copy(title=next.title) else next.copy(id=old?.id ?: 0)
        }
        val future=candidates.filter {it.postedAt==null && (it.deferredUntil ?: it.triggerAt)>now}
            .sortedBy {it.deferredUntil ?: it.triggerAt}.take(300)
        val scheduled=future.map {it.key()}.toSet()
        s.reminders.forEach {old->
            val current=wanted[old.key()]
            if(current==null || current.triggerAt!=old.triggerAt) {
                cancel(old);repo.dao.put(old.copy(state="cancelled",deferredUntil=null))
            } else if(old.postedAt==null && old.key() !in scheduled) {
                val time=old.deferredUntil ?: old.triggerAt
                if(time<=now && ReminderDeliveryPolicy.eligible(time,now,old.sourceType,ZoneId.systemDefault())) return@forEach
                alarms.cancel(intent(old.id))
                repo.dao.put(old.copy(state=if(time<=now) "expired" else "pending"))
            }
        }
        future.forEach {next->
            val state=if(can) {if(next.deferredUntil!=null) "snoozed" else "scheduled"} else "blocked"
            val job=next.copy(state=state)
            val id=repo.dao.put(job)
            alarms.cancel(intent(id))
            if(can) try {schedule(job.copy(id=id))}
            catch(e:Exception) {repo.dao.put(job.copy(id=id,state="failed"))}
        }
        val refill=intent(-99,"PLAN_REFILL")
        alarms.cancel(refill)
        if(wanted.isNotEmpty() || s.rules.any {it.stoppedFrom==null}) {
            try {alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,now+12*60*60*1000L,refill)}
            catch(e:Exception) {android.util.Log.w("PlanAlarms","Refill unavailable",e)}
        }
        try {FocusNotifications(context,repo).render(s,appNotificationsEnabled())} catch(e:Exception) {if(e is CancellationException) throw e;android.util.Log.w("PlanAlarms","Focus notification unavailable",e)}
    }
    private suspend fun current(job:PlanReminder,s:PlansSnapshot)=desired(s).any {
        it.key()==job.key() && it.triggerAt==job.triggerAt
    }
    suspend fun snooze(id:Long,minutes:Int=15)=locked {
        require(minutes in setOf(15,30,60)) {"请选择 15、30 或 60 分钟"}
        val job=repo.dao.reminders().firstOrNull {it.id==id} ?: return@locked
        val now=clock()
        // Duplicate notification actions must not move an already deferred alarm again.
        if(job.deferredUntil!=null && job.deferredUntil>now && job.postedAt==null) return@locked
        require(job.readAt==null && current(job,repo.load()) && (job.deferredUntil ?: job.triggerAt)<=now) {"原事项已变更或提醒已处理"}
        val until=now+minutes*60000L
        require(ReminderDeliveryPolicy.eligible(job.triggerAt,until,job.sourceType,ZoneId.systemDefault())) {"已超出本次提醒的有效时间"}
        val next=job.copy(deferredUntil=until,postedAt=null,readAt=null,state=if(enabled()) "snoozed" else "blocked")
        repo.dao.put(next);cancel(job)
        if(enabled()) try {schedule(next)} catch(e:Exception) {repo.dao.put(next.copy(state="failed"));throw e}
    }
    suspend fun acknowledge(id:Long)=locked {
        repo.dao.reminders().firstOrNull {it.id==id}?.let {job->
            if((job.deferredUntil ?: job.triggerAt)<=clock()) {repo.dao.put(job.copy(readAt=clock()));notifications.cancel(id.toInt())}
        }
    }
    suspend fun deliver(id:Long) {
        reconcile()
        locked {
            val job=repo.dao.reminders().firstOrNull {it.id==id} ?: return@locked
            val now=clock();val time=job.deferredUntil ?: job.triggerAt
            if(job.postedAt!=null || job.readAt!=null) return@locked
            val s=repo.load()
            val state=when {
                !current(job,s)->"cancelled"
                now<time->return@locked
                !ReminderDeliveryPolicy.eligible(time,now,job.sourceType,ZoneId.systemDefault())->"expired"
                !enabled()->"blocked"
                else->null
            }
            if(state!=null) {repo.dao.put(job.copy(state=state));return@locked}
            val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java)
                .setAction(if(job.sourceType=="tool") "com.example.dailylife.OPEN_TOOL" else "com.example.dailylife.OPEN_PLAN")
                .setData(android.net.Uri.parse("daily-life://reminder/"+id+"/open"))
                .putExtra("planKind",job.sourceType).putExtra("planId",job.sourceId).putExtra("toolId",job.sourceId).putExtra("jobId",job.id)
                .putExtra("instanceId",s.occurrences.firstOrNull {it.entityId==job.sourceId && it.originalKey==job.instanceKey}?.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val subtitle=reminderLabel(job)+" · 点按查看原事项"
            val publicVersion=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_daily)
                .setContentTitle("My Life 提醒").setContentText("解锁后查看内容").build()
            val notice=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_daily)
                .setContentTitle(job.title).setContentText(subtitle).setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
                .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
                .addAction(0,"15 分钟后",intent(id,"REMINDER_SNOOZE"))
                .addAction(0,"知道了",intent(id,"REMINDER_READ")).build()
            try {notifications.notify(id.toInt(),notice);repo.dao.put(job.copy(state="posted",postedAt=now))}
            catch(e:SecurityException) {repo.dao.put(job.copy(state="blocked"))}
        }
    }
}
fun reminderLabel(job:PlanReminder)=when(job.sourceType) {
    "task"->"待办时间到了";"event"->"日程即将开始";"habit"->"留一点时间给好习惯";"focus"->"计时已到，查看后决定下一段"
    "tool"->when {job.instanceKey.startsWith("period:")->"续费 / 缴费提醒";job.instanceKey=="trial"->"试用即将结束"
        job.instanceKey=="warranty"->"保修即将到期";job.instanceKey=="expiry"->"物品到期提醒";job.instanceKey=="care"->"物品养护提醒"
        job.instanceKey=="return"->"物品归还提醒";job.instanceKey.startsWith("anniversary:")->"重要日子提醒";else->"生活事项提醒"}
    else->"My Life 提醒"
}
class PlanAlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val app=context.applicationContext as DailyLifeApplication
                app.plans.initialize();app.tools.initialize()
                when(intent.action) {
                    "com.example.dailylife.FOCUS_HIDE"->FocusNotifications(context,app.plans).hide(intent.getStringExtra("focusId").orEmpty())
                    "com.example.dailylife.FOCUS_PAUSE","com.example.dailylife.FOCUS_RESUME"->{
                        app.plans.focusAction(intent.getStringExtra("focusId").orEmpty(),if(intent.action!!.endsWith("PAUSE")) "pause" else "resume")
                        app.planAlarms.reconcile()
                    }
                    "com.example.dailylife.PLAN_ALARM"->app.planAlarms.deliver(intent.getLongExtra("job",-1))
                    "com.example.dailylife.REMINDER_SNOOZE"->app.planAlarms.snooze(intent.getLongExtra("job",-1))
                    "com.example.dailylife.REMINDER_READ"->app.planAlarms.acknowledge(intent.getLongExtra("job",-1))
                    else->app.planAlarms.reconcile()
                }
            } catch(e:Exception) {android.util.Log.w("PlanAlarms","Reminder action unavailable",e)}
            finally {pending.finish()}
        }
    }
}
