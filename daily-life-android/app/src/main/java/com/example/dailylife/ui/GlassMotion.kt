package com.example.dailylife.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Shared timings; frame updates stay in graphics/layout rather than recomputing page data. */
internal object GlassMotion {
    const val PageMs=220
    const val SelectionMs=220
    const val PressMs=100
    const val ReleaseMs=160
}

internal fun Modifier.blockGlassInput()=clearAndSetSemantics {}.pointerInput(Unit) {
    awaitPointerEventScope {while(true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}}
}

@Composable internal fun <T> GlassPageTransition(targetState:T,modifier:Modifier=Modifier,label:String,
    content:@Composable (T)->Unit) {
    val reduce=LocalReduceMotion.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(targetState) {focus.clearFocus();keyboard?.hide()}
    val transition=updateTransition(targetState,label)
    val visible=remember {mutableStateListOf(targetState)}
    if(targetState !in visible) visible.add(targetState)
    if(!transition.isRunning && transition.currentState==transition.targetState) visible.removeAll {it!=targetState}
    Box(modifier) {
        visible.forEach {page->key(page) {
            val opacity=transition.animateFloat(
                transitionSpec={tween(if(reduce) 0 else GlassMotion.PageMs,easing=FastOutSlowInEasing)},label="$label opacity"
            ) {if(it==page) 1f else 0f}
            val reveal=transition.animateFloat(
                transitionSpec={tween(if(reduce) 0 else GlassMotion.PageMs,easing=FastOutSlowInEasing)},label="$label reveal"
            ) {if(it==page) 0f else 1f}
            // Own the sibling layers so a rapidly revisited page always receives the next touch.
            // Retiring pages stay drawn, but never receive touch or TalkBack focus.
            Box(Modifier.fillMaxSize().zIndex(if(page==targetState) 1f else 0f)
                .graphicsLayer {alpha=opacity.value;translationY=if(reduce) 0f else 6.dp.toPx()*reveal.value}
                .then(if(page==targetState) Modifier else Modifier.blockGlassInput())) {content(page)}
        }}
    }
}
