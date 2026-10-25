package io.github.aristidisg.ensemble.engine.color

import io.github.aristidisg.ensemble.engine.SeededRandom
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.ColorRole
import io.github.aristidisg.ensemble.model.ItemColor

/** A pixel sample in OKLab with a weight (usually the cutout alpha). */
data class Sample(val lab: Lab, val weight: Double)

data class ColorCluster(val lab: Lab, val share: Double) {
    val hex: String get() = ColorScience.labToHex(lab)
}

/**
 * Weighted k-means++ in OKLab (SPEC 3 step 5): k = 5, seeded, 15 iterations; merge clusters
 * closer than ΔE_ok 0.05; drop clusters under 4 %; sort by share.
 */
object KMeans {
    const val K = 5
    const val ITERATIONS = 15
    const val MERGE_DE = 0.05
    const val MIN_SHARE = 0.04

    fun cluster(samples: List<Sample>, seed: Long = 42L, k: Int = K): List<ColorCluster> {
        val pts = samples.filter { it.weight > 0.0 }
        if (pts.isEmpty()) return emptyList()
        val rng = SeededRandom(seed)
        val centers = initPlusPlus(pts, minOf(k, pts.size), rng).toMutableList()
        val assign = IntArray(pts.size)
        repeat(ITERATIONS) {
            for (i in pts.indices) assign[i] = nearest(centers, pts[i].lab)
            for (c in centers.indices) {
                var w = 0.0; var l = 0.0; var a = 0.0; var b = 0.0
                for (i in pts.indices) if (assign[i] == c) {
                    val p = pts[i]; w += p.weight; l += p.lab.l * p.weight; a += p.lab.a * p.weight; b += p.lab.b * p.weight
                }
                if (w > 0) centers[c] = Lab(l / w, a / w, b / w)
            }
        }
        for (i in pts.indices) assign[i] = nearest(centers, pts[i].lab)
        val total = pts.sumOf { it.weight }
        var clusters = centers.indices.map { c ->
            ColorCluster(centers[c], pts.indices.filter { assign[it] == c }.sumOf { pts[it].weight } / total)
        }.filter { it.share > 0 }
        clusters = merge(clusters)
        val kept = clusters.filter { it.share >= MIN_SHARE }.ifEmpty { listOf(clusters.maxBy { it.share }) }
        val keptTotal = kept.sumOf { it.share }
        return kept.map { it.copy(share = it.share / keptTotal) }.sortedByDescending { it.share }
    }

    private fun initPlusPlus(pts: List<Sample>, k: Int, rng: SeededRandom): List<Lab> {
        val centers = mutableListOf(pickWeighted(pts.map { it.weight }, rng).let { pts[it].lab })
        while (centers.size < k) {
            val d = pts.map { p -> p.weight * centers.minOf { sq(ColorScience.deltaE(it, p.lab)) } }
            if (d.sum() <= 1e-12) break
            centers += pts[pickWeighted(d, rng)].lab
        }
        return centers
    }

    private fun pickWeighted(w: List<Double>, rng: SeededRandom): Int {
        val total = w.sum()
        var r = rng.nextDouble() * total
        for (i in w.indices) { r -= w[i]; if (r <= 0) return i }
        return w.lastIndex
    }

    private fun nearest(centers: List<Lab>, p: Lab): Int {
        var best = 0; var bd = Double.MAX_VALUE
        for (i in centers.indices) { val d = ColorScience.deltaE(centers[i], p); if (d < bd) { bd = d; best = i } }
        return best
    }

    private fun merge(input: List<ColorCluster>): List<ColorCluster> {
        val list = input.sortedByDescending { it.share }.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in list.indices) for (j in i + 1 until list.size) {
                if (ColorScience.deltaE(list[i].lab, list[j].lab) < MERGE_DE) {
                    val a = list[i]; val b = list[j]; val s = a.share + b.share
                    list[i] = ColorCluster(
                        Lab((a.lab.l * a.share + b.lab.l * b.share) / s, (a.lab.a * a.share + b.lab.a * b.share) / s,
                            (a.lab.b * a.share + b.lab.b * b.share) / s), s)
                    list.removeAt(j); merged = true; break@loop
                }
            }
        }
        return list
    }

    private fun sq(x: Double) = x * x

    /** Maps clusters to item colors (max 4) with families and proposed roles. */
    fun toItemColors(clusters: List<ColorCluster>): List<ItemColor> =
        clusters.take(4).mapIndexed { i, c ->
            ItemColor(
                hex = c.hex, l = c.lab.l, a = c.lab.a, b = c.lab.b,
                family = ColorScience.nearestFamily(c.lab), share = c.share,
                role = when {
                    i == 0 -> ColorRole.PRIMARY
                    c.share >= 0.2 -> ColorRole.SECONDARY
                    else -> ColorRole.ACCENT
                },
            )
        }

    fun itemColor(hex: String, share: Double = 1.0, role: ColorRole = ColorRole.PRIMARY, family: ColorFamily? = null): ItemColor {
        val exact = ColorScience.normalizeHex(hex) ?: throw IllegalArgumentException("Not a hex color: $hex")
        val lab = ColorScience.hexToLab(exact)
        return ItemColor(exact, lab.l, lab.a, lab.b, family ?: ColorScience.nearestFamily(lab), share, role)
    }
}

/** "Patterned?" hint (SPEC 3 step 6). */
object PatternHint {
    /** Mean Sobel gradient magnitude of a lightness grid (values 0..1), masked by [mask]. */
    fun edgeDensity(lightness: Array<DoubleArray>, mask: Array<BooleanArray>): Double {
        val h = lightness.size; if (h < 3) return 0.0
        val w = lightness[0].size; if (w < 3) return 0.0
        var sum = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            if (!mask[y][x]) continue
            val p = lightness
            val gx = -p[y - 1][x - 1] - 2 * p[y][x - 1] - p[y + 1][x - 1] + p[y - 1][x + 1] + 2 * p[y][x + 1] + p[y + 1][x + 1]
            val gy = -p[y - 1][x - 1] - 2 * p[y - 1][x] - p[y - 1][x + 1] + p[y + 1][x - 1] + 2 * p[y + 1][x] + p[y + 1][x + 1]
            sum += kotlin.math.sqrt(gx * gx + gy * gy); n++
        }
        return if (n == 0) 0.0 else sum / n
    }

    /** True when two or more 20 %+ clusters differ by ΔE > 0.15 and the edges are dense. */
    fun looksPatterned(clusters: List<ColorCluster>, edgeDensity: Double, edgeThreshold: Double = 0.25): Boolean {
        val big = clusters.filter { it.share >= 0.2 }
        val contrasting = big.indices.any { i -> (i + 1 until big.size).any { j -> ColorScience.deltaE(big[i].lab, big[j].lab) > 0.15 } }
        return contrasting && edgeDensity > edgeThreshold
    }
}
