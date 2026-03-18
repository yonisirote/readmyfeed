package com.yonisirote.readmyfeed.providers.telegram.client

import android.content.Context
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthErrorCodes
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthException
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.providers.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.providers.telegram.chats.buildTelegramSelectedChatPreview
import com.yonisirote.readmyfeed.providers.telegram.auth.mapTelegramAuthorizationState
import com.yonisirote.readmyfeed.providers.telegram.chats.buildSortedTelegramChatPreviews
import com.yonisirote.readmyfeed.providers.telegram.messages.TelegramMessageItem
import com.yonisirote.readmyfeed.providers.telegram.messages.buildUnreadTelegramMessageItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

class TelegramClientManager(
  private val clientFactory: TelegramTdlibClientFactory,
  private val parametersFactory: TelegramTdlibParametersFactory,
) {
  private val lock = Any()
  private val snapshotState = MutableStateFlow(TelegramClientSnapshot())
  private val chatsById = mutableMapOf<Long, TdApi.Chat>()
  private val usersById = mutableMapOf<Long, TdApi.User>()
  private val selectedChatMessagesById = mutableMapOf<Long, TdApi.Message>()

  private var client: TelegramTdlibClient? = null
  private var selectedChatId: Long? = null

  val snapshot: StateFlow<TelegramClientSnapshot> = snapshotState.asStateFlow()

  fun start(): Unit {
    synchronized(lock) {
      if (client != null) {
        return
      }

      clearCachedStateLocked()
      snapshotState.value = TelegramClientSnapshot()

      client = try {
        clientFactory.create(
          updateHandler = ::handleTdlibObject,
          exceptionHandler = ::handleTdlibException,
        )
      } catch (error: Throwable) {
        throw TelegramClientException(
          message = "Failed to create the TDLib client.",
          code = TelegramClientErrorCodes.CLIENT_CREATE_FAILED,
          context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
          cause = error,
        )
      }
    }
  }

  fun loadChatList(limit: Int = DEFAULT_CHAT_LOAD_LIMIT): Unit {
    if (snapshot.value.authState !is TelegramAuthState.Ready) {
      return
    }

    val activeClient = ensureClientStarted()
    val currentSnapshot = snapshot.value
    if (currentSnapshot.isChatListLoading || currentSnapshot.hasLoadedAllChats) {
      return
    }

    snapshotState.value = currentSnapshot.copy(
      isChatListLoading = true,
      chatListError = null,
      lastError = null,
    )

    activeClient.send(TdApi.LoadChats(TdApi.ChatListMain(), limit)) { result ->
      handleLoadChatsResult(result)
    }
  }

  fun selectChat(chatId: Long, limit: Int = DEFAULT_CHAT_MESSAGES_LIMIT): Unit {
    if (chatId == 0L) {
      throw TelegramClientException(
        message = "Telegram chat selection requires a valid chat id.",
        code = TelegramClientErrorCodes.INVALID_CHAT_SELECTION,
      )
    }

    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.Ready },
      errorCode = TelegramAuthErrorCodes.CHAT_SELECTION_NOT_ALLOWED,
      message = "Telegram is not ready to select a chat.",
    )

    val selectedPreview = snapshot.value.chatPreviews.firstOrNull { preview -> preview.chatId == chatId }
    synchronized(lock) {
      selectedChatId = chatId
      clearChatSelectionLocked()
      selectedChatId = chatId
    }

    snapshotState.value = snapshot.value.copy(
      selectedChatPreview = resolveSelectedChatPreview(chatId, selectedPreview),
      selectedChatMessages = emptyList(),
      chatMessagesError = null,
      isChatMessagesLoading = false,
      hasLoadedChatMessages = false,
      chatListError = snapshot.value.chatListError,
      lastError = null,
    )

    loadSelectedChatMessages(limit)
  }

  fun loadSelectedChatMessages(limit: Int = DEFAULT_CHAT_MESSAGES_LIMIT): Unit {
    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.Ready },
      errorCode = TelegramAuthErrorCodes.CHAT_SELECTION_NOT_ALLOWED,
      message = "Telegram is not ready to load chat messages.",
    )

    val chatId = synchronized(lock) { selectedChatId }
      ?: throw TelegramClientException(
        message = "Select a Telegram chat before loading messages.",
        code = TelegramClientErrorCodes.NO_CHAT_SELECTED,
      )

    val activeClient = ensureClientStarted()
    val currentSnapshot = snapshot.value
    if (currentSnapshot.isChatMessagesLoading) {
      return
    }

    snapshotState.value = currentSnapshot.copy(
      selectedChatPreview = resolveSelectedChatPreview(chatId, currentSnapshot.selectedChatPreview),
      chatMessagesError = null,
      isChatMessagesLoading = true,
      chatListError = currentSnapshot.chatListError,
      lastError = null,
    )

    activeClient.send(
      TdApi.GetChatHistory(chatId, 0L, 0, limit.coerceIn(1, MAX_CHAT_MESSAGES_LIMIT), false),
    ) { result ->
      handleLoadSelectedChatMessagesResult(chatId, result)
    }
  }

  fun clearSelectedChat(): Unit {
    synchronized(lock) {
      clearChatSelectionLocked()
    }

    snapshotState.value = snapshot.value.copy(
      selectedChatPreview = null,
      selectedChatMessages = emptyList(),
      chatMessagesError = null,
      isChatMessagesLoading = false,
      hasLoadedChatMessages = false,
      chatListError = snapshot.value.chatListError,
      lastError = null,
    )
  }

  fun submitPhoneNumber(phoneNumber: String): Unit {
    val sanitizedPhoneNumber = phoneNumber.trim()
    if (sanitizedPhoneNumber.isBlank()) {
      throw TelegramAuthException(
        message = "Telegram phone number is required.",
        code = TelegramAuthErrorCodes.PHONE_NUMBER_REQUIRED,
      )
    }

    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.WaitPhoneNumber },
      errorCode = TelegramAuthErrorCodes.PHONE_NUMBER_NOT_ALLOWED,
      message = "Telegram is not waiting for a phone number.",
    )
    sendRequest(
      request = TdApi.SetAuthenticationPhoneNumber(sanitizedPhoneNumber, null),
      requestName = "setAuthenticationPhoneNumber",
    )
  }

  fun submitEmailAddress(emailAddress: String): Unit {
    val sanitizedEmailAddress = emailAddress.trim()
    if (sanitizedEmailAddress.isBlank()) {
      throw TelegramAuthException(
        message = "Telegram email address is required.",
        code = TelegramAuthErrorCodes.EMAIL_ADDRESS_REQUIRED,
      )
    }

    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.WaitEmailAddress },
      errorCode = TelegramAuthErrorCodes.EMAIL_ADDRESS_NOT_ALLOWED,
      message = "Telegram is not waiting for an email address.",
    )
    sendRequest(
      request = TdApi.SetAuthenticationEmailAddress(sanitizedEmailAddress),
      requestName = "setAuthenticationEmailAddress",
    )
  }

  fun submitEmailCode(code: String): Unit {
    val sanitizedCode = code.trim()
    if (sanitizedCode.isBlank()) {
      throw TelegramAuthException(
        message = "Telegram email code is required.",
        code = TelegramAuthErrorCodes.EMAIL_CODE_REQUIRED,
      )
    }

    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.WaitEmailCode },
      errorCode = TelegramAuthErrorCodes.EMAIL_CODE_NOT_ALLOWED,
      message = "Telegram is not waiting for an email code.",
    )
    sendRequest(
      request = TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(sanitizedCode)),
      requestName = "checkAuthenticationEmailCode",
    )
  }

  fun submitCode(code: String): Unit {
    val sanitizedCode = code.trim()
    if (sanitizedCode.isBlank()) {
      throw TelegramAuthException(
        message = "Telegram authentication code is required.",
        code = TelegramAuthErrorCodes.CODE_REQUIRED,
      )
    }

    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.WaitCode },
      errorCode = TelegramAuthErrorCodes.CODE_NOT_ALLOWED,
      message = "Telegram is not waiting for an authentication code.",
    )
    sendRequest(
      request = TdApi.CheckAuthenticationCode(sanitizedCode),
      requestName = "checkAuthenticationCode",
    )
  }

  fun submitPassword(password: String): Unit {
    if (password.isBlank()) {
      throw TelegramAuthException(
        message = "Telegram password is required.",
        code = TelegramAuthErrorCodes.PASSWORD_REQUIRED,
      )
    }

    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.WaitPassword },
      errorCode = TelegramAuthErrorCodes.PASSWORD_NOT_ALLOWED,
      message = "Telegram is not waiting for a password.",
    )
    sendRequest(
      request = TdApi.CheckAuthenticationPassword(password),
      requestName = "checkAuthenticationPassword",
    )
  }

  fun requestQrCodeAuthentication(): Unit {
    requireAuthState(
      predicate = ::canRequestQrCodeAuthentication,
      errorCode = TelegramAuthErrorCodes.QR_AUTH_NOT_ALLOWED,
      message = "Telegram QR authentication is not available right now.",
    )
    sendRequest(
      request = TdApi.RequestQrCodeAuthentication(longArrayOf()),
      requestName = "requestQrCodeAuthentication",
    )
  }

  fun returnToPhoneNumberAuthentication(): Unit {
    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.WaitQrConfirmation },
      errorCode = TelegramAuthErrorCodes.PHONE_NUMBER_NOT_ALLOWED,
      message = "Telegram is not waiting for QR confirmation.",
    )
    sendRequest(
      request = TdApi.SetAuthenticationPhoneNumber("", null),
      requestName = "setAuthenticationPhoneNumber",
    )
  }

  fun logOut(): Unit {
    requireAuthState(
      predicate = { authState -> authState is TelegramAuthState.Ready },
      errorCode = TelegramAuthErrorCodes.LOG_OUT_NOT_ALLOWED,
      message = "Telegram is not ready to log out.",
    )
    sendRequest(
      request = TdApi.LogOut(),
      requestName = "logOut",
    )
  }

  fun close(): Unit {
    val activeClient = synchronized(lock) { client } ?: return
    activeClient.send(TdApi.Close()) { result ->
      handleRequestResult(
        requestName = "close",
        result = result,
      )
    }
  }

  internal fun handleUpdateForTesting(result: TdApi.Object): Unit {
    handleTdlibObject(result)
  }

  internal fun handleExceptionForTesting(error: Throwable): Unit {
    handleTdlibException(error)
  }

  private fun requireAuthState(
    predicate: (TelegramAuthState) -> Boolean,
    errorCode: String,
    message: String,
  ): Unit {
    ensureClientStarted()

    val authState = snapshot.value.authState
    if (predicate(authState)) {
      return
    }

    throw TelegramAuthException(
      message = message,
      code = errorCode,
      context = mapOf("authState" to authState::class.simpleName),
    )
  }

  private fun ensureClientStarted(): TelegramTdlibClient {
    val activeClient = synchronized(lock) { client }
    if (activeClient != null) {
      return activeClient
    }

    throw TelegramAuthException(
      message = "Telegram TDLib client has not been started.",
      code = TelegramAuthErrorCodes.CLIENT_NOT_STARTED,
    )
  }

  private fun sendRequest(
    request: TdApi.Function<*>,
    requestName: String,
  ): Unit {
    val activeClient = ensureClientStarted()
    activeClient.send(request) { result ->
      handleRequestResult(
        requestName = requestName,
        result = result,
      )
    }
  }

  private fun handleTdlibObject(result: TdApi.Object): Unit {
    when (result) {
      is TdApi.UpdateAuthorizationState -> handleAuthorizationState(result.authorizationState)
      is TdApi.UpdateUser -> upsertUser(result.user)
      is TdApi.UpdateNewChat -> upsertChat(result.chat)
      is TdApi.UpdateChatTitle -> updateChat(result.chatId) { chat ->
        chat.title = result.title.orEmpty()
      }
      is TdApi.UpdateChatLastMessage -> updateChat(result.chatId) { chat ->
        chat.lastMessage = result.lastMessage
        chat.positions = result.positions.orEmpty()
      }
      is TdApi.UpdateChatPosition -> updateChat(result.chatId) { chat ->
        chat.positions = mergeChatPositions(chat.positions, result.position)
      }
      is TdApi.UpdateChatAddedToList -> updateChat(result.chatId) { chat ->
        chat.chatLists = mergeChatLists(
          chatLists = chat.chatLists,
          chatList = result.chatList,
          shouldAdd = true,
        )
      }
      is TdApi.UpdateChatRemovedFromList -> updateChat(result.chatId) { chat ->
        chat.chatLists = mergeChatLists(
          chatLists = chat.chatLists,
          chatList = result.chatList,
          shouldAdd = false,
        )
        chat.positions = removeChatPositions(
          chat.positions,
          result.chatList,
        )
      }
      is TdApi.UpdateChatReadInbox -> updateChat(result.chatId) { chat ->
        chat.lastReadInboxMessageId = result.lastReadInboxMessageId
        chat.unreadCount = result.unreadCount
      }
      is TdApi.UpdateChatReadOutbox -> updateChat(result.chatId) { chat ->
        chat.lastReadOutboxMessageId = result.lastReadOutboxMessageId
      }
      is TdApi.UpdateChatIsMarkedAsUnread -> updateChat(result.chatId) { chat ->
        chat.isMarkedAsUnread = result.isMarkedAsUnread
      }
      is TdApi.UpdateChatDraftMessage -> updateChat(result.chatId) { chat ->
        chat.draftMessage = result.draftMessage
        chat.positions = result.positions.orEmpty()
      }
      is TdApi.UpdateNewMessage -> upsertSelectedChatMessage(result.message)
      is TdApi.UpdateMessageSendSucceeded -> replaceSelectedChatMessage(
        oldMessageId = result.oldMessageId,
        message = result.message,
      )
      is TdApi.UpdateMessageContent -> updateSelectedChatMessage(result.chatId, result.messageId) { message ->
        message.content = result.newContent
      }
      is TdApi.UpdateMessageEdited -> updateSelectedChatMessage(result.chatId, result.messageId) { message ->
        message.editDate = result.editDate
        message.replyMarkup = result.replyMarkup
      }
      is TdApi.UpdateDeleteMessages -> removeSelectedChatMessages(
        chatId = result.chatId,
        messageIds = result.messageIds,
      )
    }
  }

  private fun handleAuthorizationState(state: TdApi.AuthorizationState?): Unit {
    val mappedState = mapTelegramAuthorizationState(state)
    if (mappedState !is TelegramAuthState.Ready) {
      clearCachedState()
      snapshotState.value = snapshot.value.copy(
        authState = mappedState,
        lastError = null,
        chatListError = null,
        chatPreviews = emptyList(),
        isChatListLoading = false,
        hasLoadedChatList = false,
        hasLoadedAllChats = false,
        selectedChatPreview = null,
        selectedChatMessages = emptyList(),
        chatMessagesError = null,
        isChatMessagesLoading = false,
        hasLoadedChatMessages = false,
      )
    } else {
      snapshotState.value = snapshot.value.copy(
        authState = mappedState,
        lastError = null,
        chatListError = null,
      )
    }

    if (state is TdApi.AuthorizationStateWaitTdlibParameters) {
      val parameters = try {
        parametersFactory.create()
      } catch (error: TelegramClientException) {
        snapshotState.value = snapshot.value.copy(
          lastError = TelegramClientError(
            code = error.code,
            message = error.message ?: "Failed to create TDLib parameters.",
            context = error.context,
          ),
        )
        return
      }

      sendRequest(
        request = parameters,
        requestName = "setTdlibParameters",
      )
    }

    if (mappedState is TelegramAuthState.Ready) {
      loadChatList()
    }

    if (mappedState is TelegramAuthState.Closed) {
      synchronized(lock) {
        client = null
      }
    }
  }

  private fun handleLoadChatsResult(result: TdApi.Object): Unit {
    when (result) {
      is TdApi.Ok -> snapshotState.value = snapshot.value.copy(
        isChatListLoading = false,
        hasLoadedChatList = true,
        chatListError = null,
        lastError = null,
      )
      is TdApi.Error -> {
        if (result.code == TELEGRAM_CHAT_LIST_COMPLETE_ERROR_CODE) {
          snapshotState.value = snapshot.value.copy(
            isChatListLoading = false,
            hasLoadedChatList = true,
            hasLoadedAllChats = true,
            chatListError = null,
            lastError = null,
          )
        } else {
          val error = TelegramClientError(
            code = TelegramClientErrorCodes.REQUEST_FAILED,
            message = result.message.orEmpty(),
            context = mapOf(
              "request" to "loadChats",
              "tdErrorCode" to result.code,
            ),
          )
          snapshotState.value = snapshot.value.copy(
            isChatListLoading = false,
            hasLoadedChatList = true,
            chatListError = error,
            lastError = error,
          )
        }
      }
      else -> {
        val error = TelegramClientError(
          code = TelegramClientErrorCodes.UNEXPECTED_RESPONSE,
          message = "TDLib returned an unexpected response for loadChats.",
          context = mapOf(
            "request" to "loadChats",
            "responseType" to result::class.java.simpleName,
          ),
        )
        snapshotState.value = snapshot.value.copy(
          isChatListLoading = false,
          hasLoadedChatList = true,
          chatListError = error,
          lastError = error,
        )
      }
    }
  }

  private fun handleRequestResult(
    requestName: String,
    result: TdApi.Object,
  ): Unit {
    when (result) {
      is TdApi.Ok -> snapshotState.value = snapshot.value.copy(lastError = null)
      is TdApi.Error -> snapshotState.value = snapshot.value.copy(
        lastError = TelegramClientError(
          code = TelegramClientErrorCodes.REQUEST_FAILED,
          message = result.message.orEmpty(),
          context = mapOf(
            "request" to requestName,
            "tdErrorCode" to result.code,
          ),
        ),
      )
      else -> snapshotState.value = snapshot.value.copy(
        lastError = TelegramClientError(
          code = TelegramClientErrorCodes.UNEXPECTED_RESPONSE,
          message = "TDLib returned an unexpected response for $requestName.",
          context = mapOf(
            "request" to requestName,
            "responseType" to result::class.java.simpleName,
          ),
        ),
      )
    }
  }

  private fun handleLoadSelectedChatMessagesResult(
    chatId: Long,
    result: TdApi.Object,
  ): Unit {
    if (!isSelectedChat(chatId)) {
      return
    }

    when (result) {
      is TdApi.Messages -> {
        val isStillSelected = synchronized(lock) {
          if (selectedChatId != chatId) {
            return@synchronized false
          }
          selectedChatMessagesById.clear()
          result.messages.orEmpty().forEach { message ->
            if (message != null) {
              selectedChatMessagesById[message.id] = message
            }
          }
          true
        }
        if (!isStillSelected) {
          return
        }
        requestMissingSenderDetails(result.messages.orEmpty().filterNotNull())
        publishSelectedChatMessages(
          chatId = chatId,
          error = null,
          isLoading = false,
          hasLoaded = true,
        )
      }
      is TdApi.Error -> publishSelectedChatMessages(
        chatId = chatId,
        error = TelegramClientError(
          code = TelegramClientErrorCodes.REQUEST_FAILED,
          message = result.message.orEmpty(),
          context = mapOf(
            "request" to "getChatHistory",
            "tdErrorCode" to result.code,
            "chatId" to chatId,
          ),
        ),
        isLoading = false,
        hasLoaded = true,
      )
      else -> publishSelectedChatMessages(
        chatId = chatId,
        error = TelegramClientError(
          code = TelegramClientErrorCodes.UNEXPECTED_RESPONSE,
          message = "TDLib returned an unexpected response for getChatHistory.",
          context = mapOf(
            "request" to "getChatHistory",
            "responseType" to result::class.java.simpleName,
            "chatId" to chatId,
          ),
        ),
        isLoading = false,
        hasLoaded = true,
      )
    }
  }

  private fun handleTdlibException(error: Throwable): Unit {
    val currentSnapshot = snapshot.value
    val mappedError = TelegramClientError(
      code = TelegramClientErrorCodes.CALLBACK_FAILED,
      message = error.message ?: error::class.java.simpleName,
      context = mapOf("exceptionType" to error::class.java.name),
    )

    snapshotState.value = currentSnapshot.copy(
      lastError = mappedError,
      chatListError = if (currentSnapshot.isChatListLoading) mappedError else currentSnapshot.chatListError,
      chatMessagesError = if (currentSnapshot.isChatMessagesLoading) mappedError else currentSnapshot.chatMessagesError,
    )
  }

  private fun canRequestQrCodeAuthentication(authState: TelegramAuthState): Boolean {
    return authState is TelegramAuthState.WaitPhoneNumber ||
      authState is TelegramAuthState.WaitPremiumPurchase ||
      authState is TelegramAuthState.WaitEmailAddress ||
      authState is TelegramAuthState.WaitEmailCode ||
      authState is TelegramAuthState.WaitCode ||
      authState is TelegramAuthState.WaitRegistration ||
      authState is TelegramAuthState.WaitPassword
  }

  private fun upsertUser(user: TdApi.User?): Unit {
    if (user == null) {
      return
    }

    synchronized(lock) {
      usersById[user.id] = user
    }
    publishSelectedChatMessages()
  }

  private fun upsertChat(chat: TdApi.Chat?): Unit {
    if (chat == null) {
      return
    }

    publishChatSnapshot { chats ->
      chats[chat.id] = chat.apply {
        positions = positions.orEmpty()
        chatLists = chatLists.orEmpty()
        title = title.orEmpty()
      }
    }
  }

  private fun updateChat(
    chatId: Long,
    block: (TdApi.Chat) -> Unit,
  ): Unit {
    publishChatSnapshot { chats ->
      val chat = chats.getOrPut(chatId) { createPlaceholderChat(chatId) }
      block(chat)
      chat.positions = chat.positions.orEmpty()
      chat.chatLists = chat.chatLists.orEmpty()
      chat.title = chat.title.orEmpty()
    }
  }

  private fun upsertSelectedChatMessage(message: TdApi.Message?): Unit {
    if (message == null) {
      return
    }

    val shouldRequestSender = synchronized(lock) {
      if (selectedChatId != message.chatId) {
        return@synchronized false
      }
      selectedChatMessagesById[message.id] = message
      clearLastMessageErrorLocked()
      true
    }

    if (shouldRequestSender) {
      requestMissingSenderDetails(listOf(message))
      publishSelectedChatMessages()
    }
  }

  private fun replaceSelectedChatMessage(
    oldMessageId: Long,
    message: TdApi.Message?,
  ): Unit {
    if (message == null) {
      return
    }

    val shouldRequestSender = synchronized(lock) {
      if (selectedChatId != message.chatId) {
        return@synchronized false
      }
      if (oldMessageId > 0L) {
        selectedChatMessagesById.remove(oldMessageId)
      }
      selectedChatMessagesById[message.id] = message
      clearLastMessageErrorLocked()
      true
    }

    if (shouldRequestSender) {
      requestMissingSenderDetails(listOf(message))
      publishSelectedChatMessages()
    }
  }

  private fun updateSelectedChatMessage(
    chatId: Long,
    messageId: Long,
    block: (TdApi.Message) -> Unit,
  ): Unit {
    val didUpdate = synchronized(lock) {
      if (selectedChatId != chatId) {
        return@synchronized false
      }
      val message = selectedChatMessagesById[messageId] ?: return@synchronized false
      block(message)
      clearLastMessageErrorLocked()
      true
    }

    if (didUpdate) {
      publishSelectedChatMessages()
    }
  }

  private fun removeSelectedChatMessages(
    chatId: Long,
    messageIds: LongArray,
  ): Unit {
    val didRemove = synchronized(lock) {
      if (selectedChatId != chatId) {
        return@synchronized false
      }
      var removed = false
      messageIds.forEach { messageId ->
        removed = selectedChatMessagesById.remove(messageId) != null || removed
      }
      if (removed) {
        clearLastMessageErrorLocked()
      }
      removed
    }

    if (didRemove) {
      publishSelectedChatMessages()
    }
  }

  private fun publishChatSnapshot(
    update: (MutableMap<Long, TdApi.Chat>) -> Unit,
  ): Unit {
    val (chatPreviews, selectedChatPreview, selectedChatMessages) = synchronized(lock) {
      update(chatsById)
      Triple(
        buildSortedTelegramChatPreviews(chatsById.values),
        resolveSelectedChatPreviewLocked(snapshot.value.selectedChatPreview),
        buildSelectedChatMessagesLocked(snapshot.value.selectedChatPreview),
      )
    }

    val currentSnapshot = snapshot.value
    snapshotState.value = currentSnapshot.copy(
      chatPreviews = chatPreviews,
      hasLoadedChatList = currentSnapshot.hasLoadedChatList || chatPreviews.isNotEmpty(),
      selectedChatPreview = selectedChatPreview,
      selectedChatMessages = selectedChatMessages,
      lastError = currentSnapshot.chatMessagesError ?: currentSnapshot.chatListError ?: currentSnapshot.lastError,
    )
  }

  private fun publishSelectedChatMessages(
    chatId: Long? = null,
    error: TelegramClientError? = snapshot.value.chatMessagesError,
    isLoading: Boolean = snapshot.value.isChatMessagesLoading,
    hasLoaded: Boolean = snapshot.value.hasLoadedChatMessages,
  ): Unit {
    val currentSnapshot = snapshot.value
    val selectedPreviewAndMessages = synchronized(lock) {
      val activeSelectedChatId = selectedChatId
      if (chatId != null && activeSelectedChatId != chatId) {
        return@synchronized null
      }
      Pair(
        resolveSelectedChatPreviewLocked(currentSnapshot.selectedChatPreview),
        buildSelectedChatMessagesLocked(currentSnapshot.selectedChatPreview),
      )
    }
    if (selectedPreviewAndMessages == null) {
      return
    }

    snapshotState.value = currentSnapshot.copy(
      selectedChatPreview = selectedPreviewAndMessages.first,
      selectedChatMessages = selectedPreviewAndMessages.second,
      chatMessagesError = error,
      isChatMessagesLoading = isLoading,
      hasLoadedChatMessages = hasLoaded,
      lastError = error ?: currentSnapshot.lastError,
    )
  }

  private fun requestMissingSenderDetails(messages: List<TdApi.Message>): Unit {
    if (messages.isEmpty()) {
      return
    }

    val activeClient = synchronized(lock) { client } ?: return
    val activeSelectedChatId = synchronized(lock) { selectedChatId }
    val missingUserIds = linkedSetOf<Long>()
    val missingChatIds = linkedSetOf<Long>()

    synchronized(lock) {
      messages.forEach { message ->
        if (activeSelectedChatId != null && message.chatId != activeSelectedChatId) {
          return@forEach
        }
        when (val sender = message.senderId) {
          is TdApi.MessageSenderUser -> if (!usersById.containsKey(sender.userId)) {
            missingUserIds += sender.userId
          }
          is TdApi.MessageSenderChat -> if (!chatsById.containsKey(sender.chatId)) {
            missingChatIds += sender.chatId
          }
        }
      }
    }

    missingUserIds.forEach { userId ->
      activeClient.send(TdApi.GetUser(userId)) { result ->
        if (result is TdApi.User) {
          upsertUser(result)
        }
      }
    }

    missingChatIds.forEach { chatId ->
      activeClient.send(TdApi.GetChat(chatId)) { result ->
        if (result is TdApi.Chat) {
          upsertChat(result)
        }
      }
    }
  }

  private fun buildSelectedChatMessagesLocked(
    fallbackPreview: TelegramChatPreview?,
  ): List<TelegramMessageItem> {
    val chatId = selectedChatId ?: return emptyList()
    return buildUnreadTelegramMessageItems(
      messages = selectedChatMessagesById.values,
      chat = chatsById[chatId],
      usersById = usersById,
      chatsById = chatsById,
      fallbackChatPreview = fallbackPreview,
    )
  }

  private fun resolveSelectedChatPreview(chatId: Long, fallback: TelegramChatPreview?): TelegramChatPreview? {
    return synchronized(lock) {
      if (selectedChatId != chatId) {
        null
      } else {
        resolveSelectedChatPreviewLocked(fallback)
      }
    }
  }

  private fun resolveSelectedChatPreviewLocked(fallback: TelegramChatPreview?): TelegramChatPreview? {
    val chatId = selectedChatId ?: return null
    val chat = chatsById[chatId] ?: return fallback?.takeIf { it.chatId == chatId }
    return buildTelegramSelectedChatPreview(chat, fallback?.takeIf { it.chatId == chatId })
  }

  private fun isSelectedChat(chatId: Long): Boolean {
    return synchronized(lock) { selectedChatId == chatId }
  }

  private fun clearCachedState(): Unit {
    synchronized(lock) {
      clearCachedStateLocked()
    }
  }

  private fun clearCachedStateLocked(): Unit {
    chatsById.clear()
    usersById.clear()
    clearChatSelectionLocked()
  }

  private fun clearChatSelectionLocked(): Unit {
    selectedChatMessagesById.clear()
    selectedChatId = null
  }

  private fun clearLastMessageErrorLocked(): Unit {
    val currentSnapshot = snapshot.value
    if (currentSnapshot.chatMessagesError == null && currentSnapshot.lastError == null) {
      return
    }

    snapshotState.value = currentSnapshot.copy(
      chatMessagesError = null,
      lastError = currentSnapshot.chatListError,
    )
  }

  private fun createPlaceholderChat(chatId: Long): TdApi.Chat {
    return TdApi.Chat().apply {
      id = chatId
      title = ""
      unreadCount = 0
      positions = emptyArray()
      chatLists = emptyArray()
    }
  }

  private fun mergeChatPositions(
    positions: Array<TdApi.ChatPosition>?,
    newPosition: TdApi.ChatPosition?,
  ): Array<TdApi.ChatPosition> {
    if (newPosition == null) {
      return positions?.copyOf() ?: emptyArray()
    }

    val nextPositions = positions.orEmpty()
      .filterNot { position -> isSameChatList(position.list, newPosition.list) }
      .toMutableList()

    if (newPosition.order > 0L) {
      nextPositions += newPosition
    }

    return nextPositions.toTypedArray()
  }

  private fun removeChatPositions(
    positions: Array<TdApi.ChatPosition>?,
    chatList: TdApi.ChatList?,
  ): Array<TdApi.ChatPosition> {
    return positions.orEmpty()
      .filterNot { position -> isSameChatList(position.list, chatList) }
      .toTypedArray()
  }

  private fun mergeChatLists(
    chatLists: Array<TdApi.ChatList>?,
    chatList: TdApi.ChatList?,
    shouldAdd: Boolean,
  ): Array<TdApi.ChatList> {
    if (chatList == null) {
      return chatLists?.copyOf() ?: emptyArray()
    }

    val nextChatLists = chatLists.orEmpty()
      .filterNot { existingChatList -> isSameChatList(existingChatList, chatList) }
      .toMutableList()

    if (shouldAdd) {
      nextChatLists += chatList
    }

    return nextChatLists.toTypedArray()
  }

  private fun isSameChatList(
    first: TdApi.ChatList?,
    second: TdApi.ChatList?,
  ): Boolean {
    return when {
      first is TdApi.ChatListMain && second is TdApi.ChatListMain -> true
      first is TdApi.ChatListArchive && second is TdApi.ChatListArchive -> true
      first is TdApi.ChatListFolder && second is TdApi.ChatListFolder -> first.chatFolderId == second.chatFolderId
      else -> false
    }
  }
}

private const val DEFAULT_CHAT_LOAD_LIMIT: Int = 50

private const val DEFAULT_CHAT_MESSAGES_LIMIT: Int = 50

private const val MAX_CHAT_MESSAGES_LIMIT: Int = 100

private const val TELEGRAM_CHAT_LIST_COMPLETE_ERROR_CODE: Int = 404

fun createTelegramClientManager(context: Context): TelegramClientManager {
  return TelegramClientManager(
    clientFactory = RealTelegramTdlibClientFactory,
    parametersFactory = AndroidTelegramTdlibParametersFactory(context),
  )
}
