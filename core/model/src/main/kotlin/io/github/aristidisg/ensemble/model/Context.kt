package io.github.aristidisg.ensemble.model

import kotlinx.serialization.Serializable

/** An occasion with its formality band, time window and constraints (Appendix C1). */
@Serializable
data class Scenario(
    val id: String,
    val name: String,
    val icon: String = "event",
    val formalityMin: Double,
    val formalityTarget: Double,
    val formalityMax: Double,
    /** Window in hours from local midnight; end may exceed 24 for events past midnight. */
    val windowStartHour: Int,
    val windowEndHour: Int,
    val indoorShare: Double,
    val activity: Activity,
    val palette: ScenarioPalette = ScenarioPalette.ANY,
    val accessoryMin: Int = 0,
    val accessoryMax: Int = 3,
    val requiredSlots: Set<Slot> = emptySet(),
    val requiredSubcategories: Set<Subcategory> = emptySet(),
    val forbiddenSubcategories: Set<Subcategory> = emptySet(),
    val requiredTags: Set<StyleTag> = emptySet(),
    val forbiddenTags: Set<StyleTag> = emptySet(),
    val preferredStyles: Map<StyleTag, Double> = emptyMap(),
    /** Small bonus when the outfit contains one of these (e.g. collared tops at the office). */
    val bonusSubcategories: Set<Subcategory> = emptySet(),
    /** Highest pattern loudness allowed (interview: no loud patterns or logos). */
    val maxLoudness: Int = 3,
    val allowsLogos: Boolean = true,
    val shoesOptional: Boolean = false,
    val allowsSwimwear: Boolean = false,
    val allowsSlides: Boolean = false,
    val notes: String = "",
    val builtIn: Boolean = true,
)

@Serializable
data class HourlyPoint(
    val epochSecond: Long,
    val temp: Double,
    val feels: Double,
    val precipProb: Double,
    val precip: Double,
    val wind: Double,
    val uv: Double,
    val humidity: Double,
    val code: Int,
)

@Serializable
data class DailyPoint(
    val epochDay: Long,
    val tempMin: Double,
    val tempMax: Double,
    val sunriseMinute: Int,
    val sunsetMinute: Int,
    val code: Int,
    val precipSum: Double = 0.0,
    val precipProbMax: Double = 0.0,
    val uvMax: Double = 0.0,
)

@Serializable
data class WeatherSnapshot(
    val locationName: String,
    val lat: Double,
    val lon: Double,
    val fetchedAtMillis: Long,
    val utcOffsetSeconds: Int,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
)

/** Aggregated weather over a scenario's time window. */
@Serializable
data class WindowWeather(
    val feelsMin: Double,
    val feelsMax: Double,
    val rainProbMax: Double = 0.0,
    val rainSum: Double = 0.0,
    val windMax: Double = 0.0,
    val uvMax: Double = 0.0,
    val humidityMax: Double = 50.0,
    val dark: Boolean = false,
    val snow: Boolean = false,
    val code: Int = 0,
    val manual: Boolean = false,
) {
    val feelsMid: Double get() = (feelsMin + feelsMax) / 2
    val swing: Double get() = feelsMax - feelsMin

    companion object {
        fun mild() = WindowWeather(feelsMin = 15.0, feelsMax = 19.0, manual = true)
    }
}

@Serializable
data class LatLon(val name: String, val lat: Double, val lon: Double)
