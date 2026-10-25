package io.github.aristidisg.ensemble.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aristidisg.ensemble.data.files.ImageStore
import io.github.aristidisg.ensemble.data.repo.OutfitRepository
import io.github.aristidisg.ensemble.data.repo.PlanRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.engine.DemoWardrobe
import io.github.aristidisg.ensemble.engine.ItemFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Deletes pieces together with their photos and the saved outfits and plans that use them. */
@Singleton
class WardrobeCleaner @Inject constructor(
    @ApplicationContext private val app: Context,
    private val wardrobe: WardrobeRepository,
    private val outfits: OutfitRepository,
    private val plans: PlanRepository,
    private val images: ImageStore,
    private val time: TimeSource,
) {
    suspend fun delete(ids: Set<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.forEach { wardrobe.delete(it); images.deleteItem(it) }
        outfits.all().filter { o -> o.itemIds.any { it in ids } }.forEach { outfits.delete(it.id) }
        plans.allPlans().filter { p -> p.pieces.any { it.itemId in ids } }.forEach { plans.deletePlan(it.epochDay) }
        // Wear history is kept: past days still show the pieces that remain.
    }

    /**
     * Re-derives color families and automatic names for every stored piece after the naming
     * rules change (e.g. deep navy once read as black). Typed names and exact colors are kept.
     */
    suspend fun repairColorNames(): Int = withContext(Dispatchers.IO) {
        val fixed = wardrobe.all().mapNotNull { i -> ItemFactory.normalizeColors(i).takeIf { it != i } }
        if (fixed.isNotEmpty()) wardrobe.upsertAll(fixed, time.nowMillis())
        fixed.size
    }

    private fun demoText(): String = app.assets.open("demo_wardrobe.json").bufferedReader().use { it.readText() }

    /** IDs of the example pieces; they are fixed, so the user's own pieces are never touched. */
    fun demoIds(): Set<String> = DemoWardrobe.parse(demoText()).map { it.id }.toSet()

    suspend fun seedDemo() = withContext(Dispatchers.IO) {
        wardrobe.upsertAll(DemoWardrobe.parse(demoText(), createdEpochDay = time.today() - 30), time.nowMillis())
    }

    suspend fun removeDemo(): Int = withContext(Dispatchers.IO) {
        val present = wardrobe.all().map { it.id }.toSet()
        val ids = demoIds().intersect(present)
        delete(ids)
        ids.size
    }
}
