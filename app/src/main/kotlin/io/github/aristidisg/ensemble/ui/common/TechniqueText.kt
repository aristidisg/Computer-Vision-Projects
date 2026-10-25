package io.github.aristidisg.ensemble.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.Technique

/** Technique name and blurb string resources; null means no special technique ("the classic"). */
object TechniqueText {
    val resources: Map<Technique, Pair<Int, Int>> = mapOf(
        Technique.MONOCHROME to (R.string.t_monochrome to R.string.t_monochrome_b),
        Technique.TONAL to (R.string.t_tonal to R.string.t_tonal_b),
        Technique.COLOR_BLOCK to (R.string.t_color_block to R.string.t_color_block_b),
        Technique.SANDWICH to (R.string.t_sandwich to R.string.t_sandwich_b),
        Technique.STATEMENT to (R.string.t_statement to R.string.t_statement_b),
        Technique.HIGH_LOW to (R.string.t_high_low to R.string.t_high_low_b),
        Technique.SOFT_LAYERS to (R.string.t_soft_layers to R.string.t_soft_layers_b),
        Technique.THIRD_PIECE to (R.string.t_third_piece to R.string.t_third_piece_b),
        Technique.TEXTURE to (R.string.t_texture to R.string.t_texture_b),
        Technique.CONTRAST to (R.string.t_contrast to R.string.t_contrast_b),
    )

    fun of(t: Technique?): Pair<Int, Int> = t?.let { resources[it] } ?: (R.string.t_classic to R.string.t_classic_b)
}

@Composable
fun techniqueName(t: Technique?): String = stringResource(TechniqueText.of(t).first)

@Composable
fun techniqueBlurb(t: Technique?): String = stringResource(TechniqueText.of(t).second)
