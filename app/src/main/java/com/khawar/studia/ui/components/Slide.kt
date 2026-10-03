package com.khawar.studia.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Slide to start": drag the knob most of the way across to confirm.
 * [reverse] puts the knob on the right and slides it left (used for "finish").
 */
@Composable
fun SlideToConfirm(
    text: String,
    icon: Ico,
    track: Color,
    knob: Color,
    knobContent: Color,
    hint: Color,
    modifier: Modifier = Modifier,
    edge: Color = Color.Transparent,
    reverse: Boolean = false,
    onConfirm: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val confirm by rememberUpdatedState(onConfirm)
    val off = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    // Flat, BlockIt-style: a track barely off the background,
    // a squarish knob hugging the left end, and a thin label toward the far end.
    val trackH = 70.dp
    val knobW = 76.dp
    val inset = 5.dp          // knob to the track's end
    val insetV = 7.dp         // knob to the track's top and bottom
    val trackShape = RoundedCornerShape(22.dp)

    BoxWithConstraints(
        modifier.fillMaxWidth().height(trackH).background(track, trackShape).border(1.dp, edge, trackShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = text
                onClick(label = text) { confirm(); true }
            }
    ) {
        val maxPx = with(LocalDensity.current) { (maxWidth - knobW - inset * 2).toPx() }
        val progress = if (maxPx > 0) off.value / maxPx else 0f

        Row(
            Modifier.fillMaxSize().padding(start = if (reverse) 44.dp else knobW + inset + 12.dp, end = if (reverse) knobW + inset + 12.dp else 44.dp)
                .alpha((1f - progress * 1.4f).coerceIn(0f, 1f)),
            horizontalArrangement = Arrangement.spacedBy(10.dp, if (reverse) Alignment.Start else Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // <<< SLIDE TO FINISH   /   SLIDE TO START >>>
            if (reverse) Chevrons(hint, pointLeft = true)
            T(text.uppercase(), 13.sp, FontWeight.Normal, hint, maxLines = 1, spacing = 1.6.sp)
            if (!reverse) Chevrons(hint, pointLeft = false)
        }

        Box(
            Modifier.align(if (reverse) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = inset, vertical = insetV)
                .offset { IntOffset((if (reverse) -off.value else off.value).roundToInt(), 0) }
                .size(knobW, trackH - insetV * 2)
                .background(knob, RoundedCornerShape(13.dp))
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (off.value > maxPx * .82f) {
                                haptics.confirm()
                                confirm()
                                scope.launch { off.snapTo(0f) }
                            } else scope.launch { off.animateTo(0f, spring(dampingRatio = .75f)) }
                            armed = false
                        },
                        onDragCancel = { scope.launch { off.animateTo(0f) }; armed = false },
                    ) { change, dx ->
                        change.consume()
                        val next = (off.value + if (reverse) -dx else dx).coerceIn(0f, maxPx)
                        val nowArmed = next > maxPx * .82f
                        if (nowArmed != armed) { armed = nowArmed; haptics.tick() }
                        scope.launch { off.snapTo(next) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, 18.dp, knobContent) }
    }
}

/**
 * Three thin chevrons that light up one after another in the slide direction,
 * like a soft glow travelling along them. Subtle: they never go fully dark or bright.
 */
@Composable
private fun Chevrons(color: Color, pointLeft: Boolean) {
    val t = rememberInfiniteTransition(label = "chevrons")
    // 0..4: the glow passes arrows 0, 1, 2, then rests briefly before the next sweep
    val phase by t.animateFloat(0f, 4f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "phase")
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (slot in 0..2) {
            // The glow always travels in the slide direction
            val i = if (pointLeft) 2 - slot else slot
            val glow = (1f - abs(phase - i - .5f)).coerceIn(0f, 1f)
            Canvas(Modifier.size(7.dp, 11.dp)) {
                val path = Path().apply {
                    if (pointLeft) { moveTo(size.width * .8f, 0f); lineTo(size.width * .2f, size.height / 2); lineTo(size.width * .8f, size.height) }
                    else { moveTo(size.width * .2f, 0f); lineTo(size.width * .8f, size.height / 2); lineTo(size.width * .2f, size.height) }
                }
                drawPath(
                    path, color.copy(alpha = color.alpha * (.3f + .7f * glow)),
                    style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
