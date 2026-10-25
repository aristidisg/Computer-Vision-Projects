package io.github.aristidisg.ensemble.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.Explain
import io.github.aristidisg.ensemble.engine.rules.RuleResult

/**
 * Explanation keys to text. The Appendix C4 templates live in strings.xml (translatable); other
 * keys fall back to the engine's English template.
 */
object ExplainText {
    private val resources: Map<Explain, Pair<Int, List<String>>> = mapOf(
        Explain.HARMONY_NEUTRAL to (R.string.x_harmony_neutral to emptyList()),
        Explain.HARMONY_ANALOGOUS to (R.string.x_harmony_analogous to listOf("a", "b")),
        Explain.HARMONY_COMPLEMENTARY to (R.string.x_harmony_complementary to listOf("a", "b")),
        Explain.NEUTRAL_ACCENT to (R.string.x_neutral_accent to listOf("accent", "pct")),
        Explain.VALUE_CONTRAST to (R.string.x_value_contrast to emptyList()),
        Explain.LEATHER_MATCH to (R.string.x_leather_match to listOf("color")),
        Explain.LEATHER_CLASH to (R.string.x_leather_clash to listOf("belt", "shoes")),
        Explain.FORMALITY_FIT to (R.string.x_formality_fit to listOf("f", "scenario", "min", "max")),
        Explain.WEATHER_LAYER to (R.string.x_weather_layer to listOf("min", "max", "item")),
        Explain.RAIN to (R.string.x_rain to listOf("pct", "item")),
        Explain.ROTATION to (R.string.x_rotation to listOf("item", "n")),
        Explain.LEARNED_LIKE to (R.string.x_learned_like to listOf("a", "b", "n")),
        Explain.LIKE_SAVED to (R.string.x_like_saved to emptyList()),
        Explain.GIVE_IT_A_TURN to (R.string.x_give_it_a_turn to listOf("item")),
        Explain.SILHOUETTE to (R.string.x_silhouette to emptyList()),
        Explain.PATTERN_ONE to (R.string.x_pattern_one to emptyList()),
        Explain.SHOE_REST to (R.string.x_shoe_rest to listOf("shoes")),
    )

    fun render(r: RuleResult, res: Resources): String {
        val key = r.key ?: return r.id
        val mapped = resources[key] ?: return r.render()
        val args = mapped.second.map { r.args[it] ?: "" }.toTypedArray()
        return res.getString(mapped.first, *args)
    }
}

@Composable
fun explain(r: RuleResult): String = ExplainText.render(r, LocalContext.current.resources)
