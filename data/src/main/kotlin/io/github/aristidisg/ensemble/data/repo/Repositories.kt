package io.github.aristidisg.ensemble.data.repo

import io.github.aristidisg.ensemble.data.db.CheckInEntity
import io.github.aristidisg.ensemble.data.db.CustomRuleEntity
import io.github.aristidisg.ensemble.data.db.DayPlanEntity
import io.github.aristidisg.ensemble.data.db.EnsembleDatabase
import io.github.aristidisg.ensemble.data.db.InspirationLookEntity
import io.github.aristidisg.ensemble.data.db.ItemEntity
import io.github.aristidisg.ensemble.data.db.OutfitEntity
import io.github.aristidisg.ensemble.data.db.ScenarioEntity
import io.github.aristidisg.ensemble.data.db.TripEntity
import io.github.aristidisg.ensemble.data.db.WearLogEntity
import io.github.aristidisg.ensemble.engine.tables.ScenarioPresets
import io.github.aristidisg.ensemble.model.CheckIn
import io.github.aristidisg.ensemble.model.CustomRule
import io.github.aristidisg.ensemble.model.DayPlan
import io.github.aristidisg.ensemble.model.InspirationLook
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Outfit
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Trip
import io.github.aristidisg.ensemble.model.WearLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Encodes/decodes model documents for Room. */
object Docs {
    fun item(i: Item) = ItemEntity(i.id, i.subcategory.name, i.status.name, i.updatedAtMillis, DataJson.encodeToString(Item.serializer(), i))
    fun item(e: ItemEntity): Item = DataJson.decodeFromString(Item.serializer(), e.json)
    fun outfit(o: Outfit) = OutfitEntity(o.id, o.signature, o.source.name, o.createdAtMillis, DataJson.encodeToString(Outfit.serializer(), o))
    fun outfit(e: OutfitEntity): Outfit = DataJson.decodeFromString(Outfit.serializer(), e.json)
    fun wear(w: WearLog) = WearLogEntity(w.id, w.epochDay, w.outfitSignature, DataJson.encodeToString(WearLog.serializer(), w))
    fun wear(e: WearLogEntity): WearLog = DataJson.decodeFromString(WearLog.serializer(), e.json)
    fun checkIn(c: CheckIn) = CheckInEntity(c.id, c.epochDay, DataJson.encodeToString(CheckIn.serializer(), c))
    fun checkIn(e: CheckInEntity): CheckIn = DataJson.decodeFromString(CheckIn.serializer(), e.json)
    fun plan(p: DayPlan) = DayPlanEntity(p.epochDay, DataJson.encodeToString(DayPlan.serializer(), p))
    fun plan(e: DayPlanEntity): DayPlan = DataJson.decodeFromString(DayPlan.serializer(), e.json)
    fun trip(t: Trip) = TripEntity(t.id, t.startEpochDay, DataJson.encodeToString(Trip.serializer(), t))
    fun trip(e: TripEntity): Trip = DataJson.decodeFromString(Trip.serializer(), e.json)
    fun look(l: InspirationLook) = InspirationLookEntity(l.id, l.createdAtMillis, DataJson.encodeToString(InspirationLook.serializer(), l))
    fun look(e: InspirationLookEntity): InspirationLook = DataJson.decodeFromString(InspirationLook.serializer(), e.json)
    fun rule(r: CustomRule) = CustomRuleEntity(r.id, DataJson.encodeToString(CustomRule.serializer(), r))
    fun rule(e: CustomRuleEntity): CustomRule = DataJson.decodeFromString(CustomRule.serializer(), e.json)
    fun scenario(s: Scenario) = ScenarioEntity(s.id, DataJson.encodeToString(Scenario.serializer(), s))
    fun scenario(e: ScenarioEntity): Scenario = DataJson.decodeFromString(Scenario.serializer(), e.json)
}

class WardrobeRepository(private val db: EnsembleDatabase) {
    val items: Flow<List<Item>> = db.items().observeAll().map { l -> l.map(Docs::item) }

    /** Changes whenever any item changes; part of the deck seed. */
    val version: Flow<Long> = db.items().observeVersion()

    fun observe(id: String): Flow<Item?> = db.items().observe(id).map { it?.let(Docs::item) }
    suspend fun all(): List<Item> = db.items().all().map(Docs::item)
    suspend fun get(id: String): Item? = db.items().get(id)?.let(Docs::item)
    suspend fun upsert(item: Item, now: Long) = db.items().upsert(Docs.item(item.copy(updatedAtMillis = now)))
    suspend fun upsertAll(items: List<Item>, now: Long) = db.items().upsertAll(items.map { Docs.item(it.copy(updatedAtMillis = now)) })
    suspend fun delete(id: String) = db.items().delete(id)
    suspend fun count(): Int = db.items().count()
}

class OutfitRepository(private val db: EnsembleDatabase) {
    val outfits: Flow<List<Outfit>> = db.outfits().observeAll().map { l -> l.map(Docs::outfit) }
    suspend fun all(): List<Outfit> = db.outfits().all().map(Docs::outfit)
    suspend fun get(id: String): Outfit? = db.outfits().get(id)?.let(Docs::outfit)
    suspend fun bySignature(sig: String): Outfit? = db.outfits().bySignature(sig)?.let(Docs::outfit)
    suspend fun save(o: Outfit) = db.outfits().upsert(Docs.outfit(o))
    suspend fun delete(id: String) = db.outfits().delete(id)
}

class WearRepository(private val db: EnsembleDatabase) {
    val logs: Flow<List<WearLog>> = db.wear().observeAll().map { l -> l.map(Docs::wear) }
    val checkIns: Flow<List<CheckIn>> = db.wear().observeCheckIns().map { l -> l.map(Docs::checkIn) }
    suspend fun all(): List<WearLog> = db.wear().all().map(Docs::wear)
    suspend fun log(w: WearLog) = db.wear().upsert(Docs.wear(w))
    suspend fun delete(id: String) = db.wear().delete(id)
    suspend fun checkIn(c: CheckIn) = db.wear().upsertCheckIn(Docs.checkIn(c))
    suspend fun allCheckIns(): List<CheckIn> = db.wear().allCheckIns().map(Docs::checkIn)
}

class PlanRepository(private val db: EnsembleDatabase) {
    val plans: Flow<List<DayPlan>> = db.plans().observePlans().map { l -> l.map(Docs::plan) }
    val trips: Flow<List<Trip>> = db.plans().observeTrips().map { l -> l.map(Docs::trip) }
    suspend fun allPlans(): List<DayPlan> = db.plans().allPlans().map(Docs::plan)
    suspend fun savePlan(p: DayPlan) = db.plans().upsertPlan(Docs.plan(p))
    suspend fun deletePlan(day: Long) = db.plans().deletePlan(day)
    suspend fun saveTrip(t: Trip) = db.plans().upsertTrip(Docs.trip(t))
    suspend fun deleteTrip(id: String) = db.plans().deleteTrip(id)
    suspend fun allTrips(): List<Trip> = db.plans().allTrips().map(Docs::trip)
}

class LookRepository(private val db: EnsembleDatabase) {
    val looks: Flow<List<InspirationLook>> = db.looks().observeAll().map { l -> l.map(Docs::look) }
    suspend fun all(): List<InspirationLook> = db.looks().all().map(Docs::look)
    suspend fun save(l: InspirationLook) = db.looks().upsert(Docs.look(l))
    suspend fun delete(id: String) = db.looks().delete(id)
}

/** Custom rules plus scenarios: presets from the engine overlaid with user edits and custom ones. */
class RuleRepository(private val db: EnsembleDatabase) {
    val rules: Flow<List<CustomRule>> = db.rules().observeRules().map { l -> l.map(Docs::rule) }
    suspend fun allRules(): List<CustomRule> = db.rules().allRules().map(Docs::rule)
    suspend fun saveRule(r: CustomRule) = db.rules().upsertRule(Docs.rule(r))
    suspend fun deleteRule(id: String) = db.rules().deleteRule(id)

    val scenarios: Flow<List<Scenario>> = db.rules().observeScenarios().map { l -> merge(l.map(Docs::scenario)) }
    suspend fun allScenarios(): List<Scenario> = merge(db.rules().allScenarios().map(Docs::scenario))
    suspend fun saveScenario(s: Scenario) = db.rules().upsertScenario(Docs.scenario(s))

    /** Deleting an edited preset restores the default. */
    suspend fun deleteScenario(id: String) = db.rules().deleteScenario(id)

    private fun merge(stored: List<Scenario>): List<Scenario> {
        val byId = stored.associateBy { it.id }
        return ScenarioPresets.all.map { byId[it.id] ?: it } + stored.filter { ScenarioPresets.byId(it.id) == null }
    }
}
