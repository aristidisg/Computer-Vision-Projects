package io.github.aristidisg.ensemble.engine.tables

import io.github.aristidisg.ensemble.model.FabricWeight
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.Material.*
import io.github.aristidisg.ensemble.model.OuterWeight
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.ShoeExtras
import io.github.aristidisg.ensemble.model.ShoeProfile
import io.github.aristidisg.ensemble.model.ShoeSole
import io.github.aristidisg.ensemble.model.ShoeUpper
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Slot.*
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.StyleTag.*
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.Subcategory.*
import io.github.aristidisg.ensemble.model.WaterResistance

/** Trouser columns of the shoe x trouser table (B4). */
enum class TrouserKind { JEANS, CHINOS, TAILORED, LINEN, CASUAL, SHORTS, ATHLETIC }

/** Shoe rows of the shoe x trouser table (B4). */
enum class ShoeKind { MINIMAL_SNEAKERS, CHUNKY_RUNNING, CANVAS, LOAFERS, DRESS, CHELSEA_CHUKKA, BOOTS, SANDALS, BOAT }

/** One Appendix A row: everything picking a subcategory pre-fills. */
data class SubcategoryDefaults(
    val sub: Subcategory,
    val slots: List<Slot>,
    val formality: Int,
    val clo: Double,
    val tags: Set<StyleTag>,
    val washAfter: Int,
    val outer: OuterWeight = OuterWeight.NONE,
    val tailored: Boolean = false,
    val canWearOpen: Boolean = false,
    val presentableAlone: Boolean = true,
    val bulk: Int = 1,
    val materials: List<Material> = listOf(COTTON),
    val weight: FabricWeight = FabricWeight.MID,
    val water: WaterResistance = WaterResistance.NONE,
    val shoe: ShoeExtras? = null,
    val shoeKind: ShoeKind? = null,
    val trouserKind: TrouserKind? = null,
    val beltLoops: Boolean = false,
    val seasons: Set<Season>? = null,
    val collared: Boolean = false,
    val graphic: Boolean = false,
    val minFeels: Double? = null,
    val maxFeels: Double? = null,
)

/** Appendix A as data. */
object TaxonomyTable {
    private val SUMMER = setOf(Season.SPRING, Season.SUMMER)
    private val WINTER = setOf(Season.AUTUMN, Season.WINTER)

    private fun shoe(p: ShoeProfile, u: ShoeUpper, s: ShoeSole, wp: Boolean = false, rest: Int = 0) = ShoeExtras(p, u, s, wp, rest)

    val rows: List<SubcategoryDefaults> = listOf(
        // Tops and mid layers
        SubcategoryDefaults(T_SHIRT, listOf(TOP), 2, 0.08, setOf(MINIMAL, STREETWEAR, SPORTY), 1, materials = listOf(COTTON, JERSEY), weight = FabricWeight.LIGHT),
        SubcategoryDefaults(GRAPHIC_TEE, listOf(TOP), 1, 0.08, setOf(STREETWEAR), 1, materials = listOf(COTTON, JERSEY), weight = FabricWeight.LIGHT, graphic = true),
        SubcategoryDefaults(LONG_SLEEVE_TEE, listOf(TOP), 2, 0.12, setOf(MINIMAL, STREETWEAR), 1, materials = listOf(COTTON, JERSEY)),
        SubcategoryDefaults(TANK_TOP, listOf(TOP), 1, 0.06, setOf(SPORTY), 1, weight = FabricWeight.LIGHT, seasons = SUMMER),
        SubcategoryDefaults(HENLEY, listOf(TOP), 3, 0.15, setOf(MINIMAL, RUGGED), 1),
        SubcategoryDefaults(POLO, listOf(TOP), 4, 0.14, setOf(CLASSIC, SPORTY, PREPPY), 1, collared = true),
        SubcategoryDefaults(KNIT_POLO, listOf(TOP), 5, 0.17, setOf(CLASSIC, MINIMAL), 2, materials = listOf(KNIT), collared = true),
        SubcategoryDefaults(OXFORD_SHIRT, listOf(TOP), 5, 0.25, setOf(CLASSIC, SMART_CASUAL, PREPPY), 2, canWearOpen = true, collared = true),
        SubcategoryDefaults(DRESS_SHIRT, listOf(TOP), 7, 0.25, setOf(CLASSIC), 1, collared = true),
        SubcategoryDefaults(LINEN_SHIRT, listOf(TOP), 4, 0.18, setOf(CLASSIC, RESORT), 1, materials = listOf(LINEN), weight = FabricWeight.LIGHT, seasons = SUMMER, collared = true),
        SubcategoryDefaults(CAMP_COLLAR_SHIRT, listOf(TOP), 3, 0.15, setOf(STREETWEAR, RESORT), 1, weight = FabricWeight.LIGHT, collared = true),
        SubcategoryDefaults(FLANNEL_SHIRT, listOf(TOP, MID), 3, 0.30, setOf(RUGGED), 2, canWearOpen = true, materials = listOf(FLANNEL), weight = FabricWeight.HEAVY, seasons = WINTER, collared = true),
        SubcategoryDefaults(OVERSHIRT, listOf(MID, OUTER), 4, 0.30, setOf(MINIMAL, RUGGED, SMART_CASUAL), 4, outer = OuterWeight.LIGHT, canWearOpen = true, collared = true),
        SubcategoryDefaults(CREWNECK_SWEATSHIRT, listOf(MID, TOP), 2, 0.34, setOf(STREETWEAR, SPORTY, MINIMAL), 3, materials = listOf(COTTON, Material.FLEECE)),
        SubcategoryDefaults(HOODIE, listOf(MID, TOP), 1, 0.34, setOf(STREETWEAR, SPORTY), 3, bulk = 2, materials = listOf(COTTON, Material.FLEECE)),
        SubcategoryDefaults(QUARTER_ZIP, listOf(MID), 4, 0.30, setOf(SPORTY, PREPPY, SMART_CASUAL), 3, presentableAlone = false, materials = listOf(KNIT)),
        SubcategoryDefaults(MERINO_CREWNECK, listOf(MID, TOP), 5, 0.22, setOf(MINIMAL, CLASSIC), 4, materials = listOf(MERINO, KNIT)),
        SubcategoryDefaults(CHUNKY_KNIT, listOf(MID, TOP), 4, 0.36, setOf(RUGGED, CLASSIC), 5, bulk = 3, materials = listOf(WOOL, KNIT), weight = FabricWeight.HEAVY, seasons = WINTER),
        SubcategoryDefaults(CARDIGAN, listOf(MID), 5, 0.25, setOf(CLASSIC, MINIMAL), 4, canWearOpen = true, presentableAlone = false, materials = listOf(WOOL, KNIT)),
        SubcategoryDefaults(TURTLENECK, listOf(TOP, MID), 6, 0.30, setOf(MINIMAL, CLASSIC), 3, materials = listOf(MERINO, KNIT)),
        SubcategoryDefaults(SWEATER_VEST, listOf(MID), 4, 0.12, setOf(PREPPY, CLASSIC), 4, presentableAlone = false, materials = listOf(WOOL, KNIT)),
        SubcategoryDefaults(GILET, listOf(MID, OUTER), 2, 0.25, setOf(SPORTY, OUTDOOR), 10, outer = OuterWeight.LIGHT, presentableAlone = false, materials = listOf(NYLON_TECH)),
        SubcategoryDefaults(Subcategory.FLEECE, listOf(MID, OUTER), 2, 0.35, setOf(OUTDOOR, SPORTY), 6, outer = OuterWeight.LIGHT, materials = listOf(Material.FLEECE)),

        // Outerwear
        SubcategoryDefaults(DENIM_JACKET, listOf(OUTER), 3, 0.30, setOf(STREETWEAR, RUGGED), 15, outer = OuterWeight.LIGHT, canWearOpen = true, materials = listOf(DENIM)),
        SubcategoryDefaults(BOMBER, listOf(OUTER), 3, 0.35, setOf(STREETWEAR, MINIMAL), 15, outer = OuterWeight.LIGHT, canWearOpen = true, materials = listOf(NYLON_TECH)),
        SubcategoryDefaults(HARRINGTON, listOf(OUTER), 4, 0.30, setOf(CLASSIC, MINIMAL), 15, outer = OuterWeight.LIGHT, canWearOpen = true),
        SubcategoryDefaults(CHORE_JACKET, listOf(OUTER), 3, 0.35, setOf(RUGGED, MINIMAL), 15, outer = OuterWeight.LIGHT, canWearOpen = true, materials = listOf(COTTON, CANVAS)),
        SubcategoryDefaults(COACH_JACKET, listOf(OUTER), 2, 0.20, setOf(SPORTY, STREETWEAR), 10, outer = OuterWeight.LIGHT, canWearOpen = true, materials = listOf(NYLON_TECH), weight = FabricWeight.LIGHT),
        SubcategoryDefaults(VARSITY_JACKET, listOf(OUTER), 2, 0.40, setOf(STREETWEAR, PREPPY), 15, outer = OuterWeight.LIGHT, canWearOpen = true, materials = listOf(WOOL, LEATHER)),
        SubcategoryDefaults(RAIN_SHELL, listOf(OUTER), 2, 0.15, setOf(OUTDOOR, SPORTY), 15, outer = OuterWeight.LIGHT, materials = listOf(NYLON_TECH), weight = FabricWeight.LIGHT, water = WaterResistance.WATERPROOF),
        SubcategoryDefaults(UNSTRUCTURED_BLAZER, listOf(OUTER), 6, 0.32, setOf(SMART_CASUAL, CLASSIC), 20, outer = OuterWeight.LIGHT, tailored = true, canWearOpen = true, materials = listOf(WOOL, COTTON)),
        SubcategoryDefaults(SPORT_COAT, listOf(OUTER), 7, 0.36, setOf(CLASSIC), 20, outer = OuterWeight.LIGHT, tailored = true, canWearOpen = true, materials = listOf(WOOL)),
        SubcategoryDefaults(SUIT_JACKET, listOf(OUTER), 9, 0.40, setOf(CLASSIC), 20, outer = OuterWeight.LIGHT, tailored = true, canWearOpen = true, materials = listOf(WOOL)),
        SubcategoryDefaults(FIELD_JACKET, listOf(OUTER), 4, 0.40, setOf(RUGGED, CLASSIC), 15, outer = OuterWeight.STANDARD, canWearOpen = true, water = WaterResistance.RESISTANT),
        SubcategoryDefaults(LEATHER_JACKET, listOf(OUTER), 4, 0.40, setOf(STREETWEAR, RUGGED), 30, outer = OuterWeight.STANDARD, canWearOpen = true, materials = listOf(LEATHER), water = WaterResistance.RESISTANT),
        SubcategoryDefaults(SUEDE_JACKET, listOf(OUTER), 5, 0.40, setOf(CLASSIC), 30, outer = OuterWeight.STANDARD, canWearOpen = true, materials = listOf(SUEDE)),
        SubcategoryDefaults(TRENCH_COAT, listOf(OUTER), 7, 0.45, setOf(CLASSIC, MINIMAL), 20, outer = OuterWeight.STANDARD, canWearOpen = true, water = WaterResistance.RESISTANT),
        SubcategoryDefaults(PEACOAT, listOf(OUTER), 6, 0.55, setOf(CLASSIC), 30, outer = OuterWeight.WARM, materials = listOf(WOOL), weight = FabricWeight.HEAVY, seasons = WINTER),
        SubcategoryDefaults(WOOL_OVERCOAT, listOf(OUTER), 8, 0.60, setOf(CLASSIC, MINIMAL), 30, outer = OuterWeight.WARM, canWearOpen = true, materials = listOf(WOOL), weight = FabricWeight.HEAVY, seasons = WINTER),
        SubcategoryDefaults(PARKA, listOf(OUTER), 3, 0.70, setOf(OUTDOOR, STREETWEAR), 30, outer = OuterWeight.WARM, materials = listOf(NYLON_TECH), weight = FabricWeight.HEAVY, water = WaterResistance.RESISTANT, seasons = WINTER),
        SubcategoryDefaults(PUFFER_JACKET, listOf(OUTER), 2, 0.75, setOf(STREETWEAR, OUTDOOR, SPORTY), 30, outer = OuterWeight.WARM, materials = listOf(NYLON_TECH), weight = FabricWeight.HEAVY, water = WaterResistance.RESISTANT, seasons = WINTER),

        // Bottoms
        SubcategoryDefaults(JEANS, listOf(BOTTOM), 3, 0.24, setOf(MINIMAL, STREETWEAR, RUGGED), 6, materials = listOf(DENIM), trouserKind = TrouserKind.JEANS, beltLoops = true),
        SubcategoryDefaults(CHINOS, listOf(BOTTOM), 5, 0.20, setOf(SMART_CASUAL, CLASSIC, MINIMAL), 3, trouserKind = TrouserKind.CHINOS, beltLoops = true),
        SubcategoryDefaults(WOOL_TROUSERS, listOf(BOTTOM), 7, 0.26, setOf(CLASSIC), 3, materials = listOf(WOOL), trouserKind = TrouserKind.TAILORED, beltLoops = true),
        SubcategoryDefaults(SUIT_TROUSERS, listOf(BOTTOM), 9, 0.26, setOf(CLASSIC), 3, materials = listOf(WOOL), trouserKind = TrouserKind.TAILORED, beltLoops = true),
        SubcategoryDefaults(PLEATED_TROUSERS, listOf(BOTTOM), 6, 0.22, setOf(CLASSIC, MINIMAL), 3, trouserKind = TrouserKind.TAILORED, beltLoops = true),
        SubcategoryDefaults(LINEN_TROUSERS, listOf(BOTTOM), 4, 0.15, setOf(RESORT, CLASSIC), 2, materials = listOf(LINEN), weight = FabricWeight.LIGHT, trouserKind = TrouserKind.LINEN, beltLoops = true, seasons = SUMMER),
        SubcategoryDefaults(CORDUROY_TROUSERS, listOf(BOTTOM), 4, 0.26, setOf(RUGGED, CLASSIC), 3, materials = listOf(CORDUROY), weight = FabricWeight.HEAVY, trouserKind = TrouserKind.CHINOS, beltLoops = true, seasons = WINTER),
        SubcategoryDefaults(CARGO_PANTS, listOf(BOTTOM), 2, 0.22, setOf(STREETWEAR, RUGGED, OUTDOOR), 4, trouserKind = TrouserKind.CASUAL, beltLoops = true),
        SubcategoryDefaults(JOGGERS, listOf(BOTTOM), 2, 0.24, setOf(SPORTY, STREETWEAR), 3, materials = listOf(JERSEY), trouserKind = TrouserKind.CASUAL),
        SubcategoryDefaults(SWEATPANTS, listOf(BOTTOM), 1, 0.26, setOf(SPORTY, STREETWEAR), 3, materials = listOf(JERSEY, Material.FLEECE), trouserKind = TrouserKind.CASUAL),
        SubcategoryDefaults(TRACK_PANTS, listOf(BOTTOM), 1, 0.18, setOf(SPORTY), 3, materials = listOf(NYLON_TECH), trouserKind = TrouserKind.ATHLETIC),
        SubcategoryDefaults(CHINO_SHORTS, listOf(BOTTOM), 2, 0.06, setOf(SMART_CASUAL, PREPPY), 2, weight = FabricWeight.LIGHT, trouserKind = TrouserKind.SHORTS, beltLoops = true, seasons = SUMMER),
        SubcategoryDefaults(TAILORED_SHORTS, listOf(BOTTOM), 4, 0.06, setOf(SMART_CASUAL, RESORT), 2, weight = FabricWeight.LIGHT, trouserKind = TrouserKind.SHORTS, beltLoops = true, seasons = SUMMER),
        SubcategoryDefaults(DENIM_SHORTS, listOf(BOTTOM), 2, 0.06, setOf(STREETWEAR), 3, materials = listOf(DENIM), trouserKind = TrouserKind.SHORTS, beltLoops = true, seasons = SUMMER),
        SubcategoryDefaults(ATHLETIC_SHORTS, listOf(BOTTOM), 1, 0.05, setOf(SPORTY), 1, materials = listOf(NYLON_TECH), weight = FabricWeight.LIGHT, trouserKind = TrouserKind.ATHLETIC, seasons = SUMMER),
        SubcategoryDefaults(SWIM_SHORTS, listOf(BOTTOM), 1, 0.04, setOf(RESORT), 1, materials = listOf(NYLON_TECH), weight = FabricWeight.LIGHT, trouserKind = TrouserKind.ATHLETIC, seasons = SUMMER),

        // Shoes
        SubcategoryDefaults(MINIMAL_SNEAKERS, listOf(SHOES), 4, 0.03, setOf(MINIMAL, SMART_CASUAL, STREETWEAR), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.LEATHER, ShoeSole.RUBBER), shoeKind = ShoeKind.MINIMAL_SNEAKERS),
        SubcategoryDefaults(CANVAS_SNEAKERS, listOf(SHOES), 3, 0.02, setOf(STREETWEAR, MINIMAL), 0, materials = listOf(CANVAS), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.CANVAS, ShoeSole.RUBBER), shoeKind = ShoeKind.CANVAS),
        SubcategoryDefaults(CHUNKY_SNEAKERS, listOf(SHOES), 2, 0.04, setOf(STREETWEAR, SPORTY), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.CHUNKY, ShoeUpper.SYNTHETIC, ShoeSole.RUBBER), shoeKind = ShoeKind.CHUNKY_RUNNING),
        SubcategoryDefaults(HIGH_TOP_SNEAKERS, listOf(SHOES), 2, 0.04, setOf(STREETWEAR), 0, materials = listOf(CANVAS), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.CANVAS, ShoeSole.RUBBER), shoeKind = ShoeKind.CANVAS),
        SubcategoryDefaults(RUNNING_SHOES, listOf(SHOES), 2, 0.03, setOf(SPORTY), 0, materials = listOf(NYLON_TECH), shoe = shoe(ShoeProfile.CHUNKY, ShoeUpper.KNIT, ShoeSole.RUBBER), shoeKind = ShoeKind.CHUNKY_RUNNING),
        SubcategoryDefaults(LOAFERS, listOf(SHOES), 6, 0.03, setOf(CLASSIC, SMART_CASUAL), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.SLEEK, ShoeUpper.LEATHER, ShoeSole.LEATHER, rest = 1), shoeKind = ShoeKind.LOAFERS),
        SubcategoryDefaults(DERBIES, listOf(SHOES), 7, 0.04, setOf(CLASSIC), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.LEATHER, ShoeSole.LEATHER, rest = 1), shoeKind = ShoeKind.DRESS),
        SubcategoryDefaults(OXFORDS, listOf(SHOES), 9, 0.04, setOf(CLASSIC), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.SLEEK, ShoeUpper.LEATHER, ShoeSole.LEATHER, rest = 1), shoeKind = ShoeKind.DRESS),
        SubcategoryDefaults(BROGUES, listOf(SHOES), 6, 0.04, setOf(CLASSIC), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.LEATHER, ShoeSole.LEATHER, rest = 1), shoeKind = ShoeKind.DRESS),
        SubcategoryDefaults(MONK_STRAPS, listOf(SHOES), 7, 0.04, setOf(CLASSIC), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.SLEEK, ShoeUpper.LEATHER, ShoeSole.LEATHER, rest = 1), shoeKind = ShoeKind.DRESS),
        SubcategoryDefaults(CHELSEA_BOOTS, listOf(SHOES), 6, 0.08, setOf(MINIMAL, CLASSIC, RUGGED), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.LEATHER, ShoeSole.RUBBER, rest = 1), shoeKind = ShoeKind.CHELSEA_CHUKKA),
        SubcategoryDefaults(CHUKKA_BOOTS, listOf(SHOES), 5, 0.07, setOf(SMART_CASUAL, RUGGED), 0, materials = listOf(SUEDE), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.SUEDE, ShoeSole.RUBBER, rest = 1), shoeKind = ShoeKind.CHELSEA_CHUKKA),
        SubcategoryDefaults(WORK_BOOTS, listOf(SHOES), 3, 0.10, setOf(RUGGED), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.CHUNKY, ShoeUpper.LEATHER, ShoeSole.LUG, rest = 1), shoeKind = ShoeKind.BOOTS),
        SubcategoryDefaults(HIKING_BOOTS, listOf(SHOES), 2, 0.10, setOf(OUTDOOR), 0, materials = listOf(LEATHER, NYLON_TECH), water = WaterResistance.WATERPROOF, shoe = shoe(ShoeProfile.CHUNKY, ShoeUpper.SYNTHETIC, ShoeSole.LUG, wp = true), shoeKind = ShoeKind.BOOTS),
        SubcategoryDefaults(BOAT_SHOES, listOf(SHOES), 4, 0.02, setOf(PREPPY, RESORT), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.SLEEK, ShoeUpper.LEATHER, ShoeSole.RUBBER), shoeKind = ShoeKind.BOAT, seasons = SUMMER),
        SubcategoryDefaults(ESPADRILLES, listOf(SHOES), 3, 0.02, setOf(RESORT), 0, materials = listOf(CANVAS), shoe = shoe(ShoeProfile.SLEEK, ShoeUpper.CANVAS, ShoeSole.RUBBER), shoeKind = ShoeKind.SANDALS, seasons = SUMMER),
        SubcategoryDefaults(SANDALS, listOf(SHOES), 2, 0.02, setOf(RESORT, OUTDOOR), 0, materials = listOf(LEATHER), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.LEATHER, ShoeSole.RUBBER), shoeKind = ShoeKind.SANDALS, seasons = SUMMER, minFeels = 22.0),
        SubcategoryDefaults(SLIDES, listOf(SHOES), 1, 0.01, setOf(SPORTY, RESORT), 0, materials = listOf(NYLON_TECH), shoe = shoe(ShoeProfile.STANDARD, ShoeUpper.RUBBER, ShoeSole.RUBBER), shoeKind = ShoeKind.SANDALS, seasons = SUMMER),
    ) + AccessoryTable.rows

    private val bySub: Map<Subcategory, SubcategoryDefaults> = rows.associateBy { it.sub }

    fun of(sub: Subcategory): SubcategoryDefaults = bySub.getValue(sub)
}
