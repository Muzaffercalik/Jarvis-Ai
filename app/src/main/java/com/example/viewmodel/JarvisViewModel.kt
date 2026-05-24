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
    SIMULATOR
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

        viewModelScope.launch {
            _isAnalyzing.value = true
            _jarvisSpeechResponse.value = "İstişare ediliyor sör..."
            val response = JarvisBrain.analyzeCommand(command)
            _isAnalyzing.value = false

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
                    delay(1200)
                    logStep("[TIKLAMA] Koordinat (320, 410) üzerine dokunuldu.")
                    delay(1000)
                    logStep("[BAŞARILI] Tıklama koordinatı tetiklendi.")
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
