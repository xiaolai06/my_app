package com.example.dailylife.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailylife.data.repository.Appearance

data class GlassPalette(val background: Color, val ink: Color, val muted: Color, val accent: Color,
    val sheet: Color, val line: Color, val selected: Color, val dark: Boolean, val solid: Boolean)
internal fun glassPalette(dark: Boolean, solid: Boolean = false) = if (dark)
    GlassPalette(Color(0xFF151A28), Color(0xFFF0F2FA), Color(0xFFB7C1D6), Color(0xFFB9C4FF),
        Color(0xFF252E42), Color(0xFF8E9BBB), Color(0xFF3C486D), true, solid)
else GlassPalette(Color(0xFFF3F5FB), Color(0xFF293442), Color(0xFF596579), Color(0xFF505FAC),
    Color(0xFFF9FAFF), Color.White, Color(0xFFE1E6FA), false, solid)
val LocalGlass = staticCompositionLocalOf { glassPalette(false) }
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable fun DailyTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = appearance.theme == "dark" || appearance.theme == "system" && isSystemInDarkTheme()
    val p = glassPalette(dark, appearance.frosted)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(primary=p.accent,onPrimary=if(dark) Color(0xFF20294D) else Color.White,
        primaryContainer=p.selected,onPrimaryContainer=p.ink,secondary=p.accent,onSecondary=if(dark) p.background else Color.White,
        secondaryContainer=p.selected,onSecondaryContainer=p.ink,tertiary=p.accent,background=p.background,
        surface=p.sheet,surfaceContainer=p.sheet,surfaceContainerHigh=p.sheet,surfaceContainerHighest=p.selected,
        onSurfaceVariant=p.muted,surfaceVariant=p.selected,outline=p.muted.copy(alpha=.55f),
        outlineVariant=p.accent.copy(alpha=.15f),onSurface=p.ink,onBackground=p.ink,
        error=if(dark) Color(0xFFFFB4B9) else Color(0xFFA43D50))
    CompositionLocalProvider(LocalGlass provides p, LocalContentColor provides p.ink,
        LocalReduceMotion provides (appearance.reducedMotion || !android.animation.ValueAnimator.areAnimatorsEnabled())) {
        MaterialTheme(colorScheme=colors,shapes=Shapes(extraSmall=RoundedCornerShape(10.dp),small=RoundedCornerShape(14.dp),
            medium=RoundedCornerShape(19.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(28.dp)),
            typography=Typography(
                headlineLarge=MaterialTheme.typography.headlineLarge.copy(fontSize=30.sp,lineHeight=38.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.6).sp),
                headlineMedium=MaterialTheme.typography.headlineMedium.copy(fontSize=26.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold),
                headlineSmall=MaterialTheme.typography.headlineSmall.copy(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
                titleLarge=MaterialTheme.typography.titleLarge.copy(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
                titleMedium=MaterialTheme.typography.titleMedium.copy(fontSize=17.sp,lineHeight=25.sp,fontWeight=FontWeight.Medium),
                titleSmall=MaterialTheme.typography.titleSmall.copy(fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium),
                bodyLarge=MaterialTheme.typography.bodyLarge.copy(fontSize=16.sp,lineHeight=26.sp),
                bodyMedium=MaterialTheme.typography.bodyMedium.copy(fontSize=15.sp,lineHeight=23.sp),
                bodySmall=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=20.sp),
                labelLarge=MaterialTheme.typography.labelLarge.copy(fontSize=14.sp,lineHeight=21.sp)
            ),content=content)
    }
}

/** Static environmental light; never animate the background or blur foreground text. */
fun Modifier.glassBackdrop(p: GlassPalette): Modifier = drawWithCache {
    val blue=Brush.radialGradient(listOf((if(p.dark) Color(0xFF35456E) else Color(0xFFD7E5F9)).copy(alpha=.8f),Color.Transparent),center=Offset(size.width*.08f,size.height*.12f),radius=(size.width*1.05f).coerceAtLeast(1f))
    val lilac=Brush.radialGradient(listOf((if(p.dark) Color(0xFF3F355D) else Color(0xFFE5DDF6)).copy(alpha=.65f),Color.Transparent),center=Offset(size.width*.95f,size.height*.5f),radius=(size.width*.9f).coerceAtLeast(1f))
    val mint=Brush.radialGradient(listOf((if(p.dark) Color(0xFF203F48) else Color(0xFFDDEEEA)).copy(alpha=.55f),Color.Transparent),center=Offset(size.width*.15f,size.height*.95f),radius=(size.width*.8f).coerceAtLeast(1f))
    onDrawBehind {drawRect(p.background);if(!p.solid) {drawRect(blue);drawRect(lilac);drawRect(mint)}}
}

/** The supplied .gs material: translucent gradient, fine rim and contact shadow. */
internal fun Modifier.glassSurface(p:GlassPalette,shape:Shape=RoundedCornerShape(19.dp),selected:Boolean=false,elevated:Boolean=false):Modifier {
    val tint=if(selected) p.selected else p.sheet
    val top=if(p.solid) tint else if(p.dark) tint.copy(alpha=.94f) else if(selected) Color(0xFFF3F3FF).copy(alpha=.94f) else Color.White.copy(alpha=.78f)
    val bottom=if(p.solid) tint else tint.copy(alpha=if(p.dark) .84f else .52f)
    return shadow(if(p.solid) 0.dp else if(elevated) 12.dp else 3.dp,shape,ambientColor=Color(0xFF7185AC).copy(alpha=.12f),spotColor=Color(0xFF7185AC).copy(alpha=.12f))
        .clip(shape).background(Brush.linearGradient(listOf(top,bottom)))
        .border(1.dp,Brush.linearGradient(if(selected) listOf(Color(0xFF839FE8).copy(alpha=.65f),Color(0xFFB49BD9).copy(alpha=.7f),Color(0xFF8EBEB6).copy(alpha=.6f)) else listOf(if(p.dark) Color(0xFF8190B4).copy(alpha=.5f) else Color.White,p.accent.copy(alpha=.06f),p.line.copy(alpha=.38f))),shape)
}
@Composable fun GlassPanel(modifier:Modifier=Modifier,radius:Int=18,selected:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.glassSurface(LocalGlass.current,RoundedCornerShape(radius.dp),selected),content=content)
}
@Composable fun GlassChoice(label:String,selected:Boolean,modifier:Modifier=Modifier,enabled:Boolean=true,role:Role=Role.RadioButton,onClick:()->Unit) {
    val p=LocalGlass.current
    val color by animateColorAsState(if(selected) p.accent else p.muted,tween(if(LocalReduceMotion.current) 0 else 180),label="choice ink")
    val fill by animateColorAsState(if(selected) p.selected.copy(alpha=.8f) else p.sheet.copy(alpha=.5f),tween(if(LocalReduceMotion.current) 0 else 180),label="choice surface")
    Box(modifier.heightIn(min=48.dp).clip(CircleShape).background(fill)
        .border(.5.dp,p.accent.copy(alpha=if(selected) .2f else .08f),CircleShape)
        .then(if(role==Role.Checkbox) Modifier.toggleable(value=selected,enabled=enabled,role=role,onValueChange={onClick()})
            else Modifier.selectable(selected=selected,enabled=enabled,role=role,onClick=onClick))
        .padding(horizontal=15.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
        Text(label,color=if(enabled) color else color.copy(alpha=.45f),style=MaterialTheme.typography.labelLarge,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
@Composable fun SectionLabel(title:String,subtitle:String?=null) {
    Column(Modifier.padding(top=12.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,modifier=Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        if(subtitle!=null) Text(subtitle,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable internal fun GlassEmptyState(title:String,body:String,modifier:Modifier=Modifier) {
    GlassPanel(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=30.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).glassSurface(LocalGlass.current,RoundedCornerShape(18.dp),true),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Edit,null,tint=LocalGlass.current.accent)}
            Text(title,style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Text(body,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
