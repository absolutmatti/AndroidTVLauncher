package com.example.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.R
import com.example.data.AppPreferences
import com.example.data.AppRepository
import com.example.model.AppCategory
import com.example.model.AppInfo

/**
 * Zeigt das Kontextmenü bei Lang-Klick auf eine App-Kachel an.
 * Ermöglicht Favoriten-Verwaltung, Deinstallation, System-App-Info,
 * Custom-Icons und manuelle Kategorie-Zuweisung (Streaming, Spiele, Produktivität).
 */
class AppContextMenuDialog(
    private val context: Context,
    private val appInfo: AppInfo,
    private val appRepository: AppRepository,
    private val appPreferences: AppPreferences,
    private val onDataChanged: () -> Unit,
    private val onChangeIconRequested: (AppInfo) -> Unit
) {

    private var dialog: AlertDialog? = null

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_context, null)

        val ivIcon: ImageView = view.findViewById(R.id.dialogAppIcon)
        val tvTitle: TextView = view.findViewById(R.id.dialogAppTitle)
        val tvPackage: TextView = view.findViewById(R.id.dialogAppPackage)

        val btnToggleFavorite: Button = view.findViewById(R.id.btnToggleFavorite)
        val btnAppInfo: Button = view.findViewById(R.id.btnAppInfo)
        val btnUninstall: Button = view.findViewById(R.id.btnUninstall)
        val btnChangeIcon: Button = view.findViewById(R.id.btnChangeIcon)
        val btnChangeCategory: Button = view.findViewById(R.id.btnChangeCategory)
        val btnCancel: Button = view.findViewById(R.id.btnCancel)

        // Header-Daten befüllen
        tvTitle.text = appInfo.label
        val effectiveCat = appInfo.getEffectiveCategory()
        val catLabel = context.getString(effectiveCat.titleResId)
        tvPackage.text = "${appInfo.packageName} • $catLabel"
        val displayDrawable = appInfo.getDisplayDrawable()
        if (displayDrawable != null) {
            ivIcon.setImageDrawable(displayDrawable)
        }

        // Favoriten-Button Beschriftung anpassen
        val isFav = appPreferences.isFavorite(appInfo.packageName)
        btnToggleFavorite.text = if (isFav) {
            context.getString(R.string.action_remove_favorite)
        } else {
            context.getString(R.string.action_add_favorite)
        }

        btnToggleFavorite.setOnClickListener {
            appPreferences.toggleFavorite(appInfo.packageName)
            onDataChanged()
            dialog?.dismiss()
        }

        btnAppInfo.setOnClickListener {
            appRepository.openAppDetails(appInfo.packageName)
            dialog?.dismiss()
        }

        btnUninstall.setOnClickListener {
            appRepository.requestUninstall(appInfo.packageName)
            dialog?.dismiss()
        }

        btnChangeIcon.setOnClickListener {
            dialog?.dismiss()
            onChangeIconRequested(appInfo)
        }

        btnChangeCategory.setOnClickListener {
            dialog?.dismiss()
            showCategorySelectionDialog()
        }

        btnCancel.setOnClickListener {
            dialog?.dismiss()
        }

        dialog = AlertDialog.Builder(context, R.style.TvDialogTheme)
            .setView(view)
            .create()

        dialog?.show()
    }

    /**
     * Ermöglicht dem Nutzer die manuelle Festlegung einer Kategorie für die App.
     */
    private fun showCategorySelectionDialog() {
        val options = arrayOf(
            context.getString(R.string.category_auto),
            context.getString(R.string.category_streaming),
            context.getString(R.string.category_games),
            context.getString(R.string.category_productivity)
        )

        AlertDialog.Builder(context, R.style.TvDialogTheme)
            .setTitle("${context.getString(R.string.action_change_category)}: ${appInfo.label}")
            .setItems(options) { _, which ->
                val newCategory: AppCategory? = when (which) {
                    1 -> AppCategory.STREAMING
                    2 -> AppCategory.GAMES
                    3 -> AppCategory.PRODUCTIVITY
                    else -> null // Auto / Erkannt
                }
                appRepository.setCustomCategory(appInfo.packageName, newCategory)
                Toast.makeText(
                    context,
                    context.getString(R.string.category_saved, appInfo.label),
                    Toast.LENGTH_SHORT
                ).show()
                onDataChanged()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
