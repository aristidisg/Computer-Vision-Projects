package io.github.aristidisg.ensemble.domain

import io.github.aristidisg.ensemble.data.repo.FeedbackRepository
import io.github.aristidisg.ensemble.data.repo.OutfitRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.data.repo.WearRepository
import io.github.aristidisg.ensemble.engine.EngineContext
import io.github.aristidisg.ensemble.engine.learning.Features
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.tables.WarmthTable
import io.github.aristidisg.ensemble.model.CheckIn
import io.github.aristidisg.ensemble.model.CheckInPayload
import io.github.aristidisg.ensemble.model.ContextSnapshot
import io.github.aristidisg.ensemble.model.FeedbackEvent
import io.github.aristidisg.ensemble.model.FeedbackType
import io.github.aristidisg.ensemble.model.InspirationLook
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.OutfitSource
import io.github.aristidisg.ensemble.model.WearLog
import io.github.aristidisg.ensemble.model.WhyNot
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What one user action wrote, so Undo can take it back. */
data class Recorded(val eventId: String?, val wearLogId: String? = null, val laundryItems: List<String> = emptyList())

/** Turns user actions into feedback events, wear logs, laundry counts and saved outfits. */
@Singleton
class FeedbackRecorder @Inject constructor(
    private val feedback: FeedbackRepository,
    private val wear: WearRepository,
    private val wardrobe: WardrobeRepository,
    private val outfits: OutfitRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {
    private suspend fun cfg(): LearningConfig = settings.current().let { LearningConfig.of(it) }

    private fun snapshot(ctx: EngineContext) = ContextSnapshot(
        ctx.scenario.id, ctx.season, WarmthTable.label(ctx.weather.feelsMid), ctx.weekday, ctx.weather.feelsMin, ctx.weather.feelsMax,
    )

    private suspend fun event(
        type: FeedbackType, pieces: List<OutfitPiece>, ctx: EngineContext, reasons: List<WhyNot> = emptyList(),
        target: String? = null, targetFeatures: List<String> = emptyList(), checkIn: CheckInPayload? = null, features: List<String>? = null,
    ): FeedbackEvent {
        val look = Look.of(pieces, ctx)
        return FeedbackEvent(
            id = UUID.randomUUID().toString(),
            epochMillis = time.nowMillis(),
            epochDay = ctx.epochDay,
            type = type,
            outfitSignature = if (pieces.isEmpty()) null else Outfit.signatureOf(pieces.map { it.itemId }),
            pieces = pieces,
            targetItemId = target,
            reasons = reasons,
            checkIn = checkIn,
            context = snapshot(ctx),
            features = features ?: look?.let { Features.extract(it, ctx) } ?: emptyList(),
            targetFeatures = targetFeatures,
            resetEpoch = settings.current().learningEpoch,
        )
    }

    private suspend fun record(e: FeedbackEvent): Recorded {
        feedback.record(e, cfg())
        return Recorded(e.id)
    }

    suspend fun like(pieces: List<OutfitPiece>, ctx: EngineContext) = record(event(FeedbackType.SWIPE_YES, pieces, ctx))

    suspend fun nope(pieces: List<OutfitPiece>, ctx: EngineContext, reasons: List<WhyNot> = emptyList(), target: String? = null) =
        record(event(FeedbackType.SWIPE_NO, pieces, ctx, reasons, target))

    /** Swipe up / "Wear today": event, wear log, laundry counts (and auto-laundry), outfit cooldown starts. */
    suspend fun wear(pieces: List<OutfitPiece>, ctx: EngineContext): Recorded {
        val e = event(FeedbackType.SWIPE_UP_WEAR, pieces, ctx)
        feedback.record(e, cfg())
        val log = WearLog(UUID.randomUUID().toString(), ctx.epochDay, e.outfitSignature!!, pieces, ctx.scenario.id, ctx.weather)
        wear.log(log)
        val auto = settings.current().autoLaundry
        val moved = mutableListOf<String>()
        for (p in pieces) {
            val item = wardrobe.get(p.itemId) ?: continue
            val l = item.laundry.copy(wearsSinceWash = item.laundry.wearsSinceWash + 1)
            val toLaundry = auto && l.atThreshold && item.laundry.wearsBeforeWash > 0
            if (toLaundry) moved += item.id
            wardrobe.upsert(item.copy(laundry = l, status = if (toLaundry) ItemStatus.LAUNDRY else item.status), time.nowMillis())
        }
        return Recorded(e.id, log.id, moved)
    }

    suspend fun save(pieces: List<OutfitPiece>, ctx: EngineContext, source: OutfitSource = OutfitSource.GENERATED, name: String = ""): Recorded {
        val sig = Outfit.signatureOf(pieces.map { it.itemId })
        val existing = outfits.bySignature(sig)
        outfits.save((existing ?: Outfit(UUID.randomUUID().toString(), pieces, source, name, ctx.scenario.id, createdAtMillis = time.nowMillis())).copy(liked = true))
        val type = if (source == OutfitSource.COMPOSED) FeedbackType.COMPOSED else FeedbackType.SAVE
        return record(event(type, pieces, ctx))
    }

    suspend fun pieceThumb(itemId: String, up: Boolean, pieces: List<OutfitPiece>, ctx: EngineContext): Recorded {
        val features = Look.of(pieces, ctx)?.let { Features.extract(it, ctx) } ?: emptyList()
        return record(event(if (up) FeedbackType.PIECE_UP else FeedbackType.PIECE_DOWN, pieces, ctx, target = itemId,
            targetFeatures = Features.pairsOf(itemId, features), features = features))
    }

    suspend fun neverAgain(pieces: List<OutfitPiece>, ctx: EngineContext): Recorded {
        val sig = Outfit.signatureOf(pieces.map { it.itemId })
        val o = outfits.bySignature(sig) ?: Outfit(UUID.randomUUID().toString(), pieces, OutfitSource.GENERATED, createdAtMillis = time.nowMillis())
        outfits.save(o.copy(neverShow = true, liked = false))
        return record(event(FeedbackType.NEVER_AGAIN, pieces, ctx))
    }

    suspend fun checkIn(log: WearLog, payload: CheckInPayload, ctx: EngineContext): Recorded {
        wear.checkIn(CheckIn(UUID.randomUUID().toString(), log.epochDay, log.outfitSignature, payload))
        return record(event(FeedbackType.CHECK_IN, log.pieces, ctx, checkIn = payload))
    }

    suspend fun inspirationAdded(look: InspirationLook, ctx: EngineContext) =
        record(event(FeedbackType.INSPIRATION_ADDED, emptyList(), ctx, features = Features.extract(look)))

    /** Undo: drop the event, the wear log and the laundry counts, then replay the log. */
    suspend fun undo(r: Recorded) {
        val s = settings.current()
        r.eventId?.let { feedback.deleteEvent(it) }
        r.wearLogId?.let { id ->
            val log = wear.all().firstOrNull { it.id == id }
            wear.delete(id)
            log?.pieces?.forEach { p ->
                val item = wardrobe.get(p.itemId) ?: return@forEach
                val l = item.laundry.copy(wearsSinceWash = (item.laundry.wearsSinceWash - 1).coerceAtLeast(0))
                wardrobe.upsert(item.copy(laundry = l, status = if (p.itemId in r.laundryItems) ItemStatus.AVAILABLE else item.status), time.nowMillis())
            }
        }
        feedback.rebuild(s.learningEpoch, LearningConfig.of(s))
    }
}
