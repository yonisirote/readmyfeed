package com.yonisirote.readmyfeed.shell

fun normalizeAppShellScreen(
  requestedScreen: AppScreen,
  supportsRequestedScreen: Boolean,
): AppScreen {
  return when {
    requestedScreen is AppScreen.Home -> requestedScreen
    supportsRequestedScreen -> requestedScreen
    // Final guardrail against showing a provider screen that no active controller can render.
    else -> AppScreen.Home
  }
}
