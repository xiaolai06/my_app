package com.example.dailylife.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun DailyNavigation(page:String,select:(String)->Unit) {
    val p=LocalGlass.current
    Surface(color=p.sheet.copy(alpha=if(p.solid) 1f else .96f),tonalElevation=0.dp,shadowElevation=4.dp) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color=p.line.copy(alpha=.25f))
            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=4.dp)) {
                listOf("今天","记录","计划","账本","功能箱").forEach {title->
                    val selected=page==title
                    val color by animateColorAsState(if(selected) p.selected else Color.Transparent,tween(if(LocalReduceMotion.current) 0 else 180),label="navigation surface")
                    val icon=when(title) {"今天"->Icons.Default.Home;"记录"->Icons.Default.Edit;"计划"->Icons.Default.DateRange;"账本"->Icons.Default.List;else->Icons.Default.Build}
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).selectable(selected,onClick={select(title)},role=Role.Tab)
                        .testTag("root:"+title).heightIn(min=68.dp).padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(color),contentAlignment=Alignment.Center) {
                            Icon(icon,null,Modifier.size(23.dp),tint=if(selected) p.accent else p.muted)
                        }
                        Text(title,style=MaterialTheme.typography.labelSmall,fontSize=12.sp,lineHeight=18.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,color=if(selected) p.accent else p.muted)
                    }
                }
            }
        }
    }
}
