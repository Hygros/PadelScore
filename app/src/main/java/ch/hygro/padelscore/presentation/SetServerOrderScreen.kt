package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.PlayerSlot
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.ColorPrimary

@Composable
fun SetServerOrderScreen(
    firstServingTeam: Team,
    firstServerForUs: PlayerSlot,
    firstServerForOpponent: PlayerSlot,
    onFirstServerForUsChanged: (PlayerSlot) -> Unit,
    onFirstServerForOpponentChanged: (PlayerSlot) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

        ServerSelectionRow(
            label = stringResource(R.string.our_team),
            selectedLabel = playerLabel(firstServerForUs),
            onClick = {
                onFirstServerForUsChanged(firstServerForUs.teammate())
            }
        )

        ServerSelectionRow(
            label = stringResource(R.string.opponent_team),
            selectedLabel = playerLabel(firstServerForOpponent),
            onClick = {
                onFirstServerForOpponentChanged(firstServerForOpponent.teammate())
            }
        )

        Button(
            modifier = Modifier.fillMaxWidth().height(42.dp),
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

@Composable
fun ServerCorrectionScreen(
    servingTeam: Team,
    servingPlayer: PlayerSlot,
    onPlayerSelected: (PlayerSlot) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
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

        val firstPlayer = PlayerSlot.firstFor(servingTeam)
        val secondPlayer = firstPlayer.teammate()

        ServerChoice(
            label = playerLabel(firstPlayer),
            selected = servingPlayer == firstPlayer,
            onClick = { onPlayerSelected(firstPlayer) }
        )
        ServerChoice(
            label = playerLabel(secondPlayer),
            selected = servingPlayer == secondPlayer,
            onClick = { onPlayerSelected(secondPlayer) }
        )

        Button(
            modifier = Modifier.fillMaxWidth().height(38.dp),
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
            color = Color.LightGray,
            maxFontSize = 9.sp,
            minFontSize = 7.sp,
            maxLines = 1
        )
        AutoSizeText(
            text = selectedLabel,
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
        modifier = Modifier.fillMaxWidth().height(44.dp),
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