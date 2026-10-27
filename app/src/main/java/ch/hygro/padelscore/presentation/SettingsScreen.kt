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
import androidx.compose.ui.res.stringResource
import ch.hygro.padelscore.R
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
import ch.hygro.padelscore.presentation.theme.ColorSecondary
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
    val defaultCompanionStatus = stringResource(R.string.companion_status_optional)
    val checkingStatus = stringResource(R.string.companion_status_checking)
    val openingStoreStatus = stringResource(R.string.companion_status_opening_store)
    var companionStatus by remember(defaultCompanionStatus) { androidx.compose.runtime.mutableStateOf(defaultCompanionStatus) }
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
                        text = stringResource(R.string.settings),
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
                        label = { Text(stringResource(R.string.swipe_gestures), fontSize = 12.sp) },
                        secondaryLabel = { Text(stringResource(R.string.swipe_gestures_desc), fontSize = 10.sp, color = Color.Gray, maxLines = 3) },
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
                        label = { Text(stringResource(R.string.health_tracking), fontSize = 12.sp) },
                        secondaryLabel = { Text(stringResource(R.string.health_tracking_desc), fontSize = 10.sp, color = Color.Gray, maxLines = 3) },
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
                        label = { Text(stringResource(R.string.health_sync), fontSize = 12.sp) },
                        secondaryLabel = { Text(stringResource(R.string.health_sync_desc), fontSize = 10.sp, color = Color.Gray, maxLines = 3) },
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
                        label = { Text(stringResource(R.string.match_quiet_mode), fontSize = 12.sp) },
                        secondaryLabel = { Text(stringResource(R.string.match_quiet_mode_desc), fontSize = 10.sp, color = Color.Gray, maxLines = 3) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                androidx.wear.compose.material3.Button(
                    onClick = {
                        if (!companionBusy) {
                            companionBusy = true
                            companionStatus = checkingStatus
                            syncScope.launch {
                                try {
                                    companionStatus = ch.hygro.padelscore.health.CompanionSetup.status(context.applicationContext)
                                } finally {
                                    companionBusy = false
                                }
                            }
                        }
                    }, enabled = !companionBusy, modifier = Modifier.fillMaxWidth(0.9f)
                ) { Text(stringResource(R.string.check_phone_setup), fontSize = 12.sp) }
            }
            item { Text(companionStatus, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(0.85f)) }
            item {
                androidx.wear.compose.material3.Button(
                    onClick = {
                        if (!companionBusy) {
                            companionBusy = true
                            companionStatus = openingStoreStatus
                            syncScope.launch {
                                try {
                                    companionStatus = ch.hygro.padelscore.health.CompanionSetup.openStore(context.applicationContext)
                                } finally {
                                    companionBusy = false
                                }
                            }
                        }
                    }, enabled = !companionBusy, modifier = Modifier.fillMaxWidth(0.9f)
                ) { Text(stringResource(R.string.install_phone_companion), fontSize = 12.sp) }
            }
            item {
                Text(stringResource(R.string.companion_sync_info),
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
                        Text(stringResource(R.string.configure_quiet_mode), fontSize = 12.sp)
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
                            containerColor = ColorSecondary,
                            contentColor = Color.White
                        ),
                        onClick = onBack
                    ) {
                        Text(
                            text = stringResource(R.string.back),
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
