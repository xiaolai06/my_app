package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Read-only content rows; management appears only after the user opens it. */
@Composable internal fun PlanDetailEntry(title:String,subtitle:String?=null,tag:String,kind:String?=null,
    enabled:Boolean=true,onMore:(()->Unit)?=null,onClick:()->Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).testTag(tag).clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(kind!=null) PlanGlyph(kind)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium)
                    subtitle?.takeIf {it.isNotBlank()}?.let {Text(it,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted,maxLines=2,overflow=TextOverflow.Ellipsis)}
                }
                if(onMore==null) PlanGlyph("right",Modifier.size(16.dp),LocalGlass.current.muted)
            }
            if(onMore!=null) IconButton(onClick=onMore,enabled=enabled,modifier=Modifier.testTag("more:$tag").semantics {contentDescription="管理$title"}) {PlanGlyph("more",color=LocalGlass.current.muted)}
        }
    }
}

@Composable internal fun PlanDetailMenuAction(title:String,tag:String,enabled:Boolean=true,destructive:Boolean=false,action:()->Unit) {
    TextButton(onClick=action,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag(tag),contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp)) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,color=if(destructive) MaterialTheme.colorScheme.error else LocalGlass.current.ink)
    }
}

@Composable internal fun PlanDetailHeading(title:String,count:Int?=null,action:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        count?.let {Text(it.toString(),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)}
        action?.invoke()
    }
}
