package com.batterybuddy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.batterybuddy.battery.BatteryState
import com.batterybuddy.data.OverlayPreferences
import com.batterybuddy.data.PreferencesRepository
import com.batterybuddy.pet.PetAIController
import com.batterybuddy.pet.PetView
import com.batterybuddy.pet.PlaygroundBoundaryView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var petView: PetView? = null
    private var boundaryView: PlaygroundBoundaryView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private lateinit var preferencesRepository: PreferencesRepository

    // Fix 1: Lifecycle Job & Coroutine Scope
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val petAIController = PetAIController()
    private val handler = Handler(Looper.getMainLooper())
    private var hideBoundaryRunnable: Runnable? = null

    private var currentBatteryState = BatteryState()
    private var currentPreferences = OverlayPreferences()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "battery_buddy_overlay_channel"
        const val ACTION_START = "com.batterybuddy.action.START_OVERLAY"
        const val ACTION_STOP = "com.batterybuddy.action.STOP_OVERLAY"
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else 100

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                currentBatteryState = BatteryState(percentage = percentage, isCharging = isCharging)
                petAIController.updateBatteryState(currentBatteryState)

                petView?.batteryState = currentBatteryState
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferencesRepository = PreferencesRepository(this)

        setupPetAIController()
        registerBatteryReceiver()
        observePreferences()
    }

    private fun setupPetAIController() {
        petAIController.onPositionChanged = { newX, isFacingRight ->
            serviceScope.launch(Dispatchers.Main) {
                val view = petView
                val params = overlayParams
                if (view != null && params != null) {
                    view.isFacingRight = isFacingRight
                    params.x = newX
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        petAIController.onStateChanged = { newState ->
            serviceScope.launch(Dispatchers.Main) {
                petView?.behaviorState = newState
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundServiceNotification()
                showOverlayView()
            }
        }
        return START_STICKY
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun observePreferences() {
        var lastMinX = -1
        var lastMaxX = -1
        var lastY = -999

        serviceScope.launch {
            preferencesRepository.overlayPreferencesFlow.collectLatest { prefs ->
                val boundsChanged = (lastMinX != -1 && (lastMinX != prefs.minX || lastMaxX != prefs.maxX || lastY != prefs.overlayY))
                lastMinX = prefs.minX
                lastMaxX = prefs.maxX
                lastY = prefs.overlayY

                currentPreferences = prefs
                applyPreferencesToOverlay()

                if (prefs.isPetMode) {
                    petAIController.minX = prefs.minX
                    petAIController.maxX = prefs.maxX
                    petAIController.start(serviceScope)

                    if (boundsChanged) {
                        showBoundaryHighlight(prefs.minX, prefs.maxX)
                    }
                } else {
                    petAIController.stop()
                }
            }
        }
    }

    private fun showBoundaryHighlight(minX: Int, maxX: Int) {
        val wm = windowManager ?: return

        if (boundaryView == null) {
            val bView = PlaygroundBoundaryView(this)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                (45 * resources.displayMetrics.density).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = currentPreferences.overlayY
            }

            try {
                wm.addView(bView, params)
                boundaryView = bView
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val params = boundaryView?.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.y = currentPreferences.overlayY
                try {
                    wm.updateViewLayout(boundaryView, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        boundaryView?.minX = minX
        boundaryView?.maxX = maxX
        boundaryView?.visibility = android.view.View.VISIBLE

        hideBoundaryRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            boundaryView?.let { bView ->
                try {
                    wm.removeView(bView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                boundaryView = null
            }
        }
        hideBoundaryRunnable = runnable
        handler.postDelayed(runnable, 2500L)
    }

    private fun startForegroundServiceNotification() {
        val channelName = "BatteryBuddy Overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running virtual pet overlay in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BatteryBuddy Active 🐱")
            .setContentText("Status bar virtual pet is playing")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showOverlayView() {
        if (petView != null) return

        val view = PetView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentPreferences.minX
            y = currentPreferences.overlayY
        }

        try {
            windowManager?.addView(view, params)
            petView = view
            overlayParams = params

            applyPreferencesToOverlay()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyPreferencesToOverlay() {
        val view = petView ?: return
        val params = overlayParams ?: return

        view.customSizeSp = currentPreferences.overlaySize.toFloat()
        view.showPercentageBadge = currentPreferences.showPercentage
        view.batteryState = currentBatteryState

        params.y = currentPreferences.overlayY

        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlayView() {
        petAIController.stop()
        petView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            petView = null
            overlayParams = null
        }
    }

    override fun onDestroy() {
        // Fix 1: Cancel Coroutine Job to prevent memory leaks
        serviceJob.cancel()

        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        removeOverlayView()
        super.onDestroy()
    }
}
