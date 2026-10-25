package io.github.aristidisg.ensemble.data.weather

import io.github.aristidisg.ensemble.model.DailyPoint
import io.github.aristidisg.ensemble.model.HourlyPoint
import io.github.aristidisg.ensemble.model.LatLon
import io.github.aristidisg.ensemble.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo forecast and geocoding (no API key). The only data that leaves the phone is the
 * coordinates or city name in these requests. Weather data by Open-Meteo.com, CC BY 4.0.
 */
class OpenMeteoClient(
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    private val forecastBase: String = "https://api.open-meteo.com/v1/forecast",
    private val geocodeBase: String = "https://geocoding-api.open-meteo.com/v1/search",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forecast(place: LatLon, nowMillis: Long): WeatherSnapshot = withContext(Dispatchers.IO) {
        val url = forecastBase.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", "%.4f".format(java.util.Locale.ROOT, place.lat))
            .addQueryParameter("longitude", "%.4f".format(java.util.Locale.ROOT, place.lon))
            .addQueryParameter("hourly", HOURLY.joinToString(","))
            .addQueryParameter("daily", DAILY.joinToString(","))
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "16")
            .addQueryParameter("timeformat", "unixtime")
            .build()
        parseForecast(get(url.toString()), place, nowMillis)
    }

    suspend fun searchCity(name: String): List<LatLon> = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext emptyList()
        val url = geocodeBase.toHttpUrl().newBuilder()
            .addQueryParameter("name", name.trim())
            .addQueryParameter("count", "8")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        parseGeocoding(get(url.toString()))
    }

    private fun get(url: String): String {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Open-Meteo HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("Empty response")
        }
    }

    fun parseForecast(body: String, place: LatLon, nowMillis: Long): WeatherSnapshot {
        val root = json.parseToJsonElement(body).jsonObject
        val offset = root["utc_offset_seconds"]?.jsonPrimitive?.int ?: 0
        val h = root["hourly"]!!.jsonObject
        val time = h.longs("time")
        val hourly = time.indices.map { i ->
            HourlyPoint(
                epochSecond = time[i],
                temp = h.d("temperature_2m", i),
                feels = h.d("apparent_temperature", i),
                precipProb = h.d("precipitation_probability", i),
                precip = h.d("precipitation", i),
                wind = h.d("wind_speed_10m", i),
                uv = h.d("uv_index", i),
                humidity = h.d("relative_humidity_2m", i),
                code = h.d("weather_code", i).toInt(),
            )
        }
        val d = root["daily"]!!.jsonObject
        val dt = d.longs("time")
        val sunrise = d.longs("sunrise"); val sunset = d.longs("sunset")
        val daily = dt.indices.map { i ->
            DailyPoint(
                epochDay = Math.floorDiv(dt[i] + offset, 86_400L),
                tempMin = d.d("temperature_2m_min", i),
                tempMax = d.d("temperature_2m_max", i),
                sunriseMinute = (Math.floorMod(sunrise.getOrElse(i) { 0 } + offset, 86_400L) / 60).toInt(),
                sunsetMinute = (Math.floorMod(sunset.getOrElse(i) { 0 } + offset, 86_400L) / 60).toInt(),
                code = d.d("weather_code", i).toInt(),
                precipSum = d.d("precipitation_sum", i),
                precipProbMax = d.d("precipitation_probability_max", i),
                uvMax = d.d("uv_index_max", i),
            )
        }
        return WeatherSnapshot(place.name, place.lat, place.lon, nowMillis, offset, hourly, daily)
    }

    fun parseGeocoding(body: String): List<LatLon> {
        val results = json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
        return results.map { it.jsonObject }.map { r ->
            val parts = listOfNotNull(r.s("name"), r.s("admin1"), r.s("country")).distinct()
            LatLon(parts.joinToString(", "), r["latitude"]!!.jsonPrimitive.doubleOrNull ?: 0.0, r["longitude"]!!.jsonPrimitive.doubleOrNull ?: 0.0)
        }
    }

    private fun JsonObject.longs(key: String): List<Long> = this[key]?.jsonArray?.map { it.jsonPrimitive.long } ?: emptyList()
    private fun JsonObject.d(key: String, i: Int): Double = this[key]?.jsonArray?.getOrNull(i)?.jsonPrimitive?.doubleOrNull ?: 0.0
    private fun JsonObject.s(key: String): String? = this[key]?.jsonPrimitive?.content

    companion object {
        val HOURLY = listOf(
            "temperature_2m", "apparent_temperature", "precipitation_probability", "precipitation",
            "wind_speed_10m", "uv_index", "relative_humidity_2m", "weather_code",
        )
        val DAILY = listOf(
            "temperature_2m_min", "temperature_2m_max", "sunrise", "sunset", "weather_code",
            "precipitation_sum", "precipitation_probability_max", "uv_index_max",
        )
        const val ATTRIBUTION = "Weather data by Open-Meteo.com"
        const val ATTRIBUTION_URL = "https://open-meteo.com/"
    }
}
