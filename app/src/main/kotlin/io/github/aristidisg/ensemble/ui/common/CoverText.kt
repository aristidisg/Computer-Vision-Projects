package io.github.aristidisg.ensemble.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.CoverLine
import io.github.aristidisg.ensemble.engine.rules.CoverNote
import io.github.aristidisg.ensemble.engine.tables.ColorFormula
import io.github.aristidisg.ensemble.engine.tables.Issue
import io.github.aristidisg.ensemble.engine.tables.RuleBreak

/** Vogue stylist issues, formulas, rule breaks, textures and cover lines to string resources. */
object CoverText {
    val issues: Map<Issue, Int> = mapOf(
        Issue.TONAL to R.string.issue_tonal,
        Issue.ONE_BOLD_THING to R.string.issue_one_bold_thing,
        Issue.OFF_DUTY_TAILORING to R.string.issue_off_duty_tailoring,
        Issue.ALL_BLACK to R.string.issue_all_black,
        Issue.TEXTURE_STUDY to R.string.issue_texture_study,
        Issue.COLOR_BLOCK to R.string.issue_color_block,
        Issue.FORMULA to R.string.issue_formula,
        Issue.SEOUL_LAYERS to R.string.issue_seoul_layers,
        Issue.LIGHT_AND_DARK to R.string.issue_light_and_dark,
    )

    val formulas: Map<ColorFormula, Int> = mapOf(
        ColorFormula.NAVY_CAMEL_WHITE to R.string.f_navy_camel_white,
        ColorFormula.OLIVE_ECRU_BROWN to R.string.f_olive_ecru_brown,
        ColorFormula.BLACK_GREY_RED to R.string.f_black_grey_red,
        ColorFormula.RUST_TEAL_CREAM to R.string.f_rust_teal_cream,
        ColorFormula.DENIM_WHITE_TAN to R.string.f_denim_white_tan,
        ColorFormula.GREY_NAVY_BURGUNDY to R.string.f_grey_navy_burgundy,
        ColorFormula.BLACK_WHITE_COBALT to R.string.f_black_white_cobalt,
        ColorFormula.SAGE_STONE_WHITE to R.string.f_sage_stone_white,
        ColorFormula.FOREST_CAMEL_CREAM to R.string.f_forest_camel_cream,
        ColorFormula.LAVENDER_GREY to R.string.f_lavender_grey,
        ColorFormula.MUSTARD_NAVY to R.string.f_mustard_navy,
        ColorFormula.CREAM_CHOCOLATE to R.string.f_cream_chocolate,
    )

    /** Rule break to (what is broken, how to pull it off). */
    val breaks: Map<RuleBreak, Pair<Int, Int>> = mapOf(
        RuleBreak.NAVY_WITH_BLACK to (R.string.b_navy_with_black to R.string.b_navy_with_black_how),
        RuleBreak.DOUBLE_DENIM to (R.string.b_double_denim to R.string.b_double_denim_how),
        RuleBreak.BROWN_WITH_BLACK to (R.string.b_brown_with_black to R.string.b_brown_with_black_how),
        RuleBreak.SNEAKERS_WITH_TAILORING to (R.string.b_sneakers_with_tailoring to R.string.b_sneakers_with_tailoring_how),
    )

    val textures: Map<String, Int> = mapOf(
        "knit" to R.string.tex_knit, "denim" to R.string.tex_denim, "leather" to R.string.tex_leather,
        "suede" to R.string.tex_suede, "cord" to R.string.tex_cord, "tweed" to R.string.tex_tweed,
        "flannel" to R.string.tex_flannel, "tech" to R.string.tex_tech, "linen" to R.string.tex_linen,
        "smooth" to R.string.tex_smooth, "fleece" to R.string.tex_fleece, "canvas" to R.string.tex_canvas,
    )
}

@Composable
fun issueName(i: Issue): String = CoverText.issues[i]?.let { stringResource(it) } ?: i.label

@Composable
private fun texture(key: String?): String = key?.let { CoverText.textures[it] }?.let { stringResource(it) } ?: key.orEmpty()

/** One cover line as text. */
@Composable
fun coverLineText(n: CoverNote): String = when (n.line) {
    CoverLine.RULE_BREAK -> {
        val b = RuleBreak.entries.firstOrNull { it.name == n.args["break"] }
        val res = b?.let { CoverText.breaks[it] }
        if (b == null || res == null) "" else stringResource(R.string.cl_rule_break, stringResource(res.first), stringResource(res.second))
    }
    CoverLine.FORMULA -> {
        val f = ColorFormula.entries.firstOrNull { it.name == n.args["formula"] }
        stringResource(R.string.cl_formula, f?.let { CoverText.formulas[it] }?.let { stringResource(it) } ?: f?.label.orEmpty())
    }
    CoverLine.VOLUME_TOP -> stringResource(R.string.cl_volume_top)
    CoverLine.VOLUME_BOTTOM -> stringResource(R.string.cl_volume_bottom)
    CoverLine.LONG_LINE -> stringResource(R.string.cl_long_line)
    CoverLine.TEXTURE -> stringResource(R.string.cl_texture, texture(n.args["a"]), texture(n.args["b"]).lowercase())
    CoverLine.THIRD_PIECE -> stringResource(R.string.cl_third_piece, n.args["item"].orEmpty())
}
