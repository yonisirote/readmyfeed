package com.yonisirote.readmyfeed.telegram.ui

import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.telegram.chats.TelegramChatPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelegramChatListAdapterTest {
  @Test
  fun bindsFallbacksUnreadBadgeAndClickCallback() {
    val clicked = mutableListOf<Long>()
    val adapter = TelegramChatListAdapter { preview ->
      clicked += preview.chatId
    }
    val activity = buildActivity()
    val parent = FrameLayout(activity)
    val viewHolder = adapter.onCreateViewHolder(parent, 0)
    val preview = TelegramChatPreview(
      chatId = 42L,
      title = "",
      unreadCount = 3,
      order = 5L,
      lastMessagePreview = "",
    )

    viewHolder.bind(preview)
    viewHolder.itemView.performClick()

    val title = viewHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramChatTitleTextView)
    val body = viewHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramChatPreviewTextView)
    val badge = viewHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramChatUnreadBadgeTextView)

    assertEquals("Untitled chat", title.text.toString())
    assertEquals("No supported message preview yet.", body.text.toString())
    assertEquals("3", badge.text.toString())
    assertEquals(android.view.View.VISIBLE, badge.visibility)
    assertEquals(listOf(42L), clicked)
  }

  @Test
  fun markedUnreadWithoutCountShowsUnreadLabel() {
    val adapter = TelegramChatListAdapter { }
    val activity = buildActivity()
    val parent = FrameLayout(activity)
    val viewHolder = adapter.onCreateViewHolder(parent, 0)

    viewHolder.bind(
      TelegramChatPreview(
        chatId = 7L,
        title = "Chat",
        unreadCount = 0,
        order = 1L,
        isMarkedAsUnread = true,
      ),
    )

    val badge = viewHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramChatUnreadBadgeTextView)
    assertEquals("Unread", badge.text.toString())
  }

  @Test
  fun noUnreadStateHidesBadge() {
    val adapter = TelegramChatListAdapter { }
    val activity = buildActivity()
    val parent = FrameLayout(activity)
    val viewHolder = adapter.onCreateViewHolder(parent, 0)

    viewHolder.bind(
      TelegramChatPreview(
        chatId = 8L,
        title = "Chat",
        unreadCount = 0,
        order = 1L,
      ),
    )

    val badge = viewHolder.itemView.findViewById<android.widget.TextView>(R.id.telegramChatUnreadBadgeTextView)
    assertEquals(android.view.View.GONE, badge.visibility)
  }

  private fun buildActivity(): AppCompatActivity {
    return Robolectric.buildActivity(AppCompatActivity::class.java).setup().get().apply {
      setTheme(R.style.Theme_ReadMyFeed)
    }
  }
}
