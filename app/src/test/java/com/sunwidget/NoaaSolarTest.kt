package com.sunwidget

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for NoaaSolar — verifies the NOAA Spencer/Iqbal algorithm
 * against known sunrise/sunset values for well-documented locations and dates.
 *
 * Reference values from https://gml.noaa.gov/grad/solcalc/
 * Tolerance: ±3 minutes (accounts for atmospheric refraction variance).
 */
class NoaaSolarTest {

    private val toleranceMins = 3.0

    /** Build a Calendar for a specific date at local midnight in a given TZ. */
    private fun calFor(year: Int, month: Int, day: Int, tzId: String): Calendar {
        val tz  = TimeZone.getTimeZone(tzId)
        val cal = Calendar.getInstance(tz)
        cal.set(year, month - 1, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    /** Convert "HH:MM AM/PM" string to minutes from midnight for comparison. */
    private fun parseFormatted(time: String): Double {
        if (time.contains("-") || time.contains("No")) return Double.NaN
        val parts  = time.trim().split(":", " ")
        var h      = parts[0].toInt()
        val m      = parts[1].toInt()
        val suffix = parts[2].uppercase()
        if (suffix == "PM" && h != 12) h += 12
        if (suffix == "AM" && h == 12) h = 0
        return (h * 60 + m).toDouble()
    }

    // ── New York, summer solstice ─────────────────────────────────────────────
    @Test
    fun `New York summer solstice sunrise within tolerance`() {
        val cal    = calFor(2024, 6, 21, "America/New_York")
        val result = NoaaSolar.compute(40.7128, -74.0060, cal)
        // NOAA reference: 5:24 AM EDT = 324 mins
        val diff = Math.abs(result.sunriseMinutes - 324.0)
        assertTrue("Sunrise diff $diff > $toleranceMins min", diff <= toleranceMins)
    }

    @Test
    fun `New York summer solstice sunset within tolerance`() {
        val cal    = calFor(2024, 6, 21, "America/New_York")
        val result = NoaaSolar.compute(40.7128, -74.0060, cal)
        // NOAA reference: 8:31 PM EDT = 1231 mins
        val diff = Math.abs(result.sunsetMinutes - 1231.0)
        assertTrue("Sunset diff $diff > $toleranceMins min", diff <= toleranceMins)
    }

    // ── London, winter solstice ───────────────────────────────────────────────
    @Test
    fun `London winter solstice sunrise within tolerance`() {
        val cal    = calFor(2024, 12, 21, "Europe/London")
        val result = NoaaSolar.compute(51.5074, -0.1278, cal)
        // NOAA reference: 8:03 AM GMT = 483 mins
        val diff = Math.abs(result.sunriseMinutes - 483.0)
        assertTrue("Sunrise diff $diff > $toleranceMins min", diff <= toleranceMins)
    }

    // ── Woburn MA (widget default), equinox ──────────────────────────────────
    @Test
    fun `Woburn MA equinox daylight near 12 hours`() {
        val cal    = calFor(2024, 3, 20, "America/New_York")
        val result = NoaaSolar.compute(42.4795, -71.1522, cal)
        val daylight = result.sunsetMinutes - result.sunriseMinutes
        // At equinox daylight is ~12h ±10 min
        assertTrue("Daylight $daylight not near 720", Math.abs(daylight - 720.0) < 10.0)
    }

    // ── Formatted output sanity ───────────────────────────────────────────────
    @Test
    fun `formatted sunrise matches computed minutes`() {
        val cal    = calFor(2024, 6, 21, "America/New_York")
        val result = NoaaSolar.compute(40.7128, -74.0060, cal)
        val parsed = parseFormatted(result.sunriseFormatted)
        val diff   = Math.abs(parsed - result.sunriseMinutes)
        assertTrue("Formatted/computed mismatch: $diff min", diff < 1.0)
    }

    @Test
    fun `daylight duration string format is valid`() {
        val cal    = calFor(2024, 6, 21, "America/New_York")
        val result = NoaaSolar.compute(40.7128, -74.0060, cal)
        assertTrue(
            "Duration format invalid: ${result.daylightFormatted}",
            result.daylightFormatted.matches(Regex("\\d+h \\d{2}m"))
        )
    }

    // ── Kirchhoff sky phase tests ─────────────────────────────────────────────
    @Test
    fun `night phase returned before astronomical dawn`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
        }
        val phase = KirchhoffSky.phaseForTime(cal, sunriseMins = 360.0, sunsetMins = 1200.0)
        assertEquals(KirchhoffSky.SkyPhase.NIGHT, phase)
    }

    @Test
    fun `midday phase returned at solar noon`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 45)
        }
        val phase = KirchhoffSky.phaseForTime(cal, sunriseMins = 370.0, sunsetMins = 1190.0)
        assertEquals(KirchhoffSky.SkyPhase.MIDDAY, phase)
    }

    @Test
    fun `gradient colors array is never empty`() {
        KirchhoffSky.SkyPhase.entries.forEach { phase ->
            val colors = KirchhoffSky.gradientColors(phase)
            assertTrue("Empty gradient for $phase", colors.isNotEmpty())
        }
    }
}
