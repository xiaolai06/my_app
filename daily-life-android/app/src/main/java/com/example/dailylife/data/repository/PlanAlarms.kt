package com.example.dailylife.data.repository

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.dailylife.*
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.tools.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.time.*

/** Inexact system alarms. Permission denial never deletes the user's reminder settings. */
class PlanAlarms(private val context:Context,private val repo:PlansRepository) {
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    fun enabled():Boolean {
        channel()
        return (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
            notifications.areNotificationsEnabled() && notifications.getNotificationChannel("plans").importance!=NotificationManager.IMPORTANCE_NONE
    }
    private fun channel() { notifications.createNotificationChannel(NotificationChannel("plans","计划提醒",NotificationManager.IMPORTANCE_DEFAULT)) }
    private fun intent(id:Long)=PendingIntent.getBroadcast(context,id.toInt(),
        Intent(context,PlanAlarmReceiver::class.java).setAction("com.example.dailylife.PLAN_ALARM").putExtra("job",id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun reconcile()=repo.writes.withLock {
        val s=repo.load();val now=System.currentTimeMillis();val can=enabled()
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
            for(n in 0L..14L) { val date=LocalDate.now().plusDays(n).toString()
                val r=s.habitRule(h.id,date)
                if(r!=null && s.habitScheduled(h,date) && s.habitTotal(h.id,date)<r.targetMilli)
                    wanted+=PlanReminder(sourceType="habit",sourceId=h.id,instanceKey=date,title=h.name,
                        triggerAt=LocalDate.parse(date).atTime(LocalTime.parse(h.reminderTime)).atZone(zone).toInstant().toEpochMilli())
            }
        }
        val tools=repo.db.tools().let {d->ToolsSnapshot(entries=d.entries(),logs=d.logs(),periods=d.periods())}
        wanted+=tools.reminders()
        val future=wanted.filter { it.triggerAt>now }.sortedBy { it.triggerAt }.take(300)
        s.reminders.forEach { old ->
            alarms.cancel(intent(old.id))
            if(future.none { it.sourceType==old.sourceType && it.sourceId==old.sourceId && it.instanceKey==old.instanceKey }) {
                if(old.state in setOf("scheduled","blocked","pending","failed"))
                    repo.dao.put(old.copy(state=if(old.triggerAt<=now) "expired" else "cancelled"))
                if(wanted.none { it.sourceType==old.sourceType && it.sourceId==old.sourceId && it.instanceKey==old.instanceKey }) notifications.cancel(old.id.toInt())
            }
        }
        future.forEach { next ->
            val old=s.reminders.find { it.sourceType==next.sourceType && it.sourceId==next.sourceId && it.instanceKey==next.instanceKey }
            val id=repo.dao.put(next.copy(id=old?.id ?: 0,state=if(can) "scheduled" else "blocked"))
            if(can) try { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.triggerAt,intent(id)) }
            catch(e:Exception) { repo.dao.put(next.copy(id=id,state="failed")) }
        }
        val refill=PendingIntent.getBroadcast(context,-99,Intent(context,PlanAlarmReceiver::class.java).setAction("com.example.dailylife.PLAN_REFILL"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(refill)
        if(s.rules.any {it.stoppedFrom==null} || s.habits.any {it.status=="active" && it.deletedAt==null && it.reminderTime!=null} || tools.entries.any {it.deletedAt==null && it.field("reminderOn")=="true"}) {
            try {alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,now+12*60*60*1000L,refill)}
            catch(e:Exception) {android.util.Log.w("PlanAlarms","Periodic refill unavailable",e)}
        }
    }
    suspend fun deliver(id:Long) {
        reconcile()
        repo.writes.withLock {
            val job=repo.dao.reminders().find { it.id==id } ?: return@withLock
            val now=System.currentTimeMillis()
            // Reconciliation marks past alarms expired; verify eligibility from current source again.
            val s=repo.load()
            val valid=when(job.sourceType) {
                "task" -> s.taskRows().any { row->
                    val date=row.due ?: row.date;val t=row.task
                    val relative=if(date!=null && t.reminderMinutes!=null) LocalDate.parse(date).atTime(t.dueTime?.let(LocalTime::parse) ?: LocalTime.of(9,0))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()-t.reminderMinutes*60000L else null
                    t.id==job.sourceId && row.status in setOf("todo","doing") &&
                    (if(job.instanceKey=="single") row.occurrence==null && t.reminderAt==job.triggerAt
                    else (row.occurrence?.originalKey ?: "relative")==job.instanceKey && relative==job.triggerAt)
                }
                "event" -> s.events.any { it.id==job.sourceId && it.deletedAt==null && it.archivedAt==null && it.status=="scheduled" } &&
                    s.events.first {it.id==job.sourceId}.let {e->
                        val o=s.occurrences.firstOrNull {it.entityId==e.id && it.originalKey==job.instanceKey && it.status=="scheduled"}
                        val date=if(job.instanceKey=="single") e.startDate else o?.scheduledDate
                        date!=null && e.reminderMinutes!=null && LocalDate.parse(date).atTime(if(e.allDay) LocalTime.of(9,0) else LocalTime.parse(e.startTime))
                            .atZone(ZoneId.of(e.zoneId)).toInstant().toEpochMilli()-e.reminderMinutes*60000L==job.triggerAt
                    }
                "habit" -> s.habits.any {h->h.id==job.sourceId && h.deletedAt==null && h.status=="active" && h.reminderTime!=null &&
                    s.habitScheduled(h,job.instanceKey) && s.habitRule(h.id,job.instanceKey)?.let {s.habitTotal(h.id,job.instanceKey)<it.targetMilli}==true &&
                    LocalDate.parse(job.instanceKey).atTime(LocalTime.parse(h.reminderTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()==job.triggerAt}
                "tool"->repo.db.tools().let {d->ToolsSnapshot(entries=d.entries(),logs=d.logs(),periods=d.periods())}.reminders().any {it.sourceId==job.sourceId && it.instanceKey==job.instanceKey && it.triggerAt==job.triggerAt}
                else->false
            }
            if(!valid || job.postedAt!=null || !enabled() || now-job.triggerAt !in 0..900000) return@withLock
            val open=PendingIntent.getActivity(context,id.toInt(),Intent(context,MainActivity::class.java)
                .setAction(if(job.sourceType=="tool") "com.example.dailylife.OPEN_TOOL" else "com.example.dailylife.OPEN_PLAN").putExtra("planKind",job.sourceType).putExtra("planId",job.sourceId).putExtra("toolId",job.sourceId)
                .putExtra("jobId",job.id)
                .putExtra("instanceId",s.occurrences.firstOrNull {it.entityId==job.sourceId && it.originalKey==job.instanceKey}?.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notifications.notify(id.toInt(),NotificationCompat.Builder(context,"plans").setSmallIcon(com.example.dailylife.R.drawable.ic_daily)
                .setContentTitle(job.title).setContentText("打开日常查看计划").setContentIntent(open).setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
            repo.dao.put(job.copy(state="posted",postedAt=now))
        }
    }
}
class PlanAlarmReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {
                val app=context.applicationContext as DailyLifeApplication
                app.plans.initialize()
                app.tools.initialize()
                if(intent.action=="com.example.dailylife.PLAN_ALARM") app.planAlarms.deliver(intent.getLongExtra("job",-1))
                else app.planAlarms.reconcile()
            } catch(e:Exception) {android.util.Log.w("PlanAlarms","Reminder reconciliation failed",e)}
            finally { pending.finish() }
        }
    }
}
