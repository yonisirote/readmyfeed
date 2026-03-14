package com.yonisirote.readmyfeed.x.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

interface XSessionStore {
  fun get(): String?
  fun set(cookieString: String)
  fun clear()
}

class PreferencesXSessionStore(context: Context) : XSessionStore {
  private val preferences: SharedPreferences = try {
    val appContext = context.applicationContext
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    EncryptedSharedPreferences.create(
      X_SESSION_PREFERENCES_NAME,
      masterKeyAlias,
      appContext,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  } catch (error: Exception) {
    throw XAuthException(
      message = "Failed to initialize session store",
      code = XAuthErrorCodes.SESSION_STORE_FAILED,
      context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
      cause = error,
    )
  }

  override fun get(): String? {
    return try {
      preferences.getString(X_SESSION_KEY, null)
    } catch (error: Exception) {
      throw XAuthException(
        message = "Failed to read session store",
        code = XAuthErrorCodes.SESSION_STORE_FAILED,
        context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
        cause = error,
      )
    }
  }

  override fun set(cookieString: String) {
    try {
      val committed = preferences.edit().putString(X_SESSION_KEY, cookieString).commit()
      if (!committed) {
        throw XAuthException(
          message = "Failed to persist session store",
          code = XAuthErrorCodes.SESSION_STORE_FAILED,
        )
      }
    } catch (error: XAuthException) {
      throw error
    } catch (error: Exception) {
      throw XAuthException(
        message = "Failed to persist session store",
        code = XAuthErrorCodes.SESSION_STORE_FAILED,
        context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
        cause = error,
      )
    }
  }

  override fun clear() {
    try {
      val committed = preferences.edit().remove(X_SESSION_KEY).commit()
      if (!committed) {
        throw XAuthException(
          message = "Failed to clear session store",
          code = XAuthErrorCodes.SESSION_STORE_FAILED,
        )
      }
    } catch (error: XAuthException) {
      throw error
    } catch (error: Exception) {
      throw XAuthException(
        message = "Failed to clear session store",
        code = XAuthErrorCodes.SESSION_STORE_FAILED,
        context = mapOf("cause" to (error.message ?: error::class.java.simpleName)),
        cause = error,
      )
    }
  }
}
