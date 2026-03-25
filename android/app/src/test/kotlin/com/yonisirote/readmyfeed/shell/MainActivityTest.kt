package com.yonisirote.readmyfeed.shell

import android.view.View
import androidx.core.view.isVisible
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.providers.FeedProvider
import com.yonisirote.readmyfeed.providers.ProviderFeatureController
import com.yonisirote.readmyfeed.providers.ProviderFeatureRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
  @Test
  fun showScreenKeepsSupportedProviderScreen() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val supportedScreen = AppScreen.XScreen(XDestination.CONTENT_LIST)
    val controller = RecordingProviderFeatureController(
      provider = FeedProvider.X,
      supportedScreens = setOf(supportedScreen),
    )

    activity.replaceProviderRegistry(ProviderFeatureRegistry(listOf(controller)))
    activity.showScreen(supportedScreen)

    assertEquals(listOf(supportedScreen), controller.renderedScreens)
    assertFalse(activity.findViewById<View>(R.id.homeScreen).isVisible)
  }

  @Test
  fun showScreenFallsBackToHomeForUnsupportedProviderScreen() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val unsupportedScreen = AppScreen.TelegramScreen(TelegramDestination.CHAT_MESSAGES)
    val controller = RecordingProviderFeatureController(
      provider = FeedProvider.X,
      supportedScreens = setOf(AppScreen.XScreen(XDestination.CONNECT)),
    )

    activity.replaceProviderRegistry(ProviderFeatureRegistry(listOf(controller)))
    activity.showScreen(unsupportedScreen)

    assertEquals(listOf(AppScreen.Home), controller.renderedScreens)
    assertTrue(activity.findViewById<View>(R.id.homeScreen).isVisible)
  }

  private fun MainActivity.replaceProviderRegistry(registry: ProviderFeatureRegistry) {
    val field = MainActivity::class.java.getDeclaredField("providerRegistry")
    field.isAccessible = true
    field.set(this, registry)
    registry.initializeAll()
  }
}

private class RecordingProviderFeatureController(
  override val provider: FeedProvider,
  private val supportedScreens: Set<AppScreen>,
) : ProviderFeatureController {
  val renderedScreens = mutableListOf<AppScreen>()

  override fun initialize(): Boolean {
    return true
  }

  override fun supports(screen: AppScreen): Boolean {
    return supportedScreens.contains(screen)
  }

  override fun render(screen: AppScreen) {
    renderedScreens += screen
  }

  override fun openFromHome() = Unit

  override fun handleBackPress(): Boolean {
    return false
  }

  override fun onDestroy() = Unit
}
