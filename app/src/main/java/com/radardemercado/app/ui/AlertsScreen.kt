package com.radardemercado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radardemercado.app.data.*

@Composable
fun AlertsScreen(vm: RadarViewModel, onEdit: (PriceAlert) -> Unit, onNew: () -> Unit) {
    val alerts by vm.alerts.collectAsState()
    val visibleAlerts = alerts.filter { it.quote == QuoteCurrency.USDT }
    Scaffold(
        containerColor = Background,
        floatingActionButton = { FloatingActionButton(onNew) { Icon(Icons.Default.Add, "Novo alerta") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp)); Text("Alertas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Seus gatilhos de preço", color = Muted); Spacer(Modifier.height(18.dp))
            if (visibleAlerts.isEmpty()) EmptyState("Nenhum alerta criado", "Crie um alvo e o Radar acompanha para você.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(visibleAlerts, key = { it.id }) { alert -> AlertCard(alert, { vm.toggle(alert) }, { onEdit(alert) }, { vm.delete(alert) }) }
            }
        }
    }
}

@Composable private fun AlertCard(alert: PriceAlert, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${alert.asset.symbol}/${alert.quote.name}", fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            alert.triggered -> "Disparado"
                            alert.enabled && alert.waitingForReset -> "Aguardando novo cruzamento"
                            alert.enabled -> "Ativo"
                            else -> "Desativado"
                        },
                        color = when { alert.triggered || alert.waitingForReset -> Blue; alert.enabled -> Green; else -> Muted }
                    )
                }
                Switch(checked = alert.enabled && !alert.triggered, onCheckedChange = { onToggle() })
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Editar") }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem({ Text("Excluir") }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = SurfaceRaised)
            Text(if (alert.direction == AlertDirection.ABOVE) "Avisar quando subir acima de" else "Avisar quando cair abaixo de", color = Muted)
            Text(formatPrice(alert.targetPrice, alert.quote), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.NotificationsNone, null, tint = Muted, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold); Text(detail, color = Muted)
    }
}
