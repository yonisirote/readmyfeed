package com.yonisirote.readmyfeed.providers.telegram.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.telegram.chats.hasUnreadMessages
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientSnapshot
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState

internal class TelegramChatListScreenController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val chatListAdapter: TelegramChatListAdapter,
  private val showHome: () -> Unit,
  private val onRequestChatListLoad: () -> Unit,
) {
  fun initialize() {
    binding.backFromTelegramChatList.setOnClickListener {
      showHome()
    }
    binding.telegramLoadChatsButton.setOnClickListener {
      onRequestChatListLoad()
    }
    binding.telegramChatRecyclerView.layoutManager = LinearLayoutManager(activity)
    binding.telegramChatRecyclerView.adapter = chatListAdapter
  }

  fun render(snapshot: TelegramClientSnapshot) {
    val previews = snapshot.chatPreviews
    val unreadChatCount = previews.count { preview -> preview.hasUnreadMessages() }
    val screenState = resolveTelegramChatListScreenState(snapshot)
    val stage = screenState.stage
    val errorMessage = screenState.errorMessage.orEmpty().trim()

    chatListAdapter.submitList(previews)

    binding.telegramChatListSummaryTextView.text = resolveChatListSummary(
      snapshot = snapshot,
      previewCount = previews.size,
      unreadChatCount = unreadChatCount,
      stage = stage,
    )
    binding.telegramLoadChatsButton.text = activity.getString(
      if (snapshot.hasLoadedChatList) R.string.telegram_load_more_chats else R.string.telegram_load_chats,
    )
    binding.telegramLoadChatsButton.isVisible = snapshot.authState is TelegramAuthState.Ready
    binding.telegramLoadChatsButton.isEnabled =
      snapshot.authState is TelegramAuthState.Ready &&
        !snapshot.isChatListLoading &&
        !snapshot.hasLoadedAllChats

    binding.telegramChatListErrorTextView.isVisible = errorMessage.isNotBlank()
    binding.telegramChatListErrorTextView.text = errorMessage

    binding.telegramChatRecyclerView.isVisible = previews.isNotEmpty()
    binding.telegramChatListLoadingStateContainer.isVisible =
      stage == TelegramChatListStage.LOADING && previews.isEmpty()
    binding.telegramChatListEmptyStateContainer.isVisible =
      previews.isEmpty() && stage != TelegramChatListStage.LOADING

    binding.telegramChatListEmptyTitleTextView.text = resolveChatListEmptyTitle(stage)
    binding.telegramChatListEmptyBodyTextView.text = resolveChatListEmptyBody(stage)
  }

  fun onDestroy() {
    binding.telegramChatRecyclerView.adapter = null
  }

  private fun resolveChatListSummary(
    snapshot: TelegramClientSnapshot,
    previewCount: Int,
    unreadChatCount: Int,
    stage: TelegramChatListStage,
  ): String {
    return when {
      snapshot.isChatListLoading && previewCount > 0 -> activity.getString(
        R.string.telegram_chat_list_summary_loading_more,
        previewCount,
      )
      stage == TelegramChatListStage.LOADING -> activity.getString(R.string.telegram_chat_list_summary_loading)
      stage == TelegramChatListStage.ERROR -> activity.getString(R.string.telegram_chat_list_summary_error)
      stage == TelegramChatListStage.READY -> activity.getString(
        R.string.telegram_chat_list_summary_ready,
        previewCount,
        unreadChatCount,
      )
      else -> activity.getString(R.string.telegram_chat_list_summary_empty)
    }
  }

  private fun resolveChatListEmptyTitle(stage: TelegramChatListStage): String {
    return when (stage) {
      TelegramChatListStage.ERROR -> activity.getString(R.string.telegram_chat_list_error_title)
      else -> activity.getString(R.string.telegram_chat_list_empty_title)
    }
  }

  private fun resolveChatListEmptyBody(stage: TelegramChatListStage): String {
    return when (stage) {
      TelegramChatListStage.ERROR -> activity.getString(R.string.telegram_chat_list_error_body)
      else -> activity.getString(R.string.telegram_chat_list_empty_body)
    }
  }
}
