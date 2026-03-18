package com.yonisirote.readmyfeed.providers

import com.yonisirote.readmyfeed.shell.AppScreen

class ProviderFeatureRegistry(
  controllers: List<ProviderFeatureController>,
) {
  private val orderedControllers: List<ProviderFeatureController> = controllers.toList()
  private val controllersByProvider: Map<FeedProvider, ProviderFeatureController> =
    orderedControllers.associateBy { it.provider }

  init {
    require(controllersByProvider.size == orderedControllers.size) {
      "Provider controllers must be unique per provider."
    }
  }

  fun initializeAll(): Boolean {
    for (controller in orderedControllers) {
      if (!controller.initialize()) {
        return false
      }
    }

    return true
  }

  fun hasProvider(provider: FeedProvider): Boolean {
    return controllersByProvider.containsKey(provider)
  }

  fun supports(screen: AppScreen): Boolean {
    return orderedControllers.any { controller -> controller.supports(screen) }
  }

  fun render(screen: AppScreen) {
    for (controller in orderedControllers) {
      controller.render(screen)
    }
  }

  fun openFromHome(provider: FeedProvider): Boolean {
    val controller = controllersByProvider[provider] ?: return false
    controller.openFromHome()
    return true
  }

  fun handleBackPress(): Boolean {
    return orderedControllers.any { controller -> controller.handleBackPress() }
  }

  fun onDestroy() {
    for (controller in orderedControllers) {
      controller.onDestroy()
    }
  }
}
