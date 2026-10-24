package com.example.ui.drawer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.data.AppPreferences
import com.example.data.AppRepository
import com.example.model.AppCategory
import com.example.model.AppInfo
import com.example.ui.dialog.AppContextMenuDialog
import com.example.ui.presenter.AppCardPresenter
import com.example.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

/**
 * App Drawer Fragment unter Verwendung von Leanbacks [VerticalGridSupportFragment].
 *
 * Präsentiert alle installierten TV- und Sideload-Anwendungen in einem 5-spaltigen
 * Leanback-Raster mit voller Fernbedienungs-Navigation (D-Pad).
 * Ermöglicht das Filtern nach Kategorien (Favoriten, Streaming, Produktivität)
 * und das direkte Starten von Anwendungen von der Leanback-Oberfläche.
 */
class AppDrawerFragment : VerticalGridSupportFragment() {

    private lateinit var viewModel: LauncherViewModel
    private lateinit var appRepository: AppRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var gridAdapter: ArrayObjectAdapter

    private var currentCategory: AppCategory = AppCategory.ALL
    private var allLoadedApps: List<AppInfo> = emptyList()

    /**
     * Callback, wenn sich die Anzahl der angezeigten Apps ändert (für die Zähleranzeige).
     */
    var onAppCountChanged: ((Int) -> Unit)? = null

    /**
     * Callback, wenn im Kontextmenü ein benutzerdefiniertes Icon ausgewählt werden soll.
     */
    var onChangeIconRequested: ((AppInfo) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[LauncherViewModel::class.java]
        appRepository = AppRepository(requireContext())
        appPreferences = AppPreferences(requireContext())

        setupGrid()
        setupListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadApps()
    }

    /**
     * Initialisiert den Leanback [VerticalGridPresenter] und den Daten-Adapter.
     */
    private fun setupGrid() {
        val gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false).apply {
            numberOfColumns = 6
            shadowEnabled = false
        }
        setGridPresenter(gridPresenter)

        val appCardPresenter = AppCardPresenter { appInfo, _ ->
            showContextMenu(appInfo)
        }
        gridAdapter = ArrayObjectAdapter(appCardPresenter)
        adapter = gridAdapter
    }

    /**
     * Richtet den Klick-Listener zum Starten der ausgewählten App per D-Pad OK ein.
     */
    private fun setupListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is AppInfo) {
                val launched = viewModel.launchApp(item)
                if (launched) {
                    // Nach erfolgreichem Start Activity beenden, damit bei Rückkehr der Home-Screen aktiv ist
                    activity?.finish()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "App ${item.label} konnte nicht gestartet werden",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Beobachtet den AppUiState aus dem [LauncherViewModel].
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                allLoadedApps = state.allApps
                applyFilter(currentCategory)
            }
        }
    }

    /**
     * Ändert den aktuellen Kategorie-Filter (z. B. Favoriten, Streaming, Produktivität).
     */
    fun setCategoryFilter(category: AppCategory) {
        currentCategory = category
        applyFilter(category)
    }

    /**
     * Wendet die Filterung an und aktualisiert das Raster.
     */
    private fun applyFilter(category: AppCategory) {
        val filtered = allLoadedApps.filter { it.matchesCategory(category) }
        gridAdapter.clear()
        filtered.forEach { gridAdapter.add(it) }
        onAppCountChanged?.invoke(filtered.size)
    }

    /**
     * Öffnet das Kontextmenü bei langem Tastendruck auf eine App-Kachel.
     */
    private fun showContextMenu(appInfo: AppInfo) {
        AppContextMenuDialog(
            context = requireContext(),
            appInfo = appInfo,
            appRepository = appRepository,
            appPreferences = appPreferences,
            onDataChanged = {
                viewModel.loadApps()
            },
            onChangeIconRequested = { app ->
                onChangeIconRequested?.invoke(app)
            }
        ).show()
    }
}
