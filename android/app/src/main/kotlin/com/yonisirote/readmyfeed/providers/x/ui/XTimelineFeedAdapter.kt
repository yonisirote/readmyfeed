package com.yonisirote.readmyfeed.providers.x.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ItemXTimelineEntryBinding
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineItem
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineMedia
import com.yonisirote.readmyfeed.providers.x.timeline.XTimelineMediaType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class XTimelineFeedAdapter : ListAdapter<XTimelineItem, XTimelineFeedAdapter.TimelineViewHolder>(DiffCallback) {
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return TimelineViewHolder(ItemXTimelineEntryBinding.inflate(inflater, parent, false))
  }

  override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class TimelineViewHolder(
    private val binding: ItemXTimelineEntryBinding,
  ) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: XTimelineItem) {
      val context = binding.root.context
      val authorName = item.authorName.ifBlank {
        if (item.authorHandle.isNotBlank()) {
          context.getString(R.string.feed_handle_format, item.authorHandle)
        } else {
          context.getString(R.string.feed_unknown_author)
        }
      }

      binding.authorTextView.text = authorName

      binding.handleTextView.isVisible = item.authorHandle.isNotBlank()
      binding.handleTextView.text = context.getString(R.string.feed_handle_format, item.authorHandle)

      binding.bodyTextView.text = item.text.ifBlank { context.getString(R.string.tweet_fallback_text) }

      // Keep the quote card visible for media-only or author-only quotes that have no text body.
      val hasQuote = item.quotedText.isNotBlank() || item.quotedMedia.isNotEmpty() || item.quotedAuthorHandle.isNotBlank()
      binding.quoteContainer.isVisible = hasQuote
      binding.quoteTitleTextView.text = if (item.quotedAuthorHandle.isNotBlank()) {
        context.getString(R.string.feed_quote_from_handle, item.quotedAuthorHandle)
      } else {
        context.getString(R.string.feed_quote_label)
      }
      binding.quoteBodyTextView.isVisible = item.quotedText.isNotBlank()
      binding.quoteBodyTextView.text = item.quotedText
      binding.quoteMediaTextView.isVisible = item.quotedMedia.isNotEmpty()
      binding.quoteMediaTextView.text = if (item.quotedMedia.isNotEmpty()) {
        context.getString(R.string.feed_quoted_media_label, describeMedia(item.quotedMedia))
      } else {
        ""
      }

      binding.mediaTextView.isVisible = item.media.isNotEmpty()
      binding.mediaTextView.text = if (item.media.isNotEmpty()) {
        context.getString(R.string.feed_media_label, describeMedia(item.media))
      } else {
        ""
      }

      binding.metaTextView.text = buildMetaText(item)
    }

    private fun buildMetaText(item: XTimelineItem): String {
      val context = binding.root.context
      val parts = mutableListOf<String>()

      parts += when {
        item.isRetweet -> context.getString(R.string.feed_post_type_repost)
        item.replyTo.isNotBlank() -> context.getString(R.string.feed_post_type_reply)
        else -> context.getString(R.string.feed_post_type_post)
      }

      formatTimestamp(item.createdAt)?.let(parts::add)

      if (item.replyCount > 0) {
        parts += context.getString(R.string.feed_count_replies, numberFormatter.format(item.replyCount))
      }
      if (item.retweetCount > 0) {
        parts += context.getString(R.string.feed_count_reposts, numberFormatter.format(item.retweetCount))
      }
      if (item.likeCount > 0) {
        parts += context.getString(R.string.feed_count_likes, numberFormatter.format(item.likeCount))
      }
      if (item.viewCount != null && item.viewCount > 0) {
        parts += context.getString(R.string.feed_count_views, numberFormatter.format(item.viewCount))
      }

      return parts.joinToString(separator = "  •  ")
    }

    private fun formatTimestamp(createdAt: String): String? {
      if (createdAt.isBlank()) {
        return null
      }

      return try {
        timelineDateFormatter.format(Instant.parse(createdAt).atZone(ZoneId.systemDefault()))
      } catch (_: Exception) {
        null
      }
    }

    private fun describeMedia(media: List<XTimelineMedia>): String {
      val grouped = linkedMapOf<XTimelineMediaType, Int>()
      for (item in media) {
        grouped[item.type] = (grouped[item.type] ?: 0) + 1
      }

      return grouped.entries.joinToString(separator = ", ") { (type, count) ->
        val noun = when (type) {
          XTimelineMediaType.PHOTO -> if (count == 1) "photo" else "photos"
          XTimelineMediaType.VIDEO -> if (count == 1) "video" else "videos"
          XTimelineMediaType.ANIMATED_GIF -> if (count == 1) "GIF" else "GIFs"
          XTimelineMediaType.UNKNOWN -> if (count == 1) "attachment" else "attachments"
        }
        "$count $noun"
      }
    }
  }
}

private object DiffCallback : DiffUtil.ItemCallback<XTimelineItem>() {
  override fun areItemsTheSame(oldItem: XTimelineItem, newItem: XTimelineItem): Boolean {
    return oldItem.id == newItem.id
  }

  override fun areContentsTheSame(oldItem: XTimelineItem, newItem: XTimelineItem): Boolean {
    return oldItem == newItem
  }
}

private val numberFormatter: NumberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

private val timelineDateFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
