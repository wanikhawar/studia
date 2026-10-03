package com.khawar.studia.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.khawar.studia.data.DocumentRemote
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khawar.studia.AppViewModel
import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.data.Settings
import com.khawar.studia.data.ThemeMode
import com.khawar.studia.ui.components.Btn
import com.khawar.studia.ui.components.BtnKind
import com.khawar.studia.ui.components.Ico
import com.khawar.studia.ui.components.Icon
import com.khawar.studia.ui.components.Lbl
import com.khawar.studia.ui.components.Muted
import com.khawar.studia.ui.components.Panel
import com.khawar.studia.ui.components.PillSwitch
import com.khawar.studia.ui.components.T
import com.khawar.studia.ui.components.Tile
import com.khawar.studia.ui.components.press
import com.khawar.studia.ui.theme.S

@Composable
fun SettingsScreen(vm: AppViewModel, data: AppData) {
    val c = S.c
    val st = data.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        T("Settings", 24.sp, FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(ThemeMode.LIGHT, Ico.SUN, "Light"), Triple(ThemeMode.SYSTEM, Ico.HALF, "System"), Triple(ThemeMode.DARK, Ico.MOON, "Dark"))
                .forEach { (mode, icon, label) ->
                    val on = st.theme == mode
                    Tile(Modifier.weight(1f), on = on, padding = 0.dp, onClick = { vm.updateSettings { it.copy(theme = mode) } }) {
                        Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(icon, 22.dp)
                            T(label, 12.sp, FontWeight.Bold)
                            Box(Modifier.size(28.dp, 3.dp).background(if (on) c.accent else c.line, RoundedCornerShape(2.dp)))
                        }
                    }
                }
        }
        StepRow("Daily goal", "How long you want to study each day. Tap the time to type it.", Calc.fmt(st.goalMinutes),
            { vm.updateSettings { it.copy(goalMinutes = (it.goalMinutes - 30).coerceAtLeast(30)) } },
            { vm.updateSettings { it.copy(goalMinutes = (it.goalMinutes + 30).coerceAtMost(720)) } },
            { vm.sheet = com.khawar.studia.Sheet.Duration(com.khawar.studia.DurationKind.GOAL) })
        StepRow("Default session", "The length the timer starts at. Tap the time to type it.", Calc.fmt(st.defaultLength),
            { vm.setDefaultLength(st.defaultLength - 5) }, { vm.setDefaultLength(st.defaultLength + 5) },
            { vm.sheet = com.khawar.studia.Sheet.Duration(com.khawar.studia.DurationKind.DEFAULT) })
        SwitchRow("Haptic feedback", "A light vibration when you turn the dial or tick off a topic.", st.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }
        SwitchRow("Dial clicks", "A soft click sound for every minute as the dial turns. Uses your phone’s touch sounds.", st.sound) { v -> vm.updateSettings { it.copy(sound = v) } }
        ProblemCard(vm)
        StorageCard(vm)
        BackupCard(vm)
        if (data.sample) Muted("You are looking at sample subjects and sessions.", Modifier.padding(horizontal = 4.dp))
        when (vm.confirm) {
            "clear" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Delete all subjects and sessions", Modifier.weight(1f), BtnKind.WARN) { vm.clearAll() }
                Btn("Keep", kind = BtnKind.GHOST) { vm.confirm = null }
            }
            "sample" -> {
                Muted("This replaces your subjects and sessions with example data. A backup is kept, so you can restore them from here. Your save file, if you use one, is left untouched.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Load sample data", Modifier.weight(1f), BtnKind.WARN) { vm.loadSample() }
                    Btn("Keep my data", kind = BtnKind.GHOST) { vm.confirm = null }
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (data.sample) "Start fresh" else "Clear all data", Modifier.weight(1f), BtnKind.GHOST) { vm.confirm = "clear" }
                // Real data gets a confirmation first; sample data can just be reloaded
                Btn("Load sample data", Modifier.weight(1f), BtnKind.GHOST) { if (data.sample) vm.loadSample() else vm.confirm = "sample" }
            }
        }
    }
}

/** Shown when the saved data couldn’t be read at startup. */
@Composable
private fun ProblemCard(vm: AppViewModel) {
    val problem by vm.store.problem.collectAsStateWithLifecycle()
    val p = problem ?: return
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportDamaged(uri)
    }
    Panel(gap = 10.dp) {
        T("Your saved data needs a look", 15.sp, FontWeight.ExtraBold, S.c.accent)
        Muted(p.message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.damagedFile != null) Btn("Save the damaged file", Modifier.weight(1f), BtnKind.GHOST) { export.launch("studia-damaged.json") }
            Btn("Dismiss", Modifier.weight(1f), BtnKind.GHOST) { vm.dismissProblem() }
        }
    }
}

/** Undo for the last big replacement (sample data, clearing, opening a save file). */
@Composable
private fun BackupCard(vm: AppViewModel) {
    val backup by vm.store.backup.collectAsStateWithLifecycle()
    val b = backup ?: return
    val time = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.US).format(Instant.ofEpochMilli(b.time).atZone(vm.zone))
    Panel(gap = 8.dp) {
        T("Backup available", 15.sp, FontWeight.ExtraBold)
        Muted("Your data from before ${b.reason} ($time): ${b.data.subjects.size} subjects, ${b.data.sessions.size} sessions.")
        Btn("Restore my data", Modifier.fillMaxWidth()) { vm.restoreBackup() }
    }
}

/** A "save as" file picker that can start in a given folder. */
private class CreateJson(private val initial: Uri?) : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).apply { initial?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) } }
}

/**
 * Where data is saved: inside the app, or in a save file in a folder the user
 * chooses, which the app keeps up to date after every change.
 */
@Composable
private fun StorageCard(vm: AppViewModel) {
    val c = S.c
    val sync by vm.store.sync.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(CreateJson(DocumentRemote.PHONE_DOCUMENTS)) { uri ->
        if (uri != null) vm.connectStorage(uri, useFileData = false)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.connectStorage(uri, useFileData = true)
    }
    val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    Panel {
        T("Where your data is saved", 15.sp, FontWeight.ExtraBold)
        Choice("Inside the app", "Private. Deleted if you uninstall the app.", !sync.connected) {
            if (sync.connected) vm.disconnectStorage()
        }
        Choice(
            "In a folder you choose",
            if (sync.connected) sync.location ?: "Your save file" else "For example Documents. It stays if you uninstall the app.",
            sync.connected,
        ) { pickFolder.launch("studia.json") }
        if (sync.connected) {
            val err = sync.error
            if (err != null) T(err, 12.5.sp, FontWeight.SemiBold, c.accent)
            else sync.lastSynced?.let { Muted("Kept up to date after every change. Last synced ${time.format(Instant.ofEpochMilli(it).atZone(vm.zone))}") }
        }
        vm.storageError?.let { T(it, 12.5.sp, FontWeight.SemiBold, c.accent) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sync.connected) Btn("Sync now", Modifier.weight(1f), BtnKind.GHOST) { vm.syncNow() }
            Btn("Open a save file", Modifier.weight(1f), BtnKind.GHOST) { openFile.launch(arrayOf("*/*")) }
        }
        Muted("Opening a save file (from another phone or a backup) replaces the data on this phone with what’s in the file. Tap \u201CIn a folder you choose\u201D again to move the save file.", size = 12.sp)
    }
}

@Composable
private fun Choice(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val c = S.c
    Tile(Modifier.fillMaxWidth(), on = selected, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).border(2.dp, if (selected) c.accent else c.ink3, CircleShape).padding(4.dp)
                    .background(if (selected) c.accent else Color.Transparent, CircleShape)
            )
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(title, 14.5.sp, FontWeight.Bold)
                Muted(desc, size = 12.sp)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T(title, 15.sp, FontWeight.ExtraBold)
                Muted(desc)
            }
            T(if (checked) "On" else "Off", 12.sp, FontWeight.ExtraBold, S.c.ink2, Modifier.widthIn(min = 26.dp).padding(end = 8.dp), align = TextAlign.End)
            PillSwitch(checked, title, onChange)
        }
    }
}

@Composable
private fun StepRow(title: String, desc: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: () -> Unit) {
    val c = S.c
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T(title, 15.sp, FontWeight.ExtraBold)
                Muted(desc)
            }
            StepBtn("−", "Less $title", onMinus)
            // The value itself opens an editor, so big changes don't need many taps
            Box(
                Modifier.padding(horizontal = 4.dp).width(66.dp).height(44.dp).press(onClick = onEdit)
                    .clip(RoundedCornerShape(12.dp)).border(1.2.dp, c.line, RoundedCornerShape(12.dp))
                    .semantics { contentDescription = "$title: $value. Tap to type a value" },
                contentAlignment = Alignment.Center,
            ) { T(value, 14.sp, FontWeight.ExtraBold, maxLines = 1) }
            StepBtn("+", "More $title", onPlus)
        }
    }
}

@Composable
private fun StepBtn(text: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).press(onClick = onClick).clip(RoundedCornerShape(12.dp)).background(S.c.tile)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { T(text, 18.sp, FontWeight.Bold) }
}
