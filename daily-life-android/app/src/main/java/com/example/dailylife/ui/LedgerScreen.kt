package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.*
import java.time.*

internal val ledgerTabs=listOf("overview" to "概况","transactions" to "明细","stats" to "统计","accounts" to "账户")
internal fun ledgerMoney(ui:LedgerUi,value:Long)=if(ui.hideAmounts) "••••" else "¥ "+LedgerRules.display(value)
internal fun ledgerType(value:String)=when(value) {"expense"->"支出";"income"->"收入";"transfer"->"转账";"refund"->"退款";"reimbursement"->"报销回款";"adjustment"->"余额校准";else->value}
internal fun LedgerSnapshot.ledgerName(id:String?)=ledgers.find {it.id==id}?.name ?: "日常账本"
internal fun LedgerSnapshot.accountName(id:String?)=accounts.find {it.id==id}?.name ?: "未指定账户"
internal fun LedgerSnapshot.categoryName(id:String?)=categories.find {it.id==id}?.let {c->categories.find {it.id==c.parentId}?.let {it.name+" / "+c.name} ?: c.name} ?: "未分类"
internal fun LedgerSnapshot.title(t:LedgerTransaction)=t.subject.ifBlank {t.counterparty}.ifBlank {if(t.type=="transfer") accountName(t.accountId)+" → "+accountName(t.toAccountId) else categoryName(t.categoryId)}

@Composable fun LedgerScreen(ui:LedgerUi,vm:LedgerViewModel,addFiles:()->Unit,view:(Attachment)->Unit,
    exportCsv:()->Unit,importCsv:()->Unit,exportBackup:()->Unit,restoreBackup:()->Unit) {
    val contentState=rememberSaveableStateHolder()
    when {
        ui.editor?.kind=="claim"->LedgerClaimEditor(ui,vm,addFiles,view)
        ui.editor!=null->LedgerEditor(ui,vm,addFiles,view)
        ui.detailId!=null->LedgerDetail(ui,vm,view)
        else->contentState.SaveableStateProvider("content") {LedgerContent(ui,vm,view,importCsv,restoreBackup)}
    }
    if(ui.error!=null) GlassAlertDialog(onDismissRequest=vm::clearError,title={Text("请核对后继续")},text={Text(ui.error)},confirmButton={TextButton(onClick=vm::clearError) {Text("知道了")}},containerColor=LocalGlass.current.sheet)
    ui.restore?.let {preview->GlassAlertDialog(onDismissRequest=vm::dismissRestore,title={Text("恢复账本备份？")},text={Text("已校验 "+preview.count+" 笔交易、"+preview.files+" 份附件。追加到本机，已有编号保留本机版本，不覆盖原数据。备份不含功能箱付款关系。")},confirmButton={TextButton(enabled=!ui.busy,onClick={vm.restoreBackup()}) {Text("确认追加恢复")}},dismissButton={TextButton(enabled=!ui.busy,onClick=vm::dismissRestore) {Text("取消")}},containerColor=LocalGlass.current.sheet)}
}

@Composable private fun LedgerContent(ui:LedgerUi,vm:LedgerViewModel,view:(Attachment)->Unit,importCsv:()->Unit,restoreBackup:()->Unit) {
    var chooseLedger by remember {mutableStateOf(false)}
    val pageStates=rememberSaveableStateHolder()
    val secondary=ui.page in setOf("claims","imports","trash","changes","accountFlow","budgets","manage")
    val backPage=when(ui.page) {"accountFlow"->"accounts";"manage"->ui.managementReturnPage;else->"manage"}
    LaunchedEffect(ui.page) {if(ui.page=="cashFlow") vm.page("stats")}
    BackHandler(secondary) {vm.page(backPage)}
    Column(Modifier.fillMaxSize().testTag("ledgerRoot")) {
        if(secondary) GlassPageHeader(when(ui.page) {"claims"->"报销申请";"imports"->"账单导入";"trash"->"账目回收站";"accountFlow"->"账户流水";"budgets"->"预算";"manage"->"账本设置";else->"变更记录"},back={vm.page(backPage)},backTag="backLedgerPage")
        else {
            LedgerInternalNavigation(ui.page,vm::page)
            if(ui.page!="accounts" && ui.data.ledgers.count {it.archivedAt==null}>1) {
                TextButton(onClick={chooseLedger=true},modifier=Modifier.padding(horizontal=18.dp).testTag("selectLedger")) {Text(ui.data.ledgerName(ui.ledgerId)+" ▾")}
            }
            if(ui.page!="accounts") LedgerBrowseHeader(ui,vm)
        }
        if(!ui.ready) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else Box(Modifier.weight(1f).testTag("ledgerContent")) {
            GlassPageTransition(ui.page,label="ledger page") {page->
                pageStates.SaveableStateProvider(page) {when(page) {
                    "overview"->LedgerOverview(ui,vm)
                    "transactions"->LedgerTransactions(ui,vm)
                    "stats","cashFlow"->LedgerStatistics(ui,vm)
                    "accounts"->LedgerAccounts(ui,vm)
                    "accountFlow"->LedgerAccountFlow(ui,vm)
                    "budgets"->LedgerBudgets(ui,vm)
                    "claims"->LedgerClaims(ui,vm,view)
                    "imports"->LedgerImports(ui,vm,importCsv)
                    "trash"->LedgerTrash(ui,vm)
                    "changes"->LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(ui.data.changes.isEmpty()) item {LedgerEmpty("暂无财务变更记录","更正、校准与删除操作会在本机留痕。")}
                        items(ui.data.changes,key={it.id}) {log->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {Text(log.summary);Text(log.action+" · "+Instant.ofEpochMilli(log.createdAt).atZone(ZoneId.systemDefault()).toLocalDate(),color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}}}
                    }
                    else->LedgerManage(ui,vm,importCsv,restoreBackup)
                }}
            }
        }
        if(ui.page in setOf("overview","transactions","stats")) GlassFloatingActionRow("记一笔","addLedgerTransaction",enabled=!ui.busy) {vm.begin()}
    }
    if(chooseLedger) LedgerPicker("选择账本",ui.data.ledgers.filter {it.archivedAt==null || it.id==ui.ledgerId}.map {it.id to (it.name+if(it.archivedAt!=null) " · 已归档" else "")},ui.ledgerId,{chooseLedger=false}) {vm.selectLedger(it);chooseLedger=false}
}
internal fun List<LedgerTransaction>.ledgerOrder()=sortedWith(compareByDescending<LedgerTransaction> {it.occurredOn}
    .thenByDescending {it.occurredTime}.thenByDescending {it.createdAt}.thenBy {it.id})
internal fun ledgerSignedMoney(ui:LedgerUi,t:LedgerTransaction):String {
    if(ui.hideAmounts) return "••••"
    val sign=when(t.type) {"expense"->"− ";"income","refund","reimbursement"->"＋ ";else->""}
    return sign+ledgerMoney(ui,t.amountMinor)
}
@Composable internal fun LedgerMetric(label:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
        Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
    }
}
@Composable internal fun LedgerMetricPair(first:String,firstValue:String,second:String,secondValue:String) {
    if(LocalDensity.current.fontScale>1.3f || maxOf(firstValue.length,secondValue.length)>15) Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        LedgerMetric(first,firstValue);LedgerMetric(second,secondValue)
    } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        LedgerMetric(first,firstValue,Modifier.weight(1f));LedgerMetric(second,secondValue,Modifier.weight(1f))
    }
}
@Composable internal fun LedgerEmpty(title:String,note:String) {GlassEmptyState(title,note,Modifier.padding(vertical=8.dp))}
@Composable internal fun LedgerTransactionRow(t:LedgerTransaction,ui:LedgerUi,click:()->Unit) {
    val amount=ledgerSignedMoney(ui,t)
    val stacked=LocalDensity.current.fontScale>1.3f || amount.length>16
    GlassPanel(Modifier.fillMaxWidth().clickable(onClickLabel="查看账目详情",onClick=click).testTag("ledgerTransaction:"+t.id)) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=RoundedCornerShape(14.dp),color=LocalGlass.current.selected) {
                Box(Modifier.size(44.dp),contentAlignment=Alignment.Center) {
                    if(t.type in setOf("expense","income")) LedgerCategoryIcon(ui.data.categoryName(t.categoryId))
                    else Text(when(t.type) {"transfer"->"⇄";"refund"->"退";"reimbursement"->"报";else->"≈"},color=LocalGlass.current.accent)
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(ui.data.title(t),fontWeight=FontWeight.Medium)
                Text(listOfNotNull(ledgerType(t.type),t.paymentChannel.takeIf {it.isNotBlank()} ?: t.accountId?.let {ui.data.accountName(it)})
                    .joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                Text(t.occurredOn+(t.occurredTime?.let {" · "+it} ?: ""),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                if(stacked) Text(amount,style=MaterialTheme.typography.titleMedium,color=LocalGlass.current.accent)
            }
            if(!stacked) Text(amount,style=MaterialTheme.typography.titleSmall,
                color=if(t.type in setOf("income","refund","reimbursement")) LocalGlass.current.accent else LocalGlass.current.ink)
        }
    }
}
@Composable internal fun LedgerCategorySummary(ui:LedgerUi,name:String,value:Long,total:Long,compact:Boolean=false,click:()->Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min=if(compact) 48.dp else 64.dp).clickable(onClickLabel="查看分类明细",onClick=click).padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(LocalDensity.current.fontScale>1.3f) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {LedgerCategoryIcon(name);Text(name)}
            Text(ledgerMoney(ui,value),color=LocalGlass.current.accent)
        } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            LedgerCategoryIcon(name);Text(name,Modifier.weight(1f));Text(ledgerMoney(ui,value),color=LocalGlass.current.accent)
        }
        if(!compact && !ui.hideAmounts && total>0) {
            LinearProgressIndicator(progress={value.toFloat()/total},modifier=Modifier.fillMaxWidth().height(5.dp),color=LocalGlass.current.accent,trackColor=LocalGlass.current.selected)
            Text(String.format(java.util.Locale.ROOT,"%.1f%%",value*100.0/total),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        }
    }
}
@Composable internal fun LedgerPicker(title:String,options:List<Pair<String,String>>,selected:String?,dismiss:()->Unit,choose:(String)->Unit) {
    GlassSelectionSheet(title,options,selected,dismiss,"暂无可选项，请先在管理中添加。",choose)
}
@Composable internal fun LedgerMonthPicker(value:String,dismiss:()->Unit,choose:(String)->Unit) {
    GlassMonthPicker(value,dismiss,choose)
}
@Composable internal fun LedgerSelectRow(label:String,value:String,onClick:()->Unit) {
    GlassSelectorRow(label,value,onClick=onClick)
}
