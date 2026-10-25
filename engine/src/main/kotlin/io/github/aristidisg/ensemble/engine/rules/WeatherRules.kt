package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.engine.tables.WarmthTable
import io.github.aristidisg.ensemble.model.Activity
import io.github.aristidisg.ensemble.model.FabricWeight
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.ShoeSole
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.WaterResistance
import kotlin.math.abs
import kotlin.math.roundToInt

/** Weather and comfort rules W1-W7 (SPEC 5.8) and the ISO 9920 clo model. */
object WeatherRules {
    private val G = RuleGroup.WEATHER

    /** Ensemble insulation I_cl = 0.835 × Σ clo + 0.161 (ISO 9920 summation). */
    fun ensembleClo(items: List<Item>): Double {
        val sum = items.sumOf { it.warmthClo }
        return if (sum <= 0.0) 0.0 else 0.835 * sum + 0.161
    }

    fun activityShift(a: Activity) = when (a) { Activity.SEDENTARY -> 0.2; Activity.LIGHT -> 0.0; Activity.ACTIVE -> -0.2 }

    /** Outdoor clo target band for an effective feels-like temperature. */
    fun outdoorTarget(feels: Double, ctx: EngineContext): ClosedFloatingPointRange<Double> {
        val band = WarmthTable.band(feels)
        val shift = activityShift(ctx.scenario.activity)
        return (band.targetMin + shift)..(band.targetMax + shift)
    }

    /** Indoor target: 0.5 clo at 25 °C rising linearly to 1.0 at 20 °C, ±0.15. */
    fun indoorTarget(temp: Double): ClosedFloatingPointRange<Double> {
        val c = (0.5 + (25.0 - temp) * 0.1).coerceIn(0.4, 1.2)
        return (c - 0.15)..(c + 0.15)
    }

    private fun bandScore(v: Double, r: ClosedFloatingPointRange<Double>): Double = when {
        v in r -> 1.0
        v < r.start -> 1.0 - (r.start - v) / 0.45
        else -> 1.0 - (v - r.endInclusive) / 0.45
    }.coerceIn(0.0, 1.0)

    fun w1Warmth(look: Look, ctx: EngineContext): RuleResult {
        val w = ctx.weather
        // Thermal offset moves the temperature axis: running cold (+) means it feels colder.
        val coldest = w.feelsMin - ctx.thermalOffset
        val warmest = w.feelsMax - ctx.thermalOffset
        val outdoorClo = ensembleClo(look.items)
        val target = outdoorTarget(coldest, ctx)
        val outdoorCold = bandScore(outdoorClo, target)
        // Heat at the warm end matters too: penalize being far above the warm-end target.
        val warmTarget = outdoorTarget(warmest, ctx)
        val outdoorHot = if (outdoorClo > warmTarget.endInclusive + 0.5 && look.outer == null) bandScore(outdoorClo, warmTarget) else 1.0
        val outdoor = minOf(outdoorCold, 0.5 + 0.5 * outdoorHot)
        val indoorItems = look.items.filter { it.id != look.outer?.id && TaxonomyTable.of(it.subcategory).slots.first() != Slot.HANDS }
        val indoor = bandScore(ensembleClo(indoorItems), indoorTarget(ctx.indoorTemp - ctx.thermalOffset))
        val share = ctx.scenario.indoorShare
        val s = share * indoor + (1 - share) * outdoor
        val minT = w.feelsMin.roundToInt(); val maxT = w.feelsMax.roundToInt()
        return when {
            s >= 0.8 -> res("W1", G, s, 2.0, Explain.WARMTH_OK, "min" to minT, "max" to maxT)
            outdoorClo < target.start -> res("W1", G, s, 2.0, Explain.TOO_COLD, "min" to minT)
            else -> res("W1", G, s, 2.0, Explain.TOO_WARM, "max" to maxT)
        }
    }

    /** A layer that can come off: an outer or a mid that leaves a presentable top. */
    fun removableLayer(look: Look): Item? {
        val base = look.top ?: return null
        if (!base.layering.presentableAlone) return null
        return look.outer ?: look.mid?.takeIf { it.layering.canWearOpen || it.slots.contains(Slot.MID) }
    }

    fun w2Swing(look: Look, ctx: EngineContext): RuleResult? {
        val w = ctx.weather
        if (w.swing < 8.0) return null
        val layer = removableLayer(look)
        return if (layer != null) res("W2", G, 1.0, 1.0, Explain.WEATHER_LAYER, "min" to w.feelsMin.roundToInt(), "max" to w.feelsMax.roundToInt(), "item" to layer.subcategory.label.lowercase())
        else res("W2", G, 0.6, 1.0)
    }

    fun w3Rain(look: Look, ctx: EngineContext): RuleResult? {
        val w = ctx.weather
        if (w.rainProbMax < 50 && w.rainSum < 1.0) return null
        if (ctx.scenario.indoorShare >= 1.0) return null
        val pct = w.rainProbMax.roundToInt()
        val outer = look.outer
        var s = when {
            outer == null -> 0.4
            outer.waterResistance != WaterResistance.NONE -> 1.0
            Material.SUEDE in outer.materials -> 0.1
            else -> 0.55
        }
        val lightTrousers = look.dominant(look.bottom)?.let { it.l > 0.75 } ?: false
        if (lightTrousers) s -= 0.15
        return if (outer != null && outer.waterResistance != WaterResistance.NONE)
            res("W3", G, s, 1.5, Explain.RAIN, "pct" to pct, "item" to outer.subcategory.label.lowercase())
        else res("W3", G, s, 1.5, Explain.RAIN_UNPROTECTED, "pct" to pct)
    }

    fun w4Wind(look: Look, ctx: EngineContext): RuleResult? {
        if (ctx.weather.windMax < 30) return null
        val outer = look.outer
        val windproof = outer != null && (outer.waterResistance != WaterResistance.NONE || outer.materials.any { it == Material.NYLON_TECH || it == Material.LEATHER })
        return if (windproof) res("W4", G, 1.0, 1.0, Explain.WIND) else res("W4", G, 0.6, 1.0)
    }

    fun w5Sun(look: Look, ctx: EngineContext): RuleResult? {
        if (ctx.weather.uvMax < 6 || ctx.scenario.indoorShare >= 0.9) return null
        var hits = 0
        if (look.items.any { it.subcategory == Subcategory.CAP }) hits++
        if (look.items.any { it.subcategory == Subcategory.SUNGLASSES }) hits++
        if ((look.dominant(look.top)?.l ?: 0.0) > 0.7) hits++
        if (look.core.any { it.item.materials.any { m -> m == Material.LINEN || m == Material.COTTON } }) hits++
        return res("W5", G, 0.6 + 0.1 * hits, 0.7, if (hits >= 2) Explain.SUN_READY else null)
    }

    fun w6Heat(look: Look, ctx: EngineContext): RuleResult? {
        if (ctx.weather.feelsMax < 28) return null
        val core = look.core.map { it.item }
        val breathable = core.count { it.materials.any { m -> m == Material.LINEN || m == Material.COTTON || m == Material.SEERSUCKER } }
        val bad = core.count { (it.fabricWeight == FabricWeight.HEAVY) || (look.dominant(it)?.l ?: 1.0) < 0.3 && it.slot() != Slot.SHOES || Material.NYLON_TECH in it.materials && it.slot() != Slot.SHOES }
        val s = (0.6 + 0.15 * breathable - 0.25 * bad).coerceIn(0.0, 1.0)
        return res("W6", G, s, 1.0, if (s >= 0.85) Explain.HEAT else null)
    }

    private fun Item.slot() = slots.first()

    fun w7Snow(look: Look, ctx: EngineContext): RuleResult? {
        if (!ctx.weather.snow) return null
        val grip = look.shoes?.shoe?.let { it.sole == ShoeSole.LUG || it.weatherproof } ?: false
        return if (grip) res("W7", G, 1.0, 1.5, Explain.SNOW_BOOTS) else res("W7", G, 0.2, 1.5)
    }

    internal fun absd(x: Double) = abs(x)
}
