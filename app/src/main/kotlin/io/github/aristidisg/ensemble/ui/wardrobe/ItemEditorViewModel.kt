package io.github.aristidisg.ensemble.ui.wardrobe

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aristidisg.ensemble.data.files.ImageStore
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.data.repo.WardrobeRepository
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.ColorEdits
import io.github.aristidisg.ensemble.engine.ItemFactory
import io.github.aristidisg.ensemble.engine.color.ColorScience
import io.github.aristidisg.ensemble.engine.color.KMeans
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.ItemColor
import io.github.aristidisg.ensemble.model.ItemPhotos
import io.github.aristidisg.ensemble.model.Pattern
import io.github.aristidisg.ensemble.model.PatternScale
import io.github.aristidisg.ensemble.model.PatternType
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.photo.CategoryHints
import io.github.aristidisg.ensemble.photo.Cutout
import io.github.aristidisg.ensemble.photo.Gains
import io.github.aristidisg.ensemble.photo.ImageOps
import io.github.aristidisg.ensemble.photo.Mask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

enum class WbMode { AUTO, TAP, OFF }
enum class EditorTool { NONE, WHITE_BALANCE_TAP, ERASE, RESTORE, EYEDROPPER }

data class EditorState(
    val processing: Boolean = false,
    val queue: List<String> = emptyList(),
    val index: Int = 0,
    val original: Bitmap? = null,
    val corrected: Bitmap? = null,
    val cutout: Bitmap? = null,
    val mask: Mask? = null,
    val usedMlKit: Boolean = false,
    /** Off by default: the phone camera already white-balances; Auto uses the background only. */
    val wbMode: WbMode = WbMode.OFF,
    val tool: EditorTool = EditorTool.NONE,
    val brushSize: Float = 16f,
    val tolerance: Float = 0.08f,
    val hints: List<Subcategory> = emptyList(),
    val patternHint: Boolean = false,
    /** Colors k-means found in the latest cutout; used when the draft is created. */
    val detected: List<ItemColor> = emptyList(),
    val draft: Item? = null,
    /** Once the user edits colors by hand, photo changes no longer overwrite them. */
    val colorsEdited: Boolean = false,
    val existing: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val wardrobe: WardrobeRepository,
    private val images: ImageStore,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {
    private val _s = MutableStateFlow(EditorState())
    /** For the "Same as…" picker. */
    val wardrobeItems = wardrobe.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val state = _s.asStateFlow()
    private var gains = Gains.IDENTITY
    private var started = false

    fun start(itemId: String?, uris: String?) {
        if (started) return
        started = true
        when {
            itemId != null -> viewModelScope.launch { _s.update { it.copy(draft = wardrobe.get(itemId), existing = true) } }
            !uris.isNullOrBlank() -> { _s.update { it.copy(queue = uris.split('|')) }; process(0) }
            else -> _s.update { it.copy(draft = null) }
        }
    }

    private fun process(index: Int) = viewModelScope.launch {
        val uri = _s.value.queue.getOrNull(index) ?: return@launch
        _s.update { EditorState(processing = true, queue = it.queue, index = index) }
        runCatching {
            val st = settings.current()
            val original = withContext(Dispatchers.IO) { ImageOps.loadNormalized(app.contentResolver, Uri.parse(uri)) }
            gains = Gains.IDENTITY
            val corrected = original
            val ml = if (st.cutouts) Cutout.mlKit(corrected) else null
            val mask = ml ?: withContext(Dispatchers.Default) { Cutout.fallback(corrected, _s.value.tolerance) }
            val hints = if (st.categoryHints) CategoryHints.suggest(ImageOps.scaleToFit(corrected, 512)) else emptyList()
            _s.update { it.copy(original = original, corrected = corrected, mask = mask, usedMlKit = ml != null, hints = hints) }
            rebuildCutout()
        }.onFailure { e -> _s.update { it.copy(processing = false, error = e.message) } }
    }

    /** Recomputes the cutout, colors and pattern hint after any photo change. */
    private suspend fun rebuildCutout() {
        val s = _s.value
        val corrected = s.corrected ?: return
        val mask = s.mask ?: return
        val cut = withContext(Dispatchers.Default) { val f = mask.feathered(1); ImageOps.withMask(corrected, f.a, f.w, f.h) }
        val colors = withContext(Dispatchers.Default) { ImageOps.extractColors(cut) }
        val patterned = withContext(Dispatchers.Default) { ImageOps.looksPatterned(cut) }
        val draft = s.draft?.takeIf { !s.colorsEdited }?.let { d -> ColorEdits.apply(d, colors) }
        _s.update { it.copy(cutout = cut, processing = false, patternHint = patterned, detected = colors, draft = draft ?: it.draft) }
    }

    fun setWb(mode: WbMode) {
        if (mode == WbMode.TAP) { _s.update { it.copy(tool = EditorTool.WHITE_BALANCE_TAP, wbMode = mode) }; return }
        viewModelScope.launch {
            val orig = _s.value.original ?: return@launch
            // Auto assumes the backdrop is neutral, so it averages the background, never the garment.
            gains = if (mode == WbMode.AUTO) ImageOps.grayWorldGains(orig, _s.value.mask) else Gains.IDENTITY
            val corrected = withContext(Dispatchers.Default) { ImageOps.applyGains(orig, gains) }
            _s.update { it.copy(wbMode = mode, corrected = corrected, processing = true) }
            rebuildCutout()
        }
    }

    fun setTool(t: EditorTool) = _s.update { it.copy(tool = if (it.tool == t) EditorTool.NONE else t) }
    fun setBrushSize(v: Float) = _s.update { it.copy(brushSize = v) }

    fun setTolerance(v: Float) {
        _s.update { it.copy(tolerance = v) }
        viewModelScope.launch {
            val c = _s.value.corrected ?: return@launch
            val m = withContext(Dispatchers.Default) { Cutout.fallback(c, v) }
            _s.update { it.copy(mask = m, usedMlKit = false, processing = true) }
            rebuildCutout()
        }
    }

    /** A tap on the photo at relative position (0..1, 0..1). */
    fun tap(rx: Float, ry: Float) {
        val s = _s.value
        val c = s.corrected ?: return
        val x = (rx * c.width).toInt().coerceIn(0, c.width - 1); val y = (ry * c.height).toInt().coerceIn(0, c.height - 1)
        when (s.tool) {
            EditorTool.WHITE_BALANCE_TAP -> viewModelScope.launch {
                val orig = s.original ?: return@launch
                gains = ImageOps.tapGains(orig, x, y)
                val corrected = withContext(Dispatchers.Default) { ImageOps.applyGains(orig, gains) }
                _s.update { it.copy(corrected = corrected, tool = EditorTool.NONE, processing = true) }
                rebuildCutout()
            }
            EditorTool.EYEDROPPER -> {
                val lab = ImageOps.pick(c, x, y)
                val hex = ColorScience.labToHex(lab)
                val d = s.draft ?: return
                val next = ColorEdits.add(d, hex, share = 0.1) ?: d
                _s.update { it.copy(draft = next, tool = EditorTool.NONE, colorsEdited = true) }
            }
            else -> Unit
        }
    }

    /** Brush stroke point in relative coordinates. */
    fun brush(rx: Float, ry: Float) {
        val s = _s.value
        val m = s.mask ?: return
        val restore = s.tool == EditorTool.RESTORE
        if (s.tool != EditorTool.ERASE && !restore) return
        val scale = m.w / (s.corrected?.width?.toFloat() ?: m.w.toFloat())
        m.brush(rx * m.w, ry * m.h, s.brushSize * scale * 2, restore)
    }

    fun brushEnd() = viewModelScope.launch { _s.update { it.copy(processing = true) }; rebuildCutout() }

    /** Picking a subcategory creates the draft with every smart default. */
    fun chooseSubcategory(sub: Subcategory) {
        val s = _s.value
        val colors = s.draft?.colors?.takeIf { it.isNotEmpty() } ?: s.detected.ifEmpty { listOf(KMeans.itemColor("#808080")) }
        val base = ItemFactory.create(s.draft?.id ?: UUID.randomUUID().toString(), sub, colors, s.draft?.pattern ?: Pattern(), createdEpochDay = time.today())
        val keep = s.draft
        _s.update { it.copy(draft = if (keep != null && s.existing) ItemFactory.recategorize(keep, sub) else base) }
    }

    fun update(transform: (Item) -> Item) = _s.update { s -> s.copy(draft = s.draft?.let(transform)) }

    fun setPattern(type: PatternType) = update { d ->
        ItemFactory.refreshDerived(d.copy(pattern = if (type == PatternType.SOLID) Pattern() else d.pattern.copy(type = type, scale = if (d.pattern.scale == PatternScale.NONE) PatternScale.SMALL else d.pattern.scale, loudness = maxOf(1, d.pattern.loudness))))
    }

    private fun editColors(edit: (Item) -> Item?): Boolean {
        val d = _s.value.draft ?: return false
        val next = edit(d) ?: return false
        _s.update { it.copy(draft = next, colorsEdited = true) }
        return true
    }

    /** Exact color by code ("#fff", "#1F2A44"); the rules use this exact value. False if invalid. */
    fun setColorHex(index: Int, input: String): Boolean = editColors { ColorEdits.setHex(it, index, input) }
    fun setColorShare(index: Int, share: Double) = editColors { ColorEdits.setShare(it, index, share) }
    fun addColor(input: String): Boolean = editColors { ColorEdits.add(it, input) }
    fun removeColor(index: Int) = editColors { ColorEdits.remove(it, index) }

    /** "Same as…": copy every tag from another piece, keeping this piece's colors and photo. */
    fun sameAs(source: Item) = _s.update { s ->
        val target = s.draft ?: ItemFactory.create(
            UUID.randomUUID().toString(), source.subcategory, s.detected.ifEmpty { source.colors }, createdEpochDay = time.today(),
        )
        s.copy(draft = ColorEdits.sameAs(target, source))
    }

    fun save(next: Boolean = true) = viewModelScope.launch {
        val s = _s.value
        val d = s.draft ?: return@launch
        var photos = d.photos
        val cut = s.cutout
        if (cut != null && s.original != null) withContext(Dispatchers.IO) {
            ImageOps.saveJpeg(s.original, images.original(d.id))
            ImageOps.saveWebp(cut, images.cutout(d.id))
            ImageOps.saveWebp(ImageOps.scaleToFit(cut, ImageOps.THUMB_EDGE), images.thumb(d.id), 85)
            photos = ItemPhotos(images.relative(images.original(d.id)), images.relative(images.cutout(d.id)), images.relative(images.thumb(d.id)))
        }
        wardrobe.upsert(d.copy(photos = photos), time.nowMillis())
        if (next && s.index + 1 < s.queue.size) process(s.index + 1) else _s.update { it.copy(saved = true) }
    }

    fun delete() = viewModelScope.launch {
        val d = _s.value.draft ?: return@launch
        wardrobe.delete(d.id); images.deleteItem(d.id)
        _s.update { it.copy(saved = true) }
    }

    /** "Duplicate as new color": same piece, fresh ID, same photo, ready for a new color. */
    fun duplicate() = viewModelScope.launch {
        val d = _s.value.draft ?: return@launch
        val id = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) { images.copyItem(d.id, id) }
        val copy = d.copy(id = id, name = d.name + " (copy)", photos = d.photos.let { p ->
            ItemPhotos(p.original?.replace(d.id, id), p.cutout?.replace(d.id, id), p.thumb?.replace(d.id, id))
        }, laundry = d.laundry.copy(wearsSinceWash = 0), createdEpochDay = time.today())
        wardrobe.upsert(copy, time.nowMillis())
        _s.update { it.copy(draft = copy, existing = true) }
    }

    fun skip() { val s = _s.value; if (s.index + 1 < s.queue.size) process(s.index + 1) else _s.update { it.copy(saved = true) } }
}
