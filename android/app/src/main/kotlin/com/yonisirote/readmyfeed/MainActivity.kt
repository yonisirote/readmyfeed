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
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.tts.AndroidTtsEngine
import com.yonisirote.readmyfeed.tts.TtsException
import com.yonisirote.readmyfeed.tts.TtsService
import com.yonisirote.readmyfeed.x.auth.AndroidWebViewCookieReader
import com.yonisirote.readmyfeed.x.auth.PreferencesXSessionStore
import com.yonisirote.readmyfeed.x.auth.XAuthErrorCodes
import com.yonisirote.readmyfeed.x.auth.XAuthException
import com.yonisirote.readmyfeed.x.auth.XAuthService
import com.yonisirote.readmyfeed.x.auth.XLoginCaptureCoordinator
import com.yonisirote.readmyfeed.x.auth.X_LOGIN_URL
import com.yonisirote.readmyfeed.x.auth.clearXWebViewCookies
import com.yonisirote.readmyfeed.x.speech.XTimelineSpeechPlayer
import com.yonisirote.readmyfeed.x.timeline.XFollowingTimelineBatch
import com.yonisirote.readmyfeed.x.timeline.XFollowingTimelineRequest
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
  private var lastFeedLoadErrorMessage: String? = null
  private var captureJob: Job? = null
  private var fetchJob: Job? = null
  private var speakJob: Job? = null

  private enum class Screen { HOME, SIGN_IN, FEED }
  private var currentScreen = Screen.HOME

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    if (!initializeDependencies()) {
      return
    }

    setupRecyclerView()
    setupHomeScreen()
    setupSignInScreen()
    setupFeedScreen()
    setupWebView()
    setupBackPressHandler()
    showScreen(Screen.HOME)
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
      Toast.makeText(this, error.message ?: getString(R.string.generic_auth_error), Toast.LENGTH_LONG).show()
      false
    }
  }

  // ── Screen navigation ──────────────────────────────────────────────

  private fun showScreen(screen: Screen) {
    currentScreen = screen
    binding.homeScreen.isVisible = screen == Screen.HOME
    binding.xSignInScreen.isVisible = screen == Screen.SIGN_IN
    binding.feedScreen.isVisible = screen == Screen.FEED
    binding.loadingOverlay.isVisible = false
  }

  // ── Home screen ────────────────────────────────────────────────────

  private fun setupHomeScreen() {
    binding.homeCardX.setOnClickListener {
      if (hasStoredSession) {
        showScreen(Screen.FEED)
        fetchFollowingTimeline(append = false)
      } else {
        startLoginFlow(clearExistingSession = false)
      }
    }
  }

  // ── Sign-in screen ─────────────────────────────────────────────────

  private fun setupSignInScreen() {
    binding.backFromSignIn.setOnClickListener {
      binding.xWebView.stopLoading()
      captureJob?.cancel()
      captureInFlight = false
      didCapture = false
      showScreen(Screen.HOME)
    }
  }

  private fun showSignInStatus(text: String) {
    binding.signInStatusBar.isVisible = true
    binding.signInProgressBar.isVisible = true
    binding.signInStatusText.text = text
    binding.signInStatusText.setTextColor(getColor(R.color.textSecondary))
  }

  private fun hideSignInStatus() {
    binding.signInStatusBar.isVisible = false
  }

  // ── Feed screen ────────────────────────────────────────────────────

  private fun setupFeedScreen() {
    binding.backFromFeed.setOnClickListener {
      if (isBusy()) {
        return@setOnClickListener
      }
      stopFeedPlayback(null)
      showScreen(Screen.HOME)
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

  // ── RecyclerView ───────────────────────────────────────────────────

  private fun setupRecyclerView() {
    val layoutManager = LinearLayoutManager(this)
    binding.feedRecyclerView.layoutManager = layoutManager
    binding.feedRecyclerView.adapter = feedAdapter
    binding.feedRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
      override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (dy <= 0 || !canLoadMoreTimeline(hasStoredSession, nextCursor, isFetchingInitial, isFetchingMore)) {
          return
        }

        val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
        if (shouldPrefetchTimeline(lastVisibleItem, timelineItems.size, nextCursor, isFetchingMore)) {
          fetchFollowingTimeline(append = true)
        }
      }
    })
  }

  // ── WebView ────────────────────────────────────────────────────────

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

        showSignInError(
          error?.description?.toString().orEmpty().ifBlank { getString(R.string.generic_auth_error) },
        )
      }
    }
  }

  // ── Back press ─────────────────────────────────────────────────────

  private fun setupBackPressHandler() {
    onBackPressedDispatcher.addCallback(this) {
      when (currentScreen) {
        Screen.SIGN_IN -> {
          if (binding.xWebView.canGoBack()) {
            binding.xWebView.goBack()
          } else {
            binding.xWebView.stopLoading()
            captureJob?.cancel()
            captureInFlight = false
            didCapture = false
            showScreen(Screen.HOME)
          }
        }
        Screen.FEED -> {
          if (!isBusy()) {
            stopFeedPlayback(null)
            showScreen(Screen.HOME)
          }
        }
        Screen.HOME -> {
          isEnabled = false
          onBackPressedDispatcher.onBackPressed()
        }
      }
    }
  }

  // ── Session bootstrap ──────────────────────────────────────────────

  private fun maybeLoadStoredSession() {
    val storedSession = safeLoadStoredSession() ?: run {
      hasStoredSession = false
      return
    }

    hasStoredSession = storedSession.isNotBlank()
  }

  // ── Login flow ─────────────────────────────────────────────────────

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
        lastFeedLoadErrorMessage = null
        feedAdapter.submitList(emptyList())
        binding.speechStatusTextView.text = getString(R.string.feed_speech_status_idle)
        clearXWebViewCookies()
      }

      showScreen(Screen.SIGN_IN)
      hideSignInStatus()
      binding.xWebView.stopLoading()
      if (clearExistingSession) {
        binding.xWebView.clearHistory()
        binding.xWebView.clearCache(true)
      }
      binding.xWebView.loadUrl(X_LOGIN_URL)
    }
  }

  // ── Cookie capture ─────────────────────────────────────────────────

  private fun attemptCapture(strict: Boolean) {
    if (captureInFlight || didCapture) {
      return
    }

    captureJob?.cancel()
    captureJob = lifecycleScope.launch {
      captureInFlight = true

      if (strict) {
        showSignInStatus(getString(R.string.status_capturing_body))
      }

      try {
        val session = if (strict) {
          captureCoordinator.captureAndStoreSessionWithRetry()
        } else {
          captureCoordinator.captureAndStoreSessionOnce()
        }

        hasStoredSession = true
        didCapture = true
        showScreen(Screen.FEED)
        fetchFollowingTimeline(initialCookieString = session.cookieString, append = false)
      } catch (error: XAuthException) {
        if (!strict && error.code == XAuthErrorCodes.COOKIE_MISSING_REQUIRED) {
          return@launch
        }

        showSignInError(error.message ?: getString(R.string.generic_auth_error))
      } finally {
        captureInFlight = false
      }
    }
  }

  // ── Timeline fetching ──────────────────────────────────────────────

  private fun fetchFollowingTimeline(
    initialCookieString: String? = null,
    append: Boolean,
  ) {
    if (append) {
      if (!canLoadMoreTimeline(hasStoredSession, nextCursor, isFetchingInitial, isFetchingMore)) {
        return
      }
      isFetchingMore = true
    } else {
      fetchJob?.cancel()
      isFetchingInitial = true
      lastFeedLoadErrorMessage = null
      if (currentScreen == Screen.FEED) {
        binding.loadingOverlay.isVisible = timelineItems.isEmpty()
        binding.loadingTextView.text = getString(R.string.loading_feed)
      }
    }

    updateFeedControls()

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
        handleUnexpectedTimelineError()
      } finally {
        isFetchingInitial = false
        isFetchingMore = false
        binding.loadingOverlay.isVisible = false
        updateFeedControls()
      }
    }
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

    if (currentScreen != Screen.FEED) {
      showScreen(Screen.FEED)
    }

    if (timelineItems.isEmpty()) {
      binding.speechStatusTextView.text = getString(R.string.feed_speech_status_no_items)
    } else if (!isSpeakingFeed) {
      binding.speechStatusTextView.text = getString(R.string.feed_speech_status_idle)
    }
  }

  private fun handleTimelineError(error: XTimelineException) {
    val message = error.message ?: getString(R.string.generic_timeline_error)
    lastFeedLoadErrorMessage = message

    if (shouldClearTimelineSession(error)) {
      safeClearStoredSession()
      hasStoredSession = false
      didCapture = false
      nextCursor = null
    }

    showFeedError(message)
  }

  private fun handleUnexpectedTimelineError() {
    val message = getString(R.string.generic_timeline_error)
    lastFeedLoadErrorMessage = message
    showFeedError(message)
  }

  // ── Feed controls ──────────────────────────────────────────────────

  private fun updateFeedControls() {
    val busy = isBusy()

    binding.refreshButton.isEnabled = !busy && hasStoredSession

    binding.loadMoreButton.isVisible = shouldShowLoadMoreButton(
      hasStoredSession = hasStoredSession,
      nextCursor = nextCursor,
      isFetchingMore = isFetchingMore,
    )
    binding.loadMoreButton.isEnabled = shouldEnableLoadMoreButton(
      hasStoredSession = hasStoredSession,
      nextCursor = nextCursor,
      isBusy = busy,
    )
    binding.loadMoreProgressRow.isVisible = isFetchingMore

    updateFeedSummary()
    updateSpeechControls()
  }

  private fun updateFeedSummary() {
    val summary = resolveFeedSummaryModel(
      itemCount = timelineItems.size,
      hasStoredSession = hasStoredSession,
      nextCursor = nextCursor,
      isFetchingInitial = isFetchingInitial,
      isFetchingMore = isFetchingMore,
      lastLoadErrorMessage = lastFeedLoadErrorMessage,
    )

    binding.feedSummaryTextView.text = when (summary.state) {
      FeedSummaryState.FETCHING -> getString(R.string.feed_summary_fetching)
      FeedSummaryState.EMPTY -> getString(R.string.empty_feed_body)
      FeedSummaryState.ERROR -> summary.errorMessage ?: getString(R.string.generic_timeline_error)
      FeedSummaryState.RECONNECT -> getString(R.string.feed_summary_reconnect, timelineItems.size)
      FeedSummaryState.LOADING_MORE -> getString(R.string.feed_summary_loading_more, timelineItems.size)
      FeedSummaryState.READY_MORE -> getString(R.string.feed_summary_ready_more, timelineItems.size)
      FeedSummaryState.READY_END -> getString(R.string.feed_summary_ready_end, timelineItems.size)
    }
  }

  private fun updateSpeechControls() {
    val canPlay = currentScreen == Screen.FEED &&
      timelineItems.isNotEmpty() &&
      !isBusy() &&
      !isSpeakingFeed

    binding.playFeedButton.isEnabled = canPlay
    binding.stopFeedButton.isEnabled = isSpeakingFeed
  }

  // ── Error display ──────────────────────────────────────────────────

  private fun showSignInError(message: String) {
    binding.signInStatusBar.isVisible = true
    binding.signInProgressBar.isVisible = false
    binding.signInStatusText.text = message
    binding.signInStatusText.setTextColor(getColor(R.color.error))
  }

  private fun showFeedError(message: String) {
    stopFeedPlayback(null)
    if (currentScreen == Screen.FEED) {
      Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    } else {
      showScreen(Screen.FEED)
      Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    binding.speechStatusTextView.text = buildFeedSpeechErrorMessage(message)
  }

  // ── TTS playback ──────────────────────────────────────────────────

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

  // ── Helpers ────────────────────────────────────────────────────────

  private fun safeLoadStoredSession(): String? {
    return try {
      authService.loadStoredSession()
    } catch (error: XAuthException) {
      Toast.makeText(this, error.message ?: getString(R.string.generic_auth_error), Toast.LENGTH_LONG).show()
      null
    }
  }

  private fun safeClearStoredSession() {
    try {
      authService.clearStoredSession()
    } catch (error: XAuthException) {
      Toast.makeText(this, error.message ?: getString(R.string.generic_auth_error), Toast.LENGTH_LONG).show()
    }
  }

  private fun isBusy(): Boolean {
    return captureInFlight || isFetchingInitial || isFetchingMore
  }

  private fun buildFeedSpeechErrorMessage(message: String?): String {
    return getString(R.string.feed_speech_status_error_prefix) + " " + (message ?: "Unknown error.")
  }
}
