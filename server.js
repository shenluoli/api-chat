const http = require("http");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { URL } = require("url");

const rootDir = __dirname;
const publicDir = path.join(rootDir, "public");
const runtimeFile = path.join(rootDir, ".local-chat-server.json");
const phoneUrlFile = path.join(rootDir, "手机访问地址.txt");

loadEnvFile(path.join(rootDir, ".env"));

const DEFAULT_PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "127.0.0.1";
const MAX_BODY_BYTES = 2 * 1024 * 1024;

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml; charset=utf-8",
  ".ico": "image/x-icon",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp"
};

const server = http.createServer(async (req, res) => {
  try {
    const requestUrl = new URL(req.url, `http://${req.headers.host || "localhost"}`);

    if (req.method === "GET" && requestUrl.pathname === "/api/health") {
      sendJson(res, 200, { ok: true });
      return;
    }

    if (req.method === "GET" && requestUrl.pathname === "/api/models") {
      await handleModels(req, res, requestUrl);
      return;
    }

    if (req.method === "POST" && requestUrl.pathname === "/api/chat") {
      await handleChat(req, res);
      return;
    }

    if (req.method !== "GET" && req.method !== "HEAD") {
      sendJson(res, 405, { error: "Method not allowed" });
      return;
    }

    await serveStatic(requestUrl.pathname, res, req.method === "HEAD");
  } catch (error) {
    console.error(error);
    if (!res.headersSent) {
      sendJson(res, 500, { error: error.message || "Server error" });
    } else {
      res.end();
    }
  }
});

listenWithFallback(DEFAULT_PORT);

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;

  const content = fs.readFileSync(filePath, "utf8");
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;

    const equalsIndex = trimmed.indexOf("=");
    if (equalsIndex === -1) continue;

    const key = trimmed.slice(0, equalsIndex).trim();
    let value = trimmed.slice(equalsIndex + 1).trim();

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    if (key && process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}

function listenWithFallback(port) {
  server.once("error", (error) => {
    if (error.code === "EADDRINUSE" && port < DEFAULT_PORT + 50) {
      listenWithFallback(port + 1);
      return;
    }

    console.error(error);
    process.exit(1);
  });

  server.listen(port, HOST, () => {
    const address = server.address();
    const localUrl = `http://127.0.0.1:${address.port}`;
    const lanUrls = isLocalhostOnly(HOST) ? [] : getLanUrls(address.port);
    const payload = {
      pid: process.pid,
      port: address.port,
      host: HOST,
      url: localUrl,
      localUrl,
      lanUrls,
      startedAt: new Date().toISOString()
    };

    fs.writeFileSync(runtimeFile, JSON.stringify(payload, null, 2));
    writePhoneUrlFile(payload);
    console.log(`Local API Chat is running at ${payload.localUrl}`);
    for (const lanUrl of lanUrls) {
      console.log(`LAN URL: ${lanUrl}`);
    }
  });
}

function isLocalhostOnly(host) {
  return host === "127.0.0.1" || host === "localhost" || host === "::1";
}

function getLanUrls(port) {
  const ignoredPrefixes = ["127.", "169.254.", "198.18.", "198.19."];
  const ignoredAdapterNames = ["vmware", "virtualbox", "hyper-v", "zerotier", "tailscale", "loopback"];
  const urls = [];

  for (const [adapterName, addresses] of Object.entries(os.networkInterfaces())) {
    const normalizedName = adapterName.toLowerCase();
    if (ignoredAdapterNames.some((name) => normalizedName.includes(name))) continue;

    for (const address of addresses || []) {
      if (address.family !== "IPv4" || address.internal) continue;
      if (ignoredPrefixes.some((prefix) => address.address.startsWith(prefix))) continue;
      urls.push(`http://${address.address}:${port}`);
    }
  }

  return [...new Set(urls)];
}

function writePhoneUrlFile(payload) {
  const chatUrls = payload.lanUrls.length ? payload.lanUrls : ["当前服务只监听 127.0.0.1，手机无法直接访问。"];
  const standaloneUrls = payload.lanUrls.map((url) => `${url}/mobile-standalone/`);
  const lines = [
    "手机访问方式：",
    "",
    "1. 电脑和手机连接同一个 Wi-Fi。",
    "2. 保持这个本地服务运行。",
    "3. 在手机浏览器打开下面的地址。",
    "",
    "电脑后端版：",
    ...chatUrls,
    "",
    "手机独立版入口：",
    ...(standaloneUrls.length ? standaloneUrls : ["当前暂无手机独立版局域网地址。"]),
    "",
    `电脑本机地址：${payload.localUrl}`,
    "",
    "如果手机打不开，请检查 Windows 防火墙是否允许 Node.js 访问专用网络。"
  ];

  fs.writeFileSync(phoneUrlFile, `${lines.join("\n")}\n`, "utf8");
}

async function serveStatic(pathname, res, headOnly) {
  const safePath = decodeURIComponent(pathname).replace(/\\/g, "/");
  const mount = safePath.startsWith("/mobile-standalone/")
    ? {
        baseUrl: "/mobile-standalone/",
        dir: path.join(rootDir, "mobile-standalone")
      }
    : {
        baseUrl: "/",
        dir: publicDir
      };
  const requestPath =
    mount.baseUrl === "/"
      ? safePath
      : `/${safePath.slice(mount.baseUrl.length)}`;
  const normalized = path.normalize(requestPath === "/" ? "/index.html" : requestPath);
  const relativePath = normalized.replace(/^([/\\])+/, "");
  const filePath = path.join(mount.dir, relativePath);
  const relativeToPublic = path.relative(mount.dir, filePath);

  if (relativeToPublic.startsWith("..") || path.isAbsolute(relativeToPublic)) {
    sendText(res, 403, "Forbidden");
    return;
  }

  let stats;
  try {
    stats = await fs.promises.stat(filePath);
  } catch {
    sendText(res, 404, "Not found");
    return;
  }

  if (stats.isDirectory()) {
    await serveStatic(path.join(pathname, "index.html"), res, headOnly);
    return;
  }

  const ext = path.extname(filePath).toLowerCase();
  res.writeHead(200, {
    "Content-Type": MIME_TYPES[ext] || "application/octet-stream",
    "Cache-Control": "no-store"
  });

  if (headOnly) {
    res.end();
    return;
  }

  fs.createReadStream(filePath).pipe(res);
}

async function handleModels(req, res, requestUrl) {
  const provider = requestUrl.searchParams.get("provider") || "openai";
  const baseUrl = requestUrl.searchParams.get("baseUrl") || getDefaultBaseUrl(provider);
  const apiKey = requestUrl.searchParams.get("apiKey") || getDefaultApiKey(provider);

  if (provider === "anthropic") {
    sendJson(res, 200, {
      models: [
        process.env.ANTHROPIC_MODEL || "claude-3-5-sonnet-latest",
        "claude-3-5-haiku-latest",
        "claude-3-opus-latest"
      ]
    });
    return;
  }

  if (!baseUrl) {
    sendJson(res, 400, { error: "Base URL is required." });
    return;
  }

  const response = await fetch(joinUrl(baseUrl, "models"), {
    headers: buildHeaders(provider, apiKey)
  });

  const text = await response.text();
  if (!response.ok) {
    sendJson(res, response.status, { error: extractError(text) || response.statusText });
    return;
  }

  const json = JSON.parse(text);
  const models = Array.isArray(json.data)
    ? json.data.map((item) => item.id).filter(Boolean)
    : [];

  sendJson(res, 200, { models });
}

async function handleChat(req, res) {
  const body = await readJsonBody(req);
  const provider = body.provider || "openai";
  const baseUrl = body.baseUrl || getDefaultBaseUrl(provider);
  const apiKey = body.apiKey || getDefaultApiKey(provider);
  const model = body.model || getDefaultModel(provider);
  const messages = Array.isArray(body.messages) ? body.messages : [];
  const systemPrompt = typeof body.systemPrompt === "string" ? body.systemPrompt.trim() : "";
  const temperature = clampNumber(body.temperature, 0, 2, 0.7);
  const maxTokens = clampNumber(body.maxTokens, 1, 200000, 2048);
  const stream = body.stream !== false;
  const thinking = normalizeThinking(body);

  if (!model) {
    sendJson(res, 400, { error: "Model is required." });
    return;
  }

  if (!baseUrl && provider !== "anthropic") {
    sendJson(res, 400, { error: "Base URL is required." });
    return;
  }

  if (!apiKey && provider === "anthropic") {
    sendJson(res, 400, { error: "Anthropic API key is required. Put it in settings or .env." });
    return;
  }

  if (provider === "anthropic") {
    await callAnthropic({ res, apiKey, model, messages, systemPrompt, temperature, maxTokens, stream });
    return;
  }

  await callOpenAICompatible({
    res,
    baseUrl,
    apiKey,
    model,
    messages,
    systemPrompt,
    temperature,
    maxTokens,
    thinking,
    stream
  });
}

async function callOpenAICompatible(options) {
  const { res, baseUrl, apiKey, model, messages, systemPrompt, temperature, maxTokens, thinking, stream } = options;
  const payload = {
    model,
    messages: systemPrompt ? [{ role: "system", content: systemPrompt }, ...messages] : messages,
    temperature,
    max_tokens: maxTokens,
    stream
  };

  if (thinking) {
    payload.thinking = thinking;
  }

  const response = await fetch(joinUrl(baseUrl, "chat/completions"), {
    method: "POST",
    headers: buildHeaders("openai", apiKey),
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const text = await response.text();
    sendJson(res, response.status, { error: extractError(text) || response.statusText });
    return;
  }

  if (!stream) {
    const json = await response.json();
    const text = json.choices?.[0]?.message?.content || "";
    const reasoningContent = json.choices?.[0]?.message?.reasoning_content || "";
    sendJson(res, 200, { text, reasoningContent, raw: json });
    return;
  }

  beginEventStream(res);
  await pumpOpenAIStream(response, res);
}

async function callAnthropic(options) {
  const { res, apiKey, model, messages, systemPrompt, temperature, maxTokens, stream } = options;
  const payload = {
    model,
    max_tokens: maxTokens,
    temperature,
    messages: messages.map((message) => ({
      role: message.role === "assistant" ? "assistant" : "user",
      content: message.content || ""
    })),
    stream
  };

  if (systemPrompt) {
    payload.system = systemPrompt;
  }

  const response = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-api-key": apiKey,
      "anthropic-version": "2023-06-01"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const text = await response.text();
    sendJson(res, response.status, { error: extractError(text) || response.statusText });
    return;
  }

  if (!stream) {
    const json = await response.json();
    const text = Array.isArray(json.content)
      ? json.content.map((part) => part.text || "").join("")
      : "";
    sendJson(res, 200, { text, raw: json });
    return;
  }

  beginEventStream(res);
  await pumpAnthropicStream(response, res);
}

async function pumpOpenAIStream(response, res) {
  const decoder = new TextDecoder();
  let buffer = "";

  for await (const chunk of response.body) {
    buffer += decoder.decode(chunk, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() || "";

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || !trimmed.startsWith("data:")) continue;

      const data = trimmed.slice(5).trim();
      if (data === "[DONE]") {
        sendEvent(res, "done", {});
        res.end();
        return;
      }

      try {
        const json = JSON.parse(data);
        const reasoning = json.choices?.[0]?.delta?.reasoning_content || "";
        if (reasoning) sendEvent(res, "reasoning", { text: reasoning });

        const text = json.choices?.[0]?.delta?.content || "";
        if (text) sendEvent(res, "token", { text });
      } catch (error) {
        sendEvent(res, "error", { message: error.message });
      }
    }
  }

  sendEvent(res, "done", {});
  res.end();
}

async function pumpAnthropicStream(response, res) {
  const decoder = new TextDecoder();
  let buffer = "";

  for await (const chunk of response.body) {
    buffer += decoder.decode(chunk, { stream: true });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() || "";

    for (const block of blocks) {
      const dataLine = block.split(/\r?\n/).find((line) => line.startsWith("data:"));
      if (!dataLine) continue;

      try {
        const json = JSON.parse(dataLine.slice(5).trim());
        const text = json.delta?.text || "";
        if (text) sendEvent(res, "token", { text });
        if (json.type === "message_stop") {
          sendEvent(res, "done", {});
          res.end();
          return;
        }
      } catch (error) {
        sendEvent(res, "error", { message: error.message });
      }
    }
  }

  sendEvent(res, "done", {});
  res.end();
}

function buildHeaders(provider, apiKey) {
  const headers = {
    "Content-Type": "application/json"
  };

  if (apiKey) {
    headers.Authorization = `Bearer ${apiKey}`;
  }

  if (provider === "openai" && process.env.OPENROUTER_SITE_URL) {
    headers["HTTP-Referer"] = process.env.OPENROUTER_SITE_URL;
  }

  if (provider === "openai" && process.env.OPENROUTER_APP_NAME) {
    headers["X-Title"] = process.env.OPENROUTER_APP_NAME;
  }

  return headers;
}

function getDefaultBaseUrl(provider) {
  if (provider === "anthropic") return "";
  return process.env.OPENAI_BASE_URL || "https://api.openai.com/v1";
}

function getDefaultApiKey(provider) {
  if (provider === "anthropic") return process.env.ANTHROPIC_API_KEY || "";
  return process.env.OPENAI_API_KEY || "";
}

function getDefaultModel(provider) {
  if (provider === "anthropic") return process.env.ANTHROPIC_MODEL || "claude-3-5-sonnet-latest";
  return process.env.OPENAI_MODEL || "gpt-4o-mini";
}

function joinUrl(baseUrl, endpoint) {
  const normalizedBase = String(baseUrl || "").replace(/\/+$/, "");
  const normalizedEndpoint = String(endpoint || "").replace(/^\/+/, "");
  return `${normalizedBase}/${normalizedEndpoint}`;
}

function clampNumber(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(min, Math.min(max, number));
}

function normalizeThinking(body) {
  if (body.thinking && typeof body.thinking === "object") {
    const type = body.thinking.type === "enabled" ? "enabled" : "disabled";
    return { type };
  }

  if (typeof body.thinkingEnabled === "boolean") {
    return { type: body.thinkingEnabled ? "enabled" : "disabled" };
  }

  return null;
}

function beginEventStream(res) {
  res.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache, no-transform",
    Connection: "keep-alive",
    "X-Accel-Buffering": "no"
  });
}

function sendEvent(res, event, payload) {
  res.write(`event: ${event}\n`);
  res.write(`data: ${JSON.stringify(payload)}\n\n`);
}

function sendJson(res, status, payload) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(payload));
}

function sendText(res, status, text) {
  res.writeHead(status, { "Content-Type": "text/plain; charset=utf-8" });
  res.end(text);
}

function extractError(text) {
  try {
    const json = JSON.parse(text);
    return json.error?.message || json.error || json.message || text;
  } catch {
    return text;
  }
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";

    req.on("data", (chunk) => {
      body += chunk;
      if (Buffer.byteLength(body) > MAX_BODY_BYTES) {
        reject(new Error("Request body is too large."));
        req.destroy();
      }
    });

    req.on("end", () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new Error("Invalid JSON body."));
      }
    });

    req.on("error", reject);
  });
}
