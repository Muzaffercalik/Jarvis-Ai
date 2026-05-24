package com.example

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.network.GeminiApiService
import com.example.network.JarvisIntentResponse
import com.example.viewmodel.JarvisViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.*

class JarvisFloatingService : Service(), RecognitionListener {

    private lateinit var windowManager: WindowManager
    private var floatingView: FrameLayout? = null
    private var orbView: JarvisOrbView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    private val NOTIFICATION_ID = 1989
    private val CHANNEL_ID = "jarvis_floating_channel"
    
    // Status state of Jarvis Orb
    enum class OrbState {
        IDLE, LISTENING, THINKING, SPEAKING
    }
    private var currentOrbState = OrbState.IDLE

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Sistem hazır sör.", false))
        
        initializeTts()
        initializeSpeechRecognizer()
        showFloatingOrb()
        
        Log.d("JarvisFloating", "Service created and orb shown successfully.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Pro Arka Plan Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Jarvis asistanının arka plan asistan küresini çalıştırır."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String, pulse: Boolean): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Pro Asistan Küresi")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.presence_online)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String, pulse: Boolean = false) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(text, pulse))
    }

    private fun initializeTts() {
        textToSpeech = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.setLanguage(Locale("tr", "TR"))
                isTtsReady = true
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
                    setRecognitionListener(this@JarvisFloatingService)
                }
            }
        } catch (e: Exception) {
            Log.e("JarvisFloating", "Failed to init SpeechRecognizer: ${e.message}")
        }
    }

    private fun showFloatingOrb() {
        floatingView = FrameLayout(this)
        
        val sizePx = (72 * resources.displayMetrics.density).toInt()
        orbView = JarvisOrbView(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        }
        floatingView?.addView(orbView)
        
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // Draggable listener for the floating layout
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var lastX: Int = 0
            private var lastY: Int = 0
            private var firstX: Int = 0
            private var firstY: Int = 0
            private var isMoving = false
            private var touchDownTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.rawX.toInt()
                        lastY = event.rawY.toInt()
                        firstX = params.x
                        firstY = params.y
                        isMoving = false
                        touchDownTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX.toInt() - lastX
                        val deltaY = event.rawY.toInt() - lastY
                        if (Math.abs(deltaX) > 8 || Math.abs(deltaY) > 8) {
                            isMoving = true
                        }
                        params.x = firstX + deltaX
                        params.y = firstY + deltaY
                        // Constrain within screen dimensions
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - touchDownTime
                        if (!isMoving && duration < 300) {
                            // Quick tap triggers speech listening
                            onOrbClicked()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e("JarvisFloating", "Error showing floating window: ${e.message}")
            Toast.makeText(this, "Arayüz üstünde gösterme izni verilmemiş, sör.", Toast.LENGTH_LONG).show()
        }
    }

    private fun onOrbClicked() {
        if (currentOrbState == OrbState.LISTENING) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            speakTts("Sesinizi kaydedebilmek için mikrofon yetkisini uygulamamız üzerinden vermelisiniz sör.")
            return
        }

        try {
            if (speechRecognizer == null) {
                initializeSpeechRecognizer()
            }
            vibrateDevice(60)
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
            setOrbState(OrbState.LISTENING)
            updateNotification("Dinliyorum, Efendim...", true)
        } catch (e: Exception) {
            Log.e("JarvisFloating", "Error in startListening: ${e.message}")
            setOrbState(OrbState.IDLE)
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            setOrbState(OrbState.IDLE)
            updateNotification("Sistem hazır sör.", false)
        } catch (e: Exception) {
            Log.e("JarvisFloating", "Error in stopListening: ${e.message}")
        }
    }

    private fun setOrbState(state: OrbState) {
        currentOrbState = state
        orbView?.updateState(state)
    }

    private fun speakTts(text: String) {
        if (isTtsReady && textToSpeech != null) {
            setOrbState(OrbState.SPEAKING)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "floating_tts_id")
            // Revert state back after speech completes
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                if (currentOrbState == OrbState.SPEAKING) {
                    setOrbState(OrbState.IDLE)
                }
            }, (text.length * 75L).coerceAtLeast(1500L))
        }
    }

    private fun processBackgroundCommand(command: String) {
        setOrbState(OrbState.THINKING)
        updateNotification("Analiz ediliyor sör...", true)
        
        serviceScope.launch {
            try {
                // Instantiating/calling the shared network Gemini implementation dynamically
                val response = com.example.network.JarvisBrain.analyzeCommand(command)
                
                // Speak response
                speakTts(response.explanation)
                updateNotification(response.explanation, false)
                
                // If the main activity is running and has a live logs session, we can also inject log steps!
                // Execute core physical action
                executeDeviceAction(response)
                
            } catch (e: Exception) {
                Log.e("JarvisFloating", "Error processing remote query: ${e.message}")
                speakTts("Asistan servisinde sunucuya bağlanamadım sör.")
                setOrbState(OrbState.IDLE)
            }
        }
    }

    private fun executeDeviceAction(response: JarvisIntentResponse) {
        val action = response.intent
        
        Handler(Looper.getMainLooper()).post {
            when (action) {
                "DEVICE_CONTROL" -> {
                    val ctrl = response.controlAction ?: "VIBRATE"
                    when (ctrl) {
                        "FLASHLIGHT_ON" -> setFlashlight(true)
                        "FLASHLIGHT_OFF" -> setFlashlight(false)
                        "WIFI_ON" -> setWifiEnabled(true)
                        "WIFI_OFF" -> setWifiEnabled(false)
                        "VOLUME_UP" -> adjustVolume(true)
                        "VOLUME_DOWN" -> adjustVolume(false)
                        "VIBRATE" -> vibrateDevice(300)
                        "BLUETOOTH_ON", "BLUETOOTH_OFF", "ALL_PERMISSIONS" -> {
                            vibrateDevice(100)
                        }
                    }
                }
                "SHARE_CONTENT" -> {
                    val platform = response.sharePlatform ?: "WhatsApp"
                    val contentText = response.inputText ?: ""
                    val intent = Intent().apply {
                        setAction(Intent.ACTION_SEND)
                        setType("text/plain")
                        putExtra(Intent.EXTRA_TEXT, contentText)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (platform.contains("whatsapp", ignoreCase = true)) {
                            setPackage("com.whatsapp")
                        } else if (platform.contains("telegram", ignoreCase = true)) {
                            setPackage("org.telegram.messenger")
                        }
                    }
                    try {
                        val chooser = Intent.createChooser(intent, "$platform ile paylaş sör:")
                        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(chooser)
                    } catch (e: Exception) {
                        try {
                            val generalIntent = Intent().apply {
                                setAction(Intent.ACTION_SEND)
                                setType("text/plain")
                                putExtra(Intent.EXTRA_TEXT, contentText)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(generalIntent)
                        } catch (ex: Exception) {
                            Log.e("Jarvis", "Shared fail")
                        }
                    }
                }
                "CROSS_APP_TRANSFER" -> {
                    val text = response.inputText ?: ""
                    val targetApp = response.targetContext ?: "Notlar"
                    
                    try {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Jarvis Veri Aktarımı", text)
                        clipboard.setPrimaryClip(clip)
                        vibrateDevice(150)
                    } catch (e: Exception) {
                        Log.e("Jarvis", "Clipboard failed")
                    }
                    
                    val sendIntent = Intent().apply {
                        setAction(Intent.ACTION_SEND)
                        setType("text/plain")
                        putExtra(Intent.EXTRA_TEXT, text)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        val chooser = Intent.createChooser(sendIntent, "Veriyi $targetApp uygulamasına aktar sör:")
                        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(chooser)
                    } catch (e: Exception) {
                        Log.e("Jarvis", "Transfer failed")
                    }
                }
                "CLICK_COORDINATES" -> {
                    val target = response.clickTarget ?: ""
                    if (JarvisAccessibilityService.isServiceRunning() && target.isNotEmpty()) {
                        val coordRegex = """(\d+)\s*,\s*(\d+)""".toRegex()
                        val match = coordRegex.find(target)
                        if (match != null) {
                            val x = match.groupValues[1].toFloatOrNull()
                            val y = match.groupValues[2].toFloatOrNull()
                            if (x != null && y != null) {
                                JarvisAccessibilityService.clickAtCoordinates(x, y)
                            }
                        } else {
                            JarvisAccessibilityService.clickByText(target)
                        }
                    }
                }
                "OPEN_YOUTUBE" -> {
                    val q = response.searchQuery ?: ""
                    val intent = if (q.isNotEmpty()) {
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=$q"))
                    } else {
                        packageManager.getLaunchIntentForPackage("com.google.android.youtube") ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com"))
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    try { startActivity(intent) } catch (e: Exception) {}
                }
                "SEARCH_GOOGLE" -> {
                    val q = response.searchQuery ?: ""
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=$q")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { startActivity(intent) } catch (e: Exception) {}
                }
            }
        }
    }

    private fun setFlashlight(enabled: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enabled)
                vibrateDevice(100)
            }
        } catch (e: Exception) {
            Log.e("JarvisFloating", "Fener Hatası: ${e.message}")
        }
    }

    private fun setWifiEnabled(enabled: Boolean) {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val panelIntent = Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(panelIntent)
                } else {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = enabled
                }
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (ex: Exception) {
                Log.e("JarvisFloating", "Wifi control error")
            }
        }
    }

    private fun adjustVolume(raise: Boolean) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        } catch (e: Exception) {
            Log.e("JarvisFloating", "Volume Error")
        }
    }

    private fun vibrateDevice(ms: Long) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(ms)
                }
            }
        } catch (e: Exception) {
            Log.e("JarvisFloating", "Vibrator Error")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {}

        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {}
            floatingView = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // --- SpeechListener Callbacks ---
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    
    override fun onEndOfSpeech() {
        setOrbState(OrbState.IDLE)
    }

    override fun onError(error: Int) {
        setOrbState(OrbState.IDLE)
        val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Ses duyulamadı sör"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Zaman aşımı sör"
            else -> "Bağlantı kesildi"
        }
        updateNotification(msg, false)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            processBackgroundCommand(text)
        } else {
            setOrbState(OrbState.IDLE)
        }
    }

    override fun onPartialResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            updateNotification("Alınıyor: $text", true)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}

// Custom Draw Canvas View to depict the futuristic high-tech Jarvis Orb with zero crash risk
class JarvisOrbView(context: Context) : View(context) {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulseScale = 1.0f
    private var spinAngle = 0f
    private var state = JarvisFloatingService.OrbState.IDLE
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val animator = object : Runnable {
        override fun run() {
            spinAngle += 3f
            if (spinAngle >= 360f) spinAngle = 0f
            
            pulseScale = if (state == JarvisFloatingService.OrbState.LISTENING) {
                1.0f + 0.15f * Math.abs(Math.sin(System.currentTimeMillis() / 150.0)).toFloat()
            } else if (state == JarvisFloatingService.OrbState.THINKING) {
                1.0f + 0.08f * Math.abs(Math.sin(System.currentTimeMillis() / 100.0)).toFloat()
            } else if (state == JarvisFloatingService.OrbState.SPEAKING) {
                1.0f + 0.22f * Math.abs(Math.sin(System.currentTimeMillis() / 80.0)).toFloat()
            } else {
                1.0f + 0.05f * Math.sin(System.currentTimeMillis() / 600.0).toFloat()
            }
            
            invalidate()
            mainHandler.postDelayed(this, 20)
        }
    }

    init {
        mainHandler.post(animator)
    }

    fun updateState(newState: JarvisFloatingService.OrbState) {
        this.state = newState
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = Math.min(cx, cy) - 8f
        
        val primeColor = when (state) {
            JarvisFloatingService.OrbState.LISTENING -> Color.parseColor("#EF4444") // Coral Red
            JarvisFloatingService.OrbState.THINKING -> Color.parseColor("#8B5CF6") // Neon Violet
            JarvisFloatingService.OrbState.SPEAKING -> Color.parseColor("#F59E0B") // Amber
            else -> Color.parseColor("#00F0FF") // Futuristic Neon Cyan
        }
        
        val radialColor = when (state) {
            JarvisFloatingService.OrbState.LISTENING -> Color.parseColor("#1B1115")
            JarvisFloatingService.OrbState.THINKING -> Color.parseColor("#121017")
            JarvisFloatingService.OrbState.SPEAKING -> Color.parseColor("#151210")
            else -> Color.parseColor("#0A121F")
        }

        // 1. Draw solid circular visual backgrounds
        val radGrad = RadialGradient(cx, cy, baseRadius * pulseScale, primeColor, radialColor, Shader.TileMode.CLAMP)
        glowPaint.shader = radGrad
        glowPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, baseRadius * pulseScale, glowPaint)
        
        // 2. Clear shader for vector elements
        glowPaint.shader = null
        
        // 3. Draw outer dotted orbital
        linePaint.color = primeColor
        linePaint.strokeWidth = 3f
        linePaint.style = Paint.Style.STROKE
        linePaint.pathEffect = DashPathEffect(floatArrayOf(5f, 10f), 0f)
        
        canvas.save()
        canvas.rotate(spinAngle, cx, cy)
        canvas.drawCircle(cx, cy, baseRadius * pulseScale * 0.9f, linePaint)
        canvas.restore()
        
        // 4. Draw core visual
        linePaint.pathEffect = null
        linePaint.style = Paint.Style.FILL
        linePaint.color = primeColor
        canvas.drawCircle(cx, cy, baseRadius * 0.35f, linePaint)
        
        // 5. Draw simple microphone vector inside center core
        linePaint.color = Color.BLACK
        linePaint.strokeWidth = 4f
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeCap = Paint.Cap.ROUND
        
        // Mic capsule vertical slot
        canvas.drawLine(cx, cy - 8f, cx, cy + 4f, linePaint)
        // Mic base basket arc
        val rect = RectF(cx - 7f, cy - 3f, cx + 7f, cy + 7f)
        canvas.drawArc(rect, 0f, 180f, false, linePaint)
        // Stand leg
        canvas.drawLine(cx, cy + 7f, cx, cy + 12f, linePaint)
        canvas.drawLine(cx - 6f, cy + 12f, cx + 6f, cy + 12f, linePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(animator)
    }
}
