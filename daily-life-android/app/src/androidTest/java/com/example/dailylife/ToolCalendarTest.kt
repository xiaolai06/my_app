package com.example.dailylife

import com.example.dailylife.data.local.*
import com.example.dailylife.feature.tools.ToolsSnapshot
import com.example.dailylife.ui.toolCalendarEntries
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ToolCalendarTest {
    @Test fun anniversaryCalendarRespectsLeapPolicyOriginalYearAndVisibility() {
        val anniversary=ToolEntry(ownerId="local",kind="anniversary",title="生日",payload=JSONObject().put("version",1).put("date","2000-02-29").put("repeat","yearly").put("leap","mar1").toString())
        val data=ToolsSnapshot(entries=listOf(anniversary))
        assertTrue(toolCalendarEntries(data,LocalDate.of(2027,2,28)).isEmpty())
        val row=toolCalendarEntries(data,LocalDate.of(2027,3,1)).single()
        assertEquals(anniversary.id,row.sourceId);assertEquals("27 周年",row.description)
        assertTrue(toolCalendarEntries(data,LocalDate.of(1999,3,1)).isEmpty())
        assertEquals("2000-02-29",data.nextDate(anniversary,LocalDate.of(1999,1,1)).toString())
        assertTrue(toolCalendarEntries(ToolsSnapshot(entries=listOf(anniversary.copy(payload=JSONObject(anniversary.payload).put("showCalendar","false").toString()))),LocalDate.of(2027,3,1)).isEmpty())
        assertTrue(toolCalendarEntries(ToolsSnapshot(entries=listOf(anniversary.copy(deletedAt=1))),LocalDate.of(2027,3,1)).isEmpty())
    }
    @Test fun paymentCalendarUsesDueDateAndRetainsSettledHistoryWhenPaused() {
        val today=LocalDate.now().toString()
        val source=ToolEntry(ownerId="local",kind="bill",title="房租")
        val pending=ToolPaymentPeriod(sourceId=source.id,originalKey=today,periodStart=today,periodEnd=today,dueDate=today,expectedMinor=null)
        val data=ToolsSnapshot(entries=listOf(source),periods=listOf(pending,pending.copy(id=newId(),originalKey="2020-01-01",status="superseded")))
        assertEquals(pending.id,toolCalendarEntries(data,LocalDate.now()).single().key.removePrefix("period:"))
        assertTrue(toolCalendarEntries(data.copy(entries=listOf(source.copy(state="paused"))),LocalDate.now()).isEmpty())
        assertEquals(1,toolCalendarEntries(data.copy(entries=listOf(source.copy(state="paused")),periods=listOf(pending.copy(status="paid"))),LocalDate.now()).size)
        assertTrue(toolCalendarEntries(data.copy(entries=listOf(source.copy(archivedAt=1))),LocalDate.now()).isEmpty())
    }
}
