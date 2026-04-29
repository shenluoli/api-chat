# AI Development Guide

这份文档给后续接手本项目的 AI/开发者使用。目标是快速知道项目结构、工具路径、构建命令和容易踩坑的地方。

## 项目位置

- 项目根目录：`D:\本地api`
- 本地网页服务入口：`D:\本地api\server.js`
- 桌面网页前端：`D:\本地api\public`
- 手机独立网页/PWA：`D:\本地api\mobile-standalone`
- Android 原生 APK 项目：`D:\本地api\android-app`
- 最新 APK 输出位置：`D:\本地api\APIChat-debug.apk`

## 当前维护范围

- 手机网页版/PWA `mobile-standalone` 暂时停止更新。除非用户明确要求恢复维护，否则不要修改 `mobile-standalone\app.js`、`mobile-standalone\styles.css` 或 `mobile-standalone\sw.js`。
- 当前手机端体验优先维护 Android APK，也就是 `android-app\app\src\main\java\local\api\chat\MainActivity.java`。



## 本地网页

启动命令：

```powershell
cd "D:\本地api"
npm start
```

等价命令：

```powershell
cd "D:\本地api"
node server.js
```

常用访问地址：

- 电脑本机：`http://127.0.0.1:8787/`
- 手机网页/PWA：`http://127.0.0.1:8787/mobile-standalone/`
- 局域网地址会由启动脚本写入：`D:\本地api\手机访问地址.txt`

配置文件：

- `.env` 包含端口、HOST、默认 Base URL、默认模型和 API Key。
- 不要把 `.env` 里的 API Key 写进文档、提交记录或聊天回复。
- `.env.example` 是示例配置。

当前服务端能力：

- `/api/chat`：OpenAI-compatible/Anthropic 风格聊天代理。
- `/api/models`：尝试读取模型列表。
- OpenAI-compatible 流式响应会转发 `content` 和 `reasoning_content`。
- DeepSeek 思考模式通过请求体里的 `thinking: { type: "enabled" | "disabled" }` 控制。

## Android 工具路径

Android Studio/JBR：

```text
E:\an
E:\an\jbr
```

Android SDK：

```text
C:\Users\aa\AppData\Local\Android\Sdk
```

Gradle：

```text
D:\本地api\.tools\gradle-8.14.3\bin\gradle.bat
```

Android 项目的 SDK 配置：

```text
D:\本地api\android-app\local.properties
sdk.dir=C\:\\Users\\aa\\AppData\\Local\\Android\\Sdk
```

## Android 构建命令

在 PowerShell 中运行：

```powershell
cd "D:\本地api\android-app"
$env:JAVA_HOME = "E:\an\jbr"
$env:ANDROID_HOME = "C:\Users\aa\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\aa\AppData\Local\Android\Sdk"
& "D:\本地api\.tools\gradle-8.14.3\bin\gradle.bat" assembleDebug --no-daemon
```

构建成功后复制 APK：

```powershell
Copy-Item -LiteralPath "D:\本地api\android-app\app\build\outputs\apk\debug\app-debug.apk" -Destination "D:\本地api\APIChat-debug.apk" -Force
```

注意：

- 在 Codex 沙箱里运行 Gradle 通常需要提升权限，因为会写入 Android/Gradle 缓存。
- 构建时出现 Java 8 source/target deprecated 警告目前不影响 APK 输出。
- 不要删除 `.tools`、`android-app\.gradle`、Android SDK 或 JBR。

## Android 关键文件

主 Activity：

```text
D:\本地api\android-app\app\src\main\java\local\api\chat\MainActivity.java
```

这个文件目前承担了大部分逻辑：

- 聊天 UI
- 历史对话
- API 来源管理
- 模型切换
- 思考模式开关
- 流式输出
- `reasoning_content` 展示
- APK 内本地配置持久化

## Android 已实现的重要功能

聊天：

- OpenAI-compatible `/chat/completions` 调用。
- 流式读取 SSE。
- 普通回答读取 `delta.content`。
- 思考过程读取 `delta.reasoning_content`。
- 思考过程可折叠。
- 等待连接/生成时有旋转状态条。
- 常见连接/API 错误会尽量转换为用户可读中文。

API 来源：

- 支持多个 API 来源。
- 已配置来源才显示在 `API 来源` 列表。
- 官方平台和中转入口放在 `添加来源` 次级菜单。
- 一个官方平台可以添加多份配置。
- 中转可以添加多份配置。
- 每个来源单独保存：
  - 显示名称
  - Base URL
  - API Key
  - 当前模型
  - 获取到的模型列表
- API 来源列表里长按条目可以改名。
- API Key 在设置中明文显示，便于用户检查。

文生图：

- Android 端输入框左侧有“图”开关。
- 开启“图”后，发送内容会调用当前 Base URL 的 OpenAI Images API：`POST {Base URL}/images/generations`。
- 对话模型和文生图模型分开设置；只有打开“图”开关时才走生图端点。
- 请求体格式：
  - `model`: 设置里的“文生图模型”，默认 `gpt-image-1`
  - `prompt`: 输入框内容
  - `n`: `1`
  - `size`: 设置里的“图片尺寸”，默认 `1024x1024`
  - `response_format`: 仅非 GPT Image 模型使用 `url`；`gpt-image-*` 不要传该字段，接口会返回 `b64_json`
- 响应支持 `data[0].url` 和 `data[0].b64_json`。返回后会写入消息内容并由图片预览区渲染。
- Claude 官方来源不支持文生图；需要生图时切换到 OpenAI 或支持 `/images/generations` 的中转来源。

模型：

- 模型列表支持滚动，避免模型过多时看不到底部。
- 支持从 `/models` 自动获取可用模型。
- 设置里分别维护对话模型和文生图模型，获取模型后会按模型名自动分到对应选择器。
- System Prompt 保存在每个对话上，新对话会继承当时的默认提示词。
- 获取失败时保留内置推荐模型。

交互/UI：

- 历史列表删除时不关闭重开弹窗，而是原地淡出并上浮。
- 输入框会随多行输入增高，最高约 4 倍。
- 消息正文和思考过程支持长按复制。
- 消息作者标识在气泡外展示：助手消息左侧显示模型名，用户消息右侧显示“我”。不要再把模型名或“我/你”放回气泡正文区域。
- 外置模型名标签需要保留最大宽度和省略号，避免长模型名在小屏顶出屏幕。
- 快捷提示、历史、模型、服务商/来源列表使用较统一的暖色轻菜单风格。

## 支持的预设 API 平台

当前 Android 端预设在 `MainActivity.java` 的 `PROVIDERS` 数组里：

- DeepSeek：`https://api.deepseek.com/v1`
- OpenAI：`https://api.openai.com/v1`
- Claude：`https://api.anthropic.com/v1`
- Google Gemini OpenAI-compatible：`https://generativelanguage.googleapis.com/v1beta/openai`
- 通义千问 DashScope：`https://dashscope.aliyuncs.com/compatible-mode/v1`
- Moonshot Kimi：`https://api.moonshot.cn/v1`
- 智谱 GLM：`https://open.bigmodel.cn/api/paas/v4`
- OpenRouter：`https://openrouter.ai/api/v1`
- 硅基流动：`https://api.siliconflow.cn/v1`
- 自定义 / 中转：默认占位 `https://你的中转地址/v1`

中转注意：

- 大多数中转是 OpenAI-compatible。
- Base URL 通常需要填到 `/v1`。
- 模型名可能带厂商前缀，例如 `openai/gpt-4o-mini`、`deepseek/deepseek-chat`。
- 如果中转支持 `/models`，用户可以点“获取模型”自动列出模型。
- 如果不支持 `/models`，用户仍可手动输入或使用内置推荐模型。

## 常见维护提醒

- 不要把用户的 API Key 输出到最终回复。
- 不要在没有用户明确要求时删除本地文件或清空配置。
- 修改 Android UI 后务必重新构建并复制到 `D:\本地api\APIChat-debug.apk`。
- 命令行构建前先临时设置 `JAVA_HOME=E:\an\jbr`。系统默认 `JAVA_HOME` 可能指向无效路径或 Java 8，直接运行 Gradle 会失败。
- 如果 Gradle 报 `Failed to load native library 'native-platform.dll'`，通常是沙箱限制导致，按同一构建命令提升权限重试。
- 修改 `mobile-standalone` 后如果涉及缓存，记得更新 `mobile-standalone\sw.js` 的 `CACHE_NAME`。
- PowerShell 里查看中文 Java 文件可能出现乱码显示，但文件本身通常仍可正常编译。
- 用户更关心手机端 APK 体验，Android 改动优先级通常高于网页端。

## 快速检查命令

检查 Node 语法：

```powershell
node --check "D:\本地api\server.js"
node --check "D:\本地api\public\app.js"
node --check "D:\本地api\mobile-standalone\app.js"
```

检查本地服务：

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8787/" -TimeoutSec 5
Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:8787/mobile-standalone/" -TimeoutSec 5
```

查看 8787 端口：

```powershell
Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue
```

启动隐藏 Node 服务：

```powershell
Start-Process -FilePath "node" -ArgumentList "server.js" -WorkingDirectory "D:\本地api" -WindowStyle Hidden
```
