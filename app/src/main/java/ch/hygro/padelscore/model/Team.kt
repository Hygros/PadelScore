package ch.hygro.padelscore.model

enum class Team {
    OPPONENT,
    US;

    fun other(): Team {
        return when (this) {
            OPPONENT -> US
            US -> OPPONENT
        }
    }
}