package io.github.aristidisg.ensemble.engine.rules

import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.color.ColorInfo
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.Slot
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Styling techniques a stylist reaches for; every outfit gets at most one, the most special. */
@Serializable
enum class Technique(val label: String, val blurb: String) {
    MONOCHROME("Monochrome", "One colour head to toe: a long, clean line."),
    TONAL("Tonal", "Shades of one colour: the quiet-luxury trick."),
    COLOR_BLOCK("Colour block", "Bold blocks of colour, kept clean by neutrals."),
    SANDWICH("The sandwich", "Shoes echo the top, so the eye travels the whole look."),
    STATEMENT("One statement", "One piece talks, everything else listens."),
    HIGH_LOW("High-low", "Tailoring meets casual: effortless and editorial."),
    SOFT_LAYERS("Soft layers", "Relaxed shapes layered in a calm palette, the Seoul way."),
    THIRD_PIECE("Third piece", "An open layer frames the outfit and adds depth."),
    TEXTURE("Texture play", "Contrasting textures make a simple palette rich."),
    CONTRAST("Light & dark", "Crisp light against dark: sharp and graphic."),
}

/**
 * Stylist techniques (data-driven checks on the visible pieces). An outfit using one gets a
 * bonus and the technique as its headline, and the deck mixes techniques for variety.
 */
object StylistRules {
    /** Raw score for an outfit that uses a technique (about 0.07 on the Match scale). */
    const val TECHNIQUE_BONUS = 0.03
    /** Below this chroma a color counts as neutral for techniques. */
    private const val NEUTRAL_C = 0.04
    /** Enough tint for the hue to read (camel, brown, olive, navy). */
    private const val TINT_C = 0.03

    internal val textureOf: Map<Material, String> = mapOf(
        Material.KNIT to "knit", Material.WOOL to "knit", Material.MERINO to "knit", Material.CASHMERE to "knit",
        Material.DENIM to "denim", Material.LEATHER to "leather", Material.SUEDE to "suede",
        Material.CORDUROY to "cord", Material.TWEED to "tweed", Material.FLANNEL to "flannel",
        Material.NYLON_TECH to "tech", Material.LINEN to "linen", Material.COTTON to "smooth", Material.JERSEY to "smooth",
        Material.FLEECE to "fleece", Material.CANVAS to "canvas", Material.SEERSUCKER to "linen",
    )

    private fun isNeutral(c: ColorInfo) = c.neutralness >= 0.6 || c.c < NEUTRAL_C

    /** Visible core pieces (a tee under a hoodie doesn't count) with their dominant color. */
    internal fun visible(look: Look): List<Pair<Item, ColorInfo>> =
        look.core.filter { it.slot != Slot.SHOES && (look.pieceArea[it.item.id] ?: 0.0) >= 0.06 }
            .mapNotNull { p -> look.dominant(p.item)?.let { p.item to it } }

    fun technique(look: Look, ctx: EngineContext): Technique? {
        val vis = visible(look)
        if (vis.size < 2) return null
        val infos = vis.map { it.second }
        val chroma = infos.filter { !isNeutral(it) }
        val neutral = infos.filter { isNeutral(it) }
        val lSpread = infos.maxOf { it.l } - infos.minOf { it.l }

        // Monochrome: one family everywhere, shoes included (all cream, all black, all navy).
        val withShoes = infos + listOfNotNull(look.dominant(look.shoes))
        if (withShoes.size >= 3 && withShoes.map { it.family }.distinct().size == 1) return Technique.MONOCHROME
        // Tonal: every piece tinted in one hue (browns and camels count, they are warm tints),
        // at least two lightness steps apart; one true neutral is allowed.
        val tinted = infos.filter { it.c >= TINT_C }
        val tintSpan = if (tinted.size >= 2) tinted.maxOf { a -> tinted.maxOf { b -> ColorScience.hueDistance(a.hue, b.hue) } } else 360.0
        if (tinted.size >= 2 && infos.size - tinted.size <= 1 && tintSpan <= 30.0 && lSpread >= 0.12) return Technique.TONAL
        // Colour block: two or more bold, clearly different colors with real area.
        val bold = look.chromaticColors.filter { it.info.c >= 0.08 && it.area >= 0.18 }
        if (bold.size >= 2 && bold.maxOf { a -> bold.maxOf { b -> ColorScience.hueDistance(a.info.hue, b.info.hue) } } >= 60.0) return Technique.COLOR_BLOCK
        // Sandwich: shoes echo the upper body while the bottom differs.
        val shoes = look.dominant(look.shoes); val upper = look.upperDominant; val bottom = look.dominant(look.bottom)
        if (shoes != null && upper != null && bottom != null &&
            ColorScience.deltaE(shoes.lab, upper.lab) < 0.08 && ColorScience.deltaE(bottom.lab, upper.lab) > 0.18) return Technique.SANDWICH
        // One statement: exactly one bold or loud piece, the rest neutral.
        val loud = vis.filter { (i, c) -> c.c >= 0.10 || i.pattern.loudness >= 2 }
        if (loud.size == 1 && vis.filter { it.first != loud[0].first }.all { isNeutral(it.second) }) return Technique.STATEMENT
        // High-low: a tailored layer (blazer, sport coat) over clearly casual pieces.
        val tailored = vis.any { (i, _) -> TaxonomyTable.of(i.subcategory).tailored && i.slots.any { it == Slot.OUTER || it == Slot.MID } }
        if (tailored && vis.any { it.first.formality <= 3 }) return Technique.HIGH_LOW
        // Soft layers: three visible layers, something relaxed, a calm palette.
        val layers = listOfNotNull(look.top?.takeIf { !look.topHidden }, look.mid, look.outer)
        if (layers.size >= 3 && layers.any { it.silhouette == Silhouette.RELAXED || it.silhouette == Silhouette.OVERSIZED } && chroma.none { it.c >= 0.08 }) return Technique.SOFT_LAYERS
        // Third piece: an open layer over a top it contrasts with.
        val open = look.outer?.takeIf { !look.outerClosed } ?: look.mid?.takeIf { !look.midClosed }
        val openC = look.dominant(open); val topC = look.dominant(look.top)
        if (open != null && openC != null && topC != null && ColorScience.deltaE(openC.lab, topC.lab) >= 0.15) return Technique.THIRD_PIECE
        // Texture play: three different textures.
        val textures = vis.mapNotNull { (i, _) -> i.materials.firstOrNull()?.let { textureOf[it] } }.toSet()
        if (textures.size >= 3) return Technique.TEXTURE
        // Light & dark: neutral, strongly contrasting top and bottom.
        if (chroma.isEmpty() && upper != null && bottom != null && abs(upper.l - bottom.l) >= 0.45) return Technique.CONTRAST
        return null
    }
}
