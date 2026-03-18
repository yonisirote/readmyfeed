package com.yonisirote.readmyfeed.telegram.client

import android.content.Context
import android.os.Build
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale

fun interface TelegramTdlibParametersFactory {
  fun create(): TdApi.SetTdlibParameters
}

class AndroidTelegramTdlibParametersFactory(
  private val context: Context,
  private val configProvider: () -> TelegramClientConfig = ::buildTelegramClientConfigFromBuildConfig,
) : TelegramTdlibParametersFactory {
  override fun create(): TdApi.SetTdlibParameters {
    val config = configProvider()
    val rootDirectory = ensureDirectory(File(context.noBackupFilesDir, "telegram/tdlib"))
    val databaseDirectory = ensureDirectory(File(rootDirectory, "database"))
    val filesDirectory = ensureDirectory(File(rootDirectory, "files"))

    return TdApi.SetTdlibParameters(
      config.useTestDc,
      databaseDirectory.absolutePath,
      filesDirectory.absolutePath,
      byteArrayOf(),
      true,
      true,
      true,
      true,
      config.apiId,
      config.apiHash,
      resolveSystemLanguageCode(),
      resolveDeviceModel(),
      resolveSystemVersion(),
      config.applicationVersion,
    )
  }

  private fun ensureDirectory(directory: File): File {
    if (directory.isDirectory || directory.mkdirs()) {
      return directory
    }

    throw TelegramClientException(
      message = "Failed to create TDLib directory.",
      code = TelegramClientErrorCodes.DIRECTORY_CREATE_FAILED,
      context = mapOf("path" to directory.absolutePath),
    )
  }

  private fun resolveSystemLanguageCode(): String {
    val languageTag = Locale.getDefault().toLanguageTag().trim()
    return if (languageTag.isBlank()) {
      "en"
    } else {
      languageTag
    }
  }

  private fun resolveDeviceModel(): String {
    val modelParts = listOf(Build.MANUFACTURER, Build.MODEL)
      .mapNotNull { value -> value?.trim()?.takeIf { it.isNotBlank() } }
      .distinct()

    return if (modelParts.isEmpty()) {
      "Android"
    } else {
      modelParts.joinToString(separator = " ")
    }
  }

  private fun resolveSystemVersion(): String {
    return Build.VERSION.RELEASE?.trim().orEmpty()
  }
}
