package com.skul9x.readoutloud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class TtsService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsService"
        const val CHANNEL_ID = "TtsServiceChannel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        const val EXTRA_TEXT = "EXTRA_TEXT"
        const val EXTRA_VOICE_NAME = "EXTRA_VOICE_NAME"
        const val EXTRA_VOICE_TYPE = "EXTRA_VOICE_TYPE"
        const val EXTRA_AI_VOICE_ID = "EXTRA_AI_VOICE_ID"
        const val EXTRA_API_KEY = "EXTRA_API_KEY"
    }

    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false
    private var mediaPlayer: MediaPlayer? = null
    private var aiTtsClient: AiTtsClient? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Pending request while waiting for TTS init
    private var pendingTextToSpeak: String? = null
    private var pendingVoiceNameToUse: String? = null
    private var pendingVoiceType: VoiceType = VoiceType.DEVICE
    private var pendingAiVoiceId: String? = null
    private var pendingApiKey: String? = null

    private var lastUtteranceId: String? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> stopReadingAndService()
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == lastUtteranceId) {
                        stopReadingAndService()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    stopReadingAndService()
                }
            })

            // Process pending request if any
            pendingTextToSpeak?.let { text ->
                handlePendingRequest(
                    text, 
                    pendingVoiceNameToUse, 
                    pendingVoiceType, 
                    pendingAiVoiceId,
                    pendingApiKey
                )
            }
        } else {
            stopSelf()
        }
    }

    private fun handleStart(intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val voiceName = intent.getStringExtra(EXTRA_VOICE_NAME)
        val voiceTypeStr = intent.getStringExtra(EXTRA_VOICE_TYPE) ?: VoiceType.DEVICE.name
        val voiceType = try { VoiceType.valueOf(voiceTypeStr) } catch (e: Exception) { VoiceType.DEVICE }
        val aiVoiceId = intent.getStringExtra(EXTRA_AI_VOICE_ID)
        val apiKey = intent.getStringExtra(EXTRA_API_KEY)

        Log.d(TAG, "handleStart: voiceType=$voiceType, aiVoiceId=$aiVoiceId")

        if (text.isBlank()) {
            stopReadingAndService()
            return
        }

        // AI voice
        if (voiceType == VoiceType.AI) {
            if (apiKey.isNullOrBlank()) {
                Log.e(TAG, "API key is missing for AI voice")
                stopReadingAndService()
                return
            }
            executeAiReading(text, aiVoiceId ?: "banmai", apiKey)
            return
        }

        // Device voice
        if (isTtsInitialized) {
            executeDeviceReading(text, voiceName)
        } else {
            pendingTextToSpeak = text
            pendingVoiceNameToUse = voiceName
            pendingVoiceType = voiceType
            pendingAiVoiceId = aiVoiceId
            pendingApiKey = apiKey
        }
    }

    private fun handlePendingRequest(
        text: String, 
        voiceName: String?, 
        voiceType: VoiceType,
        aiVoiceId: String?,
        apiKey: String?
    ) {
        if (voiceType == VoiceType.AI && !apiKey.isNullOrBlank()) {
            executeAiReading(text, aiVoiceId ?: "banmai", apiKey)
        } else {
            executeDeviceReading(text, voiceName)
        }
        clearPendingRequest()
    }

    private fun clearPendingRequest() {
        pendingTextToSpeak = null
        pendingVoiceNameToUse = null
        pendingVoiceType = VoiceType.DEVICE
        pendingAiVoiceId = null
        pendingApiKey = null
    }

    // ========== DEVICE TTS ==========
    
    private fun executeDeviceReading(text: String, voiceName: String?) {
        tts.language = Locale("vi", "VN")
        voiceName?.let { name ->
            val voice = tts.voices.find { it.name == name }
            tts.voice = voice ?: tts.defaultVoice
        }

        startForeground(NOTIFICATION_ID, buildNotification("Đang đọc (Thiết bị)..."))
        speakWithDeviceTts(text)
    }

    private fun speakWithDeviceTts(text: String) {
        val chunks = splitTextForTts(text)
        if (chunks.isEmpty()) {
            stopReadingAndService()
            return
        }

        lastUtteranceId = "chunk_${chunks.size - 1}"
        tts.stop()

        for ((index, chunk) in chunks.withIndex()) {
            val utteranceId = "chunk_$index"
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    private fun splitTextForTts(text: String): List<String> {
        val maxLength = 3900
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            var endIndex = (index + maxLength).coerceAtMost(text.length)
            if (endIndex < text.length) {
                val lastPunctuation = text.lastIndexOfAny(charArrayOf('.', '!', '?'), endIndex)
                if (lastPunctuation > index) {
                    endIndex = lastPunctuation + 1
                }
            }
            chunks.add(text.substring(index, endIndex))
            index = endIndex
        }
        return chunks
    }

    // ========== AI TTS ==========
    
    private fun executeAiReading(text: String, aiVoiceId: String, apiKey: String) {
        startForeground(NOTIFICATION_ID, buildNotification("Đang xử lý AI..."))
        
        aiTtsClient = AiTtsClient(apiKey)
        
        serviceScope.launch {
            val chunks = splitTextForAiTts(text)
            Log.d(TAG, "AI TTS: ${chunks.size} chunks to process")
            
            for ((index, chunk) in chunks.withIndex()) {
                updateNotification("Đang đọc AI (${index + 1}/${chunks.size})...")
                
                val result = aiTtsClient!!.synthesize(
                    text = chunk,
                    voiceId = aiVoiceId,
                    speed = 0,
                    outputDir = cacheDir
                )
                
                when (result) {
                    is AiTtsClient.SynthesisResult.Success -> {
                        playAudioFile(result.audioFile)
                    }
                    is AiTtsClient.SynthesisResult.Error -> {
                        Log.e(TAG, "AI synthesis error: ${result.message}")
                        // Fallback to device TTS for remaining text
                        if (isTtsInitialized) {
                            val remainingText = chunks.drop(index).joinToString(" ")
                            executeDeviceReading(remainingText, null)
                        } else {
                            stopReadingAndService()
                        }
                        return@launch
                    }
                }
            }
            
            // All chunks done
            stopReadingAndService()
        }
    }

    private fun splitTextForAiTts(text: String): List<String> {
        // FPT.AI khuyến nghị tối đa 5000 ký tự mỗi request
        val maxLength = 4000
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            var endIndex = (index + maxLength).coerceAtMost(text.length)
            if (endIndex < text.length) {
                // Ưu tiên cắt ở cuối đoạn văn
                val lastParagraph = text.lastIndexOf("\n\n", endIndex)
                if (lastParagraph > index) {
                    endIndex = lastParagraph + 2
                } else {
                    val lastPunctuation = text.lastIndexOfAny(charArrayOf('.', '!', '?'), endIndex)
                    if (lastPunctuation > index) {
                        endIndex = lastPunctuation + 1
                    }
                }
            }
            chunks.add(text.substring(index, endIndex).trim())
            index = endIndex
        }
        return chunks.filter { it.isNotBlank() }
    }

    private suspend fun playAudioFile(audioFile: File) {
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    prepare()
                    
                    setOnCompletionListener {
                        // Clean up audio file
                        audioFile.delete()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(Unit))
                        }
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: $what, $extra")
                        audioFile.delete()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.success(Unit))
                        }
                        true
                    }
                    
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio", e)
                audioFile.delete()
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(Unit))
                }
            }
        }
    }

    // ========== NOTIFICATION ==========

    private fun buildNotification(status: String): Notification {
        createNotificationChannel()
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, flag)

        val stopIntent = Intent(this, TtsService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, flag)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_play_arrow)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Dừng", stopPendingIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = buildNotification(status)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, 
                "Kênh dịch vụ đọc", 
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }

    private fun stopReadingAndService() {
        if (::tts.isInitialized) {
            tts.stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        mediaPlayer?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
