package io.github.aristidisg.ensemble.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.model.CategoryGroup
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.common.SliderRow
import io.github.aristidisg.ensemble.ui.nav.ItemEditorRoute
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors

@Composable
fun ItemEditorScreen(route: ItemEditorRoute, nav: NavHostController, vm: ItemEditorViewModel = hiltViewModel()) {
    LaunchedEffect(route) { vm.start(route.itemId, route.uris) }
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(s.saved) { if (s.saved) nav.popBackStack() }

    Column(Modifier.fillMaxSize()) {
        val title = if (s.existing) R.string.edit_piece else R.string.new_piece
        BackBar(stringResource(title) + if (s.queue.size > 1) " ${s.index + 1}/${s.queue.size}" else "", onBack = { nav.popBackStack() }) {
            if (s.queue.size > 1) TextButton(onClick = vm::skip) { Text(stringResource(R.string.skip)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (s.processing) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                CircularProgressIndicator(); Text(stringResource(R.string.processing_photo), Modifier.padding(start = 12.dp))
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (s.corrected != null) PhotoStep(s, vm)
            CategoryPicker(s, vm::chooseSubcategory)
            s.draft?.let { d -> ItemForm(d, s.patternHint && d.pattern.type == PatternType.SOLID, vm) }
        }
        if (s.draft != null) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.existing) {
                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
                OutlinedButton(onClick = vm::duplicate) { Text(stringResource(R.string.duplicate_color)) }
            }
            Button(onClick = { vm.save() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_piece_q)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PhotoStep(s: EditorState, vm: ItemEditorViewModel) {
    val shown = s.cutout ?: s.corrected ?: return
    val bmp = remember(shown) { shown.asImageBitmap() }
    val brushing = s.tool == EditorTool.ERASE || s.tool == EditorTool.RESTORE
    Box(
        Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)).background(LocalEnsembleColors.current.backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bmp, null, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .pointerInput(s.tool) {
                    detectTapGestures { p ->
                        val (rx, ry) = relative(p.x, p.y, size.width.toFloat(), size.height.toFloat(), shown.width.toFloat(), shown.height.toFloat())
                        if (rx in 0f..1f && ry in 0f..1f) vm.tap(rx, ry)
                    }
                }
                .then(if (brushing) Modifier.pointerInput(s.tool, s.brushSize) {
                    detectDragGestures(onDragEnd = { vm.brushEnd() }) { change, _ ->
                        val (rx, ry) = relative(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat(), shown.width.toFloat(), shown.height.toFloat())
                        vm.brush(rx, ry)
                    }
                } else Modifier),
        )
    }
    Text(stringResource(R.string.white_balance), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(WbMode.AUTO to R.string.wb_auto, WbMode.TAP to R.string.wb_tap, WbMode.OFF to R.string.wb_off).forEach { (m, l) ->
            FilterChip(selected = s.wbMode == m, onClick = { vm.setWb(m) }, label = { Text(stringResource(l)) })
        }
    }
    Text(stringResource(R.string.brush), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = s.tool == EditorTool.ERASE, onClick = { vm.setTool(EditorTool.ERASE) }, label = { Text(stringResource(R.string.brush_erase)) })
        FilterChip(selected = s.tool == EditorTool.RESTORE, onClick = { vm.setTool(EditorTool.RESTORE) }, label = { Text(stringResource(R.string.brush_restore)) })
        FilterChip(selected = s.tool == EditorTool.EYEDROPPER, onClick = { vm.setTool(EditorTool.EYEDROPPER) }, label = { Text(stringResource(R.string.eyedropper)) })
    }
    if (brushing) SliderRow(stringResource(R.string.brush_size), s.brushSize, 4f..60f, vm::setBrushSize, { "${it.toInt()}" })
    if (!s.usedMlKit) SliderRow(stringResource(R.string.cutout_tolerance), s.tolerance, 0.02f..0.2f, vm::setTolerance, { "%.2f".format(it) })
    if (s.tool == EditorTool.EYEDROPPER) Text(stringResource(R.string.eyedropper_hint), style = MaterialTheme.typography.bodySmall)
    if (s.tool == EditorTool.WHITE_BALANCE_TAP) Text(stringResource(R.string.wb_tap), style = MaterialTheme.typography.bodySmall)
}

/** Maps a touch in a Fit-scaled image box to 0..1 image coordinates. */
private fun relative(x: Float, y: Float, bw: Float, bh: Float, iw: Float, ih: Float): Pair<Float, Float> {
    val scale = minOf(bw / iw, bh / ih)
    val dw = iw * scale; val dh = ih * scale
    return ((x - (bw - dw) / 2) / dw) to ((y - (bh - dh) / 2) / dh)
}

@Composable
private fun CategoryPicker(s: EditorState, onPick: (Subcategory) -> Unit) {
    var group by remember { mutableStateOf<CategoryGroup?>(null) }
    if (s.hints.isNotEmpty()) {
        SectionTitle(stringResource(R.string.category_hints))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            s.hints.forEach { sub -> AssistChip(onClick = { onPick(sub) }, label = { Text(sub.label) }) }
        }
    }
    SectionTitle(stringResource(if (s.draft == null) R.string.choose_category else R.string.category))
    ChoiceChips(CategoryGroup.entries, setOfNotNull(group ?: s.draft?.subcategory?.group), { it.label }, { group = it }, scroll = true)
    val g = group ?: s.draft?.subcategory?.group
    if (g != null) ChoiceChips(Subcategory.entries.filter { it.group == g }, setOfNotNull(s.draft?.subcategory), { it.label }, onPick)
}
