package com.yonisirote.readmyfeed.providers

import com.yonisirote.readmyfeed.shell.AppScreen
import com.yonisirote.readmyfeed.shell.ProviderDestination

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
  fun supportsIgnoresProvidersThatFailedInitialization() {
    val xScreen = AppScreen.ProviderScreen(
      provider = FeedProvider.X,
      destination = ProviderDestination.CONTENT_LIST,
    )
    val telegramScreen = AppScreen.ProviderScreen(
      provider = FeedProvider.TELEGRAM,
      destination = ProviderDestination.CHAT_LIST,
    )
    val xController = FakeProviderFeatureController(
      provider = FeedProvider.X,
      initializeResult = true,
      supportedScreens = setOf(xScreen),
    )
    val telegramController = FakeProviderFeatureController(
      provider = FeedProvider.TELEGRAM,
      initializeResult = false,
      supportedScreens = setOf(telegramScreen),
    )

    val registry = ProviderFeatureRegistry(listOf(xController, telegramController))
    registry.initializeAll()

    assertTrue(registry.supports(xScreen))
    assertFalse(registry.supports(telegramScreen))
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
  fun handleBackPressStopsAtFirstControllerThatHandlesIt() {
    val first = FakeProviderFeatureController(
      provider = FeedProvider.X,
      handleBackPressResult = false,
    )
    val second = FakeProviderFeatureController(
      provider = FeedProvider.TELEGRAM,
      handleBackPressResult = true,
    )
    val third = FakeProviderFeatureController(
      provider = FeedProvider.WHATSAPP,
      handleBackPressResult = true,
    )

    val registry = ProviderFeatureRegistry(listOf(first, second, third))
    registry.initializeAll()

    assertTrue(registry.handleBackPress())
    assertEquals(1, first.handleBackPressCalls)
    assertEquals(1, second.handleBackPressCalls)
    assertEquals(0, third.handleBackPressCalls)
  }

  @Test
  fun handleBackPressSkipsProvidersThatFailedInitialization() {
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

    assertTrue(registry.handleBackPress())
    assertEquals(0, first.handleBackPressCalls)
    assertEquals(1, second.handleBackPressCalls)
  }

  @Test
  fun renderDelegatesToAllControllers() {
    val first = FakeProviderFeatureController(provider = FeedProvider.X)
    val second = FakeProviderFeatureController(provider = FeedProvider.TELEGRAM)
    val screen = AppScreen.Home

    val registry = ProviderFeatureRegistry(listOf(first, second))
    registry.initializeAll()

    registry.render(screen)

    assertEquals(1, first.renderCalls)
    assertEquals(1, second.renderCalls)
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
  private val supportedScreens: Set<AppScreen> = emptySet(),
  private val handleBackPressResult: Boolean = false,
) : ProviderFeatureController {
  var initializeCalls: Int = 0
  var renderCalls: Int = 0
  var openFromHomeCalls: Int = 0
  var handleBackPressCalls: Int = 0
  var onDestroyCalls: Int = 0

  override fun initialize(): Boolean {
    initializeCalls += 1
    return initializeResult
  }

  override fun supports(screen: AppScreen): Boolean {
    return supportedScreens.contains(screen)
  }

  override fun render(screen: AppScreen) {
    renderCalls += 1
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
