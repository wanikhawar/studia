package com.khawar.studia.ui.screens

import androidx.compose.foundation.background
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khawar.studia.SessionNotifier
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khawar.studia.AppViewModel
import com.khawar.studia.PRESETS
import com.khawar.studia.Sheet
import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.ui.components.Dial
import com.khawar.studia.ui.components.Ico
import com.khawar.studia.ui.components.Lbl
import com.khawar.studia.ui.components.Muted
import com.khawar.studia.ui.components.Panel
import com.khawar.studia.ui.components.Pill
import com.khawar.studia.ui.components.PixelText
import com.khawar.studia.ui.components.SectionRow
import com.khawar.studia.ui.components.SlideToConfirm
import com.khawar.studia.ui.components.T
import com.khawar.studia.ui.components.Tile
import com.khawar.studia.ui.components.press
import com.khawar.studia.ui.theme.S

@Composable
fun FocusScreen(vm: AppViewModel, data: AppData) {
    val problem by vm.store.problem.collectAsStateWithLifecycle()
    // Ask for notification permission the first time a session starts, so the
    // countdown and "time's up" can show with the app closed. Start either way.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startRun() }
    val start = {
        if (Build.VERSION.SDK_INT >= 33 && !vm.askedNotifications &&
            !SessionNotifier.canPost(vm.getApplication())
        ) { vm.askedNotifications = true; askNotify.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else vm.startRun()
    }
    val c = S.c
    val (subject, topic) = vm.focusTarget(data)
    val len = data.focusLength
    val customOn = vm.customSelected || len !in PRESETS

    // Bottom padding keeps everything clear of the floating tab bar.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        problem?.let { p ->
            Panel(gap = 8.dp, onClick = { vm.selectTab(com.khawar.studia.Tab.SETTINGS) }) {
                T("Your saved data needs a look", 15.sp, FontWeight.ExtraBold, S.c.accent)
                Muted(p.message + " Open Settings for details.")
            }
        }
        Panel(horizontalAlignment = Alignment.CenterHorizontally, gap = 14.dp) {
            Lbl("Session length")
            PixelText(Calc.hhmm(len), 96.dp)
            Muted("hours : minutes", size = 12.sp)
        }

        Tile(Modifier.fillMaxWidth(), onClick = { vm.sheet = Sheet.Pick(subject?.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Lbl("Studying")
                    T(subject?.name ?: "Add a subject first", 16.sp, FontWeight.ExtraBold, maxLines = 1)
                    Muted(topic?.title ?: "Any topic", size = 13.sp)
                }
                Pill { T("Change", 12.sp, FontWeight.Bold) }
            }
        }

        SectionRow("Length") { Muted("Tap a preset or drag the dial", size = 12.sp) }

        Row(Modifier.fillMaxWidth().height(224.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Preset("25", "min", !customOn && len == 25, Modifier.weight(1f)) { vm.choosePreset(25) }
                    Preset("50", "min", !customOn && len == 50, Modifier.weight(1f)) { vm.choosePreset(50) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Preset("90", "min", !customOn && len == 90, Modifier.weight(1f)) { vm.choosePreset(90) }
                    Preset(Calc.hhmm(len).removePrefix("0"), "Custom", customOn, Modifier.weight(1f)) { vm.customSelected = true; vm.sheet = Sheet.Duration(com.khawar.studia.DurationKind.CUSTOM) }
                }
            }
            // Presets for the usual lengths, the dial for anything else
            Dial(len, { vm.setLength(it); if (it !in PRESETS) vm.customSelected = true }, Modifier.width(86.dp).fillMaxHeight())
        }

        SlideToConfirm(
            text = "Slide to start",
            icon = Ico.PLAY,
            track = c.slider, knob = c.sel, knobContent = c.onSel, hint = c.ink2,
            modifier = Modifier.padding(top = 4.dp),
        ) { start() }
    }
}

@Composable
private fun Preset(value: String, unit: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Tile(modifier.fillMaxHeight(), on = on, onClick = onClick, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(value, 28.sp, FontWeight.SemiBold, maxLines = 1)
            Muted(unit, size = 12.sp)
        }
    }
}
