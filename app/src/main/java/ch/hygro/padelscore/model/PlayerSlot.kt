package ch.hygro.padelscore.model

enum class PlayerSlot(val team: Team) {
    US_1(Team.US),
    US_2(Team.US),
    OPPONENT_1(Team.OPPONENT),
    OPPONENT_2(Team.OPPONENT);

    fun teammate(): PlayerSlot {
        return when (this) {
            US_1 -> US_2
            US_2 -> US_1
            OPPONENT_1 -> OPPONENT_2
            OPPONENT_2 -> OPPONENT_1
        }
    }

    companion object {
        fun firstFor(team: Team): PlayerSlot {
            return when (team) {
                Team.US -> US_1
                Team.OPPONENT -> OPPONENT_1
            }
        }
    }
}