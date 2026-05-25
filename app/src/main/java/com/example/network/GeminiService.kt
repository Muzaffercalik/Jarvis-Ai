package com.example.network

import android.content.Context

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class SystemInstruction(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class ResponseFormatText(
    @Json(name = "mimeType") val mimeType: String
)

@JsonClass(generateAdapter = true)
data class ResponseFormat(
    @Json(name = "text") val text: ResponseFormatText? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "temperature") val temperature: Double? = 0.2,
    @Json(name = "responseMimeType") val responseMimeType: String? = "application/json"
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: SystemInstruction? = null,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    @Json(name = "parts") val parts: List<PartResponse>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: ContentResponse? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class JarvisIntentResponse(
    @Json(name = "explanation") val explanation: String,
    @Json(name = "intent") val intent: String, // "OPEN_YOUTUBE", "SEARCH_GOOGLE", "AUTO_LOGIN", "TYPE_TEXT", "CLICK_COORDINATES", "SHARE_CONTENT", "CROSS_APP_TRANSFER", "DEVICE_CONTROL", "SPEAK_ONLY"
    @Json(name = "searchQuery") val searchQuery: String? = "",
    @Json(name = "inputText") val inputText: String? = "",
    @Json(name = "clickTarget") val clickTarget: String? = "",
    @Json(name = "targetUrl") val targetUrl: String? = "",
    @Json(name = "sharePlatform") val sharePlatform: String? = "",
    @Json(name = "shareRecipient") val shareRecipient: String? = "",
    @Json(name = "targetContext") val targetContext: String? = "",
    @Json(name = "controlAction") val controlAction: String? = "",
    @Json(name = "clicksCount") val clicksCount: Int? = 1,
    @Json(name = "clickDelayMs") val clickDelayMs: Long? = 0,
    @Json(name = "longClick") val longClick: Boolean? = false,
    @Json(name = "swipeDirection") val swipeDirection: String? = "" // "DOWN", "UP", "LEFT", "RIGHT"
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    val jsonParser: Moshi = moshi
}

object JarvisBrain {
    private const val SYSTEM_PROMPT = """
        You are Jarvis (Turkish: Ceyvis), a high-tech sci-fi AI assistant for the user's phone.
        When the user gives a command in Turkish, you must parse the command and respond in Turkish WITH exact intent details in JSON format.
        
        The intents you can classify are:
        1. "OPEN_YOUTUBE": Opening YouTube app/browser (e.g., "jarvis youtube'u ac", "youtube'da komik videoları aç", "şu videoyu aç"). 
           Provide 'searchQuery' with the topic/video name.
        2. "SEARCH_GOOGLE": Searching on google (e.g., "shunu google'da ara", "google'da yemek tarifi bul"). 
           Provide 'searchQuery' with search term.
        3. "AUTO_LOGIN": Logging in to a site using local credentials (e.g., "spotify'a giriş yap", "google'da netflix arayıp hesabımla giriş yap").
           Provide 'targetUrl' with the target URL of the site if possible, and 'searchQuery' with the name of the service (e.g. "Netflix", "Spotify", "Google").
        4. "TYPE_TEXT": Typing text anywhere on screen (e.g., "şuraya jarvis yaz", "arama yerine kedi yaz", "google arama kutusuna mustafa yaz").
           - 'inputText' must contain characters/strings to write.
           - 'clickTarget' must contain the visual label/name of the input field if mentioned ("google arama", "arama", "email" etc.) so we can target it, or leave empty if user wants to type into the currently focused edit text.
        5. "CLICK_COORDINATES": Clicking, long pressing, swiping or executing auto clicker macros on screen (e.g., "giriş yap butonuna tıkla", "şuraya tıkla", "buraya basılı tut", "ekranın ortasından aşağı kaydır", "oraya tıkla", "3 saniye sonra buraya bas", "oraya 3 kere bas", "şuraya 5 kez hızlıca tıkla").
           - 'clickTarget': describing what to click / label on screen, or coordinate description.
           - 'clicksCount': If user requests clicking multiple times (auto-clicker style), specify the positive integer count (e.g. "3 kere", "5 kez", "200 kez tıkla" -> clicksCount will be 3, 5, or 200; default is 1).
           - 'clickDelayMs': If user requests delaying ('3 saniye sonra', '5 saniye sonra' etc.), specify the duration in MILLISECONDS (e.g. "3 saniye sonra" -> 3000; default is 0).
           - 'longClick': Boolean, set to true if user says "basılı tut", "uzun bas", "hold click".
           - 'swipeDirection': If user says swipe/scroll, specify: "DOWN" (aşağı kaydır), "UP" (yukarı kaydır), "LEFT" (sola kaydır), "RIGHT" (sağa kaydır). Leave empty for standard click.
        6. "SHARE_CONTENT": Sharing specified content, links, or videos via an external app (e.g., "Bu videoyu WhatsApp'tan Ahmet'e gönder", "Tarkan klibini Telegram ile paylaş sör", "şunu e-posta ile gönder").
           Provide 'sharePlatform' (e.g., "WhatsApp", "Telegram", "E-posta"), 'shareRecipient' if mentioned (e.g., "Ahmet", "Mehmet"), and 'inputText' with the text/data/video link to share.
        7. "CROSS_APP_TRANSFER": Copying content from one app context (e.g. browser, clipboard, site) and transferring/pasting it to another app (e.g., "web sitesindeki bilgileri kopyalayıp notlar uygulamasına yapıştır", "yazıyı alıp not defterine ekle").
           Provide 'inputText' with the actual text to be copied / simulated to copy, and 'targetContext' (e.g., "Notlar", "Panoya", "E-posta") with the destination application.
        8. "DEVICE_CONTROL": Controlling the device's hardware, system settings, or permissions (e.g., "feneri aç", "flashı kapat", "wifiyi kapat", "wifiyi aç sör", "sesi aç", "sesi kıs", "titreşim yap", "telefonun tüm kontrol yetkilerini ver sör").
           Provide 'controlAction' containing one of: "FLASHLIGHT_ON", "FLASHLIGHT_OFF", "WIFI_ON", "WIFI_OFF", "BLUETOOTH_ON", "BLUETOOTH_OFF", "VOLUME_UP", "VOLUME_DOWN", "VIBRATE", "ALL_PERMISSIONS".
        9. "SPEAK_ONLY": General conversation or command Jarvis can respond to via text/voice only.
          
        You MUST respond strictly in valid JSON format matching this schema:
        {
          "explanation": "Jarvis's spoken response in Turkish. Make it sound rich like a butler. Example: 'Tabii sör, hedefinize istendiği süre ve sıklıkta otopilot dokunuşu ayarlanıyor sör.'",
          "intent": "OPEN_YOUTUBE | SEARCH_GOOGLE | AUTO_LOGIN | TYPE_TEXT | CLICK_COORDINATES | SHARE_CONTENT | CROSS_APP_TRANSFER | DEVICE_CONTROL | SPEAK_ONLY",
          "searchQuery": "value or empty",
          "inputText": "value or empty",
          "clickTarget": "value or empty",
          "targetUrl": "value or empty",
          "sharePlatform": "value or empty (e.g. WhatsApp)",
          "shareRecipient": "value or empty (e.g. Ahmet)",
          "targetContext": "value or empty (e.g. Notlar)",
          "controlAction": "value or empty (one of FLASHLIGHT_ON, FLASHLIGHT_OFF, WIFI_ON, WIFI_OFF, BLUETOOTH_ON, BLUETOOTH_OFF, VOLUME_UP, VOLUME_DOWN, VIBRATE, ALL_PERMISSIONS)",
          "clicksCount": 1,
          "clickDelayMs": 0,
          "longClick": false,
          "swipeDirection": ""
        }
        Do not add any markup or markdown wraps like ```json in the actual voice response. We will request JSON MimeType so return pure JSON text only.
    """

    fun detectApiKeyType(key: String): String {
        val k = key.trim()
        return when {
            k.startsWith("AIzaSy") -> "GEMINI"
            k.startsWith("sk-or-") -> "OPENROUTER"
            k.startsWith("sk-ant-") -> "CLAUDE"
            k.startsWith("xai-") -> "GROK"
            k.startsWith("bu_") -> "BROWSER_USE"
            k.startsWith("sk-") -> "OPENAI"
            else -> "UNKNOWN"
        }
    }

    private suspend fun callGenericPostApi(url: String, headers: Map<String, String>, jsonBody: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = okhttp3.RequestBody.create(mediaType, jsonBody)
        
        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .post(body)
            
        for ((key, value) in headers) {
            requestBuilder.addHeader(key, value)
        }
        
        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${responseBody.ifBlank { response.message }}")
            }
            return@withContext responseBody
        }
    }

    private fun parseLlmJson(jsonText: String): JarvisIntentResponse {
        var clean = jsonText.trim()
        if (clean.startsWith("```")) {
            clean = clean.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        val obj = JSONObject(clean)
        return JarvisIntentResponse(
            explanation = obj.optString("explanation", "Anlaşıldı sör."),
            intent = obj.optString("intent", "SPEAK_ONLY"),
            searchQuery = obj.optString("searchQuery", ""),
            inputText = obj.optString("inputText", ""),
            clickTarget = obj.optString("clickTarget", ""),
            targetUrl = obj.optString("targetUrl", ""),
            sharePlatform = obj.optString("sharePlatform", ""),
            shareRecipient = obj.optString("shareRecipient", ""),
            targetContext = obj.optString("targetContext", ""),
            controlAction = obj.optString("controlAction", "")
        )
    }

    suspend fun analyzeCommand(command: String, context: Context): JarvisIntentResponse {
        val sharedPrefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val activeEngine = sharedPrefs.getString("active_ai_engine", "GEMINI") ?: "GEMINI"
        
        // Read visible layout text nodes to provide real-time environment-awareness
        val screenDump = if (com.example.JarvisAccessibilityService.isServiceRunning()) {
            com.example.JarvisAccessibilityService.getVisibleScreenDump()
        } else {
            "Erişilebilirlik Servisi Aktif Değil Sör."
        }

        val enrichedCommand = """
            KULLANICININ MEVCUT EKRAN İÇERİĞİ (TEXT LAYOUT):
            [ $screenDump ]
            
            KULLANICI KOMUTU: "$command"
            
            Eğer kullanıcı ekranda yer alan bir yazıya ("abone ol", "giriş", "beğen" vb.) tıklamak veya etkileşime girmek istiyorsa, bu yazıyı tam olarak 'clickTarget' alanına yerleştirip CLICK_COORDINATES intetini seç sör.
        """.trimIndent()
        
        try {
            when (activeEngine.uppercase()) {
                "GEMINI" -> {
                    val customKey = sharedPrefs.getString("custom_api_key", null)?.trim()
                    val isValidCustomKey = !customKey.isNullOrBlank() && 
                            customKey != "AIzaSyA2j_H4g2JwzKgN9tbXMQH3Apl6Cks0nks" && 
                            !customKey.contains("random") &&
                            customKey.length > 25 &&
                            !customKey.endsWith("...")
                    
                    val apiKey = if (isValidCustomKey) customKey!! else BuildConfig.GEMINI_API_KEY
                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "AIzaSyA2j_H4g2JwzKgN9tbXMQH3Apl6Cks0nks") {
                        return JarvisIntentResponse(
                            explanation = "Lütfen AI Studio Secrets panelinden geçerli bir GEMINI_API_KEY tanımlayın veya ayarlardan kendi geçerli anahtarınızı ekleyin sör.",
                            intent = "SPEAK_ONLY"
                        )
                    }

                    val requestBody = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = enrichedCommand)))),
                        generationConfig = GenerationConfig(temperature = 0.2, responseMimeType = "application/json"),
                        systemInstruction = SystemInstruction(parts = listOf(Part(text = SYSTEM_PROMPT)))
                    )

                    val modelsToTry = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-2.5-flash")
                    var lastEx: Exception? = null
                    for (model in modelsToTry) {
                        try {
                            val response = RetrofitClient.service.generateContent(model, apiKey, requestBody)
                            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (jsonText != null) {
                                return parseLlmJson(jsonText)
                            }
                        } catch (e: Exception) {
                            lastEx = e
                        }
                    }
                    throw lastEx ?: Exception("Gemini API çağrılamadı sör.")
                }
                
                "OPENAI" -> {
                    val apiKey = sharedPrefs.getString("openai_api_key", "") ?: ""
                    if (apiKey.isBlank()) {
                        return JarvisIntentResponse(
                            explanation = "Lütfen ayarlardan geçerli bir OpenAI API anahtarı tanımlayın sör.",
                            intent = "SPEAK_ONLY"
                        )
                    }

                    val json = JSONObject().apply {
                        put("model", "gpt-4o-mini")
                        put("temperature", 0.2)
                        put("response_format", JSONObject().put("type", "json_object"))
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                            put(JSONObject().put("role", "user").put("content", enrichedCommand))
                        })
                    }

                    val h = mapOf("Authorization" to "Bearer $apiKey")
                    val resString = callGenericPostApi("https://api.openai.com/v1/chat/completions", h, json.toString())
                    
                    val obj = JSONObject(resString)
                    val contentText = obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    return parseLlmJson(contentText)
                }

                "OPENROUTER" -> {
                    val apiKey = sharedPrefs.getString("openrouter_api_key", "") ?: ""
                    if (apiKey.isBlank()) {
                        return JarvisIntentResponse(
                            explanation = "Lütfen ayarlardan geçerli bir OpenRouter API anahtarı tanımlayın sör.",
                            intent = "SPEAK_ONLY"
                        )
                    }

                    val json = JSONObject().apply {
                        put("model", "google/gemini-2.5-flash")
                        put("temperature", 0.2)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                            put(JSONObject().put("role", "user").put("content", enrichedCommand))
                        })
                    }

                    val h = mapOf(
                        "Authorization" to "Bearer $apiKey",
                        "HTTP-Referer" to "https://ai.studio/build",
                        "X-Title" to "Jarvis Pro"
                    )
                    val resString = callGenericPostApi("https://openrouter.ai/api/v1/chat/completions", h, json.toString())
                    
                    val obj = JSONObject(resString)
                    val contentText = obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    return parseLlmJson(contentText)
                }

                "CLAUDE" -> {
                    val apiKey = sharedPrefs.getString("claude_api_key", "") ?: ""
                    if (apiKey.isBlank()) {
                        return JarvisIntentResponse(
                            explanation = "Lütfen ayarlardan geçerli bir Anthropic Claude API anahtarı tanımlayın sör.",
                            intent = "SPEAK_ONLY"
                        )
                    }

                    val json = JSONObject().apply {
                        put("model", "claude-3-5-sonnet-20241022")
                        put("max_tokens", 1024)
                        put("system", SYSTEM_PROMPT)
                        put("temperature", 0.2)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", enrichedCommand))
                        })
                    }

                    val h = mapOf(
                        "x-api-key" to apiKey,
                        "anthropic-version" to "2023-06-01"
                    )
                    val resString = callGenericPostApi("https://api.anthropic.com/v1/messages", h, json.toString())
                    
                    val obj = JSONObject(resString)
                    val contentText = obj.getJSONArray("content").getJSONObject(0).getString("text")
                    return parseLlmJson(contentText)
                }

                "GROK" -> {
                    val apiKey = sharedPrefs.getString("grok_api_key", "") ?: ""
                    if (apiKey.isBlank()) {
                        return JarvisIntentResponse(
                            explanation = "Lütfen ayarlardan geçerli bir Grok API anahtarı tanımlayın sör.",
                            intent = "SPEAK_ONLY"
                        )
                    }

                    val json = JSONObject().apply {
                        put("model", "grok-2-latest")
                        put("temperature", 0.2)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                            put(JSONObject().put("role", "user").put("content", enrichedCommand))
                        })
                    }

                    val h = mapOf("Authorization" to "Bearer $apiKey")
                    val resString = callGenericPostApi("https://api.x.ai/v1/chat/completions", h, json.toString())
                    
                    val obj = JSONObject(resString)
                    val contentText = obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    return parseLlmJson(contentText)
                }
                
                else -> {
                    return JarvisIntentResponse("Tanımsız AI Motoru seçildi sör.", "SPEAK_ONLY")
                }
            }
        } catch (e: Exception) {
            val message = e.localizedMessage ?: ""
            var code = 0
            
            // Introspect nested exception links for Retrofit HTTP status codes
            var currentEx: Throwable? = e
            while (currentEx != null) {
                if (currentEx is retrofit2.HttpException) {
                    code = currentEx.code()
                    break
                }
                currentEx = currentEx.cause
            }
            
            val userFriendlyExplanation = when {
                code == 429 || message.contains("429") -> 
                    "Sör, çok fazla üst üste istek gönderildiğinden Gemini API limiti geçici olarak aşıldı (HTTP 429 Quota Exceeded). Lütfen asistanı kapatıp birkaç saniye bekleyin ve tekrar deneyin sör."
                code == 400 || message.contains("400") -> 
                    "Sör, sunucu isteği kabul etmedi (HTTP 400 Bad Request). Bunun sebebi seçilen modelin şuan devredışı olması veya hatalı/geçersiz bir API anahtarı girilmesi olabilir. Lütfen ayarlardan API anahtarınızı temizlemeyi veya kontrol etmeyi deneyin sör."
                code == 403 || message.contains("403") || message.contains("API_KEY_INVALID") -> 
                    "Sör, API yetkilendirme hatası (HTTP 403 / Geçersiz Anahtar). Lütfen girdiğiniz API anahtarının doğruluğunu kontrol edin veya temizleyerek varsayılan tüneli kullanın sör."
                else -> 
                    "Bağlantıda bir aksama oldu sör. Detay: HTTP ${if (code > 0) code else "Hata"} - $message"
            }
            return JarvisIntentResponse(
                explanation = userFriendlyExplanation,
                intent = "SPEAK_ONLY"
            )
        }
    }

    suspend fun testConnection(context: Context, testKey: String): String {
        val sharedPrefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val activeEngine = sharedPrefs.getString("active_ai_engine", "GEMINI") ?: "GEMINI"
        val trimmedKey = testKey.trim()

        try {
            when (activeEngine.uppercase()) {
                "GEMINI" -> {
                    val fallbackKey = sharedPrefs.getString("custom_api_key", null)?.trim()
                    val isValidCustomKey = !fallbackKey.isNullOrBlank() && 
                            fallbackKey != "AIzaSyA2j_H4g2JwzKgN9tbXMQH3Apl6Cks0nks" && 
                            !fallbackKey.contains("random") &&
                            fallbackKey.length > 25 &&
                            !fallbackKey.endsWith("...")
                    
                    val key = trimmedKey.ifBlank { if (isValidCustomKey) fallbackKey!! else BuildConfig.GEMINI_API_KEY }
                    if (key.isEmpty() || key == "MY_GEMINI_API_KEY" || key == "AIzaSyA2j_H4g2JwzKgN9tbXMQH3Apl6Cks0nks") return "API anahtarı tanımlanmamış sör."

                    val requestBody = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = "Hello! respond with exactly one word: Success")))),
                        generationConfig = GenerationConfig(temperature = 0.2)
                    )

                    val response = RetrofitClient.service.generateContent("gemini-1.5-flash", key, requestBody)
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    return if (!text.isNullOrBlank()) "BAŞARILI! (Google Gemini tüneli aktif sör.)" else "Başarısız boş yanıt sör."
                }
                
                "OPENAI" -> {
                    val fallbackKey = sharedPrefs.getString("openai_api_key", "") ?: ""
                    val key = trimmedKey.ifBlank { fallbackKey }
                    if (key.isBlank()) return "OpenAI anahtarı tanımlanmamış sör."

                    val json = JSONObject().apply {
                        put("model", "gpt-4o-mini")
                        put("temperature", 0.2)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", "Hello! Respond with 'Connected'"))
                        })
                    }
                    val h = mapOf("Authorization" to "Bearer $key")
                    callGenericPostApi("https://api.openai.com/v1/chat/completions", h, json.toString())
                    return "BAŞARILI! (OpenAI tüneli kuruldu, gpt-4o-mini yanıt veriyor!)"
                }

                "OPENROUTER" -> {
                    val fallbackKey = sharedPrefs.getString("openrouter_api_key", "") ?: ""
                    val key = trimmedKey.ifBlank { fallbackKey }
                    if (key.isBlank()) return "OpenRouter anahtarı tanımlanmamış sör."

                    val json = JSONObject().apply {
                        put("model", "google/gemini-2.5-flash")
                        put("temperature", 0.2)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", "Hello!"))
                        })
                    }
                    val h = mapOf("Authorization" to "Bearer $key")
                    callGenericPostApi("https://openrouter.ai/api/v1/chat/completions", h, json.toString())
                    return "BAŞARILI! (OpenRouter tüneli kuruldu!)"
                }

                "CLAUDE" -> {
                    val fallbackKey = sharedPrefs.getString("claude_api_key", "") ?: ""
                    val key = trimmedKey.ifBlank { fallbackKey }
                    if (key.isBlank()) return "Claude anahtarı tanımlanmamış sör."

                    val json = JSONObject().apply {
                        put("model", "claude-3-5-sonnet-20241022")
                        put("max_tokens", 50)
                        put("temperature", 0.2)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", "Hello!"))
                        })
                    }
                    val h = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")
                    callGenericPostApi("https://api.anthropic.com/v1/messages", h, json.toString())
                    return "BAŞARILI! (Anthropic Claude tüneliaktif sör!)"
                }

                "GROK" -> {
                    val fallbackKey = sharedPrefs.getString("grok_api_key", "") ?: ""
                    val key = trimmedKey.ifBlank { fallbackKey }
                    if (key.isBlank()) return "Grok anahtarı tanımlanmamış sör."

                    val json = JSONObject().apply {
                        put("model", "grok-2-latest")
                        put("temperature", 0.2)
                        put("messages", JSONArray().apply {
                            put(JSONObject().put("role", "user").put("content", "Hello!"))
                        })
                    }
                    val h = mapOf("Authorization" to "Bearer $key")
                    callGenericPostApi("https://api.x.ai/v1/chat/completions", h, json.toString())
                    return "BAŞARILI! (xAI Grok tüneli kuruldu!)"
                }
                
                else -> return "Sistemsel Hata: Bilinmeyen Motor sör."
            }
        } catch (e: Exception) {
            return "BAĞLANTI HATASİ: ${e.localizedMessage ?: "Bilinmeyen hata"}"
        }
    }
}
