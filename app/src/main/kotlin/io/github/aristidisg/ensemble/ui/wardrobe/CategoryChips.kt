package io.github.aristidisg.ensemble.ui.wardrobe

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.engine.tables.TaxonomyTable
import io.github.aristidisg.ensemble.model.CategoryGroup
import io.github.aristidisg.ensemble.model.Slot
import io.github.aristidisg.ensemble.model.Subcategory
import io.github.aristidisg.ensemble.ui.common.ChoiceChips

/** True for pieces worn over a top (sweaters, cardigans, hoodies, overshirts...). */
fun Subcategory.isMidLayer(): Boolean = TaxonomyTable.of(this).slots.first() == Slot.MID

/**
 * Subcategories of a group. Tops are split into "Tops" and "Mid layers" so a sweater or an
 * overshirt isn't filed as a T-shirt, and outerwear is labeled as worn over everything.
 */
@Composable
fun SubcategoryChips(group: CategoryGroup, selected: Subcategory?, onPick: (Subcategory) -> Unit) {
    val subs = Subcategory.entries.filter { it.group == group }
    if (group == CategoryGroup.TOPS) {
        Label(stringResource(R.string.cat_tops))
        ChoiceChips(subs.filter { !it.isMidLayer() }, setOfNotNull(selected), { it.label }, onPick)
        Label(stringResource(R.string.cat_mid_layers))
        ChoiceChips(subs.filter { it.isMidLayer() }, setOfNotNull(selected), { it.label }, onPick)
    } else {
        if (group == CategoryGroup.OUTERWEAR) Label(stringResource(R.string.cat_outerwear))
        ChoiceChips(subs, setOfNotNull(selected), { it.label }, onPick)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}
