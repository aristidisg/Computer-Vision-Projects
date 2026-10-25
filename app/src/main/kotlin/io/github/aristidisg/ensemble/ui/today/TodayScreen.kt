@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.aristidisg.ensemble.ui.today

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.rules.ScoredOutfit
import io.github.aristidisg.ensemble.model.WhyNot
import io.github.aristidisg.ensemble.ui.common.EmptyState
import io.github.aristidisg.ensemble.ui.nav.ItemDetailRoute
import io.github.aristidisg.ensemble.ui.nav.OutfitDetailRoute
import io.github.aristidisg.ensemble.ui.nav.PiecesArg
import io.github.aristidisg.ensemble.ui.nav.WardrobeRoute
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(nav: NavHostController, vm: TodayViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var datePicker by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var dayMenu by remember { mutableStateOf(false) }
    var shoot by remember { mutableStateOf<ScoredOutfit?>(null) }
    val wearMsg = stringResource(R.string.logged_wear)
    LaunchedEffect(s.message) { if (s.message == "wear") { snack.showSnackbar(wearMsg); vm.consumeMessage() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Compact header (date + weather pill, then one row of chips) so the card gets the room.
            val date = LocalDate.ofEpochDay(s.epochDay)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    Column(Modifier.clickable { dayMenu = true }) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)), style = MaterialTheme.typography.displaySmall, maxLines = 1)
                        val sub = listOfNotNull(date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)), weatherSubtitle(s)).joinToString("  ·  ")
                        Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.today)) }, onClick = { dayMenu = false; vm.selectDay(s.today) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.tomorrow)) }, onClick = { dayMenu = false; vm.selectDay(s.today + 1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.pick_date)) }, onClick = { dayMenu = false; datePicker = true })
                    }
                }
                WeatherPill(s, onRefresh = vm::refreshWeather, onManual = { manual = true }, modifier = Modifier.padding(top = 6.dp))
            }
            TodayChips(s, vm, Modifier.padding(top = 6.dp))
            if (s.locked.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.locked_pieces, s.locked.mapNotNull { s.items[it.itemId]?.name }.joinToString()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::clearLocks) { Text(stringResource(R.string.clear_locks)) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                when {
                    !s.minimum.ok -> MinimumSetState(s.minimum, onAdd = { nav.navigate(WardrobeRoute) }, onSeed = vm::seedDemo)
                    s.loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.generating))
                    }
                    s.cards.isEmpty() -> EndOfDeck(vm)
                    else -> SwipeDeck(s.cards, onSwipe = vm::swipe, modifier = Modifier.fillMaxSize()) { card ->
                        val sc = s.scenario
                        OutfitCard(
                            card, s.items, cover = s.vogue, issue = s.issue, onShoot = if (s.vogue) { { shoot = card } } else null,
                            onOpen = { if (sc != null) nav.navigate(OutfitDetailRoute(PiecesArg.encode(card.pieces), sc.id, s.epochDay)) },
                            actions = PieceActions(
                                like = { vm.pieceThumb(card, it, true) },
                                dislike = { vm.pieceThumb(card, it, false) },
                                swap = { if (sc != null) nav.navigate(OutfitDetailRoute(PiecesArg.encode(card.pieces), sc.id, s.epochDay)) },
                                lock = vm::lock,
                                open = { nav.navigate(ItemDetailRoute(it)) },
                            ),
                        )
                    }
                }
            }
            if (s.minimum.ok && s.cards.isNotEmpty() && !s.loading) Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::undo, enabled = s.undoCount > 0) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }
                FilledTonalIconButton(onClick = { vm.swipe(s.cards.first(), Swipe.NOPE) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.action_nope)) }
                FilledIconButton(onClick = { vm.swipe(s.cards.first(), Swipe.LIKE) }, modifier = Modifier.size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = LocalEnsembleColors.current.accent, contentColor = LocalEnsembleColors.current.onAccent)) { Icon(Icons.Filled.Favorite, stringResource(R.string.action_like)) }
                FilledTonalIconButton(onClick = { vm.swipe(s.cards.first(), Swipe.WEAR) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.action_wear)) }
                Spacer(Modifier.size(48.dp))
            }
        }
        WhyNotBar(s.whyNotFor?.signature, onPick = { vm.whyNot(it) }, onSkip = vm::dismissWhyNot, Modifier.align(Alignment.BottomCenter))
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }

    if (datePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = s.epochDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { vm.selectDay(it / 86_400_000L) }; datePicker = false }) { Text(stringResource(R.string.done)) } },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
    shoot?.let { ShootDialog(it, s.items, s.issue, s.epochDay) { shoot = null } }
    if (manual) ManualWeatherDialog(onDismiss = { manual = false }, onClear = { vm.clearManualWeather(); manual = false }) { a, b -> vm.setManualWeather(a, b); manual = false }
}

@Composable
private fun MinimumSetState(m: MinimumSet, onAdd: () -> Unit, onSeed: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { m.progress }, modifier = Modifier.size(96.dp), strokeWidth = 6.dp)
            Text("${(m.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.empty_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.empty_progress, m.tops, m.bottoms, m.shoes), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) { Text(stringResource(R.string.add_pieces)) }
        TextButton(onClick = onSeed) { Text(stringResource(R.string.seed_demo)) }
    }
}

@Composable
private fun EndOfDeck(vm: TodayViewModel) {
    EmptyState(stringResource(R.string.deck_end)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::more) { Text(stringResource(R.string.deck_more)) }
            OutlinedButton(onClick = vm::bolder) { Text(stringResource(R.string.deck_bolder)) }
            OutlinedButton(onClick = vm::safer) { Text(stringResource(R.string.deck_safer)) }
        }
    }
}

/** "Why not?" chips slide up after a No; skippable. */
@Composable
private fun WhyNotBar(card: String?, onPick: (WhyNot) -> Unit, onSkip: () -> Unit, modifier: Modifier) {
    // No timer: the reasons stay until you pick one, tap Skip or swipe the next card.
    AnimatedVisibility(card != null, modifier, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        androidx.compose.material3.Surface(tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.why_not), style = MaterialTheme.typography.titleSmall)
                // All reasons visible at once (wrapping), none hidden off-screen.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WhyNot.entries.filter { it != WhyNot.NOT_THIS_PIECE }.forEach { r ->
                        AssistChip(onClick = { onPick(r) }, label = { Text(r.label) })
                    }
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
                }
            }
        }
    }
}

@Composable
private fun ManualWeatherDialog(onDismiss: () -> Unit, onClear: () -> Unit, onSet: (Double, Double) -> Unit) {
    var min by remember { mutableStateOf("12") }
    var max by remember { mutableStateOf("18") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_temp_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(min, { min = it }, label = { Text(stringResource(R.string.min_c)) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(max, { max = it }, label = { Text(stringResource(R.string.max_c)) }, modifier = Modifier.weight(1f), singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = min.toDoubleOrNull(); val b = max.toDoubleOrNull()
                if (a != null && b != null) onSet(minOf(a, b), maxOf(a, b))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.restore_default)) } },
    )
}
