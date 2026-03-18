package com.yonisirote.readmyfeed.providers.telegram.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramClientConfigTest {
  @Test
  fun trimsAndParsesBuildConfigValues() {
    val config = buildTelegramClientConfig(
      apiIdValue = " 12345 ",
      apiHashValue = " hash-value ",
      applicationVersion = "1.2.3",
    )

    assertEquals(12345, config.apiId)
    assertEquals("hash-value", config.apiHash)
    assertEquals("1.2.3", config.applicationVersion)
    assertTrue(!config.useTestDc)
  }

  @Test
  fun invalidApiIdThrowsConfigError() {
    try {
      buildTelegramClientConfig(
        apiIdValue = "abc",
        apiHashValue = "hash",
        applicationVersion = "1.0",
      )
    } catch (error: TelegramClientException) {
      assertEquals(TelegramClientErrorCodes.CONFIG_INVALID, error.code)
      assertEquals(mapOf("value" to "abc"), error.context)
      return
    }

    throw AssertionError("Expected TelegramClientException")
  }

  @Test
  fun blankApiHashThrowsConfigError() {
    try {
      buildTelegramClientConfig(
        apiIdValue = "123",
        apiHashValue = "   ",
        applicationVersion = "1.0",
      )
    } catch (error: TelegramClientException) {
      assertEquals(TelegramClientErrorCodes.CONFIG_INVALID, error.code)
      assertEquals("Telegram API hash is missing in BuildConfig.", error.message)
      return
    }

    throw AssertionError("Expected TelegramClientException")
  }
}
