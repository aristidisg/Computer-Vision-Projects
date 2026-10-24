package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.tables.StyleTables
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.engine.tables.TrouserKind
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Subcategory.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formality and style rules F1-F3, O1-O4 (SPEC 5.4). */
object FormalityRules {
    private val G = RuleGroup.FORMALITY_STYLE

    fun f1Coherence(look: Look, ctx: EngineContext): RuleResult {
        val (std, range) = StyleRules.formalitySpread(look)
        var s = 1.0 - ((std - 1.0) / 2.5).coerceIn(0.0, 1.0)
        val highLow = ctx.prefs.highLowTaste > 0.0
        if (range > 4 && !highLow) s *= 0.5
        return if (range > 4) res("F1", G, s, 1.0, Explain.FORMALITY_MIXED) else res("F1", G, s)
    }

    fun f2Target(look: Look, ctx: EngineContext): RuleResult {
        val sc = ctx.scenario
        val target = sc.formalityTarget + ctx.formalityOffset
        val f = look.meanFormality
        val s = StyleRules.gaussian(f - target, 1.2)
        val args = arrayOf("f" to f.roundToInt(), "scenario" to sc.name, "min" to sc.formalityMin.fmt(), "max" to sc.formalityMax.fmt())
        return if (s >= 0.6) res("F2", G, s, 1.5, Explain.FORMALITY_FIT, *args) else res("F2", G, s, 1.5, Explain.FORMALITY_OFF, *args)
    }

    private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else toString()

    /** F3 classic conflicts (a data table of checks). */
    fun f3Conflicts(look: Look, ctx: EngineContext): RuleResult? {
        val subs = look.items.map { it.subcategory }.toSet()
        val outer = look.outer
        val blazer = outer != null && TaxonomyTable.of(outer.subcategory).tailored
        val collared = look.top?.let { TaxonomyTable.of(it.subcategory).collared } ?: false
        val bottomKind = look.bottom?.let { TaxonomyTable.of(it.subcategory).trouserKind }
        val streetTaste = (ctx.settings.styleWeights[StyleTag.STREETWEAR] ?: 0.0) >= 0.9
        val issues = buildList {
            if (TIE in subs && (!collared || !blazer)) add("A tie needs a collared shirt and a blazer." to 0.1)
            if (POCKET_SQUARE in subs && !blazer) add("A pocket square needs a blazer." to 0.2)
            if (SUIT_JACKET in subs && SUIT_TROUSERS !in subs) add("A suit jacket wants its own trousers." to 0.4)
            if (HOODIE in subs && outer != null && (blazer || outer.subcategory == WOOL_OVERCOAT) && !streetTaste)
                add("A hoodie under tailoring only works with a streetwear lean." to 0.3)
            if (bottomKind == TrouserKind.SHORTS && blazer) {
                val ok = look.bottom?.subcategory == TAILORED_SHORTS && outer?.subcategory == UNSTRUCTURED_BLAZER && ctx.weather.feelsMax >= 24
                if (!ok) add("Shorts with a blazer only as tailored shorts and an unstructured blazer in the heat." to 0.2)
            }
            if (look.bottom?.subcategory == ATHLETIC_SHORTS && ctx.scenario.id !in setOf("gym", "running", "home", "beach"))
                add("Athletic shorts belong at the gym." to 0.2)
        }
        if (issues.isEmpty()) return null
        val worst = issues.minBy { it.second }
        return res("F3", G, worst.second, 2.0, Explain.CLASSIC_CONFLICT, "what" to worst.first)
    }

    /** O1: the style vector should be led by one or two styles; pairs scored with the B5 matrix. */
    fun o1Coherence(look: Look, ctx: EngineContext): RuleResult? {
        val core = look.core.map { it.item }
        if (core.size < 2) return null
        var sum = 0.0; var n = 0
        for (i in core.indices) for (j in i + 1 until core.size) {
            val a = core[i].styleTags; val b = core[j].styleTags
            if (a.isEmpty() || b.isEmpty()) continue
            var best = 0.0
            for (x in a) for (y in b) {
                val v = StyleTables.style(x, y) + ctx.prefs.styleMixAdjust(x, y)
                if (v > best) best = v
            }
            sum += best.coerceIn(0.0, 1.0); n++
        }
        if (n == 0) return null
        val s = sum / n
        val lead = leadingStyle(look)
        return if (s >= 0.85 && lead != null) res("O1", G, s, 1.0, Explain.STYLE_COHERENT, "style" to lead.label.lowercase())
        else if (s < 0.6) res("O1", G, s, 1.0, Explain.STYLE_CLASH) else res("O1", G, s)
    }

    fun leadingStyle(look: Look): StyleTag? {
        val v = HashMap<StyleTag, Double>()
        for (p in look.core) for (t in p.item.styleTags) v[t] = (v[t] ?: 0.0) + (look.pieceArea[p.item.id] ?: 0.0) / p.item.styleTags.size
        return v.maxByOrNull { it.value }?.key
    }

    /** Top two styles by area-weighted tag share (learning feature `style:`). */
    fun topStyles(look: Look): List<StyleTag> {
        val v = HashMap<StyleTag, Double>()
        for (p in look.core) for (t in p.item.styleTags) v[t] = (v[t] ?: 0.0) + (look.pieceArea[p.item.id] ?: 0.0) / p.item.styleTags.size
        return v.entries.sortedWith(compareByDescending<Map.Entry<StyleTag, Double>> { it.value }.thenBy { it.key.ordinal }).take(2).map { it.key }
    }

    fun o2Scenario(look: Look, ctx: EngineContext): RuleResult? {
        val sc = ctx.scenario
        val parts = mutableListOf<Double>()
        if (sc.preferredStyles.isNotEmpty()) {
            val core = look.core.map { it.item }
            parts += core.map { i -> i.styleTags.maxOfOrNull { sc.preferredStyles[it] ?: 0.0 } ?: 0.0 }.average().coerceIn(0.0, 1.0)
        }
        if (sc.bonusSubcategories.isNotEmpty()) parts += if (look.items.any { it.subcategory in sc.bonusSubcategories }) 1.0 else 0.6
        if (parts.isEmpty()) return null
        val s = parts.average()
        return res("O2", G, s, 0.7, if (s >= 0.9) Explain.SCENARIO_STYLE else null, "scenario" to sc.name)
    }

    fun o3UserStyle(look: Look, ctx: EngineContext): RuleResult? {
        val w = ctx.settings.styleWeights
        val maxW = w.values.maxOrNull()?.takeIf { it > 0 } ?: return null
        val core = look.core.map { it.item }.filter { it.styleTags.isNotEmpty() }
        if (core.isEmpty()) return null
        val s = core.map { i -> i.styleTags.maxOf { (w[it] ?: 0.3) / maxW } }.average()
        return res("O3", G, s, 1.0, if (s >= 0.9) Explain.YOUR_STYLE else null)
    }

    fun o4Signature(look: Look): RuleResult? {
        val sig = look.items.firstOrNull { it.flags.signature } ?: return null
        return res("O4", G, 1.0, 0.3, Explain.SIGNATURE, "item" to sig.name.lowercase())
    }

    internal fun isCoreSlot(s: Slot) = s.isCore

    internal fun absd(x: Double) = abs(x)
}
