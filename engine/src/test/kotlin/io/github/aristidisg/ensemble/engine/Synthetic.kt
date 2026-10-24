package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.model.CategoryGroup
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.Subcategory

/** Seeded synthetic wardrobes for property tests and the benchmark. */
object Synthetic {
    private val weights = mapOf(
        CategoryGroup.TOPS to 0.38, CategoryGroup.BOTTOMS to 0.2, CategoryGroup.OUTERWEAR to 0.12,
        CategoryGroup.SHOES to 0.12, CategoryGroup.ACCESSORIES to 0.18,
    )

    fun wardrobe(n: Int, seed: Long): List<Item> {
        val rng = SeededRandom(seed)
        val families = ColorTables.families
        return (0 until n).map { i ->
            var r = rng.nextDouble(); var group = CategoryGroup.TOPS
            for ((g, w) in weights) { if (r < w) { group = g; break }; r -= w }
            val subs = Subcategory.entries.filter { it.group == group }
            val sub = subs[rng.nextInt(subs.size)]
            val fam = families[rng.nextInt(families.size)]
            val item = ItemFactory.create("s$i", sub, listOf(KMeans.itemColor(fam.hex, family = fam.family)))
            item.copy(silhouette = Silhouette.entries[rng.nextInt(3)])
        }
    }
}
