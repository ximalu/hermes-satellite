# Hermes Satellite 🛰️

**Hermes Satellite** 是 [Hermes Agent](https://hermes-agent.nousresearch.com) 的 Android 手机伴侣应用。它让你通过手机与 Hermes AI Agent 实时对话，并利用手机的传感器、文件系统和网络能力执行远程操作。

## 为什么需要 Satellite？

Hermes Agent 原本通过 Matrix、Telegram、微信等平台与用户通信。但这些平台存在局限：
- **网络依赖**：需要公网可达，在某些环境下受限
- **功能边界**：无法直接访问手机的文件系统、执行 shell 命令、扫描局域网
- **延迟**：消息需要通过第三方服务器中转

Satellite 通过 WebSocket 直连 Hermes Gateway，提供低延迟、高可控的通信通道，同时赋予 Agent 远程访问手机的能力。

## 架构概览

```
┌──────────────────────┐     WebSocket      ┌──────────────────────┐     TCP + HTTP     ┌──────────────────────┐
│  手机（Satellite App）  │ ◄──────────────► │  Satellite Relay     │ ◄──────────────►  │  Hermes Gateway      │
│  Kotlin / Compose      │   ws://:8767      │  Python / aiohttp     │  localhost          │  (AI Agent)          │
│                        │   /satellite       │                       │                    │                      │
└──────────────────────┘                    └──────────────────────┘                    └──────────────────────┘
```

**三个组件：**
1. **Satellite App** — Android 手机端，用户直接操作
2. **Satellite Relay** — 独立 Python 进程，WebSocket 中转桥梁
3. **Hermes Gateway** — AI Agent 主进程，通过 Plugin 与 Relay 通信

## 已实现的功能

### ✅ 聊天通信
- WebSocket 实时文本消息收发
- Markdown 格式消息渲染（代码块带复制按钮、内联代码、URL 链接自动识别）
- 消息历史持久化（SharedPreferences）
- 自动重连机制
- 输入框采用 Element X 风格：圆角 24dp TextField + 圆形 FilledIconButton

### ✅ 文件浏览器
- 全文件系统浏览（需授权 MANAGE_EXTERNAL_STORAGE）
- 文件/目录列表，按类型和修改时间排序
- 长按菜单：分享、删除、**发送给 Hermes**
- 「发送给 Hermes」将文件上传至 Relay 服务器

### ✅ 文件上传（v0.5.0）
- Relay 端 `POST /upload` 接收 multipart 文件
- 文件保存至服务器 `/data/satellite-files/`
- 上传成功后聊天列表显示 `📎 文件名`
- Hermes Agent 可通过 HTTP 下载读取文件

### ✅ 局域网设备扫描
- ARP 表扫描 + ping 探测
- 显示设备 IP、主机名、MAC 地址

### ✅ 设备远程维护（SSH 隧道）
- 手机端启动 MINA SSHD（`127.0.0.1:2222`）
- 通过 JSch 建立反向 SSH 隧道到 Hermes 服务器
- 采用 BusyBox 作为 shell 环境（CI 下载，不跟踪二进制）
- 实现原理：`ssh -R 2222:localhost:2222 user@server`

### ✅ 开发者工具
- 应用内日志查看器，支持刷新和分享
- 崩溃日志持久化到文件，重启后可查看
- UncaughtExceptionHandler 捕获未处理异常

### ✅ 后台保活
- 前台服务（Foreground Service）
- 屏幕状态监听（ScreenStateReceiver）
- 开机自启（BootReceiver）

## 待实现功能

### 🔲 图片消息
- 选中图片后在聊天气泡中直接显示（目前已发送文件名文本占位）
- 数据模型需扩展支持 media 类型
- 考虑使用 Coil 异步加载

### 🔲 语音消息
- 录音 → 上传 → 播放

### 🔲 通知同步
- 手机通知推送到 Hermes Agent

### 🔲 GPS 定位
- 获取手机位置供 Agent 使用

### 🔲 End-to-End 加密
- 可选的 WebSocket 消息加密

## 通信协议

### WebSocket 消息格式

**手机 → Relay：**
```json
{"type": "auth", "pairing_code": "xxxxxx", "user_id": "ximalu"}
{"type": "chat", "text": "你好 Hermes"}
{"type": "ping"}
```

**Relay → 手机：**
```json
{"type": "auth_ok", "user_id": "ximalu"}
{"type": "chat", "text": "你好，有什么可以帮你的？"}
{"type": "pong"}
{"type": "ssh_config", "host": "...", "port": 22, "user": "...", "private_key": "..."}
```

### Relay ↔ Gateway IPC

**Relay → Plugin**（TCP, localhost:19001）：
```json
{"type": "chat", "chat_id": "ximalu", "text": "消息内容", "internal": false}
```

**Plugin → Relay**（HTTP POST, localhost:19000/send）：
```json
{"chat_id": "ximalu", "text": "回复内容"}
```

### 文件上传

```
POST /upload (multipart/form-data)
  file: @filename.ext

Response: {"ok": true, "name": "filename.ext", "size": 1234, "url": "/files/filename.ext"}
```

## 技术栈

| 层级 | 技术 | 用途 |
|------|------|------|
| UI | Jetpack Compose + Material3 | 声明式 UI |
| 网络 | OkHttp 4.12 | WebSocket + HTTP 文件上传 |
| SSH 客户端 | JSch 0.1.55 | 反向隧道 |
| SSH 服务器 | Apache MINA SSHD 2.12 | 手机端 SSHD |
| Shell | BusyBox ARM64 | 手机端 shell 环境 |
| 数据持久化 | SharedPreferences | 设置/聊天记录/崩溃日志 |
| 后台 | Foreground Service | 保活 |
| 构建 | Gradle 8.11 + AGP | arm64-v8a only |

## 构建与编译

### 本地编译

```bash
ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug --no-daemon --max-workers=2
```

### GitHub Actions

项目包含 `build-release.yml` 工作流，支持手动触发编译：

1. 在 GitHub 仓库页面点击 **Actions** → **Build Release APK** → **Run workflow**
2. 编译完成后下载 artifact

> **注意：** 默认编译 arm64-v8a 架构，release 模式（含 ProGuard 混淆）。输出文件名为 `HermesSatellite-{version}.apk`。

## 环境要求

- **Android**：8.0 (API 26) 或更高版本
- **权限**：文件管理权限（Android 11+ 需要 MANAGE_EXTERNAL_STORAGE）
- **服务器**：需要部署 Hermes Gateway + Satellite Relay

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 0.5.0 | 2026-07-10 | 首次 CI 编译。所有修复包含但未验证：JSch API 修复、设置持久化、日志持久化、文件上传 |
| 0.4.1 | 2026-07-09 | 已安装版本。有 SSH 隧道崩溃 bug、设置丢失、日志不持久等问题 |

## 已知问题 / 已验证状态

详见 [CHANGELOG.md](CHANGELOG.md)。

**v0.4.1 已知问题（已修复，v0.5.0 中待验证）：**
1. 收到 `ssh_config` 后 APK 崩溃（JSch `setProperties` API 错误 + META-INF 冲突）
2. 设置项（服务器地址、配对码）崩溃重启后丢失
3. 开发者日志重启后为空
4. 日志视图底部被系统导航栏遮挡

## License

MIT
