package io.github.aristidisg.ensemble.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.engine.planner.GapSuggestion
import io.github.aristidisg.ensemble.engine.planner.VarietyReport
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.ui.common.hexColor
import io.github.aristidisg.ensemble.ui.common.techniqueName
import io.github.aristidisg.ensemble.ui.theme.HeroStyle
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors

/** This week at a glance: variety score, pieces used, colours worn, repeats and forgotten pieces. */
@Composable
fun VarietySection(r: VarietyReport?) {
    val accent = LocalEnsembleColors.current.accent
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.10f)).padding(16.dp),
    ) {
        Text(stringResource(R.string.variety_title).uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
        val score = r?.score
        if (r == null || score == null) {
            Text(stringResource(R.string.variety_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            return@Column
        }
        Text(stringResource(R.string.variety_score, score), style = HeroStyle)
        Text(stringResource(R.string.variety_pieces, r.outfits, r.piecesWorn, r.pieces), style = MaterialTheme.typography.bodySmall)
        if (r.colors.isNotEmpty()) {
            Text(stringResource(R.string.variety_colors), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                r.colors.take(10).forEach { (f, n) -> Dot(f, (14 + 4 * minOf(n, 4)).dp) }
            }
        }
        if (r.repeats.isNotEmpty()) Text(
            stringResource(R.string.variety_repeats, r.repeats.joinToString { "${it.first.name} ×${it.second}" }),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
        )
        if (r.unworn.isNotEmpty()) Text(
            stringResource(R.string.variety_unworn, r.unworn.size, r.unworn.take(6).joinToString { it.name }),
            style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** One gap: the piece, how many outfits it unlocks, what it goes with, what it rescues and opens up. */
@Composable
fun GapRow(g: GapSuggestion, names: Map<String, String>, onList: Boolean, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Dot(g.color, 28.dp, Modifier.padding(top = 2.dp, end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(g.title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.gap_line, g.unlocks, g.fills.joinToString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val partners = g.pairsWith.mapNotNull { names[it] }
            if (partners.isNotEmpty()) Text(stringResource(R.string.gap_pairs, partners.joinToString()), style = MaterialTheme.typography.bodySmall)
            if (g.rescued > 0) Text(stringResource(R.string.gap_rescued, g.rescued), style = MaterialTheme.typography.bodySmall)
            if (g.techniques.isNotEmpty()) Text(
                stringResource(R.string.gap_techniques, g.techniques.take(3).map { techniqueName(it) }.joinToString()),
                style = MaterialTheme.typography.bodySmall, color = LocalEnsembleColors.current.accent,
            )
        }
        TextButton(onClick = onAdd, enabled = !onList) { Text(stringResource(if (onList) R.string.on_list else R.string.add_to_list)) }
    }
}

@Composable
private fun Dot(f: ColorFamily, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val c = if (f == ColorFamily.MULTICOLOR) Color.Gray else hexColor(ColorTables.family(f).hex)
    Box(modifier.size(size).clip(CircleShape).background(c))
}
