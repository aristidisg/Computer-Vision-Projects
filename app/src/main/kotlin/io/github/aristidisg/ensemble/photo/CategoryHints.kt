package io.github.aristidisg.ensemble.photo

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.Subcategory.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Category hints (SPEC 3 step 7): ML Kit's on-device labels mapped to subcategory candidates
 * through a lookup table. Only suggestions: the user's tap decides, nothing is auto-committed.
 * The default model's labels are coarse, so a broad label maps to its most common pieces.
 */
object CategoryHints {
    private val table: Map<String, List<Subcategory>> = mapOf(
        "jeans" to listOf(JEANS, DENIM_SHORTS, DENIM_JACKET),
        "denim" to listOf(JEANS, DENIM_JACKET, DENIM_SHORTS),
        "shorts" to listOf(CHINO_SHORTS, DENIM_SHORTS, ATHLETIC_SHORTS),
        "pants" to listOf(CHINOS, WOOL_TROUSERS, JOGGERS),
        "trousers" to listOf(CHINOS, WOOL_TROUSERS, SUIT_TROUSERS),
        "shirt" to listOf(OXFORD_SHIRT, DRESS_SHIRT, T_SHIRT),
        "t-shirt" to listOf(T_SHIRT, GRAPHIC_TEE, LONG_SLEEVE_TEE),
        "jersey" to listOf(T_SHIRT, POLO, LONG_SLEEVE_TEE),
        "sleeve" to listOf(LONG_SLEEVE_TEE, OXFORD_SHIRT, CREWNECK_SWEATSHIRT),
        "sweater" to listOf(MERINO_CREWNECK, CHUNKY_KNIT, CARDIGAN),
        "sweatshirt" to listOf(CREWNECK_SWEATSHIRT, HOODIE, QUARTER_ZIP),
        "jacket" to listOf(BOMBER, HARRINGTON, FIELD_JACKET),
        "coat" to listOf(WOOL_OVERCOAT, TRENCH_COAT, PEACOAT),
        "suit" to listOf(SUIT_JACKET, SPORT_COAT, SUIT_TROUSERS),
        "blazer" to listOf(UNSTRUCTURED_BLAZER, SPORT_COAT, SUIT_JACKET),
        "outerwear" to listOf(PARKA, PUFFER_JACKET, RAIN_SHELL),
        "vest" to listOf(GILET, SWEATER_VEST),
        "shoe" to listOf(MINIMAL_SNEAKERS, DERBIES, LOAFERS),
        "sneakers" to listOf(MINIMAL_SNEAKERS, CANVAS_SNEAKERS, RUNNING_SHOES),
        "boot" to listOf(CHELSEA_BOOTS, CHUKKA_BOOTS, WORK_BOOTS),
        "sandal" to listOf(SANDALS, SLIDES, ESPADRILLES),
        "hat" to listOf(CAP, BEANIE, BUCKET_HAT),
        "cap" to listOf(CAP, BEANIE),
        "sunglasses" to listOf(SUNGLASSES),
        "glasses" to listOf(SUNGLASSES),
        "watch" to listOf(EVERYDAY_WATCH, DRESS_WATCH, SPORT_WATCH),
        "bag" to listOf(BACKPACK, TOTE, CROSSBODY),
        "handbag" to listOf(TOTE, CROSSBODY, BRIEFCASE),
        "backpack" to listOf(BACKPACK),
        "scarf" to listOf(SCARF),
        "tie" to listOf(TIE),
        "belt" to listOf(LEATHER_BELT, CASUAL_BELT),
        "glove" to listOf(GLOVES),
        "jewellery" to listOf(NECKLACE, BRACELET, RING),
        "jewelry" to listOf(NECKLACE, BRACELET, RING),
        "necklace" to listOf(NECKLACE),
        "bracelet" to listOf(BRACELET),
        "ring" to listOf(RING),
        "leather" to listOf(LEATHER_JACKET, LEATHER_BELT, CHELSEA_BOOTS),
    )

    /** Up to 3 candidates; empty when labels are too coarse (the UI then shows the full grid). */
    suspend fun suggest(b: Bitmap): List<Subcategory> = runCatching {
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
        try {
            val labels = withTimeoutOrNull(8_000) { labeler.process(InputImage.fromBitmap(b, 0)).await() } ?: return@runCatching emptyList()
            labels.sortedByDescending { it.confidence }
                .flatMap { l -> table.entries.filter { (k, _) -> l.text.lowercase().contains(k) }.flatMap { it.value } }
                .distinct().take(3)
        } finally {
            labeler.close()
        }
    }.getOrDefault(emptyList())
}
