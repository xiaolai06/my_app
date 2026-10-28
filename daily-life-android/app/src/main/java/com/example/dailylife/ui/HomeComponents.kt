package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val LocalHomeColor = staticCompositionLocalOf { true }
@Composable internal fun HomeAmountVisibility(hidden:Boolean) {
    val ink=LocalGlass.current.muted
    Canvas(Modifier.size(20.dp).semantics {contentDescription=if(hidden) "显示金额" else "隐藏金额"}) {
        val w=size.width;val h=size.height
        val eye=Path().apply {moveTo(w*.08f,h*.5f);cubicTo(w*.3f,h*.13f,w*.7f,h*.13f,w*.92f,h*.5f);cubicTo(w*.7f,h*.87f,w*.3f,h*.87f,w*.08f,h*.5f)}
        drawPath(eye,ink,style=Stroke(w*.075f))
        drawCircle(ink,w*.13f,Offset(w*.5f,h*.5f),style=Stroke(w*.075f))
        if(hidden) drawLine(ink,Offset(w*.1f,h*.1f),Offset(w*.9f,h*.9f),w*.085f,StrokeCap.Round)
    }
}
internal fun homeIcon(kind:String):ImageVector = when(kind) {
    "weather" -> Icons.Default.Place
    "tasks" -> Icons.Default.CheckCircle
    "events" -> Icons.Default.DateRange
    "habits" -> Icons.Default.Favorite
    "life" -> Icons.Default.Notifications
    "budget" -> Icons.Default.AccountBox
    "recent" -> Icons.Default.Create
    "favorites" -> Icons.Default.Star
    "suggestions" -> Icons.Default.Face
    "focus" -> Icons.Default.PlayArrow
    else -> Icons.AutoMirrored.Filled.List
}
@Composable internal fun homeTint(kind:String):Color {
    val dark=LocalGlass.current.dark
    return when(kind) {
        "habits","budget" -> if(dark) Color(0xFF9EDBCB) else Color(0xFF327567)
        "life","favorites" -> if(dark) Color(0xFFEBC392) else Color(0xFF99612D)
        "suggestions","recent" -> if(dark) Color(0xFFD2B8EE) else Color(0xFF8260A1)
        else -> LocalGlass.current.accent
    }
}
@Composable internal fun HomeCard(kind:String,title:String,count:String?=null,onOpen:()->Unit,content:@Composable ColumnScope.()->Unit) {
    val tint=homeTint(kind)
    GlassPanel(Modifier.fillMaxWidth().testTag("homeSection:$kind")) {
        Column(Modifier.background(if(LocalHomeColor.current) tint.copy(alpha=.045f) else Color.Transparent).padding(bottom=8.dp)) {
            Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(tint.copy(alpha=.12f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
                    Icon(homeIcon(kind),null,Modifier.size(19.dp),tint=tint)
                }
                Text(title,Modifier.weight(1f).padding(horizontal=10.dp).semantics {heading()},style=MaterialTheme.typography.titleMedium)
                count?.let {Text(it,color=tint,style=MaterialTheme.typography.labelLarge)}
                IconButton(onClick=onOpen,modifier=Modifier.size(48.dp)) {Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,"查看$title",tint=LocalGlass.current.muted)}
            }
            content()
        }
    }
}

@Composable internal fun HomeRow(title:String,subtitle:String,onClick:()->Unit) {
    Surface(onClick=onClick,color=Color.Transparent,modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {
        Row(Modifier.padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleSmall)
                if(subtitle.isNotBlank()) Text(subtitle,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null,Modifier.padding(start=8.dp).size(18.dp),tint=LocalGlass.current.muted)
        }
    }
}

/** All modules use the same staged date selection and large-font day list. */
@Composable internal fun HomeCalendar(value:LocalDate,marked:Set<String>,dismiss:()->Unit,choose:(LocalDate)->Unit) {
    GlassDatePicker(value.toString(),dismiss,marked,confirmTag="confirmHomeDate") {choose(LocalDate.parse(it))}
}
