package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.*
import java.time.*

internal val ledgerTabs=listOf("overview" to "概览","transactions" to "明细","stats" to "统计","accounts" to "账户","budgets" to "预算","manage" to "管理")
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
        else->contentState.SaveableStateProvider("content") {LedgerContent(ui,vm,view,exportCsv,importCsv,exportBackup,restoreBackup)}
    }
    if(ui.error!=null) AlertDialog(onDismissRequest=vm::clearError,title={Text("请核对后继续")},text={Text(ui.error)},confirmButton={TextButton(onClick=vm::clearError) {Text("知道了")}},containerColor=LocalGlass.current.sheet)
    ui.restore?.let {preview->AlertDialog(onDismissRequest=vm::dismissRestore,title={Text("恢复账本备份？")},text={Text("已校验 "+preview.count+" 笔交易、"+preview.files+" 份附件。追加到本机，已有编号保留本机版本，不覆盖原数据。备份不含功能箱付款关系。")},confirmButton={TextButton(enabled=!ui.busy,onClick={vm.restoreBackup()}) {Text("确认追加恢复")}},dismissButton={TextButton(enabled=!ui.busy,onClick=vm::dismissRestore) {Text("取消")}},containerColor=LocalGlass.current.sheet)}
}

@Composable private fun LedgerContent(ui:LedgerUi,vm:LedgerViewModel,view:(Attachment)->Unit,exportCsv:()->Unit,importCsv:()->Unit,exportBackup:()->Unit,restoreBackup:()->Unit) {
    var picker by remember {mutableStateOf<String?>(null)}
    var confirmExport by remember {mutableStateOf<String?>(null)}
    var csvOptions by remember {mutableStateOf(false)}
    val pageStates=rememberSaveableStateHolder()
    val secondary=ui.page in setOf("claims","imports","drafts","trash","changes","accountFlow","cashFlow")
    val backPage=when(ui.page) {"accountFlow"->"accounts";"cashFlow"->"stats";else->"manage"}
    BackHandler(secondary) {vm.page(backPage)}
    Column(Modifier.fillMaxSize().testTag("ledgerRoot")) {
        if(secondary) Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={vm.page(backPage)}) {Text("返回")}
            Text(when(ui.page) {"claims"->"报销申请";"imports"->"账单导入";"drafts"->"记账草稿";"trash"->"账目回收站";"accountFlow"->"账户流水";"cashFlow"->"现金流";else->"变更记录"},style=MaterialTheme.typography.titleMedium)
        } else {
            if(LocalDensity.current.fontScale>1.3f) Column(Modifier.fillMaxWidth().padding(horizontal=14.dp)) {
                TextButton(onClick={picker="ledger"},modifier=Modifier.fillMaxWidth().testTag("selectLedger")) {Text(ui.data.ledgerName(ui.ledgerId)+" ▾")}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={picker="month"},modifier=Modifier.weight(1f)) {Text(ui.month+" ▾")}
                    TextButton(onClick=vm::toggleHidden,modifier=Modifier.testTag("hideLedgerAmounts")) {Text(if(ui.hideAmounts) "显示" else "隐藏")}
                }
            } else Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={picker="ledger"},modifier=Modifier.weight(1f).testTag("selectLedger")) {Text(ui.data.ledgerName(ui.ledgerId)+" ▾",maxLines=2)}
                TextButton(onClick={picker="month"}) {Text(ui.month+" ▾")}
                TextButton(onClick=vm::toggleHidden,modifier=Modifier.testTag("hideLedgerAmounts")) {Text(if(ui.hideAmounts) "显示" else "隐藏")}
            }
            LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(ledgerTabs) {(id,title)->GlassChoice(title,ui.page==id,Modifier.testTag("ledgerTab:"+id)) {vm.page(id)}}
            }
            Spacer(Modifier.height(10.dp))
        }
        if(!ui.ready) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()}
        else Box(Modifier.weight(1f)) {
            Crossfade(ui.page,animationSpec=tween(if(LocalReduceMotion.current) 0 else 180),label="ledger page") {page->
                pageStates.SaveableStateProvider(page) {when(page) {
                    "overview"->LedgerOverview(ui,vm)
                    "transactions"->LedgerTransactions(ui,vm)
                    "stats"->LedgerStatistics(ui,vm)
                    "accounts"->LedgerAccounts(ui,vm)
                    "accountFlow"->LedgerAccountFlow(ui,vm)
                    "cashFlow"->LedgerCashFlow(ui,vm)
                    "budgets"->LedgerBudgets(ui,vm)
                    "claims"->LedgerClaims(ui,vm,view)
                    "imports"->LedgerImports(ui,vm,importCsv)
                    "drafts"->LedgerDrafts(ui,vm)
                    "trash"->LedgerTrash(ui,vm)
                    "changes"->LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(ui.data.changes.isEmpty()) item {LedgerEmpty("暂无财务变更记录","更正、校准与删除操作会在本机留痕。")}
                        items(ui.data.changes,key={it.id}) {log->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {Text(if(ui.hideAmounts) "金额隐藏已开启，显示金额后可核对变更详情。" else log.summary);Text(log.action+" · "+Instant.ofEpochMilli(log.createdAt).atZone(ZoneId.systemDefault()).toLocalDate(),color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}}}
                    }
                    else->LedgerManage(ui,vm,{csvOptions=true},importCsv,{confirmExport="backup"},restoreBackup)
                }}
            }
            if(LocalDensity.current.fontScale<=1.3f && ui.page in setOf("overview","transactions","stats")) ExtendedFloatingActionButton(onClick={vm.begin()},modifier=Modifier.align(Alignment.BottomEnd).padding(18.dp).testTag("addLedgerTransaction"),containerColor=LocalGlass.current.selected,contentColor=LocalGlass.current.accent) {Text("＋ 记一笔")}
        }
        if(LocalDensity.current.fontScale>1.3f && ui.page in setOf("overview","transactions","stats")) FilledTonalButton(onClick={vm.begin()},enabled=!ui.busy,
            modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp).heightIn(min=56.dp).testTag("addLedgerTransaction"),
            colors=ButtonDefaults.filledTonalButtonColors(containerColor=LocalGlass.current.selected,contentColor=LocalGlass.current.accent)) {Text("＋ 记一笔")}
    }
    if(picker=="ledger") LedgerPicker("选择账本",ui.data.ledgers.filter {it.archivedAt==null || it.id==ui.ledgerId}.map {it.id to (it.name+if(it.archivedAt!=null) " · 已归档" else "")},ui.ledgerId,{picker=null}) {vm.selectLedger(it);picker=null}
    if(picker=="month") LedgerMonthPicker(ui.month,{picker=null}) {vm.month(it);picker=null}
    if(csvOptions) LedgerCsvExportSheet(ui,vm,{csvOptions=false},exportCsv)
    if(confirmExport!=null) ConfirmDialog("导出本机资料",if(confirmExport=="csv") "导出当前账本、当前月份的明细。文件包含真实金额，金额隐藏不会遮盖导出内容；CSV 不包含附件原件。" else "备份包含全部账本、账户、交易、报销、草稿和附件原件，文件未加密，请妥善保管。",{confirmExport=null},{if(confirmExport=="csv") exportCsv() else exportBackup()},"选择保存位置")
}

@Composable private fun LedgerOverview(ui:LedgerUi,vm:LedgerViewModel) {
    val summary=ui.data.summary(ui.ledgerId,ui.month)
    val recent=ui.data.transactions.filter {it.deletedAt==null && it.ledgerId==ui.ledgerId && it.occurredOn.startsWith(ui.month)}.take(6)
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerOverview"),contentPadding=PaddingValues(18.dp,4.dp,18.dp,108.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(ui.month+" · 净支出",color=LocalGlass.current.muted,style=MaterialTheme.typography.labelLarge)
            Text(ledgerMoney(ui,summary.netExpense),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                LedgerMetric("消费支出",ledgerMoney(ui,summary.expense),Modifier.weight(1f));LedgerMetric("普通收入",ledgerMoney(ui,summary.income),Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                LedgerMetric("实际退款",ledgerMoney(ui,summary.refunds),Modifier.weight(1f));LedgerMetric("报销到账",ledgerMoney(ui,summary.reimbursement),Modifier.weight(1f))
            }
            Text("转账、期初余额与校准单独核对，不计普通收支。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        }}}
        val budget=ui.data.budgets.find {it.ledgerId==ui.ledgerId && it.month==ui.month && it.categoryId==null}
        item {GlassPanel(Modifier.fillMaxWidth().clickable {vm.page("budgets")}) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("月预算",style=MaterialTheme.typography.titleMedium)
            if(budget==null) Text("给本月设置一个合适的额度 ›",color=LocalGlass.current.accent) else {
                val used=ui.data.budgetUsed(budget);Text("剩余 "+ledgerMoney(ui,budget.limitMinor-used),color=LocalGlass.current.accent)
                LinearProgressIndicator(progress={ (used.toFloat()/budget.limitMinor).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                Text("已用 "+ledgerMoney(ui,used)+" / "+ledgerMoney(ui,budget.limitMinor),color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }
        }}}
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("最近账目",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={vm.page("transactions")}) {Text("全部明细")}}}
        if(recent.isEmpty()) item {LedgerEmpty("从第一笔开始","记下真实发生的收支，账户和分类可以稍后完善。")}
        items(recent,key={it.id}) {LedgerTransactionRow(it,ui) {vm.detail(it.id)}}
    }
}
@Composable internal fun LedgerMetric(label:String,value:String,modifier:Modifier=Modifier) {Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(label,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall);Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)}}
@Composable internal fun LedgerEmpty(title:String,note:String) {Column(Modifier.fillMaxWidth().padding(vertical=25.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(note,color=LocalGlass.current.muted)}}
@Composable internal fun LedgerTransactionRow(t:LedgerTransaction,ui:LedgerUi,click:()->Unit) {
    val large=LocalDensity.current.fontScale>1.3f
    GlassPanel(Modifier.fillMaxWidth().clickable(onClick=click)) {Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(15.dp),color=LocalGlass.current.selected) {Text(when(t.type) {"expense"->"−";"transfer"->"⇄";"adjustment"->"≈";else->"＋"},Modifier.padding(13.dp),color=LocalGlass.current.accent,style=MaterialTheme.typography.titleMedium)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {Text(ui.data.title(t),fontWeight=FontWeight.Medium);Text(t.occurredOn+" · "+(t.occurredTime ?: "仅日期"),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted);Text(ledgerType(t.type)+" · "+ui.data.accountName(t.accountId),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            if(large) Text(ledgerMoney(ui,t.amountMinor),style=MaterialTheme.typography.titleSmall,color=LocalGlass.current.accent)
        }
        if(!large) Text(ledgerMoney(ui,t.amountMinor),style=MaterialTheme.typography.titleSmall,color=if(t.type in setOf("income","refund","reimbursement")) LocalGlass.current.accent else LocalGlass.current.ink)
    }}
}

@Composable private fun LedgerTransactions(ui:LedgerUi,vm:LedgerViewModel) {
    val filter=ui.transactionFilter
    val query=filter.query;val type=filter.type;val category=filter.category;val account=filter.account
    val onlyBudget=filter.onlyBudget;val advanced=filter.advanced
    val from=filter.from;val to=filter.to;val min=filter.min;val max=filter.max
    var picker by remember {mutableStateOf<String?>(null)}
    val minimum=runCatching {LedgerRules.minor(min,true)}.getOrNull();val maximum=runCatching {LedgerRules.minor(max,true)}.getOrNull()
    val validDates=(from.isBlank()||runCatching {LocalDate.parse(from)}.isSuccess)&&(to.isBlank()||runCatching {LocalDate.parse(to)}.isSuccess)&&(from.isBlank()||to.isBlank()||from<=to)
    val validAmounts=(min.isBlank()||minimum!=null)&&(max.isBlank()||maximum!=null)&&(minimum==null||maximum==null||minimum<=maximum)
    val rows=ui.data.transactions.filter {validDates&&validAmounts&&it.deletedAt==null && it.ledgerId==ui.ledgerId && (if(from.isBlank()&&to.isBlank()) it.occurredOn.startsWith(ui.month) else (from.isBlank()||it.occurredOn>=from)&&(to.isBlank()||it.occurredOn<=to)) && (type=="all"||it.type==type) && (category.isBlank()||it.categoryId==category) && (account.isBlank()||it.accountId==account||it.toAccountId==account) && (!onlyBudget||it.includeInBudget) && (minimum==null||it.amountMinor>=minimum) && (maximum==null||it.amountMinor<=maximum) && listOf(it.subject,it.counterparty,it.location,it.note,ui.data.categoryName(it.categoryId)).any {v->v.contains(query,true)}}
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerTransactions"),contentPadding=PaddingValues(18.dp,4.dp,18.dp,108.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {OutlinedTextField(query,{vm.filter(filter.copy(query=it))},label={Text("搜索事项、商户、地点与备注")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),singleLine=true)}
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("共 "+rows.size+" 笔",Modifier.weight(1f),color=LocalGlass.current.muted);TextButton(onClick={vm.filter(filter.copy(advanced=!advanced))}) {Text(if(advanced) "收起筛选" else "筛选条件")}}}
        item {LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items(listOf("all" to "全部","expense" to "支出","income" to "收入","transfer" to "转账","refund" to "退款","reimbursement" to "回款","adjustment" to "校准")) {(id,name)->GlassChoice(name,type==id) {vm.filter(filter.copy(type=id))}}}}
        if(advanced) item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            LedgerSelectRow("分类",if(category.isBlank()) "全部分类" else ui.data.categoryName(category)) {picker="category"}
            LedgerSelectRow("账户",if(account.isBlank()) "全部账户" else ui.data.accountName(account)) {picker="account"}
            LedgerField(from,{vm.filter(filter.copy(from=it))},"开始日期（可选 YYYY-MM-DD）");LedgerField(to,{vm.filter(filter.copy(to=it))},"结束日期（可选 YYYY-MM-DD）")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {LedgerField(min,{vm.filter(filter.copy(min=it))},"最低金额",Modifier.weight(1f),numeric=true);LedgerField(max,{vm.filter(filter.copy(max=it))},"最高金额",Modifier.weight(1f),numeric=true)}
            if(!validDates) Text("请填写有效日期，结束日期不能早于开始日期。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            if(!validAmounts) Text("金额最多两位小数，最高金额不能低于最低金额。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(onlyBudget,{vm.filter(filter.copy(onlyBudget=it))});Text("仅显示计入预算的账目")}
            TextButton(onClick={vm.filter(LedgerTransactionFilter(advanced=true))}) {Text("清空筛选")}
        }}}
        if(rows.isEmpty()) item {LedgerEmpty("没有符合条件的账目","可以切换月份或清空筛选条件。")}
        rows.groupBy {it.occurredOn}.forEach {(date,transactions)->item(key="date"+date) {Text(date,style=MaterialTheme.typography.labelLarge,color=LocalGlass.current.muted)};items(transactions,key={it.id}) {LedgerTransactionRow(it,ui) {vm.detail(it.id)}}}
    }
    if(picker=="category") LedgerPicker("按分类筛选",listOf("" to "全部分类")+ui.data.categories.map {it.id to ui.data.categoryName(it.id)},category,{picker=null}) {vm.filter(filter.copy(category=it));picker=null}
    if(picker=="account") LedgerPicker("按账户筛选",listOf("" to "全部账户")+ui.data.accounts.map {it.id to it.name},account,{picker=null}) {vm.filter(filter.copy(account=it));picker=null}
}

@Composable private fun LedgerStatistics(ui:LedgerUi,vm:LedgerViewModel) {
    val rows=ui.data.transactions.filter {it.deletedAt==null&&it.ledgerId==ui.ledgerId&&it.occurredOn.startsWith(ui.month)}
    val positive=rows.filter {it.type=="expense"};val total=positive.sumOf {it.amountMinor}
    val groups=positive.groupBy {it.categoryId}.map {(id,list)->id to list.sumOf {it.amountMinor}}.sortedByDescending {it.second}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,4.dp,18.dp,108.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {LedgerSelectRow("现金流","选择日期区间查看真实资金流向") {vm.page("cashFlow")}}
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("消费分类",style=MaterialTheme.typography.titleMedium)
            Text("占比只统计实际消费，退款与回款单独列示。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            if(total==0L) Text("本月暂无消费支出")
            groups.forEach {(id,value)->Column(Modifier.fillMaxWidth().clickable {vm.page("transactions")}.padding(vertical=8.dp)) {
                Row(Modifier.fillMaxWidth()) {Text(ui.data.categoryName(id),Modifier.weight(1f));Text(ledgerMoney(ui,value))}
                Spacer(Modifier.height(8.dp));LinearProgressIndicator(progress={value.toFloat()/total},modifier=Modifier.fillMaxWidth())
                Text(String.format(java.util.Locale.ROOT,"%.1f%%",value*100.0/total),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            }}
        }}}
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("近六个月净支出",style=MaterialTheme.typography.titleMedium)
            for(offset in 5 downTo 0) {val m=YearMonth.parse(ui.month).minusMonths(offset.toLong()).toString();val s=ui.data.summary(ui.ledgerId,m)
                Row(Modifier.fillMaxWidth().clickable {vm.month(m);vm.page("transactions")}.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {Text(m,Modifier.weight(1f));Text(ledgerMoney(ui,s.netExpense),color=LocalGlass.current.accent)}
            }
        }}}
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("商户排行",style=MaterialTheme.typography.titleMedium)
            val merchants=positive.groupBy {it.counterparty.ifBlank {"未填写商户"}}.map {(name,t)->name to t.sumOf {it.amountMinor}}.sortedByDescending {it.second}.take(10)
            if(merchants.isEmpty()) Text("暂无商户消费")
            merchants.forEach {(name,value)->Row(Modifier.fillMaxWidth()) {Text(name,Modifier.weight(1f));Text(ledgerMoney(ui,value))}}
        }}}
    }
}

@Composable internal fun LedgerPicker(title:String,options:List<Pair<String,String>>,selected:String?,dismiss:()->Unit,choose:(String)->Unit) {
    var search by remember {mutableStateOf("")}
    GlassSheet(title,dismiss) {
        if(options.size>8) LedgerField(search,{search=it},"搜索选项")
        options.filter {it.second.contains(search,true)}.forEach {(id,name)->GlassChoice(name,id==selected,Modifier.fillMaxWidth()) {choose(id)}}
        if(options.isEmpty()) Text("暂无可选项，请先在管理中添加。",color=LocalGlass.current.muted)
        TextButton(onClick=dismiss) {Text("取消")}
    }
}
@Composable internal fun LedgerMonthPicker(value:String,dismiss:()->Unit,choose:(String)->Unit) {
    var selected by remember {mutableStateOf(YearMonth.parse(value))};var text by remember {mutableStateOf(value)};var error by remember {mutableStateOf<String?>(null)}
    GlassSheet("选择月份",dismiss) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {TextButton(onClick={selected=selected.minusYears(1);text=selected.toString()}) {Text("上一年")};Text(selected.year.toString(),Modifier.weight(1f));TextButton(onClick={selected=selected.plusYears(1);text=selected.toString()}) {Text("下一年")}}
        FlowChoices((1..12).map {it.toString() to (it.toString()+" 月")},selected.monthValue.toString()) {selected=selected.withMonth(it.toInt());text=selected.toString()}
        LedgerField(text,{text=it;error=null},"月份 YYYY-MM")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={runCatching {YearMonth.parse(text)}.onSuccess {choose(it.toString())}.onFailure {error="请输入有效月份，例如 2026-10"}},modifier=Modifier.fillMaxWidth()) {Text("确定")}
    }
}
@Composable internal fun LedgerSelectRow(label:String,value:String,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(16.dp),color=LocalGlass.current.sheet.copy(alpha=.68f),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Text(label,Modifier.weight(1f),color=LocalGlass.current.muted);Text(value+" ▾",Modifier.weight(1.4f),color=LocalGlass.current.accent)}
    }
}
