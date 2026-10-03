package com.khawar.studia.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** A second copy of the data somewhere else, such as a save file in a folder the user chose. */
interface Remote {
    /** Short name of where the copy lives, used in messages. */
    val label: String
    val fileName: String?
    /** Human-readable location, e.g. "Documents/studia.json". */
    val location: String
    fun read(): String?
    fun write(text: String)
}

data class SyncState(
    val label: String? = null,
    val fileName: String? = null,
    val location: String? = null,
    val lastSynced: Long? = null,
    val error: String? = null,
) {
    val connected: Boolean get() = label != null
}

/** The saved data couldn't be read at startup. The original is never overwritten; it's kept as [damagedFile]. */
data class LoadProblem(val message: String, val damagedFile: File?)

/** A copy of everything taken just before a big replacement (sample data, clearing, opening a file). */
@Serializable
data class Backup(val reason: String, val time: Long, val data: AppData)

/**
 * Holds all app data in memory and saves it as one JSON file on the phone.
 * Saves are debounced (the dial can fire many updates a second) and written to
 * a temp file first, so a crash mid-write never corrupts the real file.
 *
 * When a [Remote] is connected, every save is also copied there, and [pull]
 * brings in the remote copy if it was changed more recently (another phone).
 * Nothing read from disk replaces data unless [DataFile.read] accepts it.
 */
class Store(
    private val file: File,
    private val scope: CoroutineScope,
    seed: () -> AppData,
) {
    private val json = DataFile.json
    private val lock = Any()
    private val remoteLock = Any()   // one remote write at a time
    private var pending: Job? = null
    @Volatile private var remote: Remote? = null
    private val tmpFile = File(file.path + ".tmp")
    private val prevFile = File(file.parentFile, "studia.previous.json")   // the save before the latest one
    private val backupFile = File(file.parentFile, "studia.backup.json")

    private val _state: MutableStateFlow<AppData>
    val state: StateFlow<AppData>
    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()
    private val _problem = MutableStateFlow<LoadProblem?>(null)
    val problem: StateFlow<LoadProblem?> = _problem.asStateFlow()
    private val _backup = MutableStateFlow(readBackup())
    val backup: StateFlow<Backup?> = _backup.asStateFlow()

    init {
        var loaded: AppData? = null
        if (file.exists()) {
            when (val r = readFile(file)) {
                is DataFile.Result.Ok -> loaded = r.data
                is DataFile.Result.Bad -> {
                    // Keep the damaged file aside, then try the last good copy.
                    val kept = File(file.parentFile, "studia.damaged-${System.currentTimeMillis()}.json")
                    val savedAside = runCatching { file.copyTo(kept, overwrite = true); true }.getOrDefault(false)
                    val fromTmp = (readFile(tmpFile) as? DataFile.Result.Ok)?.data
                        ?: (readFile(prevFile) as? DataFile.Result.Ok)?.data
                    loaded = fromTmp ?: AppData()           // empty, never sample data over real data
                    _problem.value = LoadProblem(
                        if (fromTmp != null) "Your saved data was damaged, so Studia loaded the last good copy. The damaged file was kept."
                        else "Your saved data couldn’t be read, so Studia started empty instead of replacing it. The damaged file was kept.",
                        if (savedAside) kept else null,
                    )
                }
            }
        } else {
            // A crash in the middle of saving can leave only the temp or previous file.
            loaded = (readFile(tmpFile) as? DataFile.Result.Ok)?.data
                ?: (readFile(prevFile) as? DataFile.Result.Ok)?.data
        }
        _state = MutableStateFlow(loaded ?: seed())
        state = _state.asStateFlow()
        if (loaded == null || _problem.value != null) schedule()
    }

    val value: AppData get() = _state.value

    fun update(f: (AppData) -> AppData) {
        _state.update { f(it).copy(savedAt = System.currentTimeMillis()) }
        schedule()
    }

    fun dismissProblem() { _problem.value = null }

    /** Save now; called when the app goes to the background. The remote copy follows in the background. */
    fun flush() {
        pending?.cancel()
        val text = encode()
        writeLocal(text)
        if (remote != null) scope.launch(Dispatchers.IO) { pushRemote(text) }
    }

    // ---------- backups ----------

    /** Keep a copy of everything before a big replacement, so it can be undone. */
    fun saveBackup(reason: String) {
        val b = Backup(reason, System.currentTimeMillis(), _state.value)
        runCatching { backupFile.writeText(json.encodeToString(Backup.serializer(), b)) }
        _backup.value = b
    }

    /** Put back the data from the last backup. */
    fun restoreBackup(): Boolean {
        val b = _backup.value ?: return false
        _state.value = b.data.copy(savedAt = System.currentTimeMillis())
        runCatching { backupFile.delete() }
        _backup.value = null
        schedule()
        return true
    }

    private fun readBackup(): Backup? = runCatching {
        if (backupFile.exists()) json.decodeFromString(Backup.serializer(), backupFile.readText()) else null
    }.getOrNull()?.takeIf { DataFile.validate(it.data) == null }

    // ---------- save file ----------

    /**
     * Start copying to [r]. With [useRemoteData], the file's contents replace what's
     * on the phone (after a backup). The file only counts as connected once a save to
     * it has actually worked. Returns an error message, or null on success.
     */
    suspend fun connect(r: Remote, useRemoteData: Boolean, push: Boolean = true): String? = withContext(Dispatchers.IO) {
        if (useRemoteData) {
            val text = runCatching { r.read() }.getOrNull() ?: return@withContext "Couldn’t read that file."
            when (val res = DataFile.read(text)) {
                is DataFile.Result.Bad -> return@withContext res.reason
                is DataFile.Result.Ok -> {
                    saveBackup("opening a save file")
                    _state.value = res.data
                    writeLocal(encode())
                }
            }
        }
        if (push) {
            val err = synchronized(remoteLock) { runCatching { r.write(encode()) }.exceptionOrNull() }
            if (err != null) return@withContext "Couldn’t save to that file, so it wasn’t connected."
        }
        remote = r
        _sync.value = SyncState(r.label, r.fileName, r.location, lastSynced = if (push) System.currentTimeMillis() else null)
        null
    }

    fun disconnect() {
        remote = null
        _sync.value = SyncState()
    }

    /** Bring in the remote copy if it is newer than ours and is valid Studia data. */
    suspend fun pull() = withContext(Dispatchers.IO) {
        val r = remote ?: return@withContext
        val text = runCatching { r.read() }.getOrNull()
        if (text == null) {
            _sync.update { it.copy(error = "Couldn’t reach ${r.label}. Your data is safe on this phone.") }
            return@withContext
        }
        val d = when (val res = DataFile.read(text)) {
            is DataFile.Result.Ok -> res.data
            is DataFile.Result.Bad -> {
                _sync.update { it.copy(error = "Your save file couldn’t be used: ${res.reason} Your data is safe on this phone.") }
                return@withContext
            }
        }
        if (d.savedAt > _state.value.savedAt) {
            _state.value = d
            writeLocal(encode())
        } else if (d.savedAt < _state.value.savedAt) {
            pushRemote(encode())
            return@withContext
        }
        _sync.update { it.copy(lastSynced = System.currentTimeMillis(), error = null) }
    }

    /** The current data as JSON, e.g. to create a new save file with it. */
    fun snapshotJson(): String = encode()

    suspend fun push() = withContext(Dispatchers.IO) { pushRemote(encode()) }

    private fun schedule() {
        pending?.cancel()
        pending = scope.launch(Dispatchers.IO) {
            delay(300)
            val text = encode()
            writeLocal(text)
            pushRemote(text)
        }
    }

    private fun readFile(f: File): DataFile.Result =
        if (!f.exists()) DataFile.Result.Bad("missing")
        else runCatching { DataFile.read(f.readText()) }.getOrElse { DataFile.Result.Bad("The file couldn’t be opened.") }

    private fun hasData(f: File): Boolean =
        (readFile(f) as? DataFile.Result.Ok)?.data?.let { it.subjects.isNotEmpty() || it.sessions.isNotEmpty() } == true

    private fun encode(): String = json.encodeToString(AppData.serializer(), _state.value)

    private fun writeLocal(text: String) = synchronized(lock) {
        tmpFile.writeText(text)
        // Keep the current save as the "previous" copy for recovery, but only if it's
        // valid and has something in it, so an empty or damaged save never pushes out
        // the last copy with real data.
        if (file.exists() && hasData(file)) { prevFile.delete(); file.renameTo(prevFile) }
        if (!tmpFile.renameTo(file)) tmpFile.copyTo(file, overwrite = true)
    }

    private fun pushRemote(text: String) {
        val r = remote ?: return
        synchronized(remoteLock) { runCatching { r.write(text) } }
            .onSuccess { _sync.update { it.copy(lastSynced = System.currentTimeMillis(), error = null) } }
            .onFailure { _sync.update { it.copy(error = "Couldn’t save to ${r.label}. Your data is safe in the app and will be copied when it can.") } }
    }
}
