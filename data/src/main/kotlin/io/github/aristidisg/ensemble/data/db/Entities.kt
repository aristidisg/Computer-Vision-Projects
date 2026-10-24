package io.github.aristidisg.ensemble.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Every record is stored as a JSON document plus the few columns we query or sort by. Nested
 * lists (item colors, outfit pieces, trip days, look pieces) live inside the document, which
 * keeps schema changes rare while the model is still settling. See DECISIONS.md.
 */

@Entity(tableName = "items", indices = [Index("status"), Index("subcategory")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val subcategory: String,
    val status: String,
    val updatedAt: Long,
    val json: String,
)

@Entity(tableName = "outfits", indices = [Index("signature")])
data class OutfitEntity(
    @PrimaryKey val id: String,
    val signature: String,
    val source: String,
    val createdAt: Long,
    val json: String,
)

@Entity(tableName = "feedback_events", indices = [Index("epochMillis"), Index("resetEpoch")])
data class FeedbackEventEntity(
    @PrimaryKey val id: String,
    val epochMillis: Long,
    val resetEpoch: Int,
    val json: String,
)

/** Cached learned state; always rebuildable from feedback_events. */
@Entity(tableName = "preference_stats")
data class PreferenceStatsEntity(
    @PrimaryKey val id: Int = 1,
    val eventCount: Int,
    val json: String,
)

@Entity(tableName = "wear_logs", indices = [Index("epochDay"), Index("signature")])
data class WearLogEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val signature: String,
    val json: String,
)

@Entity(tableName = "check_ins", indices = [Index("epochDay")])
data class CheckInEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val json: String,
)

@Entity(tableName = "day_plans")
data class DayPlanEntity(
    @PrimaryKey val epochDay: Long,
    val json: String,
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val startEpochDay: Long,
    val json: String,
)

@Entity(tableName = "inspiration_looks")
data class InspirationLookEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val json: String,
)

@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey val id: String,
    val json: String,
)

/** Custom scenarios and edited presets; untouched presets come from the engine tables. */
@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey val id: String,
    val json: String,
)

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val key: String,
    val fetchedAt: Long,
    val json: String,
)

/** Signatures shown per deck, for rule R4 (deck freshness). */
@Entity(tableName = "deck_log", indices = [Index("epochDay")])
data class DeckLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val scenarioId: String,
    val json: String,
)
