package com.yonisirote.readmyfeed.providers

import com.yonisirote.readmyfeed.shell.AppScreen
import com.yonisirote.readmyfeed.shell.TelegramDestination
import com.yonisirote.readmyfeed.shell.XDestination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFeatureRegistryTest {
  @Test
  fun initializeAllKeepsSuccessfulProvidersActive() {
    val first = FakeProviderFeatureController(
      provider = FeedProvider.X,
      initializeResult = true,
    )
    val second = FakeProviderFeatureController(
      provider = FeedProvider.TELEGRAM,
      initializeResult = false,
    )
    val third = FakeProviderFeatureController(
      provider = FeedProvider.WHATSAPP,
      initializeResult = true,
    )

    val registry = ProviderFeatureRegistry(listOf(first, second, third))

    registry.initializeAll()
    assertEquals(1, first.initializeCalls)
    assertEquals(1, second.initializeCalls)
    assertEquals(1, third.initializeCalls)
    assertTrue(registry.hasProvider(FeedProvider.X))
    assertFalse(registry.hasProvider(FeedProvider.TELEGRAM))
    assertTrue(registry.hasProvider(FeedProvider.WHATSAPP))
  }

  @Test
  fun hasControllerForUsesScreenOwnerOnly() {
    val xScreen = AppScreen.XScreen(XDestination.ContentList)
    val telegramScreen = AppScreen.TelegramScreen(TelegramDestination.ChatList)
    val xController = FakeProviderFeatureController(
      provider = FeedProvider.X,
      initializeResult = true,
    )
    val telegramController = FakeProviderFeatureController(
      provider = FeedProvider.TELEGRAM,
      initializeResult = false,
    )

    val registry = ProviderFeatureRegistry(listOf(xController, telegramController))
    registry.initializeAll()

    assertTrue(registry.hasControllerFor(AppScreen.Home))
    assertTrue(registry.hasControllerFor(xScreen))
    assertFalse(registry.hasControllerFor(telegramScreen))
  }

  @Test
  fun openFromHomeTargetsMatchingControllerOnly() {
    val xController = FakeProviderFeatureController(provider = FeedProvider.X)
    val telegramController = FakeProviderFeatureController(provider = FeedProvider.TELEGRAM)

    val registry = ProviderFeatureRegistry(listOf(xController, telegramController))
    registry.initializeAll()

    assertTrue(registry.openFromHome(FeedProvider.TELEGRAM))
    assertEquals(0, xController.openFromHomeCalls)
    assertEquals(1, telegramController.openFromHomeCalls)
  }

  @Test
  fun openFromHomeReturnsFalseWhenProviderFailedInitialization() {
    val xController = FakeProviderFeatureController(provider = FeedProvider.X)
    val telegramController = FakeProviderFeatureController(
      provider = FeedProvider.TELEGRAM,
      initializeResult = false,
    )

    val registry = ProviderFeatureRegistry(listOf(xController, telegramController))
    registry.initializeAll()

    assertFalse(registry.openFromHome(FeedProvider.TELEGRAM))
    assertEquals(0, xController.openFromHomeCalls)
    assertEquals(0, telegramController.openFromHomeCalls)
  }

  @Test
  fun openFromHomeReturnsFalseWhenProviderMissing() {
    val registry = ProviderFeatureRegistry(
      listOf(FakeProviderFeatureController(provider = FeedProvider.X)),
    )
    registry.initializeAll()

    assertFalse(registry.openFromHome(FeedProvider.TELEGRAM))
  }

  @Test
  fun handleBackPressRoutesOnlyToScreenOwner() {
    val xController = FakeProviderFeatureController(
      provider = FeedProvider.X,
      handleBackPressResult = false,
    )
    val telegramController = FakeProviderFeatureController(
      provider = FeedProvider.TELEGRAM,
      handleBackPressResult = true,
    )

    val registry = ProviderFeatureRegistry(listOf(xController, telegramController))
    registry.initializeAll()

    assertTrue(registry.handleBackPress(AppScreen.TelegramScreen(TelegramDestination.ChatList)))
    assertEquals(0, xController.handleBackPressCalls)
    assertEquals(1, telegramController.handleBackPressCalls)
  }

  @Test
  fun handleBackPressReturnsFalseWhenProviderFailedInitialization() {
    val first = FakeProviderFeatureController(
      provider = FeedProvider.X,
      initializeResult = false,
      handleBackPressResult = true,
    )
    val second = FakeProviderFeatureController(
      provider = FeedProvider.TELEGRAM,
      handleBackPressResult = true,
    )

    val registry = ProviderFeatureRegistry(listOf(first, second))
    registry.initializeAll()

    assertFalse(registry.handleBackPress(AppScreen.XScreen(XDestination.ContentList)))
    assertEquals(0, first.handleBackPressCalls)
    assertEquals(0, second.handleBackPressCalls)
  }

  @Test
  fun handleBackPressReturnsFalseOnHome() {
    val first = FakeProviderFeatureController(provider = FeedProvider.X)
    val second = FakeProviderFeatureController(provider = FeedProvider.TELEGRAM)

    val registry = ProviderFeatureRegistry(listOf(first, second))
    registry.initializeAll()

    assertFalse(registry.handleBackPress(AppScreen.Home))

    assertEquals(0, first.handleBackPressCalls)
    assertEquals(0, second.handleBackPressCalls)
  }

  @Test
  fun renderRoutesOnlyToOwningController() {
    val first = FakeProviderFeatureController(provider = FeedProvider.X)
    val second = FakeProviderFeatureController(provider = FeedProvider.TELEGRAM)

    val registry = ProviderFeatureRegistry(listOf(first, second))
    registry.initializeAll()

    registry.render(AppScreen.XScreen(XDestination.ContentList))

    assertEquals(1, first.renderCalls)
    assertEquals(0, second.renderCalls)
    assertEquals(0, first.hideCalls)
    assertEquals(0, second.hideCalls)
  }

  @Test
  fun renderHidesPreviousOwnerWhenSwitchingScreens() {
    val first = FakeProviderFeatureController(provider = FeedProvider.X)
    val second = FakeProviderFeatureController(provider = FeedProvider.TELEGRAM)

    val registry = ProviderFeatureRegistry(listOf(first, second))
    registry.initializeAll()

    registry.render(AppScreen.XScreen(XDestination.ContentList))
    registry.render(AppScreen.TelegramScreen(TelegramDestination.ChatList))
    registry.render(AppScreen.Home)

    assertEquals(1, first.renderCalls)
    assertEquals(1, second.renderCalls)
    assertEquals(1, first.hideCalls)
    assertEquals(1, second.hideCalls)
  }

  @Test
  fun onDestroyDelegatesToAllControllers() {
    val first = FakeProviderFeatureController(provider = FeedProvider.X)
    val second = FakeProviderFeatureController(provider = FeedProvider.TELEGRAM)

    val registry = ProviderFeatureRegistry(listOf(first, second))
    registry.initializeAll()

    registry.onDestroy()

    assertEquals(1, first.onDestroyCalls)
    assertEquals(1, second.onDestroyCalls)
  }

  @Test(expected = IllegalArgumentException::class)
  fun duplicateProvidersAreRejected() {
    ProviderFeatureRegistry(
      listOf(
        FakeProviderFeatureController(provider = FeedProvider.X),
        FakeProviderFeatureController(provider = FeedProvider.X),
      ),
    )
  }
}

private class FakeProviderFeatureController(
  override val provider: FeedProvider,
  private val initializeResult: Boolean = true,
  private val handleBackPressResult: Boolean = false,
) : ProviderFeatureController {
  var initializeCalls: Int = 0
  var renderCalls: Int = 0
  var hideCalls: Int = 0
  var openFromHomeCalls: Int = 0
  var handleBackPressCalls: Int = 0
  var onDestroyCalls: Int = 0

  override fun initialize(): Boolean {
    initializeCalls += 1
    return initializeResult
  }

  override fun render(screen: AppScreen) {
    renderCalls += 1
  }

  override fun hide() {
    hideCalls += 1
  }

  override fun openFromHome() {
    openFromHomeCalls += 1
  }

  override fun handleBackPress(): Boolean {
    handleBackPressCalls += 1
    return handleBackPressResult
  }

  override fun onDestroy() {
    onDestroyCalls += 1
  }
}
