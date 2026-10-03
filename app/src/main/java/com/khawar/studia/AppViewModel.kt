package com.khawar.studia

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khawar.studia.data.AppData
import com.khawar.studia.data.Calc
import com.khawar.studia.data.DocumentRemote
import com.khawar.studia.data.RunState
import com.khawar.studia.data.Sample
import com.khawar.studia.data.Session
import com.khawar.studia.data.Settings
import com.khawar.studia.data.Status
import com.khawar.studia.data.Store
import com.khawar.studia.data.Subject
import com.khawar.studia.data.Topic
import com.khawar.studia.data.newId
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Tab(val label: String) { FOCUS("Focus"), SUBJECTS("Subjects"), STATS("Stats"), SETTINGS("Settings") }

sealed interface Sheet {
    data class Pick(val subjectId: String?) : Sheet
    data object AddSubject : Sheet
    data object Bulk : Sheet
    data class TopicDetail(val topicId: String) : Sheet
    /** [sessionId] is null when nothing was logged (under a minute). */
    data class Done(val minutes: Int, val subjectId: String, val topicId: String?, val sessionId: String?, val early: Boolean) : Sheet
    data class EditSubject(val subjectId: String) : Sheet
    data class Duration(val kind: DurationKind) : Sheet
}

val PRESETS = listOf(25, 50, 90)

/** Durations that can be typed in, with their allowed range in minutes. */
enum class DurationKind(val title: String, val min: Int, val max: Int) {
    CUSTOM("Session length", 5, 240),
    GOAL("Daily goal", 15, 720),
    DEFAULT("Default session", 5, 240),
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val zone: ZoneId get() = ZoneId.systemDefault()
    val store = Store(File(app.filesDir, "studia.json"), viewModelScope) {
        Sample.create(System.currentTimeMillis(), ZoneId.systemDefault())
    }

    // Screen state that doesn't need saving
    var tab by mutableStateOf(Tab.FOCUS)
    var subjectId by mutableStateOf<String?>(null)
    var sheet by mutableStateOf<Sheet?>(null)
    var customSelected by mutableStateOf(false)
    var range by mutableStateOf(Calc.Range.MONTH)
    var anchor by mutableStateOf(LocalDate.now())
    var selectedBar by mutableStateOf<Int?>(null)
    var confirm by mutableStateOf<String?>(null)
    var timelineOpen by mutableStateOf(false)
    /** A short message shown briefly at the bottom of the screen, e.g. "Session discarded". */
    var message by mutableStateOf<String?>(null)
    var heatmapDay by mutableStateOf<LocalDate?>(null)
    var storageError by mutableStateOf<String?>(null)


    // ---------- where data is saved ----------
    // The chosen file is a per-phone setting, so it lives in SharedPreferences,
    // not in the synced data.
    private val prefs = app.getSharedPreferences("storage", Context.MODE_PRIVATE)
    private val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    init {
        // Leftovers from the removed Google Drive option
        prefs.edit().remove("driveFile").remove("driveLocation").remove("driveEmail").apply()
        store.value.run?.let { notifyRun(it) }
        val saved = prefs.getString("uri", null)?.let(Uri::parse)
        if (saved != null && app.contentResolver.persistedUriPermissions.any { it.uri == saved }) {
            viewModelScope.launch {
                store.connect(DocumentRemote(app, saved), useRemoteData = false, push = false)
                store.pull()
            }
        }
    }

    /**
     * Connect a file picked in the system picker. [useFileData]: replace this phone's
     * data with the file's.
     */
    fun connectStorage(uri: Uri, useFileData: Boolean) {
        val app = getApplication<Application>()
        runCatching { app.contentResolver.takePersistableUriPermission(uri, rw) }
        viewModelScope.launch {
            val remote = DocumentRemote(app, uri)
            val err = store.connect(remote, useFileData)
            if (err == null) {
                prefs.getString("uri", null)?.let { old -> if (old != uri.toString()) releaseUri(Uri.parse(old)) }
                prefs.getString("pausedUri", null)?.let { old -> if (old != uri.toString()) releaseUri(Uri.parse(old)) }
                prefs.edit().putString("uri", uri.toString()).remove("pausedUri").apply()
                storageError = null
                confirm = null
                subjectId = null
            } else {
                storageError = err
                releaseUri(uri)
            }
        }
    }

    fun disconnectStorage() {
        prefs.getString("uri", null)?.let { releaseUri(Uri.parse(it)) }
        prefs.edit().remove("uri").apply()
        store.disconnect()
        confirm = null
    }

    private fun releaseUri(uri: Uri) {
        runCatching { getApplication<Application>().contentResolver.releasePersistableUriPermission(uri, rw) }
    }

    fun syncNow() { viewModelScope.launch { store.pull() } }

    // ---------- damaged data and backups ----------

    fun dismissProblem() = store.dismissProblem()

    /** Copy the damaged save file to a place the user picks, so nothing is lost. */
    fun exportDamaged(uri: Uri) {
        val src = store.problem.value?.damagedFile ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { it.write(src.readBytes()) }
            }.onFailure { storageError = "Couldn’t save the damaged file there." }
        }
    }

    /**
     * Put back the data from before the last big replacement. If syncing was paused
     * for sample data, it resumes with the restored data.
     */
    fun restoreBackup() {
        if (!store.restoreBackup()) return
        confirm = null
        subjectId = null
        val paused = prefs.getString("pausedUri", null)?.let(Uri::parse) ?: return
        val app = getApplication<Application>()
        prefs.edit().remove("pausedUri").apply()
        if (app.contentResolver.persistedUriPermissions.none { it.uri == paused }) return
        viewModelScope.launch {
            if (store.connect(DocumentRemote(app, paused), useRemoteData = false) == null)
                prefs.edit().putString("uri", paused.toString()).apply()
        }
    }

    /** Stop copying to the save file without forgetting it, so sample data can't overwrite it. */
    private fun pauseSync(): Boolean {
        val uri = prefs.getString("uri", null) ?: return false
        store.disconnect()
        prefs.edit().remove("uri").putString("pausedUri", uri).apply()
        return true
    }

    // ---------- session notifications ----------

    var askedNotifications: Boolean
        get() = prefs.getBoolean("askedNotifications", false)
        set(v) { prefs.edit().putBoolean("askedNotifications", v).apply() }

    private fun notifyRun(r: RunState) {
        val d = store.value
        val what = listOfNotNull(d.subject(r.subjectId)?.name, d.topic(r.topicId)?.second?.title).joinToString(" \u00B7 ").ifEmpty { "your subject" }
        runCatching { SessionNotifier.start(getApplication(), r, what, zone) }
    }

    /** Called when the app comes to the front: pick up changes made on another phone. */
    fun onAppVisible() { viewModelScope.launch { store.pull() } }

    private val data: AppData get() = store.value

    fun selectTab(t: Tab) {
        if (t == Tab.SUBJECTS && tab == Tab.SUBJECTS) subjectId = null
        tab = t
        confirm = null
    }

    fun openSubject(id: String?) { subjectId = id; confirm = null }

    /** Back button: close a sheet, leave a subject, then return to Focus. */
    fun back(): Boolean = when {
        sheet != null -> { sheet = null; true }
        subjectId != null -> { subjectId = null; true }
        tab != Tab.FOCUS -> { tab = Tab.FOCUS; true }
        else -> false
    }

    // ---------- focus ----------

    fun setLength(min: Int) = store.update { it.copy(focusLength = min.coerceIn(5, 240)) }

    fun choosePreset(min: Int) { customSelected = false; setLength(min) }

    fun durationOf(kind: DurationKind): Int = when (kind) {
        DurationKind.CUSTOM -> data.focusLength
        DurationKind.GOAL -> data.settings.goalMinutes
        DurationKind.DEFAULT -> data.settings.defaultLength
    }

    fun setDuration(kind: DurationKind, minutes: Int) {
        val m = minutes.coerceIn(kind.min, kind.max)
        when (kind) {
            DurationKind.CUSTOM -> { customSelected = true; setLength(m) }
            DurationKind.GOAL -> updateSettings { it.copy(goalMinutes = m) }
            DurationKind.DEFAULT -> setDefaultLength(m)
        }
        sheet = null
    }

    fun pick(subjectId: String, topicId: String?) {
        store.update { it.copy(focusSubjectId = subjectId, focusTopicId = topicId) }
        sheet = null
    }

    /** The subject and topic the timer will log to, falling back sensibly if one was deleted. */
    fun focusTarget(d: AppData): Pair<Subject?, Topic?> {
        val s = d.subject(d.focusSubjectId) ?: d.subjects.firstOrNull()
        val t = s?.topics?.find { it.id == d.focusTopicId && it.status != Status.DONE }   // finished topics fall back to "Any topic"
        return s to t
    }

    fun startRun() {
        val (s, t) = focusTarget(data)
        if (s == null) { tab = Tab.SUBJECTS; sheet = Sheet.AddSubject; return }
        store.update { it.copy(run = RunState(System.currentTimeMillis(), it.focusLength, 0, s.id, t?.id)) }
        store.value.run?.let { notifyRun(it) }
    }

    fun extend() {
        store.update { d -> d.run?.let { if (it.extra < 20) d.copy(run = it.copy(extra = it.extra + 5)) else d } ?: d }
        store.value.run?.let { notifyRun(it) }
    }

    /**
     * End the session. When the time ran out ([timeUp]) the full length is logged;
     * when finished early, only whole minutes actually studied, and nothing under a minute.
     */
    fun finishRun(timeUp: Boolean = false) {
        val r = data.run ?: return
        val planned = r.minutes + r.extra
        val minutes = if (timeUp) planned else Calc.loggedMinutes(System.currentTimeMillis() - r.start, planned)
        val id = if (minutes > 0 && data.subject(r.subjectId) != null) newId() else null
        store.update { d ->
            var next = d.copy(run = null)
            if (id != null) {
                next = next.copy(sessions = next.sessions + Session(id, r.subjectId, r.topicId, r.start, minutes))
                if (r.topicId != null) next = next.mapTopic(r.topicId) { if (it.status == Status.TODO) it.copy(status = Status.DOING) else it }
            }
            next
        }
        runCatching { SessionNotifier.stop(getApplication()) }
        // Under a minute: nothing to celebrate, just say so briefly
        if (id == null) { message = "Under a minute, so nothing was logged"; sheet = null; return }
        sheet = Sheet.Done(minutes, r.subjectId, r.topicId, id, early = !timeUp)
    }

    /** Remove a just-logged session (e.g. a two-minute false start). */
    fun discardSession(id: String) {
        store.update { d -> d.copy(sessions = d.sessions.filter { it.id != id }) }
        sheet = null
        message = "Session discarded"
    }

    // ---------- subjects and topics ----------

    fun updateSubject(id: String, name: String, exam: LocalDate?, targetHours: Int) {
        store.update { d ->
            d.copy(subjects = d.subjects.map {
                if (it.id == id) it.copy(name = name.trim(), exam = exam?.toString(), targetHours = targetHours.coerceAtLeast(0)) else it
            })
        }
        sheet = null
    }

    fun addSubject(name: String, exam: LocalDate?, targetHours: Int) {
        val s = Subject(newId(), name.trim(), exam?.toString(), targetHours.coerceAtLeast(0), System.currentTimeMillis())
        store.update { it.copy(subjects = it.subjects + s) }
        sheet = null
        tab = Tab.SUBJECTS
        subjectId = s.id
    }

    fun addTopics(subjectId: String, titles: List<String>) {
        val clean = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        store.update { d ->
            d.copy(subjects = d.subjects.map { s ->
                if (s.id == subjectId) s.copy(topics = s.topics + clean.map { Topic(newId(), it) }) else s
            })
        }
    }

    fun cycle(topicId: String) = store.update { d ->
        d.mapTopic(topicId) {
            it.copy(status = when (it.status) { Status.TODO -> Status.DOING; Status.DOING -> Status.DONE; Status.DONE -> Status.TODO })
        }
    }

    fun setStatus(topicId: String, status: Status) = store.update { d -> d.mapTopic(topicId) { it.copy(status = status) } }

    fun rename(topicId: String, title: String) {
        if (title.isBlank()) return
        store.update { d -> d.mapTopic(topicId) { it.copy(title = title.trim()) } }
    }

    fun deleteTopic(topicId: String) {
        store.update { d ->
            d.copy(
                subjects = d.subjects.map { s -> s.copy(topics = s.topics.filter { it.id != topicId }) },
                sessions = d.sessions.map { if (it.topicId == topicId) it.copy(topicId = null) else it },
            )
        }
        sheet = null
    }

    fun deleteSubject(id: String) {
        store.update { d -> d.copy(subjects = d.subjects.filter { it.id != id }, sessions = d.sessions.filter { it.subjectId != id }) }
        subjectId = null
        confirm = null
    }

    // ---------- stats ----------

    fun chooseRange(r: Calc.Range) { range = r; selectedBar = null }

    fun moveRange(dir: Int) {
        anchor = when (range) {
            Calc.Range.WEEK -> anchor.plusWeeks(dir.toLong())
            Calc.Range.MONTH -> anchor.withDayOfMonth(1).plusMonths(dir.toLong())
            Calc.Range.YEAR -> anchor.plusYears(dir.toLong())
        }
        selectedBar = null
    }

    // ---------- settings ----------

    fun updateSettings(f: (Settings) -> Settings) = store.update { it.copy(settings = f(it.settings)) }

    fun setDefaultLength(min: Int) {
        val v = min.coerceIn(5, 240)
        store.update { it.copy(settings = it.settings.copy(defaultLength = v), focusLength = v) }
        customSelected = v !in PRESETS
    }

    /** Only worth a backup if there's something in it; an empty backup would replace a useful one. */
    private fun backupIfAny(reason: String) {
        if (data.subjects.isNotEmpty() || data.sessions.isNotEmpty()) store.saveBackup(reason)
    }

    fun clearAll() {
        backupIfAny("clearing all data")
        store.update { it.copy(subjects = emptyList(), sessions = emptyList(), sample = false, focusSubjectId = null, focusTopicId = null, run = null) }
        confirm = null
    }

    /** Replace everything with example data. Asks first when there's real data, and always keeps a backup. */
    fun loadSample() {
        if (!data.sample) backupIfAny("loading sample data")
        pauseSync()
        val keep = data.settings
        val fresh = Sample.create(System.currentTimeMillis(), zone)
        store.update { fresh.copy(settings = keep, focusLength = it.focusLength) }
        subjectId = null
        confirm = null
    }
}
