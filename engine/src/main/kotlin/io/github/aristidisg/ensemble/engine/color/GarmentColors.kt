package io.github.aristidisg.ensemble.engine.color

import io.github.aristidisg.ensemble.model.ItemColor
import kotlin.math.abs

/**
 * Garment colors as people name them, from cutout pixels:
 * 1. k-means in OKLab finds candidate colors.
 * 2. Shade merge: clusters that are the same hue and chroma at a different lightness are the
 *    same fabric in shadow or in highlight, so they merge (a navy sweater's folds are not a
 *    second, "black" color).
 * 3. Robust color: each color is the median of its pixels after dropping the darkest 25 %
 *    (shadow) and brightest 10 % (glare), not their mean, which reads darker and greyer than
 *    the fabric looks.
 */
object GarmentColors {
    const val K = 6
    /** Colors covering less of the piece than this are dropped (stitching, labels, noise). */
    const val MIN_SHARE = 0.06
    /** Same fabric in different light: hue within this many degrees... */
    const val SHADE_HUE_DEG = 22.0
    /** ...and lightness within this. */
    const val SHADE_MAX_DL = 0.30
    /** Below this chroma a color is treated as neutral (black, grey, white, cream). */
    const val NEUTRAL_C = 0.035

    fun extract(samples: List<Sample>, seed: Long = 42L, max: Int = 4): List<ItemColor> {
        val pts = samples.filter { it.weight > 0.0 }
        if (pts.isEmpty()) return emptyList()
        val centers = KMeans.cluster(pts, seed, K).map { it.lab }
        var groups = centers.indices.map { c -> pts.filter { nearest(centers, it.lab) == c } }.filter { it.isNotEmpty() }
        groups = shadeMerge(groups)
        val total = pts.sumOf { it.weight }
        val colors = groups.map { g -> robust(g) to g.sumOf { it.weight } / total }
            .filter { it.second >= MIN_SHARE }
            .ifEmpty { listOf(robust(pts) to 1.0) }
            .sortedByDescending { it.second }
            .take(max)
        val keptTotal = colors.sumOf { it.second }
        return KMeans.toItemColors(colors.map { (lab, share) -> ColorCluster(lab, share / keptTotal) })
    }

    /** True when two colors are one fabric under different light. */
    fun sameFabric(x: Lab, y: Lab): Boolean {
        if (abs(x.l - y.l) > SHADE_MAX_DL) return false
        // Both visibly tinted: the hue is reliable. Shadow and glare wash chroma out along with
        // the lightness change, so the chroma ratio may be well under 1 (glare on camel reads
        // as a pale beige).
        if (minOf(x.chroma, y.chroma) >= HUE_RELIABLE_C) {
            val ratio = minOf(x.chroma, y.chroma) / maxOf(x.chroma, y.chroma)
            return ColorScience.hueDistance(x.hue, y.hue) <= SHADE_HUE_DEG && ratio >= 0.3
        }
        val nx = x.chroma < NEUTRAL_C; val ny = y.chroma < NEUTRAL_C
        // Two neutrals: only close lightness (black and white are not shades of each other).
        if (nx && ny) return abs(x.l - y.l) < 0.15
        // A near-grey next to a weak tint of similar lightness is shadow on that tint.
        return maxOf(x.chroma, y.chroma) < 0.06 && abs(x.l - y.l) < 0.18
    }

    /** From this chroma on, a color's hue can be trusted. */
    const val HUE_RELIABLE_C = 0.015

    private fun shadeMerge(input: List<List<Sample>>): List<List<Sample>> {
        val groups = input.sortedByDescending { g -> g.sumOf { it.weight } }.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in groups.indices) for (j in i + 1 until groups.size) {
                if (sameFabric(robust(groups[i]), robust(groups[j]))) {
                    groups[i] = groups[i] + groups[j]; groups.removeAt(j); merged = true; break@loop
                }
            }
        }
        return groups
    }

    /** Median color of the pixels after trimming deep shadow and glare. */
    fun robust(g: List<Sample>): Lab {
        val byL = g.sortedBy { it.lab.l }
        val lo = (byL.size * 0.25).toInt()
        val hi = (byL.size * 0.90).toInt().coerceAtLeast(lo + 1).coerceAtMost(byL.size)
        val mid = byL.subList(lo, hi).ifEmpty { byL }
        fun med(f: (Lab) -> Double): Double { val v = mid.map { f(it.lab) }.sorted(); return v[v.size / 2] }
        return Lab(med { it.l }, med { it.a }, med { it.b })
    }

    private fun nearest(centers: List<Lab>, p: Lab): Int {
        var best = 0; var bd = Double.MAX_VALUE
        for (i in centers.indices) { val d = ColorScience.deltaE(centers[i], p); if (d < bd) { bd = d; best = i } }
        return best
    }
}
