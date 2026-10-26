package com.example.dailylife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.feature.plans.PlansSnapshot
import com.example.dailylife.feature.ledger.LedgerSnapshot
import com.example.dailylife.feature.tools.*
import java.math.BigDecimal

private data class SearchHit(val kind:String,val id:String,val title:String,val text:String,val label:String)
@Composable fun GlobalSearch(records:RecordsSnapshot,plans:PlansSnapshot,ledger:LedgerSnapshot,tools:ToolsSnapshot,
    hiddenAmounts:Boolean,dismiss:()->Unit,open:(String,String)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var filter by rememberSaveable {mutableStateOf("all")}
    var archives by rememberSaveable {mutableStateOf(false)}
    val hits=remember(records,plans,ledger,tools,hiddenAmounts,archives) {buildList {
        records.records.filter {it.record.deletedAt==null && (archives || it.record.archivedAt==null)}.forEach {r->
            add(SearchHit("record",r.record.id,records.title(r),listOf(r.body,r.bookmark?.url.orEmpty(),r.fields.filter {!it.isPrivate}.joinToString(" "){it.name+" "+it.value},
                records.attachmentsFor("record",r.record.id).joinToString(" "){it.originalName}).joinToString(" "),"记录"))
        }
        plans.tasks.filter {it.deletedAt==null && (archives || it.archivedAt==null)}.forEach {add(SearchHit("task",it.id,it.title,it.note,"任务"))}
        plans.events.filter {it.deletedAt==null && (archives || it.archivedAt==null)}.forEach {add(SearchHit("event",it.id,it.title,it.note+" "+it.location,"日程"))}
        plans.habits.filter {it.deletedAt==null && (archives || it.archivedAt==null)}.forEach {add(SearchHit("habit",it.id,it.name,it.note,"习惯"))}
        plans.goals.filter {it.deletedAt==null && (archives || it.archivedAt==null)}.forEach {add(SearchHit("goal",it.id,it.name,it.note,"目标"))}
        plans.sessions.filter {it.deletedAt==null}.forEach {add(SearchHit("focus",it.id,it.title,it.note,"专注"))}
        ledger.transactions.filter {it.deletedAt==null}.forEach {t->
            val category=ledger.categories.find {it.id==t.categoryId}?.name.orEmpty()
            val amount=if(hiddenAmounts) "金额已隐藏" else "¥ "+BigDecimal.valueOf(t.amountMinor,2).toPlainString()
            add(SearchHit("transaction",t.id,t.subject.ifBlank {category.ifBlank {"账目"}},listOf(t.occurredOn,category,amount,t.counterparty,t.location,t.note).joinToString(" · "),"账目"))
        }
        tools.entries.filter {it.deletedAt==null && (archives || it.archivedAt==null)}.forEach {e->
            val safe=if(e.kind=="item") listOf(e.note,e.field("model"),e.field("brand"),e.field("location"),if(e.field("privateSerial","true")=="true") "" else e.field("serial"))
                else listOf(e.note,e.field("location"),e.field("person"),e.field("counterparty"),e.field("date"))
            val children=tools.children.filter {it.entryId==e.id}.joinToString(" "){it.title+" "+it.note}
            add(SearchHit(e.kind,e.id,e.title,safe.joinToString(" ")+" "+children,toolKinds.find {it.first==e.kind}?.second ?: "生活工具"))
        }
    }.distinctBy {it.kind to it.id}}
    val q=query.trim();val results=remember(q,filter,hits) {if(q.isBlank()) emptyList() else hits.filter {
        (filter=="all" || when(filter) {"record"->it.kind=="record";"ledger"->it.kind=="transaction";"plan"->it.kind in setOf("task","event","habit","goal","focus");else->it.kind in toolKinds.map {k->k.first}}) && (it.title.contains(q,true) || it.text.contains(q,true))
    }}
    GlassSheet("搜索本机内容",dismiss) {
        OutlinedTextField(query,{query=it},label={Text("标题、正文、商户或位置")},singleLine=true,modifier=Modifier.fillMaxWidth())
        FlowChoices(listOf("all" to "全部","record" to "记录","plan" to "计划","ledger" to "账目","tools" to "生活工具"),filter) {filter=it}
        SettingRow("包含已归档","回收站与私密字段不参与搜索",archives) {archives=it}
        if(q.isBlank()) Text("输入关键词开始查找；结果会打开原内容。",color=LocalGlass.current.muted)
        else {
            Text("找到 "+results.size+" 项"+(if(results.size>100) "，先展示前 100 项" else ""))
            results.take(100).forEach {hit->HomeRow(hit.title,hit.label+" · "+hit.text.replace('\n',' ').take(100),{open(hit.kind,hit.id)})}
            if(results.isEmpty()) Text("没有找到，可以尝试更短的关键词。",color=LocalGlass.current.muted)
        }
        TextButton(onClick=dismiss) {Text("关闭搜索")}
    }
}
