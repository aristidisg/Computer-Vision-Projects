package io.github.aristidisg.ensemble.model

import kotlinx.serialization.Serializable

@Serializable
enum class CategoryGroup(val label: String) {
    TOPS("Tops and mid layers"), OUTERWEAR("Outerwear"), BOTTOMS("Bottoms"),
    SHOES("Shoes"), ACCESSORIES("Accessories")
}

/**
 * Menswear subcategories (Appendix A). Only identity lives here; every default
 * (slots, formality, clo, tags, laundry...) is data in the engine's taxonomy table.
 */
@Serializable
enum class Subcategory(val label: String, val group: CategoryGroup) {
    T_SHIRT("T-shirt", CategoryGroup.TOPS),
    GRAPHIC_TEE("Graphic tee", CategoryGroup.TOPS),
    LONG_SLEEVE_TEE("Long-sleeve tee", CategoryGroup.TOPS),
    TANK_TOP("Tank top", CategoryGroup.TOPS),
    HENLEY("Henley", CategoryGroup.TOPS),
    POLO("Polo (piqué)", CategoryGroup.TOPS),
    KNIT_POLO("Knit polo", CategoryGroup.TOPS),
    OXFORD_SHIRT("Oxford shirt", CategoryGroup.TOPS),
    DRESS_SHIRT("Dress shirt", CategoryGroup.TOPS),
    LINEN_SHIRT("Linen shirt", CategoryGroup.TOPS),
    CAMP_COLLAR_SHIRT("Camp-collar shirt", CategoryGroup.TOPS),
    FLANNEL_SHIRT("Flannel shirt", CategoryGroup.TOPS),
    OVERSHIRT("Overshirt", CategoryGroup.TOPS),
    CREWNECK_SWEATSHIRT("Crewneck sweatshirt", CategoryGroup.TOPS),
    HOODIE("Hoodie", CategoryGroup.TOPS),
    QUARTER_ZIP("Quarter-zip", CategoryGroup.TOPS),
    MERINO_CREWNECK("Merino crewneck", CategoryGroup.TOPS),
    CHUNKY_KNIT("Chunky knit", CategoryGroup.TOPS),
    CARDIGAN("Cardigan", CategoryGroup.TOPS),
    TURTLENECK("Turtleneck", CategoryGroup.TOPS),
    SWEATER_VEST("Sweater vest", CategoryGroup.TOPS),
    GILET("Gilet / puffer vest", CategoryGroup.TOPS),
    FLEECE("Fleece", CategoryGroup.TOPS),

    DENIM_JACKET("Denim jacket", CategoryGroup.OUTERWEAR),
    BOMBER("Bomber", CategoryGroup.OUTERWEAR),
    HARRINGTON("Harrington", CategoryGroup.OUTERWEAR),
    CHORE_JACKET("Chore jacket", CategoryGroup.OUTERWEAR),
    COACH_JACKET("Coach / track jacket", CategoryGroup.OUTERWEAR),
    VARSITY_JACKET("Varsity jacket", CategoryGroup.OUTERWEAR),
    RAIN_SHELL("Rain shell", CategoryGroup.OUTERWEAR),
    UNSTRUCTURED_BLAZER("Unstructured blazer", CategoryGroup.OUTERWEAR),
    SPORT_COAT("Sport coat", CategoryGroup.OUTERWEAR),
    SUIT_JACKET("Suit jacket", CategoryGroup.OUTERWEAR),
    FIELD_JACKET("Field jacket", CategoryGroup.OUTERWEAR),
    LEATHER_JACKET("Leather jacket", CategoryGroup.OUTERWEAR),
    SUEDE_JACKET("Suede jacket", CategoryGroup.OUTERWEAR),
    TRENCH_COAT("Trench coat", CategoryGroup.OUTERWEAR),
    PEACOAT("Peacoat", CategoryGroup.OUTERWEAR),
    WOOL_OVERCOAT("Wool overcoat", CategoryGroup.OUTERWEAR),
    PARKA("Parka", CategoryGroup.OUTERWEAR),
    PUFFER_JACKET("Puffer jacket", CategoryGroup.OUTERWEAR),

    JEANS("Jeans", CategoryGroup.BOTTOMS),
    CHINOS("Chinos", CategoryGroup.BOTTOMS),
    WOOL_TROUSERS("Wool trousers", CategoryGroup.BOTTOMS),
    SUIT_TROUSERS("Suit trousers", CategoryGroup.BOTTOMS),
    PLEATED_TROUSERS("Pleated wide trousers", CategoryGroup.BOTTOMS),
    LINEN_TROUSERS("Linen trousers", CategoryGroup.BOTTOMS),
    CORDUROY_TROUSERS("Corduroy trousers", CategoryGroup.BOTTOMS),
    CARGO_PANTS("Cargo pants", CategoryGroup.BOTTOMS),
    JOGGERS("Joggers", CategoryGroup.BOTTOMS),
    SWEATPANTS("Sweatpants", CategoryGroup.BOTTOMS),
    TRACK_PANTS("Track pants", CategoryGroup.BOTTOMS),
    CHINO_SHORTS("Chino shorts", CategoryGroup.BOTTOMS),
    TAILORED_SHORTS("Tailored shorts", CategoryGroup.BOTTOMS),
    DENIM_SHORTS("Denim shorts", CategoryGroup.BOTTOMS),
    ATHLETIC_SHORTS("Athletic shorts", CategoryGroup.BOTTOMS),
    SWIM_SHORTS("Swim shorts", CategoryGroup.BOTTOMS),

    MINIMAL_SNEAKERS("Minimal leather sneakers", CategoryGroup.SHOES),
    CANVAS_SNEAKERS("Canvas sneakers", CategoryGroup.SHOES),
    CHUNKY_SNEAKERS("Chunky sneakers", CategoryGroup.SHOES),
    HIGH_TOP_SNEAKERS("High-top sneakers", CategoryGroup.SHOES),
    RUNNING_SHOES("Running shoes", CategoryGroup.SHOES),
    LOAFERS("Loafers", CategoryGroup.SHOES),
    DERBIES("Derbies", CategoryGroup.SHOES),
    OXFORDS("Oxfords", CategoryGroup.SHOES),
    BROGUES("Brogues", CategoryGroup.SHOES),
    MONK_STRAPS("Monk straps", CategoryGroup.SHOES),
    CHELSEA_BOOTS("Chelsea boots", CategoryGroup.SHOES),
    CHUKKA_BOOTS("Chukka / desert boots", CategoryGroup.SHOES),
    WORK_BOOTS("Work boots", CategoryGroup.SHOES),
    HIKING_BOOTS("Hiking boots", CategoryGroup.SHOES),
    BOAT_SHOES("Boat shoes", CategoryGroup.SHOES),
    ESPADRILLES("Espadrilles", CategoryGroup.SHOES),
    SANDALS("Sandals", CategoryGroup.SHOES),
    SLIDES("Slides", CategoryGroup.SHOES),

    LEATHER_BELT("Leather belt", CategoryGroup.ACCESSORIES),
    CASUAL_BELT("Casual belt (canvas, braided)", CategoryGroup.ACCESSORIES),
    DRESS_WATCH("Dress watch", CategoryGroup.ACCESSORIES),
    EVERYDAY_WATCH("Everyday watch (diver, field)", CategoryGroup.ACCESSORIES),
    SPORT_WATCH("Sport, digital or smartwatch", CategoryGroup.ACCESSORIES),
    BACKPACK("Backpack", CategoryGroup.ACCESSORIES),
    TOTE("Tote", CategoryGroup.ACCESSORIES),
    CROSSBODY("Crossbody / sling", CategoryGroup.ACCESSORIES),
    BRIEFCASE("Leather briefcase / messenger", CategoryGroup.ACCESSORIES),
    CAP("Cap", CategoryGroup.ACCESSORIES),
    BEANIE("Beanie", CategoryGroup.ACCESSORIES),
    BUCKET_HAT("Bucket hat", CategoryGroup.ACCESSORIES),
    SUNGLASSES("Sunglasses", CategoryGroup.ACCESSORIES),
    SCARF("Scarf", CategoryGroup.ACCESSORIES),
    TIE("Tie", CategoryGroup.ACCESSORIES),
    POCKET_SQUARE("Pocket square", CategoryGroup.ACCESSORIES),
    NECKLACE("Necklace / chain", CategoryGroup.ACCESSORIES),
    BRACELET("Bracelet", CategoryGroup.ACCESSORIES),
    RING("Ring", CategoryGroup.ACCESSORIES),
    GLOVES("Gloves", CategoryGroup.ACCESSORIES);
}

/** Color families (Appendix B1). Hex, neutralness and temperature live in the engine's tables. */
@Serializable
enum class ColorFamily(val label: String) {
    BLACK("Black"), CHARCOAL("Charcoal"), MID_GREY("Mid grey"), LIGHT_GREY("Light grey"),
    WHITE("White"), ECRU("Ecru / cream"), STONE("Stone / beige"), KHAKI("Khaki"), TAUPE("Taupe"),
    CAMEL("Camel / tan"), BROWN("Brown"), CHOCOLATE("Chocolate"), OLIVE("Olive"), NAVY("Navy"),
    DARK_DENIM("Dark denim"), MID_DENIM("Mid denim"), LIGHT_DENIM("Light denim"),
    LIGHT_BLUE("Light blue"), BLUE("Blue"), COBALT("Cobalt"), TEAL("Teal"), FOREST_GREEN("Forest green"),
    SAGE("Sage"), KELLY_GREEN("Kelly green"), MINT("Mint"), BURGUNDY("Burgundy"), RED("Red"),
    RUST("Rust / terracotta"), ORANGE("Orange"), MUSTARD("Mustard"), YELLOW("Yellow"),
    DUSTY_PINK("Dusty pink"), PINK("Pink"), LAVENDER("Lavender"), PLUM("Plum"),
    MULTICOLOR("Multicolor print");

    val isDenim: Boolean get() = this == DARK_DENIM || this == MID_DENIM || this == LIGHT_DENIM
}
