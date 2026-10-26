package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.*
import java.time.*

@Composable internal fun LedgerAccounts(ui:LedgerUi,vm:LedgerViewModel) {
    var edit by remember {mutableStateOf<MoneyAccount?>(null)};var add by remember {mutableStateOf(false)};var calibrate by remember {mutableStateOf<MoneyAccount?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,4.dp,18.dp,32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("全部账本共用账户",Modifier.weight(1f),color=LocalGlass.current.muted);TextButton(onClick={add=true}) {Text("新增账户")}}}
        if(ui.data.accounts.isEmpty()) item {LedgerEmpty("账户可以稍后添加","未指定账户的普通收支也能记录，并参与消费统计。")}
        items(ui.data.accounts,key={it.id}) {a->GlassPanel(Modifier.fillMaxWidth().clickable {vm.openAccount(a.id)}) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(a.name+(if(a.archivedAt!=null) " · 已归档" else ""),style=MaterialTheme.typography.titleMedium)
            Text(ledgerMoney(ui,ui.data.balance(a.id)),style=MaterialTheme.typography.headlineSmall,color=LocalGlass.current.accent)
            Text("期初 "+ledgerMoney(ui,a.openingBalanceMinor)+" · "+a.openingAsOfDate+" 起",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={vm.openAccount(a.id)}) {Text("账户流水与期间余额 ›")}
            if(a.note.isNotBlank()) Text(a.note,color=LocalGlass.current.muted)
            Row {TextButton(onClick={edit=a}) {Text("管理账户")};TextButton(enabled=a.archivedAt==null,onClick={calibrate=a}) {Text("校准余额")}}
        }}}
        item {Text("余额汇总所有账本的有效分录。转账属于账户资金移动；校准会保留调整前后事实。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}
    }
    if(add||edit!=null) LedgerAccountEditor(ui,vm,edit,{add=false;edit=null})
    calibrate?.let {a->
        var target by remember(a.id) {mutableStateOf(LedgerRules.display(ui.data.balance(a.id)))};var date by remember(a.id) {mutableStateOf(LocalDate.now().toString())};var reason by remember {mutableStateOf("")};var calendar by remember {mutableStateOf(false)}
        GlassSheet("校准 · "+a.name,{calibrate=null}) {
            Text("当前记账余额 "+ledgerMoney(ui,ui.data.balance(a.id))+"。校准生成独立调整，不作为普通收入或支出。",color=LocalGlass.current.muted)
            LedgerField(target,{target=it},"该日目标余额",numeric=true);LedgerSelectRow("校准日期",date) {calendar=true};LedgerField(reason,{reason=it},"核对原因（必填）",single=false)
            Button(enabled=!ui.busy,onClick={vm.run("余额已校准",onSuccess={calibrate=null}) {calibrate(a.id,date,target,reason)}},modifier=Modifier.fillMaxWidth()) {Text("确认校准")}
        }
        if(calendar) CalendarDialog(date,{calendar=false}) {date=it;calendar=false}
    }
}
@Composable private fun LedgerAccountEditor(ui:LedgerUi,vm:LedgerViewModel,a:MoneyAccount?,dismiss:()->Unit) {
    var name by remember(a?.id) {mutableStateOf(a?.name.orEmpty())};var type by remember(a?.id) {mutableStateOf(a?.accountType ?: "cash")};var opening by remember(a?.id) {mutableStateOf(LedgerRules.display(a?.openingBalanceMinor ?: 0))};var date by remember(a?.id) {mutableStateOf(a?.openingAsOfDate ?: LocalDate.now().toString())}
    var note by remember(a?.id) {mutableStateOf(a?.note.orEmpty())};var tail by remember(a?.id) {mutableStateOf(a?.tail.orEmpty())};var confirm by remember {mutableStateOf(false)};var archived by remember(a?.id) {mutableStateOf(a?.archivedAt!=null)};var calendar by remember {mutableStateOf(false)}
    GlassSheet(if(a==null) "新增账户" else "管理账户",dismiss) {
        LedgerField(name,{name=it},"账户名称");FlowChoices(listOf("cash" to "现金","bank" to "银行卡","payment" to "支付余额","other" to "其他"),type) {type=it}
        LedgerField(opening,{opening=it},"期初余额（允许负数）",numeric=true);LedgerSelectRow("期初基准日期",date) {calendar=true}
        LedgerField(tail,{tail=it},"标识尾号（选填）");LedgerField(note,{note=it},"备注",single=false)
        if(a!=null) {Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(archived,{archived=it});Text("归档账户，保留历史")};Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(confirm,{confirm=it});Text("我已核对修改期初对历史余额的影响")}}
        Button(enabled=!ui.busy,onClick={vm.run("账户已保存",onSuccess=dismiss) {saveAccount(name,type,opening,date,note,tail,a?.id,confirm,archived)}},modifier=Modifier.fillMaxWidth()) {Text("保存账户")}
    }
    if(calendar) CalendarDialog(date,{calendar=false}) {date=it;calendar=false}
}

@Composable internal fun LedgerBudgets(ui:LedgerUi,vm:LedgerViewModel) {
    var edit by remember {mutableStateOf<MonthlyBudget?>(null)};var add by remember {mutableStateOf(false)};var remove by remember {mutableStateOf<MonthlyBudget?>(null)}
    val rows=ui.data.budgets.filter {it.ledgerId==ui.ledgerId && it.month==ui.month}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,4.dp,18.dp,32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("自然月 · "+ui.month,Modifier.weight(1f),color=LocalGlass.current.muted);TextButton(onClick={add=true}) {Text("设置预算")}}}
        if(rows.isEmpty()) item {LedgerEmpty("给消费留一个边界","总预算与分类预算分别观察，不会相加成重复额度。")}
        items(rows,key={it.id}) {b->val used=ui.data.budgetUsed(b);GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(b.categoryId==null) "月总预算" else ui.data.categoryName(b.categoryId),style=MaterialTheme.typography.titleMedium)
            Text("剩余 "+ledgerMoney(ui,b.limitMinor-used),style=MaterialTheme.typography.titleLarge,color=LocalGlass.current.accent)
            LinearProgressIndicator(progress={(used.toFloat()/b.limitMinor).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            Text("已用 "+ledgerMoney(ui,used)+" / "+ledgerMoney(ui,b.limitMinor),color=LocalGlass.current.muted)
            if(used>b.limitMinor) Text("已超出预算 "+ledgerMoney(ui,used-b.limitMinor),color=MaterialTheme.colorScheme.error)
            ui.data.alerts.filter {it.budgetId==b.id}.forEach {alert->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("本月首次跨过 "+alert.threshold+"%"+(if(alert.readAt==null) " · 待查看" else " · 已查看"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                if(alert.readAt==null) TextButton(enabled=!ui.busy,onClick={vm.run("预算提醒已查看") {readBudgetAlert(b.id,alert.threshold)}}) {Text("已查看")}
            }}
            Row {TextButton(onClick={edit=b}) {Text("调整预算")};TextButton(onClick={remove=b}) {Text("删除预算")}}
        }}}
        item {Text("预算已用按本月支出减本月实际退款计算，可为负数；报销回款不返还预算。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)}
    }
    if(add||edit!=null) {
        val b=edit;var amount by remember(b?.id) {mutableStateOf(b?.let {LedgerRules.display(it.limitMinor)}.orEmpty())};var category by remember(b?.id) {mutableStateOf(b?.categoryId.orEmpty())};var alerts by remember(b?.id) {mutableStateOf(b?.alertsEnabled ?: true)};var picker by remember {mutableStateOf(false)}
        fun close(){add=false;edit=null}
        GlassSheet("设置月预算",{close()}) {
            Text(ui.data.ledgerName(ui.ledgerId)+" · "+ui.month,color=LocalGlass.current.muted)
            LedgerSelectRow("范围",if(category.isBlank()) "月总预算" else ui.data.categoryName(category)) {picker=true}
            LedgerField(amount,{amount=it},"预算额度",numeric=true)
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(alerts,{alerts=it});Text("记录 80% 与 100% 首次跨越提醒")}
            Button(enabled=!ui.busy,onClick={vm.run("预算已保存",onSuccess={close()}) {saveBudget(ui.ledgerId ?: error("请先选择账本"),ui.month,category.ifBlank {null},amount,alerts,b?.id)}},modifier=Modifier.fillMaxWidth()) {Text("保存预算")}
        }
        if(picker) LedgerPicker("预算范围",listOf("" to "月总预算")+ui.data.categories.filter {it.active&&it.direction=="expense"&&ui.data.categories.none {c->c.parentId==it.id}}.map {it.id to ui.data.categoryName(it.id)},category,{picker=false}) {category=it;picker=false}
    }
    remove?.let {b->ConfirmDialog("删除预算","只删除该额度与提醒记录，不删除账目。",{remove=null},{vm.run("预算已删除") {removeBudget(b.id)}})}
}

@Composable internal fun LedgerManage(ui:LedgerUi,vm:LedgerViewModel,exportCsv:()->Unit,importCsv:()->Unit,exportBackup:()->Unit,restoreBackup:()->Unit) {
    var manager by remember {mutableStateOf<String?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,4.dp,18.dp,32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
            LedgerSelectRow("账本管理",ui.data.ledgers.size.toString()+" 个账本") {manager="ledgers"}
            LedgerSelectRow("收支分类","全账本共用") {manager="categories"}
            LedgerSelectRow("常用模板",ui.data.templates.size.toString()+" 个模板") {manager="templates"}
            LedgerSelectRow("报销申请","费用分配与分次到账") {vm.page("claims")}
        }}}
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
            LedgerSelectRow("记账草稿",ui.data.drafts.size.toString()+" 条") {vm.page("drafts")}
            LedgerSelectRow("账目回收站",ui.data.transactions.count {it.deletedAt!=null}.toString()+" 条") {vm.page("trash")}
            LedgerSelectRow("财务变更记录","校准、更正与删除") {vm.page("changes")}
        }}}
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
            LedgerSelectRow("导入 CSV 账单","匹配字段、预览后确认") {importCsv()}
            LedgerSelectRow("导入批次","查看结果与撤销") {vm.page("imports")}
            LedgerSelectRow("导出 CSV 明细","自选日期、类型与字段") {exportCsv()}
            LedgerSelectRow("备份账本与附件","本地完整财务备份") {exportBackup()}
            LedgerSelectRow("恢复账本备份","校验并预览后追加") {restoreBackup()}
        }}}
    }
    when(manager) {"ledgers"->LedgerBookManager(ui,vm) {manager=null};"categories"->LedgerCategoryManager(ui,vm) {manager=null};"templates"->LedgerTemplateManager(ui,vm) {manager=null}}
}
@Composable private fun LedgerBookManager(ui:LedgerUi,vm:LedgerViewModel,dismiss:()->Unit) {
    var id by remember {mutableStateOf<String?>(null)};var name by remember {mutableStateOf("")};var default by remember {mutableStateOf(false)};var archive by remember {mutableStateOf(false)}
    GlassSheet("账本管理",dismiss) {
        ui.data.ledgers.forEach {l->TextButton(onClick={id=l.id;name=l.name;default=l.isDefault;archive=l.archivedAt!=null}) {Text(l.name+(if(l.isDefault) " · 默认" else "")+(if(l.archivedAt!=null) " · 已归档" else ""))}}
        HorizontalDivider();Text(if(id==null) "新增账本" else "编辑账本",style=MaterialTheme.typography.titleMedium)
        LedgerField(name,{name=it},"账本名称")
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(default,{default=it});Text("设为默认账本")}
        if(id!=null) Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(archive,{archive=it});Text("归档，保留所有历史")}
        Button(enabled=!ui.busy,onClick={vm.run("账本已保存",onSuccess={id=null;name="";default=false;archive=false}) {saveLedger(name,id,default,archive)}},modifier=Modifier.fillMaxWidth()) {Text("保存账本")}
        if(id!=null) TextButton(onClick={id=null;name="";default=false;archive=false}) {Text("改为新增")}
    }
}
@Composable private fun LedgerCategoryManager(ui:LedgerUi,vm:LedgerViewModel,dismiss:()->Unit) {
    var id by remember {mutableStateOf<String?>(null)};var name by remember {mutableStateOf("")};var direction by remember {mutableStateOf("expense")};var parent by remember {mutableStateOf("")};var active by remember {mutableStateOf(true)};var choose by remember {mutableStateOf(false)}
    GlassSheet("收支分类",dismiss) {
        ui.data.categories.forEach {c->TextButton(onClick={id=c.id;name=c.name;direction=c.direction;parent=c.parentId.orEmpty();active=c.active}) {Text(ledgerType(c.direction)+" · "+ui.data.categoryName(c.id)+(if(!c.active) " · 已停用" else ""))}}
        HorizontalDivider();Text(if(id==null) "新增分类" else "编辑分类",style=MaterialTheme.typography.titleMedium)
        LedgerField(name,{name=it},"分类名称");FlowChoices(listOf("expense" to "支出","income" to "收入"),direction) {direction=it;parent=""}
        LedgerSelectRow("父级",if(parent.isBlank()) "一级分类" else ui.data.categoryName(parent)) {choose=true}
        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(active,{active=it});Text("在新增账目中显示")}
        Text("最多两层，账目记入叶子分类。停用保留历史；保护分类不能删除。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
        Button(enabled=!ui.busy,onClick={vm.run("分类已保存",onSuccess={id=null;name="";parent="";active=true}) {saveCategory(name,direction,parent.ifBlank {null},id,active)}},modifier=Modifier.fillMaxWidth()) {Text("保存分类")}
        if(id!=null) TextButton(onClick={id=null;name="";parent="";active=true}) {Text("改为新增")}
    }
    if(choose) LedgerPicker("选择父级",listOf("" to "一级分类")+ui.data.categories.filter {it.parentId==null&&it.direction==direction&&it.id!=id&&!it.protected}.map {it.id to it.name},parent,{choose=false}) {parent=it;choose=false}
}
@Composable private fun LedgerTemplateManager(ui:LedgerUi,vm:LedgerViewModel,dismiss:()->Unit) {
    var remove by remember {mutableStateOf<TransactionTemplate?>(null)}
    GlassSheet("常用模板",dismiss) {
        Text("在账目详情中保存模板；使用模板后仍需确认金额和日期，不会自动记账。",color=LocalGlass.current.muted)
        if(ui.data.templates.isEmpty()) Text("暂无模板")
        ui.data.templates.forEach {t->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {TextButton(onClick={dismiss();vm.begin(template=t)},modifier=Modifier.weight(1f)) {Text(t.name)};TextButton(onClick={remove=t}) {Text("删除")}}}
    }
    remove?.let {t->ConfirmDialog("删除模板",t.name,{remove=null},{vm.run("模板已删除") {removeTemplate(t.id)}})}
}

@Composable internal fun LedgerDrafts(ui:LedgerUi,vm:LedgerViewModel) {
    var remove by remember {mutableStateOf<LedgerDraft?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(ui.data.drafts.isEmpty()) item {LedgerEmpty("暂无记账草稿","未完成的输入会暂存在本机，不计入余额或统计。")}
        items(ui.data.drafts,key={it.id}) {d->val f=runCatching {LedgerForm.parse(d.payload)}.getOrNull();GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {
            Text((if(f?.kind=="claim") "报销申请" else f?.kind?.let(::ledgerType) ?: "账目")+" · "+(if(f?.kind=="claim") f.text("name") else f?.text("subject").orEmpty()).ifBlank {"未命名草稿"})
            if(f?.kind=="claim") Text("未提交的申请与附件",color=LocalGlass.current.muted)
            else Text(if(ui.hideAmounts) "••••" else f?.text("amount").orEmpty().ifBlank {"金额待填"},color=LocalGlass.current.muted)
            Row {TextButton(onClick={vm.resume(d)}) {Text("继续填写")};TextButton(onClick={remove=d}) {Text("丢弃")}}
        }}}
    }
    remove?.let {d->ConfirmDialog("丢弃草稿","仅删除未提交的输入及无其他引用的附件。",{remove=null},{vm.run("草稿已丢弃") {discard(d.id)}})}
}
@Composable internal fun LedgerTrash(ui:LedgerUi,vm:LedgerViewModel) {
    var remove by remember {mutableStateOf<LedgerTransaction?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        val rows=ui.data.transactions.filter {it.deletedAt!=null}
        if(rows.isEmpty()) item {LedgerEmpty("回收站为空","删除的账目先在这里保留；恢复时会重新核对关联与额度。")}
        items(rows,key={it.id}) {t->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {
            Text(ui.data.title(t));Text(ledgerType(t.type)+" · "+ledgerMoney(ui,t.amountMinor),color=LocalGlass.current.muted)
            Row {TextButton(enabled=!ui.busy,onClick={vm.run("账目已恢复") {manage(t.id,"restore")}}) {Text("恢复")};TextButton(enabled=!ui.busy,onClick={remove=t}) {Text("永久删除")}}
        }}}
    }
    remove?.let {t->ConfirmDialog("永久删除账目","无法撤销。系统将先检查退款、报销与所有附件引用。",{remove=null},{vm.run("账目已永久删除") {permanentlyDelete(t.id)}},"永久删除")}
}
