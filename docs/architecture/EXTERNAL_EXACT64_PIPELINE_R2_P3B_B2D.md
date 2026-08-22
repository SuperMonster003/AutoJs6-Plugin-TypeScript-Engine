# R2-P3b-b2d External Exact-64-MiB Capacity Evidence Pipeline

状态：内部、non-shipping external capacity evidence 切片。它使用 app 的
`r2CapacityEvidenceDebug` / `r2CapacityEvidenceRelease` 证据 build type；
`r2CapacityEvidenceRelease` 是继承普通 release 配置、经过 R8/minify 且
non-debuggable 的 **release-equivalent evidence build**，不是 shipping
production release。standalone fixture 仍使用自身的 debug/release variant；
fixture release 本身不据此宣称 minified。

两个 evidence app variant 与 fixture 都声明 `TEST_ONLY`，安装必须显式使用
`adb install -t`。evidence app 保持 ordinary app 的 application ID 与 signing，
安装时会替换同 application ID 的普通 app；Gradle 为它们生成独立、可辨认的
evidence APK 文件名。它们仍是 non-shipping evidence artifact。

最终 TEST_ONLY evidence artifact 已重建并通过本地 gate；API 24/35 的 evidence
debug、minified/non-debuggable release-equivalent 四行与 ordinary
Debug/Release 回归均已 PASS。P3b-b2d 使 internal provisional checklist 达到
**19/19**；Public R2 checklist 始终为 **0/54**。

## 声明边界

P3b-b2d 只补 P3b-b2c 明确保留的 external-UID exact-64-MiB 空白：

- standalone `r2-public-preview` fixture 与 compiler plugin 使用不同 UID、同一
  signer，并走真实 exported Binder service、post-return ACK、private ZIP/VFS、
  framed Worker、TypeScript Program、output commit 与完整 output verifier；
- 在 API 24/35 分别运行 evidence debug 和 minified/non-debuggable
  release-equivalent evidence app，共四行；
- 每行先保留 P3b-b2c 的普通 invalid ZIP、production 8 MiB inclusive 和合法
  `+1` 回归，再运行 STORED container 与 DEFLATED expanded 的 exact 64 MiB /
  `+1` 四边界，最后运行一个普通 production-fence inclusive successor；
- inclusive 必须 exactly once `OUTCOME_EMITTED`，重新验证 diagnostics、JS、
  Source Map、manifest、payload SHA、inventory 与 compiler/declarations/options/
  runtime authority；`+1` 必须 exactly once
  `OUTCOME_FAILED + ERROR_ARCHIVE_QUOTA`、零 compilation payload 与
  truncate/fsync/seek 后的 empty output。

本切片不启用 Public R2。普通 app **Debug/Release** 的 external/public dispatch
仍为 **512 files、8 MiB/file、8 MiB total**。normal release APK gate 只声明
以下三个 evidence scope token 不存在：

```text
__autojs6_r2_external_capacity_evidence_scope
stored-container-exact-64-v1
deflated-expanded-exact-64-v1
```

它不宣称 enum literal、fixture class name 或其他广义 “evidence marker” 全部
不存在。`compilerAvailable`、`projectCompilation`、
`semanticDiagnostics`、`sourceMapV3` 四项 capability 继续全部为 `false`，
Public R2 checklist 继续为 **0/54**。

## Non-shipping evidence authority

caller 不提交通用的 `expandedWorkerCapacity` bit。它只可提交两个固定 scope
之一：

```text
stored-container-exact-64-v1
deflated-expanded-exact-64-v1
```

service 只有在下列条件同时成立时才派生
`R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE` 与内部
`expandedWorkerCapacity=true`：

1. app 是 `R2_EXTERNAL_CAPACITY_EVIDENCE=true` 的 evidence build；
2. base control 已通过普通 public-preview metadata/authority 校验；
3. project ID / entry URI 精确为 `capacity-project` /
   `autojs://source/project/main.ts`；
4. Binder calling UID 与 plugin UID 不同，且精确等于已安装 fixture UID；
5. calling UID 只对应
   `io.github.supermonster003.autojs6.plugin.typescript.publicpreview`；
6. fixture 带 `FLAG_TEST_ONLY`，并与 plugin signer 一致；
7. STORED scope 的 frozen FD container size 精确为 64 MiB；DEFLATED scope
   在 admission 时只要求 frozen FD container size 位于 `1..64 MiB`。

任一条件不成立都 fail closed。四个边界中的两个 `+1` rejection case 不携带
capacity scope：它们使用结构和哈希均合法的输入，让普通 frozen archive quota
异步拒绝；因此不会把放宽权限扩散到 over-limit request。

这里必须区分 service attestation 与内容证据：STORED scope 在 admission 时认证
exact-64 container；DEFLATED scope 在 admission 时**不**读取内容并认证
exact-64 expanded payload，它只认证 container 落在 `1..64 MiB`。DEFLATED
expanded exact-64/`+1` 来自 fixture 两遍 size/CRC/SHA/compressed-size 自检，
再由真实 runtime terminal/output 结果共同证明，不能表述为 service admission
本身 content-bound exact expanded。

`r2CapacityEvidenceDebug` 继承 debug；`r2CapacityEvidenceRelease` 继承 release，
所以后者保留 minify、resource shrink 与 non-debuggable 形态；两个 evidence
variant 都是 `TEST_ONLY`，并使用独立 evidence 文件名。APK gate 还要求：

- 两个 evidence app APK 均含上述三个固定 scope token 与四 ABI
  `libautojs6_r2_fd_flags.so`；
- evidence app 与对应 fixture signer 一致，manifest/UID shape 合法；
- `r2CapacityEvidenceRelease` 生成非空 mapping；
- normal release APK 中仅精确排除上述三个 evidence scope token。

这是一条专用、可审计、不得作为 shipping artifact 发布的 evidence seam；
不能把它描述为 shipping release 已将 64 MiB production admission 对外开放。

## 常量内存四边界 fixture

`R2ExternalCapacityProjectFixture` 以一个 4 KiB `main.ts` 和八个 padding `.d.ts`
组成有效、低 token、printable-ASCII TypeScript project。前七个 padding file
固定为每文件 8 MiB，最后一个 padding file 与 request manifest 做最多八轮
size fixed point，使目标轴精确落在边界。

fixture 不使用 `ZipOutputStream`，而是两遍 replay：

1. 第一遍流式计算每个 payload 的 size、CRC32、SHA-256 与 compressed size；
2. 第二遍显式写 ZIP32 local header、payload、central directory 与 EOCD，同时
   流式计算 container SHA-256；
3. `fsync` 后用 `ZipFile` 重新读取，逐 entry 核对顺序、method、size、
   compressed size、CRC、payload SHA 与整包 accounting；
4. `+1` 先在仅放宽目标轴 1 byte 的 fixture-side validator 下证明其余 file、
   manifest、metadata、ratio 和另一容量轴都合法。

四个 case 精确为：

| Boundary | 精确目标 | 唯一越界轴 | 预期 terminal |
| --- | ---: | --- | --- |
| `STORED_CONTAINER_INCLUSIVE` | container `67,108,864` B | none | `OUTCOME_EMITTED` |
| `STORED_CONTAINER_PLUS_ONE` | container `67,108,865` B | container bytes | `ERROR_ARCHIVE_QUOTA` |
| `DEFLATED_UNCOMPRESSED_INCLUSIVE` | expanded `67,108,864` B；container ≤ 64 MiB | none | `OUTCOME_EMITTED` |
| `DEFLATED_UNCOMPRESSED_PLUS_ONE` | expanded `67,108,865` B；container ≤ 64 MiB | uncompressed bytes | `ERROR_ARCHIVE_QUOTA` |

所有 source 均不超过 8 MiB/file，source/manifest inventory、CRC、archive SHA、
request-manifest SHA 与 source payload SHA 都是 request-time 真实值。设备日志为
每个 case 记录 boundary、container/expanded/compressed/manifest/source bytes、
quota axis、output bytes 与 elapsed time。

## Ordinary P3b-b2c regression

启用 `-e externalCapacityEvidence true` 不会跳过普通路径。runner 顺序固定为：

```text
invalid ZIP
  -> ordinary production 8 MiB inclusive
  -> ordinary production 8 MiB +1
  -> STORED container exact 64 MiB
  -> STORED container 64 MiB +1
  -> DEFLATED expanded exact 64 MiB
  -> DEFLATED expanded 64 MiB +1
  -> ordinary production 8 MiB inclusive successor
```

最终 b2d matrix 证明的是 external exact-64 full pipeline 四边界，并同时回归
ordinary b2c 行为；它没有用 capacity seam 取代或改写 b2c 的 production
fence。每条 terminal 都等待 1 秒 quiet window 并断言 callback exactly once；
inclusive successor 还证明 capacity matrix 之后普通围栏路径仍可恢复。

## 构建与 artifact 证据

不运行设备的定向 gate：

```powershell
.\gradlew.bat :r2-public-preview:verifyR2P3bB2dExternalExact64SourcePolicy
.\gradlew.bat :r2-public-preview:verifyR2P3bB2dExternalExact64Apks
.\gradlew.bat verifyR2PublicPreviewFixture
```

最终 b2d 根 aggregate 为 `BUILD SUCCESSFUL in 49s`（604 actionable tasks：
61 executed、543 up-to-date）。这是加入 b2d source/APK gate 后的最终结果，
不能与 b2c 历史 aggregate 混用。

最终候选 artifact 必须来自同一 working-tree build：

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| normal app debug | 5,089,165 | `83fca363539ae0a4b8e0fc1562d81b4e92b97d1230c2ecfc384b13f157862168` |
| normal app release | 2,711,128 | `4c51525c05ed5be116d2c90bf59cd8a4eb7559477ead516f7a90534c9076afb5` |
| app `r2CapacityEvidenceDebug` / `autojs6-plugin-typescript-engine-v0.5.0-r2-capacity-evidence-debug-universal.apk` | 5,072,657 | `70a22016ba9465026cc0ee7491aceb975b12cdf07139e6b3dadb7ea7212441bb` |
| app `r2CapacityEvidenceRelease` / `autojs6-plugin-typescript-engine-v0.5.0-r2-capacity-evidence-release-universal.apk` | 2,711,140 | `602437f32ccbe0b835d6dada2a42f562479d46483ddb8fdc675b10aa44cc45fd` |
| fixture debug | 998,116 | `e71741788c026a3e52e3f2153b657ae83337a562fdec4840ea6d2197f232dd6d` |
| fixture release | 706,034 | `76c4b289c9a81172a6c247a51fd0f42d324ed04d55f2fcfae89d7b5a9b4f9bb6` |

普通 debug/release 与 evidence debug/release 是不同 app artifact，但 app
application ID/signing 相同。不得把 ordinary b2c app release 的历史 SHA 复用为
`r2CapacityEvidenceRelease`，也不得把 evidence release 标记为 shipping
release。

## 设备复跑与 runtime matrix

设备命令必须始终携带明确 disposable serial。debug evidence app 与 fixture
debug 配对；release-equivalent evidence app 与 fixture release 配对：

```powershell
adb -s <serial> install -t -r app\build\outputs\apk\r2CapacityEvidenceDebug\autojs6-plugin-typescript-engine-v0.5.0-r2-capacity-evidence-debug-universal.apk
adb -s <serial> install -t -r <fixture-debug-apk>
adb -s <serial> shell am instrument -w -r `
  -e externalCapacityEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/`
io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation

adb -s <serial> install -t -r app\build\outputs\apk\r2CapacityEvidenceRelease\autojs6-plugin-typescript-engine-v0.5.0-r2-capacity-evidence-release-universal.apk
adb -s <serial> install -t -r <fixture-release-apk>
adb -s <serial> shell am instrument -w -r `
  -e externalCapacityEvidence true `
  io.github.supermonster003.autojs6.plugin.typescript.publicpreview/`
io.github.supermonster003.autojs6.plugin.typescript.publicpreview.R2PublicPreviewInstrumentation
```

ordinary b2c regression 安装 normal app 时使用
`adb -s <serial> install -r <normal-app-apk>`；fixture 仍必须使用
`adb -s <serial> install -t -r <fixture-apk>`，runner 不传
`externalCapacityEvidence`。evidence app 与 fixture 两者都必须使用
`install -t -r`。

设备身份：

| API | serial | Android / WebView / build fingerprint |
| ---: | --- | --- |
| 24 | `emulator-5556` | Android 7.0 / API 24 x86；Chrome/WebView `119.0.6045.194`；`google/sdk_google_phone_x86/generic_x86:7.0/NYC/4409132:user/release-keys` |
| 35 | `968e9f18` | Android 15 / API 35 arm64；Google WebView `130.0.6723.86`；`Xiaomi/liuqin/liuqin:15/AQ3A.241006.001/OS2.0.9.0.VMYCNXM:user/release-keys` |

最终 runtime matrix：

| API | evidence service variant | fixture UID / plugin UID | ordinary b2c regression | STORED 64 / `+1` | DEFLATED 64 / `+1` | successor / runner |
| ---: | --- | --- | --- | --- | --- | --- |
| 24 | TEST_ONLY `r2CapacityEvidenceDebug` | `10078 / 10090` | in-run PASS；normal debug no-arg PASS，15,369 ms，无 capacity event | emitted `67,108,864 / 67,107,642 → 7,551 B`，40,863 ms / failed `67,108,865 / 67,107,643`，axis `CONTAINER_BYTES`，0 B，3,577 ms | emitted `25,804,055 / 67,108,864 → 7,561 B`，38,840 ms / failed `25,804,054 / 67,108,865`，axis `UNCOMPRESSED_BYTES`，0 B，5,922 ms | successor 9,172 ms；PASS；code -1；ADB 0；wall 113,715 ms |
| 24 | TEST_ONLY minified/non-debuggable `r2CapacityEvidenceRelease` | `10078 / 10090` | in-run PASS；normal release no-arg PASS，10,510 ms，无 capacity event | emitted，21,345 ms / failed axis `CONTAINER_BYTES`，0 B，3,100 ms | emitted，20,680 ms / failed axis `UNCOMPRESSED_BYTES`，0 B，5,307 ms | successor 6,364 ms；PASS；code -1；ADB 0；wall 68,213 ms |
| 35 | TEST_ONLY `r2CapacityEvidenceDebug` | `10314 / 10312` | in-run `1,862 / 19,748 / 1,511 ms`，PASS；normal debug no-arg `1,842 / 17,878 / 1,555 ms`，PASS，wall 21,787 ms，无 capacity event | emitted `67,108,864 / 67,107,642 → 7,551 B`，106,932 ms / failed `67,108,865 / 67,107,643`，axis `CONTAINER_BYTES`，0 B，2,449 ms | emitted `25,804,054 / 67,108,864 → 7,561 B`，107,900 ms / failed `25,804,054 / 67,108,865`，axis `UNCOMPRESSED_BYTES`，0 B，4,605 ms | successor 17,897 ms；PASS；code -1；ADB 0；wall 263,569 ms |
| 35 | TEST_ONLY minified/non-debuggable `r2CapacityEvidenceRelease` | `10314 / 10312` | in-run `1,619 / 8,909 / 1,301 ms`，PASS；normal release no-arg `1,604 / 8,997 / 1,349 ms`，PASS，wall 12,354 ms，无 capacity event | emitted `67,108,864 / 67,107,642 → 7,551 B`，38,185 ms / failed `67,108,865 / 67,107,643`，axis `CONTAINER_BYTES`，0 B，1,790 ms | emitted `25,804,054 / 67,108,864 → 7,561 B`，41,934 ms / failed `25,804,056 / 67,108,865`，axis `UNCOMPRESSED_BYTES`，0 B，4,111 ms | successor 8,318 ms；PASS；code -1；ADB 0；wall 106,751 ms |

API 24 的 normal Debug/Release 均为 non-TEST_ONLY app；no-arg b2c 回归
crash/compiler-unavailable/capacity-event 命中均为 0。最终恢复 normal debug
`83fca363539ae0a4b8e0fc1562d81b4e92b97d1230c2ecfc384b13f157862168` +
fixture debug
`e71741788c026a3e52e3f2153b657ae83337a562fdec4840ea6d2197f232dd6d`，
plugin/fixture UID 为 `10090/10078`，manifest flags 与预期一致。

API 35 的 manifest flags 也逐 variant 核对：evidence Debug 的 app/fixture 均为
debuggable + testOnly；evidence Release 的 app/fixture 均仅为 testOnly；
normal Debug app 仅 debuggable、fixture 为 debuggable + testOnly；normal
Release app 两项皆无、fixture 仅 testOnly。evidence 与 normal 四行的
crash/FATAL/compiler-unavailable scan 均为空。最终恢复 normal debug
`83fca363539ae0a4b8e0fc1562d81b4e92b97d1230c2ecfc384b13f157862168` +
fixture debug
`e71741788c026a3e52e3f2153b657ae83337a562fdec4840ea6d2197f232dd6d`：
app UID `10312`、debuggable/non-testOnly，fixture UID `10314`、
debuggable/testOnly，且没有遗留相关 process。

证据保留 app/fixture artifact SHA、UID distinct/signer attestation、四条
capacity `event=capacity` accounting、ordinary b2c 三路径、post-capacity
successor、passed stream、`INSTRUMENTATION_CODE`、ADB exit、wall time 及
crash/ANR/compiler-unavailable 检索结果。

## 尚未证明

P3b-b2d **只**证明 external exact-64-MiB full-pipeline 四边界与 ordinary
b2c regression。它不证明：

- production-fence 或 exact-64 `+1` 的 remote transient
  FD/spool/cursor/process cleanup；
- 零 spin 自然 parse/semantic/emit 中途 cancel/timeout，或自然 ingress
  timeout；
- disposable-device 的真实 LMK/OOM、dual process death 或 allocator reclaim；
- Unicode/high-token/adversarial distribution；
- 128 MiB container / 256 MiB expanded file-backed output writer；
- 正式 archive/manifest/diagnostics schema、tsconfig/NodeNext 或其他 R2 exit
  gates。

所以 internal provisional evidence 为 **19/19**，但普通
Debug/Release 的 **512 files、8 MiB/file、8 MiB total** production fence、四项
`false` capability 与 Public R2 **0/54** 均不得改变。

后续
[R2-P3b-b2e Natural Ingress Timeout and Cleanup](WORKER_NATURAL_INGRESS_TIMEOUT_R2_P3B_B2E.md)
已在 API 24/35 ordinary debug 关闭上列“自然 ingress timeout”这一项：direct
三轮 fixed-5s partial ingress timeout 与 Binder fixed-10s end-to-end natural
deadline/final cleanup 均 PASS。它不回写 b2d 的历史 19/19 结论，也不关闭其余
项目；b2e 当时使 internal provisional evidence 达到 **20/20**。在 b2d 与
b2e 当时，production-fence/exact-64 `+1` 的 remote transient
FD/spool/cursor/process cleanup 仍未证明。

后续
[R2-P3b-b2f External Plus-one Cleanup Receipt Evidence](EXTERNAL_PLUS_ONE_CLEANUP_R2_P3B_B2F.md)
在 API 24/35 normal/evidence Debug/Release 关闭上述窄缺口：scope-free
ordinary/STORED/DEFLATED `+1` rejection 后，receipt 在 callback 前创建，
fixture 在 callback 后读取并关联 receipt，随后连续 5 次 current snapshot
全零且 same-Binder successor 成功。它不回写 b2d 历史 **19/19** 或 b2e
历史 **20/20**；b2f 完成时 internal provisional evidence 为 **21/21**。
再后续 P3b-b2g 在 API 24/35 ordinary same-UID Debug 关闭零 spin 自然
parse/semantic/emit cancellation 窄矩阵，当前为 **22/22**；自然 phase
timeout 与其他完整 fault/resource matrix 仍开放。完整 b2g 边界见
[R2-P3b-b2g Natural Parse/Semantic/Emit Cancellation](WORKER_NATURAL_PHASE_CANCELLATION_R2_P3B_B2G.md)。
Public 仍为 **0/54**，四项 capability 与 production fence 均未改变。
