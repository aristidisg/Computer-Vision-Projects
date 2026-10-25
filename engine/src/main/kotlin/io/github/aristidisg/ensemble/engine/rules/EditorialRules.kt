package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.tables.MoodTable
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Mood

/** Score changes on top of the rule groups, with the lines that explain them. */
data class Editorial(val bonus: Double, val lines: List<RuleResult>, val cover: Cover? = null)

/**
 * The stylist's layer: the technique bonus (stronger when experimenting or in Vogue stylist
 * mode), the mood you picked, and the accent colour you build the day around.
 */
object EditorialRules {
    /** Raw score per unit of mood fit above [MOOD_PIVOT] (fit 1.0 ≈ +0.07 raw ≈ +18 Match). */
    const val MOOD_WEIGHT = 0.12
    const val MOOD_PIVOT = 0.4
    /** Fit at which the card says it is on-mood. */
    const val MOOD_LINE = 0.6
    /** Visible share of the outfit the accent colour needs. */
    const val ACCENT_MIN = 0.06
    const val ACCENT_BONUS = 0.08
    const val ACCENT_MISSING = 0.12
    /** Per other loud colour competing with the accent. */
    const val ACCENT_RIVAL = 0.03
    /** Vogue stylist: the technique counts this many times over; no technique costs [VOGUE_PLAIN]. */
    const val VOGUE_TECHNIQUE = 3.0
    const val VOGUE_PLAIN = 0.04

    fun apply(look: Look, ctx: EngineContext, technique: Technique?): Editorial {
        var bonus = 0.0
        val lines = ArrayList<RuleResult>()
        if (technique != null) {
            val mult = when {
                ctx.options.vogue -> VOGUE_TECHNIQUE
                ctx.settings.experimentMode -> 1.5
                else -> 1.0
            }
            bonus += StylistRules.TECHNIQUE_BONUS * mult
            lines += res("STYLE", RuleGroup.COLOR, 1.0, 0.0, Explain.TECHNIQUE, "id" to technique.name, "name" to technique.label, "blurb" to technique.blurb)
        } else if (ctx.options.vogue) bonus -= VOGUE_PLAIN
        ctx.settings.mood?.let { m ->
            val fit = moodFit(look, m)
            bonus += MOOD_WEIGHT * (fit - MOOD_PIVOT)
            if (fit >= MOOD_LINE) lines += res("MOOD", RuleGroup.FORMALITY_STYLE, fit, 0.0, Explain.MOOD, "id" to m.name, "name" to m.label)
        }
        ctx.options.accent?.let { a ->
            val share = accentShare(look, a)
            if (share >= ACCENT_MIN) {
                val rivals = look.chromaticColors.filter { it.info.family != a && it.salience > 0.05 }.map { it.info.family }.distinct().size
                bonus += ACCENT_BONUS - ACCENT_RIVAL * rivals
                lines += res("ACCENT", RuleGroup.COLOR, 1.0, 0.0, Explain.ACCENT, "color" to a.label.lowercase())
            } else {
                bonus -= ACCENT_MISSING
                lines += res("ACCENT", RuleGroup.COLOR, 0.2, 0.0, Explain.ACCENT_MISSING, "color" to a.label.lowercase())
            }
        }
        // Vogue stylist: the editor's eye (formulas, proportion, texture, third piece, rule breaks).
        val cover = if (ctx.options.vogue) CoverRules.read(look).also { bonus += it.second }.first else null
        return Editorial(bonus, lines, cover)
    }

    /** Visible area share of [family] (pieces hidden under closed layers don't count). */
    fun accentShare(look: Look, family: ColorFamily): Double =
        look.colors.filter { it.info.family == family && look.isVisible(it.slot) }.sumOf { it.area }

    /**
     * How well the outfit fits a mood, 0..1: half its colours (visible area in the mood's
     * palette), half its pieces (signature piece, fabric, style tag, cut), weighted by area.
     */
    fun moodFit(look: Look, mood: Mood): Double {
        val p = MoodTable.of(mood)
        val totalArea = look.colors.sumOf { it.area }.takeIf { it > 0 } ?: return 0.0
        val colorFit = look.colors.filter { it.info.family in p.palette }.sumOf { it.area } / totalArea
        var w = 0.0; var fit = 0.0
        for (pl in look.placed) {
            val i = pl.item
            val a = look.pieceArea[i.id] ?: continue
            val m = (if (i.subcategory in p.pieces) 0.5 else 0.0) +
                (if (i.materials.any { it in p.materials }) 0.2 else 0.0) +
                (if (i.styleTags.any { it in p.styles }) 0.2 else 0.0) +
                (if (p.cuts.isEmpty() || i.silhouette in p.cuts) 0.1 else 0.0)
            w += a; fit += a * m
        }
        val pieceFit = if (w > 0) fit / w else 0.0
        return 0.5 * colorFit + 0.5 * pieceFit
    }
}
