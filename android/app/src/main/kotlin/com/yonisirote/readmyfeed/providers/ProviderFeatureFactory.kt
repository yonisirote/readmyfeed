package com.yonisirote.readmyfeed.providers

import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.telegram.ui.TelegramFeatureController
import com.yonisirote.readmyfeed.providers.x.ui.XFeatureController
import com.yonisirote.readmyfeed.shell.AppScreen

fun buildProviderFeatureRegistry(
  activity: AppCompatActivity,
  binding: ActivityMainBinding,
  showScreen: (AppScreen) -> Unit,
): ProviderFeatureRegistry {
  return ProviderFeatureRegistry(
    controllers = listOf(
      XFeatureController(
        activity = activity,
        binding = binding,
        showScreen = showScreen,
      ),
      TelegramFeatureController(
        activity = activity,
        binding = binding,
        showScreen = showScreen,
      ),
    ),
  )
}
