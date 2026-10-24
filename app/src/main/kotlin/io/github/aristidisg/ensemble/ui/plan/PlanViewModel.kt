package io.github.aristidisg.ensemble.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.data.repo.PlanRepository
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.planner.DayContext
import io.github.aristidisg.ensemble.engine.planner.WeekPlanner
import io.github.aristidisg.ensemble.model.DayPlan
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.Scenario
import io.github.aristidisg.ensemble.model.Trip
import io.github.aristidisg.ensemble.model.WindowWeather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

data class PlanDay(val epochDay: Long, val scenario: Scenario, val weather: WindowWeather, val fromForecast: Boolean, val plan: DayPlan?)

data class PlanState(
    val days: List<PlanDay> = emptyList(),
    val scenarios: List<Scenario> = emptyList(),
    val items: Map<String, Item> = emptyMap(),
    val trips: List<Trip> = emptyList(),
    val laundryCount: Int = 0,
    val conflicts: List<String> = emptyList(),
    val planning: Boolean = false,
    val swapFrom: Long? = null,
    val replanned: Boolean = false,
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val studio: Studio,
    private val plans: PlanRepository,
    private val wardrobe: WardrobeRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {
    private val _s = MutableStateFlow(PlanState())
    val state = _s.asStateFlow()

    init {
        viewModelScope.launch { plans.trips.collect { t -> _s.update { it.copy(trips = t) } } }
        viewModelScope.launch { wardrobe.items.collect { i -> _s.update { it.copy(items = i.associateBy { x -> x.id }, laundryCount = i.count { x -> x.status == ItemStatus.LAUNDRY }) } } }
        refresh(checkReplan = true)
    }

    fun refresh(checkReplan: Boolean = false): Job = viewModelScope.launch {
        val today = time.today()
        val stored = plans.allPlans().associateBy { it.epochDay }
        val days = (0 until 7).map { d ->
            val day = today + d
            val sc = studio.scenario(stored[day]?.scenarioId ?: studio.defaultScenarioId(day))
            val w = studio.dayWeather(day, sc)
            PlanDay(day, sc, w.window, w.fromForecast, stored[day])
        }
        _s.update { it.copy(days = days, scenarios = studio.scenarios()) }
        if (checkReplan) {
            // Replan automatically when a day's forecast moves by 4 °C or more, or rain flips.
            val moved = days.filter { d ->
                val p = d.plan ?: return@filter false
                val feels = p.plannedFeels ?: return@filter false
                !p.locked && d.fromForecast && (abs(d.weather.feelsMid - feels) >= 4.0 || (d.weather.rainProbMax >= 50) != p.plannedRain)
            }
            if (moved.isNotEmpty()) { autoPlan(); _s.update { it.copy(replanned = true) } }
        }
    }

    fun setScenario(day: Long, scenarioId: String) = viewModelScope.launch {
        val p = plans.allPlans().firstOrNull { it.epochDay == day } ?: DayPlan(day, scenarioId)
        plans.savePlan(p.copy(scenarioId = scenarioId, pieces = if (p.locked) p.pieces else emptyList()))
        refresh()
    }

    fun toggleLock(day: Long) = viewModelScope.launch {
        val p = plans.allPlans().firstOrNull { it.epochDay == day } ?: return@launch
        plans.savePlan(p.copy(locked = !p.locked))
        refresh()
    }

    fun clearDay(day: Long) = viewModelScope.launch { plans.deletePlan(day); refresh() }

    /** Tap one day then another to swap their outfits. */
    fun swapTap(day: Long) = viewModelScope.launch {
        val from = _s.value.swapFrom
        if (from == null) { _s.update { it.copy(swapFrom = day) }; return@launch }
        _s.update { it.copy(swapFrom = null) }
        if (from == day) return@launch
        val all = plans.allPlans().associateBy { it.epochDay }
        val a = all[from]; val b = all[day]
        if (a != null) plans.savePlan(a.copy(epochDay = day, scenarioId = b?.scenarioId ?: a.scenarioId))
        if (b != null) plans.savePlan(b.copy(epochDay = from, scenarioId = a?.scenarioId ?: b.scenarioId)) else plans.deletePlan(from)
        refresh()
    }

    fun autoPlan(): Job = viewModelScope.launch {
        _s.update { it.copy(planning = true) }
        val s = _s.value
        val base = studio.context(time.today(), s.days.firstOrNull()?.scenario ?: studio.scenario("casual"))
        val inputs = s.days.map { d -> DayContext(d.epochDay, d.scenario, d.weather, d.plan?.takeIf { it.locked && it.pieces.isNotEmpty() }?.pieces) }
        val plan = withContext(Dispatchers.Default) { WeekPlanner().plan(inputs, base, base.items.values.toList()) }
        for (pd in plan.days) {
            val o = pd.outfit ?: continue
            plans.savePlan(DayPlan(pd.day.epochDay, pd.day.scenario.id, o.pieces, locked = pd.day.locked != null,
                plannedFeels = pd.day.weather.feelsMid, plannedRain = pd.day.weather.rainProbMax >= 50, score = o.total))
        }
        _s.update { it.copy(conflicts = plan.conflicts, planning = false) }
        refresh()
    }

    fun markAllWashed() = viewModelScope.launch {
        wardrobe.all().filter { it.status == ItemStatus.LAUNDRY || it.laundry.wearsSinceWash > 0 }.forEach {
            wardrobe.upsert(it.copy(status = if (it.status == ItemStatus.LAUNDRY) ItemStatus.AVAILABLE else it.status, laundry = it.laundry.copy(wearsSinceWash = 0)), time.nowMillis())
        }
    }

    fun washed(id: String) = viewModelScope.launch {
        val i = wardrobe.get(id) ?: return@launch
        wardrobe.upsert(i.copy(status = ItemStatus.AVAILABLE, laundry = i.laundry.copy(wearsSinceWash = 0)), time.nowMillis())
    }

    fun toLaundry(id: String) = viewModelScope.launch {
        val i = wardrobe.get(id) ?: return@launch
        wardrobe.upsert(i.copy(status = ItemStatus.LAUNDRY), time.nowMillis())
    }

    fun dismissReplanned() = _s.update { it.copy(replanned = false) }

    suspend fun current() = settings.current()
}
