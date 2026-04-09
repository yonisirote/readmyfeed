package com.yonisirote.readmyfeed.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPlaybackControllerTest {
  @Test
  fun reportsNoItemsWithoutStartingPlayback() {
    val events = mutableListOf<String>()
    val scope = TestPlaybackScope()
    val controller = ScreenPlaybackController<String>(
      coroutineScope = scope.scope,
      hasSpeakableItems = { false },
      speak = { _, _ -> error("Should not speak") },
      stopPlayback = { events += "stop" },
      renderLoadingStatus = { events += "loading" },
      renderProgressStatus = { index, total -> events += "progress:$index/$total" },
      renderNoItemsStatus = { events += "no-items" },
      renderFinishedStatus = { events += "finished" },
      renderErrorStatus = { message -> events += "error:$message" },
      onPlaybackStateChanged = { isPlaying -> events += "playing:$isPlaying" },
    )

    controller.start(listOf("a"))

    assertFalse(controller.isPlaying())
    assertEquals(listOf("no-items", "playing:false"), events)
    scope.close()
  }

  @Test
  fun playsItemsAndReportsLifecycleEvents() = runBlocking {
    val events = mutableListOf<String>()
    val scope = TestPlaybackScope()
    val controller = ScreenPlaybackController<String>(
      coroutineScope = scope.scope,
      hasSpeakableItems = { true },
      speak = { items, onItemStart ->
        items.forEachIndexed { index, item ->
          onItemStart(item, index + 1, items.size)
        }
        TtsPlaybackSummary(
          totalItems = items.size,
          speakableItems = items.size,
          spokenItems = items.size,
          skippedItems = 0,
        )
      },
      stopPlayback = { events += "stop" },
      renderLoadingStatus = { events += "loading" },
      renderProgressStatus = { index, total -> events += "progress:$index/$total" },
      renderNoItemsStatus = { events += "no-items" },
      renderFinishedStatus = { summary -> events += "finished:${summary.spokenItems}" },
      renderErrorStatus = { message -> events += "error:$message" },
      onPlaybackStateChanged = { isPlaying -> events += "playing:$isPlaying" },
    )

    controller.start(listOf("a", "b"))
    scope.joinChildren()

    assertFalse(controller.isPlaying())
    assertEquals(
      listOf(
        "playing:true",
        "loading",
        "progress:1/2",
        "progress:2/2",
        "finished:2",
        "playing:false",
      ),
      events,
    )
    scope.close()
  }

  @Test
  fun stopCancelsPlaybackAndRendersProvidedStatus() = runBlocking {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val events = mutableListOf<String>()
    val scope = TestPlaybackScope()
    val controller = ScreenPlaybackController<String>(
      coroutineScope = scope.scope,
      hasSpeakableItems = { true },
      speak = { _, _ ->
        started.complete(Unit)
        release.await()
        TtsPlaybackSummary(
          totalItems = 1,
          speakableItems = 1,
          spokenItems = 1,
          skippedItems = 0,
        )
      },
      stopPlayback = { events += "stop" },
      renderLoadingStatus = { events += "loading" },
      renderProgressStatus = { _, _ -> Unit },
      renderNoItemsStatus = { events += "no-items" },
      renderFinishedStatus = { events += "finished" },
      renderErrorStatus = { message -> events += "error:$message" },
      onPlaybackStateChanged = { isPlaying -> events += "playing:$isPlaying" },
    )

    controller.start(listOf("a"))
    started.await()
    controller.stop { events += "status" }
    release.complete(Unit)
    scope.joinChildren()

    assertFalse(controller.isPlaying())
    assertEquals(
      listOf(
        "playing:true",
        "loading",
        "stop",
        "playing:false",
        "status",
      ),
      events,
    )
    assertFalse(events.contains("finished"))
    scope.close()
  }

  @Test
  fun rendersPlaybackErrorsAndResetsState() = runBlocking {
    val events = mutableListOf<String>()
    val scope = TestPlaybackScope()
    val controller = ScreenPlaybackController<String>(
      coroutineScope = scope.scope,
      hasSpeakableItems = { true },
      speak = { _, _ -> throw TtsException("tts failed", code = "FAIL") },
      stopPlayback = { events += "stop" },
      renderLoadingStatus = { events += "loading" },
      renderProgressStatus = { _, _ -> Unit },
      renderNoItemsStatus = { events += "no-items" },
      renderFinishedStatus = { events += "finished" },
      renderErrorStatus = { message -> events += "error:$message" },
      onPlaybackStateChanged = { isPlaying -> events += "playing:$isPlaying" },
    )

    controller.start(listOf("a"))
    scope.joinChildren()

    assertFalse(controller.isPlaying())
    assertEquals(
      listOf(
        "playing:true",
        "loading",
        "error:tts failed",
        "playing:false",
      ),
      events,
    )
    scope.close()
  }

  private class TestPlaybackScope {
    private val job = Job()
    val scope = CoroutineScope(Dispatchers.Unconfined + job)

    suspend fun joinChildren() {
      job.children.forEach { child -> child.join() }
    }

    fun close() {
      scope.cancel()
    }
  }
}
