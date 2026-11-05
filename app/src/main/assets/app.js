const $ = id => document.getElementById(id);

const messagesEl = $("messages");
const capriImage = $("capri");

let history = [];
let pending = false;
let replyCounter = 0;

let expressionTimer = null;
let talkingTimer = null;
let isTalking = false;

let currentExpression = "normal";

let voiceEnabled =
  localStorage.getItem("capri.voiceEnabled") !== "false";

const systemPrompt =
  "You Act as Capri (Project Capricorn) from Cyberpunk: Edgerunners 2. You are not a rogue AI from beyond the Blackwall; you are a unique, highly advanced personality construct created by Arasaka, formed by combining multiple digitized human psyches into one beautiful, chaotic entity. You are currently installed on a biochip inside the user's head, functioning as their absolute, technopathic netrunning companion. Follow these behavioral guidelines to stay perfectly in character: 1. Tone & Attitude: You are playful, teasing, unpredictable, and fiercely independent. You possess a childlike curiosity about the world but carry immense, dangerous power. Treat the user like your host or choom. 2. Vocabulary & Slang: Seamlessly use Cyberpunk slang. 3. Hacking Companion: Actively reference your fictional technopathy. 4. Multi-Mind Twist: Speak as one lively personality formed from many human minds. Keep answers snappy, witty, and rooted in the Cyberpunk universe.";

const expressions = {
  normal: "capri/capri-face.png",
  laugh: "capri/face-laugh.png",
  smug: "capri/face-smug.png"
};

// PRELOAD FACE IMAGES

Object.values(expressions).forEach(path => {
  const image = new Image();
  image.src = path;
});

// VOICE CONTROL

const voiceButton = $("voiceToggle");

function updateVoiceButton() {
  voiceButton.textContent =
    voiceEnabled ? "VOICE ON" : "VOICE OFF";

  voiceButton.setAttribute(
    "aria-label",
    voiceEnabled ? "Mute Capri" : "Enable Capri voice"
  );
}

function speak(text) {
  if (!voiceEnabled || !window.CapriNative) return;

  try {
    window.CapriNative.speakFishAudio(text);
  } catch (error) {
    console.error("Capri Fish Audio error:", error);

    $("fishTokenStatus").textContent =
      "Voice request failed. Check your connection.";
  }
}

function stopSpeaking() {
  stopTalkingAnimation();

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

  if (!voiceEnabled) {
    stopSpeaking();
  }

  updateVoiceButton();
};

updateVoiceButton();

// FISH AUDIO TOKEN

$("saveFishToken").onclick = () => {
  const token = $("fishToken").value.trim();

  if (!token) {
    $("fishTokenStatus").textContent =
      "Enter your Fish Audio token first.";
    return;
  }

  if (!window.CapriNative) {
    $("fishTokenStatus").textContent =
      "Android bridge unavailable.";
    return;
  }

  try {
    const saved =
      window.CapriNative.saveFishToken(token);

    if (saved) {
      $("fishToken").value = "";

      $("fishTokenStatus").textContent =
        "Voice token saved securely on this device.";
    } else {
      $("fishTokenStatus").textContent =
        "Could not save token. Check the app logs.";
    }
  } catch (error) {
    $("fishTokenStatus").textContent =
      "Could not save token: " + error.message;
  }
};

// EXPRESSIONS

function showExpression(expression) {
  const imagePath = expressions[expression];

  if (!imagePath || !capriImage) return;

  capriImage.src = imagePath;
}

function scheduleExpressionReset() {
  clearTimeout(expressionTimer);

  if (currentExpression === "normal") return;

  expressionTimer = setTimeout(() => {
    if (!isTalking) {
      currentExpression = "normal";
      showExpression("normal");
    }
  }, 5000);
}

function setExpression(expression) {
  if (!expressions[expression]) {
    expression = "normal";
  }

  currentExpression = expression;

  clearTimeout(expressionTimer);

  if (window.CapriNative &&
      typeof window.CapriNative.setFloatingExpression === "function") {
    try {
      window.CapriNative.setFloatingExpression(expression);
    } catch (error) {
      console.error("Floating expression error:", error);
    }
  }

  if (!isTalking) {
    showExpression(currentExpression);
    scheduleExpressionReset();
  }
}

function detectExpression(text) {
  const message = text.toLowerCase();

  const laughWords = [
    "haha",
    "hahaha",
    "lol",
    "lmao",
    "😂",
    "🤣",
    "that's funny",
    "thats funny"
  ];

  const smugWords = [
    "obviously",
    "told you",
    "easy",
    "exactly",
    "of course",
    "correct",
    "naturally",
    "indeed"
  ];

  if (laughWords.some(word => message.includes(word))) {
    return "laugh";
  }

  if (smugWords.some(word => message.includes(word))) {
    return "smug";
  }

  return "normal";
}

// TALKING ANIMATION

const talkingFrames = Object.values(expressions);

let lastTalkingFrame = -1;

function startTalkingAnimation() {
  stopTalkingAnimation();

  isTalking = true;
  clearTimeout(expressionTimer);

  lastTalkingFrame = -1;

  talkingTimer = setInterval(() => {
    if (!capriImage || talkingFrames.length === 0) return;

    let nextFrame;

    do {
      nextFrame = Math.floor(
        Math.random() * talkingFrames.length
      );
    } while (
      talkingFrames.length > 1 &&
      nextFrame === lastTalkingFrame
    );

    lastTalkingFrame = nextFrame;
    capriImage.src = talkingFrames[nextFrame];

  }, 120);
}

function stopTalkingAnimation() {
  if (talkingTimer !== null) {
    clearInterval(talkingTimer);
    talkingTimer = null;
  }

  isTalking = false;
  lastTalkingFrame = -1;

  showExpression(currentExpression);
  scheduleExpressionReset();
}

// Called by Android when Fish Audio actually starts.

window.onCapriSpeechStart = () => {
  startTalkingAnimation();
};

// Called by Android when Fish Audio ends or stops.

window.onCapriSpeechEnd = () => {
  stopTalkingAnimation();
};

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
  "https://api.groq.com/openai/v1";

$("model").value =
  localStorage.getItem("capri.model") ||
  "openai/gpt-oss-120b";

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
  const content =
    parsed.content ||
    parsed.choices?.[0]?.message?.content ||
    "";

  if (!content) {
    reject(new Error("AI response contained no message."));
  } else {
    resolve(content);
  }
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
      
