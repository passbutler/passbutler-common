package de.passbutler.common.base

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import java.util.concurrent.TimeUnit

val Instant.formattedDateTime: String
    get() {
        val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())

        return dateTimeFormatter.format(this)
    }

fun Instant.formattedRelativeDateTime(relativeDateFormattingTranslations: RelativeDateFormattingTranslations): String {
    val currentTimestampMilliseconds = Instant.now().toEpochMilli()
    val pastTimestampMilliseconds = this.toEpochMilli()

    val durationMilliseconds: Long = currentTimestampMilliseconds - pastTimestampMilliseconds

    val timeUnitMillisecondsMapping = listOf(
        TimeUnit.DAYS.toMillis(365) to relativeDateFormattingTranslations.unitTranslations.year,
        TimeUnit.DAYS.toMillis(30) to relativeDateFormattingTranslations.unitTranslations.month,
        TimeUnit.DAYS.toMillis(1) to relativeDateFormattingTranslations.unitTranslations.day,
        TimeUnit.HOURS.toMillis(1) to relativeDateFormattingTranslations.unitTranslations.hour,
        TimeUnit.MINUTES.toMillis(1) to relativeDateFormattingTranslations.unitTranslations.minute,
        TimeUnit.SECONDS.toMillis(1) to relativeDateFormattingTranslations.unitTranslations.second
    )

    val sinceString = relativeDateFormattingTranslations.sinceString

    return timeUnitMillisecondsMapping.mapNotNull { (timeUnitMilliseconds, unitTranslation) ->
        (durationMilliseconds / timeUnitMilliseconds).takeIf { it > 0 }?.let { millisecondsAmountOfTimeUnit ->
            val userFacingUnitTranslation = when (millisecondsAmountOfTimeUnit) {
                1L -> unitTranslation.one
                else -> unitTranslation.other
            }
            sinceString.format(millisecondsAmountOfTimeUnit, userFacingUnitTranslation)
        }
    }.firstOrNull() ?: sinceString.format(0, relativeDateFormattingTranslations.unitTranslations.second.other)
}

data class RelativeDateFormattingTranslations(
    val unitTranslations: UnitTranslations,
    val sinceString: String
)

data class UnitTranslations(
    val second: UnitTranslation,
    val minute: UnitTranslation,
    val hour: UnitTranslation,
    val day: UnitTranslation,
    val month: UnitTranslation,
    val year: UnitTranslation
)

data class UnitTranslation(val one: String, val other: String)
