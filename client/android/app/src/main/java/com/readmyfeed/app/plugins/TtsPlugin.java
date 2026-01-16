package com.readmyfeed.app.plugins;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;

@CapacitorPlugin(name = "Tts")
public class TtsPlugin extends Plugin {
  private TextToSpeech tts;
  private boolean isReady = false;

  @Override
  public void load() {
    super.load();
    ensureInitialized();
  }

  private void ensureInitialized() {
    if (tts != null) {
      return;
    }

    tts = new TextToSpeech(getContext(), status -> {
      isReady = status == TextToSpeech.SUCCESS;
      if (!isReady) {
        notifyError("tts_init_failed", "Failed to initialize TextToSpeech");
        return;
      }

      int langResult = tts.setLanguage(Locale.US);
      if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
        notifyError("tts_lang_failed", "Locale.US missing or not supported");
      }

      tts.setOnUtteranceProgressListener(
          new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
              JSObject data = new JSObject();
              data.put("utteranceId", utteranceId);
              notifyListeners("ttsStart", data);
            }

            @Override
            public void onDone(String utteranceId) {
              JSObject data = new JSObject();
              data.put("utteranceId", utteranceId);
              notifyListeners("ttsDone", data);
            }

            @Override
            public void onError(String utteranceId) {
              JSObject data = new JSObject();
              data.put("utteranceId", utteranceId);
              data.put("code", "tts_error");
              data.put("message", "TTS error");
              notifyListeners("ttsError", data);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
              JSObject data = new JSObject();
              data.put("utteranceId", utteranceId);
              data.put("code", "tts_error");
              data.put("message", "TTS error code=" + errorCode);
              notifyListeners("ttsError", data);
            }
          });
    });
  }

  private void notifyError(String code, String message) {
    JSObject data = new JSObject();
    data.put("code", code);
    data.put("message", message);
    notifyListeners("ttsError", data);
  }

  @PluginMethod
  public void speak(PluginCall call) {
    ensureInitialized();

    if (!isReady) {
      call.reject("TTS not ready");
      return;
    }

    String text = call.getString("text");
    if (text == null || text.trim().isEmpty()) {
      call.reject("Missing text");
      return;
    }

    String utteranceId = call.getString("utteranceId");
    if (utteranceId == null || utteranceId.trim().isEmpty()) {
      utteranceId = "rmf_utterance";
    }

    int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    if (result == TextToSpeech.ERROR) {
      call.reject("TTS speak failed");
      return;
    }

    JSObject res = new JSObject();
    res.put("ok", true);
    res.put("utteranceId", utteranceId);
    call.resolve(res);
  }

  @PluginMethod
  public void stop(PluginCall call) {
    if (tts != null) {
      tts.stop();
    }

    JSObject res = new JSObject();
    res.put("ok", true);
    call.resolve(res);
  }

  @Override
  protected void handleOnDestroy() {
    if (tts != null) {
      tts.stop();
      tts.shutdown();
      tts = null;
      isReady = false;
    }

    super.handleOnDestroy();
  }
}
