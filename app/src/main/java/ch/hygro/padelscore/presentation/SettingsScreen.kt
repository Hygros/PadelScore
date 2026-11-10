package ch.hygro.padelscore.presentation

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ch.hygro.padelscore.health.CompanionSetup
import ch.hygro.padelscore.health.FitnessTrackingService
import ch.hygro.padelscore.health.WorkoutDataSender
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ch.hygro.padelscore.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
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
    val wristGesturesEnabled by repository.wristGesturesEnabled.collectAsState()
    val ambientModeEnabled by repository.ambientModeEnabled.collectAsState()
    val healthTrackingEnabled by repository.healthTrackingEnabled.collectAsState()
    val matchQuietModeEnabled by repository.matchQuietModeEnabled.collectAsState()
    val healthSyncEnabled by repository.healthSyncEnabled.collectAsState()
    val historySyncEnabled by repository.matchHistorySyncEnabled.collectAsState()
    val syncScope = rememberCoroutineScope()
    val defaultCompanionStatus = stringResource(R.string.companion_status_optional)
    val checkingStatus = stringResource(R.string.companion_status_checking)
    val openingStoreStatus = stringResource(R.string.companion_status_opening_store)
    var companionStatus by remember(defaultCompanionStatus) { mutableStateOf(defaultCompanionStatus) }
    var companionBusy by remember { mutableStateOf(false) }

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
                    AutoSizeText(
                        text = stringResource(R.string.settings),
                        color = Color.White,
                        maxFontSize = 12.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
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
                        label = { SettingsLabel(stringResource(R.string.swipe_gestures)) },
                        secondaryLabel = { SettingsDescription(stringResource(R.string.swipe_gestures_desc)) },
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
                        checked = wristGesturesEnabled,
                        onCheckedChange = { repository.setWristGesturesEnabled(it) },
                        label = { SettingsLabel(stringResource(R.string.wrist_gestures)) },
                        secondaryLabel = { SettingsDescription(stringResource(R.string.wrist_gestures_desc)) },
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
                        checked = ambientModeEnabled,
                        onCheckedChange = { repository.setAmbientModeEnabled(it) },
                        label = { SettingsLabel(stringResource(R.string.ambient_mode)) },
                        secondaryLabel = { SettingsDescription(stringResource(R.string.ambient_mode_desc)) },
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
                            if (!enabled) FitnessTrackingService.stop(context)
                            if (enabled) {
                                val missingPermissions = missingFitnessPermissions(context)
                                if (missingPermissions.isNotEmpty()) {
                                    bodySensorLauncher.launch(missingPermissions.toTypedArray())
                                }
                            }
                        },
                        label = { SettingsLabel(stringResource(R.string.health_tracking)) },
                        secondaryLabel = { SettingsDescription(stringResource(R.string.health_tracking_desc)) },
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
                        label = { SettingsLabel(stringResource(R.string.health_sync)) },
                        secondaryLabel = { SettingsDescription(stringResource(R.string.health_sync_desc)) },
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
            checked = historySyncEnabled,
            onCheckedChange = { enabled ->
            repository.setMatchHistorySyncEnabled(enabled)
            ch.hygro.padelscore.health.MatchHistorySync.schedule(context)
            },
            label = { SettingsLabel(stringResource(R.string.match_history_sync)) },
            secondaryLabel = { SettingsDescription(stringResource(R.string.match_history_sync_desc)) },
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
                        label = { SettingsLabel(stringResource(R.string.match_quiet_mode)) },
                        secondaryLabel = { SettingsDescription(stringResource(R.string.match_quiet_mode_desc)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        if (!companionBusy) {
                            companionBusy = true
                            companionStatus = checkingStatus
                            syncScope.launch {
                                try {
                                    companionStatus = CompanionSetup.status(context.applicationContext)
                                } finally {
                                    companionBusy = false
                                }
                            }
                        }
                    },
                    enabled = !companionBusy,
                    contentPadding = CompactButtonContentPadding,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.check_phone_setup),
                        maxFontSize = 12.sp,
                        minFontSize = 9.sp,
                        maxLines = 2
                    )
                }
            }
            item {
                AutoSizeText(
                    text = companionStatus,
                    maxFontSize = 10.sp,
                    minFontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
            item {
                Button(
                    onClick = {
                        if (!companionBusy) {
                            companionBusy = true
                            companionStatus = openingStoreStatus
                            syncScope.launch {
                                try {
                                    companionStatus = CompanionSetup.openStore(context.applicationContext)
                                } finally {
                                    companionBusy = false
                                }
                            }
                        }
                    },
                    enabled = !companionBusy,
                    contentPadding = CompactButtonContentPadding,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.install_phone_companion),
                        maxFontSize = 12.sp,
                        minFontSize = 9.sp,
                        maxLines = 2
                    )
                }
            }
            item {
                AutoSizeText(
                    text = stringResource(R.string.companion_sync_info),
                    maxFontSize = 10.sp,
                    minFontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 20,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
            }
            item {
                val itemScope = this
                Box(
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    Button(
                        onClick = { (context as? MainActivity)?.configureMatchQuietMode() },
                        contentPadding = CompactButtonContentPadding,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AutoSizeText(
                            text = stringResource(R.string.configure_quiet_mode),
                            maxFontSize = 12.sp,
                            minFontSize = 9.sp,
                            maxLines = 2
                        )
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
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(if (LocalConfiguration.current.isScreenRound) 0.84f else 0.88f)
                            .height(48.dp),
                        contentPadding = CompactButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorSecondary,
                            contentColor = Color.White
                        ),
                        onClick = onBack
                    ) {
                        AutoSizeText(
                            text = stringResource(R.string.back),
                            maxFontSize = 12.sp,
                            minFontSize = 8.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsLabel(text: String) {
    AutoSizeText(
        text = text,
        maxFontSize = 12.sp,
        minFontSize = 9.5.sp,
        fontWeight = null,
        textAlign = TextAlign.Start,
        maxLines = 3
    )
}

@Composable
internal fun SettingsDescription(text: String) {
    AutoSizeText(
        text = text,
        maxFontSize = 10.sp,
        minFontSize = 9.sp,
        color = Color.Gray,
        fontWeight = null,
        textAlign = TextAlign.Start,
        maxLines = 12
    )
}
