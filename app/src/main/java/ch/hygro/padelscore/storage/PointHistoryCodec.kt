package ch.hygro.padelscore.storage

import ch.hygro.padelscore.model.GamePoint
import ch.hygro.padelscore.model.Team
import org.json.JSONArray
import org.json.JSONObject

internal object PointHistoryCodec {
    fun encode(points: List<GamePoint>): JSONArray = JSONArray().apply {
        points.forEach { point ->
            put(JSONObject()
                .put("number", point.number)
                .put("setNumber", point.setNumber)
                .put("gameNumber", point.gameNumber)
                .put("scoringTeam", point.scoringTeam.name)
                .put("ourScore", point.ourScore)
                .put("opponentScore", point.opponentScore)
                .put("ourRallies", point.ourRallies)
                .put("opponentRallies", point.opponentRallies)
                .put("numeric", point.numeric)
                .put("matchTieBreak", point.matchTieBreak)
                .put("gameEnded", point.gameEnded))
        }
    }

    fun decode(array: JSONArray?): List<GamePoint> = buildList {
        if (array != null) {
            for (index in 0 until array.length()) {
                runCatching {
                    val json = array.getJSONObject(index)
                    GamePoint(
                        number = json.getInt("number"),
                        setNumber = json.getInt("setNumber"),
                        gameNumber = json.getInt("gameNumber"),
                        scoringTeam = Team.valueOf(json.getString("scoringTeam")),
                        ourScore = json.getString("ourScore"),
                        opponentScore = json.getString("opponentScore"),
                        ourRallies = json.getInt("ourRallies"),
                        opponentRallies = json.getInt("opponentRallies"),
                        numeric = json.getBoolean("numeric"),
                        matchTieBreak = json.getBoolean("matchTieBreak"),
                        gameEnded = json.getBoolean("gameEnded")
                    )
                }.getOrNull()?.let { add(it) }
            }
        }
    }
}