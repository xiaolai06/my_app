package com.example.dailylife

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.feature.records.EditorData
import com.example.dailylife.feature.records.RecordsViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class)
class RecordsViewModelLocalTest {
    private val app=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val store=ViewModelStore()
    private fun model(state:SavedStateHandle=SavedStateHandle())=
        RecordsViewModel(app,state).also {store.put("records",it)}

    private fun await(condition:()->Boolean) {
        val deadline=System.nanoTime()+15_000_000_000L
        while(!condition() && System.nanoTime()<deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("ViewModel did not reach the expected state",condition())
    }
    @After fun close() {
        store.clear();app.database.close();app.backgroundScope.cancel()
    }

    @Test fun blockedCommitRejectsLaterEditsAndRepeatedSave()=runBlocking {
        val vm=model();await {vm.ui.value.ready}
        val type=vm.ui.value.snapshot.types.first {it.kind=="note"&&it.subtype=="plain"}
        vm.begin(type.id);await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val session=vm.ui.value.editor!!
        val accepted=session.data.copy(body="提交时已经确认的完整内容")
        vm.edit(accepted)
        app.records.writes.lock()
        try {
            vm.save();shadowOf(Looper.getMainLooper()).idle()
            assertTrue(vm.ui.value.busy)
            vm.edit(accepted.copy(body="不应接收的提交期间输入"));vm.save()
            assertEquals(accepted,vm.ui.value.editor!!.data)
        } finally {app.records.writes.unlock()}
        await {vm.ui.value.editor==null&&!vm.ui.value.busy}
        assertEquals(accepted.body,app.records.dao.record(accepted.recordId)!!.body)
        assertNull(app.records.dao.draft(session.draftId))
    }

    @Test fun invalidRestoredDraftShowsLoadErrorAndCanRetryAfterRepair()=runBlocking {
        app.records.initialize()
        val type=app.records.dao.types().first {it.kind=="note"&&it.subtype=="plain"}
        val draft=app.records.newDraft(type.id)
        val data=EditorData.fromJson(draft.payload).copy(body="需要保留的草稿")
        app.records.dao.putDraft(draft.copy(payload="{invalid"))
        val vm=model(SavedStateHandle(mapOf("draft" to draft.id)))
        await {vm.ui.value.loadError!=null}
        assertFalse(vm.ui.value.ready);assertNotNull(app.records.dao.draft(draft.id))
        app.records.saveDraft(draft.id,data)
        vm.retryLoad();await {vm.ui.value.ready}
        assertNull(vm.ui.value.loadError);assertEquals(data,vm.ui.value.editor!!.data)
    }

    @Test fun failedValidationKeepsInputAndAllowsCorrection()=runBlocking {
        val vm=model();await {vm.ui.value.ready}
        vm.begin(vm.ui.value.snapshot.types.first {it.kind=="note"&&it.subtype=="plain"}.id)
        await {vm.ui.value.editor!=null&&!vm.ui.value.busy}
        val invalid=vm.ui.value.editor!!.data.copy(title="长".repeat(121),body="保留正文")
        vm.edit(invalid);vm.save();await {!vm.ui.value.busy&&vm.ui.value.editorError!=null}
        assertEquals(invalid,vm.ui.value.editor!!.data)
        vm.edit(invalid.copy(title="有效标题"));vm.save();await {!vm.ui.value.busy&&vm.ui.value.editor==null}
        assertEquals("有效标题",app.records.dao.record(invalid.recordId)!!.record.title)
    }
}
