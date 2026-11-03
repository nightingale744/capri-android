package com.capri.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.media.MediaPlayer;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {

    private static final String WORKER_URL =
            "https://ecapri-voice.pitchayawat020.workers.dev/";

    private static final String KEY_ALIAS = "CapriFishAudioKey";
    private static final String PREFS = "capri_secure_settings";
    private static final String TOKEN_KEY = "fish_token";

    private WebView webView;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    private MediaPlayer mediaPlayer;
    private File currentAudioFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);

                ttsReady =
                        result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED;

                textToSpeech.setSpeechRate(1.0f);
            }
        });

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF101014);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());

        webView.addJavascriptInterface(
                new CapriBridge(),
                "CapriNative"
        );

        setContentView(webView);

        webView.loadUrl("file:///android_asset/index.html");
    }

    private class CapriBridge {

        @JavascriptInterface
        public void sendChat(String payload, String callbackId) {
            executor.execute(() -> {
                String result;

                try {
                    result = performChat(
                            new JSONObject(payload)
                    ).toString();

                } catch (Exception e) {
                    JSONObject err = new JSONObject();

                    try {
                        err.put(
                                "error",
                                e.getMessage() == null
                                        ? "Request failed"
                                        : e.getMessage()
                        );
                    } catch (Exception ignored) {}

                    result = err.toString();
                }

                final String response = result;

                mainHandler.post(() -> {
                    if (webView != null) {
                        String js =
                                "window.onCapriNativeReply("
                                + JSONObject.quote(callbackId)
                                + ","
                                + JSONObject.quote(response)
                                + ")";

                        webView.evaluateJavascript(js, null);
                    }
                });
            });
        }

        @JavascriptInterface
        public void saveFishToken(String token) {
            executor.execute(() -> {
                try {
                    saveEncryptedToken(token);
                } catch (Exception e) {
                    android.util.Log.e(
                            "Capri",
                            "Could not save Fish Audio token",
                            e
                    );
                }
            });
        }

        @JavascriptInterface
        public void speakFishAudio(String text) {
            if (text == null || text.trim().isEmpty()) return;

            executor.execute(() -> {
                try {
                    String token = readEncryptedToken();

                    if (token == null || token.isEmpty()) {
                        android.util.Log.e(
                                "Capri",
                                "Fish Audio token is not configured"
                        );
                        return;
                    }

                    byte[] audio = requestFishAudio(text, token);

                    File audioFile = File.createTempFile(
                            "capri_voice_",
                            ".mp3",
                            getCacheDir()
                    );

                    try (FileOutputStream out =
                                 new FileOutputStream(audioFile)) {
                        out.write(audio);
                    }

                    mainHandler.post(() -> playAudioFile(audioFile));

                } catch (Exception e) {
                    android.util.Log.e(
                            "Capri",
                            "Fish Audio request failed",
                            e
                    );
                }
            });
        }

        @JavascriptInterface
        public void speak(String text) {
            mainHandler.post(() -> {
                if (ttsReady && textToSpeech != null) {
                    textToSpeech.speak(
                            text,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "capri_reply"
                    );
                }
            });
        }

        @JavascriptInterface
        public void stopSpeaking() {
            mainHandler.post(() -> {
                if (textToSpeech != null) {
                    textToSpeech.stop();
                }
                stopAudioPlayback();
            });
        }
    }

    private byte[] requestFishAudio(String text, String token)
            throws Exception {

        HttpURLConnection conn =
                (HttpURLConnection)
                new URL(WORKER_URL).openConnection();

        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);

        conn.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        conn.setRequestProperty(
                "Authorization",
                "Bearer " + token
        );

        JSONObject body = new JSONObject();
        body.put("text", text);

        byte[] requestBytes =
                body.toString().getBytes(StandardCharsets.UTF_8);

        try (OutputStream out = conn.getOutputStream()) {
            out.write(requestBytes);
        }

        int status = conn.getResponseCode();

        if (status < 200 || status >= 300) {
            InputStream errorStream = conn.getErrorStream();
            String error = errorStream == null
                    ? "HTTP " + status
                    : readText(errorStream);

            conn.disconnect();
            throw new Exception(error);
        }

        byte[] audio;

        try (InputStream input = conn.getInputStream();
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            audio = output.toByteArray();
        }

        conn.disconnect();
        return audio;
    }

    private String readText(InputStream stream) throws Exception {
        StringBuilder result = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        stream,
                        StandardCharsets.UTF_8
                ))) {

            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }

    private void playAudioFile(File file) {
        stopAudioPlayback();

        try {
            currentAudioFile = file;
            mediaPlayer = new MediaPlayer();

            mediaPlayer.setDataSource(file.getAbsolutePath());

            mediaPlayer.setOnPreparedListener(player ->
                    player.start()
            );

            mediaPlayer.setOnCompletionListener(player ->
                    stopAudioPlayback()
            );

            mediaPlayer.setOnErrorListener((player, what, extra) -> {
                stopAudioPlayback();
                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            android.util.Log.e(
                    "Capri",
                    "Audio playback failed",
                    e
            );
            stopAudioPlayback();
        }
    }

    private void stopAudioPlayback() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {}

            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (currentAudioFile != null) {
            currentAudioFile.delete();
            currentAudioFile = null;
        }
    }

    private void saveEncryptedToken(String token)
            throws Exception {

        if (token == null || token.trim().isEmpty()) {
            throw new Exception("Token is empty");
        }

        SecretKey key = getOrCreateSecretKey();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] encrypted = cipher.doFinal(
                token.trim().getBytes(StandardCharsets.UTF_8)
        );

        String stored =
                Base64.encodeToString(
                        cipher.getIV(),
                        Base64.NO_WRAP
                )
                + "."
                + Base64.encodeToString(
                        encrypted,
                        Base64.NO_WRAP
                );

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(TOKEN_KEY, stored)
                .apply();
    }

    private String readEncryptedToken() throws Exception {
        String stored = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(TOKEN_KEY, null);

        if (stored == null) return null;

        String[] parts = stored.split("\\.", 2);

        if (parts.length != 2) {
            throw new Exception("Saved token data is invalid");
        }

        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted =
                Base64.decode(parts[1], Base64.NO_WRAP);

        SecretKey key = getOrCreateSecretKey();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(128, iv)
        );

        byte[] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );

        generator.init(
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT |
                        KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        );

        return generator.generateKey();
    }

    private JSONObject performChat(JSONObject input)
            throws Exception {

        String base = input.getString("baseUrl").trim();
        String model = input.getString("model").trim();
        String apiKey = input.optString("apiKey", "").trim();

        if (!base.startsWith("https://")) {
            throw new Exception("Use an HTTPS API endpoint.");
        }

        if (model.isEmpty()) {
            throw new Exception("Enter a model name in Settings.");
        }

        String endpoint =
                base.replaceAll("/+$", "")
                + "/chat/completions";

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", input.getJSONArray("messages"));
        body.put("temperature", 0.8);

        HttpURLConnection conn =
                (HttpURLConnection)
                new URL(endpoint).openConnection();

        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);

        conn.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        if (!apiKey.isEmpty()) {
            conn.setRequestProperty(
                    "Authorization",
                    "Bearer " + apiKey
            );
        }

        byte[] bytes =
                body.toString().getBytes(StandardCharsets.UTF_8);

        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
        }

        int status = conn.getResponseCode();

        InputStream stream =
                status >= 200 && status < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream();

        StringBuilder text = new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     stream,
                                     StandardCharsets.UTF_8
                             )
                     )) {

            String line;

            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }

        conn.disconnect();

        JSONObject response =
                new JSONObject(text.toString());

        if (status < 200 || status >= 300) {
            String message =
                    response.optJSONObject("error") != null
                            ? response.getJSONObject("error")
                                .optString(
                                        "message",
                                        "HTTP " + status
                                )
                            : "HTTP " + status;

            throw new Exception(message);
        }

        String answer =
                response.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "");

        JSONObject result = new JSONObject();
        result.put("content", answer);

        return result;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        stopAudioPlayback();

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }

        ttsReady = false;

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        executor.shutdownNow();

        super.onDestroy();
    }
}
