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
import com.yonisirote.readmyfeed.shell.ProviderDestination
import com.yonisirote.readmyfeed.shell.TelegramDestination
import com.yonisirote.readmyfeed.tts.AndroidTtsEngine
import com.yonisirote.readmyfeed.tts.TtsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TelegramFeatureController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val showScreen: (AppScreen) -> Unit,
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

  private var currentDestination: TelegramDestination? = null
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
        showHome = { showScreen(AppScreen.Home) },
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
        showHome = { showScreen(AppScreen.Home) },
        onRequestChatListLoad = ::requestChatListLoad,
      )
      messageScreenController = TelegramMessageScreenController(
        activity = activity,
        binding = binding,
        messageListAdapter = dependencies.messageListAdapter,
        messageSpeechPlayer = dependencies.messageSpeechPlayer,
        isChatMessagesVisible = { currentDestination == TelegramDestination.ChatMessages },
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

  override fun render(screen: AppScreen) {
    if (!isInitialized) {
      return
    }

    require(screen is AppScreen.TelegramScreen) {
      "TelegramFeatureController can render only Telegram screens."
    }

    currentDestination = screen.destination
    binding.telegramConnectScreen.isVisible = currentDestination == ProviderDestination.Connect
    binding.telegramChatListScreen.isVisible = currentDestination == TelegramDestination.ChatList
    binding.telegramChatMessagesScreen.isVisible = currentDestination == TelegramDestination.ChatMessages

    connectScreenController.render(latestSnapshot)
    chatListScreenController.render(latestSnapshot)
    messageScreenController.render(latestSnapshot)
  }

  override fun hide() {
    currentDestination = null
    binding.telegramConnectScreen.isVisible = false
    binding.telegramChatListScreen.isVisible = false
    binding.telegramChatMessagesScreen.isVisible = false
  }

  override fun openFromHome() {
    if (!isInitialized) {
      return
    }

    if (!provider.isAvailable) {
      showScreen(AppScreen.Home)
      return
    }

    resetMessageSelection()
    ensureClientStarted()

    if (latestSnapshot.authState is TelegramAuthState.Ready) {
      showProviderScreen(TelegramDestination.ChatList)
      requestChatListLoad()
    } else {
      showProviderScreen(ProviderDestination.Connect)
    }
  }

  override fun handleBackPress(): Boolean {
    if (!isInitialized) {
      return false
    }

    return when {
      currentDestination == ProviderDestination.Connect -> {
        showScreen(AppScreen.Home)
        true
      }
      currentDestination == TelegramDestination.ChatMessages -> {
        returnToChatList()
        true
      }
      currentDestination == TelegramDestination.ChatList -> {
        resetMessageSelection()
        showScreen(AppScreen.Home)
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
          snapshot.authState is TelegramAuthState.Ready && currentDestination == ProviderDestination.Connect -> {
            showProviderScreen(TelegramDestination.ChatList)
            requestChatListLoad()
          }
          snapshot.selectedChatPreview == null && currentDestination == TelegramDestination.ChatMessages -> {
            messageScreenController.stopPlayback(null)
            showProviderScreen(TelegramDestination.ChatList)
          }
          snapshot.authState !is TelegramAuthState.Ready &&
            (
              currentDestination == TelegramDestination.ChatList ||
                currentDestination == TelegramDestination.ChatMessages
            ) -> {
            messageScreenController.stopPlayback(null)
            showProviderScreen(ProviderDestination.Connect)
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
    showProviderScreen(ProviderDestination.Connect)
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
      showProviderScreen(TelegramDestination.ChatMessages)
    } catch (error: TelegramAuthException) {
      showErrorToast(error.message)
    } catch (error: TelegramClientException) {
      showErrorToast(error.message)
    }
  }

  private fun returnToChatList() {
    messageScreenController.stopPlayback(null)
    clientManager.clearSelectedChat()
    showProviderScreen(TelegramDestination.ChatList)
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

  private fun showProviderScreen(destination: TelegramDestination) {
    showScreen(AppScreen.TelegramScreen(destination))
  }

  private fun showErrorToast(message: String?) {
    Toast.makeText(
      activity,
      message ?: activity.getString(R.string.telegram_unknown_error),
      Toast.LENGTH_LONG,
    ).show()
  }
}
