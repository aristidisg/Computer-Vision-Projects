package io.github.aristidisg.ensemble.ui.you

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.ui.common.NavRow
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import io.github.aristidisg.ensemble.ui.nav.AboutRoute
import io.github.aristidisg.ensemble.ui.nav.ComposerRoute
import io.github.aristidisg.ensemble.ui.nav.CustomRuleRoute
import io.github.aristidisg.ensemble.ui.nav.DataRoute
import io.github.aristidisg.ensemble.ui.nav.InsightsRoute
import io.github.aristidisg.ensemble.ui.nav.ShoppingListRoute
import io.github.aristidisg.ensemble.ui.nav.LocationRoute
import io.github.aristidisg.ensemble.ui.nav.NotificationsRoute
import io.github.aristidisg.ensemble.ui.nav.OnboardingRoute
import io.github.aristidisg.ensemble.ui.nav.PersonalColorRoute
import io.github.aristidisg.ensemble.ui.nav.RotationRoute
import io.github.aristidisg.ensemble.ui.nav.RulesRoute
import io.github.aristidisg.ensemble.ui.nav.ScenariosRoute
import io.github.aristidisg.ensemble.ui.nav.StyleProfileRoute
import io.github.aristidisg.ensemble.ui.nav.TasteRoute

@Composable
fun YouScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.tab_you), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 12.dp))
        NavRow(stringResource(R.string.style_profile), "${stringResource(R.string.boldness)} ${(s.boldness * 100).toInt()}% · ${s.strictness.label}") { nav.navigate(StyleProfileRoute) }
        NavRow(stringResource(R.string.taste_profile)) { nav.navigate(TasteRoute) }
        NavRow(stringResource(R.string.rules_weights)) { nav.navigate(RulesRoute) }
        NavRow(stringResource(R.string.custom_rules)) { nav.navigate(CustomRuleRoute()) }
        NavRow(stringResource(R.string.scenarios)) { nav.navigate(ScenariosRoute) }
        NavRow(stringResource(R.string.personal_color), if (s.personalColor.enabled) stringResource(R.string.on) else stringResource(R.string.off)) { nav.navigate(PersonalColorRoute) }
        NavRow(stringResource(R.string.insights)) { nav.navigate(InsightsRoute) }
        val toBuy = s.shoppingList.count { !it.done }
        NavRow(stringResource(R.string.shopping_list), if (toBuy > 0) stringResource(R.string.to_buy_n, toBuy) else null) { nav.navigate(ShoppingListRoute) }
        NavRow(stringResource(R.string.rotation)) { nav.navigate(RotationRoute) }
        NavRow(stringResource(R.string.location_weather), s.savedCity?.name) { nav.navigate(LocationRoute) }
        NavRow(stringResource(R.string.notifications)) { nav.navigate(NotificationsRoute) }
        NavRow(stringResource(R.string.data)) { nav.navigate(DataRoute) }
        NavRow(stringResource(R.string.about)) { nav.navigate(AboutRoute) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(R.string.debug_tools))
        NavRow(stringResource(R.string.rate_my_outfit)) { nav.navigate(ComposerRoute(rate = true)) }
        NavRow("Onboarding") { nav.navigate(OnboardingRoute) }
    }
}
