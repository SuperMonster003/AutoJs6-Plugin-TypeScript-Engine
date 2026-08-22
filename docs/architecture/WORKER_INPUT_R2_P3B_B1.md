# R2-P3b-b1 Android Device Capacity and Exact Realm Retirement

状态：内部 `DEBUG` 设备容量切片；不是完整 R2-P3b-b，也不是 Public R2。

后续 R2-P3b-b2a 已在同一 production fence 下补充 synthetic phase-entry
hard-stop 与 active exact-64-MiB renderer-loss/recovery evidence；见
[R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss](WORKER_FAULT_R2_P3B_B2A.md)。
R2-P3b-b2b 又补充 repeated exact-64-MiB resource、自然 ingress cancel cleanup
与 compiler-service startup orphan scavenging；见
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](WORKER_RESOURCE_R2_P3B_B2B.md)。
P3b-b2c 后续冻结 ordinary external production fence，P3b-b2d 再用 non-shipping
TEST_ONLY evidence build 完成 external exact-64 四边界；API 24/35 最终四行
均已 PASS。见
[R2-P3b-b2c External Production-fence Pipeline](EXTERNAL_FENCE_PIPELINE_R2_P3B_B2C.md)
与
[R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline](EXTERNAL_EXACT64_PIPELINE_R2_P3B_B2D.md)。
本文件仍只记录 P3b-b1 的 capacity/realm-retirement 边界。

P3b-a 只在 Node/V8 desktop child 中证明 Worker 的 4607 files/72 MiB
representation 与 retained-reference 上界。P3b-b1 把冻结 caller input 的两个
64 MiB 边界实际送过 Android Binder、private ZIP/VFS、String WebMessage、Web
Worker、TypeScript Program 与 output commit，并为每个成功 project 建立 exact
WebView realm retirement。production admission、安全 capability 与 public
contract 均未放宽。

## 本切片改变的内容

- 新增 same-UID、`BuildConfig.DEBUG` 限定的
  `expandedWorkerCapacity` control。它只让 instrumentation 使用
  `FILE_BACKED_ARCHIVE` 的 4607 files、8 MiB/file、72 MiB Worker envelope；
- project input/output raw chunk 从 16 KiB 调整为 64 KiB，String WebMessage
  JSON/UTF-8 上限同步调整为 128 KiB；window 仍为 1；
- input framer 暴露 sent-frame、maximum encoded frame、accepted/total bytes、
  file cursor 与 completion telemetry；
- 每个成功 R2 project 在 terminal publication 前销毁其 exact WebView session，
  取得 `WebView.destroy()` 或 renderer-gone proof 后才返回成功。下一 job 懒创建
  新 session；
- 新增常量内存的 STORED/DEFLATED 64 MiB inclusive/`+1` ZIP fixture，以及
  Binder full-pipeline instrumentation。

本切片没有改变：

- 普通 debug/release production host 的 512 files、8 MiB/file、8 MiB total
  admission；
- AIDL、冻结 `TypeScriptCompilerContract`、ZIP v1、profile/config revision；
- 512 outputs/1 MiB aggregate output 与 6 MiB typed-result provisional 配额；
- 四项 capability advertisement 和 Public R2 `0/54` checklist。

## Authority 与不可外泄边界

```text
public/release request
  -> no expanded control
  -> PROVISIONAL_WORKER: 512 files / 8 MiB total

same-UID DEBUG instrumentation request
  -> KEY_ENABLED=true
  -> KEY_EXPANDED_WORKER_CAPACITY=true
  -> FILE_BACKED_ARCHIVE: 4607 files / 72 MiB total
```

Binder parser 同时要求 debug build 与 caller UID 等于 plugin UID。
`R2CompilationControls` 另把
`PUBLIC_PREVIEW + expandedWorkerCapacity=true` 定义为构造期非法状态；因此即使
未来 parser 重构，public control 也不能携带该开关。release build 的 host 测试
入口还会直接 fail closed。

这个 seam 用于收集解除围栏前的证据，不是隐藏的 production capacity toggle。

## 64 KiB transport

最初的 16 KiB raw frame 在 API 35 的 exact 64 MiB direct-device test 中于
120 秒 deadline 前未完成 ingress。23 KiB 诊断版本在同一 deadline 时只接受了
`57,797,632 / 70,893,622` bytes，停在 caller file index 6，共发送 2473 frames；
失败点是 ingress throughput，不是 parse、emit 或 OOM。

当前 wire budget 为：

| 维度 | 上限 |
| --- | ---: |
| input/output raw chunk | 64 KiB |
| canonical padded Base64（64 KiB raw） | 87,384 bytes |
| 完整 JSON/String WebMessage | 128 KiB |
| outstanding frame window | 1 |
| 设备实测最大 encoded frame | 87,525 bytes |

framer 仍只持有一个 reusable raw buffer、一个 exact file cursor 与 streaming
SHA-256。ACK 前不读下一 chunk；cancel、timeout、session failure 与 release 都清零
buffer 并关闭 cursor。128 KiB 是内部 message bound，不修改 Binder metadata
64 KiB 上限。

## Streaming capacity fixture

`R2CapacityArchiveFixture` 不提交 64 MiB 静态资源，也不调用 `readBytes()`：

- source body 由可重放的 8 KiB xorshift block 流式产生；
- payload 第一遍计算 size、CRC-32、SHA-256 与 raw-DEFLATE size；
- ZIP 第二遍写入并重新核对全部四项；
- 使用严格 ZIP32 local/central/EOCD records，无 descriptor、extra、comment、
  encryption 或 ZIP64；
- 1 个 4 KiB main root 通过 triple-slash references 引用 8 个大 `.d.ts`；
  大 payload 全在 block comment 中，token 与 emitted output 保持很小；
- filler 不含 `*` 或 `/`，只有 suffix 能关闭注释；
- 生成过程只保留小 buffer、Deflater state、manifest 与 entry metadata。

四个精确边界：

| fixture | 精确轴 | production 预期 |
| --- | ---: | --- |
| `STORED_CONTAINER_INCLUSIVE` | container = 67,108,864 | accept |
| `STORED_CONTAINER_PLUS_ONE` | container = 67,108,865 | quota |
| `DEFLATED_UNCOMPRESSED_INCLUSIVE` | expanded = 67,108,864 | accept |
| `DEFLATED_UNCOMPRESSED_PLUS_ONE` | expanded = 67,108,865 | quota |

每个 `+1` fixture 会先用 container/expanded 各放宽 1 byte 的 production
streaming validator 证明 ZIP layout、actual inflate、CRC、size 与 compression
ratio 有效，再用正式 `ZipArchiveV1Limits.INPUT` 证明唯一失败原因是 `QUOTA`。
STORED `+1` 的真实 Binder pipeline 在 private capture 阶段先命中 container
quota；DEFLATED `+1` 在 ZIP expanded accounting 命中 quota。

## Exact success retirement

Worker `finally` 能断开 Program/VFS 引用，但不能决定 WebView/V8 allocator page
何时归还。P3b-b1 因此把成功路径固定为：

```text
typed project result accepted
  -> retain exact pending/session identity
  -> destroy exact WebView session
  -> close native port and clear generation
  -> status = BOOTSTRAP
  -> bind outcome to exact snapshot
  -> release input and publish terminal success
  -> next job lazily creates a newer session
```

destroy 失败时每 250 ms 保留同一 pending identity 重试；不会先清除 pending 后再
销毁，也不会制造 synthetic success。main-handler post/retry 无法调度时进入既有
external-supervision fail-closed 状态。cancel/timeout 在 retirement 窗口获胜时仍按
operation handle 的 first-wins terminal CAS 收口。

direct instrumentation 证明 generation 1 在大项目成功前已退休，随后小项目使用
更高 generation 成功。这个设计提供确定的 realm lifetime boundary，但不能替代
renderer/compiler-service PSS、LMK 或 allocator reclaim 测量。

## Android evidence

### Direct file-backed Worker capacity

`expandedWorkerAcceptsExact64MiBCallerAndRetiresBeforeSmallSuccessor` 使用：

- caller：67,108,864 bytes；
- 108 个 trusted TypeScript libraries：3,784,758 bytes；
- Worker total：70,893,622 bytes；
- 117 files、1419 input frames、maximum encoded frame 87,525 bytes；
- exact large success、session retirement、spool unlink 与 newer-session small
  successor。

| serial | API / WebView | 结果 | 设备时间 |
| --- | --- | --- | ---: |
| `emulator-5556` | API 24 / `119.0.6045.194` | PASS | compile 46,603 ms；runner 50.129 s |
| `BH900ASK9E` | API 28 / `126.0.6478.186` | PASS | compile 101,069 ms；runner 106.237 s |
| `968e9f18` | API 35 / `130.0.6723.86` | PASS | runner 105.159 s |

### Binder ZIP boundary matrix

`R2BinderCompilationInstrumentationTest` 对每种 compression 先执行 `+1`，再在同一
bound service 中执行 inclusive success。所有 request 都先同步
`accepted=true`，随后 ACK；quota terminal 为 `OUTCOME_FAILED`、
`ERROR_ARCHIVE_QUOTA`、0-byte output，inclusive terminal 为
`OUTCOME_EMITTED`、`succeeded=true`、1 module，并重新验证 output ZIP 与 manifest
SHA/provenance。callback 只出现一次。

| serial | API / WebView | STORED `+1` / exact | DEFLATED `+1` / exact | runner |
| --- | --- | ---: | ---: | ---: |
| `emulator-5556` | API 24 / `119.0.6045.194` | 1,279 / 38,106 ms | 962 / 35,093 ms | 2 tests / 85.627 s |
| `968e9f18` | API 35 / `130.0.6723.86` | 652 / 104,472 ms | 593 / 103,964 ms | 2 tests / 218.776 s |

API 35 的两个 exact job 都在 contract `MAX_TIMEOUT_MS=120,000` 内完成；测试没有
放宽 timeout。DEFLATED container/compressed byte count 会受 manifest request ID
与设备 zlib 影响，证据只锁定 expanded boundary、compression ratio 与同 fixture
两遍自洽，不跨设备硬编码压缩尺寸。

## Source/build gates

定向门禁：

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'io.github.supermonster003.autojs6.plugin.typescript.R2CompilationProbeProtocolTest' `
  --tests 'io.github.supermonster003.autojs6.plugin.typescript.R2ProjectFramedTransportTest' `
  --tests 'io.github.supermonster003.autojs6.plugin.typescript.CompilerHostProtocolTest'
.\gradlew.bat :app:verifyR2ProjectCompilerDesktop
```

desktop Worker golden 继续在 512 MiB old-space 下通过；本轮 retained
`heapUsed` 为 22.6 MiB baseline、23.7 MiB boundary、24.0 MiB small replay。

最终根聚合：

```text
verifyR2PublicPreviewFixture
BUILD SUCCESSFUL in 47s
485 actionable tasks: 25 executed, 460 up-to-date

app JVM              17 suites / 160 tests / 0 failures
TypeScript API JVM    3 suites /  16 tests / 0 failures
app lint              0 errors / 3 warnings
public fixture lint   No issues found (debug/release)
```

最终安装到 API 24/35 并复跑上表矩阵的 artifacts：

```text
debug app APK       5,894,411 bytes
SHA-256             05B3FBC1915D9FFEBC362C8EAA8DDD7671C01E9F55D6860BCBE043FF9D5EB8D6
androidTest APK     1,375,422 bytes
SHA-256             F0673E92D0F50E488873AAF0AE91E90356E04FEC3898AF45C46F5E261F04382D
release app APK     2,638,091 bytes
SHA-256             29C381C97E023335D64A6346899A9FC145B1BBBFFEA5CFD8819D912DA303A955
```

设备复跑命令：

```powershell
adb -s <serial> shell am instrument -w -r `
  -e class 'io.github.supermonster003.autojs6.plugin.typescript.R2BinderCompilationInstrumentationTest#storedContainerCapacityAcceptsExact64MiBAndRejectsPlusOne,io.github.supermonster003.autojs6.plugin.typescript.R2BinderCompilationInstrumentationTest#deflatedCapacityAcceptsExact64MiBExpandedAndRejectsPlusOne' `
  io.github.supermonster003.autojs6.plugin.typescript.test/androidx.test.runner.AndroidJUnitRunner
```

两台最终复跑后 capacity fixture/output leftovers 均为 none。本切片没有访问
`QV710AF65F`，也没有创建 commit；artifact hash 绑定的是上述 working-tree build。

## 截至 P3b-b2b 的历史 blockers 与后续状态

P3b-b1 只关闭 debug capacity infrastructure、两个 ZIP boundary、exact success
realm retirement 与 API 24/28/35 direct-device evidence。后续 P3b-b2a 又关闭
same-UID DEBUG synthetic phase-entry cancel/timeout 以及一次主动 active renderer
loss/recovery infrastructure。P3b-b2b 再加入 repeated exact-64-MiB
compiler-service/renderer PSS、稳定 FD target multiset、transient
spool/cursor/Worker successor cleanup，以及 startup recognized orphan
scavenging；后者无法证明枚举、owner-write 恢复、删除或复核时，会在
host/coordinator 创建前 fail closed。这些仍不是 external caller、自然 compiler
phase 或系统内存压力证明。
在 P3b-b2b 完成时至少仍需：

- external-UID debug + minified-release valid full-pipeline 64 MiB matrix；当前
  external fixture 仍只有小型 invalid-ZIP bounded failure；
- 零 spin、高工作量项目在自然 parse、semantic、emit 中途的 cancel/timeout 与
  fault-path cleanup；P3b-b2a 只在 exact phase entry 的 DEBUG bounded spin 上
  证明这些阶段的 hard stop，P3b-b2b 的自然 cancel 只覆盖 ingress；
- disposable-device 的真实 LMK/OOM fault 与 successor recovery；P3b-b2a 的
  主动 renderer termination 且 `didCrash=false` 不关闭这一项；
- Unicode/high-token 合法与 adversarial distributions；
- 128 MiB container/256 MiB expanded file-backed output writer；
- 正式 archive/manifest/diagnostics schema、tsconfig/NodeNext 与其余 R2 exit gates。

后续 P3b-b2c 先完成 ordinary external production-fence 三路径；P3b-b2d 再以
TEST_ONLY `r2CapacityEvidenceDebug` / `r2CapacityEvidenceRelease` 落地上列
第一项的 source/APK policy，API 24/35 最终四行均已 PASS。release evidence
app 是 minified/non-debuggable release-equivalent build，不是 shipping
production release；其余 blockers 仍开放。internal provisional evidence 为
**19/19**。

因此普通 Debug/Release 的 production 512 files、8 MiB/file、8 MiB total fence
保持不变；
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics`、
`sourceMapV3` 四项 capability 继续为 `false`，Public R2 checklist 继续为
**0/54**。
