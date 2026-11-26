package com.radardemercado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.radardemercado.app.data.Asset
import com.radardemercado.app.data.QuoteCurrency
import android.net.Uri

sealed class Destination(val route: String, val label: String) {
    data object Home : Destination("home", "Mercado")
    data object Alerts : Destination("alerts", "Alertas")
    data object History : Destination("history", "Histórico")
    data object Settings : Destination("settings", "Mais")
}

@Composable
fun RadarApp(vm: RadarViewModel) = RadarTheme {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val mainDestinations = listOf(Destination.Home, Destination.Alerts, Destination.History, Destination.Settings)
    val showBottom = mainDestinations.any { it.route == route }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottom) NavigationBar(containerColor = Surface) {
                mainDestinations.forEach { destination ->
                    val icon = when (destination) {
                        Destination.Home -> Icons.Default.ShowChart
                        Destination.Alerts -> Icons.Default.Notifications
                        Destination.History -> Icons.Default.History
                        Destination.Settings -> Icons.Default.MoreHoriz
                    }
                    NavigationBarItem(
                        selected = route == destination.route,
                        onClick = { nav.navigate(destination.route) { popUpTo(Destination.Home.route); launchSingleTop = true } },
                        icon = { Icon(icon, null) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, Destination.Home.route, Modifier.padding(padding)) {
            composable(Destination.Home.route) { DashboardScreen(vm, onNewAlert = { nav.navigate("edit/0") }, onSimulator = { nav.navigate("simulator") }, onAsset = { asset, quote -> nav.navigate("chart/${asset.symbol}/${quote.name}") }, onSearch = { nav.navigate("search") }) }
            composable(Destination.Alerts.route) { AlertsScreen(vm, onEdit = { nav.navigate("edit/${it.id}") }, onNew = { nav.navigate("edit/0") }) }
            composable(Destination.History.route) { HistoryScreen(vm) }
            composable(Destination.Settings.route) { SettingsScreen(vm, onSimulator = { nav.navigate("simulator") }, onAnalysis = { nav.navigate("analysis") }) }
            composable(
                "edit/{id}?asset={asset}&quote={quote}&target={target}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("asset") { type = NavType.StringType; defaultValue = "BTC" },
                    navArgument("quote") { type = NavType.StringType; defaultValue = "USDT" },
                    navArgument("target") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                AlertEditorScreen(
                    vm, it.arguments?.getLong("id") ?: 0,
                    onBack = { nav.popBackStack() },
                    initialAsset = Asset(it.arguments?.getString("asset") ?: "BTC"),
                    initialQuote = QuoteCurrency.USDT,
                    initialTarget = it.arguments?.getString("target").orEmpty()
                )
            }
            composable("simulator") { SimulatorScreen(vm, onBack = { nav.popBackStack() }) }
            composable("analysis") { AnalysisScreen(vm, onBack = { nav.popBackStack() }) }
            composable("global-chart?symbol={symbol}&name={name}", arguments = listOf(navArgument("symbol") { defaultValue = "" }, navArgument("name") { defaultValue = "" })) { entry -> GlobalChartScreen(vm, Uri.decode(entry.arguments?.getString("symbol").orEmpty()), Uri.decode(entry.arguments?.getString("name").orEmpty()), { nav.popBackStack() }) }
            composable("chart/{asset}/{quote}") { entry ->
                ChartScreen(
                    vm = vm,
                    asset = Asset(entry.arguments?.getString("asset") ?: "BTC"),
                    quote = runCatching { QuoteCurrency.valueOf(entry.arguments?.getString("quote") ?: "USDT") }.getOrDefault(QuoteCurrency.USDT),
                    onBack = { nav.popBackStack() },
                    onAlert = { nav.navigate("edit/0?asset=${entry.arguments?.getString("asset")}&quote=USDT") },
                    onAlertAtPrice = { price -> nav.navigate("edit/0?asset=${entry.arguments?.getString("asset")}&quote=USDT&target=$price") },
                    onEditAlert = { id -> nav.navigate("edit/$id") }
                )
            }
            composable("search") {
                SearchAssetsScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpen = { asset, quote -> nav.navigate("chart/${asset.symbol}/${quote.name}") },
                    onOpenGlobal = { item -> nav.navigate("global-chart?symbol=${Uri.encode(item.symbol)}&name=${Uri.encode(item.instrument_name)}") }
                )
            }
        }
    }
}
