package com.yonisirote.readmyfeed

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.tts.AndroidTtsEngine
import com.yonisirote.readmyfeed.tts.TtsException
import com.yonisirote.readmyfeed.tts.TtsService
import com.yonisirote.readmyfeed.tts.x.XTimelineSpeechPlayer
import com.yonisirote.readmyfeed.x.auth.AndroidWebViewCookieReader
import com.yonisirote.readmyfeed.x.auth.PreferencesXSessionStore
import com.yonisirote.readmyfeed.x.auth.XAuthErrorCodes
import com.yonisirote.readmyfeed.x.auth.XAuthException
import com.yonisirote.readmyfeed.x.auth.XAuthService
import com.yonisirote.readmyfeed.x.auth.XLoginCaptureCoordinator
import com.yonisirote.readmyfeed.x.auth.X_LOGIN_URL
import com.yonisirote.readmyfeed.x.auth.clearXWebViewCookies
import com.yonisirote.readmyfeed.x.timeline.XFollowingTimelineBatch
import com.yonisirote.readmyfeed.x.timeline.XFollowingTimelineRequest
import com.yonisirote.readmyfeed.x.timeline.XTimelineErrorCodes
import com.yonisirote.readmyfeed.x.timeline.XTimelineException
import com.yonisirote.readmyfeed.x.timeline.XTimelineItem
import com.yonisirote.readmyfeed.x.timeline.XTimelineService
import com.yonisirote.readmyfeed.x.timeline.mergeTimelineItems
import com.yonisirote.readmyfeed.x.timeline.shouldPrefetchTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding
  private lateinit var authService: XAuthService
  private lateinit var captureCoordinator: XLoginCaptureCoordinator
  private lateinit var timelineService: XTimelineService
  private lateinit var ttsService: TtsService
  private lateinit var timelineSpeechPlayer: XTimelineSpeechPlayer
  private lateinit var feedAdapter: XTimelineFeedAdapter

  private var timelineItems: List<XTimelineItem> = emptyList()
  private var nextCursor: String? = null
  private var hasStoredSession = false
  private var captureInFlight = false
  private var didCapture = false
  private var isFetchingInitial = false
  private var isFetchingMore = false
  private var isSpeakingFeed = false
  private var captureJob: Job? = null
  private var fetchJob: Job? = null
  private var speakJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    if (!initializeDependencies()) {
      return
    }

    setupRecyclerView()
    setupButtons()
    setupWebView()
    setupBackPressHandler()
    renderConnectPrompt()
    maybeLoadStoredSession()
  }

  override fun onDestroy() {
    captureJob?.cancel()
    fetchJob?.cancel()
    speakJob?.cancel()
    timelineSpeechPlayer.stop()
    timelineSpeechPlayer.shutdown()
    binding.feedRecyclerView.adapter = null
    binding.xWebView.stopLoading()
    binding.xWebView.destroy()
    super.onDestroy()
  }

  private fun initializeDependencies(): Boolean {
    return try {
      val cookieReader = AndroidWebViewCookieReader()
      val sessionStore = PreferencesXSessionStore(applicationContext)
      authService = XAuthService(cookieReader, sessionStore)
      captureCoordinator = XLoginCaptureCoordinator(authService)
      timelineService = XTimelineService(authService)
      ttsService = TtsService(AndroidTtsEngine(applicationContext))
      timelineSpeechPlayer = XTimelineSpeechPlayer(ttsService)
      feedAdapter = XTimelineFeedAdapter()
      true
    } catch (error: XAuthException) {
      renderError(error.message ?: getString(R.string.generic_auth_error), preserveFeed = false)
      binding.connectButton.isEnabled = false
      binding.refreshButton.isEnabled = false
      binding.loadMoreButton.isEnabled = false
      binding.playFeedButton.isEnabled = false
      binding.stopFeedButton.isEnabled = false
      false
    }
  }

  private fun setupRecyclerView() {
    val layoutManager = LinearLayoutManager(this)
    binding.feedRecyclerView.layoutManager = layoutManager
    binding.feedRecyclerView.adapter = feedAdapter
    binding.feedRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
      override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (dy <= 0) {
          return
        }

        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        if (shouldPrefetchTimeline(lastVisibleItem, timelineItems.size, nextCursor, isFetchingMore)) {
          fetchFollowingTimeline(append = true)
        }
      }
    })
  }

  private fun setupButtons() {
    binding.connectButton.setOnClickListener {
      if (isBusy()) {
        return@setOnClickListener
      }

      val shouldClearSession = hasStoredSession || timelineItems.isNotEmpty()
      startLoginFlow(clearExistingSession = shouldClearSession)
    }

    binding.refreshButton.setOnClickListener {
      if (isBusy()) {
        return@setOnClickListener
      }

      if (!hasStoredSession) {
        startLoginFlow(clearExistingSession = false)
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
      stopFeedPlayback(getString(R.string.feed_speech_status_stopped))
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(binding.xWebView, true)

    binding.xWebView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      cacheMode = WebSettings.LOAD_DEFAULT
      loadsImagesAutomatically = true
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
      setSupportMultipleWindows(false)
    }

    binding.xWebView.isVerticalScrollBarEnabled = false
    binding.xWebView.isHorizontalScrollBarEnabled = false
    binding.xWebView.webViewClient = object : WebViewClient() {
      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        if (!captureInFlight && !didCapture && captureCoordinator.shouldCaptureOnNavigation(url)) {
          attemptCapture(strict = true)
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)

        if (!captureInFlight && !didCapture && captureCoordinator.shouldAttemptFallbackCapture(url)) {
          attemptCapture(strict = false)
        }
      }

      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return false
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
      ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == false) {
          return
        }

        renderError(
          error?.description?.toString().orEmpty().ifBlank { getString(R.string.generic_auth_error) },
          preserveFeed = timelineItems.isNotEmpty(),
        )
      }
    }
  }

  private fun setupBackPressHandler() {
    onBackPressedDispatcher.addCallback(this) {
      if (binding.xWebView.isVisible && binding.xWebView.canGoBack()) {
        binding.xWebView.goBack()
      } else {
        isEnabled = false
        onBackPressedDispatcher.onBackPressed()
      }
    }
  }

  private fun maybeLoadStoredSession() {
    val storedSession = safeLoadStoredSession() ?: run {
      hasStoredSession = false
      updateActionButtons()
      return
    }

    hasStoredSession = storedSession.isNotBlank()
    if (!hasStoredSession) {
      updateActionButtons()
      return
    }

    fetchFollowingTimeline(initialCookieString = storedSession, append = false)
  }

  private fun startLoginFlow(clearExistingSession: Boolean) {
    captureJob?.cancel()
    fetchJob?.cancel()
    stopFeedPlayback(null)
    isFetchingInitial = false
    isFetchingMore = false
    captureInFlight = false
    didCapture = false

    lifecycleScope.launch {
      if (clearExistingSession) {
        safeClearStoredSession()
        hasStoredSession = false
        timelineItems = emptyList()
        nextCursor = null
        feedAdapter.submitList(emptyList())
        binding.speechStatusTextView.text = getString(R.string.feed_speech_status_idle)
        clearXWebViewCookies()
      }

      renderLoginState()
      binding.xWebView.stopLoading()
      if (clearExistingSession) {
        binding.xWebView.clearHistory()
        binding.xWebView.clearCache(true)
      }
      binding.xWebView.loadUrl(X_LOGIN_URL)
      updateActionButtons()
    }
  }

  private fun attemptCapture(strict: Boolean) {
    if (captureInFlight || didCapture) {
      return
    }

    captureJob?.cancel()
    captureJob = lifecycleScope.launch {
      captureInFlight = true
      updateActionButtons()

      if (strict) {
        renderCapturingState()
      }

      try {
        val session = if (strict) {
          captureCoordinator.captureAndStoreSessionWithRetry()
        } else {
          captureCoordinator.captureAndStoreSessionOnce()
        }

        hasStoredSession = true
        didCapture = true
        fetchFollowingTimeline(initialCookieString = session.cookieString, append = false)
      } catch (error: XAuthException) {
        if (!strict && error.code == XAuthErrorCodes.COOKIE_MISSING_REQUIRED) {
          return@launch
        }

        renderError(error.message ?: getString(R.string.generic_auth_error), preserveFeed = false)
      } finally {
        captureInFlight = false
        updateActionButtons()
      }
    }
  }

  private fun fetchFollowingTimeline(
    initialCookieString: String? = null,
    append: Boolean,
  ) {
    if (append) {
      if (nextCursor.isNullOrBlank() || isFetchingMore || isFetchingInitial) {
        return
      }
      isFetchingMore = true
    } else {
      fetchJob?.cancel()
      isFetchingInitial = true
      if (timelineItems.isEmpty()) {
        renderFetchingState(showExistingFeed = false)
      } else {
        renderFetchingState(showExistingFeed = true)
      }
    }

    updateActionButtons()
    updateFeedSummary()

    val request = XFollowingTimelineRequest(
      cursor = if (append) nextCursor else null,
      cookieString = initialCookieString,
    )

    fetchJob = lifecycleScope.launch {
      try {
        val batch = timelineService.fetchFollowingTimeline(request)
        applyTimelineBatch(batch, append)
      } catch (error: XTimelineException) {
        handleTimelineError(error)
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        renderError(getString(R.string.generic_timeline_error), preserveFeed = timelineItems.isNotEmpty())
      } finally {
        isFetchingInitial = false
        isFetchingMore = false
        updateActionButtons()
        updateFeedSummary()
      }
    }
  }

  private fun applyTimelineBatch(batch: XFollowingTimelineBatch, append: Boolean) {
    nextCursor = batch.nextCursor
    timelineItems = if (append) {
      mergeTimelineItems(timelineItems, batch.items)
    } else {
      mergeTimelineItems(emptyList(), batch.items)
    }

    feedAdapter.submitList(timelineItems)

    if (timelineItems.isEmpty()) {
      binding.speechStatusTextView.text = getString(R.string.feed_speech_status_no_items)
      updateSpeechControls()
      renderEmptyFeedState()
      return
    }

    if (!isSpeakingFeed) {
      binding.speechStatusTextView.text = getString(R.string.feed_speech_status_idle)
    }

    renderFeedState()
  }

  private fun handleTimelineError(error: XTimelineException) {
    val shouldClearStoredSession = error.code == XTimelineErrorCodes.SESSION_MISSING ||
      error.code == XTimelineErrorCodes.COOKIE_INVALID ||
      (error.code == XTimelineErrorCodes.REQUEST_FAILED && (error.context["status"] as? Int) in listOf(401, 403))

    if (shouldClearStoredSession) {
      safeClearStoredSession()
      hasStoredSession = false
      didCapture = false
    }

    renderError(
      error.message ?: getString(R.string.generic_timeline_error),
      preserveFeed = timelineItems.isNotEmpty(),
    )
  }

  private fun renderConnectPrompt() {
    renderStatus(
      title = getString(R.string.status_connect_title),
      body = getString(R.string.status_connect_body),
      titleColor = R.color.textPrimary,
      bodyColor = R.color.textSecondary,
    )
    renderEmptyState(
      title = getString(R.string.empty_title),
      body = getString(R.string.empty_body),
    )
    binding.xWebView.isVisible = false
    binding.feedContainer.isVisible = false
    binding.loadingOverlay.isVisible = false
    updateActionButtons()
  }

  private fun renderLoginState() {
    renderStatus(
      title = getString(R.string.status_login_title),
      body = getString(R.string.status_login_body),
      titleColor = R.color.textPrimary,
      bodyColor = R.color.textSecondary,
    )
    binding.emptyStateView.isVisible = false
    binding.feedContainer.isVisible = false
    binding.xWebView.isVisible = true
    binding.loadingOverlay.isVisible = false
    binding.loadingTextView.text = getString(R.string.loading_capture)
    updateSpeechControls()
  }

  private fun renderCapturingState() {
    renderStatus(
      title = getString(R.string.status_capturing_title),
      body = getString(R.string.status_capturing_body),
      titleColor = R.color.textPrimary,
      bodyColor = R.color.textSecondary,
    )
    binding.emptyStateView.isVisible = false
    binding.feedContainer.isVisible = false
    binding.xWebView.isVisible = true
    binding.loadingTextView.text = getString(R.string.loading_capture)
    binding.loadingOverlay.isVisible = true
    updateSpeechControls()
  }

  private fun renderFetchingState(showExistingFeed: Boolean) {
    renderStatus(
      title = getString(R.string.status_fetching_title),
      body = getString(R.string.status_fetching_body),
      titleColor = R.color.textPrimary,
      bodyColor = R.color.textSecondary,
    )

    binding.xWebView.isVisible = false
    binding.emptyStateView.isVisible = false
    binding.feedContainer.isVisible = showExistingFeed
    binding.loadingTextView.text = getString(R.string.loading_feed)
    binding.loadingOverlay.isVisible = true
    binding.feedSummaryTextView.text = if (showExistingFeed) {
      getString(R.string.feed_summary_fetching)
    } else {
      ""
    }
    updateSpeechControls()
  }

  private fun renderFeedState() {
    renderStatus(
      title = getString(R.string.status_ready_title),
      body = getString(R.string.status_ready_body, timelineItems.size),
      titleColor = R.color.success,
      bodyColor = R.color.textSecondary,
    )

    binding.emptyStateView.isVisible = false
    binding.xWebView.isVisible = false
    binding.feedContainer.isVisible = true
    binding.loadingOverlay.isVisible = false
    updateFeedSummary()
    updateActionButtons()
  }

  private fun renderEmptyFeedState() {
    renderStatus(
      title = getString(R.string.status_ready_title),
      body = getString(R.string.empty_feed_body),
      titleColor = R.color.textPrimary,
      bodyColor = R.color.textSecondary,
    )
    renderEmptyState(
      title = getString(R.string.empty_feed_title),
      body = getString(R.string.empty_feed_body),
    )
    binding.xWebView.isVisible = false
    binding.feedContainer.isVisible = false
    binding.loadingOverlay.isVisible = false
    updateActionButtons()
  }

  private fun renderError(message: String, preserveFeed: Boolean) {
    stopFeedPlayback(null)
    renderStatus(
      title = getString(R.string.status_error_title),
      body = message,
      titleColor = R.color.error,
      bodyColor = R.color.error,
    )
    binding.loadingOverlay.isVisible = false
    binding.xWebView.isVisible = false
    binding.feedContainer.isVisible = preserveFeed && timelineItems.isNotEmpty()

    if (binding.feedContainer.isVisible) {
      binding.emptyStateView.isVisible = false
      updateFeedSummary()
      binding.speechStatusTextView.text = buildFeedSpeechErrorMessage(message)
    } else {
      renderEmptyState(
        title = getString(R.string.status_error_title),
        body = message,
      )
    }

    updateActionButtons()
  }

  private fun renderStatus(
    title: String,
    body: String,
    titleColor: Int,
    bodyColor: Int,
  ) {
    binding.statusTitleTextView.text = title
    binding.statusTitleTextView.setTextColor(ContextCompat.getColor(this, titleColor))
    binding.statusBodyTextView.text = body
    binding.statusBodyTextView.setTextColor(ContextCompat.getColor(this, bodyColor))
  }

  private fun renderEmptyState(title: String, body: String) {
    binding.emptyTitleTextView.text = title
    binding.emptyBodyTextView.text = body
    binding.emptyStateView.isVisible = true
  }

  private fun updateFeedSummary() {
    if (!binding.feedContainer.isVisible) {
      return
    }

    binding.feedSummaryTextView.text = when {
      timelineItems.isEmpty() -> getString(R.string.feed_summary_fetching)
      isFetchingMore -> getString(R.string.feed_summary_loading_more, timelineItems.size)
      !nextCursor.isNullOrBlank() -> getString(R.string.feed_summary_ready_more, timelineItems.size)
      else -> getString(R.string.feed_summary_ready_end, timelineItems.size)
    }
  }

  private fun updateActionButtons() {
    val busy = isBusy()
    binding.connectButton.isEnabled = !busy
    binding.connectButton.text = getString(
      if (hasStoredSession || timelineItems.isNotEmpty()) R.string.reconnect_x else R.string.connect_x,
    )

    binding.refreshButton.isVisible = hasStoredSession && !binding.xWebView.isVisible
    binding.refreshButton.isEnabled = !busy && hasStoredSession

    binding.loadMoreButton.isVisible = binding.feedContainer.isVisible && !nextCursor.isNullOrBlank() && !isFetchingMore
    binding.loadMoreButton.isEnabled = !busy && !nextCursor.isNullOrBlank()
    binding.loadMoreProgressRow.isVisible = binding.feedContainer.isVisible && isFetchingMore

    updateSpeechControls()
  }

  private fun safeLoadStoredSession(): String? {
    return try {
      authService.loadStoredSession()
    } catch (error: XAuthException) {
      renderError(error.message ?: getString(R.string.generic_auth_error), preserveFeed = false)
      null
    }
  }

  private fun safeClearStoredSession() {
    try {
      authService.clearStoredSession()
    } catch (error: XAuthException) {
      renderError(error.message ?: getString(R.string.generic_auth_error), preserveFeed = timelineItems.isNotEmpty())
    }
  }

  private fun isBusy(): Boolean {
    return captureInFlight || isFetchingInitial || isFetchingMore
  }

  private fun startFeedPlayback() {
    if (!timelineSpeechPlayer.hasSpeakableItems(timelineItems)) {
      binding.speechStatusTextView.text = getString(R.string.feed_speech_status_no_items)
      updateSpeechControls()
      return
    }

    speakJob?.cancel()
    speakJob = lifecycleScope.launch {
      isSpeakingFeed = true
      updateSpeechControls()
      binding.speechStatusTextView.text = getString(R.string.feed_speech_status_loading)

      try {
        val summary = withContext(Dispatchers.IO) {
          timelineSpeechPlayer.speak(timelineItems) { _, index, total ->
            runOnUiThread {
              if (isSpeakingFeed) {
                binding.speechStatusTextView.text = getString(R.string.feed_speech_status_playing, index, total)
              }
            }
          }
        }

        if (!isSpeakingFeed) {
          return@launch
        }

        binding.speechStatusTextView.text = when {
          summary.spokenItems <= 0 -> getString(R.string.feed_speech_status_no_items)
          summary.skippedItems > 0 -> getString(
            R.string.feed_speech_status_skipped,
            summary.spokenItems,
            summary.skippedItems,
          )
          else -> getString(R.string.feed_speech_status_done, summary.spokenItems)
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

  private fun updateSpeechControls() {
    val canPlay = binding.feedContainer.isVisible &&
      timelineItems.isNotEmpty() &&
      !isBusy() &&
      !isSpeakingFeed

    binding.playFeedButton.isEnabled = canPlay
    binding.stopFeedButton.isEnabled = isSpeakingFeed
  }

  private fun buildFeedSpeechErrorMessage(message: String?): String {
    return getString(R.string.feed_speech_status_error_prefix) + " " + (message ?: "Unknown error.")
  }
}
