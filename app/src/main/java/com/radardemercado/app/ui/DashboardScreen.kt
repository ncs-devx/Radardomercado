package com.radardemercado.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardemercado.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast

@Composable
fun DashboardScreen(vm: RadarViewModel, onNewAlert: () -> Unit, onSimulator: () -> Unit, onAsset: (Asset, QuoteCurrency) -> Unit, onSearch: () -> Unit) {
    val market by vm.market.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val history by vm.history.collectAsState()
    val newsState by vm.news.collectAsState()
    val newsEnabled by vm.newsEnabled.collectAsState()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RADAR DE MERCADO", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Visão geral", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            LiveBadge(market.liveStatus)
            IconButton(onSearch) { Icon(Icons.Default.Search, "Pesquisar ativos") }
            IconButton(vm::refresh) { Icon(Icons.Default.Refresh, "Atualizar") }
        }
        if (market.lastUpdatedAt > 0L) {
            Text(
                "Última cotação: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(market.lastUpdatedAt))}",
                color = Muted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(20.dp))
        if (newsEnabled) {
          Row(verticalAlignment = Alignment.CenterVertically) { Text("Principais notícias", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton(vm::refreshNews) { Text("Atualizar") } }
          when {
            newsState.loading && newsState.items.isEmpty() -> LinearProgressIndicator(Modifier.fillMaxWidth())
            newsState.error != null && newsState.items.isEmpty() -> Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(newsState.error!!, Modifier.weight(1f), color = Muted); TextButton(vm::refreshNews) { Text("Tentar") } } }
            else -> {
                val pagerState = rememberPagerState(pageCount = { newsState.items.size })
                HorizontalPager(state = pagerState, pageSpacing = 10.dp, beyondViewportPageCount = 1, key = { newsState.items[it].link }) { page ->
                    val item = newsState.items[page]
                    Card(onClick = {
                        if (!context.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))))
                            Toast.makeText(context, "Nenhum navegador disponível", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth().height(112.dp), colors = CardDefaults.cardColors(containerColor = SurfaceRaised), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(item.source.uppercase(), color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(item.title, fontWeight = FontWeight.Bold, maxLines = 3) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.Center) { repeat(newsState.items.size) { index -> Box(Modifier.padding(horizontal = 3.dp).size(if (pagerState.currentPage == index) 7.dp else 5.dp).background(if (pagerState.currentPage == index) Green else Muted, RoundedCornerShape(50))) } }
            }
        }
          Spacer(Modifier.height(14.dp))
        }
        if (market.loading && market.tickers.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        market.error?.let { Text(it, color = Red, modifier = Modifier.padding(vertical = 8.dp)) }
        market.tickers.forEach { TickerCard(it) { onAsset(it.asset, it.quote) } }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Alertas ativos", alerts.count { it.quote == QuoteCurrency.USDT && it.enabled && !it.triggered }.toString(), Modifier.weight(1f))
            StatCard("Disparados", history.count { it.quote == QuoteCurrency.USDT }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onNewAlert, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Novo alerta") }
            OutlinedButton(onSimulator, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Science, null); Spacer(Modifier.width(6.dp)); Text("Simular") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable private fun LiveBadge(status: LiveStatus) {
    val (label, color) = when (status) {
        LiveStatus.LIVE -> "● AO VIVO" to Green
        LiveStatus.STALE -> "● DESATUALIZADO" to androidx.compose.ui.graphics.Color(0xFFFFB74D)
        LiveStatus.CONNECTING -> "CONECTANDO" to Blue
        LiveStatus.OFFLINE -> "OFFLINE" to Red
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(20.dp)) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable private fun TickerCard(ticker: Ticker, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceRaised) {
                Text(ticker.asset.symbol.first().toString(), Modifier.padding(horizontal = 16.dp, vertical = 11.dp), color = Green, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${ticker.asset.symbol}/${ticker.quote.name}", fontWeight = FontWeight.Bold)
                Text("Binance • 24h", color = Muted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPrice(ticker.price, ticker.quote), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${if (ticker.changePercent >= 0) "+" else ""}${"%.2f".format(ticker.changePercent)}%", color = if (ticker.changePercent >= 0) Green else Red)
            }
        }
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceRaised), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) { Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 12.sp) }
    }
}
