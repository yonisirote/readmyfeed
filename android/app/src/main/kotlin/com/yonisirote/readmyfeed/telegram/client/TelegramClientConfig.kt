package com.yonisirote.readmyfeed.telegram.client

import com.yonisirote.readmyfeed.BuildConfig

data class TelegramClientConfig(
  val apiId: Int,
  val apiHash: String,
  val applicationVersion: String,
  val useTestDc: Boolean = false,
)

fun buildTelegramClientConfigFromBuildConfig(): TelegramClientConfig {
  return buildTelegramClientConfig(
    apiIdValue = BuildConfig.TELEGRAM_API_ID,
    apiHashValue = BuildConfig.TELEGRAM_API_HASH,
    applicationVersion = BuildConfig.VERSION_NAME,
  )
}

internal fun buildTelegramClientConfig(
  apiIdValue: String,
  apiHashValue: String,
  applicationVersion: String,
): TelegramClientConfig {
  val apiId = apiIdValue.trim().toIntOrNull() ?: throw TelegramClientException(
    message = "Telegram API ID is missing or invalid in BuildConfig.",
    code = TelegramClientErrorCodes.CONFIG_INVALID,
    context = mapOf("value" to apiIdValue),
  )
  val apiHash = apiHashValue.trim()
  if (apiHash.isBlank()) {
    throw TelegramClientException(
      message = "Telegram API hash is missing in BuildConfig.",
      code = TelegramClientErrorCodes.CONFIG_INVALID,
    )
  }

  return TelegramClientConfig(
    apiId = apiId,
    apiHash = apiHash,
    applicationVersion = applicationVersion,
  )
}
