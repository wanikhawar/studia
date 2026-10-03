package com.khawar.studia.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khawar.studia.AppViewModel
import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.ui.components.Blocks
import com.khawar.studia.ui.components.Ico
import com.khawar.studia.ui.components.Icon
import com.khawar.studia.ui.components.Lbl
import com.khawar.studia.ui.components.LocalHaptics
import com.khawar.studia.ui.components.Muted
import com.khawar.studia.ui.components.Panel
import com.khawar.studia.ui.components.PixelText
import com.khawar.studia.ui.components.T
import com.khawar.studia.ui.components.Tile
import com.khawar.studia.ui.components.press
import com.khawar.studia.ui.theme.S
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun StatsScreen(vm: AppViewModel, data: AppData) {
    val c = S.c
    val h = LocalHaptics.current
    val zone: ZoneId = vm.zone
    val today = LocalDate.now(zone)
    val byDay = Calc.byDay(data.sessions, zone)
    val info = Calc.rangeInfo(vm.range, vm.anchor, byDay, today)
    val bars = info.bars
    val max = bars.maxOf { it.minutes }
    val sel = (vm.selectedBar ?: info.todayIndex ?: bars.indices.maxBy { bars[it].minutes }).coerceIn(bars.indices)
    val total = bars.sumOf { it.minutes }
    val best = bars.maxBy { it.minutes }
    val unit = if (vm.range == Calc.Range.YEAR) "month" else "day"
    val gap = if (vm.range == Calc.Range.WEEK) 12.dp else 3.dp

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Range first, then which period, then the chart
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Calc.Range.entries.forEach { r ->
                Tile(Modifier.weight(1f), on = vm.range == r, padding = 12.dp, onClick = { vm.chooseRange(r) }, contentAlignment = Alignment.Center) {
                    T(r.name.lowercase().replaceFirstChar { it.uppercase() }, 13.5.sp, FontWeight.Bold)
                }
            }
        }

        // Period navigator
        Row(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(52.dp).fillMaxHeight().press { vm.moveRange(-1) }.semantics { contentDescription = "Previous" }, contentAlignment = Alignment.Center) {
                Icon(Ico.CHEVRON_LEFT, 18.dp, c.ink2)
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            T(info.title.uppercase(), 12.sp, FontWeight.ExtraBold, modifier = Modifier.weight(1f), align = TextAlign.Center, spacing = 1.6.sp)
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            Box(Modifier.width(52.dp).fillMaxHeight().press { vm.moveRange(1) }.semantics { contentDescription = "Next" }, contentAlignment = Alignment.Center) {
                Icon(Ico.CHEVRON_RIGHT, 18.dp, c.ink2)
            }
        }


        Panel(gap = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lbl("Time studied per $unit", Modifier.weight(1f))
                if (max > 0) Muted("Peak ${Calc.fmt(max)}", size = 11.sp)
            }
            Box(Modifier.fillMaxWidth().height(150.dp).padding(top = 6.dp)) {
                if (max > 0) Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                    drawLine(c.line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
                    bars.forEachIndexed { i, b ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight().press(haptic = false) { h.tick(); vm.selectedBar = i }
                                .semantics { contentDescription = "${b.caption}: ${Calc.fmt(b.minutes)}" },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val frac = if (max > 0 && b.minutes > 0) b.minutes.toFloat() / max else 0f
                            val col = when { i == sel -> c.accent; b.minutes == 0 -> c.tile2; else -> c.ink }
                            Box(
                                Modifier.fillMaxWidth().then(if (frac > 0) Modifier.fillMaxHeight(frac) else Modifier.height(3.dp))
                                    .background(col, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                bars.forEach { b ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        T(b.axis, 10.sp, FontWeight.Bold, c.ink2, Modifier.wrapContentWidth(unbounded = true), maxLines = 1)
                    }
                }
            }
            T(
                "${bars[sel].caption.uppercase()} · ${Calc.fmt(bars[sel].minutes).uppercase()}",
                11.5.sp, FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), align = TextAlign.Center, spacing = 1.6.sp,
            )
            Muted("Tap any bar to see it here", Modifier.fillMaxWidth(), size = 11.5.sp, align = TextAlign.Center)
        }

        // Totals for the period, together
        Panel(gap = 12.dp) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lbl("Total")
                    Row(verticalAlignment = Alignment.Bottom) {
                        PixelText(Calc.hrs(total), 40.dp)
                        Lbl("h", Modifier.padding(start = 6.dp, bottom = 2.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KV("Daily average", Calc.fmt(total.toDouble() / maxOf(1, info.elapsedDays)))
                    KV("Best $unit", if (best.minutes > 0) Calc.fmt(best.minutes) else "\u2014")
                }
            }
        }

        Panel {
            Lbl("By subject")
            val per = data.subjects.map { s ->
                s to data.sessions.filter { it.subjectId == s.id }.sumOf { Calc.minutesIn(it, info.from, info.to, zone) }
            }.filter { it.second > 0 }.sortedByDescending { it.second }
            if (per.isEmpty()) Muted("No study time in this period.")
            val top = per.firstOrNull()?.second ?: 1
            per.forEach { (s, m) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        T(s.name, 13.5.sp, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        Muted(Calc.fmt(m), size = 13.5.sp)
                    }
                    Blocks(20, maxOf(1, Math.round(m * 20f / top)))
                }
            }
        }

        Panel(gap = 14.dp) {
            val streak = Calc.streak(byDay, today)
            Row(verticalAlignment = Alignment.Bottom) {
                PixelText("$streak", 64.dp, color = c.accent)
                Column(Modifier.padding(start = 10.dp, bottom = 4.dp)) {
                    Lbl("Days in a row")
                    Muted("Best: ${Calc.bestStreak(byDay, today)}")
                }
            }
            // 12 weeks, one column per week (Monday at the top), ending with this week
            val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11)
            val labelW = 16.dp
            val gap = 3.dp
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = (maxWidth - labelW - gap * 12) / 12
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    // Month names above the week where each month starts
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Spacer(Modifier.width(labelW))
                        for (w in 0 until 12) {
                            val monday = firstMonday.plusWeeks(w.toLong())
                            Box(Modifier.width(cell)) {
                                if (w == 0 || monday.month != monday.minusWeeks(1).month)
                                    T(Calc.mon3(monday), 10.sp, FontWeight.Bold, c.ink2, Modifier.wrapContentWidth(unbounded = true, align = Alignment.Start), maxLines = 1)
                            }
                        }
                    }
                    for (d in 0 until 7) Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(labelW)) { if (d % 2 == 0 && d < 6) T("MTWTFSS"[d].toString(), 10.sp, FontWeight.Bold, c.ink2) }
                        for (w in 0 until 12) {
                            val day = firstMonday.plusWeeks(w.toLong()).plusDays(d.toLong())
                            if (day.isAfter(today)) { Spacer(Modifier.size(cell)); continue }
                            val col = when { day == today -> c.accent; (byDay[day] ?: 0) > 0 -> c.ink; else -> c.tile }
                            val picked = day == (vm.heatmapDay ?: today)
                            Box(
                                Modifier.size(cell).press(haptic = false) { vm.heatmapDay = day }
                                    .background(col, RoundedCornerShape(2.dp))
                                    .then(if (picked) Modifier.border(2.dp, c.accent.copy(alpha = .55f), RoundedCornerShape(2.dp)) else Modifier)
                                    .semantics { contentDescription = "${Calc.dow(day)} ${Calc.short(day)}: ${Calc.fmt(byDay[day] ?: 0)}" }
                            )
                        }
                    }
                }
            }
            // The day you tapped (today until you tap one)
            val sel = vm.heatmapDay ?: today
            val m = byDay[sel] ?: 0
            T("${Calc.dow(sel)} ${Calc.short(sel)} \u00B7 ${if (m > 0) Calc.fmt(m) + " studied" else "no study"}", 12.5.sp, FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(c.ink, "Studied"); Spacer(Modifier.width(14.dp))
                Legend(c.tile, "Missed"); Spacer(Modifier.width(14.dp))
                Legend(c.accent, "Today"); Spacer(Modifier.weight(1f))
                T("Tap a day", 11.sp, FontWeight.Bold, c.ink2)
            }
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Muted(k, Modifier.weight(1f), size = 13.sp)
        T(v, 13.sp, FontWeight.ExtraBold)
    }
}
