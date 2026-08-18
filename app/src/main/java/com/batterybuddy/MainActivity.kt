package com.batterybuddy

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.batterybuddy.battery.BatteryState
import com.batterybuddy.data.OverlayPreferences
import com.batterybuddy.data.PreferencesRepository
import com.batterybuddy.overlay.CharacterStateMapper
import com.batterybuddy.service.OverlayService
import com.batterybuddy.ui.theme.BatteryBuddyTheme
import kotlinx.coroutines.launch

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

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = remember { PreferencesRepository(context) }
    val preferences by preferencesRepository.overlayPreferencesFlow.collectAsState(
        initial = OverlayPreferences()
    )

    // Dynamic Screen Width in Pixels
    val screenWidthPx = remember(context) { context.resources.displayMetrics.widthPixels }

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

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🐱 BatteryBuddy Virtual Pet",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Status Bar Autonomous Pet Companion",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                            Text(text = "Battery Stamina", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (batteryState.isCharging) "⚡ Recharging!" else "🔋 Discharging",
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
                            batteryState.isCharging -> "Pet mood: ⚡ Full of excitement & energy!"
                            batteryState.percentage >= 80 -> "Pet mood: 🏃‍♂️ High stamina (walking & playing)"
                            batteryState.percentage >= 40 -> "Pet mood: 🐱 Normal energy (walking & resting)"
                            batteryState.percentage >= 15 -> "Pet mood: 😿 Low energy (tired & resting)"
                            else -> "Pet mood: 💤 Exhausted (sleeping Zzz)"
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
                            "✓ Overlay Permission Granted"
                        else
                            "✕ Overlay Permission Required",
                        fontWeight = FontWeight.Bold,
                        color = if (hasOverlayPermission)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )

                    if (!hasOverlayPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { requestOverlayPermission(context) }) {
                            Text("Grant Permission")
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
                            Toast.makeText(context, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
                            requestOverlayPermission(context)
                        } else {
                            val intent = Intent(context, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_START
                            }
                            ContextCompat.startForegroundService(context, intent)
                            scope.launch { preferencesRepository.updateOverlayEnabled(true) }
                            Toast.makeText(context, "Virtual Pet Activated! 🐱", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = hasOverlayPermission
                ) {
                    Text("Enable Pet")
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, OverlayService::class.java).apply {
                            action = OverlayService.ACTION_STOP
                        }
                        context.startService(intent)
                        scope.launch { preferencesRepository.updateOverlayEnabled(false) }
                        Toast.makeText(context, "Pet Sleeping", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disable Pet")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Virtual Pet Mode Switch
            Text(
                text = "Cấu Hình Thú Ảo (Virtual Pet AI)",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Show Battery Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hiện % Pin Cạnh Thú Ảo",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = preferences.showPercentage,
                    onCheckedChange = { scope.launch { preferencesRepository.updateShowPercentage(it) } }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Clamped Playground Sliders
            val maxAllowedMinX = (preferences.maxX - 40).coerceAtLeast(0)
            val minAllowedMaxX = (preferences.minX + 40).coerceAtMost(screenWidthPx)

            Text(
                text = "Phạm Vi Sân Chơi Status Bar (Width: ${screenWidthPx}px)",
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
                onValueChange = { scope.launch { preferencesRepository.updateMinX(it.toInt()) } },
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
                onValueChange = { scope.launch { preferencesRepository.updateMaxX(it.toInt()) } },
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
                text = "Kích Thước Thú Ảo: ${preferences.overlaySize} sp",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = preferences.overlaySize.toFloat(),
                onValueChange = { scope.launch { preferencesRepository.updateOverlaySize(it.toInt()) } },
                valueRange = 14f..42f,
                modifier = Modifier.fillMaxWidth()
            )
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
