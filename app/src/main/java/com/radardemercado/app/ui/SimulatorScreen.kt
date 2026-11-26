package com.radardemercado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.radardemercado.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorScreen(vm: RadarViewModel, onBack: () -> Unit) {
    var asset by remember { mutableStateOf(Asset.BTC) }; val quote = QuoteCurrency.USDT; var price by remember { mutableStateOf("") }; var message by remember { mutableStateOf<String?>(null) }
    val value = price.replace(',', '.').toDoubleOrNull()
    Scaffold(containerColor = Background, topBar = { TopAppBar(title = { Text("Simulador") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Teste seus alertas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Informe um preço fictício. Alertas compatíveis serão disparados uma única vez e marcados como simulação.", color = Muted)
            ChoiceSectionLocal("Ativo", Asset.DEFAULTS, asset, { asset = it })
            Text("Par: ${asset.symbol}/USDT", color = Muted)
            OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Preço simulado") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Button(enabled = value != null && value > 0, onClick = { vm.simulate(asset, quote, value!!) { message = if (it == 0) "Nenhum alerta ativo foi atingido." else "$it alerta(s) disparado(s) no teste." } }, modifier = Modifier.fillMaxWidth()) { Text("Executar simulação") }
            message?.let { Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised)) { Text(it, Modifier.padding(16.dp), color = Green) } }
        }
    }
}

@Composable private fun <T> ChoiceSectionLocal(title: String, values: List<T>, selected: T, onSelect: (T) -> Unit) {
    Column { Text(title, fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { FilterChip(selected == it, { onSelect(it) }, { Text(it.toString()) }) } } }
}
