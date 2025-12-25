package com.skul9x.readoutloud

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Client gọi API FPT.AI Text-to-Speech
 * 
 * API Endpoint: POST https://api.fpt.ai/hmi/tts/v5
 * Headers:
 *   - api-key: API key từ console.fpt.ai
 *   - voice: ID giọng đọc (banmai, leminh, thuminh...)
 *   - speed: Tốc độ đọc (-3 đến 3, 0 là bình thường)
 * Body: Plain text cần đọc
 * Response: JSON chứa URL file audio
 */
class AiTtsClient(private val apiKey: String) {

    companion object {
        private const val TAG = "AiTtsClient"
        private const val API_URL = "https://api.fpt.ai/hmi/tts/v5"
        private const val DEFAULT_VOICE = "banmai"
        private const val DEFAULT_SPEED = "0"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Response từ FPT.AI TTS API
     */
    data class TtsResponse(
        @SerializedName("async") val asyncUrl: String?,
        @SerializedName("error") val error: Int?,
        @SerializedName("message") val message: String?,
        @SerializedName("request_id") val requestId: String?
    )

    /**
     * Kết quả synthesis
     */
    sealed class SynthesisResult {
        data class Success(val audioFile: File) : SynthesisResult()
        data class Error(val message: String) : SynthesisResult()
    }

    /**
     * Chuyển văn bản thành file audio
     * 
     * @param text Văn bản cần đọc
     * @param voiceId ID giọng đọc (mặc định: banmai)
     * @param speed Tốc độ đọc (-3 đến 3)
     * @param outputDir Thư mục lưu file audio
     * @return SynthesisResult chứa file audio hoặc lỗi
     */
    suspend fun synthesize(
        text: String,
        voiceId: String = DEFAULT_VOICE,
        speed: Int = 0,
        outputDir: File
    ): SynthesisResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Synthesizing text with voice: $voiceId, length: ${text.length}")
            
            // 1. Gọi API lấy URL audio
            val audioUrl = requestSynthesis(text, voiceId, speed)
                ?: return@withContext SynthesisResult.Error("Không nhận được URL audio từ API")
            
            Log.d(TAG, "Got audio URL: $audioUrl")
            
            // 2. Download file audio
            val audioFile = downloadAudio(audioUrl, outputDir)
                ?: return@withContext SynthesisResult.Error("Không thể tải file audio")
            
            Log.d(TAG, "Downloaded audio to: ${audioFile.absolutePath}")
            SynthesisResult.Success(audioFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error", e)
            SynthesisResult.Error(e.message ?: "Lỗi không xác định")
        }
    }

    /**
     * Gọi API để lấy URL audio
     */
    private fun requestSynthesis(text: String, voiceId: String, speed: Int): String? {
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("api-key", apiKey)
            .addHeader("voice", voiceId)
            .addHeader("speed", speed.coerceIn(-3, 3).toString())
            .post(text.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            Log.d(TAG, "API Response: $body")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code} - $body")
                return null
            }
            
            val ttsResponse = gson.fromJson(body, TtsResponse::class.java)
            
            if (ttsResponse.error != null && ttsResponse.error != 0) {
                Log.e(TAG, "TTS error: ${ttsResponse.message}")
                return null
            }
            
            return ttsResponse.asyncUrl
        }
    }

    /**
     * Download file audio từ URL
     */
    private fun downloadAudio(url: String, outputDir: File): File? {
        // Đợi một chút để FPT.AI xử lý file
        Thread.sleep(1500)
        
        val request = Request.Builder().url(url).build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Download error: ${response.code}")
                return null
            }
            
            val audioFile = File(outputDir, "ai_tts_${System.currentTimeMillis()}.mp3")
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(audioFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            return if (audioFile.exists() && audioFile.length() > 0) audioFile else null
        }
    }

    /**
     * Kiểm tra API key có hợp lệ không
     */
    suspend fun validateApiKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("api-key", apiKey)
                .addHeader("voice", DEFAULT_VOICE)
                .post("test".toRequestBody("text/plain".toMediaType()))
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "API key validation failed", e)
            false
        }
    }
}
