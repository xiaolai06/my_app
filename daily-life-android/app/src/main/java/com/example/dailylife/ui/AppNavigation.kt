package com.example.dailylife.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.*

/** Stable route keys keep restored navigation compatible; labels follow My Life. */
internal val rootDestinations=listOf("今天" to "首页","记录" to "记录","计划" to "计划","账本" to "账本","功能箱" to "我的")

@Composable fun DailyNavigation(page:String,backdrop:HazeState?=null,select:(String)->Unit) {
    val p=LocalGlass.current
    val duration=if(LocalReduceMotion.current) 0 else GlassMotion.SelectionMs
    val motion=tween<Dp>(duration,easing=FastOutSlowInEasing)
    val density=LocalDensity.current
    val labelStyle=MaterialTheme.typography.labelLarge.copy(fontSize=14.sp,fontWeight=FontWeight.SemiBold)
    val measurer=rememberTextMeasurer()
    val labelWidth=with(density) {rootDestinations.maxOf {measurer.measure(it.second,labelStyle).size.width}.toDp()}
    val shape=RoundedCornerShape(32.dp)
    val effect=if(backdrop!=null && !p.solid && Build.VERSION.SDK_INT>=31) Modifier.hazeEffect(backdrop,
        HazeStyle(backgroundColor=p.background,tint=HazeTint(p.sheet.copy(alpha=.76f)),blurRadius=14.dp,noiseFactor=0f)) else Modifier
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max=560.dp).fillMaxWidth().clip(shape).then(effect)
            .background(p.sheet.copy(alpha=if(p.solid) 1f else .70f)).padding(5.dp).testTag("myLifeNavigation")) {
            val expandedWidth=labelWidth+62.dp
            val inlineLabel=maxWidth-expandedWidth>=48.dp*4
            val selectedWidth=if(inlineLabel) expandedWidth else maxWidth/5
            val otherWidth=if(inlineLabel) (maxWidth-selectedWidth)/4 else selectedWidth
            val selectedIndex=rootDestinations.indexOfFirst {it.first==page}.coerceAtLeast(0)
            val offset by animateDpAsState(otherWidth*selectedIndex,motion,label="navigation highlight position")
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(50.dp)) {
                    Box(Modifier.offset {IntOffset(offset.roundToPx(),0)}.width(selectedWidth).height(50.dp).testTag("navigationHighlight")
                        .shadow(if(p.solid) 0.dp else 3.dp,CircleShape,ambientColor=p.accent.copy(alpha=.10f),spotColor=p.accent.copy(alpha=.10f))
                        .clip(CircleShape).background(Brush.linearGradient(listOf(p.sheet,p.selected.copy(alpha=.28f),p.sheet)))
                        .border(.75.dp,Brush.linearGradient(listOf(p.accent.copy(alpha=.18f),p.line.copy(alpha=.65f),p.accent.copy(alpha=.10f))),CircleShape))
                    Row(Modifier.fillMaxSize().selectableGroup(),verticalAlignment=Alignment.CenterVertically) {
                        rootDestinations.forEach {(route,label)->
                            val selected=route==page
                            val width by animateDpAsState(if(selected) selectedWidth else otherWidth,motion,label="navigation item width")
                            val ink by animateColorAsState(if(selected) p.accent else p.ink,tween(duration),label="navigation icon color")
                            val labelAlpha by animateFloatAsState(if(selected) 1f else 0f,tween(if(LocalReduceMotion.current) 0 else 180),label="navigation label opacity")
                            val icon=when(route) {"今天"->Icons.Outlined.Home;"记录"->Icons.Outlined.Edit;"计划"->Icons.Outlined.DateRange;"账本"->Icons.Outlined.List;else->Icons.Outlined.Person}
                            Row(Modifier.width(width).height(50.dp).clip(CircleShape)
                                .selectable(selected,onClick={select(route)},role=Role.Tab)
                                .semantics {contentDescription=label}.testTag("root:"+route),
                                horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).background(if(selected) ink else Color.Transparent,CircleShape),contentAlignment=Alignment.Center) {
                                    Icon(icon,null,Modifier.size(20.dp),tint=if(selected) (if(p.dark) p.background else Color.White) else ink)
                                }
                                if(inlineLabel && labelAlpha>0f) {
                                    Spacer(Modifier.width(7.dp))
                                    Text(label,style=labelStyle,color=p.ink,maxLines=1,softWrap=false,modifier=Modifier.graphicsLayer {alpha=labelAlpha}.clearAndSetSemantics {})
                                }
                            }
                        }
                    }
                }
                // Large fonts retain every 48dp target and the complete destination name.
                if(!inlineLabel) Text(rootDestinations[selectedIndex].second,style=labelStyle,color=p.accent,
                    modifier=Modifier.padding(top=2.dp,bottom=3.dp).clearAndSetSemantics {})
            }
        }
    }
}
