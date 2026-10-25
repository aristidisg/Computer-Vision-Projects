package io.github.aristidisg.ensemble.ui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.ColorEdits
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemColor
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.hexColor
import kotlin.math.roundToInt

/**
 * Exact colors: each one has an editable code (#fff or #1F2A44) and a share of the piece, up to
 * [ColorEdits.MAX_COLORS]. The rules measure exactly these codes; the family is only a label.
 */
@Composable
fun ColorsEditor(d: Item, vm: ItemEditorViewModel) {
    SectionTitle(stringResource(R.string.colors))
    d.colors.forEachIndexed { i, c -> ColorRow(i, c, d.colors.size > 1, vm) }
    if (d.colors.size < ColorEdits.MAX_COLORS) AddColorRow(vm)
    Text(stringResource(R.string.color_code_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    var familyPick by remember { mutableStateOf(false) }
    TextButton(onClick = { familyPick = !familyPick }) { Text(stringResource(R.string.pick_by_name)) }
    if (familyPick) {
        val primary = d.colors.indices.maxByOrNull { d.colors[it].share } ?: 0
        ChoiceChips(ColorFamily.entries.filter { it != ColorFamily.MULTICOLOR }, setOfNotNull(d.primaryColor?.family), { it.label }, { f ->
            vm.setColorHex(primary, ColorTables.family(f).hex)
        }, scroll = true)
    }
}

@Composable
private fun Swatch(hex: String) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(hexColor(hex)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
}

@Composable
private fun ColorRow(index: Int, c: ItemColor, removable: Boolean, vm: ItemEditorViewModel) {
    var text by remember(index, c.hex) { mutableStateOf(c.hex) }
    val valid = ColorScience.normalizeHex(text) != null
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(ColorScience.normalizeHex(text) ?: c.hex)
            OutlinedTextField(
                text,
                { v ->
                    text = v.take(7)
                    // Six digits are unambiguous, so apply them while typing; #fff waits for Done.
                    if (text.removePrefix("#").length == 6) vm.setColorHex(index, text)
                },
                label = { Text(stringResource(R.string.color_code)) },
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.setColorHex(index, text) }),
                modifier = Modifier.weight(1f),
            )
            var matching by remember { mutableStateOf(false) }
            IconButton(onClick = { matching = true }) { Icon(Icons.Outlined.Palette, stringResource(R.string.match_by_eye)) }
            if (matching) ColorMatchDialog(c.hex, onDismiss = { matching = false }) { hex -> matching = false; vm.setColorHex(index, hex) }
            IconButton(onClick = { vm.removeColor(index) }, enabled = removable) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_color)) }
        }
        Text(
            stringResource(R.string.color_line, c.family.label, (c.share * 100).roundToInt(), c.role.name.lowercase()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (removable) Slider(value = c.share.toFloat(), onValueChange = { vm.setColorShare(index, it.toDouble()) }, valueRange = 0.01f..1f)
    }
}

@Composable
private fun AddColorRow(vm: ItemEditorViewModel) {
    var text by remember { mutableStateOf("") }
    val parsed = ColorScience.normalizeHex(text)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Swatch(parsed ?: "#00000000")
        OutlinedTextField(
            text, { text = it.take(7) },
            label = { Text(stringResource(R.string.add_color_code)) },
            placeholder = { Text("#C8A27A") },
            isError = text.isNotEmpty() && parsed == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (vm.addColor(text)) text = "" }),
            modifier = Modifier.weight(1f),
        )
        var matching by remember { mutableStateOf(false) }
        IconButton(onClick = { matching = true }) { Icon(Icons.Outlined.Palette, stringResource(R.string.match_by_eye)) }
        if (matching) ColorMatchDialog(parsed ?: "#808080", onDismiss = { matching = false }) { hex -> matching = false; vm.addColor(hex) }
        OutlinedButton(onClick = { if (vm.addColor(text)) text = "" }, enabled = parsed != null) { Text(stringResource(R.string.add)) }
    }
}

/** "Same as…": copy every tag from an existing piece; this piece keeps its own colors and photo. */
@Composable
fun SameAsPicker(pieces: List<Item>, onPick: (Item) -> Unit) {
    if (pieces.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.same_as)) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.same_as)) },
        text = {
            Column {
                Text(stringResource(R.string.same_as_body), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(pieces.sortedBy { it.name.lowercase() }, key = { it.id }) { i ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open = false; onPick(i) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ItemArt(i, Modifier.size(44.dp))
                            Column(Modifier.weight(1f)) {
                                Text(i.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(i.subcategory.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
