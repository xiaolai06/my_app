package com.example.dailylife.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Video-style soft glass appearance on a controlled page background.
 * This surface does not capture, blur, or refract the backdrop.
 * Supply accessible interactive controls inside [content].
 */
@Composable
fun SoftGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit,
) {
    val tint = if (darkTheme) Color(0xFF24302A) else Color.White
    val topAlpha = if (darkTheme) 0.92f else 0.84f
    val bottomAlpha = if (darkTheme) 0.96f else 0.94f
    val rim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (darkTheme) 0.24f else 0.95f),
            Color.White.copy(alpha = if (darkTheme) 0.06f else 0.20f),
            Color(0xFF82948C).copy(alpha = 0.18f),
        ),
    )

    Box(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = shape, clip = false)
            .clip(shape),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = topAlpha), tint.copy(alpha = bottomAlpha)),
                ),
            )
            if (size.minDimension > 0f) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFB8DDEB).copy(alpha = if (darkTheme) 0.07f else 0.14f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.18f, 0f),
                        radius = size.maxDimension * 0.65f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD2C8EC).copy(alpha = if (darkTheme) 0.06f else 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.78f, size.height * 0.1f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            }
        }
        Box(Modifier.matchParentSize().border(0.75.dp, rim, shape))
        content()
    }
}
