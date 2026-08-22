# R2-P3b-a Worker Input Capacity and Lifetime

状态：内部 desktop slice；不是 Public R2。后续 P3b-b1 已增加 DEBUG
Android/WebView 64 MiB boundary proof，P3b-b2a/P3b-b2b 又增加 fault 与 repeated
resource/startup orphan-cleanup evidence，但都没有解除 production gate。

R2-P3a 已把 production native input 改为 app-private file-backed VFS spool 和
最多 16 KiB 的 random-access framing。P3b-a 在不改变 public contract 或
production admission 的前提下，补上 Worker 内部的容量分层、复杂度预算与
per-job reference release，并为 `64 MiB caller source + 8 MiB trusted
declarations` 建立受限 Node/V8 证据。

## 容量分层

| 层 | 文件数 | 单文件 | 合计 | 当前用途 |
| --- | ---: | ---: | ---: | --- |
| frozen caller ZIP v1 | 4096 entries（含 manifest） | 8 MiB/entry | 64 MiB actual uncompressed | public container 结构上限 |
| P3a native VFS spool/index | 4607 files | 8 MiB | 72 MiB | 4095 caller sources + 512 trusted declarations |
| P3b-a Worker 内部 ingress/VFS | 4607 files | 8 MiB | 72 MiB | desktop direct-framing capacity |
| P3b-b1 DEBUG expanded dispatch | 4607 files | 8 MiB | 72 MiB | same-UID device evidence only |
| Android production dispatch | 512 files | 8 MiB | 8 MiB | 仍生效的发布前安全围栏 |

Worker 内部容量提升不绕过 Android host 的
`requireProvisionalWorkerEligibility`。production snapshot 在创建 framer 前仍
按 512 files、8 MiB/file、8 MiB total 拒绝超限输入，因此 P3b-a 不能被解释为
设备上的 64 MiB compilation admission。

现有 provisional output 配额也没有改变：最多 512 outputs、512 KiB/JS 或 map、
1 MiB aggregate output、1000 diagnostics/512 KiB diagnostics，以及 6 MiB
framed typed result。它们不是冻结的 128 MiB container/256 MiB uncompressed
output contract。

## Worker input representation

每个文件仍使用一个完整 `Uint8Array` 接收 stop-and-wait frame。file-end 的处理
顺序固定为：

1. 对完整 byte buffer 重算 SHA-256；
2. 使用 `TextDecoder("utf-8", { fatal: true, ignoreBOM: true })` 执行唯一一次
   UTF-8 decode；
3. 清零并断开 byte buffer；
4. 用 TypeScript scanner 计算有界 token 数；
5. 只保留一个 `{ path, byteLength, tokenCount, text }` record。

后续只读 VFS 信任这份已经完成 digest/fatal-decode 的内部 record，不再
`TextEncoder`/`TextDecoder` 重编码或重复解码。CompilerHost 仅在 TypeScript
首次请求路径时创建 `SourceFile`，以 canonical path 为 key 缓存在当前 job 的
`sourceFileCache` 中，并固定 `sourceFile.version = "0"`；同一路径在一个 job
内返回同一个 `SourceFile` identity，job 之间不共享 Program 或 build info。

这是“单份 decoded String + lazy SourceFile”的 allocation shape，不是 streaming
parser。当前 job 最终仍可能同时持有全部 decoded strings 与 TypeScript
Program/AST；P3b-a 依靠下述复杂度配额和 desktop memory gate 对这一形态设限，
不能把它外推到任意 72 MiB Unicode/高 token 项目或 WebView renderer。

## 解析前复杂度预算

Worker 在创建 Program/AST 前执行以下独立上限：

| 维度 | 上限 |
| --- | ---: |
| 单 path UTF-16 characters | 4096 |
| 单 path segment depth | 64 |
| aggregate path characters | 8 MiB |
| directory trie nodes（含 root） | 65536 |
| aggregate unique directory-segment characters | 4 MiB |
| tokens/file | 65536 |
| tokens/project | 262144 |

directory 查询使用按 segment 建立的有界 trie，不预展开每个 ancestor 的完整路径
字符串。token scan 跳过 trivia，因此大面积空白的容量 fixture 可以验证 byte
envelope，同时高 token 小文件会在 AST construction 前失败。file、single-file、
total-byte、path、directory 与 token 配额彼此独立；命中任意一项都 fail closed。

这些数值目前只是 P3b-a 内部 authority，不属于 frozen archive schema 或 public
semantic quota。后续冻结前仍可依据真实设备 evidence 收紧。

## Diagnostics 与 emit

diagnostics 按固定顺序收集：

```text
options
-> syntactic（逐 SourceFile）
-> global
-> semantic（逐 SourceFile）
-> emit
```

collector 在保留每条 diagnostic 前先生成最多 4096 characters 的结构化消息，
用该 bounded representation 去重，并逐条累计 UTF-8 JSON byte cost；达到 1000
条或 512 KiB 时立即失败，不先构造 whole-project diagnostic arrays。若 pre-emit
阶段已经出现 error，Worker 直接返回 `emitSkipped=true`，不调用会再次聚合
pre-emit diagnostics 的 `builder.emit()`。

## Per-job release

`compileProject` 用 `finally` 覆盖成功与异常路径，并在结果离开 compiler routine
前执行：

- 断开 Program、builder、host 和 diagnostic collector 引用；
- 清空 per-job `sourceFileCache` 与 output map；
- release VFS map、root/source arrays 和 directory trie；
- 把每个 input record 的 `text` 置空，再清空 files/rootNames；
- 在 ingress 失败时清零当前 file bytes，并释放尚未转交给 compiler routine 的
  records、path sets 与 directory trie。

成功结果随后才编码成受 6 MiB 限制的 framed bytes。该顺序缩短 input/AST 与
result bytes 的重叠生命周期，但 JavaScript GC 与 Android renderer reclaim
时机仍由 runtime 决定；显式断开引用不等于设备 PSS 已下降。

## Desktop retained-memory gate

`verify-r2-project-compiler.js` 在独立 child process 中使用：

```text
node --expose-gc --max-old-space-size=512
```

同一个 isolate 先完成小项目 warm-up，再编译精确的 64 MiB caller source（8 个
8 MiB ASCII `.ts`）和 8 MiB trusted `lib.d.ts`，随后强制多轮 GC、运行一个小型
successor compilation 并再次强制 GC。gate 检查：

- boundary 后 `heapUsed <= baseline + 32 MiB`；
- small successor 后 `heapUsed <= boundary + 8 MiB`；
- 最终 `arrayBuffers <= baseline + 16 MiB`；
- boundary project 正常 emit 每个 source 对应的一份 JS 与 Source Map。

这是受 512 MiB V8 old-space 限制的 same-isolate aggregate/retained-reference
证据。它不是 WebView renderer、Android compiler-service PSS、LMK/OOM 或设备热
循环证据；fixture 还是低 token ASCII 形态，不能代表所有合法 byte
distributions。

同一 desktop suite 还覆盖：

- 完整 4607 个 zero-byte file records 的 ingress、错误释放与小型 successor，
  以及 72 MiB inclusive start boundary 与 file-count/total-byte `+1`；
- fatal UTF-8 跨 frame 边界、每文件只 decode 一次、VFS 不再 re-encode；
- per-file/project token overflow；
- aggregate path characters、directory nodes/characters overflow；
- lazy `SourceFile` identity/version；
- incremental diagnostic cap 与 pre-emit error short circuit。

## 截至 P3b-b2b 的 Public blocker

P3b-a 不修改 AIDL、public API、ZIP v1、profile/schema revision 或 capability
advertisement。`compilerAvailable`、`projectCompilation`、
`semanticDiagnostics`、`sourceMapV3` 继续全部为 `false`，Public R2 checklist
保持 **0/54**。

P3b-b1 已在 API 24/28/35 完成 DEBUG direct capacity，并在 API 24/35 完成
STORED/DEFLATED inclusive/`+1` Binder boundary 与 exact success realm
retirement，见
[R2-P3b-b1 Android Device Capacity and Exact Realm Retirement](WORKER_INPUT_R2_P3B_B1.md)。

P3b-b2a 随后增加 synthetic phase-entry hard-stop 与 active renderer-loss；
P3b-b2b 再增加 repeated exact-64-MiB resource plateau、自然 ingress cancel 的
cursor/Worker successor cleanup，以及 compiler-service startup recognized orphan
scavenging；枚举、owner-write 恢复、删除或复核任一无法证明时，会在
host/coordinator 创建前 fail closed。见
[R2-P3b-b2a DEBUG Phase-entry Hard-stop and Active Renderer-loss](WORKER_FAULT_R2_P3B_B2A.md)
与
[R2-P3b-b2b Repeated Resource and Startup Orphan Cleanup](WORKER_RESOURCE_R2_P3B_B2B.md)。

完整 P3b-b 仍需 external-UID debug/minified release、自然 parse/semantic/emit
中途 fault、disposable-device 真实 LMK/OOM、Unicode/high-token 合法与 adversarial
distributions，以及 128 MiB container/256 MiB expanded file-backed output。
正式 schema/profile 和其余 R2 exit gates 也仍未关闭。因此 production
512 files、8 MiB/file、8 MiB total 围栏保持不变，四项 capability 继续为
`false`，Public R2 checklist 继续为 **0/54**。
