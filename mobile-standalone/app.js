const STORAGE_KEY = "phone-api-chat-v2";
const LEGACY_STORAGE_KEY = "phone-api-chat-v1";

const defaultSettings = {
  baseUrl: "https://api.deepseek.com/v1",
  apiKey: "",
  model: "deepseek-v4-flash",
  thinkingEnabled: true,
  temperature: 0.7,
  maxTokens: 2048,
  systemPrompt: "你是一个有帮助、表达清晰的助手。"
};

let state = loadState();
let abortController = null;
let activeGeneration = false;
let autoFollowBottom = true;
let messageScrollIntent = false;
let messageScrollIntentTimer = null;

const $ = (selector) => document.querySelector(selector);

const els = {
  title: $("#conversationTitle"),
  messages: $("#messages"),
  empty: $("#emptyState"),
  composer: $("#composer"),
  prompt: $("#promptInput"),
  send: $("#sendButton"),
  history: $("#historySheet"),
  historyButton: $("#historyButton"),
  closeHistory: $("#closeHistoryButton"),
  newChat: $("#newChatButton"),
  newChatFromHistory: $("#newChatFromHistoryButton"),
  conversationList: $("#conversationList"),
  settings: $("#settingsSheet"),
  settingsButton: $("#settingsButton"),
  thinkingToggle: $("#thinkingToggle"),
  thinkingLabel: $("#thinkingLabel"),
  closeSettings: $("#closeSettingsButton"),
  save: $("#saveButton"),
  test: $("#testButton"),
  hint: $("#settingsHint"),
  baseUrl: $("#baseUrlInput"),
  apiKey: $("#apiKeyInput"),
  model: $("#modelInput"),
  temperature: $("#temperatureInput"),
  maxTokens: $("#maxTokensInput"),
  systemPrompt: $("#systemPromptInput")
};

init();

function init() {
  if (!state.activeConversationId) {
    createConversation({ render: false });
  }

  bindEvents();
  render();
  syncSettingsForm();
  syncThinkingToggle();
  autosize();

  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("./sw.js").then((registration) => {
      registration.update().catch(() => {});
    }).catch(() => {});
  }
}

function bindEvents() {
  els.composer.addEventListener("submit", async (event) => {
    event.preventDefault();
    await submit();
  });

  els.prompt.addEventListener("input", autosize);
  els.messages.addEventListener("wheel", markMessageScrollIntent, { passive: true });
  els.messages.addEventListener("touchmove", markMessageScrollIntent, { passive: true });
  els.messages.addEventListener("pointerdown", markMessageScrollIntent);
  els.messages.addEventListener("scroll", () => syncAutoFollow(els.messages));
  els.prompt.addEventListener("keydown", async (event) => {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      await submit();
    }
  });

  document.querySelectorAll("[data-prompt]").forEach((button) => {
    button.addEventListener("click", async () => {
      els.prompt.value = button.dataset.prompt || "";
      autosize();
      await submit();
    });
  });

  els.newChat.addEventListener("click", () => createConversation());
  els.newChatFromHistory.addEventListener("click", () => {
    createConversation();
    closeHistory();
  });

  els.historyButton.addEventListener("click", openHistory);
  els.closeHistory.addEventListener("click", closeHistory);
  els.history.addEventListener("click", (event) => {
    if (event.target === els.history) closeHistory();
  });

  els.settingsButton.addEventListener("click", openSettings);
  els.closeSettings.addEventListener("click", closeSettings);
  els.settings.addEventListener("click", (event) => {
    if (event.target === els.settings) closeSettings();
  });

  els.save.addEventListener("click", () => {
    saveSettingsFromForm();
    closeSettings();
  });

  els.test.addEventListener("click", testConnection);

  els.thinkingToggle.addEventListener("click", () => {
    state.settings.thinkingEnabled = !state.settings.thinkingEnabled;
    saveState();
    syncThinkingToggle();
  });
}

function loadState() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    if (Array.isArray(parsed.conversations)) {
      return {
        settings: { ...defaultSettings, ...(parsed.settings || {}) },
        conversations: parsed.conversations,
        activeConversationId: parsed.activeConversationId || parsed.conversations[0]?.id || null
      };
    }
  } catch {}

  try {
    const legacy = JSON.parse(localStorage.getItem(LEGACY_STORAGE_KEY) || "{}");
    const legacyMessages = Array.isArray(legacy.messages) ? legacy.messages : [];
    const conversation = {
      id: createId(),
      title: makeTitle(legacyMessages[0]?.content || "新对话"),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      messages: legacyMessages
    };

    return {
      settings: { ...defaultSettings, ...(legacy.settings || {}) },
      conversations: [conversation],
      activeConversationId: conversation.id
    };
  } catch {
    return {
      settings: { ...defaultSettings },
      conversations: [],
      activeConversationId: null
    };
  }
}

function saveState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function createConversation(options = {}) {
  const { render: shouldRender = true } = options;
  const conversation = {
    id: createId(),
    title: "新对话",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    messages: []
  };

  state.conversations.unshift(conversation);
  state.activeConversationId = conversation.id;
  saveState();

  if (shouldRender) {
    render();
    els.prompt.focus();
  }

  return conversation;
}

function getActiveConversation() {
  let conversation = state.conversations.find((item) => item.id === state.activeConversationId);
  if (!conversation) {
    conversation = createConversation({ render: false });
  }
  return conversation;
}

function render(keepAtBottom = true) {
  const conversation = getActiveConversation();
  els.messages.innerHTML = "";
  els.title.textContent = conversation.title || "新对话";
  renderConversationList();

  if (!conversation.messages.length) {
    els.messages.appendChild(els.empty);
    return;
  }

  for (const message of conversation.messages) {
    const article = document.createElement("article");
    article.className = `message ${message.role}`;
    article.dataset.id = message.id;

    const meta = document.createElement("div");
    meta.className = "meta";
    meta.textContent = message.role === "assistant" ? message.model || "Assistant" : "我";

    const content = document.createElement("div");
    content.className = "content";
    content.innerHTML = renderMarkdown(message.content || "");

    article.append(meta);
    if (message.role === "assistant" && message.reasoningContent) {
      article.append(createReasoningNode(message));
    }
    article.append(content);
    els.messages.appendChild(article);
  }

  if (keepAtBottom) scrollToBottom();
}

function createReasoningNode(message) {
  const panel = document.createElement("section");
  panel.className = `reasoning-panel${message.reasoningOpen ? " open" : ""}`;

  const toggle = document.createElement("button");
  toggle.className = "reasoning-toggle";
  toggle.type = "button";
  toggle.setAttribute("aria-expanded", String(!!message.reasoningOpen));
  toggle.innerHTML = `
    <span class="reasoning-chevron" aria-hidden="true"></span>
    <span>思考过程</span>
    <span class="reasoning-count">${message.reasoningContent.length} 字</span>
  `;
  toggle.addEventListener("click", () => {
    message.reasoningOpen = !message.reasoningOpen;
    updateReasoning(message, false);
    saveState();
  });

  const body = document.createElement("div");
  body.className = "reasoning-content";
  body.innerHTML = renderMarkdown(message.reasoningContent || "");

  panel.append(toggle, body);
  return panel;
}

function renderConversationList() {
  els.conversationList.innerHTML = "";

  for (const conversation of state.conversations) {
    const item = document.createElement("div");
    item.className = `conversation-item${conversation.id === state.activeConversationId ? " active" : ""}`;

    const button = document.createElement("button");
    button.type = "button";
    button.className = "conversation-button";
    button.textContent = conversation.title || "新对话";
    button.addEventListener("click", () => {
      state.activeConversationId = conversation.id;
      saveState();
      render();
      closeHistory();
    });

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "delete-button";
    remove.setAttribute("aria-label", "删除对话");
    remove.textContent = "×";
    remove.addEventListener("click", (event) => {
      event.stopPropagation();
      deleteConversation(conversation.id);
    });

    item.append(button, remove);
    els.conversationList.appendChild(item);
  }
}

function deleteConversation(id) {
  state.conversations = state.conversations.filter((item) => item.id !== id);
  if (state.activeConversationId === id) {
    state.activeConversationId = state.conversations[0]?.id || null;
  }
  if (!state.activeConversationId) {
    createConversation({ render: false });
  }
  saveState();
  render();
}

async function submit() {
  const text = els.prompt.value.trim();
  if (!text || abortController) return;

  if (!state.settings.apiKey) {
    openSettings();
    els.hint.textContent = "先填写 API Key，再发送消息。";
    return;
  }

  const settings = { ...state.settings };
  const conversation = getActiveConversation();
  const userMessage = {
    id: createId(),
    role: "user",
    content: text,
    createdAt: new Date().toISOString()
  };
    const assistantMessage = {
    id: createId(),
    role: "assistant",
    model: settings.model,
    content: "",
    reasoningContent: "",
    reasoningOpen: false,
    thinkingEnabled: settings.thinkingEnabled,
    createdAt: new Date().toISOString()
  };

  if (!conversation.messages.length) {
    conversation.title = makeTitle(text);
  }

  conversation.messages.push(userMessage, assistantMessage);
  conversation.updatedAt = new Date().toISOString();
  moveConversationToTop(conversation.id);
  els.prompt.value = "";
  autosize();
  setBusy(true);
  saveState();
  activeGeneration = true;
  autoFollowBottom = true;
  render(true);

  abortController = new AbortController();

  try {
    const response = await fetch(`${settings.baseUrl.replace(/\/+$/, "")}/chat/completions`, {
      method: "POST",
      signal: abortController.signal,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${settings.apiKey}`
      },
      body: JSON.stringify({
        model: settings.model,
        messages: buildMessages(conversation, settings.systemPrompt),
        temperature: Number(settings.temperature),
        max_tokens: Number(settings.maxTokens),
        thinking: { type: settings.thinkingEnabled !== false ? "enabled" : "disabled" },
        stream: true
      })
    });

    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error?.message || payload.error || response.statusText);
    }

    await readOpenAIStream(response, (type, token) => {
      if (type === "reasoning") {
        assistantMessage.reasoningContent += token;
        updateReasoning(assistantMessage);
        return;
      }
      assistantMessage.content += token;
      updateMessage(assistantMessage);
    });
  } catch (error) {
    assistantMessage.content =
      error.name === "AbortError"
        ? "已停止生成。"
        : `请求失败：${formatError(error)}`;
    updateMessage(assistantMessage);
  } finally {
    abortController = null;
    setBusy(false);
    conversation.updatedAt = new Date().toISOString();
    saveState();
    const keepAtBottom = autoFollowBottom;
    activeGeneration = false;
    render(keepAtBottom);
  }
}

function buildMessages(conversation, systemPrompt) {
  const messages = conversation.messages
    .filter((message) => message.content)
    .map((message) => ({ role: message.role, content: message.content }));

  return systemPrompt?.trim()
    ? [{ role: "system", content: systemPrompt.trim() }, ...messages]
    : messages;
}

function moveConversationToTop(id) {
  const index = state.conversations.findIndex((conversation) => conversation.id === id);
  if (index <= 0) return;
  const [conversation] = state.conversations.splice(index, 1);
  state.conversations.unshift(conversation);
}

async function readOpenAIStream(response, onToken) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() || "";

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed.startsWith("data:")) continue;

      const data = trimmed.slice(5).trim();
      if (data === "[DONE]") return;

      const payload = JSON.parse(data);
      const delta = payload.choices?.[0]?.delta || {};
      const reasoning = delta.reasoning_content || "";
      const token = delta.content || "";
      if (reasoning) onToken("reasoning", reasoning);
      if (token) onToken("content", token);
    }
  }
}

function updateMessage(message) {
  const node = els.messages.querySelector(`[data-id="${message.id}"] .content`);
  if (node) {
    node.innerHTML = renderMarkdown(message.content || "");
    if (autoFollowBottom) scrollToBottom();
  }
}

function updateReasoning(message, shouldScroll = true) {
  if (!message.reasoningContent) return;

  const article = els.messages.querySelector(`[data-id="${message.id}"]`);
  if (!article) {
    render();
    return;
  }

  let panel = article.querySelector(".reasoning-panel");
  const content = article.querySelector(".content");
  if (!panel) {
    panel = createReasoningNode(message);
    content?.before(panel);
  }

  panel.classList.toggle("open", !!message.reasoningOpen);
  const toggle = panel.querySelector(".reasoning-toggle");
  const count = panel.querySelector(".reasoning-count");
  const body = panel.querySelector(".reasoning-content");
  toggle?.setAttribute("aria-expanded", String(!!message.reasoningOpen));
  if (count) count.textContent = `${message.reasoningContent.length} 字`;
  if (body) body.innerHTML = renderMarkdown(message.reasoningContent || "");
  if (shouldScroll && autoFollowBottom) scrollToBottom();
}

async function testConnection() {
  saveSettingsFromForm();
  els.hint.textContent = "正在测试...";

  try {
    const response = await fetch(`${state.settings.baseUrl.replace(/\/+$/, "")}/models`, {
      headers: {
        Authorization: `Bearer ${state.settings.apiKey}`
      }
    });

    if (!response.ok) {
      const payload = await response.json().catch(() => ({}));
      throw new Error(payload.error?.message || payload.error || response.statusText);
    }

    const payload = await response.json();
    const count = Array.isArray(payload.data) ? payload.data.length : 0;
    els.hint.textContent = `直连成功，获取到 ${count} 个模型。`;
  } catch (error) {
    els.hint.textContent = `直连失败：${formatError(error)}`;
  }
}

function syncSettingsForm() {
  els.baseUrl.value = state.settings.baseUrl;
  els.apiKey.value = state.settings.apiKey;
  els.model.value = state.settings.model;
  els.temperature.value = state.settings.temperature;
  els.maxTokens.value = state.settings.maxTokens;
  els.systemPrompt.value = state.settings.systemPrompt;
  syncThinkingToggle();
}

function saveSettingsFromForm() {
  state.settings = {
    baseUrl: els.baseUrl.value.trim() || defaultSettings.baseUrl,
    apiKey: els.apiKey.value.trim(),
    model: els.model.value,
    thinkingEnabled: state.settings.thinkingEnabled !== false,
    temperature: Number(els.temperature.value || defaultSettings.temperature),
    maxTokens: Number(els.maxTokens.value || defaultSettings.maxTokens),
    systemPrompt: els.systemPrompt.value
  };

  saveState();
  els.hint.textContent = "已保存。";
  syncThinkingToggle();
}

function syncThinkingToggle() {
  const enabled = state.settings.thinkingEnabled !== false;
  state.settings.thinkingEnabled = enabled;
  els.thinkingToggle.classList.toggle("active", enabled);
  els.thinkingToggle.setAttribute("aria-pressed", String(enabled));
  els.thinkingLabel.textContent = enabled ? "思考" : "快答";
}

function openHistory() {
  renderConversationList();
  els.history.hidden = false;
}

function closeHistory() {
  els.history.hidden = true;
}

function openSettings() {
  syncSettingsForm();
  els.settings.hidden = false;
}

function closeSettings() {
  els.settings.hidden = true;
  els.prompt.focus();
}

function setBusy(isBusy) {
  els.send.disabled = isBusy;
  els.prompt.disabled = isBusy;
  els.newChat.disabled = isBusy;
}

function autosize() {
  els.prompt.style.height = "auto";
  els.prompt.style.height = `${Math.min(els.prompt.scrollHeight, 150)}px`;
}

function renderMarkdown(text) {
  const jsonImages = renderJsonImages(text);
  if (jsonImages) return `<p>已生成图片</p>${jsonImages}`;

  const escaped = escapeHtml(text || "");
  const tokens = [];
  let html = escaped.replace(/```([\s\S]*?)```/g, (_, code) => {
    const token = `@@CODE_${tokens.length}@@`;
    tokens.push(`<pre><code>${code.trim()}</code></pre>`);
    return token;
  });

  html = html
    .replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, (_, alt, url) => imageToken(tokens, url, alt))
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");

  return html
    .split(/\n{2,}/)
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      if (/^@@CODE_\d+@@$/.test(part)) return part;
      if (isImageUrl(part)) return imageToken(tokens, part, "生成图片");
      return `<p>${part.replace(/\n/g, "<br>")}</p>`;
    })
    .join("")
    .replace(/@@CODE_(\d+)@@/g, (_, index) => tokens[Number(index)] || "");
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function imageToken(tokens, url, alt = "") {
  const token = `@@CODE_${tokens.length}@@`;
  tokens.push(imageFigureHtml(url, alt));
  return token;
}

function imageFigureHtml(url, alt = "") {
  const cleanUrl = sanitizeImageUrl(url);
  if (!cleanUrl) return "";
  const cleanAlt = escapeHtml(String(alt || "生成图片")).replaceAll("\n", " ");
  return `
    <figure class="generated-image">
      <img src="${cleanUrl}" alt="${cleanAlt}" loading="lazy" />
      <figcaption>${cleanAlt}</figcaption>
    </figure>
  `;
}

function renderJsonImages(text) {
  const clean = String(text || "").trim();
  if (!clean.startsWith("{") && !clean.startsWith("[")) return "";
  try {
    const refs = [];
    const seen = new Set();
    collectJsonImages(JSON.parse(clean), refs, seen);
    return refs.map((src) => imageFigureHtml(src, "生成图片")).join("");
  } catch {
    return "";
  }
}

function collectJsonImages(node, refs, seen) {
  if (!node || typeof node !== "object") return;
  if (Array.isArray(node)) {
    node.forEach((item) => collectJsonImages(item, refs, seen));
    return;
  }
  for (const [key, value] of Object.entries(node)) {
    const lower = key.toLowerCase();
    let src = "";
    if (typeof value === "string" && lower === "b64_json") src = `data:image/png;base64,${value}`;
    if (typeof value === "string" && lower.includes("url") && /^https?:\/\//i.test(value)) src = value;
    if (src && !seen.has(src)) {
      seen.add(src);
      refs.push(src);
    }
    collectJsonImages(value, refs, seen);
  }
}

function sanitizeImageUrl(url) {
  const clean = String(url || "").trim().replaceAll("&amp;", "&");
  if (!clean) return "";
  if (/^data:image\/[a-z0-9.+-]+;base64,/i.test(clean)) return clean;
  if (/^https?:\/\//i.test(clean)) return escapeHtml(clean);
  return "";
}

function isImageUrl(value) {
  const clean = String(value || "").trim();
  return /^data:image\/[a-z0-9.+-]+;base64,/i.test(clean)
    || /^https?:\/\/\S+\.(png|jpe?g|webp|gif)(\?\S*)?$/i.test(clean);
}

function scrollToBottom() {
  requestAnimationFrame(() => {
    els.messages.scrollTop = els.messages.scrollHeight;
  });
}

function markMessageScrollIntent() {
  if (!activeGeneration) return;
  messageScrollIntent = true;
  clearTimeout(messageScrollIntentTimer);
  messageScrollIntentTimer = setTimeout(() => {
    messageScrollIntent = false;
  }, 250);
}

function syncAutoFollow(container) {
  if (!activeGeneration) return;
  if (isNearBottom(container)) {
    autoFollowBottom = true;
  } else if (messageScrollIntent) {
    autoFollowBottom = false;
  }
}

function isNearBottom(container) {
  return container.scrollHeight - container.scrollTop - container.clientHeight < 32;
}

function formatError(error) {
  const message = error?.message || String(error);
  if (message === "Failed to fetch") {
    return "浏览器可能被 CORS 限制拦截。纯网页直连不稳定时，需要改用 APK 或 Termux 本地后端。";
  }
  return message;
}

function makeTitle(text) {
  const clean = String(text || "").replace(/\s+/g, " ").trim();
  return clean.length > 18 ? `${clean.slice(0, 18)}...` : clean || "新对话";
}

function createId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `id-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}
