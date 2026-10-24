package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.model.CustomRule
import io.github.aristidisg.ensemble.model.ItemField
import io.github.aristidisg.ensemble.model.ItemPredicate
import io.github.aristidisg.ensemble.model.RuleAction

/** User IF/THEN rules (SPEC 5.10). Hard actions remove candidates; prefer/avoid adjust the score. */
object CustomRules {

    fun matches(p: Placed, preds: List<ItemPredicate>): Boolean = preds.isNotEmpty() && preds.all { pr ->
        val i = p.item
        when (pr.field) {
            ItemField.SLOT -> p.slot.name == pr.value
            ItemField.SUBCATEGORY -> i.subcategory.name == pr.value
            ItemField.COLOR_FAMILY -> i.primaryColor?.family?.name == pr.value
            ItemField.STYLE -> i.styleTags.any { it.name == pr.value }
            ItemField.PATTERN -> i.pattern.type.name == pr.value
            ItemField.MATERIAL -> i.materials.any { it.name == pr.value }
            ItemField.CUSTOM_TAG -> pr.value in i.meta.customTags
            ItemField.ITEM -> i.id == pr.value
        }
    }

    fun inScope(rule: CustomRule, ctx: EngineContext): Boolean {
        val s = rule.scope
        if (s.scenarioIds.isNotEmpty() && ctx.scenario.id !in s.scenarioIds) return false
        if (s.weekdays.isNotEmpty() && ctx.weekday !in s.weekdays) return false
        val t = ctx.weather.feelsMid
        if (s.minTemp != null && t < s.minTemp) return false
        if (s.maxTemp != null && t > s.maxTemp) return false
        return true
    }

    private fun has(look: Look, preds: List<ItemPredicate>) = look.placed.any { matches(it, preds) }

    /** True when the rule's condition holds for the outfit. */
    fun fires(rule: CustomRule, look: Look): Boolean = when (rule.action) {
        RuleAction.NEVER_TOGETHER -> has(look, rule.a) && has(look, rule.b)
        RuleAction.REQUIRE -> !has(look, rule.a)
        RuleAction.FORBID -> has(look, rule.a)
        RuleAction.IF_THEN_REQUIRE -> has(look, rule.a) && !has(look, rule.b)
        RuleAction.PREFER, RuleAction.AVOID -> has(look, rule.a) && (rule.b.isEmpty() || has(look, rule.b))
    }

    private fun active(ctx: EngineContext) = ctx.customRules.filter { it.enabled && inScope(it, ctx) }

    fun hardViolation(look: Look, ctx: EngineContext): Boolean =
        active(ctx).any { it.action.hard && fires(it, look) }

    /** Soft adjustments: +weight per firing PREFER rule, -weight per firing AVOID rule. */
    fun soft(look: Look, ctx: EngineContext): List<Pair<CustomRule, Double>> = active(ctx)
        .filter { !it.action.hard && fires(it, look) }
        .map { it to if (it.action == RuleAction.PREFER) it.weight else -it.weight }
}
