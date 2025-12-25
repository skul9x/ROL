package com.skul9x.readoutloud

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.skul9x.readoutloud.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tts: TextToSpeech
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPreferences: SharedPreferences

    // Device voices
    private var vietnameseVoices = listOf<Voice>()
    private var selectedDeviceVoiceName: String? = null
    private val deviceVoiceDisplayNames = mutableListOf<String>()
    private val deviceVoiceMap = mutableMapOf<String, String>()
    
    // AI voices
    private var selectedAiVoiceId: String? = null
    private val aiVoiceDisplayNames = mutableListOf<String>()
    private val aiVoiceMap = mutableMapOf<String, String>()
    
    // Current voice type
    private var currentVoiceType = VoiceType.DEVICE

    companion object {
        private const val PREFS_NAME = "ReadOutLoudPrefs"
        private const val KEY_DEVICE_VOICE_NAME = "lastDeviceVoiceName"
        private const val KEY_AI_VOICE_ID = "lastAiVoiceId"
        private const val KEY_VOICE_TYPE = "voiceType"
        private const val KEY_API_KEY = "apiKey"
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startReading()
            } else {
                Toast.makeText(this, "Cần cấp quyền thông báo để chạy nền", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        loadSavedPreferences()
        setupUI()
        initializeTtsForVoiceDiscovery()
        populateAiVoiceSelector()
        setInitialSystemVolume()
    }

    private fun loadSavedPreferences() {
        currentVoiceType = try {
            VoiceType.valueOf(sharedPreferences.getString(KEY_VOICE_TYPE, VoiceType.DEVICE.name)!!)
        } catch (e: Exception) {
            VoiceType.DEVICE
        }
        selectedDeviceVoiceName = sharedPreferences.getString(KEY_DEVICE_VOICE_NAME, null)
        selectedAiVoiceId = sharedPreferences.getString(KEY_AI_VOICE_ID, "banmai")
        
        // Load API key
        val savedApiKey = sharedPreferences.getString(KEY_API_KEY, null)
        if (!savedApiKey.isNullOrBlank()) {
            binding.apiKeyInput.setText(savedApiKey)
        }
    }

    private fun setupUI() {
        binding.pasteButton.setOnClickListener { pasteFromClipboard() }
        binding.readButton.setOnClickListener { checkPermissionsAndRead() }
        binding.stopButton.setOnClickListener { stopReading() }

        binding.editText.movementMethod = ScrollingMovementMethod.getInstance()

        // Volume toggle
        binding.volumeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val percentage = when (checkedId) {
                    R.id.volumeButton80 -> 0.80f
                    R.id.volumeButton85 -> 0.85f
                    R.id.volumeButton90 -> 0.90f
                    else -> 0.80f
                }
                setSystemVolume(percentage)
            }
        }

        // Voice type toggle (Device / AI)
        setupVoiceTypeToggle()
    }

    private fun setupVoiceTypeToggle() {
        // Set initial state
        when (currentVoiceType) {
            VoiceType.DEVICE -> binding.voiceTypeToggleGroup.check(R.id.voiceTypeDevice)
            VoiceType.AI -> binding.voiceTypeToggleGroup.check(R.id.voiceTypeAi)
        }
        updateUIForVoiceType(currentVoiceType)

        binding.voiceTypeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newType = when (checkedId) {
                    R.id.voiceTypeAi -> VoiceType.AI
                    else -> VoiceType.DEVICE
                }
                if (newType != currentVoiceType) {
                    currentVoiceType = newType
                    updateUIForVoiceType(newType)
                    saveSettings()
                }
            }
        }
    }

    private fun updateUIForVoiceType(voiceType: VoiceType) {
        when (voiceType) {
            VoiceType.DEVICE -> {
                binding.apiKeyLayout.visibility = View.GONE
                binding.voiceSelectorLayout.hint = "Chọn giọng đọc (Thiết bị)"
                populateDeviceVoiceSelector()
            }
            VoiceType.AI -> {
                binding.apiKeyLayout.visibility = View.VISIBLE
                binding.voiceSelectorLayout.hint = "Chọn giọng đọc (AI)"
                populateAiVoiceSelector()
            }
        }
    }

    private fun initializeTtsForVoiceDiscovery() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                vietnameseVoices = tts.voices
                    .filter { it.locale == Locale("vi", "VN") && !it.isNetworkConnectionRequired }
                    .distinctBy { it.name }

                if (vietnameseVoices.isNotEmpty()) {
                    populateDeviceVoiceSelector()
                } else {
                    Toast.makeText(this, "Không tìm thấy giọng đọc Tiếng Việt trên thiết bị", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Không thể khởi tạo Text-to-Speech", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun populateDeviceVoiceSelector() {
        deviceVoiceDisplayNames.clear()
        deviceVoiceMap.clear()

        vietnameseVoices.forEachIndexed { index, voice ->
            val displayName = "Giọng ${index + 1}"
            deviceVoiceDisplayNames.add(displayName)
            deviceVoiceMap[displayName] = voice.name
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, deviceVoiceDisplayNames)
        val autoCompleteTextView = (binding.voiceSelectorLayout.editText as? AutoCompleteTextView)
        autoCompleteTextView?.setAdapter(adapter)

        val lastDisplayName = deviceVoiceMap.entries.find { it.value == selectedDeviceVoiceName }?.key
        if (lastDisplayName != null) {
            autoCompleteTextView?.setText(lastDisplayName, false)
        } else if (deviceVoiceDisplayNames.isNotEmpty()) {
            autoCompleteTextView?.setText(deviceVoiceDisplayNames[0], false)
            selectedDeviceVoiceName = deviceVoiceMap[deviceVoiceDisplayNames[0]]
        }

        autoCompleteTextView?.setOnItemClickListener { _, _, position, _ ->
            val displayName = adapter.getItem(position) ?: return@setOnItemClickListener
            selectedDeviceVoiceName = deviceVoiceMap[displayName]
            saveSettings()
        }
    }

    private fun populateAiVoiceSelector() {
        aiVoiceDisplayNames.clear()
        aiVoiceMap.clear()

        AiVoice.ALL_VOICES.forEach { voice ->
            aiVoiceDisplayNames.add(voice.displayName)
            aiVoiceMap[voice.displayName] = voice.id
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, aiVoiceDisplayNames)
        val autoCompleteTextView = (binding.voiceSelectorLayout.editText as? AutoCompleteTextView)
        autoCompleteTextView?.setAdapter(adapter)

        val lastDisplayName = AiVoice.findById(selectedAiVoiceId ?: "banmai")?.displayName
        if (lastDisplayName != null) {
            autoCompleteTextView?.setText(lastDisplayName, false)
        } else if (aiVoiceDisplayNames.isNotEmpty()) {
            autoCompleteTextView?.setText(aiVoiceDisplayNames[0], false)
            selectedAiVoiceId = aiVoiceMap[aiVoiceDisplayNames[0]]
        }

        autoCompleteTextView?.setOnItemClickListener { _, _, position, _ ->
            val displayName = adapter.getItem(position) ?: return@setOnItemClickListener
            selectedAiVoiceId = aiVoiceMap[displayName]
            saveSettings()
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val textToPaste = clipboard.primaryClip?.getItemAt(0)?.text.toString()
            val plainText = textToPaste.replace(Regex("[*#_`~]"), "")
            binding.editText.setText(plainText)
            Toast.makeText(this, "Đã dán và lọc văn bản", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Không có gì trong clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndRead() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startReading()
    }

    private fun startReading() {
        val text = binding.editText.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "Vui lòng nhập văn bản", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate AI settings
        if (currentVoiceType == VoiceType.AI) {
            val apiKey = binding.apiKeyInput.text.toString().trim()
            if (apiKey.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập API Key từ console.fpt.ai", Toast.LENGTH_LONG).show()
                binding.apiKeyLayout.requestFocus()
                return
            }
            // Save API key
            sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
        }

        val intent = Intent(this, TtsService::class.java).apply {
            action = TtsService.ACTION_START
            putExtra(TtsService.EXTRA_TEXT, text)
            putExtra(TtsService.EXTRA_VOICE_TYPE, currentVoiceType.name)
            
            when (currentVoiceType) {
                VoiceType.DEVICE -> {
                    putExtra(TtsService.EXTRA_VOICE_NAME, selectedDeviceVoiceName)
                }
                VoiceType.AI -> {
                    putExtra(TtsService.EXTRA_AI_VOICE_ID, selectedAiVoiceId)
                    putExtra(TtsService.EXTRA_API_KEY, binding.apiKeyInput.text.toString().trim())
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopReading() {
        val intent = Intent(this, TtsService::class.java).apply {
            action = TtsService.ACTION_STOP
        }
        startService(intent)
    }

    private fun setInitialSystemVolume() {
        binding.volumeToggleGroup.check(R.id.volumeButton80)
        setSystemVolume(0.80f)
    }

    private fun setSystemVolume(percentage: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (maxVolume * percentage).roundToInt()
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Không có quyền thay đổi âm lượng", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        with(sharedPreferences.edit()) {
            putString(KEY_VOICE_TYPE, currentVoiceType.name)
            putString(KEY_DEVICE_VOICE_NAME, selectedDeviceVoiceName)
            putString(KEY_AI_VOICE_ID, selectedAiVoiceId)
            apply()
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        super.onDestroy()
    }
}
