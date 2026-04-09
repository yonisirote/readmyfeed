package com.yonisirote.readmyfeed.providers

import com.yonisirote.readmyfeed.shell.AppScreen

interface ProviderFeatureController {
  val provider: FeedProvider

  fun initialize(): Boolean

  fun render(screen: AppScreen)

  fun hide()

  fun openFromHome()

  fun handleBackPress(): Boolean

  fun onDestroy()
}
