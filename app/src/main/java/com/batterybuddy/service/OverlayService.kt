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
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.batterybuddy.battery.BatteryState
import com.batterybuddy.data.OverlayPreferences
import com.batterybuddy.data.PreferencesRepository
import com.batterybuddy.event.EventEnvironment
import com.batterybuddy.event.EventEnvironmentResolver
import com.batterybuddy.event.EventEnvironmentView
import com.batterybuddy.event.EventMode
import com.batterybuddy.pet.PetAIController
import com.batterybuddy.pet.PetBehaviorState
import com.batterybuddy.pet.PetView
import com.batterybuddy.pet.PlaygroundBoundaryView
import com.batterybuddy.weather.WeatherCondition
import com.batterybuddy.weather.WeatherRepository
import com.batterybuddy.weather.WeatherSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var petView: PetView? = null
    private var eventEnvironmentView: EventEnvironmentView? = null
    private var boundaryView: PlaygroundBoundaryView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var eventOverlayParams: WindowManager.LayoutParams? = null

    private lateinit var preferencesRepository: PreferencesRepository

    // Fix 1: Lifecycle Job & Coroutine Scope
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val petAIController = PetAIController()
    private val weatherRepository = WeatherRepository()
    private val handler = Handler(Looper.getMainLooper())
    private var hideBoundaryRunnable: Runnable? = null
    private var weatherRefreshJob: Job? = null
    private var eventRefreshJob: Job? = null

    private var currentBatteryState = BatteryState()
    private var currentPreferences = OverlayPreferences()
    private var currentWeatherCondition = WeatherCondition.CLEAR

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "battery_buddy_overlay_channel"
        const val ACTION_START = "com.batterybuddy.action.START_OVERLAY"
        const val ACTION_STOP = "com.batterybuddy.action.STOP_OVERLAY"
        const val ACTION_TEST_STATE = "com.batterybuddy.action.TEST_STATE"
        const val ACTION_TEST_WEATHER = "com.batterybuddy.action.TEST_WEATHER"
        const val ACTION_TEST_LIGHTNING = "com.batterybuddy.action.TEST_LIGHTNING"
        const val ACTION_REFRESH_WEATHER = "com.batterybuddy.action.REFRESH_WEATHER"
        const val ACTION_TEST_EVENT = "com.batterybuddy.action.TEST_EVENT"
        const val EXTRA_STATE = "state"
        const val EXTRA_WEATHER = "weather"
        const val EXTRA_EVENT = "event"
        private const val WEATHER_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        private const val EVENT_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
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
                when (intent?.action) {
                    ACTION_TEST_STATE -> {
                        petAIController.start(serviceScope)
                        intent.getStringExtra(EXTRA_STATE)
                            ?.let { name -> runCatching { PetBehaviorState.valueOf(name) }.getOrNull() }
                            ?.let(petAIController::forceBehavior)
                    }
                    ACTION_TEST_WEATHER -> intent.getStringExtra(EXTRA_WEATHER)
                        ?.let { name -> runCatching { WeatherCondition.valueOf(name) }.getOrNull() }
                        ?.let(::applyTestWeather)
                    ACTION_TEST_LIGHTNING -> {
                        petAIController.start(serviceScope)
                        petAIController.forceLightning()
                    }
                    ACTION_REFRESH_WEATHER -> refreshWeather(force = true)
                    ACTION_TEST_EVENT -> intent.getStringExtra(EXTRA_EVENT)
                        ?.let { name -> runCatching { EventEnvironment.valueOf(name) }.getOrNull() }
                        ?.let(::applyEventEnvironment)
                }
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
        var lastWeatherConfig: Triple<Boolean, Int, Int>? = null
        var lastEventMode: EventMode? = null

        serviceScope.launch {
            preferencesRepository.overlayPreferencesFlow.collectLatest { prefs ->
                val boundsChanged = (lastMinX != -1 && (lastMinX != prefs.minX || lastMaxX != prefs.maxX || lastY != prefs.overlayY))
                lastMinX = prefs.minX
                lastMaxX = prefs.maxX
                lastY = prefs.overlayY

                currentPreferences = prefs
                applyPreferencesToOverlay()

                val weatherConfig = Triple(
                    prefs.weatherEnabled,
                    prefs.weatherLatitudeE6,
                    prefs.weatherLongitudeE6
                )
                if (weatherConfig != lastWeatherConfig) {
                    lastWeatherConfig = weatherConfig
                    restartWeatherUpdates()
                }

                if (prefs.eventMode != lastEventMode) {
                    lastEventMode = prefs.eventMode
                    restartEventUpdates()
                }

                if (prefs.isPetMode) {
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

    private fun restartWeatherUpdates() {
        weatherRefreshJob?.cancel()
        weatherRefreshJob = null

        if (!currentPreferences.weatherEnabled) {
            petAIController.updateWeather(null)
            currentWeatherCondition = WeatherCondition.CLEAR
            eventEnvironmentView?.weatherCondition = WeatherCondition.CLEAR
            return
        }

        weatherRefreshJob = serviceScope.launch {
            while (true) {
                fetchAndApplyWeather(force = false)
                delay(WEATHER_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refreshWeather(force: Boolean) {
        if (!currentPreferences.weatherEnabled) return
        serviceScope.launch { fetchAndApplyWeather(force) }
    }

    private fun restartEventUpdates() {
        eventRefreshJob?.cancel()
        eventRefreshJob = serviceScope.launch {
            while (true) {
                applyEventEnvironment(
                    EventEnvironmentResolver.resolve(currentPreferences.eventMode)
                )
                if (currentPreferences.eventMode != EventMode.AUTO) break
                delay(EVENT_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun applyEventEnvironment(environment: EventEnvironment) {
        eventEnvironmentView?.environment = environment
    }

    private suspend fun fetchAndApplyWeather(force: Boolean) {
        val latitude = currentPreferences.weatherLatitudeE6 / 1_000_000.0
        val longitude = currentPreferences.weatherLongitudeE6 / 1_000_000.0
        runCatching {
            weatherRepository.getCurrentWeather(latitude, longitude, force)
        }.onSuccess(::applyWeather)
    }

    private fun applyTestWeather(condition: WeatherCondition) {
        val isWet = condition == WeatherCondition.RAIN ||
            condition == WeatherCondition.HEAVY_RAIN ||
            condition == WeatherCondition.STORM
        val isWindy = condition == WeatherCondition.WIND ||
            condition == WeatherCondition.STORM
        applyWeather(
            WeatherSnapshot(
                condition = condition,
                weatherCode = 0,
                temperatureC = 25.0,
                precipitationMm = if (isWet) 5.0 else 0.0,
                windSpeedKmh = if (isWindy) 45.0 else 5.0,
                windGustKmh = if (condition == WeatherCondition.STORM) 65.0 else 10.0,
                isDay = true,
                fetchedAtMs = System.currentTimeMillis()
            )
        )
    }

    private fun applyWeather(snapshot: WeatherSnapshot) {
        petAIController.updateWeather(snapshot)
        currentWeatherCondition = snapshot.condition
        eventEnvironmentView?.weatherCondition = snapshot.condition
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
        val channelName = "Status Cat Overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Running virtual pet overlay in background"
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Status Cat Active 🐱")
            .setContentText("Status bar virtual pet is playing")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun showOverlayView() {
        if (petView != null) return

        showEventEnvironmentView()

        val view = PetView(this)
        view.isClickable = true
        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!petAIController.isRunning) {
                        petAIController.start(serviceScope)
                    }
                    petAIController.reactToPoke()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    touchedView.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
            view.post {
                updateCenterNoStopZone()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showEventEnvironmentView() {
        if (eventEnvironmentView != null) return

        val view = EventEnvironmentView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            EventEnvironmentView.desiredHeightPx(this),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }

        try {
            windowManager?.addView(view, params)
            eventEnvironmentView = view
            eventOverlayParams = params
            view.weatherCondition = currentWeatherCondition
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyPreferencesToOverlay() {
        val environment = EventEnvironmentResolver.resolve(currentPreferences.eventMode)
        eventEnvironmentView?.environment = environment
        eventEnvironmentView?.weatherCondition = currentWeatherCondition
        val environmentView = eventEnvironmentView
        eventOverlayParams?.let { eventParams ->
            eventParams.x = 0
            eventParams.y = 0
            eventParams.height = EventEnvironmentView.desiredHeightPx(this)
            try {
                if (environmentView != null) {
                    windowManager?.updateViewLayout(environmentView, eventParams)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val view = petView ?: return
        val params = overlayParams ?: return

        view.customSizeSp = currentPreferences.overlaySize.toFloat()
        view.showPercentageBadge = currentPreferences.showPercentage
        view.batteryState = currentBatteryState

        petAIController.updatePlaygroundBounds(
            minX = currentPreferences.minX,
            maxX = currentPreferences.maxX,
            petWidthPx = view.desiredWidthPx()
        )
        updateCenterNoStopZone()

        params.y = currentPreferences.overlayY

        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCenterNoStopZone() {
        val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.width()
                ?: resources.displayMetrics.widthPixels
        } else {
            resources.displayMetrics.widthPixels
        }
        val screenCenterX = screenWidth / 2
        val marginPx = (4 * resources.displayMetrics.density).toInt()
        petAIController.updateNoStopZone(
            left = screenCenterX,
            right = screenCenterX,
            marginPx = marginPx
        )
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
        eventEnvironmentView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            eventEnvironmentView = null
            eventOverlayParams = null
        }
    }

    override fun onDestroy() {
        // Fix 1: Cancel Coroutine Job to prevent memory leaks
        weatherRefreshJob?.cancel()
        eventRefreshJob?.cancel()
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
