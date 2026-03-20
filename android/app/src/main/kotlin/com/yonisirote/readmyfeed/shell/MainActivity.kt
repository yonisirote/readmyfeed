package com.yonisirote.readmyfeed.shell

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.FeedProvider
import com.yonisirote.readmyfeed.providers.ProviderFeatureRegistry
import com.yonisirote.readmyfeed.providers.buildProviderFeatureRegistry

private const val DISABLED_HOME_CARD_ALPHA = 0.5f

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
    providerRegistry.initializeAll()

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

    updateHomeCardState(
      card = binding.homeCardX,
      isAvailable = providerRegistry.hasProvider(FeedProvider.X),
    )
    updateHomeCardState(
      card = binding.homeCardTelegram,
      isAvailable = providerRegistry.hasProvider(FeedProvider.TELEGRAM),
    )

    if (!providerRegistry.hasActiveProviders()) {
      binding.homeSubtitle.setText(R.string.home_subtitle_unavailable)
    }
  }

  private fun updateHomeCardState(card: View, isAvailable: Boolean) {
    card.isEnabled = isAvailable
    card.isClickable = isAvailable
    card.isFocusable = isAvailable
    card.alpha = if (isAvailable) 1f else DISABLED_HOME_CARD_ALPHA
    card.setBackgroundResource(
      if (isAvailable) R.drawable.feed_card_selector else R.drawable.feed_card_disabled,
    )
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
