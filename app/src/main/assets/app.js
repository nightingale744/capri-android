const $ = id => document.getElementById(id);
const messagesEl = $("messages");
let history = [];
let pending = false;
let replyCounter = 0;
const systemPrompt = "You are Capri, a lively, witty cyberpunk-inspired desktop companion. Speak warmly and concisely. You are an AI character, not a human. Do not claim to control the phone or see its activity. Keep the user safe.";

$("settingsOpen").onclick = () => $("settings").classList.remove("hidden");
$("settingsClose").onclick = () => $("settings").classList.add("hidden");
$("settings").addEventListener("click", e => { if (e.target === $("settings")) $("settings").classList.add("hidden"); });
$("baseUrl").value = localStorage.getItem("capri.baseUrl") || "https://api.openai.com/v1";
$("model").value = localStorage.getItem("capri.model") || "gpt-4o-mini";

$("saveSettings").onclick = () => {
  localStorage.setItem("capri.baseUrl", $("baseUrl").value.trim());
  localStorage.setItem("capri.model", $("model").value.trim());
  $("speech").textContent = "Link settings saved. Your API key stays in memory until you close Capri.";
  $("settings").classList.add("hidden");
};
$("testSettings").onclick = () => {
  $("speech").textContent = "Connection test sends a short message to your selected provider.";
  $("prompt").value = "Say hello in one short sentence.";
  $("settings").classList.add("hidden");
  $("chatForm").requestSubmit();
};

function addMessage(role, text) {
  const div = document.createElement("div");
  div.className = "bubble " + (role === "user" ? "user-msg" : "capri-msg");
  const label = document.createElement("small");
  label.textContent = role === "user" ? "YOU" : "CAPRI";
  div.appendChild(label);
  div.appendChild(document.createTextNode(text));
  messagesEl.appendChild(div);
  messagesEl.scrollTop = messagesEl.scrollHeight;
}
function setBusy(value) {
  pending = value;
  $("send").disabled = value;
  $("send").textContent = value ? "..." : "SEND ↗";
  $("mood").textContent = value ? "ONLINE // THINKING" : "ONLINE // IDLE";
  $("character").classList.toggle("talking", value);
}
function sendNative(payload) {
  return new Promise((resolve, reject) => {
    const id = "r" + (++replyCounter);
    window.onCapriNativeReply = (replyId, raw) => {
      if (replyId !== id) return;
      try {
        const parsed = JSON.parse(raw);
        if (parsed.error) reject(new Error(parsed.error));
        else resolve(parsed.content || "");
      } catch (e) { reject(e); }
    };
    if (!window.CapriNative) return reject(new Error("Android bridge unavailable."));
    CapriNative.sendChat(JSON.stringify(payload), id);
  });
}
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
    $("speech").textContent = "I need an API endpoint and model first. Open Settings and add them.";
    return;
  }
  $("prompt").value = "";
  addMessage("user", text);
  history.push({role:"user",content:text});
  if (history.length > 30) history = history.slice(-30);
  setBusy(true);
  $("speech").textContent = "Gimme a sec, choom...";
  try {
    const content = await sendNative({
      baseUrl, model, apiKey,
      messages: [{role:"system",content:systemPrompt}, ...history]
    });
    history.push({role:"assistant",content});
    addMessage("assistant", content);
    $("speech").textContent = content.length > 105 ? content.slice(0,102) + "..." : content;
  } catch (err) {
    history.pop();
    const message = "Link error: " + err.message;
    addMessage("assistant", message);
    $("speech").textContent = "The link's acting up. Check Settings and your connection.";
  } finally { setBusy(false); $("prompt").focus(); }
});
