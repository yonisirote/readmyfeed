package com.yonisirote.readmyfeed.providers.x.ui

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.FeedProvider
import com.yonisirote.readmyfeed.providers.ProviderFeatureController
import com.yonisirote.readmyfeed.providers.x.auth.XAuthException
import com.yonisirote.readmyfeed.shell.AppScreen
import com.yonisirote.readmyfeed.shell.AppScreenHost
import com.yonisirote.readmyfeed.shell.ProviderDestination
import com.yonisirote.readmyfeed.shell.matchesProvider
import com.yonisirote.readmyfeed.shell.matchesProviderDestination
import com.yonisirote.readmyfeed.shell.resolveHomeSelectionScreen

internal class XFeatureController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val screenHost: AppScreenHost,
  private val dependenciesFactory: (AppCompatActivity) -> XFeatureDependencies = ::createXFeatureDependencies,
) : ProviderFeatureController {

  private lateinit var signInController: XSignInScreenController
  private lateinit var timelineController: XTimelineScreenController

  private var hasStoredSession = false
  private var currentScreen: AppScreen = AppScreen.Home
  private var isInitialized = false

  override val provider: FeedProvider = FeedProvider.X

  override fun initialize(): Boolean {
    return try {
      val dependencies = dependenciesFactory(activity)

      signInController = XSignInScreenController(
        activity = activity,
        binding = binding,
        authService = dependencies.authService,
        captureCoordinator = dependencies.captureCoordinator,
        showHome = { screenHost.showScreen(AppScreen.Home) },
        showProviderScreen = ::showProviderScreen,
        onSessionCaptured = ::handleCapturedSession,
      )
      timelineController = XTimelineScreenController(
        activity = activity,
        binding = binding,
        timelineService = dependencies.timelineService,
        timelineSpeechPlayer = dependencies.timelineSpeechPlayer,
        feedAdapter = dependencies.feedAdapter,
        isContentListVisible = { isOnProviderDestination(ProviderDestination.CONTENT_LIST) },
        showProviderScreen = ::showProviderScreen,
        showHome = { screenHost.showScreen(AppScreen.Home) },
        requestLogin = { startLoginFlow(clearExistingSession = false) },
        clearStoredSession = { signInController.clearStoredSession() },
        onSessionAvailabilityChanged = ::updateStoredSessionAvailability,
      )

      signInController.initialize()
      timelineController.initialize()
      updateStoredSessionAvailability(signInController.loadStoredSessionAvailability())
      isInitialized = true
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
    val showsSignIn = isOnProviderDestination(ProviderDestination.CONNECT)
    val showsTimeline = isOnProviderDestination(ProviderDestination.CONTENT_LIST)

    binding.xSignInScreen.isVisible = showsSignIn
    binding.feedScreen.isVisible = showsTimeline
    timelineController.render(isVisible = showsTimeline)
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
            timelineController.fetchFollowingTimeline(append = false)
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
      isOnProviderDestination(ProviderDestination.CONNECT) -> signInController.handleBackPress()
      isOnProviderDestination(ProviderDestination.CONTENT_LIST) -> timelineController.handleBackPress()
      else -> false
    }
  }

  override fun onDestroy() {
    if (!isInitialized) {
      return
    }

    timelineController.onDestroy()
    signInController.onDestroy()
  }

  private fun startLoginFlow(clearExistingSession: Boolean) {
    if (!isInitialized) {
      return
    }

    timelineController.prepareForLoginFlow(clearExistingSession)
    if (clearExistingSession) {
      updateStoredSessionAvailability(false)
    }
    signInController.startLoginFlow(clearExistingSession)
  }

  private fun handleCapturedSession(cookieString: String) {
    updateStoredSessionAvailability(true)
    timelineController.fetchFollowingTimeline(
      initialCookieString = cookieString,
      append = false,
    )
  }

  private fun updateStoredSessionAvailability(value: Boolean) {
    hasStoredSession = value
    timelineController.setHasStoredSession(value)
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
}
