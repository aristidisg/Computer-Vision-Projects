package io.github.aristidisg.ensemble.model

import kotlinx.serialization.Serializable

/** A style direction for the deck. What each one favours is data in the engine's MoodTable. */
@Serializable
enum class Mood(val label: String) {
    SEOUL_MINIMAL("Seoul minimal"),
    ITALIAN_SUMMER("Italian summer"),
    NINETIES_STREET("'90s street"),
    OLD_MONEY("Old money"),
    WORKWEAR("Workwear"),
    GORPCORE("Gorpcore"),
}
