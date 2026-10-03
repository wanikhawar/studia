package com.khawar.studia.ui.components

import android.os.Build
import android.os.SystemClock
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khawar.studia.ui.theme.Manrope
import com.khawar.studia.ui.theme.S

// ---------- haptics ----------

/**
 * Haptics through the vibration motor directly. (View.performHapticFeedback is
 * skipped by many phones and is off whenever the system "Touch feedback"
 * setting is off, which is why it felt like there was no feedback at all.)
 * Uses the phone's own crisp tick/click effects where it has them.
 */
class Haptics(context: Context, private val view: View, private val enabled: () -> Boolean, private val sound: () -> Boolean) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    private val tickEffect: VibrationEffect? = when {
        vibrator == null || !vibrator.hasVibrator() -> null
        Build.VERSION.SDK_INT >= 30 && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) ->
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f).compose()
        Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        else -> VibrationEffect.createOneShot(10, 120)
    }
    private val clickEffect: VibrationEffect? = when {
        vibrator == null || !vibrator.hasVibrator() -> null
        Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        else -> VibrationEffect.createOneShot(18, 180)
    }
    private val heavyEffect: VibrationEffect? = when {
        vibrator == null || !vibrator.hasVibrator() -> null
        Build.VERSION.SDK_INT >= 29 -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else -> VibrationEffect.createOneShot(35, 255)
    }

    private fun play(effect: VibrationEffect?) {
        if (!enabled() || effect == null) return
        runCatching { vibrator?.vibrate(effect) }
    }

    private var lastTick = 0L
    /** One detent of the dial. Throttled so a fast spin stays a series of clicks, not a buzz. */
    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 30) return
        lastTick = now
        play(tickEffect)
        if (sound()) view.playSoundEffect(SoundEffectConstants.CLICK)
    }
    fun click() = play(clickEffect)
    fun confirm() = play(heavyEffect)
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics not provided") }

// ---------- text ----------

@Composable
fun T(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Medium,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    spacing: TextUnit = TextUnit.Unspecified,
    decoration: TextDecoration? = null,
) = Text(
    text, modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = Manrope,
    letterSpacing = spacing, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
    textDecoration = decoration, lineHeight = size * 1.35f,
)

/** Small uppercase label, like "SESSION LENGTH". */
@Composable
// Secondary text at 75% of the text colour: at least 4.5:1 contrast on every surface in both themes.
fun Lbl(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current.copy(alpha = .75f), maxLines: Int = 1) =
    T(text.uppercase(), 10.5.sp, FontWeight.ExtraBold, color, modifier, maxLines = maxLines, spacing = 1.4.sp)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.5.sp, align: TextAlign? = null) =
    T(text, size, FontWeight.SemiBold, LocalContentColor.current.copy(alpha = .75f), modifier, align = align)

// ---------- pixel numerals and wordmark ----------

private val GLYPHS = mapOf(
    '0' to listOf("1111", "1001", "1001", "1001", "1111"), '1' to listOf("0110", "0010", "0010", "0010", "0111"),
    '2' to listOf("1111", "0001", "1111", "1000", "1111"), '3' to listOf("1111", "0001", "0111", "0001", "1111"),
    '4' to listOf("1001", "1001", "1111", "0001", "0001"), '5' to listOf("1111", "1000", "1111", "0001", "1111"),
    '6' to listOf("1111", "1000", "1111", "1001", "1111"), '7' to listOf("1111", "0001", "0001", "0001", "0001"),
    '8' to listOf("1111", "1001", "1111", "1001", "1111"), '9' to listOf("1111", "1001", "1111", "0001", "1111"),
    ':' to listOf("0", "1", "0", "1", "0"), '.' to listOf("0", "0", "0", "0", "1"),
)

private fun units(text: String, map: Map<Char, List<String>>) =
    maxOf(1, text.sumOf { (map[it]?.get(0)?.length ?: -1) + 1 } - 1)

/**
 * Big blocky numbers drawn from 4x5 bitmaps. Fills the width available,
 * up to [height] tall. Colons and dots use the softer [soft] colour.
 */
@Composable
fun PixelText(
    text: String,
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    soft: Color = S.c.ink3,
) {
    val u = units(text, GLYPHS)
    Canvas(
        modifier.widthIn(max = height * u / 5f).fillMaxWidth().aspectRatio(u / 5f)
            .semantics { contentDescription = text }
    ) {
        val cell = size.height / 5f
        var x = 0
        for (ch in text) {
            val g = GLYPHS[ch] ?: continue
            val col = if (ch == ':' || ch == '.') soft else color
            g.forEachIndexed { r, row ->
                row.forEachIndexed { c, b ->
                    if (b == '1') drawRect(col, Offset((x + c) * cell, r * cell), Size(cell * 1.03f, cell * 1.03f))
                }
            }
            x += g[0].length + 1
        }
    }
}

private val WORD = mapOf(
    's' to listOf("0000", "0000", "1111", "1000", "1111", "0001", "1111"),
    't' to listOf("010", "010", "111", "010", "010", "010", "011"),
    'u' to listOf("0000", "0000", "1001", "1001", "1001", "1001", "1111"),
    'd' to listOf("0001", "0001", "1111", "1001", "1001", "1001", "1111"),
    'i' to listOf("2", "0", "1", "1", "1", "1", "1"),
    'a' to listOf("0000", "0000", "1111", "0001", "1111", "1001", "1111"),
)

/** The "studia" logo, drawn in pixel blocks with an orange dot on the i. */
@Composable
fun Wordmark(height: Dp, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val accent = S.c.accent
    val text = "studia"
    val u = units(text, WORD)
    Canvas(modifier.size(height * u / 7f, height).semantics { contentDescription = "Studia" }) {
        val cell = size.height / 7f
        var x = 0
        for (ch in text) {
            val g = WORD.getValue(ch)
            g.forEachIndexed { r, row ->
                row.forEachIndexed { c, b ->
                    if (b != '0') drawRect(if (b == '2') accent else color, Offset((x + c) * cell, r * cell), Size(cell * 1.03f, cell * 1.03f))
                }
            }
            x += g[0].length + 1
        }
    }
}

/** A row of square blocks: dark for done, orange for in progress. */
@Composable
fun Blocks(
    n: Int,
    done: Int,
    part: Int = 0,
    modifier: Modifier = Modifier,
    empty: Color = S.c.tile2,
    doneColor: Color = S.c.ink,
    partColor: Color = S.c.accent,
    gap: Dp = 3.dp,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(n) { i ->
            val col = when { i < done -> doneColor; i < done + part -> partColor; else -> empty }
            Box(Modifier.weight(1f).aspectRatio(1f).background(col, RoundedCornerShape(2.dp)))
        }
    }
}

// ---------- surfaces ----------

/** Press feedback used everywhere: a slight shrink plus a haptic click. */
fun Modifier.press(role: Role = Role.Button, haptic: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, label = "press")
    val h = LocalHaptics.current
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, role = role) { if (haptic) h.click(); onClick() }
}

val CardShape = RoundedCornerShape(26.dp)
val TileShape = RoundedCornerShape(18.dp)

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    gap: Dp = 12.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.fillMaxWidth()
    if (onClick != null) m = m.press(onClick = onClick)
    Column(
        m.clip(CardShape).background(S.c.card).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
fun Tile(
    modifier: Modifier = Modifier,
    on: Boolean = false,
    padding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit,
) {
    val c = S.c
    var m = modifier
    if (onClick != null) m = m.press(onClick = onClick)
    CompositionLocalProvider(LocalContentColor provides if (on) c.onSel else c.ink) {
        Box(m.clip(TileShape).background(if (on) c.sel else c.tile).padding(padding), contentAlignment = contentAlignment) { content() }
    }
}

@Composable
fun Pill(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    var m = modifier
    if (onClick != null) m = m.press(onClick = onClick)
    Row(
        m.clip(CircleShape).background(S.c.card).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

@Composable
fun AccentSquare(size: Dp = 9.dp) = Box(Modifier.size(size).background(S.c.accent, RoundedCornerShape(2.dp)))

enum class BtnKind { PRIMARY, GHOST, WARN }

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.PRIMARY, onClick: () -> Unit) {
    val c = S.c
    val (bg, fg) = when (kind) {
        BtnKind.PRIMARY -> c.sel to c.onSel
        BtnKind.GHOST -> c.tile to c.ink
        BtnKind.WARN -> c.accent to c.onAccent
    }
    Box(
        modifier.press(onClick = onClick).clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 14.sp, FontWeight.Bold, fg, align = TextAlign.Center) }
}

/** Square dark button with a "+" (add subject, add topic). */
@Composable
fun PlusButton(description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = S.c
    Box(
        modifier.size(46.dp).press(onClick = onClick).clip(RoundedCornerShape(14.dp)).background(c.sel)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(Ico.PLUS, 18.dp, c.onSel) }
}

@Composable
fun PillSwitch(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = S.c
    val h = LocalHaptics.current
    val x by animateDpAsState(if (checked) 25.dp else 3.dp, label = "switch")
    Box(
        Modifier.size(52.dp, 30.dp).clip(CircleShape).background(if (checked) c.accent else c.tile2)
            .toggleable(checked, role = Role.Switch) { h.click(); onChange(it) }
            .semantics { contentDescription = label }
    ) {
        Box(Modifier.offset(x = x, y = 3.dp).size(24.dp).shadow(2.dp, CircleShape).background(c.card, CircleShape))
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    size: TextUnit = 15.sp,
    weight: FontWeight = FontWeight.Medium,
    onDone: (() -> Unit)? = null,
) {
    val c = S.c
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value, onChange,
        modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        textStyle = TextStyle(fontFamily = Manrope, fontSize = size, fontWeight = weight, color = c.ink),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = if (onDone != null) keyboard.copy(imeAction = ImeAction.Done) else keyboard,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        cursorBrush = SolidColor(c.accent),
        decorationBox = { inner ->
            Box(
                Modifier.background(c.card, RoundedCornerShape(14.dp))
                    .border(1.5.dp, if (focused) c.ink else c.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                if (value.isEmpty()) T(placeholder, size, FontWeight.Medium, c.ink2)
                inner()
            }
        },
    )
}

// ---------- icons (drawn, so they match the pixel style without an icon library) ----------

enum class Ico { TIMER, GRID, BARS, GEAR, SUN, HALF, MOON, PLAY, STOP, PLUS, BACK, PHONE, CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_UP, CHEVRON_DOWN }

@Composable
fun Icon(kind: Ico, size: Dp = 20.dp, color: Color = LocalContentColor.current, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIcon(kind, color) }
}

private fun DrawScope.drawIcon(kind: Ico, color: Color) {
    val s = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val stroke = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(vararg pts: Pair<Float, Float>) {
        val path = Path().apply {
            moveTo(pts[0].first * s, pts[0].second * s)
            for (i in 1 until pts.size) lineTo(pts[i].first * s, pts[i].second * s)
        }
        drawPath(path, color, style = stroke)
    }
    when (kind) {
        Ico.TIMER -> {
            drawCircle(color, 7.5f * s, p(12f, 13.5f), style = stroke)
            line(12f to 13.5f, 12f to 9.6f); line(9.5f to 2.8f, 14.5f to 2.8f); line(18.4f to 6.4f, 19.7f to 5.1f)
        }
        Ico.GRID -> listOf(3f to 3f, 13f to 3f, 3f to 13f, 13f to 13f).forEach { (x, y) ->
            drawRoundRect(color, p(x, y), Size(8f * s, 8f * s), CornerRadius(1.6f * s))
        }
        Ico.BARS -> listOf(Triple(4f, 11f, 9f), Triple(10.3f, 4f, 16f), Triple(16.6f, 8f, 12f)).forEach { (x, y, h) ->
            drawRoundRect(color, p(x, y), Size(3.4f * s, h * s), CornerRadius(1f * s))
        }
        Ico.GEAR -> {
            for (i in 0 until 8) rotate(i * 45f, p(12f, 12f)) {
                drawRoundRect(color, p(10f, 1.5f), Size(4f * s, 5f * s), CornerRadius(1f * s))
            }
            drawCircle(color, 5.2f * s, p(12f, 12f), style = Stroke(3.2f * s))
        }
        Ico.SUN -> {
            drawCircle(color, 4f * s, p(12f, 12f), style = Stroke(2f * s))
            for (i in 0 until 8) rotate(i * 45f, p(12f, 12f)) { line(12f to 2.5f, 12f to 4.5f) }
        }
        Ico.HALF -> {
            drawCircle(color, 8f * s, p(12f, 12f), style = Stroke(2f * s))
            drawArc(color, -90f, 180f, true, p(4f, 4f), Size(16f * s, 16f * s))
        }
        Ico.MOON -> {
            val a = Path().apply { addOval(Rect(p(4f, 4f), Size(16f * s, 16f * s))) }
            val b = Path().apply { addOval(Rect(p(9f, 1f), Size(14f * s, 14f * s))) }
            drawPath(Path.combine(PathOperation.Difference, a, b), color)
        }
        Ico.PLAY -> drawPath(Path().apply { moveTo(7f * s, 4f * s); lineTo(19f * s, 12f * s); lineTo(7f * s, 20f * s); close() }, color)
        Ico.STOP -> drawRoundRect(color, p(5f, 5f), Size(14f * s, 14f * s), CornerRadius(2.5f * s))
        Ico.PLUS -> { line(12f to 4f, 12f to 20f); line(4f to 12f, 20f to 12f) }
        Ico.BACK -> { line(19f to 12f, 5f to 12f); line(11f to 6f, 5f to 12f, 11f to 18f) }
        Ico.PHONE -> {
            drawRoundRect(color, p(6.5f, 2.5f), Size(11f * s, 19f * s), CornerRadius(2.5f * s), style = Stroke(2f * s))
            line(10.5f to 18f, 13.5f to 18f)
        }
        Ico.CHEVRON_LEFT -> line(15f to 5f, 8f to 12f, 15f to 19f)
        Ico.CHEVRON_RIGHT -> line(9f to 5f, 16f to 12f, 9f to 19f)
        Ico.CHEVRON_UP -> line(5f to 15f, 12f to 8f, 19f to 15f)
        Ico.CHEVRON_DOWN -> line(5f to 9f, 12f to 16f, 19f to 9f)
    }
}

/** Section heading row: small label on the left, optional hint on the right. */
@Composable
fun SectionRow(label: String, modifier: Modifier = Modifier, right: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Lbl(label, Modifier.weight(1f))
        right?.invoke()
    }
}
