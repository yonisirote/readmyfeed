package com.yonisirote.readmyfeed.shell

import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.providers.FeedProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeProviderCardStateTest {
  @Test
  fun resolveHomeProviderCardStateReturnsAvailableXState() {
    val state = resolveHomeProviderCardState(
      provider = FeedProvider.X,
      isAvailable = true,
    )

    assertTrue(state.isAvailable)
    assertTrue(state.showsChevron)
    assertFalse(state.showsUnavailableBadge)
    assertEquals(R.string.home_card_x_available, state.statusTextResId)
    assertEquals(R.color.textSecondary, state.statusTextColorResId)
  }

  @Test
  fun resolveHomeProviderCardStateReturnsUnavailableTelegramState() {
    val state = resolveHomeProviderCardState(
      provider = FeedProvider.TELEGRAM,
      isAvailable = false,
    )

    assertFalse(state.isAvailable)
    assertFalse(state.showsChevron)
    assertTrue(state.showsUnavailableBadge)
    assertEquals(R.string.home_card_telegram_unavailable, state.statusTextResId)
    assertEquals(R.color.comingSoonText, state.statusTextColorResId)
  }

  @Test
  fun resolveHomeSubtitleTextResIdReturnsDefaultCopyWhenProviderIsAvailable() {
    assertEquals(R.string.home_subtitle, resolveHomeSubtitleTextResId(hasActiveProviders = true))
  }

  @Test
  fun resolveHomeSubtitleTextResIdReturnsUnavailableCopyWhenNoProviderIsAvailable() {
    assertEquals(
      R.string.home_subtitle_unavailable,
      resolveHomeSubtitleTextResId(hasActiveProviders = false),
    )
  }
}
