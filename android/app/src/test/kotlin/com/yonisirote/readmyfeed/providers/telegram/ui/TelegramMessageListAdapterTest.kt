package com.yonisirote.readmyfeed.providers.telegram.ui

import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageContentKind
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelegramMessageListAdapterTest {
  @Test
  fun bindsUnknownAuthorAndContentKindLabels() {
    val adapter = TelegramMessageListAdapter()
    val activity = buildActivity()
    val parent = FrameLayout(activity)
    val viewHolder = adapter.onCreateViewHolder(parent, 0)

    viewHolder.bind(
      TelegramMessageItem(
        messageId = 1L,
        chatId = 2L,
        authorLabel = "",
        text = "caption body",
        lang = "en",
        isUnread = true,
        contentKind = TelegramMessageContentKind.CAPTION,
      ),
    )

    val author = viewHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramMessageAuthorTextView)
    val body = viewHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramMessageBodyTextView)
    val kind = viewHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramMessageKindTextView)

    assertEquals("Unknown sender", author.text.toString())
    assertEquals("caption body", body.text.toString())
    assertEquals("Caption", kind.text.toString())
  }

  @Test
  fun bindsTextAndUnsupportedLabels() {
    val adapter = TelegramMessageListAdapter()
    val activity = buildActivity()
    val parent = FrameLayout(activity)
    val firstHolder = adapter.onCreateViewHolder(parent, 0)
    val secondHolder = adapter.onCreateViewHolder(parent, 0)

    firstHolder.bind(
      TelegramMessageItem(
        messageId = 2L,
        chatId = 2L,
        authorLabel = "Alice",
        text = "hello",
        lang = "en",
        isUnread = true,
        contentKind = TelegramMessageContentKind.TEXT,
      ),
    )
    secondHolder.bind(
      TelegramMessageItem(
        messageId = 3L,
        chatId = 2L,
        authorLabel = "Bot",
        text = "service",
        lang = "en",
        isUnread = true,
        contentKind = TelegramMessageContentKind.UNSUPPORTED,
      ),
    )

    val firstKind = firstHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramMessageKindTextView)
    val secondKind = secondHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramMessageKindTextView)

    assertEquals("Text", firstKind.text.toString())
    assertEquals("Unsupported", secondKind.text.toString())
  }

  private fun buildActivity(): AppCompatActivity {
    return Robolectric.buildActivity(AppCompatActivity::class.java).setup().get().apply {
      setTheme(R.style.Theme_ReadMyFeed)
    }
  }
}
