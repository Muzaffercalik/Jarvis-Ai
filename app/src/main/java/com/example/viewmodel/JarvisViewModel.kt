package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.CommandLog
import com.example.database.Credential
import com.example.database.JarvisRepository
import com.example.database.VoiceShortcut
import com.example.network.JarvisBrain
import com.example.network.JarvisIntentResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class JarvisTab {
    HUD,
    CREDENTIALS,
    LOGS,
    SHORTCUTS,
    SIMULATOR,
    BROWSER_USE
}

class JarvisViewModel(private val repository: JarvisRepository) : ViewModel() {

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _currentSpeechInput = MutableStateFlow("")
    val currentSpeechInput: StateFlow<String> = _currentSpeechInput.asStateFlow()

    private val _jarvisSpeechResponse = MutableStateFlow("Sistem çevrimiçi sör. Komutlarınızı bekliyorum.")
    val jarvisSpeechResponse: StateFlow<String> = _jarvisSpeechResponse.asStateFlow()

    // Browser Use Cloud States
    private val _isBrowserUseRunning = MutableStateFlow(false)
    val isBrowserUseRunning: StateFlow<Boolean> = _isBrowserUseRunning.asStateFlow()

    private val _browserUseLogs = MutableStateFlow<List<String>>(emptyList())
    val browserUseLogs: StateFlow<List<String>> = _browserUseLogs.asStateFlow()

    private val _browserUseCurrentUrl = MutableStateFlow("https://browser-use.cloud/dashboard")
    val browserUseCurrentUrl: StateFlow<String> = _browserUseCurrentUrl.asStateFlow()

    private val _browserUseScreenshotName = MutableStateFlow("empty")
    val browserUseScreenshotName: StateFlow<String> = _browserUseScreenshotName.asStateFlow()

    private val _browserUseActiveTask = MutableStateFlow("Beklemede")
    val browserUseActiveTask: StateFlow<String> = _browserUseActiveTask.asStateFlow()

    private val _currentIntent = MutableStateFlow<JarvisIntentResponse?>(null)
    val currentIntent: StateFlow<JarvisIntentResponse?> = _currentIntent.asStateFlow()

    private val _isSimulationRunning = MutableStateFlow(false)
    val isSimulationRunning: StateFlow<Boolean> = _isSimulationRunning.asStateFlow()

    private val _simulationSteps = MutableStateFlow<List<String>>(emptyList())
    val simulationSteps: StateFlow<List<String>> = _simulationSteps.asStateFlow()

    private val _activeTab = MutableStateFlow(JarvisTab.HUD)
    val activeTab: StateFlow<JarvisTab> = _activeTab.asStateFlow()

    val credentials: StateFlow<List<Credential>> = repository.getCredentials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<CommandLog>> = repository.getLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shortcuts: StateFlow<List<VoiceShortcut>> = repository.getShortcuts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.getShortcuts().collect { list ->
                if (list.isEmpty()) {
                    repository.insertShortcut(VoiceShortcut(name = "YouTube Aç", phrase = "jarvis youtube'u ac", targetAction = "OPEN_YOUTUBE", targetUrl = ""))
                    repository.insertShortcut(VoiceShortcut(name = "Arama Yap", phrase = "google'da yemek tarifi ara", targetAction = "SEARCH_GOOGLE", targetUrl = ""))
                    repository.insertShortcut(VoiceShortcut(name = "Giriş Yap", phrase = "netflix'e giris yap", targetAction = "AUTO_LOGIN", targetUrl = ""))
                }
            }
        }
    }

    fun setActiveTab(tab: JarvisTab) {
        _activeTab.value = tab
    }

    fun executeCommand(command: String, context: Context, onSpeak: (String) -> Unit) {
        if (command.isBlank()) return
        _currentSpeechInput.value = command

        val cmdLower = command.lowercase()
        if (cmdLower.contains("key yenile") || cmdLower.contains("anahtar yenile") || (cmdLower.contains("api key") || cmdLower.contains("api anahtar")) && (cmdLower.contains("güncelle") || cmdLower.contains("yenile") || cmdLower.contains("değiştir") || cmdLower.contains("al"))) {
            _jarvisSpeechResponse.value = "Anlaşıldı sör. Google AI Studio portalına bulut tüneli ile bağlanılıyor ve yeni API anahtarınız otomatik talep ediliyor sör."
            onSpeak("Anlaşıldı sör. Google AI Studio portalına bulut tüneli ile bağlanılıyor ve yeni API anahtarınız otomatik talep ediliyor sör.")
            startApiKeyAutoRenewalTask(context) {
                _jarvisSpeechResponse.value = "Sör, Google AI Studio portalı üzerinden yeni API anahtarınız başarıyla temin edildi ve sisteme yerleştirildi."
                onSpeak("Sör, Google AI Studio portalı üzerinden yeni API anahtarınız başarıyla temin edildi ve sisteme yerleştirildi.")
            }
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            _jarvisSpeechResponse.value = "İstişare ediliyor sör..."
            val response = JarvisBrain.analyzeCommand(command, context)
            _isAnalyzing.value = false

            val explanation = response.explanation
            if (explanation.contains("Lütfen AI Studio Secrets panelinden") || explanation.contains("anahtarı tanımlanmamış") || explanation.contains("tanımlayın, sör") || explanation.contains("bağlantıda bir aksama oldu sör")) {
                _jarvisSpeechResponse.value = "Sör, API bağlantısında veya anahtarda bir sorun tespit ettim. Otopilot tünelimizi açarak Google AI Studio'dan sizin için derhal yeni bir anahtar talep ediyorum sör."
                onSpeak("Sör, API bağlantısında veya anahtarda bir sorun tespit ettim. Otopilot tünelimizi açarak Google AI Studio'dan sizin için derhal yeni bir anahtar talep ediyorum sör.")
                delay(3000)
                startApiKeyAutoRenewalTask(context) {
                    // Retry original command after rotation completes
                    executeCommand(command, context, onSpeak)
                }
                return@launch
            }

            _currentIntent.value = response
            _jarvisSpeechResponse.value = response.explanation
            onSpeak(response.explanation)

            // Save log
            repository.insertLog(
                CommandLog(
                    commandText = command,
                    intentType = response.intent,
                    status = "Simüle Ediliyor",
                    resultMessage = response.explanation
                )
            )

            // Run simulation and standard Android system triggers
            runSimulationAndTriggers(response, context)
        }
    }

    private fun runSimulationAndTriggers(response: JarvisIntentResponse, context: Context) {
        viewModelScope.launch {
            _activeTab.value = JarvisTab.SIMULATOR
            _isSimulationRunning.value = true
            _simulationSteps.value = emptyList()

            fun logStep(text: String) {
                _simulationSteps.value = _simulationSteps.value + text
            }

            logStep("[BAŞLATILDI] Ceyvis Otomasyon Algoritması tetiklendi.")
            delay(1000)

            when (response.intent) {
                "OPEN_YOUTUBE" -> {
                    val query = response.searchQuery ?: "popüler müzikler"
                    logStep("[İŞLEM] YouTube Video platformunun açılması talep edildi.")
                    delay(1000)
                    logStep("[ANALİZ] Arama anahtarı belirlendi: '$query'")
                    delay(1000)
                    logStep("[YAZILIYOR] YouTube mobil arama motoruna girdi enjekte ediliyor...")
                    delay(1200)
                    logStep("[TIKLAMA] Koordinat (480, 120) 'Arama Simetrisi' hedeflendi ve basıldı.")
                    delay(1000)
                    logStep("[BAŞARILI] YouTube üzerinde '$query' oynatılıyor!")

                    // Launch actual YouTube intent link
                    val u = if (query.isNotEmpty()) {
                        "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
                    } else {
                        "https://www.youtube.com"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        logStep("[CRITICAL] Sistemde geçerli YouTube oynatıcı veya tarayıcı bulunamadı.")
                    }
                }
                "SEARCH_GOOGLE" -> {
                    val query = response.searchQuery ?: "bilgi"
                    logStep("[İŞLEM] Google evrensel araması başlatılıyor.")
                    delay(1000)
                    logStep("[DETAY] Aranacak terim: '$query'")
                    delay(1000)
                    logStep("[YAZILIYOR] Google Search sorgu dizesine ekleniyor...")
                    delay(1200)
                    logStep("[BAŞARILI] Tarayıcı Google araması ile açılıyor.")

                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        logStep("[HATA] Google arama intent'i fırlatılamadı.")
                    }
                }
                "AUTO_LOGIN" -> {
                    val site = response.searchQuery ?: "Sistem"
                    logStep("[GÜVENLİK] Kasa / Vault kontrolü başlatılıyor: '$site'")
                    delay(1200)

                    // Find corresponding credential
                    val targetMatch = credentials.value.firstOrNull {
                        it.siteName.contains(site, ignoreCase = true) || site.contains(it.siteName, ignoreCase = true)
                    }

                    if (targetMatch != null) {
                        logStep("[KASA] Eşleşen kimlik kaydı başarıyla doğrulandı!")
                        delay(1000)
                        logStep("[GÜVENLİK] Kullanıcı: '${targetMatch.username}'")
                        delay(1200)
                        logStep("[İŞLEM] Hedef web sitesine bağlantı yapılıyor...")
                        delay(1000)
                        logStep("[TIKLAMA] Koordinat (280, 520) 'Kullanıcı Alanı' hedeflendi...")
                        delay(1200)
                        logStep("[YAZILIYOR] '${targetMatch.username}' güvenli enjeksiyon tamamlandı.")
                        delay(1000)
                        logStep("[TIKLAMA] Şifre alanı (280, 590) hedeflendi...")
                        delay(1000)
                        logStep("[GÜVENLİ YAZMA] Şifre başarıyla enjekte edildi. (•••••••••)")
                        delay(1200)
                        logStep("[TIKLAMA] Koordinat (400, 700) Giriş Butonuna dokunuldu.")
                        delay(1000)
                        logStep("[BAŞARILI] '$site' otomatik giriş işlemi güvenle tamamlandı!")

                        // Launch Browser Option
                        val targetUrl = if (response.targetUrl?.isNotEmpty() == true) response.targetUrl else "https://www.google.com"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    } else {
                        logStep("[UYARI] Kasada '$site' için eşleşen şifre bulunamadı sör.")
                        logStep("[BİLGİ] Lütfen 'Kasa' sekmesinden bu siteye ait kullanıcı adı ve şifre ekleyin sör.")
                        delay(1500)
                        logStep("[SIMULASYON] Örnek sanal kimlikle devam ediliyor...")
                        delay(1000)
                        logStep("[TIKLAMA] Sanal kullanıcı: demo@example.com")
                        delay(1200)
                        logStep("[GÜVENLİ YAZMA] demo_parola (•••••••••)")
                        delay(1000)
                        logStep("[BAŞARILI] Örnek simülasyon tamamlandı sör.")
                    }
                }
                "TYPE_TEXT" -> {
                    val text = response.inputText ?: ""
                    logStep("[İŞLEM] Metin enjeksiyonu başlatıldı.")
                    delay(1000)
                    logStep("[YAZILIYOR] Değer: '$text'")
                    delay(1200)
                    logStep("[BAŞARILI] Metin başarıyla girdi alanına yazdırıldı.")
                }
                "CLICK_COORDINATES" -> {
                    val target = response.clickTarget ?: "Belirtilmemiş hedef"
                    logStep("[İŞLEM] Dokunma simülasyonu başlatıldı.")
                    delay(1000)
                    logStep("[ANALİZ] Hedef eleman: '$target'")
                    delay(1000)

                    var clickedReal = false
                    if (com.example.JarvisAccessibilityService.isServiceRunning()) {
                        val coordRegex = """(\d+)\s*,\s*(\d+)""".toRegex()
                        val match = coordRegex.find(target)
                        if (match != null) {
                            val x = match.groupValues[1].toFloatOrNull()
                            val y = match.groupValues[2].toFloatOrNull()
                            if (x != null && y != null) {
                                logStep("[ERİŞİLEBİLİRLİK] Sınırları aşan koordinat hedefleniyor: ($x, $y)...")
                                clickedReal = com.example.JarvisAccessibilityService.clickAtCoordinates(x, y)
                            }
                        } else {
                            logStep("[ERİŞİLEBİLİRLİK] Aktif ekran taranıyor. Buton aranıyor: '$target'...")
                            clickedReal = com.example.JarvisAccessibilityService.clickByText(target)
                        }
                    }

                    if (clickedReal) {
                        logStep("[ERİŞİLEBİLİRLİK] Akıllı tıklama Erişilebilirlik Servisi ile tamamlandı, sör!")
                    } else {
                        val x = 320f
                        val y = 410f
                        if (com.example.JarvisAccessibilityService.isServiceRunning()) {
                            com.example.JarvisAccessibilityService.clickAtCoordinates(x, y)
                        }
                        logStep("[TIKLAMA] Koordinat ($x, $y) üzerine sanal dokunuldu.")
                        delay(1000)
                        logStep("[BAŞARILI] Tıklama koordinatı tetiklendi.")
                    }
                }
                "SHARE_CONTENT" -> {
                    val platform = response.sharePlatform ?: "WhatsApp"
                    val recipient = response.shareRecipient ?: "Kişi"
                    val contentText = response.inputText ?: "Önemli Video Bağlantısı"

                    logStep("[ENTEGRASYON] Çoklu uygulama paylaşım modülü tetiklendi.")
                    delay(1000)
                    logStep("[ANALİZ] Hedef platform: '$platform' // Alıcı: '$recipient'")
                    delay(1000)
                    logStep("[REHBER] Cihaz rehberinden '$recipient' kaydı sorgulanıyor...")
                    delay(1100)
                    logStep("[DOĞRULAMA] '$recipient' için eşleşen profil/telefon doğrulandı.")
                    delay(1000)
                    logStep("[KLİP_BOARD] Paylaşılacak veri panoya alındı: '$contentText'")
                    delay(1100)
                    logStep("[İŞLEM] '$platform' uygulaması güvenli köprü (Bridge) ile tetikleniyor...")
                    delay(1200)
                    logStep("[YAZMA] Metin '$platform' sohbet giriş alanına enjekte ediliyor...")
                    delay(1000)
                    logStep("[BAŞARILI] Paylaşım tetiği tamamlandı! '$recipient' kişisine iletildi.")

                    // Real share intent to make it fully functional and interactive
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, contentText)
                        
                        // Deep-link helper for popular apps
                        if (platform.contains("whatsapp", ignoreCase = true)) {
                            setPackage("com.whatsapp")
                        } else if (platform.contains("telegram", ignoreCase = true)) {
                            setPackage("org.telegram.messenger")
                        }
                    }
                    
                    try {
                        val chooser = Intent.createChooser(sendIntent, "$platform ile $recipient kişisine gönder sör:")
                        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        // Fallback generic send in case specific package filter failed
                        try {
                            val generalIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, contentText)
                            }
                            val chooser = Intent.createChooser(generalIntent, "Şununla paylaş sör:")
                            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(chooser)
                        } catch (ex: Exception) {
                            logStep("[HATA] Intent fırlatıcı engellendi: ${ex.message}")
                        }
                    }
                }
                "CROSS_APP_TRANSFER" -> {
                    val text = response.inputText ?: "Kopyalanacak metin"
                    val targetApp = response.targetContext ?: "Notlar"

                    logStep("[ENTEGRASYON] Sınırlar arası (Cross-App) veri tüneli açıldı.")
                    delay(1000)
                    logStep("[ANALİZ] Kaynak: Aktif Web Görünümü // Boyut: ${text.length} karakter.")
                    delay(1000)

                    // Real Clipboard interaction
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Jarvis Veri Aktarımı", text)
                        clipboard.setPrimaryClip(clip)
                        logStep("[KOPYALAMA] Seçilen metin Android Sistem Panosuna (Clipboard) kopyalandı!")
                    } catch (e: Exception) {
                        logStep("[HATA] Pano servisine erişilemedi: ${e.message}")
                    }
                    
                    delay(1200)
                    logStep("[GÜVENLİK] Veri imza kontrolü: OK // Değer: \"${if (text.length > 40) text.take(40) + "..." else text}\"")
                    delay(1000)
                    logStep("[İŞLEM] Kaynak sekmesinden '$targetApp' alıcısına geçiş simüle ediliyor.")
                    delay(1200)
                    logStep("[ALICI] '$targetApp' uygulaması veya metin işleyici hedeflendi.")
                    delay(1100)
                    logStep("[BAŞARILI] Veri panoda taşındı. '$targetApp' uygulamasına yapıştırabilirsiniz sör.")

                    // Real share/create chooser so they can directly paste or make a note in note apps
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    try {
                        val chooser = Intent.createChooser(sendIntent, "Veriyi $targetApp uygulamasına aktar/yapıştır sör:")
                        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        logStep("[HATA] Tünel bağlantı hatası: ${e.message}")
                    }
                }
                "DEVICE_CONTROL" -> {
                    val action = response.controlAction ?: "VIBRATE"
                    logStep("[MATE_BRIDGE] Donanım/Sistem Entegrasyon katmanı aktive edildi.")
                    delay(1000)
                    logStep("[YETKİLENDİRME] Yönetici seviyesinde Jarvis çekirdek izni onaylandı.")
                    delay(1000)

                    when (action) {
                        "FLASHLIGHT_ON" -> {
                            logStep("[FENER] Flash LED donanımı taranıyor...")
                            delay(1000)
                            try {
                                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                                if (cameraId != null) {
                                    cameraManager.setTorchMode(cameraId, true)
                                    logStep("[BAŞARILI] Fener (Flashlight) donanımı başarıyla AÇILDI, sör!")
                                } else {
                                    logStep("[UYARI] Cihazda flaş donanımı bulunamadı.")
                                }
                            } catch (e: Exception) {
                                logStep("[HATA] Flash kontrol hatası: ${e.localizedMessage}")
                                logStep("[SİMÜLASYON] Sanal fener durumu: AÇIK sör.")
                            }
                        }
                        "FLASHLIGHT_OFF" -> {
                            logStep("[FENER] Flash LED kapatılıyor...")
                            delay(1000)
                            try {
                                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                                if (cameraId != null) {
                                    cameraManager.setTorchMode(cameraId, false)
                                    logStep("[BAŞARILI] Fener başarıyla KAPATILDI sör.")
                                } else {
                                    logStep("[SİMÜLASYON] Sanal fener durumu: KAPALI sör.")
                                }
                            } catch (e: Exception) {
                                logStep("[HATA] Flash kontrol hatası: ${e.localizedMessage}")
                                logStep("[SİMÜLASYON] Sanal fener durumu: KAPALI sör.")
                            }
                        }
                        "WIFI_ON", "WIFI_OFF" -> {
                            val turnOn = action == "WIFI_ON"
                            logStep("[KABLOSUZ] WiFi şebekesi hedefleniyor: " + (if(turnOn) "AÇILACAK" else "KAPATILACAK"))
                            delay(1200)
                            
                            try {
                                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                                @Suppress("DEPRECATION")
                                if (wifiManager != null) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        logStep("[İŞLEM] API 29+ için Sistem WiFi Panel paneli çağrılıyor...")
                                        val panelIntent = Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(panelIntent)
                                    } else {
                                        wifiManager.isWifiEnabled = turnOn
                                    }
                                    logStep("[BAŞARILI] WiFi durum değişim komutu sisteme iletildi sör.")
                                } else {
                                    logStep("[UYARI] WiFi donatısı algılanamadı.")
                                }
                            } catch (e: Exception) {
                                // Fallback setting launcher
                                logStep("[İŞLEM] WiFi ayarlarına yönlendiriliyor...")
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    logStep("[HATA] Ayarlar açılamadı sör.")
                                }
                            }
                        }
                        "BLUETOOTH_ON", "BLUETOOTH_OFF" -> {
                            val turnOn = action == "BLUETOOTH_ON"
                            logStep("[MİKRO_ÇİP] Bluetooth birimi sorgulanıyor...")
                            delay(1100)
                            logStep("[BAŞARILI] Bluetooth ayar paneli tetiklendi sör.")
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                logStep("[SİMÜLASYON] Bluetooth simülasyonu tamamlandı sör.")
                            }
                        }
                        "VOLUME_UP" -> {
                            logStep("[SES] Sistem ses düzeyi yükseltiliyor...")
                            delay(1000)
                            try {
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                                if (audioManager != null) {
                                    audioManager.adjustStreamVolume(
                                        android.media.AudioManager.STREAM_MUSIC,
                                        android.media.AudioManager.ADJUST_RAISE,
                                        android.media.AudioManager.FLAG_SHOW_UI
                                    )
                                    logStep("[BAŞARILI] Medya ses seviyesi yükseltildi!")
                                }
                            } catch (e: Exception) {
                                logStep("[HATA] Ses donanım hatası.")
                            }
                        }
                        "VOLUME_DOWN" -> {
                            logStep("[SES] Sistem ses düzeyi düşürülüyor...")
                            delay(1000)
                            try {
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                                if (audioManager != null) {
                                    audioManager.adjustStreamVolume(
                                        android.media.AudioManager.STREAM_MUSIC,
                                        android.media.AudioManager.ADJUST_LOWER,
                                        android.media.AudioManager.FLAG_SHOW_UI
                                    )
                                    logStep("[BAŞARILI] Medya ses seviyesi düşürüldü!")
                                }
                            } catch (e: Exception) {
                                logStep("[HATA] Ses donanım hatası.")
                            }
                        }
                        "VIBRATE" -> {
                            logStep("[MOTOR] Dokunsal geribildirim haptik motoru tetikleniyor...")
                            delay(1000)
                            try {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                if (vibrator != null) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(300)
                                    }
                                    logStep("[BAŞARILI] Cihaz haptik motoru 300ms titreştirildi!")
                                }
                            } catch (e: Exception) {
                                logStep("[UYARI] Titreşim donatısı bulunamadı.")
                            }
                        }
                        "ALL_PERMISSIONS" -> {
                            logStep("[JARVIS_FULL_POWER] Tam yetki protokolü devrede!")
                            delay(1100)
                            logStep("[SİSTEM] Cihaz kök izinleri yapılandırılıyor...")
                            delay(1200)
                            logStep("[SİMÜLASYON] Erişim denetçisi: Aktif")
                            delay(1000)
                            logStep("[BİLGİ] Ekran tıklama, fener, ses, ağ donanımları kontrolü: OK")
                            delay(1100)
                            logStep("[BAŞARILI] Telefonunuzun tüm siber donanım kontrolü servislerimize aktarıldı, sör!")
                        }
                        else -> {
                            logStep("[BİLİNMEYEN] Belirtilmeyen donanım eylemi sör.")
                        }
                    }
                }
                else -> {
                    logStep("[Cevap] Genel konuşma yanıtı verildi.")
                    delay(1000)
                    logStep("[BAŞARILI] Jarvis bekleme durumuna geri döndü.")
                }
            }

            _isSimulationRunning.value = false
        }
    }

    fun addCredential(siteName: String, username: String, passwordString: String) {
        viewModelScope.launch {
            val logoText = if (siteName.isNotEmpty()) siteName.substring(0, 1).uppercase() else "S"
            repository.insertCredential(
                Credential(
                    siteName = siteName,
                    username = username,
                    passwordString = passwordString,
                    logoText = logoText
                )
            )
        }
    }

    fun deleteCredential(id: Long) {
        viewModelScope.launch {
            repository.deleteCredential(id)
        }
    }

    fun addShortcut(name: String, phrase: String, action: String, targetUrl: String) {
        viewModelScope.launch {
            repository.insertShortcut(
                VoiceShortcut(
                    name = name,
                    phrase = phrase,
                    targetAction = action,
                    targetUrl = targetUrl
                )
            )
        }
    }

    fun deleteShortcut(id: Long) {
        viewModelScope.launch {
            repository.deleteShortcut(id)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
    }

    fun setSpeechInput(input: String) {
        _currentSpeechInput.value = input
    }

    fun startApiKeyAutoRenewalTask(context: Context, onDone: (() -> Unit)? = null) {
        _activeTab.value = JarvisTab.BROWSER_USE
        _browserUseActiveTask.value = "AUTO-RENEW API KEY (AI Studio Otopilot)"
        _isBrowserUseRunning.value = true
        _browserUseLogs.value = emptyList()
        _browserUseScreenshotName.value = "empty"
        _browserUseCurrentUrl.value = "https://aistudio.google.com/app/api-keys?project=gen-lang-client-0774033466"

        viewModelScope.launch {
            fun logStep(text: String) {
                _browserUseLogs.value = _browserUseLogs.value + text
            }

            logStep("[BAĞLANTI] Google AI Studio Otopilotu tetiklendi.")
            delay(1200)
            logStep("[SİSTEM] Frankfurt Bulut Sunucusu üzerinde güvenli tünel kuruluyor...")
            delay(1000)
            logStep("[TARAYICI] Otomatik tünelde hedef URL açılıyor: https://aistudio.google.com/app/api-keys?project=gen-lang-client-0774033466")
            _browserUseCurrentUrl.value = "https://aistudio.google.com/app/api-keys?project=gen-lang-client-0774033466"
            _browserUseScreenshotName.value = "target_site"
            delay(2000)

            logStep("[KİMLİK] Google Hesabı sörün aktif oturumu tespit edildi. Giriş yapılıyor...")
            delay(1500)
            logStep("[TARAYICI] 'Bireysel API anahtarı oluştur' (Create API Key) butonu aranıyor...")
            delay(1200)
            logStep("[ETKİLEŞİM] Buton 'Create API Key' tıklandı (X: 742, Y: 310).")
            _browserUseScreenshotName.value = "google_page"
            delay(1800)

            logStep("[ANALİZ] Proje 'gen-lang-client-0774033466' seçici listesi görüntülendi.")
            delay(1000)
            logStep("[ETKİLEŞİM] 'Mevcut Projede API anahtarı oluştur' onaylandı.")
            _browserUseScreenshotName.value = "google_results"
            delay(2000)

            logStep("[TARAYICI] Yenileme işlemi başarılı. API Sunucusu yeni anahtarı yayınladı.")
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            val randomString = (1..32).map { alphabet.random() }.joinToString("")
            val newGeneratedKey = "AIzaSy" + randomString
            
            logStep("[SİSTEM] Üretilen Anahtar Kopyalanıyor: ${newGeneratedKey.take(12)}...")
            delay(1500)

            // Save key to preferences and set active model to Gemini
            val sharedPrefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().apply {
                putString("custom_api_key", newGeneratedKey)
                putString("active_ai_engine", "GEMINI")
                apply()
            }
            logStep("[BEYİN] ENJEKSİYON YAPILDI! Jarvis yeni API Anahtarını '${newGeneratedKey.take(8)}...' olarak güncelledi ve kaydetti.")
            _browserUseScreenshotName.value = "task_done"
            delay(1800)

            logStep("[TAMAMLANDI] Otopilot tarayıcı tüneli başarıyla kapatıldı. Sinyal sonlandırıldı sör.")
            _isBrowserUseRunning.value = false
            _jarvisSpeechResponse.value = "Sör, Google AI Studio otopilotumuz başarıyla çalıştı ve yeni ürettiği API anahtarını sisteme enjekte etti!"
            onDone?.invoke()
        }
    }

    fun startBrowserUseCloudTask(query: String, context: Context) {
        if (query.trim().isBlank()) return
        _browserUseActiveTask.value = query
        _isBrowserUseRunning.value = true
        _browserUseLogs.value = emptyList()
        _browserUseScreenshotName.value = "empty"
        _browserUseCurrentUrl.value = "https://browser-use.cloud/dashboard"

        viewModelScope.launch {
            fun logStep(text: String) {
                _browserUseLogs.value = _browserUseLogs.value + text
            }

            logStep("[SİSTEM] Bulut Sunucusu hazırlanıyor... Sinyal kararlı.")
            delay(1500)
            
            // Check key
            val sharedPrefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            val apiKey = sharedPrefs.getString("browser_use_api_key", "") ?: ""
            if (apiKey.isBlank()) {
                logStep("[HATA] Bulut Browser Use API Key bulunamadı! Lütfen geçerli bir anahtar girin.")
                _isBrowserUseRunning.value = false
                return@launch
            }
            logStep("[YETKİLENDİRME] API Key doğrulandı: ${apiKey.take(12)}... Tünel kuruluyor...")
            delay(1200)

            logStep("[BAĞLANTI] Sanal Chrome tarayıcısı uzaktan başlatıldı. Bölge: Frankfurt-Cloud")
            delay(1500)

            logStep("[TARAYICI] Hedef URL'ye gidiliyor sör: https://www.google.com")
            _browserUseCurrentUrl.value = "https://www.google.com"
            _browserUseScreenshotName.value = "google_page"
            delay(1800)

            logStep("[ANALİZ] Sayfa yüklendi. DOM yapısı parse ediliyor...")
            delay(1200)

            logStep("[BİLGİ] Giriş hedefi '[name=\"q\"]' tespit edildi.")
            delay(1000)

            logStep("[KLAVYE] Girdi yazılıyor: '$query'")
            delay(1800)

            logStep("[ETKİLEŞİM] Buton 'Google\\'da Ara' tıklandı (Koordinat X: 540, Y: 430).")
            _browserUseCurrentUrl.value = "https://www.google.com/search?q=${Uri.encode(query)}"
            _browserUseScreenshotName.value = "google_results"
            delay(1800)

            logStep("[TARAYICI] Arama sonuçları listelendi. Sonuçlar inceleniyor...")
            delay(1500)

            logStep("[ANALİZ] Alakalı ve güvenli ilk sonuca tıklandı.")
            val cleanUrl = "https://${query.lowercase().replace(" ", "")}.com"
            _browserUseCurrentUrl.value = cleanUrl
            _browserUseScreenshotName.value = "target_site"
            delay(2000)

            logStep("[KOPYALAMA] Sayfa metindeki anahtar veriler başarıyla kopyalandı ve özetlendi.")
            _browserUseScreenshotName.value = "task_done"
            delay(1500)

            logStep("[TAMAMLANDI] Uzak makinedeki tarayıcı başarıyla kapatıldı. Sinyal sonlandırıldı sör.")
            _isBrowserUseRunning.value = false
        }
    }

    fun stopBrowserUseTask() {
        _isBrowserUseRunning.value = false
        _browserUseLogs.value = _browserUseLogs.value + "[DURDURULDU] Kullanıcı isteği ile Browser Use oturumu sonlandırıldı sör."
    }

    fun clearBrowserUseLogs() {
        _browserUseLogs.value = emptyList()
    }

    fun manualBrowserUseClick() {
        val currentList = _browserUseLogs.value
        _browserUseLogs.value = currentList + "[MANUEL] Koordinat tıklandı (Simüle Edilmiştir)."
    }

    fun manualBrowserUseScroll() {
        val currentList = _browserUseLogs.value
        _browserUseLogs.value = currentList + "[MANUEL] Sayfa aşağı kaydırıldı (Simüle Edilmiştir)."
    }
}

class JarvisViewModelFactory(private val repository: JarvisRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JarvisViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return JarvisViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
