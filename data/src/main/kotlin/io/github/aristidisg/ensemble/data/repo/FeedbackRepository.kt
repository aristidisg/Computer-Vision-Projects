package io.github.aristidisg.ensemble.data.repo

import io.github.aristidisg.ensemble.data.db.EnsembleDatabase
import io.github.aristidisg.ensemble.data.db.FeedbackEventEntity
import io.github.aristidisg.ensemble.data.db.PreferenceStatsEntity
import io.github.aristidisg.ensemble.engine.learning.Learning
import io.github.aristidisg.ensemble.engine.learning.LearningConfig
import io.github.aristidisg.ensemble.engine.learning.PreferenceStats
import io.github.aristidisg.ensemble.model.FeedbackEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The append-only feedback log and the learned stats derived from it. Stats are updated
 * incrementally and cached; "Rebuild from history" replays the log deterministically.
 */
class FeedbackRepository(private val db: EnsembleDatabase) {
    private val lock = Mutex()

    val stats: Flow<PreferenceStats> = db.feedback().observeStats().map { e ->
        e?.let { DataJson.decodeFromString(PreferenceStats.serializer(), it.json) } ?: PreferenceStats.EMPTY
    }

    val eventCount: Flow<Int> = db.feedback().observeCount()

    /** Last stats written or read; every write goes through [save], so it never goes stale. */
    @Volatile private var cached: PreferenceStats? = null

    /** Decoding the stats JSON is the slowest part of building an engine context, so it's done once. */
    suspend fun currentStats(): PreferenceStats = cached ?: (
        db.feedback().stats()?.let { DataJson.decodeFromString(PreferenceStats.serializer(), it.json) } ?: PreferenceStats.EMPTY
    ).also { cached = it }

    suspend fun record(event: FeedbackEvent, cfg: LearningConfig) = lock.withLock {
        db.feedback().insert(entity(event))
        val next = Learning.apply(event, currentStats(), cfg)
        save(next)
    }

    suspend fun events(epoch: Int? = null): List<FeedbackEvent> =
        (if (epoch == null) db.feedback().all() else db.feedback().forEpoch(epoch))
            .map { DataJson.decodeFromString(FeedbackEvent.serializer(), it.json) }

    /** Deterministic replay of the current epoch's log. */
    suspend fun rebuild(epoch: Int, cfg: LearningConfig) = lock.withLock {
        save(Learning.rebuild(events(epoch), cfg))
    }

    /** Soft reset: the caller bumps the epoch; stats restart empty while the log is kept. */
    suspend fun softReset() = lock.withLock { save(PreferenceStats.EMPTY) }

    /** Hard reset: delete the whole log. */
    suspend fun hardReset() = lock.withLock {
        db.feedback().clear()
        save(PreferenceStats.EMPTY)
    }

    /** "Forget this": drop one feature from every context. */
    suspend fun forget(featureKey: String) = lock.withLock {
        val s = currentStats()
        save(s.copy(stats = s.stats.filterValues { it.featureKey != featureKey }))
    }

    suspend fun insertAll(events: List<FeedbackEvent>) = db.feedback().insertAll(events.map(::entity))

    suspend fun deleteEvent(id: String) = db.feedback().delete(id)

    private suspend fun save(s: PreferenceStats) {
        cached = s
        db.feedback().saveStats(PreferenceStatsEntity(1, s.stats.size, DataJson.encodeToString(PreferenceStats.serializer(), s)))
    }

    /** For writers that bypass [save] (import, delete all): read the table again next time. */
    fun invalidate() { cached = null }

    private fun entity(e: FeedbackEvent) =
        FeedbackEventEntity(e.id, e.epochMillis, e.resetEpoch, DataJson.encodeToString(FeedbackEvent.serializer(), e))
}
