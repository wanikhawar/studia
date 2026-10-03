package com.khawar.studia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khawar.studia.AppViewModel
import com.khawar.studia.Tab
import com.khawar.studia.data.Calc
import com.khawar.studia.ui.components.AccentSquare
import com.khawar.studia.ui.components.Haptics
import com.khawar.studia.ui.components.Ico
import com.khawar.studia.ui.components.Icon
import com.khawar.studia.ui.components.LocalHaptics
import com.khawar.studia.ui.components.Pill
import com.khawar.studia.ui.components.T
import com.khawar.studia.ui.components.Wordmark
import com.khawar.studia.ui.components.press
import com.khawar.studia.ui.screens.FocusScreen
import com.khawar.studia.ui.screens.RunningScreen
import com.khawar.studia.ui.screens.SettingsScreen
import com.khawar.studia.ui.screens.SheetHost
import com.khawar.studia.ui.screens.StatsScreen
import com.khawar.studia.ui.screens.SubjectsScreen
import com.khawar.studia.ui.theme.S
import com.khawar.studia.ui.theme.StudiaTheme
import com.khawar.studia.ui.theme.isDark
import java.time.LocalDate

@Composable
fun StudiaApp(vm: AppViewModel, onDarkChange: (Boolean) -> Unit) {
    val data by vm.store.state.collectAsStateWithLifecycle()
    val dark = isDark(data.settings.theme)
    LaunchedEffect(dark) { onDarkChange(dark) }

    val view = LocalView.current
    val settings by rememberUpdatedState(data.settings)
    val haptics = remember(view) { Haptics(view.context, view, { settings.haptics }, { settings.sound }) }

    StudiaTheme(dark) {
        CompositionLocalProvider(LocalHaptics provides haptics) {
            val c = S.c
            BackHandler(enabled = vm.sheet != null || vm.subjectId != null || vm.tab != Tab.FOCUS) { vm.back() }

            // Tabs are pages you swipe between. vm.tab follows the page once it settles;
            // when code changes vm.tab (pill tap, back button), the pager scrolls there.
            val pager = rememberPagerState(initialPage = vm.tab.ordinal) { Tab.entries.size }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.drop(1).collect { page ->
                    if (vm.tab.ordinal != page) { vm.selectTab(Tab.entries[page]); haptics.tick() }
                }
            }
            LaunchedEffect(vm.tab) {
                if (pager.settledPage != vm.tab.ordinal) pager.animateScrollToPage(vm.tab.ordinal)
            }
            // The tab pill only shows while the pages are actually moving sideways,
            // then fades away. (Not isScrollInProgress: vertical scrolling hands its
            // leftover fling to the pager, which reports a "scroll" without moving.)
            // It also shows for a moment at launch, so it's clear the tabs exist.
            var showDock by remember { mutableStateOf(true) }
            LaunchedEffect(pager) {
                snapshotFlow { abs(pager.currentPageOffsetFraction) > .02f || pager.currentPage != pager.settledPage }
                    .collectLatest { moving ->
                        if (moving) showDock = true else { delay(1600); showDock = false }
                    }
            }

            Box(Modifier.fillMaxSize().background(c.bg)) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    TopBar(vm)
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.weight(1f),
                        userScrollEnabled = data.run == null,
                        beyondViewportPageCount = 1,
                    ) { page ->
                        when (Tab.entries[page]) {
                            Tab.FOCUS -> FocusScreen(vm, data)
                            Tab.SUBJECTS -> SubjectsScreen(vm, data)
                            Tab.STATS -> StatsScreen(vm, data)
                            Tab.SETTINGS -> SettingsScreen(vm, data)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showDock,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    // Highlight follows the finger while swiping, not just after the page settles.
                    Dock(vm, Tab.entries[pager.targetPage])
                }
                data.run?.let { RunningScreen(vm, data, it) }
                // Brief message pill, e.g. "Session discarded", that fades after a moment
                val msg = vm.message
                LaunchedEffect(msg) { if (msg != null) { delay(2600); vm.message = null } }
                AnimatedVisibility(
                    visible = msg != null,
                    // Just under the top bar, where it never covers a control
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 24.dp, end = 24.dp),
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut(),
                ) {
                    Box(Modifier.clip(CircleShape).background(c.sel).padding(horizontal = 18.dp, vertical = 12.dp)) {
                        T(msg ?: "", 13.5.sp, FontWeight.Bold, c.onSel)
                    }
                }
                SheetHost(vm, data)
            }
        }
    }
}

@Composable
private fun TopBar(vm: AppViewModel) {
    val data by vm.store.state.collectAsStateWithLifecycle()
    val today = LocalDate.now(vm.zone)
    val byDay = Calc.byDay(data.sessions, vm.zone)
    val streak = Calc.streak(byDay, today)
    Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp)) {
        Pill(Modifier.align(Alignment.CenterStart).semantics(mergeDescendants = true) { contentDescription = "Study streak: $streak days" }) {
            AccentSquare()
            T("$streak ${if (streak == 1) "day" else "days"}", 12.sp, FontWeight.Bold, maxLines = 1)
        }
        Wordmark(20.dp, Modifier.align(Alignment.Center))
        // Today's progress toward the daily goal; orange once it's reached
        val mins = byDay[today] ?: 0
        val goal = data.settings.goalMinutes
        Pill(Modifier.align(Alignment.CenterEnd).semantics(mergeDescendants = true) {
            contentDescription = "Studied ${Calc.fmt(mins)} of your ${Calc.fmt(goal)} goal today"
        }) {
            T("${Calc.fmt(mins)} / ${Calc.fmt(goal)}", 12.sp, FontWeight.Bold, if (mins >= goal) S.c.accent else LocalContentColor.current, maxLines = 1)
        }
    }
}

@Composable
private fun Dock(vm: AppViewModel, current: Tab, modifier: Modifier = Modifier) {
    val c = S.c
    Row(
        modifier.shadow(14.dp, CircleShape, ambientColor = c.shadow, spotColor = c.shadow).clip(CircleShape).background(c.card).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = current == t
            val icon = when (t) { Tab.FOCUS -> Ico.TIMER; Tab.SUBJECTS -> Ico.GRID; Tab.STATS -> Ico.BARS; Tab.SETTINGS -> Ico.GEAR }
            Row(
                Modifier.height(48.dp).clip(CircleShape).background(if (on) c.sel else Color.Transparent)
                    .press { vm.selectTab(t) }
                    .semantics { contentDescription = t.label; selected = on }
                    .padding(horizontal = if (on) 16.dp else 15.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(icon, 20.dp, if (on) c.onSel else c.ink2)
                if (on) T(t.label, 13.sp, FontWeight.ExtraBold, c.onSel, maxLines = 1)
            }
        }
    }
}
