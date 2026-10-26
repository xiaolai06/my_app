package com.example.dailylife.ui
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.Attachment
import com.example.dailylife.feature.tools.*
import com.example.dailylife.feature.ledger.LedgerSnapshot
import java.time.LocalDate

@Composable internal fun ToolEditor(ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,addFiles:()->Unit,view:(Attachment)->Unit) {
    val f=ui.editor ?: return;var leave by remember {mutableStateOf(false)};var calendar by remember {mutableStateOf<String?>(null)};var preview by remember {mutableStateOf(false)};var reuse by remember {mutableStateOf(false)}
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    fun set(k:String,v:String) {var changed=f.set(k,v);if(k=="billingStart" && f.text("billingEnd").isBlank()) changed=changed.set("billingEnd",f.text("date"));if(k=="billingEnd" && f.text("billingStart").isBlank()) changed=changed.set("billingStart",f.text("date"));vm.edit(changed)}
    BackHandler(!ui.busy) {leave=true}
    Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp)) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton(enabled=!ui.busy,onClick={preview=!preview}) {Text(if(preview) "编辑备注" else "预览备注")};TextButton(enabled=!ui.busy,onClick=addFiles) {Text("图片与文件")};TextButton(enabled=!ui.busy,onClick={reuse=true}) {Text("已有附件")}}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(if(ui.saved) "草稿已暂存本机" else "正在暂存…",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.muted);TextButton(enabled=!ui.busy,onClick=vm::keep) {Text("稍后继续")};Button(enabled=!ui.busy,onClick={keyboard?.hide();focus.clearFocus();vm.save()},modifier=Modifier.testTag("saveTool")) {Text("保存")}}
        }}
    }) {padding->Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
        Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {TextButton(enabled=!ui.busy,onClick={leave=true}) {Text("返回")};Text((if(f.revision==0L) "新建" else "编辑")+(toolKinds.find {it.first==f.kind}?.second ?: "清单模板"),style=MaterialTheme.typography.titleLarge)}
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("toolEditor"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(f.text("_project").isNotBlank()) GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {Text("保存后加入专题「"+(ui.data.entries.find {it.id==f.text("_project")}?.title ?: "原专题")+"」");TextButton(enabled=!ui.busy,onClick={set("_project","")}) {Text("取消加入专题")}}}
            ToolField("名称（必填）",f.text("title"),{set("title",it)},enabled=!ui.busy,tag="toolTitle")
            when(f.kind) {
                "checklist","checklist_template"->{FlowChoices(listOf("general" to "通用","shopping" to "购物","travel" to "行李","steps" to "办事"),f.text("type","general")) {set("type",it)};ToolDate("使用日期",f.text("date"),{calendar="date"},{set("date","")});Text("保存后可以批量添加条目、分组、重排和保存为模板。",color=LocalGlass.current.muted)}
                "anniversary"->{ToolDate("原始日期",f.text("date"),{calendar="date"});FlowChoices(listOf("birthday" to "生日","anniversary" to "周年","exam" to "考试","custom" to "自定义"),f.text("type","custom")) {set("type",it)};FlowChoices(listOf("once" to "单次","yearly" to "每年"),f.text("repeat","once")) {set("repeat",it)}
                    if(f.text("repeat")=="yearly") {SectionLabel("2 月 29 日的安排");FlowChoices(listOf("feb28" to "2月28日","mar1" to "3月1日","skip" to "跳过"),f.text("leap","feb28")) {set("leap",it)}}
                    ToolSwitch("今天页显示",f.text("showHome","true")=="true") {set("showHome",it.toString())};ToolSwitch("日历显示",f.text("showCalendar","true")=="true") {set("showCalendar",it.toString())}
                }
                "subscription","bill"->{
                    ToolField(if(f.kind=="subscription") "预计单期金额（元，0为免费）" else "预计金额（元，可留空）",f.text("amount",if(f.kind=="subscription") "0" else ""),{set("amount",it)})
                    ToolDate(if(f.kind=="subscription") "周期锚点 / 首次续费日期" else "首期截止日期",f.text("date"),{calendar="date"})
                    FlowChoices(listOf("once" to "一次性","daily" to "每天","weekly" to "每周","monthly" to "每月","yearly" to "每年"),f.text("cycle",if(f.kind=="bill") "once" else "monthly")) {set("cycle",it)}
                    if(f.kind=="bill" && f.text("cycle","once")=="once") {ToolDate("所属期开始",f.text("billingStart",f.text("date")),{calendar="billingStart"});ToolDate("所属期结束",f.text("billingEnd",f.text("date")),{calendar="billingEnd"});Text("所属期与截止日分别保存，也可在保存后调整任一期。",color=LocalGlass.current.muted)}
                    if(f.text("cycle","monthly")!="once") ToolField("周期间隔（1～120）",f.text("interval","1"),{set("interval",it)})
                    ToolDate("结束日期（可选）",f.text("end"),{calendar="end"},{set("end","")})
                    if(f.revision>0) ToolDate("新费用与规则生效日",f.text("effective",LocalDate.now().toString()),{calendar="effective"})
                    if(f.kind=="subscription") {FlowChoices(listOf("active" to "订阅中","trial" to "试用中","paused" to "暂停","cancelled" to "已取消"),f.text("status","active")) {set("status",it)}
                        if(f.text("status")=="trial") ToolDate("试用结束日期",f.text("trialEnd"),{calendar="trialEnd"})
                        FlowChoices(listOf("unknown" to "自动续费未知","yes" to "外部自动续费","no" to "手动续费"),f.text("autoRenew","unknown")) {set("autoRenew",it)};ToolField("管理链接（http/https）",f.text("url"),{set("url",it)})}
                    SectionLabel("记账建议","仅用于付款表单预填，不提前扣余额。")
                    ToolPicker("默认账本",ledger.ledgers.find {it.id==f.text("defaultLedger")}?.name ?: "使用当前默认",listOf("" to "使用当前默认")+ledger.ledgers.filter {it.archivedAt==null}.map {it.id to it.name},f.text("defaultLedger")) {set("defaultLedger",it)}
                    ToolPicker("预计付款账户",ledger.accounts.find {it.id==f.text("defaultAccount")}?.name ?: "不指定",listOf("" to "不指定")+ledger.accounts.filter {it.archivedAt==null}.map {it.id to it.name},f.text("defaultAccount")) {set("defaultAccount",it)}
                    ToolPicker("默认支出分类",ledger.categories.find {it.id==f.text("defaultCategory")}?.name ?: "未分类",listOf("" to "未分类")+ledger.categories.filter {it.active && it.direction=="expense"}.map {it.id to it.name},f.text("defaultCategory")) {set("defaultCategory",it)}
                    Text("这里只跟踪应付期间。到期不会自动扣余额；每次付款需确认金额、日期和记账方式。",color=LocalGlass.current.muted)
                }
                "item"->{ToolSuggestedField("分类",f.text("category"),ui.data.entries.filter {it.kind=="item" && it.deletedAt==null}.map {it.field("category")}) {set("category",it)};ToolSuggestedField("存放位置",f.text("location"),ui.data.entries.filter {it.kind=="item" && it.deletedAt==null}.map {it.field("location")}) {set("location",it)};ToolField("品牌",f.text("brand"),{set("brand",it)});ToolField("型号",f.text("model"),{set("model",it)});ToolField("序列号",f.text("serial"),{set("serial",it)});ToolSwitch("序列号私密显示",f.text("privateSerial","true")=="true") {set("privateSerial",it.toString())}
                    ToolDate("购买日期",f.text("date"),{calendar="date"},{set("date","")});ToolField("购买金额（元，可选）",f.text("amount"),{set("amount",it)});ToolDate("保修截止日期",f.text("warranty"),{calendar="warranty"},{set("warranty","")});FlowChoices(listOf("active" to "使用中","idle" to "闲置","sold" to "已售出","scrapped" to "已报废"),f.text("status","active")) {set("status",it)}
                    if(f.text("status")=="sold") {ToolDate("出售日期",f.text("soldDate"),{calendar="soldDate"});ToolField("出售金额（元）",f.text("soldAmount"),{set("soldAmount",it)})};Text("购买、出售、维修均不会自动记账。",color=LocalGlass.current.muted)
                }
                "loan"->{FlowChoices(listOf("out" to "借出","in" to "借入"),f.text("direction","out")) {set("direction",it)};ToolField("对方称呼（必填）",f.text("person"),{set("person",it)});ToolField("数量",f.text("quantity","1"),{set("quantity",it)});ToolField("单位",f.text("unit","件"),{set("unit",it)});ToolDate("借出 / 借入日期",f.text("date"),{calendar="date"});ToolDate("预计归还日期",f.text("end"),{calendar="end"},{set("end","")})
                    ToolPicker("关联物品",ui.data.entries.find {it.id==f.text("item")}?.title ?: "不关联",listOf("" to "不关联")+ui.data.entries.filter {it.kind=="item" && it.deletedAt==null}.map {it.id to it.title},f.text("item")) {set("item",it)}
                }
                "project"->{ToolField("图标名称（可选）",f.text("icon"),{set("icon",it)});ToolDate("开始日期",f.text("date"),{calendar="date"},{set("date","")});ToolDate("结束日期",f.text("end"),{calendar="end"},{set("end","")});Text("保存后关联已有内容；移除专题关系不会删除原内容。",color=LocalGlass.current.muted)}
            }
            SectionLabel("备注与资料")
            if(f.kind !in listOf("project","checklist_template")) {
                ToolSwitch("启用本机提醒",f.text("reminderOn")=="true") {set("reminderOn",it.toString())}
                if(f.text("reminderOn")=="true") {ToolField("提前自然日（0～366）",f.text("reminderDays","0"),{set("reminderDays",it)});ToolField("提醒时刻（如 09:00）",f.text("reminderTime","09:00"),{set("reminderTime",it)});Text("非精确系统提醒，可能受系统省电延迟。保存后可在详情检查通知状态。",color=LocalGlass.current.muted)}
            }
            if(preview) MarkdownBody(f.text("note").ifBlank {"暂无备注"}) else ToolField("备注 · 支持 Markdown",f.text("note"),{set("note",it)},single=false,enabled=!ui.busy)
            ToolSwitch("置顶",f.text("pinned")=="true") {set("pinned",it.toString())}
            val attachments=ui.records.attachmentsFor("tool_draft",f.id)
            attachments.forEach {a->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {Text(a.displayName);Row {TextButton(onClick={view(a)}) {Text("预览")};TextButton(enabled=!ui.busy,onClick={vm.run("附件已移除") {detach(f.id,a.id)}}) {Text("移除")}}}}}
            if(attachments.isEmpty()) Text("照片、票据和文件可从底部添加，保存在本机。",color=LocalGlass.current.muted)
        }
    }}
    calendar?.let {key->CalendarDialog(f.text(key),{calendar=null}) {set(key,it);calendar=null}}
    if(leave) GlassSheet("保留当前内容？",{leave=false}) {Text("草稿留在本机，稍后可以从功能箱继续。")
        Button(enabled=!ui.busy,onClick={vm.keep();leave=false},modifier=Modifier.fillMaxWidth()) {Text("保留草稿并返回")};TextButton(enabled=!ui.busy,onClick={vm.discardForm();leave=false}) {Text("丢弃草稿")}}
    if(reuse) GlassSheet("复用已有附件",{reuse=false}) {ui.records.attachments.forEach {a->TextButton(onClick={vm.run("已添加") {attach(f.id,a.id)};reuse=false}) {Text(a.displayName)}};if(ui.records.attachments.isEmpty()) Text("还没有已导入原件")}
}
@Composable internal fun ToolField(label:String,value:String,change:(String)->Unit,single:Boolean=true,enabled:Boolean=true,tag:String="") {val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current;OutlinedTextField(value,change,label={Text(label)},singleLine=single,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag(tag),minLines=if(single) 1 else 4,keyboardOptions=KeyboardOptions(imeAction=if(single) ImeAction.Done else ImeAction.Default),keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}))}
@Composable internal fun ToolSwitch(label:String,value:Boolean,change:(Boolean)->Unit) {Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f));Switch(value,change)}}
@Composable internal fun ToolDate(label:String,value:String,choose:()->Unit,clear:(()->Unit)?=null) {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {TextButton(onClick=choose,modifier=Modifier.weight(1f)) {Text(label+"："+value.ifBlank {"未设置"})};if(clear!=null && value.isNotBlank()) TextButton(onClick=clear) {Text("清除")}}}
@Composable internal fun ToolPicker(label:String,value:String,options:List<Pair<String,String>>,selected:String,choose:(String)->Unit) {var show by remember {mutableStateOf(false)};TextButton(onClick={show=true},modifier=Modifier.fillMaxWidth()) {Text("$label：$value ▾")};if(show) GlassSheet(label,{show=false}) {options.forEach {(id,text)->GlassChoice(text,id==selected,Modifier.fillMaxWidth()) {choose(id);show=false}}}}
@Composable private fun ToolSuggestedField(label:String,value:String,suggestions:List<String>,change:(String)->Unit) {Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {ToolField(label+"（可自建）",value,change);val options=suggestions.map {it.trim()}.filter {it.isNotBlank()}.distinctBy {it.lowercase(java.util.Locale.ROOT)}.sorted();if(options.isNotEmpty()) ToolPicker("已有"+label,value.ifBlank {"选择或直接输入"},options.map {it to it},value,change)}}
