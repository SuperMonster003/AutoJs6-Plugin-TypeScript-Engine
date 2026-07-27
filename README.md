# AutoJs6 TypeScript Engine Plugin

AutoJs6 的完整 TypeScript 编译前端插件. 插件负责把 TypeScript 项目转换为
JavaScript, Source Map 和结构化诊断, 最终 JavaScript 仍由 AutoJs6 选择的
Rhino 或 Node.js 引擎执行.

> 当前状态: R0 插件基线与 R1 官方编译器/真实宿主探针均已完成.
> v0.5.0 已加入经过三摘要验证的
> TypeScript 6.0.3 compiler distribution, 真实跨进程 Binder/FD 门禁以及独立
> WebView/Web Worker prewarm probe. Android 12/13 双设备已通过 service
> destruction, renderer recovery, 10 个冷进程, 100 次热调用和 compiler
> service FD、PSS/request correlation、双冷 Rhino、accepted lifecycle 及冻结
> AAR 双向兼容门禁; API 33/WebView 109 也通过 Node/Ace 四种安装态矩阵.
> minified release probe 已在 Android 13 真机通过，API 24/25 Chrome 119
> modern-provider runtime matrix 也已关闭最后一项 R1 退出门禁.
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
.\gradlew.bat verifyR1ProtocolCompatibility
```

`verifyR1CompilerDistribution` 从 lock 内的精确 npm URL 获取 TypeScript
tarball, 验证 SHA-1, SHA-256 和 SRI SHA-512, 安全生成发行目录, 并重新校验
debug/release APK 中的实际资产字节. `verifyR1CompilerHost` 同时校验 host
JavaScript/CSP/HTTPS allowlist/WebView policy, 无危险 bridge API, 无 INTERNET
权限, 以及 debug/release APK 内 host 与唯一 compiler asset 的实际字节.
`verifyR1ProtocolCompatibility` 校验冻结 v1 AAR、transaction/Bundle fixtures、
compat APK 签名和双变体构建/lint；重冻 AAR 必须另行显式执行，普通 verification
不会覆盖基线. 该聚合任务是静态门禁，不会安装 APK 或操作连接设备. 双向设备门禁
必须先安装当前 app，再安装同签名 compat APK，并串行运行两个 framework-only
instrumentation:

```powershell
.\gradlew.bat verifyR1ProtocolCompatibility
adb -s <serial> install -r -t -d app\build\outputs\apk\debug\autojs6-plugin-typescript-engine-v0.5.0-universal.apk
adb -s <serial> install -r -t -d r1-compat-v1\build\outputs\apk\debug\r1-compat-v1-debug.apk
adb -s <serial> shell am instrument -w -r io.github.supermonster003.autojs6.plugin.typescript.compat.v1/io.github.supermonster003.autojs6.plugin.typescript.compat.v1.FrozenClientToCurrentServiceInstrumentation
adb -s <serial> shell am instrument -w -r io.github.supermonster003.autojs6.plugin.typescript.compat.v1/io.github.supermonster003.autojs6.plugin.typescript.compat.v1.CurrentClientToFrozenServiceInstrumentation
```

两条 runner 都必须唯一返回 `INSTRUMENTATION_CODE: -1`. 2026-07-27 的 API 31/33
双机证据均为 pass；app/compat debug APK 的实际 signer certificate SHA-256 同为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.

当前 AutoJs6 Rhino 的可行性探针是 debug-only 且显式 opt-in. 传入的 JAR 必须
匹配 `rhino-probe.lock.json` 的文件名和 SHA-256; 任务会构建 debug target/test
APK, 并同时验证 release APK/manifest 没有 probe service、process 或 Rhino
class 残留:

```powershell
$autoJs6Root = 'D:\path\to\AutoJs6'
.\gradlew.bat verifyR1RhinoProbe `
  "-Pr1RhinoProbeJar=$autoJs6Root\libs\org-mozilla-rhino-2_0_0-SNAPSHOT.jar"
```

连接设备后可只运行 Rhino/WebView 对比、双冷进程和取消 watchdog/rebind 门禁:

```powershell
$autoJs6Root = 'D:\path\to\AutoJs6'
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pr1RhinoProbeJar=$autoJs6Root\libs\org-mozilla-rhino-2_0_0-SNAPSHOT.jar" `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.supermonster003.autojs6.plugin.typescript.R1RhinoFeasibilityInstrumentationTest"
```

不传 `r1RhinoProbeJar` 时普通 debug/check/connected 流程不会打包 Rhino, 上述
instrumentation 会以 assumption 跳过. 探针的固定预期是 `unsupported`, 它不会
启用 fallback、改变 `backend=webview_worker` / `compilerAvailable=false`, 或
降级 TypeScript 版本.

连接 Android 设备后运行真实 `:typescript_compiler` Binder/FD 与 prewarm
runtime golden 门禁:

```powershell
.\gradlew.bat verifyR1BinderFdDebug
```

当前已确认 Android 12/API 31 与 Android 13/API 33 上的 debug 远程 bind,
每台设备 100 次顺序 prewarm, 1 ms timeout 后的 Worker 重建恢复, 真实
renderer 首次退出后的单次 retry, 第二次退出后的稳定失败与后续恢复, service
destruction 的完整 teardown/rebind, 以及每台设备 10 个冷 compiler process 的
身份, fingerprint 和 FD 边界. API 33/WebView 109 的干净 AVD 还通过了 none,
Node-only, Ace-only 和 Node+Ace 四种安装态. minified release 也已在 Android 13
真机通过. Rhino debug probe 已在 Android 12/API 31、WebView
`145.0.7632.120` 与 Android 13/API 33、WebView `150.0.7871.124` 通过双冷进程
unsupported 与 watchdog/rebind 门禁；两台设备均稳定停在 compiler
`typescript.js:2341:13`，且 `fallbackEligible=false`.

同两台设备还通过了 API 31/33 的 service + renderer PSS 和 distinct-request
correlation 门禁、每台 7 项 debug-only accepted/cancel/timeout/death lifecycle
门禁，以及冻结 client → 当前 service / 当前 AIDL → 冻结 service 的双向
transaction 1--6 compatibility gate. production/release 与外部
`startCompilation` 仍全部拒绝，项目编译能力没有因此开启.

Node/Ace 四态证据可通过显式 opt-in 任务在 disposable emulator 上复现. 任务
不会加入普通 `check`; 它只接受明确的 `emulator-*` serial 与完全匹配的
`MUTATE:<serial>` 确认值, 并在任何设备改动前验证两个 APK 的 package name:

```powershell
.\gradlew.bat :app:verifyR1OptionalPluginMatrix `
  -Pr1MatrixSerial=emulator-5554 `
  -Pr1MatrixNodeApk=D:\apk\node.apk `
  -Pr1MatrixAceApk=D:\apk\ace.apk `
  -Pr1MatrixConfirm=MUTATE:emulator-5554
```

该任务会安装 debug target/test APK, 依次验证 none, Node-only, Node+Ace 和
Ace-only, 最后卸载 Node/Ace 并严格复核 none. 请勿指向保存用户数据的设备.
设备指纹, WebView provider, APK SHA-256 与四次 runner 原始输出保存在
`app/build/reports/r1/optional-plugin-matrix.txt`.

API 24 既有 6 项 Bundle/FD transport 门禁保持通过. 本轮官方 Play Store x86
镜像又确认 API 24/Chrome `51.0.2704.90` 与 API 25/Chrome
`55.0.2883.91` 的 remote Binder transport 均通过，但两个 stock-old provider
都在 `compiler-host.js:73` 因 trailing call comma 报
`Unexpected token )`，随后进入有界 Worker timeout. 这不是 compiler host
兼容证据. 两台 AVD 经 Play Store 更新并明确选中同签名 Chrome
`119.0.6045.194` 后，均通过真实 TypeScript 6.0.3 Worker golden、100 次热
探针/FD、timeout recovery、teardown/rebind 和 10 个冷 compiler process 门禁，
从而关闭最后一项 R1 退出证据. Android 7 上 Chrome 可能稳定保留 compiler
process 自身的 `statm`/`status` 两个只读 procfs telemetry FD；测试只允许这个完整
二元组，其他增长或 staging archive 残留仍失败.
当前 FD 证据不能外推为完整 renderer memory leak 结论.

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
.\gradlew.bat :app:verifyOptionalPluginIndependence
.\gradlew.bat :app:verifyCompilerDistributionLock
.\gradlew.bat :app:assembleDebug
```

Debug/release APK 分别输出到 `app/build/outputs/apk/debug/` 与
`app/build/outputs/apk/release/`.

## License

项目使用 [MPL-2.0](LICENSE). TypeScript 发行物使用其上游许可证; R1 开始
打包发行物时必须同时包含 `LICENSE.txt` 和 `ThirdPartyNoticeText.txt`.
