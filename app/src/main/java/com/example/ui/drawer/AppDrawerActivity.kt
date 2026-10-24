package com.example.ui.drawer

import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.R
import com.example.model.AppCategory
import com.example.model.AppInfo
import com.example.ui.viewmodel.LauncherViewModel

/**
 * Activity für den App Drawer auf dem Android TV.
 *
 * Beherbergt das [AppDrawerFragment] (Leanback [androidx.leanback.app.VerticalGridSupportFragment])
 * und bietet eine Kategorieleiste (Alle Apps, Favoriten, Streaming, Produktivität)
 * mit vollständiger D-Pad-Navigation.
 */
class AppDrawerActivity : FragmentActivity() {

    private lateinit var viewModel: LauncherViewModel
    private var drawerFragment: AppDrawerFragment? = null
    private var currentTargetAppForIcon: AppInfo? = null

    // UI-Elemente
    private lateinit var btnDrawerBack: FrameLayout
    private lateinit var chipCategoryAll: TextView
    private lateinit var chipCategoryFavorites: TextView
    private lateinit var chipCategoryStreaming: TextView
    private lateinit var chipCategoryGames: TextView
    private lateinit var chipCategoryProductivity: TextView
    private lateinit var tvDrawerAppCount: TextView

    // Bildauswahl zur Änderung des App-Icons
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val target = currentTargetAppForIcon
        if (uri != null && target != null) {
            viewModel.saveCustomIcon(target, uri) { success ->
                if (success) {
                    Toast.makeText(this, "Icon für ${target.label} aktualisiert", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Fehler beim Speichern des Icons", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        viewModel = ViewModelProvider(this)[LauncherViewModel::class.java]

        initViews()
        setupFragment()
        setupCategoryChips()
    }

    private fun initViews() {
        btnDrawerBack = findViewById(R.id.btnDrawerBack)
        chipCategoryAll = findViewById(R.id.chipCategoryAll)
        chipCategoryFavorites = findViewById(R.id.chipCategoryFavorites)
        chipCategoryStreaming = findViewById(R.id.chipCategoryStreaming)
        chipCategoryGames = findViewById(R.id.chipCategoryGames)
        chipCategoryProductivity = findViewById(R.id.chipCategoryProductivity)
        tvDrawerAppCount = findViewById(R.id.tvDrawerAppCount)

        btnDrawerBack.setOnClickListener {
            finish()
        }
    }

    private fun setupFragment() {
        var fragment = supportFragmentManager.findFragmentById(R.id.drawerFragmentContainer) as? AppDrawerFragment
        if (fragment == null) {
            fragment = AppDrawerFragment().apply {
                onAppCountChanged = { count ->
                    tvDrawerAppCount.text = resources.getQuantityString(
                        R.plurals.app_count_plural,
                        count,
                        count
                    )
                }
                onChangeIconRequested = { app ->
                    currentTargetAppForIcon = app
                    pickImageLauncher.launch("image/*")
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.drawerFragmentContainer, fragment)
                .commit()
        }
        drawerFragment = fragment
    }

    private fun setupCategoryChips() {
        val chips = listOf(
            chipCategoryAll to AppCategory.ALL,
            chipCategoryFavorites to AppCategory.FAVORITES,
            chipCategoryStreaming to AppCategory.STREAMING,
            chipCategoryGames to AppCategory.GAMES,
            chipCategoryProductivity to AppCategory.PRODUCTIVITY
        )

        chips.forEach { (chip, category) ->
            chip.setOnClickListener {
                selectCategory(category, chips)
            }
        }

        // Standardmäßig "Alle Apps" auswählen
        chipCategoryAll.isSelected = true
    }

    private fun selectCategory(
        selectedCategory: AppCategory,
        allChips: List<Pair<TextView, AppCategory>>
    ) {
        allChips.forEach { (chip, cat) ->
            chip.isSelected = (cat == selectedCategory)
        }
        drawerFragment?.setCategoryFilter(selectedCategory)
    }
}
