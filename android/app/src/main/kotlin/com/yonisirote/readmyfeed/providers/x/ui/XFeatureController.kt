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
import com.yonisirote.readmyfeed.shell.XDestination
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
        isContentListVisible = { isOnDestination(XDestination.CONTENT_LIST) },
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
    return screen is AppScreen.XScreen
  }

  override fun render(screen: AppScreen) {
    if (!isInitialized) {
      return
    }

    currentScreen = screen
    val showsSignIn = isOnDestination(XDestination.CONNECT)
    val showsTimeline = isOnDestination(XDestination.CONTENT_LIST)

    binding.xSignInScreen.isVisible = showsSignIn
    binding.feedScreen.isVisible = showsTimeline
    timelineController.render(isVisible = showsTimeline)
  }

  override fun openFromHome() {
    if (!isInitialized) {
      return
    }

    when (val targetScreen = resolveHomeSelectionScreen(provider, hasStoredSession)) {
      AppScreen.Home -> screenHost.showScreen(AppScreen.Home)
      is AppScreen.XScreen -> {
        when (targetScreen.destination) {
          XDestination.CONNECT -> startLoginFlow(clearExistingSession = false)
          XDestination.CONTENT_LIST -> {
            screenHost.showScreen(targetScreen)
            timelineController.fetchFollowingTimeline(append = false)
          }
        }
      }
      is AppScreen.TelegramScreen -> Unit
    }
  }

  override fun handleBackPress(): Boolean {
    if (!isInitialized) {
      return false
    }

    return when {
      isOnDestination(XDestination.CONNECT) -> signInController.handleBackPress()
      isOnDestination(XDestination.CONTENT_LIST) -> timelineController.handleBackPress()
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

  private fun isOnDestination(destination: XDestination): Boolean {
    val screen = currentScreen
    return screen is AppScreen.XScreen && screen.destination == destination
  }

  private fun showProviderScreen(destination: XDestination) {
    screenHost.showScreen(AppScreen.XScreen(destination))
  }
}
