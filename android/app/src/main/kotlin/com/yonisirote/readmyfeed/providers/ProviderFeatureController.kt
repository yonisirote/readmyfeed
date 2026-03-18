package com.yonisirote.readmyfeed.providers

import com.yonisirote.readmyfeed.shell.AppScreen

interface ProviderFeatureController {
  val provider: FeedProvider

  fun initialize(): Boolean

  fun supports(screen: AppScreen): Boolean

  fun render(screen: AppScreen)

  fun openFromHome()

  fun handleBackPress(): Boolean

  fun onDestroy()
}
