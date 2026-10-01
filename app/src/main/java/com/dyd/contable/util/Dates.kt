package com.dyd.contable.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val locale: Locale get() = Locale.getDefault()

    fun today(): LocalDate = LocalDate.now(zone)

    fun toLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Guardamos las fechas como el mediodía local, así nunca cambian de día por zona horaria. */
    fun toMillis(date: LocalDate): Long = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endOf(date: LocalDate): Long = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun monthRange(month: YearMonth): Pair<Long, Long> = startOf(month.atDay(1)) to endOf(month.atEndOfMonth())
    fun yearRange(year: Int): Pair<Long, Long> =
        startOf(LocalDate.of(year, 1, 1)) to endOf(LocalDate.of(year, 12, 31))

    /** El DatePicker de Material trabaja en UTC. */
    fun toPickerMillis(millis: Long): Long =
        toLocalDate(millis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(utcMillis: Long): Long =
        toMillis(Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate())

    fun formatShort(millis: Long): String =
        toLocalDate(millis).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

    fun formatDayHeader(date: LocalDate): String {
        val t = today()
        return when (date) {
            t -> "Hoy"
            t.minusDays(1) -> "Ayer"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)).replaceFirstChar { it.titlecase(locale) }
        }
    }

    fun monthName(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year

    fun monthShort(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.SHORT, locale).replace(".", "").replaceFirstChar { it.titlecase(locale) }

    fun isoDate(millis: Long): String = toLocalDate(millis).toString()
}
