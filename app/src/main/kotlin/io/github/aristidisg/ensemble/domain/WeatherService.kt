package io.github.aristidisg.ensemble.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.weather.WeatherRepository
import io.github.aristidisg.ensemble.model.LatLon
import io.github.aristidisg.ensemble.model.LocationMode
import io.github.aristidisg.ensemble.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Coarse location (city-level is enough) with a saved-city fallback, then the forecast. */
@Singleton
class WeatherService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val weather: WeatherRepository,
    private val time: TimeSource,
) {
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * City-level location, cheapest first: any recent last-known fix (no radio use at all), and
     * only when [active] and nothing recent exists, one network fix. GPS is never switched on;
     * coarse location doesn't need it and it costs the most battery.
     */
    @SuppressLint("MissingPermission")
    private suspend fun coarseLocation(active: Boolean): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = enabled.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && (!active || time.nowMillis() - last.time < 6 * 3600_000L)) return last
        val provider = enabled.firstOrNull { it == LocationManager.NETWORK_PROVIDER } ?: return last
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                val signal = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            }
        } ?: last
    }

    private val cityNames = HashMap<Pair<Double, Double>, String>()

    /** Reverse geocoding is cached per rounded coordinate, so it runs once per place, not per refresh. */
    @Suppress("DEPRECATION")
    private suspend fun cityName(loc: Location): String = withContext(Dispatchers.IO) {
        val key = Math.round(loc.latitude * 100) / 100.0 to Math.round(loc.longitude * 100) / 100.0
        cityNames[key]?.let { return@withContext it }
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality
        }.getOrNull()?.also { cityNames[key] = it } ?: "%.2f, %.2f".format(loc.latitude, loc.longitude)
    }

    /** Where to fetch the forecast for. Coordinates are rounded to about a kilometer before they leave the phone. */
    suspend fun place(active: Boolean = true): LatLon? {
        val s = settings.current()
        if (s.locationMode == LocationMode.GPS) {
            coarseLocation(active)?.let { loc ->
                val rounded = LatLon(cityName(loc), Math.round(loc.latitude * 100) / 100.0, Math.round(loc.longitude * 100) / 100.0)
                return rounded
            }
        }
        return s.savedCity
    }

    /** Refresh on app open when older than 3 h, and from the morning worker. */
    suspend fun refresh(force: Boolean = false): Result<WeatherSnapshot> {
        // A fresh cached forecast only needs the free last-known location to check you haven't moved.
        val cached = weather.current()
        val fresh = cached != null && !weather.isStale(cached, time.nowMillis(), null)
        val p = place(active = force || !fresh) ?: return Result.failure(IllegalStateException("No location: allow location or pick a city"))
        return weather.refresh(p, time.nowMillis(), force)
    }

    suspend fun search(name: String) = weather.search(name)

    suspend fun cachedFetchMillis(): Long? = weather.current()?.fetchedAtMillis

    val supportsGeocoder: Boolean get() = Build.VERSION.SDK_INT >= 26 && Geocoder.isPresent()
}
