package com.vauzi.clocklock.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.vauzi.clocklock.core.FpsProbe
import com.vauzi.clocklock.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Measures the real FPS of the foreground game through Shizuku (shell uid), by
 * parsing `dumpsys SurfaceFlinger --latency` — data a normal app can't read.
 *
 * The output is fed into [FpsProbe] so the rest of the app (monitor, overlay,
 * session recorder) stays decoupled from Shizuku.
 */
object FpsSampler {

    enum class State { UNAVAILABLE, NEED_PERMISSION, STARTING, RUNNING, ERROR }

    private val _state = MutableStateFlow(State.UNAVAILABLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _message = MutableStateFlow("Shizuku FPS is off.")
    val message: StateFlow<String> = _message.asStateFlow()

    private const val REQ_CODE = 4210

    private var appContext: Context? = null
    private var service: IUserService? = null
    private var loopScope: CoroutineScope? = null
    private var args: Shizuku.UserServiceArgs? = null

    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (t: Throwable) {
        false
    }

    private var attached = false

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener { tryAutoStart() }
    private val binderDeadListener =
        Shizuku.OnBinderDeadListener { onBinderDead() }

    /**
     * Registers Shizuku listeners once and resumes FPS if the user previously
     * enabled it and Shizuku is available. Call from the Activity/service start
     * so a single activation keeps working across app launches (and after a
     * Shizuku restart) with no wifi/adb needed again.
     */
    fun attachAutoStart(context: Context) {
        appContext = context.applicationContext
        if (!attached) {
            attached = true
            runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
            }
        }
        tryAutoStart()
    }

    private fun tryAutoStart() {
        val ctx = appContext ?: return
        if (!Prefs.get(ctx).fpsEnabled || isOn) return
        if (!shizukuReady()) return
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
        if (granted) bindAndStart()
    }

    private fun onBinderDead() {
        stopLoop()
        service = null
        cachedLayer = null
        FpsProbe.update(null)
        val stillWanted = appContext?.let { Prefs.get(it).fpsEnabled } == true
        if (stillWanted) set(State.UNAVAILABLE, "Shizuku stopped — restart it to resume FPS.")
    }

    fun enable(context: Context) {
        appContext = context.applicationContext
        Prefs.get(context).fpsEnabled = true
        if (!shizukuReady()) {
            set(State.UNAVAILABLE, "Shizuku isn't running. Open Shizuku and start it first.")
            return
        }
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            false
        }
        if (!granted) {
            set(State.NEED_PERMISSION, "Waiting for Shizuku permission…")
            runCatching {
                Shizuku.removeRequestPermissionResultListener(permListener)
                Shizuku.addRequestPermissionResultListener(permListener)
                Shizuku.requestPermission(REQ_CODE)
            }.onFailure { set(State.ERROR, "Couldn't request permission: ${it.message}") }
            return
        }
        bindAndStart()
    }

    fun disable() {
        appContext?.let { Prefs.get(it).fpsEnabled = false }
        stopLoop()
        unbind()
        FpsProbe.setActive(false)
        set(State.UNAVAILABLE, "Shizuku FPS is off.")
    }

    val isOn: Boolean get() = _state.value == State.RUNNING || _state.value == State.STARTING

    private val permListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQ_CODE) {
            if (result == PackageManager.PERMISSION_GRANTED) bindAndStart()
            else set(State.NEED_PERMISSION, "Shizuku permission denied.")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service = IUserService.Stub.asInterface(binder)
                startLoop()
                set(State.RUNNING, "Measuring FPS…")
            } else {
                set(State.ERROR, "Shizuku service returned no binder.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private fun bindAndStart() {
        val ctx = appContext ?: return
        set(State.STARTING, "Starting Shizuku service…")
        val a = Shizuku.UserServiceArgs(
            ComponentName(ctx.packageName, ShellUserService::class.java.name)
        ).daemon(false).processNameSuffix("fps").debuggable(false).version(1)
        args = a
        runCatching { Shizuku.bindUserService(a, connection) }
            .onFailure { set(State.ERROR, "Bind failed: ${it.message}") }
    }

    private fun unbind() {
        val a = args ?: return
        runCatching { Shizuku.unbindUserService(a, connection, true) }
        service = null
        args = null
    }

    private fun startLoop() {
        stopLoop()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        loopScope = scope
        FpsProbe.setActive(true)
        scope.launch {
            while (isActive) {
                val svc = service
                if (svc == null) {
                    FpsProbe.update(null)
                } else {
                    val fps = runCatching { measureFps(svc) }.getOrNull()
                    FpsProbe.update(fps)
                }
                delay(1000)
            }
        }
    }

    private fun stopLoop() {
        loopScope?.cancel()
        loopScope = null
    }

    // --- FPS measurement ------------------------------------------------------

    private var cachedLayer: String? = null

    private fun measureFps(svc: IUserService): Int? {
        cachedLayer?.let { c ->
            parseLatency(svc.exec("dumpsys SurfaceFlinger --latency \"$c\""))?.let {
                _message.value = "Measuring FPS…"
                return it
            }
            cachedLayer = null
        }

        val candidates = detectLayerCandidates(svc)
        for (name in candidates) {
            val fps = parseLatency(svc.exec("dumpsys SurfaceFlinger --latency \"$name\""))
            if (fps != null) {
                cachedLayer = name
                _message.value = "Measuring FPS…"
                return fps
            }
        }

        _message.value = if (candidates.isEmpty())
            "FPS: no game surface focused yet."
        else
            "FPS: layer found but no frame data. Try Winlator's OpenGL/Vulkan mode."
        return null
    }

    /**
     * Builds candidate SurfaceFlinger layer names for the focused app. The modern
     * `--list` wraps names as `RequestedLayerState{<name> <ts>#<id> ...}`, so we
     * strip the wrapper/metadata and also try the name without the handle prefix
     * and without the (BLAST) suffix, since `--latency` matching varies.
     */
    private fun detectLayerCandidates(svc: IUserService): List<String> {
        val focus = svc.exec("dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'")
        val pkg = Regex("([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.]+").find(focus)?.groupValues?.getOrNull(1)

        val out = LinkedHashSet<String>()
        svc.exec("dumpsys SurfaceFlinger --list").lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.contains("SurfaceView[")) return@forEach
            if (pkg != null && !line.contains(pkg)) return@forEach

            val inner = Regex("""RequestedLayerState\{(.+)}""").find(line)?.groupValues?.get(1) ?: line
            val name = inner
                .replace(Regex("""\s+\d\d-\d\d \d\d:\d\d:\d\d\.\d+#\d+.*$"""), "")
                .trim()
            if (name.isBlank() || name.startsWith("Background")) return@forEach

            out.add(name)
            val noHandle = name.replace(Regex("^[0-9a-fA-F]+\\s+"), "")
            out.add(noHandle)
            out.add(noHandle.replace("(BLAST)", "").trim())
        }
        return out.toList()
    }

    /**
     * Parses `--latency` output: first line is the refresh period (ns); each
     * following line has three timestamps, the middle one being the frame's
     * present time. FPS = (frames - 1) / span in seconds.
     */
    private fun parseLatency(output: String): Int? {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 3) return null

        val present = ArrayList<Long>(lines.size)
        for (i in 1 until lines.size) {
            val cols = lines[i].split(Regex("\\s+"))
            if (cols.size < 3) continue
            val t = cols[1].toLongOrNull() ?: continue
            if (t > 0L && t != Long.MAX_VALUE) present.add(t)
        }
        if (present.size < 2) return null

        val first = present.first()
        val last = present.last()
        val spanSec = (last - first) / 1_000_000_000.0
        if (spanSec <= 0.0) return null

        val fps = (present.size - 1) / spanSec
        if (fps <= 0.0 || fps > 400.0) return null
        return fps.toInt()
    }

    private fun set(state: State, message: String) {
        _state.value = state
        _message.value = message
    }
}
