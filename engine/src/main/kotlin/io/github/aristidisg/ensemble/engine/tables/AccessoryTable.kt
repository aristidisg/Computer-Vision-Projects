package io.github.aristidisg.ensemble.engine.tables

import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.StyleTag.*
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.Subcategory.*

/** Accessory rows of Appendix A. "any" style is every tag at once. */
object AccessoryTable {
    private val ANY = StyleTag.entries.toSet()

    private fun acc(
        sub: Subcategory, slot: Slot, formality: Int, tags: Set<StyleTag>,
        clo: Double = 0.0, materials: List<Material> = emptyList(), minFeels: Double? = null, maxFeels: Double? = null,
    ) = SubcategoryDefaults(sub, listOf(slot), formality, clo, tags, 0, materials = materials, minFeels = minFeels, maxFeels = maxFeels)

    val rows: List<SubcategoryDefaults> = listOf(
        acc(LEATHER_BELT, Slot.BELT, 6, setOf(CLASSIC, SMART_CASUAL), materials = listOf(Material.LEATHER)),
        acc(CASUAL_BELT, Slot.BELT, 3, setOf(RUGGED, STREETWEAR), materials = listOf(Material.CANVAS)),
        acc(DRESS_WATCH, Slot.WATCH, 8, setOf(CLASSIC)),
        acc(EVERYDAY_WATCH, Slot.WATCH, 5, setOf(CLASSIC, SPORTY, MINIMAL)),
        acc(SPORT_WATCH, Slot.WATCH, 2, setOf(SPORTY)),
        acc(BACKPACK, Slot.BAG, 2, setOf(STREETWEAR, SPORTY, MINIMAL)),
        acc(TOTE, Slot.BAG, 3, setOf(MINIMAL)),
        acc(CROSSBODY, Slot.BAG, 2, setOf(STREETWEAR)),
        acc(BRIEFCASE, Slot.BAG, 7, setOf(CLASSIC), materials = listOf(Material.LEATHER)),
        acc(CAP, Slot.HEADWEAR, 2, setOf(STREETWEAR, SPORTY)),
        acc(BEANIE, Slot.HEADWEAR, 2, setOf(STREETWEAR, MINIMAL, RUGGED), clo = 0.03, materials = listOf(Material.KNIT), maxFeels = 10.0),
        acc(BUCKET_HAT, Slot.HEADWEAR, 2, setOf(STREETWEAR)),
        acc(SUNGLASSES, Slot.EYEWEAR, 5, ANY),
        acc(SCARF, Slot.NECK, 5, setOf(CLASSIC, MINIMAL), clo = 0.05, materials = listOf(Material.WOOL)),
        acc(TIE, Slot.NECK, 9, setOf(CLASSIC)),
        acc(POCKET_SQUARE, Slot.POCKET, 8, setOf(CLASSIC)),
        acc(NECKLACE, Slot.JEWELRY, 3, setOf(STREETWEAR)),
        acc(BRACELET, Slot.JEWELRY, 3, ANY),
        acc(RING, Slot.JEWELRY, 4, ANY),
        acc(GLOVES, Slot.HANDS, 5, ANY, clo = 0.05, maxFeels = 5.0),
    )
}
