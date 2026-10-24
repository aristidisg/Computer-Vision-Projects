package io.github.aristidisg.ensemble.data.weather

import io.github.aristidisg.ensemble.data.db.EnsembleDatabase
import io.github.aristidisg.ensemble.data.db.WeatherCacheEntity
import io.github.aristidisg.ensemble.data.repo.DataJson
import io.github.aristidisg.ensemble.model.LatLon
import io.github.aristidisg.ensemble.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Forecast cache in Room: refreshed on app open when older than 3 h and each morning. */
class WeatherRepository(private val db: EnsembleDatabase, private val client: OpenMeteoClient) {

    val cached: Flow<WeatherSnapshot?> = db.cache().observeWeather(KEY).map { it?.let(::decode) }

    suspend fun current(): WeatherSnapshot? = db.cache().weather(KEY)?.let(::decode)

    fun isStale(s: WeatherSnapshot?, now: Long, place: LatLon?): Boolean =
        s == null || now - s.fetchedAtMillis > MAX_AGE_MS ||
            (place != null && (kotlin.math.abs(s.lat - place.lat) > 0.05 || kotlin.math.abs(s.lon - place.lon) > 0.05))

    /** Fetches and caches; returns the cache (with its age) when offline. */
    suspend fun refresh(place: LatLon, now: Long, force: Boolean = false): Result<WeatherSnapshot> {
        val old = current()
        if (!force && !isStale(old, now, place)) return Result.success(old!!)
        return runCatching {
            val fresh = client.forecast(place, now)
            db.cache().saveWeather(WeatherCacheEntity(KEY, now, DataJson.encodeToString(WeatherSnapshot.serializer(), fresh)))
            fresh
        }.recoverCatching { e -> old ?: throw e }
    }

    /** Forecast for a trip destination (not cached). */
    suspend fun forecastFor(place: LatLon, now: Long): Result<WeatherSnapshot> = runCatching { client.forecast(place, now) }

    suspend fun search(name: String): Result<List<LatLon>> = runCatching { client.searchCity(name) }

    private fun decode(e: WeatherCacheEntity) = DataJson.decodeFromString(WeatherSnapshot.serializer(), e.json)

    companion object {
        const val KEY = "current"
        const val MAX_AGE_MS = 3 * 60 * 60 * 1000L
    }
}
