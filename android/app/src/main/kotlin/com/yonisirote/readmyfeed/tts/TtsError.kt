package com.yonisirote.readmyfeed.tts

class TtsException(
  message: String,
  val code: String,
  val context: Map<String, Any?> = emptyMap(),
  cause: Throwable? = null,
) : Exception(message, cause)

object TtsErrorCodes {
  const val INITIALIZATION_FAILED = "TTS_INITIALIZATION_FAILED"
  const val GENERATION_FAILED = "TTS_GENERATION_FAILED"
  const val NOT_INITIALIZED = "TTS_NOT_INITIALIZED"
  const val LANGUAGE_NOT_SUPPORTED = "TTS_LANGUAGE_NOT_SUPPORTED"
  const val LANGUAGE_DATA_MISSING = "TTS_LANGUAGE_DATA_MISSING"
  const val VOICE_UNAVAILABLE = "TTS_VOICE_UNAVAILABLE"
}
