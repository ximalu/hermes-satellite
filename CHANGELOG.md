# Hermes Satellite 项目履历

## 2026-07-09

> ⚠️ 以下修复均已提交到 GitHub，但**尚未编译 APK 验证**。用户当前安装的仍是 v0.4.1 旧版 APK，不含以下任何修复。

### 修复：日志持久化 + 日志视图底部导航栏遮挡

**问题：**
1. 崩溃重启后，开发者日志为空 — CrashLogger 用纯内存 buffer，进程死亡后丢失
2. 日志页面底部被系统导航栏遮挡 — `contentWindowInsets` 只设了 statusBars

**修复：**
- CrashLogger 增加文件持久化（`filesDir/crash_log.txt`），`init(context)` 在 Application.onCreate 中初始化
- 每条日志同时写入内存 buffer 和文件，崩溃重启后日志仍可从文件读取
- 日志文件超 500KB 自动清理
- SettingsScreen 中 Scaffold 的 `contentWindowInsets` 从 `statusBars` 改为 `systemBars`，底部导航栏不再遮挡内容

**涉及文件：**
- `app/src/main/java/com/hermes/satellite/ui/CrashLogger.kt`
- `app/src/main/java/com/hermes/satellite/SatelliteApp.kt`
- `app/src/main/java/com/hermes/satellite/ui/SettingsScreen.kt`

**验证状态：** ❌ 未测试

---

### 修复：设置项持久化 + 启动自动重连

**问题：** App 崩溃重启后，服务器地址和配对码丢失，需重新输入。

**原因：** SettingsScreen 用 `remember { mutableStateOf("") }` 存储设置，该状态仅在内存中，进程死亡后丢失。

**修复：**
- SettingsScreen 改用 SharedPreferences 持久化 serverUrl 和 pairingCode
- 点击「连接」时自动保存设置
- SatelliteApp 启动时检测已保存的设置，自动重连

**涉及文件：**
- `app/src/main/java/com/hermes/satellite/ui/SettingsScreen.kt` — 添加 SharedPreferences 读写
- `app/src/main/java/com/hermes/satellite/SatelliteApp.kt` — 启动时自动重连

**验证状态：** ❌ 未测试

---

### 修复：JSch setProperties → setConfig + META-INF 冲突

**问题：** 编译失败：
1. `SshTunnelManager.kt:52` — JSch API 错误，`setProperties()` 不存在
2. JSch + MINA SSHD 的 `META-INF/DEPENDENCIES` 文件重复

**修复：**
- `setProperties()` → `setConfig()`（JSch 的正确 API）
- `build.gradle.kts` 添加 packaging excludes 排除冲突文件

**涉及文件：**
- `app/src/main/java/com/hermes/satellite/ssh/SshTunnelManager.kt`
- `app/build.gradle.kts`

**验证状态：** ❌ 未测试 — 用户出现的 "收到 ssh_config 瞬间断连" 现象，很大概率就是此问题所致。旧 APK 不含此修复。

---

### Git 历史清洗
- 使用 git filter-branch 从所有历史 commit 中移除 `app/src/main/assets/busybox`（1.5MB 二进制）
- Repo 大小：22M → 17M
- Force push 到 GitHub
