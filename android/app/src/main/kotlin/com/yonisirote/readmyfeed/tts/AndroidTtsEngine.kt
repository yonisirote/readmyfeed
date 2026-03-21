package com.yonisirote.readmyfeed.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID

private const val ttsInitTimeoutMs = 10_000L
private const val ttsSpeechTimeoutBaseMs = 15_000L
private const val ttsSpeechTimeoutPerCharacterMs = 100L
private const val enhancedVoiceQualityThreshold = 400
private const val defaultPitch = 1.0f
private const val defaultRate = 1.0f

class AndroidTtsEngine(
  context: Context,
  private val createTextToSpeech: (Context, TextToSpeech.OnInitListener) -> TextToSpeech = { appContext, listener ->
    TextToSpeech(appContext, listener)
  },
) : TtsEngine {
  private val applicationContext = context.applicationContext
  private val initMutex = Mutex()
  private val speakMutex = Mutex()
  private val activeUtteranceLock = Any()

  private var textToSpeech: TextToSpeech? = null
  private var availableVoices: List<TtsVoice> = emptyList()
  private var initialized = false
  private var activeUtterance: ActiveUtterance? = null

  override suspend fun initialize() {
    if (initialized) {
      return
    }

    initMutex.withLock {
      if (initialized) {
        return
      }

      val initResult = CompletableDeferred<Int>()
      // Construct TextToSpeech on the main thread to match Android service expectations.
      val instance = withContext(Dispatchers.Main.immediate) {
        createTextToSpeech(applicationContext) { status ->
          if (!initResult.isCompleted) {
            initResult.complete(status)
          }
        }
      }

      try {
        val status = withTimeout(ttsInitTimeoutMs) {
          initResult.await()
        }

        if (status != TextToSpeech.SUCCESS) {
          throw TtsException(
            message = "Failed to initialize the TTS engine.",
            code = TtsErrorCodes.INITIALIZATION_FAILED,
            context = mapOf("status" to status),
          )
        }

        withContext(Dispatchers.Main.immediate) {
          // Route playback through speech-focused audio attributes for better ducking/routing.
          instance.setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build(),
          )
          instance.setOnUtteranceProgressListener(createUtteranceProgressListener())
        }

        val loadedVoices = withContext(Dispatchers.Main.immediate) {
          loadVoices(instance)
        }

        textToSpeech = instance
        availableVoices = loadedVoices
        initialized = true
      } catch (error: TimeoutCancellationException) {
        shutdownCreatedInstance(instance)

        throw TtsException(
          message = "Timed out while initializing the TTS engine.",
          code = TtsErrorCodes.INITIALIZATION_FAILED,
          context = mapOf("timeoutMs" to ttsInitTimeoutMs),
          cause = error,
        )
      } catch (error: CancellationException) {
        shutdownCreatedInstance(instance)
        throw error
      } catch (error: TtsException) {
        shutdownCreatedInstance(instance)
        throw error
      } catch (error: Exception) {
        shutdownCreatedInstance(instance)

        throw TtsException(
          message = "Failed to initialize the TTS engine.",
          code = TtsErrorCodes.INITIALIZATION_FAILED,
          cause = error,
        )
      }
    }
  }

  override fun voices(): List<TtsVoice> {
    return availableVoices.toList()
  }

  override suspend fun speak(text: String, options: TtsSpeakOptions) {
    val tts = requireTextToSpeech()

    // Android TTS keeps global engine state, so serialize callers around one active utterance.
    speakMutex.withLock {
      val utteranceId = UUID.randomUUID().toString()
      val completion = CompletableDeferred<Unit>()
      // Some engines never report completion, so bound each utterance by text length.
      val timeoutMs = ttsSpeechTimeoutBaseMs + (text.length * ttsSpeechTimeoutPerCharacterMs)

      setActiveUtterance(ActiveUtterance(utteranceId, completion))

      try {
        withContext(Dispatchers.Main.immediate) {
          applySpeakOptions(tts, options)

          val result = tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            buildUtteranceParams(options),
            utteranceId,
          )

          if (result == TextToSpeech.ERROR) {
            throw TtsException(
              message = "Failed to start TTS playback.",
              code = TtsErrorCodes.GENERATION_FAILED,
            )
          }
        }

        withTimeout(timeoutMs) {
          completion.await()
        }
      } catch (error: TimeoutCancellationException) {
        withContext(Dispatchers.Main.immediate) {
          tts.stop()
        }
        finishActiveUtterance(utteranceId)

        throw TtsException(
          message = "Speech playback timed out.",
          code = TtsErrorCodes.GENERATION_FAILED,
          context = mapOf(
            "textLength" to text.length,
            "timeoutMs" to timeoutMs,
          ),
          cause = error,
        )
      } catch (error: CancellationException) {
        withContext(Dispatchers.Main.immediate) {
          tts.stop()
        }
        finishActiveUtterance(utteranceId)
        throw error
      } catch (error: TtsException) {
        finishActiveUtterance(utteranceId)
        throw error
      } catch (error: Exception) {
        finishActiveUtterance(utteranceId)

        throw TtsException(
          message = "Failed to generate speech.",
          code = TtsErrorCodes.GENERATION_FAILED,
          context = mapOf("textLength" to text.length),
          cause = error,
        )
      } finally {
        clearActiveUtterance(utteranceId)
      }
    }
  }

  override fun stop() {
    val tts = textToSpeech ?: return
    tts.stop()
    finishActiveUtterance(null)
  }

  override fun shutdown() {
    val tts = textToSpeech ?: return

    tts.stop()
    finishActiveUtterance(null)
    tts.shutdown()

    textToSpeech = null
    availableVoices = emptyList()
    initialized = false
  }

  private fun applySpeakOptions(tts: TextToSpeech, options: TtsSpeakOptions) {
    tts.setPitch(options.pitch ?: defaultPitch)
    tts.setSpeechRate(options.rate ?: defaultRate)

    // An explicit voice should win because setLanguage can silently swap the engine's voice.
    if (!options.voice.isNullOrBlank()) {
      applyVoice(tts, options.voice)
      return
    }

    if (!options.language.isNullOrBlank()) {
      applyLanguage(tts, options.language)
    }
  }

  private fun applyVoice(tts: TextToSpeech, voiceId: String) {
    val voice = tts.voices.orEmpty().firstOrNull { it.name == voiceId }
      ?: throw TtsException(
        message = "Requested TTS voice is not installed.",
        code = TtsErrorCodes.VOICE_UNAVAILABLE,
        context = mapOf("voice" to voiceId),
      )

    val result = tts.setVoice(voice)
    if (result == TextToSpeech.ERROR) {
      throw TtsException(
        message = "Requested TTS voice could not be activated.",
        code = TtsErrorCodes.VOICE_UNAVAILABLE,
        context = mapOf("voice" to voiceId),
      )
    }
  }

  private fun applyLanguage(tts: TextToSpeech, language: String) {
    // Android returns several success-ish codes here, so only hard failures abort playback.
    when (tts.setLanguage(Locale.forLanguageTag(language))) {
      TextToSpeech.LANG_NOT_SUPPORTED -> {
        throw TtsException(
          message = "Requested TTS language is not supported on this device.",
          code = TtsErrorCodes.LANGUAGE_NOT_SUPPORTED,
          context = mapOf("language" to language),
        )
      }
      TextToSpeech.LANG_MISSING_DATA -> {
        throw TtsException(
          message = "Requested TTS language is missing voice data on this device.",
          code = TtsErrorCodes.LANGUAGE_DATA_MISSING,
          context = mapOf("language" to language),
        )
      }
    }
  }

  private fun buildUtteranceParams(options: TtsSpeakOptions): Bundle {
    return Bundle().apply {
      options.volume?.let {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, it)
      }
    }
  }

  private fun createUtteranceProgressListener(): UtteranceProgressListener {
    return object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) = Unit

      override fun onDone(utteranceId: String?) {
        finishActiveUtterance(utteranceId)
      }

      override fun onStop(utteranceId: String?, interrupted: Boolean) {
        // Treat stop as completion so cancelled/flush utterances still unblock suspended callers.
        finishActiveUtterance(utteranceId)
      }

      override fun onError(utteranceId: String?) {
        finishActiveUtterance(
          utteranceId,
          TtsException(
            message = "Speech playback failed.",
            code = TtsErrorCodes.GENERATION_FAILED,
          ),
        )
      }

      override fun onError(utteranceId: String?, errorCode: Int) {
        finishActiveUtterance(
          utteranceId,
          TtsException(
            message = "Speech playback failed.",
            code = TtsErrorCodes.GENERATION_FAILED,
            context = mapOf("errorCode" to errorCode),
          ),
        )
      }
    }
  }

  private fun loadVoices(tts: TextToSpeech): List<TtsVoice> {
    return tts.voices.orEmpty().mapNotNull { voice ->
      val language = voice.locale?.toLanguageTag().orEmpty()
      val identifier = voice.name.orEmpty()

      // Ignore malformed placeholder voices that do not expose a real locale or identifier.
      if (language.isBlank() || language == "und" || identifier.isBlank()) {
        return@mapNotNull null
      }

      TtsVoice(
        identifier = identifier,
        language = language,
        quality = voice.toTtsVoiceQuality(),
        requiresNetwork = voice.isNetworkConnectionRequired,
      )
    }
  }

  private fun Voice.toTtsVoiceQuality(): TtsVoiceQuality {
    // Android exposes opaque vendor quality integers, so map them into coarse app-level buckets.
    return when {
      quality >= enhancedVoiceQualityThreshold -> TtsVoiceQuality.ENHANCED
      quality > 0 -> TtsVoiceQuality.DEFAULT
      else -> TtsVoiceQuality.UNKNOWN
    }
  }

  private fun requireTextToSpeech(): TextToSpeech {
    return textToSpeech ?: throw TtsException(
      message = "TTS engine is not initialized.",
      code = TtsErrorCodes.NOT_INITIALIZED,
    )
  }

  private suspend fun shutdownCreatedInstance(instance: TextToSpeech) {
    withContext(Dispatchers.Main.immediate) {
      instance.shutdown()
    }
  }

  private fun setActiveUtterance(value: ActiveUtterance) {
    synchronized(activeUtteranceLock) {
      activeUtterance = value
    }
  }

  private fun finishActiveUtterance(utteranceId: String?, error: Throwable? = null) {
    val current = synchronized(activeUtteranceLock) {
      val candidate = activeUtterance
      // Ignore stale callbacks from earlier utterances after stop/shutdown or a queued retry.
      if (candidate == null || (utteranceId != null && candidate.id != utteranceId)) {
        null
      } else {
        activeUtterance = null
        candidate
      }
    } ?: return

    if (error == null) {
      current.completion.complete(Unit)
    } else {
      current.completion.completeExceptionally(error)
    }
  }

  private fun clearActiveUtterance(utteranceId: String) {
    synchronized(activeUtteranceLock) {
      if (activeUtterance?.id == utteranceId) {
        activeUtterance = null
      }
    }
  }

  private data class ActiveUtterance(
    val id: String,
    val completion: CompletableDeferred<Unit>,
  )
}
