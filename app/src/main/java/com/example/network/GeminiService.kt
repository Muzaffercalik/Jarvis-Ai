package com.example.network

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
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

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
    @Json(name = "intent") val intent: String, // "OPEN_YOUTUBE", "SEARCH_GOOGLE", "AUTO_LOGIN", "TYPE_TEXT", "CLICK_COORDINATES", "SPEAK_ONLY"
    @Json(name = "searchQuery") val searchQuery: String? = "",
    @Json(name = "inputText") val inputText: String? = "",
    @Json(name = "clickTarget") val clickTarget: String? = "",
    @Json(name = "targetUrl") val targetUrl: String? = ""
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
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
        4. "TYPE_TEXT": Typing text anywhere on screen (e.g., "şuraya jarvis yaz", "arama yerine kedi yaz").
           Provide 'inputText' with characters/strings.
        5. "CLICK_COORDINATES": Clicking somewhere on the screen (e.g., "giriş yap butonuna tıkla", "şuraya tıkla", "arama simgesine tıkla").
           Provide 'clickTarget' describing what to click.
        6. "SPEAK_ONLY": General conversation or command Jarvis can respond to via text/voice only.
        
        You MUST respond strictly in valid JSON format matching this schema:
        {
          "explanation": "Jarvis's spoken response in Turkish. Make it sound rich like a butler. Example: 'Tabii sör, YouTube araması başlatılıyor.'",
          "intent": "OPEN_YOUTUBE | SEARCH_GOOGLE | AUTO_LOGIN | TYPE_TEXT | CLICK_COORDINATES | SPEAK_ONLY",
          "searchQuery": "value or empty",
          "inputText": "value or empty",
          "clickTarget": "value or empty",
          "targetUrl": "value or empty"
        }
        Do not add any markup or markdown wraps like ```json in the actual voice response. We will request JSON MimeType so return pure JSON text only.
    """

    suspend fun analyzeCommand(command: String): JarvisIntentResponse {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return JarvisIntentResponse(
                explanation = "Lütfen AI Studio Secrets panelinden geçerli bir GEMINI_API_KEY tanımlayın, sör.",
                intent = "SPEAK_ONLY"
            )
        }

        val requestBody = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = command)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.2,
                responseMimeType = "application/json"
            ),
            systemInstruction = SystemInstruction(
                parts = listOf(Part(text = SYSTEM_PROMPT))
            )
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                RetrofitClient.jsonParser.adapter(JarvisIntentResponse::class.java).fromJson(jsonText)
                    ?: JarvisIntentResponse(
                        explanation = "Üzgünüm sör, komutu ayrıştırırken bir hata oluştu: Boş yanıt.",
                        intent = "SPEAK_ONLY"
                    )
            } else {
                JarvisIntentResponse(
                    explanation = "Anlaşılamadı sör. Komutu tekrar edebilir misiniz?",
                    intent = "SPEAK_ONLY"
                )
            }
        } catch (e: Exception) {
            JarvisIntentResponse(
                explanation = "Bağlantıda bir aksama oldu sör. Detay: ${e.localizedMessage}",
                intent = "SPEAK_ONLY"
            )
        }
    }
}
