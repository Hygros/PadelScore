package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.PlayerSlot
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.ColorPrimary
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

@Composable
fun SetServerOrderScreen(
    firstServingTeam: Team,
    firstServerForUs: PlayerSlot,
    firstServerForOpponent: PlayerSlot,
    onFirstServerForUsChanged: (PlayerSlot) -> Unit,
    onFirstServerForOpponentChanged: (PlayerSlot) -> Unit,
    onConfirm: () -> Unit
) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound

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
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec),
                    transformation = with(itemScope) { SurfaceTransformation(transformationSpec) }
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.confirm_server_order),
                        color = Color.White,
                        maxFontSize = 13.sp,
                        minFontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }

            item {
                val itemScope = this
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.88f else 1f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AutoSizeText(
                        text = if (firstServingTeam == Team.US) {
                            stringResource(R.string.our_team)
                        } else {
                            stringResource(R.string.opponent_team)
                        },
                        color = Color(0xFFFFD54F),
                        maxFontSize = 10.sp,
                        minFontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            item {
                val itemScope = this
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.88f else 1f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.server_order_help),
                        color = Color.LightGray,
                        maxFontSize = 9.sp,
                        minFontSize = 7.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.88f else 0.94f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    ServerSelectionRow(
                        label = stringResource(R.string.our_team),
                        selectedLabel = playerLabel(firstServerForUs),
                        onClick = {
                            onFirstServerForUsChanged(firstServerForUs.teammate())
                        }
                    )
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.88f else 0.94f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    ServerSelectionRow(
                        label = stringResource(R.string.opponent_team),
                        selectedLabel = playerLabel(firstServerForOpponent),
                        onClick = {
                            onFirstServerForOpponentChanged(firstServerForOpponent.teammate())
                        }
                    )
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.84f else 0.88f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        contentPadding = CompactButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimary,
                            contentColor = Color.White
                        ),
                        onClick = onConfirm
                    ) {
                        AutoSizeText(
                            text = stringResource(R.string.confirm),
                            color = Color.White,
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerCorrectionScreen(
    servingTeam: Team,
    servingPlayer: PlayerSlot,
    onPlayerSelected: (PlayerSlot) -> Unit,
    onCancel: () -> Unit
) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound

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
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec),
                    transformation = with(itemScope) { SurfaceTransformation(transformationSpec) }
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.correct_server),
                        color = Color.White,
                        maxFontSize = 14.sp,
                        minFontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }

            item {
                val itemScope = this
                Column(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.88f else 1f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AutoSizeText(
                        text = stringResource(R.string.correct_server_hint),
                        color = Color.LightGray,
                        maxFontSize = 9.5.sp,
                        minFontSize = 7.5.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }

            val otherPlayer = servingPlayer.teammate()

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.88f else 0.94f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(vertical = 2.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentPadding = CompactButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimary,
                            contentColor = Color.White
                        ),
                        onClick = { onPlayerSelected(otherPlayer) }
                    ) {
                        AutoSizeText(
                            text = playerLabel(otherPlayer),
                            color = Color.White,
                            maxFontSize = 11.sp,
                            minFontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isRound) 0.84f else 0.88f)
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        contentPadding = CompactButtonContentPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray,
                            contentColor = Color.White
                        ),
                        onClick = onCancel
                    ) {
                        AutoSizeText(
                            text = stringResource(R.string.cancel),
                            color = Color.White,
                            maxFontSize = 10.sp,
                            minFontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerSelectionRow(
    label: String,
    selectedLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color(0xFF292929), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AutoSizeText(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            fillMaxWidth = false,
            color = Color.LightGray,
            maxFontSize = 9.5.sp,
            minFontSize = 7.5.sp,
            maxLines = 1
        )
        AutoSizeText(
            text = selectedLabel,
            fillMaxWidth = false,
            color = Color(0xFFFFD54F),
            maxFontSize = 11.sp,
            minFontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ServerChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentPadding = CompactButtonContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ColorPrimary else Color(0xFF292929),
            contentColor = Color.White
        ),
        onClick = onClick
    ) {
        AutoSizeText(
            text = label,
            color = Color.White,
            maxFontSize = 11.sp,
            minFontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun playerLabel(player: PlayerSlot): String {
    return when (player) {
        PlayerSlot.US_1 -> stringResource(R.string.player_me)
        PlayerSlot.US_2 -> stringResource(R.string.player_partner)
        PlayerSlot.OPPONENT_1 -> stringResource(R.string.player_opponent_1)
        PlayerSlot.OPPONENT_2 -> stringResource(R.string.player_opponent_2)
    }
}

@WearPreviewDevices
@Composable
private fun SetServerOrderScreenPreview() {
    PadelScoreTheme {
        SetServerOrderScreen(
            firstServingTeam = Team.OPPONENT,
            firstServerForUs = PlayerSlot.US_1,
            firstServerForOpponent = PlayerSlot.OPPONENT_1,
            onFirstServerForUsChanged = {},
            onFirstServerForOpponentChanged = {},
            onConfirm = {}
        )
    }
}

@WearPreviewDevices
@Composable
private fun ServerCorrectionScreenPreview() {
    PadelScoreTheme {
        ServerCorrectionScreen(
            servingTeam = Team.US,
            servingPlayer = PlayerSlot.US_1,
            onPlayerSelected = {},
            onCancel = {}
        )
    }
}
