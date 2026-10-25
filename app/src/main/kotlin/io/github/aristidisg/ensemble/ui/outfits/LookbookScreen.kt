package io.github.aristidisg.ensemble.ui.outfits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.repo.OutfitRepository
import io.github.aristidisg.ensemble.domain.Studio
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.Item
import io.github.aristidisg.ensemble.model.OutfitSource
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.Collage
import io.github.aristidisg.ensemble.ui.common.EmptyState
import io.github.aristidisg.ensemble.ui.common.hexColor
import io.github.aristidisg.ensemble.ui.common.techniqueBlurb
import io.github.aristidisg.ensemble.ui.common.techniqueName
import io.github.aristidisg.ensemble.ui.common.tipText
import io.github.aristidisg.ensemble.ui.nav.OutfitDetailRoute
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import io.github.aristidisg.ensemble.ui.theme.Fraunces
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One lookbook page: the outfit as the engine reads it today, and where to open it. */
data class LookPage(val name: String, val scored: ScoredOutfit, val scenarioId: String)

data class LookbookState(val pages: List<LookPage> = emptyList(), val items: Map<String, Item> = emptyMap(), val day: Long = 0, val loading: Boolean = true)

@HiltViewModel
class LookbookViewModel @Inject constructor(outfits: OutfitRepository, studio: Studio, time: TimeSource) : ViewModel() {
    private val _s = MutableStateFlow(LookbookState())
    val state = _s.asStateFlow()

    init {
        viewModelScope.launch {
            val day = time.today()
            val saved = outfits.all().filter { !it.neverShow && (it.liked || it.source == OutfitSource.COMPOSED) }.sortedByDescending { it.createdAtMillis }
            val fallback = studio.defaultScenarioId(day)
            // One context per occasion: the outfits are scored (technique, styling notes) as of today.
            val ctxs = saved.map { it.scenarioId ?: fallback }.distinct().associateWith { id -> studio.context(day, studio.scenario(id)) }
            val pages = saved.mapNotNull { o ->
                val id = o.scenarioId ?: fallback
                val ctx = ctxs[id] ?: return@mapNotNull null
                studio.engine.score(o.pieces, ctx)?.let { LookPage(o.name, it, id) }
            }
            _s.value = LookbookState(pages, ctxs.values.firstOrNull()?.items.orEmpty(), day, loading = false)
        }
    }
}

/** Saved and composed outfits as full-screen editorial pages, swiped like a magazine. */
@Composable
fun LookbookScreen(nav: NavHostController, vm: LookbookViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackBar(stringResource(R.string.lookbook), onBack = { nav.popBackStack() })
        if (!s.loading && s.pages.isEmpty()) { EmptyState(stringResource(R.string.lookbook_empty)); return@Column }
        val pager = rememberPagerState { s.pages.size }
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            val page = s.pages[i]
            LookPageView(page, s.items, i, s.pages.size) {
                nav.navigate(OutfitDetailRoute(PiecesArg.encode(page.scored.pieces), page.scenarioId, s.day))
            }
        }
    }
}

@Composable
private fun LookPageView(page: LookPage, items: Map<String, Item>, index: Int, count: Int, onOpen: () -> Unit) {
    val o = page.scored
    val placed = o.pieces.mapNotNull { p -> items[p.itemId]?.let { it to p.slot } }
    val palette = placed.mapNotNull { (it, _) -> it.colors.firstOrNull()?.let { c -> hexColor(c.hex) } }.distinct()
    val accent = LocalEnsembleColors.current.accent
    val wash = Brush.verticalGradient(listOf((palette.getOrNull(0) ?: accent).copy(alpha = 0.35f), (palette.getOrNull(1) ?: accent).copy(alpha = 0.12f), Color.Transparent))
    Column(Modifier.fillMaxSize().background(wash).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lookbook).uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp), color = accent, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.lookbook_count, index + 1, count), style = MaterialTheme.typography.labelSmall)
        }
        Text(
            techniqueName(o.technique), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.displayMedium.copy(fontFamily = Fraunces, lineHeight = 46.sp), modifier = Modifier.padding(top = 6.dp),
        )
        Text(techniqueBlurb(o.technique), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp)) { Collage(placed, Modifier.fillMaxSize()) }
        o.tips.forEach { t -> Text("— " + tipText(t), style = MaterialTheme.typography.titleSmall, color = accent) }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            palette.take(6).forEach { c -> Box(Modifier.size(18.dp).clip(CircleShape).background(c).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
        }
        Text(
            (if (page.name.isNotBlank()) page.name + "  ·  " else "") + placed.filter { it.second.isCore }.joinToString(" / ") { it.first.name },
            style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.match_pct, o.matchPct), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = onOpen) { Text(stringResource(R.string.piece_open)) }
        }
    }
}
