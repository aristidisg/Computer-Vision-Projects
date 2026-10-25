package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory

/** Rotation and cooldown rules R1-R7 (SPEC 5.9). R2 (exact) and R3 are hard constraints. */
object RotationRules {
    private val G = RuleGroup.ROTATION

    fun rotationGroup(item: Item, slot: Slot): String = when (slot) {
        Slot.TOP -> "BASE_TOP"
        Slot.MID -> "MID"
        Slot.BOTTOM -> if (item.subcategory == Subcategory.JEANS) "JEANS" else "TROUSERS"
        Slot.OUTER -> "OUTER"
        Slot.SHOES -> "SHOES"
        else -> "ACCESSORY"
    }

    fun window(item: Item, slot: Slot, ctx: EngineContext): Int {
        val g = rotationGroup(item, slot)
        return (ctx.settings.rotationDays[g] ?: 0) + (ctx.prefs.rotationExtra[g] ?: 0)
    }

    /** True when strict rotation is on and the piece is still inside its window (a hard block). */
    fun strictBlocked(item: Item, slot: Slot, ctx: EngineContext): Boolean {
        if (!ctx.settings.strictRotation) return false
        val w = window(item, slot, ctx)
        val since = ctx.history.daysSinceItem(item.id, ctx.epochDay) ?: return false
        return w > 0 && since in 1 until w
    }

    fun r1ItemRotation(look: Look, ctx: EngineContext): RuleResult? {
        var worst = 1.0; var worstItem: Item? = null; var worstDays = 0L
        val scores = look.placed.mapNotNull { p ->
            val w = window(p.item, p.slot, ctx)
            if (w <= 0) return@mapNotNull null
            val since = ctx.history.daysSinceItem(p.item.id, ctx.epochDay) ?: return@mapNotNull 1.0
            val s = if (since >= w || since <= 0) 1.0 else 1.0 - (w - since).toDouble() / w
            if (s < worst) { worst = s; worstItem = p.item; worstDays = since }
            s
        }
        if (scores.isEmpty()) return null
        val mean = scores.average()
        return if (worstItem != null && worst < 0.7) res("R1", G, mean, 1.5, Explain.ROTATION, "item" to worstItem!!.name.lowercase(), "n" to worstDays)
        else res("R1", G, mean, 1.5)
    }

    fun r2NearDuplicate(look: Look, ctx: EngineContext): RuleResult? {
        val coreIds = look.core.map { it.item.id }.toSet()
        if (coreIds.size < 3) return null
        val window = ctx.settings.outfitCooldownDays
        val near = ctx.history.recent(ctx.epochDay, window).any { log ->
            log.outfitSignature != look.signature && log.pieces.count { it.itemId in coreIds && it.slot.isCore } >= 3
        }
        return if (near) res("R2", G, 0.5, 1.0, Explain.NEAR_DUPLICATE) else null
    }

    fun r4Freshness(look: Look, ctx: EngineContext): RuleResult? =
        if (look.signature in ctx.recentDeckSignatures) res("R4", G, 0.5, 1.0, Explain.SEEN_RECENTLY) else null

    fun r5Forgotten(look: Look, ctx: EngineContext): RuleResult? {
        if (!ctx.settings.forgottenLove) return null
        val forgotten = look.core.map { it.item }.mapNotNull { i ->
            val since = ctx.history.daysSinceItem(i.id, ctx.epochDay) ?: (ctx.epochDay - i.createdEpochDay).takeIf { i.createdEpochDay > 0 }
            if (since != null && since >= 30) i to since else null
        }.maxByOrNull { it.second } ?: return null
        return res("R5", G, 1.0, 0.5, Explain.FORGOTTEN, "item" to forgotten.first.name.lowercase(), "n" to forgotten.second)
    }

    fun r6PlanConflicts(look: Look, ctx: EngineContext): RuleResult? {
        val hit = look.items.firstOrNull { it.id in ctx.plannedSoon && it.slots.first().isCore } ?: return null
        return res("R6", G, 0.4, 1.0, Explain.PLANNED_SOON, "item" to hit.name.lowercase())
    }

    fun r7Laundry(look: Look): RuleResult? {
        val due = look.items.firstOrNull { it.laundry.atThreshold } ?: return null
        return res("R7", G, 0.7, 0.7, Explain.LAUNDRY_DUE, "item" to due.name.lowercase())
    }
}
