package com.yonisirote.readmyfeed.providers.x.ui

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.x.speech.XTimelineSpeechPlayer
import com.yonisirote.readmyfeed.providers.x.timeline.XFollowingTimelineBatch
import com.yonisirote.readmyfeed.providers.x.timeline.XFollowingTimelineRequest
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineException
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineItem
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineService
import com.yonisirote.readmyfeed.providers.x.timeline.mergeTimelineItems
import com.yonisirote.readmyfeed.providers.x.timeline.shouldClearXTimelineSession
import com.yonisirote.readmyfeed.providers.x.timeline.shouldPrefetchTimeline
import com.yonisirote.readmyfeed.shell.XDestination
import com.yonisirote.readmyfeed.tts.TtsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class XTimelineScreenController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val timelineService: XTimelineService,
  private val timelineSpeechPlayer: XTimelineSpeechPlayer,
  private val feedAdapter: XTimelineFeedAdapter,
  private val isContentListVisible: () -> Boolean,
  private val showProviderScreen: (XDestination) -> Unit,
  private val showHome: () -> Unit,
  private val requestLogin: () -> Unit,
  private val clearStoredSession: () -> Unit,
  private val onSessionAvailabilityChanged: (Boolean) -> Unit,
) {
  private var timelineItems: List<XTimelineItem> = emptyList()
  private var nextCursor: String? = null
  private var hasStoredSession = false
  private var isFetchingInitial = false
  private var isFetchingMore = false
  private var isSpeakingFeed = false
  private var lastFeedLoadErrorMessage: String? = null
  private var fetchJob: Job? = null
  private var speakJob: Job? = null

  fun initialize() {
    setupRecyclerView()
    setupFeedScreen()
    updateFeedControls()
  }

  fun render(isVisible: Boolean) {
    syncLoadingOverlay(isVisible)
    updateFeedControls()
  }

  fun setHasStoredSession(value: Boolean) {
    hasStoredSession = value
    updateFeedControls()
  }

  fun prepareForLoginFlow(clearExistingSession: Boolean) {
    fetchJob?.cancel()
    isFetchingInitial = false
    isFetchingMore = false
    stopFeedPlayback(null)

    if (clearExistingSession) {
      timelineItems = emptyList()
      nextCursor = null
      lastFeedLoadErrorMessage = null
      feedAdapter.submitList(emptyList())
      binding.speechStatusTextView.text = activity.getString(R.string.feed_speech_status_idle)
    }

    syncLoadingOverlay(isVisible = false)
    updateFeedControls()
  }

  fun fetchFollowingTimeline(
    initialCookieString: String? = null,
    append: Boolean,
  ) {
    if (append) {
      if (!canLoadMoreContent(hasStoredSession, nextCursor, isFetchingInitial, isFetchingMore)) {
        return
      }
      isFetchingMore = true
    } else {
      fetchJob?.cancel()
      isFetchingInitial = true
      lastFeedLoadErrorMessage = null
      syncLoadingOverlay(isContentListVisible())
    }

    updateFeedControls()

    val request = XFollowingTimelineRequest(
      cursor = if (append) nextCursor else null,
      cookieString = initialCookieString,
    )

    fetchJob = activity.lifecycleScope.launch {
      try {
        val batch = timelineService.fetchFollowingTimeline(request)
        applyTimelineBatch(batch, append)
      } catch (error: XTimelineException) {
        handleTimelineError(error)
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        handleUnexpectedTimelineError()
      } finally {
        isFetchingInitial = false
        isFetchingMore = false
        syncLoadingOverlay(isContentListVisible())
        updateFeedControls()
      }
    }
  }

  fun handleBackPress(): Boolean {
    if (!isBusy()) {
      stopFeedPlayback(null)
      showHome()
    }

    return true
  }

  fun onDestroy() {
    fetchJob?.cancel()
    speakJob?.cancel()
    timelineSpeechPlayer.stop()
    timelineSpeechPlayer.shutdown()
    binding.feedRecyclerView.adapter = null
  }

  private fun setupFeedScreen() {
    binding.backFromFeed.setOnClickListener {
      if (isBusy()) {
        return@setOnClickListener
      }

      stopFeedPlayback(null)
      showHome()
    }

    binding.refreshButton.setOnClickListener {
      if (isBusy()) {
        return@setOnClickListener
      }

      if (!hasStoredSession) {
        requestLogin()
        return@setOnClickListener
      }

      fetchFollowingTimeline(append = false)
    }

    binding.loadMoreButton.setOnClickListener {
      if (isBusy()) {
        return@setOnClickListener
      }

      fetchFollowingTimeline(append = true)
    }

    binding.playFeedButton.setOnClickListener {
      if (isBusy() || isSpeakingFeed) {
        return@setOnClickListener
      }

      startFeedPlayback()
    }

    binding.stopFeedButton.setOnClickListener {
      stopFeedPlayback(activity.getString(R.string.feed_speech_status_stopped))
    }
  }

  private fun setupRecyclerView() {
    val layoutManager = LinearLayoutManager(activity)
    binding.feedRecyclerView.layoutManager = layoutManager
    binding.feedRecyclerView.adapter = feedAdapter
    binding.feedRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
      override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (dy <= 0 || !canLoadMoreContent(hasStoredSession, nextCursor, isFetchingInitial, isFetchingMore)) {
          return
        }

        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        if (shouldPrefetchTimeline(lastVisibleItem, timelineItems.size, nextCursor, isFetchingMore)) {
          fetchFollowingTimeline(append = true)
        }
      }
    })
  }

  private fun applyTimelineBatch(batch: XFollowingTimelineBatch, append: Boolean) {
    nextCursor = batch.nextCursor
    lastFeedLoadErrorMessage = null
    timelineItems = if (append) {
      mergeTimelineItems(timelineItems, batch.items)
    } else {
      mergeTimelineItems(emptyList(), batch.items)
    }

    feedAdapter.submitList(timelineItems)

    if (!isContentListVisible()) {
      showProviderScreen(XDestination.CONTENT_LIST)
    }

    binding.speechStatusTextView.text = when {
      timelineItems.isEmpty() -> activity.getString(R.string.feed_speech_status_no_items)
      !isSpeakingFeed -> activity.getString(R.string.feed_speech_status_idle)
      else -> binding.speechStatusTextView.text
    }
  }

  private fun handleTimelineError(error: XTimelineException) {
    val message = error.message ?: activity.getString(R.string.generic_timeline_error)
    lastFeedLoadErrorMessage = message

    // Certain timeline failures mean the stored browser session is no longer trustworthy.
    if (shouldClearXTimelineSession(error)) {
      clearStoredSession()
      hasStoredSession = false
      nextCursor = null
      onSessionAvailabilityChanged(false)
    }

    showFeedError(message)
  }

  private fun handleUnexpectedTimelineError() {
    val message = activity.getString(R.string.generic_timeline_error)
    lastFeedLoadErrorMessage = message
    showFeedError(message)
  }

  private fun updateFeedControls() {
    val busy = isBusy()

    binding.refreshButton.isEnabled = !busy && hasStoredSession
    binding.loadMoreButton.isVisible = shouldShowLoadMoreContentButton(
      isConnected = hasStoredSession,
      nextPageToken = nextCursor,
      isFetchingMore = isFetchingMore,
    )
    binding.loadMoreButton.isEnabled = shouldEnableLoadMoreContentButton(
      isConnected = hasStoredSession,
      nextPageToken = nextCursor,
      isBusy = busy,
    )
    binding.loadMoreProgressRow.isVisible = isFetchingMore

    updateFeedSummary()
    updateSpeechControls()
  }

  private fun updateFeedSummary() {
    val summary = resolveContentListSummaryModel(
      itemCount = timelineItems.size,
      isConnected = hasStoredSession,
      nextPageToken = nextCursor,
      isFetchingInitial = isFetchingInitial,
      isFetchingMore = isFetchingMore,
      lastLoadErrorMessage = lastFeedLoadErrorMessage,
    )

    binding.feedSummaryTextView.text = when (summary.state) {
      ContentListSummaryState.FETCHING -> activity.getString(R.string.feed_summary_fetching)
      ContentListSummaryState.EMPTY -> activity.getString(R.string.empty_feed_body)
      ContentListSummaryState.ERROR -> summary.errorMessage ?: activity.getString(R.string.generic_timeline_error)
      ContentListSummaryState.RECONNECT -> activity.getString(R.string.feed_summary_reconnect, timelineItems.size)
      ContentListSummaryState.LOADING_MORE -> activity.getString(R.string.feed_summary_loading_more, timelineItems.size)
      ContentListSummaryState.READY_MORE -> activity.getString(R.string.feed_summary_ready_more, timelineItems.size)
      ContentListSummaryState.READY_END -> activity.getString(R.string.feed_summary_ready_end, timelineItems.size)
    }
  }

  private fun updateSpeechControls() {
    val canPlay = isContentListVisible() &&
      timelineItems.isNotEmpty() &&
      !isBusy() &&
      !isSpeakingFeed

    binding.playFeedButton.isEnabled = canPlay
    binding.stopFeedButton.isEnabled = isSpeakingFeed
  }

  private fun startFeedPlayback() {
    if (!timelineSpeechPlayer.hasSpeakableItems(timelineItems)) {
      binding.speechStatusTextView.text = activity.getString(R.string.feed_speech_status_no_items)
      updateSpeechControls()
      return
    }

    speakJob?.cancel()
    speakJob = activity.lifecycleScope.launch {
      isSpeakingFeed = true
      updateSpeechControls()
      binding.speechStatusTextView.text = activity.getString(R.string.feed_speech_status_loading)

      try {
        val summary = withContext(Dispatchers.IO) {
          timelineSpeechPlayer.speak(timelineItems) { _, index, total ->
            activity.runOnUiThread {
              if (isSpeakingFeed) {
                binding.speechStatusTextView.text = activity.getString(
                  R.string.feed_speech_status_playing,
                  index,
                  total,
                )
              }
            }
          }
        }

        if (!isSpeakingFeed) {
          return@launch
        }

        binding.speechStatusTextView.text = when {
          summary.spokenItems <= 0 -> activity.getString(R.string.feed_speech_status_no_items)
          summary.skippedItems > 0 -> activity.getString(
            R.string.feed_speech_status_skipped,
            summary.spokenItems,
            summary.skippedItems,
          )
          else -> activity.getString(R.string.feed_speech_status_done, summary.spokenItems)
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        if (isSpeakingFeed) {
          val message = if (error is TtsException) {
            error.message
          } else {
            error.message ?: "Unknown error."
          }
          binding.speechStatusTextView.text = buildFeedSpeechErrorMessage(message)
        }
      } finally {
        isSpeakingFeed = false
        speakJob = null
        updateSpeechControls()
      }
    }
  }

  private fun stopFeedPlayback(status: String?) {
    speakJob?.cancel()
    speakJob = null
    timelineSpeechPlayer.stop()
    isSpeakingFeed = false
    updateSpeechControls()

    if (status != null) {
      binding.speechStatusTextView.text = status
    }
  }

  private fun showFeedError(message: String) {
    stopFeedPlayback(null)

    if (!isContentListVisible()) {
      showProviderScreen(XDestination.CONTENT_LIST)
    }

    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    binding.speechStatusTextView.text = buildFeedSpeechErrorMessage(message)
  }

  private fun syncLoadingOverlay(isVisible: Boolean) {
    val shouldShow = isVisible && isFetchingInitial && timelineItems.isEmpty()
    binding.loadingOverlay.isVisible = shouldShow
    if (shouldShow) {
      binding.loadingTextView.text = activity.getString(R.string.loading_feed)
    }
  }

  private fun isBusy(): Boolean {
    return isFetchingInitial || isFetchingMore
  }

  private fun buildFeedSpeechErrorMessage(message: String?): String {
    return activity.getString(R.string.feed_speech_status_error_prefix) + " " + (message ?: "Unknown error.")
  }
}
