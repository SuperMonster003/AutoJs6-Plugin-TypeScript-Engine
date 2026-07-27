# AutoJs6 TypeScript Engine Plugin

AutoJs6 的完整 TypeScript 编译前端插件. 插件负责把 TypeScript 项目转换为
JavaScript, Source Map 和结构化诊断, 最终 JavaScript 仍由 AutoJs6 选择的
Rhino 或 Node.js 引擎执行.

> 当前状态: R0 插件基线已完成, R1 正在进行. v0.4.0 已加入经过三摘要验证的
> TypeScript 6.0.3 compiler distribution, 真实跨进程 Binder/FD 门禁以及独立
> WebView/Web Worker prewarm probe. Android 12/13 双设备已通过 renderer
> recovery, 10 轮 compiler process restart 和 fingerprint 稳定性门禁.
> minified release probe 也已在 Android 13 真机通过.
> `compilerAvailable=false` 仍为权威状态; R2 完成前尚不能编译或执行
> TypeScript 项目.

## 固定基线

- AutoJs6 开发基线: `6.8.0 Alpha7`, build `5253`.
- Android: min SDK `24`, compile/target SDK `36`.
- TypeScript: 精确锁定 `6.0.3`.
- 插件 ID / engine: `typescript`.
- 插件 variant: `typescript_6_0_3`.
- Compiler action: `org.autojs.plugin.typescript.COMPILER`.
- Compiler process: `:typescript_compiler`.

## 核心边界

```text
AutoJs6 host
  -> 读取或解密项目并构造受限 VFS archive
  -> TypeScript compiler plugin
       -> JS + Source Map + diagnostics + manifest
  -> AutoJs6 host
       -> Rhino adapter 或 Node adapter
```

- 插件是编译前端, 不是第三个 JavaScript VM.
- 插件不读取宿主私有项目路径, 只消费宿主显式传入的归档.
- 插件不依赖, 不发现, 不启用 Node.js Runtime 插件.
- Node Runtime 未安装或未启用时, Rhino TypeScript 仍应可编译并执行.
- Ace 编辑器和编译插件可以共享 TypeScript 发行包规范, 但不共享 WebView,
  Language Service, AST 或生命周期.
- R1 compiler host 使用受限 HTTPS asset origin 和专用 Web Worker. 它只运行
  固定 golden, 不接收真实项目 VFS, 不执行 emitted JavaScript.
- 首个 GA 的 Rhino profile 支持 `.ts`, Node profile 支持 `.ts/.mts/.cts`.
- 首个 GA 只编译执行前 snapshot; 运行中外部 TS 与 bundled compiler 延至
  Post-GA.
- StackTrace 契约为 Node 文件/行/列, Rhino 文件/行; 两者编译诊断均到列.

## Roadmap

最终集成计划和逐项状态见 [ROADMAP.md](ROADMAP.md).

R1 发行物和受限宿主设计见
[Compiler Distribution v1](docs/architecture/COMPILER_DISTRIBUTION_V1.md) 与
[Restricted Compiler Host R1](docs/architecture/COMPILER_HOST_R1.md).

多语言发行记录见
[CHANGELOG-zh-Hans.md](.changelog/CHANGELOG-zh-Hans.md). 修改
`.changelog/lang_*.json` 后运行:

```powershell
py .python/generate_changelog.py
py .python/generate_changelog.py --check
```

## 构建与验证

```powershell
.\gradlew.bat verifyR0
.\gradlew.bat verifyR1CompilerDistribution
.\gradlew.bat verifyR1CompilerHost
```

`verifyR1CompilerDistribution` 从 lock 内的精确 npm URL 获取 TypeScript
tarball, 验证 SHA-1, SHA-256 和 SRI SHA-512, 安全生成发行目录, 并重新校验
debug/release APK 中的实际资产字节. `verifyR1CompilerHost` 同时校验 host
JavaScript/CSP/HTTPS allowlist/WebView policy, 无危险 bridge API, 无 INTERNET
权限, 以及 debug/release APK 内 host 与唯一 compiler asset 的实际字节.

连接 Android 设备后运行真实 `:typescript_compiler` Binder/FD 与 prewarm
runtime golden 门禁:

```powershell
.\gradlew.bat verifyR1BinderFdDebug
```

当前已确认 Android 12/API 31 与 Android 13/API 33 上的 debug 远程 bind,
每台设备 100 次顺序 prewarm, 1 ms timeout 后的 Worker 重建恢复, 真实
renderer 首次退出后的单次 retry, 第二次退出后的稳定失败与后续恢复, 以及每台
设备 10 轮 compiler process restart 后的 fingerprint 稳定性. minified release
也已在 Android 13 真机通过.

API 24 已通过 6 项 Bundle/FD transport 门禁, 但设备自带的旧 WebView 无法解析
原始 TypeScript 6.0.3 compiler JavaScript, 并报告 `Unexpected token ?`.
这不构成 compiler host 兼容证据. API 24/25 仍需 modern WebView provider matrix;
service destruction, 10 次冷启动/100 次热调用资源泄漏和 Node/Ace 缺失矩阵也仍是
R1 待办.

`release-smoke` 是不依赖 AndroidX runner 的 framework-only helper APK, 用于
验证 R8/minified target. `verifyR1CompilerHost` 会确保 helper 可构建, 但不会
为 unsigned release APK 选择签名证书或操作设备. 真机门禁需先用同一测试证书
签名并安装 release target 与 helper, 再运行:

```powershell
adb shell am instrument -w io.github.supermonster003.autojs6.plugin.typescript.releasesmoke/.R1ReleaseSmokeInstrumentation
```

R0 的细分任务仍可分别运行:

```powershell
.\gradlew.bat :plugin-api:typescript-api:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:verifyNodeRuntimeIndependence
.\gradlew.bat :app:verifyCompilerDistributionLock
.\gradlew.bat :app:assembleDebug
```

Debug/release APK 分别输出到 `app/build/outputs/apk/debug/` 与
`app/build/outputs/apk/release/`.

## License

项目使用 [MPL-2.0](LICENSE). TypeScript 发行物使用其上游许可证; R1 开始
打包发行物时必须同时包含 `LICENSE.txt` 和 `ThirdPartyNoticeText.txt`.
