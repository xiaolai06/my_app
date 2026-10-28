package com.example.dailylife.ui
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.ToolRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.tools.*
import com.example.dailylife.feature.ledger.LedgerSnapshot
import com.example.dailylife.feature.plans.PlansSnapshot
import java.time.*
import java.time.temporal.ChronoUnit

private data class ToolFeature(val key:String,val title:String,val description:String,val group:String,val icon:ImageVector)
private val features=listOf(
    ToolFeature("checklist","生活清单","购物、出行、办事步骤","生活管理",Icons.Outlined.List),
    ToolFeature("anniversary","纪念日","重要日期与倒数日","生活管理",Icons.Outlined.Face),
    ToolFeature("subscription","订阅","会员续费与试用到期","生活管理",Icons.Outlined.Refresh),
    ToolFeature("bill","缴费","待缴费用与支付记录","生活管理",Icons.Outlined.ShoppingCart),
    ToolFeature("wardrobe","衣橱","单品、颜色与收藏","物品资料",Icons.Outlined.AccountBox),
    ToolFeature("outfit","穿搭","搭配灵感与穿着记录","物品资料",Icons.Outlined.Star),
    ToolFeature("item","物品","位置、保修与维修档案","物品资料",Icons.Outlined.Home),
    ToolFeature("loan","借还","借出、借入与分次归还","物品资料",Icons.Outlined.Refresh),
    ToolFeature("project","专题","把有关的内容放在一起","目标效率",Icons.Outlined.List),
    ToolFeature("habit","习惯","打开计划中的习惯打卡","目标效率",Icons.Outlined.CheckCircle),
    ToolFeature("focus","专注","打开计划中的专注计时","目标效率",Icons.Outlined.DateRange),
    ToolFeature("goal","目标","打开计划中的目标进度","目标效率",Icons.Outlined.Star),
    ToolFeature("card","信息卡","打开记录中的信息卡","物品资料",Icons.Outlined.AccountBox),
    ToolFeature("calculator","计算器","四则、括号与百分数","计算工具",Icons.Outlined.Build),
    ToolFeature("dates","日期计算","日期间隔与自然日加减","计算工具",Icons.Outlined.DateRange),
    ToolFeature("aa","AA 等分","精确到分，多退少补","计算工具",Icons.Outlined.Person))

@Composable fun ToolsScreen(ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,plans:PlansSnapshot,
    openLinked:(String,String)->Unit,onAddFiles:()->Unit,viewAttachment:(Attachment)->Unit,onBackup:()->Unit,onRestore:()->Unit,nickname:String="",recoverable:Int=0,onAddPhotos:()->Unit=onAddFiles,
    unfinished:List<UnfinishedEditSummary> = emptyList(),continueEditing:(UnfinishedEditSummary)->Unit={openLinked("feature","recovery")}) {
    val pageStates=rememberSaveableStateHolder()
    if(ui.editor!=null) {ToolEditor(ui,vm,ledger,onAddFiles,viewAttachment,onAddPhotos);ToolError(ui,vm);return}
    if(ui.detailId!=null) {ToolDetail(ui,vm,ledger,plans,openLinked,viewAttachment);ToolError(ui,vm);return}
    BackHandler(ui.page!="home") {vm.page("home")}
    Column(Modifier.fillMaxSize().then(if(ui.page!="home") Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)) {
        if(ui.page!="home") GlassPageHeader(features.find {it.key==ui.page}?.title ?: when(ui.page) {"trash"->"回收站";"archive"->"已归档";else->"我的"},back={vm.page("home")})
        GlassPageTransition(ui.page,modifier=Modifier.weight(1f).testTag("toolsContent"),label="tool page") {page->
            pageStates.SaveableStateProvider(page) {when(page) {
                "home"->ToolHome(ui,vm,openLinked,onBackup,onRestore,nickname,recoverable,unfinished,continueEditing)
                "wardrobe","outfit"->WardrobeScreen(ui.copy(page=page),vm)
                "calculator","dates","aa"->ToolCalculator(page,ui,vm,openLinked)
                else->ToolsList(page,ui,vm)
            }}
        }
        if(ui.page in toolKinds.map {it.first}+"wardrobe") GlassFloatingActionRow(
            if(ui.page=="wardrobe") "添加衣物" else "新建"+(toolKinds.find {it.first==ui.page}?.second ?: "内容"),"addTool",enabled=!ui.busy) {
            if(ui.page=="wardrobe") vm.beginClothing() else vm.begin(ui.page)
        }
    }
    ui.backupPreview?.let {p->ConfirmDialog("恢复工具备份","将检查并追加 ${p.count} 项内容和 ${p.files} 份原件。已有编号保留本机版本。原账目不在此备份内；无效付款引用改为仅登记状态，无法核对的条目支出关联会解除，费用与维修日期保留。",vm::dismissRestore,vm::restore,"追加恢复")}
    ToolError(ui,vm)
}
@Composable private fun ToolError(ui:ToolsUi,vm:ToolsViewModel) {ui.error?.let {error->GlassAlertDialog(onDismissRequest=vm::clearError,title={Text("操作未完成")},text={Text(error)},confirmButton={TextButton(onClick=vm::clearError) {Text("知道了")}},containerColor=LocalGlass.current.sheet)}}
@Composable private fun ToolHome(ui:ToolsUi,vm:ToolsViewModel,open:(String,String)->Unit,backup:()->Unit,restore:()->Unit,nickname:String,recoverable:Int,
    unfinished:List<UnfinishedEditSummary>,continueEditing:(UnfinishedEditSummary)->Unit) {
    fun launch(f:ToolFeature) {if(f.key in listOf("habit","focus","goal","card")) open("feature",f.key) else vm.page(f.key)}
    PersonalCenter(nickname,recoverable,{open("feature",it)},unfinished=unfinished,continueEditing=continueEditing) {
        features.groupBy {it.group}.forEach {(group,list)->
            item {SectionLabel(group)}
            item(key=group) {PersonalGroup {
                list.forEachIndexed {index,f->
                    PersonalRow(f.title,f.icon,"toolFeature:"+f.key) {launch(f)}
                    if(index<list.lastIndex) PersonalDivider()
                }
            }}
        }
        item {SectionLabel("资料管理")}
        item {PersonalGroup {
            PersonalRow("已归档",Icons.Outlined.List) {vm.page("archive")}
            PersonalDivider()
            PersonalRow("工具回收站",Icons.Outlined.Delete) {vm.page("trash")}
            PersonalDivider()
            PersonalRow("备份工具与附件",Icons.Outlined.AccountBox) {backup()}
            PersonalDivider()
            PersonalRow("恢复工具备份",Icons.Outlined.Refresh) {restore()}
        }}
    }
}
@Composable internal fun ToolEmpty(title:String,body:String) {GlassEmptyState(title,body,Modifier.padding(vertical=12.dp))}
@Composable private fun ToolsList(page:String,ui:ToolsUi,vm:ToolsViewModel) {
    val grid=ui.layouts[page]=="grid" && page !in setOf("trash","archive")
    var query by rememberSaveable(page) {mutableStateOf("")};var status by rememberSaveable(page) {mutableStateOf("all")};var confirm by remember {mutableStateOf<String?>(null)}
    var category by rememberSaveable(page) {mutableStateOf("all")};var location by rememberSaveable(page) {mutableStateOf("all")};var direction by rememberSaveable(page) {mutableStateOf("all")};var itemFilters by rememberSaveable(page) {mutableStateOf(false)}
    val base=ui.data.entries.filter {when(page) {"trash"->it.deletedAt!=null;"archive"->it.deletedAt==null && it.archivedAt!=null;else->(it.kind==page || page=="checklist" && it.kind=="checklist_template") && it.deletedAt==null && it.archivedAt==null}}
    var entries=base.filter {e->toolSearchText(e,ui.data).contains(query.trim(),true)}
        .filter {toolMatchesFilter(it,ui.data,status,page)}
        .filter {e->page!="item" || (category=="all"||e.field("category")==category.removePrefix("value:")) && (location=="all"||e.field("location")==location.removePrefix("value:"))}
        .filter {page!="loan" || direction=="all" || it.field("direction","out")==direction}
    if(page=="anniversary") entries=entries.sortedBy {ui.data.nextDate(it)}
    val activeFilter=query.isNotBlank()||status!="all"||category!="all"||location!="all"||direction!="all"
    val choices=when(page) {
        "item"->listOf("all" to "全部","active" to "使用中","idle" to "闲置","sold" to "已售出","scrapped" to "已报废")
        "loan"->listOf("all" to "全部","unreturned" to "未归还","partial" to "部分归还","returned" to "全部归还","cancelled" to "已取消")
        "subscription"->listOf("all" to "全部","active" to "活跃","upcoming" to "近期续费","trial" to "试用中","trialDue" to "试用到期","paused" to "暂停","cancelled" to "已取消")
        "bill"->listOf("all" to "全部","active" to "跟踪中","paused" to "已停止","cancelled" to "已取消")
        else->listOf("all" to "全部","active" to "进行中","done" to "已完成")+(if(page=="checklist") listOf("templates" to "模板") else emptyList())
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns=if(grid && maxWidth>=360.dp && LocalDensity.current.fontScale<=1.3f) 2 else 1
    LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().testTag("toolsList"),horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(18.dp,8.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) {GlassTextField(query,{query=it},placeholder={Text(when(page) {"item"->"搜索名称、型号、品牌或位置";"loan"->"搜索物品或对方称呼";"subscription"->"搜索服务名称或备注";else->"搜索名称、备注和资料"})},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("toolListSearch"))}
        if(page !in listOf("trash","archive")) item(span={GridItemSpan(maxLineSpan)}) {LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items(choices) {(id,label)->GlassChoice(label,status==id,Modifier.testTag("toolFilter:"+page+":"+id)) {status=id}}}}
        if(page=="item") item(span={GridItemSpan(maxLineSpan)}) {
            TextButton(onClick={itemFilters=!itemFilters},modifier=Modifier.testTag("toolItemFilters")) {Text((if(itemFilters) "收起" else "筛选")+"分类与位置"+(if(category!="all"||location!="all") " · 已设置" else ""))}
            if(itemFilters) GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                ToolListSelector("分类",if(category=="all") "全部分类" else category.removePrefix("value:").ifBlank {"未分类"},listOf("all" to "全部分类")+base.map {it.field("category")}.distinct().sorted().map {"value:"+it to it.ifBlank {"未分类"}},category) {category=it}
                ToolListSelector("位置",if(location=="all") "全部位置" else location.removePrefix("value:").ifBlank {"未填写位置"},listOf("all" to "全部位置")+base.map {it.field("location")}.distinct().sorted().map {"value:"+it to it.ifBlank {"未填写位置"}},location) {location=it}
            }}
        }
        if(page=="loan") item(span={GridItemSpan(maxLineSpan)}) {LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items(listOf("all" to "全部方向","out" to "借出","in" to "借入")) {(id,label)->GlassChoice(label,direction==id) {direction=id}}}}
        if(page=="subscription" && status in listOf("upcoming","trialDue")) item(span={GridItemSpan(maxLineSpan)}) {Text(if(status=="upcoming") "包含逾期待处理和未来 7 天的续费；已暂停、已取消不会列入。" else "包含已到期和未来 7 天结束的试用；到期不会自动视为付款。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)}
        item(span={GridItemSpan(maxLineSpan)}) {Column {if(page !in setOf("trash","archive")) ToolCollectionBar(entries.size,grid,{vm.layout(page,it)}) else Text("${entries.size} 项",color=LocalGlass.current.muted);if(activeFilter) TextButton(onClick={query="";status="all";category="all";location="all";direction="all"}) {Text("清空条件")}}}
        if(entries.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {ToolEmpty(if(activeFilter) "没有符合条件的内容" else "这里还没有内容",if(activeFilter) "调整搜索或清空条件，再看看其他内容。" else if(page in listOf("trash","archive")) "内容会保留原来的身份与状态。" else "点右下角「＋」，开始整理一件日常小事。")}
        items(entries,key={it.id}) {e->ToolCollectionItem(e,toolPhotos(e,ui.data,ui.records),vm.repository.records.files,ui.data,grid,ui.hideAmounts,{vm.detail(e.id)},modifier=if(LocalReduceMotion.current) Modifier else Modifier.animateItem(placementSpec=androidx.compose.animation.core.tween(220))) {
            if(page=="trash") Row {TextButton(onClick={vm.run("已恢复") {manage(e.id,"restore")}}) {Text("恢复")};TextButton(onClick={confirm=e.id}) {Text("永久删除")}}
        }}
    }}
    confirm?.let {id->ConfirmDialog("永久删除","此操作不能撤销。仍有账目或专题引用时会阻止删除，未使用的附件才会清理。",{confirm=null},{vm.run("已永久删除") {deleteForever(id)}},"永久删除")}
}
@Composable private fun ToolListSelector(label:String,value:String,options:List<Pair<String,String>>,selected:String,change:(String)->Unit) {
    GlassSelectField(label,options,selected,fallback=value,choose=change)
}

private fun loanFilter(e:ToolEntry,s:ToolsSnapshot):String {
    if(e.state=="cancelled") return "cancelled"
    val remaining=runCatching {s.remaining(e)}.getOrNull() ?: return "unknown"
    val returned=s.logs.filter {it.entryId==e.id&&it.kind=="return"}.sumOf {it.quantityMilli}
    return when {remaining==0L->"returned";remaining>0&&returned>0->"partial";remaining>0->"unreturned";else->"unknown"}
}
private fun toolMatchesFilter(e:ToolEntry,s:ToolsSnapshot,status:String,page:String):Boolean {
    if(status=="all") return true
    return when(page) {
        "loan"->loanFilter(e,s)==status
        "subscription"->when(status) {
            "active"->e.state in setOf("active","trial")
            "upcoming"->e.state in setOf("active","trial")&&s.periods.any {it.sourceId==e.id&&it.status=="pending"&&it.dueDate<=LocalDate.now().plusDays(7).toString()}
            "trialDue"->e.state=="trial"&&runCatching {LocalDate.parse(e.field("trialEnd"))<=LocalDate.now().plusDays(7)}.getOrDefault(false)
            else->e.state==status
        }
        else->if(status=="templates") e.kind=="checklist_template" else e.state==status
    }
}
private fun toolSearchText(e:ToolEntry,s:ToolsSnapshot):String {
    val keys=when(e.kind) {"item"->listOf("category","location","brand","model")+(if(e.field("privateSerial","true")=="true") emptyList() else listOf("serial"));"loan"->listOf("person","unit","date","end");"subscription"->listOf("date","trialEnd","url");"bill","anniversary"->listOf("date");else->emptyList()}
    val children=s.children.filter {it.entryId==e.id}.joinToString(" ") {it.title+" "+it.note}
    val linkedItem=if(e.kind=="loan") s.entries.find {it.id==e.field("item")&&it.deletedAt==null}?.title.orEmpty() else ""
    return (listOf(e.title,e.note,children,linkedItem)+keys.map {e.field(it)}).joinToString(" ")
}
internal fun toolMoney(value:Long,hidden:Boolean)=if(hidden) "••••" else ToolRules.money(value)
internal fun toolSummary(e:ToolEntry,s:ToolsSnapshot,hidden:Boolean=false):String=when(e.kind) {
    "anniversary"->s.anniversaryText(e)
    "checklist","checklist_template"->{val children=s.children.filter {it.entryId==e.id};"${children.count {it.completedAt!=null}} / ${children.size} 项完成"+(if(e.kind=="checklist_template") " · 模板" else "")}
    "loan"->when(loanFilter(e,s)) {"cancelled"->"已取消";"partial"->"部分归还";"returned"->"全部归还";"unknown"->"数量待核对";else->"未归还"}+" · ${if(e.field("direction","out")=="out") "借给" else "借自"} ${e.field("person")} · 剩余 ${ToolRules.number(s.remaining(e))} ${e.field("unit","件")}"+(if(e.field("end").isNotBlank()) " · 预计 "+e.field("end") else "")
    "subscription","bill"->{val next=s.periods.filter {it.sourceId==e.id && it.status=="pending"}.minByOrNull {it.dueDate};val label=when(e.state) {"trial"->"试用中";"paused"->"已暂停";"cancelled"->"已取消";else->if(e.kind=="subscription") "订阅中" else "跟踪中"};label+" · "+(if(next==null) "暂无待处理期间" else "${next.dueDate} · "+(next.expectedMinor?.let {"¥"+toolMoney(it,hidden)} ?: "金额待确认"))+(if(e.state=="trial"&&e.field("trialEnd").isNotBlank()) " · 试用至 "+e.field("trialEnd") else "")}
    "item"->listOf(e.field("category"),e.field("location"),e.field("model"),when(e.state) {"sold"->"已售出";"scrapped"->"已报废";"idle"->"闲置";else->"使用中"}).filter {it.isNotBlank()}.joinToString(" · ")
    "outfit"->"${s.links.count {it.entryId==e.id && it.section=="outfit"}} 件单品 · 穿过 ${s.logs.count {it.entryId==e.id && it.kind=="wear"}} 天"
    else->"${s.links.count {it.entryId==e.id}} 项关联内容"
}
