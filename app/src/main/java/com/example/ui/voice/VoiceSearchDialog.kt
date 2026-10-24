package com.example.ui.voice

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import coil.load
import com.example.R
import com.example.data.AppRepository
import com.example.data.weather.WeatherRepository
import com.example.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Interaktiver TV-Dialog für die Sprachsuche auf dem Android TV Home-Screen.
 * Führt die Spracherkennung aus, verarbeitet gesprochene Befehle (Apps & Wetter)
 * und stellt Ergebnisse mit voller D-Pad-Bedienung dar.
 */
class VoiceSearchDialog(
    private val context: Context,
    private val appRepository: AppRepository,
    private val weatherRepository: WeatherRepository,
    private val onAppLaunchRequested: (AppInfo) -> Unit
) {

    private val voiceSearchManager = VoiceSearchManager(context, appRepository, weatherRepository)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var dialog: AlertDialog? = null
    private var pulseAnimator: ObjectAnimator? = null

    // UI-Elemente
    private lateinit var voiceOrbContainer: FrameLayout
    private lateinit var ivVoiceMicIcon: ImageView
    private lateinit var tvVoiceStatus: TextView
    private lateinit var tvVoiceTranscript: TextView

    private lateinit var voiceWeatherResultLayout: LinearLayout
    private lateinit var ivVoiceWeatherIcon: ImageView
    private lateinit var tvVoiceWeatherCity: TextView
    private lateinit var tvVoiceWeatherCondition: TextView
    private lateinit var tvVoiceWeatherTemp: TextView

    private lateinit var voiceAppsResultLayout: LinearLayout
    private lateinit var voiceAppListContainer: LinearLayout
    private lateinit var tvVoiceNoResult: TextView
    private lateinit var voiceSuggestionsLayout: LinearLayout

    private lateinit var btnVoiceRetry: Button
    private lateinit var btnVoiceClose: Button

    fun show(initialQuery: String? = null) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_voice_search, null)

        voiceOrbContainer = view.findViewById(R.id.voiceOrbContainer)
        ivVoiceMicIcon = view.findViewById(R.id.ivVoiceMicIcon)
        tvVoiceStatus = view.findViewById(R.id.tvVoiceStatus)
        tvVoiceTranscript = view.findViewById(R.id.tvVoiceTranscript)

        voiceWeatherResultLayout = view.findViewById(R.id.voiceWeatherResultLayout)
        ivVoiceWeatherIcon = view.findViewById(R.id.ivVoiceWeatherIcon)
        tvVoiceWeatherCity = view.findViewById(R.id.tvVoiceWeatherCity)
        tvVoiceWeatherCondition = view.findViewById(R.id.tvVoiceWeatherCondition)
        tvVoiceWeatherTemp = view.findViewById(R.id.tvVoiceWeatherTemp)

        voiceAppsResultLayout = view.findViewById(R.id.voiceAppsResultLayout)
        voiceAppListContainer = view.findViewById(R.id.voiceAppListContainer)
        tvVoiceNoResult = view.findViewById(R.id.tvVoiceNoResult)
        voiceSuggestionsLayout = view.findViewById(R.id.voiceSuggestionsLayout)

        btnVoiceRetry = view.findViewById(R.id.btnVoiceRetry)
        btnVoiceClose = view.findViewById(R.id.btnVoiceClose)

        // Vorschlag-Buttons für schnelles Testen per D-Pad
        view.findViewById<View>(R.id.chipVoiceSuggestWeather).setOnClickListener {
            handleSpokenText("Wetter")
        }
        view.findViewById<View>(R.id.chipVoiceSuggestStreaming).setOnClickListener {
            handleSpokenText("Streaming")
        }
        view.findViewById<View>(R.id.chipVoiceSuggestGames).setOnClickListener {
            handleSpokenText("Spiele")
        }

        btnVoiceRetry.setOnClickListener {
            startListening()
        }

        btnVoiceClose.setOnClickListener {
            dialog?.dismiss()
        }

        dialog = AlertDialog.Builder(context, R.style.TvDialogTheme)
            .setView(view)
            .setOnDismissListener {
                stopPulsing()
                voiceSearchManager.destroy()
            }
            .create()

        dialog?.show()

        if (!initialQuery.isNullOrBlank()) {
            handleSpokenText(initialQuery)
        } else {
            startListening()
        }
    }

    /**
     * Startet die Sprachaufnahme über das Mikrofon und aktiviert die visuelle TV-Animation.
     */
    private fun startListening() {
        resetResultViews()
        tvVoiceStatus.text = context.getString(R.string.voice_search_listening)
        tvVoiceTranscript.text = context.getString(R.string.voice_search_prompt)
        startPulsing()

        voiceSearchManager.startListening(
            onReadyForSpeech = {
                tvVoiceStatus.text = "Ich höre zu… Bitte sprechen"
            },
            onRmsChanged = { rms ->
                // Skaliere Mikrofon-Orb leicht mit Spracheingabe
                val scale = 1.0f + (rms.coerceIn(0f, 10f) / 50f)
                voiceOrbContainer.scaleX = scale
                voiceOrbContainer.scaleY = scale
            },
            onResult = { text ->
                stopPulsing()
                handleSpokenText(text)
            },
            onError = { error ->
                stopPulsing()
                tvVoiceStatus.text = "Sprachsuche"
                tvVoiceTranscript.text = error
                tvVoiceNoResult.text = error
                tvVoiceNoResult.visibility = View.VISIBLE
                btnVoiceRetry.requestFocus()
            }
        )
    }

    /**
     * Verarbeitet den erkannten Sprachtext und präsentiert das passende Ergebnis.
     */
    fun handleSpokenText(query: String) {
        stopPulsing()
        tvVoiceStatus.text = context.getString(R.string.voice_search_processing)
        tvVoiceTranscript.text = "»$query«"

        scope.launch {
            val result = voiceSearchManager.processQuery(query)
            tvVoiceStatus.text = "Ergebnis"
            resetResultViews()

            when (result) {
                is VoiceSearchResult.Weather -> {
                    displayWeatherResult(result)
                }
                is VoiceSearchResult.SingleApp -> {
                    displaySingleAppResult(result.app)
                }
                is VoiceSearchResult.MultipleApps -> {
                    displayMultipleAppsResult(result.apps)
                }
                is VoiceSearchResult.NoMatch -> {
                    tvVoiceNoResult.text = context.getString(R.string.voice_search_no_results, query)
                    tvVoiceNoResult.visibility = View.VISIBLE
                    btnVoiceRetry.requestFocus()
                }
            }
        }
    }

    private fun displayWeatherResult(result: VoiceSearchResult.Weather) {
        val weather = result.weatherInfo
        voiceWeatherResultLayout.visibility = View.VISIBLE

        if (weather.isLoaded) {
            tvVoiceWeatherCity.text = weather.formattedLocation()
            tvVoiceWeatherCondition.text = weather.conditionText
            tvVoiceWeatherTemp.text = weather.formattedTemperature()

            val iconUrl = weather.getIconUrl()
            if (!iconUrl.isNullOrEmpty()) {
                ivVoiceWeatherIcon.load(iconUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_weather_placeholder)
                    error(R.drawable.ic_weather_placeholder)
                }
            } else {
                ivVoiceWeatherIcon.setImageResource(R.drawable.ic_weather_placeholder)
            }
        } else {
            tvVoiceWeatherCity.text = weather.cityName.ifBlank { "Wetter" }
            tvVoiceWeatherCondition.text = weather.errorMessage ?: weather.conditionText
            tvVoiceWeatherTemp.text = "--°C"
            ivVoiceWeatherIcon.setImageResource(R.drawable.ic_weather_placeholder)
        }

        btnVoiceClose.requestFocus()
    }

    private fun displaySingleAppResult(app: AppInfo) {
        voiceAppsResultLayout.visibility = View.VISIBLE
        voiceAppListContainer.removeAllViews()

        val appButton = createAppButton(app) {
            dialog?.dismiss()
            onAppLaunchRequested(app)
        }
        voiceAppListContainer.addView(appButton)

        Toast.makeText(
            context,
            context.getString(R.string.voice_search_app_launching, app.label),
            Toast.LENGTH_SHORT
        ).show()

        appButton.requestFocus()
    }

    private fun displayMultipleAppsResult(apps: List<AppInfo>) {
        voiceAppsResultLayout.visibility = View.VISIBLE
        voiceAppListContainer.removeAllViews()

        apps.forEach { app ->
            val appButton = createAppButton(app) {
                dialog?.dismiss()
                onAppLaunchRequested(app)
            }
            voiceAppListContainer.addView(appButton)
        }

        voiceAppListContainer.getChildAt(0)?.requestFocus()
    }

    private fun createAppButton(app: AppInfo, onClick: () -> Unit): Button {
        return Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            ).apply {
                bottomMargin = dpToPx(8)
            }
            background = ContextCompat.getDrawable(context, R.drawable.bg_dialog_item_selector)
            text = app.label
            setTextColor(ContextCompat.getColor(context, R.color.tv_text_primary))
            textSize = 14f
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), 0, dpToPx(16), 0)
            isFocusable = true
            isFocusableInTouchMode = true

            val drawable = app.getDisplayDrawable()
            if (drawable != null) {
                drawable.setBounds(0, 0, dpToPx(28), dpToPx(28))
                setCompoundDrawables(drawable, null, null, null)
                compoundDrawablePadding = dpToPx(12)
            }

            setOnClickListener {
                onClick()
            }
        }
    }

    private fun resetResultViews() {
        voiceWeatherResultLayout.visibility = View.GONE
        voiceAppsResultLayout.visibility = View.GONE
        tvVoiceNoResult.visibility = View.GONE
        voiceAppListContainer.removeAllViews()
    }

    private fun startPulsing() {
        stopPulsing()
        pulseAnimator = ObjectAnimator.ofFloat(ivVoiceMicIcon, View.ALPHA, 0.4f, 1.0f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulsing() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        ivVoiceMicIcon.alpha = 1.0f
        voiceOrbContainer.scaleX = 1.0f
        voiceOrbContainer.scaleY = 1.0f
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
