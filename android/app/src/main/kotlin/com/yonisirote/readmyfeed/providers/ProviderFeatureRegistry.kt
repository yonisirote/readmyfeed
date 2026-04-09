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
  private var renderedProvider: FeedProvider? = null

  init {
    require(controllersByProvider.size == orderedControllers.size) {
      "Provider controllers must be unique per provider."
    }
  }

  fun initializeAll() {
    activeControllers.clear()
    activeControllersByProvider.clear()
    renderedProvider = null

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

  fun hasControllerFor(screen: AppScreen): Boolean {
    val provider = screen.owner ?: return true
    return activeControllersByProvider.containsKey(provider)
  }

  fun render(screen: AppScreen) {
    val provider = screen.owner
    if (renderedProvider != null && renderedProvider != provider) {
      activeControllersByProvider[renderedProvider]?.hide()
    }

    if (provider == null) {
      renderedProvider = null
      return
    }

    val controller = activeControllersByProvider[provider] ?: run {
      renderedProvider = null
      return
    }

    controller.render(screen)
    renderedProvider = provider
  }

  fun openFromHome(provider: FeedProvider): Boolean {
    val controller = activeControllersByProvider[provider] ?: return false
    controller.openFromHome()
    return true
  }

  fun handleBackPress(screen: AppScreen): Boolean {
    val provider = screen.owner ?: return false
    val controller = activeControllersByProvider[provider] ?: return false
    return controller.handleBackPress()
  }

  fun onDestroy() {
    for (controller in orderedControllers) {
      controller.onDestroy()
    }
  }
}
