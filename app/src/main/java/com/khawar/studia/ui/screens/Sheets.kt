package com.khawar.studia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khawar.studia.AppViewModel
import com.khawar.studia.Sheet
import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.data.Status
import com.khawar.studia.ui.components.Btn
import com.khawar.studia.ui.components.BtnKind
import com.khawar.studia.ui.components.Field
import com.khawar.studia.ui.components.Lbl
import com.khawar.studia.ui.components.LocalHaptics
import com.khawar.studia.ui.components.Muted
import com.khawar.studia.ui.components.Panel
import com.khawar.studia.ui.components.PixelText
import com.khawar.studia.ui.components.SectionRow
import com.khawar.studia.ui.components.T
import com.khawar.studia.ui.components.Tile
import com.khawar.studia.ui.components.press
import com.khawar.studia.ui.theme.S
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(vm: AppViewModel, data: AppData) {
    val sheet = vm.sheet ?: return
    val c = S.c
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { vm.sheet = null } }
    ModalBottomSheet(
        onDismissRequest = { vm.sheet = null },
        sheetState = state,
        containerColor = c.bg,
        contentColor = c.ink,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).background(c.line, CircleShape)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (sheet) {
                is Sheet.Pick -> PickSheet(vm, data, sheet.subjectId)
                Sheet.AddSubject -> SubjectSheet(vm, null)
                is Sheet.EditSubject -> SubjectSheet(vm, data.subject(sheet.subjectId))
                is Sheet.Duration -> DurationSheet(vm, sheet.kind)
                Sheet.Bulk -> BulkSheet(vm, close)
                is Sheet.TopicDetail -> TopicSheet(vm, data, sheet.topicId, close)
                is Sheet.Done -> DoneSheet(vm, data, sheet, close)
            }
        }
    }
}

@Composable
private fun Title(text: String) = T(text, 21.sp, FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp))

@Composable
private fun Option(title: String, sub: String?, on: Boolean, onClick: () -> Unit) {
    Tile(Modifier.fillMaxWidth(), on = on, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(title, 15.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
            if (sub != null) Muted(sub, size = 12.sp)
        }
    }
}

@Composable
private fun PickSheet(vm: AppViewModel, data: AppData, initial: String?) {
    var sid by remember { mutableStateOf(initial ?: data.subjects.firstOrNull()?.id) }
    Title("What are you studying?")
    data.subjects.forEach { s ->
        val cnt = Calc.counts(s)
        Option(s.name, "${cnt.left} left", s.id == sid) { sid = s.id }
    }
    val s = data.subject(sid) ?: return
    SectionRow("Topic in ${s.name}")
    Option("Any topic", null, data.focusSubjectId == s.id && data.focusTopicId == null) { vm.pick(s.id, null) }
    s.topics.filter { it.status != Status.DONE }.forEach { t ->
        Option(t.title, if (t.status == Status.DOING) "In progress" else "To do", data.focusTopicId == t.id) { vm.pick(s.id, t.id) }
    }
}

/** Type an exact duration as hours and minutes. */
@Composable
private fun DurationSheet(vm: AppViewModel, kind: com.khawar.studia.DurationKind) {
    val start = vm.durationOf(kind)
    var hours by remember { mutableStateOf((start / 60).toString()) }
    var mins by remember { mutableStateOf((start % 60).toString()) }
    var error by remember { mutableStateOf("") }
    val save = {
        val total = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
        if (total < kind.min || total > kind.max) error = "Choose between ${Calc.fmt(kind.min)} and ${Calc.fmt(kind.max)}."
        else vm.setDuration(kind, total)
    }
    Title(kind.title)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Lbl("Hours")
            Field(hours, { v -> hours = v.filter { it.isDigit() }.take(2); error = "" }, "0",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), size = 22.sp, weight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Lbl("Minutes")
            Field(mins, { v -> mins = v.filter { it.isDigit() }.take(3); error = "" }, "0",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number), size = 22.sp, weight = FontWeight.ExtraBold, onDone = save)
        }
    }
    Muted("Between ${Calc.fmt(kind.min)} and ${Calc.fmt(kind.max)}.", size = 12.5.sp)
    if (error.isNotEmpty()) T(error, 12.5.sp, FontWeight.SemiBold, S.c.accent)
    Btn("Set ${kind.title.lowercase()}", Modifier.fillMaxWidth().padding(top = 4.dp), onClick = save)
}

/** New subject, or editing an existing one ([editing]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectSheet(vm: AppViewModel, editing: com.khawar.studia.data.Subject?) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var target by remember { mutableStateOf(editing?.targetHours?.takeIf { it > 0 }?.toString() ?: "") }
    var exam by remember { mutableStateOf(editing?.exam?.let(LocalDate::parse)) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val submit = {
        when {
            name.isBlank() -> error = "Give the subject a name."
            editing != null -> vm.updateSubject(editing.id, name, exam, target.toIntOrNull() ?: 0)
            else -> vm.addSubject(name, exam, target.toIntOrNull() ?: 0)
        }
    }

    Title(if (editing != null) "Edit subject" else "New subject")
    Lbl("Name")
    Field(name, { name = it; error = "" }, "e.g. Biology", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    Lbl("Exam date (optional)")
    Tile(Modifier.fillMaxWidth(), onClick = { picking = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(exam?.let { "${Calc.dow(it)}, ${Calc.short(it)} ${it.year}" } ?: "Not set", 15.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
            Muted(if (exam == null) "Choose" else "Change")
        }
    }
    if (exam != null) Btn("Remove exam date", Modifier.fillMaxWidth(), BtnKind.GHOST) { exam = null }
    Lbl("Target hours (optional)")
    Field(target, { v -> target = v.filter { it.isDigit() }.take(4) }, "e.g. 30", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
    if (error.isNotEmpty()) T(error, 12.5.sp, FontWeight.SemiBold, S.c.accent)
    Btn(if (editing != null) "Save changes" else "Add subject", Modifier.fillMaxWidth().padding(top = 4.dp), onClick = submit)

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (exam ?: LocalDate.now().plusDays(30)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { exam = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { T("Set", 14.sp, FontWeight.Bold, S.c.accent) }
            },
            dismissButton = { TextButton({ picking = false }) { T("Cancel", 14.sp, FontWeight.Bold, S.c.ink2) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun BulkSheet(vm: AppViewModel, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Title("Paste a list")
    Muted("One topic per line. Bullets and numbers at the start of a line are removed.", size = 13.sp)
    Field(text, { text = it; error = "" }, "Thermodynamics\nElectrostatics\nMagnetism", singleLine = false, minLines = 6)
    if (error.isNotEmpty()) T(error, 12.5.sp, FontWeight.SemiBold, S.c.accent)
    Btn("Add topics", Modifier.fillMaxWidth()) {
        val lines = text.lines().map { it.replace(Regex("^\\s*([-*•]|\\d+[.)])\\s*"), "").trim() }.filter { it.isNotEmpty() }
        val sid = vm.subjectId
        if (lines.isEmpty() || sid == null) error = "Paste at least one topic, one per line."
        else { vm.addTopics(sid, lines); close() }
    }
}

@Composable
private fun TopicSheet(vm: AppViewModel, data: AppData, topicId: String, close: () -> Unit) {
    val (s, t) = data.topic(topicId) ?: return
    val h = LocalHaptics.current
    var title by remember(topicId) { mutableStateOf(t.title) }
    var confirm by remember { mutableStateOf(false) }
    val sessions = data.sessions.filter { it.topicId == t.id }.sortedByDescending { it.start }

    Lbl(s.name)
    Field(title, { title = it; vm.rename(t.id, it) }, "Topic name", size = 17.sp, weight = FontWeight.ExtraBold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Same words as the status picker on each topic row
        listOf(Status.TODO to "To do", Status.DOING to "In progress", Status.DONE to "Done").forEach { (st, label) ->
            Tile(Modifier.weight(1f), on = t.status == st, padding = 8.dp, onClick = { vm.setStatus(t.id, st); if (st == Status.DONE) h.confirm() }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    StatusMeter(st)
                    T(label, 13.sp, FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(Modifier.weight(1f)) { Column { Lbl("Studied"); T(Calc.fmt(Calc.topicMinutes(data, t.id)), 22.sp, FontWeight.ExtraBold) } }
        Tile(Modifier.weight(1f)) { Column { Lbl("Sessions"); T("${sessions.size}", 22.sp, FontWeight.ExtraBold) } }
    }
    if (sessions.isNotEmpty()) Panel(gap = 8.dp) {
        sessions.take(4).forEach { x ->
            val d = Calc.day(x.start, vm.zone)
            Row {
                Muted("${Calc.dow(d).take(3)} ${Calc.short(d)}", Modifier.weight(1f), size = 13.sp)
                T(Calc.fmt(x.minutes), 13.sp, FontWeight.ExtraBold)
            }
        }
    }
    Btn("Start a session on this topic", Modifier.fillMaxWidth()) {
        vm.pick(s.id, t.id)
        vm.selectTab(com.khawar.studia.Tab.FOCUS)
    }
    if (confirm) Btn("Tap again to delete", Modifier.fillMaxWidth(), BtnKind.WARN) { vm.deleteTopic(t.id) }
    else Btn("Delete topic", Modifier.fillMaxWidth(), BtnKind.GHOST) { confirm = true }
}

@Composable
private fun DoneSheet(vm: AppViewModel, data: AppData, sheet: Sheet.Done, close: () -> Unit) {
    val s = data.subject(sheet.subjectId)
    val t = data.topic(sheet.topicId)?.second
    val byDay = Calc.byDay(data.sessions, vm.zone)
    val today = LocalDate.now(vm.zone)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelText("${sheet.minutes}", 80.dp)
        Lbl(if (sheet.sessionId == null) "Nothing logged" else "Minutes logged")
    }
    if (sheet.sessionId == null) {
        // Stopped within the first minute: nothing was added to your history
        Muted("You finished before a full minute, so this session wasn’t added to your history.", Modifier.fillMaxWidth(), size = 13.sp, align = TextAlign.Center)
        Btn("OK", Modifier.fillMaxWidth().padding(top = 6.dp), onClick = close)
        Box(Modifier.height(4.dp))
        return
    }
    T(listOfNotNull(s?.name, t?.title).joinToString(" · "), 15.sp, FontWeight.Bold, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
    Muted(
        "${Calc.fmt(byDay[today] ?: 0)} today · ${Calc.streak(byDay, today)} days in a row",
        Modifier.fillMaxWidth().padding(bottom = 6.dp), size = 13.sp, align = TextAlign.Center,
    )
    if (t != null && t.status != Status.DONE) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Mark topic done", Modifier.weight(1f), BtnKind.WARN) { vm.setStatus(t.id, Status.DONE); close() }
            Btn("Still working on it", Modifier.weight(1f), BtnKind.GHOST) { vm.setStatus(t.id, Status.DOING); close() }
        }
    } else Btn("Done", Modifier.fillMaxWidth(), onClick = close)
    // A very short session finished early is probably a false start
    if (sheet.early && sheet.minutes < 5) {
        Btn("Discard this session", Modifier.fillMaxWidth(), BtnKind.GHOST) { vm.discardSession(sheet.sessionId) }
    }
    Box(Modifier.height(4.dp))
}
