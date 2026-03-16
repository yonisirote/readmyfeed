package com.yonisirote.readmyfeed.tts

class TtsService(
  private val engine: TtsEngine,
) {
  private var initialized = false

  suspend fun initialize() {
    if (initialized) {
      return
    }

    engine.initialize()
    initialized = true
  }

  suspend fun speak(text: String, options: TtsSpeakOptions = TtsSpeakOptions()) {
    assertInitialized()

    val trimmedText = text.trim()
    if (trimmedText.isEmpty()) {
      return
    }

    engine.speak(trimmedText, resolveSpeakOptions(options))
  }

  fun stop() {
    if (!initialized) {
      return
    }

    engine.stop()
  }

  fun shutdown() {
    if (!initialized) {
      return
    }

    engine.shutdown()
    initialized = false
  }

  fun deinitialize() {
    shutdown()
  }

  fun hasLanguageSupport(language: String): Boolean {
    assertInitialized()
    return findBestVoiceForLanguage(engine.voices(), language) != null
  }

  private fun resolveSpeakOptions(options: TtsSpeakOptions): TtsSpeakOptions {
    if (options.language.isNullOrBlank() || !options.voice.isNullOrBlank()) {
      return options
    }

    val matchedVoice = findBestVoiceForLanguage(engine.voices(), options.language)
      ?: return options

    return options.copy(
      language = matchedVoice.language,
      voice = matchedVoice.identifier,
    )
  }

  private fun assertInitialized() {
    if (!initialized) {
      throw TtsException(
        message = "TTS not initialized - call initialize() first.",
        code = TtsErrorCodes.NOT_INITIALIZED,
      )
    }
  }
}
