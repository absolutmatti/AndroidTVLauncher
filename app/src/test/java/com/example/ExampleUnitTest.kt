package com.example

import com.example.model.AppCategory
import com.example.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für App-Kategorisierung und App Drawer Logik.
 */
class ExampleUnitTest {

    @Test
    fun testStreamingAppDetection() {
        val netflix = AppInfo(
            id = "com.netflix.ninja",
            packageName = "com.netflix.ninja",
            activityName = "MainActivity",
            label = "Netflix"
        )
        val youtube = AppInfo(
            id = "com.google.android.youtube.tv",
            packageName = "com.google.android.youtube.tv",
            activityName = "MainActivity",
            label = "YouTube TV"
        )
        val fileManager = AppInfo(
            id = "com.alphainventor.filemanager",
            packageName = "com.alphainventor.filemanager",
            activityName = "MainActivity",
            label = "File Manager"
        )

        assertTrue(netflix.isStreamingApp())
        assertTrue(youtube.isStreamingApp())
        assertFalse(fileManager.isStreamingApp())

        assertTrue(netflix.matchesCategory(AppCategory.STREAMING))
        assertFalse(fileManager.matchesCategory(AppCategory.STREAMING))
    }

    @Test
    fun testProductivityAppDetection() {
        val downloader = AppInfo(
            id = "net.aftvnews.downloader",
            packageName = "net.aftvnews.downloader",
            activityName = "MainActivity",
            label = "Downloader"
        )
        val chrome = AppInfo(
            id = "com.android.chrome",
            packageName = "com.android.chrome",
            activityName = "MainActivity",
            label = "Chrome Browser"
        )
        val spotify = AppInfo(
            id = "com.spotify.tv.android",
            packageName = "com.spotify.tv.android",
            activityName = "MainActivity",
            label = "Spotify Music"
        )

        assertTrue(downloader.isProductivityApp())
        assertTrue(chrome.isProductivityApp())
        assertFalse(spotify.isProductivityApp())

        assertTrue(downloader.matchesCategory(AppCategory.PRODUCTIVITY))
        assertTrue(spotify.matchesCategory(AppCategory.STREAMING))
    }

    @Test
    fun testFavoritesAndAllCategories() {
        val app = AppInfo(
            id = "com.example.custom",
            packageName = "com.example.custom",
            activityName = "MainActivity",
            label = "Custom App",
            isFavorite = true
        )

        assertTrue(app.matchesCategory(AppCategory.ALL))
        assertTrue(app.matchesCategory(AppCategory.FAVORITES))

        val nonFav = app.copy(isFavorite = false)
        assertTrue(nonFav.matchesCategory(AppCategory.ALL))
        assertFalse(nonFav.matchesCategory(AppCategory.FAVORITES))
    }

    @Test
    fun testWeatherInfoFormatting() {
        val weather = com.example.model.WeatherInfo(
            temperatureCelsius = 18.4,
            feelsLikeCelsius = 17.1,
            conditionText = "Leicht bewölkt",
            cityName = "Berlin",
            countryCode = "DE",
            iconCode = "02d",
            humidity = 65,
            windSpeedMps = 3.5,
            isLoaded = true
        )

        assertEquals("18°C", weather.formattedTemperature())
        assertEquals("Gefühlt 17°C", weather.formattedFeelsLike())
        assertEquals("Berlin, DE", weather.formattedLocation())
        assertEquals("https://openweathermap.org/img/wn/02d@2x.png", weather.getIconUrl())
    }

    @Test
    fun testGamesCategory() {
        val retroArch = AppInfo(
            id = "com.retroarch",
            packageName = "com.retroarch",
            activityName = "MainActivity",
            label = "RetroArch Emulator"
        )
        val asphalt = AppInfo(
            id = "com.gameloft.android.ANMP.GloftA8HM",
            packageName = "com.gameloft.android.ANMP.GloftA8HM",
            activityName = "MainActivity",
            label = "Asphalt 8"
        )

        assertTrue(retroArch.isGameApp())
        assertTrue(asphalt.isGameApp())
        assertTrue(retroArch.matchesCategory(AppCategory.GAMES))
        assertTrue(asphalt.matchesCategory(AppCategory.GAMES))
    }

    @Test
    fun testCustomCategoryOverride() {
        // App, die normalerweise als Produktivität erkannt werden würde
        val app = AppInfo(
            id = "com.example.tool",
            packageName = "com.example.tool",
            activityName = "MainActivity",
            label = "Custom Tool"
        )
        assertTrue(app.matchesCategory(AppCategory.PRODUCTIVITY))

        // Manuelle Zuweisung zu Streaming
        app.customCategory = AppCategory.STREAMING
        assertTrue(app.matchesCategory(AppCategory.STREAMING))
        assertFalse(app.matchesCategory(AppCategory.PRODUCTIVITY))

        // Manuelle Zuweisung zu Games
        app.customCategory = AppCategory.GAMES
        assertTrue(app.matchesCategory(AppCategory.GAMES))
        assertFalse(app.matchesCategory(AppCategory.STREAMING))
    }
}
