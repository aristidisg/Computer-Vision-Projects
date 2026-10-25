@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.aristidisg.ensemble.ui.you

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.repo.RuleRepository
import io.github.aristidisg.ensemble.engine.rules.RuleGroup
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.CustomRule
import io.github.aristidisg.ensemble.model.ItemField
import io.github.aristidisg.ensemble.model.ItemPredicate
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.RuleAction
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Strictness
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.EmptyState
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.SliderRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Every soft rule with a short description, for the toggles and weights screen. */
private val ruleCatalog = listOf(
    "C1" to "Harmony template fit", "C2" to "Accent count", "C3" to "60/30/10 proportion", "C4" to "Value contrast",
    "C5" to "Chroma balance", "C6" to "Temperature coherence", "C7" to "Classic pairings", "C8" to "Denim on denim",
    "C9" to "Near-miss colors", "C10" to "Leather harmony", "C11" to "Metal harmony", "C12" to "Personal color",
    "C13" to "Scenario palette", "C14" to "Seasonal nudge", "C15" to "Wedding white",
    "P1" to "One loud pattern", "P2" to "Pattern mixing", "P3" to "One graphic or logo",
    "M1" to "Fabric season", "M2" to "Visual weight", "M3" to "Sheen in formal outfits", "M4" to "Tonal texture", "M5" to "Tech with tailoring",
    "F1" to "Formality coherence", "F2" to "Formality target", "F3" to "Classic conflicts",
    "O1" to "Style coherence", "O2" to "Scenario styles", "O3" to "Your style weights", "O4" to "Signature pieces", "SIL" to "Silhouette",
    "SH1" to "Shoe formality", "SH2" to "Shoe × trouser", "SH3" to "Shoes and weather", "SH4" to "Shoe leather", "SH5" to "Shoe rest days", "SH6" to "Darker shoes",
    "AC1" to "Accessory budget", "AC2" to "Belt", "AC3" to "Watch", "AC4" to "Bag", "AC5" to "Headwear", "AC6" to "Sunglasses", "AC7" to "Scarf", "AC8" to "Jewelry",
    "W1" to "Warmth", "W2" to "Temperature swing", "W3" to "Rain", "W4" to "Wind", "W5" to "Sun", "W6" to "Heat", "W7" to "Snow",
    "R1" to "Item rotation", "R2" to "Near duplicates", "R4" to "Deck freshness", "R5" to "Forgotten pieces", "R6" to "Plan conflicts", "R7" to "Laundry",
)

@Composable
fun RulesScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column {
        BackBar(stringResource(R.string.rules_weights), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle(stringResource(R.string.strictness))
            ChoiceChips(Strictness.entries, setOf(s.strictness), { it.label }, vm::setStrictness)
            SectionTitle(stringResource(R.string.group_weights))
            RuleGroup.entries.filter { it != RuleGroup.LEARNED }.forEach { g ->
                val k = "group:${g.name}"
                SliderRow(g.label, (s.ruleWeights[k] ?: g.defaultWeight).toFloat(), 0f..0.5f, { v -> vm.update { it.copy(ruleWeights = it.ruleWeights + (k to v.toDouble())) } }, { "%.2f".format(it) })
            }
            SectionTitle(stringResource(R.string.rules_weights))
            ruleCatalog.forEach { (id, name) ->
                val on = s.ruleEnabled[id] ?: true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$id  $name", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(on, { v -> vm.update { it.copy(ruleEnabled = it.ruleEnabled + (id to v)) } })
                }
                if (on) SliderRow(stringResource(R.string.rule_weight), (s.ruleWeights[id] ?: 1.0).toFloat(), 0f..3f, { v -> vm.update { it.copy(ruleWeights = it.ruleWeights + (id to v.toDouble())) } }, { "×%.1f".format(it) })
            }
        }
    }
}

@HiltViewModel
class CustomRulesViewModel @Inject constructor(private val repo: RuleRepository) : ViewModel() {
    val rules = repo.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(r: CustomRule) = viewModelScope.launch { repo.saveRule(r) }
    fun delete(id: String) = viewModelScope.launch { repo.deleteRule(id) }
}

/** Value options for each predicate field. */
private fun valuesFor(f: ItemField): List<Pair<String, String>> = when (f) {
    ItemField.SLOT -> Slot.entries.map { it.name to it.label }
    ItemField.SUBCATEGORY -> Subcategory.entries.map { it.name to it.label }
    ItemField.COLOR_FAMILY -> ColorFamily.entries.map { it.name to it.label }
    ItemField.STYLE -> StyleTag.entries.map { it.name to it.label }
    ItemField.PATTERN -> PatternType.entries.map { it.name to it.label }
    ItemField.MATERIAL -> Material.entries.map { it.name to it.label }
    ItemField.CUSTOM_TAG, ItemField.ITEM -> emptyList()
}

@Composable
fun CustomRuleScreen(id: String?, nav: NavHostController, vm: CustomRulesViewModel = hiltViewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CustomRule?>(null) }
    Column {
        BackBar(stringResource(R.string.custom_rules), onBack = { if (editing != null) editing = null else nav.popBackStack() })
        val e = editing
        if (e == null) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (rules.isEmpty()) EmptyState(stringResource(R.string.no_rules))
                rules.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge)
                            Text(describe(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(r.enabled, { v -> vm.save(r.copy(enabled = v)) })
                        TextButton(onClick = { editing = r }) { Text(stringResource(R.string.edit)) }
                    }
                }
                Button(onClick = { editing = CustomRule(UUID.randomUUID().toString(), name = "", a = emptyList(), action = RuleAction.NEVER_TOGETHER) }, modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(stringResource(R.string.new_rule))
                }
            }
        } else RuleEditor(e, onChange = { editing = it }, onSave = { vm.save(it); editing = null }, onDelete = { vm.delete(it.id); editing = null })
    }
}

private fun describe(r: CustomRule): String {
    fun side(p: List<ItemPredicate>) = p.joinToString(" & ") { "${it.field.label.lowercase()} ${it.value.lowercase().replace('_', ' ')}" }
    val b = if (r.b.isNotEmpty()) " / ${side(r.b)}" else ""
    return "${side(r.a)}$b → ${r.action.label.lowercase()}"
}

@Composable
private fun RuleEditor(r: CustomRule, onChange: (CustomRule) -> Unit, onSave: (CustomRule) -> Unit, onDelete: (CustomRule) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        OutlinedTextField(r.name, { onChange(r.copy(name = it)) }, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth())
        SectionTitle(stringResource(R.string.rule_if))
        PredicateEditor(r.a) { onChange(r.copy(a = it)) }
        SectionTitle(stringResource(R.string.rule_then))
        ChoiceChips(RuleAction.entries, setOf(r.action), { it.label }, { onChange(r.copy(action = it)) })
        if (r.action == RuleAction.NEVER_TOGETHER || r.action == RuleAction.IF_THEN_REQUIRE || r.action == RuleAction.PREFER || r.action == RuleAction.AVOID) {
            SectionTitle(stringResource(R.string.rule_and))
            PredicateEditor(r.b) { onChange(r.copy(b = it)) }
        }
        if (!r.action.hard) SliderRow(stringResource(R.string.rule_weight), r.weight.toFloat(), 0.1f..1f, { onChange(r.copy(weight = it.toDouble())) }, { "%.1f".format(it) })
        SectionTitle(stringResource(R.string.rule_scope))
        val sc = r.scope
        SliderRow(stringResource(R.string.min_c), (sc.minTemp ?: -20.0).toFloat(), -20f..40f, { v -> onChange(r.copy(scope = sc.copy(minTemp = if (v <= -20f) null else v.toInt().toDouble()))) }, { if (it <= -20f) "—" else "${it.toInt()} °C" })
        SliderRow(stringResource(R.string.max_c), (sc.maxTemp ?: 40.0).toFloat(), -20f..40f, { v -> onChange(r.copy(scope = sc.copy(maxTemp = if (v >= 40f) null else v.toInt().toDouble()))) }, { if (it >= 40f) "—" else "${it.toInt()} °C" })
        ChoiceChips((1..7).toList(), sc.weekdays, { java.time.DayOfWeek.of(it).name.take(3) }, { d ->
            onChange(r.copy(scope = sc.copy(weekdays = if (d in sc.weekdays) sc.weekdays - d else sc.weekdays + d)))
        })
        OutlinedTextField(sc.scenarioIds.joinToString(","), { v -> onChange(r.copy(scope = sc.copy(scenarioIds = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()))) },
            label = { Text(stringResource(R.string.scenarios) + " (office, date…)") }, modifier = Modifier.fillMaxWidth())
        FlowRow(Modifier.padding(vertical = 16.dp)) {
            OutlinedButton(onClick = { onDelete(r) }) { Text(stringResource(R.string.delete)) }
            Button(onClick = { onSave(r.copy(name = r.name.ifBlank { describe(r) })) }, enabled = r.a.isNotEmpty(), modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun PredicateEditor(preds: List<ItemPredicate>, onChange: (List<ItemPredicate>) -> Unit) {
    var field by remember { mutableStateOf(ItemField.SLOT) }
    var free by remember { mutableStateOf("") }
    preds.forEachIndexed { i, p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${p.field.label}: ${p.value.lowercase().replace('_', ' ')}", Modifier.weight(1f))
            TextButton(onClick = { onChange(preds.filterIndexed { j, _ -> j != i }) }) { Text(stringResource(R.string.delete)) }
        }
    }
    ChoiceChips(ItemField.entries, setOf(field), { it.label }, { field = it }, scroll = true)
    val options = valuesFor(field)
    if (options.isNotEmpty()) ChoiceChips(options.map { it.first }, emptySet(), { v -> options.first { it.first == v }.second }, { v ->
        onChange(preds.filter { it.field != field } + ItemPredicate(field, v))
    }, scroll = true)
    else Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(free, { free = it }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (free.isNotBlank()) onChange(preds + ItemPredicate(field, free.trim())); free = "" }) { Text(stringResource(R.string.add)) }
    }
}
