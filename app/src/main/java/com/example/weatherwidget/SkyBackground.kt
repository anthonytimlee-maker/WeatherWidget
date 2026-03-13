package com.example.weatherwidget

import android.graphics.Color
import kotlin.math.*

/**
 * Generates sky background colors based on Kirchhoff/Planckian blackbody radiation curve.
 * Maps time-of-day relative to sunrise/sunset to a perceptually accurate sky color.
 *
 * Color temperature progression:
 *  - Night:          2000K  (deep blue-black)
 *  - Astronomical twilight: 2500K
 *  - Nautical twilight:     3000K (deep orange)
 *  - Civil twilight:        4500K (orange-red)
 *  - Golden hour:           5000K (golden orange)
 *  - Daylight:              6500K (sky blue-white)
 *  - Solar noon:            7000K (bright blue-white)
 */
object SkyBackground {

    /**
     * Given current time (minutes from midnight UTC) and sunrise/sunset times,
     * returns a background color derived from the blackbody color temperature.
     */
    fun getColor(
        currentMinutesUtc: Double,
        sunriseMinutesUtc: Double,
        sunsetMinutesUtc: Double
    ): Int {
        val kelvin = getColorTemperature(currentMinutesUtc, sunriseMinutesUtc, sunsetMinutesUtc)
        return kelvinToRgb(kelvin)
    }

    /**
     * Returns a gradient pair (top color, bottom color) for the background.
     */
    fun getGradientColors(
        currentMinutesUtc: Double,
        sunriseMinutesUtc: Double,
        sunsetMinutesUtc: Double
    ): Pair<Int, Int> {
        val kelvin = getColorTemperature(currentMinutesUtc, sunriseMinutesUtc, sunsetMinutesUtc)
        val topKelvin = (kelvin * 1.08).coerceAtMost(8000.0)  // slightly cooler/bluer at top
        val bottomKelvin = (kelvin * 0.85).coerceAtLeast(1800.0) // warmer at horizon
        return Pair(kelvinToRgb(topKelvin), kelvinToRgb(bottomKelvin))
    }

    private fun getColorTemperature(
        nowMin: Double,
        sunriseMin: Double,
        sunsetMin: Double
    ): Double {
        val solarNoon = (sunriseMin + sunsetMin) / 2.0
        val halfDay = (sunsetMin - sunriseMin) / 2.0

        // Twilight offsets (minutes before sunrise / after sunset)
        val civilTwilight = 30.0
        val nauticalTwilight = 60.0
        val astroTwilight = 90.0

        return when {
            // Deep night (well before sunrise)
            nowMin < sunriseMin - astroTwilight -> 2000.0

            // Astronomical dawn
            nowMin < sunriseMin - nauticalTwilight ->
                lerp(2000.0, 2500.0, (nowMin - (sunriseMin - astroTwilight)) / (nauticalTwilight - astroTwilight).coerceAtLeast(1.0))

            // Nautical dawn
            nowMin < sunriseMin - civilTwilight ->
                lerp(2500.0, 3200.0, (nowMin - (sunriseMin - nauticalTwilight)) / (nauticalTwilight - civilTwilight).coerceAtLeast(1.0))

            // Civil dawn (golden hour approach)
            nowMin < sunriseMin ->
                lerp(3200.0, 4800.0, (nowMin - (sunriseMin - civilTwilight)) / civilTwilight.coerceAtLeast(1.0))

            // Morning golden hour (sunrise to 1hr after)
            nowMin < sunriseMin + 60.0 ->
                lerp(4800.0, 6000.0, (nowMin - sunriseMin) / 60.0)

            // Daytime: noon is brightest/bluest
            nowMin < solarNoon -> {
                val progress = (nowMin - (sunriseMin + 60.0)) / ((solarNoon - (sunriseMin + 60.0)).coerceAtLeast(1.0))
                lerp(6000.0, 7000.0, progress)
            }

            nowMin < sunsetMin - 60.0 -> {
                val progress = (nowMin - solarNoon) / ((sunsetMin - 60.0 - solarNoon).coerceAtLeast(1.0))
                lerp(7000.0, 6000.0, progress)
            }

            // Evening golden hour (1hr before sunset)
            nowMin < sunsetMin ->
                lerp(6000.0, 4800.0, (nowMin - (sunsetMin - 60.0)) / 60.0)

            // Civil dusk
            nowMin < sunsetMin + civilTwilight ->
                lerp(4800.0, 3200.0, (nowMin - sunsetMin) / civilTwilight.coerceAtLeast(1.0))

            // Nautical dusk
            nowMin < sunsetMin + nauticalTwilight ->
                lerp(3200.0, 2500.0, (nowMin - (sunsetMin + civilTwilight)) / (nauticalTwilight - civilTwilight).coerceAtLeast(1.0))

            // Astronomical dusk
            nowMin < sunsetMin + astroTwilight ->
                lerp(2500.0, 2000.0, (nowMin - (sunsetMin + nauticalTwilight)) / (astroTwilight - nauticalTwilight).coerceAtLeast(1.0))

            // Deep night
            else -> 2000.0
        }
    }

    /**
     * Convert color temperature in Kelvin to RGB using Tanner Helland's algorithm
     * (approximation of the Planckian locus / Kirchhoff blackbody curve)
     * Valid range: 1000K – 40000K
     */
    fun kelvinToRgb(kelvin: Double): Int {
        val temp = kelvin.coerceIn(1000.0, 40000.0) / 100.0

        val red: Double
        val green: Double
        val blue: Double

        // Red
        red = if (temp <= 66.0) {
            255.0
        } else {
            (329.698727446 * (temp - 60.0).pow(-0.1332047592)).coerceIn(0.0, 255.0)
        }

        // Green
        green = if (temp <= 66.0) {
            (99.4708025861 * ln(temp) - 161.1195681661).coerceIn(0.0, 255.0)
        } else {
            (288.1221695283 * (temp - 60.0).pow(-0.0755148492)).coerceIn(0.0, 255.0)
        }

        // Blue
        blue = if (temp >= 66.0) {
            255.0
        } else if (temp <= 19.0) {
            0.0
        } else {
            (138.5177312231 * ln(temp - 10.0) - 305.0447927307).coerceIn(0.0, 255.0)
        }

        // Apply night-time darkening: below 3000K darken significantly
        val brightnessFactor = when {
            kelvin < 2200.0 -> 0.15  // near-black night
            kelvin < 3000.0 -> lerp(0.15, 0.45, (kelvin - 2200.0) / 800.0)
            kelvin < 4000.0 -> lerp(0.45, 0.65, (kelvin - 3000.0) / 1000.0)
            else -> 1.0
        }

        val r = (red * brightnessFactor).toInt().coerceIn(0, 255)
        val g = (green * brightnessFactor).toInt().coerceIn(0, 255)
        val b = (blue * brightnessFactor).toInt().coerceIn(0, 255)

        // Add alpha: semi-transparent so wallpaper shows through slightly
        return Color.argb(230, r, g, b)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t.coerceIn(0.0, 1.0)
}
