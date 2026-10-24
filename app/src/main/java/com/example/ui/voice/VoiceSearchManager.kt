package com.example.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.data.AppRepository
import com.example.data.weather.WeatherRepository
import com.example.model.AppCategory
import com.example.model.AppInfo
import com.example.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Ergebnisarten der Sprachsuche.
 */
sealed class VoiceSearchResult {
    data class Weather(val weatherInfo: WeatherInfo, val query: String) : VoiceSearchResult()
    data class SingleApp(val app: AppInfo, val query: String) : VoiceSearchResult()
    data class MultipleApps(val apps: List<AppInfo>, val query: String) : VoiceSearchResult()
    data class NoMatch(val query: String) : VoiceSearchResult()
}

/**
 * Verwaltet die Spracherkennung über das Mikrofon und analysiert Sprachbefehle
 * zur Steuerung des TV Launchers (z. B. "Wetter anzeigen", "YouTube starten", "Spiele").
 */
class VoiceSearchManager(
    private val context: Context,
    private val appRepository: AppRepository,
    private val weatherRepository: WeatherRepository
) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Prüft, ob ein Spracherkennungsdienst auf dem Android TV Gerät verfügbar ist.
     */
    fun isSpeechRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Erstellt einen Intent für die standardmäßige Sprachsuche auf Android TV.
     */
    fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMANY.toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprechen… z. B. „Wetter“ oder „Netflix“")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    /**
     * Startet die Spracherkennung direkt im Hintergrund über den [SpeechRecognizer].
     */
    fun startListening(
        onReadyForSpeech: () -> Unit,
        onRmsChanged: (Float) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        destroy()

        if (!isSpeechRecognitionAvailable()) {
            onError("Spracherkennungsdienst auf diesem Gerät nicht verfügbar.")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onReadyForSpeech()
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio-Aufnahmefehler"
                            SpeechRecognizer.ERROR_CLIENT -> "Client-Fehler"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon-Berechtigung fehlt"
                            SpeechRecognizer.ERROR_NETWORK -> "Netzwerkfehler bei Spracherkennung"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerk-Timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Keine Sprache erkannt. Bitte erneut versuchen."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Spracherkennung ist belegt"
                            SpeechRecognizer.ERROR_SERVER -> "Server-Fehler"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keine Spracheingabe empfangen"
                            else -> "Fehler bei der Spracherkennung ($error)"
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            onResult(text)
                        } else {
                            onError("Keine Spracheingabe erkannt.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening(createRecognizerIntent())
            }
        } catch (e: Exception) {
            onError("Fehler beim Starten der Spracherkennung: ${e.localizedMessage}")
        }
    }

    /**
     * Verarbeitet den erkannten Sprachtext und ordnet ihn Wetter- oder App-Befehlen zu.
     */
    suspend fun processQuery(rawQuery: String): VoiceSearchResult = withContext(Dispatchers.IO) {
        val query = rawQuery.trim().lowercase()

        // 1. Wetter-Anfragen erkennen
        val weatherKeywords = listOf(
            "wetter", "temperatur", "regen", "sonne", "wie ist das wetter",
            "kalt", "warm", "grad", "weather", "forecast", "forecasts", "vorhersage"
        )
        if (weatherKeywords.any { query.contains(it) }) {
            val weather = weatherRepository.fetchCurrentWeather()
            return@withContext VoiceSearchResult.Weather(weather, rawQuery)
        }

        // 2. Befehlswörter bereinigen ("öffne", "starte", "open", "launch", "starte bitte", "app")
        var cleaned = query
            .replace("öffne bitte", "")
            .replace("starte bitte", "")
            .replace("öffne", "")
            .replace("starte", "")
            .replace("start", "")
            .replace("open", "")
            .replace("launch", "")
            .replace("spiel bitte", "")
            .replace("spiele", "")
            .replace("app", "")
            .trim()

        if (cleaned.isEmpty()) cleaned = query

        val installedApps = appRepository.getInstalledApps()

        // 3. Nach Kategorien suchen ("spiele", "games", "streaming", "produktivität")
        if (query.contains("spiele") || query.contains("games") || query.contains("gaming")) {
            val gameApps = installedApps.filter { it.matchesCategory(AppCategory.GAMES) }
            if (gameApps.isNotEmpty()) {
                return@withContext VoiceSearchResult.MultipleApps(gameApps, rawQuery)
            }
        }
        if (query.contains("streaming") || query.contains("stream") || query.contains("medien") || query.contains("video")) {
            val streamApps = installedApps.filter { it.matchesCategory(AppCategory.STREAMING) }
            if (streamApps.isNotEmpty()) {
                return@withContext VoiceSearchResult.MultipleApps(streamApps, rawQuery)
            }
        }
        if (query.contains("produktivität") || query.contains("tools") || query.contains("werkzeuge")) {
            val prodApps = installedApps.filter { it.matchesCategory(AppCategory.PRODUCTIVITY) }
            if (prodApps.isNotEmpty()) {
                return@withContext VoiceSearchResult.MultipleApps(prodApps, rawQuery)
            }
        }

        // 4. Nach installierten Apps suchen
        // A: Exakte Übereinstimmung mit Label oder Paketname
        val exactMatch = installedApps.firstOrNull {
            it.label.equals(cleaned, ignoreCase = true) ||
                    it.packageName.equals(cleaned, ignoreCase = true)
        }
        if (exactMatch != null) {
            return@withContext VoiceSearchResult.SingleApp(exactMatch, rawQuery)
        }

        // B: Enthält-Suche im Namen oder Paket
        val matchingApps = installedApps.filter { app ->
            val labelLower = app.label.lowercase()
            val pkgLower = app.packageName.lowercase()
            labelLower.contains(cleaned) || cleaned.contains(labelLower) || pkgLower.contains(cleaned)
        }

        return@withContext when {
            matchingApps.size == 1 -> VoiceSearchResult.SingleApp(matchingApps.first(), rawQuery)
            matchingApps.size > 1 -> VoiceSearchResult.MultipleApps(matchingApps, rawQuery)
            else -> VoiceSearchResult.NoMatch(rawQuery)
        }
    }

    /**
     * Gibt Ressourcen des SpeechRecognizers frei.
     */
    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
