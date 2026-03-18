package com.yonisirote.readmyfeed

enum class FeedProvider(
  val isAvailable: Boolean,
  val connectDestination: ProviderDestination,
  val connectedDestination: ProviderDestination,
) {
  X(
    isAvailable = true,
    connectDestination = ProviderDestination.CONNECT,
    connectedDestination = ProviderDestination.CONTENT_LIST,
  ),
  TELEGRAM(
    isAvailable = true,
    connectDestination = ProviderDestination.CONNECT,
    connectedDestination = ProviderDestination.CHAT_LIST,
  ),
  WHATSAPP(
    isAvailable = false,
    connectDestination = ProviderDestination.CONNECT,
    connectedDestination = ProviderDestination.CHAT_LIST,
  ),
  FACEBOOK(
    isAvailable = false,
    connectDestination = ProviderDestination.CONNECT,
    connectedDestination = ProviderDestination.CONTENT_LIST,
  ),
}
