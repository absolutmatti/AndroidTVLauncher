package com.example.model

import android.graphics.drawable.Drawable
import com.example.R

/**
 * Kategorien im App Drawer zur schnellen Organisation und Navigation auf dem Android TV.
 */
enum class AppCategory(val titleResId: Int) {
    ALL(R.string.category_all),
    FAVORITES(R.string.category_favorites),
    STREAMING(R.string.category_streaming),
    GAMES(R.string.category_games),
    PRODUCTIVITY(R.string.category_productivity)
}

/**
 * Repräsentiert eine auf dem Android TV installierte Anwendung.
 *
 * @property id Eindeutige Kennung (typischerweise der Paketname der App).
 * @property packageName Der Android-Paketname (z. B. "com.netflix.ninja").
 * @property activityName Der Name der Start-Activity (z. B. "com.netflix.ninja.MainActivity").
 * @property label Der im Launcher sichtbare Anzeigename der App.
 * @property bannerDrawable Optionales horizontales TV-Banner (16:9), bevorzugt für Leanback UI.
 * @property iconDrawable Standard-App-Icon als Fallback, falls kein Leanback-Banner existiert.
 * @property customIconPath Lokaler Dateipfad zu einem vom Nutzer ausgewählten benutzerdefinierten Icon.
 * @property customCategory Vom Nutzer manuell zugewiesene Kategorie (überschreibt Auto-Erkennung).
 * @property isFavorite Gibt an, ob die App in der Favoriten-Reihe angezeigt werden soll.
 * @property isSystemApp Gibt an, ob es sich um eine vorinstallierte Systemanwendung handelt.
 */
data class AppInfo(
    val id: String,
    val packageName: String,
    val activityName: String,
    val label: String,
    val bannerDrawable: Drawable? = null,
    val iconDrawable: Drawable? = null,
    val customIconPath: String? = null,
    var customCategory: AppCategory? = null,
    var isFavorite: Boolean = false,
    val isSystemApp: Boolean = false
) {
    /**
     * Ermittelt die beste visuelle Darstellung für die Leanback-Karte:
     * Bevorzugt wird ein TV-Banner, ansonsten das normale Icon.
     */
    fun getDisplayDrawable(): Drawable? {
        return bannerDrawable ?: iconDrawable
    }

    /**
     * Prüft, ob es sich bei der App typischerweise um eine Video- oder Audio-Streaming-App handelt.
     */
    fun isStreamingApp(): Boolean {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        val streamingKeywords = listOf(
            "video", "stream", "media", "tv", "movie", "player", "cinema",
            "youtube", "netflix", "prime", "amazon.video", "disney", "twitch",
            "spotify", "ard", "zdf", "kodi", "plex", "vlc", "max", "hulu",
            "peacock", "crunchyroll", "pluto", "waipu", "zattoo", "ted", "vimeo",
            "tidal", "deezer", "music", "audioplayer", "podcast", "tunein"
        )
        return streamingKeywords.any { pkg.contains(it) || name.contains(it) }
    }

    /**
     * Prüft, ob es sich bei der App um ein Spiel oder einen Emulator handelt.
     */
    fun isGameApp(): Boolean {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        val gameKeywords = listOf(
            "game", "arcade", "racing", "puzzle", "rpg", "simulator",
            "steam", "geforce", "retroarch", "emulator", "ppsspp", "chess",
            "sudoku", "minecraft", "asphalt", "crossy", "pacman", "sonic",
            "mario", "nintendo", "sega", "action", "adventure", "rhythm"
        )
        return gameKeywords.any { pkg.contains(it) || name.contains(it) }
    }

    /**
     * Prüft, ob es sich bei der App um eine Produktivitäts-, Datei- oder Hilfs-App handelt.
     */
    fun isProductivityApp(): Boolean {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        val productivityKeywords = listOf(
            "down", "file", "browser", "chrome", "firefox", "opera", "edge",
            "office", "doc", "pdf", "sheet", "tool", "setting", "manage",
            "cloud", "drive", "dropbox", "term", "calc", "note", "editor",
            "email", "mail", "calendar", "speedtest", "explorer", "apk", "installer"
        )
        return productivityKeywords.any { pkg.contains(it) || name.contains(it) }
    }

    /**
     * Ermittelt die effektive Kategorie (Manuell oder Auto-Erkennung).
     */
    fun getEffectiveCategory(): AppCategory {
        customCategory?.let { return it }
        return when {
            isGameApp() -> AppCategory.GAMES
            isStreamingApp() -> AppCategory.STREAMING
            isProductivityApp() -> AppCategory.PRODUCTIVITY
            else -> AppCategory.ALL
        }
    }

    /**
     * Prüft, ob die App einer bestimmten [AppCategory] entspricht.
     */
    fun matchesCategory(category: AppCategory): Boolean {
        if (customCategory != null) {
            return when (category) {
                AppCategory.ALL -> true
                AppCategory.FAVORITES -> isFavorite
                else -> customCategory == category
            }
        }

        return when (category) {
            AppCategory.ALL -> true
            AppCategory.FAVORITES -> isFavorite
            AppCategory.STREAMING -> isStreamingApp()
            AppCategory.GAMES -> isGameApp()
            AppCategory.PRODUCTIVITY -> isProductivityApp()
        }
    }
}
