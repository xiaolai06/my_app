package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.newId
import com.example.dailylife.feature.ledger.LedgerUi
import com.example.dailylife.feature.ledger.LedgerViewModel

/** Original vector marks: category names select a motif; no brand assets are used. */
@Composable internal fun LedgerCategoryIcon(name:String,modifier:Modifier=Modifier) {
    val ink=LocalGlass.current.accent
    Canvas(modifier.size(22.dp)) {
        val scale=size.minDimension/24f
        fun point(x:Float,y:Float)=Offset(x*scale,y*scale)
        fun line(x:Float,y:Float,xx:Float,yy:Float)=drawLine(ink,point(x,y),point(xx,yy),1.7f*scale,StrokeCap.Round)
        fun rect(x:Float,y:Float,w:Float,h:Float)=drawRoundRect(ink,point(x,y),Size(w*scale,h*scale),androidx.compose.ui.geometry.CornerRadius(2*scale),style=Stroke(1.7f*scale))
        when {
            name.contains("日期")-> {
                rect(3f,5f,18f,16f);line(3f,10f,21f,10f);line(8f,3f,8f,7f);line(16f,3f,16f,7f)
                drawCircle(ink,1.2f*scale,point(8f,15f));drawCircle(ink,1.2f*scale,point(13f,15f))
            }
            name.contains("银行")||name.contains("账户")-> {rect(3f,5f,18f,15f);line(3f,10f,21f,10f);line(7f,15f,11f,15f)}
            name.contains("餐")||name.contains("食")||name.contains("饮")||name.contains("饭")-> {
                line(5f,3f,5f,9f);line(8f,3f,8f,9f);line(11f,3f,11f,9f);line(5f,9f,11f,9f);line(8f,9f,8f,21f)
                line(18f,3f,18f,21f);line(15f,3f,15f,12f);line(15f,12f,18f,12f)
            }
            name.contains("交通")||name.contains("车")||name.contains("出行")-> {
                rect(4f,5f,16f,13f);line(4f,11f,20f,11f);line(8f,18f,8f,21f);line(16f,18f,16f,21f)
                drawCircle(ink,1.1f*scale,point(8f,15f));drawCircle(ink,1.1f*scale,point(16f,15f))
            }
            name.contains("购物")||name.contains("衣")||name.contains("日用")-> {
                rect(4f,8f,16f,13f);drawArc(ink,180f,180f,false,point(8f,2f),Size(8*scale,12*scale),style=Stroke(1.7f*scale))
            }
            name.contains("住")||name.contains("房")-> {
                val p=Path().apply {moveTo(3*scale,11*scale);lineTo(12*scale,3*scale);lineTo(21*scale,11*scale)}
                drawPath(p,ink,style=Stroke(1.7f*scale));line(5f,10f,5f,21f);line(5f,21f,19f,21f);line(19f,21f,19f,10f);rect(10f,14f,4f,7f)
            }
            name.contains("医")||name.contains("健康")-> {
                rect(3f,4f,18f,17f);line(12f,8f,12f,17f);line(8f,12.5f,16f,12.5f)
            }
            name.contains("娱乐")||name.contains("休闲")-> {
                rect(3f,7f,18f,13f);line(6f,12f,11f,12f);line(8.5f,9.5f,8.5f,14.5f);drawCircle(ink,1.2f*scale,point(16f,12f));line(12f,7f,12f,3f)
            }
            name.contains("学习")||name.contains("教育")||name.contains("书")-> {
                rect(3f,4f,18f,17f);line(12f,4f,12f,21f);line(6f,9f,9f,9f);line(15f,9f,18f,9f)
            }
            name.contains("工资")||name.contains("收入")||name.contains("奖金")-> {
                rect(3f,7f,18f,14f);rect(8f,3f,8f,4f);line(3f,12f,21f,12f);line(12f,10f,12f,14f)
            }
            else-> {rect(3f,3f,7f,7f);rect(14f,3f,7f,7f);rect(3f,14f,7f,7f);rect(14f,14f,7f,7f)}
        }
    }
}

@Composable internal fun LedgerEntrySelect(label:String,value:String,iconName:String,enabled:Boolean=true,tag:String,select:()->Unit) {
    val p=LocalGlass.current
    Surface(onClick=select,enabled=enabled,color=Color.Transparent,shape=RoundedCornerShape(18.dp),
        modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).glassSurface(p,RoundedCornerShape(18.dp)).testTag(tag)) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(p.selected,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {LedgerCategoryIcon(iconName)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(label,style=MaterialTheme.typography.bodySmall,color=p.muted)
                Text(value,style=MaterialTheme.typography.bodyLarge,color=p.ink)
            }
            Text("⌄",color=p.accent,modifier=Modifier.clearAndSetSemantics {})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LedgerCategoryPicker(ui:LedgerUi,vm:LedgerViewModel,direction:String,selected:String,dismiss:()->Unit,choose:(String)->Unit) {
    var search by rememberSaveable {mutableStateOf("")}
    var adding by rememberSaveable {mutableStateOf(false)}
    var name by rememberSaveable {mutableStateOf("")}
    var pendingSelection by rememberSaveable {mutableStateOf<String?>(null)}
    val latestChoose by rememberUpdatedState(choose)
    LaunchedEffect(pendingSelection,ui.busy,ui.data.categories) {
        pendingSelection?.takeIf {id->!ui.busy&&ui.data.categories.any {it.id==id}}?.let {id->pendingSelection=null;latestChoose(id)}
    }
    val categories=ui.data.categories.filter {it.active&&it.direction==direction&&ui.data.categories.none {child->child.parentId==it.id}}
    GlassSheet(if(adding) "新增分类" else "${ledgerType(direction)}分类",{if(!ui.busy) dismiss()},dismissEnabled=!ui.busy,scrollContent=adding,
        footer=if(adding) ({
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=!ui.busy,onClick={adding=false}) {Text("返回分类")}
                GlassButton(enabled=!ui.busy&&name.isNotBlank(),modifier=Modifier.weight(1f).testTag("saveNewLedgerCategory"),onClick={
                    val categoryId=newId()
                    vm.run("分类已添加",onSuccess={pendingSelection=categoryId}) {saveCategory(name,direction,id=categoryId)}
                }) {Text(if(ui.busy) "正在保存…" else "添加并选用")}
            }
        }) else null) {
        if(adding) {
            LedgerEntrySelect("分类预览",name.ifBlank {"自定义分类"},name,enabled=false,tag="newCategoryPreview") {}
            GlassTextField(name,{name=it},enabled=!ui.busy,label={Text("分类名称")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("newLedgerCategoryName"))
            Text("保存在本机，可在${ledgerType(direction)}分类中重复使用。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        } else {
            GlassTextField(search,{search=it},enabled=!ui.busy,label={Text("搜索分类")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("searchLedgerCategory"))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("分类",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                TextButton(enabled=!ui.busy,onClick={adding=true},modifier=Modifier.testTag("addLedgerCategory")) {Text("＋ 自定义")}
            }
            val filtered=categories.filter {ui.data.categoryName(it.id).contains(search,true)}
            if(filtered.isEmpty()) Text("没有匹配分类，可以添加自己的分类。",color=LocalGlass.current.muted)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f,fill=false)) {
                val largeFont=LocalDensity.current.fontScale>1.3f
                val columns=if(largeFont||maxWidth<320.dp) 2 else 4
                val nameHeight=with(LocalDensity.current) {MaterialTheme.typography.labelLarge.lineHeight.toDp()}*2
                val cellHeight=maxOf(if(largeFont) 156.dp else 112.dp,nameHeight+64.dp)
                LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxWidth().testTag("ledgerCategoryGrid"),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=8.dp)) {
                    items(filtered,key={it.id}) {category->
                        val picked=category.id==selected||(selected.isBlank()&&category.protected)
                        Column(Modifier.fillMaxWidth().height(cellHeight).glassSurface(LocalGlass.current,RoundedCornerShape(16.dp),picked)
                            .selectable(picked,enabled=!ui.busy,role=Role.Button) {choose(category.id)}.testTag("ledgerCategory:"+category.id)
                            .padding(horizontal=8.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterVertically)) {
                            LedgerCategoryIcon(category.name,Modifier.size(25.dp))
                            Box(Modifier.fillMaxWidth().height(nameHeight),contentAlignment=Alignment.Center) {
                                Text(ui.data.categoryName(category.id),modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis,
                                    color=if(picked) LocalGlass.current.accent else LocalGlass.current.ink,fontWeight=if(picked) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LedgerCalendarPicker(value:String,dismiss:()->Unit,choose:(String)->Unit) {
    GlassDatePicker(value,dismiss,confirmLabel="确认日期",confirmTag="ledgerCalendarConfirm",cancelTag="ledgerCalendarCancel",choose=choose)
}
