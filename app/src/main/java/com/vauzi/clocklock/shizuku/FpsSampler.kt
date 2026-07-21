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
        lastFps = 0
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
        runCatching { service?.exec("dumpsys SurfaceFlinger --timestats -disable -clear") }
        stopLoop()
        unbind()
        FpsProbe.setActive(false)
        lastFps = 0
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
                // Only start sampling if the user actually wants FPS; the shell may
                // have been bound just to run one-off commands (e.g. RAM boost).
                if (appContext?.let { Prefs.get(it).fpsEnabled } == true) {
                    startLoop()
                    set(State.RUNNING, "Measuring FPS…")
                }
            } else {
                set(State.ERROR, "Shizuku service returned no binder.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    /** True when the Shizuku shell user-service is bound and usable. */
    val shellReady: Boolean get() = service != null

    /** Runs a shell command through Shizuku; null when the shell isn't bound. */
    fun execShell(cmd: String): String? = runCatching { service?.exec(cmd) }.getOrNull()

    /**
     * Binds the shell user-service *without* starting FPS sampling, so features
     * like RAM boost can use Shizuku even when the FPS counter is off.
     */
    fun ensureShellBound(context: Context) {
        appContext = context.applicationContext
        if (service != null || !shizukuReady()) return
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) return
        val ctx = appContext ?: return
        val a = args ?: Shizuku.UserServiceArgs(
            ComponentName(ctx.packageName, ShellUserService::class.java.name)
        ).daemon(false).processNameSuffix("fps").debuggable(false).version(1)
        args = a
        runCatching { Shizuku.bindUserService(a, connection) }
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
        lastFps = 0
        scope.launch {
            runCatching { service?.exec("dumpsys SurfaceFlinger --timestats -clear -enable") }
            while (isActive) {
                val svc = service
                if (svc == null) FpsProbe.update(null)
                else FpsProbe.update(runCatching { measureFps(svc) }.getOrNull())
                delay(1000)
            }
        }
    }

    private fun stopLoop() {
        loopScope?.cancel()
        loopScope = null
    }

    // --- FPS measurement (SurfaceFlinger --timestats) -------------------------
    // Matches how open-source Shizuku FPS meters work (e.g. FrameX-Android): read
    // the global averageFPS from SurfaceFlinger's timestats. This is the accurate
    // method on Android 12+/14 and needs no per-game layer detection, so it works
    // for any game — native Android or Winlator, whatever the render backend.

    private var lastFps = 0
    private val fpsRegex = Regex("averageFPS\\s*=\\s*([0-9.]+)")

    private fun measureFps(svc: IUserService): Int {
        // Clear+re-enable during the first second of each 3s cycle so averageFPS
        // reflects a fresh ~2s window instead of a long drifting average.
        if (System.currentTimeMillis() % 3000L < 1000L) {
            svc.exec("dumpsys SurfaceFlinger --timestats -clear -enable")
        }
        val dump = svc.exec("dumpsys SurfaceFlinger --timestats -dump")
        val fps = fpsRegex.find(dump)?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: 0
        if (fps > 0) {
            lastFps = fps
            _message.value = "Measuring FPS…"
        } else if (lastFps == 0) {
            _message.value = "FPS: no frames captured yet — open a game."
        }
        return lastFps
    }

    private fun set(state: State, message: String) {
        _state.value = state
        _message.value = message
    }
}
