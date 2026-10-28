package com.example.dailylife.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Optional sections keep values in the parent form even while collapsed. */
@Composable internal fun GlassFormSection(title:String,key:String,collapsible:Boolean=false,initiallyExpanded:Boolean=true,
    summary:String?=null,content:@Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable(key) {mutableStateOf(initiallyExpanded)}
    LaunchedEffect(initiallyExpanded) {if(initiallyExpanded) expanded=true}
    val open=!collapsible || expanded
    val duration=if(LocalReduceMotion.current) 0 else 180
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp)
            .then(if(collapsible) Modifier.clickable(role=Role.Button) {expanded=!expanded}.semantics {stateDescription=if(open) "已展开" else "已收起"} else Modifier)
            .testTag("section:"+key).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics {heading()})
                if(summary!=null && !open) Text(summary,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            }
            if(collapsible) Icon(if(open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,null,Modifier.size(20.dp),tint=LocalGlass.current.muted)
        }
        AnimatedVisibility(open,enter=fadeIn(tween(duration))+expandVertically(tween(duration)),exit=fadeOut(tween(duration))+shrinkVertically(tween(duration))) {
            Column(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}

@Composable internal fun GlassInfoRow(label:String,value:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        Text(value,style=MaterialTheme.typography.bodyLarge)
    }
}

@Composable internal fun GlassActionRow(title:String,modifier:Modifier=Modifier,enabled:Boolean=true,destructive:Boolean=false,onClick:()->Unit) {
    Row(modifier.fillMaxWidth().heightIn(min=56.dp).clickable(enabled=enabled,role=Role.Button,onClick=onClick)
        .padding(horizontal=8.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,
            color=if(destructive) MaterialTheme.colorScheme.error else LocalGlass.current.ink)
    }
    HorizontalDivider(color=LocalGlass.current.accent.copy(alpha=.08f))
}
