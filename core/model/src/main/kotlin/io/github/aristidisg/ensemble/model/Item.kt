package io.github.aristidisg.ensemble.model

import kotlinx.serialization.Serializable

/** One measured color of an item. L/a/b are OKLab; share is 0..1 of the item's visible area. */
@Serializable
data class ItemColor(
    val hex: String,
    val l: Double,
    val a: Double,
    val b: Double,
    val family: ColorFamily,
    val share: Double,
    val role: ColorRole = ColorRole.PRIMARY,
)

@Serializable
data class Pattern(
    val type: PatternType = PatternType.SOLID,
    val scale: PatternScale = PatternScale.NONE,
    val loudness: Int = 0,
) {
    val isPatterned: Boolean get() = type != PatternType.SOLID
}

@Serializable
data class Layering(
    val canWearOpen: Boolean = false,
    val presentableAlone: Boolean = true,
    val bulk: Int = 1,
)

@Serializable
data class ShoeExtras(
    val profile: ShoeProfile = ShoeProfile.STANDARD,
    val upper: ShoeUpper = ShoeUpper.LEATHER,
    val sole: ShoeSole = ShoeSole.RUBBER,
    val weatherproof: Boolean = false,
    val restDays: Int = 0,
)

@Serializable
data class AccessoryExtras(
    val leatherColor: ColorFamily? = null,
    val metalTone: MetalTone? = null,
)

@Serializable
data class Laundry(val wearsBeforeWash: Int = 1, val wearsSinceWash: Int = 0) {
    val atThreshold: Boolean get() = wearsBeforeWash in 1..wearsSinceWash
}

@Serializable
data class ItemFlags(
    val favorite: Boolean = false,
    val signature: Boolean = false,
    val neverSuggest: Boolean = false,
)

@Serializable
data class ItemMeta(
    val brand: String = "",
    val size: String = "",
    val price: Double? = null,
    val purchaseEpochDay: Long? = null,
    val notes: String = "",
    val customTags: Set<String> = emptySet(),
)

@Serializable
data class ItemPhotos(
    val original: String? = null,
    val cutout: String? = null,
    val thumb: String? = null,
) {
    /** Best image for display: cutout, then original. */
    val display: String? get() = cutout ?: original
}

/** A piece of clothing. Tags arrive pre-filled from its subcategory and are user-editable. */
@Serializable
data class Item(
    val id: String,
    val name: String,
    val subcategory: Subcategory,
    val slots: List<Slot>,
    val colors: List<ItemColor>,
    val pattern: Pattern = Pattern(),
    val materials: List<Material> = emptyList(),
    val fabricWeight: FabricWeight = FabricWeight.MID,
    val warmthClo: Double = 0.0,
    val waterResistance: WaterResistance = WaterResistance.NONE,
    val formality: Int = 5,
    val styleTags: Set<StyleTag> = emptySet(),
    val seasons: Set<Season> = Season.entries.toSet(),
    val silhouette: Silhouette = Silhouette.REGULAR,
    val layering: Layering = Layering(),
    val shoe: ShoeExtras? = null,
    val accessory: AccessoryExtras? = null,
    val laundry: Laundry = Laundry(),
    val status: ItemStatus = ItemStatus.AVAILABLE,
    val flags: ItemFlags = ItemFlags(),
    val meta: ItemMeta = ItemMeta(),
    val photos: ItemPhotos = ItemPhotos(),
    val createdEpochDay: Long = 0,
    val updatedAtMillis: Long = 0,
) {
    val primarySlot: Slot get() = slots.first()
    val primaryColor: ItemColor? get() = colors.maxByOrNull { it.share }
    val isAvailable: Boolean get() = status == ItemStatus.AVAILABLE && !flags.neverSuggest
}

/** A piece placed in a slot of an outfit. */
@Serializable
data class OutfitPiece(val itemId: String, val slot: Slot)

@Serializable
data class Outfit(
    val id: String,
    val pieces: List<OutfitPiece>,
    val source: OutfitSource = OutfitSource.GENERATED,
    val name: String = "",
    val scenarioId: String? = null,
    val liked: Boolean = false,
    val rating: Int? = null,
    val cooldownDays: Int? = null,
    val notes: String = "",
    val createdAtMillis: Long = 0,
    val neverShow: Boolean = false,
) {
    val itemIds: List<String> get() = pieces.map { it.itemId }
    val signature: String get() = signatureOf(itemIds)

    companion object {
        /** Dedupe key: a stable 64-bit FNV-1a hash of the sorted item IDs. */
        fun signatureOf(itemIds: Collection<String>): String {
            var h = -0x340d631b7bdddcdbL
            for (id in itemIds.sorted()) {
                for (ch in id) {
                    h = h xor ch.code.toLong()
                    h *= 0x100000001b3L
                }
                h = h xor '|'.code.toLong()
                h *= 0x100000001b3L
            }
            return java.lang.Long.toHexString(h)
        }
    }
}
