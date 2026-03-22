package com.yonisirote.readmyfeed.providers.telegram.ui

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.FeedProvider
import com.yonisirote.readmyfeed.providers.ProviderFeatureController
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthException
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.providers.telegram.chats.TelegramChatPreview
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientException
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientManager
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientSnapshot
import com.yonisirote.readmyfeed.providers.telegram.client.createTelegramClientManager
import com.yonisirote.readmyfeed.providers.telegram.speech.TelegramMessageSpeechPlayer
import com.yonisirote.readmyfeed.shell.AppScreen
import com.yonisirote.readmyfeed.shell.AppScreenHost
import com.yonisirote.readmyfeed.shell.ProviderDestination
import com.yonisirote.readmyfeed.shell.matchesProvider
import com.yonisirote.readmyfeed.shell.matchesProviderDestination
import com.yonisirote.readmyfeed.shell.resolveHomeSelectionScreen
import com.yonisirote.readmyfeed.tts.AndroidTtsEngine
import com.yonisirote.readmyfeed.tts.TtsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
  private lateinit var connectScreenController: TelegramConnectScreenController
  private lateinit var chatListScreenController: TelegramChatListScreenController
  private lateinit var messageScreenController: TelegramMessageScreenController

  private var currentScreen: AppScreen = AppScreen.Home
  private var isInitialized = false
  private var snapshotJob: Job? = null
  private var latestSnapshot: TelegramClientSnapshot = TelegramClientSnapshot()

  override val provider: FeedProvider = FeedProvider.TELEGRAM

  override fun initialize(): Boolean {
    return try {
      val dependencies = createTelegramFeatureDependencies(
        activity = activity,
        clientManagerFactory = clientManagerFactory,
        ttsServiceFactory = ttsServiceFactory,
        messageSpeechPlayerFactory = messageSpeechPlayerFactory,
        onOpenSelectedChat = ::openSelectedChat,
      )

      clientManager = dependencies.clientManager
      connectScreenController = TelegramConnectScreenController(
        activity = activity,
        binding = binding,
        showHome = { screenHost.showScreen(AppScreen.Home) },
        onSubmitPhoneNumber = ::submitPhoneNumber,
        onSubmitEmailAddress = ::submitEmailAddress,
        onSubmitCodeOrEmailCode = ::submitCodeOrEmailCode,
        onSubmitPassword = ::submitPassword,
        onRequestQrAuthentication = ::requestQrAuthentication,
        onReturnToPhoneNumberAuthentication = ::returnToPhoneNumberAuthentication,
        onRestartAuthentication = ::restartAuthentication,
      )
      chatListScreenController = TelegramChatListScreenController(
        activity = activity,
        binding = binding,
        chatListAdapter = dependencies.chatListAdapter,
        showHome = { screenHost.showScreen(AppScreen.Home) },
        onRequestChatListLoad = ::requestChatListLoad,
      )
      messageScreenController = TelegramMessageScreenController(
        activity = activity,
        binding = binding,
        messageListAdapter = dependencies.messageListAdapter,
        messageSpeechPlayer = dependencies.messageSpeechPlayer,
        isChatMessagesVisible = { isOnProviderDestination(ProviderDestination.CHAT_MESSAGES) },
        onBackToChatList = ::returnToChatList,
        onRequestMessagesLoad = ::requestSelectedChatMessagesLoad,
      )

      connectScreenController.initialize()
      chatListScreenController.initialize()
      messageScreenController.initialize()
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
    binding.telegramConnectScreen.isVisible = isOnProviderDestination(ProviderDestination.CONNECT)
    binding.telegramChatListScreen.isVisible = isOnProviderDestination(ProviderDestination.CHAT_LIST)
    binding.telegramChatMessagesScreen.isVisible = isOnProviderDestination(ProviderDestination.CHAT_MESSAGES)

    connectScreenController.render(latestSnapshot)
    chatListScreenController.render(latestSnapshot)
    messageScreenController.render(latestSnapshot)
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
            resetMessageSelection()
            ensureClientStarted()
            showProviderScreen(ProviderDestination.CONNECT)
          }
          targetScreen.matchesProvider(provider, ProviderDestination.CHAT_LIST) -> {
            resetMessageSelection()
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
        returnToChatList()
        true
      }
      isOnProviderDestination(ProviderDestination.CHAT_LIST) -> {
        resetMessageSelection()
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
    chatListScreenController.onDestroy()
    messageScreenController.onDestroy()
    clientManager.close()
  }

  private fun observeClientSnapshot() {
    snapshotJob = activity.lifecycleScope.launch {
      clientManager.snapshot.collectLatest { snapshot ->
        latestSnapshot = snapshot
        connectScreenController.render(snapshot)
        chatListScreenController.render(snapshot)
        messageScreenController.render(snapshot)

        // Navigation stays derived from auth/selection state so stale screens self-correct.
        when {
          snapshot.authState is TelegramAuthState.Ready && isOnProviderDestination(ProviderDestination.CONNECT) -> {
            showProviderScreen(ProviderDestination.CHAT_LIST)
            requestChatListLoad()
          }
          snapshot.selectedChatPreview == null && isOnProviderDestination(ProviderDestination.CHAT_MESSAGES) -> {
            messageScreenController.stopPlayback(null)
            showProviderScreen(ProviderDestination.CHAT_LIST)
          }
          snapshot.authState !is TelegramAuthState.Ready && (
            isOnProviderDestination(ProviderDestination.CHAT_LIST) ||
              isOnProviderDestination(ProviderDestination.CHAT_MESSAGES)
            ) -> {
            messageScreenController.stopPlayback(null)
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
    messageScreenController.stopPlayback(null)
    clientManager.restart()
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
      messageScreenController.stopPlayback(null)
      clientManager.selectChat(preview.chatId)
      showProviderScreen(ProviderDestination.CHAT_MESSAGES)
    } catch (error: TelegramAuthException) {
      showErrorToast(error.message)
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
    }
  }

  private fun returnToChatList() {
    messageScreenController.stopPlayback(null)
    clientManager.clearSelectedChat()
    showProviderScreen(ProviderDestination.CHAT_LIST)
  }

  private fun resetMessageSelection() {
    messageScreenController.stopPlayback(null)
    clientManager.clearSelectedChat()
  }

  private fun submitPhoneNumber(phoneNumber: String) {
    submitTelegramAction {
      clientManager.submitPhoneNumber(phoneNumber)
    }
  }

  private fun submitEmailAddress(emailAddress: String) {
    submitTelegramAction {
      clientManager.submitEmailAddress(emailAddress)
    }
  }

  private fun submitCodeOrEmailCode(authState: TelegramAuthState, code: String) {
    submitTelegramAction {
      when (authState) {
        is TelegramAuthState.WaitEmailCode -> clientManager.submitEmailCode(code)
        is TelegramAuthState.WaitCode -> clientManager.submitCode(code)
        else -> Unit
      }
    }
  }

  private fun submitPassword(password: String) {
    submitTelegramAction {
      clientManager.submitPassword(password)
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
