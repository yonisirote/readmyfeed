package com.yonisirote.readmyfeed.telegram.client

class TelegramClientException(
  message: String,
  val code: String,
  val context: Map<String, Any?> = emptyMap(),
  cause: Throwable? = null,
) : Exception(message, cause)

object TelegramClientErrorCodes {
  const val CONFIG_INVALID = "TELEGRAM_CLIENT_CONFIG_INVALID"
  const val CLIENT_CREATE_FAILED = "TELEGRAM_CLIENT_CREATE_FAILED"
  const val PARAMETERS_CREATE_FAILED = "TELEGRAM_CLIENT_PARAMETERS_CREATE_FAILED"
  const val REQUEST_FAILED = "TELEGRAM_CLIENT_REQUEST_FAILED"
  const val UNEXPECTED_RESPONSE = "TELEGRAM_CLIENT_UNEXPECTED_RESPONSE"
  const val CALLBACK_FAILED = "TELEGRAM_CLIENT_CALLBACK_FAILED"
  const val DIRECTORY_CREATE_FAILED = "TELEGRAM_CLIENT_DIRECTORY_CREATE_FAILED"
  const val INVALID_CHAT_SELECTION = "TELEGRAM_CLIENT_INVALID_CHAT_SELECTION"
  const val NO_CHAT_SELECTED = "TELEGRAM_CLIENT_NO_CHAT_SELECTED"
}
