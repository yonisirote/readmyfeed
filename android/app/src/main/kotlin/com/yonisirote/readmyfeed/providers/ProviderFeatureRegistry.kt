package com.yonisirote.readmyfeed.providers

import com.yonisirote.readmyfeed.shell.AppScreen

class ProviderFeatureRegistry(
  controllers: List<ProviderFeatureController>,
) {
  private val orderedControllers: List<ProviderFeatureController> = controllers.toList()
  private val controllersByProvider: Map<FeedProvider, ProviderFeatureController> =
    orderedControllers.associateBy { it.provider }
  private val activeControllers = mutableListOf<ProviderFeatureController>()
  private val activeControllersByProvider = mutableMapOf<FeedProvider, ProviderFeatureController>()

  init {
    require(controllersByProvider.size == orderedControllers.size) {
      "Provider controllers must be unique per provider."
    }
  }

  fun initializeAll() {
    activeControllers.clear()
    activeControllersByProvider.clear()

    for (controller in orderedControllers) {
      if (controller.initialize()) {
        activeControllers += controller
        activeControllersByProvider[controller.provider] = controller
      }
    }
  }

  fun hasProvider(provider: FeedProvider): Boolean {
    return activeControllersByProvider.containsKey(provider)
  }

  fun hasActiveProviders(): Boolean {
    return activeControllers.isNotEmpty()
  }

  fun supports(screen: AppScreen): Boolean {
    return activeControllers.any { controller -> controller.supports(screen) }
  }

  fun render(screen: AppScreen) {
    // Each active controller owns part of the shared layout and decides its own visibility.
    for (controller in activeControllers) {
      controller.render(screen)
    }
  }

  fun openFromHome(provider: FeedProvider): Boolean {
    val controller = activeControllersByProvider[provider] ?: return false
    controller.openFromHome()
    return true
  }

  fun handleBackPress(): Boolean {
    return activeControllers.any { controller -> controller.handleBackPress() }
  }

  fun onDestroy() {
    for (controller in orderedControllers) {
      controller.onDestroy()
    }
  }
}
