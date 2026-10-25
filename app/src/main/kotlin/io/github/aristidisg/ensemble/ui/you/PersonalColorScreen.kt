@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.aristidisg.ensemble.ui.you

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.PersonalColorData
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorSeason
import io.github.aristidisg.ensemble.model.PersonalContrast
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.SliderRow
import io.github.aristidisg.ensemble.ui.common.SwitchRow
import io.github.aristidisg.ensemble.ui.common.hexColor
import kotlin.math.roundToInt

/** Rule-based season quiz: answers move three axes (temperature, value, chroma); nearest season wins. */
object SeasonQuiz {
    data class Answer(val label: String, val temp: Double = 0.0, val value: Double = 0.0, val chroma: Double = 0.0)
    data class Question(val text: String, val answers: List<Answer>)

    val questions = listOf(
        Question("Veins on your wrist look…", listOf(Answer("Blue or purple", temp = -1.0), Answer("Green", temp = 1.0), Answer("Hard to say"))),
        Question("Which jewelry flatters you?", listOf(Answer("Gold", temp = 1.0), Answer("Silver", temp = -1.0), Answer("Both"))),
        Question("In the sun you…", listOf(Answer("Burn easily", temp = -0.5, value = -0.5), Answer("Tan easily", temp = 0.5, value = 0.5), Answer("Burn, then tan"))),
        Question("Natural hair color", listOf(Answer("Light / blond", value = -1.0), Answer("Medium brown"), Answer("Dark brown / black", value = 1.0), Answer("Red / auburn", temp = 1.0))),
        Question("Eye color", listOf(Answer("Light blue / grey", temp = -0.5, value = -0.5), Answer("Green / hazel", temp = 0.5, chroma = -0.3), Answer("Brown / black", value = 0.5))),
        Question("Contrast between hair and skin", listOf(Answer("Low", chroma = -1.0), Answer("Medium"), Answer("High", chroma = 1.0))),
        Question("Near your face, which looks better?", listOf(Answer("Pure white", temp = -0.5, chroma = 0.5), Answer("Cream", temp = 0.5, chroma = -0.5), Answer("No difference"))),
        Question("Black near your face…", listOf(Answer("Looks sharp", value = 0.5, chroma = 0.5), Answer("Drains me", value = -0.5, chroma = -0.5), Answer("It's fine"))),
    )

    private val axes: Map<ColorSeason, Triple<Double, Double, Double>> = mapOf(
        ColorSeason.LIGHT_SPRING to Triple(0.5, -1.0, 0.3), ColorSeason.WARM_SPRING to Triple(1.0, -0.3, 0.5), ColorSeason.CLEAR_SPRING to Triple(0.3, -0.2, 1.0),
        ColorSeason.LIGHT_SUMMER to Triple(-0.5, -1.0, -0.3), ColorSeason.COOL_SUMMER to Triple(-1.0, -0.3, -0.3), ColorSeason.SOFT_SUMMER to Triple(-0.3, 0.0, -1.0),
        ColorSeason.SOFT_AUTUMN to Triple(0.3, 0.0, -1.0), ColorSeason.WARM_AUTUMN to Triple(1.0, 0.3, -0.3), ColorSeason.DEEP_AUTUMN to Triple(0.5, 1.0, -0.2),
        ColorSeason.DEEP_WINTER to Triple(-0.5, 1.0, 0.3), ColorSeason.COOL_WINTER to Triple(-1.0, 0.3, 0.5), ColorSeason.CLEAR_WINTER to Triple(-0.3, 0.2, 1.0),
    )

    fun result(answers: List<Answer>): Pair<ColorSeason, PersonalContrast> {
        val n = answers.size.coerceAtLeast(1) / 3.0
        val t = answers.sumOf { it.temp } / n; val v = answers.sumOf { it.value } / n; val c = answers.sumOf { it.chroma } / n
        val season = axes.minBy { (_, a) -> (a.first - t) * (a.first - t) + (a.second - v) * (a.second - v) + (a.third - c) * (a.third - c) }.key
        val contrast = when { c > 0.4 -> PersonalContrast.HIGH; c < -0.4 -> PersonalContrast.LOW; else -> PersonalContrast.MEDIUM }
        return season to contrast
    }
}

@Composable
fun PersonalColorScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val pc = s.personalColor
    var quiz by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<Int, SeasonQuiz.Answer>() }
    fun set(t: (io.github.aristidisg.ensemble.model.PersonalColor) -> io.github.aristidisg.ensemble.model.PersonalColor) = vm.update { it.copy(personalColor = t(it.personalColor)) }
    Column {
        BackBar(stringResource(R.string.personal_color), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SwitchRow(stringResource(R.string.pc_enable), pc.enabled, { v -> set { it.copy(enabled = v) } })
            SliderRow(stringResource(R.string.pc_strength), pc.strength.toFloat(), 0f..1f, { v -> set { it.copy(strength = v.toDouble()) } }, { "${(it * 100).roundToInt()}%" })
            SectionTitle(stringResource(R.string.pc_season))
            ChoiceChips(ColorSeason.entries, setOfNotNull(pc.season), { it.label }, { v -> set { it.copy(season = v) } })
            pc.season?.let { season ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    PersonalColorData.of(season).swatches.forEach { Box(Modifier.size(24.dp).clip(CircleShape).background(hexColor(it))) }
                }
            }
            SectionTitle(stringResource(R.string.pc_contrast))
            ChoiceChips(PersonalContrast.entries, setOf(pc.contrast), { it.name.lowercase() }, { v -> set { it.copy(contrast = v) } })
            OutlinedButton(onClick = { quiz = !quiz; answers.clear() }) { Text(stringResource(R.string.pc_quiz)) }
            if (quiz) {
                SeasonQuiz.questions.forEachIndexed { i, q ->
                    Text(q.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    ChoiceChips(q.answers, setOfNotNull(answers[i]), { it.label }, { answers[i] = it })
                }
                if (answers.size == SeasonQuiz.questions.size) {
                    val (season, contrast) = SeasonQuiz.result(answers.values.toList())
                    Text(stringResource(R.string.pc_quiz_result, season.label), modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedButton(onClick = { set { it.copy(season = season, contrast = contrast) }; quiz = false }) { Text(stringResource(R.string.save)) }
                }
            }
            val families = ColorFamily.entries.filter { it != ColorFamily.MULTICOLOR }
            SectionTitle(stringResource(R.string.pc_favorites))
            ChoiceChips(families, pc.favorites, { it.label }, { f -> set { it.copy(favorites = if (f in it.favorites) it.favorites - f else it.favorites + f) } })
            SectionTitle(stringResource(R.string.pc_avoid))
            ChoiceChips(families, pc.avoid, { it.label }, { f -> set { it.copy(avoid = if (f in it.avoid) it.avoid - f else it.avoid + f) } })
            SectionTitle(stringResource(R.string.pc_never))
            ChoiceChips(families, pc.never, { it.label }, { f -> set { it.copy(never = if (f in it.never) it.never - f else it.never + f) } })
            SwitchRow(stringResource(R.string.pc_all_slots), pc.applyAllSlots, { v -> set { it.copy(applyAllSlots = v) } })
        }
    }
}
