package com.example.model

/**
 * Modell für die aktuellen Wetterdaten, die in der Hero-Karte des TV Launchers angezeigt werden.
 *
 * @property temperatureCelsius Gemessene Temperatur in Grad Celsius.
 * @property feelsLikeCelsius Gefühlte Temperatur in Grad Celsius.
 * @property conditionText Textuelle Beschreibung des Wetterzustands (z. B. "Leicht bewölkt").
 * @property cityName Name des erkannten oder konfigurierten Standorts.
 * @property countryCode Ländercode (z. B. "DE", "AT", "CH").
 * @property iconCode Wetter-Symbolcode der API (z. B. "01d" für Sonnenschein, "10d" für Regen).
 * @property humidity Relative Luftfeuchtigkeit in Prozent.
 * @property windSpeedMps Windgeschwindigkeit in m/s.
 * @property isLoaded Zeigt an, ob gültige Live-Daten geladen werden konnten.
 * @property errorMessage Fehlermeldung bei Problemen mit Netzwerk oder Berechtigungen.
 */
data class WeatherInfo(
    val temperatureCelsius: Double = 0.0,
    val feelsLikeCelsius: Double? = null,
    val conditionText: String = "",
    val cityName: String = "",
    val countryCode: String? = null,
    val iconCode: String? = null,
    val humidity: Int? = null,
    val windSpeedMps: Double? = null,
    val isLoaded: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Formatiert die Temperatur gerundet mit Einheitszeichen für die TV-Anzeige.
     */
    fun formattedTemperature(): String {
        return "${Math.round(temperatureCelsius)}°C"
    }

    /**
     * Formatiert die gefühlte Temperatur falls vorhanden.
     */
    fun formattedFeelsLike(): String? {
        return feelsLikeCelsius?.let { "Gefühlt ${Math.round(it)}°C" }
    }

    /**
     * Liefert die offizielle OpenWeatherMap CDN-URL für das hochauflösende Wetter-Icon.
     */
    fun getIconUrl(): String? {
        return if (!iconCode.isNullOrEmpty()) {
            "https://openweathermap.org/img/wn/${iconCode}@2x.png"
        } else {
            null
        }
    }

    /**
     * Formatiert den Standort (Stadt + optionales Länderkürzel).
     */
    fun formattedLocation(): String {
        return if (!countryCode.isNullOrEmpty()) {
            "$cityName, $countryCode"
        } else {
            cityName
        }
    }
}
