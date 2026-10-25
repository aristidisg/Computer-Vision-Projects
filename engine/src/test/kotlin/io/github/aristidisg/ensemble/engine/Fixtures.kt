package io.github.aristidisg.ensemble.engine

import io.github.aristidisg.ensemble.engine.learning.PreferenceSnapshot
import io.github.aristidisg.ensemble.engine.rules.Look
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.engine.rules.Scorer
import io.github.aristidisg.ensemble.engine.tables.ScenarioPresets
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Settings
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.WearLog
import io.github.aristidisg.ensemble.model.WindowWeather
import java.time.LocalDate

object Fixtures {
    /** A fixed Wednesday in October. */
    val TODAY: Long = LocalDate.of(2026, 10, 14).toEpochDay()

    val wardrobe: List<Item> by lazy {
        val text = Fixtures::class.java.getResource("/demo_wardrobe.json")!!.readText()
        DemoWardrobe.parse(text, createdEpochDay = TODAY - 10)
    }

    val byId: Map<String, Item> by lazy { wardrobe.associateBy { it.id } }

    fun item(id: String): Item = byId[id] ?: error("No demo item $id")

    fun scenario(id: String): Scenario = ScenarioPresets.byId(id)!!

    fun weather(min: Double, max: Double = min + 4, rain: Double = 0.0, uv: Double = 3.0, wind: Double = 10.0, rainSum: Double = 0.0) =
        WindowWeather(feelsMin = min, feelsMax = max, rainProbMax = rain, rainSum = rainSum, uvMax = uv, windMax = wind)

    fun ctx(
        scenario: String = "casual",
        weather: WindowWeather = weather(15.0, 19.0),
        day: Long = TODAY,
        settings: Settings = Settings(),
        history: List<WearLog> = emptyList(),
        prefs: PreferenceSnapshot = PreferenceSnapshot.EMPTY,
        items: List<Item> = wardrobe,
    ) = EngineContext(
        scenario = scenario(scenario), weather = weather, epochDay = day, settings = settings,
        items = items.associateBy { it.id }, prefs = prefs, history = WearHistory(history),
    )

    /** Pieces from IDs, each in its item's primary slot unless given as "id@SLOT". */
    fun pieces(vararg ids: String): List<OutfitPiece> = ids.map { spec ->
        val (id, slot) = if ('@' in spec) spec.substringBefore('@') to Slot.valueOf(spec.substringAfter('@')) else spec to item(spec).slots.first()
        OutfitPiece(id, slot)
    }

    fun look(ctx: EngineContext, vararg ids: String): Look = Look.of(pieces(*ids), ctx)!!

    fun score(ctx: EngineContext, vararg ids: String): ScoredOutfit = Scorer.score(look(ctx, *ids), ctx)

    fun wear(day: Long, vararg ids: String, scenario: String = "casual"): WearLog {
        val p = pieces(*ids)
        return WearLog("w-$day-${ids.joinToString()}", day, io.github.aristidisg.ensemble.model.Outfit.signatureOf(p.map { it.itemId }), p, scenario)
    }
}
