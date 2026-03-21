package com.yonisirote.readmyfeed.providers.telegram.client

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

interface TelegramTdlibClient {
  fun send(request: TdApi.Function<*>, resultHandler: (TdApi.Object) -> Unit)
}

interface TelegramTdlibClientFactory {
  fun create(
    updateHandler: (TdApi.Object) -> Unit,
    exceptionHandler: (Throwable) -> Unit,
  ): TelegramTdlibClient
}

object RealTelegramTdlibClientFactory : TelegramTdlibClientFactory {
  override fun create(
    updateHandler: (TdApi.Object) -> Unit,
    exceptionHandler: (Throwable) -> Unit,
  ): TelegramTdlibClient {
    // TDLib reports exceptions through both callback channels, so normalize them into one handler.
    val client = Client.create(
      Client.ResultHandler { result -> updateHandler(result) },
      Client.ExceptionHandler { error -> exceptionHandler(error) },
      Client.ExceptionHandler { error -> exceptionHandler(error) },
    )

    return RealTelegramTdlibClient(client)
  }
}

private class RealTelegramTdlibClient(
  private val client: Client,
) : TelegramTdlibClient {
  override fun send(request: TdApi.Function<*>, resultHandler: (TdApi.Object) -> Unit) {
    client.send(
      request,
      Client.ResultHandler { result -> resultHandler(result) },
    )
  }
}
