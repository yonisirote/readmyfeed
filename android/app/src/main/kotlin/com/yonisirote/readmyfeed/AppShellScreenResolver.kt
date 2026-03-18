package com.yonisirote.readmyfeed

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
