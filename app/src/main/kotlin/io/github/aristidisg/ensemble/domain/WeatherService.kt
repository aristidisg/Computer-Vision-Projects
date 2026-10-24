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

    @SuppressLint("MissingPermission")
    private suspend fun coarseLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        lm.getLastKnownLocation(provider)?.let { if (time.nowMillis() - it.time < 6 * 3600_000L) return it }
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                val signal = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            }
        } ?: lm.getLastKnownLocation(provider)
    }

    @Suppress("DEPRECATION")
    private suspend fun cityName(loc: Location): String = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.locality
        }.getOrNull() ?: "%.2f, %.2f".format(loc.latitude, loc.longitude)
    }

    /** Where to fetch the forecast for. Coordinates are rounded to about a kilometer before they leave the phone. */
    suspend fun place(): LatLon? {
        val s = settings.current()
        if (s.locationMode == LocationMode.GPS) {
            coarseLocation()?.let { loc ->
                val rounded = LatLon(cityName(loc), Math.round(loc.latitude * 100) / 100.0, Math.round(loc.longitude * 100) / 100.0)
                return rounded
            }
        }
        return s.savedCity
    }

    /** Refresh on app open when older than 3 h, and from the morning worker. */
    suspend fun refresh(force: Boolean = false): Result<WeatherSnapshot> {
        val p = place() ?: return Result.failure(IllegalStateException("No location: allow location or pick a city"))
        return weather.refresh(p, time.nowMillis(), force)
    }

    suspend fun search(name: String) = weather.search(name)

    val supportsGeocoder: Boolean get() = Build.VERSION.SDK_INT >= 26 && Geocoder.isPresent()
}
