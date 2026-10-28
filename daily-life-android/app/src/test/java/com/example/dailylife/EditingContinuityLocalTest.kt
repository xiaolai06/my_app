package com.example.dailylife

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.feature.records.EditorData
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.plans.PlanForm
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.ledger.LedgerForm
import com.example.dailylife.feature.ledger.LedgerViewModel
import com.example.dailylife.feature.tools.ToolForm
import com.example.dailylife.feature.tools.ToolsViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The screen can close only after the last keystroke is in Room, without committing business data. */
@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class)
class EditingContinuityLocalTest {
    private val app=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val store=ViewModelStore()
    private fun records(state:SavedStateHandle=SavedStateHandle())=RecordsViewModel(app,state).also {store.put("records",it)}
    private fun plans(state:SavedStateHandle=SavedStateHandle())=PlansViewModel(app,state).also {store.put("plans",it)}
    private fun ledger(state:SavedStateHandle=SavedStateHandle())=LedgerViewModel(app,state).also {store.put("ledger",it)}
    private fun tools(state:SavedStateHandle=SavedStateHandle())=ToolsViewModel(app,state).also {store.put("tools",it)}
    private fun await(condition:()->Boolean) {
        val deadline=System.nanoTime()+20_000_000_000L
        while(!condition() && System.nanoTime()<deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Editing did not reach the expected persisted state",condition())
    }
    @Before fun initialize()=runBlocking {
        app.records.initialize();app.plans.initialize();app.ledger.initialize();app.tools.initialize()
    }
    @After fun close() {store.clear();app.database.close();app.backgroundScope.cancel()}

    @Test fun recordsReuseOneDraftAndReloadLatestPayloadWhenAnOldRecoveryRowIsClicked()=runBlocking {
        val vm=records();await {vm.ui.value.ready}
        val type=vm.ui.value.snapshot.types.first {it.kind=="note"&&it.subtype=="plain"}.id
        vm.begin(type);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val initial=vm.ui.value.editor!!
        vm.edit(initial.data.copy(body="第一次退出前的最后一句"));vm.leaveEditor()
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        val oldRow=app.records.dao.draft(initial.draftId)!!
        assertTrue(app.records.dao.allRecords().isEmpty())
        repeat(3) {index->
            vm.resume(oldRow);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
            if(index>0) assertEquals("继续填写 $index",vm.ui.value.editor!!.data.body)
            vm.edit(vm.ui.value.editor!!.data.copy(body="继续填写 ${index+1}"));vm.leaveEditor();vm.leaveEditor()
            await {vm.ui.value.editor==null&&!vm.ui.value.busy}
            assertEquals(listOf(initial.draftId),app.records.dao.drafts().map {it.id})
        }
        vm.resume(oldRow);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        assertEquals("继续填写 3",vm.ui.value.editor!!.data.body)
        vm.save();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertTrue(app.records.dao.drafts().isEmpty())
        val saved=app.records.dao.record(initial.data.recordId)!!
        vm.begin(type,target=saved.record.id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val editId=vm.ui.value.editor!!.draftId
        vm.edit(vm.ui.value.editor!!.data.copy(body="原记录待补充内容"));vm.leaveEditor()
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        val simultaneous=coroutineScope {(1..4).map {async(Dispatchers.Default) {app.records.newDraft(type,target=saved.record.id)}}.awaitAll()}
        assertEquals(setOf(editId),simultaneous.map {it.id}.toSet())
        repeat(2) {
            vm.begin(type,target=saved.record.id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
            assertEquals(editId,vm.ui.value.editor!!.draftId)
            assertEquals(saved.record.revision,vm.ui.value.editor!!.data.baseRevision)
            assertEquals("原记录待补充内容",vm.ui.value.editor!!.data.body)
            vm.leaveEditor();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        }
        assertEquals(1,app.records.dao.drafts().size)
        assertEquals("继续填写 3",app.records.dao.record(saved.record.id)!!.body)
        vm.begin(type,target=saved.record.id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        vm.save();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals("原记录待补充内容",app.records.dao.record(saved.record.id)!!.body)
        assertTrue(app.records.dao.drafts().isEmpty())
    }

    @Test fun plansSurviveViewModelRestartAndOnlyActualSaveCreatesATask()=runBlocking {
        var vm=plans();await {vm.ui.value.ready}
        vm.begin("task");await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val id=vm.ui.value.editor!!.id
        vm.edit(vm.ui.value.editor!!.set("title","重启后接着写"));vm.leaveEditor()
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertTrue(app.plans.load().tasks.isEmpty())
        vm=plans();await {vm.ui.value.ready}
        assertNull(vm.ui.value.editor)
        repeat(3) {
            vm.begin("task",id=id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
            assertEquals(id,vm.ui.value.editor!!.id);assertEquals("重启后接着写",vm.ui.value.editor!!.text("title"))
            vm.leaveEditor();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
            assertEquals(1,app.plans.dao.drafts().size)
        }
        vm=plans(SavedStateHandle(mapOf("planDraft" to id)));await {vm.ui.value.ready&&vm.ui.value.editor!=null}
        vm.save();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals(id,app.plans.load().tasks.single().id);assertTrue(app.plans.dao.drafts().isEmpty())
        vm.begin("task",id=id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        vm.edit(vm.ui.value.editor!!.set("title","未正式保存的更名"));vm.leaveEditor()
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        vm.begin("task",id=id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        assertEquals("未正式保存的更名",vm.ui.value.editor!!.text("title"))
        assertEquals("重启后接着写",app.plans.load().tasks.single().title)
        vm.save();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals("未正式保存的更名",app.plans.load().tasks.single().title)
        assertTrue(app.plans.dao.drafts().isEmpty())
    }

    @Test fun ledgerRepeatedLeaveNeverChangesBalanceAndSavedCorrectionRemovesItsDraft()=runBlocking {
        app.ledger.saveAccount("暂存验证账户","cash","100",LocalDate.now().toString())
        val account=app.ledger.load().accounts.single().id
        var vm=ledger();await {vm.ui.value.ready}
        vm.begin();await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val id=vm.ui.value.editor!!.id
        vm.edit(vm.ui.value.editor!!.set("amount","12.34").set("account",account).set("subject","只在保存后记账"))
        vm.leaveEditor();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        repeat(3) {
            vm.begin(id=id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
            assertEquals(id,vm.ui.value.editor!!.id)
            vm.leaveEditor();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
            assertEquals(1,app.ledger.load().drafts.size)
            assertTrue(app.ledger.load().transactions.isEmpty());assertEquals(10000L,app.ledger.load().balance(account))
        }
        vm=ledger();await {vm.ui.value.ready};assertNull(vm.ui.value.editor)
        vm.resume(app.ledger.load().drafts.single());await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        vm.save();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals(8766L,app.ledger.load().balance(account));assertTrue(app.ledger.load().drafts.isEmpty())
        vm.begin(id=id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        vm.edit(vm.ui.value.editor!!.set("amount","20"));vm.leaveEditor()
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals(8766L,app.ledger.load().balance(account))
        vm=ledger(SavedStateHandle(mapOf("ledgerDraft" to id)));await {vm.ui.value.ready&&vm.ui.value.editor!=null}
        assertEquals(1L,vm.ui.value.editor!!.revision);assertEquals("20",vm.ui.value.editor!!.text("amount"))
        vm.save();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals(8000L,app.ledger.load().balance(account))
        assertEquals(1,app.ledger.load().transactions.size);assertTrue(app.ledger.load().drafts.isEmpty())
    }

    @Test fun toolAttachmentEditsSurviveLeavingWithoutReseedingRemovedOriginals()=runBlocking {
        var vm=tools();await {vm.ui.value.ready}
        vm.begin("item");await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val id=vm.ui.value.editor!!.id
        val original=app.records.files.importStream("原附件".byteInputStream(),app.records.owner(),"original.txt","text/plain")
        app.records.dao.putAttachment(original);app.tools.attach(id,original.id)
        vm.edit(vm.ui.value.editor!!.set("title","需要补完的物品"));vm.save()
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        vm.begin("item",id=id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        app.tools.detach(id,original.id)
        vm.edit(vm.ui.value.editor!!.set("note","待补充说明"));vm.leaveEditor()
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertTrue(app.records.files.file(original).exists())
        vm.begin("item",id=id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        assertTrue(app.records.dao.linksFor("tool_draft",id).isEmpty())
        assertEquals("待补充说明",vm.ui.value.editor!!.text("note"))
        val replacement=app.records.files.importStream("新附件".byteInputStream(),app.records.owner(),"replacement.txt","text/plain")
        app.records.dao.putAttachment(replacement);app.tools.attach(id,replacement.id)
        vm.leaveEditor();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals(listOf(original.id),app.records.dao.linksFor("tool",id).map {it.attachmentId})
        assertTrue(app.records.files.file(replacement).exists())
        vm=tools(SavedStateHandle(mapOf("toolDraft" to id)));await {vm.ui.value.ready&&vm.ui.value.editor!=null}
        assertEquals(listOf(replacement.id),app.records.dao.linksFor("tool_draft",id).map {it.attachmentId})
        vm.save();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertTrue(app.tools.dao.drafts().isEmpty());assertEquals(1,app.tools.load().entries.size)
        assertEquals(listOf(replacement.id),app.records.dao.linksFor("tool",id).map {it.attachmentId})
        assertFalse(app.records.files.file(original).exists());assertTrue(app.records.files.file(replacement).exists())
    }

    @Test fun everyDomainKeepsTheOriginalRevisionAndRefusesToOverwriteAChangedRecord()=runBlocking {
        val r=records();val p=plans();val l=ledger();val t=tools()
        await {r.ui.value.ready&&p.ui.value.ready&&l.ui.value.ready&&t.ui.value.ready}
        val type=r.ui.value.snapshot.types.first {it.kind=="note"&&it.subtype=="plain"}.id
        val rd=app.records.newDraft(type);val record=EditorData.fromJson(rd.payload).copy(body="正式记录")
        app.records.commit(rd.id,record)
        val task=PlanForm("task").set("title","正式任务");app.plans.save(task)
        val money=LedgerForm("expense").set("ledger",app.ledger.load().defaultLedger()!!.id).set("date",LocalDate.now().toString()).set("amount","10");app.ledger.save(money)
        val item=ToolForm("item").set("title","正式物品");app.tools.save(item)
        r.begin(type,target=record.recordId);p.begin("task",id=task.id);l.begin(id=money.id);t.begin("item",id=item.id)
        await {r.ui.value.editor!=null&&p.ui.value.editor!=null&&l.ui.value.editor!=null&&t.ui.value.editor!=null&&!r.ui.value.busy&&!p.ui.value.busy&&!l.ui.value.busy&&!t.ui.value.busy}
        r.edit(r.ui.value.editor!!.data.copy(body="记录未完成更正"));p.edit(p.ui.value.editor!!.set("title","任务未完成更正"))
        l.edit(l.ui.value.editor!!.set("amount","25"));t.edit(t.ui.value.editor!!.set("title","物品未完成更正"))
        r.leaveEditor();p.leaveEditor();l.leaveEditor();t.leaveEditor()
        await {r.ui.value.editor==null&&p.ui.value.editor==null&&l.ui.value.editor==null&&t.ui.value.editor==null&&!r.ui.value.busy&&!p.ui.value.busy&&!l.ui.value.busy&&!t.ui.value.busy}
        // Independent business changes advance revisions without discarding an unfinished edit.
        app.records.mutate(setOf(record.recordId),"pin")
        app.plans.manage("task",task.id,"archive")
        val current=app.ledger.load().transactions.single();app.ledger.dao.put(current.copy(subject="另一处已更正",revision=current.revision+1))
        app.tools.manage(item.id,"pin")
        r.begin(type,target=record.recordId);p.begin("task",id=task.id);l.begin(id=money.id);t.begin("item",id=item.id)
        await {r.ui.value.editor!=null&&p.ui.value.editor!=null&&l.ui.value.editor!=null&&t.ui.value.editor!=null&&!r.ui.value.busy&&!p.ui.value.busy&&!l.ui.value.busy&&!t.ui.value.busy}
        assertEquals(1L,r.ui.value.editor!!.data.baseRevision);assertEquals(1L,p.ui.value.editor!!.revision)
        assertEquals(1L,l.ui.value.editor!!.revision);assertEquals(1L,t.ui.value.editor!!.revision)
        r.save();p.save();l.save();t.save()
        await {!r.ui.value.busy&&!p.ui.value.busy&&!l.ui.value.busy&&!t.ui.value.busy&&r.ui.value.editorError!=null&&p.ui.value.error!=null&&l.ui.value.error!=null&&t.ui.value.error!=null}
        assertNotNull(r.ui.value.editor);assertNotNull(p.ui.value.editor);assertNotNull(l.ui.value.editor);assertNotNull(t.ui.value.editor)
        assertEquals("正式记录",app.records.dao.record(record.recordId)!!.body);assertEquals("正式任务",app.plans.load().tasks.single().title)
        assertEquals(1000L,app.ledger.load().transactions.single().amountMinor);assertEquals("正式物品",app.tools.load().entries.single().title)
        assertEquals(1,app.records.dao.drafts().size);assertEquals(1,app.plans.dao.drafts().size)
        assertEquals(1,app.ledger.load().drafts.size);assertEquals(1,app.tools.dao.drafts().size)
    }

    @Test fun busyLeaveRejectsRepeatedActionsAndFailedFlushKeepsTheEditorOpen()=runBlocking {
        val vm=tools();await {vm.ui.value.ready}
        vm.begin("item");await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val initial=vm.ui.value.editor!!.set("title","等待暂存的内容")
        vm.edit(initial)
        app.records.writes.lock()
        try {
            vm.leaveEditor();shadowOf(Looper.getMainLooper()).idle()
            assertTrue(vm.ui.value.busy)
            vm.edit(initial.set("title","忙碌期间不能覆盖"));vm.leaveEditor();vm.begin("item")
            assertEquals(initial,vm.ui.value.editor)
        } finally {app.records.writes.unlock()}
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals(1,app.tools.dao.drafts().size)
        vm.resume(app.tools.dao.drafts().single());await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val invalid=vm.ui.value.editor!!.set("note","长".repeat(100001))
        vm.edit(invalid);vm.leaveEditor();await {!vm.ui.value.busy&&vm.ui.value.error!=null}
        assertEquals(invalid,vm.ui.value.editor);assertFalse(vm.ui.value.saved)
        assertEquals(initial,ToolForm.parse(app.tools.dao.drafts().single().payload))
        vm.edit(initial);vm.leaveEditor();await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals(1,app.tools.dao.drafts().size);assertTrue(app.tools.load().entries.isEmpty())
        // A late autosave after an explicit delete must not recreate the removed recovery entry.
        app.tools.discard(initial.id)
        assertTrue(runCatching {app.tools.draft(initial)}.isFailure)
        assertTrue(app.tools.dao.drafts().isEmpty())
    }
}
