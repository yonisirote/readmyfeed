package com.yonisirote.readmyfeed.providers.telegram.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientSnapshot
import com.yonisirote.readmyfeed.providers.telegram.speech.TelegramMessageSpeechPlayer
import com.yonisirote.readmyfeed.tts.ScreenPlaybackController
import com.yonisirote.readmyfeed.tts.TtsPlaybackSummary

internal class TelegramMessageScreenController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val messageListAdapter: TelegramMessageListAdapter,
  private val messageSpeechPlayer: TelegramMessageSpeechPlayer,
  private val isChatMessagesVisible: () -> Boolean,
  private val onBackToChatList: () -> Unit,
  private val onRequestMessagesLoad: () -> Unit,
) {
  private var latestSnapshot: TelegramClientSnapshot = TelegramClientSnapshot()
  private var isSpeakingMessages = false
  private lateinit var playbackController: ScreenPlaybackController<com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem>

  fun initialize() {
    playbackController = ScreenPlaybackController(
      coroutineScope = activity.lifecycleScope,
      hasSpeakableItems = messageSpeechPlayer::hasSpeakableItems,
      speak = messageSpeechPlayer::speak,
      stopPlayback = messageSpeechPlayer::stop,
      renderLoadingStatus = {
        binding.telegramMessageSpeechStatusTextView.text = activity.getString(
          R.string.telegram_message_speech_status_loading,
        )
      },
      renderProgressStatus = { index, total ->
        activity.runOnUiThread {
          if (isSpeakingMessages) {
            binding.telegramMessageSpeechStatusTextView.text = activity.getString(
              R.string.telegram_message_speech_status_playing,
              index,
              total,
            )
          }
        }
      },
      renderNoItemsStatus = {
        binding.telegramMessageSpeechStatusTextView.text = activity.getString(
          R.string.telegram_message_speech_status_no_items,
        )
      },
      renderFinishedStatus = { summary ->
        binding.telegramMessageSpeechStatusTextView.text = resolveFinishedPlaybackStatus(summary)
      },
      renderErrorStatus = { message ->
        binding.telegramMessageSpeechStatusTextView.text = buildTelegramSpeechErrorMessage(message)
      },
      onPlaybackStateChanged = { isPlaying ->
        isSpeakingMessages = isPlaying
        updateMessageSpeechControls(latestSnapshot)
      },
    )
    binding.backFromTelegramChatMessages.setOnClickListener {
      onBackToChatList()
    }
    binding.telegramReloadMessagesButton.setOnClickListener {
      onRequestMessagesLoad()
    }
    binding.telegramPlayMessagesButton.setOnClickListener {
      if (isSpeakingMessages) {
        return@setOnClickListener
      }
      startMessagePlayback()
    }
    binding.telegramStopMessagesButton.setOnClickListener {
      stopPlayback(activity.getString(R.string.telegram_message_speech_status_stopped))
    }
    binding.telegramMessageRecyclerView.layoutManager = LinearLayoutManager(activity)
    binding.telegramMessageRecyclerView.adapter = messageListAdapter
  }

  fun render(snapshot: TelegramClientSnapshot) {
    latestSnapshot = snapshot

    val screenState = resolveTelegramMessageListScreenState(snapshot)
    val stage = screenState.stage
    val messages = snapshot.selectedChatMessages
    val selectedChatTitle = snapshot.selectedChatPreview?.title.orEmpty().ifBlank {
      activity.getString(R.string.telegram_chat_untitled)
    }
    val errorMessage = screenState.errorMessage.orEmpty().trim()

    messageListAdapter.submitList(messages)

    binding.telegramChatMessagesTitleTextView.text = selectedChatTitle
    binding.telegramChatMessagesSummaryTextView.text = resolveChatMessagesSummary(snapshot, stage)
    binding.telegramChatMessagesErrorTextView.isVisible = errorMessage.isNotBlank()
    binding.telegramChatMessagesErrorTextView.text = errorMessage

    binding.telegramReloadMessagesButton.isEnabled =
      snapshot.selectedChatPreview != null && !snapshot.isChatMessagesLoading

    binding.telegramMessageRecyclerView.isVisible = messages.isNotEmpty()
    binding.telegramChatMessagesLoadingStateContainer.isVisible =
      stage == TelegramMessageListStage.LOADING && messages.isEmpty()
    binding.telegramChatMessagesEmptyStateContainer.isVisible =
      messages.isEmpty() && stage != TelegramMessageListStage.LOADING

    binding.telegramChatMessagesEmptyTitleTextView.text = resolveChatMessagesEmptyTitle(stage)
    binding.telegramChatMessagesEmptyBodyTextView.text = resolveChatMessagesEmptyBody(stage)

    // Do not overwrite live playback progress just because the screen re-rendered.
    if (messages.isEmpty() && !isSpeakingMessages) {
      binding.telegramMessageSpeechStatusTextView.text = activity.getString(
        R.string.telegram_message_speech_status_no_items,
      )
    } else if (!isSpeakingMessages) {
      binding.telegramMessageSpeechStatusTextView.text = activity.getString(
        R.string.telegram_message_speech_status_idle,
      )
    }

    updateMessageSpeechControls(snapshot)
  }

  fun stopPlayback(status: String?) {
    playbackController.stop(
      status = status?.let { message ->
        { binding.telegramMessageSpeechStatusTextView.text = message }
      },
    )
  }

  fun onDestroy() {
    playbackController.shutdown()
    binding.telegramMessageRecyclerView.adapter = null
    messageSpeechPlayer.shutdown()
  }

  private fun resolveChatMessagesSummary(
    snapshot: TelegramClientSnapshot,
    stage: TelegramMessageListStage,
  ): String {
    return when {
      stage == TelegramMessageListStage.SELECT_CHAT -> activity.getString(
        R.string.telegram_chat_messages_select_body,
      )
      stage == TelegramMessageListStage.LOADING -> activity.getString(
        R.string.telegram_chat_messages_summary_loading,
      )
      stage == TelegramMessageListStage.ERROR -> activity.getString(
        R.string.telegram_chat_messages_summary_error,
      )
      stage == TelegramMessageListStage.READY -> activity.getString(
        R.string.telegram_chat_messages_summary_ready,
        snapshot.selectedChatMessages.size,
      )
      else -> activity.getString(R.string.telegram_chat_messages_summary_empty)
    }
  }

  private fun resolveChatMessagesEmptyTitle(stage: TelegramMessageListStage): String {
    return when (stage) {
      TelegramMessageListStage.SELECT_CHAT -> activity.getString(R.string.telegram_chat_messages_select_title)
      TelegramMessageListStage.ERROR -> activity.getString(R.string.telegram_chat_messages_error_title)
      else -> activity.getString(R.string.telegram_chat_messages_empty_title)
    }
  }

  private fun resolveChatMessagesEmptyBody(stage: TelegramMessageListStage): String {
    return when (stage) {
      TelegramMessageListStage.SELECT_CHAT -> activity.getString(R.string.telegram_chat_messages_select_body)
      TelegramMessageListStage.ERROR -> activity.getString(R.string.telegram_chat_messages_error_body)
      else -> activity.getString(R.string.telegram_chat_messages_empty_body)
    }
  }

  private fun updateMessageSpeechControls(snapshot: TelegramClientSnapshot) {
    val canPlay = isChatMessagesVisible() &&
      snapshot.selectedChatMessages.isNotEmpty() &&
      !snapshot.isChatMessagesLoading &&
      !isSpeakingMessages

    binding.telegramPlayMessagesButton.isEnabled = canPlay
    binding.telegramStopMessagesButton.isEnabled = isSpeakingMessages
  }

  private fun startMessagePlayback() {
    playbackController.start(latestSnapshot.selectedChatMessages)
  }

  private fun buildTelegramSpeechErrorMessage(message: String?): String {
    return activity.getString(R.string.telegram_message_speech_status_error_prefix) +
      " " +
      (message ?: "Unknown error.")
  }

  private fun resolveFinishedPlaybackStatus(summary: TtsPlaybackSummary): String {
    return when {
      summary.spokenItems <= 0 -> activity.getString(R.string.telegram_message_speech_status_no_items)
      summary.skippedItems > 0 -> activity.getString(
        R.string.telegram_message_speech_status_skipped,
        summary.spokenItems,
        summary.skippedItems,
      )
      else -> activity.getString(R.string.telegram_message_speech_status_done, summary.spokenItems)
    }
  }
}
