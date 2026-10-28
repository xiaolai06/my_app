package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.feature.tools.*
import kotlinx.coroutines.launch

internal fun toolPhotos(entry:ToolEntry,data:ToolsSnapshot,records:RecordsSnapshot):List<Attachment> {
    val own=records.attachmentsFor("tool",entry.id).filter {it.mime.startsWith("image/")}
    if(own.isNotEmpty() || entry.kind!="outfit") return own
    val available=data.entries.filter {it.isClothing() && it.deletedAt==null && it.archivedAt==null}.map {it.id}.toSet()
    return data.links.filter {it.entryId==entry.id && it.section=="outfit" && it.entityId in available}.sortedBy {it.sortOrder}
        .flatMap {records.attachmentsFor("tool",it.entityId).filter {a->a.mime.startsWith("image/")}.take(1)}.distinctBy {it.id}
}

/** Original line artwork, used only while there is no user photo. */
@Composable internal fun WardrobeArt(outfit:Boolean,modifier:Modifier=Modifier) {
    val color=LocalGlass.current.accent
    Canvas(modifier) {
        val w=size.width;val h=size.height;val stroke=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round)
        fun shirt(x:Float,y:Float,s:Float) {
            val path=Path().apply {
                moveTo(x+.32f*s,y+.08f*s);lineTo(x+.13f*s,y+.17f*s);lineTo(x+.02f*s,y+.40f*s)
                lineTo(x+.21f*s,y+.48f*s);lineTo(x+.26f*s,y+.37f*s);lineTo(x+.26f*s,y+.91f*s)
                lineTo(x+.74f*s,y+.91f*s);lineTo(x+.74f*s,y+.37f*s);lineTo(x+.79f*s,y+.48f*s)
                lineTo(x+.98f*s,y+.40f*s);lineTo(x+.87f*s,y+.17f*s);lineTo(x+.68f*s,y+.08f*s)
                cubicTo(x+.62f*s,y+.28f*s,x+.38f*s,y+.28f*s,x+.32f*s,y+.08f*s);close()
            }
            drawPath(path,color.copy(alpha=.08f));drawPath(path,color.copy(alpha=.65f),style=stroke)
        }
        val s=minOf(w,h)*if(outfit) .55f else .72f
        shirt(if(outfit) w*.08f else (w-s)/2,(h-s)/2,s)
        if(outfit) {
            val x=w*.57f;val y=h*.28f;val pw=w*.28f;val ph=h*.56f
            val path=Path().apply {moveTo(x,y);lineTo(x+pw,y);lineTo(x+pw*1.1f,y+ph);lineTo(x+pw*.62f,y+ph);lineTo(x+pw*.5f,y+ph*.38f);lineTo(x+pw*.38f,y+ph);lineTo(x-pw*.1f,y+ph);close()}
            drawPath(path,color.copy(alpha=.12f));drawPath(path,color.copy(alpha=.65f),style=stroke)
        }
    }
}

@Composable internal fun ToolPhotoEditor(photos:List<Attachment>,store:AttachmentStore,outfit:Boolean,busy:Boolean,
    add:()->Unit,open:(Attachment)->Unit,cover:(Attachment,()->Unit)->Unit,remove:(Attachment,()->Unit)->Unit,clothing:Boolean=false) {
    var selected by remember {mutableStateOf<Attachment?>(null)}
    var removing by remember {mutableStateOf<Attachment?>(null)}
    GlassPanel(Modifier.fillMaxWidth().testTag("toolPhotos")) {
        if(photos.isEmpty()) {
            Column(Modifier.fillMaxWidth().clickable(enabled=!busy,onClick=add).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                ToolPhotoArtwork(outfit,clothing,Modifier.size(100.dp,86.dp))
                Text(if(outfit) "留住这套搭配" else if(clothing) "给衣物添张照片" else "用照片补充记录",style=MaterialTheme.typography.titleMedium)
                Text("添加照片",Modifier.padding(top=6.dp),color=LocalGlass.current.accent)
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).background(LocalGlass.current.selected.copy(alpha=.25f)).clickable(enabled=!busy,onClickLabel="管理封面") {selected=photos.first()}) {
                AttachmentPreview(photos.first(),store,Modifier.fillMaxSize(),ContentScale.Fit)
                Surface(Modifier.align(Alignment.BottomStart).padding(12.dp),shape=RoundedCornerShape(10.dp),color=LocalGlass.current.sheet) {
                    Text("封面",Modifier.padding(horizontal=10.dp,vertical=5.dp),style=MaterialTheme.typography.labelMedium)
                }
            }
            LazyRow(contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(photos,key={it.id}) {photo->Box(Modifier.size(68.dp).clickable(enabled=!busy,onClickLabel="管理照片") {selected=photo}.testTag("editPhoto:"+photo.id)) {
                    AttachmentPreview(photo,store,Modifier.fillMaxSize())
                }}
                item {Box(Modifier.size(68.dp).clip(androidx.compose.foundation.shape.CircleShape).background(LocalGlass.current.selected)
                    .clickable(enabled=!busy,onClick=add).semantics {contentDescription="添加照片"}.testTag("addToolPhoto"),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Add,null)}}
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(photos.isEmpty()) "照片保存在本机" else "${photos.size} 张照片 · 点击管理封面",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            if(photos.isNotEmpty()) TextButton(enabled=!busy,onClick=add) {Text("添加")}
        }
    }
    selected?.let {photo->GlassSheet("照片",{selected=null},dismissEnabled=!busy) {
        AttachmentPreview(photo,store,Modifier.fillMaxWidth().height(220.dp),ContentScale.Fit)
        GlassActionRow("查看原图",enabled=!busy) {open(photo)}
        GlassActionRow(if(photos.firstOrNull()?.id==photo.id) "当前封面" else "设为封面",enabled=!busy && photos.firstOrNull()?.id!=photo.id) {cover(photo) {selected=null}}
        GlassActionRow("移除照片",enabled=!busy,destructive=true) {removing=photo}
    }}
    removing?.let {photo->ConfirmDialog("移除这张照片？","只移除当前编辑中的照片，其他内容引用不变。",
        {removing=null},{remove(photo) {removing=null;selected=null}},"移除")}
}

@Composable internal fun ToolPhotoGallery(photos:List<Attachment>,store:AttachmentStore,open:(Attachment)->Unit) {
    if(photos.isEmpty()) return
    val pager=rememberPagerState {photos.size}
    val scope=rememberCoroutineScope();val reduce=LocalReduceMotion.current
    LaunchedEffect(photos.size) {if(pager.currentPage>photos.lastIndex) pager.scrollToPage(photos.lastIndex)}
    GlassPanel(Modifier.fillMaxWidth().testTag("toolPhotoGallery")) {
        Box(Modifier.fillMaxWidth()) {
            HorizontalPager(pager,key={photos[it].id},modifier=Modifier.fillMaxWidth().heightIn(max=340.dp).aspectRatio(1.1f)) {page->
                Box(Modifier.fillMaxSize().clickable(onClickLabel="查看原图") {open(photos[page])}) {AttachmentPreview(photos[page],store,Modifier.fillMaxSize(),ContentScale.Fit)}
            }
            Surface(Modifier.align(Alignment.BottomEnd).padding(12.dp),shape=RoundedCornerShape(12.dp),color=LocalGlass.current.sheet) {
                Text("${pager.currentPage+1} / ${photos.size}",Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)
            }
        }
        if(photos.size>1) LazyRow(contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photos,key={_,a->a.id}) {index,photo->
                Box(Modifier.clip(RoundedCornerShape(14.dp)).then(if(index==pager.currentPage) Modifier.border(1.dp,LocalGlass.current.accent,RoundedCornerShape(14.dp)) else Modifier)
                    .clickable {scope.launch {if(reduce) pager.scrollToPage(index) else pager.animateScrollToPage(index)}}
                    .semantics {contentDescription="查看第 ${index+1} 张照片";selected=index==pager.currentPage}.testTag("galleryPhoto:"+index)) {
                    AttachmentPreview(photo,store,Modifier.size(56.dp).padding(3.dp))
                }
            }
        }
    }
}

@Composable internal fun ToolPhotoArtwork(outfit:Boolean,clothing:Boolean,modifier:Modifier=Modifier) {
    if(outfit || clothing) {WardrobeArt(outfit,modifier);return}
    val color=LocalGlass.current.accent
    Canvas(modifier) {
        val side=size.minDimension*.7f;val x=(size.width-side)/2;val y=(size.height-side)/2
        val stroke=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round)
        drawRoundRect(color.copy(alpha=.08f),androidx.compose.ui.geometry.Offset(x,y),androidx.compose.ui.geometry.Size(side,side),androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
        drawRoundRect(color.copy(alpha=.65f),androidx.compose.ui.geometry.Offset(x,y),androidx.compose.ui.geometry.Size(side,side),androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),style=stroke)
        drawCircle(color.copy(alpha=.65f),side*.07f,androidx.compose.ui.geometry.Offset(x+side*.72f,y+side*.27f))
        val mountain=Path().apply {moveTo(x+side*.15f,y+side*.72f);lineTo(x+side*.38f,y+side*.43f);lineTo(x+side*.59f,y+side*.65f);lineTo(x+side*.71f,y+side*.52f);lineTo(x+side*.86f,y+side*.72f)}
        drawPath(mountain,color.copy(alpha=.65f),style=stroke)
    }
}
