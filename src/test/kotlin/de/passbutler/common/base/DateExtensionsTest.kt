package de.passbutler.common.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DateExtensionsTest {

    /**
     * Seconds
     */

    @Test
    fun `Format less one second`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 123L)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("0 seconds ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format exactly one second`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 1_000L)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("1 second ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format multiple seconds`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 1_000L * 2)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("2 seconds ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format upper seconds limit`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 1_000L * 59)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("59 seconds ago", formattedRelativeDateTime)
    }

    /**
     * Minutes
     */

    @Test
    fun `Format exactly one minute`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("1 minute ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format multiple minutes`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 2)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("2 minutes ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format upper minutes limit`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 59)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("59 minutes ago", formattedRelativeDateTime)
    }

    /**
     * Hours
     */

    @Test
    fun `Format exactly one hour`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("1 hour ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format multiple hours`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 2)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("2 hours ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format upper hour limit`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 23)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("23 hours ago", formattedRelativeDateTime)
    }

    /**
     * Days
     */

    @Test
    fun `Format exactly one day`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 24)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("1 day ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format multiple days`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 24 * 2)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("2 days ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format upper days limit`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 24 * 29)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("29 days ago", formattedRelativeDateTime)
    }

    /**
     * Months
     */

    @Test
    fun `Format exactly one month`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 24 * 30)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("1 month ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format multiple months`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 24 * 30 * 2)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("2 months ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format upper months limit`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 24 * 30 * 11)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("11 months ago", formattedRelativeDateTime)
    }

    /**
     * Years
     */

    @Test
    fun `Format exactly one year`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 24 * 365)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("1 year ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format multiple years`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() - 60_000L * 60 * 24 * 365 * 2)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("2 years ago", formattedRelativeDateTime)
    }

    /**
     * Edge cases
     */

    @Test
    fun `Format current date`() {
        val dateInPast = Instant.now()
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("0 seconds ago", formattedRelativeDateTime)
    }

    @Test
    fun `Format not in past date`() {
        val dateInPast = Instant.ofEpochMilli(Instant.now().toEpochMilli() + 3600L)
        val formattedRelativeDateTime = dateInPast.formattedRelativeDateTime(UNIT_TRANSLATIONS)
        assertEquals("0 seconds ago", formattedRelativeDateTime)
    }

    companion object {
        private val UNIT_TRANSLATIONS = RelativeDateFormattingTranslations(
            unitTranslations = UnitTranslations(
                second = UnitTranslation("second", "seconds"),
                minute = UnitTranslation("minute", "minutes"),
                hour = UnitTranslation("hour", "hours"),
                day = UnitTranslation("day", "days"),
                month = UnitTranslation("month", "months"),
                year = UnitTranslation("year", "years")
            ),
            sinceString = "%s %s ago"
        )
    }
}