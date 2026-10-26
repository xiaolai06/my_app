package com.example.dailylife.feature.tools
import com.example.dailylife.data.local.*
import org.json.JSONObject
import java.time.*
import java.time.temporal.ChronoUnit

val toolKinds=listOf("checklist" to "生活清单","anniversary" to "纪念日","subscription" to "订阅","bill" to "缴费","item" to "物品","loan" to "借还","project" to "专题")
data class ToolForm(val kind:String,val id:String=newId(),val revision:Long=0,val fields:Map<String,String> = emptyMap()) {
    fun text(key:String,default:String="")=fields[key] ?: default
    fun set(key:String,value:String)=copy(fields=fields+(key to value))
    fun json()=JSONObject().put("version",1).put("kind",kind).put("id",id).put("revision",revision).put("fields",JSONObject(fields)).toString()
    companion object {fun parse(raw:String):ToolForm {val j=JSONObject(raw);require(j.getInt("version")==1);val f=j.getJSONObject("fields");return ToolForm(j.getString("kind"),j.getString("id"),j.getLong("revision"),f.keys().asSequence().associateWith {f.getString(it)})}}
}
fun ToolEntry.field(key:String,default:String="")=runCatching {JSONObject(payload).optString(key,default)}.getOrDefault(default)
/** Stable source and instance keys share the existing system reminder pipeline. */
fun ToolsSnapshot.reminders():List<PlanReminder> {
    val zone=ZoneId.systemDefault();val wanted=mutableListOf<PlanReminder>()
    entries.filter {it.deletedAt==null && it.archivedAt==null && it.state !in listOf("paused","cancelled","done","sold","scrapped") && it.field("reminderOn")=="true"}.forEach {e->
        val time=LocalTime.parse(e.field("reminderTime","09:00"));val days=e.field("reminderDays","0").toLong()
        fun add(date:String,key:String) {wanted+=PlanReminder(sourceType="tool",sourceId=e.id,instanceKey=key,title=e.title,triggerAt=LocalDate.parse(date).minusDays(days).atTime(time).atZone(zone).toInstant().toEpochMilli())}
        when(e.kind) {
            "subscription","bill"->{periods.filter {it.sourceId==e.id && it.status=="pending"}.forEach {add(it.dueDate,"period:"+it.originalKey)};if(e.state=="trial" && e.field("trialEnd").isNotBlank()) add(e.field("trialEnd"),"trial")}
            "anniversary"->nextDate(e)?.let {add(it.toString(),"anniversary:"+it)}
            "loan"->if(e.field("end").isNotBlank() && remaining(e)>0) add(e.field("end"),"return")
            "item"->if(e.field("warranty").isNotBlank()) add(e.field("warranty"),"warranty")
            "checklist"->if(e.field("date").isNotBlank()) add(e.field("date"),"single")
        }
    };return wanted
}
data class ToolsSnapshot(val entries:List<ToolEntry> = emptyList(),val children:List<ToolChild> = emptyList(),val logs:List<ToolLog> = emptyList(),
    val links:List<ToolLink> = emptyList(),val drafts:List<ToolDraft> = emptyList(),val calculations:List<ToolCalculation> = emptyList(),
    val periods:List<ToolPaymentPeriod> = emptyList(),val settlements:List<ToolPaymentSettlement> = emptyList(),val preferences:List<FeaturePreference> = emptyList()) {
    fun remaining(e:ToolEntry)=e.field("quantity","1").toBigDecimal().movePointRight(3).longValueExact()-logs.filter {it.entryId==e.id && it.kind=="return"}.sumOf {it.quantityMilli}
    fun nextDate(e:ToolEntry,today:LocalDate=LocalDate.now()):LocalDate? {
        val date=runCatching {LocalDate.parse(e.field("date"))}.getOrNull() ?: return null
        if(e.field("repeat")!="yearly") return date
        val startYear=maxOf(today.year,date.year)
        for(y in startYear..startYear+8) {
            val next=runCatching {date.withYear(y)}.getOrNull()?.let {if(date.monthValue==2 && date.dayOfMonth==29 && !Year.isLeap(y.toLong())) when(e.field("leap","feb28")) {"skip"->null;"mar1"->LocalDate.of(y,3,1);else->it} else it}
            if(next!=null && next>=today) return next
        };return null
    }
    fun anniversaryText(e:ToolEntry,today:LocalDate=LocalDate.now()):String {
        val next=nextDate(e,today) ?: return "暂无下一次日期";val days=ChronoUnit.DAYS.between(today,next)
        val original=LocalDate.parse(e.field("date"));val years=next.year-original.year
        return (if(days==0L) "就是今天" else if(days>0) "还有 $days 天" else "已过去 ${-days} 天")+(if(e.field("repeat")=="yearly") " · $years 周年" else "")
    }
    fun pending(today:LocalDate=LocalDate.now())=periods.filter {it.status=="pending" && it.dueDate<=today.plusDays(7).toString() && entries.any {e->e.id==it.sourceId && e.deletedAt==null && e.archivedAt==null && e.state in setOf("active","trial")}}
}
