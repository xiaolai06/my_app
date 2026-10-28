package com.example.dailylife.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.tools.*

private val wardrobeFilterSaver=listSaver<WardrobeFilter,String>(
    save={listOf(it.query,it.collection,it.category,it.occasion,it.tag,it.laundry,it.membership,it.sort)},
    restore={WardrobeFilter(it[0],it[1],it[2],it[3],it[4],it[5],it[6],it[7])})

@Composable internal fun WardrobeScreen(ui:ToolsUi,vm:ToolsViewModel) {
    val outfits=ui.page=="outfit"
    val catalog=remember(ui.data.entries,ui.data.links,ui.data.logs) {WardrobeCatalog(ui.data)}
    var filter by rememberSaveable(ui.page,stateSaver=wardrobeFilterSaver) {mutableStateOf(WardrobeFilter())}
    var filtering by rememberSaveable(ui.page) {mutableStateOf(false)}
    var actions by remember(ui.page) {mutableStateOf<ToolEntry?>(null)}
    val entries=remember(catalog,outfits,filter) {catalog.filter(outfits,filter)}
    val grid=ui.layouts[ui.page]!="list"
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val large=LocalDensity.current.fontScale>1.3f
        val columns=if(grid && maxWidth>=360.dp && !large) 2 else 1
        LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().testTag("wardrobeGrid"),
            contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item(span={GridItemSpan(maxLineSpan)}) {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        GlassChoice("衣物",!outfits,Modifier.testTag("wardrobeTab:clothes")) {if(outfits) vm.page("wardrobe")}
                        GlassChoice("穿搭",outfits,Modifier.testTag("wardrobeTab:outfits")) {if(!outfits) vm.page("outfit")}
                    }
                    GlassIconButton({filtering=true},Modifier.testTag("wardrobeFilters")) {
                        val ink=if(filter.hasDetails) LocalGlass.current.accent else LocalGlass.current.ink
                        Canvas(Modifier.size(21.dp).semantics {contentDescription=if(filter.hasDetails) "筛选，已设置条件" else "筛选"}) {
                            repeat(3) {i->
                                val y=size.height*(.2f+i*.3f);val x=size.width*(if(i==1) .65f else .35f)
                                drawLine(ink,Offset(0f,y),Offset(size.width,y),1.6.dp.toPx(),StrokeCap.Round)
                                drawCircle(ink,2.5.dp.toPx(),Offset(x,y))
                            }
                        }
                    }
                }
                GlassTextField(filter.query,{filter=filter.copy(query=it)},placeholder={Text(if(outfits) "搜索穿搭、场景或标签" else "搜索衣物、标签或所属穿搭",maxLines=1,overflow=TextOverflow.Ellipsis)},
                    leadingIcon={Icon(Icons.Outlined.Search,null,Modifier.size(20.dp))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("wardrobeSearch"))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    LazyRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        items(listOf("all" to "全部","pinned" to "置顶","favorites" to "收藏")) {(key,title)->
                            GlassChoice(title,filter.collection==key,Modifier.testTag("wardrobeCollection:"+key)) {filter=filter.copy(collection=key)}
                        }
                    }
                    CollectionViewSwitch(grid) {vm.layout(ui.page,it)}
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("${entries.size} ${if(outfits) "套穿搭" else "件衣物"}",Modifier.weight(1f),color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
                    if(filter.hasDetails) TextButton(onClick={filtering=true},modifier=Modifier.testTag("activeWardrobeFilters")) {Text("已筛选 · 修改")}
                }
            }}
            if(entries.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {
                val filtered=filter!=WardrobeFilter()
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    WardrobeArt(outfits,Modifier.fillMaxWidth().height(100.dp))
                    GlassEmptyState(if(filtered) "没有符合条件的内容" else if(outfits) "组合你的第一套穿搭" else "收好每一件喜欢的衣物",
                        if(filtered) "调整筛选，或查看全部内容。" else if(outfits) "从衣橱选择单品，或添加整套照片。" else "添加照片，也可以先填写名称和标签。")
                    if(filtered) TextButton(onClick={filter=WardrobeFilter()},modifier=Modifier.testTag("clearWardrobeFilters")) {Text("清空条件")}
                }
            }
            items(entries,key={it.id}) {entry->
                val photos=toolPhotos(entry,ui.data,ui.records)
                val composite=outfits && ui.records.attachmentsFor("tool",entry.id).none {it.mime.startsWith("image/")}
                WardrobeCollectionCard(entry,photos,vm.repository.records.files,catalog,grid,composite,!ui.busy,
                    open={vm.detail(entry.id)},favorite={vm.run(null) {manage(entry.id,"favorite")}},more={actions=entry},
                    modifier=if(LocalReduceMotion.current) Modifier else Modifier.animateItem(placementSpec=tween(GlassMotion.SelectionMs)))
            }
        }
    }
    if(filtering) WardrobeFilterSheet(outfits,catalog,filter,{filtering=false}) {filter=it;filtering=false}
    actions?.let {old->val e=ui.data.entries.find {it.id==old.id} ?: old
        GlassSheet(if(outfits) "穿搭操作" else "衣物操作",{actions=null},dismissEnabled=!ui.busy) {
            Text(e.title,style=MaterialTheme.typography.titleMedium)
            GlassActionRow(if(e.pinned) "取消置顶" else "置顶",enabled=!ui.busy,modifier=Modifier.testTag("wardrobePinAction")) {vm.run(null,onSuccess={actions=null}) {manage(e.id,"pin")}}
            GlassActionRow(if(e.isFavorite()) "取消收藏" else "收藏",enabled=!ui.busy,modifier=Modifier.testTag("wardrobeFavoriteAction")) {vm.run(null,onSuccess={actions=null}) {manage(e.id,"favorite")}}
            GlassActionRow("编辑",enabled=!ui.busy) {actions=null;vm.begin(e.kind,e.id)}
        }
    }
}

@Composable private fun WardrobeCollectionCard(e:ToolEntry,photos:List<Attachment>,store:AttachmentStore,catalog:WardrobeCatalog,
    grid:Boolean,composite:Boolean,enabled:Boolean,open:()->Unit,favorite:()->Unit,more:()->Unit,modifier:Modifier=Modifier) {
    val outfit=e.kind=="outfit"
    val large=LocalDensity.current.fontScale>1.3f
    val membership=catalog.memberships[e.id].orEmpty()
    val subtitle=if(outfit) e.field("occasion").ifBlank {"日常搭配"} else listOfNotNull(clothingCategories[e.field("clothingType","top")],e.field("color").ifBlank {null}).joinToString(" · ")
    val relation=if(outfit) "${catalog.itemCounts[e.id] ?: 0} 件单品 · ${catalog.wornCounts[e.id] ?: 0} 次穿着" else if(membership.isEmpty()) "独立单品" else "用于 ${membership.size} 套穿搭"
    GlassPanel(modifier.fillMaxWidth().clickable(role=Role.Button,onClick=open).testTag("wardrobeEntry:"+e.id)) {
        if(grid) Box(Modifier.fillMaxWidth().aspectRatio(1.4f).testTag("wardrobeMedia:"+e.id)) {
            WardrobeCover(photos,store,outfit,composite,Modifier.fillMaxSize())
            if(e.pinned) Surface(Modifier.align(Alignment.TopStart).padding(8.dp),shape=CircleShape,color=LocalGlass.current.sheet.copy(alpha=.93f)) {
                Text("置顶",Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.accent)
            }
            Surface(Modifier.align(Alignment.TopEnd).padding(4.dp),shape=CircleShape,color=LocalGlass.current.sheet.copy(alpha=.92f)) {GlassFavorite(e.isFavorite(),enabled,favorite)}
        }
        Row(Modifier.padding(start=12.dp,end=12.dp,top=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            if(!grid) WardrobeCover(photos,store,outfit,composite,Modifier.size(72.dp,82.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(e.title,style=MaterialTheme.typography.titleSmall,maxLines=if(grid) 1 else 2,overflow=TextOverflow.Ellipsis)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(!grid) Text(relation,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            if(!grid && !large) GlassFavorite(e.isFavorite(),enabled,favorite)
        }
        Row(Modifier.fillMaxWidth().padding(start=12.dp,end=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
            val label=e.styleTags().firstOrNull()?.let {"# $it"} ?: if(grid) relation else if(outfit) "整套穿搭" else when(e.field("laundry","ready")) {"wash"->"待清洗";"stored"->"已收纳";else->"可穿"}
            val caption=if(!grid && e.pinned) "置顶 · $label" else label
            Text(caption,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=LocalGlass.current.accent,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(!grid && large) GlassFavorite(e.isFavorite(),enabled,favorite)
            IconButton(onClick=more,enabled=enabled,modifier=Modifier.size(48.dp).testTag("wardrobeMore:"+e.id)) {Icon(Icons.Outlined.MoreVert,"更多操作："+e.title,Modifier.size(20.dp))}
        }
    }
}

@Composable private fun WardrobeCover(photos:List<Attachment>,store:AttachmentStore,outfit:Boolean,composite:Boolean,modifier:Modifier) {
    Box(modifier.background(LocalGlass.current.selected.copy(alpha=.28f))) {
        if(photos.isEmpty()) WardrobeArt(outfit,Modifier.fillMaxSize().padding(22.dp))
        else if(composite && photos.size>1) Column(Modifier.fillMaxSize().padding(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            photos.take(4).chunked(2).forEach {row->Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                row.forEach {photo->AttachmentPreview(photo,store,Modifier.weight(1f).fillMaxHeight(),ContentScale.Fit)}
                if(row.size==1) Spacer(Modifier.weight(1f))
            }}
        } else AttachmentPreview(photos.first(),store,Modifier.fillMaxSize(),ContentScale.Fit)
    }
}

@Composable private fun WardrobeFilterSheet(outfit:Boolean,catalog:WardrobeCatalog,current:WardrobeFilter,dismiss:()->Unit,apply:(WardrobeFilter)->Unit) {
    var pending by rememberSaveable(stateSaver=wardrobeFilterSaver) {mutableStateOf(current)}
    GlassSheet(if(outfit) "筛选穿搭" else "筛选衣物",dismiss,footer={PickerActions(dismiss,{apply(pending)},"应用筛选")}) {
        if(!outfit) {
            GlassSelectField("衣物分类",listOf("all" to "全部分类")+clothingCategories.toList(),pending.category) {pending=pending.copy(category=it)}
            GlassSelectField("衣物状态",listOf("all" to "全部状态","ready" to "可穿","wash" to "待清洗","stored" to "已收纳"),pending.laundry) {pending=pending.copy(laundry=it)}
            GlassSelectField("搭配归属",listOf("all" to "全部衣物","single" to "独立单品","paired" to "已加入穿搭"),pending.membership) {pending=pending.copy(membership=it)}
        }
        GlassSelectField("场景",listOf("" to "全部场景")+catalog.occasions(outfit).map {it to it},pending.occasion) {pending=pending.copy(occasion=it)}
        GlassSelectField("风格标签",listOf("" to "全部标签")+catalog.tags(outfit).map {it to it},pending.tag) {pending=pending.copy(tag=it)}
        GlassSelectField("排序",listOf("recent" to "最近更新","oldest" to "最早添加","name" to "名称"),pending.sort) {pending=pending.copy(sort=it)}
        Text("置顶内容优先显示",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        TextButton(onClick={pending=WardrobeFilter(query=current.query,collection=current.collection)},modifier=Modifier.testTag("resetWardrobeFilter")) {Text("重置筛选")}
    }
}

@Composable internal fun WardrobeTagsEditor(value:String,change:(String)->Unit) {
    val tags=wardrobeTags(value)
    ToolField("风格标签（用逗号分隔）",value,change,tag="wardrobeTags")
    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        items(wardrobeTagSuggestions) {tag->GlassChoice(tag,tag in tags,role=Role.Checkbox,enabled=tag in tags || tags.size<10) {
            change((if(tag in tags) tags-tag else tags+tag).joinToString(","))
        }}
    }
    Text("最多 10 个标签，每个 12 字",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WardrobeDetailLabels(e:ToolEntry,data:ToolsSnapshot,open:(String)->Unit) {
    val tags=e.styleTags()
    if(tags.isNotEmpty()) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        tags.forEach {tag->Text("# $tag",Modifier.background(LocalGlass.current.selected.copy(alpha=.5f),CircleShape).padding(horizontal=12.dp,vertical=7.dp),
            style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.accent)}
    }
    if(e.isClothing()) {
        val outfits=remember(e.id,data.entries,data.links) {WardrobeCatalog(data).memberships[e.id].orEmpty()}
        if(outfits.isNotEmpty()) GlassFormSection("搭配归属","clothing-memberships") {
            outfits.forEach {outfit->GlassActionRow(outfit.title) {open(outfit.id)}}
        } else Text("独立单品 · 还没有加入穿搭",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
    }
}
