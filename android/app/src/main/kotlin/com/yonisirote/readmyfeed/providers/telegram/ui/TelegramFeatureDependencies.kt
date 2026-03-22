package com.yonisirote.readmyfeed.providers.telegram.ui

import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.providers.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientManager
import com.yonisirote.readmyfeed.providers.telegram.speech.TelegramMessageSpeechPlayer
import com.yonisirote.readmyfeed.tts.TtsService

internal data class TelegramFeatureDependencies(
  val clientManager: TelegramClientManager,
  val chatListAdapter: TelegramChatListAdapter,
  val messageListAdapter: TelegramMessageListAdapter,
  val messageSpeechPlayer: TelegramMessageSpeechPlayer,
)

internal fun createTelegramFeatureDependencies(
  activity: AppCompatActivity,
  clientManagerFactory: (AppCompatActivity) -> TelegramClientManager,
  ttsServiceFactory: (AppCompatActivity) -> TtsService,
  messageSpeechPlayerFactory: (TtsService) -> TelegramMessageSpeechPlayer,
  onOpenSelectedChat: (TelegramChatPreview) -> Unit,
): TelegramFeatureDependencies {
  val clientManager = clientManagerFactory(activity)
  val ttsService = ttsServiceFactory(activity)

  return TelegramFeatureDependencies(
    clientManager = clientManager,
    chatListAdapter = TelegramChatListAdapter(onOpenSelectedChat),
    messageListAdapter = TelegramMessageListAdapter(),
    messageSpeechPlayer = messageSpeechPlayerFactory(ttsService),
  )
}
