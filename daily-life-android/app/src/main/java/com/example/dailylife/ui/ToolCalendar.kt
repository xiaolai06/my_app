package com.example.dailylife.ui

import com.example.dailylife.feature.tools.ToolsSnapshot
import com.example.dailylife.feature.tools.field
import java.time.LocalDate

/** A calendar projection keeps the original source ID; it never creates plan objects. */
internal data class ToolCalendarEntry(val key:String,val sourceId:String,val title:String,val kind:String,val description:String)

internal fun toolCalendarEntries(data:ToolsSnapshot,date:LocalDate):List<ToolCalendarEntry> {
    val day=date.toString()
    val visible=data.entries.filter {it.deletedAt==null && it.archivedAt==null}
    val result=mutableListOf<ToolCalendarEntry>()
    visible.filter {it.kind=="anniversary" && it.field("showCalendar","true")=="true" && it.state !in setOf("cancelled","paused")}.forEach {entry->
        val original=runCatching {LocalDate.parse(entry.field("date"))}.getOrNull()
        if(original!=null && original<=date && data.nextDate(entry,date)==date) {
            val years=date.year-original.year
            val description=if(entry.field("repeat")=="yearly") (if(years==0) "原始日期" else "$years 周年") else "单次日期"
            result+=ToolCalendarEntry("anniversary:"+entry.id,entry.id,entry.title,"纪念日",description)
        }
    }
    val payments=visible.filter {it.kind in setOf("subscription","bill")}.associateBy {it.id}
    data.periods.filter {it.dueDate==day && it.status in setOf("pending","paid")}.forEach {period->
        val source=payments[period.sourceId] ?: return@forEach
        // Stopping a source hides pending prompts, while settled history remains visible.
        if(period.status=="pending" && source.state !in setOf("active","trial")) return@forEach
        val kind=if(source.kind=="subscription") "订阅" else "缴费"
        val description=(if(period.status=="paid") "已付" else if(date<LocalDate.now()) "待处理 · 已逾期" else "待处理")+
            " · 所属期 "+period.periodStart+(if(period.periodEnd!=period.periodStart) " 至 "+period.periodEnd else "")
        result+=ToolCalendarEntry("period:"+period.id,source.id,source.title,kind,description)
    }
    payments.values.filter {it.kind=="subscription" && it.state=="trial" && it.field("trialEnd")==day}.forEach {entry->
        result+=ToolCalendarEntry("trial:"+entry.id,entry.id,entry.title,"试用结束","仅提示试用到期，请核对原平台的续费设置")
    }
    return result.sortedWith(compareBy<ToolCalendarEntry> {it.kind}.thenBy {it.title}.thenBy {it.key})
}

internal fun toolCalendarDates(data:ToolsSnapshot,start:LocalDate,end:LocalDate):Set<String> = buildSet {
    var date=start
    while(date<=end) {if(toolCalendarEntries(data,date).isNotEmpty()) add(date.toString());date=date.plusDays(1)}
}
