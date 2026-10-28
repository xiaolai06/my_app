package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.Attachment
import com.example.dailylife.feature.records.JournalStyle

@Composable internal fun journalAccent(style:JournalStyle):Color {
    val dark=LocalGlass.current.dark
    return when(style.accent) {"rose"->if(dark) Color(0xFFF2B4CE) else Color(0xFF98536D);"mint"->if(dark) Color(0xFF9AD8C2) else Color(0xFF376E5E);"amber"->if(dark) Color(0xFFEAC18A) else Color(0xFF8A6034);else->LocalGlass.current.accent}
}
@Composable internal fun JournalPaper(style:JournalStyle,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    val p=LocalGlass.current;val accent=journalAccent(style)
    val paper=if(p.dark) Color(0xFF252C39) else if(style.paper=="cream") Color(0xFFFFFAEF) else p.sheet
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(paper).drawWithCache {
        val space=28.dp.toPx();val line=accent.copy(alpha=if(p.dark) .09f else .07f)
        onDrawBehind {
            if(style.paper=="ruled") {var y=space;while(y<size.height) {drawLine(line,Offset(0f,y),Offset(size.width,y),1.dp.toPx());y+=space}}
            if(style.paper=="dots") {var y=space/2;while(y<size.height) {var x=space/2;while(x<size.width) {drawCircle(line,1.dp.toPx(),Offset(x,y));x+=space};y+=space}}
        }
    }.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
}
@Composable internal fun JournalStory(title:String,body:String,date:String?,mood:String?,style:JournalStyle,
    images:List<Attachment>,store:AttachmentStore,open:(Attachment)->Unit) {
    JournalPaper(style,Modifier.fillMaxWidth().testTag("journalStory")) {
        if(style.sticker.isNotBlank() || style.caption.isNotBlank()) Text(listOf(style.sticker,style.caption).filter {it.isNotBlank()}.joinToString("  "),color=journalAccent(style),style=MaterialTheme.typography.titleMedium)
        if(!date.isNullOrBlank()) Text(date+(if(mood.isNullOrBlank()) "" else " · "+mood),color=journalAccent(style),style=MaterialTheme.typography.labelLarge)
        Text(title,style=MaterialTheme.typography.headlineSmall)
        if(style.layout=="collage" && images.isNotEmpty()) JournalPhotos(images,style.layout,store,open)
        if(body.isNotBlank()) MarkdownBody(body,Modifier.fillMaxWidth())
        if(style.layout!="collage" && images.isNotEmpty()) JournalPhotos(images,style.layout,store,open)
    }
}
/** Page images in small groups: long journals never decode every original at once. */
@Composable internal fun JournalPhotos(images:List<Attachment>,layout:String,store:AttachmentStore,open:(Attachment)->Unit) {
    var page by remember(images.map {it.id}) {mutableIntStateOf(0)}
    val selected=images.drop(page*6).take(6);val dark=LocalGlass.current.dark
    BoxWithConstraints {
        val columns=if(layout!="story" && maxWidth>=260.dp && LocalDensity.current.fontScale<=1.3f) 2 else 1
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            selected.chunked(columns).forEachIndexed {row,group->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                group.forEachIndexed {index,a->
                    Column(Modifier.weight(1f).then(if(layout=="film") Modifier.graphicsLayer {rotationZ=if((row+index)%2==0) -.8f else .8f} else Modifier)
                        .clip(RoundedCornerShape(if(layout=="film") 5.dp else 14.dp))
                        .background(if(dark) Color(0xFF343D4E) else Color.White).clickable {open(a)}.padding(if(layout=="film") 8.dp else 0.dp)) {
                        AttachmentPreview(a,store,Modifier.fillMaxWidth().aspectRatio(if(layout=="story") 1.3f else 1f))
                        if(layout=="film") Text(a.displayName,Modifier.padding(top=8.dp,bottom=4.dp),maxLines=2,style=MaterialTheme.typography.bodySmall)
                    }
                }
                if(group.size<columns) Spacer(Modifier.weight(1f))
            }}
        }
    }
    if(images.size>6) Row(verticalAlignment=Alignment.CenterVertically) {
        Text("${page*6+1}–${(page*6+6).coerceAtMost(images.size)} / ${images.size}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
        TextButton(enabled=page>0,onClick={page--}) {Text("上一组")}
        TextButton(enabled=(page+1)*6<images.size,onClick={page++}) {Text("下一组")}
    }
}
@Composable internal fun JournalStylePicker(value:String,busy:Boolean,change:(String)->Unit,dismiss:()->Unit) {
    var style by remember(value) {mutableStateOf(runCatching {JournalStyle.parse(value)}.getOrDefault(JournalStyle()))}
    GlassSheet("我的手账样式",dismiss,footer={
        GlassButton(enabled=!busy,onClick={change(style.json());dismiss()},modifier=Modifier.fillMaxWidth()) {Text("应用到这篇记录")}
    }) {
        Text("纸张和排版独立保存，正文与图片原件保持完整。",color=LocalGlass.current.muted)
        SectionLabel("纸张");FlowChoices(JournalStyle.papers.toList(),style.paper) {style=style.copy(paper=it)}
        SectionLabel("点缀色");FlowChoices(JournalStyle.accents.toList(),style.accent) {style=style.copy(accent=it)}
        SectionLabel("图片排版");FlowChoices(JournalStyle.layouts.toList(),style.layout) {style=style.copy(layout=it)}
        SectionLabel("小印记");FlowChoices(JournalStyle.stickers.map {it to it.ifBlank {"无"}},style.sticker) {style=style.copy(sticker=it)}
        GlassTextField(style.caption,{style=style.copy(caption=it.take(120))},label={Text("手账题签（可选）")},modifier=Modifier.fillMaxWidth())
        JournalPaper(style,Modifier.fillMaxWidth()) {
            Text(style.sticker+" "+style.caption.ifBlank {"把平常的小事留在这里"},color=journalAccent(style))
            Text("今天，慢一点也没关系。",style=MaterialTheme.typography.titleMedium)
            Text("这是样式预览，不会添加到你的正文。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        }
    }
}
