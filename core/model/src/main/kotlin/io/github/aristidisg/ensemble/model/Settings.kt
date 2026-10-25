package io.github.aristidisg.ensemble.model

import kotlinx.serialization.Serializable

@Serializable
enum class ItemField(val label: String) {
    SLOT("Slot"), SUBCATEGORY("Subcategory"), COLOR_FAMILY("Color"), STYLE("Style"),
    PATTERN("Pattern"), MATERIAL("Material"), CUSTOM_TAG("Tag"), ITEM("Specific item")
}

/** A piece to buy: what it is, its color, and how many new outfits it unlocks. */
@Serializable
data class ShoppingEntry(
    val id: String,
    val subcategory: Subcategory,
    val family: ColorFamily,
    val hex: String,
    val unlocks: Int = 0,
    val note: String = "",
    val done: Boolean = false,
    val addedEpochDay: Long = 0,
) {
    val title: String get() = "${family.label} ${subcategory.label.lowercase()}"
}

/** Matches a piece when the field has the given value (enum name, tag or item ID). */
@Serializable
data class ItemPredicate(val field: ItemField, val value: String)

@Serializable
enum class RuleAction(val label: String, val hard: Boolean) {
    NEVER_TOGETHER("Never together", true),
    REQUIRE("Require", true),
    FORBID("Forbid", true),
    PREFER("Prefer", false),
    AVOID("Avoid", false),
    IF_THEN_REQUIRE("If … then require", true),
}

@Serializable
data class RuleScope(
    val scenarioIds: Set<String> = emptySet(),
    val minTemp: Double? = null,
    val maxTemp: Double? = null,
    val weekdays: Set<Int> = emptySet(),
)

/**
 * IF a piece matches [a] (all predicates) [and a piece matches [b]] in [scope] THEN [action].
 * NEVER_TOGETHER and IF_THEN_REQUIRE use both sides; REQUIRE, FORBID, PREFER and AVOID use [a]
 * (and [b] when given, for pair preferences).
 */
@Serializable
data class CustomRule(
    val id: String,
    val enabled: Boolean = true,
    val name: String,
    val a: List<ItemPredicate>,
    val b: List<ItemPredicate> = emptyList(),
    val action: RuleAction,
    val weight: Double = 0.5,
    val scope: RuleScope = RuleScope(),
)

@Serializable
enum class ColorSeason(val label: String) {
    LIGHT_SPRING("Light Spring"), WARM_SPRING("Warm Spring"), CLEAR_SPRING("Clear Spring"),
    LIGHT_SUMMER("Light Summer"), COOL_SUMMER("Cool Summer"), SOFT_SUMMER("Soft Summer"),
    SOFT_AUTUMN("Soft Autumn"), WARM_AUTUMN("Warm Autumn"), DEEP_AUTUMN("Deep Autumn"),
    DEEP_WINTER("Deep Winter"), COOL_WINTER("Cool Winter"), CLEAR_WINTER("Clear Winter"),
}

@Serializable
enum class PersonalContrast { LOW, MEDIUM, HIGH }

@Serializable
data class PersonalColor(
    val enabled: Boolean = false,
    val strength: Double = 0.5,
    val season: ColorSeason? = null,
    val contrast: PersonalContrast = PersonalContrast.MEDIUM,
    val favorites: Set<ColorFamily> = emptySet(),
    val avoid: Set<ColorFamily> = emptySet(),
    val never: Set<ColorFamily> = emptySet(),
    val applyAllSlots: Boolean = false,
)

@Serializable
enum class LocationMode { GPS, CITY }

/** All user settings, stored as one JSON document in DataStore. */
@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val styleWeights: Map<StyleTag, Double> = DEFAULT_STYLE_WEIGHTS,
    val boldness: Double = 0.4,
    val strictness: Strictness = Strictness.BALANCED,
    val personalColor: PersonalColor = PersonalColor(),
    val thermalBias: Double = 0.0,
    val rotationDays: Map<String, Int> = DEFAULT_ROTATION,
    val strictRotation: Boolean = false,
    val outfitCooldownDays: Int = 14,
    val rejectedCooldownDays: Int = 21,
    val deckSize: Int = 12,
    val explorationRate: Double = 0.2,
    val ruleEnabled: Map<String, Boolean> = emptyMap(),
    val ruleWeights: Map<String, Double> = emptyMap(),
    val trustMyTaste: Double = 0.18,
    val halfLifeDays: Double = 120.0,
    val locationMode: LocationMode = LocationMode.GPS,
    val savedCity: LatLon? = null,
    val savedCities: List<LatLon> = emptyList(),
    val indoorTempWinter: Double = 22.0,
    val indoorTempSummer: Double = 25.0,
    val shortsMinTemp: Double = 20.0,
    val morningMinute: Int = 7 * 60 + 30,
    val eveningMinute: Int = 21 * 60,
    val morningNotification: Boolean = true,
    val eveningCheckIn: Boolean = true,
    val categoryHints: Boolean = true,
    val cutouts: Boolean = true,
    val autoLaundry: Boolean = false,
    val hideAccessories: Boolean = false,
    val allowMixedMetals: Boolean = false,
    val shoesDarkerThanTrousers: Boolean = true,
    val forgottenLove: Boolean = true,
    val weeklyTemplate: Map<Int, String> = DEFAULT_WEEK,
    val usualWeekday: String = "mixed",
    /** Soft "reset all learning" starts a new epoch; older events are kept but ignored. */
    val learningEpoch: Int = 0,
    val notificationsAsked: Boolean = false,
    /** Folder (SAF tree URI) the weekly automatic backup is written to, e.g. a Google Drive folder. */
    val autoBackupUri: String? = null,
    val autoBackupLastMillis: Long = 0,
    /** Manual weather per day ("set temperature by hand"), keyed by epoch day. */
    val manualWeather: Map<String, WindowWeather> = emptyMap(),
    /**
     * Saved and composed outfits shape suggestions: similar outfits get a boost and saving
     * teaches taste. Off: neither, and past saves stop counting.
     */
    val savedOutfitsInfluence: Boolean = true,
    /** Pieces to buy, usually added from the gap finder. */
    val shoppingList: List<ShoppingEntry> = emptyList(),
) {
    companion object {
        val DEFAULT_STYLE_WEIGHTS: Map<StyleTag, Double> = StyleTag.entries.associateWith {
            when (it) {
                StyleTag.MINIMAL, StyleTag.SMART_CASUAL, StyleTag.CLASSIC -> 1.0
                StyleTag.STREETWEAR, StyleTag.SPORTY -> 0.8
                else -> 0.3
            }
        }

        /** Rotation windows in days, keyed by RotationGroup name. */
        val DEFAULT_ROTATION: Map<String, Int> = mapOf(
            "BASE_TOP" to 7, "MID" to 4, "JEANS" to 2, "TROUSERS" to 3, "OUTER" to 0, "ACCESSORY" to 0, "SHOES" to 0,
        )

        /** ISO weekday (1 = Monday) to scenario ID. */
        val DEFAULT_WEEK: Map<Int, String> = mapOf(
            1 to "office", 2 to "office", 3 to "office", 4 to "office", 5 to "office",
            6 to "casual", 7 to "casual",
        )
    }
}
