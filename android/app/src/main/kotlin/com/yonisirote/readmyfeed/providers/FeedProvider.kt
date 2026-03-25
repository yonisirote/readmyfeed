package com.yonisirote.readmyfeed.providers

enum class FeedProvider(
  val isAvailable: Boolean,
) {
  X(
    isAvailable = true,
  ),
  TELEGRAM(
    isAvailable = true,
  ),
  WHATSAPP(
    isAvailable = false,
  ),
  FACEBOOK(
    isAvailable = false,
  ),
}
