package io.github.aristidisg.ensemble.engine.tables

import io.github.aristidisg.ensemble.model.Activity
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorFamily.*
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.ScenarioPalette
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.Subcategory.*

/** Appendix C1: scenario presets. All editable by the user; custom scenarios use the same fields. */
object ScenarioPresets {
    private val collared = setOf(OXFORD_SHIRT, DRESS_SHIRT, POLO, KNIT_POLO, LINEN_SHIRT, FLANNEL_SHIRT)

    val all: List<Scenario> = listOf(
        Scenario("casual", "Everyday casual", "weekend", 1.0, 3.0, 5.0, 10, 20, 0.5, Activity.LIGHT, accessoryMin = 0, accessoryMax = 3),
        Scenario("university", "University", "school", 2.0, 3.5, 5.0, 8, 18, 0.7, Activity.LIGHT, accessoryMin = 1, accessoryMax = 3,
            forbiddenSubcategories = setOf(TIE, SUIT_JACKET, SUIT_TROUSERS), bonusSubcategories = setOf(BACKPACK),
            notes = "Comfortable shoes for walking; a light or removable layer for cold lecture halls."),
        Scenario("office", "Office (business casual)", "work", 4.0, 6.0, 7.0, 8, 19, 0.85, Activity.SEDENTARY,
            ScenarioPalette.CONSERVATIVE_LEANING, 1, 3, bonusSubcategories = collared),
        Scenario("business_formal", "Business formal", "business_center", 7.0, 8.5, 10.0, 8, 19, 0.85, Activity.SEDENTARY,
            ScenarioPalette.CONSERVATIVE, 2, 4, requiredSubcategories = setOf(SUIT_JACKET, SPORT_COAT, UNSTRUCTURED_BLAZER)),
        Scenario("interview", "Interview", "handshake", 6.0, 7.5, 9.0, 8, 12, 0.9, Activity.SEDENTARY,
            ScenarioPalette.CONSERVATIVE, 1, 3, maxLoudness = 1, allowsLogos = false),
        Scenario("brunch", "Brunch / coffee", "local_cafe", 3.0, 4.0, 6.0, 10, 14, 0.5, Activity.LIGHT, accessoryMin = 1, accessoryMax = 3),
        Scenario("date", "Date night", "favorite", 4.0, 5.5, 7.0, 19, 24, 0.7, Activity.LIGHT, ScenarioPalette.EVENING, 1, 3),
        Scenario("dinner", "Dinner (smart)", "restaurant", 5.0, 6.5, 8.0, 19, 24, 0.85, Activity.SEDENTARY, ScenarioPalette.EVENING, 2, 4),
        Scenario("night_out", "Night out", "nightlife", 3.0, 5.0, 7.0, 21, 27, 0.8, Activity.ACTIVE, ScenarioPalette.EVENING, 1, 4,
            notes = "Bolder allowed."),
        Scenario("concert", "Concert / festival", "music_note", 1.0, 3.0, 5.0, 18, 26, 0.3, Activity.ACTIVE, accessoryMin = 1, accessoryMax = 3,
            notes = "Comfortable shoes."),
        Scenario("wedding", "Wedding guest", "celebration", 7.0, 8.5, 10.0, 12, 24, 0.6, Activity.LIGHT, ScenarioPalette.NO_DOMINANT_WHITE, 2, 4),
        Scenario("funeral", "Funeral / memorial", "church", 7.0, 8.0, 10.0, 9, 15, 0.6, Activity.SEDENTARY, ScenarioPalette.DARK_MUTED, 1, 2),
        Scenario("religious", "Religious service", "church", 5.0, 6.5, 8.0, 9, 13, 0.9, Activity.SEDENTARY, ScenarioPalette.CONSERVATIVE, 1, 3),
        Scenario("sports_event", "Sports event (spectator)", "stadium", 1.0, 3.0, 4.0, 14, 18, 0.2, Activity.SEDENTARY, accessoryMin = 1, accessoryMax = 3,
            notes = "Warmer: sitting outdoors."),
        Scenario("travel", "Travel day", "flight", 2.0, 3.0, 5.0, 6, 22, 0.6, Activity.SEDENTARY, accessoryMin = 1, accessoryMax = 3,
            notes = "Comfort, pockets, a removable layer."),
        Scenario("gym", "Gym / training", "fitness_center", 1.0, 1.0, 2.0, 17, 19, 1.0, Activity.ACTIVE, accessoryMin = 0, accessoryMax = 2,
            requiredTags = setOf(StyleTag.SPORTY), allowsSlides = true),
        Scenario("running", "Running", "directions_run", 1.0, 1.0, 2.0, 7, 8, 0.0, Activity.ACTIVE, accessoryMin = 0, accessoryMax = 2,
            requiredTags = setOf(StyleTag.SPORTY), preferredStyles = mapOf(StyleTag.TECHWEAR to 0.5)),
        Scenario("hiking", "Hiking / outdoors", "hiking", 1.0, 2.0, 3.0, 8, 17, 0.0, Activity.ACTIVE, accessoryMin = 1, accessoryMax = 3,
            preferredStyles = mapOf(StyleTag.OUTDOOR to 1.0), bonusSubcategories = setOf(HIKING_BOOTS, RAIN_SHELL)),
        Scenario("beach", "Beach / pool", "beach_access", 1.0, 1.0, 3.0, 10, 19, 0.0, Activity.LIGHT, ScenarioPalette.LIGHT, 0, 3,
            allowsSwimwear = true, allowsSlides = true),
        Scenario("home", "Home / lounge", "home", 1.0, 1.0, 2.0, 8, 22, 1.0, Activity.SEDENTARY, accessoryMin = 0, accessoryMax = 0,
            shoesOptional = true, allowsSlides = true, notes = "Rules relaxed, no shoes needed."),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Scenario? = byId[id]
}

/** One C2 row: a feels-like band, its outdoor clo target and typical slot structures. */
data class WarmthBand(
    val minFeels: Double,
    val maxFeels: Double,
    val targetMin: Double,
    val targetMax: Double,
    val needsMid: Boolean,
    val needsOuter: Boolean,
    val warmOuter: Boolean,
    val scarf: Boolean = false,
    val beanie: Boolean = false,
    val gloves: Boolean = false,
    val shortsOk: Boolean = false,
)

/** Appendix C2: warmth targets and slot structures (rule W1, generator step 2). */
object WarmthTable {
    val bands: List<WarmthBand> = listOf(
        WarmthBand(28.0, 99.0, 0.30, 0.40, needsMid = false, needsOuter = false, warmOuter = false, shortsOk = true),
        WarmthBand(24.0, 28.0, 0.40, 0.50, needsMid = false, needsOuter = false, warmOuter = false),
        WarmthBand(20.0, 24.0, 0.45, 0.65, needsMid = false, needsOuter = false, warmOuter = false),
        WarmthBand(16.0, 20.0, 0.65, 0.85, needsMid = false, needsOuter = false, warmOuter = false),
        WarmthBand(12.0, 16.0, 0.85, 1.05, needsMid = true, needsOuter = false, warmOuter = false),
        WarmthBand(8.0, 12.0, 1.00, 1.20, needsMid = true, needsOuter = true, warmOuter = false),
        WarmthBand(4.0, 8.0, 1.20, 1.45, needsMid = true, needsOuter = true, warmOuter = true, scarf = true),
        WarmthBand(0.0, 4.0, 1.40, 1.65, needsMid = true, needsOuter = true, warmOuter = true, scarf = true, beanie = true),
        WarmthBand(-99.0, 0.0, 1.60, 2.00, needsMid = true, needsOuter = true, warmOuter = true, scarf = true, beanie = true, gloves = true),
    )

    fun band(feels: Double): WarmthBand = bands.first { feels >= it.minFeels }

    fun label(feels: Double): String {
        val b = band(feels)
        return when {
            b.minFeels < -50 -> "<0"
            b.maxFeels > 50 -> "28+"
            else -> "${b.minFeels.toInt()}-${b.maxFeels.toInt()}"
        }
    }
}

data class Archetype(val sub: Subcategory, val colors: List<ColorFamily>)

/** Appendix C3: gap-finder archetypes. */
object Archetypes {
    val all: List<Archetype> = listOf(
        Archetype(OXFORD_SHIRT, listOf(WHITE, LIGHT_BLUE)),
        Archetype(T_SHIRT, listOf(WHITE, BLACK, MID_GREY, NAVY)),
        Archetype(MERINO_CREWNECK, listOf(NAVY, MID_GREY, CAMEL, OLIVE)),
        Archetype(HOODIE, listOf(MID_GREY, BLACK, NAVY)),
        Archetype(OVERSHIRT, listOf(OLIVE, NAVY, ECRU)),
        Archetype(UNSTRUCTURED_BLAZER, listOf(NAVY)),
        Archetype(DENIM_JACKET, listOf(MID_DENIM)),
        Archetype(BOMBER, listOf(BLACK, OLIVE, NAVY)),
        Archetype(CHORE_JACKET, listOf(NAVY, OLIVE, ECRU)),
        Archetype(WOOL_OVERCOAT, listOf(CAMEL, CHARCOAL, NAVY)),
        Archetype(RAIN_SHELL, listOf(BLACK, NAVY, OLIVE)),
        Archetype(PUFFER_JACKET, listOf(BLACK, OLIVE)),
        Archetype(JEANS, listOf(DARK_DENIM, BLACK)),
        Archetype(CHINOS, listOf(STONE, NAVY, OLIVE)),
        Archetype(WOOL_TROUSERS, listOf(MID_GREY, CHARCOAL)),
        Archetype(JOGGERS, listOf(MID_GREY, BLACK)),
        Archetype(CHINO_SHORTS, listOf(NAVY, STONE)),
        Archetype(MINIMAL_SNEAKERS, listOf(WHITE)),
        Archetype(CHELSEA_BOOTS, listOf(CHOCOLATE, BLACK)),
        Archetype(LOAFERS, listOf(BROWN)),
        Archetype(DERBIES, listOf(BLACK, CHOCOLATE)),
        Archetype(RUNNING_SHOES, listOf(MID_GREY, BLACK)),
        Archetype(LEATHER_BELT, listOf(BROWN, BLACK)),
        Archetype(EVERYDAY_WATCH, listOf(LIGHT_GREY)),
        Archetype(BACKPACK, listOf(BLACK)),
        Archetype(CAP, listOf(NAVY)),
        Archetype(BEANIE, listOf(MID_GREY, BLACK)),
        Archetype(SCARF, listOf(MID_GREY, CAMEL)),
        Archetype(SUNGLASSES, listOf(BLACK, CHOCOLATE)),
    )
}
