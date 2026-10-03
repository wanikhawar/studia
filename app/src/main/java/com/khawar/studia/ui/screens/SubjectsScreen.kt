package com.khawar.studia.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khawar.studia.AppViewModel
import com.khawar.studia.Sheet
import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.data.Status
import com.khawar.studia.data.Subject
import com.khawar.studia.data.Topic
import com.khawar.studia.ui.components.AccentSquare
import com.khawar.studia.ui.components.Blocks
import com.khawar.studia.ui.components.Btn
import com.khawar.studia.ui.components.BtnKind
import com.khawar.studia.ui.components.Field
import com.khawar.studia.ui.components.Ico
import com.khawar.studia.ui.components.Icon
import com.khawar.studia.ui.components.Lbl
import com.khawar.studia.ui.components.LocalHaptics
import com.khawar.studia.ui.components.Muted
import com.khawar.studia.ui.components.Panel
import com.khawar.studia.ui.components.Pill
import com.khawar.studia.ui.components.PixelText
import com.khawar.studia.ui.components.PlusButton
import com.khawar.studia.ui.components.SectionRow
import com.khawar.studia.ui.components.T
import com.khawar.studia.ui.components.Tile
import com.khawar.studia.ui.components.press
import com.khawar.studia.ui.theme.S
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SubjectsScreen(vm: AppViewModel, data: AppData) {
    val sid = vm.subjectId
    val subject = data.subject(sid)
    if (sid != null && subject != null) { SubjectDetail(vm, data, subject); return }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Subjects", 24.sp, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            PlusButton("Add subject") { vm.sheet = Sheet.AddSubject }
        }
        if (data.subjects.isEmpty()) {
            Panel(horizontalAlignment = Alignment.CenterHorizontally) {
                Muted("No subjects yet.\nTap + to add your first one.", size = 14.sp, align = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            // What's coming up first, then the subjects, then the (collapsed) timeline
            UpcomingExams(vm, data)
            data.subjects.forEach { SubjectCard(vm, data, it) }
            GanttCard(vm, data)
        }
    }
}

/** The next few exams, soonest first. */
@Composable
private fun UpcomingExams(vm: AppViewModel, data: AppData) {
    val today = LocalDate.now(vm.zone)
    val upcoming = data.subjects.mapNotNull { s -> Calc.daysTo(s.exam, today)?.takeIf { it >= 0 }?.let { s to it } }
        .sortedBy { it.second }.take(3)
    if (upcoming.isEmpty()) return
    Panel(gap = 10.dp) {
        Lbl("Upcoming exams")
        upcoming.forEach { (s, days) ->
            val date = LocalDate.parse(s.exam)
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).press { vm.openSubject(s.id) }, verticalAlignment = Alignment.CenterVertically) {
                T(s.name, 14.5.sp, FontWeight.Bold, modifier = Modifier.weight(1f).padding(end = 10.dp), maxLines = 1)
                T(when (days) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "$days days" }, 14.sp, FontWeight.ExtraBold, if (days <= 7) S.c.accent else S.c.ink)
                Muted("  ${Calc.dow(date).take(3)} ${Calc.short(date)}", size = 12.5.sp)
            }
        }
    }
}

@Composable
private fun SubjectCard(vm: AppViewModel, data: AppData, s: Subject) {
    val c = Calc.counts(s)
    val pct = if (c.total > 0) c.done * 100 / c.total else 0
    val days = Calc.daysTo(s.exam, LocalDate.now(vm.zone))
    Panel(onClick = { vm.openSubject(s.id) }, gap = 14.dp) {
        Row(verticalAlignment = Alignment.Top) {
            T(s.name, 18.sp, FontWeight.ExtraBold, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                PixelText("$pct", 30.dp)
                T("%", 13.sp, FontWeight.ExtraBold, S.c.ink2, Modifier.padding(start = 3.dp))
            }
        }
        ProgressBlocks(c)
        Facts(c, Calc.subjectMinutes(data, s.id), days)
    }
}

@Composable
private fun ProgressBlocks(c: Calc.Counts) {
    val done = if (c.total > 0) (c.done * 20f / c.total).roundToInt() else 0
    val part = if (c.total > 0) (c.doing * 20f / c.total).roundToInt() else 0
    Blocks(20, done, part)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(c: Calc.Counts, minutes: Int, days: Int?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Fact("${c.done}", " of ${c.total} topics done")
        if (c.doing > 0) Row(verticalAlignment = Alignment.CenterVertically) { AccentSquare(); Spacer(Modifier.width(5.dp)); Fact("${c.doing}", " in progress") }
        Fact(Calc.hrs(minutes), " h studied")
        if (days != null) { if (days >= 0) Fact("$days", " days to exam", prefix = "") else Muted("Exam passed") }
    }
}

@Composable
private fun Fact(bold: String, rest: String, prefix: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (prefix.isNotEmpty()) Muted(prefix)
        T(bold, 12.5.sp, FontWeight.ExtraBold)
        Muted(rest)
    }
}

// ---------- gantt ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GanttCard(vm: AppViewModel, data: AppData) {
    val c = S.c
    val g = Calc.gantt(data, LocalDate.now(vm.zone), vm.zone) ?: return
    Panel(gap = 14.dp) {
        // Collapsed by default: a one-line summary you can tap to expand
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).press { vm.timelineOpen = !vm.timelineOpen }, verticalAlignment = Alignment.CenterVertically) {
            Lbl("Progress timeline", Modifier.weight(1f))
            if (vm.timelineOpen) Lbl("${Calc.short(g.from)} – ${Calc.short(g.to)}", Modifier.padding(end = 8.dp))
            Icon(if (vm.timelineOpen) Ico.CHEVRON_UP else Ico.CHEVRON_DOWN, 16.dp, c.ink2)
        }
        if (!vm.timelineOpen) {
            val behind = g.rows.count { it.behind }
            Muted(
                (if (behind > 0) "$behind behind schedule. " else "Everything is on track. ") + "Tap to see every subject against its exam date.",
                size = 12.5.sp,
            )
            return@Panel
        }
        // Month axis
        BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp)) {
            val w = maxWidth
            var m = g.from.withDayOfMonth(1).plusMonths(1)
            val months = mutableListOf<LocalDate>()
            while (!m.isAfter(g.to)) { months += m; m = m.plusMonths(1) }
            val every = maxOf(1, (months.size + 4) / 5)
            months.forEachIndexed { i, d ->
                val x = w * g.pos(d)
                Box(Modifier.offset(x = x, y = 22.dp).size(1.dp, 6.dp).background(c.line))
                if (i % every == 0 && g.pos(d) < .9f) Lbl(Calc.mon3(d), Modifier.offset(x = x))
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.line))
        }
        g.rows.forEach { r ->
            Column(
                Modifier.fillMaxWidth().press { vm.openSubject(r.subject.id) }
                    .semantics(mergeDescendants = true) { contentDescription = "${r.subject.name}: ${(r.progress * 100).roundToInt()}% done, ${r.label}" },
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(r.subject.name, 13.5.sp, FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f).padding(end = 10.dp))
                    Lbl(r.label, color = if (r.behind) c.accent else c.ink2)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
                    val w = maxWidth
                    val l = g.pos(r.start); val e = g.pos(r.end)
                    GanttBar(r, Modifier.offset(x = w * l).width(w * (e - l)).fillMaxHeight())
                    // Today: taller than the bar and outlined, so it shows even over orange
                    Box(
                        Modifier.offset(x = w * g.pos(g.today) - 2.dp).requiredHeight(26.dp).width(4.dp)
                            .background(c.card, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.width(2.dp).fillMaxHeight().background(c.accent, RoundedCornerShape(1.dp))) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Legend(c.ink, "Done"); Legend(c.accent, "In progress"); Legend(c.tile2, "Until exam")
            Legend(c.ink3, "Estimated", hatched = true); Legend(c.accent, "Today", width = 2.dp)
        }
    }
}

@Composable
private fun GanttBar(r: Calc.GanttRow, modifier: Modifier) {
    val c = S.c
    val shape = RoundedCornerShape(4.dp)
    BoxWithConstraints(
        modifier.clip(shape).then(
            if (r.estimated) Modifier.drawBehind {
                var x = -size.height
                while (x < size.width) {
                    drawLine(c.tile2, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 4.dp.toPx())
                    x += 7.dp.toPx()
                }
            } else Modifier.background(c.tile2)
        )
    ) {
        val w = maxWidth
        Box(Modifier.width(w * r.progress).fillMaxHeight().background(c.ink))
        Box(Modifier.offset(x = w * r.progress).width(w * r.partial).fillMaxHeight().background(c.accent))
    }
}

@Composable
fun Legend(color: Color, label: String, hatched: Boolean = false, width: Dp = 10.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (hatched) Canvas(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))) {
            var x = -size.height
            while (x < size.width) { drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), 2.dp.toPx()); x += 4.dp.toPx() }
        } else Box(Modifier.size(width, 10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        T(label, 11.sp, FontWeight.Bold, S.c.ink2)
    }
}

// ---------- subject detail ----------

@Composable
private fun SubjectDetail(vm: AppViewModel, data: AppData, s: Subject) {
    val c = S.c
    val counts = Calc.counts(s)
    val minutes = Calc.subjectMinutes(data, s.id)
    val today = LocalDate.now(vm.zone)
    val days = Calc.daysTo(s.exam, today)
    val pace = Calc.pace(s, minutes, today)
    val week = data.sessions.filter { it.subjectId == s.id }.sumOf { Calc.minutesIn(it, today.minusDays(6), today.plusDays(1), vm.zone) }
    var newTopic by remember(s.id) { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(onClick = { vm.openSubject(null) }) { Icon(Ico.BACK, 14.dp); T("Subjects", 12.sp, FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Pill(onClick = { vm.sheet = Sheet.EditSubject(s.id) }) { T("Edit", 12.sp, FontWeight.Bold) }
        }
        Panel(gap = 14.dp) {
            T(s.name, 24.sp, FontWeight.ExtraBold)
            // Hours and topics are two separate measures, each labelled
            Row(verticalAlignment = Alignment.Bottom) {
                PixelText(Calc.hrs(minutes), 64.dp)
                Column(Modifier.padding(start = 10.dp, bottom = 4.dp)) {
                    Lbl("Hours studied")
                    Muted(if (s.targetHours > 0) "${Calc.hrs(minutes)} / ${s.targetHours} h target" else "No hours target set")
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Lbl("Topics completed", Modifier.weight(1f))
                T("${counts.done} / ${counts.total}", 13.sp, FontWeight.ExtraBold)
            }
            ProgressBlocks(counts)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Fact("${counts.done}", " done")
                Row(verticalAlignment = Alignment.CenterVertically) { AccentSquare(); Spacer(Modifier.width(5.dp)); Fact("${counts.doing}", " in progress") }
                Fact("${counts.todo}", " to do")
            }
        }
        // Equal-height tiles; labels may wrap to two lines on narrow phones
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Exam in", when { days == null -> "—"; days < 0 -> "Passed"; else -> "$days" }, if (days != null && days >= 0) "days" else "", Modifier.weight(1f))
            Stat("Pace needed", pace?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—", if (pace != null) "h/day" else "", Modifier.weight(1f))
            Stat("Last 7 days", Calc.hrs(week), "h", Modifier.weight(1f))
        }
        listOf(Status.DOING to "In progress", Status.TODO to "To do", Status.DONE to "Done").forEach { (st, title) ->
            val list = s.topics.filter { it.status == st }
            if (list.isNotEmpty()) {
                SectionRow(title) { Lbl("${list.size}") }
                list.forEach { TopicRow(vm, data, it) }
            }
        }
        if (counts.total == 0) {
            Panel(horizontalAlignment = Alignment.CenterHorizontally) {
                Muted("No topics yet. Add them one by one below, or paste your whole syllabus.", size = 14.sp, align = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        SectionRow("Add topic") { Pill(onClick = { vm.sheet = Sheet.Bulk }) { T("Paste a list", 12.sp, FontWeight.Bold) } }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val add = { vm.addTopics(s.id, listOf(newTopic)); newTopic = "" }
            Field(newTopic, { newTopic = it }, "e.g. Thermodynamics", Modifier.weight(1f), onDone = add)
            PlusButton("Add topic", onClick = add)
        }
        Spacer(Modifier.height(10.dp))
        if (vm.confirm == "delsub") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Delete ${s.name} and its sessions", Modifier.weight(1f), BtnKind.WARN) { vm.deleteSubject(s.id) }
                Btn("Keep", kind = BtnKind.GHOST) { vm.confirm = null }
            }
        } else {
            Btn("Delete subject", Modifier.fillMaxWidth(), BtnKind.GHOST) { vm.confirm = "delsub" }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier) {
    Tile(modifier.fillMaxHeight().heightIn(min = 96.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)) {
            Lbl(label, Modifier.weight(1f), maxLines = 2)
            Row(verticalAlignment = Alignment.Bottom) {
                T(value, 22.sp, FontWeight.ExtraBold, maxLines = 1)
                if (unit.isNotEmpty()) Muted(" $unit", Modifier.padding(bottom = 3.dp), size = 11.sp)
            }
        }
    }
}

@Composable
private fun TopicRow(vm: AppViewModel, data: AppData, t: Topic) {
    val c = S.c
    val h = LocalHaptics.current
    val minutes = Calc.topicMinutes(data, t.id)
    val done = t.status == Status.DONE
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).press { vm.sheet = Sheet.TopicDetail(t.id) }.padding(vertical = 4.dp, horizontal = 0.dp)) {
            T(t.title, 14.5.sp, FontWeight.Bold, if (done) c.ink2 else c.ink, decoration = if (done) TextDecoration.LineThrough else null)
            Muted(buildString {
                append(if (minutes > 0) "${Calc.fmt(minutes)} studied" else "Not started")
                if (t.estHours > 0) append(" · est. ${t.estHours} h")
            }, size = 12.sp)
        }
        // Status picker: tap to choose To do / In progress / Done
        var open by remember { mutableStateOf(false) }
        Box(Modifier.padding(start = 12.dp)) {
            Column(
                Modifier.width(104.dp).heightIn(min = 48.dp).press { open = true }   // same width for every status
                    .clip(RoundedCornerShape(12.dp)).background(c.tile).padding(horizontal = 10.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Status: ${statusLabel(t.status)}. Tap to change" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                StatusMeter(t.status)
                Lbl(statusLabel(t.status))
            }
            DropdownMenu(open, { open = false }, containerColor = c.card, shape = RoundedCornerShape(16.dp)) {
                listOf(Status.TODO, Status.DOING, Status.DONE).forEach { st ->
                    DropdownMenuItem(
                        text = { T(statusLabel(st), 14.5.sp, if (st == t.status) FontWeight.ExtraBold else FontWeight.SemiBold) },
                        leadingIcon = { StatusMeter(st) },
                        onClick = {
                            open = false
                            if (st != t.status) { vm.setStatus(t.id, st); if (st == Status.DONE) h.confirm() else h.click() }
                        },
                    )
                }
            }
        }
    }
}

fun statusLabel(s: Status) = when (s) { Status.TODO -> "To do"; Status.DOING -> "In progress"; Status.DONE -> "Done" }

/** Three small blocks: empty to do, one orange in progress, all dark done. */
@Composable
fun StatusMeter(status: Status) {
    val c = S.c
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val col = when (status) {
                Status.TODO -> c.tile2
                Status.DOING -> if (i == 0) c.accent else c.tile2
                Status.DONE -> c.ink
            }
            Box(Modifier.size(10.dp).background(col, RoundedCornerShape(2.dp)))
        }
    }
}
