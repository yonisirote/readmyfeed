package com.yonisirote.readmyfeed.shell

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.FeedProvider
import com.yonisirote.readmyfeed.providers.ProviderFeatureRegistry
import com.yonisirote.readmyfeed.providers.buildProviderFeatureRegistry

private const val DISABLED_HOME_CARD_ALPHA = 0.82f

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
      // Let providers release view/activity resources before the activity fully tears down.
      providerRegistry.onDestroy()
    }
    super.onDestroy()
  }

  override fun showScreen(screen: AppScreen) {
    // Unsupported provider destinations always collapse back to Home at the shell boundary.
    currentScreen = normalizeAppShellScreen(
      requestedScreen = screen,
      supportsRequestedScreen = providerRegistry.supports(screen),
    )
    binding.homeScreen.isVisible = currentScreen is AppScreen.Home
    providerRegistry.render(currentScreen)
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
      statusTextView = binding.homeCardXStatusTextView,
      unavailableBadgeView = binding.homeCardXUnavailableBadgeTextView,
      chevronView = binding.homeCardXChevronImageView,
      state = resolveHomeProviderCardState(
        provider = FeedProvider.X,
        isAvailable = providerRegistry.hasProvider(FeedProvider.X),
      ),
    )
    updateHomeCardState(
      card = binding.homeCardTelegram,
      statusTextView = binding.homeCardTelegramStatusTextView,
      unavailableBadgeView = binding.homeCardTelegramUnavailableBadgeTextView,
      chevronView = binding.homeCardTelegramChevronImageView,
      state = resolveHomeProviderCardState(
        provider = FeedProvider.TELEGRAM,
        isAvailable = providerRegistry.hasProvider(FeedProvider.TELEGRAM),
      ),
    )

    binding.homeSubtitle.setText(resolveHomeSubtitleTextResId(providerRegistry.hasActiveProviders()))
  }

  private fun updateHomeCardState(
    card: View,
    statusTextView: TextView,
    unavailableBadgeView: TextView,
    chevronView: View,
    state: HomeProviderCardState,
  ) {
    card.isEnabled = state.isAvailable
    card.isClickable = state.isAvailable
    card.isFocusable = state.isAvailable
    card.alpha = if (state.isAvailable) 1f else DISABLED_HOME_CARD_ALPHA
    card.setBackgroundResource(
      if (state.isAvailable) R.drawable.feed_card_selector else R.drawable.feed_card_disabled,
    )
    statusTextView.setText(state.statusTextResId)
    statusTextView.setTextColor(card.context.getColor(state.statusTextColorResId))
    unavailableBadgeView.isVisible = state.showsUnavailableBadge
    chevronView.isVisible = state.showsChevron
  }

  private fun setupBackPressHandler() {
    onBackPressedDispatcher.addCallback(this) {
      when {
        providerRegistry.handleBackPress() -> Unit
        currentScreen is AppScreen.Home -> {
          // Temporarily step aside so Android can handle the real activity back press.
          isEnabled = false
          onBackPressedDispatcher.onBackPressed()
        }
        else -> showScreen(AppScreen.Home)
      }
    }
  }
}

internal data class HomeProviderCardState(
  val isAvailable: Boolean,
  val showsUnavailableBadge: Boolean,
  val showsChevron: Boolean,
  val statusTextResId: Int,
  val statusTextColorResId: Int,
)

internal fun resolveHomeProviderCardState(
  provider: FeedProvider,
  isAvailable: Boolean,
): HomeProviderCardState {
  return if (isAvailable) {
    when (provider) {
      FeedProvider.X -> HomeProviderCardState(
        isAvailable = true,
        showsUnavailableBadge = false,
        showsChevron = true,
        statusTextResId = R.string.home_card_x_available,
        statusTextColorResId = R.color.textSecondary,
      )
      FeedProvider.TELEGRAM -> HomeProviderCardState(
        isAvailable = true,
        showsUnavailableBadge = false,
        showsChevron = true,
        statusTextResId = R.string.home_card_telegram_available,
        statusTextColorResId = R.color.textSecondary,
      )
      FeedProvider.WHATSAPP -> HomeProviderCardState(
        isAvailable = true,
        showsUnavailableBadge = false,
        showsChevron = true,
        statusTextResId = R.string.coming_soon,
        statusTextColorResId = R.color.textSecondary,
      )
      FeedProvider.FACEBOOK -> HomeProviderCardState(
        isAvailable = true,
        showsUnavailableBadge = false,
        showsChevron = true,
        statusTextResId = R.string.coming_soon,
        statusTextColorResId = R.color.textSecondary,
      )
    }
  } else {
    when (provider) {
      FeedProvider.X -> HomeProviderCardState(
        isAvailable = false,
        showsUnavailableBadge = true,
        showsChevron = false,
        statusTextResId = R.string.home_card_x_unavailable,
        statusTextColorResId = R.color.comingSoonText,
      )
      FeedProvider.TELEGRAM -> HomeProviderCardState(
        isAvailable = false,
        showsUnavailableBadge = true,
        showsChevron = false,
        statusTextResId = R.string.home_card_telegram_unavailable,
        statusTextColorResId = R.color.comingSoonText,
      )
      FeedProvider.WHATSAPP -> HomeProviderCardState(
        isAvailable = false,
        showsUnavailableBadge = true,
        showsChevron = false,
        statusTextResId = R.string.coming_soon,
        statusTextColorResId = R.color.comingSoonText,
      )
      FeedProvider.FACEBOOK -> HomeProviderCardState(
        isAvailable = false,
        showsUnavailableBadge = true,
        showsChevron = false,
        statusTextResId = R.string.coming_soon,
        statusTextColorResId = R.color.comingSoonText,
      )
    }
  }
}

internal fun resolveHomeSubtitleTextResId(hasActiveProviders: Boolean): Int {
  return if (hasActiveProviders) R.string.home_subtitle else R.string.home_subtitle_unavailable
}

internal fun normalizeAppShellScreen(
  requestedScreen: AppScreen,
  supportsRequestedScreen: Boolean,
): AppScreen {
  return when {
    requestedScreen is AppScreen.Home -> requestedScreen
    supportsRequestedScreen -> requestedScreen
    else -> AppScreen.Home
  }
}
