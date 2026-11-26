package com.radardemercado.app.ui

import com.radardemercado.app.data.QuoteCurrency
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

fun formatPrice(value: Double, quote: QuoteCurrency): String {
    val formatter = NumberFormat.getCurrencyInstance(if (quote == QuoteCurrency.BRL) Locale("pt", "BR") else Locale.US)
    formatter.currency = Currency.getInstance(if (quote == QuoteCurrency.BRL) "BRL" else "USD")
    formatter.maximumFractionDigits = if (value < 10) 4 else 2
    return if (quote == QuoteCurrency.USDT) formatter.format(value).replace("$", "USDT ") else formatter.format(value)
}

fun formatTime(value: Long): String = SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale("pt", "BR")).format(Date(value))
