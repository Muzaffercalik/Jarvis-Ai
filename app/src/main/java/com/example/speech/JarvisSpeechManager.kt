package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        mainHandler.post {
            initializeTts()
            initializeSpeechRecognizer()
        }
    }

    private fun initializeTts() {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
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
            if (SpeechRecognizer.isRecognitionAvailable(context.applicationContext)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(this@JarvisSpeechManager)
                }
            } else {
                Log.e("JarvisSpeechManager", "Speech recognition is not available")
            }
        } catch (e: Throwable) {
            Log.e("JarvisSpeechManager", "Failed to init SpeechRecognizer: ${e.message}")
        }
    }

    fun speak(text: String) {
        mainHandler.post {
            if (isTtsReady && textToSpeech != null) {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts_id")
            }
        }
    }

    fun startListening() {
        mainHandler.post {
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
            } catch (e: Throwable) {
                onError("Ses tanıma başlatılamadı sör: ${e.localizedMessage}")
                onListeningStateChanged(false)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Throwable) {
                Log.e("JarvisSpeechManager", "Error stopping speech: ${e.message}")
            }
            onListeningStateChanged(false)
        }
    }

    fun shutdown() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                textToSpeech = null
            } catch (e: Throwable) {
                Log.e("JarvisSpeechManager", "Error during shutdown: ${e.message}")
            }
        }
    }

    // --- RecognitionListener Callbacks ---
    override fun onReadyForSpeech(params: Bundle?) {
        mainHandler.post {
            onListeningStateChanged(true)
        }
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        mainHandler.post {
            onListeningStateChanged(false)
        }
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
        mainHandler.post {
            onError(message)
            onListeningStateChanged(false)
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val resultText = matches?.firstOrNull() ?: ""
        mainHandler.post {
            if (resultText.isNotEmpty()) {
                onResult(resultText)
            }
            onListeningStateChanged(false)
        }
    }

    override fun onPartialResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val resultText = matches?.firstOrNull() ?: ""
        mainHandler.post {
            if (resultText.isNotEmpty()) {
                onPartialResult(resultText)
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
