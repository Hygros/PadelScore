package ch.hygro.padelscore.storage

import ch.hygro.padelscore.model.MatchConfiguration
import ch.hygro.padelscore.model.MatchFormat
import ch.hygro.padelscore.model.ScoringMode
import ch.hygro.padelscore.model.SetResult
import ch.hygro.padelscore.model.Team
import org.json.JSONObject

/**
 * Shared JSON serialization for [MatchConfiguration] and [SetResult] used by both
 * [MatchRepository] (active match snapshot) and [MatchHistoryRepository] (completed matches).
 *
 * Field names, enum representations, and fallback defaults must remain stable to preserve
 * compatibility with already persisted SharedPreferences data.
 */
internal object MatchJsonCodec {

    fun encodeConfiguration(configuration: MatchConfiguration): JSONObject {
        return JSONObject()
            .put("scoringMode", configuration.scoringMode.name)
            .put("matchFormat", configuration.matchFormat.name)
            .put("gamesRequiredToWinSet", configuration.gamesRequiredToWinSet)
            .put("tieBreakEnabled", configuration.tieBreakEnabled)
            .put("tieBreakAtGames", configuration.tieBreakAtGames)
            .put("tieBreakPointsToWin", configuration.tieBreakPointsToWin)
            .put("matchTieBreakPointsToWin", configuration.matchTieBreakPointsToWin)
            .put("firstServingTeam", configuration.firstServingTeam.name)
    }

    fun decodeConfiguration(json: JSONObject): MatchConfiguration {
        val gamesRequired = json.optInt("gamesRequiredToWinSet", 6)
        val defaultTieBreakAt = if (gamesRequired == 4) 4 else 6

        return MatchConfiguration(
            scoringMode = enumValueOrDefault(
                json.optString("scoringMode"),
                ScoringMode.STAR_POINT
            ),
            matchFormat = enumValueOrDefault(
                json.optString("matchFormat"),
                MatchFormat.BEST_OF_THREE
            ),
            gamesRequiredToWinSet = gamesRequired,
            tieBreakEnabled = json.optBoolean("tieBreakEnabled", true),
            tieBreakAtGames = json.optInt("tieBreakAtGames", defaultTieBreakAt),
            tieBreakPointsToWin = json.optInt("tieBreakPointsToWin", 7),
            matchTieBreakPointsToWin = json.optInt("matchTieBreakPointsToWin", 10),
            firstServingTeam = enumValueOrDefault(
                json.optString("firstServingTeam"),
                Team.US
            )
        )
    }

    fun encodeSetResult(result: SetResult): JSONObject {
        return JSONObject()
            .put("opponentGames", result.opponentGames)
            .put("ourGames", result.ourGames)
            .put("wasTieBreak", result.wasTieBreak)
            .put("opponentTieBreakPoints", result.opponentTieBreakPoints)
            .put("ourTieBreakPoints", result.ourTieBreakPoints)
    }

    fun decodeSetResult(json: JSONObject): SetResult {
        return SetResult(
            opponentGames = json.optInt("opponentGames", 0),
            ourGames = json.optInt("ourGames", 0),
            wasTieBreak = json.optBoolean("wasTieBreak", false),
            opponentTieBreakPoints = json.optInt("opponentTieBreakPoints", 0),
            ourTieBreakPoints = json.optInt("ourTieBreakPoints", 0)
        )
    }

    inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
