package com.yonisirote.readmyfeed.shell

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.FeedProvider
import com.yonisirote.readmyfeed.providers.ProviderFeatureRegistry
import com.yonisirote.readmyfeed.providers.buildProviderFeatureRegistry

class MainActivity : AppCompatActivity(), AppScreenHost {
  private lateinit var binding: ActivityMainBinding
  private lateinit var providerRegistry: ProviderFeatureRegistry

  private var currentScreen: AppScreen = AppScreen.Home

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    providerRegistry = buildProviderFeatureRegistry(
      activity = this,
      binding = binding,
      screenHost = this,
    )
    if (!providerRegistry.initializeAll()) {
      return
    }

    setupHomeScreen()
    setupBackPressHandler()
    showScreen(AppScreen.Home)
  }

  override fun onDestroy() {
    if (::providerRegistry.isInitialized) {
      providerRegistry.onDestroy()
    }
    super.onDestroy()
  }

  override fun showScreen(screen: AppScreen) {
    currentScreen = normalizeScreen(screen)
    binding.homeScreen.isVisible = currentScreen is AppScreen.Home
    providerRegistry.render(currentScreen)
  }

  private fun normalizeScreen(screen: AppScreen): AppScreen {
    return normalizeAppShellScreen(
      requestedScreen = screen,
      supportsRequestedScreen = providerRegistry.supports(screen),
    )
  }

  private fun setupHomeScreen() {
    binding.homeCardX.setOnClickListener {
      providerRegistry.openFromHome(FeedProvider.X)
    }
    binding.homeCardTelegram.setOnClickListener {
      providerRegistry.openFromHome(FeedProvider.TELEGRAM)
    }
  }

  private fun setupBackPressHandler() {
    onBackPressedDispatcher.addCallback(this) {
      when {
        providerRegistry.handleBackPress() -> Unit
        currentScreen is AppScreen.Home -> {
          isEnabled = false
          onBackPressedDispatcher.onBackPressed()
        }
        else -> showScreen(AppScreen.Home)
      }
    }
  }
}
