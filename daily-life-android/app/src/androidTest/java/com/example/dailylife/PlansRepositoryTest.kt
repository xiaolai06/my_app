package com.example.dailylife

import android.content.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.plans.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.*
import java.io.*
import java.time.*
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class PlansRepositoryTest {
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root:File
    private lateinit var db:DailyLifeDatabase
    private lateinit var repo:PlansRepository
    private lateinit var ctx:Context
    private val today get()=LocalDate.now().toString()
    private class Clock:FocusClock {
        var now=System.currentTimeMillis();var tick=1000L;var bootId=1
        override fun wall()=now;override fun elapsed()=tick;override fun boot()=bootId
        fun add(seconds:Long) {now+=seconds*1000;tick+=seconds*1000}
    }
    private lateinit var clock:Clock
    private fun contextAt(name:String)=object:ContextWrapper(base) {
        override fun getFilesDir()=File(root,name+"/files").apply {mkdirs()}
        override fun getCacheDir()=File(root,name+"/cache").apply {mkdirs()}
        override fun getNoBackupFilesDir()=File(root,name+"/private").apply {mkdirs()}
    }
    @Before fun setup()=runBlocking {
        root=File(base.cacheDir,"plans-tests-"+newId()).apply {mkdirs()}
        ctx=contextAt("primary");clock=Clock()
        db=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"primary.db").absolutePath).build()
        repo=PlansRepository(RecordsRepository(db,AttachmentStore(ctx)),clock);repo.initialize()
    }
    @After fun cleanup() {db.close();root.deleteRecursively()}
    private suspend fun save(f:PlanForm) {if(repo.dao.drafts().none {it.id==f.id}) repo.prepareDraft(f,if(f.revision>0) f.kind else null);repo.save(f)}
    private suspend fun task(name:String="任务",planned:String?=null):String {
        var f=PlanForm("task").set("title",name);if(planned!=null) f=f.set("planned",planned)
        save(f);return f.id
    }
    private suspend fun failure(block:suspend ()->Unit) {assertTrue("operation must reject invalid data",runCatching {block()}.isFailure)}
    @Test fun undatedIsNotTodayAndCompletionUndoHasOneSource()=runBlocking {
        task("无日期")
        val id=task("今天",today);save(repo.form("task",id).set("due",today))
        assertEquals(1,repo.load().todayTasks().size)
        var row=repo.load().todayTasks().single();repo.taskAction(row,"done")
        assertTrue(repo.load().todayTasks().isEmpty());assertNotNull(repo.dao.tasks().first {it.id==id}.completedAt)
        row=TaskRow(repo.dao.tasks().first {it.id==id});repo.taskAction(row,"todo")
        assertNull(repo.dao.tasks().first {it.id==id}.completedAt);assertEquals(1,repo.load().todayTasks().size)
    }
    @Test fun failedSaveAndRevisionConflictKeepDraftAndOriginal()=runBlocking {
        val id=task("原始名称")
        val f=repo.form("task",id).set("title","");repo.prepareDraft(f,"task")
        failure {repo.save(f)};assertTrue(repo.dao.drafts().any {it.id==id});assertEquals("原始名称",repo.dao.tasks().single().title)
        repo.manage("task",id,"archive")
        failure {repo.save(f.set("title","未覆盖"))}
        assertEquals("原始名称",repo.dao.tasks().single().title)
    }
    @Test fun repeatingInstancesPreserveOriginalKeyAndIndependentSubtasks()=runBlocking {
        val child=newId();val f=PlanForm("task").set("title","重复").set("planned",today).set("repeat","daily").set("count","3")
            .set("subtasks",JSONArray().put(JSONObject().put("id",child).put("title","子项")).toString())
        save(f);val before=repo.dao.occurrences();assertEquals(3,before.size)
        repo.subtask(child,before[0].id);repo.taskAction(TaskRow(repo.dao.tasks().single(),before[0]),"done",true)
        repo.instance(before[1].id,before[0].scheduledDate,null,null)
        repo.refresh();repo.refresh()
        val after=repo.dao.occurrences();assertEquals(3,after.size)
        assertEquals(before[1].originalKey,after.first {it.id==before[1].id}.originalKey)
        assertEquals("",after.first {it.id==before[2].id}.subtaskStates)
        assertEquals(2,after.count {it.scheduledDate==before[0].scheduledDate})
        repo.stop(before[0].ruleId);assertEquals("done",repo.dao.occurrences().first {it.id==before[0].id}.status)
    }
    @Test fun allDayExclusiveEndAndElapsedEventNeverCompleteTask()=runBlocking {
        val task=task();val f=PlanForm("event").set("title","全天").set("allDay","true").set("start",today).set("end",today).set("task",task)
        save(f);assertEquals(LocalDate.now().plusDays(1).toString(),repo.dao.events().single().endDateExclusive)
        val past=LocalDate.now().minusDays(1).toString()
        save(PlanForm("event").set("title","昨天会议").set("start",past).set("end",past).set("startTime","09:00").set("endTime","10:00").set("task",task))
        repo.refresh();assertEquals("todo",repo.dao.tasks().single().status)
        failure {save(PlanForm("event").set("title","倒序").set("start",today).set("end",today).set("startTime","11:00").set("endTime","10:00"))}
    }
    @Test fun habitEditReplacesOneLogAndWeeklyCountsDistinctDays()=runBlocking {
        val f=PlanForm("habit").set("title","喝水").set("tracking","quantity").set("unit","杯").set("target","2")
            .set("start",LocalDate.now().minusDays(8).toString()).set("schedule","weekly").set("weeklyDays","3")
        save(f)
        repo.habitLog(f.id,today,"1");repo.habitLog(f.id,today,"1");repo.habitLog(f.id,today,"1")
        val second=repo.dao.habitLogs()[1];repo.habitLog(f.id,today,"2","修改",second.id)
        assertEquals(4000L,repo.load().habitTotal(f.id,today));assertEquals(3,repo.dao.habitLogs().size)
        assertEquals(1,repo.load().weekDays(repo.dao.habits().single(),LocalDate.now()))
        failure {repo.habitLog(f.id,LocalDate.now().plusDays(1).toString(),"1")}
        val rulesBefore=repo.dao.habitRules()
        save(repo.form("habit",f.id).set("target","3").set("effective",LocalDate.now().plusDays(1).toString()))
        assertEquals(rulesBefore.first(),repo.load().habitRule(f.id,today))
        repo.manage("habit",f.id,"pause");failure {repo.habitLog(f.id,today,"1")}
        repo.habitLog(f.id,today,"1",id=second.id,delete=true)
        assertEquals(2,repo.dao.habitLogs().size)
    }
    @Test fun onceHabitIsIdempotentAndRestDaysDoNotBreakStreak()=runBlocking {
        val start=LocalDate.now().minusDays(6);val weekday=LocalDate.now().dayOfWeek.value.toString()
        val f=PlanForm("habit").set("title","每周").set("start",start.toString()).set("schedule","weekdays").set("weekdays",weekday)
        save(f);repo.habitLog(f.id,today,"1");repo.habitLog(f.id,today,"1")
        assertEquals(1,repo.dao.habitLogs().size);assertEquals(1,repo.load().habitStreak(repo.dao.habits().single(),LocalDate.now()))
    }
    @Test fun focusPersistsSegmentsExcludesPauseAndCapsCountdown()=runBlocking {
        val f=PlanForm("focus").set("title","阅读").set("mode","countdown").set("seconds","60")
        repo.startFocus(f);clock.add(20);repo.focusAction(f.id,"pause")
        clock.add(600);repo.focusAction(f.id,"resume");clock.add(100);repo.focusAction(f.id,"finish")
        assertEquals(60L,repo.load().focusSeconds(f.id));assertEquals("finished",repo.dao.sessions().single().state)
        assertEquals(2,repo.dao.segments().size)
        repo.focusAction(f.id,"correct","修正漏记",90)
        assertEquals(90L,repo.load().focusSeconds(f.id));assertNotNull(repo.dao.segments().first().originalSeconds)
    }
    @Test fun rebootAndWallClockDriftRequireReviewWithoutInflatingTime()=runBlocking {
        val f=PlanForm("focus").set("mode","stopwatch")
        repo.startFocus(f);clock.add(10);clock.bootId++
        assertNull(repo.openSeconds(repo.dao.sessions().single()));repo.refresh()
        assertEquals("review",repo.dao.sessions().single().state)
        repo.focusAction(f.id,"recover");clock.add(15);repo.focusAction(f.id,"early")
        assertEquals(15L,repo.load().focusSeconds(f.id))
        val second=PlanForm("focus");repo.startFocus(second);clock.now+=3600000
        assertNull(repo.openSeconds(repo.dao.sessions().first {it.id==second.id}))
    }
    @Test fun pomodoroRestExcludedAndSingleActiveConstraint()=runBlocking {
        val f=PlanForm("focus").set("mode","pomodoro").set("seconds","10").set("rounds","2")
        repo.startFocus(f);failure {repo.startFocus(PlanForm("focus"))}
        failure {repo.focusAction(f.id,"finish")}
        clock.add(10);repo.focusAction(f.id,"break");clock.add(300);repo.focusAction(f.id,"next");clock.add(10);repo.focusAction(f.id,"finish")
        assertEquals(20L,repo.load().focusSeconds(f.id));assertEquals(300L,repo.dao.segments().filter {it.phase=="break"}.sumOf {it.seconds})
    }
    @Test fun goalsDeduplicateSourcesAndRecomputeAfterUndoOrUnlink()=runBlocking {
        val id=task()
        val f=PlanForm("goal").set("title","完成目标").set("source","tasks").set("sources",id)
        save(f);repo.link(f.id,"task",id);repo.link(f.id,"task",id)
        assertEquals(1,repo.dao.goalLinks().size)
        repo.taskAction(TaskRow(repo.dao.tasks().single()),"done");repo.manage("goal",f.id,"done")
        repo.taskAction(TaskRow(repo.dao.tasks().single()),"todo")
        assertTrue(repo.load().goalReview(repo.dao.goals().single()))
        repo.link(f.id,"task",id,true);assertEquals(0L,repo.load().goalValue(repo.dao.goals().single()).second)
        failure {repo.goalLog(f.id,today,"1","不能混合来源")}
    }
    @Test fun crossMidnightFocusUsesGoalDateIntersectionAndExcludesAbandoned()=runBlocking {
        val day=LocalDate.now().minusDays(1);val zone=ZoneId.systemDefault()
        clock.now=day.atTime(23,59,30).atZone(zone).toInstant().toEpochMilli()
        val f=PlanForm("focus").set("mode","stopwatch");repo.startFocus(f);clock.add(90);repo.focusAction(f.id,"early")
        val g=PlanForm("goal").set("title","今天专注").set("source","focus").set("sources",f.id).set("target","120").set("start",today).set("end",today)
        save(g);assertEquals(60000L,repo.load().goalValue(repo.dao.goals().single()).first)
        repo.manage("focus",f.id,"trash");assertEquals(0L,repo.load().goalValue(repo.dao.goals().single()).first)
    }
    @Test fun attachmentsSharedAcrossRecordsPlansAndDraftsAreNotPrematurelyRemoved()=runBlocking {
        val f=PlanForm("task").set("title","有附件");repo.prepareDraft(f)
        val a=repo.records.files.importStream("原件".byteInputStream(),repo.records.owner(),"资料.txt","text/plain")
        repo.records.dao.putAttachment(a);repo.attachFile(f.id,a.id);repo.save(f)
        val copy=PlanForm("event").set("title","复用").set("allDay","true").set("start",today).set("end",today)
        repo.prepareDraft(copy);repo.attachFile(copy.id,a.id);repo.save(copy)
        repo.manage("task",f.id,"trash");repo.permanentlyDelete("task",f.id)
        assertTrue(repo.records.files.file(a).exists())
        repo.manage("event",copy.id,"trash");repo.permanentlyDelete("event",copy.id)
        assertFalse(repo.records.files.file(a).exists())
    }
    @Test fun planArchiveRoundTripAndCorruptRestoreKeepExistingRecords()=runBlocking {
        val t=task("备份测试",today)
        val goal=PlanForm("goal").set("title","关联").set("source","tasks").set("sources",t);save(goal)
        val out=ByteArrayOutputStream();val backup=PlansBackup(ctx,repo);backup.export(out)
        val otherDb=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"restored.db").absolutePath).build()
        try {
            val other=PlansRepository(RecordsRepository(otherDb,AttachmentStore(contextAt("restored"))),clock);other.initialize()
            val recordDraft=other.records.newDraft(other.records.dao.types().first {it.kind=="note"}.id)
            val recordId=other.records.commit(recordDraft.id,com.example.dailylife.feature.records.EditorData.fromJson(recordDraft.payload).copy(body="保留本机记录"))
            val restore=PlansBackup(contextAt("restored"),other);val preview=restore.prepare(out.toByteArray().inputStream())
            assertTrue(restore.restore(preview)>0);assertEquals(0,restore.restore(preview));restore.discard(preview)
            assertEquals("备份测试",other.dao.tasks().single().title);assertEquals(1,other.dao.goalLinks().size)
            assertEquals("保留本机记录",other.records.dao.record(recordId)!!.body)
            val corrupt=ByteArrayOutputStream();ZipOutputStream(corrupt).use {z->z.putNextEntry(ZipEntry("../unsafe"));z.write("x".toByteArray());z.closeEntry()}
            failure {restore.prepare(corrupt.toByteArray().inputStream())}
            assertEquals(1,other.dao.tasks().size)
        } finally {otherDb.close()}
    }
    @Test fun permanentDeleteProtectsGoalAndTaskDependencies()=runBlocking {
        val id=task();save(PlanForm("goal").set("title","依赖").set("source","tasks").set("sources",id))
        repo.manage("task",id,"trash");failure {repo.permanentlyDelete("task",id)}
        assertEquals(1,repo.dao.tasks().size)
    }
    @Test fun planArchiveIncludesOriginalAndRejectsTamperingBeforeWriting()=runBlocking {
        val f=PlanForm("task").set("title","保留附件");repo.prepareDraft(f)
        val a=repo.records.files.importStream("计划附件原件".byteInputStream(),repo.records.owner(),"原件.txt","text/plain")
        repo.records.dao.putAttachment(a);repo.attachFile(f.id,a.id);repo.save(f)
        val backup=PlansBackup(ctx,repo);val out=ByteArrayOutputStream();backup.export(out)
        val imported=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"file-copy.db").absolutePath).build()
        try {
            val other=PlansRepository(RecordsRepository(imported,AttachmentStore(contextAt("file-copy"))),clock);other.initialize()
            val target=PlansBackup(contextAt("file-copy"),other);val preview=target.prepare(out.toByteArray().inputStream())
            target.restore(preview);target.discard(preview)
            val restored=other.records.dao.attachment(a.id)!!
            assertEquals("计划附件原件",other.records.files.file(restored).readText())
            val tampered=ByteArrayOutputStream()
            ZipOutputStream(tampered).use {z->ZipInputStream(out.toByteArray().inputStream()).use {source->
                while(true) {val e=source.nextEntry ?: break;val bytes=source.readBytes();z.putNextEntry(ZipEntry(e.name))
                    z.write(if(e.name.startsWith("attachments/")) "被改写".toByteArray() else bytes);z.closeEntry();source.closeEntry()}
            }}
            failure {target.prepare(tampered.toByteArray().inputStream())}
            assertEquals("计划附件原件",other.records.files.file(restored).readText());assertEquals(1,other.dao.tasks().size)
        } finally {imported.close()}
    }
    @Test fun replayingFocusStartDoesNotOverwriteCommittedHistory()=runBlocking {
        val f=PlanForm("focus").set("title","一次提交").set("mode","stopwatch")
        repo.startFocus(f);clock.add(20);repo.focusAction(f.id,"early");repo.startFocus(f)
        assertEquals(1,repo.dao.sessions().size);assertEquals("early",repo.dao.sessions().single().state)
        assertEquals(20L,repo.load().focusSeconds(f.id))
    }
    @Test fun movingOnceLogToOccupiedDayRejectsDuplicateAndKeepsOriginal()=runBlocking {
        val yesterday=LocalDate.now().minusDays(1).toString()
        val f=PlanForm("habit").set("title","一天一次").set("start",yesterday);save(f)
        repo.habitLog(f.id,yesterday,"1");repo.habitLog(f.id,today,"1")
        val old=repo.dao.habitLogs().first {it.logDate==yesterday}
        failure {repo.habitLog(f.id,today,"1",id=old.id)}
        assertEquals(2,repo.dao.habitLogs().size);assertEquals(1000L,repo.load().habitTotal(f.id,today))
    }
    @Test fun seriesRenameRetainsCompletedTitleAndUpdatesPendingInstances()=runBlocking {
        val f=PlanForm("task").set("title","原系列").set("planned",today).set("repeat","daily").set("count","3");save(f)
        val rows=repo.load().taskRows();repo.taskAction(rows[0],"done")
        save(repo.form("task",f.id).set("title","新系列"))
        val after=repo.load().taskRows()
        assertEquals("原系列",after.first {it.key==rows[0].key}.title)
        assertEquals("新系列",after.first {it.key==rows[1].key}.title)
    }
    @Test fun manualDurationGoalKeepsSecondsAndReplacesProgressLog()=runBlocking {
        val f=PlanForm("goal").set("title","阅读时长").set("source","manual").set("goalType","duration").set("target","1200");save(f)
        repo.goalLog(f.id,today,"600","十分钟")
        val log=repo.dao.progress().single();repo.goalLog(f.id,today,"900","修正为十五分钟",log.id)
        val goal=repo.dao.goals().single()
        assertEquals("duration",goal.goalType);assertEquals("秒",goal.unit)
        assertEquals(900000L,repo.load().goalValue(goal).first)
        assertEquals(1,repo.dao.progress().size);assertEquals("duration",repo.form("goal",f.id).text("goalType"))
    }
}
