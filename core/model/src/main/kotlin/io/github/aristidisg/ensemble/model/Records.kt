package io.github.aristidisg.ensemble.model

import kotlinx.serialization.Serializable

@Serializable
enum class FeedbackType {
    SWIPE_YES, SWIPE_NO, SWIPE_UP_WEAR, SAVE, COMPOSED, PIECE_UP, PIECE_DOWN,
    CHECK_IN, INSPIRATION_ADDED, NEVER_AGAIN
}

/** "Why not?" chips (SPEC 7). */
@Serializable
enum class WhyNot(val label: String) {
    COLORS("Colors don't work"),
    TOO_FORMAL("Too formal"),
    TOO_CASUAL("Too casual"),
    TOO_WARM("Too warm"),
    TOO_COLD("Too cold"),
    FIT("Fit / proportions"),
    NOT_THIS_PIECE("Not this piece"),
    TOO_BORING("Too boring"),
    TOO_LOUD("Too loud"),
    WORE_RECENTLY("Wore it recently"),
    NOT_TODAY("Not today"),
}

@Serializable
data class ContextSnapshot(
    val scenarioId: String,
    val season: Season,
    val tempBand: String,
    val weekday: Int,
    val feelsMin: Double,
    val feelsMax: Double,
)

@Serializable
data class CheckInPayload(val comfort: Int, val thermal: Thermal, val felt: Int, val note: String = "")

/** Append-only feedback log entry. Learned stats can always be rebuilt from these. */
@Serializable
data class FeedbackEvent(
    val id: String,
    val epochMillis: Long,
    val epochDay: Long,
    val type: FeedbackType,
    val outfitSignature: String?,
    val pieces: List<OutfitPiece>,
    val targetItemId: String? = null,
    val reasons: List<WhyNot> = emptyList(),
    val checkIn: CheckInPayload? = null,
    val context: ContextSnapshot,
    /** Feature keys extracted when the event was recorded, so a rebuild never depends on later edits. */
    val features: List<String> = emptyList(),
    /** Pair features of the target piece (piece thumbs) or the outfit (never again). */
    val targetFeatures: List<String> = emptyList(),
    val resetEpoch: Int = 0,
)

@Serializable
data class PreferenceStat(
    val featureKey: String,
    val contextKey: String,
    val pos: Double,
    val neg: Double,
    val lastUpdatedEpochDay: Long,
)

@Serializable
data class WearLog(
    val id: String,
    val epochDay: Long,
    val outfitSignature: String,
    val pieces: List<OutfitPiece>,
    val scenarioId: String,
    val weather: WindowWeather? = null,
)

@Serializable
data class CheckIn(
    val id: String,
    val epochDay: Long,
    val outfitSignature: String,
    val payload: CheckInPayload,
)

@Serializable
data class DayPlan(
    val epochDay: Long,
    val scenarioId: String,
    val pieces: List<OutfitPiece> = emptyList(),
    val locked: Boolean = false,
    val note: String = "",
) {
    val outfitSignature: String? get() = if (pieces.isEmpty()) null else Outfit.signatureOf(pieces.map { it.itemId })
}

@Serializable
data class TripDay(val epochDay: Long, val scenarioId: String, val pieces: List<OutfitPiece> = emptyList())

@Serializable
data class Trip(
    val id: String,
    val name: String,
    val destination: LatLon?,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val days: List<TripDay>,
    val luggageLimit: Int = 14,
    val shoeLimit: Int = 2,
    val laundryAvailable: Boolean = false,
    val expectedMin: Double? = null,
    val expectedMax: Double? = null,
    val packedItemIds: List<String> = emptyList(),
    val checkedItemIds: Set<String> = emptySet(),
    val travelDayItemIds: List<String> = emptyList(),
)

@Serializable
data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

@Serializable
data class LookPiece(
    val subcategory: Subcategory,
    val hex: String,
    val family: ColorFamily,
    val pattern: PatternType = PatternType.SOLID,
    val silhouette: Silhouette = Silhouette.REGULAR,
    val crop: CropBox? = null,
)

@Serializable
data class InspirationLook(
    val id: String,
    val photoPath: String?,
    val pieces: List<LookPiece>,
    val styleTags: Set<StyleTag> = emptySet(),
    val notes: String = "",
    val createdAtMillis: Long = 0,
)
