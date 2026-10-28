package com.example.dailylife.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.dailylife.feature.plans.PlanForm
import com.example.dailylife.feature.plans.PlansViewModel
import java.math.BigDecimal

/** Light field groups avoid putting another card around every input card. */
@Composable internal fun PlanEditorGroup(title:String,key:String,collapsible:Boolean=false,initiallyExpanded:Boolean=true,
    summary:String?=null,content:@Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable(key) {mutableStateOf(initiallyExpanded)}
    LaunchedEffect(initiallyExpanded) {if(initiallyExpanded) expanded=true}
    val open=!collapsible || expanded
    val duration=if(LocalReduceMotion.current) 0 else 180
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(collapsible) Row(Modifier.fillMaxWidth().heightIn(min=56.dp)
            .background(LocalGlass.current.sheet.copy(alpha=.55f),RoundedCornerShape(16.dp))
            .clickable(role=Role.Button) {expanded=!expanded}.testTag("section:$key")
            .semantics {stateDescription=if(open) "已展开" else "已收起"}.padding(horizontal=14.dp,vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleSmall,modifier=Modifier.semantics {heading()})
                if(!open && !summary.isNullOrBlank()) Text(summary,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            }
            PlanGlyph(if(open) "up" else "down",Modifier.size(16.dp),LocalGlass.current.muted)
        } else Text(title,Modifier.padding(start=4.dp).testTag("section:$key").semantics {heading()},
            style=MaterialTheme.typography.labelLarge,color=LocalGlass.current.muted)
        AnimatedVisibility(open,enter=fadeIn(tween(duration))+expandVertically(tween(duration)),exit=fadeOut(tween(duration))+shrinkVertically(tween(duration))) {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}

private fun durationNumber(text:String)=if(text.matches(Regex("-?\\d{1,15}(\\.\\d{0,3})?"))) runCatching {text.toBigDecimal()}.getOrNull() else null
private fun cleanDuration(value:BigDecimal)=value.stripTrailingZeros().toPlainString()
internal fun planDurationLabel(seconds:String):String {
    val value=durationNumber(seconds) ?: return "设置时长"
    val absolute=value.abs()
    val hours=absolute.divideToIntegralValue(BigDecimal(3600))
    val remaining=absolute.remainder(BigDecimal(3600))
    val minutes=remaining.divideToIntegralValue(BigDecimal(60))
    val rest=remaining.remainder(BigDecimal(60))
    return buildString {
        if(value.signum()<0) append("−")
        if(hours.signum()>0) append(cleanDuration(hours)+"小时")
        if(minutes.signum()>0) append(cleanDuration(minutes)+"分钟")
        if(rest.signum()>0 || isEmpty()) append(cleanDuration(rest)+"秒")
    }
}

/** A temporary duration is committed only at Confirm, always in the original seconds unit. */
@Composable internal fun PlanDurationField(f:PlanForm,key:String,label:String,default:String,vm:PlansViewModel,integerSeconds:Boolean=false) {
    val editorState by vm.ui.collectAsState()
    PlanDurationValue(label,f.text(key,default),{vm.edit(f.set(key,it))},key,!editorState.busy,integerSeconds,
        if(f.kind=="focus") 86400 else 1_000_000_000)
}

@Composable internal fun PlanDurationValue(label:String,value:String,onChange:(String)->Unit,tag:String,enabled:Boolean=true,
    integerSeconds:Boolean=false,maximumSeconds:Long=1_000_000_000,allowNegative:Boolean=false,allowZero:Boolean=false) {
    var open by remember(tag) {mutableStateOf(false)}
    GlassSelectorRow(label,planDurationLabel(value),Modifier.testTag(tag),enabled=enabled) {open=true}
    if(open) PlanDurationSheet(label,tag,value,integerSeconds,maximumSeconds,allowNegative,allowZero,{open=false},onChange)
}

@Composable private fun PlanDurationSheet(title:String,key:String,value:String,integerSeconds:Boolean,maximumSeconds:Long,
    allowNegative:Boolean,allowZero:Boolean,dismiss:()->Unit,choose:(String)->Unit) {
    val initial=remember(value) {durationNumber(value)?.abs() ?: BigDecimal.ZERO}
    var subtract by rememberSaveable(value) {mutableStateOf(allowNegative && value.startsWith("-"))}
    var hours by rememberSaveable(value) {mutableStateOf(cleanDuration(initial.divideToIntegralValue(BigDecimal(3600))))}
    var minutes by rememberSaveable(value) {mutableStateOf(cleanDuration(initial.remainder(BigDecimal(3600)).divideToIntegralValue(BigDecimal(60))))}
    var seconds by rememberSaveable(value) {mutableStateOf(cleanDuration(initial.remainder(BigDecimal(60))))}
    val h=hours.toLongOrNull();val m=minutes.toIntOrNull();val s=durationNumber(seconds)
    val magnitude=if(h!=null && m!=null && s!=null) BigDecimal.valueOf(h).multiply(BigDecimal(3600)).add(BigDecimal(m*60)).add(s) else null
    val total=magnitude?.let {if(subtract) it.negate() else it}
    val maximum=BigDecimal(maximumSeconds)
    val valid=h!=null && h>=0 && m!=null && m in 0..59 && s!=null && s>=BigDecimal.ZERO && s<BigDecimal(60) &&
        magnitude!=null && (magnitude>BigDecimal.ZERO || allowZero && magnitude.signum()==0) && magnitude<=maximum &&
        (!integerSeconds || magnitude.stripTrailingZeros().scale()<=0)
    fun preset(raw:Long) {hours=(raw/3600).toString();minutes=(raw/60%60).toString();seconds=(raw%60).toString()}
    val large=LocalDensity.current.fontScale>1.3f
    GlassSheet(title,dismiss,footer={PickerActions(dismiss,{choose(cleanDuration(total!!));dismiss()},"确定",valid,"confirmDuration:$key","cancelDuration:$key")}) {
        if(allowNegative) FlowChoices(listOf("add" to "增加","subtract" to "扣减"),if(subtract) "subtract" else "add") {subtract=it=="subtract"}
        val amount=magnitude?.let(::cleanDuration)
        FlowChoices((if(key=="short" || key=="long") listOf(60L,300L,900L) else listOf(900L,1500L,1800L,3600L))
            .map {it.toString() to planDurationLabel(it.toString())},amount) {preset(it.toLong())}
        @Composable fun Fields(modifier:Modifier) {
            GlassTextField(hours,{hours=it.take(9)},label={Text("小时")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=modifier.testTag("durationHours:$key"))
            GlassTextField(minutes,{minutes=it.take(2)},label={Text("分钟")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=modifier.testTag("durationMinutes:$key"))
            GlassTextField(seconds,{seconds=it.take(8)},label={Text("秒")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=if(integerSeconds) KeyboardType.Number else KeyboardType.Decimal),modifier=modifier.testTag("durationSeconds:$key"))
        }
        if(large) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {Fields(Modifier.fillMaxWidth())}
        else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {Fields(Modifier.weight(1f))}
        if(!valid) Text(if(integerSeconds && maximumSeconds==86400L) "设置 1秒至24小时的整秒时长" else if(integerSeconds) "请输入有效整秒时长" else "请输入有效时长",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    }
}
