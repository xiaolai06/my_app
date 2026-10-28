package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.repository.Appearance

@Composable internal fun AppearanceSettings(value:Appearance,change:(Appearance)->Unit,nickname:String,
    saveNickname:(String,(String?)->Unit)->Unit,dismiss:()->Unit) {
    var editName by rememberSaveable {mutableStateOf(false)}
    GlassSheet("外观与偏好",dismiss) {
        SectionLabel("显示模式")
        BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
            val compact=maxWidth<300.dp || LocalDensity.current.fontScale>1.3f
            val modes=listOf("system" to "跟随系统","light" to "浅色","dark" to "深色")
            if(compact) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                modes.forEach {(mode,label)->ThemeOption(mode,label,value.theme==mode,true,Modifier.fillMaxWidth()) {change(value.copy(theme=mode))}}
            } else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                modes.forEach {(mode,label)->ThemeOption(mode,label,value.theme==mode,false,Modifier.weight(1f)) {change(value.copy(theme=mode))}}
            }
        }
        SectionLabel("阅读与动效")
        PersonalGroup {
            AppearanceToggle("减少动态效果","切换更平静",Icons.Outlined.Refresh,value.reducedMotion) {change(value.copy(reducedMotion=it))}
            PersonalDivider()
            AppearanceToggle("增强磨砂底色","文字更清晰",Icons.Outlined.Settings,value.frosted) {change(value.copy(frosted=it))}
        }
        SectionLabel("个人偏好")
        PersonalGroup {PersonalRow("称呼",Icons.Outlined.Person,value=nickname.ifBlank {"未设置"},tag="editNickname") {editName=true}}
    }
    if(editName) {
        var text by rememberSaveable {mutableStateOf(nickname)};var saving by remember {mutableStateOf(false)};var error by remember {mutableStateOf<String?>(null)}
        GlassSheet("设置称呼",{editName=false},dismissEnabled=!saving,footer={PickerActions({if(!saving) editName=false},{
            saving=true;error=null;saveNickname(text) {message->saving=false;if(message==null) editName=false else error=message}
        },"保存",!saving)}) {
            GlassTextField(text,{text=it.take(30)},label={Text("称呼")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("nicknameInput"))
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
}

@Composable private fun ThemeOption(mode:String,label:String,chosen:Boolean,horizontal:Boolean,modifier:Modifier,select:()->Unit) {
    val p=LocalGlass.current
    val base=modifier.clip(RoundedCornerShape(18.dp)).background(p.sheet.copy(alpha=.8f))
        .border(if(chosen) 1.dp else .5.dp,p.accent.copy(alpha=if(chosen) .55f else .12f),RoundedCornerShape(18.dp))
        .selectable(chosen,role=Role.RadioButton,onClick=select).testTag("theme:"+mode).padding(10.dp)
    if(horizontal) Row(base,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        ThemeMiniature(mode,Modifier.size(92.dp,70.dp));Text(label,Modifier.weight(1f));if(chosen) Icon(Icons.Outlined.Check,null,tint=p.accent)
    } else Column(base,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        ThemeMiniature(mode,Modifier.fillMaxWidth().height(100.dp))
        Text(label,style=MaterialTheme.typography.labelLarge,color=if(chosen) p.accent else p.ink)
        Box(Modifier.size(18.dp),contentAlignment=Alignment.Center) {if(chosen) Icon(Icons.Outlined.Check,null,tint=p.accent)}
    }
}

/** A small illustration of the actual surface hierarchy; no sample personal data. */
@Composable private fun ThemeMiniature(mode:String,modifier:Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp))) {
        val w=size.width;val h=size.height
        fun paint(dark:Boolean,left:Float,right:Float) {
            val bg=if(dark) Color(0xFF151A28) else Color(0xFFF3F5FB)
            val card=if(dark) Color(0xFF303B56) else Color.White
            val ink=if(dark) Color(0xFFB9C4FF) else Color(0xFF505FAC)
            drawContext.canvas.save();drawContext.canvas.clipRect(left,0f,right,h)
            drawRect(bg);drawCircle(ink.copy(alpha=.1f),w*.8f,Offset(w*.2f,h*.3f))
            drawRoundRect(card,Offset(w*.1f,h*.12f),Size(w*.8f,h*.36f),CornerRadius(7.dp.toPx()))
            drawRoundRect(ink.copy(alpha=.5f),Offset(w*.18f,h*.22f),Size(w*.32f,h*.035f),CornerRadius(2.dp.toPx()))
            drawRoundRect(ink.copy(alpha=.18f),Offset(w*.18f,h*.32f),Size(w*.52f,h*.035f),CornerRadius(2.dp.toPx()))
            drawRoundRect(card.copy(alpha=.75f),Offset(w*.1f,h*.56f),Size(w*.8f,h*.15f),CornerRadius(6.dp.toPx()))
            drawRoundRect(card,Offset(w*.1f,h*.8f),Size(w*.8f,h*.12f),CornerRadius(7.dp.toPx()))
            repeat(3) {i->drawCircle(ink.copy(alpha=if(i==0) .9f else .3f),2.5.dp.toPx(),Offset(w*(.26f+i*.24f),h*.86f))}
            drawContext.canvas.restore()
        }
        paint(mode=="dark",0f,w)
        if(mode=="system") paint(true,w/2,w)
    }
}

@Composable private fun AppearanceToggle(title:String,subtitle:String,icon:ImageVector,checked:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=76.dp).toggleable(checked,role=Role.Switch,onValueChange=change).padding(16.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,Modifier.size(22.dp),tint=LocalGlass.current.accent)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        }
        GlassSwitch(checked,null)
    }
}

@Composable internal fun BackupSettings(busy:Boolean,dismiss:()->Unit,backup:(String)->Unit,restore:(String)->Unit,transfer:(String)->Unit={}) {
    GlassSheet("数据与备份",dismiss,dismissEnabled=!busy) {
        Text("备份包含原照片与文件；卸载前请先保存备份。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
        listOf("records" to "记录","plans" to "计划","ledger" to "账本","tools" to "生活工具").forEach {(key,label)->
            PersonalGroup {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(when(key) {"records"->Icons.Outlined.Edit;"plans"->Icons.Outlined.DateRange;"ledger"->Icons.Outlined.ShoppingCart;else->Icons.Outlined.Home},null,tint=LocalGlass.current.accent)
                    Text(label,style=MaterialTheme.typography.titleMedium)
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxWidth().padding(horizontal=10.dp).padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton(enabled=!busy,onClick={backup(key)},modifier=Modifier.testTag("backup:"+key)) {Text("导出备份")}
                    TextButton(enabled=!busy,onClick={restore(key)},modifier=Modifier.testTag("restore:"+key)) {Text("从文件恢复")}
                }
            }
        }
        Text("文件导入与导出",style=MaterialTheme.typography.titleMedium)
        PersonalGroup {
            listOf("importCsv" to "导入账单 CSV","csv" to "导出账目 CSV","markdown" to "导出记录 Markdown","attachment" to "导出附件原件").forEach {(key,label)->
                TextButton(enabled=!busy,onClick={transfer(key)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("dataTransfer:$key")) {Text(label)}
            }
        }
    }
}
