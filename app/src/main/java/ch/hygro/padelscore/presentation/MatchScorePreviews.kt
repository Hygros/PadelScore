package ch.hygro.padelscore.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices
import ch.hygro.padelscore.R
import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchState
import ch.hygro.padelscore.model.Team
import ch.hygro.padelscore.presentation.theme.ColorTeamOpponent
import ch.hygro.padelscore.presentation.theme.ColorTeamUs
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

/**
 * Text-fit regression previews: the main match screen with the longest labels per language.
 * Check that no label is clipped (reset button, center label, games/sets, team names).
 */
@Composable
private fun MatchLayoutForPreview(state: MatchState) {
    PadelScoreTheme {
        Column(Modifier.fillMaxSize()) {
            ScoreArea(
                modifier = Modifier.fillMaxWidth().weight(1f),
                teamName = stringResource(R.string.opponent_short),
                pointText = "40",
                games = 4,
                sets = 1,
                backgroundColor = ColorTeamOpponent,
                isServing = false,
                isDeuceCourt = true,
                statsAtTop = true,
                enabled = true,
                ambient = false,
                onClick = {}
            )
            CenterScoreArea(
                modifier = Modifier.fillMaxWidth(),
                matchState = state,
                showCourtChange = false,
                clockText = "12:34",
                canUndo = true,
                onUndo = {},
                onReset = {}
            )
            ScoreArea(
                modifier = Modifier.fillMaxWidth().weight(1f),
                teamName = stringResource(R.string.our_team),
                pointText = "40",
                games = 5,
                sets = 1,
                backgroundColor = ColorTeamUs,
                isServing = true,
                isDeuceCourt = true,
                statsAtTop = false,
                enabled = true,
                ambient = false,
                onClick = {}
            )
        }
    }
}

private val advantageOpponentState = MatchState(
    configuration = MatchConfiguration.normalSet(),
    opponentPoints = 3,
    ourPoints = 3,
    advantageTeam = Team.OPPONENT
)

@Preview(device = WearDevices.SMALL_ROUND, locale = "en", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchLayoutEnPreview() = MatchLayoutForPreview(advantageOpponentState)

@Preview(device = WearDevices.SMALL_ROUND, locale = "fr", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchLayoutFrPreview() = MatchLayoutForPreview(advantageOpponentState)

@Preview(device = WearDevices.SMALL_ROUND, locale = "pt", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchLayoutPtPreview() = MatchLayoutForPreview(advantageOpponentState)

@Preview(device = WearDevices.SMALL_ROUND, locale = "it", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchLayoutItPreview() = MatchLayoutForPreview(advantageOpponentState)

@Preview(device = WearDevices.SMALL_ROUND, locale = "sv", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchLayoutSvPreview() = MatchLayoutForPreview(advantageOpponentState)

@Preview(device = WearDevices.SMALL_ROUND, locale = "ar", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchLayoutArPreview() = MatchLayoutForPreview(advantageOpponentState)

@Preview(device = WearDevices.SMALL_ROUND, locale = "de", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SettingsSwitchDePreview() {
    PadelScoreTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            androidx.wear.compose.material3.SwitchButton(
                checked = true,
                onCheckedChange = {},
                label = { SettingsLabel(stringResource(R.string.match_history_sync)) },
                secondaryLabel = { SettingsDescription(stringResource(R.string.match_history_sync_desc)) },
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
    }
}

