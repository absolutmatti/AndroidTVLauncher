package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import com.example.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Verantwortlich für das Abfragen, Filtern, Sortieren und Starten von Android-Apps
 * auf dem Philips TV sowie für das Speichern von benutzerdefinierten App-Icons.
 */
class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val appPreferences: AppPreferences = AppPreferences(context)

    /**
     * Lädt alle installierten Anwendungen im Hintergrund (IO-Thread).
     * Filtert standardmäßig auf Apps mit Launcher-Kategorie (TV & Standard).
     *
     * @param showSystemApps Wenn true, werden auch System-Pakete ohne Launcher-Icon einbezogen.
     */
    suspend fun loadInstalledApps(showSystemApps: Boolean): List<AppInfo> = withContext(Dispatchers.IO) {
        val appsMap = mutableMapOf<String, AppInfo>()

        // 1. Abfrage für Android TV Leanback Apps (bevorzugt auf Smart TVs)
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvResolves: List<ResolveInfo> = packageManager.queryIntentActivities(tvIntent, 0)

        // 2. Abfrage für Standard-Android-Launcher Apps (Sideloaded Apps)
        val standardIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val standardResolves: List<ResolveInfo> = packageManager.queryIntentActivities(standardIntent, 0)

        // Zusammenführen, TV-Versionen haben Vorrang bei Banners
        val combinedResolves = (tvResolves + standardResolves).distinctBy { it.activityInfo.packageName }

        for (resolve in combinedResolves) {
            val packageName = resolve.activityInfo.packageName
            // Eigenen Launcher nicht in der App-Liste anzeigen
            if (packageName == context.packageName) continue

            val appInfo = createFromResolveInfo(resolve)
            appsMap[packageName] = appInfo
        }

        // 3. Optional: System-Apps ohne eigenes Launcher-Icon einbeziehen
        if (showSystemApps) {
            val allInstalled = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in allInstalled) {
                if (app.packageName == context.packageName || appsMap.containsKey(app.packageName)) continue

                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = packageManager.getApplicationLabel(app).toString()
                val icon = try {
                    packageManager.getApplicationIcon(app)
                } catch (_: Exception) {
                    null
                }

                val customCatName = appPreferences.getCustomCategory(app.packageName)
                val customCat = customCatName?.let {
                    try { com.example.model.AppCategory.valueOf(it) } catch (_: Exception) { null }
                }

                appsMap[app.packageName] = AppInfo(
                    id = app.packageName,
                    packageName = app.packageName,
                    activityName = "",
                    label = label,
                    iconDrawable = icon,
                    customCategory = customCat,
                    isFavorite = appPreferences.isFavorite(app.packageName),
                    isSystemApp = isSystem
                )
            }
        }

        // Benutzerdefinierte Sortierung anwenden
        val customOrder = appPreferences.getCustomAppOrder()
        val allApps = appsMap.values.toList()

        if (customOrder.isEmpty()) {
            // Alphabetische Standardsortierung nach App-Name
            allApps.sortedBy { it.label.lowercase() }
        } else {
            // Apps nach gespeicherter Nutzer-Reihenfolge anordnen, neue Apps ans Ende hängen
            val orderMap = customOrder.withIndex().associate { it.value to it.index }
            allApps.sortedWith(compareBy(
                { orderMap[it.packageName] ?: Int.MAX_VALUE },
                { it.label.lowercase() }
            ))
        }
    }

    /**
     * Bequeme Hilfsmethode: Ruft alle installierten Launcher-Apps ab,
     * gefiltert nach CATEGORY_LAUNCHER / CATEGORY_LEANBACK_LAUNCHER.
     */
    suspend fun getInstalledApps(): List<AppInfo> {
        return loadInstalledApps(appPreferences.isShowSystemApps())
    }

    /**
     * Erstellt ein [AppInfo]-Objekt aus den ResolveInfo-Daten des PackageManagers.
     */
    private fun createFromResolveInfo(resolve: ResolveInfo): AppInfo {
        val activityInfo = resolve.activityInfo
        val packageName = activityInfo.packageName
        val activityName = activityInfo.name
        val label = resolve.loadLabel(packageManager).toString()

        // 16:9 Leanback TV-Banner laden falls vorhanden (Standard bei Android TV Apps)
        val banner = activityInfo.loadBanner(packageManager)
            ?: activityInfo.applicationInfo.loadBanner(packageManager)

        // Standard-Icon laden
        val defaultIcon = resolve.loadIcon(packageManager)

        // Prüfen, ob ein Custom Icon aus einer Bilddatei existiert
        val customIconPath = appPreferences.getCustomIconPath(packageName)
        val effectiveIcon = if (customIconPath != null) {
            loadCustomDrawable(customIconPath) ?: defaultIcon
        } else {
            defaultIcon
        }

        val isSystem = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        val customCatName = appPreferences.getCustomCategory(packageName)
        val customCat = customCatName?.let {
            try { com.example.model.AppCategory.valueOf(it) } catch (_: Exception) { null }
        }

        return AppInfo(
            id = packageName,
            packageName = packageName,
            activityName = activityName,
            label = label,
            bannerDrawable = banner,
            iconDrawable = effectiveIcon,
            customIconPath = customIconPath,
            customCategory = customCat,
            isFavorite = appPreferences.isFavorite(packageName),
            isSystemApp = isSystem
        )
    }

    /**
     * Setzt eine benutzerdefinierte Kategorie für die Anwendung.
     */
    fun setCustomCategory(packageName: String, category: com.example.model.AppCategory?) {
        appPreferences.setCustomCategory(packageName, category?.name)
    }

    /**
     * Lädt ein Bild aus dem privaten App-Speicher als Drawable.
     */
    private fun loadCustomDrawable(filePath: String): Drawable? {
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) BitmapDrawable(context.resources, bitmap) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Startet die ausgewählte App und vermerkt sie in der Historie der zuletzt genutzten Apps.
     */
    fun launchApp(appInfo: AppInfo): Boolean {
        return try {
            var launchIntent = packageManager.getLeanbackLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent == null) {
                launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            }
            if (launchIntent == null && appInfo.activityName.isNotEmpty()) {
                launchIntent = Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(appInfo.packageName, appInfo.activityName)
                }
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                // In Historie der zuletzt gestarteten Apps einreihen
                appPreferences.addRecentApp(appInfo.packageName)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Öffnet die Android-Systemeinstellungen für das angegebene Paket.
     */
    fun openAppDetails(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback auf allgemeine Einstellungen
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    /**
     * Öffnet den System-Dialog zum Deinstallieren der App.
     */
    fun requestUninstall(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignoriere Fehler
        }
    }

    /**
     * Speichert ein vom Nutzer ausgewähltes Bild lokal im App-Dateiverzeichnis
     * und verknüpft es als Custom Icon für das Paket.
     */
    suspend fun saveCustomIcon(packageName: String, sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val iconsDir = File(context.filesDir, "custom_icons").apply { mkdirs() }
            val destFile = File(iconsDir, "${packageName}_icon.png")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            appPreferences.setCustomIconPath(packageName, destFile.absolutePath)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Setzt das Icon einer App wieder auf das Standard-Systemicon zurück.
     */
    fun resetCustomIcon(packageName: String) {
        val path = appPreferences.getCustomIconPath(packageName)
        if (path != null) {
            try {
                File(path).delete()
            } catch (_: Exception) {
                // Ignoriere Löschfehler
            }
        }
        appPreferences.setCustomIconPath(packageName, null)
    }
}
