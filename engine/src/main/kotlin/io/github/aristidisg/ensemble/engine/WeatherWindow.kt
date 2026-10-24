package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.WeatherSnapshot
import io.github.aristidisg.ensemble.model.WindowWeather

/** Scenario window aggregates from a forecast (SPEC 8.1). Pure: the day is passed in. */
object WeatherWindow {
    private val snowCodes = setOf(71, 73, 75, 77, 85, 86)

    /**
     * Feels-like min/max over the window's hours, max rain probability, rain sum, max wind, max UV,
     * and "dark" when sunset falls inside the window. Null when the forecast doesn't cover it.
     */
    fun aggregate(snapshot: WeatherSnapshot, epochDay: Long, scenario: Scenario): WindowWeather? =
        aggregate(snapshot, epochDay, scenario.windowStartHour, scenario.windowEndHour)

    fun aggregate(snapshot: WeatherSnapshot, epochDay: Long, startHour: Int, endHour: Int): WindowWeather? {
        val offset = snapshot.utcOffsetSeconds.toLong()
        val dayStart = epochDay * 86_400L - offset
        val from = dayStart + startHour * 3600L
        val to = dayStart + maxOf(endHour, startHour + 1) * 3600L
        val hours = snapshot.hourly.filter { it.epochSecond in from until to }
        if (hours.isEmpty()) {
            // Beyond the hourly range: fall back to the daily summary (16-day forecast).
            val d = snapshot.daily.firstOrNull { it.epochDay == epochDay } ?: return null
            return WindowWeather(
                feelsMin = d.tempMin, feelsMax = d.tempMax, rainProbMax = d.precipProbMax, rainSum = d.precipSum,
                uvMax = d.uvMax, snow = d.code in snowCodes, code = d.code,
            )
        }
        val daily = snapshot.daily.firstOrNull { it.epochDay == epochDay }
        val sunsetHour = daily?.sunsetMinute?.div(60.0)
        val dark = sunsetHour != null && (sunsetHour in startHour.toDouble()..endHour.toDouble() || endHour > 24 || startHour >= sunsetHour)
        return WindowWeather(
            feelsMin = hours.minOf { it.feels },
            feelsMax = hours.maxOf { it.feels },
            rainProbMax = hours.maxOf { it.precipProb },
            rainSum = hours.sumOf { it.precip },
            windMax = hours.maxOf { it.wind },
            uvMax = hours.maxOf { it.uv },
            humidityMax = hours.maxOf { it.humidity },
            dark = dark,
            snow = hours.any { it.code in snowCodes },
            code = hours.maxOf { it.code },
        )
    }

    /** WMO weather code to a short label. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "—"
    }
}
