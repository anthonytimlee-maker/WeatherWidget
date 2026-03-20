# SunWidget ProGuard rules

# Keep widget provider, job service, receivers, and activity
-keep class com.sunwidget.SunWidgetProvider { *; }
-keep class com.sunwidget.SunUpdateJobService { *; }
-keep class com.sunwidget.BootReceiver { *; }
-keep class com.sunwidget.PermissionActivity { *; }

# Keep NOAA solar calculator and Kirchhoff engine (used via reflection by Kotlin)
-keep class com.sunwidget.NoaaSolar { *; }
-keep class com.sunwidget.KirchhoffSky { *; }
-keep class com.sunwidget.KirchhoffSky$SkyPhase { *; }

# Android widget framework
-keep class android.appwidget.** { *; }
-keep class android.app.job.** { *; }

# Kotlin
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Suppress notes about reflection
-dontnote kotlin.internal.PlatformImplementationsKt
