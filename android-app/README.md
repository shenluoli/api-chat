# Phone API Chat Android

原生 Android 版 API 聊天客户端，不依赖电脑后端，不受浏览器 CORS 限制。

功能：

- DeepSeek/OpenAI-compatible API
- API Key 保存在手机本地
- 模型切换
- 新对话和历史对话
- 本地持久化

## 构建

先用 Android Studio 打开本目录 `android-app`。如果提示安装 SDK、Gradle、Build Tools，按提示安装。

命令行构建：

```powershell
.\gradlew.bat assembleDebug
```

输出 APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```
