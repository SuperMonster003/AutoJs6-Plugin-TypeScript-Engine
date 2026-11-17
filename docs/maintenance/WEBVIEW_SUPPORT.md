# WebView 支持策略

## 当前支持契约

AutoJs6 TypeScript Engine v0.6.0 同时要求：

| 维度 | 最低支持值 | 说明 |
|---|---:|---|
| Android | API 24 / Android 7.0 | 决定 service、Binder 和系统 API 可用性 |
| Chrome/WebView provider | major 119 | 决定 compiler host、Worker 与 TypeScript 6.0.3 JavaScript 能否解析和运行 |

两条要求相互独立。Android API 达标不表示系统镜像自带的 WebView 已达标；尤其是
API 24/25，需要更新并明确选中兼容的 Chrome/WebView provider。当前插件不会下载
provider、不会联网获取编译器，也不会为旧引擎降级 compiler host 或 TypeScript。

### 低于最低线时的明确错误 (0.7.1 起)

0.7.1 起插件在加载编译页或解析页之前读取 WebView 的 Chromium 引擎版本 (默认 User-Agent
的 `Chrome/<版本>`, OEM 提供方的包版本号另有规则时也跟随引擎), 主版本低于 119 时:

- 编译请求立即以 `ERR_AUTOJS6_TYPESCRIPT_COMPILER_UNAVAILABLE` 拒绝, 消息为
  `TypeScript compilation needs Android System WebView 119 or later; the current one is <版本> (<包名>). Update Android System WebView or Chrome, then try again.`
  (按系统语言显示 10 种语言之一). 宿主控制台的首行仍是宿主自己的 "插件不可用" 提示,
  插件的这条消息在 `Caused by` 行与日志里; prewarm 返回同一错误, 开机预热不创建 WebView.
- 解析服务 (`org.autojs.plugin.typescript.PARSER`) 以 `ERR_AUTOJS6_TYPESCRIPT_PARSER_UNAVAILABLE`
  与同一消息拒绝, `getParserInfo` 的状态为 `unavailable`.
- 读不出引擎版本时不拒绝, 沿用原来的有界 Worker timeout.

最低值仍是 major 119 (下表的正向证据); 实现与单测见
`CompilerWebViewRequirement.kt` 与 `CompilerWebViewRequirementTest`. 这不是把编译器转译到旧
语法 (3-Probe Debugger Q150 选 A, 不做转译).

“受支持”表示该组合至少通过真实 Worker 启动、TypeScript compiler metadata、公开
Binder 编译、JavaScript/Source Map/diagnostics 验证以及 release smoke。它不表示所有
OEM WebView 定制都自动得到保证；问题报告必须带 active provider 的包名和完整版本。

## 已验证边界

v0.6.0 的正向 release smoke：

| Android/设备 | active provider | 结果 |
|---|---|---|
| API 24 x86 AVD | Chrome-WebView `119.0.6045.194` | PASS |
| API 31 Sony XQ-AT72 | Android System WebView `145.0.7632.120` | PASS |
| API 35 Xiaomi 23046RP50C | Android System WebView `130.0.6723.86` | PASS |
| API 37 x86_64 AVD | Android System WebView `145.0.7632.218` | PASS |

保留的负向边界：

- API 24 stock Chrome 51 与 API 25 stock Chrome 55 能完成 Binder transport，但在
  解析 compiler host 时报告 `Unexpected token )`，随后由有界 Worker timeout 收口。
- API 24 Chrome 69 对当前 v0.6.0 host 在 `compiler-host.js:76` fail closed，并由
  Worker probe timeout 有界退出。
- 历史阶段在 WebView 109 上通过过较早的 R1 路径；它不是当前 v0.6.0 bundle 的支持
  声明，也不能覆盖正式的 major 119 最低线。

负基线用于证明失败有界，不计作成功矩阵，也不触发语法重写、fallback 或 TypeScript
降级。

0.7.1 的明确错误 (debug 包, 2026-11-17):

| Android/设备 | active provider | 结果 |
|---|---|---|
| API 24 x86 AVD (AVD_API_24 只读实例) | `com.android.chrome` `69.0.3497.100` | 编译请求约 0.6 秒内以上述消息失败, 不再等待 Worker timeout; 脚本未运行 |
| API 28 Sony G8441 | `com.android.chrome` `126.0.6478.186` | 不误报: 普通 `.ts` 与调用 `debugging` 的 `.ts` 都编译并运行 |

设备检查脚本: `py tools/check-device-ts-scripts.py --serial <serial> [--apk <apk>] [--expect-old-webview]`.

## 查询 active provider

不同 Android 版本的 `cmd webviewupdate` 子命令并不一致，维护记录统一使用：

```powershell
adb -s <serial> shell dumpsys webviewupdate |
  Select-String 'Current WebView package'
```

应保存形如
`Current WebView package (name, version): (com.google.android.webview, 145.0.7632.120)`
的完整行。不要用浏览器界面显示的版本、APK 文件名或 Android API level 猜测 active
provider。

## 季度与发版复核规则

1. 每季度依赖巡检时保留 major 119 最低镜像的 smoke；每次发版在最低镜像和当前高
   major 各跑一次。
2. compiler host、TypeScript distribution 或 AndroidX WebKit 变更后，必须重跑最低
   provider；只跑高版本设备不能证明最低线。
3. 只有在最低线无法继续安全运行，且相邻更高 major 已形成正向证据时才提高最低值。
   同一提交要更新 README、快速上手、错误目录、发布说明、本页与 release smoke 矩阵。
4. 降低最低值需要新的真实正向证据和安全审查，不能仅以删改一处现代语法作为依据。
5. OEM/provider 特定故障先按包名、完整版本、ABI 和设备复现；可重复问题进入 issue，
   不立即外推为整个 WebView major 不兼容。
