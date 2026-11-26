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
fun AlertEditorScreen(vm: RadarViewModel, id: Long, onBack: () -> Unit, initialAsset: Asset = Asset.BTC, initialQuote: QuoteCurrency = QuoteCurrency.USDT, initialTarget: String = "") {
    val alerts by vm.alerts.collectAsState()
    val existing = alerts.firstOrNull { it.id == id }
    var asset by remember(existing) { mutableStateOf(existing?.asset ?: initialAsset) }
    val quote = QuoteCurrency.USDT
    var direction by remember(existing) { mutableStateOf(existing?.direction ?: AlertDirection.ABOVE) }
    var target by remember(existing, initialTarget) { mutableStateOf(existing?.targetPrice?.toString() ?: initialTarget) }
    var enabled by remember(existing) { mutableStateOf(existing?.enabled ?: true) }
    val value = target.replace(',', '.').toDoubleOrNull()

    Scaffold(
        containerColor = Background,
        topBar = { TopAppBar(title = { Text(if (id == 0L) "Novo alerta" else "Editar alerta") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (existing == null) {
                Text("Ativo", fontWeight = FontWeight.Bold)
                AssistChip(onClick = {}, label = { Text(asset.symbol) })
            } else ChoiceSection("Ativo", listOf(asset), asset, { asset = it }, { it.symbol })
            Text("Par: ${asset.symbol}/USDT", color = Muted)
            Text("Condição", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(direction == AlertDirection.ABOVE, { direction = AlertDirection.ABOVE }, { Text("Subir acima") }, leadingIcon = { Text("↗") })
                FilterChip(direction == AlertDirection.BELOW, { direction = AlertDirection.BELOW }, { Text("Cair abaixo") }, leadingIcon = { Text("↘") })
            }
            OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("Preço-alvo em ${quote.name}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = target.isNotEmpty() && (value == null || value <= 0))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Alerta ativo", fontWeight = FontWeight.Bold); Text("Começar a monitorar ao salvar", color = Muted) }; Switch(enabled, { enabled = it }) }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = value != null && value > 0,
                onClick = { vm.save(PriceAlert(id = existing?.id ?: 0, asset = asset, quote = quote, direction = direction, targetPrice = value!!, enabled = enabled, triggered = false, waitingForReset = false, createdAt = existing?.createdAt ?: System.currentTimeMillis()), onBack) },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Salvar alerta") }
        }
    }
}

@Composable private fun <T> ChoiceSection(title: String, values: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { values.forEach { FilterChip(selected == it, { onSelect(it) }, { Text(label(it)) }) } }
    }
}
