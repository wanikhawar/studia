package com.khawar.studia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khawar.studia.AppViewModel
import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.data.RunState
import com.khawar.studia.ui.components.Blocks
import com.khawar.studia.ui.components.CardShape
import com.khawar.studia.ui.components.Ico
import com.khawar.studia.ui.components.Lbl
import com.khawar.studia.ui.components.PixelText
import com.khawar.studia.ui.components.SlideToConfirm
import com.khawar.studia.ui.components.T
import com.khawar.studia.ui.components.Wordmark
import com.khawar.studia.ui.components.press
import com.khawar.studia.ui.theme.S
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The dark "locked" screen shown while a session runs. */
@Composable
fun RunningScreen(vm: AppViewModel, data: AppData, run: RunState) {
    val c = S.c
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.start) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= vm.store.value.run?.endsAt ?: Long.MAX_VALUE) { vm.finishRun(timeUp = true); break }
            delay(1000 - now % 1000)
        }
    }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    BackHandler { /* stay on the session; slide to finish to leave */ }

    val total = (run.minutes + run.extra) * 60_000L
    val left = (run.endsAt - now).coerceAtLeast(0)
    val done = ((1 - left.toFloat() / total) * 30).toInt().coerceIn(0, 30)
    val subject = data.subject(run.subjectId)
    val topic = data.topic(run.topicId)?.second
    val ends = DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(Instant.ofEpochMilli(run.endsAt).atZone(vm.zone))
    val used = run.extra / 5

    CompositionLocalProvider(LocalContentColor provides c.lockInk) {
      // Fills the screen with Finish at the bottom; on short screens (e.g. landscape)
      // the whole thing scrolls instead, so Finish is always reachable.
      BoxWithConstraints(
          Modifier.fillMaxSize().background(c.lockBg)
              .clickable(remember { MutableInteractionSource() }, null) {}   // swallow taps meant for screens below
              .statusBarsPadding().navigationBarsPadding()
      ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), contentAlignment = Alignment.Center) { Wordmark(20.dp) }
            // What you're studying comes first and large
            LockTile(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Lbl("Studying", color = c.lockInk2)
                    T(subject?.name ?: "", 22.sp, FontWeight.ExtraBold)
                    T(topic?.title ?: "Any topic", 15.sp, FontWeight.Medium, c.lockInk2)
                }
            }
            // One countdown, read at a glance: 47:58, or 1:05:23 for long sessions
            LockTile(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PixelText(countdown(left), 100.dp, soft = c.lockInk2)
                    Lbl("Remaining", color = c.lockInk2)
                    Blocks(30, done, empty = c.lockDim, doneColor = c.lockInk)
                }
            }
            // Smaller controls underneath
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canExtend = used < 4
                LockTile(
                    Modifier.weight(1f).fillMaxHeight().alpha(if (canExtend) 1f else .5f)
                        .then(if (canExtend) Modifier.press { vm.extend() } else Modifier)
                        .semantics { contentDescription = "Add 5 minutes, ${4 - used} left" },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("+5", 24.sp, FontWeight.SemiBold)
                        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            T("Add 5 min", 12.sp, FontWeight.SemiBold, c.lockInk2)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(4) { i -> Box(Modifier.size(8.dp).background(if (i < used) c.accent else c.lockDim, RoundedCornerShape(2.dp))) }
                            }
                        }
                    }
                }
                LockTile(Modifier.weight(1f).fillMaxHeight()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Lbl("Ends at", color = c.lockInk2)
                        T(ends, 18.sp, FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            SlideToConfirm(
                text = "Slide to finish",
                icon = Ico.STOP,
                track = c.lockTile, knob = c.lockInk, knobContent = c.lockBg, hint = c.lockInk2,
                reverse = true,
            ) { vm.finishRun() }
        }
      }
    }
}

@Composable
private fun LockTile(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(CardShape).background(S.c.lockTile).padding(16.dp)) { content() }
}

/** Time left as m:ss, or h:mm:ss once it's an hour or more. */
private fun countdown(ms: Long): String {
    val total = (ms + 999) / 1000          // round up, so it never shows 0:00 early
    val h = total / 3600; val m = (total % 3600) / 60; val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
