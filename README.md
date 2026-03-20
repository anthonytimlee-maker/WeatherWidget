# SunWidget 🌅

An Android 16 home screen widget that displays NOAA-calculated **sunrise**, **sunset**, and **total daylight hours** — with a dynamically shifting background colour driven by the **Kirchhoff blackbody radiation curve** (Planckian locus).

---

## Features

| Feature | Detail |
|---|---|
| **NOAA solar math** | Spencer (1971) / Iqbal (1983) algorithm — pure math, zero network calls |
| **Kirchhoff sky gradient** | 8 sky phases (night → pre-dawn → sunrise → morning → midday → golden hour → sunset → dusk) mapped to blackbody colour temperatures |
| **Material 3 design** | 28 dp pill container, icon-only rows, auto dark/light content tinting |
| **Location** | GPS (fine) with automatic fallback to network / coarse |
| **Refresh cadence** | JobScheduler every 30 min; also refreshes on tap |
| **Battery friendly** | No network required; respects Doze mode |
| **Android 16 ready** | Targets API 36, minSdk 26 |

---

## Widget layout

```
┌─────────────────────────────────────────┐
│  ☀  6:12 AM                             │  ← Sunrise
│  🌅  7:54 PM                             │  ← Sunset
│  🕐  13h 42m                            │  ← Daylight duration
└─────────────────────────────────────────┘
     ↑ background shifts through Kirchhoff curve all day
```

---

## Kirchhoff colour map

The background gradient interpolates through blackbody radiation colours:

```
Midnight → Pre-dawn → Sunrise → Morning → Midday → Golden hour → Sunset → Dusk → Midnight
 #050a1a     #2a1248   #d05820   #f0b050   #50a0e0   #e87028      #c04020  #180c30  #050a1a
 (~800 K)   (~1400 K) (~2200 K) (~3500 K) (~6500 K) (~2800 K)   (~1800 K) (~900 K)
```

---

## Building

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 36 (Android 16)

### Clone and open

```bash
git clone https://github.com/YOUR_USERNAME/SunWidget.git
cd SunWidget
```

Open in Android Studio → **Build → Make Project**.

### Build APK from command line

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (unsigned — sign before distributing)
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## GitHub Actions CI

Every push to `main` automatically:

1. Builds the **debug APK** and **release APK (unsigned)**
2. Uploads both as build artefacts (retained 30 days)
3. Creates a **GitHub Release** with the debug APK attached

See [`.github/workflows/build.yml`](.github/workflows/build.yml).

---

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS coordinates for NOAA calculation |
| `ACCESS_COARSE_LOCATION` | Fallback when GPS is unavailable |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule the 30-min refresh job after reboot |

No location data ever leaves the device.

---

## Project structure

```
SunWidget/
├── .github/workflows/build.yml       # CI/CD → APK
├── app/src/main/
│   ├── java/com/sunwidget/
│   │   ├── SunWidgetProvider.kt      # Widget + NOAA + Kirchhoff engine
│   │   ├── SunUpdateJobService.kt    # 30-min JobScheduler
│   │   ├── BootReceiver.kt           # Re-schedule after reboot
│   │   └── PermissionActivity.kt    # Transparent location-permission trampoline
│   └── res/
│       ├── layout/widget_sun.xml     # Vertical M3 pill layout
│       ├── xml/widget_info.xml       # AppWidgetProviderInfo (3×1 cells)
│       ├── drawable/                 # ic_sunrise, ic_sunset, ic_clock (vector)
│       └── values/                   # strings, colors, dimens, themes
└── app/src/test/
    └── NoaaSolarTest.kt              # JUnit tests for solar math & sky phases
```

---

## Licence

MIT — see [LICENSE](LICENSE).
