package com.example.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.AppPreferences
import com.example.data.AppRepository
import kotlinx.coroutines.launch

/**
 * Einstellungs-Activity für Android TV.
 * Ermöglicht die Anpassung des Launchers per D-Pad (Fernbedienung).
 */
class SettingsActivity : FragmentActivity() {

    private lateinit var appPreferences: AppPreferences
    private lateinit var appRepository: AppRepository

    private lateinit var switchSystemApps: Switch
    private lateinit var tvCurrentRecentsMode: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        appPreferences = AppPreferences(this)
        appRepository = AppRepository(this)

        initViews()
    }

    private fun initViews() {
        val btnBack: Button = findViewById(R.id.btnBack)
        val settingSystemAppsCard: LinearLayout = findViewById(R.id.settingSystemAppsCard)
        switchSystemApps = findViewById(R.id.switchSystemApps)

        val settingRecentsModeCard: LinearLayout = findViewById(R.id.settingRecentsModeCard)
        tvCurrentRecentsMode = findViewById(R.id.tvCurrentRecentsMode)

        val settingReorderAppsCard: LinearLayout = findViewById(R.id.settingReorderAppsCard)
        val settingResetCard: LinearLayout = findViewById(R.id.settingResetCard)

        // Status laden
        switchSystemApps.isChecked = appPreferences.isShowSystemApps()
        updateRecentsModeText()

        // 1. Zurück-Button
        btnBack.setOnClickListener {
            finish()
        }

        // 2. System-Apps Toggle
        settingSystemAppsCard.setOnClickListener {
            val newState = !switchSystemApps.isChecked
            switchSystemApps.isChecked = newState
            appPreferences.setShowSystemApps(newState)
        }

        // 3. Letzte Apps Modus Auswahl
        settingRecentsModeCard.setOnClickListener {
            showRecentsModeDialog()
        }

        // 4. App-Reihenfolge anpassen
        settingReorderAppsCard.setOnClickListener {
            showReorderAppsDialog()
        }

        // 5. Auf Standardeinstellungen zurücksetzen
        settingResetCard.setOnClickListener {
            showResetConfirmDialog()
        }

        // Fokus beim Öffnen auf das erste Element legen
        settingSystemAppsCard.requestFocus()
    }

    /**
     * Aktualisiert die Textanzeige des aktuellen Letzte-Apps-Modus.
     */
    private fun updateRecentsModeText() {
        val text = when (appPreferences.getRecentAppsMode()) {
            AppPreferences.RECENTS_MODE_OFF -> getString(R.string.settings_recents_off)
            AppPreferences.RECENTS_MODE_HERO_ONLY -> getString(R.string.settings_recents_hero_only)
            AppPreferences.RECENTS_MODE_ROW_ONLY -> getString(R.string.settings_recents_row_only)
            else -> getString(R.string.settings_recents_both)
        }
        tvCurrentRecentsMode.text = text
    }

    /**
     * Öffnet den Auswahldialog für die 4 Stufen der "Zuletzt genutzt"-Anzeige.
     */
    private fun showRecentsModeDialog() {
        val options = arrayOf(
            getString(R.string.settings_recents_off),
            getString(R.string.settings_recents_hero_only),
            getString(R.string.settings_recents_row_only),
            getString(R.string.settings_recents_both)
        )
        val current = appPreferences.getRecentAppsMode()

        AlertDialog.Builder(this, R.style.TvDialogTheme)
            .setTitle(R.string.settings_recents_mode)
            .setSingleChoiceItems(options, current) { dialog, which ->
                appPreferences.setRecentAppsMode(which)
                updateRecentsModeText()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Ermöglicht das Anpassen der App-Reihenfolge in einem TV-Dialog.
     */
    private fun showReorderAppsDialog() {
        lifecycleScope.launch {
            val apps = appRepository.loadInstalledApps(appPreferences.isShowSystemApps()).toMutableList()
            if (apps.isEmpty()) {
                Toast.makeText(this@SettingsActivity, "Keine Apps vorhanden", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val appNames = apps.map { it.label }.toTypedArray()

            AlertDialog.Builder(this@SettingsActivity, R.style.TvDialogTheme)
                .setTitle(R.string.settings_reorder_apps)
                .setItems(appNames) { _, which ->
                    // Ausgewählte App nach vorne schieben
                    val selected = apps.removeAt(which)
                    apps.add(0, selected)
                    val newOrder = apps.map { it.packageName }
                    appPreferences.saveCustomAppOrder(newOrder)
                    Toast.makeText(
                        this@SettingsActivity,
                        "\"${selected.label}\" an den Anfang verschoben",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    /**
     * Bestätigungsdialog für das Zurücksetzen aller Einstellungen.
     */
    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this, R.style.TvDialogTheme)
            .setTitle(R.string.settings_reset)
            .setMessage(R.string.settings_reset_confirm)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                appPreferences.resetToDefaults()
                switchSystemApps.isChecked = false
                updateRecentsModeText()
                Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
