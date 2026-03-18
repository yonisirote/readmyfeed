package com.yonisirote.readmyfeed.x.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yonisirote.readmyfeed.AppScreen
import com.yonisirote.readmyfeed.AppScreenHost
import com.yonisirote.readmyfeed.ContentListSummaryState
import com.yonisirote.readmyfeed.FeedProvider
import com.yonisirote.readmyfeed.ProviderDestination
import com.yonisirote.readmyfeed.ProviderFeatureController
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.canLoadMoreContent
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.matchesProvider
import com.yonisirote.readmyfeed.matchesProviderDestination
import com.yonisirote.readmyfeed.resolveContentListSummaryModel
import com.yonisirote.readmyfeed.resolveHomeSelectionScreen
import com.yonisirote.readmyfeed.shouldEnableLoadMoreContentButton
import com.yonisirote.readmyfeed.shouldShowLoadMoreContentButton
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
import com.yonisirote.readmyfeed.x.timeline.shouldClearXTimelineSession
import com.yonisirote.readmyfeed.x.timeline.shouldPrefetchTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class XFeatureController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val screenHost: AppScreenHost,
) : ProviderFeatureController {

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
  private var currentScreen: AppScreen = AppScreen.Home
  private var isInitialized = false

  override val provider: FeedProvider = FeedProvider.X

  override fun initialize(): Boolean {
    if (!initializeDependencies()) {
      return false
    }

    setupRecyclerView()
    setupSignInScreen()
    setupFeedScreen()
    setupWebView()
    maybeLoadStoredSession()
    isInitialized = true
    return true
  }

  override fun supports(screen: AppScreen): Boolean {
    return screen.matchesProviderDestination(
      provider = provider,
      destination = ProviderDestination.CONNECT,
    ) || screen.matchesProviderDestination(
      provider = provider,
      destination = ProviderDestination.CONTENT_LIST,
    )
  }

  override fun render(screen: AppScreen) {
    if (!isInitialized) {
      return
    }

    currentScreen = if (supports(screen)) screen else AppScreen.Home
    binding.xSignInScreen.isVisible = currentScreen.matchesProviderDestination(
      provider = provider,
      destination = ProviderDestination.CONNECT,
    )
    binding.feedScreen.isVisible = currentScreen.matchesProviderDestination(
      provider = provider,
      destination = ProviderDestination.CONTENT_LIST,
    )
    binding.loadingOverlay.isVisible = false
  }

  override fun openFromHome() {
    if (!isInitialized) {
      return
    }

    val targetScreen = resolveHomeSelectionScreen(
      provider = provider,
      hasStoredSession = hasStoredSession,
    )

    when (targetScreen) {
      AppScreen.Home -> screenHost.showScreen(AppScreen.Home)
      is AppScreen.ProviderScreen -> {
        when {
          targetScreen.matchesProvider(provider, ProviderDestination.CONNECT) -> {
            startLoginFlow(clearExistingSession = false)
          }
          targetScreen.matchesProvider(provider, ProviderDestination.CONTENT_LIST) -> {
            screenHost.showScreen(targetScreen)
            fetchFollowingTimeline(append = false)
          }
        }
      }
    }
  }

  override fun handleBackPress(): Boolean {
    if (!isInitialized) {
      return false
    }

    return when {
      isOnProviderDestination(ProviderDestination.CONNECT) -> {
        if (binding.xWebView.canGoBack()) {
          binding.xWebView.goBack()
        } else {
          binding.xWebView.stopLoading()
          captureJob?.cancel()
          captureInFlight = false
          didCapture = false
          screenHost.showScreen(AppScreen.Home)
        }
        true
      }
      isOnProviderDestination(ProviderDestination.CONTENT_LIST) -> {
        if (!isBusy()) {
          stopFeedPlayback(null)
          screenHost.showScreen(AppScreen.Home)
        }
        true
      }
      else -> false
    }
  }

  override fun onDestroy() {
    if (!isInitialized) {
      return
    }

    captureJob?.cancel()
    fetchJob?.cancel()
    speakJob?.cancel()
    timelineSpeechPlayer.stop()
    timelineSpeechPlayer.shutdown()
    binding.feedRecyclerView.adapter = null
    binding.xWebView.stopLoading()
    binding.xWebView.destroy()
  }

  private fun initializeDependencies(): Boolean {
    return try {
      val cookieReader = AndroidWebViewCookieReader()
      val sessionStore = PreferencesXSessionStore(activity.applicationContext)
      authService = XAuthService(cookieReader, sessionStore)
      captureCoordinator = XLoginCaptureCoordinator(authService)
      timelineService = XTimelineService(authService)
      ttsService = TtsService(AndroidTtsEngine(activity.applicationContext))
      timelineSpeechPlayer = XTimelineSpeechPlayer(ttsService)
      feedAdapter = XTimelineFeedAdapter()
      true
    } catch (error: XAuthException) {
      Toast.makeText(
        activity,
        error.message ?: activity.getString(R.string.generic_auth_error),
        Toast.LENGTH_LONG,
      ).show()
      false
    }
  }

  private fun setupSignInScreen() {
    binding.backFromSignIn.setOnClickListener {
      binding.xWebView.stopLoading()
      captureJob?.cancel()
      captureInFlight = false
      didCapture = false
      screenHost.showScreen(AppScreen.Home)
    }
  }

  private fun setupFeedScreen() {
    binding.backFromFeed.setOnClickListener {
      if (isBusy()) {
        return@setOnClickListener
      }
      stopFeedPlayback(null)
      screenHost.showScreen(AppScreen.Home)
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
          error?.description?.toString().orEmpty().ifBlank {
            activity.getString(R.string.generic_auth_error)
          },
        )
      }
    }
  }

  private fun maybeLoadStoredSession() {
    val storedSession = safeLoadStoredSession() ?: run {
      hasStoredSession = false
      return
    }

    hasStoredSession = storedSession.isNotBlank()
  }

  private fun startLoginFlow(clearExistingSession: Boolean) {
    captureJob?.cancel()
    fetchJob?.cancel()
    stopFeedPlayback(null)
    isFetchingInitial = false
    isFetchingMore = false
    captureInFlight = false
    didCapture = false

    activity.lifecycleScope.launch {
      if (clearExistingSession) {
        safeClearStoredSession()
        hasStoredSession = false
        timelineItems = emptyList()
        nextCursor = null
        lastFeedLoadErrorMessage = null
        feedAdapter.submitList(emptyList())
        binding.speechStatusTextView.text = activity.getString(R.string.feed_speech_status_idle)
        clearXWebViewCookies()
      }

      showProviderScreen(ProviderDestination.CONNECT)
      hideSignInStatus()
      binding.xWebView.stopLoading()
      if (clearExistingSession) {
        binding.xWebView.clearHistory()
        binding.xWebView.clearCache(true)
      }
      binding.xWebView.loadUrl(X_LOGIN_URL)
    }
  }

  private fun attemptCapture(strict: Boolean) {
    if (captureInFlight || didCapture) {
      return
    }

    captureJob?.cancel()
    captureJob = activity.lifecycleScope.launch {
      captureInFlight = true

      if (strict) {
        showSignInStatus(activity.getString(R.string.status_capturing_body))
      }

      try {
        val session = if (strict) {
          captureCoordinator.captureAndStoreSessionWithRetry()
        } else {
          captureCoordinator.captureAndStoreSessionOnce()
        }

        hasStoredSession = true
        didCapture = true
        showProviderScreen(ProviderDestination.CONTENT_LIST)
        fetchFollowingTimeline(initialCookieString = session.cookieString, append = false)
      } catch (error: XAuthException) {
        if (!strict && error.code == XAuthErrorCodes.COOKIE_MISSING_REQUIRED) {
          return@launch
        }

        showSignInError(error.message ?: activity.getString(R.string.generic_auth_error))
      } finally {
        captureInFlight = false
      }
    }
  }

  private fun fetchFollowingTimeline(
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
      if (isOnProviderDestination(ProviderDestination.CONTENT_LIST)) {
        binding.loadingOverlay.isVisible = timelineItems.isEmpty()
        binding.loadingTextView.text = activity.getString(R.string.loading_feed)
      }
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

    if (!isOnProviderDestination(ProviderDestination.CONTENT_LIST)) {
      showProviderScreen(ProviderDestination.CONTENT_LIST)
    }

    if (timelineItems.isEmpty()) {
      binding.speechStatusTextView.text = activity.getString(R.string.feed_speech_status_no_items)
    } else if (!isSpeakingFeed) {
      binding.speechStatusTextView.text = activity.getString(R.string.feed_speech_status_idle)
    }
  }

  private fun handleTimelineError(error: XTimelineException) {
    val message = error.message ?: activity.getString(R.string.generic_timeline_error)
    lastFeedLoadErrorMessage = message

    if (shouldClearXTimelineSession(error)) {
      safeClearStoredSession()
      hasStoredSession = false
      didCapture = false
      nextCursor = null
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
    val canPlay = isOnProviderDestination(ProviderDestination.CONTENT_LIST) &&
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

  private fun showSignInStatus(text: String) {
    binding.signInStatusBar.isVisible = true
    binding.signInProgressBar.isVisible = true
    binding.signInStatusText.text = text
    binding.signInStatusText.setTextColor(activity.getColor(R.color.textSecondary))
  }

  private fun hideSignInStatus() {
    binding.signInStatusBar.isVisible = false
  }

  private fun showSignInError(message: String) {
    binding.signInStatusBar.isVisible = true
    binding.signInProgressBar.isVisible = false
    binding.signInStatusText.text = message
    binding.signInStatusText.setTextColor(activity.getColor(R.color.error))
  }

  private fun showFeedError(message: String) {
    stopFeedPlayback(null)
    if (isOnProviderDestination(ProviderDestination.CONTENT_LIST)) {
      Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    } else {
      showProviderScreen(ProviderDestination.CONTENT_LIST)
      Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
    binding.speechStatusTextView.text = buildFeedSpeechErrorMessage(message)
  }

  private fun safeLoadStoredSession(): String? {
    return try {
      authService.loadStoredSession()
    } catch (error: XAuthException) {
      Toast.makeText(
        activity,
        error.message ?: activity.getString(R.string.generic_auth_error),
        Toast.LENGTH_LONG,
      ).show()
      null
    }
  }

  private fun safeClearStoredSession() {
    try {
      authService.clearStoredSession()
    } catch (error: XAuthException) {
      Toast.makeText(
        activity,
        error.message ?: activity.getString(R.string.generic_auth_error),
        Toast.LENGTH_LONG,
      ).show()
    }
  }

  private fun isOnProviderDestination(destination: ProviderDestination): Boolean {
    return currentScreen.matchesProviderDestination(
      provider = provider,
      destination = destination,
    )
  }

  private fun showProviderScreen(destination: ProviderDestination) {
    screenHost.showScreen(
      AppScreen.ProviderScreen(
        provider = provider,
        destination = destination,
      ),
    )
  }

  private fun isBusy(): Boolean {
    return captureInFlight || isFetchingInitial || isFetchingMore
  }

  private fun buildFeedSpeechErrorMessage(message: String?): String {
    return activity.getString(R.string.feed_speech_status_error_prefix) + " " + (message ?: "Unknown error.")
  }
}
