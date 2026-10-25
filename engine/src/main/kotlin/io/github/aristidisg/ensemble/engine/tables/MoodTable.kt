package io.github.aristidisg.ensemble.engine.tables

import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorFamily.*
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.Mood
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.Subcategory.*

/** What a mood favours: its palette, signature pieces, fabrics, style tags and cut. */
data class MoodProfile(
    val palette: Set<ColorFamily>,
    val pieces: Set<Subcategory>,
    val materials: Set<Material> = emptySet(),
    val styles: Set<StyleTag> = emptySet(),
    val cuts: Set<Silhouette> = emptySet(),
    /** Added to boldness while the mood is on. */
    val boldness: Double = 0.0,
)

/** The moods as data (see Mood). Matching is in MoodRules. */
object MoodTable {
    private val profiles: Map<Mood, MoodProfile> = mapOf(
        Mood.SEOUL_MINIMAL to MoodProfile(
            palette = setOf(BLACK, CHARCOAL, MID_GREY, LIGHT_GREY, WHITE, ECRU, STONE, TAUPE, NAVY, SAGE),
            pieces = setOf(T_SHIRT, LONG_SLEEVE_TEE, CARDIGAN, OVERSHIRT, MERINO_CREWNECK, PLEATED_TROUSERS, WOOL_TROUSERS,
                WOOL_OVERCOAT, TRENCH_COAT, MINIMAL_SNEAKERS, LOAFERS, TOTE, CROSSBODY),
            materials = setOf(Material.WOOL, Material.MERINO, Material.COTTON, Material.KNIT),
            styles = setOf(StyleTag.MINIMAL),
            cuts = setOf(Silhouette.RELAXED, Silhouette.OVERSIZED),
            boldness = -0.1,
        ),
        Mood.ITALIAN_SUMMER to MoodProfile(
            palette = setOf(WHITE, ECRU, STONE, LIGHT_BLUE, NAVY, CAMEL, BROWN, OLIVE, RUST),
            pieces = setOf(LINEN_SHIRT, CAMP_COLLAR_SHIRT, KNIT_POLO, POLO, LINEN_TROUSERS, PLEATED_TROUSERS, TAILORED_SHORTS,
                CHINO_SHORTS, UNSTRUCTURED_BLAZER, LOAFERS, ESPADRILLES, BOAT_SHOES, SUNGLASSES),
            materials = setOf(Material.LINEN, Material.SEERSUCKER, Material.COTTON, Material.SUEDE),
            styles = setOf(StyleTag.RESORT, StyleTag.SMART_CASUAL),
            boldness = 0.05,
        ),
        Mood.NINETIES_STREET to MoodProfile(
            palette = setOf(BLACK, WHITE, MID_GREY, LIGHT_DENIM, MID_DENIM, NAVY, RED, COBALT, KELLY_GREEN, YELLOW, MULTICOLOR),
            pieces = setOf(GRAPHIC_TEE, HOODIE, CREWNECK_SWEATSHIRT, COACH_JACKET, VARSITY_JACKET, BOMBER, DENIM_JACKET,
                CARGO_PANTS, JEANS, TRACK_PANTS, CHUNKY_SNEAKERS, HIGH_TOP_SNEAKERS, CAP, BUCKET_HAT, BACKPACK),
            materials = setOf(Material.DENIM, Material.JERSEY, Material.NYLON_TECH),
            styles = setOf(StyleTag.STREETWEAR, StyleTag.SPORTY),
            cuts = setOf(Silhouette.OVERSIZED, Silhouette.RELAXED),
            boldness = 0.25,
        ),
        Mood.OLD_MONEY to MoodProfile(
            palette = setOf(NAVY, ECRU, WHITE, STONE, CAMEL, CHOCOLATE, BROWN, BURGUNDY, FOREST_GREEN, LIGHT_BLUE, CHARCOAL),
            pieces = setOf(OXFORD_SHIRT, KNIT_POLO, MERINO_CREWNECK, CHUNKY_KNIT, CARDIGAN, SWEATER_VEST, SPORT_COAT,
                UNSTRUCTURED_BLAZER, WOOL_OVERCOAT, PEACOAT, WOOL_TROUSERS, PLEATED_TROUSERS, CHINOS, LOAFERS, BROGUES,
                DRESS_WATCH, LEATHER_BELT),
            materials = setOf(Material.WOOL, Material.MERINO, Material.CASHMERE, Material.LINEN, Material.SUEDE, Material.TWEED, Material.CORDUROY),
            styles = setOf(StyleTag.CLASSIC, StyleTag.PREPPY),
            cuts = setOf(Silhouette.REGULAR, Silhouette.SLIM),
            boldness = -0.1,
        ),
        Mood.WORKWEAR to MoodProfile(
            palette = setOf(OLIVE, KHAKI, BROWN, RUST, NAVY, DARK_DENIM, MID_DENIM, ECRU, STONE, MUSTARD, CHARCOAL),
            pieces = setOf(CHORE_JACKET, FIELD_JACKET, DENIM_JACKET, OVERSHIRT, FLANNEL_SHIRT, HENLEY, T_SHIRT, JEANS,
                CARGO_PANTS, CORDUROY_TROUSERS, WORK_BOOTS, CHUKKA_BOOTS, BEANIE, CASUAL_BELT),
            materials = setOf(Material.CANVAS, Material.DENIM, Material.CORDUROY, Material.FLANNEL, Material.LEATHER),
            styles = setOf(StyleTag.RUGGED),
        ),
        Mood.GORPCORE to MoodProfile(
            palette = setOf(OLIVE, FOREST_GREEN, ORANGE, MUSTARD, BLACK, TEAL, SAGE, KHAKI, COBALT, MID_GREY),
            pieces = setOf(FLEECE, GILET, RAIN_SHELL, PARKA, PUFFER_JACKET, QUARTER_ZIP, CARGO_PANTS, TRACK_PANTS,
                HIKING_BOOTS, RUNNING_SHOES, CROSSBODY, BEANIE, CAP),
            materials = setOf(Material.NYLON_TECH, Material.FLEECE),
            styles = setOf(StyleTag.OUTDOOR, StyleTag.TECHWEAR, StyleTag.SPORTY),
            boldness = 0.15,
        ),
    )

    fun of(mood: Mood): MoodProfile = profiles.getValue(mood)
}
