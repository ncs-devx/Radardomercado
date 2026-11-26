package com.radardemercado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radardemercado.app.data.QuoteCurrency

@Composable
fun HistoryScreen(vm: RadarViewModel) {
    val history by vm.history.collectAsState()
    val visibleHistory = history.filter { it.quote == QuoteCurrency.USDT }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp)); Text("Histórico", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Alertas que já foram disparados", color = Muted); Spacer(Modifier.height(18.dp))
        if (visibleHistory.isEmpty()) EmptyState("Histórico vazio", "Os disparos aparecerão aqui.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(visibleHistory, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("${item.asset.symbol}/${item.quote.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (item.simulated) SuggestionChip({}, { Text("SIMULAÇÃO") }) }
                        Text("Preço observado: ${formatPrice(item.observedPrice, item.quote)}", color = Green)
                        Text("Alvo: ${formatPrice(item.targetPrice, item.quote)}", color = Muted)
                        Text(formatTime(item.triggeredAt), color = Muted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.reactivate(item) }, contentPadding = PaddingValues(0.dp)) { Text("Reativar alerta") }
                    }
                }
            }
        }
    }
}
