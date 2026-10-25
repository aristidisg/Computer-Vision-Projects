package io.github.aristidisg.ensemble.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class, OutfitEntity::class, FeedbackEventEntity::class, PreferenceStatsEntity::class,
        WearLogEntity::class, CheckInEntity::class, DayPlanEntity::class, TripEntity::class,
        InspirationLookEntity::class, CustomRuleEntity::class, ScenarioEntity::class,
        WeatherCacheEntity::class, DeckLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EnsembleDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun outfits(): OutfitDao
    abstract fun feedback(): FeedbackDao
    abstract fun wear(): WearDao
    abstract fun plans(): PlanDao
    abstract fun looks(): LookDao
    abstract fun rules(): RuleDao
    abstract fun cache(): CacheDao

    companion object {
        const val NAME = "ensemble.db"

        /** Every future schema change ships with a migration here and a migration test. */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        fun build(context: Context): EnsembleDatabase =
            Room.databaseBuilder(context, EnsembleDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
