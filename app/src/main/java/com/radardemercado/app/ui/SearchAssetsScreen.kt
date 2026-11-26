package com.radardemercado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radardemercado.app.data.Asset
import com.radardemercado.app.data.QuoteCurrency
import com.radardemercado.app.data.GlobalInstrument
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAssetsScreen(
    vm: RadarViewModel,
    onBack: () -> Unit,
    onOpen: (Asset, QuoteCurrency) -> Unit,
    onOpenGlobal: (GlobalInstrument) -> Unit
) {
    val favorites by vm.favorites.collectAsState()
    var query by remember { mutableStateOf("") }
    val quote = QuoteCurrency.USDT
    var assets by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var global by remember { mutableStateOf<List<GlobalInstrument>>(emptyList()) }
    val globalKey by vm.twelveDataKey.collectAsState()

    LaunchedEffect(Unit) {
        loading = true; error = null
        vm.loadSearchAssets(quote) { result ->
            result.onSuccess { assets = it }.onFailure { error = "Não foi possível carregar os ativos" }
            loading = false
        }
    }
    LaunchedEffect(query, globalKey) {
        global = emptyList()
        if (query.trim().length >= 2 && globalKey.isNotBlank()) { delay(500); vm.searchGlobal(query.trim()) { it.onSuccess { values -> global = values } } }
    }
    val filtered = remember(assets, query) {
        if (query.isBlank()) assets.take(80) else assets.filter { it.symbol.contains(query.trim(), ignoreCase = true) }.take(80)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Pesquisar ativos", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("BTC, XAU/USD, EUR/GBP…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                error != null -> Text(error!!, color = Red)
                filtered.isEmpty() -> EmptyState("Nenhum ativo encontrado", "Tente outro símbolo ou par.")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (globalKey.isBlank() && query.isNotBlank()) item { Text("Configure a chave Twelve Data em Mais para buscar forex, metais, índices e ações.", color = Muted, modifier = Modifier.padding(12.dp)) }
                    items(global, key = { "${it.symbol}-${it.exchange}" }) { item ->
                        ListItem(headlineContent = { Text(item.symbol, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${item.instrument_name} • ${item.exchange} • Twelve Data") }, modifier = Modifier.fillMaxWidth().clickable { onOpenGlobal(item) })
                        HorizontalDivider(color = SurfaceRaised)
                    }
                    items(filtered, key = { it.symbol }) { asset ->
                        ListItem(
                            headlineContent = { Text(asset.symbol, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("${asset.symbol}/${quote.name} • Binance Spot") },
                            trailingContent = {
                                IconButton({ vm.toggleFavorite(asset) }) {
                                    Icon(
                                        if (favorites.contains(asset)) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        if (favorites.contains(asset)) "Remover dos favoritos" else "Adicionar aos favoritos",
                                        tint = if (favorites.contains(asset)) Green else Muted
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(asset, quote) }
                        )
                        HorizontalDivider(color = SurfaceRaised)
                    }
                }
            }
        }
    }
}
