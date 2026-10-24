package com.example.data.weather

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit-Interface für die OpenWeatherMap REST-API (data/2.5/weather).
 * Unterstützt Koordinaten (GPS/Fused Location) und Städtenamen.
 */
interface WeatherApiService {

    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "de",
        @Query("appid") apiKey: String
    ): OpenWeatherResponse

    @GET("data/2.5/weather")
    suspend fun getCurrentWeatherByCity(
        @Query("q") cityName: String,
        @Query("units") units: String = "metric",
        @Query("lang") language: String = "de",
        @Query("appid") apiKey: String
    ): OpenWeatherResponse
}

@JsonClass(generateAdapter = true)
data class OpenWeatherResponse(
    @param:Json(name = "name") val cityName: String?,
    @param:Json(name = "main") val main: MainWeatherData?,
    @param:Json(name = "weather") val weatherList: List<WeatherDescriptionItem>?,
    @param:Json(name = "wind") val wind: WindData? = null,
    @param:Json(name = "sys") val sys: SysData? = null
)

@JsonClass(generateAdapter = true)
data class MainWeatherData(
    @param:Json(name = "temp") val temp: Double?,
    @param:Json(name = "feels_like") val feelsLike: Double? = null,
    @param:Json(name = "temp_min") val tempMin: Double? = null,
    @param:Json(name = "temp_max") val tempMax: Double? = null,
    @param:Json(name = "humidity") val humidity: Int? = null,
    @param:Json(name = "pressure") val pressure: Int? = null
)

@JsonClass(generateAdapter = true)
data class WeatherDescriptionItem(
    @param:Json(name = "id") val id: Int? = null,
    @param:Json(name = "main") val main: String? = null,
    @param:Json(name = "description") val description: String? = null,
    @param:Json(name = "icon") val icon: String? = null
)

@JsonClass(generateAdapter = true)
data class WindData(
    @param:Json(name = "speed") val speed: Double? = null,
    @param:Json(name = "deg") val deg: Int? = null
)

@JsonClass(generateAdapter = true)
data class SysData(
    @param:Json(name = "country") val country: String? = null
)
