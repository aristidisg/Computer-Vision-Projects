package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.model.Subcategory.*
import kotlinx.serialization.Serializable

/** How to wear an outfit: the small moves a stylist makes. Text lives in the app's strings. */
@Serializable
enum class Tip(val text: String) {
    FRENCH_TUCK("French tuck the front of the top: shows the waist, keeps it relaxed."),
    FULL_TUCK("Tuck the shirt in fully for a clean line."),
    UNTUCKED("Wear it untucked: it's cut to sit at the hip."),
    ROLL_SLEEVES("Roll the sleeves twice, just below the elbow."),
    PUSH_SLEEVES("Push the sleeves up to the forearm."),
    CUFF_OVER_BOOTS("One slim cuff so the jeans sit just on the boots."),
    CUFF_ANKLE("Cuff the jeans once to show a little ankle."),
    COLLAR_OUT("Collar out over the knit to frame the face."),
    OPEN_LAYER("Wear the layer open, like a light jacket."),
    HALF_ZIP("Zip it halfway to show the collar underneath."),
    HEM_PEEK("Let the tee's hem peek out under the sweater."),
    NO_BREAK("Hems just touching the shoes: no break."),
    SHORTS_ROLL("Roll the shorts' hem once, above the knee."),
    BLAZER_OPEN("Leave the jacket unbuttoned and the sleeves pushed up a little."),
}

/**
 * One tip: when a [piece] of one of these subcategories is in the outfit, optionally [with] a
 * piece of another, inside a feels-like window and formality range.
 */
data class TipRule(
    val tip: Tip,
    val piece: Set<Subcategory>,
    val with: Set<Subcategory> = emptySet(),
    val minFeels: Double? = null,
    val maxFeels: Double? = null,
    val minFormality: Double? = null,
    val maxFormality: Double? = null,
    /** False for tips about a piece under another (collar out, hem peek). */
    val pieceVisible: Boolean = true,
)

object StylingTips {
    private val shirts = setOf(OXFORD_SHIRT, LINEN_SHIRT, FLANNEL_SHIRT, CAMP_COLLAR_SHIRT)
    private val tailoredBottoms = setOf(PLEATED_TROUSERS, WOOL_TROUSERS, SUIT_TROUSERS, CHINOS, LINEN_TROUSERS)
    private val boots = setOf(CHELSEA_BOOTS, CHUKKA_BOOTS, WORK_BOOTS)

    /** Only one of these per outfit: they all say how the top meets the trousers. */
    private val tucks = setOf(Tip.FULL_TUCK, Tip.FRENCH_TUCK, Tip.UNTUCKED, Tip.HEM_PEEK)

    /** In priority order; an outfit gets the first two that apply, at most one tuck. */
    val table: List<TipRule> = listOf(
        TipRule(Tip.FULL_TUCK, setOf(DRESS_SHIRT, OXFORD_SHIRT), with = setOf(WOOL_TROUSERS, SUIT_TROUSERS, PLEATED_TROUSERS), minFormality = 5.5),
        TipRule(Tip.FRENCH_TUCK, setOf(T_SHIRT, LONG_SLEEVE_TEE, HENLEY, POLO, KNIT_POLO), with = tailoredBottoms),
        TipRule(Tip.COLLAR_OUT, setOf(OXFORD_SHIRT, CAMP_COLLAR_SHIRT, POLO, DRESS_SHIRT), with = setOf(MERINO_CREWNECK, CREWNECK_SWEATSHIRT, CHUNKY_KNIT, SWEATER_VEST), pieceVisible = false),
        TipRule(Tip.HEM_PEEK, setOf(T_SHIRT, LONG_SLEEVE_TEE), with = setOf(CREWNECK_SWEATSHIRT, MERINO_CREWNECK, HOODIE), maxFormality = 5.0, pieceVisible = false),
        TipRule(Tip.UNTUCKED, setOf(CAMP_COLLAR_SHIRT, FLANNEL_SHIRT, LINEN_SHIRT, GRAPHIC_TEE), maxFormality = 5.0),
        TipRule(Tip.ROLL_SLEEVES, shirts, minFeels = 17.0, maxFormality = 6.0),
        TipRule(Tip.BLAZER_OPEN, setOf(UNSTRUCTURED_BLAZER, SPORT_COAT), minFeels = 14.0, maxFormality = 6.5),
        TipRule(Tip.PUSH_SLEEVES, setOf(MERINO_CREWNECK, CARDIGAN, CREWNECK_SWEATSHIRT, CHORE_JACKET, DENIM_JACKET), minFeels = 14.0),
        TipRule(Tip.OPEN_LAYER, setOf(OVERSHIRT, CARDIGAN)),
        TipRule(Tip.HALF_ZIP, setOf(QUARTER_ZIP)),
        TipRule(Tip.CUFF_OVER_BOOTS, setOf(JEANS), with = boots),
        TipRule(Tip.CUFF_ANKLE, setOf(JEANS), with = setOf(LOAFERS, MINIMAL_SNEAKERS, CANVAS_SNEAKERS, BOAT_SHOES), minFeels = 15.0),
        TipRule(Tip.NO_BREAK, setOf(WOOL_TROUSERS, SUIT_TROUSERS, PLEATED_TROUSERS), with = setOf(LOAFERS, OXFORDS, DERBIES, MONK_STRAPS)),
        TipRule(Tip.SHORTS_ROLL, setOf(CHINO_SHORTS, TAILORED_SHORTS)),
    )

    fun tips(look: Look, ctx: EngineContext, max: Int = 2): List<Tip> {
        val feels = ctx.weather.feelsMid
        val out = ArrayList<Tip>()
        for (r in table) {
            if (out.size >= max) break
            val p = look.placed.firstOrNull { it.item.subcategory in r.piece } ?: continue
            if (r.tip in tucks && out.any { it in tucks }) continue
            if (r.pieceVisible && !look.isVisible(p.slot)) continue
            if (r.with.isNotEmpty() && look.placed.none { it.item !== p.item && it.item.subcategory in r.with }) continue
            if (r.minFeels != null && feels < r.minFeels) continue
            if (r.maxFeels != null && feels > r.maxFeels) continue
            if (r.minFormality != null && look.meanFormality < r.minFormality) continue
            if (r.maxFormality != null && look.meanFormality > r.maxFormality) continue
            out += r.tip
        }
        return out
    }
}
