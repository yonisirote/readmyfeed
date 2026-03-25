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
import com.yonisirote.readmyfeed.shell.TelegramDestination
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
        isChatMessagesVisible = { isOnDestination(TelegramDestination.CHAT_MESSAGES) },
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
    return screen is AppScreen.TelegramScreen
  }

  override fun render(screen: AppScreen) {
    if (!isInitialized) {
      return
    }

    currentScreen = screen
    binding.telegramConnectScreen.isVisible = isOnDestination(TelegramDestination.CONNECT)
    binding.telegramChatListScreen.isVisible = isOnDestination(TelegramDestination.CHAT_LIST)
    binding.telegramChatMessagesScreen.isVisible = isOnDestination(TelegramDestination.CHAT_MESSAGES)

    connectScreenController.render(latestSnapshot)
    chatListScreenController.render(latestSnapshot)
    messageScreenController.render(latestSnapshot)
  }

  override fun openFromHome() {
    if (!isInitialized) {
      return
    }

    val hasConnectedSession = latestSnapshot.authState is TelegramAuthState.Ready

    when (val targetScreen = resolveHomeSelectionScreen(provider, hasConnectedSession)) {
      AppScreen.Home -> screenHost.showScreen(AppScreen.Home)
      is AppScreen.TelegramScreen -> {
        when (targetScreen.destination) {
          TelegramDestination.CONNECT -> {
            resetMessageSelection()
            ensureClientStarted()
            showProviderScreen(TelegramDestination.CONNECT)
          }
          TelegramDestination.CHAT_LIST -> {
            resetMessageSelection()
            ensureClientStarted()
            showProviderScreen(TelegramDestination.CHAT_LIST)
            requestChatListLoad()
          }
          TelegramDestination.CHAT_MESSAGES -> Unit
        }
      }
      is AppScreen.XScreen -> Unit
    }
  }

  override fun handleBackPress(): Boolean {
    if (!isInitialized) {
      return false
    }

    return when {
      isOnDestination(TelegramDestination.CONNECT) -> {
        screenHost.showScreen(AppScreen.Home)
        true
      }
      isOnDestination(TelegramDestination.CHAT_MESSAGES) -> {
        returnToChatList()
        true
      }
      isOnDestination(TelegramDestination.CHAT_LIST) -> {
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
          snapshot.authState is TelegramAuthState.Ready && isOnDestination(TelegramDestination.CONNECT) -> {
            showProviderScreen(TelegramDestination.CHAT_LIST)
            requestChatListLoad()
          }
          snapshot.selectedChatPreview == null && isOnDestination(TelegramDestination.CHAT_MESSAGES) -> {
            messageScreenController.stopPlayback(null)
            showProviderScreen(TelegramDestination.CHAT_LIST)
          }
          snapshot.authState !is TelegramAuthState.Ready && (
            isOnDestination(TelegramDestination.CHAT_LIST) ||
              isOnDestination(TelegramDestination.CHAT_MESSAGES)
            ) -> {
            messageScreenController.stopPlayback(null)
            showProviderScreen(TelegramDestination.CONNECT)
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
    showProviderScreen(TelegramDestination.CONNECT)
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
      showProviderScreen(TelegramDestination.CHAT_MESSAGES)
    } catch (error: TelegramAuthException) {
      showErrorToast(error.message)
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
    }
  }

  private fun returnToChatList() {
    messageScreenController.stopPlayback(null)
    clientManager.clearSelectedChat()
    showProviderScreen(TelegramDestination.CHAT_LIST)
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

  private fun isOnDestination(destination: TelegramDestination): Boolean {
    val screen = currentScreen
    return screen is AppScreen.TelegramScreen && screen.destination == destination
  }

  private fun showProviderScreen(destination: TelegramDestination) {
    screenHost.showScreen(AppScreen.TelegramScreen(destination))
  }

  private fun showErrorToast(message: String?) {
    Toast.makeText(
      activity,
      message ?: activity.getString(R.string.telegram_unknown_error),
      Toast.LENGTH_LONG,
    ).show()
  }
}
