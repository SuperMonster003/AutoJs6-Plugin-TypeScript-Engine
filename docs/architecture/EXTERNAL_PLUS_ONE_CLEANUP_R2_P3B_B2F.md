# R2-P3b-b2f External Plus-one Cleanup Receipt Evidence

日期：2026-07-30

状态：本切片完成时 internal provisional evidence **21/21**；Public R2
**0/54**。本切片没有
解除普通 Debug/Release 的 production fence，也没有启用任何公开 capability。

## 历史位置与本切片目标

各阶段计数必须按当时证据保留：

| slice | 当时的 internal provisional evidence | 当时尚未证明的相关缺口 |
| --- | ---: | --- |
| P3b-b2c | **18/18** | ordinary production-fence `+1` 只证明 external rejection/recovery，不证明 remote transient cleanup |
| P3b-b2d | **19/19** | external exact-64 边界只证明 capacity pipeline，不证明三类 `+1` cleanup |
| P3b-b2e | **20/20** | natural ingress timeout/final cleanup 已证明，但 remote plus-one cleanup 仍开放 |
| P3b-b2f | **21/21** | 关闭上述 scope-free external `+1` cleanup receipt 窄缺口 |

P3b-b2f 只回答一个问题：standalone same-signer、distinct-UID external fixture
提交被 quota 拒绝的 ordinary/STORED/DEFLATED `+1` request 时，service 是否在
发布 terminal callback 前释放 request 所有权与 transient resource、创建可关联的
immutable receipt，并在 callback 后持续保持全零且可用同一 Binder 完成 ordinary
successor。

## Case 与声明边界

normal Debug/Release 各执行：

- ordinary production-fence `+1` rejection；
- 紧随其后的 `4,603,850`-byte inclusive ordinary successor。

normal 行是 production-shaped black-box rejection/recovery 回归；normal build
不提供 private cleanup telemetry，所以不把这两行写成 receipt 证据。

TEST_ONLY evidence Debug 与 minified/non-debuggable release-equivalent 各执行：

- ordinary production-fence `+1`；
- STORED container `67,108,865` bytes；
- DEFLATED expanded `67,108,865` bytes，container 不超过 64 MiB；
- 每个 rejection 后各执行一个 ordinary inclusive successor。

三个 evidence request 都预期 `ERROR_ARCHIVE_QUOTA`、零 compilation payload 与
empty output。它们明确不携带
`__autojs6_r2_external_capacity_evidence_scope`，control origin 保持
`PUBLIC_PREVIEW`，`expandedWorkerCapacity=false`。因此这里的 “scope-free”
不授予 exact-64 success authority，也不改变普通 Worker 的 **512 files、
8 MiB/file、8 MiB total** production fence。

## Non-shipping observation authority

cleanup observation 只在 evidence build flag 开启且 external caller attestation
全部匹配时启用：

- app 与 fixture 均为 TEST_ONLY；
- fixture package 精确为
  `io.github.supermonster003.autojs6.plugin.typescript.publicpreview`；
- fixture UID 与 Binder calling UID 相同，并与 plugin UID 不同；
- calling UID 只解析到该 fixture package；
- fixture 与 plugin signer 相同；
- public controls 的 origin 为 `PUBLIC_PREVIEW`。

该 observation bit 不增加 compilation capacity，只允许 coordinator 为这一精确
evidence pair 保留 bounded cleanup observation。normal Debug/Release 对 cleanup
observation fail closed，不提供 private telemetry；shipping normal release 的 DEX 不保留 b2f cleanup
prefix/class/epoch-domain marker。

跨 pre-ACK、receipt、current snapshot 与 successor 只关联 request ID、
job-token SHA-256、caller/server descriptor-target SHA-256，以及安全的
process identity：PID、start-time ticks、固定 process name 与 domain-separated
epoch SHA-256。raw boot ID/nonce 不导出，telemetry 不能形成 process termination
authority。

## Receipt 与 callback 顺序

每个 evidence `+1` case 的证明顺序如下：

1. `startCompilation` 返回 ticket；fixture 尚未 ACK。
2. ticket 返回后、ACK 前执行最多 80 ms、每 10 ms 一次的有界只读 metadata
   sampling；unavailable 或非 exact candidate 不缓存，只有 active lease 为 1、
   server input/output exact-target FD 为 `1/1`，且 request ID、job-token digest
   与两端 descriptor-target digest 全部匹配时才记录 pre-ACK ownership。同一次
   `getCompilerInfo()` 的 current snapshot 是第二次独立 descriptor enumeration；
   此时不存在与该 active、unacknowledged request 匹配的 terminal receipt。
3. fixture ACK，等待 exactly-once quota terminal。
4. coordinator 先完成 lease、caller FD、private input/project spool/private
   output named/open transient 与 cursor cleanup。quota rejection 的 cursor 为
   `NOT_CREATED`，opened/closed 为 `0/0`。
5. coordinator 用上述已清理状态创建 immutable receipt；receipt 的 pre-callback
   resource snapshot 中 exact-target FD 为 `0/0`，private input/spool/output
   named/open counter 全部为零，active lease 已释放。receipt **在 terminal
   callback 发布前创建**。
6. fixture 收到 callback 后才读取并关联 receipt，然后取得连续 5 个 matching
   current snapshot；每个 snapshot 的 active lease 与全部 transient counter
   都为零。
7. ordinary inclusive successor 在同一原始 Binder 与同一 safe process epoch
   上 emit；successor cursor opened/closed 平衡并处于 `RELEASED`，其后再次满足
   cleanup proof。

因此准确的时序是：**receipt created before callback; read and correlated after
callback**。

## Device-discovered hardening

最终通过矩阵包含两项设备发现的兼容 hardening：

- API 35 cold supervisor 在初始 admission 时可能短暂 unavailable。fixture 现在
  只在同一个原始 bound Binder 上执行 bounded retry；不 rebind，也不借新
  process 绕过 same-Binder 证明。
- API 24 的同步 `startCompilation` Binder transaction 在返回前可能暂时保留
  incoming descriptor duplicate。exact FD 枚举因此惰性延后到 ticket 返回后、
  ACK 前的上述有界 sampling；非 exact/unavailable candidate 不缓存，exact `1/1`
  才发布。该 observation sampling 不重发 admission/fault，且 80 ms 小于后来
  natural-timeout strict calibration 的 100 ms 最小 margin。

本文只记录包含这两项 hardening 的最终候选 artifact 与 PASS 结果，不保留失败
中间 artifact。

## Source/APK gate 与 no-device aggregate

定向门禁：

```powershell
.\gradlew.bat :r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupSourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2P3bB2fExternalPlusOneCleanupApks
```

source gate 固定 exact caller attestation、三类 scope-free `+1`、ticket-return
后的 pre-ACK ownership、receipt-created-before-callback/read-after-callback、
连续 5 次 all-zero、same Binder/safe epoch 与 ordinary successor。APK gate
检查 fixture/evidence Debug/Release DEX 与 manifest，要求 evidence
release-equivalent 有非空 R8 mapping，并要求 normal release 移除 b2f cleanup
prefix/class/epoch-domain marker；它不笼统声称所有 enum literal 或其他 evidence
字符串均被移除。

设备 hardening 完成后、最终 gate patch 前的定向构建为
`BUILD SUCCESSFUL in 1m 22s`（412 actionable tasks：63 executed、
349 up-to-date）。最终根 aggregate：

```powershell
.\gradlew.bat verifyR2PublicPreviewFixture
```

结果为 `BUILD SUCCESSFUL in 1m 32s`（608 actionable tasks：68 executed、
540 up-to-date）。desktop 4,607-file ingress 为 3,176 ms；512 MiB old-space
retained heap baseline/boundary/replay 为 `22.6 / 23.7 / 24.0 MiB`。

## 最终 artifact

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| normal debug app APK | 5,593,036 | `7db1fbaf6041fcaeb90055c8126e4331e1e7b9671a88bf3397fe88525f3c9726` |
| normal release app APK | 2,711,128 | `42c7b5a4dee4b64f344088510978bbb9944b835bd48c93ade54aa1f74bf88a13` |
| TEST_ONLY evidence debug app APK | 5,560,679 | `552d9c315b7cd654b9b7ef463ee99168c3cb5f2ae79ee3f0eeb5c27efa99e503` |
| TEST_ONLY evidence release-equivalent app APK | 2,711,140 | `dcbbea9d31609e883d366508f394ca828aeb026cc5e1e82bcac04cd5ef88cfe2` |
| fixture debug APK | 1,012,953 | `ce514c781128896c7cd966926a466a5ec176f07f86e71928df5380b567ef0583` |
| fixture release APK | 717,950 | `d2df097faa675e39cc02de8bac4199a5b08b81d58b5343ac750a7b2f5b75685c` |

六包共同的 V2 signer certificate SHA-256 为
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。

manifest 核对：

| APK family | Debug | Release |
| --- | --- | --- |
| normal app | `testOnly=false`，`debuggable=true` | `testOnly=false`，`debuggable=false` |
| evidence app | `testOnly=true`，`debuggable=true` | `testOnly=true`，`debuggable=false` |
| fixture | `testOnly=true`，`debuggable=true` | `testOnly=true`，`debuggable=false` |

fixture Debug/Release 均恰有一个 self-target instrumentation。normal release
缺少 b2f cleanup prefix/class/epoch-domain marker；evidence
release-equivalent 保留所需 telemetry marker 并生成非空 R8 mapping。

## API 24/35 最终 runtime

设备：

| API | fixture UID / plugin UID | provider |
| ---: | --- | --- |
| 24 | `10078 / 10090` | Chrome/WebView `119.0.6045.194` |
| 35 | `10314 / 10312` | Google WebView `130.0.6723.86` |

`O` 表示 ordinary production-fence `+1`，`S` 表示 STORED container `+1`，
`D` 表示 DEFLATED expanded `+1`。每格为 `rejection → ordinary successor`：

| API | app variant | O / S / D（ms） | runner wall |
| ---: | --- | --- | ---: |
| 24 | normal Debug | `2,212 → 15,703 / — / —` | 18.427 s |
| 24 | normal Release | `1,536 → 6,437 / — / —` | 8.274 s |
| 24 | TEST_ONLY evidence Debug | `2,376 → 8,827 / 3,666 → 8,348 / 5,610 → 9,607` | 38.726 s |
| 24 | TEST_ONLY evidence Release | `1,793 → 6,975 / 3,670 → 6,394 / 5,288 → 6,132` | 30.570 s |
| 35 | normal Debug | `1,883 → 20,300 / — / —` | 23.002 s |
| 35 | normal Release | `1,575 → 8,821 / — / —` | 10.893 s |
| 35 | TEST_ONLY evidence Debug | `2,371 → 20,285 / 2,955 → 19,176 / 5,240 → 20,186` | 70.929 s |
| 35 | TEST_ONLY evidence Release | `1,856 → 9,240 / 2,235 → 8,657 / 4,440 → 8,919` | 35.879 s |

8/8 runner 均 PASS。所有 evidence `O/S/D` case 均满足 pre-ACK lease/FD、
pre-callback zero-resource receipt、post-callback five-zero current
convergence、same Binder/safe epoch 与 successor proof。normal 行则只声明
black-box rejection/recovery。所有行的 crash/ANR 检索为空。

最终设备状态均恢复为 normal debug app + fixture debug；API 24 与 API 35 的
UID、signer、debuggable/testOnly flags 均与上表一致，没有遗留相关 process。

## 回归

| matrix | API 24 Debug / Release | API 35 Debug / Release | result |
| --- | --- | --- | --- |
| P3b-b2c ordinary external production fence | `13.056 / 9.621 s` | `22.664 / 11.992 s` | 4/4 PASS |
| P3b-b2d TEST_ONLY external exact-64 | `103.607 / 63.455 s` | `263.179 / 106.820 s` | 4/4 PASS |

## 精确设备调用形状

所有设备命令必须带明确 disposable serial。normal app 使用 `install -r`，
evidence app 与 fixture 使用 `install -t -r`。

normal Debug/Release：

```powershell
adb -s <serial> install -r <normal-app-apk>
adb -s <serial> install -t -r <matching-fixture-apk>
adb -s <serial> shell am instrument -w -r `
  -e externalPlusOneCleanupEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/`
io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation
```

TEST_ONLY evidence Debug/Release：

```powershell
adb -s <serial> install -t -r <evidence-app-apk>
adb -s <serial> install -t -r <matching-fixture-apk>
adb -s <serial> shell am instrument -w -r `
  -e externalPlusOneCleanupEvidence true `
  -e externalCapacityEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/`
io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation
```

## 尚未证明

P3b-b2f 不证明：

- 自然 parse/semantic/emit 中途 cancel/timeout；
- disposable-device 的真实 LMK/OOM、dual process death 或 allocator reclaim；
- Unicode/high-token/adversarial distribution；
- 128 MiB output container / 256 MiB expanded file-backed writer；
- 正式 archive/manifest/diagnostics schema、tsconfig/NodeNext 或其他 R2 exit
  gates。

因此普通 Debug/Release 继续使用 **512 files、8 MiB/file、8 MiB total**
production fence；四项 capability 继续全部为 `false`；Public R2 checklist
继续为 **0/54**。P3b-b2f 是 provisional admission evidence，不是 Public R2
开放声明。

## 后续 P3b-b2g

本页的 **21/21** 是 b2f 完成时的历史计数。后续
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)
已在 API 24/35 ordinary same-UID Debug，以零 spin、无 timeout 注入的真实
parse/semantic/emit progress 关闭 cancellation 窄矩阵，当前 internal
provisional evidence 为 **22/22**。b2g 不把该证据外推为自然 timeout、
external/Binder/Release 或 LMK/OOM；Public R2 继续为 **0/54**。
