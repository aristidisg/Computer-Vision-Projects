package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.tables.StyleTables
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.engine.tables.TrouserKind
import io.github.aristidisg.ensemble.model.Activity
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ShoeSole
import io.github.aristidisg.ensemble.model.ShoeUpper
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Subcategory.*
import kotlin.math.abs

/** Shoe rules SH1-SH7 and accessory rules AC1-AC8 (SPEC 5.6, 5.7). */
object ShoeAccessoryRules {
    private val G = RuleGroup.SHOES_ACCESSORIES

    fun outdoorShare(ctx: EngineContext) = 1.0 - ctx.scenario.indoorShare

    fun shoes(look: Look, ctx: EngineContext): List<RuleResult> {
        val shoes = look.shoes ?: return emptyList()
        val out = mutableListOf<RuleResult>()
        val d = TaxonomyTable.of(shoes.subcategory)
        val name = shoes.subcategory.label.lowercase()
        // SH1 formality
        out += res("SH1", G, 1.0 - abs(shoes.formality - look.meanFormality) / 6.0, 1.0)
        // SH2 shoe x trouser table
        val bottom = look.bottom
        val tk = bottom?.let { TaxonomyTable.of(it.subcategory).trouserKind }
        if (d.shoeKind != null && tk != null) {
            val v = StyleTables.shoeTrouser(d.shoeKind, tk, shoes.subcategory == OXFORDS, ctx.scenario.id == "hiking", shoes.subcategory == HIKING_BOOTS)
            out += if (v >= 1.0) res("SH2", G, 1.0, 1.5, Explain.SHOE_FIT, "shoes" to shoes.subcategory.label)
            else res("SH2", G, v, 1.5, if (v == 0.0) Explain.SHOE_CLASH else null, "shoes" to shoes.subcategory.label, "trousers" to bottom.subcategory.label.lowercase())
        }
        // SH3 weather
        val w = ctx.weather
        val upper = shoes.shoe?.upper
        val outdoor = outdoorShare(ctx) > 0.0
        when {
            w.snow -> out += if (shoes.shoe?.sole == ShoeSole.LUG) res("SH3", G, 1.0, 1.5, Explain.SHOE_WEATHER, "shoes" to shoes.subcategory.label)
                else res("SH3", G, 0.2, 1.5, Explain.SHOE_WEATHER_BAD, "shoes" to shoes.subcategory.label)
            w.rainProbMax >= 50 && outdoor -> out += when {
                upper in setOf(ShoeUpper.SUEDE, ShoeUpper.CANVAS, ShoeUpper.KNIT) -> res("SH3", G, 0.15, 1.5, Explain.SHOE_WEATHER_BAD, "shoes" to shoes.subcategory.label)
                shoes.shoe?.weatherproof == true || shoes.shoe?.sole == ShoeSole.RUBBER || shoes.shoe?.sole == ShoeSole.LUG ->
                    res("SH3", G, 1.0, 1.5, Explain.SHOE_WEATHER, "shoes" to shoes.subcategory.label)
                else -> res("SH3", G, 0.6, 1.0)
            }
            w.feelsMax >= 28 -> out += when {
                shoes.subcategory.name.contains("BOOTS") -> res("SH3", G, 0.3, 1.0, Explain.SHOE_WEATHER_BAD, "shoes" to shoes.subcategory.label)
                upper == ShoeUpper.CANVAS || upper == ShoeUpper.KNIT || d.shoeKind?.name == "SANDALS" -> res("SH3", G, 1.0, 1.0)
                else -> res("SH3", G, 0.8, 0.5)
            }
        }
        // SH4 leather harmony with belt and bag
        ColorPairRules.c10Leather(look, ctx, "SH4", G)?.let { out += it.copy(weight = 0.5) }
        // SH5 rest days
        val rest = shoes.shoe?.restDays ?: 0
        val since = ctx.history.daysSinceItem(shoes.id, ctx.epochDay)
        if (rest > 0 && since != null && since in 1..rest) out += res("SH5", G, 0.2, 1.5, Explain.SHOE_REST, "shoes" to name)
        // SH6 shoes as dark as or darker than trousers at formality >= 6
        if (ctx.settings.shoesDarkerThanTrousers && ctx.scenario.formalityTarget >= 6 && bottom != null) {
            val exempt = shoes.subcategory == MINIMAL_SNEAKERS && shoes.primaryColor?.family == ColorFamily.WHITE
            val sl = look.dominant(shoes)?.l; val bl = look.dominant(bottom)?.l
            if (!exempt && sl != null && bl != null) out += if (sl <= bl + 0.02) res("SH6", G, 1.0, 0.7) else res("SH6", G, 0.4, 0.7, Explain.SHOE_DARKER)
        }
        // SH7 sock hint (explanation only, zero weight)
        val noShow = tk == TrouserKind.SHORTS || shoes.subcategory in setOf(LOAFERS, BOAT_SHOES, ESPADRILLES, MINIMAL_SNEAKERS) && ctx.weather.feelsMax >= 20
        out += res("SH7", G, 1.0, 0.0, if (noShow) Explain.SOCKS_NO_SHOW else Explain.SOCKS_MATCH)
        return out
    }

    fun accessories(look: Look, ctx: EngineContext): List<RuleResult> {
        if (ctx.settings.hideAccessories) return emptyList()
        val out = mutableListOf<RuleResult>()
        val sc = ctx.scenario
        val acc = look.accessories
        val f = sc.formalityTarget
        // AC1 budget
        val n = acc.size
        out += if (n in sc.accessoryMin..sc.accessoryMax) res("AC1", G, 1.0, 0.7)
        else res("AC1", G, 0.6, 0.7, Explain.ACCESSORY_COUNT, "n" to n, "scenario" to sc.name)
        val bySlot = acc.groupBy({ it.slot }, { it.item })
        // AC2 belt
        val bottom = look.bottom
        val loops = bottom?.let { TaxonomyTable.of(it.subcategory).beltLoops } ?: false
        val belt = bySlot[Slot.BELT]?.firstOrNull()
        when {
            belt != null && !loops -> out += res("AC2", G, 0.1, 2.0, Explain.BELT_WRONG)
            belt == null && loops && f >= 6 && look.top != null && (look.mid == null) -> out += res("AC2", G, 0.4, 1.0, Explain.BELT_MISSING)
            belt != null -> out += res("AC2", G, 1.0, 0.5)
        }
        // AC3 watch
        bySlot[Slot.WATCH]?.firstOrNull()?.let { w ->
            val s = when {
                f >= 7 -> if (w.subcategory == DRESS_WATCH) 1.0 else if (w.subcategory == EVERYDAY_WATCH) 0.7 else 0.3
                sc.activity == Activity.ACTIVE -> if (w.subcategory == SPORT_WATCH) 1.0 else 0.5
                else -> if (w.subcategory == DRESS_WATCH && f <= 3) 0.6 else 1.0
            }
            out += res("AC3", G, s, 0.7, if (s >= 1.0) Explain.WATCH_FIT else null)
        }
        // AC4 bag
        bySlot[Slot.BAG]?.firstOrNull()?.let { b ->
            val s = when {
                b.subcategory == BACKPACK && sc.id in setOf("university", "travel", "hiking") -> 1.0
                b.subcategory == BRIEFCASE && f >= 6 -> 1.0
                b.subcategory == BACKPACK && f >= 7 -> 0.3
                b.subcategory == BRIEFCASE && f <= 3 -> 0.4
                else -> 0.8
            }
            out += res("AC4", G, s, 0.7)
        }
        // AC5 headwear
        val hats = bySlot[Slot.HEADWEAR].orEmpty()
        if (hats.isNotEmpty()) {
            val h = hats.first()
            val streety = look.items.count { StyleTag.STREETWEAR in it.styleTags || StyleTag.SPORTY in it.styleTags } >= 2
            val s = when {
                hats.size > 1 -> 0.0
                f >= 7 -> 0.1
                h.subcategory == BEANIE -> if (ctx.weather.feelsMin <= 10) 1.0 else if (ctx.weather.feelsMin > 15) 0.3 else 0.7
                h.subcategory == CAP -> if (ctx.weather.uvMax >= 6 || streety) 1.0 else 0.6
                else -> 0.7
            }
            out += res("AC5", G, s, 1.0, if (f >= 7) Explain.HEADWEAR_OFF else null)
        }
        // AC6 sunglasses
        bySlot[Slot.EYEWEAR]?.firstOrNull()?.let {
            val s = when { ctx.weather.dark -> 0.2; ctx.weather.uvMax >= 5 && outdoorShare(ctx) > 0 -> 1.0; else -> 0.6 }
            out += res("AC6", G, s, 0.7, if (s >= 1.0) Explain.SUN_READY else null)
        }
        // AC7 scarf
        bySlot[Slot.NECK]?.firstOrNull { it.subcategory == SCARF }?.let {
            val s = when { ctx.weather.feelsMin <= 8 || ctx.weather.windMax >= 30 -> 1.0; ctx.weather.feelsMin > 15 -> 0.2; else -> 0.6 }
            out += res("AC7", G, s, 1.0, if (s >= 1.0) Explain.SCARF_FIT else null)
        }
        // AC8 jewelry
        val jewelry = bySlot[Slot.JEWELRY].orEmpty()
        if (jewelry.isNotEmpty()) {
            val lead = FormalityRules.leadingStyle(look)
            val s = when {
                lead == StyleTag.STREETWEAR -> 1.0
                (lead == StyleTag.MINIMAL || lead == StyleTag.CLASSIC) && jewelry.size > 2 -> 0.3
                jewelry.any { it.subcategory == NECKLACE } && lead == StyleTag.CLASSIC -> 0.6
                else -> 0.9
            }
            out += res("AC8", G, s, 0.5)
        }
        return out
    }
}
