package com.example.data.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.data.AppPreferences
import com.example.model.WeatherInfo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Ruft aktuelle Live-Wetterdaten von OpenWeatherMap ab.
 *
 * Verwaltet:
 * - API-Key aus [BuildConfig.OPENWEATHERMAP_API_KEY] (konfiguriert über AI Studio Secrets Panel / .env)
 * - Standortbestimmung über [FusedLocationProviderClient] mit sicherem Fallback
 * - Unterstützung für konfigurierte Städte aus [AppPreferences]
 * - Caching des letzten erfolgreichen Wetters
 * - Robuste Fehlerbehandlung ohne App-Abstürze
 */
class WeatherRepository(private val context: Context) {

    private val appPreferences = AppPreferences(context)
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // In-Memory Cache des letzten erfolgreichen Wetters
    private var cachedWeather: WeatherInfo? = null

    private val weatherApi: WeatherApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WeatherApiService::class.java)
    }

    /**
     * Ermittelt die aktuellen Live-Wetterdaten von OpenWeatherMap.
     *
     * @param forceRefresh Wenn false, kann bei kurzem Abstand auf den Cache zurückgegriffen werden.
     */
    suspend fun fetchCurrentWeather(forceRefresh: Boolean = false): WeatherInfo = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.OPENWEATHERMAP_API_KEY
        } catch (_: Exception) {
            ""
        }

        // 1. Prüfen, ob ein gültiger API-Schlüssel hinterlegt wurde
        if (apiKey.isNullOrBlank() ||
            apiKey == "OPENWEATHERMAP_API_KEY_PLACEHOLDER" ||
            apiKey == "MY_OPENWEATHERMAP_KEY"
        ) {
            return@withContext WeatherInfo(
                temperatureCelsius = 0.0,
                conditionText = "API-Key nicht hinterlegt",
                cityName = "OpenWeather",
                iconCode = null,
                isLoaded = false,
                errorMessage = "Trage deinen OpenWeatherMap API-Key in den AI Studio Secrets ein"
            )
        }

        // 2. Prüfen, ob der Nutzer eine feste Stadt in den Einstellungen hinterlegt hat
        val customCity = appPreferences.getWeatherCity()

        try {
            val response: OpenWeatherResponse = if (customCity.isNotBlank()) {
                // Abfrage nach benutzerdefinierter Stadt
                weatherApi.getCurrentWeatherByCity(
                    cityName = customCity,
                    apiKey = apiKey
                )
            } else {
                // Standort ermitteln (GPS/Netzwerk) oder Fallback verwenden
                val location = getLastKnownLocation()
                val lat = location?.latitude ?: 52.5200 // Fallback: Berlin
                val lon = location?.longitude ?: 13.4050

                weatherApi.getCurrentWeather(
                    latitude = lat,
                    longitude = lon,
                    apiKey = apiKey
                )
            }

            val temp = response.main?.temp ?: 0.0
            val feelsLike = response.main?.feelsLike
            val rawDesc = response.weatherList?.firstOrNull()?.description ?: "Klar"
            val formattedDesc = rawDesc.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            val icon = response.weatherList?.firstOrNull()?.icon
            val city = response.cityName ?: customCity.ifBlank { "Lokal" }
            val country = response.sys?.country
            val humidity = response.main?.humidity
            val windSpeed = response.wind?.speed

            val info = WeatherInfo(
                temperatureCelsius = temp,
                feelsLikeCelsius = feelsLike,
                conditionText = formattedDesc,
                cityName = city,
                countryCode = country,
                iconCode = icon,
                humidity = humidity,
                windSpeedMps = windSpeed,
                isLoaded = true
            )
            cachedWeather = info
            info
        } catch (e: Exception) {
            // Bei Verbindungsfehlern alten Cache bevorzugen, falls vorhanden
            cachedWeather?.copy(errorMessage = "Aktualisierung fehlgeschlagen: ${e.localizedMessage}")
                ?: WeatherInfo(
                    temperatureCelsius = 0.0,
                    conditionText = "Offline / Kein Empfang",
                    cityName = customCity.ifBlank { "Lokal" },
                    iconCode = null,
                    isLoaded = false,
                    errorMessage = e.localizedMessage ?: "Verbindungsfehler"
                )
        }
    }

    /**
     * Ruft den letzten bekannten Standort ab, falls die Berechtigung erteilt wurde.
     */
    private suspend fun getLastKnownLocation(): Location? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (_: SecurityException) {
                continuation.resume(null)
            }
        }
    }
}
