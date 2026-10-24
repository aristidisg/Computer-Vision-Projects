package io.github.aristidisg.ensemble.ui.outfits

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.files.ImageStore
import io.github.aristidisg.ensemble.data.repo.LookRepository
import io.github.aristidisg.ensemble.domain.FeedbackRecorder
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.planner.LookMatch
import io.github.aristidisg.ensemble.engine.planner.Recreate
import io.github.aristidisg.ensemble.model.CategoryGroup
import io.github.aristidisg.ensemble.model.InspirationLook
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.LookPiece
import io.github.aristidisg.ensemble.model.OutfitSource
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.Silhouette
import io.github.aristidisg.ensemble.model.StyleTag
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.photo.ImageOps
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.ChoiceChips
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.ColorDot
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.nav.InsightsRoute
import io.github.aristidisg.ensemble.ui.nav.LookEditorRoute
import io.github.aristidisg.ensemble.ui.nav.LookMatchRoute
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class LookState(
    val photo: Bitmap? = null,
    val pieces: List<LookPiece> = emptyList(),
    val editing: LookPiece? = null,
    val styles: Set<StyleTag> = emptySet(),
    val savedId: String? = null,
    val match: LookMatch? = null,
    val items: Map<String, Item> = emptyMap(),
)

@HiltViewModel
class LookViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val looks: LookRepository,
    private val images: ImageStore,
    private val studio: Studio,
    private val recorder: FeedbackRecorder,
    private val time: TimeSource,
) : ViewModel() {
    private val _s = MutableStateFlow(LookState())
    val state = _s.asStateFlow()

    fun loadPhoto(uri: Uri) = viewModelScope.launch {
        val b = withContext(Dispatchers.IO) { ImageOps.loadNormalized(app.contentResolver, uri) }
        _s.update { it.copy(photo = b) }
    }

    fun startPiece(sub: Subcategory) = _s.update { it.copy(editing = LookPiece(sub, "#808080", ColorScience.nearestFamily(ColorScience.hexToLab("#808080")))) }
    fun editPiece(t: (LookPiece) -> LookPiece) = _s.update { s -> s.copy(editing = s.editing?.let(t)) }
    fun commitPiece() = _s.update { s -> s.copy(pieces = s.pieces + listOfNotNull(s.editing), editing = null) }
    fun removePiece(i: Int) = _s.update { s -> s.copy(pieces = s.pieces.filterIndexed { j, _ -> j != i }) }
    fun toggleStyle(t: StyleTag) = _s.update { s -> s.copy(styles = if (t in s.styles) s.styles - t else s.styles + t) }

    /** Eyedropper on the inspiration photo. */
    fun tap(rx: Float, ry: Float) {
        val b = _s.value.photo ?: return
        val lab = ImageOps.pick(b, (rx * b.width).toInt().coerceIn(0, b.width - 1), (ry * b.height).toInt().coerceIn(0, b.height - 1))
        editPiece { it.copy(hex = ColorScience.labToHex(lab), family = ColorScience.nearestFamily(lab)) }
    }

    fun save() = viewModelScope.launch {
        val s = _s.value
        if (s.pieces.isEmpty()) return@launch
        val id = UUID.randomUUID().toString()
        val path = s.photo?.let { b -> withContext(Dispatchers.IO) { ImageOps.saveJpeg(ImageOps.scaleToFit(b, 1280), images.lookPhoto(id)) }; images.relative(images.lookPhoto(id)) }
        val look = InspirationLook(id, path, s.pieces, s.styles, createdAtMillis = time.nowMillis())
        looks.save(look)
        val day = time.today()
        recorder.inspirationAdded(look, studio.context(day, studio.scenario(studio.defaultScenarioId(day))))
        _s.update { it.copy(savedId = id) }
    }

    fun loadMatch(id: String) = viewModelScope.launch {
        val look = looks.all().firstOrNull { it.id == id } ?: return@launch
        val day = time.today()
        val ctx = studio.context(day, studio.scenario(studio.defaultScenarioId(day)))
        val m = withContext(Dispatchers.Default) { Recreate.recreate(look, ctx.items.values.toList(), ctx) }
        val photo = look.photoPath?.let { images.resolve(it) }?.let { f -> withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.path) } }
        _s.update { it.copy(match = m, items = ctx.items, photo = photo) }
    }

    fun saveRecreated(done: () -> Unit) = viewModelScope.launch {
        val m = _s.value.match?.outfit ?: return@launch
        val day = time.today()
        recorder.save(m.pieces, studio.context(day, studio.scenario(studio.defaultScenarioId(day))), OutfitSource.RECREATED)
        done()
    }

    fun delete(id: String, done: () -> Unit) = viewModelScope.launch { looks.delete(id); images.deleteLook(id); done() }
}

@Composable
fun LookEditorScreen(route: LookEditorRoute, nav: NavHostController, vm: LookViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::loadPhoto) }
    LaunchedEffect(route.uri) { route.uri?.let { vm.loadPhoto(Uri.parse(it)) } }
    LaunchedEffect(s.savedId) { s.savedId?.let { nav.popBackStack(); nav.navigate(LookMatchRoute(it)) } }
    Column {
        BackBar(stringResource(R.string.add_look), onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val photo = s.photo
            if (photo == null) OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.add_gallery)) }
            else {
                val bmp = remember(photo) { photo.asImageBitmap() }
                Image(bmp, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(300.dp).pointerInput(Unit) {
                    detectTapGestures { p ->
                        val scale = minOf(size.width / photo.width.toFloat(), size.height / photo.height.toFloat())
                        val dw = photo.width * scale; val dh = photo.height * scale
                        vm.tap((p.x - (size.width - dw) / 2) / dw, (p.y - (size.height - dh) / 2) / dh)
                    }
                })
            }
            SectionTitle(stringResource(R.string.mark_pieces))
            s.pieces.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(p.hex, "${p.subcategory.label} · ${p.family.label} · ${p.pattern.label}", Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { vm.removePiece(i) }) { Text(stringResource(R.string.delete)) }
                }
            }
            val e = s.editing
            if (e == null) {
                Text(stringResource(R.string.add_piece_to_look), style = MaterialTheme.typography.titleSmall)
                CategoryGroup.entries.forEach { g ->
                    ChoiceChips(Subcategory.entries.filter { it.group == g }, emptySet(), { it.label }, vm::startPiece, scroll = true)
                }
            } else {
                Text(e.subcategory.label, style = MaterialTheme.typography.titleMedium)
                ColorDot(e.hex, e.family.label)
                Text(stringResource(R.string.tap_color), style = MaterialTheme.typography.bodySmall)
                ChoiceChips(PatternType.entries, setOf(e.pattern), { it.label }, { p -> vm.editPiece { it.copy(pattern = p) } }, scroll = true)
                ChoiceChips(Silhouette.entries, setOf(e.silhouette), { it.label }, { v -> vm.editPiece { it.copy(silhouette = v) } })
                Button(onClick = vm::commitPiece) { Text(stringResource(R.string.add)) }
            }
            SectionTitle(stringResource(R.string.styles))
            ChoiceChips(StyleTag.entries, s.styles, { it.label }, vm::toggleStyle)
        }
        Button(onClick = vm::save, enabled = s.pieces.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(stringResource(R.string.recreate)) }
    }
}

@Composable
fun LookMatchScreen(id: String, nav: NavHostController, vm: LookViewModel = hiltViewModel()) {
    LaunchedEffect(id) { vm.loadMatch(id) }
    val s by vm.state.collectAsStateWithLifecycle()
    Column {
        BackBar(stringResource(R.string.recreate), onBack = { nav.popBackStack() }) {
            androidx.compose.material3.TextButton(onClick = { vm.delete(id) { nav.popBackStack() } }) { Text(stringResource(R.string.delete)) }
        }
        val m = s.match ?: return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            s.photo?.let { val bmp = remember(it) { it.asImageBitmap() }; Image(bmp, null, Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit) }
            m.outfit?.let { o ->
                Collage(o.pieces.mapNotNull { p -> s.items[p.itemId]?.let { it to p.slot } }, Modifier.fillMaxWidth().height(260.dp).padding(top = 12.dp))
            }
            Text(stringResource(R.string.match_pct, (m.overall * 100).toInt()), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))
            m.pieces.forEach { pm ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(pm.piece.hex, pm.piece.subcategory.label, Modifier.weight(1f))
                    if (pm.missing) Text(stringResource(R.string.missing_piece), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { nav.navigate(InsightsRoute) })
                    else pm.item?.let { item ->
                        ItemArt(item, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop))
                        Text(" ${(pm.similarity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Button(onClick = { vm.saveRecreated { nav.popBackStack() } }, enabled = m.outfit != null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.save_outfit)) }
        }
    }
}
