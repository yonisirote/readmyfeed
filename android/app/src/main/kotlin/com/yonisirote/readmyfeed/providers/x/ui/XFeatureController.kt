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
import com.yonisirote.readmyfeed.shell.ProviderDestination
import com.yonisirote.readmyfeed.shell.XDestination

internal class XFeatureController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val showScreen: (AppScreen) -> Unit,
  private val dependenciesFactory: (AppCompatActivity) -> XFeatureDependencies = ::createXFeatureDependencies,
) : ProviderFeatureController {

  private lateinit var signInController: XSignInScreenController
  private lateinit var timelineController: XTimelineScreenController

  private var hasStoredSession = false
  private var currentDestination: XDestination? = null
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
        showHome = { showScreen(AppScreen.Home) },
        showProviderScreen = ::showProviderScreen,
        onSessionCaptured = ::handleCapturedSession,
      )
      timelineController = XTimelineScreenController(
        activity = activity,
        binding = binding,
        timelineService = dependencies.timelineService,
        timelineSpeechPlayer = dependencies.timelineSpeechPlayer,
        feedAdapter = dependencies.feedAdapter,
        isContentListVisible = { currentDestination == XDestination.ContentList },
        showProviderScreen = ::showProviderScreen,
        showHome = { showScreen(AppScreen.Home) },
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

  override fun render(screen: AppScreen) {
    if (!isInitialized) {
      return
    }

    require(screen is AppScreen.XScreen) {
      "XFeatureController can render only X screens."
    }

    currentDestination = screen.destination
    val showsSignIn = currentDestination == ProviderDestination.Connect
    val showsTimeline = currentDestination == XDestination.ContentList

    binding.xSignInScreen.isVisible = showsSignIn
    binding.feedScreen.isVisible = showsTimeline
    timelineController.render(isVisible = showsTimeline)
  }

  override fun hide() {
    currentDestination = null
    binding.xSignInScreen.isVisible = false
    binding.feedScreen.isVisible = false
    timelineController.render(isVisible = false)
  }

  override fun openFromHome() {
    if (!isInitialized) {
      return
    }

    if (!provider.isAvailable) {
      showScreen(AppScreen.Home)
      return
    }

    if (hasStoredSession) {
      showProviderScreen(XDestination.ContentList)
      timelineController.fetchFollowingTimeline(append = false)
    } else {
      startLoginFlow(clearExistingSession = false)
    }
  }

  override fun handleBackPress(): Boolean {
    if (!isInitialized) {
      return false
    }

    return when {
      currentDestination == ProviderDestination.Connect -> signInController.handleBackPress()
      currentDestination == XDestination.ContentList -> timelineController.handleBackPress()
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

  private fun showProviderScreen(destination: XDestination) {
    showScreen(AppScreen.XScreen(destination))
  }
}
