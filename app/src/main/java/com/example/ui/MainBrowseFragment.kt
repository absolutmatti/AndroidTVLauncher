package com.example.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.RowPresenter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.AppPreferences
import com.example.data.AppRepository
import com.example.model.AppInfo
import com.example.ui.dialog.AppContextMenuDialog
import com.example.ui.presenter.AppCardPresenter
import com.example.ui.settings.SettingsActivity
import com.example.ui.viewmodel.LauncherUiState
import com.example.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

class MainBrowseFragment : RowsSupportFragment() {

    private lateinit var viewModel: LauncherViewModel
    private lateinit var appRepository: AppRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var rowsAdapter: ArrayObjectAdapter

    var onChangeIconRequested: ((AppInfo) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[LauncherViewModel::class.java]
        appRepository = AppRepository(requireContext())
        appPreferences = AppPreferences(requireContext())

        setupAdapter()
        setupListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Geändert auf 70f, damit die Kacheln weiter hoch kommen und nicht abgeschnitten werden
        verticalGridView?.apply {
            windowAlignment = VerticalGridView.WINDOW_ALIGN_NO_EDGE
            windowAlignmentOffsetPercent = 70f
            windowAlignmentOffset = 0
            itemAlignmentOffsetPercent = 0f
            clipToPadding = false
            clipChildren = false // Sorgt dafür, dass überstehende Schatten nicht abgeschnitten werden
        }
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadApps()
    }

    private fun setupAdapter() {
        val listRowPresenter = object : ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE) {
            override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
                super.onBindRowViewHolder(holder, item)
                val listRowViewHolder = holder as? ListRowPresenter.ViewHolder ?: return
                val listRow = item as? ListRow ?: return

                listRowViewHolder.headerViewHolder?.view?.visibility = View.GONE

                val rowIndex = rowsAdapter.indexOf(listRow)

                // Grid zentrieren und jegliches horizontales Verschieben/Scrollen vollständig sperren
                listRowViewHolder.gridView.apply {
                    isScrollEnabled = false
                    windowAlignment = HorizontalGridView.WINDOW_ALIGN_NO_EDGE
                    windowAlignmentOffsetPercent = 0f
                    itemAlignmentOffsetPercent = HorizontalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED
                    clipChildren = false
                    clipToPadding = false

                    val lp = layoutParams
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    if (lp is LinearLayout.LayoutParams) {
                        lp.gravity = Gravity.CENTER_HORIZONTAL
                    } else if (lp is FrameLayout.LayoutParams) {
                        lp.gravity = Gravity.CENTER_HORIZONTAL
                    }
                    layoutParams = lp

                    if (rowIndex == 0) {
                        // Kapsel um die erste Zeile (Favoriten)
                        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_favorites_panel)
                        setPadding(32, 16, 32, 24)
                    } else {
                        // Alle weiteren Zeilen (Grid ohne Hintergrund)
                        background = null
                        setPadding(12, 8, 12, 8)
                    }
                }

                (listRowViewHolder.view as? ViewGroup)?.apply {
                    clipChildren = false
                    clipToPadding = false
                    background = null
                }
            }
        }.apply {
            shadowEnabled = false
            selectEffectEnabled = false
        }

        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        adapter = rowsAdapter
    }

    private fun setupListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is AppInfo) {
                when (item.packageName) {
                    "action.app_drawer" -> startActivity(Intent(requireContext(), com.example.ui.drawer.AppDrawerActivity::class.java))
                    "action.settings" -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    else -> viewModel.launchApp(item)
                }
            }
        }

        onItemViewSelectedListener = OnItemViewSelectedListener { _, _, _, row ->
            if (row is ListRow) {
                val rowIndex = rowsAdapter.indexOf(row)
                if (rowIndex >= 0) {
                    appPreferences.setLastFocusedRow(rowIndex)

                    if (rowIndex == 0) {
                        // Startscreen (Wieder nach unten/Mitte scrollen, jetzt auf 70f!)
                        verticalGridView?.windowAlignmentOffsetPercent = 70f
                    } else {
                        // App Grid Scrolling (Wenn wir runter gehen)
                        verticalGridView?.windowAlignmentOffsetPercent = 35f
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderState(state) }
        }
    }

    private fun renderState(state: LauncherUiState) {
        rowsAdapter.clear()
        val appCardPresenter = AppCardPresenter { appInfo, _ -> showContextMenu(appInfo) }

        val favApps = if (state.favoriteApps.isNotEmpty()) state.favoriteApps.take(6) else state.allApps.take(6)
        if (favApps.isNotEmpty()) {
            val favAdapter = ArrayObjectAdapter(appCardPresenter)
            favApps.forEach { favAdapter.add(it) }
            rowsAdapter.add(ListRow(favAdapter))
        }

        val favPackageNames = favApps.map { it.packageName }.toSet()
        val remainingApps = state.allApps.filter { it.packageName !in favPackageNames }.toMutableList()
        val chunks = remainingApps.chunked(6)

        chunks.forEach { chunk ->
            val rowAdapter = ArrayObjectAdapter(appCardPresenter)
            chunk.forEach { rowAdapter.add(it) }
            rowsAdapter.add(ListRow(rowAdapter))
        }

        resetToHomePosition()
    }

    fun resetToHomePosition() {
        if (rowsAdapter.size() > 0) {
            setSelectedPosition(0)
            verticalGridView?.windowAlignmentOffsetPercent = 70f
        }
    }

    private fun showContextMenu(appInfo: AppInfo) {
        if (appInfo.packageName == "action.settings" || appInfo.packageName == "action.app_drawer") return
        AppContextMenuDialog(
            requireContext(), appInfo, appRepository, appPreferences,
            { viewModel.loadApps() }, { onChangeIconRequested?.invoke(it) }
        ).show()
    }
}