package io.github.aristidisg.ensemble.ui.today

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
import androidx.compose.material3.AssistChip
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OutfitCard(card: ScoredOutfit, items: Map<String, Item>, onOpen: () -> Unit, actions: PieceActions, modifier: Modifier = Modifier) {
    val placed = card.pieces.mapNotNull { p -> items[p.itemId]?.let { it to p.slot } }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Collage(placed, Modifier.fillMaxSize())
                if (card.newIdea) Text(
                    stringResource(R.string.new_idea), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.match_pct, card.matchPct), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onOpen, label = { Text(card.harmony, maxLines = 1) })
                val f = card.results.firstOrNull { it.id == "F2" }?.args?.get("f")
                if (f != null) AssistChip(onClick = onOpen, label = { Text("${stringResource(R.string.formality)} $f") })
                card.weatherLine?.let { w ->
                    AssistChip(onClick = onOpen, label = { Text(explain(w), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            card.why.firstOrNull()?.let {
                Text(explain(it), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
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
        ItemArt(item, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(LocalEnsembleColors.current.backdrop)
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
