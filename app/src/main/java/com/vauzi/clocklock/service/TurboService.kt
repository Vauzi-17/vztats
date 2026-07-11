package com.vauzi.clocklock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vauzi.clocklock.MainActivity
import com.vauzi.clocklock.R
import com.vauzi.clocklock.core.CpuMonitor
import com.vauzi.clocklock.core.CpuSample
import com.vauzi.clocklock.core.FpsProbe
import com.vauzi.clocklock.core.GpuMonitor
import com.vauzi.clocklock.core.GpuSample
import com.vauzi.clocklock.core.PowerMonitor
import com.vauzi.clocklock.core.Prefs
import com.vauzi.clocklock.core.SessionRecorder
import com.vauzi.clocklock.core.SystemMonitor
import com.vauzi.clocklock.core.TurboManager
import com.vauzi.clocklock.shizuku.FpsSampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground keep-alive service. Three jobs:
 *  1. Re-apply turbo when the screen unlocks (README point 6).
 *  2. Host the floating overlay while it is enabled.
 *  3. Run optional thermal/battery auto-safety.
 *
 * It stays alive whenever the user wants turbo OR the overlay; otherwise it
 * stops itself.
 */
class TurboService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefs: Prefs
    private var overlay: OverlayController? = null
    private var autoSafetyTripped = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    if (prefs.reapplyOnUnlock) TurboManager.reapplyIfDesired(applicationContext)
                    scope.launch(Dispatchers.Main) { overlay?.refresh() }
                }
            }
        }
    }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Prefs.KEY_OVERLAY -> scope.launch(Dispatchers.Main) { syncOverlay() }
                Prefs.KEY_DESIRED_TURBO -> {
                    scope.launch(Dispatchers.Main) { overlay?.refresh() }
                    updateNotification()
                    if (prefs.desiredTurbo) autoSafetyTripped = false
                    stopIfNothingToDo()
                }
                Prefs.KEY_FLOAT_METRICS,
                Prefs.KEY_FLOAT_MODE,
                Prefs.KEY_FLOAT_OPACITY,
                Prefs.KEY_FLOAT_SIZE ->
                    scope.launch(Dispatchers.Main) { overlay?.rebuild() }
            }
        }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        createChannel()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        )
        prefs.registerListener(prefsListener)
        FpsSampler.attachAutoStart(this)
        startAsForeground()
        startAutoSafetyLoop()
        startStatsLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // onCreate already put us in the foreground; tear that down cleanly.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        scope.launch(Dispatchers.Main) { syncOverlay() }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        prefs.unregisterListener(prefsListener)
        overlay?.hide()
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Foreground / notification -------------------------------------------

    private fun startAsForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val on = TurboManager.state.value.desiredOn
        val content = when {
            autoSafetyTripped -> "Turbo auto-disabled (thermal/battery limit reached)"
            on -> "Turbo ON — GPU clock locked at max"
            else -> "Turbo OFF"
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_turbo)
            .setContentTitle("Adreno Clock Lock")
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        // Notification channels only exist on API 26+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.turbo_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.turbo_channel_desc) }
        nm.createNotificationChannel(channel)
    }

    // --- Overlay --------------------------------------------------------------

    private fun syncOverlay() {
        if (prefs.overlayEnabled && OverlayController.canDraw(this)) {
            if (overlay == null) overlay = OverlayController(this)
            overlay?.show()
            overlay?.refresh()
        } else {
            overlay?.hide()
        }
        stopIfNothingToDo()
    }

    // --- Auto-safety ----------------------------------------------------------

    private fun startAutoSafetyLoop() {
        scope.launch {
            while (isActive) {
                delay(5_000L)
                if (!prefs.autoSafetyEnabled) continue
                if (!prefs.desiredTurbo) continue

                val tempC = GpuMonitor.gpuTempMilliC()?.let { it / 1000f }
                val battery = batteryLevel()

                val overTemp = tempC != null && tempC >= prefs.tempLimitC
                val lowBattery = battery in 0..prefs.batteryLimitPct

                if (overTemp || lowBattery) {
                    autoSafetyTripped = true
                    TurboManager.forceOff(applicationContext)
                    withContext(Dispatchers.Main) { overlay?.refresh() }
                    updateNotification()
                }
            }
        }
    }

    // --- Live stats for the overlay ------------------------------------------

    private fun startStatsLoop() {
        scope.launch {
            while (isActive) {
                delay(1_000L)
                val ov = overlay
                val overlayShowing = ov != null && ov.isShowing
                val recording = SessionRecorder.recording.value
                if (!overlayShowing && !recording) continue

                val sample = SystemMonitor.snapshot(
                    applicationContext, null, null, FpsProbe.currentFps
                )
                if (recording) SessionRecorder.feed(sample)
                if (overlayShowing) {
                    withContext(Dispatchers.Main) {
                        ov?.updateStats(sample.gpu, sample.cpu, sample.power, sample.fps)
                    }
                }
            }
        }
    }

    private fun batteryLevel(): Int {
        val bm = getSystemService(BatteryManager::class.java) ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun stopIfNothingToDo() {
        if (!prefs.desiredTurbo && !prefs.overlayEnabled && !SessionRecorder.recording.value) {
            stopSelf()
        }
    }

    companion object {
        private const val CHANNEL_ID = "turbo_status"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.vauzi.clocklock.STOP"

        /**
         * Starts the service if there is a reason to run, stops it otherwise.
         * Always goes through startForegroundService so it is allowed even when
         * invoked from a background context (tile / overlay). Wrapped in
         * runCatching because starting an FGS from the background can still be
         * denied on some OEMs — turbo itself is already applied by then, so a
         * failure here only skips the keep-alive.
         */
        fun sync(context: Context) {
            val prefs = Prefs.get(context)
            val shouldRun = prefs.desiredTurbo || prefs.overlayEnabled ||
                SessionRecorder.recording.value
            val intent = Intent(context, TurboService::class.java)
            if (!shouldRun) intent.action = ACTION_STOP
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
