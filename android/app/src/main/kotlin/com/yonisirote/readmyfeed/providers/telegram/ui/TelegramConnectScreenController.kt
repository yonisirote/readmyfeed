package com.yonisirote.readmyfeed.providers.telegram.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthCodeDeliveryMethod
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthCodeDeliveryMethodKind
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramAuthState
import com.yonisirote.readmyfeed.providers.telegram.auth.TelegramEmailResetState
import com.yonisirote.readmyfeed.providers.telegram.client.TelegramClientSnapshot

internal class TelegramConnectScreenController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val showHome: () -> Unit,
  private val onSubmitPhoneNumber: (String) -> Unit,
  private val onSubmitEmailAddress: (String) -> Unit,
  private val onSubmitCodeOrEmailCode: (TelegramAuthState, String) -> Unit,
  private val onSubmitPassword: (String) -> Unit,
  private val onRequestQrAuthentication: () -> Unit,
  private val onReturnToPhoneNumberAuthentication: () -> Unit,
  private val onRestartAuthentication: () -> Unit,
) {
  private var latestSnapshot: TelegramClientSnapshot = TelegramClientSnapshot()

  fun initialize() {
    binding.backFromTelegramConnect.setOnClickListener {
      showHome()
    }
    binding.telegramSubmitPhoneButton.setOnClickListener {
      onSubmitPhoneNumber(binding.telegramPhoneEditText.text?.toString().orEmpty())
    }
    binding.telegramSubmitEmailButton.setOnClickListener {
      onSubmitEmailAddress(binding.telegramEmailEditText.text?.toString().orEmpty())
    }
    binding.telegramSubmitCodeButton.setOnClickListener {
      onSubmitCodeOrEmailCode(
        latestSnapshot.authState,
        binding.telegramCodeEditText.text?.toString().orEmpty(),
      )
    }
    binding.telegramSubmitPasswordButton.setOnClickListener {
      onSubmitPassword(binding.telegramPasswordEditText.text?.toString().orEmpty())
    }
    binding.telegramQrButton.setOnClickListener {
      onRequestQrAuthentication()
    }
    binding.telegramUsePhoneInsteadButton.setOnClickListener {
      onReturnToPhoneNumberAuthentication()
    }
    binding.telegramRestartButton.setOnClickListener {
      onRestartAuthentication()
    }
  }

  fun render(snapshot: TelegramClientSnapshot) {
    latestSnapshot = snapshot

    val connectScreenState = resolveTelegramConnectScreenState(snapshot)
    val authState = snapshot.authState
    val stage = connectScreenState.stage
    val showsPhoneFallback = connectScreenState.qrActionMode == TelegramQrActionMode.RETURN_TO_PHONE

    binding.telegramConnectStatusTitleTextView.text = resolveStatusTitle(stage)
    binding.telegramConnectStatusBodyTextView.text = resolveStatusBody(authState, stage)
    renderConnectError(connectScreenState.errorMessage)

    binding.telegramPhoneInputSection.isVisible = stage == TelegramConnectStage.PHONE_NUMBER
    binding.telegramEmailInputSection.isVisible = stage == TelegramConnectStage.EMAIL_ADDRESS
    binding.telegramCodeInputSection.isVisible =
      stage == TelegramConnectStage.CODE || stage == TelegramConnectStage.EMAIL_CODE
    binding.telegramPasswordInputSection.isVisible = stage == TelegramConnectStage.PASSWORD

    binding.telegramQrButton.isVisible = !showsPhoneFallback
    binding.telegramQrButton.isEnabled = !showsPhoneFallback && canUseQr(authState)
    binding.telegramQrButton.text = activity.getString(R.string.telegram_use_qr)
    binding.telegramUsePhoneInsteadButton.isVisible = showsPhoneFallback
    binding.telegramUsePhoneInsteadButton.isEnabled = showsPhoneFallback
    binding.telegramRestartButton.isEnabled = stage != TelegramConnectStage.INITIALIZING

    binding.telegramCodeHintTextView.text = resolveCodeHint(authState)
    binding.telegramPasswordHintTextView.text = resolvePasswordHint(authState)
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
}
