package com.yonisirote.readmyfeed.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenPlaybackController<T>(
  private val coroutineScope: CoroutineScope,
  private val hasSpeakableItems: (List<T>) -> Boolean,
  private val speak: suspend (items: List<T>, onItemStart: (T, Int, Int) -> Unit) -> TtsPlaybackSummary,
  private val stopPlayback: () -> Unit,
  private val renderLoadingStatus: () -> Unit,
  private val renderProgressStatus: (Int, Int) -> Unit,
  private val renderNoItemsStatus: () -> Unit,
  private val renderFinishedStatus: (TtsPlaybackSummary) -> Unit,
  private val renderErrorStatus: (String?) -> Unit,
  private val onPlaybackStateChanged: (Boolean) -> Unit,
) {
  private var playbackJob: Job? = null
  private var isPlaying = false

  fun isPlaying(): Boolean {
    return isPlaying
  }

  fun start(items: List<T>): Unit {
    if (!hasSpeakableItems(items)) {
      renderNoItemsStatus()
      setPlaybackState(isPlaying = false, forceNotify = true)
      return
    }

    playbackJob?.cancel()
    playbackJob = coroutineScope.launch {
      setPlaybackState(isPlaying = true)
      renderLoadingStatus()

      try {
        val summary = withContext(Dispatchers.IO) {
          speak(items) { _, index, total ->
            renderProgressStatus(index, total)
          }
        }

        if (!isPlaying) {
          return@launch
        }

        renderFinishedStatus(summary)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        if (isPlaying) {
          val message = if (error is TtsException) {
            error.message
          } else {
            error.message ?: "Unknown error."
          }
          renderErrorStatus(message)
        }
      } finally {
        setPlaybackState(isPlaying = false)
        playbackJob = null
      }
    }
  }

  fun stop(status: (() -> Unit)? = null): Unit {
    playbackJob?.cancel()
    playbackJob = null
    stopPlayback()
    setPlaybackState(isPlaying = false, forceNotify = true)
    status?.invoke()
  }

  fun shutdown() {
    playbackJob?.cancel()
    playbackJob = null
    stopPlayback()
    setPlaybackState(isPlaying = false)
  }

  private fun setPlaybackState(
    isPlaying: Boolean,
    forceNotify: Boolean = false,
  ) {
    if (!forceNotify && this.isPlaying == isPlaying) {
      return
    }

    this.isPlaying = isPlaying
    onPlaybackStateChanged(isPlaying)
  }
}
