package com.readmyfeed.app;

import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;

import com.readmyfeed.app.plugins.TtsPlugin;

public class MainActivity extends BridgeActivity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(TtsPlugin.class);

    super.onCreate(savedInstanceState);

    Bridge bridge = this.bridge;

    // Provide a simple native logger that the web layer can call without a custom Capacitor plugin.
    // Usage in JS: window.__RMF_NATIVE_LOG__('message')
    if (bridge != null) {
      bridge
          .getWebView()
          .post(
              () ->
                  bridge
                      .getWebView()
                      .evaluateJavascript(
                          "window.__RMF_NATIVE_LOG__ = function(msg){ try { console.log('[rmf/native] ' + msg); } catch(e) {} };", 
                          null));
    }

    String apiKey = null;
    boolean autoRun = false;

    if (getIntent() != null && getIntent().getExtras() != null) {
      apiKey = getIntent().getExtras().getString("API_KEY");
      autoRun = getIntent().getExtras().getBoolean("AUTO_RUN", false);
    }

    final String safeApiKey = apiKey;
    final boolean safeAutoRun = autoRun;

    android.util.Log.d("rmf", "MainActivity onCreate extras apiKeyPresent=" + (safeApiKey != null && !safeApiKey.isEmpty()) + " autoRun=" + safeAutoRun);

    if (safeApiKey != null && !safeApiKey.isEmpty()) {
      bridge.getWebView().post(
          () ->
              bridge
                  .getWebView()
                  .evaluateJavascript(
                      "window.__RMF_API_KEY__=" + org.json.JSONObject.quote(safeApiKey) + ";" +
                      "window.__RMF_AUTO_RUN__=" + (safeAutoRun ? "true" : "false") + ";" +
                      "console.log('[rmf] injected extras apiKeyPresent=true autoRun=" + (safeAutoRun ? "true" : "false") + "');",
                      null));
    } else {
      bridge.getWebView().post(
          () ->
              bridge
                  .getWebView()
                  .evaluateJavascript(
                      "window.__RMF_AUTO_RUN__=" + (safeAutoRun ? "true" : "false") + ";" +
                      "console.log('[rmf] injected extras apiKeyPresent=false autoRun=" + (safeAutoRun ? "true" : "false") + "');",
                      null));
    }
  }
}

