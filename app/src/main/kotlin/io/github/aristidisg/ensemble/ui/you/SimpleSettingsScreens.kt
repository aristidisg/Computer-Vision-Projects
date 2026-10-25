package io.github.aristidisg.ensemble.ui.you

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.BuildConfig
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.weather.OpenMeteoClient
import io.github.aristidisg.ensemble.model.LocationMode
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Strictness
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.SliderRow
import io.github.aristidisg.ensemble.ui.common.SwitchRow
import kotlin.math.roundToInt

@Composable
private fun SettingsPage(title: String, nav: NavHostController, content: @Composable () -> Unit) {
    Column {
        BackBar(title, onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) { content() }
    }
}

@Composable
fun StyleProfileScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsPage(stringResource(R.string.style_profile), nav) {
        SectionTitle(stringResource(R.string.strictness))
        ChoiceChips(Strictness.entries, setOf(s.strictness), { it.label }, vm::setStrictness)
        SliderRow(stringResource(R.string.boldness), s.boldness.toFloat(), 0f..1f, { v -> vm.update { it.copy(boldness = v.toDouble()) } }, { "${(it * 100).roundToInt()}%" })
        SwitchRow(stringResource(R.string.outerwear_off_title), s.outerwearOff, { v -> vm.update { it.copy(outerwearOff = v) } }, stringResource(R.string.outerwear_off_body))
        SwitchRow(stringResource(R.string.mid_off_title), s.midLayersOff, { v -> vm.update { it.copy(midLayersOff = v) } }, stringResource(R.string.mid_off_body))
        SwitchRow(stringResource(R.string.experiment_title), s.experimentMode, { v -> vm.update { it.copy(experimentMode = v) } }, stringResource(R.string.experiment_body))
        SectionTitle(stringResource(R.string.styles))
        StyleTag.entries.forEach { t ->
            SliderRow(t.label, (s.styleWeights[t] ?: 0.3).toFloat(), 0f..1f, { v -> vm.update { it.copy(styleWeights = it.styleWeights + (t to v.toDouble())) } }, { "%.1f".format(it) })
        }
        SectionTitle(stringResource(R.string.thermal_bias))
        val cold = stringResource(R.string.run_cold); val neutral = stringResource(R.string.run_neutral); val hot = stringResource(R.string.run_hot)
        ChoiceChips(listOf(1.0, 0.0, -1.0), setOf(Math.signum(s.thermalBias)), { v -> when { v > 0 -> cold; v < 0 -> hot; else -> neutral } },
            { v -> vm.update { it.copy(thermalBias = v * 2) } })
        SliderRow(stringResource(R.string.shorts_min), s.shortsMinTemp.toFloat(), 12f..28f, { v -> vm.update { it.copy(shortsMinTemp = v.roundToInt().toDouble()) } }, { "${it.roundToInt()} °C" })
        SwitchRow(stringResource(R.string.hide_accessories), s.hideAccessories, { v -> vm.update { it.copy(hideAccessories = v) } })
        SwitchRow(stringResource(R.string.allow_mixed_metals), s.allowMixedMetals, { v -> vm.update { it.copy(allowMixedMetals = v) } })
        SwitchRow(stringResource(R.string.shoes_darker), s.shoesDarkerThanTrousers, { v -> vm.update { it.copy(shoesDarkerThanTrousers = v) } })
        SwitchRow(stringResource(R.string.categories_hints_setting), s.categoryHints, { v -> vm.update { it.copy(categoryHints = v) } })
        SwitchRow(stringResource(R.string.cutouts_setting), s.cutouts, { v -> vm.update { it.copy(cutouts = v) } })
    }
}

@Composable
fun RotationScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsPage(stringResource(R.string.rotation), nav) {
        val labels = mapOf("BASE_TOP" to "Base tops", "MID" to "Mid layers", "JEANS" to "Jeans", "TROUSERS" to "Other trousers", "OUTER" to "Outerwear", "SHOES" to "Shoes", "ACCESSORY" to "Accessories")
        labels.forEach { (k, l) ->
            SliderRow(stringResource(R.string.rotation_window, l), (s.rotationDays[k] ?: 0).toFloat(), 0f..14f, { v -> vm.update { it.copy(rotationDays = it.rotationDays + (k to v.roundToInt())) } }, { "${it.roundToInt()}" })
        }
        SwitchRow(stringResource(R.string.strict_rotation), s.strictRotation, { v -> vm.update { it.copy(strictRotation = v) } })
        SwitchRow(stringResource(R.string.forgotten_love), s.forgottenLove, { v -> vm.update { it.copy(forgottenLove = v) } })
        SliderRow(stringResource(R.string.outfit_cooldown), s.outfitCooldownDays.toFloat(), 0f..60f, { v -> vm.update { it.copy(outfitCooldownDays = v.roundToInt()) } }, { "${it.roundToInt()}" })
        SliderRow(stringResource(R.string.rejected_cooldown), s.rejectedCooldownDays.toFloat(), 0f..90f, { v -> vm.updateLearning { it.copy(rejectedCooldownDays = v.roundToInt()) } }, { "${it.roundToInt()}" })
        SliderRow(stringResource(R.string.deck_size), s.deckSize.toFloat(), 6f..20f, { v -> vm.update { it.copy(deckSize = v.roundToInt()) } }, { "${it.roundToInt()}" })
        SliderRow(stringResource(R.string.exploration), s.explorationRate.toFloat(), 0f..0.3f, { v -> vm.update { it.copy(explorationRate = v.toDouble()) } }, { "${(it * 100).roundToInt()}%" })
        SwitchRow(stringResource(R.string.auto_laundry), s.autoLaundry, { v -> vm.update { it.copy(autoLaundry = v) } })
    }
}

@Composable
fun LocationScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cities by vm.cities.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshWeather() }
    SettingsPage(stringResource(R.string.location_weather), nav) {
        SwitchRow(stringResource(R.string.use_gps), s.locationMode == LocationMode.GPS, { v ->
            vm.update { it.copy(locationMode = if (v) LocationMode.GPS else LocationMode.CITY) }
            if (v) perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        })
        SectionTitle(stringResource(R.string.saved_city))
        Text(s.savedCity?.name ?: "—")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, label = { Text(stringResource(R.string.search_city)) }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.searchCity(q) }) { Text(stringResource(R.string.search)) }
        }
        (cities + s.savedCities).distinctBy { it.name }.forEach { c -> TextButton(onClick = { vm.pickCity(c) }) { Text(c.name) } }
        SliderRow(stringResource(R.string.indoor_winter), s.indoorTempWinter.toFloat(), 16f..26f, { v -> vm.update { it.copy(indoorTempWinter = v.roundToInt().toDouble()) } }, { "${it.roundToInt()} °C" })
        SliderRow(stringResource(R.string.indoor_summer), s.indoorTempSummer.toFloat(), 18f..30f, { v -> vm.update { it.copy(indoorTempSummer = v.roundToInt().toDouble()) } }, { "${it.roundToInt()} °C" })
        OutlinedButton(onClick = vm::refreshWeather) { Text(stringResource(R.string.refresh_weather)) }
        Text(stringResource(R.string.weather_attribution) + " · CC BY 4.0", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
    }
}

private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

@Composable
fun NotificationsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SettingsPage(stringResource(R.string.notifications), nav) {
        SwitchRow(stringResource(R.string.morning_note), s.morningNotification, { v ->
            vm.updateSchedule { it.copy(morningNotification = v) }
            if (v && Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, stringResource(R.string.at_time, hhmm(s.morningMinute)))
        SliderRow(stringResource(R.string.morning_note), s.morningMinute.toFloat(), 300f..720f, { v -> vm.updateSchedule { it.copy(morningMinute = (v / 15).roundToInt() * 15) } }, { hhmm(it.toInt()) })
        SwitchRow(stringResource(R.string.evening_checkin), s.eveningCheckIn, { v ->
            vm.updateSchedule { it.copy(eveningCheckIn = v) }
            if (v && Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, stringResource(R.string.at_time, hhmm(s.eveningMinute)))
        SliderRow(stringResource(R.string.evening_checkin), s.eveningMinute.toFloat(), 1020f..1380f, { v -> vm.updateSchedule { it.copy(eveningMinute = (v / 15).roundToInt() * 15) } }, { hhmm(it.toInt()) })
    }
}

@Composable
fun AboutScreen(nav: NavHostController) {
    SettingsPage(stringResource(R.string.about), nav) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.about_body), modifier = Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.storage_note), style = MaterialTheme.typography.bodyMedium)
        Text(OpenMeteoClient.ATTRIBUTION + " (CC BY 4.0)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        Text(stringResource(R.string.fonts_license), style = MaterialTheme.typography.bodySmall)
    }
}
