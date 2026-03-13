# Weather Widget for Android

A home screen widget for Android showing real-time weather and NOAA-calculated sunrise/sunset times, with a Kirchhoff blackbody curve sky background.

## Features

- **Weather (Left side):** Current temperature, weather condition icon, daily Hi/Lo, precipitation probability, barometric pressure with trend arrow
- **Sun (Right side):** Sunrise, sunset, total daylight, solar noon — calculated using the NOAA solar algorithm
- **Dynamic background:** Color shifts along the Planckian locus (Kirchhoff blackbody curve) from deep night blue → golden dawn → daylight blue → golden dusk → night
- **Resizable:** 1×1 to 4×1 cells; sun panel hides when too narrow, weather prioritized
- **GPS/Home toggle:** Small icon in the top-right corner switches between live GPS and a saved Home location
- **Temperature toggle:** Tap the temperature to switch °C ↔ °F
- **Auto-refresh:** Weather updates every 15 minutes via WorkManager
- **Free weather API:** Uses [Open-Meteo](https://open-meteo.com/) — no API key required

---

## Building via GitHub Actions (no Android Studio required)

### Step 1: Create a GitHub repository

```
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/YOUR_USERNAME/weather-widget.git
git push -u origin main
```

### Step 2: The GitHub Actions workflow runs automatically

On every push to `main`, the workflow at `.github/workflows/build.yml`:
1. Sets up JDK 17
2. Downloads Gradle 8.6 (via the wrapper)
3. Builds both `debug` and `release` APKs
4. Uploads them as **Artifacts** (downloadable from the Actions tab)

### Step 3: Download your APK

1. Go to your repository on GitHub
2. Click **Actions** tab
3. Click the latest workflow run
4. Scroll to **Artifacts** at the bottom
5. Download `WeatherWidget-debug` (install directly) or `WeatherWidget-release`

### Step 4: Install on your Moto G Power 2025

1. On your phone: **Settings → Security → Install unknown apps** → enable for your browser/file manager
2. Transfer the APK to your phone (email, Drive, USB, etc.)
3. Tap the APK to install
4. Long-press your home screen → **Widgets** → find **Weather & Sun** → drag to home screen

### Step 5: Grant permissions

On first use, grant:
- **Location** (for GPS weather and sun calculation)
- The widget will request permissions when you tap the GPS icon if not yet granted

---

## Creating a versioned release

Tag a commit to automatically create a GitHub Release with APKs attached:

```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## Project structure

```
app/src/main/
  java/com/example/weatherwidget/
    WeatherWidgetProvider.kt   # AppWidgetProvider (BroadcastReceiver)
    WidgetUpdater.kt           # Builds RemoteViews, background bitmap
    WeatherUpdateWorker.kt     # WorkManager worker (background fetch)
    WorkScheduler.kt           # Schedules/cancels WorkManager tasks
    WeatherFetcher.kt          # Open-Meteo API client
    LocationHelper.kt          # FusedLocationProviderClient wrapper
    SunCalculator.kt           # NOAA solar algorithm (pure math)
    SkyBackground.kt           # Kirchhoff/Planckian color temperature
    HomeLocationActivity.kt    # Dialog to save GPS as Home
    WidgetConfigActivity.kt    # Widget config stub (pass-through)
    BootReceiver.kt            # Reschedules work after reboot
    Prefs.kt                   # SharedPreferences helper
    WeatherData.kt             # Data models + WMO code mapping
  res/
    layout/widget_layout.xml          # Widget RemoteViews layout
    xml/weather_widget_info.xml       # Widget metadata (sizing, etc.)
    drawable/                         # Vector icons (Material Design)
    values/                           # strings, colors, themes
```

---

## Customization

| What | Where |
|------|-------|
| Refresh interval | `WorkScheduler.kt` — change `15, TimeUnit.MINUTES` |
| Default units | `Prefs.kt` — `KEY_USE_CELSIUS` default value |
| Background opacity | `SkyBackground.kt` — `Color.argb(230, ...)` alpha value |
| Kirchhoff curve tuning | `SkyBackground.kt` — `getColorTemperature()` thresholds |

---

## Technical notes

- **"Can't load widget" prevention:** `onUpdate()` does zero network/disk I/O. All heavy work is in WorkManager. RemoteViews uses only compatible view types. All operations wrapped in try/catch with fallback.
- **NOAA algorithm:** Exact port of the NOAA Solar Calculator spreadsheet (not a simplified approximation). Accurate to within ±1 minute for most latitudes.
- **Kirchhoff curve:** Uses Tanner Helland's polynomial approximation of the Planckian locus, with additional brightness scaling for twilight phases (astronomical, nautical, civil).
- **WorkManager:** Survives reboots, Doze mode, and app updates. BootReceiver ensures rescheduling after fresh install/update.
- **No API key needed:** Open-Meteo is fully free with no registration required.
