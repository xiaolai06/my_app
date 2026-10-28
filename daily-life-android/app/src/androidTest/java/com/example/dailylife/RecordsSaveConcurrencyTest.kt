package com.example.dailylife

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.feature.records.RecordsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class RecordsSaveConcurrencyTest {
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test fun editsAndRepeatedSaveWhileCommitWaitsCannotReplaceAcceptedInput()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
        val store=ViewModelStore()
        lateinit var vm:RecordsViewModel
        main {vm=RecordsViewModel(app,SavedStateHandle());store.put("save-regression",vm)}
        var recordId:String?=null
        try {
            val ready=withTimeout(15000) {vm.ui.first {it.ready}}
            val type=ready.snapshot.types.first {it.kind=="note"&&it.subtype=="plain"}
            main {vm.begin(type.id)}
            val editor=withTimeout(10000) {vm.ui.first {it.editor!=null&&!it.busy}}.editor!!
            recordId=editor.data.recordId
            val accepted=editor.data.copy(body="点击保存时的完整内容")
            main {vm.edit(accepted)}
            app.records.writes.lock()
            try {
                main {vm.save();vm.edit(accepted.copy(body="保存过程中不应被接收的内容"));vm.save()}
                assertTrue(vm.ui.value.busy)
                assertEquals(accepted,vm.ui.value.editor!!.data)
            } finally {app.records.writes.unlock()}
            withTimeout(10000) {vm.ui.first {it.editor==null&&!it.busy}}
            assertEquals(accepted.body,app.records.dao.record(recordId!!)!!.body)
            assertNull(app.records.dao.draft(editor.draftId))
        } finally {
            main {store.clear()}
            recordId?.let {id->
                if(app.records.dao.record(id)!=null) {app.records.mutate(setOf(id),"trash");app.records.permanentlyDelete(setOf(id))}
            }
        }
    }
}
