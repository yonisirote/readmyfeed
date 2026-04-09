package com.yonisirote.readmyfeed.providers.x.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.yonisirote.readmyfeed.R
import com.yonisirote.readmyfeed.databinding.ActivityMainBinding
import com.yonisirote.readmyfeed.providers.x.auth.XAuthErrorCodes
import com.yonisirote.readmyfeed.providers.x.auth.XAuthException
import com.yonisirote.readmyfeed.providers.x.auth.XAuthService
import com.yonisirote.readmyfeed.providers.x.auth.XLoginCaptureCoordinator
import com.yonisirote.readmyfeed.providers.x.auth.X_LOGIN_URL
import com.yonisirote.readmyfeed.providers.x.auth.clearXWebViewCookies
import com.yonisirote.readmyfeed.shell.ProviderDestination
import com.yonisirote.readmyfeed.shell.XDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class XSignInScreenController(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val authService: XAuthService,
  private val captureCoordinator: XLoginCaptureCoordinator,
  private val showHome: () -> Unit,
  private val showProviderScreen: (XDestination) -> Unit,
  private val onSessionCaptured: (String) -> Unit,
) {
  private var captureInFlight = false
  private var didCapture = false
  private var captureJob: Job? = null

  fun initialize() {
    setupSignInScreen()
    setupWebView()
  }

  fun loadStoredSessionAvailability(): Boolean {
    val storedSession = safeLoadStoredSession() ?: return false
    return storedSession.isNotBlank()
  }

  fun clearStoredSession() {
    safeClearStoredSession()
  }

  fun startLoginFlow(clearExistingSession: Boolean) {
    cancelCapture()

    activity.lifecycleScope.launch {
      if (clearExistingSession) {
        safeClearStoredSession()
        clearXWebViewCookies()
      }

      showProviderScreen(ProviderDestination.Connect)
      hideSignInStatus()
      binding.xWebView.stopLoading()

      if (clearExistingSession) {
        binding.xWebView.clearHistory()
        binding.xWebView.clearCache(true)
      }

      binding.xWebView.loadUrl(X_LOGIN_URL)
    }
  }

  fun handleBackPress(): Boolean {
    if (binding.xWebView.canGoBack()) {
      binding.xWebView.goBack()
    } else {
      navigateHome()
    }

    return true
  }

  fun onDestroy() {
    captureJob?.cancel()
    binding.xWebView.stopLoading()
    binding.xWebView.destroy()
  }

  private fun setupSignInScreen() {
    binding.backFromSignIn.setOnClickListener {
      navigateHome()
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(binding.xWebView, true)

    // X login relies on modern web storage/cookies across multiple web origins.
    binding.xWebView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      cacheMode = WebSettings.LOAD_DEFAULT
      loadsImagesAutomatically = true
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
      setSupportMultipleWindows(false)
    }

    binding.xWebView.isVerticalScrollBarEnabled = false
    binding.xWebView.isHorizontalScrollBarEnabled = false
    binding.xWebView.webViewClient = object : WebViewClient() {
      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)

        // Strict capture waits for known post-login routes, then retries missing-cookie timing gaps.
        if (!captureInFlight && !didCapture && captureCoordinator.shouldCaptureOnNavigation(url)) {
          attemptCapture(strict = true)
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)

        // Fallback capture is broader because X does not always finish login on one reliable redirect.
        if (!captureInFlight && !didCapture && captureCoordinator.shouldAttemptFallbackCapture(url)) {
          attemptCapture(strict = false)
        }
      }

      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return false
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
      ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == false) {
          return
        }

        showSignInError(
          error?.description?.toString().orEmpty().ifBlank {
            activity.getString(R.string.generic_auth_error)
          },
        )
      }
    }
  }

  private fun attemptCapture(strict: Boolean) {
    if (captureInFlight || didCapture) {
      return
    }

    captureJob?.cancel()
    captureJob = activity.lifecycleScope.launch {
      captureInFlight = true

      if (strict) {
        showSignInStatus(activity.getString(R.string.status_capturing_body))
      }

      try {
        val session = if (strict) {
          captureCoordinator.captureAndStoreSessionWithRetry()
        } else {
          // Fallback capture only takes a single shot and waits for later page loads if cookies are incomplete.
          captureCoordinator.captureAndStoreSessionOnce()
        }

        didCapture = true
        showProviderScreen(XDestination.ContentList)
        onSessionCaptured(session.cookieString)
      } catch (error: XAuthException) {
        if (!strict && error.code == XAuthErrorCodes.COOKIE_MISSING_REQUIRED) {
          return@launch
        }

        showSignInError(error.message ?: activity.getString(R.string.generic_auth_error))
      } finally {
        captureInFlight = false
      }
    }
  }

  private fun navigateHome() {
    binding.xWebView.stopLoading()
    cancelCapture()
    showHome()
  }

  private fun cancelCapture() {
    captureJob?.cancel()
    captureInFlight = false
    didCapture = false
  }

  private fun showSignInStatus(text: String) {
    binding.signInStatusBar.isVisible = true
    binding.signInProgressBar.isVisible = true
    binding.signInStatusText.text = text
    binding.signInStatusText.setTextColor(activity.getColor(R.color.textSecondary))
  }

  private fun hideSignInStatus() {
    binding.signInStatusBar.isVisible = false
  }

  private fun showSignInError(message: String) {
    binding.signInStatusBar.isVisible = true
    binding.signInProgressBar.isVisible = false
    binding.signInStatusText.text = message
    binding.signInStatusText.setTextColor(activity.getColor(R.color.error))
  }

  private fun safeLoadStoredSession(): String? {
    return try {
      authService.loadStoredSession()
    } catch (error: XAuthException) {
      Toast.makeText(
        activity,
        error.message ?: activity.getString(R.string.generic_auth_error),
        Toast.LENGTH_LONG,
      ).show()
      null
    }
  }

  private fun safeClearStoredSession() {
    try {
      authService.clearStoredSession()
    } catch (error: XAuthException) {
      Toast.makeText(
        activity,
        error.message ?: activity.getString(R.string.generic_auth_error),
        Toast.LENGTH_LONG,
      ).show()
    }
  }
}
