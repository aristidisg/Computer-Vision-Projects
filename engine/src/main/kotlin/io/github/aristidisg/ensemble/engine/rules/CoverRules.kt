package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.tables.ColorFormula
import io.github.aristidisg.ensemble.engine.tables.Issue
import io.github.aristidisg.ensemble.engine.tables.RuleBreak
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory
import kotlinx.serialization.Serializable

/** A cover line: why a cover look works, in a stylist's words. Text lives in the app's strings. */
@Serializable
enum class CoverLine { RULE_BREAK, FORMULA, VOLUME_TOP, VOLUME_BOTTOM, LONG_LINE, TEXTURE, THIRD_PIECE }

@Serializable
data class CoverNote(val line: CoverLine, val args: Map<String, String> = emptyMap())

/** How a look reads on a cover (Vogue stylist mode only). */
@Serializable
data class Cover(
    val formula: ColorFormula? = null,
    val ruleBreak: RuleBreak? = null,
    /** Distinct visible textures (knit, denim, suede...). */
    val textures: Int = 0,
    val thirdPiece: Boolean = false,
    /** Everything visible, shoes included, is black or charcoal. */
    val allDark: Boolean = false,
    val notes: List<CoverNote> = emptyList(),
)

/**
 * The editor's eye: proven colour formulas, proportion, texture contrast, a finishing third
 * piece and deliberate rule breaks. Each adds a little to a cover look's score and a cover line.
 */
object CoverRules {
    const val FORMULA_BONUS = 0.05
    const val VOLUME_BONUS = 0.03
    const val LONG_LINE_BONUS = 0.02
    /** Baggy on baggy reads sloppy on a cover. */
    const val BAGGY_PENALTY = 0.04
    const val TEXTURE_BONUS = 0.015
    const val THIRD_PIECE_BONUS = 0.03
    /** Offsets the classic rule the break goes against, so one can make the deck. */
    const val BREAK_BONUS = 0.05
    /** Visible share each part of a colour formula needs. */
    const val FORMULA_PART = 0.04
    /** Cover looks an issue needs before it can run. */
    const val MIN_ISSUE_LOOKS = 4

    private val big = setOf(Silhouette.RELAXED, Silhouette.OVERSIZED)
    private val sneakers = setOf(Subcategory.MINIMAL_SNEAKERS, Subcategory.CANVAS_SNEAKERS, Subcategory.HIGH_TOP_SNEAKERS, Subcategory.CHUNKY_SNEAKERS)
    private val dark = setOf(ColorFamily.BLACK, ColorFamily.CHARCOAL)

    fun read(look: Look): Pair<Cover, Double> {
        var bonus = 0.0
        val notes = ArrayList<CoverNote>()
        val brk = ruleBreak(look)
        if (brk != null) { bonus += BREAK_BONUS; notes += CoverNote(CoverLine.RULE_BREAK, mapOf("break" to brk.name)) }
        val formula = formula(look)
        if (formula != null) { bonus += FORMULA_BONUS; notes += CoverNote(CoverLine.FORMULA, mapOf("formula" to formula.name)) }
        proportion(look)?.let { (line, b) -> bonus += b; if (line != null) notes += CoverNote(line) }
        val tex = textures(look)
        if (tex.size >= 2) {
            bonus += TEXTURE_BONUS * (tex.size - 1).coerceAtMost(2)
            notes += CoverNote(CoverLine.TEXTURE, mapOf("a" to tex[0], "b" to tex[1]))
        }
        val third = thirdPiece(look)
        if (third != null) { bonus += THIRD_PIECE_BONUS; notes += CoverNote(CoverLine.THIRD_PIECE, mapOf("item" to third.lowercase())) }
        val shown = StylistRules.visible(look).map { it.second.family } + listOfNotNull(look.dominant(look.shoes)?.family)
        val allDark = shown.size >= 3 && shown.all { it in dark }
        return Cover(formula, brk, tex.size, third != null, allDark, notes) to bonus
    }

    /** The first formula whose every part shows on at least [FORMULA_PART] of the outfit. */
    fun formula(look: Look): ColorFormula? {
        val area = look.colors.filter { look.isVisible(it.slot) }.groupBy { it.info.family }.mapValues { e -> e.value.sumOf { it.area } }
        return ColorFormula.entries.firstOrNull { f -> f.parts.all { part -> part.sumOf { area[it] ?: 0.0 } >= FORMULA_PART } }
    }

    fun ruleBreak(look: Look): RuleBreak? {
        val area = look.colors.filter { look.isVisible(it.slot) }.groupBy { it.info.family }.mapValues { e -> e.value.sumOf { it.area } }
        if ((area[ColorFamily.NAVY] ?: 0.0) >= 0.12 && (area[ColorFamily.BLACK] ?: 0.0) >= 0.12) return RuleBreak.NAVY_WITH_BLACK
        val upper = listOfNotNull(look.outer, look.mid, look.top.takeIf { !look.topHidden }).mapNotNull { look.dominant(it)?.family }
        val bottom = look.dominant(look.bottom)?.family
        if (bottom != null && bottom.isDenim && upper.any { it.isDenim && it != bottom }) return RuleBreak.DOUBLE_DENIM
        val shoes = look.dominant(look.shoes)?.family
        if (bottom == ColorFamily.BLACK && shoes in setOf(ColorFamily.BROWN, ColorFamily.CHOCOLATE, ColorFamily.CAMEL)) return RuleBreak.BROWN_WITH_BLACK
        val tailored = look.outer?.let { TaxonomyTable.of(it.subcategory).tailored } ?: false
        if (tailored && look.shoes?.subcategory in sneakers) return RuleBreak.SNEAKERS_WITH_TAILORING
        return null
    }

    /** Volume against a clean line scores; baggy on baggy costs. Null when nothing to say. */
    fun proportion(look: Look): Pair<CoverLine?, Double>? {
        val upper = listOfNotNull(look.outer?.takeIf { look.outerClosed }, look.mid, look.top).firstOrNull() ?: return null
        val bottom = look.bottom ?: return null
        val u = upper.silhouette; val b = bottom.silhouette
        return when {
            u in big && b in big -> null to -BAGGY_PENALTY
            u in big -> CoverLine.VOLUME_TOP to VOLUME_BONUS
            b in big -> CoverLine.VOLUME_BOTTOM to VOLUME_BONUS
            u == Silhouette.SLIM && b == Silhouette.SLIM -> CoverLine.LONG_LINE to LONG_LINE_BONUS
            else -> null
        }
    }

    /** Distinct textures of the visible clothes and the shoes, in outfit order. */
    fun textures(look: Look): List<String> {
        val pieces = StylistRules.visible(look).map { it.first } + listOfNotNull(look.shoes)
        return pieces.mapNotNull { i -> i.materials.firstOrNull()?.let { StylistRules.textureOf[it] } }.distinct()
    }

    /** The finishing piece: an open layer, a scarf or a hat. Its name, or null. */
    fun thirdPiece(look: Look): String? {
        look.outer?.takeIf { !look.outerClosed }?.let { return it.name }
        look.mid?.takeIf { !look.midClosed && look.outer == null }?.let { return it.name }
        return look.placed.firstOrNull { it.slot == Slot.NECK || it.slot == Slot.HEADWEAR }?.item?.name
    }

    fun matches(issue: Issue, c: ScoredOutfit): Boolean {
        val cv = c.cover ?: return false
        return when (issue) {
            Issue.TONAL -> c.technique == Technique.TONAL || (c.technique == Technique.MONOCHROME && !cv.allDark)
            Issue.ONE_BOLD_THING -> c.technique == Technique.STATEMENT
            Issue.OFF_DUTY_TAILORING -> c.technique == Technique.HIGH_LOW
            Issue.ALL_BLACK -> cv.allDark
            Issue.TEXTURE_STUDY -> cv.textures >= 3
            Issue.COLOR_BLOCK -> c.technique == Technique.COLOR_BLOCK
            Issue.FORMULA -> cv.formula != null
            Issue.SEOUL_LAYERS -> c.technique == Technique.SOFT_LAYERS || c.technique == Technique.THIRD_PIECE
            Issue.LIGHT_AND_DARK -> c.technique == Technique.CONTRAST
        }
    }

    /** Today's issue: rotated by day (and "More") among those with enough looks; null if none. */
    fun issue(cands: List<ScoredOutfit>, ctx: EngineContext, regenerate: Int): Issue? {
        val feasible = Issue.entries.filter { i -> cands.count { it.cover?.ruleBreak == null && matches(i, it) } >= MIN_ISSUE_LOOKS }
        if (feasible.isEmpty()) return null
        return feasible[Math.floorMod(ctx.epochDay + regenerate, feasible.size.toLong()).toInt()]
    }
}
