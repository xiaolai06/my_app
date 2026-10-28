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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.Attachment
import com.example.dailylife.feature.tools.*
import com.example.dailylife.feature.ledger.LedgerSnapshot
import java.time.LocalDate

@Composable internal fun ToolEditor(ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,addFiles:()->Unit,view:(Attachment)->Unit,addPhotos:()->Unit=addFiles) {
    val f=ui.editor ?: return;var calendar by remember {mutableStateOf<String?>(null)};var preview by remember {mutableStateOf(false)};var reuse by remember {mutableStateOf(false)}
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    val attachments=ui.records.attachmentsFor("tool_draft",f.id)
    val photos=attachments.filter {it.mime.startsWith("image/")}
    val photoFirst=f.kind in setOf("item","outfit")
    fun set(k:String,v:String) {var changed=f.set(k,v);if(k=="billingStart" && f.text("billingEnd").isBlank()) changed=changed.set("billingEnd",f.text("date"));if(k=="billingEnd" && f.text("billingStart").isBlank()) changed=changed.set("billingStart",f.text("date"));vm.edit(changed)}
    BackHandler {if(!ui.busy) vm.leaveEditor()}
    Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp)) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {TextButton(enabled=!ui.busy,onClick={preview=!preview}) {Text(if(preview) "编辑备注" else "预览备注")};TextButton(enabled=!ui.busy,onClick=addFiles) {Text("文件")};TextButton(enabled=!ui.busy,onClick={reuse=true}) {Text("已有附件")}}
            EditorDraftStatus(ui.saved,ui.busy)
            EditorSaveActions(!ui.busy,vm::leaveEditor,{keyboard?.hide();focus.clearFocus();vm.save()},"保存","saveTool","leaveToolEditor")
        }}
    }) {padding->Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
        GlassPageHeader((if(f.revision==0L) "新建" else "编辑")+(if(f.kind=="item" && f.text("wardrobe")=="true") "衣物" else toolKinds.find {it.first==f.kind}?.second ?: "清单模板"),back=vm::leaveEditor,backTag="backToolEditor",enabled=!ui.busy)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("toolEditor"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(f.text("_project").isNotBlank()) GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {Text("保存后加入专题「"+(ui.data.entries.find {it.id==f.text("_project")}?.title ?: "原专题")+"」");TextButton(enabled=!ui.busy,onClick={set("_project","")}) {Text("取消加入专题")}}}
            ToolField("名称（必填）",f.text("title"),{set("title",it)},enabled=!ui.busy,tag="toolTitle")
            if(photoFirst) ToolPhotoEditor(photos,vm.repository.records.files,f.kind=="outfit",ui.busy,addPhotos,view,
                {a,done->vm.run("封面已更新",onSuccess=done) {photoCover(f.id,a.id)}},
                {a,done->vm.run("照片已移除",onSuccess=done) {detach(f.id,a.id)}},clothing=f.text("wardrobe")=="true")
            when(f.kind) {
                "checklist","checklist_template"->GlassFormSection("清单安排","checklist") {
                    FlowChoices(listOf("general" to "通用","shopping" to "购物","travel" to "行李","steps" to "办事"),f.text("type","general")) {set("type",it)}
                    ToolDate("使用日期",f.text("date"),{calendar="date"},{set("date","")})
                    Text("保存后添加条目，也可以一次粘贴多行清单。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                }
                "anniversary"->{
                    GlassFormSection("日期与重复","anniversary") {
                        ToolDate("原始日期",f.text("date"),{calendar="date"})
                        GlassSelectField("纪念类型",listOf("birthday" to "生日","anniversary" to "周年","exam" to "考试","custom" to "自定义"),f.text("type","custom")) {set("type",it)}
                        FlowChoices(listOf("once" to "单次","yearly" to "每年"),f.text("repeat","once")) {set("repeat",it)}
                        if(f.text("repeat")=="yearly") GlassSelectField("闰日遇到平年时",listOf("feb28" to "2月28日","mar1" to "3月1日","skip" to "跳过"),f.text("leap","feb28")) {set("leap",it)}
                    }
                    GlassFormSection("显示位置","anniversary-display",true,false,"首页与日历") {
                        ToolSwitch("首页显示",f.text("showHome","true")=="true") {set("showHome",it.toString())}
                        ToolSwitch("日历显示",f.text("showCalendar","true")=="true") {set("showCalendar",it.toString())}
                    }
                }
                "subscription","bill"->{
                    GlassFormSection("费用与周期","payment") {
                        ToolField(if(f.kind=="subscription") "单期金额（元，0 为免费）" else "预计金额（元，可留空）",f.text("amount",if(f.kind=="subscription") "0" else ""),{set("amount",it)},keyboardType=KeyboardType.Decimal)
                        ToolDate(if(f.kind=="subscription") "首次续费日期" else "首期截止日期",f.text("date"),{calendar="date"})
                        GlassSelectField("重复周期",listOf("once" to "一次性","daily" to "每天","weekly" to "每周","monthly" to "每月","yearly" to "每年"),f.text("cycle",if(f.kind=="bill") "once" else "monthly")) {set("cycle",it)}
                        if(f.text("cycle",if(f.kind=="bill") "once" else "monthly")!="once") ToolField("每隔几个周期（1～120）",f.text("interval","1"),{set("interval",it)},keyboardType=KeyboardType.Number)
                        if(f.kind=="bill" && f.text("cycle","once")=="once") {
                            ToolDate("所属期开始",f.text("billingStart",f.text("date")),{calendar="billingStart"})
                            ToolDate("所属期结束",f.text("billingEnd",f.text("date")),{calendar="billingEnd"})
                        }
                        ToolDate("结束日期（可选）",f.text("end"),{calendar="end"},{set("end","")})
                        if(f.revision>0) ToolDate("新规则生效日",f.text("effective",LocalDate.now().toString()),{calendar="effective"})
                    }
                    if(f.kind=="subscription") GlassFormSection("订阅状态","subscription") {
                        GlassSelectField("当前状态",listOf("active" to "订阅中","trial" to "试用中","paused" to "暂停","cancelled" to "已取消"),f.text("status","active")) {set("status",it)}
                        if(f.text("status")=="trial") ToolDate("试用结束日期",f.text("trialEnd"),{calendar="trialEnd"})
                        GlassSelectField("原平台自动续费",listOf("unknown" to "尚不确定","yes" to "已开启","no" to "手动续费"),f.text("autoRenew","unknown")) {set("autoRenew",it)}
                        ToolField("管理链接（选填）",f.text("url"),{set("url",it)},keyboardType=KeyboardType.Uri)
                    }
                    GlassFormSection("付款时的默认信息","payment-defaults",true,false,"账本、账户与分类，可选") {
                        ToolPicker("默认账本",ledger.ledgers.find {it.id==f.text("defaultLedger")}?.name ?: "使用当前默认",listOf("" to "使用当前默认")+ledger.ledgers.filter {it.archivedAt==null}.map {it.id to it.name},f.text("defaultLedger")) {set("defaultLedger",it)}
                        ToolPicker("付款账户",ledger.accounts.find {it.id==f.text("defaultAccount")}?.name ?: "不指定",listOf("" to "不指定")+ledger.accounts.filter {it.archivedAt==null}.map {it.id to it.name},f.text("defaultAccount")) {set("defaultAccount",it)}
                        ToolPicker("支出分类",ledger.categories.find {it.id==f.text("defaultCategory")}?.name ?: "未分类",listOf("" to "未分类")+ledger.categories.filter {it.active && it.direction=="expense"}.map {it.id to it.name},f.text("defaultCategory")) {set("defaultCategory",it)}
                        Text("仅用于付款时预填；到期不会自动扣款或生成账目。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                    }
                }
                "outfit"->GlassFormSection("搭配与穿着","outfit") {OutfitEditor(f,ui,::set,vm.repository.records.files) {calendar="date"}}
                "item"->{
                    GlassFormSection("分类与存放","item-location") {
                        ToolSuggestedField("分类",f.text("category"),ui.data.entries.filter {it.kind=="item" && it.deletedAt==null}.map {it.field("category")}) {set("category",it)}
                        ToolSuggestedField("存放位置",f.text("location"),ui.data.entries.filter {it.kind=="item" && it.deletedAt==null}.map {it.field("location")}) {set("location",it)}
                        GlassSelectField("使用状态",listOf("active" to "使用中","idle" to "闲置","sold" to "已售出","scrapped" to "已报废"),f.text("status","active")) {set("status",it)}
                        if(f.text("status")=="sold") {ToolDate("出售日期",f.text("soldDate"),{calendar="soldDate"});ToolField("出售金额（元）",f.text("soldAmount"),{set("soldAmount",it)},keyboardType=KeyboardType.Decimal)}
                    }
                    GlassFormSection("衣橱资料","clothing",true,f.text("wardrobe")=="true","衣物类别、颜色与穿着场景") {ClothingEditor(f,::set)}
                    GlassFormSection("购买、保修与养护","item-dates",true,false,"金额与日期均可选") {
                        ToolDate("购买日期",f.text("date"),{calendar="date"},{set("date","")})
                        ToolField("购买金额（元，可选）",f.text("amount"),{set("amount",it)},keyboardType=KeyboardType.Decimal)
                        ToolDate("保修截止日期",f.text("warranty"),{calendar="warranty"},{set("warranty","")})
                        ToolDate("有效期 / 到期日",f.text("expiry"),{calendar="expiry"},{set("expiry","")})
                        ToolDate("下次养护日",f.text("careDate"),{calendar="careDate"},{set("careDate","")})
                        Text("这些资料不会自动生成账目。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                    }
                    GlassFormSection("品牌与序列号","item-identity",true,false,"品牌、型号与私密序列号") {
                        ToolField("品牌",f.text("brand"),{set("brand",it)});ToolField("型号",f.text("model"),{set("model",it)})
                        ToolField("序列号",f.text("serial"),{set("serial",it)})
                        ToolSwitch("序列号私密显示",f.text("privateSerial","true")=="true") {set("privateSerial",it.toString())}
                    }
                }
                "loan"->GlassFormSection("借还信息","loan") {
                    FlowChoices(listOf("out" to "借出","in" to "借入"),f.text("direction","out")) {set("direction",it)}
                    ToolField("对方称呼（必填）",f.text("person"),{set("person",it)})
                    ToolField("数量",f.text("quantity","1"),{set("quantity",it)},keyboardType=KeyboardType.Decimal)
                    ToolField("单位",f.text("unit","件"),{set("unit",it)})
                    ToolDate("借出 / 借入日期",f.text("date"),{calendar="date"})
                    ToolDate("预计归还日期",f.text("end"),{calendar="end"},{set("end","")})
                    ToolPicker("关联物品",ui.data.entries.find {it.id==f.text("item")}?.title ?: "不关联",listOf("" to "不关联")+ui.data.entries.filter {it.kind=="item" && it.deletedAt==null}.map {it.id to it.title},f.text("item")) {set("item",it)}
                }
                "project"->GlassFormSection("专题安排","project") {
                    ToolField("图标名称（可选）",f.text("icon"),{set("icon",it)})
                    ToolDate("开始日期",f.text("date"),{calendar="date"},{set("date","")})
                    ToolDate("结束日期",f.text("end"),{calendar="end"},{set("end","")})
                    Text("保存后关联已有内容；移除专题关系会保留原内容。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                }
            }
            if(f.kind !in listOf("project","checklist_template")) GlassFormSection("提醒","tool-reminder") {
                ToolSwitch("启用本机提醒",f.text("reminderOn")=="true") {set("reminderOn",it.toString())}
                if(f.text("reminderOn")=="true") {
                    ToolField("提前几天（0～366）",f.text("reminderDays","0"),{set("reminderDays",it)},keyboardType=KeyboardType.Number)
                    PlanTimeRow("提醒时间",f.text("reminderTime","09:00"),{set("reminderTime",it)})
                    Text("使用普通系统通知。物品按保修、有效期和养护日期提醒；权限不足时会明确显示状态。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                }
            }
            GlassFormSection("备注与整理","tool-note",true,preview || f.text("note").isNotBlank(),"备注与置顶，可选") {
                if(preview) MarkdownBody(f.text("note").ifBlank {"暂无备注"}) else ToolField("备注 · 支持 Markdown",f.text("note"),{set("note",it)},single=false,enabled=!ui.busy)
                ToolSwitch("置顶",f.text("pinned")=="true") {set("pinned",it.toString())}
            }
            GlassFormSection("照片与附件","tool-files",true,!photoFirst && photos.isNotEmpty(),"照片、票据与文件") {
                if(!photoFirst) ToolPhotoEditor(photos,vm.repository.records.files,false,ui.busy,addPhotos,view,
                    {a,done->vm.run("封面已更新",onSuccess=done) {photoCover(f.id,a.id)}},
                    {a,done->vm.run("照片已移除",onSuccess=done) {detach(f.id,a.id)}})
                AttachmentList(attachments.filterNot {it.mime.startsWith("image/")},vm.repository.records.files,view,
                    onRemove=if(ui.busy) null else ({a->vm.run("附件已移除") {detach(f.id,a.id)}}))
                TextButton(enabled=!ui.busy,onClick=addFiles) {Text("添加文件")}
            }
        }
    }}
    calendar?.let {key->CalendarDialog(f.text(key),{calendar=null}) {set(key,it);calendar=null}}
    if(reuse) GlassSelectionSheet("复用已有附件",
        ui.records.attachments.filter {a->ui.records.attachmentsFor("tool_draft",f.id).none {it.id==a.id}}.map {it.id to it.displayName},null,{reuse=false}) {id->
        vm.run("已添加",onSuccess={reuse=false}) {attach(f.id,id)}
    }
}
@Composable internal fun ToolField(label:String,value:String,change:(String)->Unit,single:Boolean=true,enabled:Boolean=true,tag:String="",keyboardType:KeyboardType=KeyboardType.Text) {val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current;GlassTextField(value,change,label={Text(label)},singleLine=single,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag(tag),minLines=if(single) 1 else 4,keyboardOptions=KeyboardOptions(keyboardType=keyboardType,imeAction=if(single) ImeAction.Done else ImeAction.Default),keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}))}
@Composable internal fun ToolSwitch(label:String,value:Boolean,change:(Boolean)->Unit) {SettingRow(label,"",value,change)}
@Composable internal fun ToolDate(label:String,value:String,choose:()->Unit,clear:(()->Unit)?=null) {DateRow(label,value,choose,clear)}
@Composable internal fun ToolPicker(label:String,value:String,options:List<Pair<String,String>>,selected:String,choose:(String)->Unit) {
    GlassSelectField(label,options,selected,fallback=value,choose=choose)
}

@Composable private fun ToolSuggestedField(label:String,value:String,suggestions:List<String>,change:(String)->Unit) {
    var show by remember {mutableStateOf(false)}
    val options=remember(suggestions) {suggestions.map {it.trim()}.filter {it.isNotBlank()}.distinctBy {it.lowercase(java.util.Locale.ROOT)}.sorted()}
    GlassTextField(value,change,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth(),
        trailingIcon=if(options.isEmpty()) null else ({IconButton(onClick={show=true}) {Icon(Icons.Outlined.KeyboardArrowDown,"选择已有"+label)}}))
    if(show) GlassSelectionSheet("选择"+label,options.map {it to it},value,{show=false}) {change(it);show=false}
}
