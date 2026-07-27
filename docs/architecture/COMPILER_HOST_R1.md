# Restricted Compiler Host R1

R1 在插件私有的 `:typescript_compiler` 进程内提供一个受限
WebView/Web Worker host, 用真实 `typescript@6.0.3` 完成运行时探针. 此 host
只验证发行物, JavaScript 隔离和单文件 Compiler API golden, 不接入 AutoJs6
项目执行链, 也不构成 R2 的生产编译服务.

`compilerAvailable=false` 仍是对外能力协商的权威值. `backend=webview_worker`,
`serviceStatus=ready` 和发行物 fingerprint 只说明 R1 probe host 已经成功加载,
不能解释为 `startCompilation` 可以接受项目.

## 组件和线程

```text
ITypeScriptCompilerPlugin.prewarm
        |
        | primitive-only Bundle
        v
TypeScriptWebViewCompilerHost
        |
        | exact-origin WebMessagePort
        v
compiler-host.js
        |
        | request ID + bounded in-memory VFS
        v
compiler-worker.js
        |
        v
typescript/6.0.3/lib/typescript.js
```

- compiler WebView 由 compiler service 独占, 不复用 Ace activity, editor WebView
  或 Language Service.
- Kotlin 主线程只创建, 配置, 恢复和销毁 WebView/MessagePort. TypeScript 加载和
  `transpileModule` 均在专用 Web Worker 内执行.
- Binder worker thread 等待有界 probe 结果. 主线程调用 probe 会安全失败, 避免
  等待自身造成死锁.
- host 同一时间只允许一个活动 probe. request ID 和 Worker generation 同时匹配
  后才接受结果, 迟到或串线消息被忽略.
- R1 probe protocol 是 app 内部实现细节. 它不属于 AIDL, API AAR, Binder Bundle,
  input/output archive, diagnostics schema 或 compiler profile.

## HTTPS asset origin

host 只加载 Android 为应用资产保留的 origin:

```text
https://appassets.androidplatform.net/typescript-compiler-r1/
```

allowlist 精确包含:

```text
index.html
compiler-host.js
compiler-worker.js
typescript.js
```

其中 `typescript.js` 是
`typescript/6.0.3/lib/typescript.js` 的受控 runtime alias. WebView 不直接暴露
完整 APK assets 树. 请求必须是 HTTPS `GET`, authority, encoded path, query 和
fragment 必须精确匹配; 未匹配请求返回拒绝响应, 不回落到网络.

每个允许响应都携带 `no-store`, CSP, same-origin resource policy,
`nosniff` 和受限 permissions policy. 页面 CSP 只允许同源 script/worker;
Worker CSP 只允许加载同源 compiler script. host 不使用
`addJavascriptInterface`.

WebView 和 Service Worker 设置同时禁止:

- file 和 content access.
- network load 和 mixed content.
- DOM storage, database 和 Web Storage 持久状态.
- cookie 和 third-party cookie.
- image, download, geolocation, media permission 和 file chooser.
- JavaScript window opening, dialog 和非入口页面导航.
- HTTP auth, client certificate 和 SSL error 继续执行.

应用 Manifest 不声明 `INTERNET` 权限. 即使 allowlist 或页面脚本发生错误,
WebView 也不能把未处理 URL 当作远程资源获取.

## Worker 隔离和消息

原生侧通过带精确 target origin 的 `WebMessagePort` 把一个端口交给 host page.
页面只负责 Worker 生命周期和消息转发. 每条返回消息都证明页面全局没有 `ts`;
probe 还验证 Worker 环境存在, 且 `window`, `document` 和原生 bridge 均不存在.

原生请求只含内部 request ID 和固定的有界内存 VFS fixture. Worker 返回有界的
golden 证据; TypeScript 源码, emitted JS, Source Map 和完整 diagnostics 都不进入
Binder Bundle. Worker 不执行 emitted JavaScript. 它使用 TypeScript JS parser
验证 emit 无语法诊断, 检查 enum runtime assignment, 并计算与桌面 reference
一致的 SHA-256.

此固定 probe VFS 不接受 AutoJs6 项目输入. R2 必须通过 FD archive 建立独立的
项目 VFS 和正式 manifest/diagnostics schema, 不能把 R1 JSON 消息直接提升为公共
协议.

## `prewarm` 语义

`prewarm(options)` 的公共 options 仍只允许:

- `contractVersion`.
- `contractHash`.
- `timeoutMs`.

Debug instrumentation 可以使用 app-internal lifecycle control key 建立确定性的
held pending. 该 key 不属于公共 contract, AIDL/API AAR 或 release 行为, 也不能
由调用方用于普通 prewarm.

接收端先执行 primitive-only, marshalled size, key count, contract 和 timeout
校验. 校验失败时返回现有稳定错误码. 合法调用同步启动或复用受限 host, 等待真实
Worker golden:

- 成功时 result 使用 `compilerInfo` schema, 不携带 `errorCode`, 并可报告
  `backend=webview_worker`, `serviceStatus=ready`, distribution fingerprint 和
  declarations fingerprint.
- Worker, runtime version, protocol 或 timeout 失败时, result 使用相同
  `compilerInfo` schema 并附加有界 `errorCode/errorMessage`.
- prewarm 不读取 input/output FD, 不创建 job token, 不发送 compilation callback,
  也不保证下一次项目编译成功.

无论 prewarm 是否成功, R1 均保持:

| 能力 | R1 值 |
|---|---|
| `compilerAvailable` | `false` |
| `projectCompilation` | `false` |
| `semanticDiagnostics` | `false` |
| `sourceMapV3` | `false` |
| `startCompilation` | `accepted=false` |
| `cancelCompilation` | `false` |

因此调用方必须先以 `compilerAvailable` 判定可否提交项目, 不能只看 backend 或
service status.

## TypeScript 6.0.3 runtime golden

probe 固定使用:

```text
target          = ES2018
module          = CommonJS
sourceMap       = true
inlineSourceMap = false
inlineSources   = false
newLine         = LF
```

enum fixture:

```typescript
enum Direction { Up, Down }
(self as any).__r1ProbeValue = Direction.Down;
```

syntax fixture:

```typescript
const broken: = 1;
```

真实 `typescript@6.0.3` 的 golden 为:

| 项目 | 值 |
|---|---|
| runtime `ts.version` | `6.0.3` |
| enum source URI | `autojs://source/r1-probe/enum.ts` |
| enum JS SHA-256 | `75c017bb059e4e246ed6db84c9ea19121df3e0473c428d4aa38e4d2fee1b51a9` |
| enum map SHA-256 | `f6b14de7b69b6cf5e52b247f6ab3f8c0625cc7070fb2a81d05b16e06331fb02f` |
| enum JS parse diagnostics | `0` |
| enum runtime assignment | `self.__r1ProbeValue = Direction.Down` |
| syntax source URI | `autojs://source/r1-probe/syntax-error.ts` |
| diagnostic category/code | `1 / 1110` |
| diagnostic start/length | `14 / 1` UTF-16 code units |
| diagnostic line/column | `1 / 15` for the internal one-based probe view |
| diagnostic message | `Type expected.` |
| syntax JS SHA-256 | `6b57a203bc8cd6b27e7b8383c924ba13cb9365de40637f31a8a839385111ff75` |
| syntax map SHA-256 | `1f5041e240885a38ac5ffcb0f88cae4c149d875805361a30f3a21078c8c38fea` |
| Source Map | `version=3`, non-empty `mappings`, no `sourcesContent` |

enum JS 的相同 golden bytes 已在桌面 reference 中执行并得到
`Direction.Down == 1`; Android Worker 验证完全相同的 emit hash, runtime
assignment 和零 JS parse diagnostic. 这只证明 R1 单文件探针, 不证明项目级
semantic diagnostics 或 R2 正式 emit.

内部 probe 使用 one-based line/column 便于断言, 但该表示不是公共 diagnostics
schema. R2 冻结 schema 时仍需明确 UTF-16 offset, line/column base 和 message
chain 规则.

## Rhino compiler context 可行性探针

R1 另有一个严格 opt-in 的 debug 探针, 只回答“AutoJs6 当前实际消费的 Rhino
能否直接解析同一份 TypeScript 6.0.3 compiler”. 它不属于 compiler backend,
不进入 release, 也不能把结果转换成 fallback 资格.

身份由根目录 `rhino-probe.lock.json` 三层锁定:

| 项目 | 锁定值 |
|---|---|
| AutoJs6 Rhino artifact | `org-mozilla-rhino-2_0_0-SNAPSHOT.jar` |
| artifact SHA-256 | `712a73d62edc56eb36a909a229eca87ac433afa7aaceb6d60e3acbbc75a9533a` |
| Rhino source commit | `e0dfa6f316bfb5ed99fff3d0cf33310e18458d3d` |
| AutoJs6 consumer commit | `f78c281985618205d2dc3dd056cf9bf042cc5996` |
| TypeScript compiler asset SHA-256 | `569177652966bd528c319171c7dd22860dbf72bde116cbc4f644f1d02bb12e39` |

只有显式传入 `-Pr1RhinoProbeJar=<path>` 时, Gradle 才在 debug variant 加入该
JAR; 文件名和 SHA-256 必须与 lock 完全一致. 普通 `check`, 普通 debug 和
`connectedDebugAndroidTest` 不需要该属性, 对应 instrumentation 使用 assumption
跳过. release APK/merged manifest 门禁无条件拒绝 probe service/process、Rhino
class marker 或 artifact 残留.

两个 commit 是经审查源码 checkout 的 provenance 声明; JAR 本身不能反推出构建
commit. 可执行 artifact 的独立门禁是精确 SHA-256, Gradle 同时固定断言这两个
provenance commit, instrumentation 再回报相同身份.

debug-only `R1RhinoProbeService` 运行在独立
`:typescript_rhino_probe` 进程, `exported=false`, 使用 Messenger 和
primitive-only Bundle. 服务以反射装载 Rhino, 固定 `VERSION_ES6`,
`optimizationLevel=-1`, `initSafeStandardObjects` 和 deny-all `ClassShutter`;
它不安装 Java bridge. 服务重新读取
`assets/typescript/6.0.3/lib/typescript.js`, 独立计算实际 SHA-256 并与同一 APK
的 `distribution.json` 比较, 随后把该 Reader 真正交给 `evaluateReader`.

每个结果记录 artifact/compiler/distribution/declarations fingerprint、执行阶段、
第一个有界错误位置、elapsed time、进程 PID 和前后 PSS. 无论解析失败还是意外
成功, `supported=false` 与 `fallbackEligible=false` 都是硬编码值; production
能力仍保持 `backend=webview_worker` 和 `compilerAvailable=false`.

取消先 interrupt parser thread. 专用 cancellation fixture 仍由
`evaluateReader` 读取同一 compiler asset, 但模拟忽略 interrupt 的 Reader;
750 ms watchdog 会只终止 disposable probe process. instrumentation 等待 Binder
death, 记录取消延迟, 再重新 bind 新进程并运行普通 unsupported probe. 正常门禁
还先完成真实 WebView prewarm golden, 再从至少两个冷 Rhino 进程比较稳定身份和
首个语法失败邻域, 不绑定完整 stack trace 或 provider 文本.

当前桌面预检已用锁定 JAR、`VERSION_ES6` 和 `optimizationLevel=-1` 对同一
9,144,216-byte compiler 执行解析: 首个错误稳定在 `typescript.js:2341` 的
`for (const value of iter)`. 因此预期结论是 **unsupported**, 不是降级
TypeScript 的理由. Android 12/API 31、WebView `145.0.7632.120` 已通过真实
WebView prewarm、两个冷 Rhino process 的稳定 unsupported/fingerprint 和
watchdog/Binder death/rebind 门禁; bounded `R1RhinoProbe` log 记录 PSS、elapsed、
首个错误位置与取消延迟. API 31 两个冷进程分别为 352/356 ms、
`23574→93723`/`23551→95640` KiB，watchdog 801 ms；Android 13/API 33、
WebView `150.0.7871.124` 分别为 334/339 ms、
`22452→63131`/`22441→63385` KiB，watchdog 771 ms. 两台设备均稳定报告
`supported=false`、`fallbackEligible=false`、`EvaluatorException` line 2341 /
column 13，并在 watchdog kill 后成功 rebind.

## 生命周期和恢复

实现包含以下保护:

- probe timeout 后终止并重建 Worker; 同一恢复周期最多尝试一次 Worker restart.
- timeout recovery 使用 epoch 标识并合并同一恢复周期的执行, stale recovery
  不能销毁已经替换的 session.
- API 26+ 的 renderer death callback 销毁失效 WebView, 当前 probe 最多重试一次.
- service destruction 完成 pending probe, 关闭 MessagePort, 停止 Worker 并销毁
  WebView.
- API 28+ 为 `:typescript_compiler` 使用独立 WebView data directory suffix.

这些路径只有在相应 instrumentation 和资源观测通过后才能作为 R1 完成项.
当前双设备 instrumentation 已覆盖 timeout 后的 Worker rebuild, 并使用真实
WebView renderer termination 验证当前 probe 第一次退出只重试一次, 第二次退出
稳定失败, 随后的普通 probe 可重新创建 session 并恢复. service destruction
门禁先建立成熟 ready session, 再安装一个不向 Worker dispatch 的确定性 native
pending; 最后一次真实 unbind 验证 close report 在 waiting Binder latch 之前发布,
并验证后续两次重新 bind/probe. 因此它覆盖 native pending 与 host ownership 的
teardown 顺序, 不宣称覆盖 Worker 正在计算时的取消语义.

### Accepted transport lifecycle 门禁

R1 不启用项目编译；production、release 与外部 `startCompilation` 仍稳定拒绝.
debug APK 仅对 same UID 开放一个不读取 input、不生成项目 output 的私有 lifecycle
probe，用于运行 accepted/cancel/timeout/death 状态机:

- active queue 容量为 1；饱和请求返回 `ERROR_BUSY`，且不改写 caller output
  sentinel.
- client 收到 accepted ticket 后，以第二次同步 transaction 发送内部
  acknowledgement. 这是 callback 必须晚于 `startCompilation` 返回的明确 barrier；
  timeout 仍从 admission 开始计算.
- 未 acknowledgement 的 probe 在 `min(timeoutMs, 5s)` 内静默释放 FD. 历史最多
  保留 256 项，满后拒绝新 debug admission 直到 service restart，以保持 duplicate
  reject 与 cancel idempotence 而不让内存无界增长.
- callback death 通过 `linkToDeath` 清理；service death 由测试 client 的
  exactly-once arbiter 合成本地终态. 后者只是 R1 代表性 client 证据，不表示已经
  接入 AutoJs6 正式 host.
- release 构建不创建 coordinator/executor；APK 门禁要求 transport control key、
  executor thread name、coordinator marker 与 correlation control marker 只存在于
  debug.

`R1AcceptedLifecycleInstrumentationTest` 已在 API 31/33 各通过 7 项. accepted
active 时 `/proc/<compiler-pid>/fd` 精确观察到两份 `.archive` service 引用；
各终态后 `.archive` target 归零且 `(pid,starttime)` 身份保持不变. compiler
总 FD 有界与最终回到 warm baseline 由下述 100 次真实 Worker probe 独立验证，
不把 process/runtime 首用持久 descriptor 计作 accepted job 泄漏. 其余门禁覆盖 exactly-once
completion、幂等 cancel、timeout、callback process death、compiler process
death/rebind、capacity reject 和未 acknowledgement reaper.

### PSS 与 request correlation 门禁

`R1PssAndCorrelationInstrumentationTest` 为 API 31/33 提供两个独立门禁:

- PSS 门禁在 10 次真实 Worker warmup 后, 从
  `dumpsys activity services <package>` 同时解析 compiler service 和 WebView
  sandbox renderer 的 `ServiceRecord`. compiler record 必须使用
  `:typescript_compiler` process; renderer record 的 `packageName` 必须是插件包,
  `app=ProcessRecord` 必须给出唯一存活 renderer PID, 且 renderer 的
  `Client AppBindRecord` 必须反向指向同一个 compiler service PID/process.
  门禁同时绑定两个 PID 的 `/proc/<pid>/stat` start time, 避免 PID reuse 或
  renderer replacement 被误当成同一采样对象.
- warmup baseline 和随后 100 次热 probe 各取 5 组 compiler service/renderer
  `TOTAL PSS` 样本. 每组都是真实 shell `dumpsys meminfo <pid>` 结果. 当前 R1
  校准 guardrail 为 compiler service 最大 128 MiB, renderer 最大 192 MiB,
  两者合计最大 256 MiB, 且热 probe 后合计 median 相对 baseline 增长不得超过
  48 MiB. 该增长上界来自两轮 clean-install 校准: API 33 为 30.9--31.0 MiB,
  API 31 为 33.0--40.0 MiB, 并在较高观测值上保留约 20% 余量. 这些是阻止明显
  回归和泄漏的保守 R1 上界, 不是 R6 的产品内存预算.
- correlation 门禁只在 debug build 且调用 UID 为插件自身 UID 时识别 app-internal
  control key. host 在真实 Worker probe dispatch 前, 将带错误 request ID 的
  `probeError` 注入同一 JSON parser 和 native message handler, 证明它没有完成或
  破坏当前 pending; 随后原请求仍必须从真实 Worker 返回完整 golden.
  release build 忽略该 key, 它不属于公共 prewarm Bundle contract, AIDL 或 API AAR.

两项都会以 `R1PssCorrelation` tag 记录设备/API/provider、精确 PID/start time、原始
PSS 样本、median/growth 和 pass/fail 上界. 双真机执行均已通过：API 31 baseline /
final combined median 为 `160178/193540` KiB，增长 `33362` KiB；API 33 为
`154002/184126` KiB，增长 `30124` KiB. 两台设备的错误 request ID 均被忽略，
随后真实 Worker 原请求仍完成完整 golden.
普通 `connectedDebugAndroidTest` 会自动发现这两个 test; 也可以通过 Android
instrumentation class filter 只运行该测试类.

## 当前证据和限制

当前 debug instrumentation 已在 Android 12/API 31 与 Android 13/API 33
两台真实设备上通过:

- API 31 使用 WebView `145.0.7632.120`; API 33 使用 WebView
  `150.0.7871.124`.
- 绑定真实 `:typescript_compiler` 远程 Binder.
- 每台设备运行 100 次顺序 prewarm, 每次完成真实 Worker `ts.version` 和
  golden 校验.
- 以 1 ms deadline 强制 timeout, 验证 Worker rebuild 后下一次 prewarm 恢复.
- 确定性阻塞 main thread, 让连续两个 1 ms probe 在 recovery 执行前 timeout,
  再验证排队的普通 probe 在合并 recovery 后恢复.
- 真实终止 WebView renderer 一次时重建 host 并完成 pending probe; 连续终止两次
  时不进行第二次 retry, 返回 compiler unavailable, 随后的普通 probe 恢复.
- 每台设备完成 10 轮 `:typescript_compiler` process restart, 每轮验证 Binder
  death 和旧 `(pid,starttime)` 身份消失, 下一轮重新绑定新的 remote process,
  并验证 runtime golden 及稳定的 distribution/declarations fingerprint.
- 每台设备在 10 次 warmup 后完成 100 次热 prewarm; FD snapshot 绑定相同
  `(pid,starttime)`, 拒绝部分 shell 观测和 retained `.archive`, 每 10 次采样,
  最终 compiler service FD 精确回到 baseline. 最新回归中的 API 31
  baseline/final 为 `178/178`, API 33 为 `205/205`; 数值只描述当前
  设备/provider, 不是跨设备常量.
- 10 个冷 compiler process 均从无缓存 process 的状态开始. API 31 的稳定 FD
  count 为 177; API 33 为 204 或 208, 并受首个冷 process baseline `+4` 上界约束.
- 最后一次真实 unbind 在成熟 host 和 held native pending 同时存在时触发 service
  destruction; MessagePort, WebView, ServiceWorker client, handler callback 和
  session/recovery state 均在 pending latch 之前完成清理, 随后两次 rebind probe
  成功.
- API 33 AVD 使用 WebView `109.0.5414.123`, 在 none, Node-only, Ace-only 和
  Node+Ace 四种真实 APK 安装态下完成相同 remote prewarm golden/fingerprint.
  `:app:verifyR1OptionalPluginMatrix` 只接受显式 disposable emulator serial,
  Node/Ace APK 和匹配的 mutation confirmation; 它先校验 APK package name,
  再以 Gray-code 顺序成对传入 expected runner arguments, 由 androidTest shell
  observation 严格核对实际安装态. 任务同时保存设备/provider/APK digest 与四次
  runner 原始输出, 并在 finally 中清理、复核 none. 普通 connected gate 不查询
  package state, 该 mutation task 也不接入普通 `check`.
  静态门禁同时禁止 production source, manifest 和直接 dependency 发现或依赖
  Node/Ace. 这不构成 Rhino/Node 执行验证.
- 返回稳定 distribution/declarations fingerprint.
- prewarm 前后持续报告 `compilerAvailable=false` 及所有项目编译能力为 false.
- 每台设备通过 7 项 debug-only accepted lifecycle 门禁，包括有界 admission、
  exactly-once completion、cancel/timeout、callback/service death、FD cleanup 与
  rebind；release APK 不含 transport/correlation control 或 executor marker.
- 每台设备通过 service + renderer PSS guardrail 与 distinct-request correlation
  门禁；combined median 增长分别为 API 31 `33362` KiB、API 33 `30124` KiB.
- 锁定 Rhino artifact 在两台设备各完成两个冷进程 unsupported probe 及
  watchdog/Binder death/rebind，首个错误稳定为 line 2341 / column 13.
- 在 Android 13/API 33 安装临时测试签名的 minified release APK, 通过独立
  framework-only `release-smoke` helper 完成 discovery, remote bind 和相同
  Worker golden, 包括无 inline source content.

API 24/25 modern-provider matrix 另在两台官方 Play Store x86 AVD 上完成:

| API | system fingerprint | stock 负基线 | 选中的通过 provider |
|---|---|---|---|
| 24 | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/4409132:user/release-keys` | Chrome `51.0.2704.90` | `com.android.chrome` `119.0.6045.194` |
| 25 | `google/sdk_google_phone_x86/generic_x86:7.1.1/NYC/4316688:user/release-keys` | Chrome `55.0.2883.91` | `com.android.chrome` `119.0.6045.194` |

Chrome 119 是 Play Store 安装的 system-package 更新，`versionCode=604519426`,
`minSdk=24`, `targetSdk=34`. 两台的 191,313,115-byte base APK SHA-256 均为
`e16c169f6a71c6d688ced787d35b20660ee8ef11870a99873be93fc0446ddd74`;
PackageManager 的更新包和各自 stock Chrome 均显示相同 signature identity
`[e3ca78d8]`，更新包 signer certificate SHA-256 为
`f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83`.

每台 AVD 都先通过单次
`R1OptionalPluginMatrixInstrumentationTest#installedOptionalPluginStateDoesNotAffectRemotePrewarmGolden`,
随后通过完整 5 项 `R1BinderFdInstrumentationTest`、连续 timeout epoch
recovery、service destruction/rebind 以及包含 10 个冷 compiler process 的
`R1ProcessRestartInstrumentationTest`. 完整 Binder/FD 复验分别为 API 24
`OK (5 tests)` / `5.286s`、API 25 `OK (5 tests)` / `8.519s`; runtime 返回
`backend=webview_worker`, `status=ready`, distribution fingerprint
`3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658` 和
declarations fingerprint
`6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f`,
同时所有项目编译能力仍为 false.

Chrome 119 在 Android 7 上可能于 warm baseline 后懒加载 compiler process
自身的 `/proc/<pid>/statm` 与 `/proc/<pid>/status` 两个只读 telemetry FD. API 24
复验为 `135→137`, API 25 同时覆盖了 `138→140` 和已成熟 baseline 的
`140→140`. 最终 FD 门禁只允许目标 PID 等于当前 compiler process、无
replacement 的完整二元组；任何
其他新增、缺项、baseline descriptor replacement、持续增长或 `.archive` 残留
仍失败. 同一窄化门禁在 API 31/33 真机分别以 `178→178`、`205→205` 零增长
通过.

stock Chrome 51/55 仍是明确的不兼容负基线：两者 remote Binder
discovery/transport 均通过，但在解析 `compiler-host.js:73` 的 trailing call
comma 时报告 `Unexpected token )`，随后由 30 秒有界 Worker timeout 收口.
这不是降级 host wrapper 或 TypeScript 的理由，也不能把 Chrome 119 的证据
外推到 stock provider.

### R1 完成后的范围边界

- `onRenderProcessGone` 从 API 26 才可用. API 24/25 的证据依赖有界 timeout、
  epoch recovery 和完整 session teardown，不声称覆盖 renderer callback recovery.
- 真实项目 Worker 在途取消仍未覆盖；R1 没有启用项目 compilation admission，
  该限制不应被 debug lifecycle probe 外推为生产编译能力.
- 当前 FD/PSS guardrail 用于阻止明确回归，不构成完整 renderer memory-leak
  结论或 R6 产品内存预算.
