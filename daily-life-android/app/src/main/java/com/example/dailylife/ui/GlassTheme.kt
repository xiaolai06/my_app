package com.example.dailylife.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.example.dailylife.data.repository.Appearance

data class GlassPalette(val background: Color, val ink: Color, val muted: Color, val accent: Color,
    val sheet: Color, val line: Color, val selected: Color, val dark: Boolean, val solid: Boolean)
val LocalGlass = staticCompositionLocalOf {
    GlassPalette(Color(0xFFE9EFF6), Color(0xFF192638), Color(0xFF3D4E65), Color(0xFF16499F),
        Color(0xFFF6F9FE), Color.White, Color(0xFFD8E6FA), false, false)
}
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable fun DailyTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = appearance.theme == "dark" || appearance.theme == "system" && isSystemInDarkTheme()
    val palette = if (dark) GlassPalette(Color(0xFF111720), Color(0xFFEDF2FB), Color(0xFFC6D1E2), Color(0xFFB5D0FF),
        Color(0xFF232E3E), Color(0xFF637286), Color(0xFF344A69), true, appearance.frosted)
    else GlassPalette(Color(0xFFE9EFF6), Color(0xFF192638), Color(0xFF3D4E65), Color(0xFF16499F),
        Color(0xFFF6F9FE), Color.White, Color(0xFFD8E6FA), false, appearance.frosted)
    val colors = if (dark) darkColorScheme(primary = palette.accent,onPrimary=Color(0xFF142E55),primaryContainer=palette.selected,onPrimaryContainer=palette.ink,
        secondary=palette.accent,secondaryContainer=palette.selected,onSecondaryContainer=palette.ink,tertiary=palette.accent,
        background = palette.background,surface = palette.sheet,onSurfaceVariant=palette.muted,surfaceVariant=palette.selected.copy(alpha=.3f),
        outline=palette.muted.copy(alpha=.55f),outlineVariant=palette.line.copy(alpha=.3f),onSurface = palette.ink, onBackground = palette.ink)
    else lightColorScheme(primary = palette.accent,onPrimary=Color.White,primaryContainer=palette.selected,onPrimaryContainer=palette.ink,
        secondary=palette.accent,secondaryContainer=palette.selected,onSecondaryContainer=palette.ink,tertiary=palette.accent,
        background = palette.background,surface = palette.sheet,onSurfaceVariant=palette.muted,surfaceVariant=palette.selected.copy(alpha=.3f),
        outline=palette.muted.copy(alpha=.55f),outlineVariant=palette.accent.copy(alpha=.15f),onSurface = palette.ink, onBackground = palette.ink)
    CompositionLocalProvider(LocalGlass provides palette, LocalContentColor provides palette.ink, LocalReduceMotion provides
        (appearance.reducedMotion || !android.animation.ValueAnimator.areAnimatorsEnabled())) {
        MaterialTheme(colorScheme = colors, typography = Typography(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp)
        ), content = content)
    }
}

@Composable fun GlassPanel(modifier: Modifier = Modifier, radius: Int = 24, selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val p = LocalGlass.current
    val color by animateColorAsState(if (selected) p.selected else p.sheet,
        tween(if (LocalReduceMotion.current) 0 else 160), label = "glass selection")
    val shape = RoundedCornerShape(radius.dp)
    Column(modifier.shadow(if (p.solid) 0.dp else 7.dp, shape, ambientColor = Color(0x18234168), spotColor = Color(0x15234168))
        .clip(shape).background(if (p.solid) Brush.linearGradient(listOf(color, color)) else Brush.linearGradient(
            if (p.dark) listOf(color.copy(alpha = .96f), color.copy(alpha = .76f), color.copy(alpha = .94f))
            else listOf(Color.White.copy(alpha = .95f), color.copy(alpha = .77f), color.copy(alpha = .95f))))
        .border(1.dp, Brush.linearGradient(listOf(p.line.copy(alpha = .95f), p.line.copy(alpha = .16f), p.line.copy(alpha = .7f))), shape),
        content = content)
}

@Composable fun GlassChoice(label: String, selected: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit) {
    val p = LocalGlass.current
    val fill by animateColorAsState(if (selected) p.selected else p.sheet.copy(alpha = .7f),
        tween(if (LocalReduceMotion.current) 0 else 160), label = "choice surface")
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(16.dp), color = fill,
        contentColor = if (selected) p.accent else p.ink,
        border = BorderStroke(1.dp, if (selected) p.accent.copy(alpha = .32f) else p.line.copy(alpha = .75f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable fun SectionLabel(title: String, subtitle: String? = null) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, color = LocalGlass.current.muted, style = MaterialTheme.typography.bodySmall)
    }
}
