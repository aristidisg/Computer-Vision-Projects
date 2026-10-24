package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.model.AccessoryExtras
import io.github.aristidisg.ensemble.model.ColorRole
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.MetalTone
import io.github.aristidisg.ensemble.model.Pattern
import io.github.aristidisg.ensemble.model.PatternScale
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.ShoeUpper
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.Subcategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One compact row of demo_wardrobe.json. */
@Serializable
data class DemoItem(
    val id: String,
    val sub: Subcategory,
    val colors: List<String>,
    val name: String? = null,
    val pattern: PatternType = PatternType.SOLID,
    val scale: PatternScale = PatternScale.NONE,
    val loud: Int = 0,
    val silhouette: Silhouette = Silhouette.REGULAR,
    val materials: List<Material>? = null,
    val metal: MetalTone? = null,
)

/** Builds the fixture wardrobe used by tests and the debug "Seed demo wardrobe" action. */
object DemoWardrobe {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String, createdEpochDay: Long = 0): List<Item> =
        json.decodeFromString<List<DemoItem>>(text).map { build(it, createdEpochDay) }

    fun build(d: DemoItem, createdEpochDay: Long = 0): Item {
        val shares = when (d.colors.size) { 1 -> listOf(1.0); 2 -> listOf(0.7, 0.3); else -> listOf(0.5, 0.3, 0.2) }
        val colors = d.colors.mapIndexed { i, hex ->
            KMeans.itemColor(hex, shares.getOrElse(i) { 0.1 }, if (i == 0) ColorRole.PRIMARY else if (shares[i] >= 0.2) ColorRole.SECONDARY else ColorRole.ACCENT)
        }
        var item = ItemFactory.create(d.id, d.sub, colors, Pattern(d.pattern, d.scale, d.loud), d.materials, createdEpochDay)
        item = item.copy(silhouette = d.silhouette, name = d.name ?: item.name)
        if (d.materials?.contains(Material.SUEDE) == true && item.shoe != null) item = item.copy(shoe = item.shoe!!.copy(upper = ShoeUpper.SUEDE))
        if (d.metal != null) item = item.copy(accessory = (item.accessory ?: AccessoryExtras()).copy(metalTone = d.metal))
        return ItemFactory.refreshDerived(item)
    }
}
