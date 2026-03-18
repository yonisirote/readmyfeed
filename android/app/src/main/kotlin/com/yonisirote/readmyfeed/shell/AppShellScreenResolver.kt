package com.yonisirote.readmyfeed.shell

fun normalizeAppShellScreen(
  requestedScreen: AppScreen,
  supportsRequestedScreen: Boolean,
): AppScreen {
  return when {
    requestedScreen is AppScreen.Home -> requestedScreen
    supportsRequestedScreen -> requestedScreen
    else -> AppScreen.Home
  }
}
