package com.yonisirote.readmyfeed.providers.telegram.client

import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthErrorCodes
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthException
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageContentKind
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramClientManagerTest {
  @Test
  fun waitTdlibParametersUpdateSendsInitializationParameters() {
    val factory = FakeTelegramTdlibClientFactory()
    val parameters = TdApi.SetTdlibParameters(
      false,
      "/tmp/database",
      "/tmp/files",
      byteArrayOf(),
      true,
      true,
      true,
      true,
      123,
      "hash",
      "en",
      "Pixel",
      "14",
      "1.0",
    )
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { parameters },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))

    assertEquals(TelegramAuthState.WaitTdlibParameters, manager.snapshot.value.authState)
    assertEquals(1, factory.client.requests.size)
    assertTrue(factory.client.requests.single() is TdApi.SetTdlibParameters)
  }

  @Test
  fun requestErrorsAreExposedOnSnapshot() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
    factory.client.enqueueResult(TdApi.Error(400, "PHONE_NUMBER_INVALID"))

    manager.submitPhoneNumber("+15551234567")

    assertEquals(TelegramAuthState.WaitPhoneNumber, manager.snapshot.value.authState)
    assertEquals(TelegramClientErrorCodes.REQUEST_FAILED, manager.snapshot.value.lastError?.code)
    assertEquals("PHONE_NUMBER_INVALID", manager.snapshot.value.lastError?.message)
    assertEquals(
      mapOf("request" to "setAuthenticationPhoneNumber", "tdErrorCode" to 400),
      manager.snapshot.value.lastError?.context,
    )
  }

  @Test
  fun submitCodeRequiresMatchingAuthState() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))

    try {
      manager.submitCode("12345")
    } catch (error: TelegramAuthException) {
      assertEquals(TelegramAuthErrorCodes.CODE_NOT_ALLOWED, error.code)
      return
    }

    throw AssertionError("Expected TelegramAuthException")
  }

  @Test
  fun blankInputsAreRejected() {
    val manager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
    assertAuthError(TelegramAuthErrorCodes.PHONE_NUMBER_REQUIRED) {
      manager.submitPhoneNumber("   ")
    }

    manager.handleUpdateForTesting(
      TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitEmailAddress(false, false)),
    )
    assertAuthError(TelegramAuthErrorCodes.EMAIL_ADDRESS_REQUIRED) {
      manager.submitEmailAddress("   ")
    }

    manager.handleUpdateForTesting(
      TdApi.UpdateAuthorizationState(
        TdApi.AuthorizationStateWaitEmailCode(
          false,
          false,
          TdApi.EmailAddressAuthenticationCodeInfo("a***@example.com", 6),
          null,
        ),
      ),
    )
    assertAuthError(TelegramAuthErrorCodes.EMAIL_CODE_REQUIRED) {
      manager.submitEmailCode("  ")
    }

    manager.handleUpdateForTesting(
      TdApi.UpdateAuthorizationState(
        TdApi.AuthorizationStateWaitCode(
          TdApi.AuthenticationCodeInfo(
            "+1555",
            TdApi.AuthenticationCodeTypeSms(6),
            null,
            30,
          ),
        ),
      ),
    )
    assertAuthError(TelegramAuthErrorCodes.CODE_REQUIRED) {
      manager.submitCode("  ")
    }

    manager.handleUpdateForTesting(
      TdApi.UpdateAuthorizationState(
        TdApi.AuthorizationStateWaitPassword("hint", false, false, ""),
      ),
    )
    assertAuthError(TelegramAuthErrorCodes.PASSWORD_REQUIRED) {
      manager.submitPassword(" ")
    }
  }

  @Test
  fun qrRequestAndLogoutSendExpectedRequests() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
    manager.requestQrCodeAuthentication()
    assertTrue(factory.client.requests.last() is TdApi.RequestQrCodeAuthentication)

    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.logOut()
    assertTrue(factory.client.requests.last() is TdApi.LogOut)
  }

  @Test
  fun invalidChatSelectionAndMissingSelectionAreRejected() {
    val manager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))

    try {
      manager.selectChat(0L)
    } catch (error: TelegramClientException) {
      assertEquals(TelegramClientErrorCodes.INVALID_CHAT_SELECTION, error.code)
    }

    try {
      manager.loadSelectedChatMessages()
    } catch (error: TelegramClientException) {
      assertEquals(TelegramClientErrorCodes.NO_CHAT_SELECTED, error.code)
      return
    }

    throw AssertionError("Expected TelegramClientException")
  }

  @Test
  fun negativeChatIdsAreAllowedForSelection() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    factory.client.enqueueResult(TdApi.Messages(0, emptyArray()))

    manager.selectChat(-1001234567890L)

    val request = factory.client.requests.filterIsInstance<TdApi.GetChatHistory>().last()
    assertEquals(-1001234567890L, request.chatId)
    assertTrue(manager.snapshot.value.hasLoadedChatMessages)
    assertFalse(manager.snapshot.value.isChatMessagesLoading)
  }

  @Test
  fun closeClearsClientAfterClosedUpdateAndCanRestart() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
    manager.start()

    assertEquals(2, factory.createCalls)
  }

  @Test
  fun exceptionsFromTdlibCallbacksAreStored() {
    val manager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.handleExceptionForTesting(IllegalStateException("boom"))

    assertEquals(TelegramClientErrorCodes.CALLBACK_FAILED, manager.snapshot.value.lastError?.code)
    assertEquals("boom", manager.snapshot.value.lastError?.message)
  }

  @Test
  fun readyAuthorizationLoadsChatsAndPublishesSortedPreviews() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildChat(
          chatId = 100L,
          title = "Older chat",
          unreadCount = 0,
          order = 50L,
          lastMessageText = "first",
        ),
      ),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildChat(
          chatId = 200L,
          title = "Newest chat",
          unreadCount = 3,
          order = 100L,
          lastMessageText = "hello world",
        ),
      ),
    )

    assertTrue(factory.client.requests.first() is TdApi.LoadChats)
    assertEquals(TelegramAuthState.Ready, manager.snapshot.value.authState)
    assertEquals(2, manager.snapshot.value.chatPreviews.size)
    assertEquals(200L, manager.snapshot.value.chatPreviews.first().chatId)
    assertEquals("hello world", manager.snapshot.value.chatPreviews.first().lastMessagePreview)
    assertTrue(manager.snapshot.value.hasLoadedChatList)
    assertFalse(manager.snapshot.value.isChatListLoading)
  }

  @Test
  fun chatUpdatesRefreshUnreadCountPreviewAndOrder() {
    val manager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildChat(
          chatId = 1L,
          title = "Alpha",
          unreadCount = 1,
          order = 10L,
          lastMessageText = "alpha",
        ),
      ),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateChatReadInbox(
        1L,
        90L,
        0,
      ),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateChatIsMarkedAsUnread(
        1L,
        true,
      ),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateChatLastMessage(
        1L,
        buildTextMessage(chatId = 1L, messageId = 11L, text = "latest update"),
        arrayOf(mainPosition(order = 500L)),
      ),
    )

    val preview = manager.snapshot.value.chatPreviews.single()
    assertEquals(0, preview.unreadCount)
    assertTrue(preview.isMarkedAsUnread)
    assertEquals("latest update", preview.lastMessagePreview)
    assertEquals(500L, preview.order)
  }

  @Test
  fun loadChats404MarksAllChatsLoadedWithoutError() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    factory.client.enqueueResult(TdApi.Error(404, "Not Found"))

    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))

    assertTrue(manager.snapshot.value.hasLoadedAllChats)
    assertTrue(manager.snapshot.value.hasLoadedChatList)
    assertEquals(null, manager.snapshot.value.lastError)
    assertFalse(manager.snapshot.value.isChatListLoading)
  }

  @Test
  fun qrFallbackUsesSetAuthenticationPhoneNumberFromQrState() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(
      TdApi.UpdateAuthorizationState(
        TdApi.AuthorizationStateWaitOtherDeviceConfirmation("tg://login"),
      ),
    )

    manager.returnToPhoneNumberAuthentication()

    val request = factory.client.requests.single() as TdApi.SetAuthenticationPhoneNumber
    assertEquals("", request.phoneNumber)
  }

  @Test
  fun selectingChatLoadsUnreadMessagesAndResolvesAuthorData() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 10L,
          title = "Alice Chat",
          unreadCount = 3,
          order = 200L,
          lastReadInboxMessageId = 100L,
          userId = 7L,
        ),
      ),
    )
    factory.client.enqueueResult(
      TdApi.Messages(
        3,
        arrayOf(
          buildTextMessage(chatId = 10L, messageId = 101L, text = "hello", senderUserId = 7L),
          buildPhotoMessage(chatId = 10L, messageId = 102L, caption = "photo caption", senderUserId = 7L),
          buildStickerMessage(chatId = 10L, messageId = 103L, senderUserId = 7L),
        ),
      ),
    )
    factory.client.enqueueResult(
      TdApi.User().apply {
        id = 7L
        firstName = "Alice"
        lastName = "Reader"
      },
    )

    manager.selectChat(10L)

    val snapshot = manager.snapshot.value
    assertEquals(10L, snapshot.selectedChatPreview?.chatId)
    assertEquals(1, factory.client.requests.filterIsInstance<TdApi.GetChatHistory>().size)
    assertEquals(listOf(101L, 102L), snapshot.selectedChatMessages.map { it.messageId })
    assertEquals("Alice Reader", snapshot.selectedChatMessages.first().authorLabel)
    assertEquals(TelegramMessageContentKind.TEXT, snapshot.selectedChatMessages.first().contentKind)
    assertEquals(TelegramMessageContentKind.CAPTION, snapshot.selectedChatMessages.last().contentKind)
    assertTrue(snapshot.hasLoadedChatMessages)
    assertFalse(snapshot.isChatMessagesLoading)
  }

  @Test
  fun selectChatRequiresReadyAuthorization() {
    val manager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))

    try {
      manager.selectChat(99L)
    } catch (error: TelegramAuthException) {
      assertEquals(TelegramAuthErrorCodes.CHAT_SELECTION_NOT_ALLOWED, error.code)
      return
    }

    throw AssertionError("Expected TelegramAuthException")
  }

  @Test
  fun selectingChatStoresMessageLoadErrorsSeparatelyFromChatList() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 88L,
          title = "Chat",
          unreadCount = 1,
          order = 5L,
          lastReadInboxMessageId = 0L,
          userId = 9L,
        ),
      ),
    )
    factory.client.enqueueResult(TdApi.Error(500, "HISTORY_FAILED"))

    manager.selectChat(88L)

    assertEquals("HISTORY_FAILED", manager.snapshot.value.chatMessagesError?.message)
    assertNull(manager.snapshot.value.chatListError)
    assertTrue(manager.snapshot.value.hasLoadedChatMessages)
    assertEquals("HISTORY_FAILED", manager.snapshot.value.lastError?.message)
  }

  @Test
  fun newMessageClearsExistingMessageError() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 77L,
          title = "Chat",
          unreadCount = 1,
          order = 4L,
          lastReadInboxMessageId = 0L,
          userId = 5L,
        ),
      ),
    )
    factory.client.enqueueResult(TdApi.Error(500, "HISTORY_FAILED"))

    manager.selectChat(77L)
    manager.handleUpdateForTesting(
      TdApi.UpdateNewMessage(
        buildTextMessage(chatId = 77L, messageId = 1L, text = "recovered", senderUserId = 5L),
      ),
    )

    assertNull(manager.snapshot.value.chatMessagesError)
    assertEquals(listOf(1L), manager.snapshot.value.selectedChatMessages.map { it.messageId })
  }

  @Test
  fun selectedChatUpdatesReactToNewMessageContentAndDeleteEvents() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 55L,
          title = "Thread",
          unreadCount = 2,
          order = 7L,
          lastReadInboxMessageId = 10L,
          userId = 2L,
        ),
      ),
    )
    factory.client.enqueueResult(
      TdApi.Messages(
        1,
        arrayOf(buildTextMessage(chatId = 55L, messageId = 11L, text = "initial", senderUserId = 2L)),
      ),
    )
    factory.client.enqueueResult(
      TdApi.User().apply {
        id = 2L
        firstName = "Bob"
      },
    )

    manager.selectChat(55L)
    manager.handleUpdateForTesting(
      TdApi.UpdateMessageContent(
        55L,
        11L,
        TdApi.MessageText(TdApi.FormattedText("edited text", emptyArray()), null, null),
      ),
    )
    assertEquals("edited text", manager.snapshot.value.selectedChatMessages.single().text)

    manager.handleUpdateForTesting(
      TdApi.UpdateNewMessage(
        buildTextMessage(chatId = 55L, messageId = 12L, text = "new item", senderUserId = 2L),
      ),
    )
    assertEquals(listOf(11L, 12L), manager.snapshot.value.selectedChatMessages.map { it.messageId })

    manager.handleUpdateForTesting(TdApi.UpdateDeleteMessages(55L, longArrayOf(11L), true, false))
    assertEquals(listOf(12L), manager.snapshot.value.selectedChatMessages.map { it.messageId })
  }

  @Test
  fun clearSelectedChatResetsMessageState() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 1L,
          title = "Chat",
          unreadCount = 1,
          order = 10L,
          lastReadInboxMessageId = 0L,
          userId = 4L,
        ),
      ),
    )
    factory.client.enqueueResult(
      TdApi.Messages(
        1,
        arrayOf(buildTextMessage(chatId = 1L, messageId = 1L, text = "hello", senderUserId = 4L)),
      ),
    )
    factory.client.enqueueResult(TdApi.User().apply { id = 4L; firstName = "Sam" })

    manager.selectChat(1L)
    manager.clearSelectedChat()

    assertNull(manager.snapshot.value.selectedChatPreview)
    assertTrue(manager.snapshot.value.selectedChatMessages.isEmpty())
    assertFalse(manager.snapshot.value.hasLoadedChatMessages)
  }

  @Test
  fun loadChatListShortCircuitsWhenBusyOrComplete() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    val initialCount = factory.client.requests.filterIsInstance<TdApi.LoadChats>().size

    factory.client.blockNextResult = true
    manager.loadChatList()
    assertEquals(initialCount + 1, factory.client.requests.filterIsInstance<TdApi.LoadChats>().size)
    manager.loadChatList()
    assertEquals(initialCount + 1, factory.client.requests.filterIsInstance<TdApi.LoadChats>().size)
    factory.client.blockNextResult = false
    factory.client.dispatchPendingResult(TdApi.Ok())

    factory.client.enqueueResult(TdApi.Error(404, "Not Found"))
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    val afterComplete = factory.client.requests.filterIsInstance<TdApi.LoadChats>().size
    manager.loadChatList()
    assertEquals(afterComplete, factory.client.requests.filterIsInstance<TdApi.LoadChats>().size)
  }

  @Test
  fun messageLoadUsesLimitClamp() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 500L,
          title = "Clamp",
          unreadCount = 1,
          order = 1L,
          lastReadInboxMessageId = 0L,
          userId = 1L,
        ),
      ),
    )
    factory.client.enqueueResult(TdApi.Messages(0, emptyArray()))

    manager.selectChat(500L, limit = 500)

    val request = factory.client.requests.filterIsInstance<TdApi.GetChatHistory>().last()
    assertEquals(100, request.limit)
  }

  @Test
  fun parameterFactoryFailureIsSurfacedOnSnapshot() {
    val manager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory {
        throw TelegramClientException(
          message = "bad config",
          code = TelegramClientErrorCodes.PARAMETERS_CREATE_FAILED,
        )
      },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))

    assertEquals(TelegramClientErrorCodes.PARAMETERS_CREATE_FAILED, manager.snapshot.value.lastError?.code)
  }

  @Test
  fun unexpectedResponsesAreStoredForCloseAndMessageLoad() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    factory.client.enqueueResult(TdApi.Messages(0, emptyArray()))
    factory.client.enqueueResult(TdApi.AuthenticationCodeInfo("", null, null, 0))
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 8L,
          title = "Chat",
          unreadCount = 1,
          order = 3L,
          lastReadInboxMessageId = 0L,
          userId = 1L,
        ),
      ),
    )

    manager.selectChat(8L)
    assertEquals(TelegramClientErrorCodes.UNEXPECTED_RESPONSE, manager.snapshot.value.chatMessagesError?.code)

    factory.client.enqueueResult(TdApi.AuthenticationCodeInfo("", null, null, 0))
    manager.close()
    assertEquals(TelegramClientErrorCodes.UNEXPECTED_RESPONSE, manager.snapshot.value.lastError?.code)
  }

  @Test
  fun updateVariantsRefreshChatPreviewState() {
    val manager = TelegramClientManager(
      clientFactory = FakeTelegramTdlibClientFactory(),
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 3L,
          title = "Seed",
          unreadCount = 0,
          order = 1L,
          lastReadInboxMessageId = 0L,
          userId = 1L,
        ),
      ),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateChatTitle(3L, "Renamed"),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateChatAddedToList(3L, TdApi.ChatListMain()),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateChatPosition(
        3L,
        TdApi.ChatPosition(TdApi.ChatListMain(), 44L, false, null),
      ),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateChatReadOutbox(3L, 9L),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateChatDraftMessage(3L, null, arrayOf(mainPosition(44L))),
    )

    assertEquals("Renamed", manager.snapshot.value.chatPreviews.single().title)

    manager.handleUpdateForTesting(
      TdApi.UpdateChatRemovedFromList(3L, TdApi.ChatListMain()),
    )
    assertTrue(manager.snapshot.value.chatPreviews.isEmpty())
  }

  @Test
  fun sendSucceededAndEditedUpdatesRefreshSelectedMessages() {
    val factory = FakeTelegramTdlibClientFactory()
    val manager = TelegramClientManager(
      clientFactory = factory,
      parametersFactory = TelegramTdlibParametersFactory { TdApi.SetTdlibParameters() },
    )

    manager.start()
    manager.handleUpdateForTesting(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
    manager.handleUpdateForTesting(
      TdApi.UpdateNewChat(
        buildPrivateChat(
          chatId = 20L,
          title = "Edits",
          unreadCount = 1,
          order = 4L,
          lastReadInboxMessageId = 0L,
          userId = 7L,
        ),
      ),
    )
    factory.client.enqueueResult(
      TdApi.Messages(
        1,
        arrayOf(buildTextMessage(chatId = 20L, messageId = 1L, text = "initial", senderUserId = 7L)),
      ),
    )
    factory.client.enqueueResult(TdApi.User().apply { id = 7L; firstName = "Ana" })
    manager.selectChat(20L)

    manager.handleUpdateForTesting(
      TdApi.UpdateMessageEdited(20L, 1L, 123, null),
    )
    manager.handleUpdateForTesting(
      TdApi.UpdateMessageSendSucceeded(
        buildTextMessage(chatId = 20L, messageId = 2L, text = "sent", senderUserId = 7L),
        1L,
      ),
    )

    assertEquals(listOf(2L), manager.snapshot.value.selectedChatMessages.map { it.messageId })
  }

  private class FakeTelegramTdlibClientFactory : TelegramTdlibClientFactory {
    val client = FakeTelegramTdlibClient()
    var createCalls: Int = 0

    override fun create(
      updateHandler: (TdApi.Object) -> Unit,
      exceptionHandler: (Throwable) -> Unit,
    ): TelegramTdlibClient {
      createCalls += 1
      client.updateHandler = updateHandler
      client.exceptionHandler = exceptionHandler
      return client
    }
  }

  private class FakeTelegramTdlibClient : TelegramTdlibClient {
    val requests = mutableListOf<TdApi.Function<*>>()
    val queuedResults = ArrayDeque<TdApi.Object>()
    var updateHandler: ((TdApi.Object) -> Unit)? = null
    var exceptionHandler: ((Throwable) -> Unit)? = null
    var blockNextResult: Boolean = false
    private var pendingResultHandler: ((TdApi.Object) -> Unit)? = null

    override fun send(request: TdApi.Function<*>, resultHandler: (TdApi.Object) -> Unit) {
      requests += request
      if (blockNextResult) {
        blockNextResult = false
        pendingResultHandler = resultHandler
      } else {
        resultHandler(queuedResults.removeFirstOrNull() ?: TdApi.Ok())
      }
    }

    fun enqueueResult(result: TdApi.Object) {
      queuedResults.addLast(result)
    }

    fun dispatchPendingResult(result: TdApi.Object) {
      val handler = pendingResultHandler ?: throw AssertionError("No pending result handler")
      pendingResultHandler = null
      handler(result)
    }
  }

  private fun buildChat(
    chatId: Long,
    title: String,
    unreadCount: Int,
    order: Long,
    lastMessageText: String,
  ): TdApi.Chat {
    return TdApi.Chat().apply {
      id = chatId
      this.title = title
      this.unreadCount = unreadCount
      isMarkedAsUnread = unreadCount > 0
      positions = arrayOf(mainPosition(order))
      chatLists = arrayOf(TdApi.ChatListMain())
      lastMessage = buildTextMessage(chatId, chatId + 1L, lastMessageText)
    }
  }

  private fun buildPrivateChat(
    chatId: Long,
    title: String,
    unreadCount: Int,
    order: Long,
    lastReadInboxMessageId: Long,
    userId: Long,
  ): TdApi.Chat {
    return TdApi.Chat().apply {
      id = chatId
      this.title = title
      this.unreadCount = unreadCount
      this.lastReadInboxMessageId = lastReadInboxMessageId
      type = TdApi.ChatTypePrivate(userId)
      positions = arrayOf(mainPosition(order))
      chatLists = arrayOf(TdApi.ChatListMain())
      isMarkedAsUnread = unreadCount > 0
    }
  }

  private fun mainPosition(order: Long): TdApi.ChatPosition {
    return TdApi.ChatPosition(
      TdApi.ChatListMain(),
      order,
      false,
      null,
    )
  }

  private fun buildTextMessage(
    chatId: Long,
    messageId: Long,
    text: String,
    senderUserId: Long = 1L,
  ): TdApi.Message {
    return TdApi.Message().apply {
      id = messageId
      this.chatId = chatId
      senderId = TdApi.MessageSenderUser(senderUserId)
      content = TdApi.MessageText(TdApi.FormattedText(text, emptyArray()), null, null)
    }
  }

  private fun buildPhotoMessage(
    chatId: Long,
    messageId: Long,
    caption: String,
    senderUserId: Long,
  ): TdApi.Message {
    return TdApi.Message().apply {
      id = messageId
      this.chatId = chatId
      senderId = TdApi.MessageSenderUser(senderUserId)
      content = TdApi.MessagePhoto(
        null,
        TdApi.FormattedText(caption, emptyArray()),
        false,
        false,
        false,
      )
    }
  }

  private fun buildStickerMessage(
    chatId: Long,
    messageId: Long,
    senderUserId: Long,
  ): TdApi.Message {
    return TdApi.Message().apply {
      id = messageId
      this.chatId = chatId
      senderId = TdApi.MessageSenderUser(senderUserId)
      content = TdApi.MessageSticker(null, false)
    }
  }

  private fun assertAuthError(expectedCode: String, block: () -> Unit) {
    try {
      block()
    } catch (error: TelegramAuthException) {
      assertEquals(expectedCode, error.code)
      return
    }

    throw AssertionError("Expected TelegramAuthException")
  }
}
