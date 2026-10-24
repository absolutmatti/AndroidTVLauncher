package com.example.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Verwaltet alle persistenten Einstellungen des Launchers über Android SharedPreferences.
 * Dazu gehören Favoriten, App-Reihenfolge, Start-Historie, Anzeige-Modi und Fokus-Position.
 */
class AppPreferences(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_FILE_NAME = "philips_tv_launcher_prefs"

        private const val KEY_FAVORITES = "key_favorites"
        private const val KEY_CUSTOM_ORDER = "key_custom_order"
        private const val KEY_RECENTS = "key_recents"
        private const val KEY_RECENTS_MODE = "key_recents_mode"
        private const val KEY_SHOW_SYSTEM_APPS = "key_show_system_apps"
        private const val KEY_CUSTOM_ICONS = "key_custom_icons"
        private const val KEY_CUSTOM_CATEGORIES = "key_custom_categories"
        private const val KEY_LAST_FOCUSED_ROW = "key_last_focused_row"
        private const val KEY_LAST_FOCUSED_ITEM = "key_last_focused_item"
        private const val KEY_WEATHER_CITY = "key_weather_city"

        // Konstanten für den "Letzte Apps"-Modus (entspricht Block F der Spezifikation)
        const val RECENTS_MODE_OFF = 0
        const val RECENTS_MODE_HERO_ONLY = 1
        const val RECENTS_MODE_ROW_ONLY = 2
        const val RECENTS_MODE_BOTH = 3
    }

    /**
     * Liest die Liste der favorisierten Paketnamen aus.
     */
    fun getFavorites(): Set<String> {
        return preferences.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    /**
     * Fügt eine App zu den Favoriten hinzu.
     */
    fun addFavorite(packageName: String) {
        val current = getFavorites().toMutableSet()
        current.add(packageName)
        preferences.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    /**
     * Entfernt eine App aus den Favoriten.
     */
    fun removeFavorite(packageName: String) {
        val current = getFavorites().toMutableSet()
        current.remove(packageName)
        preferences.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    /**
     * Prüft, ob ein Paket in den Favoriten markiert ist.
     */
    fun isFavorite(packageName: String): Boolean {
        return getFavorites().contains(packageName)
    }

    /**
     * Schaltet den Favoriten-Status einer App um.
     * @return Der neue Status (true = jetzt Favorit, false = entfernt).
     */
    fun toggleFavorite(packageName: String): Boolean {
        return if (isFavorite(packageName)) {
            removeFavorite(packageName)
            false
        } else {
            addFavorite(packageName)
            true
        }
    }

    /**
     * Liest die benutzerdefinierte Reihenfolge der Apps (Paketnamen in Reihenfolge).
     */
    fun getCustomAppOrder(): List<String> {
        val jsonString = preferences.getString(KEY_CUSTOM_ORDER, null) ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (_: Exception) {
            // Bei Formatfehlern Fallback auf leere Liste
        }
        return list
    }

    /**
     * Speichert eine benutzerdefinierte Reihenfolge der Apps.
     */
    fun saveCustomAppOrder(order: List<String>) {
        val jsonArray = JSONArray()
        order.forEach { jsonArray.put(it) }
        preferences.edit().putString(KEY_CUSTOM_ORDER, jsonArray.toString()).apply()
    }

    /**
     * Liest die zuletzt geöffneten Apps (max. 10 Einträge, neuester zuerst).
     */
    fun getRecentApps(): List<String> {
        val jsonString = preferences.getString(KEY_RECENTS, null) ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (_: Exception) {
            // Ignoriere Parsing-Fehler
        }
        return list
    }

    /**
     * Fügt eine App an den Anfang der Liste der zuletzt geöffneten Apps ein.
     */
    fun addRecentApp(packageName: String) {
        val current = getRecentApps().toMutableList()
        current.remove(packageName) // Verhindert Duplikate
        current.add(0, packageName)
        // Begrenze auf maximal 10 Einträge
        val trimmed = if (current.size > 10) current.subList(0, 10) else current

        val jsonArray = JSONArray()
        trimmed.forEach { jsonArray.put(it) }
        preferences.edit().putString(KEY_RECENTS, jsonArray.toString()).apply()
    }

    /**
     * Gibt den konfigurierten "Letzte Apps"-Modus zurück.
     * Standard: RECENTS_MODE_BOTH (wie vom Nutzer gewünscht).
     */
    fun getRecentAppsMode(): Int {
        return preferences.getInt(KEY_RECENTS_MODE, RECENTS_MODE_BOTH)
    }

    /**
     * Setzt den Modus für die Anzeige der zuletzt genutzten Apps.
     */
    fun setRecentAppsMode(mode: Int) {
        preferences.edit().putInt(KEY_RECENTS_MODE, mode).apply()
    }

    /**
     * Gibt an, ob System-Apps ohne Launcher-Kategorie sichtbar sein sollen.
     */
    fun isShowSystemApps(): Boolean {
        return preferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
    }

    /**
     * Schaltet die Anzeige von System-Apps ein oder aus.
     */
    fun setShowSystemApps(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, show).apply()
    }

    /**
     * Ermittelt den Pfad zu einem benutzerdefinierten Icon für das angegebene Paket.
     */
    fun getCustomIconPath(packageName: String): String? {
        val jsonString = preferences.getString(KEY_CUSTOM_ICONS, null) ?: return null
        return try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has(packageName)) jsonObject.getString(packageName) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Speichert oder entfernt einen benutzerdefinierten Icon-Pfad für eine App.
     */
    fun setCustomIconPath(packageName: String, path: String?) {
        val currentString = preferences.getString(KEY_CUSTOM_ICONS, "{}") ?: "{}"
        try {
            val jsonObject = JSONObject(currentString)
            if (path != null) {
                jsonObject.put(packageName, path)
            } else {
                jsonObject.remove(packageName)
            }
            preferences.edit().putString(KEY_CUSTOM_ICONS, jsonObject.toString()).apply()
        } catch (_: Exception) {
            // Ignoriere Fehler
        }
    }

    /**
     * Liest die benutzerdefinierte Kategorie-Zuweisung für eine App (z. B. "GAMES", "STREAMING", "PRODUCTIVITY").
     */
    fun getCustomCategory(packageName: String): String? {
        val jsonString = preferences.getString(KEY_CUSTOM_CATEGORIES, null) ?: return null
        return try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has(packageName)) jsonObject.getString(packageName) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Speichert oder setzt eine benutzerdefinierte Kategorie-Zuweisung zurück.
     */
    fun setCustomCategory(packageName: String, categoryName: String?) {
        val currentString = preferences.getString(KEY_CUSTOM_CATEGORIES, "{}") ?: "{}"
        try {
            val jsonObject = JSONObject(currentString)
            if (categoryName != null) {
                jsonObject.put(packageName, categoryName)
            } else {
                jsonObject.remove(packageName)
            }
            preferences.edit().putString(KEY_CUSTOM_CATEGORIES, jsonObject.toString()).apply()
        } catch (_: Exception) {
            // Ignoriere Fehler
        }
    }

    /**
     * Merkt sich die zuletzt genutzte Reihe für den Fokus nach Druck auf die HOME-Taste.
     */
    fun getLastFocusedRow(): Int = preferences.getInt(KEY_LAST_FOCUSED_ROW, 0)
    fun setLastFocusedRow(row: Int) = preferences.edit().putInt(KEY_LAST_FOCUSED_ROW, row).apply()

    /**
     * Merkt sich das zuletzt fokussierte Element innerhalb einer Reihe.
     */
    fun getLastFocusedItem(): Int = preferences.getInt(KEY_LAST_FOCUSED_ITEM, 0)
    fun setLastFocusedItem(item: Int) = preferences.edit().putInt(KEY_LAST_FOCUSED_ITEM, item).apply()

    /**
     * Gibt den manuell konfigurierten Standort für das Wetter zurück (oder leer für automatische Erkennung).
     */
    fun getWeatherCity(): String = preferences.getString(KEY_WEATHER_CITY, "") ?: ""
    fun setWeatherCity(city: String) = preferences.edit().putString(KEY_WEATHER_CITY, city.trim()).apply()

    /**
     * Setzt alle Launcher-Einstellungen und Favoriten auf die Standardwerte zurück.
     */
    fun resetToDefaults() {
        preferences.edit().clear().apply()
    }
}
