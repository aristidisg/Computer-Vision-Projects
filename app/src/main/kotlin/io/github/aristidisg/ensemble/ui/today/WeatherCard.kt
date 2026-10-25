@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.aristidisg.ensemble.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.weather.OpenMeteoClient
import io.github.aristidisg.ensemble.engine.WeatherWindow
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

fun weatherIcon(code: Int): ImageVector = when (code) {
    in 0..2 -> Icons.Outlined.WbSunny
    in 51..67, in 80..82 -> Icons.Outlined.Umbrella
    in 71..77, 85, 86 -> Icons.Outlined.AcUnit
    in 95..99 -> Icons.Outlined.Thunderstorm
    else -> Icons.Outlined.Cloud
}

/** The selected scenario's window forecast: icon, feels-like range, rain, wind, attribution. */
@Composable
fun WeatherCard(s: TodayState, onRefresh: () -> Unit, onManual: () -> Unit) {
    val dw = s.weather ?: return
    val w = dw.window
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(weatherIcon(w.code), WeatherWindow.describe(w.code))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feels_range, w.feelsMin.roundToInt(), w.feelsMax.roundToInt()), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.rain_pct, w.rainProbMax.roundToInt()), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.wind_kmh, w.windMax.roundToInt()), style = MaterialTheme.typography.bodySmall)
                    if (w.uvMax >= 3) Text(stringResource(R.string.uv_index, w.uvMax.roundToInt()), style = MaterialTheme.typography.bodySmall)
                }
                val sub = when {
                    w.manual -> stringResource(R.string.manual_temp)
                    !dw.fromForecast && s.weatherError != null -> s.weatherError
                    dw.fetchedAtMillis != null && System.currentTimeMillis() - dw.fetchedAtMillis > 3 * 3600_000L ->
                        stringResource(R.string.offline_age, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(dw.fetchedAtMillis)))
                    else -> s.place
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (dw.fromForecast) Text(
                    stringResource(R.string.weather_attribution), style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { uri.openUri(OpenMeteoClient.ATTRIBUTION_URL) },
                )
            }
            IconButton(onClick = onManual) { Icon(Icons.Outlined.Edit, stringResource(R.string.manual_temp)) }
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh_weather)) }
        }
    }
}
