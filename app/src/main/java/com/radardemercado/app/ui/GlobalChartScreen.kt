package com.radardemercado.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radardemercado.app.data.Candle

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlobalChartScreen(vm: RadarViewModel, symbol: String, name: String, onBack: () -> Unit) {
    val intervals = listOf("1min", "5min", "15min", "1h", "4h", "1day")
    var interval by remember { mutableStateOf("15min") }; var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }; var loading by remember { mutableStateOf(true) }
    LaunchedEffect(symbol, interval) { loading = true; error = null; vm.loadGlobalCandles(symbol, interval) { it.onSuccess { candles = it }.onFailure { e -> error = e.message ?: "Não foi possível carregar" }; loading = false } }
    Scaffold(containerColor = Background, topBar = { TopAppBar(title = { Column { Text(symbol, fontWeight = FontWeight.Bold); Text(name, style = MaterialTheme.typography.labelSmall, color = Muted) } }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            candles.lastOrNull()?.let { Text("${"%,.2f".format(it.close)} USD", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            Text("Fonte: Twelve Data", color = Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { intervals.forEach { FilterChip(interval == it, { interval = it }, { Text(it) }) } }
            Spacer(Modifier.height(12.dp)); Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) { Box(Modifier.fillMaxWidth().height(420.dp).padding(12.dp)) { when { loading -> CircularProgressIndicator(); error != null -> Text(error!!, color = Red); else -> CandleChart(candles.takeLast(60), emptyList(), Modifier.fillMaxSize()) {} } } }
        }
    }
}
