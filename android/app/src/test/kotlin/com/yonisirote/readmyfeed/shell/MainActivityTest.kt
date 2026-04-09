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
  fun showScreenKeepsProviderScreenWhenOwnerIsRegistered() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val supportedScreen = AppScreen.XScreen(XDestination.ContentList)
    val controller = RecordingProviderFeatureController(
      provider = FeedProvider.X,
    )

    activity.replaceProviderRegistry(ProviderFeatureRegistry(listOf(controller)))
    activity.showScreen(supportedScreen)

    assertEquals(listOf(supportedScreen), controller.renderedScreens)
    assertFalse(activity.findViewById<View>(R.id.homeScreen).isVisible)
  }

  @Test
  fun showScreenFallsBackToHomeWhenOwnerIsMissing() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    val unsupportedScreen = AppScreen.TelegramScreen(TelegramDestination.ChatMessages)
    val controller = RecordingProviderFeatureController(
      provider = FeedProvider.X,
    )

    activity.replaceProviderRegistry(ProviderFeatureRegistry(listOf(controller)))
    activity.showScreen(unsupportedScreen)

    assertTrue(controller.renderedScreens.isEmpty())
    assertEquals(0, controller.hideCalls)
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
) : ProviderFeatureController {
  val renderedScreens = mutableListOf<AppScreen>()
  var hideCalls: Int = 0

  override fun initialize(): Boolean {
    return true
  }

  override fun render(screen: AppScreen) {
    renderedScreens += screen
  }

  override fun hide() {
    hideCalls += 1
  }

  override fun openFromHome() = Unit

  override fun handleBackPress(): Boolean {
    return false
  }

  override fun onDestroy() = Unit
}
