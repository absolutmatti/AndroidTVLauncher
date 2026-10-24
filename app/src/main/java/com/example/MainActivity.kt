package com.example

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.data.AppPreferences
import com.example.data.AppRepository
import com.example.data.weather.WeatherRepository
import com.example.model.AppInfo
import com.example.model.WeatherInfo
import com.example.ui.MainBrowseFragment
import com.example.ui.settings.SettingsActivity
import com.example.ui.viewmodel.LauncherViewModel
import com.example.ui.voice.VoiceSearchDialog
import kotlinx.coroutines.launch

/**
 * Haupt-Activity des TV Launchers mit kompakter Top-Leiste und zentral platziertem App-Grid.
 */
class MainActivity : FragmentActivity() {

    private lateinit var launcherViewModel: LauncherViewModel
    private lateinit var appRepository: AppRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var weatherRepository: WeatherRepository

    private var currentTargetAppForIcon: AppInfo? = null
    private var currentWeatherInfo: WeatherInfo? = null

    // Top-Leiste Views
    private lateinit var btnTopSettings: FrameLayout
    private lateinit var btnTopWifi: FrameLayout
    private lateinit var tvTopWeatherTemp: TextView
    private lateinit var ivTopWeatherIcon: ImageView
    private lateinit var btnTopUser: FrameLayout
    private lateinit var btnTopNotification: FrameLayout

    // Launcher zur Bildauswahl für benutzerdefinierte Icons
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val target = currentTargetAppForIcon
        if (uri != null && target != null) {
            launcherViewModel.saveCustomIcon(target, uri) { success ->
                if (success) {
                    Toast.makeText(this@MainActivity, "Icon für ${target.label} aktualisiert", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Fehler beim Speichern des Icons", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Berechtigungsabfrage für Standort (für lokale Wetterdaten)
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        loadWeather()
    }

    // Berechtigungsabfrage für Mikrofon (für Sprachsuche)
    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceSearch()
        } else {
            Toast.makeText(
                this,
                getString(R.string.voice_search_permission_needed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        launcherViewModel = ViewModelProvider(this)[LauncherViewModel::class.java]
        appRepository = AppRepository(this)
        appPreferences = AppPreferences(this)
        weatherRepository = WeatherRepository(this)

        initTopBar()
        setupMainFragment()
        checkLocationPermissionsAndLoadWeather()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetToHomePosition()
    }

    override fun onResume() {
        super.onResume()
        launcherViewModel.loadApps()
        loadWeather(showToast = false)
        resetToHomePosition()
    }

    /**
     * Setzt das App-Grid auf die Favoriten-Reihe unten zurück.
     */
    private fun resetToHomePosition() {
        val fragment = supportFragmentManager.findFragmentById(R.id.rowsContainer) as? MainBrowseFragment
        fragment?.resetToHomePosition()
    }

    /**
     * Initialisiert die interaktiven Buttons der oberen Statusleiste.
     */
    private fun initTopBar() {
        btnTopSettings = findViewById(R.id.btnTopSettings)
        btnTopWifi = findViewById(R.id.btnTopWifi)
        tvTopWeatherTemp = findViewById(R.id.tvTopWeatherTemp)
        ivTopWeatherIcon = findViewById(R.id.ivTopWeatherIcon)
        btnTopUser = findViewById(R.id.btnTopUser)
        btnTopNotification = findViewById(R.id.btnTopNotification)

        // 1. Einstellungsknopf (jetzt oben rechts)
        btnTopSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 2. WLAN-Symbol -> Direkt zu den System-WLAN-Einstellungen
        btnTopWifi.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (ex: Exception) {
                    Toast.makeText(this, "WLAN-Einstellungen nicht verfügbar", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 3. Wetterklick -> Wetterdetails
        val openWeatherDetails = View.OnClickListener {
            showWeatherDetailsDialog()
        }
        tvTopWeatherTemp.setOnClickListener(openWeatherDetails)
        ivTopWeatherIcon.setOnClickListener(openWeatherDetails)

        // 4. Benutzersymbol (jetzt oben links) -> Konto / Profileinstellungen
        btnTopUser.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Benutzerprofil", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Benachrichtigungssymbol
        btnTopNotification.setOnClickListener {
            Toast.makeText(this, "Keine neuen Benachrichtigungen", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Startet die TV-Sprachsuche.
     */
    private fun startVoiceSearch() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        VoiceSearchDialog(this, appRepository, weatherRepository) { app ->
            launcherViewModel.launchApp(app)
        }.show()
    }

    /**
     * Bindet das Leanback MainBrowseFragment ein.
     */
    private fun setupMainFragment() {
        var fragment = supportFragmentManager.findFragmentById(R.id.rowsContainer) as? MainBrowseFragment
        if (fragment == null) {
            fragment = MainBrowseFragment().apply {
                onChangeIconRequested = { app ->
                    currentTargetAppForIcon = app
                    pickImageLauncher.launch("image/*")
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.rowsContainer, fragment)
                .commit()
        }
    }

    /**
     * Prüft Standort-Berechtigungen für Wetterdaten.
     */
    private fun checkLocationPermissionsAndLoadWeather() {
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarse) {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            loadWeather(showToast = false)
        }
    }

    /**
     * Lädt Wetterdaten und aktualisiert die obere Leiste mit Temperatur & wetterabhängigem Icon.
     */
    private fun loadWeather(showToast: Boolean = false) {
        lifecycleScope.launch {
            val weatherInfo: WeatherInfo = weatherRepository.fetchCurrentWeather()
            currentWeatherInfo = weatherInfo

            if (weatherInfo.isLoaded) {
                tvTopWeatherTemp.text = weatherInfo.formattedTemperature()
                updateWeatherIcon(weatherInfo.conditionText)

                if (showToast) {
                    Toast.makeText(
                        this@MainActivity,
                        "Wetter aktualisiert: ${weatherInfo.formattedTemperature()}, ${weatherInfo.conditionText}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                tvTopWeatherTemp.text = "--°C"
                ivTopWeatherIcon.setImageResource(R.drawable.ic_weather_sunny)

                if (showToast && !weatherInfo.errorMessage.isNullOrEmpty()) {
                    Toast.makeText(this@MainActivity, weatherInfo.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Wählt das passende Vektor-Icon je nach Wetterbedingung.
     */
    private fun updateWeatherIcon(conditionText: String) {
        val lower = conditionText.lowercase()
        val iconRes = when {
            lower.contains("regen") || lower.contains("rain") || lower.contains("drizzle") -> R.drawable.ic_weather_rain
            lower.contains("schnee") || lower.contains("snow") || lower.contains("frost") -> R.drawable.ic_weather_snow
            lower.contains("wolke") || lower.contains("cloud") || lower.contains("bedeckt") || lower.contains("trüb") -> R.drawable.ic_weather_cloudy
            lower.contains("sonne") || lower.contains("sun") || lower.contains("klar") || lower.contains("heiter") -> R.drawable.ic_weather_sunny
            else -> R.drawable.ic_weather_sunny
        }
        ivTopWeatherIcon.setImageResource(iconRes)
    }

    /**
     * Zeigt ausführliche Wetterdetails an.
     */
    private fun showWeatherDetailsDialog() {
        val info = currentWeatherInfo
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_weather_details, null)

        val ivIcon: ImageView = view.findViewById(R.id.dialogWeatherIcon)
        val tvCity: TextView = view.findViewById(R.id.dialogWeatherCity)
        val tvCondition: TextView = view.findViewById(R.id.dialogWeatherCondition)
        val tvTemp: TextView = view.findViewById(R.id.dialogWeatherTemp)
        val tvFeelsLike: TextView = view.findViewById(R.id.dialogWeatherFeelsLike)
        val tvHumidity: TextView = view.findViewById(R.id.dialogWeatherHumidity)
        val tvWind: TextView = view.findViewById(R.id.dialogWeatherWind)
        val tvNotice: TextView = view.findViewById(R.id.dialogWeatherNotice)
        val btnRefresh: Button = view.findViewById(R.id.btnRefreshWeather)
        val btnClose: Button = view.findViewById(R.id.btnCloseWeather)

        var dialog: AlertDialog? = null

        if (info != null && info.isLoaded) {
            tvCity.text = info.formattedLocation()
            tvCondition.text = info.conditionText
            tvTemp.text = info.formattedTemperature()
            tvFeelsLike.text = info.formattedFeelsLike() ?: "--"
            tvHumidity.text = info.humidity?.let { "$it%" } ?: "--"
            tvWind.text = info.windSpeedMps?.let { "${Math.round(it * 3.6)} km/h" } ?: "--"

            val iconUrl = info.getIconUrl()
            if (!iconUrl.isNullOrEmpty()) {
                ivIcon.load(iconUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_weather_placeholder)
                    error(R.drawable.ic_weather_placeholder)
                }
            } else {
                updateWeatherIcon(info.conditionText)
            }
        } else {
            tvCity.text = info?.cityName?.ifBlank { "Live-Wetter" } ?: "Live-Wetter"
            tvCondition.text = info?.conditionText?.ifBlank { getString(R.string.weather_unavailable) }
            tvTemp.text = "--°C"
            tvFeelsLike.text = "--"
            tvHumidity.text = "--"
            tvWind.text = "--"

            val msg = info?.errorMessage ?: "Wetterdaten werden geladen…"
            tvNotice.text = msg
            tvNotice.visibility = View.VISIBLE
        }

        btnRefresh.setOnClickListener {
            dialog?.dismiss()
            loadWeather(showToast = true)
        }

        btnClose.setOnClickListener {
            dialog?.dismiss()
        }

        dialog = AlertDialog.Builder(this, R.style.TvDialogTheme)
            .setView(view)
            .create()

        dialog.show()
        btnRefresh.requestFocus()
    }

    /**
     * Springt bei Druck auf die Zurück-Taste zur Favoriten-Reihe unten zurück.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.rowsContainer) as? MainBrowseFragment
        if (fragment != null && fragment.selectedPosition > 0) {
            fragment.resetToHomePosition()
        } else {
            btnTopUser.requestFocus()
        }
    }
}
