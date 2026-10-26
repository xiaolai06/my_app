package com.example.dailylife.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.ToolRules
import com.example.dailylife.feature.tools.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.math.RoundingMode
import kotlinx.coroutines.launch

@Composable internal fun ToolCalculator(kind:String,ui:ToolsUi,vm:ToolsViewModel,open:(String,String)->Unit) {
    var first by rememberSaveable(kind) {mutableStateOf(if(kind=="dates") LocalDate.now().toString() else "")}
    var second by rememberSaveable(kind) {mutableStateOf(if(kind=="aa") "2" else if(kind=="dates") LocalDate.now().toString() else "")}
    var mode by rememberSaveable(kind) {mutableStateOf("between")};var include by rememberSaveable {mutableStateOf(false)}
    var result by rememberSaveable(kind) {mutableStateOf("")};var error by rememberSaveable(kind) {mutableStateOf<String?>(null)};var dateKey by remember {mutableStateOf<String?>(null)};var clear by remember {mutableStateOf(false)}
    val clipboard=LocalClipboardManager.current;val historyEnabled=ui.data.preferences.find {it.featureKey=="calculationHistory"}?.hidden!=true
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current;val listState=rememberLazyListState();val scope=rememberCoroutineScope();val reduce=LocalReduceMotion.current
    fun calculate() {
        runCatching {
            result=when(kind) {"calculator"->ToolRules.calculate(first).toPlainString();"aa"->ToolRules.aa(first,second).mapIndexed {i,n->"第 ${i+1} 人：¥"+ToolRules.money(n)}.joinToString("\n");else->{val a=LocalDate.parse(first);if(mode=="between") {val b=LocalDate.parse(second);require(b>=a) {"结束日期不能早于开始日期"};(ChronoUnit.DAYS.between(a,b)+(if(include) 1 else 0)).toString()+" 天"} else {val days=second.toLong();require(days in -365000..365000) {"天数超出范围"};a.plusDays(days).toString()}}}
            error=null;keyboard?.hide();focus.clearFocus();vm.run(null) {calculation(kind,listOf(first,second,mode,include).joinToString(" | "),result,historyEnabled)}
            scope.launch {withFrameNanos {};if(reduce) listState.scrollToItem(1) else listState.animateScrollToItem(1)}
        }.onFailure {error=it.message ?: "输入有误，请检查数字和日期"}
    }
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(20.dp,12.dp,20.dp,24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(kind) {
                "calculator"->{ToolField("算式，如 (120 + 30) × 80%",first,{first=it});Text("支持四则、括号和百分数；50% 表示 50÷100。",color=LocalGlass.current.muted)}
                "aa"->{ToolField("总金额（元）",first,{first=it});ToolField("人数（1～1000）",second,{second=it});Text("按整数分等分，余下的分依次分给前几人，总和保持一致。",color=LocalGlass.current.muted)}
                else->{FlowChoices(listOf("between" to "日期间隔","offset" to "日期加减"),mode) {mode=it;second=if(it=="offset") "0" else first;result=""};ToolDate("开始日期",first,{dateKey="first"});if(mode=="between") {ToolDate("结束日期",second,{dateKey="second"});ToolSwitch("包含开始日",include) {include=it}} else ToolField("增加 / 减少自然日，如 -7",second,{second=it});Text("按公历自然日计算，不包含法定工作日算法。",color=LocalGlass.current.muted)}
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Button(enabled=!ui.busy,onClick={calculate()},modifier=Modifier.fillMaxWidth()) {Text("计算结果")}
        }}}
        if(result.isNotEmpty()) item {GlassPanel(Modifier.fillMaxWidth(),selected=true) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("结果",style=MaterialTheme.typography.labelLarge);Text(result,style=MaterialTheme.typography.titleLarge,modifier=Modifier.testTag("calculationResult"))
            TextButton(onClick={clipboard.setText(AnnotatedString(result))}) {Text("复制结果")}
            if(kind=="calculator") TextButton(onClick={runCatching {ToolRules.calculate(first).setScale(2,RoundingMode.HALF_UP).toPlainString()}.onSuccess {open("expense_amount",it)}.onFailure {error=it.message}}) {Text("四舍五入到分，带入记账")}
            if(kind=="aa") TextButton(onClick={runCatching {ToolRules.aa(first,second).first()}.onSuccess {open("expense_amount",ToolRules.money(it))}.onFailure {error=it.message}}) {Text("将第1人的金额带入记账")}
            if(kind=="dates" && mode=="offset") {TextButton(onClick={open("task_date",result)}) {Text("带入新任务的计划日期")};TextButton(onClick={open("anniversary_date",result)}) {Text("带入新纪念日的原始日期")}}
        }}}
        item {ToolSwitch("保存计算历史（最近200条）",historyEnabled) {vm.run(null) {feature("calculationHistory","hide")}}}
        item {Row(Modifier.fillMaxWidth()) {Text("计算历史",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={clear=true}) {Text("清空")}}}
        val history=ui.data.calculations.filter {it.kind==kind}
        if(history.isEmpty()) item {Text("暂无计算历史",color=LocalGlass.current.muted)}
        items(history,key={it.id}) {h->
            val input=JSONObject(h.input).optString("value").split(" | ")
            val summary=when(kind) {
                "aa"->input.getOrElse(0) {""}+" 元 · "+input.getOrElse(1) {""}+" 人"
                "dates"->if(input.getOrNull(2)=="offset") input.getOrElse(0) {""}+" · 增减 "+input.getOrElse(1) {""}+" 天"
                    else input.getOrElse(0) {""}+" → "+input.getOrElse(1) {""}+if(input.getOrNull(3)=="true") "（含开始日）" else "（不含开始日）"
                else->input.firstOrNull().orEmpty()
            }
            GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {Text(summary,color=LocalGlass.current.muted);Text(JSONObject(h.result).optString("value"));Row {TextButton(onClick={clipboard.setText(AnnotatedString(JSONObject(h.result).optString("value")))}) {Text("复制")};TextButton(onClick={vm.run("历史已删除") {clearCalculation(h.id)}}) {Text("删除")}}}}
        }
    }
    dateKey?.let {key->CalendarDialog(if(key=="first") first else second,{dateKey=null}) {if(key=="first") first=it else second=it;dateKey=null}}
    if(clear) ConfirmDialog("清空计算历史","会清空所有计算工具的历史，业务内容保留。",{clear=false},{vm.run("历史已清空") {clearCalculation()}})
}
