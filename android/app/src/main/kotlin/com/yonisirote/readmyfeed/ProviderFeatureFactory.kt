package com.yonisirote.readmyfeed

import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.telegram.ui.TelegramFeatureController
import com.yonisirote.readmyfeed.x.ui.XFeatureController

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
