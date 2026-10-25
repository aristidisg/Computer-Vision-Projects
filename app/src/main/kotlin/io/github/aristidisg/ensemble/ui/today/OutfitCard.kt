package io.github.aristidisg.ensemble.ui.today

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import io.github.aristidisg.ensemble.ui.common.hexColor
import io.github.aristidisg.ensemble.ui.common.techniqueBlurb
import io.github.aristidisg.ensemble.ui.common.tipText
import io.github.aristidisg.ensemble.ui.common.techniqueName
import io.github.aristidisg.ensemble.ui.theme.HeroStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OutfitPiece
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.ItemArt
import io.github.aristidisg.ensemble.ui.common.explain
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors

/** Actions available from a long-press on one piece. */
data class PieceActions(
    val like: (String) -> Unit,
    val dislike: (String) -> Unit,
    val swap: (OutfitPiece) -> Unit,
    val lock: (OutfitPiece) -> Unit,
    val open: (String) -> Unit,
)

/**
 * The Today card, magazine style: the technique as a headline, the Match as a bold badge, the
 * collage as the hero (it always gets most of the height) and the outfit's palette as dots.
 */
@Composable
fun OutfitCard(
    card: ScoredOutfit, items: Map<String, Item>, onOpen: () -> Unit, actions: PieceActions, modifier: Modifier = Modifier,
    /** Vogue stylist: a cover look, with the styling notes as cover lines. */
    cover: Boolean = false,
) {
    val placed = card.pieces.mapNotNull { p -> items[p.itemId]?.let { it to p.slot } }
    val palette = placed.mapNotNull { (it, _) -> it.colors.firstOrNull()?.let { c -> hexColor(c.hex) } }.distinct()
    val accent = LocalEnsembleColors.current.accent
    val wash = Brush.verticalGradient(
        listOf((palette.getOrNull(0) ?: accent).copy(alpha = 0.22f), (palette.getOrNull(1) ?: accent).copy(alpha = 0.08f), Color.Transparent),
    )
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.fillMaxSize().background(wash).padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val over = if (cover) stringResource(R.string.cover_look) else card.harmony
                        Text(over.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = if (cover) accent else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        if (card.newIdea) Text(
                            stringResource(R.string.new_idea), style = MaterialTheme.typography.labelSmall, color = LocalEnsembleColors.current.onAccent,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(accent).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(techniqueName(card.technique), style = HeroStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(techniqueBlurb(card.technique), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.padding(start = 10.dp).size(58.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Text("${card.matchPct}%", style = MaterialTheme.typography.titleMedium, color = LocalEnsembleColors.current.onAccent)
                }
            }
            Collage(placed, Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                palette.take(6).forEach { c ->
                    Box(Modifier.size(16.dp).clip(CircleShape).background(c).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                }
                Spacer(Modifier.width(6.dp))
                val f = card.results.firstOrNull { it.id == "F2" }?.args?.get("f")
                val meta = listOfNotNull(f?.let { "${stringResource(R.string.formality)} $it" }, card.weatherLine?.let { explain(it) }).joinToString("  ·  ")
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (!cover) card.why.firstOrNull { it.id != "STYLE" }?.let {
                Text(explain(it), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            // How to wear it: one note on a normal card, both as cover lines on a cover look.
            card.tips.take(if (cover) 2 else 1).forEach { t ->
                Text("— " + tipText(t), style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                card.pieces.forEach { p -> items[p.itemId]?.let { PieceThumb(it, p, actions, Modifier.weight(1f)) } }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PieceThumb(item: Item, piece: OutfitPiece, a: PieceActions, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        ItemArt(item, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop)
            .combinedClickable(onClick = { a.open(item.id) }, onLongClick = { menu = true }))
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(item.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            DropdownMenuItem(text = { Text(stringResource(R.string.piece_like)) }, onClick = { menu = false; a.like(item.id) })
            DropdownMenuItem(text = { Text(stringResource(R.string.piece_dislike)) }, onClick = { menu = false; a.dislike(item.id) })
            DropdownMenuItem(text = { Text(stringResource(R.string.piece_swap)) }, onClick = { menu = false; a.swap(piece) })
            DropdownMenuItem(text = { Text(stringResource(R.string.piece_lock)) }, onClick = { menu = false; a.lock(piece) })
            DropdownMenuItem(text = { Text(stringResource(R.string.piece_open)) }, onClick = { menu = false; a.open(item.id) })
        }
    }
}
