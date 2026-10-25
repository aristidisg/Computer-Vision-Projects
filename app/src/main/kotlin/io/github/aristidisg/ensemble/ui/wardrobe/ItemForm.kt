package io.github.aristidisg.ensemble.ui.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import io.github.aristidisg.ensemble.model.CategoryGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.ItemFactory
import io.github.aristidisg.ensemble.model.AccessoryExtras
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.FabricWeight
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemStatus
import io.github.aristidisg.ensemble.model.Material
import io.github.aristidisg.ensemble.model.MetalTone
import io.github.aristidisg.ensemble.model.PatternScale
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.Season
import io.github.aristidisg.ensemble.model.ShoeProfile
import io.github.aristidisg.ensemble.model.ShoeSole
import io.github.aristidisg.ensemble.model.ShoeUpper
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.WaterResistance
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.SliderRow
import io.github.aristidisg.ensemble.ui.common.SwitchRow
import kotlin.math.roundToInt

private fun <T> Set<T>.toggled(t: T): Set<T> = if (t in this) this - t else this + t
private fun <T> List<T>.toggled(t: T): List<T> = if (t in this) this - t else this + t

/** Every Item field from SPEC 2, pre-filled from the subcategory; only fix what's wrong. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemForm(d: Item, suggestPattern: Boolean, vm: ItemEditorViewModel) {
    var advanced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(d.name, { v -> vm.update { it.copy(name = v) } }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))

        ColorsEditor(d, vm)

        if (suggestPattern) {
            SectionTitle(stringResource(R.string.patterned_q))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(PatternType.STRIPES, PatternType.CHECK, PatternType.FLORAL).forEach { p -> AssistChip(onClick = { vm.setPattern(p) }, label = { Text(p.label) }) }
            }
        }
        SectionTitle(stringResource(R.string.pattern))
        ChoiceChips(PatternType.entries, setOf(d.pattern.type), { it.label }, vm::setPattern, scroll = true)
        if (d.pattern.isPatterned) {
            ChoiceChips(listOf(PatternScale.SMALL, PatternScale.LARGE), setOf(d.pattern.scale), { it.name.lowercase() }, { sc -> vm.update { it.copy(pattern = it.pattern.copy(scale = sc)) } })
            SliderRow(stringResource(R.string.loudness), d.pattern.loudness.toFloat(), 0f..3f, { v -> vm.update { it.copy(pattern = it.pattern.copy(loudness = v.roundToInt())) } }, { "${it.roundToInt()}" }, steps = 2)
        }

        // How the piece is worn decides where it can go in an outfit; shown for every top, mid
        // layer and jacket so "this isn't a jacket" is one tap, not hidden in Advanced.
        if (d.subcategory.group == CategoryGroup.TOPS || d.subcategory.group == CategoryGroup.OUTERWEAR) {
            SectionTitle(stringResource(R.string.worn_as))
            val layers = listOf(Slot.TOP to R.string.worn_as_top, Slot.MID to R.string.worn_as_mid, Slot.OUTER to R.string.worn_as_outer)
            val names = layers.associate { (slot, res) -> slot to stringResource(res) }
            ChoiceChips(layers.map { it.first }, d.slots.toSet(), { s -> names.getValue(s) }, { s ->
                vm.update { it.copy(slots = it.slots.toggled(s).ifEmpty { it.slots }) }
            })
            Text(stringResource(R.string.worn_as_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle(stringResource(R.string.silhouette))
        ChoiceChips(Silhouette.entries, setOf(d.silhouette), { it.label }, { v -> vm.update { it.copy(silhouette = v) } })
        SliderRow(stringResource(R.string.formality), d.formality.toFloat(), 1f..10f, { v -> vm.update { it.copy(formality = v.roundToInt()) } }, { "${it.roundToInt()}/10" }, steps = 8)
        SectionTitle(stringResource(R.string.styles))
        ChoiceChips(StyleTag.entries, d.styleTags, { it.label }, { t -> vm.update { it.copy(styleTags = it.styleTags.toggled(t)) } })
        SectionTitle(stringResource(R.string.seasons))
        ChoiceChips(Season.entries, d.seasons, { it.label }, { t -> vm.update { it.copy(seasons = it.seasons.toggled(t)) } })
        SectionTitle(stringResource(R.string.materials))
        ChoiceChips(Material.entries, d.materials.toSet(), { it.label }, { m -> vm.update { ItemFactory.refreshDerived(it.copy(materials = it.materials.toggled(m)), it) } })
        SectionTitle(stringResource(R.string.status))
        ChoiceChips(ItemStatus.entries, setOf(d.status), { it.label }, { v -> vm.update { it.copy(status = v) } }, scroll = true)
        SwitchRow(stringResource(R.string.favorite), d.flags.favorite, { v -> vm.update { it.copy(flags = it.flags.copy(favorite = v)) } })
        SwitchRow(stringResource(R.string.signature_piece), d.flags.signature, { v -> vm.update { it.copy(flags = it.flags.copy(signature = v)) } })
        SwitchRow(stringResource(R.string.never_suggest), d.flags.neverSuggest, { v -> vm.update { it.copy(flags = it.flags.copy(neverSuggest = v)) } })

        TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.advanced)) }
        if (advanced) Advanced(d, vm)
    }
}

@Composable
private fun Advanced(d: Item, vm: ItemEditorViewModel) {
    SectionTitle(stringResource(R.string.slots))
    ChoiceChips(Slot.entries, d.slots.toSet(), { it.label }, { s -> vm.update { it.copy(slots = it.slots.toggled(s).ifEmpty { it.slots }) } }, scroll = true)
    SectionTitle(stringResource(R.string.fabric_weight))
    ChoiceChips(FabricWeight.entries, setOf(d.fabricWeight), { it.name.lowercase() }, { v -> vm.update { ItemFactory.refreshDerived(it.copy(fabricWeight = v), it) } })
    SliderRow(stringResource(R.string.warmth), d.warmthClo.toFloat(), 0f..1f, { v -> vm.update { it.copy(warmthClo = (v * 100).roundToInt() / 100.0) } }, { "%.2f".format(it) })
    SectionTitle(stringResource(R.string.water))
    ChoiceChips(WaterResistance.entries, setOf(d.waterResistance), { it.name.lowercase() }, { v -> vm.update { it.copy(waterResistance = v) } })
    SwitchRow(stringResource(R.string.can_wear_open), d.layering.canWearOpen, { v -> vm.update { it.copy(layering = it.layering.copy(canWearOpen = v)) } })
    SwitchRow(stringResource(R.string.presentable_alone), d.layering.presentableAlone, { v -> vm.update { it.copy(layering = it.layering.copy(presentableAlone = v)) } })
    SliderRow(stringResource(R.string.bulk), d.layering.bulk.toFloat(), 1f..3f, { v -> vm.update { it.copy(layering = it.layering.copy(bulk = v.roundToInt())) } }, { "${it.roundToInt()}" }, steps = 1)
    d.shoe?.let { sh ->
        SectionTitle(stringResource(R.string.shoe_profile))
        ChoiceChips(ShoeProfile.entries, setOf(sh.profile), { it.name.lowercase() }, { v -> vm.update { it.copy(shoe = sh.copy(profile = v)) } })
        SectionTitle(stringResource(R.string.shoe_upper))
        ChoiceChips(ShoeUpper.entries, setOf(sh.upper), { it.name.lowercase() }, { v -> vm.update { it.copy(shoe = sh.copy(upper = v)) } }, scroll = true)
        SectionTitle(stringResource(R.string.shoe_sole))
        ChoiceChips(ShoeSole.entries, setOf(sh.sole), { it.name.lowercase() }, { v -> vm.update { it.copy(shoe = sh.copy(sole = v)) } })
        SwitchRow(stringResource(R.string.weatherproof), sh.weatherproof, { v -> vm.update { it.copy(shoe = sh.copy(weatherproof = v)) } })
        SliderRow(stringResource(R.string.rest_days), sh.restDays.toFloat(), 0f..3f, { v -> vm.update { it.copy(shoe = sh.copy(restDays = v.roundToInt())) } }, { "${it.roundToInt()}" }, steps = 2)
    }
    if (!d.slots.first().isCore || d.slots.first() == Slot.SHOES) {
        val a = d.accessory ?: AccessoryExtras()
        SectionTitle(stringResource(R.string.leather_color))
        ChoiceChips(listOf(ColorFamily.BLACK, ColorFamily.BROWN, ColorFamily.CHOCOLATE, ColorFamily.CAMEL, ColorFamily.TAUPE, ColorFamily.BURGUNDY), setOfNotNull(a.leatherColor), { it.label }, { v ->
            vm.update { it.copy(accessory = a.copy(leatherColor = if (a.leatherColor == v) null else v)) }
        }, scroll = true)
        SectionTitle(stringResource(R.string.metal_tone))
        ChoiceChips(MetalTone.entries, setOfNotNull(a.metalTone), { it.label }, { v -> vm.update { it.copy(accessory = a.copy(metalTone = if (a.metalTone == v) null else v)) } }, scroll = true)
    }
    SliderRow(stringResource(R.string.wash_after), d.laundry.wearsBeforeWash.toFloat(), 0f..30f, { v -> vm.update { it.copy(laundry = it.laundry.copy(wearsBeforeWash = v.roundToInt())) } }, { if (it < 1) "—" else "${it.roundToInt()}" })
    SliderRow(stringResource(R.string.wears_since_wash), d.laundry.wearsSinceWash.toFloat(), 0f..30f, { v -> vm.update { it.copy(laundry = it.laundry.copy(wearsSinceWash = v.roundToInt())) } }, { "${it.roundToInt()}" })
    OutlinedTextField(d.meta.brand, { v -> vm.update { it.copy(meta = it.meta.copy(brand = v)) } }, label = { Text(stringResource(R.string.brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(d.meta.size, { v -> vm.update { it.copy(meta = it.meta.copy(size = v)) } }, label = { Text(stringResource(R.string.size)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    var price by remember(d.id) { mutableStateOf(d.meta.price?.toString() ?: "") }
    OutlinedTextField(price, { v -> price = v; vm.update { it.copy(meta = it.meta.copy(price = v.replace(',', '.').toDoubleOrNull())) } },
        label = { Text(stringResource(R.string.price)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
    var tags by remember(d.id) { mutableStateOf(d.meta.customTags.joinToString(", ")) }
    OutlinedTextField(tags, { v -> tags = v; vm.update { it.copy(meta = it.meta.copy(customTags = v.split(',').map { t -> t.trim() }.filter { t -> t.isNotEmpty() }.toSet())) } },
        label = { Text(stringResource(R.string.custom_tags)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(d.meta.notes, { v -> vm.update { it.copy(meta = it.meta.copy(notes = v)) } }, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
}
