package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.CategoryGroup
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory

/** Why a piece looks mislabeled. */
enum class LabelIssue { NAME, MATERIAL, PHOTO, SLOTS }

/** A likely mislabel: the group it probably belongs to and, when clear, the exact subcategory. */
data class LabelSuggestion(val item: Item, val issue: LabelIssue, val target: CategoryGroup, val suggested: Subcategory?)

/**
 * Finds tops, mid layers and outerwear that look filed in the wrong place. Every check is a
 * table or a plain comparison; nothing is changed until the user taps the fix.
 */
object LabelCheck {

    /** Words in a typed name and what they usually mean, longest first. */
    val nameHints: List<Pair<String, Subcategory>> = listOf(
        "leather jacket" to Subcategory.LEATHER_JACKET, "suede jacket" to Subcategory.SUEDE_JACKET,
        "denim jacket" to Subcategory.DENIM_JACKET, "jean jacket" to Subcategory.DENIM_JACKET,
        "field jacket" to Subcategory.FIELD_JACKET, "chore jacket" to Subcategory.CHORE_JACKET,
        "suit jacket" to Subcategory.SUIT_JACKET, "sport coat" to Subcategory.SPORT_COAT,
        "rain jacket" to Subcategory.RAIN_SHELL, "puffer vest" to Subcategory.GILET,
        "quarter zip" to Subcategory.QUARTER_ZIP, "quarter-zip" to Subcategory.QUARTER_ZIP,
        "sweater vest" to Subcategory.SWEATER_VEST, "sweatshirt" to Subcategory.CREWNECK_SWEATSHIRT,
        "turtleneck" to Subcategory.TURTLENECK, "roll neck" to Subcategory.TURTLENECK,
        "overshirt" to Subcategory.OVERSHIRT, "shacket" to Subcategory.OVERSHIRT,
        "cardigan" to Subcategory.CARDIGAN, "hoodie" to Subcategory.HOODIE, "fleece" to Subcategory.FLEECE,
        "gilet" to Subcategory.GILET, "jumper" to Subcategory.MERINO_CREWNECK, "sweater" to Subcategory.MERINO_CREWNECK,
        "pullover" to Subcategory.MERINO_CREWNECK, "bomber" to Subcategory.BOMBER, "harrington" to Subcategory.HARRINGTON,
        "varsity" to Subcategory.VARSITY_JACKET, "trench" to Subcategory.TRENCH_COAT, "peacoat" to Subcategory.PEACOAT,
        "pea coat" to Subcategory.PEACOAT, "overcoat" to Subcategory.WOOL_OVERCOAT, "parka" to Subcategory.PARKA,
        "puffer" to Subcategory.PUFFER_JACKET, "blazer" to Subcategory.UNSTRUCTURED_BLAZER,
    ).sortedByDescending { it.first.length }

    /** Words that only say "this is outerwear". */
    val genericOuter: List<String> = listOf("jacket", "coat", "anorak", "windbreaker")

    private val knitOnly = setOf(Material.KNIT, Material.MERINO, Material.CASHMERE, Material.JERSEY)
    private val shell = setOf(Material.LEATHER, Material.SUEDE, Material.NYLON_TECH)
    private val layers = setOf(CategoryGroup.TOPS, CategoryGroup.OUTERWEAR)

    private fun has(name: String, word: String) = Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(name)

    /** What a typed name points to: an exact subcategory, or just the outerwear group. */
    fun hintFromName(name: String): Pair<CategoryGroup, Subcategory?>? {
        val n = name.lowercase()
        nameHints.firstOrNull { (word, _) -> has(n, word) }?.let { return it.second.group to it.second }
        return if (genericOuter.any { has(n, it) }) CategoryGroup.OUTERWEAR to null else null
    }

    private fun isMidOnly(sub: Subcategory) = TaxonomyTable.of(sub).slots.let { Slot.MID in it && Slot.OUTER !in it }

    /** One suggestion per piece at most, strongest signal first. [photoHints] come from ML Kit, if on. */
    fun check(items: List<Item>, photoHints: Map<String, Subcategory> = emptyMap()): List<LabelSuggestion> =
        items.filter { it.subcategory.group in layers }.mapNotNull { i -> suggestion(i, photoHints[i.id]) }

    fun suggestion(i: Item, photo: Subcategory? = null): LabelSuggestion? {
        val group = i.subcategory.group
        if (i.name != ItemFactory.autoName(i.subcategory, i.colors)) {
            val named = hintFromName(i.name)
            if (named != null && named.first != group) return LabelSuggestion(i, LabelIssue.NAME, named.first, named.second)
        }
        if (group == CategoryGroup.OUTERWEAR && i.materials.isNotEmpty() && i.materials.all { it in knitOnly }) {
            return LabelSuggestion(i, LabelIssue.MATERIAL, CategoryGroup.TOPS, Subcategory.CARDIGAN.takeIf { i.layering.canWearOpen } ?: Subcategory.MERINO_CREWNECK)
        }
        if (isMidOnly(i.subcategory) && i.materials.any { it in shell }) return LabelSuggestion(i, LabelIssue.MATERIAL, CategoryGroup.OUTERWEAR, null)
        // Overshirts, gilets and fleeces already work as outerwear; a photo saying "jacket" is no news.
        val alreadyOuter = photo?.group == CategoryGroup.OUTERWEAR && Slot.OUTER in i.slots
        if (photo != null && photo.group in layers && photo.group != group && !alreadyOuter) return LabelSuggestion(i, LabelIssue.PHOTO, photo.group, photo)
        if (i.slots.toSet() != TaxonomyTable.of(i.subcategory).slots.toSet()) return LabelSuggestion(i, LabelIssue.SLOTS, group, i.subcategory)
        return null
    }

    /** Applies a fix: a new subcategory, or default slots when the subcategory stays. */
    fun fix(s: LabelSuggestion, sub: Subcategory? = s.suggested): Item? = when {
        sub == null -> null
        sub == s.item.subcategory -> ItemFactory.resetSlots(s.item)
        else -> ItemFactory.recategorize(s.item, sub)
    }
}
