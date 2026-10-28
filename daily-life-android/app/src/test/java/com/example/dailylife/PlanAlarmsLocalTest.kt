package com.example.dailylife

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Exercises scheduling state and notifications; actual Android power-management timing still needs a device. */
@RunWith(RobolectricTestRunner::class)
class PlanAlarmsLocalTest {
    private val context=ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var db:DailyLifeDatabase
    private lateinit var repo:PlansRepository
    private lateinit var alarms:PlanAlarms
    private var now=System.currentTimeMillis()
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(context,DailyLifeDatabase::class.java).build()
        repo=PlansRepository(RecordsRepository(db,AttachmentStore(context)),AndroidFocusClock(context))
        repo.initialize();alarms=PlanAlarms(context,repo) {now}
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }
    @After fun close() {db.close()}
    private suspend fun schedule():PlanReminder {
        val task=Task(ownerId=repo.records.owner(),title="有延迟的系统提醒",reminderAt=now+600000)
        repo.dao.put(task);alarms.reconcile()
        return repo.dao.reminders().single {it.sourceId==task.id}
    }
    @Test fun snoozeSurvivesRecreationIsIdempotentAndCompletedSourceCannotNotify()=runBlocking {
        val job=schedule();now=job.triggerAt+1000;alarms.deliver(job.id)
        alarms.snooze(job.id,15)
        val deferred=repo.dao.reminders().first {it.id==job.id}
        assertEquals("snoozed",deferred.state);assertNull(deferred.postedAt)
        alarms.snooze(job.id,60);assertEquals(deferred.deferredUntil,repo.dao.reminders().first {it.id==job.id}.deferredUntil)
        alarms=PlanAlarms(context,repo) {now};alarms.reconcile()
        assertEquals(deferred.deferredUntil,repo.dao.reminders().first {it.id==job.id}.deferredUntil)
        now=deferred.deferredUntil!!-1;alarms.deliver(job.id);assertNull(repo.dao.reminders().first {it.id==job.id}.postedAt)
        now=deferred.deferredUntil+1;alarms.deliver(job.id);assertEquals(now,repo.dao.reminders().first {it.id==job.id}.postedAt)
        alarms.snooze(job.id,15)
        repo.dao.put(repo.dao.tasks().first {it.id==job.sourceId}.copy(status="done"));alarms.reconcile()
        now+=16*60000;alarms.deliver(job.id)
        assertEquals("cancelled",repo.dao.reminders().first {it.id==job.id}.state)
    }
    @Test fun viewingFutureReminderNeverSilencesItAndPrivateNotificationsHaveActions()=runBlocking {
        val job=schedule();alarms.acknowledge(job.id)
        assertNull(repo.dao.reminders().first {it.id==job.id}.readAt)
        now=job.triggerAt+1000;alarms.deliver(job.id)
        val manager=context.getSystemService(NotificationManager::class.java)
        val notice=shadowOf(manager).allNotifications.single()
        assertEquals(2,notice.actions.size);assertEquals(android.app.Notification.VISIBILITY_PRIVATE,notice.visibility)
        assertEquals("My Life 提醒",notice.publicVersion.extras.getString(android.app.Notification.EXTRA_TITLE))
        alarms.acknowledge(job.id);assertNotNull(repo.dao.reminders().first {it.id==job.id}.readAt)
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
    @Test fun focusDeadlineReschedulesAfterPauseAndSystemChronometerDoesNotNeedPolling()=runBlocking {
        val start=now;var elapsed=0L
        val clock=object:FocusClock {override fun wall()=start+elapsed;override fun elapsed()=elapsed;override fun boot()=1}
        repo=PlansRepository(repo.records,clock);alarms=PlanAlarms(context,repo) {start+elapsed}
        val f=com.example.dailylife.feature.plans.PlanForm("focus").set("mode","countdown").set("seconds","600").set("title","阅读")
        repo.startFocus(f);alarms.reconcile()
        val original=repo.dao.reminders().single {it.sourceType=="focus"}
        assertEquals(start+600000,original.triggerAt)
        val manager=context.getSystemService(NotificationManager::class.java)
        val notice=shadowOf(manager).getNotification(FocusNotifications.ID)
        assertTrue(notice.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(notice.extras.containsKey("android.requestPromotedOngoing"))
        assertEquals(listOf("暂停","收起通知"),notice.actions.map {it.title.toString()})
        elapsed=120000;repo.focusAction(f.id,"pause");alarms.reconcile()
        assertEquals("cancelled",repo.dao.reminders().first {it.id==original.id}.state)
        elapsed=300000;repo.focusAction(f.id,"resume");alarms.reconcile()
        val resumed=repo.dao.reminders().single {it.sourceType=="focus" && it.state=="scheduled"}
        assertEquals(start+780000,resumed.triggerAt)
        elapsed=781000;alarms.deliver(resumed.id)
        assertEquals("posted",repo.dao.reminders().first {it.id==resumed.id}.state)
        assertNull(shadowOf(manager).getNotification(FocusNotifications.ID))
    }
    @Test fun ordinaryFocusNotificationKeepsHidePreferenceAndRenamesItsExistingChannel()=runBlocking {
        val start=now
        repo=PlansRepository(repo.records,object:FocusClock {override fun wall()=start;override fun elapsed()=0L;override fun boot()=1})
        alarms=PlanAlarms(context,repo) {start}
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(FocusNotifications.CHANNEL,"专注实况计时",NotificationManager.IMPORTANCE_LOW))
        alarms.reconcile()
        assertEquals("专注计时",manager.getNotificationChannel(FocusNotifications.CHANNEL).name.toString())
        val f=com.example.dailylife.feature.plans.PlanForm("focus").set("mode","countdown").set("seconds","600").set("title","阅读")
        repo.startFocus(f);alarms.reconcile()
        assertNotNull(shadowOf(manager).getNotification(FocusNotifications.ID))
        FocusNotifications(context,repo).hide(f.id);alarms.reconcile()
        assertNull(shadowOf(manager).getNotification(FocusNotifications.ID))
        assertEquals("running",repo.dao.sessions().first {it.id==f.id}.state)
        PreferencesRepository(context).setFocusNotifications(true);alarms.reconcile()
        assertNotNull(shadowOf(manager).getNotification(FocusNotifications.ID))
        @Suppress("DEPRECATION")
        val requested=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse("android.permission.POST_PROMOTED_NOTIFICATIONS" in requested)
    }
    @Test fun blockedReminderChannelDoesNotDisableTheSeparateFocusChannel()=runBlocking {
        val start=now
        repo=PlansRepository(repo.records,object:FocusClock {override fun wall()=start;override fun elapsed()=0L;override fun boot()=1})
        alarms=PlanAlarms(context,repo) {start}
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(PlanAlarms.CHANNEL,"到期提醒",NotificationManager.IMPORTANCE_NONE))
        repo.startFocus(com.example.dailylife.feature.plans.PlanForm("focus").set("mode","countdown").set("seconds","600").set("title","阅读"))
        alarms.reconcile()
        assertFalse(alarms.enabled())
        assertNotNull(shadowOf(manager).getNotification(FocusNotifications.ID))
    }
    @Test fun twentyAndSixtyMinuteDelaysPostOnceAndKeepReadHistoryAfterClockChange()=runBlocking {
        for(minutes in listOf(20,60)) {
            val job=schedule();now=job.triggerAt+minutes*60000L
            alarms.deliver(job.id)
            val posted=repo.dao.reminders().first {it.id==job.id}
            assertEquals("posted",posted.state);assertEquals(now,posted.postedAt)
            repo.readReminder(job.id);val read=repo.dao.reminders().first {it.id==job.id}.readAt
            now=job.triggerAt-1000;alarms.reconcile();now=job.triggerAt+minutes*60000L;alarms.deliver(job.id)
            val again=repo.dao.reminders().first {it.id==job.id}
            assertEquals(posted.postedAt,again.postedAt);assertEquals(read,again.readAt)
        }
        val notifications=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(2,shadowOf(notifications).allNotifications.size)
    }
    @Test fun obsoleteSourceAndExpiredCallbackCannotNotify()=runBlocking {
        val cancelled=schedule()
        repo.dao.put(repo.dao.tasks().first {it.id==cancelled.sourceId}.copy(status="done"))
        now=cancelled.triggerAt+1200000;alarms.deliver(cancelled.id)
        assertEquals("cancelled",repo.dao.reminders().first {it.id==cancelled.id}.state)
        val stale=schedule();now=stale.triggerAt+25*60*60*1000L;alarms.deliver(stale.id)
        assertEquals("expired",repo.dao.reminders().first {it.id==stale.id}.state)
        assertTrue(repo.dao.reminders().all {it.postedAt==null})
    }
    @Test fun deniedPermissionDoesNotPretendTheReminderWasDelivered()=runBlocking {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val job=schedule();assertEquals("blocked",job.state)
        now=job.triggerAt+3600000;alarms.deliver(job.id)
        val blocked=repo.dao.reminders().first {it.id==job.id}
        assertEquals("blocked",blocked.state);assertNull(blocked.postedAt);assertNull(blocked.readAt)
        assertNotNull(repo.dao.tasks().first {it.id==job.sourceId}.reminderAt)
    }
}
