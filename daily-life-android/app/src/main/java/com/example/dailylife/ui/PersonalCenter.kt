package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailylife.R

@Composable internal fun MyLifeMark(modifier:Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(19.dp)).testTag("myLifeLogo")) {
        Image(painterResource(R.drawable.ic_my_life_background),null,Modifier.matchParentSize())
        Image(painterResource(R.drawable.ic_my_life_foreground),"My Life 标志",Modifier.matchParentSize())
    }
}

/** Settings-style rows: one shared surface per group, with quiet separators. */
@Composable internal fun PersonalRow(title:String,icon:ImageVector,tag:String="",value:String?=null,onClick:()->Unit) {
    val p=LocalGlass.current
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(role=Role.Button,onClick=onClick)
        .then(if(tag.isBlank()) Modifier else Modifier.testTag(tag)).padding(horizontal=16.dp,vertical=14.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
        Icon(icon,null,Modifier.size(21.dp),tint=p.accent)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            value?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=p.muted)}
        }
        Icon(Icons.Outlined.KeyboardArrowRight,null,Modifier.size(18.dp),tint=p.muted.copy(alpha=.65f))
    }
}

@Composable internal fun PersonalGroup(content:@Composable ColumnScope.()->Unit) {
    val p=LocalGlass.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(p.sheet.copy(alpha=if(p.solid) 1f else .82f))
        .border(.75.dp,p.line.copy(alpha=if(p.dark) .17f else .9f),RoundedCornerShape(18.dp)),content=content)
}
@Composable internal fun PersonalDivider() {
    HorizontalDivider(Modifier.padding(start=50.dp,end=16.dp),thickness=.5.dp,color=LocalGlass.current.accent.copy(alpha=.10f))
}

@Composable internal fun PersonalCenter(nickname:String,recoverable:Int,open:(String)->Unit,
    unfinished:List<UnfinishedEditSummary> = emptyList(),continueEditing:((UnfinishedEditSummary)->Unit)?=null,
    tools:LazyListScope.()->Unit) {
    val p=LocalGlass.current
    LazyColumn(Modifier.fillMaxSize().testTag("toolsHome"),contentPadding=PaddingValues(20.dp,8.dp,20.dp,24.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=16.dp).heightIn(min=76.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(17.dp)) {
                MyLifeMark(Modifier.size(64.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(nickname.ifBlank {"My Life"},style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                    Text(if(nickname.isBlank()) "我的生活空间" else "My Life · 个人空间",color=p.muted,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        if(unfinished.isNotEmpty()) {
            item {PersonalGroup {
                PersonalRow("未完成编辑",Icons.Outlined.Edit,"recoverEditing","共 ${unfinished.size} 条 · 查看全部") {open("recovery")}
                unfinished.take(3).forEach {entry->
                    HorizontalDivider(Modifier.padding(horizontal=16.dp),thickness=.5.dp,color=p.line)
                    UnfinishedSummaryContent(entry,Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel="继续编辑") {
                        continueEditing?.invoke(entry) ?: open("recovery")
                    }.testTag("unfinishedPreview:${entry.domain}:${entry.id}").padding(16.dp))
                }
            }}
        } else if(recoverable>0) {
            item {PersonalGroup {PersonalRow("未完成编辑",Icons.Outlined.Edit,"recoverEditing","共 $recoverable 条") {open("recovery")}}}
        }
        item {PersonalGroup {
            PersonalRow("外观与偏好",Icons.Outlined.Settings,"appSettings") {open("settings")}
            PersonalDivider()
            PersonalRow("提醒设置",Icons.Outlined.Notifications,"reminderCenter") {open("notifications")}
            PersonalDivider()
            PersonalRow("数据与备份",Icons.Outlined.AccountBox,"personalBackup") {open("backup")}
        }}
        tools()
    }
}
