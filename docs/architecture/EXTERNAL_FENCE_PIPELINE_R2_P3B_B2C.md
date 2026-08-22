# R2-P3b-b2c External Production-fence Pipeline

状态：内部 external production-fence source/APK 证据与 API 24/35
debug/minified-service-release runtime matrix 已完成。它不是 Public R2，也不证明
external exact 64 MiB。

后续
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)
已用专用 non-shipping TEST_ONLY debug/release-equivalent evidence build 落地
external exact-64 source/APK policy，API 24/35 最终四行均已 PASS；该后续
结论不回写或放宽本文件的 ordinary production fence。

## 声明边界

P3b-b2c 为 standalone `r2-public-preview` caller 固定三条 production-shaped
路径。fixture 与 compiler plugin 使用不同 application ID；release/debug APK
signer gate 与设备 instrumentation 都要求两者同签名，instrumentation 还要求
运行时 UID 不同、compiler permission 的 base protection 为 `signature`，且
permission owner、plugin 与 fixture signer 一致。

本切片只使用未放宽的 production Worker eligibility fence：

| 项目 | 固定值 |
| --- | ---: |
| files | 512 |
| single file | 8 MiB |
| caller + installed trusted declarations | 8,388,608 bytes |
| installed trusted declarations | 3,784,758 bytes / 108 files |
| inclusive caller source | 4,603,850 bytes |
| plus-one caller source | 4,603,851 bytes |

`4,603,850 + 3,784,758 = 8,388,608`，因此 inclusive case 是 production
8 MiB aggregate 的 exact caller boundary，不是 64 MiB archive、64 MiB caller
或 P3b-a 的 72 MiB Worker direct-framing envelope。plus-one case 也不是 malformed
ZIP：它的结构、entry metadata、manifest coverage、CRC、archive SHA-256、
request-manifest SHA-256 和 source payload SHA-256 都合法，只比 production
aggregate eligibility 多 1 byte。

Android production dispatch 仍为 **512 files、8 MiB/file、8 MiB total**。
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics` 与
`sourceMapV3` 四项 capability 继续全部为 `false`，Public R2 checklist 继续为
**0/54**。

## 三条 external caller 路径

### 正确哈希的 invalid ZIP 回归

fixture 先提交一份 archive SHA-256 正确、但内容不是 ZIP 的输入。服务必须：

1. 返回 accepted ticket；
2. 拒绝错误 job token 的 ACK，再接受 exact request ID + job token ACK；
3. exactly once 发布 `OUTCOME_FAILED + ERROR_ARCHIVE_FORMAT`；
4. terminal 不携带 diagnostics、module、manifest SHA 或其他 compilation
   payload；
5. 对预填 sentinel 的 output 执行 truncate、fsync、seek 并证明 0-byte output。

这条回归保留 archive-format validation 与异步 terminal/empty-output 边界；不得
被 inclusive 或 plus-one quota case 替代。

### Production-fence inclusive emit

fixture 从已安装 plugin artifact 读取并锁定 compiler authority，包括
distribution、declarations、profile/config 与 runtime-capability revisions 和
fingerprints。authority 读取与构造 request 之间的 package identity、source APK
identity 和 authority 值必须保持不变，避免以安装替换或 authority drift 绕过
request-time 校验。installed metadata 的 profile/runtime schema revision 分别从
`TypeScriptPluginCapabilityKeys.PROFILE_SCHEMA_REVISION` 与
`TypeScriptPluginCapabilityKeys.RUNTIME_CAPABILITY_SCHEMA_REVISION` 读取，source
gate 锁定该 capability-key mapping。

caller source 为 exact `4,603,850` bytes；与 `3,784,758` bytes / 108 files 的
installed trusted declarations 合计 exact `8,388,608` bytes。服务必须：

1. accepted 后拒绝错误 ACK，接受 exact post-return ACK；
2. exactly once 发布 `OUTCOME_EMITTED`，diagnostic count 为 0、module count 为
   1；
3. 提交非空 output，并验证 `diagnostics.json`、`modules/main.js`、
   `maps/main.js.map` 与 `compilation-manifest.json`；
4. 重新计算每份 payload SHA-256，核对 manifest payload inventory、kind、
   source inventory、module/map/source 一一对应关系，以及 terminal manifest
   SHA-256；
5. 核对 request ID、project ID、entry URI、input archive SHA-256、
   request-manifest SHA-256、compiler/declarations/options/runtime authority，
   并验证真实非空 Source Map v3 与 emitted JavaScript provenance；
   `sourceMappingURL` directive 必须位于终端，允许可选 terminal LF，但拒绝 CR。

### Production-fence plus-one quota

caller source 为 `4,603,851` bytes；它与同一 installed declarations authority
合计 `8,388,609` bytes。服务必须先 accept 并完成错误 ACK 拒绝与 exact ACK，
随后异步、exactly once 发布
`OUTCOME_FAILED + ERROR_ARCHIVE_QUOTA`。terminal 必须为零 compilation payload，
output 必须经 truncate/fsync/seek 证明为空。

本切片证明的是 remote caller 收到 quota terminal 与 empty output；它没有直接
证明 plus-one 路径的 remote compiler transient FD、spool/cursor 或 process
cleanup。相关 cleanup 不能从 b2b 的 same-UID `DEBUG` 资源矩阵外推。

三条路径在 terminal 后都等待固定 1 秒 quiet window，再断言 callback exactly
once。

## Minified-release compatibility corrections

首次 minified service release 运行暴露了两处 debug APK 无法证明的 production
兼容性问题：

- API 24 的 input/output PFD、callback 与 Binder parcel 均有效，但
  non-debuggable compiler process 无权读取 procfs FD metadata，导致两个 descriptor
  在 admission 前 fail closed；
- API 35 的 `/proc` 使用 `hidepid=invisible`。non-debuggable supervisor 无法读取
  sibling compiler 的 process snapshot，online registration 因而以约 250 ms
  retry 持续失败，最终返回 `ERR_COMPILER_UNAVAILABLE`。

最终实现不再把这两类 procfs 可见性当成 online production 前提：

- API 30+ 通过 public SDK `Os.fcntlInt(F_GETFL)` 读取 FD flags；API 24–29 通过
  随 APK 打包的极小 JNI/libc `fcntl(F_GETFL)` bridge，并处理 `EINTR`。它不调用
  hidden API、不试写 caller data，仍在 admission 与 commit 前拒绝错误 access
  mode 和 `O_APPEND`；
- online compiler/supervisor handshake 只 capture 本进程 identity。跨进程身份由
  Binder kernel `callingUid/callingPid`、固定 build-time process name、双方
  self-captured boot ID、canonical epoch、256-bit challenge token、exact remote
  descriptor/Binder identity 与 `linkToDeath` 联合约束。termination callback
  复用已登记 token、UID/PID 和 exact compiler epoch，不再重新读取 caller procfs。

procfs 没有从 process supervisor 全部移除：self identity capture，以及 Binder
death 后 quarantine 对旧 PID 的 `ABSENT/REUSED/UNREADABLE` fail-closed 观测仍保留。
新增 source/APK gate 要求 online control path 不含跨 PID capture、release
`android:debuggable=false`、debug/release APK 精确包含
arm64-v8a/armeabi-v7a/x86/x86_64 四份 bridge、每份导出 exact JNI symbol，且 DEX
不含旧 FD-procfs pathname。supervisor online identity policy 的 JVM suite 为
19/19 通过。

## Build 与 artifact 证据

最终 no-device 根聚合：

```powershell
.\gradlew.bat verifyR2PublicPreviewFixture
```

结果为 `BUILD SUCCESSFUL in 1m 22s`，513 tasks：50 executed、463 up-to-date。
定向 source/APK + release-compatibility gate 为 309 tasks：25 executed、
284 up-to-date。门禁覆盖
standalone fixture shape、不同 application ID、同 signer、signature permission、
三条 case 的 marker、production fence 常量、authority TOCTOU fence、1 秒 quiet
window、debug/release service pipeline、release service APK 的 R8/minify shape、
non-debuggable manifest、四 ABI JNI bridge 和 procfs-independent online
registration。

本轮 device matrix 使用的 artifact：

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| app debug | 5,894,744 | `89783d4d52aa9a23a0731a49f40cb14a58aac96150d61d5eb5566ecc6ac28054` |
| app release | 2,711,128 | `03dd61e71b1071e0b4ead41dd1d9a78f13ce740f15bb036732ef400231e29ddb` |
| fixture debug | 976,559 | `8f0d4dff497c1fddafbd6297af39d3f9162a08ea068f07607de85f596b241008` |
| fixture release | 691,274 | `2136073c16db0172f35ecf42e1254bc4f3c5d2cfcd74d6b0b3e9c3c851c3b029` |

这里的 “minified service release” 只表示 app release 中 compiler service
pipeline 经 R8/minify；不宣称 fixture release 自身经过 minify。

## 设备 runtime evidence

设备命令必须始终携带明确的 disposable serial。debug 使用 app debug +
fixture debug；release 使用 app release + fixture release，instrumentation
component 不变：

```powershell
adb -s <serial> install -r <app-apk>
adb -s <serial> install -t -r <fixture-apk>
adb -s <serial> shell am instrument -w -r `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/`
io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation
```

每行都先核对本地 artifact size/SHA-256，再安装同 variant app + fixture。四行均
收到完整 passed stream、`INSTRUMENTATION_CODE=-1`、ADB exit 0；crash buffer
为空，`ERR_COMPILER_UNAVAILABLE`、fatal/crash/ANR 命中均为 0。API 35 两行还
确认只有初始 compiler process start，没有约 5 秒 service rebind。

| target | Android | WebView provider | build fingerprint |
| --- | --- | --- | --- |
| `emulator-5556` | 7.0 / API 24 | `com.android.chrome` `119.0.6045.194` | `google/sdk_google_phone_x86/generic_x86:7.0/NYC/4409132:user/release-keys` |
| `968e9f18` | 15 / API 35 | `com.google.android.webview` `130.0.6723.86` | `Xiaomi/liuqin/liuqin:15/AQ3A.241006.001/OS2.0.9.0.VMYCNXM:user/release-keys` |

| target | service variant | fixture UID / plugin UID | invalid ZIP | inclusive | plus-one | runner |
| --- | --- | --- | --- | --- | --- | --- |
| `emulator-5556` / API 24 | debug | `10078 / 10090` | failed；41 → 0 B；1,543 ms | emitted；4,603,850 → 3,351 B；9,876 ms | failed；4,603,851 → 0 B；2,205 ms | PASS；code -1；ADB 0；wall 13,894 ms |
| `emulator-5556` / API 24 | minified service release | `10078 / 10090` | failed；41 → 0 B；1,608 ms | emitted；4,603,850 → 3,351 B；9,060 ms | failed；4,603,851 → 0 B；1,672 ms | PASS；code -1；ADB 0；wall 12,631 ms |
| `968e9f18` / API 35 | debug | `10314 / 10312` | failed；41 → 0 B；1,799 ms | emitted；4,603,850 → 3,351 B；19,867 ms | failed；4,603,851 → 0 B；1,532 ms | PASS；code -1；ADB 0；wall 23,706 ms |
| `968e9f18` / API 35 | minified service release | `10314 / 10312` | failed；41 → 0 B；1,596 ms | emitted；4,603,850 → 3,351 B；7,861 ms | failed；4,603,851 → 0 B；1,338 ms | PASS；code -1；ADB 0；wall 11,218 ms |

API 24 另以最终 debug app 与 SHA-256 为
`ffe6c1de01cebc3326c17e38d7ad87062454e991ea34d064ebbf7995ddbeb689`
的 androidTest APK 定向复跑完整 `R2OutputFdCommitterInstrumentationTest`：
6/6 PASS、`INSTRUMENTATION_CODE=-1`、ADB exit 0、wall 622 ms。它覆盖
append-mode admission 拒绝且 sentinel 不变、正常 commit、terminal-gate 与
partial-copy rollback、cancel/failure finalizer，以及 descriptor 已关闭时的
unproven fail-closed。

每台设备最后都恢复并重新核对 debug app + fixture；两包保持相同 signer、不同
runtime UID 且均为 `DEBUGGABLE`。

## 本切片自身尚未证明

P3b-b2c 使 internal provisional checklist 达到 **18/18**；它不勾选下方任何
Public R2 项。已完成的四行只关闭 production-fence external caller 的窄路径。
P3b-b2c 本身不证明：

- external-UID exact-64-MiB debug 或 minified-service-release full pipeline；
- plus-one remote transient FD/spool/cursor/process cleanup；
- 零 spin 自然 parse/semantic/emit 中途 cancel/timeout 或自然 ingress timeout；
- disposable-device LMK/OOM、dual process death 或 allocator reclaim；
- Unicode/high-token/adversarial distribution；
- 128 MiB output container / 256 MiB expanded file-backed writer；
- 正式 archive/manifest/diagnostics schema、tsconfig/NodeNext 或其他 R2 exit
  gates。

后续 P3b-b2d 已关闭上列第一项；最终 TEST_ONLY device matrix 的 API 24/35
四行均已 PASS。它只使用 `r2CapacityEvidenceDebug` /
`r2CapacityEvidenceRelease` non-shipping TEST_ONLY evidence app；release
variant 是 minified/non-debuggable release-equivalent build，不是 shipping
production release。b2c + b2d 的 internal provisional evidence 为
**19/19**。后续 P3b-b2e 又在 API 24/35 ordinary debug 关闭 fixed-5s
partial-ingress natural timeout，并补 fixed-10s Binder end-to-end natural
deadline/final cleanup；b2e 当时使 internal provisional evidence 达到
**20/20**，但当时仍未证明 remote plus-one cleanup。完整 b2e 边界见
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)。

后续 P3b-b2f 又在 API 24/35 normal/evidence Debug/Release 关闭上列
production-fence 与 exact-64 `+1` 的 remote transient
FD/spool/cursor/process cleanup，并证明 receipt 在 callback 前创建、fixture
只在 callback 后读取并关联 receipt、连续 5 次 current snapshot 全零，以及
same-Binder successor 恢复。它不回写 b2c 历史 **18/18**、b2d 历史
**19/19** 或 b2e 历史 **20/20** 结论；b2f 完成时 internal provisional
evidence 为 **21/21**。完整 b2f 边界见
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)。

后续 P3b-b2g 在 API 24/35 ordinary same-UID Debug 关闭零 spin 自然
parse/semantic/emit cancellation 窄矩阵，当前 internal provisional evidence
为 **22/22**。这些阶段的自然 timeout、真实 LMK/OOM、
Unicode/high-token/adversarial、output 128/256 MiB、formal schemas/NodeNext
与其余项目仍开放。
完整 b2g 边界见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)。
因此普通 Debug/Release 的
512 files、8 MiB/file、8 MiB total production fence、四项 `false` capability
和 Public R2 **0/54** 状态均不得改变。
