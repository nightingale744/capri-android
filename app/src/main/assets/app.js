
const $ = id => document.getElementById(id);

const messagesEl = $("messages");
const capriImage = $("capri");

let history = [];
let pending = false;
let replyCounter = 0;
let expressionTimer = null;

let voiceEnabled =
  localStorage.getItem("capri.voiceEnabled") !== "false";

const systemPrompt =
  "You are Capri, a lively, witty cyberpunk-inspired desktop companion. Speak warmly and concisely. You are an AI character, not a human. Do not claim to control the phone or see its activity. Keep the user safe.";

const expressions = {
  normal: "capri/capri-face.png",
  laugh: "capri/face-laugh.png",
  smug: "capri/face-smug.png"
};

// VOICE CONTROL

const voiceButton = document.createElement("button");

voiceButton.id = "voiceToggle";
voiceButton.className = "iconbtn";
voiceButton.type = "button";
voiceButton.style.fontSize = "12px";
voiceButton.style.padding = "8px 12px";

$("chat-head")?.appendChild(voiceButton);

function updateVoiceButton() {
  voiceButton.textContent = voiceEnabled ? "VOICE ON" : "VOICE OFF";
  voiceButton.setAttribute(
    "aria-label",
    voiceEnabled ? "Mute Capri" : "Enable Capri voice"
  );
}

function speak(text) {
  if (!voiceEnabled || !window.CapriNative) return;

  try {
    window.CapriNative.speak(text);
  } catch (error) {
    console.error("Capri voice error:", error);
  }
}

function stopSpeaking() {
  if (!window.CapriNative) return;

  try {
    window.CapriNative.stopSpeaking();
  } catch (error) {
    console.error("Capri voice error:", error);
  }
}

voiceButton.onclick = () => {
  voiceEnabled = !voiceEnabled;

  localStorage.setItem(
    "capri.voiceEnabled",
    String(voiceEnabled)
  );

  if (!voiceEnabled) stopSpeaking();

  updateVoiceButton();
};

updateVoiceButton();

// EXPRESSIONS

function setExpression(expression) {
  const imagePath = expressions[expression];

  if (!imagePath || !capriImage) return;

  clearTimeout(expressionTimer);

  capriImage.style.transition = "opacity 180ms ease";
  capriImage.style.opacity = "0";

  setTimeout(() => {
    capriImage.src = imagePath;
    capriImage.style.opacity = "1";
  }, 180);

  if (expression !== "normal") {
    expressionTimer = setTimeout(() => {
      setExpression("normal");
    }, 5000);
  }
}

function detectExpression(text) {
  const message = text.toLowerCase();

  const laughWords = [
    "haha", "hahaha", "lol", "lmao",
    "😂", "🤣", "that's funny", "thats funny"
  ];

  const smugWords = [
    "obviously", "told you", "easy",
    "exactly", "of course", "correct",
    "naturally", "indeed"
  ];

  if (laughWords.some(word => message.includes(word))) {
    return "laugh";
  }

  if (smugWords.some(word => message.includes(word))) {
    return "smug";
  }

  return "normal";
}

// SETTINGS

$("settingsOpen").onclick = () =>
  $("settings").classList.remove("hidden");

$("settingsClose").onclick = () =>
  $("settings").classList.add("hidden");

$("settings").addEventListener("click", e => {
  if (e.target === $("settings")) {
    $("settings").classList.add("hidden");
  }
});

$("baseUrl").value =
  localStorage.getItem("capri.baseUrl") ||
  "https://api.openai.com/v1";

$("model").value =
  localStorage.getItem("capri.model") ||
  "gpt-4o-mini";

$("saveSettings").onclick = () => {
  localStorage.setItem(
    "capri.baseUrl",
    $("baseUrl").value.trim()
  );

  localStorage.setItem(
    "capri.model",
    $("model").value.trim()
  );

  $("speech").textContent =
    "Link settings saved. Your API key stays in memory until you close Capri.";

  $("settings").classList.add("hidden");
};

$("testSettings").onclick = () => {
  $("speech").textContent =
    "Connection test sends a short message to your selected provider.";

  $("prompt").value =
    "Say hello in one short sentence.";

  $("settings").classList.add("hidden");
  $("chatForm").requestSubmit();
};

// CHAT MESSAGES

function addMessage(role, text) {
  const div = document.createElement("div");

  div.className =
    "bubble " +
    (role === "user" ? "user-msg" : "capri-msg");

  const label = document.createElement("small");

  label.textContent =
    role === "user" ? "YOU" : "CAPRI";

  div.appendChild(label);
  div.appendChild(document.createTextNode(text));

  messagesEl.appendChild(div);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}

// BUSY STATUS

function setBusy(value) {
  pending = value;

  $("send").disabled = value;
  $("send").textContent =
    value ? "..." : "SEND ↗";

  $("mood").textContent =
    value ? "ONLINE // THINKING" : "ONLINE // IDLE";
}

// ANDROID CHAT CONNECTION

function sendNative(payload) {
  return new Promise((resolve, reject) => {
    const id = "r" + (++replyCounter);

    window.onCapriNativeReply = (replyId, raw) => {
      if (replyId !== id) return;

      try {
        const parsed = JSON.parse(raw);

        if (parsed.error) {
          reject(new Error(parsed.error));
        } else {
          resolve(parsed.content || "");
        }
      } catch (error) {
        reject(error);
      }
    };

    if (!window.CapriNative) {
      reject(new Error("Android bridge unavailable."));
      return;
    }

    window.CapriNative.sendChat(
      JSON.stringify(payload),
      id
    );
  });
}

// MAIN CHAT

$("chatForm").addEventListener("submit", async e => {
  e.preventDefault();

  if (pending) return;

  const text = $("prompt").value.trim();

  if (!text) return;

  const baseUrl = $("baseUrl").value.trim();
  const model = $("model").value.trim();
  const apiKey = $("apiKey").value.trim();

  if (!baseUrl || !model) {
    $("settings").classList.remove("hidden");

    $("speech").textContent =
      "I need an API endpoint and model first. Open Settings and add them.";

    return;
  }

  $("prompt").value = "";

  addMessage("user", text);

  history.push({
    role: "user",
    content: text
  });

  if (history.length > 30) {
    history = history.slice(-30);
  }

  setBusy(true);

  $("speech").textContent =
    "Gimme a sec, choom...";

  try {
    const content = await sendNative({
      baseUrl,
      model,
      apiKey,
      messages: [
        {
          role: "system",
          content: systemPrompt
        },
        ...history
      ]
    });

    history.push({
      role: "assistant",
      content
    });

    addMessage("assistant", content);

    $("speech").textContent =
      content.length > 105
        ? content.slice(0, 102) + "..."
        : content;

    setExpression(detectExpression(content));

    speak(content);

  } catch (error) {
    history.pop();

    const message =
      "Link error: " + error.message;

    addMessage("assistant", message);

    $("speech").textContent =
      "The link's acting up. Check Settings and your connection.";

    setExpression("normal");

  } finally {
    setBusy(false);
    $("prompt").focus();
  }
});
