package com.yonisirote.readmyfeed.tts

interface TtsEngine {
  suspend fun initialize()

  fun voices(): List<TtsVoice>

  suspend fun speak(text: String, options: TtsSpeakOptions = TtsSpeakOptions())

  fun stop()

  fun shutdown()
}
