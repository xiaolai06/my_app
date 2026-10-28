package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import com.example.dailylife.data.files.AttachmentStore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.tools.*
import java.time.LocalDate

@Composable internal fun GlassFavorite(selected:Boolean,enabled:Boolean=true,onClick:()->Unit) {
    IconToggleButton(checked=selected,onCheckedChange={onClick()},enabled=enabled,
        modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics {contentDescription=if(selected) "取消收藏" else "收藏"}) {
        val color=if(selected) LocalGlass.current.accent else LocalGlass.current.muted
        Canvas(Modifier.size(24.dp)) {
            val path=androidx.compose.ui.graphics.Path()
            for(i in 0..9) {
                val angle=Math.PI*(i/5.0-.5);val radius=size.minDimension*(if(i%2==0) .44f else .21f)
                val x=center.x+kotlin.math.cos(angle).toFloat()*radius;val y=center.y+kotlin.math.sin(angle).toFloat()*radius
                if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            path.close()
            drawPath(path,color,style=if(selected) androidx.compose.ui.graphics.drawscope.Fill else androidx.compose.ui.graphics.drawscope.Stroke(1.7.dp.toPx(),join=androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}
@Composable internal fun ClothingEditor(f:ToolForm,set:(String,String)->Unit) {
    ToolSwitch("收进衣橱",f.text("wardrobe")=="true") {set("wardrobe",it.toString())}
    if(f.text("wardrobe")=="true") {
        SectionLabel("衣物资料")
        FlowChoices(clothingCategories.toList(),f.text("clothingType","top")) {set("clothingType",it)}
        ToolField("颜色",f.text("color"),{set("color",it)})
        ToolField("尺码",f.text("size"),{set("size",it)})
        ToolField("材质",f.text("material"),{set("material",it)})
        ToolField("适合场景",f.text("occasion"),{set("occasion",it)})
        WardrobeTagsEditor(f.text("styleTags")) {set("styleTags",it)}
        FlowChoices(clothingWarmth.toList(),f.text("warmth","all")) {set("warmth",it)}
        FlowChoices(listOf("ready" to "可穿","wash" to "待清洗","stored" to "已收纳"),f.text("laundry","ready")) {set("laundry",it)}
    }
}
@Composable internal fun OutfitEditor(f:ToolForm,ui:ToolsUi,set:(String,String)->Unit,store:AttachmentStore,chooseDate:()->Unit) {
    var picker by remember {mutableStateOf(false)}
    val ids=f.text("items").split(',').filter {it.isNotBlank()}.toSet()
    ToolField("穿搭场景",f.text("occasion"),{set("occasion",it)})
    WardrobeTagsEditor(f.text("styleTags")) {set("styleTags",it)}
    ToolDate("计划穿着日（可选）",f.text("date"),chooseDate,{set("date","")})
    TextButton(onClick={picker=true},enabled=!ui.busy,modifier=Modifier.testTag("outfitItems")) {Text("选择衣橱单品 · ${ids.size} 件")}
    if(ids.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        items(ids.toList(),key={it}) {id->
            val item=ui.data.entries.find {it.id==id && it.deletedAt==null}
            val photo=item?.let {toolPhotos(it,ui.data,ui.records).firstOrNull()}
            Column(Modifier.width(90.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                if(photo!=null) AttachmentPreview(photo,store,Modifier.size(90.dp,100.dp)) else WardrobeArt(false,Modifier.size(90.dp,100.dp))
                Text(item?.title ?: "衣物已移除",style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
    if(picker) GlassMultiSelectionSheet("组合衣橱单品",
        ui.data.entries.filter {it.isClothing() && it.deletedAt==null && it.archivedAt==null && it.state !in setOf("sold","scrapped")}.map {it.id to it.title},
        ids,{picker=false},maximum=30) {set("items",it.joinToString(","))}
}
@Composable internal fun OutfitDetail(e:ToolEntry,ui:ToolsUi,vm:ToolsViewModel,open:(String,String)->Unit) {
    var date by remember {mutableStateOf(false)}
    val links=ui.data.links.filter {it.entryId==e.id && it.section=="outfit"}.sortedBy {it.sortOrder}
    SectionLabel("搭配单品",e.field("occasion").ifBlank {null})
    if(links.isEmpty()) Text("可以编辑穿搭，加入衣橱中的单品。",color=LocalGlass.current.muted)
    links.forEach {link->val item=ui.data.entries.find {it.id==link.entityId && it.deletedAt==null}
        if(item!=null) Row(Modifier.fillMaxWidth().clickable {open("tool",item.id)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            val photo=toolPhotos(item,ui.data,ui.records).firstOrNull()
            if(photo!=null) AttachmentPreview(photo,vm.repository.records.files,Modifier.size(64.dp,76.dp)) else WardrobeArt(false,Modifier.size(64.dp,76.dp))
            Column(Modifier.weight(1f)) {Text(item.title,style=MaterialTheme.typography.titleMedium);Text(clothingHint(item),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)}
        }
        else Text("一件衣物已移入回收站或缺失，编辑穿搭可移除引用。",color=LocalGlass.current.muted)
    }
    if(e.field("date").isNotBlank()) Text("计划穿着 · "+e.field("date"))
    SectionLabel("穿着记录")
    @OptIn(ExperimentalLayoutApi::class)
    if(e.deletedAt==null && e.archivedAt==null) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        GlassButton(enabled=!ui.busy,onClick={vm.run("已记录今天的穿搭") {wear(e.id,LocalDate.now().toString())}}) {Text("今天穿过")}
        TextButton(onClick={date=true}) {Text("补记日期")}
    }
    ui.data.logs.filter {it.entryId==e.id && it.kind=="wear"}.sortedByDescending {it.date}.take(30).forEach {log->Row(verticalAlignment=Alignment.CenterVertically) {
        Text(log.date,Modifier.weight(1f));TextButton(enabled=!ui.busy && e.deletedAt==null && e.archivedAt==null,onClick={vm.run("已撤销这次穿着记录") {removeWear(log.id)}}) {Text("撤销记录")}
    }}
    if(date) CalendarDialog(LocalDate.now().toString(),{date=false}) {d->vm.run("穿着日期已记录",onSuccess={date=false}) {wear(e.id,d)}}
}
