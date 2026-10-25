package io.github.aristidisg.ensemble.ui.nav

import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Slot
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

@Serializable object TodayRoute
@Serializable object WardrobeRoute
@Serializable object OutfitsRoute
@Serializable object PlanRoute
@Serializable object YouRoute

@Serializable data class ItemEditorRoute(val itemId: String? = null, val uris: String? = null, val camera: Boolean = false)
@Serializable data class ItemDetailRoute(val id: String)
@Serializable data class OutfitDetailRoute(val pieces: String, val scenarioId: String, val epochDay: Long)
@Serializable data class ComposerRoute(val pieces: String = "", val scenarioId: String = "", val rate: Boolean = false)
@Serializable data class LookEditorRoute(val id: String? = null, val uri: String? = null)
@Serializable data class LookMatchRoute(val id: String)
@Serializable data class TripRoute(val id: String? = null)
@Serializable object LaundryRoute
@Serializable object LabelCheckRoute
@Serializable object StyleProfileRoute
@Serializable object TasteRoute
@Serializable object RulesRoute
@Serializable data class CustomRuleRoute(val id: String? = null)
@Serializable object ScenariosRoute
@Serializable data class ScenarioEditorRoute(val id: String? = null)
@Serializable object PersonalColorRoute
@Serializable object InsightsRoute
@Serializable object ShoppingListRoute
@Serializable object RotationRoute
@Serializable object LocationRoute
@Serializable object NotificationsRoute
@Serializable object DataRoute
@Serializable object AboutRoute
@Serializable object OnboardingRoute
@Serializable data class CheckInRoute(val logId: String? = null)

/** Outfit pieces as a compact route argument: "id@SLOT,id@SLOT". */
object PiecesArg {
    fun encode(p: List<OutfitPiece>): String = p.joinToString(",") { "${it.itemId}@${it.slot.name}" }
    fun decode(s: String): List<OutfitPiece> = s.split(',').filter { '@' in it }.mapNotNull { part ->
        runCatching { OutfitPiece(part.substringBeforeLast('@'), Slot.valueOf(part.substringAfterLast('@'))) }.getOrNull()
    }
}
