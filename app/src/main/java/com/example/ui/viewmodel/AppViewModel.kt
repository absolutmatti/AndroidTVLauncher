package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppPreferences
import com.example.data.AppRepository
import com.example.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI-Zustand für den TV-Launcher.
 *
 * @property isLoading Zeigt an, ob die App-Liste aktuell geladen wird.
 * @property favoriteApps Liste der Apps, die vom Nutzer favorisiert wurden.
 * @property recentApps Liste der zuletzt geöffneten Apps (geordnet von neu nach alt).
 * @property allApps Vollständige Liste der installierten Launcher-Apps.
 * @property mostRecentApp Die allerletzte geöffnete App (für die linke Hero-Kachel-Spalte).
 * @property recentsMode Konfigurierter Anzeige-Modus für Historie (Aus, Nur Hero, Nur Reihe, Beides).
 * @property showSystemApps Gibt an, ob auch System-Apps ohne Launcher-Kategorie sichtbar sind.
 */
data class AppUiState(
    val isLoading: Boolean = true,
    val favoriteApps: List<AppInfo> = emptyList(),
    val recentApps: List<AppInfo> = emptyList(),
    val allApps: List<AppInfo> = emptyList(),
    val mostRecentApp: AppInfo? = null,
    val recentsMode: Int = AppPreferences.RECENTS_MODE_BOTH,
    val showSystemApps: Boolean = false
)

/**
 * ViewModel für den Philips Android TV Launcher (AppViewModel).
 *
 * Verwaltet den Lebenszyklus der App-Daten, lädt installierte Anwendungen
 * über das [AppRepository] (gefiltert nach CATEGORY_LAUNCHER / LEANBACK_LAUNCHER)
 * und stellt diese als reaktiven [StateFlow] für die UI (BrowseFragment / MainActivity) bereit.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application.applicationContext)
    private val appPreferences = AppPreferences(application.applicationContext)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    /**
     * Lädt alle installierten Apps asynchron aus dem PackageManager und aktualisiert
     * die Favoriten, Zuletzt-genutzt-Reihen und die Hero-Karte.
     */
    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val showSystem = appPreferences.isShowSystemApps()
            val recentsMode = appPreferences.getRecentAppsMode()

            // 1. Alle Launcher-Apps abrufen (gefiltert nach CATEGORY_LAUNCHER & LEANBACK_LAUNCHER)
            val installedApps = appRepository.loadInstalledApps(showSystem)

            // 2. Favoriten filtern
            val favorites = installedApps.filter { it.isFavorite }

            // 3. Zuletzt genutzte Apps auflösen
            val recentPackageNames = appPreferences.getRecentApps()
            val recentApps = recentPackageNames.mapNotNull { pkg ->
                installedApps.find { it.packageName == pkg }
            }

            // 4. Zuletzt gestartete App für die Hero-Kachel bestimmen
            val mostRecent = recentApps.firstOrNull() ?: installedApps.firstOrNull()

            _uiState.value = AppUiState(
                isLoading = false,
                favoriteApps = favorites,
                recentApps = recentApps,
                allApps = installedApps,
                mostRecentApp = mostRecent,
                recentsMode = recentsMode,
                showSystemApps = showSystem
            )
        }
    }

    /**
     * Startet die ausgewählte Anwendung und reiht sie in die Start-Historie ein.
     */
    fun launchApp(appInfo: AppInfo): Boolean {
        val success = appRepository.launchApp(appInfo)
        if (success) {
            // Nach Start der App den Zustand aktualisieren (Historie)
            loadApps()
        }
        return success
    }

    /**
     * Schaltet den Favoriten-Status einer App um und aktualisiert die UI.
     */
    fun toggleFavorite(appInfo: AppInfo) {
        appPreferences.toggleFavorite(appInfo.packageName)
        loadApps()
    }

    /**
     * Speichert ein benutzerdefiniertes Icon für eine App und lädt die Liste neu.
     */
    fun saveCustomIcon(appInfo: AppInfo, uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = appRepository.saveCustomIcon(appInfo.packageName, uri)
            if (success) {
                loadApps()
            }
            onComplete(success)
        }
    }

    /**
     * Ändert die Kategorie-Zuweisung einer App (Streaming, Spiele, Produktivität oder Auto).
     */
    fun setAppCategory(appInfo: AppInfo, category: com.example.model.AppCategory?) {
        appRepository.setCustomCategory(appInfo.packageName, category)
        loadApps()
    }

    /**
     * Setzt ein verändertes Icon wieder auf das Original-App-Icon zurück.
     */
    fun resetCustomIcon(appInfo: AppInfo) {
        appRepository.resetCustomIcon(appInfo.packageName)
        loadApps()
    }

    /**
     * Speichert eine neue Reihenfolge der installierten Apps.
     */
    fun reorderApps(newOrder: List<String>) {
        appPreferences.saveCustomAppOrder(newOrder)
        loadApps()
    }

    /**
     * Öffnet die Android-Systemdetails zur App.
     */
    fun openAppDetails(packageName: String) {
        appRepository.openAppDetails(packageName)
    }

    /**
     * Startet den Deinstallations-Dialog der App.
     */
    fun requestUninstall(packageName: String) {
        appRepository.requestUninstall(packageName)
    }
}

/**
 * Typ-Aliase zur vollständigen Abwärtskompatibilität,
 * damit sowohl LauncherViewModel als auch AppViewModel verwendet werden können.
 */
typealias LauncherViewModel = AppViewModel
typealias LauncherUiState = AppUiState
