package ch.hygro.padelscore.storage

import ch.hygro.padelscore.model.GamePoint
import ch.hygro.padelscore.model.Team
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointHistoryCodecTest {
 private val fixture = """{
 "number":1,"setNumber":1,"gameNumber":1,"scoringTeam":"US",
 "ourScore":"15","opponentScore":"0","ourRallies":1,"opponentRallies":0,
 "numeric":false,"matchTieBreak":false,"gameEnded":false
 }"""

 private val expected = GamePoint(
 number = 1, setNumber = 1, gameNumber = 1, scoringTeam = Team.US,
 ourScore = "15", opponentScore = "0", ourRallies = 1, opponentRallies = 0,
 numeric = false, matchTieBreak = false, gameEnded = false
 )

 @Test fun fixedStoredExampleDecodes() {
 assertEquals(listOf(expected), PointHistoryCodec.decode(JSONArray().put(JSONObject(fixture))))
 }

 @Test fun encodingPreservesEveryStoredField() {
 val actual = PointHistoryCodec.encode(listOf(expected)).getJSONObject(0)
 val stored = JSONObject(fixture)
 val storedKeys = stored.keys().asSequence().toSet()
 val actualKeys = actual.keys().asSequence().toSet()
 assertEquals(storedKeys, actualKeys)
 for (key in storedKeys) assertEquals(stored.get(key), actual.get(key))
 }

 @Test fun roundTripPreservesBothTeamsAndNumericScores() {
 val points = listOf(expected, expected.copy(
 number = 2, scoringTeam = Team.OPPONENT, ourScore = "7",
 opponentScore = "9", numeric = true, matchTieBreak = true, gameEnded = true
 ))
 assertEquals(points, PointHistoryCodec.decode(PointHistoryCodec.encode(points)))
 }

 @Test fun absentHistoryAndEmptyHistoryDecodeAsEmpty() {
 assertTrue(PointHistoryCodec.decode(null).isEmpty())
 assertTrue(PointHistoryCodec.decode(JSONArray()).isEmpty())
 }

 @Test fun missingRequiredFieldsDoNotDiscardAdjacentValidPoints() {
 for (key in JSONObject(fixture).keys().asSequence().toList()) {
 val incomplete = JSONObject(fixture).apply { remove(key) }
 val array = JSONArray().put(incomplete).put(JSONObject(fixture))
 assertEquals(key, listOf(expected), PointHistoryCodec.decode(array))
 }
 }

 @Test fun unknownTeamAndNullRecordsDoNotDiscardValidPoints() {
 val unknown = JSONObject(fixture).put("scoringTeam", "FUTURE_TEAM")
 val nullTeam = JSONObject(fixture).put("scoringTeam", JSONObject.NULL)
 val array = JSONArray().put(JSONObject.NULL).put(unknown).put(nullTeam).put(JSONObject(fixture))
 assertEquals(listOf(expected), PointHistoryCodec.decode(array))
 }
}
