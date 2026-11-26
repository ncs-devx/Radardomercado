package com.radardemercado.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radardemercado.app.data.*
import kotlin.math.max
import kotlin.math.min
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    vm: RadarViewModel,
    asset: Asset,
    quote: QuoteCurrency,
    onBack: () -> Unit,
    onAlert: () -> Unit,
    onAlertAtPrice: (Double) -> Unit,
    onEditAlert: (Long) -> Unit
) {
    val market by vm.market.collectAsState()
    val alerts by vm.alerts.collectAsState()
    val intervals = listOf("1m", "5m", "15m", "1h", "4h", "1d")
    var interval by remember { mutableStateOf("15m") }
    var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedAlertPrice by remember { mutableStateOf<Double?>(null) }
    val liveTicker = market.tickers.firstOrNull { it.asset == asset && it.quote == quote }
    val activeAlerts = alerts.filter { it.asset == asset && it.quote == quote && it.enabled && !it.triggered }

    LaunchedEffect(asset, quote, interval) {
        loading = true; error = null
        vm.loadCandles(asset, quote, interval) { result ->
            result.onSuccess { candles = it }.onFailure { error = "Não foi possível carregar o gráfico" }
            loading = false
        }
    }

    val displayCandles = remember(candles, liveTicker?.price) {
        if (candles.isEmpty() || liveTicker == null) candles else candles.dropLast(1) + candles.last().let {
            it.copy(close = liveTicker.price, high = max(it.high, liveTicker.price), low = min(it.low, liveTicker.price))
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("${asset.symbol}/${quote.name}", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = { IconButton(onAlert) { Icon(Icons.Default.AddAlert, "Criar alerta") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Text(liveTicker?.let { formatPrice(it.price, quote) } ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                liveTicker?.let { "${if (it.changePercent >= 0) "+" else ""}${"%.2f".format(it.changePercent)}% • AO VIVO" } ?: "Carregando cotação",
                color = if ((liveTicker?.changePercent ?: 0.0) >= 0) Green else Red
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                intervals.forEach { item ->
                    FilterChip(selected = interval == item, onClick = { interval = item }, label = { Text(item) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
                Box(Modifier.fillMaxWidth().height(360.dp).padding(12.dp)) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(34.dp))
                        error != null -> Text(error!!, color = Red)
                        else -> CandleChart(displayCandles.takeLast(60), activeAlerts, Modifier.fillMaxSize()) { selectedAlertPrice = it }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val visible = displayCandles.takeLast(60)
            if (visible.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ChartStat("Máxima", formatPrice(visible.maxOf { it.high }, quote))
                    ChartStat("Mínima", formatPrice(visible.minOf { it.low }, quote))
                    ChartStat("Candles", visible.size.toString())
                }
            }
            if (activeAlerts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Alertas no gráfico", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeAlerts.forEach { alert ->
                        AssistChip(
                            onClick = { onEditAlert(alert.id) },
                            label = { Text("${if (alert.direction == AlertDirection.ABOVE) "↑" else "↓"} ${formatPrice(alert.targetPrice, quote)}") },
                            leadingIcon = { Icon(Icons.Default.AddAlert, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onAlert, Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Icon(Icons.Default.AddAlert, null); Spacer(Modifier.width(8.dp)); Text("Criar alerta para ${asset.symbol}")
            }
        }
    }
    selectedAlertPrice?.let { selectedPrice ->
        AlertDialog(
            onDismissRequest = { selectedAlertPrice = null },
            title = { Text("Criar alerta neste preço?") },
            text = { Text("Preço selecionado: ${formatPrice(selectedPrice, quote)}") },
            confirmButton = {
                TextButton(onClick = { selectedAlertPrice = null; onAlertAtPrice(selectedPrice) }) { Text("Criar alerta") }
            },
            dismissButton = { TextButton(onClick = { selectedAlertPrice = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun CandleChart(candles: List<Candle>, alerts: List<PriceAlert>, modifier: Modifier = Modifier, onLongPressPrice: (Double) -> Unit) {
    if (candles.isEmpty()) return
    val grid = Muted.copy(alpha = 0.16f)
    val priceFormat = remember { DecimalFormat("#,##0.00") }
    var crosshair by remember(candles) { mutableStateOf<Offset?>(null) }
    val lowest = candles.minOf { it.low }
    val highest = candles.maxOf { it.high }
    val range = (highest - lowest).takeIf { it > 0 } ?: 1.0
    Canvas(modifier.pointerInput(candles) {
        detectTapGestures(
            onTap = { crosshair = null },
            onLongPress = { position ->
                val plotWidth = (size.width.toFloat() - 78.dp.toPx()).coerceAtLeast(1f)
                if (position.x <= plotWidth) {
                    val fixed = Offset(position.x.coerceIn(0f, plotWidth), position.y.coerceIn(0f, size.height.toFloat()))
                    crosshair = fixed
                    onLongPressPrice(highest - (fixed.y / size.height.toDouble()) * range)
                }
            }
        )
    }) {
        val axisWidth = 78.dp.toPx()
        val plotWidth = (size.width - axisWidth).coerceAtLeast(1f)
        fun y(price: Double) = size.height - (((price - lowest) / range) * size.height).toFloat()
        val currentY = y(candles.last().close).coerceIn(10.dp.toPx(), size.height - 10.dp.toPx())
        repeat(5) { index ->
            val lineY = size.height * index / 4f
            drawLine(grid, Offset(0f, lineY), Offset(plotWidth, lineY), strokeWidth = 1f)
            val level = highest - (range * index / 4.0)
            if (kotlin.math.abs(lineY - currentY) > 18.dp.toPx()) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = Muted.toArgb()
                        textSize = 11.sp.toPx()
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawText(priceFormat.format(level), plotWidth + 7.dp.toPx(), lineY.coerceIn(12.dp.toPx(), size.height - 3.dp.toPx()), paint)
                }
            }
        }
        drawLine(grid, Offset(plotWidth, 0f), Offset(plotWidth, size.height), strokeWidth = 1.dp.toPx())
        val slot = plotWidth / candles.size
        val bodyWidth = (slot * 0.58f).coerceAtLeast(2f)
        candles.forEachIndexed { index, candle ->
            val x = slot * index + slot / 2
            val color = if (candle.close >= candle.open) Green else Red
            drawLine(color, Offset(x, y(candle.high)), Offset(x, y(candle.low)), strokeWidth = 1.5f)
            val top = min(y(candle.open), y(candle.close))
            val bottom = max(y(candle.open), y(candle.close))
            val bodyHeight = (bottom - top).coerceAtLeast(2f)
            drawRect(color, topLeft = Offset(x - bodyWidth / 2, top), size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight))
        }
        alerts.forEach { alert ->
            val rawY = y(alert.targetPrice)
            val alertY = rawY.coerceIn(4.dp.toPx(), size.height - 4.dp.toPx())
            val alertColor = if (alert.direction == AlertDirection.ABOVE) Green else Red
            drawLine(
                color = alertColor,
                start = Offset(0f, alertY),
                end = Offset(plotWidth, alertY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
            )
            drawCircle(alertColor, radius = 4.dp.toPx(), center = Offset(plotWidth - 5.dp.toPx(), alertY))
        }
        drawLine(Muted.copy(alpha = .7f), Offset(0f, currentY), Offset(plotWidth, currentY), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        drawRoundRect(
            color = Green,
            topLeft = Offset(plotWidth + 2.dp.toPx(), currentY - 10.dp.toPx()),
            size = Size(axisWidth - 4.dp.toPx(), 20.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = Background.toArgb()
                textSize = 10.sp.toPx()
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.nativeCanvas.drawText(priceFormat.format(candles.last().close), plotWidth + axisWidth / 2, currentY + 4.dp.toPx(), paint)
        }
        crosshair?.let { point ->
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            drawLine(Blue, Offset(point.x, 0f), Offset(point.x, size.height), 1.dp.toPx(), pathEffect = dash)
            drawLine(Blue, Offset(0f, point.y), Offset(plotWidth, point.y), 1.dp.toPx(), pathEffect = dash)
        }
        drawRect(Muted.copy(alpha = .25f), size = androidx.compose.ui.geometry.Size(plotWidth, size.height), style = Stroke(1f))
    }
}

@Composable private fun ChartStat(label: String, value: String) {
    Column { Text(label, color = Muted, style = MaterialTheme.typography.labelSmall); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall) }
}
