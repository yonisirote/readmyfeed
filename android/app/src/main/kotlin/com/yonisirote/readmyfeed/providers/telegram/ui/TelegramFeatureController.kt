package com.yonisirote.readmyfeed.providers.telegram.ui

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yonisirote.readmyfeed.shell.AppScreen
import com.yonisirote.readmyfeed.shell.AppScreenHost
import com.yonisirote.readmyfeed.providers.FeedProvider
import com.yonisirote.readmyfeed.shell.ProviderDestination
import com.yonisirote.readmyfeed.providers.ProviderFeatureController
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.shell.matchesProvider
import com.yonisirote.readmyfeed.shell.matchesProviderDestination
import com.yonisirote.readmyfeed.shell.resolveHomeSelectionScreen
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthCodeDeliveryMethod
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthCodeDeliveryMethodKind
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthException
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramEmailResetState
import com.yonisirote.readmyfeed.providers.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.providers.telegram.chats.hasUnreadMessages
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientException
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientManager
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientSnapshot
import com.yonisirote.readmyfeed.providers.telegram.client.createTelegramClientManager
import com.yonisirote.readmyfeed.providers.telegram.speech.TelegramMessageSpeechPlayer
import com.yonisirote.readmyfeed.tts.AndroidTtsEngine
import com.yonisirote.readmyfeed.tts.TtsException
import com.yonisirote.readmyfeed.tts.TtsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TelegramFeatureController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val screenHost: AppScreenHost,
  private val clientManagerFactory: (AppCompatActivity) -> TelegramClientManager = { hostActivity ->
    createTelegramClientManager(hostActivity.applicationContext)
  },
  private val ttsServiceFactory: (AppCompatActivity) -> TtsService = { hostActivity ->
    TtsService(AndroidTtsEngine(hostActivity.applicationContext))
  },
  private val messageSpeechPlayerFactory: (TtsService) -> TelegramMessageSpeechPlayer = { ttsService ->
    TelegramMessageSpeechPlayer(ttsService)
  },
) : ProviderFeatureController {
  private lateinit var clientManager: TelegramClientManager
  private lateinit var chatListAdapter: TelegramChatListAdapter
  private lateinit var messageListAdapter: TelegramMessageListAdapter
  private lateinit var ttsService: TtsService
  private lateinit var messageSpeechPlayer: TelegramMessageSpeechPlayer

  private var currentScreen: AppScreen = AppScreen.Home
  private var isInitialized = false
  private var snapshotJob: Job? = null
  private var speakJob: Job? = null
  private var isSpeakingMessages = false
  private var latestSnapshot: TelegramClientSnapshot = TelegramClientSnapshot()
  private var connectScreenState: TelegramConnectScreenState = resolveTelegramConnectScreenState(latestSnapshot)
  private var chatListScreenState: TelegramChatListScreenState = resolveTelegramChatListScreenState(latestSnapshot)
  private var messageListScreenState: TelegramMessageListScreenState = resolveTelegramMessageListScreenState(latestSnapshot)

  override val provider: FeedProvider = FeedProvider.TELEGRAM

  override fun initialize(): Boolean {
    return try {
      clientManager = clientManagerFactory(activity)
      ttsService = ttsServiceFactory(activity)
      messageSpeechPlayer = messageSpeechPlayerFactory(ttsService)
      chatListAdapter = TelegramChatListAdapter(::openSelectedChat)
      messageListAdapter = TelegramMessageListAdapter()
      setupConnectScreen()
      setupChatListScreen()
      setupChatMessagesScreen()
      observeClientSnapshot()
      isInitialized = true
      true
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
      false
    }
  }

  override fun supports(screen: AppScreen): Boolean {
    return screen.matchesProviderDestination(provider, ProviderDestination.CONNECT) ||
      screen.matchesProviderDestination(provider, ProviderDestination.CHAT_LIST) ||
      screen.matchesProviderDestination(provider, ProviderDestination.CHAT_MESSAGES)
  }

  override fun render(screen: AppScreen) {
    if (!isInitialized) {
      return
    }

    currentScreen = if (supports(screen)) screen else AppScreen.Home
    binding.telegramConnectScreen.isVisible = currentScreen.matchesProviderDestination(
      provider = provider,
      destination = ProviderDestination.CONNECT,
    )
    binding.telegramChatListScreen.isVisible = currentScreen.matchesProviderDestination(
      provider = provider,
      destination = ProviderDestination.CHAT_LIST,
    )
    binding.telegramChatMessagesScreen.isVisible = currentScreen.matchesProviderDestination(
      provider = provider,
      destination = ProviderDestination.CHAT_MESSAGES,
    )

    renderConnectScreen(latestSnapshot)
    renderChatListScreen(latestSnapshot)
    renderChatMessagesScreen(latestSnapshot)
  }

  override fun openFromHome() {
    if (!isInitialized) {
      return
    }

    val hasConnectedSession = latestSnapshot.authState is TelegramAuthState.Ready
    val targetScreen = resolveHomeSelectionScreen(
      provider = provider,
      hasStoredSession = hasConnectedSession,
    )

    when (targetScreen) {
      AppScreen.Home -> screenHost.showScreen(AppScreen.Home)
      is AppScreen.ProviderScreen -> {
        when {
          targetScreen.matchesProvider(provider, ProviderDestination.CONNECT) -> {
            stopMessagePlayback(null)
            clientManager.clearSelectedChat()
            ensureClientStarted()
            showProviderScreen(ProviderDestination.CONNECT)
          }
          targetScreen.matchesProvider(provider, ProviderDestination.CHAT_LIST) -> {
            stopMessagePlayback(null)
            ensureClientStarted()
            showProviderScreen(ProviderDestination.CHAT_LIST)
            requestChatListLoad()
          }
        }
      }
    }
  }

  override fun handleBackPress(): Boolean {
    if (!isInitialized) {
      return false
    }

    return when {
      isOnProviderDestination(ProviderDestination.CONNECT) -> {
        screenHost.showScreen(AppScreen.Home)
        true
      }
      isOnProviderDestination(ProviderDestination.CHAT_MESSAGES) -> {
        stopMessagePlayback(null)
        clientManager.clearSelectedChat()
        showProviderScreen(ProviderDestination.CHAT_LIST)
        true
      }
      isOnProviderDestination(ProviderDestination.CHAT_LIST) -> {
        stopMessagePlayback(null)
        clientManager.clearSelectedChat()
        screenHost.showScreen(AppScreen.Home)
        true
      }
      else -> false
    }
  }

  override fun onDestroy() {
    if (!isInitialized) {
      return
    }

    snapshotJob?.cancel()
    speakJob?.cancel()
    binding.telegramChatRecyclerView.adapter = null
    binding.telegramMessageRecyclerView.adapter = null
    messageSpeechPlayer.stop()
    messageSpeechPlayer.shutdown()
    clientManager.close()
  }

  private fun setupConnectScreen() {
    binding.backFromTelegramConnect.setOnClickListener {
      screenHost.showScreen(AppScreen.Home)
    }
    binding.telegramSubmitPhoneButton.setOnClickListener {
      submitPhoneNumber()
    }
    binding.telegramSubmitEmailButton.setOnClickListener {
      submitEmailAddress()
    }
    binding.telegramSubmitCodeButton.setOnClickListener {
      submitCodeOrEmailCode()
    }
    binding.telegramSubmitPasswordButton.setOnClickListener {
      submitPassword()
    }
    binding.telegramQrButton.setOnClickListener {
      requestQrAuthentication()
    }
    binding.telegramUsePhoneInsteadButton.setOnClickListener {
      returnToPhoneNumberAuthentication()
    }
    binding.telegramRestartButton.setOnClickListener {
      restartAuthentication()
    }
  }

  private fun setupChatListScreen() {
    binding.backFromTelegramChatList.setOnClickListener {
      screenHost.showScreen(AppScreen.Home)
    }
    binding.telegramLoadChatsButton.setOnClickListener {
      requestChatListLoad()
    }
    binding.telegramChatRecyclerView.layoutManager = LinearLayoutManager(activity)
    binding.telegramChatRecyclerView.adapter = chatListAdapter
  }

  private fun setupChatMessagesScreen() {
    binding.backFromTelegramChatMessages.setOnClickListener {
      stopMessagePlayback(null)
      clientManager.clearSelectedChat()
      showProviderScreen(ProviderDestination.CHAT_LIST)
    }
    binding.telegramReloadMessagesButton.setOnClickListener {
      requestSelectedChatMessagesLoad()
    }
    binding.telegramPlayMessagesButton.setOnClickListener {
      if (isSpeakingMessages) {
        return@setOnClickListener
      }
      startMessagePlayback()
    }
    binding.telegramStopMessagesButton.setOnClickListener {
      stopMessagePlayback(activity.getString(R.string.telegram_message_speech_status_stopped))
    }
    binding.telegramMessageRecyclerView.layoutManager = LinearLayoutManager(activity)
    binding.telegramMessageRecyclerView.adapter = messageListAdapter
  }

  private fun observeClientSnapshot() {
    snapshotJob = activity.lifecycleScope.launch {
      clientManager.snapshot.collectLatest { snapshot ->
        latestSnapshot = snapshot
        connectScreenState = resolveTelegramConnectScreenState(snapshot)
        chatListScreenState = resolveTelegramChatListScreenState(snapshot)
        messageListScreenState = resolveTelegramMessageListScreenState(snapshot)
        renderConnectScreen(snapshot)
        renderChatListScreen(snapshot)
        renderChatMessagesScreen(snapshot)

        when {
          snapshot.authState is TelegramAuthState.Ready && isOnProviderDestination(ProviderDestination.CONNECT) -> {
            showProviderScreen(ProviderDestination.CHAT_LIST)
            requestChatListLoad()
          }
          snapshot.selectedChatPreview == null && isOnProviderDestination(ProviderDestination.CHAT_MESSAGES) -> {
            stopMessagePlayback(null)
            showProviderScreen(ProviderDestination.CHAT_LIST)
          }
          snapshot.authState !is TelegramAuthState.Ready && (
            isOnProviderDestination(ProviderDestination.CHAT_LIST) ||
              isOnProviderDestination(ProviderDestination.CHAT_MESSAGES)
            ) -> {
            stopMessagePlayback(null)
            showProviderScreen(ProviderDestination.CONNECT)
          }
        }
      }
    }
  }

  private fun ensureClientStarted() {
    try {
      clientManager.start()
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
    }
  }

  private fun restartAuthentication() {
    stopMessagePlayback(null)
    clientManager.close()
    ensureClientStarted()
    showProviderScreen(ProviderDestination.CONNECT)
  }

  private fun requestChatListLoad() {
    ensureClientStarted()

    try {
      clientManager.loadChatList()
    } catch (error: TelegramAuthException) {
      showErrorToast(error.message)
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
    }
  }

  private fun requestSelectedChatMessagesLoad() {
    ensureClientStarted()

    try {
      clientManager.loadSelectedChatMessages()
    } catch (error: TelegramAuthException) {
      showErrorToast(error.message)
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
    }
  }

  private fun openSelectedChat(preview: TelegramChatPreview) {
    ensureClientStarted()

    try {
      stopMessagePlayback(null)
      clientManager.selectChat(preview.chatId)
      showProviderScreen(ProviderDestination.CHAT_MESSAGES)
    } catch (error: TelegramAuthException) {
      showErrorToast(error.message)
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
    }
  }

  private fun submitPhoneNumber() {
    submitTelegramAction {
      clientManager.submitPhoneNumber(binding.telegramPhoneEditText.text?.toString().orEmpty())
    }
  }

  private fun submitEmailAddress() {
    submitTelegramAction {
      clientManager.submitEmailAddress(binding.telegramEmailEditText.text?.toString().orEmpty())
    }
  }

  private fun submitCodeOrEmailCode() {
    val code = binding.telegramCodeEditText.text?.toString().orEmpty()
    submitTelegramAction {
      when (latestSnapshot.authState) {
        is TelegramAuthState.WaitEmailCode -> clientManager.submitEmailCode(code)
        is TelegramAuthState.WaitCode -> clientManager.submitCode(code)
        else -> Unit
      }
    }
  }

  private fun submitPassword() {
    submitTelegramAction {
      clientManager.submitPassword(binding.telegramPasswordEditText.text?.toString().orEmpty())
    }
  }

  private fun requestQrAuthentication() {
    submitTelegramAction {
      clientManager.requestQrCodeAuthentication()
    }
  }

  private fun returnToPhoneNumberAuthentication() {
    submitTelegramAction {
      clientManager.returnToPhoneNumberAuthentication()
    }
  }

  private fun submitTelegramAction(action: () -> Unit) {
    ensureClientStarted()

    try {
      action()
    } catch (error: TelegramAuthException) {
      showErrorToast(error.message)
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
    }
  }

  private fun renderConnectScreen(snapshot: TelegramClientSnapshot) {
    if (!isInitialized) {
      return
    }

    val authState = snapshot.authState
    val stage = connectScreenState.stage

    binding.telegramConnectStatusTitleTextView.text = resolveStatusTitle(stage)
    binding.telegramConnectStatusBodyTextView.text = resolveStatusBody(authState, stage)
    renderConnectError(snapshot.lastError?.message)

    binding.telegramPhoneInputSection.isVisible = stage == TelegramConnectStage.PHONE_NUMBER
    binding.telegramEmailInputSection.isVisible = stage == TelegramConnectStage.EMAIL_ADDRESS
    binding.telegramCodeInputSection.isVisible = stage == TelegramConnectStage.CODE || stage == TelegramConnectStage.EMAIL_CODE
    binding.telegramPasswordInputSection.isVisible = stage == TelegramConnectStage.PASSWORD

    val showsPhoneFallback = connectScreenState.qrActionMode == TelegramQrActionMode.RETURN_TO_PHONE

    binding.telegramQrButton.isVisible = !showsPhoneFallback
    binding.telegramQrButton.isEnabled = !showsPhoneFallback && canUseQr(authState)
    binding.telegramRestartButton.isEnabled = stage != TelegramConnectStage.INITIALIZING
    binding.telegramQrButton.text = activity.getString(R.string.telegram_use_qr)
    binding.telegramUsePhoneInsteadButton.isVisible = showsPhoneFallback
    binding.telegramUsePhoneInsteadButton.isEnabled = showsPhoneFallback

    binding.telegramCodeHintTextView.text = resolveCodeHint(authState)
    binding.telegramPasswordHintTextView.text = resolvePasswordHint(authState)
  }

  private fun renderChatListScreen(snapshot: TelegramClientSnapshot) {
    if (!isInitialized) {
      return
    }

    val previews = snapshot.chatPreviews
    val unreadChatCount = previews.count { preview -> preview.hasUnreadMessages() }
    val stage = chatListScreenState.stage
    val errorMessage = chatListScreenState.errorMessage.orEmpty().trim()

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

  private fun renderChatMessagesScreen(snapshot: TelegramClientSnapshot) {
    if (!isInitialized) {
      return
    }

    val stage = messageListScreenState.stage
    val messages = snapshot.selectedChatMessages
    val selectedChatTitle = snapshot.selectedChatPreview?.title.orEmpty().ifBlank {
      activity.getString(R.string.telegram_chat_untitled)
    }
    val errorMessage = messageListScreenState.errorMessage.orEmpty().trim()

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

  private fun renderConnectError(message: String?) {
    val trimmedMessage = message.orEmpty().trim()
    binding.telegramConnectErrorTextView.isVisible = trimmedMessage.isNotBlank()
    binding.telegramConnectErrorTextView.text = trimmedMessage
  }

  private fun resolveStatusTitle(stage: TelegramConnectStage): String {
    return when (stage) {
      TelegramConnectStage.IDLE -> activity.getString(R.string.telegram_status_idle_title)
      TelegramConnectStage.INITIALIZING -> activity.getString(R.string.telegram_status_initializing_title)
      TelegramConnectStage.PHONE_NUMBER -> activity.getString(R.string.telegram_status_phone_title)
      TelegramConnectStage.EMAIL_ADDRESS -> activity.getString(R.string.telegram_status_email_title)
      TelegramConnectStage.EMAIL_CODE -> activity.getString(R.string.telegram_status_email_code_title)
      TelegramConnectStage.CODE -> activity.getString(R.string.telegram_status_code_title)
      TelegramConnectStage.PASSWORD -> activity.getString(R.string.telegram_status_password_title)
      TelegramConnectStage.QR_CONFIRMATION -> activity.getString(R.string.telegram_status_qr_title)
      TelegramConnectStage.READY -> activity.getString(R.string.telegram_status_ready_title)
      TelegramConnectStage.BLOCKED -> activity.getString(R.string.telegram_status_blocked_title)
      TelegramConnectStage.CLOSED -> activity.getString(R.string.telegram_status_closed_title)
    }
  }

  private fun resolveStatusBody(
    authState: TelegramAuthState,
    stage: TelegramConnectStage,
  ): String {
    val base = when (stage) {
      TelegramConnectStage.IDLE -> activity.getString(R.string.telegram_status_idle_body)
      TelegramConnectStage.INITIALIZING -> activity.getString(R.string.telegram_status_initializing_body)
      TelegramConnectStage.PHONE_NUMBER -> activity.getString(R.string.telegram_status_phone_body)
      TelegramConnectStage.EMAIL_ADDRESS -> activity.getString(R.string.telegram_status_email_body)
      TelegramConnectStage.EMAIL_CODE -> activity.getString(R.string.telegram_status_email_code_body)
      TelegramConnectStage.CODE -> activity.getString(R.string.telegram_status_code_body)
      TelegramConnectStage.PASSWORD -> activity.getString(R.string.telegram_status_password_body)
      TelegramConnectStage.QR_CONFIRMATION -> activity.getString(R.string.telegram_status_qr_body)
      TelegramConnectStage.READY -> activity.getString(R.string.telegram_status_ready_body)
      TelegramConnectStage.BLOCKED -> activity.getString(R.string.telegram_status_blocked_body)
      TelegramConnectStage.CLOSED -> activity.getString(R.string.telegram_status_closed_body)
    }

    val details = buildList {
      when (authState) {
        is TelegramAuthState.WaitEmailCode -> {
          if (authState.emailAddressPattern.isNotBlank()) {
            add(activity.getString(R.string.telegram_email_code_hint, authState.emailAddressPattern))
          }
          when (val resetState = authState.resetState) {
            is TelegramEmailResetState.Available -> add(
              activity.getString(
                R.string.telegram_email_reset_available_hint,
                resetState.waitPeriodSeconds,
              ),
            )
            is TelegramEmailResetState.Pending -> add(
              activity.getString(
                R.string.telegram_email_reset_pending_hint,
                resetState.resetInSeconds,
              ),
            )
            null -> Unit
          }
        }
        is TelegramAuthState.WaitQrConfirmation -> {
          if (authState.link.isNotBlank()) {
            add(activity.getString(R.string.telegram_qr_link_label, authState.link))
          }
        }
        else -> Unit
      }
    }

    return if (details.isEmpty()) {
      base
    } else {
      (listOf(base) + details).joinToString(separator = "\n\n")
    }
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

  private fun resolveCodeHint(authState: TelegramAuthState): String {
    return when (authState) {
      is TelegramAuthState.WaitCode -> buildCodeHint(authState)
      is TelegramAuthState.WaitEmailCode -> {
        val parts = mutableListOf<String>()
        if (authState.emailAddressPattern.isNotBlank()) {
          parts += activity.getString(R.string.telegram_email_code_hint, authState.emailAddressPattern)
        }
        parts.joinToString(separator = "\n")
      }
      else -> ""
    }
  }

  private fun resolvePasswordHint(authState: TelegramAuthState): String {
    if (authState !is TelegramAuthState.WaitPassword) {
      return ""
    }

    val parts = mutableListOf<String>()
    if (authState.passwordHint.isNotBlank()) {
      parts += activity.getString(R.string.telegram_password_hint_label, authState.passwordHint)
    }
    if (authState.hasRecoveryEmailAddress && authState.recoveryEmailAddressPattern.isNotBlank()) {
      parts += activity.getString(
        R.string.telegram_password_recovery_hint,
        authState.recoveryEmailAddressPattern,
      )
    }
    return parts.joinToString(separator = "\n")
  }

  private fun buildCodeHint(authState: TelegramAuthState.WaitCode): String {
    val parts = mutableListOf<String>()
    parts += describeCodeDeliveryMethod(authState.deliveryMethod)
    if (authState.nextDeliveryMethod != null) {
      parts += activity.getString(
        R.string.telegram_next_code_hint,
        describeCodeDeliveryMethod(authState.nextDeliveryMethod),
      )
    }
    if (authState.timeoutSeconds > 0) {
      parts += activity.getString(R.string.telegram_code_timeout_hint, authState.timeoutSeconds)
    }
    return parts.filter { it.isNotBlank() }.joinToString(separator = "\n")
  }

  private fun describeCodeDeliveryMethod(method: TelegramAuthCodeDeliveryMethod?): String {
    if (method == null) {
      return activity.getString(R.string.telegram_code_delivery_unknown)
    }

    val lengthSuffix = method.length?.takeIf { it > 0 }?.let { length ->
      activity.getString(R.string.telegram_code_length_suffix, length)
    }.orEmpty()

    return when (method.kind) {
      TelegramAuthCodeDeliveryMethodKind.TELEGRAM_MESSAGE -> activity.getString(
        R.string.telegram_code_delivery_telegram_message,
        lengthSuffix,
      )
      TelegramAuthCodeDeliveryMethodKind.SMS -> activity.getString(
        R.string.telegram_code_delivery_sms,
        lengthSuffix,
      )
      TelegramAuthCodeDeliveryMethodKind.SMS_WORD -> activity.getString(
        R.string.telegram_code_delivery_sms_word,
        method.hint.orEmpty(),
      )
      TelegramAuthCodeDeliveryMethodKind.SMS_PHRASE -> activity.getString(
        R.string.telegram_code_delivery_sms_phrase,
        method.hint.orEmpty(),
      )
      TelegramAuthCodeDeliveryMethodKind.CALL -> activity.getString(
        R.string.telegram_code_delivery_call,
        lengthSuffix,
      )
      TelegramAuthCodeDeliveryMethodKind.FLASH_CALL -> activity.getString(
        R.string.telegram_code_delivery_flash_call,
        method.hint.orEmpty(),
      )
      TelegramAuthCodeDeliveryMethodKind.MISSED_CALL -> activity.getString(
        R.string.telegram_code_delivery_missed_call,
        method.hint.orEmpty(),
        method.length ?: 0,
      )
      TelegramAuthCodeDeliveryMethodKind.FRAGMENT -> activity.getString(
        R.string.telegram_code_delivery_fragment,
        method.hint.orEmpty(),
      )
      TelegramAuthCodeDeliveryMethodKind.FIREBASE_ANDROID -> activity.getString(
        R.string.telegram_code_delivery_firebase_android,
        lengthSuffix,
      )
      TelegramAuthCodeDeliveryMethodKind.FIREBASE_IOS -> activity.getString(
        R.string.telegram_code_delivery_firebase_ios,
        lengthSuffix,
      )
      TelegramAuthCodeDeliveryMethodKind.UNKNOWN -> activity.getString(R.string.telegram_code_delivery_unknown)
    }
  }

  private fun canUseQr(authState: TelegramAuthState): Boolean {
    return authState is TelegramAuthState.WaitPhoneNumber ||
      authState is TelegramAuthState.WaitPremiumPurchase ||
      authState is TelegramAuthState.WaitEmailAddress ||
      authState is TelegramAuthState.WaitEmailCode ||
      authState is TelegramAuthState.WaitCode ||
      authState is TelegramAuthState.WaitRegistration ||
      authState is TelegramAuthState.WaitPassword
  }

  private fun updateMessageSpeechControls(snapshot: TelegramClientSnapshot) {
    val canPlay = isOnProviderDestination(ProviderDestination.CHAT_MESSAGES) &&
      snapshot.selectedChatMessages.isNotEmpty() &&
      !snapshot.isChatMessagesLoading &&
      !isSpeakingMessages

    binding.telegramPlayMessagesButton.isEnabled = canPlay
    binding.telegramStopMessagesButton.isEnabled = isSpeakingMessages
  }

  private fun startMessagePlayback() {
    val messages = latestSnapshot.selectedChatMessages
    if (!messageSpeechPlayer.hasSpeakableItems(messages)) {
      binding.telegramMessageSpeechStatusTextView.text = activity.getString(
        R.string.telegram_message_speech_status_no_items,
      )
      updateMessageSpeechControls(latestSnapshot)
      return
    }

    speakJob?.cancel()
    speakJob = activity.lifecycleScope.launch {
      isSpeakingMessages = true
      updateMessageSpeechControls(latestSnapshot)
      binding.telegramMessageSpeechStatusTextView.text = activity.getString(
        R.string.telegram_message_speech_status_loading,
      )

      try {
        val summary = withContext(Dispatchers.IO) {
          messageSpeechPlayer.speak(messages) { _, index, total ->
            activity.runOnUiThread {
              if (isSpeakingMessages) {
                binding.telegramMessageSpeechStatusTextView.text = activity.getString(
                  R.string.telegram_message_speech_status_playing,
                  index,
                  total,
                )
              }
            }
          }
        }

        if (!isSpeakingMessages) {
          return@launch
        }

        binding.telegramMessageSpeechStatusTextView.text = when {
          summary.spokenItems <= 0 -> activity.getString(R.string.telegram_message_speech_status_no_items)
          summary.skippedItems > 0 -> activity.getString(
            R.string.telegram_message_speech_status_skipped,
            summary.spokenItems,
            summary.skippedItems,
          )
          else -> activity.getString(R.string.telegram_message_speech_status_done, summary.spokenItems)
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        if (isSpeakingMessages) {
          val message = if (error is TtsException) {
            error.message
          } else {
            error.message ?: "Unknown error."
          }
          binding.telegramMessageSpeechStatusTextView.text = buildTelegramSpeechErrorMessage(message)
        }
      } finally {
        isSpeakingMessages = false
        speakJob = null
        updateMessageSpeechControls(latestSnapshot)
      }
    }
  }

  private fun stopMessagePlayback(status: String?) {
    speakJob?.cancel()
    speakJob = null
    messageSpeechPlayer.stop()
    isSpeakingMessages = false
    updateMessageSpeechControls(latestSnapshot)

    if (status != null) {
      binding.telegramMessageSpeechStatusTextView.text = status
    }
  }

  private fun buildTelegramSpeechErrorMessage(message: String?): String {
    return activity.getString(R.string.telegram_message_speech_status_error_prefix) +
      " " +
      (message ?: "Unknown error.")
  }

  private fun isOnProviderDestination(destination: ProviderDestination): Boolean {
    return currentScreen.matchesProviderDestination(
      provider = provider,
      destination = destination,
    )
  }

  private fun showProviderScreen(destination: ProviderDestination) {
    screenHost.showScreen(
      AppScreen.ProviderScreen(
        provider = provider,
        destination = destination,
      ),
    )
  }

  private fun showErrorToast(message: String?) {
    Toast.makeText(
      activity,
      message ?: activity.getString(R.string.telegram_unknown_error),
      Toast.LENGTH_LONG,
    ).show()
  }
}
