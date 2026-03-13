package com.example.weatherwidget

import kotlin.math.*

/**
 * NOAA Solar Calculator
 * Based on the NOAA Solar Calculator spreadsheet algorithms.
 * https://www.esrl.noaa.gov/gmd/grad/solcalc/
 *
 * Returns times in minutes from midnight UTC.
 */
object SunCalculator {

    data class SunTimes(
        val sunriseMinutesUtc: Double,  // minutes from midnight UTC
        val sunsetMinutesUtc: Double,
        val solarNoonMinutesUtc: Double
    ) {
        val daylightMinutes: Double get() = sunsetMinutesUtc - sunriseMinutesUtc
    }

    /**
     * Calculate sunrise/sunset for given date and location.
     * @param lat Latitude in degrees
     * @param lon Longitude in degrees (positive = East, negative = West)
     * @param year  Full year (e.g., 2025)
     * @param month 1-12
     * @param day   1-31
     * @return SunTimes in UTC minutes from midnight, or null if no sunrise/sunset (polar)
     */
    fun calculate(lat: Double, lon: Double, year: Int, month: Int, day: Int): SunTimes? {
        // Julian Day Number
        val jd = toJulianDay(year, month, day)

        // Julian Century
        val t = (jd - 2451545.0) / 36525.0

        // Geometric mean longitude of the Sun (degrees)
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0

        // Geometric mean anomaly of the Sun (degrees)
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)

        // Equation of center
        val mRad = toRad(m)
        val c = sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(2 * mRad) * (0.019993 - 0.000101 * t) +
                sin(3 * mRad) * 0.000289

        // Sun's true longitude (degrees)
        val sunLon = l0 + c

        // Sun's apparent longitude (degrees)
        val omega = 125.04 - 1934.136 * t
        val sunAppLon = sunLon - 0.00569 - 0.00478 * sin(toRad(omega))

        // Mean obliquity of the ecliptic (degrees)
        val meanObliq = 23.0 + (26.0 + (21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0

        // Corrected obliquity
        val obliqCorr = meanObliq + 0.00256 * cos(toRad(omega))

        // Sun's declination (degrees)
        val declRad = asin(sin(toRad(obliqCorr)) * sin(toRad(sunAppLon)))
        val decl = toDeg(declRad)

        // Equation of time (minutes)
        val y = tan(toRad(obliqCorr / 2)).pow(2)
        val eqTime = 4.0 * toDeg(
            y * sin(2 * toRad(l0))
                    - 2 * eccentricityEarthOrbit(t) * sin(toRad(m))
                    + 4 * eccentricityEarthOrbit(t) * y * sin(toRad(m)) * cos(2 * toRad(l0))
                    - 0.5 * y * y * sin(4 * toRad(l0))
                    - 1.25 * eccentricityEarthOrbit(t).pow(2) * sin(2 * toRad(m))
        )

        // Hour angle sunrise (degrees)
        val latRad = toRad(lat)
        val cosHa = cos(toRad(90.833)) / (cos(latRad) * cos(declRad)) - tan(latRad) * tan(declRad)

        // Check for polar day/night
        if (cosHa < -1.0 || cosHa > 1.0) return null

        val haSunrise = toDeg(acos(cosHa))

        // Solar noon (minutes from midnight UTC)
        val solarNoon = (720 - 4.0 * lon - eqTime)

        // Sunrise and sunset in minutes from midnight UTC
        val sunrise = solarNoon - haSunrise * 4.0
        val sunset = solarNoon + haSunrise * 4.0

        return SunTimes(sunrise, sunset, solarNoon)
    }

    private fun eccentricityEarthOrbit(t: Double) =
        0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun toJulianDay(year: Int, month: Int, day: Int): Double {
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 12 else month
        val a = (y / 100.0).toInt()
        val b = 2 - a + (a / 4.0).toInt()
        return (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + day + b - 1524.5
    }

    private fun toRad(deg: Double) = deg * PI / 180.0
    private fun toDeg(rad: Double) = rad * 180.0 / PI
}
