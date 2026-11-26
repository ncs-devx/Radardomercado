package com.radardemercado.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radardemercado.app.data.MarketCycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(vm: RadarViewModel, onBack: () -> Unit) {
    val state by vm.analysis.collectAsState()
    LaunchedEffect(Unit) { if (state.data == null) vm.loadAnalysis() }
    Scaffold(
        containerColor = Background,
        topBar = { TopAppBar(
            title = { Text("Análise de mercado", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = { IconButton(vm::loadAnalysis) { Icon(Icons.Default.Refresh, "Atualizar") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
        ) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                state.loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Calculando tendências e desempenho de 90 dias…", color = Muted) }
                state.error != null -> { Text(state.error!!, color = Red); Button(vm::loadAnalysis) { Text("Tentar novamente") } }
                state.data != null -> {
                    val data = state.data!!
                    AnalysisCard("Sentimento consolidado", "${data.sentimentScore}/100", data.sentimentLabel, when { data.sentimentScore >= 60 -> Green; data.sentimentScore <= 40 -> Red; else -> Blue }) {
                        LinearProgressIndicator(progress = { data.sentimentScore / 100f }, modifier = Modifier.fillMaxWidth(), color = if (data.sentimentScore >= 60) Green else if (data.sentimentScore <= 40) Red else Blue)
                        SourceValue("CoinMarketCap", data.coinMarketCapSentiment)
                        SourceValue("Alternative.me", data.alternativeMeSentiment)
                        SourceValue("Radar Técnico (Binance)", data.radarSentimentScore)
                        Text("Média de ${data.sentimentSources} fonte(s)", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    val cycleLabel = when (data.cycle) { MarketCycle.BULL -> "Bull market"; MarketCycle.BEAR -> "Bear market"; MarketCycle.TRANSITION -> "Transição" }
                    val cycleColor = when (data.cycle) { MarketCycle.BULL -> Green; MarketCycle.BEAR -> Red; MarketCycle.TRANSITION -> Blue }
                    AnalysisCard("Ciclo do Bitcoin", cycleLabel, "BTC versus médias de 50 e 200 dias", cycleColor) {
                        Text("BTC ${formatPrice(data.btcPrice, com.radardemercado.app.data.QuoteCurrency.USDT)}", fontWeight = FontWeight.Bold)
                        Text("Média 50d: ${formatPrice(data.btcMa50, com.radardemercado.app.data.QuoteCurrency.USDT)}", color = Muted)
                        Text("Média 200d: ${formatPrice(data.btcMa200, com.radardemercado.app.data.QuoteCurrency.USDT)}", color = Muted)
                    }
                    val season = when { data.altcoinSeasonIndex >= 75 -> "Altcoin Season"; data.altcoinSeasonIndex <= 25 -> "Bitcoin Season"; else -> "Mercado neutro" }
                    AnalysisCard("Altcoin Season", "${data.altcoinSeasonIndex}%", season, if (data.altcoinSeasonIndex >= 75) Green else Blue) {
                        LinearProgressIndicator(progress = { data.altcoinSeasonIndex / 100f }, modifier = Modifier.fillMaxWidth(), color = if (data.altcoinSeasonIndex >= 75) Green else Blue)
                        SourceValue("CoinMarketCap", data.coinMarketCapAltcoinSeasonIndex, "%")
                        SourceValue("Radar/Binance", data.radarAltcoinSeasonIndex, "%")
                        Text("${data.altcoinsAnalyzed} ativos líquidos comparados ao BTC em 90 dias", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Fontes: CoinMarketCap, Alternative.me e dados públicos da Binance. Alternative.me exibida com atribuição conforme seus termos. Não constitui recomendação de investimento.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SourceValue(label: String, value: Int?, suffix: String = "") {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(value?.let { "$it$suffix" } ?: "Indisponível", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AnalysisCard(title: String, value: String, subtitle: String, color: androidx.compose.ui.graphics.Color, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(value, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            Text(subtitle, color = color)
            content()
        }
    }
}
