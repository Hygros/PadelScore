package ch.hygro.padelscore.model

/** One accepted point. Game numbers are match-wide; set numbers are one-based. */
data class GamePoint(
    val number: Int,
    val setNumber: Int,
    val gameNumber: Int,
    val scoringTeam: Team,
    val ourScore: String,
    val opponentScore: String,
    val ourRallies: Int,
    val opponentRallies: Int,
    val numeric: Boolean,
    val matchTieBreak: Boolean,
    val gameEnded: Boolean
)