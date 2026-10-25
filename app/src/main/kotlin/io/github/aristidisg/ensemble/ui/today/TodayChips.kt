package io.github.aristidisg.ensemble.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.color.ColorTables
import io.github.aristidisg.ensemble.model.ColorFamily
import io.github.aristidisg.ensemble.model.Mood
import io.github.aristidisg.ensemble.ui.common.hexColor
import io.github.aristidisg.ensemble.ui.common.issueName
import io.github.aristidisg.ensemble.ui.common.moodName
import io.github.aristidisg.ensemble.ui.theme.LocalEnsembleColors

/** Today's one row of choices: Vogue stylist, occasion, mood, accent colour and the switches. */
@Composable
fun TodayChips(s: TodayState, vm: TodayViewModel, modifier: Modifier = Modifier) {
    var moodMenu by remember { mutableStateOf(false) }
    var accentMenu by remember { mutableStateOf(false) }
    val accent = LocalEnsembleColors.current.accent
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (s.vogue) Button(
            onClick = { vm.setVogue(false) },
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = LocalEnsembleColors.current.onAccent),
        ) { Text(s.issue?.let { stringResource(R.string.vogue_issue, issueName(it)) } ?: stringResource(R.string.vogue_on)) }
        else OutlinedButton(onClick = { vm.setVogue(true) }) { Text(stringResource(R.string.vogue_stylist), color = accent) }
        s.scenarios.forEach { sc ->
            FilterChip(selected = sc.id == s.scenario?.id, onClick = { vm.selectScenario(sc.id) }, label = { Text(sc.name) })
        }
        VerticalDivider(Modifier.height(24.dp))
        Box {
            FilterChip(selected = s.mood != null, onClick = { moodMenu = true }, label = { Text(s.mood?.let { moodName(it) } ?: stringResource(R.string.mood)) })
            DropdownMenu(expanded = moodMenu, onDismissRequest = { moodMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.mood_none)) }, onClick = { moodMenu = false; vm.setMood(null) })
                Mood.entries.forEach { m -> DropdownMenuItem(text = { Text(moodName(m)) }, onClick = { moodMenu = false; vm.setMood(m) }) }
            }
        }
        Box {
            val chosen = s.accent
            FilterChip(
                selected = chosen != null, onClick = { accentMenu = true },
                leadingIcon = if (chosen != null) { { Swatch(chosen) } } else null,
                label = { Text(s.accent?.let { stringResource(R.string.accent_of, it.label.lowercase()) } ?: stringResource(R.string.accent)) },
            )
            DropdownMenu(expanded = accentMenu, onDismissRequest = { accentMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.accent_none)) }, onClick = { accentMenu = false; vm.setAccent(null) })
                s.accentChoices.forEach { f ->
                    DropdownMenuItem(text = { Text(f.label) }, leadingIcon = { Swatch(f) }, onClick = { accentMenu = false; vm.setAccent(f) })
                }
            }
        }
        FilterChip(selected = s.freshPairs, onClick = { vm.setFreshPairs(!s.freshPairs) }, label = { Text(stringResource(R.string.fresh_pairs)) })
        FilterChip(selected = s.experiment, onClick = { vm.setExperiment(!s.experiment) }, label = { Text(stringResource(R.string.experiment)) })
        FilterChip(selected = s.noMid, onClick = { vm.setNoMid(!s.noMid) }, label = { Text(stringResource(R.string.no_mid_layers)) })
        FilterChip(selected = s.noOuter, onClick = { vm.setNoOuter(!s.noOuter) }, label = { Text(stringResource(R.string.no_outerwear)) })
    }
}

@Composable
private fun Swatch(f: ColorFamily) {
    val c = if (f == ColorFamily.MULTICOLOR) Color.Gray else hexColor(ColorTables.family(f).hex)
    Box(Modifier.padding(2.dp).size(16.dp).clip(CircleShape).background(c).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}
