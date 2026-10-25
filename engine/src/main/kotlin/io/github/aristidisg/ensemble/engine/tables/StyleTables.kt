package io.github.aristidisg.ensemble.engine.tables

import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag

/** Appendix B3-B5 and the SPEC 5.5 silhouette table as data. */
object StyleTables {

    /** B3 slot area weights (rules C3, C4). */
    object Area {
        const val OUTER_CLOSED = 0.40
        const val OUTER_OPEN = 0.25
        const val TOP_ALONE = 0.30
        const val TOP_UNDER_OPEN = 0.15
        const val TOP_UNDER_CLOSED = 0.08
        /** Under a pullover and a closed coat: only a sliver of collar. */
        const val TOP_FULLY_HIDDEN = 0.03
        const val MID = 0.25
        const val MID_UNDER_CLOSED = 0.10
        const val BOTTOM = 0.30
        const val SHOES = 0.07
        const val BAG = 0.05
        const val SCARF = 0.05
        const val HEADWEAR = 0.03
        const val BELT = 0.02
        const val SMALL = 0.01

        fun accessory(slot: Slot): Double = when (slot) {
            Slot.BAG -> BAG
            Slot.NECK -> SCARF
            Slot.HEADWEAR -> HEADWEAR
            Slot.BELT -> BELT
            Slot.HANDS -> 0.02
            else -> SMALL
        }
    }

    private const val Y = 1.0
    private const val T = 0.5
    private const val X = 0.0

    /** B4 shoe x trouser table (rule SH2). Columns follow [TrouserKind] order. */
    private val shoeTrouser: Map<ShoeKind, DoubleArray> = mapOf(
        ShoeKind.MINIMAL_SNEAKERS to doubleArrayOf(Y, Y, T, Y, Y, Y, T),
        ShoeKind.CHUNKY_RUNNING to doubleArrayOf(Y, T, X, T, Y, Y, Y),
        ShoeKind.CANVAS to doubleArrayOf(Y, Y, X, Y, Y, Y, T),
        ShoeKind.LOAFERS to doubleArrayOf(Y, Y, Y, Y, X, Y, X),
        ShoeKind.DRESS to doubleArrayOf(T, Y, Y, T, X, X, X),
        ShoeKind.CHELSEA_CHUKKA to doubleArrayOf(Y, Y, Y, X, T, X, X),
        ShoeKind.BOOTS to doubleArrayOf(Y, T, X, X, Y, X, X),
        ShoeKind.SANDALS to doubleArrayOf(T, T, X, Y, X, Y, Y),
        ShoeKind.BOAT to doubleArrayOf(Y, Y, X, Y, X, Y, X),
    )

    /** Oxfords with jeans are a hard no even though derbies and brogues are "~". */
    fun shoeTrouser(shoe: ShoeKind, trousers: TrouserKind, oxfords: Boolean = false, hikingScenario: Boolean = false, hikingBoots: Boolean = false): Double {
        if (oxfords && trousers == TrouserKind.JEANS) return 0.0
        if (hikingBoots && hikingScenario && trousers == TrouserKind.SHORTS) return 1.0
        return shoeTrouser.getValue(shoe)[trousers.ordinal]
    }

    private val styleOrder = listOf(
        StyleTag.MINIMAL, StyleTag.SMART_CASUAL, StyleTag.CLASSIC, StyleTag.STREETWEAR, StyleTag.SPORTY,
        StyleTag.RUGGED, StyleTag.OUTDOOR, StyleTag.PREPPY, StyleTag.RESORT, StyleTag.TECHWEAR,
    )

    /** B5 style compatibility matrix (rule O1), symmetric. */
    private val styleMatrix: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.0, 0.9, 0.8, 0.8, 0.6, 0.6, 0.5, 0.6, 0.7, 0.7),
        doubleArrayOf(0.9, 1.0, 0.9, 0.6, 0.4, 0.7, 0.4, 0.9, 0.8, 0.4),
        doubleArrayOf(0.8, 0.9, 1.0, 0.4, 0.2, 0.6, 0.3, 0.9, 0.7, 0.2),
        doubleArrayOf(0.8, 0.6, 0.4, 1.0, 0.9, 0.6, 0.7, 0.4, 0.5, 0.8),
        doubleArrayOf(0.6, 0.4, 0.2, 0.9, 1.0, 0.4, 0.8, 0.5, 0.5, 0.8),
        doubleArrayOf(0.6, 0.7, 0.6, 0.6, 0.4, 1.0, 0.8, 0.5, 0.4, 0.5),
        doubleArrayOf(0.5, 0.4, 0.3, 0.7, 0.8, 0.8, 1.0, 0.3, 0.3, 0.8),
        doubleArrayOf(0.6, 0.9, 0.9, 0.4, 0.5, 0.5, 0.3, 1.0, 0.7, 0.2),
        doubleArrayOf(0.7, 0.8, 0.7, 0.5, 0.5, 0.4, 0.3, 0.7, 1.0, 0.2),
        doubleArrayOf(0.7, 0.4, 0.2, 0.8, 0.8, 0.5, 0.8, 0.2, 0.2, 1.0),
    )

    fun style(a: StyleTag, b: StyleTag): Double = styleMatrix[styleOrder.indexOf(a)][styleOrder.indexOf(b)]

    /** SPEC 5.5 silhouette table: rows top, columns bottom. */
    private val silhouette: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.9, 1.0, 0.9, 0.7),
        doubleArrayOf(1.0, 1.0, 0.9, 0.6),
        doubleArrayOf(0.9, 0.9, 0.7, 0.5),
        doubleArrayOf(1.0, 0.9, 0.5, 0.3),
    )

    fun silhouette(top: Silhouette, bottom: Silhouette, streetwear: Boolean): Double {
        if (top == Silhouette.OVERSIZED && bottom == Silhouette.OVERSIZED && streetwear) return 0.7
        return silhouette[top.ordinal][bottom.ordinal]
    }
}
