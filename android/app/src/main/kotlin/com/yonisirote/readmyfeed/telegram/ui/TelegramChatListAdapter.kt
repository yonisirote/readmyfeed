package com.yonisirote.readmyfeed.telegram.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ItemTelegramChatBinding
import com.yonisirote.readmyfeed.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.telegram.chats.hasUnreadMessages

class TelegramChatListAdapter(
  private val onChatSelected: (TelegramChatPreview) -> Unit,
) : ListAdapter<TelegramChatPreview, TelegramChatListAdapter.TelegramChatViewHolder>(ChatDiffCallback) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TelegramChatViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return TelegramChatViewHolder(
      binding = ItemTelegramChatBinding.inflate(inflater, parent, false),
      onChatSelected = onChatSelected,
    )
  }

  override fun onBindViewHolder(holder: TelegramChatViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class TelegramChatViewHolder(
    private val binding: ItemTelegramChatBinding,
    private val onChatSelected: (TelegramChatPreview) -> Unit,
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: TelegramChatPreview) {
      val context = binding.root.context

      binding.root.setOnClickListener {
        onChatSelected(item)
      }

      binding.telegramChatTitleTextView.text = item.title.ifBlank {
        context.getString(R.string.telegram_chat_untitled)
      }

      binding.telegramChatPreviewTextView.text = item.lastMessagePreview.ifBlank {
        context.getString(R.string.telegram_chat_preview_empty)
      }

      val unreadLabel = resolveUnreadLabel(context, item)
      binding.telegramChatUnreadBadgeTextView.isVisible = unreadLabel != null
      binding.telegramChatUnreadBadgeTextView.text = unreadLabel.orEmpty()
    }

    private fun resolveUnreadLabel(
      context: android.content.Context,
      item: TelegramChatPreview,
    ): String? {
      return when {
        item.unreadCount > 0 -> context.getString(R.string.telegram_chat_unread_count, item.unreadCount)
        item.hasUnreadMessages() -> context.getString(R.string.telegram_chat_unread_marked)
        else -> null
      }
    }
  }
}

private object ChatDiffCallback : DiffUtil.ItemCallback<TelegramChatPreview>() {
  override fun areItemsTheSame(oldItem: TelegramChatPreview, newItem: TelegramChatPreview): Boolean {
    return oldItem.chatId == newItem.chatId
  }

  override fun areContentsTheSame(oldItem: TelegramChatPreview, newItem: TelegramChatPreview): Boolean {
    return oldItem == newItem
  }
}
