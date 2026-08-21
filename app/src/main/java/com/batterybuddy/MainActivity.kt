package com.batterybuddy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.batterybuddy.battery.BatteryState
import com.batterybuddy.data.OverlayPreferences
import com.batterybuddy.data.PreferencesRepository
import com.batterybuddy.event.EventEnvironment
import com.batterybuddy.event.EventMode
import com.batterybuddy.overlay.CharacterStateMapper
import com.batterybuddy.pet.PetBehaviorState
import com.batterybuddy.service.OverlayService
import com.batterybuddy.ui.theme.BatteryBuddyTheme
import com.batterybuddy.weather.WeatherCondition
import com.batterybuddy.weather.WeatherRepository
import com.batterybuddy.weather.WeatherSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BatteryBuddyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = remember { PreferencesRepository(context) }
    val weatherRepository = remember { WeatherRepository() }
    val preferences by preferencesRepository.overlayPreferencesFlow.collectAsState(
        initial = OverlayPreferences()
    )
    var weatherSnapshot by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var weatherLocationName by remember { mutableStateOf("Khu vực đã lưu") }
    var weatherRefreshKey by remember { mutableStateOf(0) }

    // Dynamic Screen Width in Pixels
    val screenWidthPx = remember(context) { context.resources.displayMetrics.widthPixels }

    val startPetOverlay = {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
        scope.launch { preferencesRepository.updateOverlayEnabled(true) }
        Toast.makeText(context, "Đã bật Pet thành công! 🐱", Toast.LENGTH_SHORT).show()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            saveApproximateWeatherLocation(context, preferencesRepository, scope)
        } else {
            Toast.makeText(context, "Chưa được cấp quyền truy cập vị trí", Toast.LENGTH_SHORT).show()
        }
    }

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryState by remember { mutableStateOf(BatteryState()) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = context.registerReceiver(null, filter)
        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else 100
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            batteryState = BatteryState(percentage, isCharging)
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentCharacterState = CharacterStateMapper.map(batteryState)
    val weatherLatitude = preferences.weatherLatitudeE6 / 1_000_000.0
    val weatherLongitude = preferences.weatherLongitudeE6 / 1_000_000.0

    LaunchedEffect(weatherLatitude, weatherLongitude) {
        weatherLocationName = resolveApproximatePlaceName(
            context = context,
            latitude = weatherLatitude,
            longitude = weatherLongitude
        )
    }

    LaunchedEffect(preferences.weatherEnabled, weatherLatitude, weatherLongitude, weatherRefreshKey) {
        if (!preferences.weatherEnabled) {
            weatherSnapshot = null
            weatherError = null
            return@LaunchedEffect
        }

        weatherError = null
        runCatching {
            weatherRepository.getCurrentWeather(
                latitude = weatherLatitude,
                longitude = weatherLongitude,
                forceRefresh = weatherRefreshKey > 0
            )
        }.onSuccess {
            weatherSnapshot = it
        }.onFailure {
            weatherError = "Chưa lấy được thời tiết"
        }
    }

    // Modal States for Testing
    var selectedPetState by remember { mutableStateOf(PetBehaviorState.IDLE) }
    var showPetStateModal by remember { mutableStateOf(false) }

    // Weather Test Selection
    val weatherTestOptions = remember {
        listOf(
            "Quang đãng" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_WEATHER, OverlayService.EXTRA_WEATHER, WeatherCondition.CLEAR.name) },
            "Nhiều mây" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_WEATHER, OverlayService.EXTRA_WEATHER, WeatherCondition.CLOUDY.name) },
            "Mưa phùn" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_WEATHER, OverlayService.EXTRA_WEATHER, WeatherCondition.RAIN.name) },
            "Mưa lớn" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_WEATHER, OverlayService.EXTRA_WEATHER, WeatherCondition.HEAVY_RAIN.name) },
            "Gió mạnh" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_WEATHER, OverlayService.EXTRA_WEATHER, WeatherCondition.WIND.name) },
            "Dông bão" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_WEATHER, OverlayService.EXTRA_WEATHER, WeatherCondition.STORM.name) },
            "Tuyết rơi" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_WEATHER, OverlayService.EXTRA_WEATHER, WeatherCondition.SNOW.name) }
        )
    }
    var selectedWeatherIndex by remember { mutableStateOf(0) }
    var showWeatherModal by remember { mutableStateOf(false) }

    // Event Test Selection
    val eventTestOptions = remember {
        listOf(
            "Lễ Quốc Khánh 2/9 (Mèo vẫy cờ 🇻🇳)" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_EVENT, OverlayService.EXTRA_EVENT, EventEnvironment.NATIONAL_DAY.name) },
            "Sự kiện Bắt Bướm 🦋" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_BUTTERFLY) },
            "Sự kiện Sét Đánh Trúng Pet ⚡" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_LIGHTNING) },
            "Lễ Thất Tịch (Ô Thước & Pháo hoa)" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_EVENT, OverlayService.EXTRA_EVENT, EventEnvironment.QIXI.name) },
            "Mặc định (Tắt sự kiện)" to { sendOverlayCommand(context, OverlayService.ACTION_TEST_EVENT, OverlayService.EXTRA_EVENT, EventEnvironment.DEFAULT.name) }
        )
    }
    var selectedEventIndex by remember { mutableStateOf(0) }
    var showEventModal by remember { mutableStateOf(false) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.status_cat_launcher_art),
                    contentDescription = "Logo Pet",
                    modifier = Modifier.size(58.dp)
                )
                Column {
                    Text(
                        text = "Status Pet",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Pet Theo Dõi Pin & Sự Kiện",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Battery & Pet Mood Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Dung Lượng Pin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (batteryState.isCharging) "⚡ Đang sạc pin!" else "🔋 Đang dùng pin",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "${currentCharacterState.defaultEmoji} ${batteryState.percentage}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            batteryState.isCharging -> "Tâm trạng Pet: ⚡ Tràn đầy năng lượng & uống sữa!"
                            batteryState.percentage >= 80 -> "Tâm trạng Pet: 🏃‍♂️ Năng lượng dồi dào (chạy nhảy & đi dạo)"
                            batteryState.percentage >= 40 -> "Tâm trạng Pet: 🐱 Năng lượng bình thường (đi bộ & nghỉ ngơi)"
                            batteryState.percentage >= 15 -> "Tâm trạng Pet: 😿 Năng lượng thấp (mệt mỏi)"
                            else -> "Tâm trạng Pet: 💤 Cạn kiệt pin (ngủ say Zzz)"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permission Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasOverlayPermission)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (hasOverlayPermission)
                            "✓ Đã cấp quyền hiển thị trên ứng dụng khác"
                        else
                            "✕ Cần cấp quyền hiển thị trên ứng dụng khác",
                        fontWeight = FontWeight.Bold,
                        color = if (hasOverlayPermission)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )

                    if (!hasOverlayPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { requestOverlayPermission(context) }) {
                            Text("Cấp Quyền Hiển Thị")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Service Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (!hasOverlayPermission) {
                            Toast.makeText(context, "Vui lòng cấp quyền hiển thị trước", Toast.LENGTH_SHORT).show()
                            requestOverlayPermission(context)
                        } else {
                            startPetOverlay()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = hasOverlayPermission
                ) {
                    Text("Bật Pet")
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_STOP
                        }
                        context.startService(intent)
                        scope.launch { preferencesRepository.updateOverlayEnabled(false) }
                        Toast.makeText(context, "Đã tắt Pet", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tắt Pet")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Virtual Pet Mode Switch
            Text(
                text = "Cấu Hình Pet AI",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bật Chế Độ Di Chuyển Ngẫu Nhiên AI",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = preferences.isPetMode,
                    onCheckedChange = { scope.launch { preferencesRepository.updateIsPetMode(it) } }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Weather Section
            Text(
                text = "Môi Trường Thời Tiết",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dùng thời tiết thực tế", fontSize = 15.sp)
                Switch(
                    checked = preferences.weatherEnabled,
                    onCheckedChange = {
                        scope.launch { preferencesRepository.updateWeatherEnabled(it) }
                    }
                )
            }

            WeatherSummary(
                enabled = preferences.weatherEnabled,
                locationName = weatherLocationName,
                snapshot = weatherSnapshot,
                errorMessage = weatherError
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            saveApproximateWeatherLocation(context, preferencesRepository, scope)
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lấy vị trí", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        weatherRefreshKey += 1
                        sendOverlayCommand(context, OverlayService.ACTION_REFRESH_WEATHER)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cập nhật thời tiết", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Event Mode Configuration
            Text(
                text = "Môi Trường Sự Kiện",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            EventMode.values().toList().chunked(2).forEach { rowModes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowModes.forEach { mode ->
                        if (preferences.eventMode == mode) {
                            Button(
                                onClick = {
                                    scope.launch { preferencesRepository.updateEventMode(mode) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(mode.displayName(), fontSize = 13.sp, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    scope.launch { preferencesRepository.updateEventMode(mode) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(mode.displayName(), fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // TEST SECTION: DROPDOWN + SUBMIT BUTTONS
            // ==========================================
            // TEST SECTION: MODAL BOTTOM SHEET + SUBMIT BUTTONS
            // ==========================================
            Text(
                text = "Khu Vực Test Chức Năng",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Test Trạng Thái Pet Modal Selector
            Text(
                text = "Test Trạng Thái Pet",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            SelectionField(
                label = "Trạng thái đang chọn",
                value = "${selectedPetState.emoji()}  ${selectedPetState.displayName()}",
                onClick = { showPetStateModal = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    sendOverlayCommand(
                        context,
                        OverlayService.ACTION_TEST_STATE,
                        OverlayService.EXTRA_STATE,
                        selectedPetState.name
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Trạng Thái")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Test Thời Tiết & Sét Modal Selector
            Text(
                text = "Test Thời Tiết & Giông Sét",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            SelectionField(
                label = "Thời tiết đang chọn",
                value = weatherTestOptions[selectedWeatherIndex].first,
                onClick = { showWeatherModal = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    weatherTestOptions[selectedWeatherIndex].second.invoke()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Thời Tiết")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Test Sự Kiện Modal Selector
            Text(
                text = "Test Sự Kiện Đặc Biệt",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            SelectionField(
                label = "Sự kiện đang chọn",
                value = eventTestOptions[selectedEventIndex].first,
                onClick = { showEventModal = true }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    eventTestOptions[selectedEventIndex].second.invoke()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Sự Kiện")
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // PLAYGROUND RANGE & SETTINGS SLIDERS
            // ==========================================
            val density = context.resources.displayMetrics.density
            val petWidthPx = (
                preferences.overlaySize * density * 2.6f + 24 * density
            ).roundToInt()
            val maxAllowedMinX = (preferences.maxX - petWidthPx).coerceAtLeast(0)
            val minAllowedMaxX = (preferences.minX + petWidthPx).coerceAtMost(screenWidthPx)

            Text(
                text = "Phạm Vi Sân Chơi Status Bar (${screenWidthPx}px)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Min X Slider with clamping
            Text(
                text = "Góc Trái Sân Chơi (Min X): ${preferences.minX} px",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = preferences.minX.toFloat().coerceIn(0f, maxAllowedMinX.toFloat()),
                onValueChange = {
                    val minX = it.toInt().coerceAtMost(maxAllowedMinX)
                    scope.launch { preferencesRepository.updateMinX(minX) }
                },
                valueRange = 0f..screenWidthPx.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Max X Slider with clamping
            Text(
                text = "Góc Phải Sân Chơi (Max X): ${preferences.maxX} px",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = preferences.maxX.toFloat().coerceIn(minAllowedMaxX.toFloat(), screenWidthPx.toFloat()),
                onValueChange = {
                    val maxX = it.toInt().coerceAtLeast(minAllowedMaxX)
                    scope.launch { preferencesRepository.updateMaxX(maxX) }
                },
                valueRange = 0f..screenWidthPx.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Độ Cao Status Bar (Y Offset): ${preferences.overlayY} px",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = preferences.overlayY.toFloat(),
                onValueChange = { scope.launch { preferencesRepository.updateOverlayY(it.toInt()) } },
                valueRange = -40f..100f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Kích Thước Pet: ${preferences.overlaySize} sp",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = preferences.overlaySize.toFloat(),
                onValueChange = { scope.launch { preferencesRepository.updateOverlaySize(it.toInt()) } },
                valueRange = 8f..42f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Modal Bottom Sheets for Testing
        if (showPetStateModal) {
            SelectionModalBottomSheet(
                title = "Chọn Trạng Thái Pet Test",
                items = PetBehaviorState.values().toList(),
                selectedItem = selectedPetState,
                itemLabel = { it.displayName() },
                itemIcon = { it.emoji() },
                onItemSelected = { selectedPetState = it },
                onDismissRequest = { showPetStateModal = false }
            )
        }

        if (showWeatherModal) {
            SelectionModalBottomSheet(
                title = "Chọn Thời Tiết & Giông Sét Test",
                items = weatherTestOptions.indices.toList(),
                selectedItem = selectedWeatherIndex,
                itemLabel = { weatherTestOptions[it].first },
                itemIcon = null,
                onItemSelected = { selectedWeatherIndex = it },
                onDismissRequest = { showWeatherModal = false }
            )
        }

        if (showEventModal) {
            SelectionModalBottomSheet(
                title = "Chọn Sự Kiện Đặc Biệt Test",
                items = eventTestOptions.indices.toList(),
                selectedItem = selectedEventIndex,
                itemLabel = { eventTestOptions[it].first },
                itemIcon = null,
                onItemSelected = { selectedEventIndex = it },
                onDismissRequest = { showEventModal = false }
            )
        }
    }
}

@Composable
private fun SelectionField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionModalBottomSheet(
    title: String,
    items: List<T>,
    selectedItem: T,
    itemLabel: (T) -> String,
    itemIcon: ((T) -> String)?,
    onItemSelected: (T) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                items(items) { item ->
                    val isSelected = item == selectedItem
                    Surface(
                        onClick = {
                            onItemSelected(item)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (itemIcon != null) {
                                Text(itemIcon(item), fontSize = 20.sp)
                            }
                            Text(
                                text = itemLabel(item),
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSummary(
    enabled: Boolean,
    locationName: String,
    snapshot: WeatherSnapshot?,
    errorMessage: String?
) {
    val summary = when {
        !enabled -> "Đang tắt"
        snapshot != null -> "${snapshot.condition.vietnameseName()} • ${snapshot.temperatureC.roundToInt()}°C"
        errorMessage != null -> errorMessage
        else -> "Đang cập nhật..."
    }
    val details = snapshot?.let {
        "Mưa ${formatOneDecimal(it.precipitationMm)} mm • Gió ${it.windSpeedKmh.roundToInt()} km/h • ${formatWeatherUpdateTime(it.fetchedAtMs)}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "Địa điểm: $locationName",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Thời tiết: $summary",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (details != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}

private fun sendOverlayCommand(
    context: Context,
    action: String,
    extraKey: String? = null,
    extraValue: String? = null
) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Cần cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(context, OverlayService::class.java).apply {
        this.action = action
        if (extraKey != null && extraValue != null) {
            putExtra(extraKey, extraValue)
        }
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun saveApproximateWeatherLocation(
    context: Context,
    preferencesRepository: PreferencesRepository,
    scope: CoroutineScope
) {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = locationManager.getProviders(true)
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }

    if (location == null) {
        Toast.makeText(context, "Không tìm thấy tọa độ vị trí gần đây", Toast.LENGTH_SHORT).show()
        return
    }

    scope.launch {
        preferencesRepository.updateWeatherLocation(location.latitude, location.longitude)
    }
    Toast.makeText(context, "Đã cập nhật vị trí thời tiết thành công", Toast.LENGTH_SHORT).show()
}

private suspend fun resolveApproximatePlaceName(
    context: Context,
    latitude: Double,
    longitude: Double
): String = withContext(Dispatchers.IO) {
    runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        val address = Geocoder(context, Locale("vi", "VN"))
            .getFirstAddress(latitude, longitude)

        listOfNotNull(
            address?.subLocality,
            address?.locality ?: address?.subAdminArea,
            address?.adminArea
        )
            .distinct()
            .joinToString(", ")
            .ifBlank { null }
    }.getOrNull() ?: "Khu vực đã lưu"
}

private suspend fun Geocoder.getFirstAddress(latitude: Double, longitude: Double) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            getFromLocation(latitude, longitude, 1) { addresses ->
                if (continuation.isActive) {
                    continuation.resume(addresses.firstOrNull())
                }
            }
        }
    } else {
        @Suppress("DEPRECATION")
        getFromLocation(latitude, longitude, 1)?.firstOrNull()
    }

private fun WeatherCondition.vietnameseName(): String = when (this) {
    WeatherCondition.CLEAR -> "Quang đãng"
    WeatherCondition.CLOUDY -> "Nhiều mây"
    WeatherCondition.RAIN -> "Mưa phùn"
    WeatherCondition.HEAVY_RAIN -> "Mưa lớn"
    WeatherCondition.WIND -> "Gió mạnh"
    WeatherCondition.STORM -> "Dông bão"
    WeatherCondition.SNOW -> "Tuyết rơi"
}

private fun EventMode.displayName(): String = when (this) {
    EventMode.AUTO -> "Tự động"
    EventMode.DEFAULT -> "Mặc định (Tắt)"
    EventMode.QIXI -> "Lễ Thất Tịch"
    EventMode.NATIONAL_DAY -> "Lễ Quốc Khánh 2/9"
}

private fun PetBehaviorState.displayName(): String = when (this) {
    PetBehaviorState.IDLE -> "Đứng quan sát"
    PetBehaviorState.WALK -> "Đi bộ"
    PetBehaviorState.RUN -> "Chạy nước rút"
    PetBehaviorState.SIT -> "Ngồi nghỉ"
    PetBehaviorState.SIT_DOWN -> "Ngồi xuống"
    PetBehaviorState.LOOK_FRONT -> "Nhìn thẳng phía trước"
    PetBehaviorState.SLEEP -> "Ngủ say"
    PetBehaviorState.CHARGING_HAPPY -> "Vui vẻ khi sạc pin"
    PetBehaviorState.DRINK_START -> "Chuẩn bị uống sữa"
    PetBehaviorState.DRINK_MILK -> "Uống bát sữa"
    PetBehaviorState.LIGHTNING_HIT -> "Bị sét giáng trúng ⚡"
    PetBehaviorState.SHOCKED -> "Cháy đen phục hồi"
    PetBehaviorState.POKE_JUMP -> "Giật mình khi chạm"
    PetBehaviorState.ANGRY_LOOK -> "Nhìn giận dỗi"
    PetBehaviorState.POUNCE -> "Phi thân vồ bướm"
    PetBehaviorState.CONFUSED -> "Ngơ ngác gãi tai"
    PetBehaviorState.FLAG_WAVE -> "Vẫy cờ Quốc Khánh 🇻🇳"
    PetBehaviorState.FLAG_WALK -> "Diễu hành cờ 2/9 🇻🇳"
}

private fun PetBehaviorState.emoji(): String = when (this) {
    PetBehaviorState.IDLE -> "👀"
    PetBehaviorState.WALK -> "🐾"
    PetBehaviorState.RUN -> "🏃"
    PetBehaviorState.SIT -> "🐱"
    PetBehaviorState.SIT_DOWN -> "🐾"
    PetBehaviorState.LOOK_FRONT -> "✨"
    PetBehaviorState.SLEEP -> "💤"
    PetBehaviorState.CHARGING_HAPPY -> "⚡"
    PetBehaviorState.DRINK_START -> "🥛"
    PetBehaviorState.DRINK_MILK -> "🥛"
    PetBehaviorState.LIGHTNING_HIT -> "⚡"
    PetBehaviorState.SHOCKED -> "💥"
    PetBehaviorState.POKE_JUMP -> "🗯️"
    PetBehaviorState.ANGRY_LOOK -> "😾"
    PetBehaviorState.POUNCE -> "🦋"
    PetBehaviorState.CONFUSED -> "❓"
    PetBehaviorState.FLAG_WAVE -> "🇻🇳"
    PetBehaviorState.FLAG_WALK -> "🇻🇳"
}

private fun formatOneDecimal(value: Double): String =
    String.format(Locale.US, "%.1f", value)

private fun formatWeatherUpdateTime(timestampMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale("vi", "VN"))
    return "Cập nhật lúc ${formatter.format(Date(timestampMs))}"
}
