const STORAGE_KEY = "local-api-chat-state-v1";

const defaults = {
  settings: {
    provider: "openai",
    baseUrl: "https://api.deepseek.com/v1",
    apiKey: "",
    model: "deepseek-v4-flash",
    thinkingEnabled: true,
    temperature: 0.7,
    maxTokens: 2048,
    stream: true,
    systemPrompt: "你是一个有帮助、表达清晰的助手。"
  },
  conversations: [],
  activeConversationId: null,
  models: ["deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"]
};

const state = loadState();
let abortController = null;
let activeGeneration = false;
let autoFollowBottom = true;
let messageScrollIntent = false;
let messageScrollIntentTimer = null;

const elements = {
  sidebar: document.querySelector("#sidebar"),
  sidebarToggle: document.querySelector("#sidebarToggle"),
  newChatButton: document.querySelector("#newChatButton"),
  conversationList: document.querySelector("#conversationList"),
  chatTitle: document.querySelector("#chatTitle"),
  statusText: document.querySelector("#statusText"),
  messageArea: document.querySelector("#messageArea"),
  emptyState: document.querySelector("#emptyState"),
  composer: document.querySelector("#composer"),
  promptInput: document.querySelector("#promptInput"),
  sendButton: document.querySelector("#sendButton"),
  stopButton: document.querySelector("#stopButton"),
  settingsButton: document.querySelector("#settingsButton"),
  settingsModal: document.querySelector("#settingsModal"),
  closeSettingsButton: document.querySelector("#closeSettingsButton"),
  saveSettingsButton: document.querySelector("#saveSettingsButton"),
  fetchModelsButton: document.querySelector("#fetchModelsButton"),
  providerInput: document.querySelector("#providerInput"),
  baseUrlInput: document.querySelector("#baseUrlInput"),
  apiKeyInput: document.querySelector("#apiKeyInput"),
  modelInput: document.querySelector("#modelInput"),
  modelOptions: document.querySelector("#modelOptions"),
  modelQuickSelect: document.querySelector("#modelQuickSelect"),
  thinkingToggle: document.querySelector("#thinkingToggle"),
  thinkingLabel: document.querySelector("#thinkingLabel"),
  temperatureInput: document.querySelector("#temperatureInput"),
  maxTokensInput: document.querySelector("#maxTokensInput"),
  streamInput: document.querySelector("#streamInput"),
  systemPromptInput: document.querySelector("#systemPromptInput")
};

init();

function init() {
  if (!state.activeConversationId) {
    createConversation(false);
  }

  bindEvents();
  syncSettingsForm();
  renderModels();
  syncThinkingToggle();
  renderConversations();
  renderMessages();
  setStatus("准备就绪");
  autosizeTextarea();
  elements.promptInput.focus();
}

function bindEvents() {
  elements.composer.addEventListener("submit", async (event) => {
    event.preventDefault();
    await submitPrompt();
  });

  elements.promptInput.addEventListener("input", autosizeTextarea);
  elements.promptInput.addEventListener("keydown", async (event) => {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      await submitPrompt();
    }
  });

  elements.stopButton.addEventListener("click", () => {
    if (abortController) {
      abortController.abort();
      setStatus("已停止");
      setBusy(false);
    }
  });

  elements.newChatButton.addEventListener("click", () => {
    createConversation();
    closeSidebarOnMobile();
  });

  elements.sidebarToggle.addEventListener("click", () => {
    elements.sidebar.classList.toggle("open");
  });

  elements.settingsButton.addEventListener("click", openSettings);
  elements.closeSettingsButton.addEventListener("click", closeSettings);
  elements.settingsModal.addEventListener("click", (event) => {
    if (event.target === elements.settingsModal) closeSettings();
  });

  elements.saveSettingsButton.addEventListener("click", () => {
    saveSettingsFromForm();
    closeSettings();
  });

  elements.fetchModelsButton.addEventListener("click", fetchModels);
  elements.messageArea.addEventListener("wheel", markMessageScrollIntent, { passive: true });
  elements.messageArea.addEventListener("touchmove", markMessageScrollIntent, { passive: true });
  elements.messageArea.addEventListener("pointerdown", markMessageScrollIntent);
  elements.messageArea.addEventListener("scroll", () => syncAutoFollow(elements.messageArea));

  elements.modelQuickSelect.addEventListener("change", () => {
    state.settings.model = elements.modelQuickSelect.value;
    state.models = uniqueModels([state.settings.model, ...state.models]);
    saveState();
    syncSettingsForm();
    renderModels();
  });

  elements.thinkingToggle.addEventListener("click", () => {
    state.settings.thinkingEnabled = !state.settings.thinkingEnabled;
    saveState();
    syncThinkingToggle();
    setStatus(state.settings.thinkingEnabled ? "思考模式已开启" : "快答模式已开启");
  });

  elements.providerInput.addEventListener("change", () => {
    if (elements.providerInput.value === "anthropic") {
      elements.baseUrlInput.value = "";
      if (!elements.modelInput.value || elements.modelInput.value.startsWith("gpt-")) {
        elements.modelInput.value = "claude-3-5-sonnet-latest";
      }
    } else if (!elements.baseUrlInput.value) {
      elements.baseUrlInput.value = "https://api.deepseek.com/v1";
      if (!elements.modelInput.value || elements.modelInput.value.startsWith("claude-")) {
        elements.modelInput.value = "deepseek-v4-flash";
      }
    }
  });

  document.querySelectorAll("[data-prompt]").forEach((button) => {
    button.addEventListener("click", async () => {
      elements.promptInput.value = button.dataset.prompt || "";
      autosizeTextarea();
      elements.promptInput.focus();
      await submitPrompt();
    });
  });
}

function loadState() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    const settings = { ...defaults.settings, ...(parsed.settings || {}) };
    const legacyDefaultModels = ["gpt-4o-mini", "gpt-4o", "o3-mini"];
    let storedModels = Array.isArray(parsed.models) ? parsed.models : [];

    if (
      settings.provider === "openai" &&
      settings.baseUrl === "https://api.openai.com/v1" &&
      !settings.apiKey &&
      legacyDefaultModels.includes(settings.model)
    ) {
      settings.baseUrl = defaults.settings.baseUrl;
      settings.model = defaults.settings.model;
      storedModels = storedModels.filter((model) => !legacyDefaultModels.includes(model));
    }

    return {
      settings,
      conversations: Array.isArray(parsed.conversations) ? parsed.conversations : [],
      activeConversationId: parsed.activeConversationId || null,
      models: uniqueModels([...storedModels, ...defaults.models])
    };
  } catch {
    return structuredClone(defaults);
  }
}

function saveState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function createConversation(shouldRender = true) {
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
    renderConversations();
    renderMessages();
    elements.promptInput.focus();
  }

  return conversation;
}

function getActiveConversation() {
  let conversation = state.conversations.find((item) => item.id === state.activeConversationId);
  if (!conversation) {
    conversation = createConversation(false);
  }
  return conversation;
}

function renderConversations() {
  elements.conversationList.innerHTML = "";

  for (const conversation of state.conversations) {
    const item = document.createElement("div");
    item.className = `conversation-item${conversation.id === state.activeConversationId ? " active" : ""}`;

    const title = document.createElement("button");
    title.className = "conversation-title";
    title.type = "button";
    title.textContent = conversation.title || "新对话";
    title.addEventListener("click", () => {
      state.activeConversationId = conversation.id;
      saveState();
      renderConversations();
      renderMessages();
      closeSidebarOnMobile();
    });

    const remove = document.createElement("button");
    remove.className = "delete-chat";
    remove.type = "button";
    remove.setAttribute("aria-label", "删除会话");
    remove.textContent = "×";
    remove.addEventListener("click", (event) => {
      event.stopPropagation();
      deleteConversation(conversation.id);
    });

    item.append(title, remove);
    elements.conversationList.appendChild(item);
  }
}

function deleteConversation(id) {
  const index = state.conversations.findIndex((item) => item.id === id);
  if (index === -1) return;

  state.conversations.splice(index, 1);
  if (state.activeConversationId === id) {
    state.activeConversationId = state.conversations[0]?.id || null;
  }

  if (!state.activeConversationId) {
    createConversation(false);
  }

  saveState();
  renderConversations();
  renderMessages();
}

function renderMessages(keepAtBottom = true) {
  const conversation = getActiveConversation();
  elements.chatTitle.textContent = conversation.title || "新对话";
  elements.messageArea.innerHTML = "";

  if (!conversation.messages.length) {
    elements.messageArea.appendChild(elements.emptyState);
    return;
  }

  const stack = document.createElement("div");
  stack.className = "message-stack";

  for (const message of conversation.messages) {
    stack.appendChild(createMessageNode(message));
  }

  elements.messageArea.appendChild(stack);
  if (keepAtBottom) scrollToBottom();
}

function createMessageNode(message) {
  const wrapper = document.createElement("article");
  wrapper.className = `message ${message.role}`;
  wrapper.dataset.messageId = message.id;

  const avatar = document.createElement("div");
  avatar.className = "avatar";
  avatar.textContent = message.role === "assistant" ? "AI" : "我";

  const body = document.createElement("div");
  const meta = document.createElement("div");
  meta.className = "message-meta";
  meta.textContent = message.role === "assistant" ? message.model || "Assistant" : "我";

  const content = document.createElement("div");
  content.className = "message-content";
  content.innerHTML = renderMarkdown(message.content || "");

  if (message.streaming) {
    content.appendChild(document.createElement("span")).className = "typing-caret";
  }

  body.append(meta);
  if (message.role === "assistant" && message.reasoningContent) {
    body.append(createReasoningNode(message));
  }
  body.append(content);
  wrapper.append(avatar, body);
  return wrapper;
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
    updateReasoningMessage(message, false);
    saveState();
  });

  const body = document.createElement("div");
  body.className = "reasoning-content";
  body.innerHTML = renderMarkdown(message.reasoningContent || "");

  panel.append(toggle, body);
  return panel;
}

async function submitPrompt() {
  const text = elements.promptInput.value.trim();
  if (!text || abortController) return;

  const conversation = getActiveConversation();
  const now = new Date().toISOString();
  const requestSettings = { ...state.settings };

  if (!conversation.messages.length) {
    conversation.title = makeTitle(text);
  }

  conversation.updatedAt = now;
  conversation.messages.push({
    id: createId(),
    role: "user",
    content: text,
    createdAt: now
  });

  const assistantMessage = {
    id: createId(),
    role: "assistant",
    model: requestSettings.model,
    provider: requestSettings.provider,
    baseUrl: requestSettings.baseUrl,
    content: "",
    reasoningContent: "",
    reasoningOpen: false,
    thinkingEnabled: requestSettings.thinkingEnabled,
    streaming: true,
    createdAt: now
  };
  conversation.messages.push(assistantMessage);

  elements.promptInput.value = "";
  autosizeTextarea();
  saveState();
  renderConversations();
  activeGeneration = true;
  autoFollowBottom = true;
  renderMessages(true);
  setBusy(true);
  setStatus("正在连接 API...");

  abortController = new AbortController();

  try {
    const requestMessages = conversation.messages
      .filter((message) => !message.streaming)
      .map(({ role, content }) => ({ role, content }));

    const response = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      signal: abortController.signal,
      body: JSON.stringify({
        ...requestSettings,
        thinking: { type: requestSettings.thinkingEnabled ? "enabled" : "disabled" },
        messages: requestMessages
      })
    });

    if (!response.ok) {
      const errorPayload = await response.json().catch(() => ({}));
      throw new Error(errorPayload.error || response.statusText);
    }

    if (requestSettings.stream) {
      setStatus("正在生成回复...");
      await readEventStream(response, (event, payload) => {
        if (event === "token") {
          assistantMessage.content += payload.text || "";
          updateStreamingMessage(assistantMessage);
        }

        if (event === "reasoning") {
          assistantMessage.reasoningContent += payload.text || "";
          updateReasoningMessage(assistantMessage);
        }

        if (event === "error") {
          throw new Error(payload.message || "Stream error");
        }
      });
    } else {
      const payload = await response.json();
      assistantMessage.content = payload.text || "";
      assistantMessage.reasoningContent = payload.reasoningContent || "";
      updateStreamingMessage(assistantMessage);
      updateReasoningMessage(assistantMessage);
    }

    assistantMessage.streaming = false;
    conversation.updatedAt = new Date().toISOString();
    setStatus("完成");
  } catch (error) {
    if (error.name === "AbortError") {
      assistantMessage.content ||= "已停止生成。";
      setStatus("已停止");
    } else {
      assistantMessage.content = `请求失败：${error.message}`;
      setStatus("请求失败");
    }
  } finally {
    assistantMessage.streaming = false;
    abortController = null;
    setBusy(false);
    saveState();
    const keepAtBottom = autoFollowBottom;
    activeGeneration = false;
    renderMessages(keepAtBottom);
  }
}

async function readEventStream(response, onEvent) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split("\n\n");
    buffer = blocks.pop() || "";

    for (const block of blocks) {
      const lines = block.split(/\r?\n/);
      const event = lines.find((line) => line.startsWith("event:"))?.slice(6).trim() || "message";
      const data = lines.find((line) => line.startsWith("data:"))?.slice(5).trim() || "{}";
      const payload = JSON.parse(data);

      if (event === "done") return;
      onEvent(event, payload);
    }
  }
}

function updateStreamingMessage(message) {
  const node = elements.messageArea.querySelector(`[data-message-id="${message.id}"] .message-content`);
  if (!node) {
    renderMessages();
    return;
  }

  node.innerHTML = renderMarkdown(message.content || "");
  node.appendChild(document.createElement("span")).className = "typing-caret";
  if (autoFollowBottom) scrollToBottom();
}

function updateReasoningMessage(message, shouldScroll = true) {
  if (!message.reasoningContent) return;

  const wrapper = elements.messageArea.querySelector(`[data-message-id="${message.id}"]`);
  if (!wrapper) {
    renderMessages();
    return;
  }

  let panel = wrapper.querySelector(".reasoning-panel");
  const content = wrapper.querySelector(".message-content");
  if (!panel) {
    panel = createReasoningNode(message);
    content?.before(panel);
  }

  panel.classList.toggle("open", !!message.reasoningOpen);
  const toggle = panel.querySelector(".reasoning-toggle");
  const body = panel.querySelector(".reasoning-content");
  const count = panel.querySelector(".reasoning-count");
  toggle?.setAttribute("aria-expanded", String(!!message.reasoningOpen));
  if (count) count.textContent = `${message.reasoningContent.length} 字`;
  if (body) body.innerHTML = renderMarkdown(message.reasoningContent || "");
  if (shouldScroll && autoFollowBottom) scrollToBottom();
}

function renderMarkdown(text) {
  if (!text) return "";
  const jsonImages = renderJsonImages(text);
  if (jsonImages) return `<p>已生成图片</p>${jsonImages}`;

  const escaped = escapeHtml(text);
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

  const lines = html.split(/\r?\n/);
  const blocks = [];
  let paragraph = [];
  let list = [];

  const flushParagraph = () => {
    if (paragraph.length) {
      blocks.push(`<p>${paragraph.join("<br>")}</p>`);
      paragraph = [];
    }
  };

  const flushList = () => {
    if (list.length) {
      blocks.push(`<ul>${list.map((item) => `<li>${item}</li>`).join("")}</ul>`);
      list = [];
    }
  };

  for (const line of lines) {
    if (/^@@CODE_\d+@@$/.test(line.trim())) {
      flushParagraph();
      flushList();
      blocks.push(line.trim());
      continue;
    }

    if (isImageUrl(line.trim())) {
      flushParagraph();
      flushList();
      blocks.push(imageToken(tokens, line.trim(), "生成图片"));
      continue;
    }

    const bullet = line.match(/^\s*[-*]\s+(.+)$/);
    if (bullet) {
      flushParagraph();
      list.push(bullet[1]);
      continue;
    }

    if (!line.trim()) {
      flushParagraph();
      flushList();
      continue;
    }

    flushList();
    paragraph.push(line);
  }

  flushParagraph();
  flushList();

  return blocks.join("").replace(/@@CODE_(\d+)@@/g, (_, index) => tokens[Number(index)] || "");
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

function makeTitle(text) {
  const clean = text.replace(/\s+/g, " ").trim();
  return clean.length > 24 ? `${clean.slice(0, 24)}...` : clean || "新对话";
}

function setBusy(isBusy) {
  elements.sendButton.disabled = isBusy;
  elements.stopButton.hidden = !isBusy;
  elements.promptInput.disabled = isBusy;
}

function setStatus(text) {
  elements.statusText.textContent = text;
}

function autosizeTextarea() {
  const textarea = elements.promptInput;
  textarea.style.height = "auto";
  textarea.style.height = `${Math.min(textarea.scrollHeight, 190)}px`;
}

function scrollToBottom() {
  requestAnimationFrame(() => {
    elements.messageArea.scrollTop = elements.messageArea.scrollHeight;
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

function openSettings() {
  syncSettingsForm();
  elements.settingsModal.hidden = false;
  elements.modelInput.focus();
}

function closeSettings() {
  elements.settingsModal.hidden = true;
  elements.promptInput.focus();
}

function syncSettingsForm() {
  elements.providerInput.value = state.settings.provider;
  elements.baseUrlInput.value = state.settings.baseUrl;
  elements.apiKeyInput.value = state.settings.apiKey;
  elements.modelInput.value = state.settings.model;
  elements.temperatureInput.value = state.settings.temperature;
  elements.maxTokensInput.value = state.settings.maxTokens;
  elements.streamInput.checked = state.settings.stream;
  elements.systemPromptInput.value = state.settings.systemPrompt;
  syncThinkingToggle();
}

function saveSettingsFromForm() {
  state.settings = {
    provider: elements.providerInput.value,
    baseUrl: elements.baseUrlInput.value.trim(),
    apiKey: elements.apiKeyInput.value.trim(),
    model: elements.modelInput.value.trim(),
    temperature: Number(elements.temperatureInput.value || defaults.settings.temperature),
    maxTokens: Number(elements.maxTokensInput.value || defaults.settings.maxTokens),
    stream: elements.streamInput.checked,
    thinkingEnabled: state.settings.thinkingEnabled !== false,
    systemPrompt: elements.systemPromptInput.value
  };

  state.models = uniqueModels([state.settings.model, ...state.models]);
  saveState();
  renderModels();
  renderMessages();
  setStatus("设置已保存");
}

function renderModels() {
  const models = uniqueModels([state.settings.model, ...state.models]).filter(Boolean);

  elements.modelOptions.innerHTML = "";
  elements.modelQuickSelect.innerHTML = "";

  for (const model of models) {
    const option = document.createElement("option");
    option.value = model;
    elements.modelOptions.appendChild(option);

    const quickOption = document.createElement("option");
    quickOption.value = model;
    quickOption.textContent = model;
    elements.modelQuickSelect.appendChild(quickOption);
  }

  elements.modelQuickSelect.value = state.settings.model;
  syncThinkingToggle();
}

function syncThinkingToggle() {
  const enabled = state.settings.thinkingEnabled !== false;
  state.settings.thinkingEnabled = enabled;
  elements.thinkingToggle.classList.toggle("active", enabled);
  elements.thinkingToggle.setAttribute("aria-pressed", String(enabled));
  elements.thinkingLabel.textContent = enabled ? "思考" : "快答";
}

async function fetchModels() {
  saveSettingsFromForm();
  setStatus("正在获取模型...");

  const params = new URLSearchParams({
    provider: state.settings.provider,
    baseUrl: state.settings.baseUrl,
    apiKey: state.settings.apiKey
  });

  try {
    const response = await fetch(`/api/models?${params.toString()}`);
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || response.statusText);

    state.models = uniqueModels([state.settings.model, ...(payload.models || []), ...state.models]);
    saveState();
    renderModels();
    setStatus(`已获取 ${payload.models?.length || 0} 个模型`);
  } catch (error) {
    setStatus(`获取模型失败：${error.message}`);
  }
}

function uniqueModels(models) {
  return [...new Set(models.filter(Boolean).map((model) => String(model).trim()).filter(Boolean))];
}

function createId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }

  const randomPart =
    globalThis.crypto?.getRandomValues
      ? Array.from(globalThis.crypto.getRandomValues(new Uint32Array(2)))
          .map((value) => value.toString(36))
          .join("")
      : Math.random().toString(36).slice(2);

  return `msg-${Date.now().toString(36)}-${randomPart}`;
}

function closeSidebarOnMobile() {
  elements.sidebar.classList.remove("open");
}
