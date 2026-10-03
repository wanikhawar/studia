package com.khawar.studia.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.khawar.studia.ui.theme.S
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DMIN = 5
private const val DMAX = 240

/**
 * A vertical drum for picking minutes. Drag it, fling it and it coasts, then
 * settles on the nearest minute. Each minute passed gives a haptic tick.
 * Setting [value] from outside (presets, +5/-5) spins it to the new value.
 */
@Composable
fun Dial(value: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = S.c
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { 7.dp.toPx() }       // travel per minute = gap between lines
    val pos = remember { Animatable(value.toFloat()) }
    val emit by rememberUpdatedState(onValue)
    // True from the moment a finger touches the drum until it has settled.
    // Only then does the drum report values; when a preset or +5/-5 sets the
    // value, it just spins to it, so passing minutes can't overwrite the target.
    var userDriving by remember { mutableStateOf(false) }
    var gesture by remember { mutableIntStateOf(0) }

    LaunchedEffect(value) {
        if (!userDriving && value != pos.targetValue.roundToInt()) {
            scope.launch { pos.animateTo(value.toFloat(), spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) }
        }
    }
    // Every whole minute the drum passes: tick, and report it if the user is turning it.
    LaunchedEffect(Unit) {
        snapshotFlow { pos.value.roundToInt().coerceIn(DMIN, DMAX) }
            .distinctUntilChanged()
            .drop(1)
            .collect { v -> haptics.tick(); if (userDriving) emit(v) }
    }

    fun rubber(raw: Float) = when {
        raw < DMIN -> DMIN - min(3f, (DMIN - raw) * .3f)
        raw > DMAX -> DMAX + min(3f, (raw - DMAX) * .3f)
        else -> raw
    }

    fun settle(velocity: Float) {
        val mine = gesture
        scope.launch {
            val predicted = exponentialDecay<Float>(frictionMultiplier = 1.6f).calculateTargetValue(pos.value, velocity)
            val target = predicted.roundToInt().coerceIn(DMIN, DMAX).toFloat()
            try {
                pos.animateTo(target, spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow), initialVelocity = velocity)
            } finally {
                if (gesture == mine) {            // not interrupted by a newer touch
                    emit(pos.value.roundToInt().coerceIn(DMIN, DMAX))
                    userDriving = false
                }
            }
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .border(1.2.dp, c.ink3, RoundedCornerShape(22.dp))
            .semantics {
                contentDescription = "Dial: drag up or down to change minutes"
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), DMIN.toFloat()..DMAX.toFloat())
                setProgress { v -> emit(v.roundToInt().coerceIn(DMIN, DMAX)); true }
            }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var raw = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        gesture++
                        userDriving = true
                        tracker.resetTracking()
                        raw = pos.value
                        scope.launch { pos.stop() }
                    },
                    onDragEnd = { settle(-tracker.calculateVelocity().y / stepPx) },
                    onDragCancel = { settle(0f) },
                ) { change, dy ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    raw -= dy / stepPx
                    val next = rubber(raw)
                    scope.launch { pos.snapTo(next) }
                }
            }
    ) {
        // Flat, BlockIt-style: evenly spaced faint lines that scroll past a fixed
        // orange marker, fading out only right at the top and bottom edges.
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height; val w = size.width
            val centre = h / 2
            val left = w * .25f; val lineW = w * .5f
            val thick = 1.5.dp.toPx()
            val fade = h * .12f
            val p = pos.value
            val half = (centre / stepPx).toInt() + 1
            val base = p.roundToInt()
            for (m in base - half..base + half) {
                if (m < DMIN || m > DMAX) continue
                val y = centre + (m - p) * stepPx
                if (y < thick || y > h - thick) continue
                val alpha = (minOf(y, h - y) / fade).coerceIn(0f, 1f)
                drawRoundRect(
                    color = c.tile2.copy(alpha = alpha),
                    topLeft = Offset(left, y - thick / 2),
                    size = Size(lineW, thick),
                    cornerRadius = CornerRadius(thick / 2),
                )
            }
            val nh = 5.dp.toPx()
            drawRoundRect(c.accent, Offset(left, centre - nh / 2), Size(lineW, nh), CornerRadius(nh / 2))
        }
    }
}
