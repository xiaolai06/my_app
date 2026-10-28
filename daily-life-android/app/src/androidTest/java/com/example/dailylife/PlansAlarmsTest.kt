package com.example.dailylife
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.feature.plans.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
/** Uses the real app snapshot. An explicit test-only argument may grant notification permission on the disposable AVD. */
class PlansAlarmsTest {
    @Test fun delayedAlarmsRespectPermissionAndNeverEraseDeliveryHistory()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
        if(InstrumentationRegistry.getArguments().getString("expectedNotifications")=="true" && android.os.Build.VERSION.SDK_INT>=33)
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        app.plans.initialize();app.tools.initialize()
        var now=System.currentTimeMillis()
        val alarms=com.example.dailylife.data.repository.PlanAlarms(app,app.plans) {now}
        for(delayMinutes in listOf(20,60)) {
            val form=PlanForm("task").set("title","延迟提醒回归 $delayMinutes")
            try {
                app.plans.prepareDraft(form);app.plans.save(form)
                val trigger=now+600000
                app.plans.dao.put(app.plans.dao.tasks().first {it.id==form.id}.copy(reminderAt=trigger,reminderMinutes=null))
                alarms.reconcile()
                val job=app.plans.dao.reminders().single {it.sourceId==form.id}
                now=trigger+delayMinutes*60000L
                alarms.deliver(job.id)
                val delivered=app.plans.dao.reminders().first {it.id==job.id}
                assertEquals(if(alarms.enabled()) "posted" else "blocked",delivered.state)
                assertEquals(if(alarms.enabled()) now else null,delivered.postedAt)
                alarms.deliver(job.id)
                assertEquals(delivered.postedAt,app.plans.dao.reminders().first {it.id==job.id}.postedAt)
                if(delivered.postedAt!=null) {
                    app.plans.readReminder(job.id)
                    val read=app.plans.dao.reminders().first {it.id==job.id}.readAt
                    now=trigger-1000;alarms.reconcile()
                    val preserved=app.plans.dao.reminders().first {it.id==job.id}
                    assertEquals(delivered.postedAt,preserved.postedAt);assertEquals(read,preserved.readAt)
                }
                app.plans.taskAction(TaskRow(app.plans.dao.tasks().first {it.id==form.id}),"done")
                alarms.reconcile()
            } finally {
                app.plans.discard(form.id)
                if(app.plans.dao.tasks().any {it.id==form.id}) {app.plans.manage("task",form.id,"trash");app.plans.permanentlyDelete("task",form.id)}
                alarms.reconcile()
                app.plans.db.openHelper.writableDatabase.execSQL("DELETE FROM plan_reminders WHERE sourceId=?",arrayOf(form.id))
            }
        }
    }
    @Test fun toolReminderUsesCurrentPermissionAndSourceCancellation()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
        if(InstrumentationRegistry.getArguments().getString("expectedNotifications")=="true" && android.os.Build.VERSION.SDK_INT>=33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        app.plans.initialize();app.tools.initialize()
        val f=com.example.dailylife.feature.tools.ToolForm("anniversary").set("title","生活提醒临时验证").set("date",LocalDate.now().plusDays(1).toString()).set("reminderOn","true").set("reminderTime","09:00").set("reminderDays","0")
        try {
            app.tools.prepare(f);app.tools.save(f);app.planAlarms.reconcile()
            val job=app.plans.dao.reminders().single {it.sourceType=="tool" && it.sourceId==f.id}
            assertEquals(if(app.planAlarms.enabled()) "scheduled" else "blocked",job.state)
            assertNull(job.postedAt);assertNull(job.readAt)
            app.tools.manage(f.id,"trash");app.planAlarms.reconcile()
            assertEquals("cancelled",app.plans.dao.reminders().first {it.id==job.id}.state)
        } finally {
            app.tools.discard(f.id)
            if(app.tools.load().entries.any {it.id==f.id}) {app.tools.manage(f.id,"trash");app.tools.deleteForever(f.id)}
            app.planAlarms.reconcile()
            app.plans.db.openHelper.writableDatabase.execSQL("DELETE FROM plan_reminders WHERE sourceType='tool' AND sourceId=?",arrayOf(f.id))
        }
    }
    @Test fun reminderFollowsCurrentPermissionAndCompletionCancelsOldJob()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
        val expected=InstrumentationRegistry.getArguments().getString("expectedNotifications")
        if(expected=="true" && android.os.Build.VERSION.SDK_INT>=33) {
            // Gradle reinstalls the test app, so grant only after that installation has completed.
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val repo=app.plans;repo.initialize()
        expected?.let {
            assertEquals("The device notification capability must match the requested test branch",it.toBooleanStrict(),app.planAlarms.enabled())
        }
        val f=PlanForm("task").set("title","通知校验临时事项").set("planned",LocalDate.now().plusDays(1).toString()).set("minutes","60")
        try {
            repo.prepareDraft(f);repo.save(f);app.planAlarms.reconcile()
            val job=repo.dao.reminders().single {it.sourceId==f.id}
            assertEquals(if(app.planAlarms.enabled()) "scheduled" else "blocked",job.state)
            assertNull(job.postedAt);assertNull(job.readAt)
            assertEquals(60,repo.dao.tasks().first {it.id==f.id}.reminderMinutes)
            repo.taskAction(TaskRow(repo.dao.tasks().first {it.id==f.id}),"done");app.planAlarms.reconcile()
            assertEquals("cancelled",repo.dao.reminders().first {it.id==job.id}.state)
            assertEquals(60,repo.dao.tasks().first {it.id==f.id}.reminderMinutes)
        } finally {
            repo.discard(f.id)
            if(repo.dao.tasks().any {it.id==f.id}) {repo.manage("task",f.id,"trash");repo.permanentlyDelete("task",f.id)}
            app.planAlarms.reconcile()
            repo.db.openHelper.writableDatabase.execSQL("DELETE FROM plan_reminders WHERE sourceId=?",arrayOf(f.id))
        }
    }
}
