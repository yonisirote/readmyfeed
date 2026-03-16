package com.yonisirote.readmyfeed.tts

enum class TtsVoiceQuality {
  DEFAULT,
  ENHANCED,
  UNKNOWN,
}

data class TtsVoice(
  val identifier: String,
  val language: String,
  val quality: TtsVoiceQuality = TtsVoiceQuality.UNKNOWN,
  val requiresNetwork: Boolean = false,
)

data class TtsSpeakOptions(
  val language: String? = null,
  val pitch: Float? = null,
  val rate: Float? = null,
  val voice: String? = null,
  val volume: Float? = null,
)
