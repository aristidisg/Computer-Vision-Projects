package io.github.aristidisg.ensemble.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.model.ColorSeason
import io.github.aristidisg.ensemble.model.LocationMode
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.SliderRow
import io.github.aristidisg.ensemble.ui.common.hexColor
import io.github.aristidisg.ensemble.ui.you.SettingsViewModel
import kotlin.math.roundToInt

private const val STEPS = 9

private val examplePalettes = listOf(
    listOf("#1F2A44", "#FAFAFA", "#808080", "#7B4B2A"),
    listOf("#1F2A44", "#C19A6B", "#EFE6D2", "#6B6B3A", "#B5532E"),
    listOf("#2F55D4", "#EF7D22", "#FAFAFA", "#C62828", "#2E9E4F"),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit, onAddPieces: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cities by vm.cities.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.update { it.copy(locationMode = if (ok) LocationMode.GPS else LocationMode.CITY) }
        if (ok) vm.refreshWeather()
    }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.updateSchedule { it.copy(morningNotification = ok, eveningCheckIn = ok, notificationsAsked = true) }
    }
    fun finish(then: () -> Unit) { vm.update { it.copy(onboarded = true) }; then() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / STEPS }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
            when (step) {
                0 -> { Title(R.string.ob_welcome_title); Body(R.string.ob_welcome_body) }
                1 -> {
                    Title(R.string.ob_style_title)
                    val seeds = listOf(StyleTag.MINIMAL, StyleTag.SMART_CASUAL, StyleTag.CLASSIC, StyleTag.STREETWEAR, StyleTag.SPORTY, StyleTag.RUGGED, StyleTag.PREPPY, StyleTag.OUTDOOR)
                    val chosen = seeds.filter { (s.styleWeights[it] ?: 0.0) >= 0.8 }.toSet()
                    ChoiceChips(seeds, chosen, { it.label }, { t ->
                        vm.update { st -> st.copy(styleWeights = st.styleWeights + (t to if (t in chosen) 0.3 else 1.0)) }
                    })
                }
                2 -> {
                    Title(R.string.ob_boldness_title)
                    SliderRow(stringResource(R.string.boldness), s.boldness.toFloat(), 0f..1f, { v -> vm.update { it.copy(boldness = v.toDouble()) } }, { "${(it * 100).roundToInt()}%" })
                    val idx = when { s.boldness < 0.33 -> 0; s.boldness < 0.66 -> 1; else -> 2 }
                    examplePalettes.forEachIndexed { i, p ->
                        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            p.forEach { Box(Modifier.size(if (i == idx) 40.dp else 28.dp).clip(RoundedCornerShape(8.dp)).background(hexColor(it))) }
                        }
                    }
                }
                3 -> {
                    Title(R.string.ob_thermal_title)
                    val opts = listOf(2.0 to R.string.run_cold, 0.0 to R.string.run_neutral, -2.0 to R.string.run_hot)
                    opts.forEach { (v, l) ->
                        if (s.thermalBias == v) Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(stringResource(l)) }
                        else OutlinedButton(onClick = { vm.update { it.copy(thermalBias = v) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(l)) }
                    }
                }
                4 -> {
                    Title(R.string.ob_week_title)
                    val opts = listOf("university" to R.string.ob_week_university, "office" to R.string.ob_week_office, "mixed" to R.string.ob_week_mixed, "varies" to R.string.ob_week_varies)
                    opts.forEach { (k, l) ->
                        OutlinedButton(onClick = { vm.update { it.copy(usualWeekday = k, weeklyTemplate = week(k)) }; step++ }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(l) + if (s.usualWeekday == k) "  ✓" else "")
                        }
                    }
                }
                5 -> {
                    Title(R.string.ob_location_title); Body(R.string.ob_location_body)
                    Button(onClick = { location.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text(stringResource(R.string.use_gps)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(q, { q = it }, label = { Text(stringResource(R.string.search_city)) }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.searchCity(q) }) { Text(stringResource(R.string.search)) }
                    }
                    cities.forEach { c -> TextButton(onClick = { vm.update { it.copy(locationMode = LocationMode.CITY) }; vm.pickCity(c) }) { Text(c.name) } }
                    s.savedCity?.let { Text("✓ ${it.name}") }
                }
                6 -> {
                    Title(R.string.ob_pc_title)
                    ChoiceChips(ColorSeason.entries, setOfNotNull(s.personalColor.season), { it.label }, { v ->
                        vm.update { it.copy(personalColor = it.personalColor.copy(enabled = true, season = v)) }
                    })
                    Body(R.string.pc_quiz)
                }
                7 -> {
                    Title(R.string.ob_notif_title); Body(R.string.ob_notif_body)
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.updateSchedule { it.copy(morningNotification = true, eveningCheckIn = true, notificationsAsked = true) }
                    }) { Text(stringResource(R.string.allow)) }
                }
                else -> {
                    Title(R.string.ob_add_title); Body(R.string.photo_tips); Body(R.string.empty_body)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { finish(onAddPieces) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_pieces)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) } else Spacer(Modifier.size(1.dp))
            if (step < STEPS - 1) Button(onClick = { step++ }) { Text(stringResource(R.string.next)) }
            else Button(onClick = { finish(onDone) }) { Text(stringResource(R.string.ob_start)) }
        }
    }
}

private fun week(kind: String): Map<Int, String> = when (kind) {
    "university" -> (1..5).associateWith { "university" } + mapOf(6 to "casual", 7 to "casual")
    "office" -> (1..5).associateWith { "office" } + mapOf(6 to "casual", 7 to "casual")
    "mixed" -> mapOf(1 to "office", 2 to "university", 3 to "office", 4 to "university", 5 to "casual", 6 to "casual", 7 to "casual")
    else -> (1..7).associateWith { "casual" }
}

@Composable private fun Title(id: Int) = Text(stringResource(id), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp))
@Composable private fun Body(id: Int) = Text(stringResource(id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp))
