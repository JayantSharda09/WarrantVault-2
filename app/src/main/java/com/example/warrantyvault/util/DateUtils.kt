package com.example.warrantyvault.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private const val PATTERN = "dd/MM/yyyy"

    fun format(millis: Long): String =
        SimpleDateFormat(PATTERN, Locale.getDefault()).format(Date(millis))

    fun addMonths(dateMillis: Long, months: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            add(Calendar.MONTH, months)
        }
        return calendar.timeInMillis
    }

    fun daysRemaining(expiryMillis: Long): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val expiry = Calendar.getInstance().apply {
            timeInMillis = expiryMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return (expiry - now) / (24 * 60 * 60 * 1000)
    }
}
