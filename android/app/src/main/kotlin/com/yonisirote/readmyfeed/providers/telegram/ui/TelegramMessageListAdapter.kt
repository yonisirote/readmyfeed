package com.yonisirote.readmyfeed.providers.telegram.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ItemTelegramMessageBinding
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageContentKind
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem

class TelegramMessageListAdapter :
  ListAdapter<TelegramMessageItem, TelegramMessageListAdapter.TelegramMessageViewHolder>(MessageDiffCallback) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TelegramMessageViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return TelegramMessageViewHolder(ItemTelegramMessageBinding.inflate(inflater, parent, false))
  }

  override fun onBindViewHolder(holder: TelegramMessageViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class TelegramMessageViewHolder(
    private val binding: ItemTelegramMessageBinding,
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: TelegramMessageItem) {
      val context = binding.root.context
      binding.telegramMessageAuthorTextView.text = item.authorLabel.ifBlank {
        context.getString(R.string.telegram_message_unknown_author)
      }
      binding.telegramMessageBodyTextView.text = item.text
      binding.telegramMessageKindTextView.text = when (item.contentKind) {
        TelegramMessageContentKind.TEXT -> context.getString(R.string.telegram_message_kind_text)
        TelegramMessageContentKind.CAPTION -> context.getString(R.string.telegram_message_kind_caption)
        TelegramMessageContentKind.UNSUPPORTED -> context.getString(R.string.telegram_message_kind_unsupported)
      }
    }
  }
}

private object MessageDiffCallback : DiffUtil.ItemCallback<TelegramMessageItem>() {
  override fun areItemsTheSame(oldItem: TelegramMessageItem, newItem: TelegramMessageItem): Boolean {
    return oldItem.messageId == newItem.messageId
  }

  override fun areContentsTheSame(oldItem: TelegramMessageItem, newItem: TelegramMessageItem): Boolean {
    return oldItem == newItem
  }
}
