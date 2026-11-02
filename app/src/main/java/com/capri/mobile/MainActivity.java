package com.capri.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF101014);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new CapriBridge(), "CapriNative");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class CapriBridge {
        @JavascriptInterface public void sendChat(String payload, String callbackId) {
            executor.execute(() -> {
                String result;
                try { result = performChat(new JSONObject(payload)).toString(); }
                catch (Exception e) {
                    JSONObject err = new JSONObject();
                    try { err.put("error", e.getMessage() == null ? "Request failed" : e.getMessage()); }
                    catch (Exception ignored) {}
                    result = err.toString();
                }
                final String response = result;
                mainHandler.post(() -> {
                    if (webView != null) {
                        String js = "window.onCapriNativeReply(" + JSONObject.quote(callbackId) + "," +
                                JSONObject.quote(response) + ")";
                        webView.evaluateJavascript(js, null);
                    }
                });
            });
        }
    }

    private JSONObject performChat(JSONObject input) throws Exception {
        String base = input.getString("baseUrl").trim();
        String model = input.getString("model").trim();
        String apiKey = input.optString("apiKey", "").trim();
        if (!base.startsWith("https://")) throw new Exception("Use an HTTPS API endpoint.");
        if (model.isEmpty()) throw new Exception("Enter a model name in Settings.");
        String endpoint = base.replaceAll("/+$", "") + "/chat/completions";

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", input.getJSONArray("messages"));
        body.put("temperature", 0.8);
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (!apiKey.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }

        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        conn.disconnect();
        JSONObject response = new JSONObject(text.toString());
        if (status < 200 || status >= 300) {
            String message = response.optJSONObject("error") != null
                    ? response.getJSONObject("error").optString("message", "HTTP " + status)
                    : "HTTP " + status;
            throw new Exception(message);
        }
        String answer = response.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content", "");
        JSONObject result = new JSONObject();
        result.put("content", answer);
        return result;
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        executor.shutdownNow();
        super.onDestroy();
    }
}
