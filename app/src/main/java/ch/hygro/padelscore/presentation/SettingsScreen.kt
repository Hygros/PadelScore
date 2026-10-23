package ch.hygro.padelscore.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ch.hygro.padelscore.health.WorkoutDataSender
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import ch.hygro.padelscore.storage.SettingsRepository

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val swipeGesturesEnabled by repository.swipeGesturesEnabled.collectAsState()
    val healthTrackingEnabled by repository.healthTrackingEnabled.collectAsState()
    val matchQuietModeEnabled by repository.matchQuietModeEnabled.collectAsState()
    val healthSyncEnabled by repository.healthSyncEnabled.collectAsState()
    val syncScope = rememberCoroutineScope()
    var companionStatus by remember { androidx.compose.runtime.mutableStateOf("Phone companion is optional.") }
    var companionBusy by remember { androidx.compose.runtime.mutableStateOf(false) }

    val bodySensorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                repository.setHealthTrackingEnabled(false)
            }
        }
    )

    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = columnState) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 1.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SETTINGS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    SwitchButton(
                        checked = swipeGesturesEnabled,
                        onCheckedChange = { repository.setSwipeGesturesEnabled(it) },
                        label = { Text("Swipe Gestures", fontSize = 12.sp) },
                        secondaryLabel = { Text("Swipe up/down to score", fontSize = 10.sp, color = Color.Gray, maxLines = 3) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    SwitchButton(
                        checked = healthTrackingEnabled,
                        onCheckedChange = { enabled ->
                            repository.setHealthTrackingEnabled(enabled)
                            if (!enabled) ch.hygro.padelscore.health.FitnessTrackingService.stop(context)
                            if (enabled) {
                                val requiredPermissions = listOf(
                                    if (android.os.Build.VERSION.SDK_INT >= 36) {
                                        "android.permission.health.READ_HEART_RATE"
                                    } else {
                                        Manifest.permission.BODY_SENSORS
                                    },
                                    Manifest.permission.ACTIVITY_RECOGNITION
                                )
                                val missingPermissions = requiredPermissions.filter {
                                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                }
                                if (missingPermissions.isNotEmpty()) {
                                    bodySensorLauncher.launch(missingPermissions.toTypedArray())
                                }
                            }
                        },
                        label = { Text("Health Tracking", fontSize = 12.sp) },
                        secondaryLabel = { Text("Record match heart rate and steps locally", fontSize = 10.sp, color = Color.Gray, maxLines = 3) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                val itemScope = this
                Box(
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    SwitchButton(
                        checked = healthSyncEnabled,
                        onCheckedChange = { enabled ->
                            repository.setHealthSyncEnabled(enabled)
                            if (enabled) {
                                syncScope.launch(Dispatchers.IO) {
                                    WorkoutDataSender(context.applicationContext).publishAll()
                                }
                            } else {
                                syncScope.launch(Dispatchers.IO) {
                                    WorkoutDataSender(context.applicationContext).clearPending()
                                }
                            }
                        },
                        label = { Text("Health Sync", fontSize = 12.sp) },
                        secondaryLabel = { Text("Send workouts to Health Connect via phone", fontSize = 10.sp, color = Color.Gray, maxLines = 3) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                val itemScope = this
                Box(
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    SwitchButton(
                        checked = matchQuietModeEnabled,
                        onCheckedChange = { enabled ->
                            repository.setMatchQuietModeEnabled(enabled)
                            (context as? MainActivity)?.refreshMatchQuietModeSettings()
                        },
                        label = { Text("Match Quiet Mode", fontSize = 12.sp) },
                        secondaryLabel = { Text("Silence notifications during matches", fontSize = 10.sp, color = Color.Gray, maxLines = 3) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                androidx.wear.compose.material3.Button(
                    onClick = {
                        if (!companionBusy) {
                            companionBusy = true
                            companionStatus = "Checking phone connection..."
                            syncScope.launch {
                                try {
                                    companionStatus = ch.hygro.padelscore.health.CompanionSetup.status(context.applicationContext)
                                } finally {
                                    companionBusy = false
                                }
                            }
                        }
                    }, enabled = !companionBusy, modifier = Modifier.fillMaxWidth(0.9f)
                ) { Text("Check phone setup", fontSize = 12.sp) }
            }
            item { Text(companionStatus, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(0.85f)) }
            item {
                androidx.wear.compose.material3.Button(
                    onClick = {
                        if (!companionBusy) {
                            companionBusy = true
                            companionStatus = "Opening store on phone..."
                            syncScope.launch {
                                try {
                                    companionStatus = ch.hygro.padelscore.health.CompanionSetup.openStore(context.applicationContext)
                                } finally {
                                    companionBusy = false
                                }
                            }
                        }
                    }, enabled = !companionBusy, modifier = Modifier.fillMaxWidth(0.9f)
                ) { Text("Install phone companion", fontSize = 12.sp) }
            }
            item {
                Text("Sync requires consent on both devices. Steps stay on the watch. " +
                    "Explicit deletions are sent even with sync OFF; Samsung Health copies may need separate deletion.",
                    fontSize = 10.sp, modifier = Modifier.fillMaxWidth(0.85f))
            }
            item {
                val itemScope = this
                Box(
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    androidx.wear.compose.material3.Button(
                        onClick = { (context as? MainActivity)?.configureMatchQuietMode() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Configure Quiet Mode", fontSize = 12.sp)
                    }
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 8.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.wear.compose.material3.Button(
                        modifier = Modifier
                            .fillMaxWidth(if (androidx.compose.ui.platform.LocalConfiguration.current.isScreenRound) 0.82f else 0.88f)
                            .height(48.dp),
                        colors = androidx.wear.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A3A3A),
                            contentColor = Color.White
                        ),
                        onClick = onBack
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(ch.hygro.padelscore.R.string.back),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
