package io.github.aristidisg.ensemble.engine.tables

import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorFamily.*
import kotlinx.serialization.Serializable

/**
 * A proven colour combination from menswear editorials. Every part must be visible in the
 * outfit; a part lists the families that play that role (navy, or camel / tan...).
 */
@Serializable
enum class ColorFormula(val label: String, val parts: List<Set<ColorFamily>>) {
    NAVY_CAMEL_WHITE("Navy, camel and white", listOf(setOf(NAVY), setOf(CAMEL, KHAKI), setOf(WHITE, ECRU))),
    OLIVE_ECRU_BROWN("Olive, ecru and brown", listOf(setOf(OLIVE), setOf(ECRU, STONE, WHITE), setOf(BROWN, CHOCOLATE, CAMEL))),
    BLACK_GREY_RED("Black, grey and one red", listOf(setOf(BLACK), setOf(CHARCOAL, MID_GREY, LIGHT_GREY), setOf(RED, BURGUNDY))),
    RUST_TEAL_CREAM("Rust, teal and cream", listOf(setOf(RUST, ORANGE), setOf(TEAL), setOf(ECRU, WHITE, STONE))),
    DENIM_WHITE_TAN("Denim, white and tan", listOf(setOf(DARK_DENIM, MID_DENIM, LIGHT_DENIM), setOf(WHITE, ECRU), setOf(CAMEL, BROWN))),
    GREY_NAVY_BURGUNDY("Grey, navy and burgundy", listOf(setOf(CHARCOAL, MID_GREY), setOf(NAVY), setOf(BURGUNDY, PLUM))),
    BLACK_WHITE_COBALT("Black, white and cobalt", listOf(setOf(BLACK), setOf(WHITE), setOf(COBALT, BLUE))),
    SAGE_STONE_WHITE("Sage, stone and white", listOf(setOf(SAGE, MINT), setOf(STONE, TAUPE), setOf(WHITE, ECRU))),
    FOREST_CAMEL_CREAM("Forest, camel and cream", listOf(setOf(FOREST_GREEN), setOf(CAMEL, BROWN), setOf(ECRU, STONE))),
    LAVENDER_GREY("Lavender and grey", listOf(setOf(LAVENDER, DUSTY_PINK), setOf(MID_GREY, LIGHT_GREY, CHARCOAL))),
    MUSTARD_NAVY("Mustard and navy", listOf(setOf(MUSTARD, YELLOW), setOf(NAVY))),
    CREAM_CHOCOLATE("Cream and chocolate", listOf(setOf(ECRU, STONE), setOf(CHOCOLATE, BROWN))),
}

/** A deliberate break of a classic rule: allowed (and named) on one cover look per issue. */
@Serializable
enum class RuleBreak(val label: String, val how: String) {
    NAVY_WITH_BLACK("Navy with black", "keep both deep and let the texture tell them apart"),
    DOUBLE_DENIM("Double denim", "two clearly different washes, nothing else blue"),
    BROWN_WITH_BLACK("Brown shoes with black", "a rich brown, polished, against true black"),
    SNEAKERS_WITH_TAILORING("Sneakers with tailoring", "clean, minimal sneakers and a sharp jacket"),
}

/**
 * A Vogue stylist "issue": the idea every cover look of a deck shares. Which issue runs is
 * rotated by day among those your wardrobe can fill (see CoverRules.issue).
 */
@Serializable
enum class Issue(val label: String) {
    TONAL("The Tonal Issue"),
    ONE_BOLD_THING("One Bold Thing"),
    OFF_DUTY_TAILORING("Tailoring, Off Duty"),
    ALL_BLACK("All Black Everything"),
    TEXTURE_STUDY("Texture Study"),
    COLOR_BLOCK("Colour Block Party"),
    FORMULA("The Formula Issue"),
    SEOUL_LAYERS("Seoul Layers"),
    LIGHT_AND_DARK("Light & Dark"),
}
