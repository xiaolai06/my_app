package com.example.dailylife.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.feature.ledger.*
import java.time.LocalDate
import java.time.YearMonth

/** One connected navigation surface; no detached underline or far-away dropdown arrow. */
@Composable internal fun LedgerInternalNavigation(page:String,select:(String)->Unit) {
    val large=LocalDensity.current.fontScale>1.3f
    val p=LocalGlass.current
    Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp)
        .glassSurface(p,RoundedCornerShape(24.dp)).then(if(large) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
        .padding(4.dp).selectableGroup()) {
        ledgerTabs.forEach {(id,title)->
            val selected=page==id
            val color by animateColorAsState(if(selected) p.selected else androidx.compose.ui.graphics.Color.Transparent,
                tween(if(LocalReduceMotion.current) 0 else GlassMotion.SelectionMs),label="ledger navigation fill")
            Box((if(large) Modifier.widthIn(min=92.dp) else Modifier.weight(1f)).heightIn(min=48.dp)
                .background(color,RoundedCornerShape(20.dp)).selectable(selected,role=Role.Tab,onClick={select(id)})
                .testTag("ledgerTab:$id").padding(horizontal=12.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,
                    color=if(selected) p.accent else p.muted,maxLines=1)
            }
        }
    }
}

@Composable internal fun LedgerPeriodSwitch(ui:LedgerUi,vm:LedgerViewModel,modifier:Modifier=Modifier) {
    Row(modifier.glassSurface(LocalGlass.current,RoundedCornerShape(28.dp)).padding(3.dp).selectableGroup()) {
        listOf("day" to "日","month" to "月").forEach {(id,title)->
            Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)
                .background(if(ui.periodMode==id) LocalGlass.current.selected else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(24.dp))
                .selectable(ui.periodMode==id,role=Role.Tab,onClick={vm.periodMode(id)})
                .testTag("ledgerPeriod:$id").semantics {contentDescription=if(id=="day") "按日查看" else "按月查看"},contentAlignment=Alignment.Center) {Text(title)}
        }
    }
}
@Composable internal fun LedgerBrowseHeader(ui:LedgerUi,vm:LedgerViewModel) {
    var calendar by remember {mutableStateOf(false)}
    val search=ui.page=="transactions"
    val windowWidth=LocalWindowInfo.current.containerSize.width
    val large=LocalDensity.current.fontScale>1.3f || with(LocalDensity.current) {windowWidth.toDp()}<360.dp
    val date=LocalDate.parse(ui.selectedDate)
    val custom=search&&(ui.transactionFilter.from.isNotBlank()||ui.transactionFilter.to.isNotBlank())
    val title=if(custom) "自选范围" else if(ui.periodMode=="day") "${date.monthValue}月${date.dayOfMonth}日" else "${date.year}年${date.monthValue}月"
    @Composable fun DateControl(modifier:Modifier=Modifier) {
        TextButton(onClick={calendar=true},modifier=modifier.heightIn(min=52.dp).testTag("ledgerDateRange"),contentPadding=PaddingValues(horizontal=8.dp)) {
            Icon(Icons.Outlined.DateRange,null,Modifier.size(19.dp));Spacer(Modifier.width(7.dp))
            Text(title,color=LocalGlass.current.ink,fontWeight=FontWeight.Medium)
        }
    }
    @Composable fun Search(modifier:Modifier=Modifier) {
        GlassTextField(ui.transactionFilter.query,{vm.filter(ui.transactionFilter.copy(query=it))},modifier=modifier.testTag("ledgerSearch"),
            placeholder={Text("搜索账目",maxLines=1)},leadingIcon={Icon(Icons.Outlined.Search,null,Modifier.size(20.dp))},singleLine=true)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(search) {
            if(large) {DateControl();Search(Modifier.fillMaxWidth())}
            else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DateControl(Modifier.widthIn(max=158.dp));Search(Modifier.weight(1f))
            }
            LedgerFilterBar(ui,vm)
        } else {
            if(large) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {DateControl(Modifier.weight(1f));LedgerPeriodSwitch(ui,vm)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={vm.stepPeriod(-1)}) {Text(if(ui.periodMode=="day") "前一天" else "上个月")}
                    TextButton(onClick={vm.stepPeriod(1)}) {Text(if(ui.periodMode=="day") "后一天" else "下个月")}
                }
            } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={vm.stepPeriod(-1)},modifier=Modifier.size(48.dp).testTag("ledgerPreviousPeriod")) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,"上一时段")}
                DateControl(Modifier.weight(1f))
                IconButton(onClick={vm.stepPeriod(1)},modifier=Modifier.size(48.dp).testTag("ledgerNextPeriod")) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,"下一时段")}
                LedgerPeriodSwitch(ui,vm)
            }
        }
    }
    if(calendar) {
        val marked=remember(ui.data.transactions,ui.ledgerId) {ui.data.transactions.filter {it.deletedAt==null&&(ui.ledgerId==null||it.ledgerId==ui.ledgerId)}.map {it.occurredOn}.toSet()}
        GlassDatePicker(ui.selectedDate,{calendar=false},marked,confirmLabel="查看当天",
            chooseMonth={vm.month(it);calendar=false},choose={vm.day(it);calendar=false})
    }
}

internal val ledgerFilterTypes=listOf("all" to "全部类型","expense" to "支出","income" to "收入","transfer" to "转账","refund" to "退款","reimbursement" to "报销到账","adjustment" to "余额校准")
@Composable private fun LedgerFilterControl(label:String,tag:String,modifier:Modifier=Modifier,click:()->Unit) {
    Row(modifier.heightIn(min=48.dp).clickable(role=Role.Button,onClick=click).testTag(tag)
        .padding(horizontal=4.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
        Icon(Icons.Outlined.KeyboardArrowDown,null,Modifier.size(18.dp),tint=LocalGlass.current.muted)
    }
}
@Composable private fun LedgerFilterBar(ui:LedgerUi,vm:LedgerViewModel) {
    var picker by remember {mutableStateOf<String?>(null)}
    val large=LocalDensity.current.fontScale>1.3f
    val f=ui.transactionFilter
    val extra=listOf(f.account.isNotBlank(),f.from.isNotBlank()||f.to.isNotBlank(),f.min.isNotBlank()||f.max.isNotBlank(),f.onlyBudget).count {it}
    val category=when(f.category) {""->"全部分类";UnclassifiedLedgerCategory->"旧账目未分类";else->ui.data.categoryName(f.category)}
    Row(Modifier.fillMaxWidth().then(if(large) Modifier.horizontalScroll(rememberScrollState()) else Modifier).testTag("ledgerFilterBar"),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(if(large) Modifier.width(180.dp) else Modifier.weight(1f)) {
            LedgerFilterControl(ledgerFilterTypes.find {it.first==f.type}?.second ?: "全部类型","ledgerTypeFilter",Modifier.fillMaxWidth()) {picker="type"}
            DropdownMenu(picker=="type",{picker=null},containerColor=LocalGlass.current.sheet) {
                ledgerFilterTypes.forEach {(id,label)->DropdownMenuItem(text={Text(label)},onClick={vm.filter(f.copy(type=id,category=""));picker=null},
                    modifier=Modifier.testTag("ledgerFilterType:$id").semantics {selected=f.type==id})}
            }
        }
        LedgerFilterControl(category,"ledgerCategoryFilter",if(large) Modifier.width(180.dp) else Modifier.weight(1f)) {picker="category"}
        LedgerFilterControl(if(extra==0) "筛选" else "筛选 · $extra","ledgerAdvancedFilter",if(large) Modifier.width(144.dp) else Modifier.weight(1f)) {picker="advanced"}
    }
    if(picker=="category") LedgerPicker("分类筛选",listOf("" to "全部分类")+
        (if(f.category==UnclassifiedLedgerCategory||ui.data.transactions.any {it.deletedAt==null&&it.categoryId.isNullOrBlank()}) listOf(UnclassifiedLedgerCategory to "旧账目未分类") else emptyList())+
        ui.data.categories.filter {f.type !in setOf("expense","income")||it.direction==f.type}.map {
            it.id to (ui.data.categoryName(it.id)+if(f.type !in setOf("expense","income")) " · "+ledgerType(it.direction) else "")
        },f.category,{picker=null}) {vm.filter(f.copy(category=it));picker=null}
    if(picker=="advanced") LedgerFilterSheet(ui,{picker=null}) {vm.filter(it);picker=null}
}
@Composable private fun LedgerFilterSheet(ui:LedgerUi,dismiss:()->Unit,apply:(LedgerTransactionFilter)->Unit) {
    var draft by remember {mutableStateOf(ui.transactionFilter)}
    var dateField by remember {mutableStateOf<String?>(null)}
    val min=draft.min.takeIf {it.isNotBlank()}?.let {runCatching {LedgerRules.minor(it,true)}.getOrNull()}
    val max=draft.max.takeIf {it.isNotBlank()}?.let {runCatching {LedgerRules.minor(it,true)}.getOrNull()}
    val valid=(draft.min.isBlank()||min!=null)&&(draft.max.isBlank()||max!=null)&&(min==null||max==null||min<=max)&&
        (draft.from.isBlank()||runCatching {LocalDate.parse(draft.from)}.isSuccess)&&
        (draft.to.isBlank()||runCatching {LocalDate.parse(draft.to)}.isSuccess)&&
        (draft.from.isBlank()||draft.to.isBlank()||draft.from<=draft.to)
    GlassSheet("筛选账目",dismiss,footer={
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={draft=LedgerTransactionFilter()},modifier=Modifier.testTag("resetLedgerFilter")) {Text("重置")}
            GlassButton(enabled=valid,onClick={apply(draft.copy(advanced=false))},modifier=Modifier.weight(1f).testTag("applyLedgerFilter")) {Text("查看结果")}
        }
    }) {
        GlassSelectField("账户",listOf("" to "全部账户")+ui.data.accounts.map {it.id to it.name},draft.account) {draft=draft.copy(account=it)}
        LedgerSelectRow("开始日期",draft.from.ifBlank {if(draft.to.isNotBlank()) "不限" else "当前"+if(ui.periodMode=="day") "日期" else "月份"}) {dateField="from"}
        LedgerSelectRow("结束日期",draft.to.ifBlank {if(draft.from.isNotBlank()) "不限" else "当前"+if(ui.periodMode=="day") "日期" else "月份"}) {dateField="to"}
        if(draft.from.isNotBlank()||draft.to.isNotBlank()) TextButton(onClick={draft=draft.copy(from="",to="")}) {Text("恢复当前时段")}
        LedgerField(draft.min,{draft=draft.copy(min=it)},"最低金额（选填）",numeric=true)
        LedgerField(draft.max,{draft=draft.copy(max=it)},"最高金额（选填）",numeric=true)
        Row(verticalAlignment=Alignment.CenterVertically) {GlassCheckbox(draft.onlyBudget,{draft=draft.copy(onlyBudget=it)});Text("仅计入预算的账目")}
        if(!valid) Text("请核对日期先后和金额区间。",color=MaterialTheme.colorScheme.error)
    }
    if(dateField!=null) {
        val marked=remember(ui.data.transactions,ui.ledgerId) {ui.data.transactions.filter {it.deletedAt==null&&(ui.ledgerId==null||it.ledgerId==ui.ledgerId)}.map {it.occurredOn}.toSet()}
        GlassDatePicker((if(dateField=="from") draft.from else draft.to).ifBlank {ui.selectedDate},{dateField=null},marked) {
            draft=if(dateField=="from") draft.copy(from=it) else draft.copy(to=it);dateField=null
        }
    }
}
