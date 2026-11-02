# Capri Android port (starter)

This is a standalone Android companion prototype based on the supplied Capri Desktop v0.5.0 source archive.

## Current implementation
- Native Android shell using a WebView.
- Reuses selected Capri artwork from the original archive.
- Animated idle character presentation, status display, chat UI and settings sheet.
- OpenAI-compatible chat endpoint support, including providers such as OpenAI and OpenRouter.
- API key remains in JavaScript memory for the current app session. The app stores only endpoint and model in local storage.
- Native Java networking bridge avoids WebView cross-origin restrictions.

## Build
1. Install Android Studio (Ladybug or newer) and Android SDK Platform 35.
2. Open this folder in Android Studio.
3. Let Gradle sync.
4. Select a device or emulator running Android 8.0 (API 26) or newer.
5. Run the `app` configuration.

To make an APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## Configure chat
Open Settings in the app and enter:
- API Base URL, e.g. `https://api.openai.com/v1`
- Model, e.g. `gpt-4o-mini` (choose a model available to your provider)
- API key, if your provider requires one.

The app sends chat requests to `BASE_URL/chat/completions`. Only use a provider you trust. Your key is sent to that provider and is not saved by this prototype. The app requires HTTPS endpoints.

## Scope and limitations
This is a first Android port, not a feature-complete conversion of the Windows app. It does not include the original Electron overlay, Windows hooks, Claude Code/Codex/Antigravity monitoring, desktop window tracking, system tray, or original full animation state machine. Those features depend on desktop operating-system APIs and need Android-specific designs. This standalone version focuses on the animated companion and AI chat.

## License and artwork
The original project files and artwork are distributed under the terms in `LICENSE`. Review the license before modifying or redistributing this port. This project is intended as a personal, noncommercial build.
