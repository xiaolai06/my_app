package com.example.dailylife.ui
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.*
import com.example.dailylife.core.ToolRules
import com.example.dailylife.feature.tools.*
import com.example.dailylife.feature.ledger.LedgerSnapshot
import com.example.dailylife.feature.plans.PlansSnapshot
import java.time.*

@Composable internal fun ToolDetail(ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,plans:PlansSnapshot,open:(String,String)->Unit,view:(Attachment)->Unit) {
    val e=ui.data.entries.find {it.id==ui.detailId} ?: return;val deleted=e.deletedAt!=null
    val webLinks=LocalUriHandler.current
    var action by remember {mutableStateOf<String?>(null)};var childEdit by remember {mutableStateOf<ToolChild?>(null)};var addChild by remember {mutableStateOf(false)};var batch by remember {mutableStateOf(false)}
    var logEdit by remember {mutableStateOf<ToolLog?>(null)};var addReturn by remember {mutableStateOf(false)};var postpone by remember {mutableStateOf(false)};var privateShown by remember(e.id) {mutableStateOf(false)}
    var pay by remember {mutableStateOf<ToolPaymentPeriod?>(null)};var adjust by remember {mutableStateOf<ToolPaymentPeriod?>(null)};var allPeriods by remember {mutableStateOf(false)}
    var payChild by remember {mutableStateOf<ToolChild?>(null)}
    BackHandler {vm.detail(null)}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {TextButton(onClick={vm.detail(null)},modifier=Modifier.testTag("backToolDetail")) {Text("返回")};Text(toolKinds.find {it.first==e.kind}?.second ?: "清单模板",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);if(!deleted) TextButton(enabled=!ui.busy,onClick={vm.begin(e.kind,e.id)}) {Text("编辑")}}
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(e.title,style=MaterialTheme.typography.headlineMedium)
            Text(toolSummary(e,ui.data,ui.hideAmounts),color=LocalGlass.current.accent)
            if(e.field("reminderOn")=="true") {val reminders=plans.reminders.filter {it.sourceType=="tool" && it.sourceId==e.id};Text("系统提醒："+when {reminders.any {it.state=="blocked"}->"通知权限未开启，配置仍保留";reminders.any {it.state=="scheduled"}->"已提交非精确排程";reminders.any {it.state=="failed"}->"排程失败，稍后重新核对";else->"没有未来可排程日期"},color=LocalGlass.current.muted);TextButton(onClick={open("feature","notifications")}) {Text("检查通知设置")}}
            if(deleted) Text("已在回收站，恢复后可继续管理。",color=MaterialTheme.colorScheme.error)
            if(e.note.isNotBlank()) GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {MarkdownBody(e.note)}}
            when(e.kind) {
                "checklist","checklist_template"->{val children=ui.data.children.filter {it.entryId==e.id};if(children.isNotEmpty() && children.all {it.completedAt!=null} && e.state!="done") Text("条目已全部完成，可以确认完成整张清单。",color=LocalGlass.current.accent)
                    if(!deleted) {Row(Modifier.horizontalScroll(rememberScrollState())) {Button(onClick={addChild=true},enabled=!ui.busy) {Text("添加条目")};TextButton(onClick={batch=true}) {Text("批量添加")};TextButton(onClick={action="reset"}) {Text("重置勾选")}}
                        Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton(onClick={vm.run("已复制",onSuccess={}) {val id=copy(e.id);vm.detail(id)}}) {Text(if(e.kind=="checklist_template") "从模板创建清单" else "复制清单")};if(e.kind!="checklist_template") TextButton(onClick={vm.run("已保存为模板") {copy(e.id,true)}}) {Text("保存为模板")};TextButton(onClick={vm.run("状态已更新") {manage(e.id,"complete")}}) {Text(if(e.state=="done") "重新打开" else "确认完成整单")}}
                    }
                    if(children.isEmpty()) ToolEmpty("清单还是空的","逐项添加，或按每行一项批量输入。")
                    children.groupBy {it.groupName}.forEach {(group,list)->var expanded by rememberSaveable(e.id,group) {mutableStateOf(true)};if(group.isNotBlank()) TextButton(onClick={expanded=!expanded}) {Text(group+(if(expanded) " ▴" else " ▾"))};if(expanded) list.forEach {c->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(c.completedAt!=null,{vm.run("已更新") {childAction(c.id,"toggle")}},enabled=!deleted && !ui.busy,modifier=Modifier.testTag("toolCheck:"+c.id));Column(Modifier.weight(1f)) {Text(c.title,style=MaterialTheme.typography.titleMedium);if(c.quantityMilli!=null) Text(ToolRules.number(c.quantityMilli)+" "+c.unit,color=LocalGlass.current.muted);if(c.note.isNotBlank()) Text(c.note,color=LocalGlass.current.muted)}}
                        if(!deleted) Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton(onClick={childEdit=c}) {Text("编辑")};TextButton(onClick={vm.run {childAction(c.id,"up")}}) {Text("上移")};TextButton(onClick={vm.run {childAction(c.id,"down")}}) {Text("下移")};TextButton(onClick={action="child:"+c.id}) {Text("删除")}}
                        if(e.kind!="checklist_template") ToolChildExpenseActions(c,ui,ledger,deleted,open,{payChild=c},{action="childDetach:"+c.id})
                    }}}}
                }
                "anniversary"->{Text("原始日期："+e.field("date"));Text("下一次："+(ui.data.nextDate(e)?.toString() ?: "暂无"));Text(if(e.field("repeat")=="yearly") "每年重复，保留原始年份。" else "单次日期，过去的日期仍会保留。",color=LocalGlass.current.muted)}
                "item"->{listOf("category" to "分类","location" to "位置","brand" to "品牌","model" to "型号","date" to "购买日期","amount" to "购买金额（元）","warranty" to "保修截止日","soldDate" to "出售日期","soldAmount" to "出售金额（元）").forEach {(key,label)->e.field(key).takeIf {it.isNotBlank()}?.let {Text("$label："+if(ui.hideAmounts && key in listOf("amount","soldAmount")) "••••" else it)}}
                    if(e.field("serial").isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically) {Text("序列号："+if(e.field("privateSerial","true")=="true" && !privateShown) "••••••" else e.field("serial"),Modifier.weight(1f));TextButton(onClick={privateShown=!privateShown}) {Text(if(privateShown) "隐藏" else "查看")}}
                    ToolItemTransactions(e,ui,vm,ledger,open)
                    SectionLabel("维修记录");if(!deleted) Button(onClick={addChild=true}) {Text("添加维修")}
                    ui.data.children.filter {it.entryId==e.id}.forEach {c->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {Text(c.date.orEmpty()+" · "+c.title);if(c.note.isNotBlank()) Text(c.note);if(!deleted) Row {TextButton(onClick={childEdit=c}) {Text("编辑")};TextButton(onClick={action="child:"+c.id}) {Text("删除")}};ToolChildExpenseActions(c,ui,ledger,deleted,open,{payChild=c},{action="childDetach:"+c.id})}}}
                    val loans=ui.data.entries.filter {it.kind=="loan" && it.field("item")==e.id && it.deletedAt==null};if(loans.isNotEmpty()) SectionLabel("关联借还");loans.forEach {l->TextButton(onClick={vm.detail(l.id)}) {Text(l.title+" · 剩余 "+ToolRules.number(ui.data.remaining(l)))}}
                }
                "loan"->{Text("起始日期："+e.field("date"));Text("原数量："+e.field("quantity","1")+" "+e.field("unit","件"));if(e.field("end").isNotBlank()) Text("预计归还："+e.field("end"));e.field("item").takeIf {it.isNotBlank()}?.let {id->TextButton(onClick={vm.detail(id)}) {Text("查看关联物品")}}
                    if(!deleted && e.state!="cancelled") {Row {Button(onClick={addReturn=true},enabled=ui.data.remaining(e)>0 && !ui.busy) {Text("登记归还")};TextButton(onClick={postpone=true}) {Text("调整归还日期")}}}
                    SectionLabel("归还与延期历史");ui.data.logs.filter {it.entryId==e.id && it.kind in listOf("return","postpone")}.forEach {l->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {Text(l.date+" · "+if(l.kind=="return") "归还 "+ToolRules.number(l.quantityMilli)+" "+e.field("unit","件") else "延期");if(l.note.isNotBlank()) Text(l.note,color=LocalGlass.current.muted);if(l.kind=="return" && !deleted) Row {TextButton(onClick={logEdit=l}) {Text("编辑")};TextButton(onClick={action="return:"+l.id}) {Text("撤销本次归还")}}}}}
                    if(!deleted) TextButton(onClick={action="cancel"}) {Text(if(e.state=="cancelled") "恢复借还" else "取消借还，保留历史")}
                }
                "subscription","bill"->{
                    if(e.kind=="subscription") {Text("外部自动续费："+when(e.field("autoRenew","unknown")) {"yes"->"是";"no"->"否";else->"未知"});if(e.field("trialEnd").isNotBlank()) Text("试用结束："+e.field("trialEnd"));if(e.field("url").isNotBlank()) TextButton(onClick={webLinks.openUri(e.field("url"))}) {Text("打开原平台管理页面")};Text("在这里标记取消，不会替你在原平台退订。",color=LocalGlass.current.muted)}
                    if(!deleted) Row {TextButton(onClick={vm.run("状态已更新") {manage(e.id,"pause")}}) {Text(if(e.state=="paused") "恢复安排" else "暂停安排")};TextButton(onClick={action="cancel"}) {Text(if(e.state=="cancelled") "恢复安排" else "停止后续")}}
                    val periods=ui.data.periods.filter {it.sourceId==e.id};val month=YearMonth.now().toString();val monthly=periods.filter {it.originalKey.startsWith(month) && it.status in setOf("pending","paid")};val expected=monthly.mapNotNull {it.expectedMinor}.sum();val unknown=monthly.count {it.expectedMinor==null}
                    Text((if(unknown>0) "本月已知预计：¥" else "本月预计：¥")+toolMoney(expected,ui.hideAmounts)+(if(unknown>0) " · 另 $unknown 期金额待确认" else "（按本月期间计算）"),color=LocalGlass.current.accent)
                    val cycle=e.field("cycle","monthly");val amount=e.field("amount").toBigDecimalOrNull();if(amount!=null && cycle in listOf("monthly","yearly")) Text("月均折算约 ¥"+(if(ui.hideAmounts) "••••" else amount.divide((if(cycle=="yearly") 12 else 1).toBigDecimal().multiply(e.field("interval","1").toBigDecimal()),2,java.math.RoundingMode.HALF_UP).toString())+"，仅估算",color=LocalGlass.current.muted)
                    SectionLabel("付款期间","仅处理本期不会停止整个周期；时间到期不会自动记账。")
                    val visible=if(allPeriods) periods else periods.filter {it.status!="pending" || it.dueDate<=LocalDate.now().plusMonths(2).toString()}.take(24)
                    visible.forEach {p->val settlement=ui.data.settlements.find {it.periodId==p.id};GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(p.dueDate+" · "+when(p.status) {"paid"->"已付";"cancelled"->"已跳过";"superseded"->"规则变更已停止";else->if(p.dueDate<LocalDate.now().toString()) "待处理 · 已逾期" else "待处理"},style=MaterialTheme.typography.titleMedium)
                        Text("所属期间 ${p.periodStart} — ${p.periodEnd}",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
                        Text("预计 "+(p.expectedMinor?.let {"¥"+toolMoney(it,ui.hideAmounts)} ?: "待确认")+(settlement?.let {" · 实付 ¥"+toolMoney(it.actualMinor,ui.hideAmounts)+" / "+it.paidOn} ?: ""))
                        if(!deleted) {if(p.status=="pending") Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton(onClick={pay=p}) {Text("登记已付")};TextButton(onClick={adjust=p}) {Text("调整本期")};TextButton(onClick={action="skip:"+p.id}) {Text("跳过")}}
                            else if(settlement?.expenseId!=null) {TextButton(onClick={open("ledger",settlement.expenseId)}) {Text("查看关联账目")};TextButton(onClick={action="detach:"+p.id}) {Text("解除账目关联")}}
                            else if(p.status!="superseded") {if(settlement!=null && settlement.actualMinor>0) TextButton(onClick={pay=p}) {Text("补记或关联账目")};TextButton(onClick={action="reopen:"+p.id}) {Text("撤销本期处理")}}}
                    }}}
                    if(!allPeriods && periods.size>visible.size) TextButton(onClick={allPeriods=true}) {Text("查看全部 ${periods.size} 个期间")}
                    SectionLabel("费用规则历史");ui.data.logs.filter {it.entryId==e.id && it.kind=="payment_version"}.forEach {l->val p=org.json.JSONObject(l.payload);Text(l.date+" 起 · ¥"+(if(ui.hideAmounts) "••••" else p.optString("amount","待确认"))+" · "+p.optString("cycle"),color=LocalGlass.current.muted)}
                }
                "project"->ToolProject(e,ui,vm,ledger,plans,open)
            }
            ui.records.attachmentsFor("tool",e.id).forEach {a->TextButton(onClick={view(a)},modifier=Modifier.fillMaxWidth()) {Text("附件 · "+a.displayName)}}
            if(deleted) {Button(enabled=!ui.busy,onClick={vm.run("已恢复") {manage(e.id,"restore")}}) {Text("恢复")};TextButton(onClick={action="forever"}) {Text("永久删除")}}
            else {HorizontalDivider();Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton(onClick={vm.run {manage(e.id,"pin")}}) {Text(if(e.pinned) "取消置顶" else "置顶")};TextButton(onClick={vm.run {manage(e.id,if(e.archivedAt==null) "archive" else "unarchive")}}) {Text(if(e.archivedAt==null) "归档" else "取消归档")};TextButton(onClick={action="trash"}) {Text("移入回收站")}}}
        }
    }
    if(addChild || childEdit!=null) ToolChildSheet(e,childEdit,ui.busy,{addChild=false;childEdit=null}) {title,q,unit,group,note,date,cost->vm.run("已保存",onSuccess={addChild=false;childEdit=null}) {child(e.id,title,q,unit,group,note,childEdit?.id,date,cost)}}
    if(batch) {var value by remember {mutableStateOf("")};var group by remember {mutableStateOf("")};GlassSheet("批量添加条目",{batch=false}) {ToolField("每行一项，最多100项",value,{value=it},single=false);ToolField("这些条目的分组（可选）",group,{group=it});Button(enabled=!ui.busy,onClick={vm.run("条目已添加",onSuccess={batch=false}) {batchChildren(e.id,value,group)}}) {Text("添加这些条目")}}}
    if(addReturn || logEdit!=null) ToolReturnSheet(e,logEdit,ui.busy,{addReturn=false;logEdit=null}) {date,q,note->vm.run("归还已登记",onSuccess={addReturn=false;logEdit=null}) {returned(e.id,date,q,note,logEdit?.id)}}
    if(postpone) CalendarDialog(e.field("end"),{postpone=false}) {d->vm.run("归还日期已调整",onSuccess={postpone=false}) {postpone(e.id,d)}}
    pay?.let {p->ToolPaySheet(p,ui,vm,ledger) {pay=null}}
    payChild?.let {child->ToolChildPaySheet(child,ui,vm,ledger) {payChild=null}}
    adjust?.let {p->ToolPeriodSheet(p,ui.busy,{adjust=null}) {date,amount,start,end->vm.run("本期已更新",onSuccess={adjust=null}) {adjustPeriod(p.id,date,amount,start,end)}}}
    action?.let {a->if(a.startsWith("childDetach:")) ConfirmDialog("解除条目支出关联？","原账目、已录入的费用与日期都会保留。解除后可重新关联，避免再创建重复支出。",{action=null},{vm.run("关联已解除，原账目保留",onSuccess={action=null}) {detachChildExpense(a.substringAfter(":"))}})
        else if(a.startsWith("detach:")) GlassSheet("解除付款账目关联",{action=null}) {Text("原账目会保留，请选择本期状态。")
        Button(onClick={vm.run("已解除关联，保留已付") {detachSettlement(a.substringAfter(":"),true)};action=null}) {Text("保留已付状态")};TextButton(onClick={vm.run("已解除关联，回到待处理") {detachSettlement(a.substringAfter(":"),false)};action=null}) {Text("撤销结算回待处理")}}
        else ConfirmDialog("确认操作",when {a=="trash"->"内容进入回收站；原账目与被专题关联的原内容会保留。";a=="forever"->"永久删除不可撤销，仍有关系时会阻止。";a=="reset"->"清除当前勾选，条目本身保留。";a=="cancel"->"更改当前状态，历史日志与付款记录保留。";else->"确认处理当前条目？"},{action=null},{vm.run("已更新",onSuccess={if(a=="trash" || a=="forever") vm.detail(null)}) {when {a=="forever"->deleteForever(e.id);a=="reset"->reset(e.id);a.startsWith("child:")->childAction(a.substringAfter(":"),"delete");a.startsWith("return:")->removeLog(a.substringAfter(":"));a.startsWith("skip:")->skipPeriod(a.substringAfter(":"));a.startsWith("reopen:")->reopenPeriod(a.substringAfter(":"));else->manage(e.id,a)}}})}
}
@Composable private fun ToolChildSheet(e:ToolEntry,old:ToolChild?,busy:Boolean,dismiss:()->Unit,save:(String,String,String,String,String,String?,String)->Unit) {
    var title by remember {mutableStateOf(old?.title.orEmpty())};var q by remember {mutableStateOf(old?.quantityMilli?.let(ToolRules::number).orEmpty())};var unit by remember {mutableStateOf(old?.unit.orEmpty())};var group by remember {mutableStateOf(old?.groupName.orEmpty())};var note by remember {mutableStateOf(old?.note.orEmpty())};var date by remember {mutableStateOf(old?.date ?: if(e.kind=="item" || old==null) LocalDate.now().toString() else "")};var cost by remember {mutableStateOf(old?.costMinor?.let(ToolRules::money).orEmpty())};var picker by remember {mutableStateOf(false)}
    val hasCost=e.kind=="item" || e.field("type")=="shopping" || old?.costMinor!=null || old?.expenseId!=null
    GlassSheet(if(e.kind=="item") "维修记录" else "清单条目",dismiss) {
        ToolField(if(e.kind=="item") "维修说明（必填）" else "条目名称（必填）",title,{title=it})
        if(e.kind!="item") {ToolField("数量（可选，最多三位小数）",q,{q=it});ToolField("单位",unit,{unit=it});ToolField("分组（可选）",group,{group=it})}
        if(hasCost) {if(old?.expenseId!=null) Text("已关联支出，修改金额或日期需先解除关联。",color=LocalGlass.current.muted)
            ToolDate(if(e.kind=="item") "维修日期" else "购买日期",date,{if(old?.expenseId==null) picker=true});ToolField("费用（元，可选）",cost,{cost=it},enabled=old?.expenseId==null)}
        ToolField(if(e.kind=="item") "服务商与备注" else "备注",note,{note=it},single=false)
        Button(enabled=!busy,onClick={save(title,q,unit,group,note,if(hasCost) date.ifBlank {null} else old?.date,cost)}) {Text("保存条目")}
    };if(picker) CalendarDialog(date,{picker=false}) {date=it;picker=false}
}
@Composable private fun ToolChildExpenseActions(child:ToolChild,ui:ToolsUi,ledger:LedgerSnapshot,deleted:Boolean,open:(String,String)->Unit,pay:()->Unit,detach:()->Unit) {
    child.costMinor?.let {Text("费用 ¥"+toolMoney(it,ui.hideAmounts)+(child.date?.let {date->" · "+date} ?: ""),color=LocalGlass.current.muted)}
    if(child.expenseId!=null) {ledger.transactions.find {it.id==child.expenseId}?.let {Text("账目付款日："+it.occurredOn,color=LocalGlass.current.muted)};Row(Modifier.horizontalScroll(rememberScrollState())) {
        TextButton(onClick={open("ledger",child.expenseId)}) {Text("查看关联支出")}
        if(!deleted) TextButton(onClick=detach,enabled=!ui.busy) {Text("解除支出关联")}
    }} else if(!deleted) TextButton(onClick=pay,enabled=!ui.busy,modifier=Modifier.testTag("childExpense:"+child.id)) {Text("记账 / 关联支出")}
}
@Composable private fun ToolChildPaySheet(child:ToolChild,ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,dismiss:()->Unit) {
    var mode by remember(child.id) {mutableStateOf("new")};var amount by remember(child.id) {mutableStateOf(child.costMinor?.let(ToolRules::money).orEmpty())};var date by remember(child.id) {mutableStateOf(child.date ?: LocalDate.now().toString())};var picker by remember {mutableStateOf(false)}
    var ledgerId by remember {mutableStateOf(ledger.defaultLedger()?.id.orEmpty())};var account by remember {mutableStateOf("")};var category by remember {mutableStateOf("")};var existing by remember {mutableStateOf("")}
    val expenseId=ui.data.children.find {it.id==child.id}?.expenseId
    LaunchedEffect(expenseId) {if(expenseId!=null) dismiss()}
    GlassSheet("为条目记账",dismiss) {
        Text(child.title,style=MaterialTheme.typography.titleMedium)
        Text("实付费用将写回条目，原日期保留；付款日期以关联账目为准。关联已有支出不会再记一笔。",color=LocalGlass.current.muted)
        FlowChoices(listOf("new" to "新建支出","existing" to "关联已有支出"),mode) {mode=it}
        ToolField("实际支出金额（元）",amount,{amount=it},tag="childExpenseAmount");ToolDate("付款日期",date,{picker=true})
        ToolPicker("账本",ledger.ledgers.find {it.id==ledgerId}?.name ?: "请选择",ledger.ledgers.filter {it.archivedAt==null}.map {it.id to it.name},ledgerId) {ledgerId=it;existing=""}
        if(mode=="new") {
            ToolPicker("账户",ledger.accounts.find {it.id==account}?.name ?: "不指定",listOf("" to "不指定")+ledger.accounts.filter {it.archivedAt==null}.map {it.id to it.name},account) {account=it}
            ToolPicker("支出分类",ledger.categories.find {it.id==category}?.name ?: "未分类",listOf("" to "未分类")+ledger.categories.filter {it.direction=="expense" && it.active}.map {it.id to it.name},category) {category=it}
        } else {
            val expenses=ledger.transactions.filter {it.type=="expense" && it.deletedAt==null && it.ledgerId==ledgerId && ui.data.children.none {c->c.expenseId==it.id} && ui.data.settlements.none {s->s.expenseId==it.id}}
            ToolPicker("已有支出",ledger.transactions.find {it.id==existing}?.subject?.ifBlank {"已选支出"} ?: "请选择",expenses.map {it.id to (it.occurredOn+" · ¥"+toolMoney(it.amountMinor,ui.hideAmounts)+" · "+it.subject)},existing) {id->existing=id;expenses.find {it.id==id}?.let {amount=ToolRules.money(it.amountMinor);date=it.occurredOn}}
        }
        Button(enabled=!ui.busy && (mode!="existing" || existing.isNotBlank()),onClick={vm.settleChildExpense(child.id,amount,date,existing.takeIf {mode=="existing"},ledgerId,account.ifBlank {null},category.ifBlank {null})},modifier=Modifier.fillMaxWidth().testTag("saveChildExpense")) {Text("确认支出并关联")}
    };if(picker) CalendarDialog(date,{picker=false}) {date=it;picker=false}
}
@Composable private fun ToolReturnSheet(e:ToolEntry,old:ToolLog?,busy:Boolean,dismiss:()->Unit,save:(String,String,String)->Unit) {var date by remember {mutableStateOf(old?.date ?: LocalDate.now().toString())};var q by remember {mutableStateOf(old?.quantityMilli?.let(ToolRules::number) ?: "1")};var note by remember {mutableStateOf(old?.note.orEmpty())};var picker by remember {mutableStateOf(false)};GlassSheet("登记归还",dismiss) {ToolDate("归还日期",date,{picker=true});ToolField("本次归还数量（"+e.field("unit","件")+"）",q,{q=it});ToolField("备注",note,{note=it});Button(enabled=!busy,onClick={save(date,q,note)}) {Text("保存归还记录")}};if(picker) CalendarDialog(date,{picker=false}) {date=it;picker=false}}
@Composable private fun ToolPaySheet(p:ToolPaymentPeriod,ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,dismiss:()->Unit) {
    val source=ui.data.entries.first {it.id==p.sourceId};val previous=ui.data.settlements.find {it.periodId==p.id};val supplement=p.status=="paid"
    var mode by remember(p.id) {mutableStateOf(if(supplement) "new" else "status")};var amount by remember(p.id) {mutableStateOf((previous?.actualMinor ?: p.expectedMinor)?.let(ToolRules::money) ?: "")};var date by remember(p.id) {mutableStateOf(previous?.paidOn ?: LocalDate.now().toString())};var picker by remember {mutableStateOf(false)}
    var ledgerId by remember {mutableStateOf(ledger.ledgers.find {it.id==source.field("defaultLedger") && it.archivedAt==null}?.id ?: ledger.defaultLedger()?.id.orEmpty())};var account by remember {mutableStateOf(ledger.accounts.find {it.id==source.field("defaultAccount") && it.archivedAt==null}?.id.orEmpty())};var category by remember {mutableStateOf(ledger.categories.find {it.id==source.field("defaultCategory") && it.active && it.direction=="expense"}?.id.orEmpty())};var existing by remember {mutableStateOf("")}
    val saved=ui.data.settlements.any {it.periodId==p.id && (!supplement || it.expenseId!=null)};LaunchedEffect(saved) {if(saved) dismiss()}
    GlassSheet(if(supplement) "为本期补充账目" else "登记本期已付",dismiss) {Text(if(supplement) "沿用原付款金额与日期，只补充原结算的账目引用，不新增第二次付款。" else "登记后不会重复生成第二次付款；零元请仅登记状态。",color=LocalGlass.current.muted)
        FlowChoices((if(supplement) emptyList() else listOf("status" to "仅登记状态"))+listOf("new" to "同时新建支出","existing" to "关联已有支出"),mode) {mode=it}
        ToolField("实际金额（元）",amount,{amount=it},enabled=!supplement);ToolDate("实际付款日期",date,{if(!supplement) picker=true})
        if(mode!="status") {ToolPicker("账本",ledger.ledgers.find {it.id==ledgerId}?.name ?: "请选择",ledger.ledgers.filter {it.archivedAt==null}.map {it.id to it.name},ledgerId) {ledgerId=it;existing=""}}
        if(mode=="new") {ToolPicker("账户",ledger.accounts.find {it.id==account}?.name ?: "不指定",listOf("" to "不指定")+ledger.accounts.filter {it.archivedAt==null}.map {it.id to it.name},account) {account=it};ToolPicker("支出分类",ledger.categories.find {it.id==category}?.name ?: "未分类",listOf("" to "未分类")+ledger.categories.filter {it.direction=="expense" && it.active}.map {it.id to it.name},category) {category=it}}
        if(mode=="existing") {val expenses=ledger.transactions.filter {it.type=="expense" && it.deletedAt==null && it.ledgerId==ledgerId && ui.data.settlements.none {s->s.expenseId==it.id} && ui.data.children.none {c->c.expenseId==it.id} && (!supplement || it.amountMinor==previous?.actualMinor && it.occurredOn==previous?.paidOn)};ToolPicker("已有支出",ledger.transactions.find {it.id==existing}?.subject ?: "请选择",expenses.map {it.id to (it.occurredOn+" · ¥"+toolMoney(it.amountMinor,ui.hideAmounts)+" · "+it.subject)},existing) {id->existing=id;expenses.find {it.id==id}?.let {amount=ToolRules.money(it.amountMinor);date=it.occurredOn}}}
        Button(enabled=!ui.busy && (mode!="existing" || existing.isNotBlank()),onClick={if(mode=="status") vm.run("已登记付款") {settleOnly(p.id,amount,date)} else vm.settleExpense(p.id,amount,date,if(mode=="existing") existing else null,ledgerId,account.ifBlank {null},category.ifBlank {null})},modifier=Modifier.fillMaxWidth()) {Text(if(supplement) "确认补充原付款账目" else "确认本期付款")}
    };if(picker) CalendarDialog(date,{picker=false}) {date=it;picker=false}
}
@Composable private fun ToolPeriodSheet(period:ToolPaymentPeriod,busy:Boolean,dismiss:()->Unit,save:(String,String,String,String)->Unit) {
    var amount by remember(period.id) {mutableStateOf(period.expectedMinor?.let(ToolRules::money).orEmpty())};var date by remember(period.id) {mutableStateOf(period.dueDate)};var start by remember(period.id) {mutableStateOf(period.periodStart)};var end by remember(period.id) {mutableStateOf(period.periodEnd)};var picker by remember {mutableStateOf<String?>(null)}
    GlassSheet("调整本期",dismiss) {ToolDate("所属期开始",start,{picker="start"});ToolDate("所属期结束",end,{picker="end"});ToolDate("截止日期",date,{picker="due"});ToolField("预计金额（元，可空）",amount,{amount=it});Text("仅调整本期，原周期键保持不变，刷新不会覆盖这次调整。",color=LocalGlass.current.muted);Button(enabled=!busy,onClick={save(date,amount,start,end)}) {Text("保存本期")}}
    picker?.let {key->CalendarDialog(when(key) {"start"->start;"end"->end;else->date},{picker=null}) {when(key) {"start"->start=it;"end"->end=it;else->date=it};picker=null}}
}
