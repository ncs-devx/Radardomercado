package com.radardemercado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: RadarViewModel, onSimulator: () -> Unit, onAnalysis: () -> Unit) {
    val savedKey by vm.twelveDataKey.collectAsState()
    val newsEnabled by vm.newsEnabled.collectAsState()
    val alertSound by vm.alertSound.collectAsState()
    val alertVibration by vm.alertVibration.collectAsState()
    val lastBackgroundCheck by vm.lastBackgroundCheck.collectAsState()
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    val context = LocalContext.current
    var notificationMessage by remember { mutableStateOf<String?>(null) }
    var keyMessage by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mais", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), onClick = onAnalysis) { Row(Modifier.padding(18.dp)) { Icon(Icons.Default.Insights, null, tint = Green); Spacer(Modifier.width(14.dp)); Column { Text("Análise de mercado", fontWeight = FontWeight.Bold); Text("Sentimento, ciclo e Altcoin Season", color = Muted) } } }
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), onClick = onSimulator) { Row(Modifier.padding(18.dp)) { Icon(Icons.Default.Science, null, tint = Green); Spacer(Modifier.width(14.dp)); Column { Text("Simulador de preços", fontWeight = FontWeight.Bold); Text("Dispare alertas sem esperar o mercado", color = Muted) } } }
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Notícias", fontWeight = FontWeight.Bold); Text("Exibir banners na tela inicial", color = Muted) }; Switch(newsEnabled, vm::setNewsEnabled) } }
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Notificações de alertas", fontWeight = FontWeight.Bold)
                Text(if (vm.notificationsEnabled()) "Permissão habilitada" else "Permissão desabilitada", color = if (vm.notificationsEnabled()) Green else Red)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Som"); Switch(alertSound, vm::setAlertSound) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Vibração"); Switch(alertVibration, vm::setAlertVibration) }
                Text(
                    if (lastBackgroundCheck > 0) "Última verificação em segundo plano: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(lastBackgroundCheck))}" else "Aguardando a primeira verificação em segundo plano",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ notificationMessage = if (vm.testNotification()) "Notificação de teste enviada" else "Ative as notificações nas configurações" }) { Text("Testar") }
                    OutlinedButton({
                        if (!context.openSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)))
                            notificationMessage = "Não foi possível abrir as configurações"
                    }) { Text("Configurar") }
                }
                TextButton({
                    if (!context.openSafely(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
                        notificationMessage = "Configuração de bateria indisponível neste aparelho"
                }) { Text("Configurar economia de bateria") }
                notificationMessage?.let { Text(it, color = Muted) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Mercados globais", fontWeight = FontWeight.Bold); Text("Chave Twelve Data — criptografada neste aparelho", color = Muted); OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true); Button({ keyMessage = if (vm.saveTwelveDataKey(key)) "Chave salva com segurança" else "Não foi possível proteger a chave neste aparelho" }, enabled = key.isNotBlank()) { Text("Salvar chave") }; keyMessage?.let { Text(it, color = Muted) } } }
        Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Info, null, tint = Blue); Text("Sobre os alertas", fontWeight = FontWeight.Bold); Text("O Android executa verificações periódicas em segundo plano, mas pode adiá-las quando o app está fechado ou sob economia de bateria. Alertas instantâneos 24h exigirão servidor e push em uma versão futura.", color = Muted); HorizontalDivider(); Text("Somente monitoramento", fontWeight = FontWeight.Bold); Text("Este app usa dados públicos da Binance. Não solicita credenciais e não compra nem vende ativos.", color = Muted) } }
        Text("Radar de Mercado • versão 1.4.2", color = Muted)
        Spacer(Modifier.height(20.dp))
    }
}
