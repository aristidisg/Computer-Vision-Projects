package io.github.aristidisg.ensemble.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items") suspend fun all(): List<ItemEntity>
    @Query("SELECT * FROM items WHERE id = :id") suspend fun get(id: String): ItemEntity?
    @Query("SELECT * FROM items WHERE id = :id") fun observe(id: String): Flow<ItemEntity?>
    @Upsert suspend fun upsert(e: ItemEntity)
    @Upsert suspend fun upsertAll(e: List<ItemEntity>)
    @Query("DELETE FROM items WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT COUNT(*) FROM items") suspend fun count(): Int
    @Query("SELECT COALESCE(MAX(updatedAt), 0) + COUNT(*) FROM items") fun observeVersion(): Flow<Long>
}

@Dao
interface OutfitDao {
    @Query("SELECT * FROM outfits ORDER BY createdAt DESC") fun observeAll(): Flow<List<OutfitEntity>>
    @Query("SELECT * FROM outfits") suspend fun all(): List<OutfitEntity>
    @Query("SELECT * FROM outfits WHERE id = :id") suspend fun get(id: String): OutfitEntity?
    @Query("SELECT * FROM outfits WHERE signature = :sig LIMIT 1") suspend fun bySignature(sig: String): OutfitEntity?
    @Upsert suspend fun upsert(e: OutfitEntity)
    @Upsert suspend fun upsertAll(e: List<OutfitEntity>)
    @Query("DELETE FROM outfits WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback_events ORDER BY epochMillis") suspend fun all(): List<FeedbackEventEntity>
    @Query("SELECT * FROM feedback_events WHERE resetEpoch = :epoch ORDER BY epochMillis") suspend fun forEpoch(epoch: Int): List<FeedbackEventEntity>
    @Query("SELECT COUNT(*) FROM feedback_events") fun observeCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: FeedbackEventEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(e: List<FeedbackEventEntity>)
    @Query("DELETE FROM feedback_events WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM feedback_events") suspend fun clear()
    @Query("SELECT * FROM preference_stats WHERE id = 1") fun observeStats(): Flow<PreferenceStatsEntity?>
    @Query("SELECT * FROM preference_stats WHERE id = 1") suspend fun stats(): PreferenceStatsEntity?
    @Upsert suspend fun saveStats(e: PreferenceStatsEntity)
}

@Dao
interface WearDao {
    @Query("SELECT * FROM wear_logs ORDER BY epochDay DESC") fun observeAll(): Flow<List<WearLogEntity>>
    @Query("SELECT * FROM wear_logs") suspend fun all(): List<WearLogEntity>
    @Upsert suspend fun upsert(e: WearLogEntity)
    @Upsert suspend fun upsertAll(e: List<WearLogEntity>)
    @Query("DELETE FROM wear_logs WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM check_ins ORDER BY epochDay DESC") fun observeCheckIns(): Flow<List<CheckInEntity>>
    @Query("SELECT * FROM check_ins") suspend fun allCheckIns(): List<CheckInEntity>
    @Upsert suspend fun upsertCheckIn(e: CheckInEntity)
    @Upsert suspend fun upsertCheckIns(e: List<CheckInEntity>)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM day_plans ORDER BY epochDay") fun observePlans(): Flow<List<DayPlanEntity>>
    @Query("SELECT * FROM day_plans") suspend fun allPlans(): List<DayPlanEntity>
    @Upsert suspend fun upsertPlan(e: DayPlanEntity)
    @Upsert suspend fun upsertPlans(e: List<DayPlanEntity>)
    @Query("DELETE FROM day_plans WHERE epochDay = :day") suspend fun deletePlan(day: Long)
    @Query("SELECT * FROM trips ORDER BY startEpochDay DESC") fun observeTrips(): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips") suspend fun allTrips(): List<TripEntity>
    @Upsert suspend fun upsertTrip(e: TripEntity)
    @Upsert suspend fun upsertTrips(e: List<TripEntity>)
    @Query("DELETE FROM trips WHERE id = :id") suspend fun deleteTrip(id: String)
}

@Dao
interface LookDao {
    @Query("SELECT * FROM inspiration_looks ORDER BY createdAt DESC") fun observeAll(): Flow<List<InspirationLookEntity>>
    @Query("SELECT * FROM inspiration_looks") suspend fun all(): List<InspirationLookEntity>
    @Upsert suspend fun upsert(e: InspirationLookEntity)
    @Upsert suspend fun upsertAll(e: List<InspirationLookEntity>)
    @Query("DELETE FROM inspiration_looks WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM custom_rules") fun observeRules(): Flow<List<CustomRuleEntity>>
    @Query("SELECT * FROM custom_rules") suspend fun allRules(): List<CustomRuleEntity>
    @Upsert suspend fun upsertRule(e: CustomRuleEntity)
    @Upsert suspend fun upsertRules(e: List<CustomRuleEntity>)
    @Query("DELETE FROM custom_rules WHERE id = :id") suspend fun deleteRule(id: String)
    @Query("SELECT * FROM scenarios") fun observeScenarios(): Flow<List<ScenarioEntity>>
    @Query("SELECT * FROM scenarios") suspend fun allScenarios(): List<ScenarioEntity>
    @Upsert suspend fun upsertScenario(e: ScenarioEntity)
    @Upsert suspend fun upsertScenarios(e: List<ScenarioEntity>)
    @Query("DELETE FROM scenarios WHERE id = :id") suspend fun deleteScenario(id: String)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM weather_cache WHERE `key` = :key") suspend fun weather(key: String): WeatherCacheEntity?
    @Query("SELECT * FROM weather_cache WHERE `key` = :key") fun observeWeather(key: String): Flow<WeatherCacheEntity?>
    @Upsert suspend fun saveWeather(e: WeatherCacheEntity)
    @Query("SELECT * FROM deck_log ORDER BY id DESC LIMIT :n") suspend fun recentDecks(n: Int): List<DeckLogEntity>
    @Insert suspend fun logDeck(e: DeckLogEntity)
    @Query("DELETE FROM deck_log WHERE id NOT IN (SELECT id FROM deck_log ORDER BY id DESC LIMIT 20)") suspend fun trimDecks()
}
