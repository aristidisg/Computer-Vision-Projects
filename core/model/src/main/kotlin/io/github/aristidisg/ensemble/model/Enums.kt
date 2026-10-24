package io.github.aristidisg.ensemble.model

import kotlinx.serialization.Serializable

/** Outfit slots. The five core slots make up the outfit; the rest are accessories. */
@Serializable
enum class Slot(val isCore: Boolean, val label: String) {
    TOP(true, "Top"),
    MID(true, "Mid layer"),
    OUTER(true, "Outer layer"),
    BOTTOM(true, "Bottom"),
    SHOES(true, "Shoes"),
    BELT(false, "Belt"),
    WATCH(false, "Watch"),
    BAG(false, "Bag"),
    HEADWEAR(false, "Headwear"),
    EYEWEAR(false, "Sunglasses"),
    NECK(false, "Scarf / tie"),
    POCKET(false, "Pocket square"),
    JEWELRY(false, "Jewelry"),
    HANDS(false, "Gloves");

    companion object {
        val core = entries.filter { it.isCore }
        val accessories = entries.filterNot { it.isCore }
    }
}

/** How heavy an outer layer is; NONE for anything that is not outerwear. */
@Serializable
enum class OuterWeight { NONE, LIGHT, STANDARD, WARM }

@Serializable
enum class PatternType(val label: String) {
    SOLID("Solid"), STRIPES("Stripes"), CHECK("Check"), HERRINGBONE("Herringbone"),
    HOUNDSTOOTH("Houndstooth"), FLORAL("Floral"), GRAPHIC("Graphic"), LOGO("Logo"),
    CAMO("Camo"), TEXTURE("Texture only")
}

@Serializable
enum class PatternScale { NONE, SMALL, LARGE }

@Serializable
enum class Material(val label: String) {
    COTTON("Cotton"), LINEN("Linen"), WOOL("Wool"), MERINO("Merino"), CASHMERE("Cashmere"),
    DENIM("Denim"), LEATHER("Leather"), SUEDE("Suede"), CORDUROY("Corduroy"), FLANNEL("Flannel"),
    TWEED("Tweed"), NYLON_TECH("Nylon / tech"), FLEECE("Fleece"), KNIT("Knit"), JERSEY("Jersey"),
    CANVAS("Canvas"), SEERSUCKER("Seersucker")
}

@Serializable
enum class FabricWeight { LIGHT, MID, HEAVY }

@Serializable
enum class WaterResistance { NONE, RESISTANT, WATERPROOF }

@Serializable
enum class StyleTag(val label: String) {
    MINIMAL("Minimal"), SMART_CASUAL("Smart casual"), CLASSIC("Classic"), STREETWEAR("Streetwear"),
    SPORTY("Sporty"), RUGGED("Rugged"), OUTDOOR("Outdoor"), PREPPY("Preppy"), RESORT("Resort"),
    TECHWEAR("Techwear")
}

@Serializable
enum class Season(val label: String) { SPRING("Spring"), SUMMER("Summer"), AUTUMN("Autumn"), WINTER("Winter") }

@Serializable
enum class Silhouette(val label: String) { SLIM("Slim"), REGULAR("Regular"), RELAXED("Relaxed"), OVERSIZED("Oversized") }

@Serializable
enum class ShoeProfile { SLEEK, STANDARD, CHUNKY }

@Serializable
enum class ShoeUpper { LEATHER, SUEDE, CANVAS, KNIT, SYNTHETIC, RUBBER }

@Serializable
enum class ShoeSole { LEATHER, RUBBER, LUG }

@Serializable
enum class MetalTone(val label: String) { GOLD("Gold"), SILVER("Silver"), GUNMETAL("Gunmetal"), ROSE("Rose gold"), BLACK("Black") }

@Serializable
enum class ItemStatus(val label: String) {
    AVAILABLE("Available"), LAUNDRY("In laundry"), TAILOR("At tailor"),
    STORED("Stored"), LENT("Lent"), RETIRED("Retired")
}

@Serializable
enum class ColorRole { PRIMARY, SECONDARY, ACCENT }

@Serializable
enum class Temperature { WARM, NEUTRAL, COOL }

@Serializable
enum class Activity { SEDENTARY, LIGHT, ACTIVE }

@Serializable
enum class ScenarioPalette { ANY, CONSERVATIVE, CONSERVATIVE_LEANING, DARK_MUTED, LIGHT, EVENING, NO_DOMINANT_WHITE }

@Serializable
enum class OutfitSource { GENERATED, COMPOSED, RECREATED, PLANNED }

@Serializable
enum class Strictness(val label: String, val boldness: Double) {
    CLASSIC("Classic", 0.2), BALANCED("Balanced", 0.4), ADVENTUROUS("Adventurous", 0.75)
}

@Serializable
enum class Thermal { TOO_COLD, OK, TOO_WARM }

@Serializable
enum class HueWheel { ARTIST, PERCEPTUAL }
