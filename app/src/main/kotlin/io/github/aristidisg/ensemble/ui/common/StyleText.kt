package io.github.aristidisg.ensemble.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.Tip
import io.github.aristidisg.ensemble.model.Mood

/** Styling tips and moods to string resources. */
object StyleText {
    val tips: Map<Tip, Int> = mapOf(
        Tip.FRENCH_TUCK to R.string.tip_french_tuck,
        Tip.FULL_TUCK to R.string.tip_full_tuck,
        Tip.UNTUCKED to R.string.tip_untucked,
        Tip.ROLL_SLEEVES to R.string.tip_roll_sleeves,
        Tip.PUSH_SLEEVES to R.string.tip_push_sleeves,
        Tip.CUFF_OVER_BOOTS to R.string.tip_cuff_over_boots,
        Tip.CUFF_ANKLE to R.string.tip_cuff_ankle,
        Tip.COLLAR_OUT to R.string.tip_collar_out,
        Tip.OPEN_LAYER to R.string.tip_open_layer,
        Tip.HALF_ZIP to R.string.tip_half_zip,
        Tip.HEM_PEEK to R.string.tip_hem_peek,
        Tip.NO_BREAK to R.string.tip_no_break,
        Tip.SHORTS_ROLL to R.string.tip_shorts_roll,
        Tip.BLAZER_OPEN to R.string.tip_blazer_open,
    )

    val moods: Map<Mood, Int> = mapOf(
        Mood.SEOUL_MINIMAL to R.string.mood_seoul_minimal,
        Mood.ITALIAN_SUMMER to R.string.mood_italian_summer,
        Mood.NINETIES_STREET to R.string.mood_nineties_street,
        Mood.OLD_MONEY to R.string.mood_old_money,
        Mood.WORKWEAR to R.string.mood_workwear,
        Mood.GORPCORE to R.string.mood_gorpcore,
    )
}

@Composable
fun tipText(t: Tip): String = StyleText.tips[t]?.let { stringResource(it) } ?: t.text

@Composable
fun moodName(m: Mood): String = StyleText.moods[m]?.let { stringResource(it) } ?: m.label
