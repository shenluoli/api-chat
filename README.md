# Local API Chat

一个本地运行的网页聊天应用，界面是温暖、克制的 Claude-inspired 风格，默认接入 DeepSeek 的 OpenAI 兼容 `/v1/chat/completions` API，也支持其他 OpenAI 兼容接口和 Anthropic Messages API。

## 运行

直接双击：

```text
启动本地聊天.bat
```

它会在后台启动服务，并自动打开浏览器。

也可以双击这个无黑框入口：

```text
启动本地聊天-无黑框.vbs
```

也可以手动运行：

```powershell
npm start
```

默认会监听 `http://127.0.0.1:8787`。如果端口被占用，会自动尝试下一个端口。

## 手机上使用

电脑和手机连接同一个 Wi-Fi 后，双击启动入口，保持电脑端服务运行。然后在手机浏览器打开项目根目录里 `手机访问地址.txt` 写出的地址，例如：

```text
http://192.168.18.6:8787
```

如果手机打不开，通常是 Windows 防火墙拦住了 Node.js。允许 Node.js 访问“专用网络”后再试。公共 Wi-Fi 或不可信网络不建议开启局域网访问，因为这个本地服务会代理你的 API Key。

## 手机独立版

访问这个路径可以安装一个手机独立版 PWA：

```text
/mobile-standalone/
```

它不依赖电脑后端，API Key 保存在手机浏览器本地，并从手机直接请求 DeepSeek。安装后可以添加到主屏幕。若手机浏览器提示 CORS 或 `Failed to fetch`，说明 DeepSeek 不允许浏览器直连，这时需要改用 Android APK 或 Termux 本地后端方案。

手机独立版已经支持：

- 新对话
- 历史对话
- 删除对话
- 刷新后保留手机本地记录

注意：如果是通过 `http://192.168.x.x:8787/mobile-standalone/` 打开的，它的入口仍然依赖电脑服务和当前局域网 IP。添加到主屏幕后会像 App 一样打开，但电脑关机或 IP 变化时会打不开。真正随时打开、不依赖电脑，需要打包成 Android APK，或在手机上用 Termux 跑本地后端。

也可以双击这个脚本添加一条更明确的防火墙规则：

```text
允许手机访问-防火墙规则.bat
```

它只允许本地子网访问 TCP `8787`。

## 配置

可以直接在网页右上角的设置里填写：

- Provider
- Base URL
- API Key
- Model
- Temperature
- Max tokens

也可以复制 `.env.example` 为 `.env`，把密钥写在本地环境文件里：

```powershell
Copy-Item .env.example .env
```

如果网页里 API Key 留空，后端会优先使用 `.env` 或系统环境变量里的密钥。

## 常见 Base URL

- DeepSeek: `https://api.deepseek.com/v1`
- OpenAI: `https://api.openai.com/v1`
- OpenRouter: `https://openrouter.ai/api/v1`
- 本地 Ollama OpenAI 兼容接口: `http://127.0.0.1:11434/v1`
- LM Studio: `http://127.0.0.1:1234/v1`

## DeepSeek 模型

当前已查询到这个 key 可用：

- `deepseek-v4-flash`
- `deepseek-v4-pro`

前端顶部的模型下拉框可以随时切换模型，也可以在设置里点击“获取模型”刷新列表。

## 文件

- `server.js`: 本地静态服务和 API 代理
- `public/index.html`: 页面结构
- `public/styles.css`: Claude-inspired 界面样式
- `public/app.js`: 前端聊天逻辑
