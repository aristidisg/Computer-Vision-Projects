package io.github.aristidisg.ensemble.engine.color

import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorFamily.*
import io.github.aristidisg.ensemble.model.Temperature

data class FamilyInfo(
    val family: ColorFamily,
    val hex: String,
    val neutralness: Double,
    val temperature: Temperature,
) {
    val lab: Lab = ColorScience.hexToLab(hex)
}

/** Appendix B1 and B2 as data. Change a number here and the behavior follows. */
object ColorTables {
    private val W = Temperature.WARM
    private val C = Temperature.COOL
    private val N = Temperature.NEUTRAL

    /** B1 color families. MULTICOLOR has no hex and is never a nearest-family candidate. */
    val families: List<FamilyInfo> = listOf(
        FamilyInfo(BLACK, "#111111", 1.0, N),
        FamilyInfo(CHARCOAL, "#36454F", 1.0, C),
        FamilyInfo(MID_GREY, "#808080", 1.0, N),
        FamilyInfo(LIGHT_GREY, "#C8C8C8", 1.0, N),
        FamilyInfo(WHITE, "#FAFAFA", 1.0, C),
        FamilyInfo(ECRU, "#EFE6D2", 0.95, W),
        FamilyInfo(STONE, "#D6C7A8", 0.9, W),
        FamilyInfo(KHAKI, "#C3B091", 0.85, W),
        FamilyInfo(TAUPE, "#8B7D6B", 0.9, N),
        FamilyInfo(CAMEL, "#C19A6B", 0.8, W),
        FamilyInfo(BROWN, "#7B4B2A", 0.8, W),
        FamilyInfo(CHOCOLATE, "#4A2C1D", 0.85, W),
        FamilyInfo(OLIVE, "#6B6B3A", 0.7, W),
        FamilyInfo(NAVY, "#1F2A44", 0.9, C),
        FamilyInfo(DARK_DENIM, "#2E3F5C", 0.9, C),
        FamilyInfo(MID_DENIM, "#5B7FA6", 0.85, C),
        FamilyInfo(LIGHT_DENIM, "#A7BCD6", 0.8, C),
        FamilyInfo(LIGHT_BLUE, "#A9C7E8", 0.6, C),
        FamilyInfo(COBALT, "#2F55D4", 0.0, C),
        FamilyInfo(TEAL, "#1F7A7A", 0.2, C),
        FamilyInfo(FOREST_GREEN, "#1F4D2E", 0.4, C),
        FamilyInfo(SAGE, "#9CAF88", 0.5, N),
        FamilyInfo(KELLY_GREEN, "#2E9E4F", 0.0, C),
        FamilyInfo(MINT, "#B8E3C9", 0.2, C),
        FamilyInfo(BURGUNDY, "#6D1A2A", 0.4, W),
        FamilyInfo(RED, "#C62828", 0.0, W),
        FamilyInfo(RUST, "#B5532E", 0.3, W),
        FamilyInfo(ORANGE, "#EF7D22", 0.0, W),
        FamilyInfo(MUSTARD, "#D4A017", 0.2, W),
        FamilyInfo(YELLOW, "#F5D33A", 0.0, W),
        FamilyInfo(DUSTY_PINK, "#D8A7A0", 0.4, W),
        FamilyInfo(PINK, "#F2A7B8", 0.1, C),
        FamilyInfo(LAVENDER, "#C3B1E1", 0.2, C),
        FamilyInfo(PLUM, "#5B2A5C", 0.3, C),
    )

    private val multicolor = FamilyInfo(MULTICOLOR, "#808080", 0.0, N)
    private val byFamily: Map<ColorFamily, FamilyInfo> = families.associateBy { it.family } + (MULTICOLOR to multicolor)

    fun family(f: ColorFamily): FamilyInfo = byFamily.getValue(f)

    /** A "neutral" family for B2 purposes: unlisted neutral pairs default to 0.75. */
    fun isNeutralFamily(f: ColorFamily): Boolean = family(f).neutralness >= 0.6

    private val greys = listOf(CHARCOAL, MID_GREY, LIGHT_GREY)
    private val denims = listOf(DARK_DENIM, MID_DENIM, LIGHT_DENIM)

    /** Pairs marked "left to your swipes": contested, so learning moves them fastest. */
    val contested: Set<Set<ColorFamily>> = setOf(setOf(NAVY, BLACK), setOf(BLACK, BROWN))

    /** B2 classic pairing affinities (rule C7). */
    val affinities: Map<Set<ColorFamily>, Double> = buildMap {
        fun put(a: List<ColorFamily>, b: List<ColorFamily>, v: Double) {
            for (x in a) for (y in b) if (x != y) put(setOf(x, y), v)
        }
        fun put(a: ColorFamily, b: List<ColorFamily>, v: Double) = put(listOf(a), b, v)
        fun put(a: ColorFamily, b: ColorFamily, v: Double) = put(listOf(a), listOf(b), v)

        put(NAVY, CAMEL, 0.95); put(NAVY, listOf(WHITE, ECRU), 0.95); put(NAVY, greys, 0.90)
        put(NAVY, LIGHT_BLUE, 0.90); put(NAVY, listOf(STONE, KHAKI), 0.90); put(NAVY, BROWN, 0.90)
        put(NAVY, BURGUNDY, 0.85); put(NAVY, OLIVE, 0.80); put(NAVY, BLACK, 0.50)
        put(BLACK, WHITE, 0.95); put(BLACK, greys, 0.85); put(BLACK, CAMEL, 0.85)
        put(BLACK, OLIVE, 0.70); put(BLACK, BROWN, 0.35)
        put(greys, listOf(WHITE), 0.90); put(greys, listOf(BURGUNDY), 0.85); put(greys, listOf(BROWN), 0.80)
        put(CHARCOAL, LIGHT_BLUE, 0.90)
        put(STONE, WHITE, 0.90); put(STONE, BROWN, 0.85); put(STONE, OLIVE, 0.85)
        put(OLIVE, ECRU, 0.85); put(OLIVE, RUST, 0.85); put(OLIVE, BROWN, 0.85); put(OLIVE, greys, 0.70)
        put(CAMEL, WHITE, 0.90); put(CAMEL, greys, 0.90)
        put(BROWN, LIGHT_BLUE, 0.90); put(BROWN, ECRU, 0.90)
        put(denims, listOf(WHITE), 0.95); put(denims, listOf(CAMEL), 0.90); put(denims, greys, 0.85)
        put(denims, listOf(OLIVE), 0.80); put(denims, listOf(BLACK), 0.75); put(denims, listOf(NAVY), 0.70)
    }

    /** Affinity for a pair of families, or null when the color math alone should decide. */
    fun affinity(a: ColorFamily, b: ColorFamily): Double? {
        if (a == b) return null
        affinities[setOf(a, b)]?.let { return it }
        return if (isNeutralFamily(a) && isNeutralFamily(b)) 0.75 else null
    }
}
