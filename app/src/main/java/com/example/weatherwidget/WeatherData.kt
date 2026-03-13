package com.example.weatherwidget

/**
 * Weather data model.
 * WMO Weather interpretation codes: https://open-meteo.com/en/docs
 */
data class WeatherData(
    val temperatureC: Float,
    val temperatureMaxC: Float,
    val temperatureMinC: Float,
    val precipitationProbability: Float,   // 0-100
    val surfacePressureHpa: Float,
    val wmoCode: Int,
    val latitude: Double,
    val longitude: Double
)

/**
 * WMO weather code to human-readable description and drawable resource name.
 */
object WmoWeather {
    data class WeatherInfo(val description: String, val iconRes: Int)

    fun getInfo(wmoCode: Int): WeatherInfo {
        return when (wmoCode) {
            0 -> WeatherInfo("Clear sky", R.drawable.ic_weather_sunny)
            1 -> WeatherInfo("Mainly clear", R.drawable.ic_weather_sunny)
            2 -> WeatherInfo("Partly cloudy", R.drawable.ic_weather_partly_cloudy)
            3 -> WeatherInfo("Overcast", R.drawable.ic_weather_cloudy)
            45, 48 -> WeatherInfo("Foggy", R.drawable.ic_weather_fog)
            51, 53, 55 -> WeatherInfo("Drizzle", R.drawable.ic_weather_rainy)
            56, 57 -> WeatherInfo("Freezing drizzle", R.drawable.ic_weather_rainy)
            61, 63, 65 -> WeatherInfo("Rain", R.drawable.ic_weather_rainy)
            66, 67 -> WeatherInfo("Freezing rain", R.drawable.ic_weather_rainy)
            71, 73, 75 -> WeatherInfo("Snow", R.drawable.ic_weather_snowy)
            77 -> WeatherInfo("Snow grains", R.drawable.ic_weather_snowy)
            80, 81, 82 -> WeatherInfo("Rain showers", R.drawable.ic_weather_rainy)
            85, 86 -> WeatherInfo("Snow showers", R.drawable.ic_weather_snowy)
            95 -> WeatherInfo("Thunderstorm", R.drawable.ic_weather_thunder)
            96, 99 -> WeatherInfo("Thunderstorm + hail", R.drawable.ic_weather_thunder)
            else -> WeatherInfo("Unknown", R.drawable.ic_weather_sunny)
        }
    }
}
