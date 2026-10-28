package com.example.dailylife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.Attachment
import com.example.dailylife.data.local.RecordBundle
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.feature.ledger.*
import java.time.LocalDate

/** File tools live in the personal data area. They read committed content, never editor payloads. */
@Composable internal fun DataTransferSheet(kind:String,records:RecordsSnapshot,ledger:LedgerUi,vm:LedgerViewModel,
    dismiss:()->Unit,exportCsv:()->Unit,exportRecord:(RecordBundle)->Unit,exportAttachment:(Attachment)->Unit) {
    when(kind) {
        "csv"->LedgerDataExport(ledger,vm,dismiss,exportCsv)
        "markdown"->{
            val items=records.records.filter {it.record.deletedAt==null}.sortedByDescending {it.record.updatedAt}
            GlassSelectionSheet("选择要导出的记录",items.map {it.record.id to records.title(it)},null,dismiss,
                emptyText="还没有已保存的记录") {id->items.find {it.record.id==id}?.let {dismiss();exportRecord(it)}}
        }
        "attachment"->{
            val ids=records.links.filter {it.entityType!="draft"&&!it.entityType.endsWith("_draft")}.map {it.attachmentId}.toSet()
            val items=records.attachments.filter {it.id in ids&&it.status=="available"}.sortedByDescending {it.createdAt}
            GlassSelectionSheet("选择附件原件",items.map {it.id to it.displayName},null,dismiss,
                emptyText="还没有已保存内容的附件") {id->items.find {it.id==id}?.let {dismiss();exportAttachment(it)}}
        }
    }
}

@Composable private fun LedgerDataExport(ui:LedgerUi,vm:LedgerViewModel,dismiss:()->Unit,chooseFile:()->Unit) {
    var options by remember {mutableStateOf(ui.csvExport ?: LedgerCsvExport())}
    var dateField by remember {mutableStateOf<String?>(null)}
    val from=options.from.takeIf {it.isNotBlank()}?.let {parsedPickerDate(it)}
    val to=options.to.takeIf {it.isNotBlank()}?.let {parsedPickerDate(it)}
    val valid=(options.from.isBlank()||from!=null)&&(options.to.isBlank()||to!=null)&&
        (from==null||to==null||from<=to)&&options.types.isNotEmpty()&&options.fields.isNotEmpty()
    val count=ui.data.transactions.count {it.deletedAt==null&&(options.ledgerId==null||it.ledgerId==options.ledgerId)&&
        (options.from.isBlank()||it.occurredOn>=options.from)&&(options.to.isBlank()||it.occurredOn<=options.to)&&it.type in options.types}
    GlassSheet("导出账目 CSV",dismiss,dismissEnabled=!ui.busy,footer={
        PickerActions(dismiss,{vm.configureCsv(options);dismiss();chooseFile()},"选择保存位置",valid&&!ui.busy,
            confirmTag="exportDataCsv",cancelTag="cancelDataCsv")
    }) {
        Text("导出已保存的账目；未完成编辑不包含在内。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        GlassSelectField("账本",listOf("" to "全部账本")+ui.data.ledgers.map {it.id to it.name},options.ledgerId.orEmpty()) {
            options=options.copy(ledgerId=it.ifBlank {null})
        }
        GlassSelectorRow("开始日期",options.from.ifBlank {"不限"},Modifier.testTag("dataCsvFrom")) {dateField="from"}
        GlassSelectorRow("结束日期",options.to.ifBlank {"不限"},Modifier.testTag("dataCsvTo")) {dateField="to"}
        if(options.from.isNotBlank()||options.to.isNotBlank()) TextButton(onClick={options=options.copy(from="",to="")}) {Text("全部日期")}
        GlassMultiSelectField("交易类型",ledgerCsvTypes.map {it to ledgerType(it)},options.types) {options=options.copy(types=it)}
        GlassMultiSelectField("导出字段",ledgerCsvFields.toList(),options.fields.toSet()) {selected->
            options=options.copy(fields=ledgerCsvFields.keys.filter {it in selected})
        }
        Text(if(valid) "当前范围共 $count 笔" else "请检查日期范围，并至少选择一种交易类型和一个字段。",
            modifier=Modifier.testTag("dataCsvCount"),color=if(valid) LocalGlass.current.accent else MaterialTheme.colorScheme.error)
        Text("CSV 包含真实金额和选中的文字字段，附件原件请使用备份保存。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
    }
    dateField?.let {field->GlassDatePicker((if(field=="from") options.from else options.to).ifBlank {LocalDate.now().toString()},{dateField=null}) {
        options=if(field=="from") options.copy(from=it) else options.copy(to=it);dateField=null
    }}
}
