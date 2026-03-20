package com.sunwidget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.*

/**
 * SunWidget — Android 16 home screen widget
 *
 * Persistence: last values saved to SharedPreferences — widget never goes
 *              blank after screen-off, reboot, or launcher restart.
 * Updates    : AlarmManager fires at 12 AM, 6 AM, 12 PM, 6 PM daily.
 *              Also refreshes immediately on tap.
 */
class SunWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SunWidgetProvider::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }
    }

    override fun onEnabled(context: Context)  { SunAlarmReceiver.schedule(context) }
    override fun onDisabled(context: Context) { SunAlarmReceiver.cancel(context) }

    companion object {
        const val ACTION_REFRESH = "com.sunwidget.ACTION_REFRESH"

        private const val KEY_TEMP_CURRENT = "last_temp_current"
        private const val KEY_TEMP_HIGH    = "last_temp_high"
        private const val KEY_TEMP_LOW     = "last_temp_low"
        
        private const val PREFS_NAME    = "com.sunwidget.prefs"
        private const val KEY_SUNRISE   = "last_sunrise"
        private const val KEY_SUNSET    = "last_sunset"
        private const val KEY_DAYLIGHT  = "last_daylight"
        private const val KEY_RISE_MINS = "last_sunrise_mins"
        private const val KEY_SET_MINS  = "last_sunset_mins"

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // ── 1. Paint cached data immediately — widget is never blank ──────
            val cachedRise    = prefs.getString(KEY_SUNRISE,  null)
            val cachedSet     = prefs.getString(KEY_SUNSET,   null)
            val cachedDay     = prefs.getString(KEY_DAYLIGHT, null)
            val cachedRiseMins = prefs.getFloat(KEY_RISE_MINS, -1f).toDouble()
            val cachedSetMins  = prefs.getFloat(KEY_SET_MINS,  -1f).toDouble()

            if (cachedRise != null && cachedRiseMins >= 0) {
                val cal   = Calendar.getInstance()
                val phase = KirchhoffSky.phaseForTime(cal, cachedRiseMins, cachedSetMins)
                renderViews(context, manager, widgetId,
                    cachedRise, cachedSet ?: "--:--", cachedDay ?: "--h --m", phase)
            }

            // ── 2. Recalculate with fresh location, save, re-render ───────────
            val location = getBestLocation(context) ?: return  // keep showing cache
            val lat = location.latitude
            val lon = location.longitude

            val cal   = Calendar.getInstance()
            val solar = NoaaSolar.compute(lat, lon, cal)
            val phase = KirchhoffSky.phaseForTime(cal, solar.sunriseMinutes, solar.sunsetMinutes)

            prefs.edit()
                .putString(KEY_SUNRISE,   solar.sunriseFormatted)
                .putString(KEY_SUNSET,    solar.sunsetFormatted)
                .putString(KEY_DAYLIGHT,  solar.daylightFormatted)
                .putFloat(KEY_RISE_MINS,  solar.sunriseMinutes.toFloat())
                .putFloat(KEY_SET_MINS,   solar.sunsetMinutes.toFloat())
                .apply()

            renderViews(context, manager, widgetId,
                solar.sunriseFormatted, solar.sunsetFormatted, solar.daylightFormatted, phase)
        }

        private fun renderViews(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            sunrise: String,
            sunset: String,
            daylight: String,
            phase: KirchhoffSky.SkyPhase
        ) {
            val gradColors   = KirchhoffSky.gradientColors(phase)
            val midColor     = gradColors[gradColors.size / 2]
            val contentColor = if (isColorLight(midColor))
                Color.argb(204, 28, 14, 4) else Color.argb(237, 255, 255, 255)

            val views = RemoteViews(context.packageName, R.layout.widget_sun)

            val cornerPx = context.resources.getDimensionPixelSize(R.dimen.widget_corner_radius)
            views.setImageViewBitmap(R.id.iv_background,
                buildGradientBitmap(gradColors, cornerPx.toFloat(), 400, 110))

            views.setTextViewText(R.id.tv_sunrise, sunrise)
            views.setTextColor(R.id.tv_sunrise, contentColor)
            views.setInt(R.id.iv_icon_sunrise, "setColorFilter", contentColor)

            views.setTextViewText(R.id.tv_sunset, sunset)
            views.setTextColor(R.id.tv_sunset, contentColor)
            views.setInt(R.id.iv_icon_sunset, "setColorFilter", contentColor)

            views.setTextViewText(R.id.tv_daylight, daylight)
            views.setTextColor(R.id.tv_daylight, contentColor)
            views.setInt(R.id.iv_icon_daylight, "setColorFilter", contentColor)

            val tapIntent = Intent(context, SunWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val pi = PendingIntent.getBroadcast(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(widgetId, views)
        }

        private fun getBestLocation(context: Context): Location? {
            val hasFine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) return null

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
                .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .minByOrNull { System.currentTimeMillis() - it.time }
        }

        private fun isColorLight(color: Int): Boolean {
            fun lin(c: Int): Double {
                val f = c / 255.0
                return if (f <= 0.04045) f / 12.92 else ((f + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * lin(Color.red(color)) +
                    0.7152 * lin(Color.green(color)) +
                    0.0722 * lin(Color.blue(color)) > 0.40
        }

        private fun buildGradientBitmap(
            colors: IntArray, cornerPx: Float, w: Int, h: Int
        ): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val gd = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
            gd.cornerRadius = cornerPx
            gd.setBounds(0, 0, w, h)
            gd.draw(canvas)
            return bmp
        }
    }
}

// =============================================================================
// NOAA Solar Calculator (Spencer 1971 / Iqbal 1983)
// =============================================================================
object NoaaSolar {

    data class SunResult(
        val sunriseMinutes: Double,
        val sunsetMinutes: Double,
        val sunriseFormatted: String,
        val sunsetFormatted: String,
        val daylightFormatted: String
    )

    fun compute(latDeg: Double, lonDeg: Double, cal: Calendar): SunResult {
        val tzHours  = cal.timeZone.getOffset(cal.timeInMillis) / 3_600_000.0
        val doy      = cal.get(Calendar.DAY_OF_YEAR)
        val year     = cal.get(Calendar.YEAR)
        val daysInYr = if (isLeap(year)) 366.0 else 365.0
        val g        = 2.0 * PI / daysInYr * (doy - 1)

        val eqt = 229.18 * (0.000075
                + 0.001868 * cos(g)   - 0.032077 * sin(g)
                - 0.014615 * cos(2*g) - 0.04089  * sin(2*g))

        val decl = 0.006918 - 0.399912*cos(g) + 0.070257*sin(g) -
                0.006758*cos(2*g) + 0.000907*sin(2*g) -
                0.002697*cos(3*g) + 0.001480*sin(3*g)

        val latR  = Math.toRadians(latDeg)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) -
                tan(latR) * tan(decl)

        if (cosHa <= -1.0) return SunResult(0.0,   1440.0, "--:--", "--:--", "24h 00m")
        if (cosHa >=  1.0) return SunResult(720.0,  720.0, "--:--", "--:--", "0h 00m")

        val haD       = Math.toDegrees(acos(cosHa))
        val riseLocal = (720.0 - 4.0*(lonDeg + haD) - eqt) + tzHours * 60.0
        val setLocal  = (720.0 - 4.0*(lonDeg - haD) - eqt) + tzHours * 60.0

        return SunResult(riseLocal, setLocal,
            fmtTime(riseLocal), fmtTime(setLocal),
            fmtDuration((setLocal - riseLocal).coerceAtLeast(0.0)))
    }

    private fun fmtTime(mins: Double): String {
        val t   = mins.toInt().coerceIn(0, 1439)
        val h24 = t / 60; val m = t % 60
        val h12 = when { h24 == 0 -> 12; h24 > 12 -> h24 - 12; else -> h24 }
        return "%d:%02d %s".format(h12, m, if (h24 < 12) "AM" else "PM")
    }

    private fun fmtDuration(mins: Double): String {
        val t = mins.toInt().coerceIn(0, 1440)
        return "%dh %02dm".format(t / 60, t % 60)
    }

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
}

// =============================================================================
// Kirchhoff Sky — Planckian locus gradient engine
// =============================================================================
object KirchhoffSky {

    enum class SkyPhase {
        NIGHT, PRE_DAWN, SUNRISE, MORNING, MIDDAY, GOLDEN_HOUR, SUNSET, DUSK
    }

    fun phaseForTime(cal: Calendar, riseMins: Double, setMins: Double): SkyPhase {
        val now       = cal.get(Calendar.HOUR_OF_DAY) * 60.0 + cal.get(Calendar.MINUTE)
        val astroDawn = riseMins - 72.0
        val nautDawn  = riseMins - 48.0
        val noon      = (riseMins + setMins) / 2.0
        val goldenPM  = setMins - 60.0
        val astroDusk = setMins + 72.0

        return when {
            now < astroDawn || now > astroDusk -> SkyPhase.NIGHT
            now < nautDawn                     -> SkyPhase.PRE_DAWN
            now < riseMins + 60                -> SkyPhase.SUNRISE
            now < noon - 90                    -> SkyPhase.MORNING
            now < noon + 90                    -> SkyPhase.MIDDAY
            now < goldenPM                     -> SkyPhase.MORNING
            now < setMins                      -> SkyPhase.GOLDEN_HOUR
            now < setMins + 36                 -> SkyPhase.SUNSET
            else                               -> SkyPhase.DUSK
        }
    }

    fun gradientColors(phase: SkyPhase): IntArray = when (phase) {
        SkyPhase.NIGHT       -> colors("#050a1a", "#0f1535", "#1a2050")
        SkyPhase.PRE_DAWN    -> colors("#0d0820", "#2a1248", "#6b2540", "#c04020")
        SkyPhase.SUNRISE     -> colors("#1a0f2e", "#7a2830", "#d05820", "#f0a040")
        SkyPhase.MORNING     -> colors("#e07030", "#f0b050", "#f8d880", "#b0d8f0")
        SkyPhase.MIDDAY      -> colors("#2a78c0", "#50a0e0", "#88caf5", "#c8e8ff")
        SkyPhase.GOLDEN_HOUR -> colors("#c84010", "#e87028", "#f5b040", "#fce8a0")
        SkyPhase.SUNSET      -> colors("#1a0828", "#601830", "#c04020", "#e88030")
        SkyPhase.DUSK        -> colors("#060412", "#180c30", "#3a1848")
    }

    private fun colors(vararg hex: String) = IntArray(hex.size) { Color.parseColor(hex[it]) }
}
