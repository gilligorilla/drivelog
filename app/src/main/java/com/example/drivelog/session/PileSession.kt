package com.example.drivelog.session

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.drivelog.audio.DetectorSettings
import com.example.drivelog.data.CsvLogger
import com.example.drivelog.data.Prefs
import com.example.drivelog.data.Storage
import com.example.drivelog.service.MonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * IDLE    - no pile session.
 * RUNNING - session active, microphone listening.
 * PAUSED  - session active, microphone switched off by the user (counts can still be entered by hand).
 */
enum class RunState { IDLE, RUNNING, PAUSED }

/** One plotted data point, captured whenever the depth changes. */
data class ProfilePoint(val depth: Int, val bpf: Int, val strokeInches: Int)

data class SessionState(
    // Persistent fields (survive Stop/reset; edited in the start-session dialog).
    val projectName: String = "",
    val hammerDescription: String = "",
    val pileDescription: String = "",
    // Cleared on Stop.
    val pileName: String = "",
    val runState: RunState = RunState.IDLE,
    val depth: Int = 0,
    val previousBpf: Int = 0,
    val currentBpf: Int = 0,
    val strokeInches: Int = 0,
    val points: List<ProfilePoint> = emptyList(),
    /** Null until the first action of the session creates the CSV file. */
    val csvFileName: String? = null,
    val statusMessage: String = "",
    val statusIsError: Boolean = false,
) {
    val isActive: Boolean get() = runState != RunState.IDLE
    val micOn: Boolean get() = runState == RunState.RUNNING
}

data class StorageStatus(
    val isSharedDocuments: Boolean = false,
    val path: String = "",
)

/**
 * Single source of truth for the pile being logged.
 *
 * It lives in the process (not in the Activity) so the foreground service keeps counting and
 * logging while the UI is gone, and the UI simply re-attaches to this state when it returns.
 * Every mutating action is serialised by [lock]; each loggable action writes its CSV row
 * immediately (streamed, never batched).
 *
 * The CSV file is created lazily: [beginSession] only starts the microphone, and the file (with its
 * four project/pile header lines) is created when the first row is about to be written.
 */
object PileSession {
    const val MAX_PROJECT = 20
    const val MAX_HAMMER = 20
    const val MAX_PILE_DESC = 40
    const val MAX_PILE_NAME = 10
    const val MAX_DEPTH = 999
    const val MAX_BPF = 999
    const val STROKE_MAX = 60
    const val MAX_SPLICE = 20
    const val MAX_NOTE = 100

    private val lock = Any()
    private lateinit var app: Application
    private lateinit var prefs: Prefs
    private var initialised = false

    /** True once this session's CSV file has been created. Guarded by [lock]. */
    private var csvOpened = false

    private val csv = CsvLogger { msg -> setStatus(msg, error = true) }
    private val ioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "drivelog-io") }

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _presets = MutableStateFlow(Storage.DEFAULT_PRESETS)
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    private val _storage = MutableStateFlow(StorageStatus())
    val storage: StateFlow<StorageStatus> = _storage.asStateFlow()

    private val _detector = MutableStateFlow(DetectorSettings())
    val detectorSettings: StateFlow<DetectorSettings> = _detector.asStateFlow()

    private val _levelDb = MutableStateFlow(-100f)
    /** Live short-time energy of the microphone in dB re full scale (for the tuning meter). */
    val levelDb: StateFlow<Float> = _levelDb.asStateFlow()

    private val _hitCounter = MutableStateFlow(0)
    /** Increments on every detected impact (also in test mode); used to flash the meter. */
    val hitCounter: StateFlow<Int> = _hitCounter.asStateFlow()

    fun init(application: Application) {
        synchronized(lock) {
            if (initialised) return
            initialised = true
            app = application
            prefs = Prefs(application)
            _state.value = SessionState(
                projectName = prefs.projectName,
                hammerDescription = prefs.hammerDescription,
                pileDescription = prefs.pileDescription,
            )
            _detector.value = prefs.detector
        }
        refreshStorageAndPresets()
    }

    // ---------------------------------------------------------------- session lifecycle

    /**
     * Starts a pile session from the start dialog: stores the descriptions (they persist for the
     * next pile), starts the foreground microphone service and begins listening. No CSV exists
     * yet; it is created by the first logged action.
     */
    fun beginSession(project: String, hammer: String, pileDescription: String, pileName: String) =
        locked {
            if (_state.value.isActive) return@locked
            val p = clean(project, MAX_PROJECT)
            val h = clean(hammer, MAX_HAMMER)
            val d = clean(pileDescription, MAX_PILE_DESC)
            prefs.projectName = p
            prefs.hammerDescription = h
            prefs.pileDescription = d
            csvOpened = false
            _state.value = SessionState(
                projectName = p,
                hammerDescription = h,
                pileDescription = d,
                pileName = clean(pileName, MAX_PILE_NAME),
                runState = RunState.RUNNING,
            )
            startService()
        }

    /**
     * Stop (after the user confirmed): closes the CSV, stops the microphone service and resets pile
     * name, depth, blow counts, stroke and chart. Project/hammer/pile descriptions and the
     * detector/filter settings are preserved.
     */
    fun stop() = locked {
        val s = _state.value
        if (!s.isActive) return@locked
        val saved = s.csvFileName
        if (csvOpened) {
            log("stop")
            csv.close()
            csvOpened = false
        }
        _state.value = SessionState(
            projectName = s.projectName,
            hammerDescription = s.hammerDescription,
            pileDescription = s.pileDescription,
            statusMessage = if (saved != null) "Saved $saved" else "Pile ended - nothing was logged",
        )
        app.stopService(Intent(app, MonitorService::class.java))
        _levelDb.value = -100f
    }

    /** Microphone icon tap: switches listening off/on in the middle of a session. */
    fun toggleMic() = locked {
        when (_state.value.runState) {
            RunState.RUNNING -> {
                _state.update { it.copy(runState = RunState.PAUSED) }
                log("pause")
            }
            RunState.PAUSED -> {
                _state.update { it.copy(runState = RunState.RUNNING, statusMessage = "", statusIsError = false) }
                log("start")
            }
            RunState.IDLE -> Unit
        }
    }

    // ---------------------------------------------------------------- depth / counts / stroke

    /** Depth tap: depth + 1. */
    fun addDepth() = locked {
        val s = _state.value
        if (!s.isActive) return@locked
        if (s.depth >= MAX_DEPTH) {
            setStatusLocked("Maximum depth reached", error = true)
            return@locked
        }
        applyDepth(s, s.depth + 1)
    }

    /** Depth long-press entry: jump to a typed depth, with the same BPF transfer/reset as a tap. */
    fun setDepth(target: Int) = locked {
        val s = _state.value
        if (!s.isActive) return@locked
        applyDepth(s, target.coerceIn(0, MAX_DEPTH))
    }

    /**
     * Current BPF -> previous BPF, plot point at [newDepth], CSV "depth" row, current BPF -> 0.
     * Points at or below [newDepth] are replaced, so re-entering a shallower depth corrects the
     * chart instead of making the profile zig-zag back on itself.
     */
    private fun applyDepth(s: SessionState, newDepth: Int) {
        val completed = s.currentBpf
        val next = s.copy(
            depth = newDepth,
            previousBpf = completed,
            currentBpf = 0,
            points = s.points.filter { it.depth < newDepth } +
                ProfilePoint(newDepth, completed, s.strokeInches),
        )
        _state.value = next
        // The row records the foot just completed (its blow count) before the counter resets.
        row("depth", newDepth, next.strokeInches, completed)
    }

    /** CURR BPF tap: +1, logged as "manual". */
    fun incrementBpf() = locked {
        val s = _state.value
        if (!s.isActive) return@locked
        if (s.currentBpf >= MAX_BPF) {
            setStatusLocked("Maximum blow count reached", error = true)
            return@locked
        }
        _state.value = s.copy(currentBpf = s.currentBpf + 1)
        log("manual")
    }

    /** CURR BPF long-press entry: overwrite the count, logged as "manual". */
    fun setBpf(value: Int) = locked {
        val s = _state.value
        if (!s.isActive) return@locked
        _state.value = s.copy(currentBpf = value.coerceIn(0, MAX_BPF))
        log("manual")
    }

    /** Stroke long-press entry (whole inches, 0..60), logged as "stroke". */
    fun setStroke(inches: Int) = locked {
        val s = _state.value
        if (!s.isActive) return@locked
        _state.value = s.copy(strokeInches = inches.coerceIn(0, STROKE_MAX))
        log("stroke")
    }

    // ---------------------------------------------------------------- notes

    fun logNote(text: String) = logText("note", text, MAX_NOTE)

    /** Engineering-format inches such as "8-3/4"; the caller formats, the string goes in Notes. */
    fun logInches(formatted: String) = logText("inches", formatted, MAX_NOTE)

    fun logSplice(text: String) = logText("splice", text, MAX_SPLICE)

    private fun logText(action: String, text: String, max: Int) = locked {
        if (!_state.value.isActive) return@locked
        val t = text.replace("\n", " ").replace("\r", " ").trim().take(max)
        if (t.isEmpty()) return@locked
        log(action, notes = t)
    }

    // ---------------------------------------------------------------- audio events

    /** Called from the audio thread for each detected hammer impact. */
    fun onImpact() = locked {
        _hitCounter.update { it + 1 }
        val s = _state.value
        if (s.runState != RunState.RUNNING) return@locked
        if (s.currentBpf >= MAX_BPF) return@locked
        _state.value = s.copy(currentBpf = s.currentBpf + 1)
        log("mic")
    }

    /** Impact seen by the settings-dialog test monitor; counts for the meter only. */
    fun onTestImpact() {
        _hitCounter.update { it + 1 }
    }

    fun reportLevel(db: Float) {
        _levelDb.value = db
    }

    fun reportError(message: String) = setStatus(message, error = true)

    // ---------------------------------------------------------------- settings / storage

    fun updateDetector(transform: (DetectorSettings) -> DetectorSettings) {
        val updated = synchronized(lock) {
            val v = transform(_detector.value)
            _detector.value = v
            v
        }
        prefs.detector = updated
    }

    /** Re-evaluates storage access and reloads the preset notes file (creating it if needed). */
    fun refreshStorageAndPresets() {
        ioExecutor.execute {
            val loc = Storage.resolve(app)
            _storage.value = StorageStatus(loc.isSharedDocuments, loc.dir.absolutePath)
            _presets.value = Storage.loadPresets(app)
        }
    }

    // ---------------------------------------------------------------- internals

    /** Logs [action] with the session's current depth / stroke / current BPF. */
    private fun log(action: String, notes: String = "") {
        val s = _state.value
        row(action, s.depth, s.strokeInches, s.currentBpf, notes)
    }

    /** Writes one CSV row, first creating the file (and its header lines) if this is the first. */
    private fun row(action: String, depth: Int, stroke: Int, bpf: Int, notes: String = "") {
        ensureCsvOpen()
        csv.append(CsvLogger.row(now(), action, depth, stroke, bpf, notes))
    }

    private fun ensureCsvOpen() {
        if (csvOpened) return
        csvOpened = true
        val s = _state.value
        val file = createLogFile(s.pileName)
        csv.open(
            file,
            CsvLogger.headerLines(s.projectName, s.hammerDescription, s.pileDescription, s.pileName),
        )
        _state.update {
            it.copy(csvFileName = file.name, statusMessage = "Logging to ${file.name}", statusIsError = false)
        }
    }

    private fun now(): String = timestampFormat.format(Date())

    private fun createLogFile(pileName: String): File {
        val dir = File(Storage.resolve(app).dir, Storage.LOG_SUBDIR).apply { mkdirs() }
        val safe = pileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "UNNAMED" }
        val base = "${fileStampFormat.format(Date())}_$safe"
        var file = File(dir, "$base.csv")
        var n = 2
        while (file.exists()) file = File(dir, "${base}_${n++}.csv")
        return file
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, MonitorService::class.java))
        } catch (e: Exception) {
            setStatusLocked("Cannot start background service: ${e.message}", error = true)
        }
    }

    private fun setStatus(msg: String, error: Boolean) = synchronized(lock) {
        setStatusLocked(msg, error)
    }

    private fun setStatusLocked(msg: String, error: Boolean) {
        _state.update { it.copy(statusMessage = msg, statusIsError = error) }
    }

    private fun clean(v: String, max: Int) = v.replace("\n", "").replace("\r", "").take(max)

    private inline fun locked(block: () -> Unit) {
        synchronized(lock) { block() }
    }
}
