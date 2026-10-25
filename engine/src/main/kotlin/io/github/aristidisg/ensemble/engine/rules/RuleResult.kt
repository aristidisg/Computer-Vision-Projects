package io.github.aristidisg.ensemble.engine.rules

import kotlinx.serialization.Serializable

/** Score groups (SPEC 5.11) with their default weights. */
@Serializable
enum class RuleGroup(val label: String, val defaultWeight: Double) {
    COLOR("Color", 0.24),
    WEATHER("Weather", 0.16),
    FORMALITY_STYLE("Formality and style", 0.14),
    SHOES_ACCESSORIES("Shoes and accessories", 0.08),
    PATTERN_TEXTURE("Patterns and texture", 0.08),
    SILHOUETTE("Silhouette", 0.06),
    ROTATION("Rotation", 0.06),
    LEARNED("Learned taste", 0.18),
}

/** Explanation keys; templates live in the app's strings.xml (Appendix C4) with English fallbacks here. */
@Serializable
enum class Explain(val template: String) {
    HARMONY_NEUTRAL("All neutrals: easy to wear, hard to get wrong."),
    HARMONY_ANALOGOUS("{a} and {b} sit side by side on the color wheel: calm and cohesive."),
    HARMONY_COMPLEMENTARY("{a} against {b}: opposite colors, kept in check by neutrals."),
    HARMONY_TRIADIC("Three colors spaced around the wheel: bold but balanced."),
    HARMONY_WEAK("The colors don't follow a clear harmony."),
    NEUTRAL_ACCENT("Neutral base with one {accent} accent (about {pct} %)."),
    TOO_MANY_ACCENTS("{n} competing accent colors."),
    COLOR_FIGHT("Two strong colors fight for attention."),
    VALUE_CONTRAST("A lighter top over darker trousers grounds the look."),
    LOW_CONTRAST("Top and trousers are very close in lightness."),
    GREY_MUSH("Everything sits in the same mid grey tone."),
    CHROMA_HEAVY("More than one loud color in big areas."),
    TEMPERATURE_MATCH("Colors share a {temp} temperature."),
    CLASSIC_PAIR("{a} with {b} is a classic pairing."),
    UNUSUAL_PAIR("{a} with {b} is a contested pairing."),
    DENIM_OK("Denim on denim with clearly different washes."),
    DENIM_CLASH("Denim washes too close but not matching."),
    NEAR_MISS("{a} pieces almost match but not quite: match it or contrast it."),
    LEATHER_MATCH("Belt and shoes in the same {color} leather."),
    LEATHER_CLASH("{belt} belt with {shoes} shoes: swap one so they match."),
    METALS_MATCH("Metals match ({metal})."),
    METALS_MIXED("Mixed metals."),
    PERSONAL_COLOR_FIT("{color} suits your {season} coloring."),
    PERSONAL_COLOR_AVOID("{color} is on your avoid list."),
    PALETTE_FIT("Colors fit the {scenario} palette."),
    PALETTE_OFF("Colors are off for {scenario}."),
    SEASONAL_NUDGE("Tones suit the season."),
    WEDDING_WHITE("Dominant white or cream at a wedding upstages the couple."),
    PATTERN_ONE("One pattern, everything else solid."),
    PATTERN_MIX_OK("Two patterns at different scales, sharing a color."),
    PATTERN_CLASH("Too many or clashing patterns."),
    GRAPHIC_CLASH("More than one graphic or big logo."),
    FABRIC_SEASON("Fabrics suit the season."),
    FABRIC_OFF_SEASON("{material} is off-season."),
    WEIGHT_MISMATCH("Fabric weights are mismatched."),
    TONAL_TEXTURE("Tonal outfit lifted by mixed textures."),
    TECH_WITH_TAILORING("Technical fabric with tailoring."),
    FORMALITY_FIT("Formality {f}/10 fits {scenario} ({min}-{max})."),
    FORMALITY_OFF("Formality {f}/10 is off for {scenario} ({min}-{max})."),
    FORMALITY_MIXED("High-low formality mix."),
    CLASSIC_CONFLICT("{what}"),
    STYLE_COHERENT("Led by {style}."),
    STYLE_CLASH("Styles pull in different directions."),
    SCENARIO_STYLE("Suits {scenario}."),
    YOUR_STYLE("Matches your style."),
    SIGNATURE("Features your signature {item}."),
    SILHOUETTE("A relaxed top balanced by slimmer trousers."),
    SILHOUETTE_OFF("Proportions are heavy top and bottom."),
    SHOE_FIT("{shoes} suit the trousers."),
    SHOE_CLASH("{shoes} with {trousers} is a mismatch."),
    SHOE_WEATHER("{shoes} suit the weather."),
    SHOE_WEATHER_BAD("{shoes} are a poor fit for the weather."),
    SHOE_REST("Your {shoes} need a day off after yesterday."),
    SHOE_DARKER("Shoes lighter than the trousers at this formality."),
    SOCKS_NO_SHOW("No-show socks."),
    SOCKS_MATCH("Match socks to trousers."),
    ACCESSORY_COUNT("{n} accessories for {scenario}."),
    BELT_MISSING("Belt loops but no belt."),
    BELT_WRONG("A belt doesn't work with these trousers."),
    WATCH_FIT("The watch suits the occasion."),
    HEADWEAR_OFF("Skip the hat at this formality."),
    SUN_READY("Sun ready."),
    SCARF_FIT("A scarf for the cold or wind."),
    WARMTH_OK("Warm enough for {min}-{max} °C."),
    TOO_COLD("Likely too cold for {min} °C."),
    TOO_WARM("Likely too warm for {max} °C."),
    WEATHER_LAYER("Feels {min} → {max} °C: the {item} comes off by midday."),
    RAIN("{pct} % chance of rain: water-resistant {item}, no suede."),
    RAIN_UNPROTECTED("{pct} % chance of rain and no rain-ready layer."),
    WIND("Windproof layer for the wind."),
    HEAT("Breathable fabrics for the heat."),
    SNOW_BOOTS("Boots with grip for snow."),
    ROTATION("You wore the {item} {n} days ago."),
    NEAR_DUPLICATE("Very close to an outfit you wore recently."),
    SEEN_RECENTLY("Shown in a recent deck."),
    FORGOTTEN("Gives the {item} some love: unworn for {n} days."),
    PLANNED_SOON("The {item} is planned for the next few days."),
    LAUNDRY_DUE("The {item} is due for a wash."),
    LEARNED_LIKE("You've liked {a} with {b} {n} times."),
    LEARNED_DISLIKE("You've passed on similar looks before."),
    CUSTOM_RULE("Your rule: {name}."),
    NEW_IDEA("New idea: something you haven't rated yet."),
}

/** One rule's verdict: score 0..1, weight within its group, and an explanation. */
@Serializable
data class RuleResult(
    val id: String,
    val group: RuleGroup,
    val score: Double,
    val weight: Double = 1.0,
    val key: Explain? = null,
    val args: Map<String, String> = emptyMap(),
) {
    fun render(): String = key?.let { k -> args.entries.fold(k.template) { s, e -> s.replace("{${e.key}}", e.value) } } ?: id
}

/** Helper to build results tersely. */
internal fun res(id: String, group: RuleGroup, score: Double, weight: Double = 1.0, key: Explain? = null, vararg args: Pair<String, Any>) =
    RuleResult(id, group, score.coerceIn(0.0, 1.0), weight, key, args.associate { it.first to it.second.toString() })
