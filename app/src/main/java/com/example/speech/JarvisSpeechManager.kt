package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class JarvisSpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    init {
        initializeTts()
        initializeSpeechRecognizer()
    }

    private fun initializeTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale("tr", "TR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale.getDefault())
                }
                isTtsReady = true
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@JarvisSpeechManager)
                }
            } else {
                Log.e("JarvisSpeechManager", "Speech recognition is not available")
            }
        } catch (e: Exception) {
            Log.e("JarvisSpeechManager", "Failed to init SpeechRecognizer: ${e.message}")
        }
    }

    fun speak(text: String) {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts_id")
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
            onListeningStateChanged(true)
        } catch (e: Exception) {
            onError("Ses tanıma başlatılamadı sör: ${e.localizedMessage}")
            onListeningStateChanged(false)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("JarvisSpeechManager", "Error stopping speech: ${e.message}")
        }
        onListeningStateChanged(false)
    }

    fun shutdown() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    // --- RecognitionListener Callbacks ---
    override fun onReadyForSpeech(params: Bundle?) {
        onListeningStateChanged(true)
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        onListeningStateChanged(false)
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Ses kayıt hatası"
            SpeechRecognizer.ERROR_CLIENT -> "Kanal hatası"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Yetki eksik sör. Lütfen mikrofon izni verin."
            SpeechRecognizer.ERROR_NETWORK -> "Ağ bağlantı hatası"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Ağ zaman aşımı"
            SpeechRecognizer.ERROR_NO_MATCH -> "Eşleşme bulunamadı sör."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ses motoru meşgul"
            SpeechRecognizer.ERROR_SERVER -> "Sunucu hatası"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ses girişi zaman aşımı"
            else -> "Bilinmeyen ses tanıma hatası"
        }
        onError(message)
        onListeningStateChanged(false)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            onResult(matches[0])
        }
        onListeningStateChanged(false)
    }

    override fun onPartialResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            onPartialResult(matches[0])
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
