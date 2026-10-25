package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.engine.tables.TrouserKind
import io.github.aristidisg.ensemble.model.FabricWeight
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.OuterWeight
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.ShoeUpper
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Subcategory

/** Why a candidate was ruled out. */
enum class Violation {
    UNAVAILABLE, HIDDEN, STRUCTURE, LAYERING, WEATHER, SCENARIO, COOLDOWN, NEVER_AGAIN, NEVER_SUGGEST, PERSONAL_NEVER, CUSTOM, ROTATION
}

/** Hard constraints (SPEC 5.1): never broken unless the user taps "use anyway". */
object HardConstraints {

    /** Item-level checks, applied when building per-slot pools. Returns null when the item may be used. */
    fun itemViolation(item: Item, slot: Slot, ctx: EngineContext): Violation? {
        if (slot !in item.slots) return Violation.STRUCTURE
        if (item.flags.neverSuggest) return Violation.NEVER_SUGGEST
        if (!item.isAvailable) return Violation.UNAVAILABLE
        if (ctx.prefs.isItemHidden(item.id, ctx.scenario.id, ctx.epochDay)) return Violation.HIDDEN
        val pc = ctx.settings.personalColor
        if (pc.enabled && item.primaryColor?.family in pc.never && PersonalColorData.appliesTo(slot, pc.applyAllSlots)) return Violation.PERSONAL_NEVER
        weatherViolation(item, slot, ctx)?.let { return it }
        scenarioViolation(item, slot, ctx)?.let { return it }
        if (RotationRules.strictBlocked(item, slot, ctx)) return Violation.ROTATION
        return null
    }

    /**
     * Why a piece can't be suggested in this context at all (for "Why isn't this suggested?"),
     * or null when at least one of its slots is open to it.
     */
    fun whyNotSuggested(item: Item, ctx: EngineContext): Violation? {
        val verdicts = item.slots.map { itemViolation(item, it, ctx) }
        return if (verdicts.any { it == null }) null else verdicts.firstOrNull()
    }

    private fun weatherViolation(item: Item, slot: Slot, ctx: EngineContext): Violation? {
        val w = ctx.weather
        val d = TaxonomyTable.of(item.subcategory)
        if (d.trouserKind == TrouserKind.SHORTS || item.subcategory == Subcategory.ATHLETIC_SHORTS || item.subcategory == Subcategory.SWIM_SHORTS) {
            if (w.feelsMax < ctx.settings.shortsMinTemp && ctx.scenario.indoorShare < 1.0) return Violation.WEATHER
        }
        if (item.subcategory == Subcategory.SANDALS && w.feelsMax < 22.0) return Violation.WEATHER
        if (w.feelsMin > 26.0) {
            if (item.subcategory == Subcategory.CHUNKY_KNIT || (slot == Slot.OUTER && d.outer == OuterWeight.WARM)) return Violation.WEATHER
            if (item.fabricWeight == FabricWeight.HEAVY && (slot == Slot.MID || slot == Slot.OUTER)) return Violation.WEATHER
        }
        val outdoor = 1.0 - ctx.scenario.indoorShare
        if (slot == Slot.SHOES && w.rainSum >= 5.0 && outdoor > 0.3) {
            val upper = item.shoe?.upper
            if (upper == ShoeUpper.SUEDE || upper == ShoeUpper.CANVAS || Material.SUEDE in item.materials) return Violation.WEATHER
        }
        // Gloves and beanies only make sense in the cold.
        d.maxFeels?.let { if (w.feelsMin > it + 5) return Violation.WEATHER }
        return null
    }

    private fun scenarioViolation(item: Item, slot: Slot, ctx: EngineContext): Violation? {
        val sc = ctx.scenario
        if (item.subcategory in sc.forbiddenSubcategories) return Violation.SCENARIO
        if (slot.isCore) {
            val lo = sc.formalityMin - 1 + minOf(0.0, ctx.formalityOffset)
            val hi = sc.formalityMax + 1 + maxOf(0.0, ctx.formalityOffset)
            // Warm coats in the cold may be more casual than the scenario (a parka over a suit is fine).
            val warmOuter = slot == Slot.OUTER && TaxonomyTable.of(item.subcategory).outer == OuterWeight.WARM && ctx.weather.feelsMin < 8
            if ((item.formality < lo || item.formality > hi) && !warmOuter) return Violation.SCENARIO
        }
        if (item.subcategory == Subcategory.SWIM_SHORTS && !sc.allowsSwimwear) return Violation.SCENARIO
        if (item.subcategory == Subcategory.SLIDES && !sc.allowsSlides) return Violation.SCENARIO
        if (sc.requiredTags.isNotEmpty() && (slot == Slot.TOP || slot == Slot.BOTTOM) && item.styleTags.none { it in sc.requiredTags || it == StyleTag.OUTDOOR }) return Violation.SCENARIO
        if (item.pattern.loudness > sc.maxLoudness && slot.isCore) return Violation.SCENARIO
        if (!sc.allowsLogos && (item.pattern.type == PatternType.LOGO || item.pattern.type == PatternType.GRAPHIC)) return Violation.SCENARIO
        if (sc.forbiddenTags.isNotEmpty() && item.styleTags.isNotEmpty() && item.styleTags.all { it in sc.forbiddenTags }) return Violation.SCENARIO
        return null
    }

    /** Outfit-level checks on a complete core outfit. */
    fun outfitViolation(look: Look, ctx: EngineContext, ignoreCooldown: Boolean = false): Violation? {
        val sc = ctx.scenario
        // Structure: top + bottom + shoes (shoes optional at home); one item per core slot.
        if (look.top == null || look.bottom == null) return Violation.STRUCTURE
        if (look.shoes == null && !sc.shoesOptional) return Violation.STRUCTURE
        if (look.core.groupBy { it.slot }.any { it.value.size > 1 }) return Violation.STRUCTURE
        if (look.accessories.groupBy { it.slot }.any { (s, v) -> v.size > (if (s == Slot.JEWELRY) 2 else 1) }) return Violation.STRUCTURE
        if (look.items.map { it.id }.toSet().size != look.items.size) return Violation.STRUCTURE
        // Layering: the mid layer's bulk must fit the outer; no two outer layers.
        val mid = look.mid; val outer = look.outer
        if (mid != null && outer != null) {
            val cap = when (TaxonomyTable.of(outer.subcategory).outer) { OuterWeight.WARM -> 3; OuterWeight.STANDARD -> 2; else -> 2 }
            if (mid.layering.bulk > cap) return Violation.LAYERING
            val midAsOuter = TaxonomyTable.of(mid.subcategory).outer
            if (midAsOuter != OuterWeight.NONE && TaxonomyTable.of(outer.subcategory).outer == OuterWeight.LIGHT &&
                mid.subcategory !in setOf(Subcategory.OVERSHIRT, Subcategory.GILET, Subcategory.FLEECE)) return Violation.LAYERING
        }
        if (look.top != null && !look.top.layering.presentableAlone && mid == null && outer == null) return Violation.LAYERING
        // Scenario requirements
        if (sc.requiredSubcategories.isNotEmpty() && look.items.none { it.subcategory in sc.requiredSubcategories }) return Violation.SCENARIO
        if (sc.requiredSlots.any { it !in look.bySlot }) return Violation.SCENARIO
        // Cooldowns and blocks
        val sig = look.signature
        if (sig in ctx.neverAgain || sig in ctx.prefs.blocked) return Violation.NEVER_AGAIN
        if (ctx.prefs.isOutfitHidden(sig, ctx.epochDay)) return Violation.HIDDEN
        if (!ignoreCooldown && sig !in ctx.cooldownOverrides) {
            val cd = ctx.outfitCooldowns[sig] ?: ctx.settings.outfitCooldownDays
            val since = ctx.history.daysSinceOutfit(sig, ctx.epochDay)
            if (since != null && since in 0 until cd) return Violation.COOLDOWN
        }
        if (CustomRules.hardViolation(look, ctx)) return Violation.CUSTOM
        return null
    }

    /** Days until a worn outfit is back, or null when it is not cooling down. */
    fun cooldownRemaining(signature: String, ctx: EngineContext): Long? {
        val cd = ctx.outfitCooldowns[signature] ?: ctx.settings.outfitCooldownDays
        val since = ctx.history.daysSinceOutfit(signature, ctx.epochDay) ?: return null
        return (cd - since).takeIf { it > 0 }
    }
}
