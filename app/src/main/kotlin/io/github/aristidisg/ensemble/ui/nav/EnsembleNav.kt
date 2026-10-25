package io.github.aristidisg.ensemble.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.aristidisg.ensemble.LaunchRequest
import io.github.aristidisg.ensemble.MainActivity
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.ui.onboarding.OnboardingScreen
import io.github.aristidisg.ensemble.ui.outfits.ComposerScreen
import io.github.aristidisg.ensemble.ui.outfits.LookEditorScreen
import io.github.aristidisg.ensemble.ui.outfits.LookMatchScreen
import io.github.aristidisg.ensemble.ui.outfits.OutfitDetailScreen
import io.github.aristidisg.ensemble.ui.outfits.OutfitsScreen
import io.github.aristidisg.ensemble.ui.plan.LaundryScreen
import io.github.aristidisg.ensemble.ui.plan.PlanScreen
import io.github.aristidisg.ensemble.ui.plan.TripScreen
import io.github.aristidisg.ensemble.ui.today.CheckInScreen
import io.github.aristidisg.ensemble.ui.today.TodayScreen
import io.github.aristidisg.ensemble.ui.wardrobe.ItemDetailScreen
import io.github.aristidisg.ensemble.ui.wardrobe.ItemEditorScreen
import io.github.aristidisg.ensemble.ui.wardrobe.WardrobeScreen
import io.github.aristidisg.ensemble.ui.you.AboutScreen
import io.github.aristidisg.ensemble.ui.you.CustomRuleScreen
import io.github.aristidisg.ensemble.ui.you.DataScreen
import io.github.aristidisg.ensemble.ui.you.InsightsScreen
import io.github.aristidisg.ensemble.ui.you.LocationScreen
import io.github.aristidisg.ensemble.ui.you.NotificationsScreen
import io.github.aristidisg.ensemble.ui.you.PersonalColorScreen
import io.github.aristidisg.ensemble.ui.you.RotationScreen
import io.github.aristidisg.ensemble.ui.you.RulesScreen
import io.github.aristidisg.ensemble.ui.you.ScenarioEditorScreen
import io.github.aristidisg.ensemble.ui.you.ScenariosScreen
import io.github.aristidisg.ensemble.ui.you.StyleProfileScreen
import io.github.aristidisg.ensemble.ui.you.TasteScreen
import io.github.aristidisg.ensemble.ui.you.YouScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val cls: KClass<*>, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(TodayRoute, TodayRoute::class, R.string.tab_today, Icons.Outlined.WbSunny),
    Tab(WardrobeRoute, WardrobeRoute::class, R.string.tab_wardrobe, Icons.Outlined.Checkroom),
    Tab(OutfitsRoute, OutfitsRoute::class, R.string.tab_outfits, Icons.Outlined.Collections),
    Tab(PlanRoute, PlanRoute::class, R.string.tab_plan, Icons.Outlined.CalendarMonth),
    Tab(YouRoute, YouRoute::class, R.string.tab_you, Icons.Outlined.Person),
)

@Composable
fun EnsembleNav(launch: LaunchRequest?, onLaunchHandled: () -> Unit) {
    val app: AppViewModel = hiltViewModel()
    val onboarded by app.onboarded.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = tabs.any { t -> dest?.hasRoute(t.cls) == true }

    if (onboarded == null) return
    val start: Any = androidx.compose.runtime.remember { if (onboarded == true) TodayRoute else OnboardingRoute }
    if (launch is LaunchRequest.Shared) SharedDialog(launch, nav, onLaunchHandled)
    LaunchedEffect(launch) {
        if (launch is LaunchRequest.Open) {
            when (launch.target) {
                MainActivity.OPEN_CHECKIN -> nav.navigate(CheckInRoute(launch.arg))
                else -> nav.navigate(TodayRoute) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
            }
            onLaunchHandled()
        }
    }

    Scaffold(bottomBar = {
        if (showBar) NavigationBar {
            tabs.forEach { t ->
                NavigationBarItem(
                    selected = dest?.hasRoute(t.cls) == true,
                    onClick = { nav.navigate(t.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                    icon = { Icon(t.icon, null) },
                    label = { Text(stringResource(t.label)) },
                )
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(pad)) {
            composable<OnboardingRoute> { OnboardingScreen(onDone = { nav.navigate(TodayRoute) { popUpTo(0) } }, onAddPieces = { nav.navigate(TodayRoute) { popUpTo(0) }; nav.navigate(WardrobeRoute) }) }
            composable<TodayRoute> { TodayScreen(nav) }
            composable<WardrobeRoute> { WardrobeScreen(nav) }
            composable<OutfitsRoute> { OutfitsScreen(nav) }
            composable<PlanRoute> { PlanScreen(nav) }
            composable<YouRoute> { YouScreen(nav) }
            composable<ItemEditorRoute> { ItemEditorScreen(it.toRoute(), nav) }
            composable<ItemDetailRoute> { ItemDetailScreen(it.toRoute<ItemDetailRoute>().id, nav) }
            composable<OutfitDetailRoute> { OutfitDetailScreen(it.toRoute(), nav) }
            composable<ComposerRoute> { ComposerScreen(it.toRoute(), nav) }
            composable<LookEditorRoute> { LookEditorScreen(it.toRoute(), nav) }
            composable<LookMatchRoute> { LookMatchScreen(it.toRoute<LookMatchRoute>().id, nav) }
            composable<TripRoute> { TripScreen(it.toRoute<TripRoute>().id, nav) }
            composable<LaundryRoute> { LaundryScreen(nav) }
            composable<CheckInRoute> { CheckInScreen(it.toRoute<CheckInRoute>().logId, nav) }
            composable<StyleProfileRoute> { StyleProfileScreen(nav) }
            composable<TasteRoute> { TasteScreen(nav) }
            composable<RulesRoute> { RulesScreen(nav) }
            composable<CustomRuleRoute> { CustomRuleScreen(it.toRoute<CustomRuleRoute>().id, nav) }
            composable<ScenariosRoute> { ScenariosScreen(nav) }
            composable<ScenarioEditorRoute> { ScenarioEditorScreen(it.toRoute<ScenarioEditorRoute>().id, nav) }
            composable<PersonalColorRoute> { PersonalColorScreen(nav) }
            composable<InsightsRoute> { InsightsScreen(nav) }
            composable<RotationRoute> { RotationScreen(nav) }
            composable<LocationRoute> { LocationScreen(nav) }
            composable<NotificationsRoute> { NotificationsScreen(nav) }
            composable<DataRoute> { DataScreen(nav) }
            composable<AboutRoute> { AboutScreen(nav) }
        }
    }
}

@Composable
private fun SharedDialog(req: LaunchRequest.Shared, nav: NavHostController, done: () -> Unit) {
    AlertDialog(
        onDismissRequest = done,
        title = { Text(stringResource(R.string.share_add_piece)) },
        confirmButton = {
            TextButton(onClick = { nav.navigate(ItemEditorRoute(uris = req.uris.joinToString("|"))); done() }) { Text(stringResource(R.string.new_piece)) }
        },
        dismissButton = {
            TextButton(onClick = { nav.navigate(LookEditorRoute(uri = req.uris.first().toString())); done() }) { Text(stringResource(R.string.add_look)) }
        },
    )
}
