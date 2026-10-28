package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.tools.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable internal fun ToolCollectionBar(count:Int,grid:Boolean,change:(String)->Unit,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("$count 项",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        CollectionViewSwitch(grid,change)
    }
}

@Composable internal fun CollectionViewSwitch(grid:Boolean,change:(String)->Unit) {
    Row(Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(LocalGlass.current.sheet.copy(alpha=.7f)).selectableGroup()) {
        listOf("list","grid").forEach {mode->
            val chosen=grid==(mode=="grid");val color=if(chosen) LocalGlass.current.accent else LocalGlass.current.muted
            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if(chosen) LocalGlass.current.selected else Color.Transparent)
                .selectable(chosen,role=Role.RadioButton) {change(mode)}
                .semantics {contentDescription=if(mode=="grid") "卡片视图" else "列表视图"}.testTag("layout:"+mode),contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(20.dp)) {
                    val line=Stroke(1.6.dp.toPx())
                    if(mode=="grid") repeat(4) {i->drawRoundRect(color,Offset((i%2)*size.width*.55f,(i/2)*size.height*.55f),Size(size.width*.38f,size.height*.38f),CornerRadius(2.dp.toPx()),style=line)}
                    else repeat(3) {i->drawRoundRect(color,Offset(0f,size.height*(.08f+i*.33f)),Size(size.width,size.height*.19f),CornerRadius(1.dp.toPx()),style=line)}
                }
            }
        }
    }
}

@Composable internal fun ToolCollectionItem(e:ToolEntry,photos:List<Attachment>,store:AttachmentStore,data:ToolsSnapshot,
    grid:Boolean,hidden:Boolean,open:()->Unit,modifier:Modifier=Modifier,footer:@Composable ColumnScope.()->Unit={}) {
    val icon=when(e.kind) {"anniversary"->Icons.Outlined.DateRange;"subscription","bill"->Icons.Outlined.ShoppingCart;"item"->Icons.Outlined.Home;"loan"->Icons.Outlined.Refresh;"project"->Icons.Outlined.Star;else->Icons.Outlined.CheckCircle}
    GlassPanel(modifier.fillMaxWidth().clickable(role=Role.Button,onClick=open).testTag("toolEntry:"+e.id)) {
        if(grid) Box(Modifier.fillMaxWidth().aspectRatio(1.4f).background(LocalGlass.current.selected.copy(alpha=.25f)),contentAlignment=Alignment.Center) {
            if(photos.isNotEmpty()) AttachmentPreview(photos.first(),store,Modifier.fillMaxSize())
            else when(e.kind) {
                "anniversary"->{
                    val days=data.nextDate(e)?.let {ChronoUnit.DAYS.between(LocalDate.now(),it)}
                    Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(if(days==0L) "今天" else days?.let {kotlin.math.abs(it).toString()}.orEmpty(),style=MaterialTheme.typography.headlineLarge,color=LocalGlass.current.accent)
                        Text(if(days==null) "日期待完善" else if(days<0) "天前" else if(days==0L) "值得记住的日子" else "天后",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                    }
                }
                "item"->ToolPhotoArtwork(false,e.isClothing(),Modifier.fillMaxSize().padding(18.dp))
                else->Box(Modifier.size(48.dp).background(LocalGlass.current.sheet,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) {Icon(icon,null,tint=LocalGlass.current.accent)}
            }
        }
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(!grid) {
                    if(photos.isNotEmpty()) AttachmentPreview(photos.first(),store,Modifier.size(66.dp))
                    else Box(Modifier.size(42.dp).background(LocalGlass.current.selected.copy(alpha=.65f),androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) {Icon(icon,null,Modifier.size(21.dp),tint=LocalGlass.current.accent)}
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(e.title,style=MaterialTheme.typography.titleSmall,maxLines=if(grid) 1 else 2,overflow=TextOverflow.Ellipsis)
                    Text(if(grid && e.kind=="anniversary" && photos.isEmpty()) data.nextDate(e)?.toString().orEmpty() else toolSummary(e,data,hidden),
                        style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted,minLines=if(grid) 2 else 1,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
            val children=if(e.kind in setOf("checklist","checklist_template")) data.children.filter {it.entryId==e.id} else emptyList()
            if(children.isNotEmpty()) LinearProgressIndicator(progress={children.count {it.completedAt!=null}.toFloat()/children.size},modifier=Modifier.fillMaxWidth().height(4.dp),color=LocalGlass.current.accent,trackColor=LocalGlass.current.selected)
            else if(grid) Spacer(Modifier.height(4.dp))
            footer()
        }
    }
}

@Composable internal fun ToolDetailSummary(e:ToolEntry,data:ToolsSnapshot,hidden:Boolean) {
    GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(e.kind=="anniversary") {
            val days=data.nextDate(e)?.let {ChronoUnit.DAYS.between(LocalDate.now(),it)}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.DateRange,null,Modifier.size(32.dp),tint=LocalGlass.current.accent)
                Text(if(days==0L) "就是今天" else days?.let {if(it<0) "已过 ${-it} 天" else "还有 $it 天"} ?: "日期待完善",style=MaterialTheme.typography.headlineSmall,color=LocalGlass.current.accent)
            }
        } else Text(toolSummary(e,data,hidden),color=LocalGlass.current.accent,style=MaterialTheme.typography.titleMedium)
        if(e.kind in setOf("checklist","checklist_template","loan")) {
            val progress=if(e.kind=="loan") {
                val total=e.field("quantity","1").toBigDecimal().movePointRight(3).toFloat()
                if(total>0) (total-data.remaining(e))/total else 0f
            } else {
                val rows=data.children.filter {it.entryId==e.id};if(rows.isEmpty()) 0f else rows.count {it.completedAt!=null}.toFloat()/rows.size
            }
            LinearProgressIndicator(progress={progress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(5.dp),color=LocalGlass.current.accent,trackColor=LocalGlass.current.selected)
        }
    }}
}
