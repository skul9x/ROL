package com.skul9x.readoutloud

/**
 * Định nghĩa các loại giọng đọc trong app
 */
enum class VoiceType {
    DEVICE,  // Giọng TTS có sẵn trên thiết bị
    AI       // Giọng AI từ FPT.AI
}

/**
 * Thông tin giọng AI từ FPT.AI
 */
data class AiVoice(
    val id: String,          // "banmai", "leminh", "thuminh"...
    val displayName: String, // "Ban Mai (Nữ - Bắc)"
    val gender: String,      // "female" hoặc "male"
    val region: String       // "north", "south", "central"
) {
    companion object {
        /**
         * Danh sách giọng AI FPT.AI tiếng Việt
         * Tham khảo: https://fpt.ai/tts
         */
        val ALL_VOICES = listOf(
            // Giọng Nữ - Miền Bắc
            AiVoice("banmai", "Ban Mai (Nữ - Bắc)", "female", "north"),
            AiVoice("thuminh", "Thu Minh (Nữ - Bắc)", "female", "north"),
            
            // Giọng Nam - Miền Bắc
            AiVoice("leminh", "Lê Minh (Nam - Bắc)", "male", "north"),
            
            // Giọng Nam - Miền Trung
            AiVoice("giahuy", "Gia Huy (Nam - Trung)", "male", "central"),
            
            // Giọng Nữ - Miền Nam
            AiVoice("lannhi", "Lan Nhi (Nữ - Nam)", "female", "south"),
            AiVoice("linhsan", "Linh San (Nữ - Nam)", "female", "south"),
            
            // Giọng Nam - Miền Nam
            AiVoice("minhquang", "Minh Quang (Nam - Nam)", "male", "south"),
            
            // Giọng Nữ - Miền Trung
            AiVoice("myan", "Mỹ An (Nữ - Trung)", "female", "central"),
            AiVoice("ngoclam", "Ngọc Lam (Nữ - Trung)", "female", "central")
        )
        
        fun findById(id: String): AiVoice? = ALL_VOICES.find { it.id == id }
    }
}
