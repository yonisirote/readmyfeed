package com.yonisirote.readmyfeed.providers

import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.telegram.ui.TelegramFeatureController
import com.yonisirote.readmyfeed.providers.x.ui.XFeatureController
import com.yonisirote.readmyfeed.shell.AppScreenHost

fun buildProviderFeatureRegistry(
  activity: AppCompatActivity,
  binding: ActivityMainBinding,
  screenHost: AppScreenHost,
): ProviderFeatureRegistry {
  return ProviderFeatureRegistry(
    controllers = listOf(
      XFeatureController(
        activity = activity,
        binding = binding,
        screenHost = screenHost,
      ),
      TelegramFeatureController(
        activity = activity,
        binding = binding,
        screenHost = screenHost,
      ),
    ),
  )
}
