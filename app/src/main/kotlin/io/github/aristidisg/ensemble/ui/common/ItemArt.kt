package io.github.aristidisg.ensemble.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import io.github.aristidisg.ensemble.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import java.io.File

fun hexColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

/** The item's cutout (or photo); without one, a category silhouette tinted with its colors. */
@Composable
fun ItemArt(item: Item, modifier: Modifier = Modifier, thumb: Boolean = true) {
    val ctx = LocalContext.current
    val path = (if (thumb) item.photos.thumb else null) ?: item.photos.display
    val file = remember(path) { path?.let { File(ctx.filesDir, it) }?.takeIf { it.exists() } }
    if (file != null) {
        AsyncImage(model = file, contentDescription = item.name, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Placeholder(item, modifier)
    }
}

@Composable
private fun Placeholder(item: Item, modifier: Modifier) {
    val main = item.colors.firstOrNull()?.let { hexColor(it.hex) } ?: Color.Gray
    val second = item.colors.getOrNull(1)?.let { hexColor(it.hex) }
    val outline = Color(0x33000000)
    Canvas(modifier) {
        val p = silhouette(item.slots.first(), item.subcategory.name, size)
        drawPath(p, main)
        if (second != null) {
            // A stripe of the secondary color so prints and two-tone pieces read as such.
            clipPathSafe(p) { drawRect(second, topLeft = Offset(0f, size.height * 0.45f), size = Size(size.width, size.height * 0.12f)) }
        }
        drawPath(p, outline, style = Stroke(width = 2f))
    }
}

private fun DrawScope.clipPathSafe(p: Path, block: DrawScope.() -> Unit) =
    drawContext.canvas.let { c -> c.save(); c.clipPath(p); block(); c.restore() }

/** Simple flat-lay silhouettes per slot. */
fun silhouette(slot: Slot, sub: String, s: Size): Path = Path().apply {
    val w = s.width; val h = s.height
    when (slot) {
        Slot.TOP, Slot.MID, Slot.OUTER -> {
            val long = slot == Slot.OUTER && (sub.contains("COAT") || sub == "PARKA" || sub == "TRENCH_COAT")
            val bottom = if (long) 0.95f else 0.85f
            moveTo(w * 0.35f, h * 0.1f); lineTo(w * 0.65f, h * 0.1f); lineTo(w * 0.92f, h * 0.25f); lineTo(w * 0.85f, h * 0.45f)
            lineTo(w * 0.75f, h * 0.4f); lineTo(w * 0.75f, h * bottom); lineTo(w * 0.25f, h * bottom); lineTo(w * 0.25f, h * 0.4f)
            lineTo(w * 0.15f, h * 0.45f); lineTo(w * 0.08f, h * 0.25f); close()
        }
        Slot.BOTTOM -> {
            val shorts = sub.contains("SHORTS")
            val legEnd = if (shorts) 0.55f else 0.95f
            moveTo(w * 0.28f, h * 0.05f); lineTo(w * 0.72f, h * 0.05f); lineTo(w * 0.76f, h * legEnd); lineTo(w * 0.56f, h * legEnd)
            lineTo(w * 0.5f, h * 0.3f); lineTo(w * 0.44f, h * legEnd); lineTo(w * 0.24f, h * legEnd); close()
        }
        Slot.SHOES -> {
            moveTo(w * 0.1f, h * 0.55f); lineTo(w * 0.45f, h * 0.45f); lineTo(w * 0.6f, h * 0.35f); lineTo(w * 0.72f, h * 0.38f)
            lineTo(w * 0.9f, h * 0.55f); lineTo(w * 0.92f, h * 0.68f); lineTo(w * 0.1f, h * 0.68f); close()
        }
        Slot.BELT -> addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.05f, h * 0.42f, w * 0.95f, h * 0.58f, w * 0.03f, w * 0.03f))
        Slot.WATCH, Slot.JEWELRY -> addOval(androidx.compose.ui.geometry.Rect(w * 0.3f, h * 0.3f, w * 0.7f, h * 0.7f))
        Slot.BAG -> { moveTo(w * 0.2f, h * 0.35f); lineTo(w * 0.8f, h * 0.35f); lineTo(w * 0.85f, h * 0.9f); lineTo(w * 0.15f, h * 0.9f); close() }
        Slot.HEADWEAR -> { moveTo(w * 0.2f, h * 0.6f); cubicTo(w * 0.2f, h * 0.2f, w * 0.8f, h * 0.2f, w * 0.8f, h * 0.6f); lineTo(w * 0.95f, h * 0.65f); lineTo(w * 0.2f, h * 0.65f); close() }
        Slot.EYEWEAR -> { addOval(androidx.compose.ui.geometry.Rect(w * 0.12f, h * 0.4f, w * 0.45f, h * 0.62f)); addOval(androidx.compose.ui.geometry.Rect(w * 0.55f, h * 0.4f, w * 0.88f, h * 0.62f)) }
        else -> addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.35f, h * 0.1f, w * 0.65f, h * 0.9f, w * 0.05f, w * 0.05f))
    }
}

/** Outfit collage on a neutral studio-grey backdrop: layers on top, bottom, then shoes and accessories. */
@Composable
fun Collage(pieces: List<Pair<Item, Slot>>, modifier: Modifier = Modifier) {
    val bySlot = pieces.groupBy({ it.second }, { it.first })
    val upper = listOfNotNull(bySlot[Slot.OUTER]?.first(), bySlot[Slot.MID]?.first(), bySlot[Slot.TOP]?.first())
    val acc = pieces.filter { !it.second.isCore }.map { it.first }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(LocalEnsembleColors.current.backdrop).padding(12.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().weight(1.1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // A top under a pullover mid layer barely shows: drawn faded and tagged "under".
                val hiddenTop = bySlot[Slot.MID]?.first()?.layering?.canWearOpen == false
                upper.forEach { item ->
                    val under = hiddenTop && item == bySlot[Slot.TOP]?.first()
                    Box(Modifier.weight(if (under) 0.7f else 1f).fillMaxSize()) {
                        ItemArt(item, Modifier.fillMaxSize().alpha(if (under) 0.45f else 1f))
                        if (under) Text(
                            stringResource(R.string.worn_under), style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                bySlot[Slot.BOTTOM]?.first()?.let { ItemArt(it, Modifier.weight(1.4f).fillMaxSize()) }
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bySlot[Slot.SHOES]?.first()?.let { ItemArt(it, Modifier.weight(1f).fillMaxWidth()) }
                    if (acc.isNotEmpty()) Row(Modifier.weight(0.7f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        acc.take(4).forEach { ItemArt(it, Modifier.weight(1f).aspectRatio(1f)) }
                    }
                }
            }
        }
    }
}
