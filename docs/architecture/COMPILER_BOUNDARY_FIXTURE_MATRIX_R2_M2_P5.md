# R2-M2-P5 Compiler Boundary Fixture Matrix

> **S1-2 supersession（2026-08-23）。** 本文与 `p5-v1.json` 记录 2026-08-11
> direct-compiler 阶段的历史 production-negative 判定。当前 archive、snapshot、Worker、
> generated mapping、diagnostics 与 output ZIP 已端到端接纳 canonical UTF-8 path；P5 的
> compiler golden 与 collision/token 证据仍保留，但不再定义当前路径准入。

日期：2026-08-11

状态：**已完成。checked-in golden、desktop oracle、JVM contract、P5 aggregate 与最终
`verifyR2ProjectCompilerCore` 已全部通过；`R2-M2-P5` 已关闭。本文只证明 P5 的 fixture、
direct compiler oracle 与 production authority 回归，不提升 public、runtime、Android/Binder
或 end-to-end 能力。**

## 决策摘要

P5 延续 P3/P4 的 snapshot-only TypeScript compiler-bundle oracle，不修改 production
profile、archive、snapshot、Worker、native result/private output 或 public capability。首个
revision 固定为：

- `typescript@6.0.3`；
- 7 类 boundary：中文路径、ASCII 空格路径、CRLF、leading UTF-8 BOM、valid Unicode、
  high token 和 canonical collision；
- Rhino revision 1 与 NodeNext revision 1 两个冻结 compiler profiles；
- 共 14 rows，其中 12 rows 是 compile-positive，2 rows 是 collision-negative；
- 每个 positive row 使用 declared/reversed 两种输入顺序并各重复 2 次，共
  `12 × 2 × 2 = 48` 次 compiles；
- collision-negative rows 必须在 `ts.createIncrementalProgram` 之前拒绝，因此不计入
  compile 数，且不得产生 diagnostics golden 或任何 output；
- 每个 positive row 使用两个不同的 executable `.ts` roots，精确产生 2 个 JavaScript 与
  2 个 Source Map v3 outputs；
- `ts.sys`、host filesystem、network 与 emitted-JavaScript execution 全部为 0。

P5 的关键边界是：**TypeScript compiler 能处理中文路径，不等于当时的 production 接纳中文
路径。** P5 当时的 production path authority 继续冻结为 printable ASCII。中文路径只能形成
direct-compiler positive；archive、snapshot、Worker、generated mapping 与 private output
必须继续稳定拒绝。ASCII 空格属于 printable ASCII，可作为 ordinary `/sources/*.ts`
production-compatible positive，但不得外推到具有更窄 grammar 的 package-target path。

## Profile 适用范围

| Authority | P5 rows | 适用方式 |
| --- | ---: | --- |
| provisional Rhino revision 1 | 7 | 6 个 compiler-positive rows 与 1 个 pre-Program collision-negative row |
| provisional NodeNext revision 1 | 7 | 6 个 compiler-positive rows 与 1 个 pre-Program collision-negative row |
| provisional NodeNext revision 2 | 0 | 只证明 canonical compiler options/fingerprint 与 revision 1 相同，并保持 Worker-result-only |
| controlled-tsconfig Rhino | 0 | CRLF/BOM config grammar 已由 C1/C2 冻结；P5 只验证 source text/path boundary |

Node revision 2 不增加第三套 boundary golden。它与 revision 1 的 compiler options 相同；其
`.mts/.cts`、module-output、resolver 与 Worker-result authority 已由 P2 负责，不属于 P5。

## Exact fixture matrix

14 个 row ID、顺序、outcome 与 production relationship 必须冻结：

| Boundary | Rhino row | Node row | Direct outcome | Production relationship |
| --- | --- | --- | --- | --- |
| 中文路径 | `rhino-chinese-path` | `node-chinese-path` | compile positive | printable-ASCII stable negative |
| ASCII 空格路径 | `rhino-space-path` | `node-space-path` | compile positive | ordinary `/sources/*.ts` compatible positive |
| CRLF | `rhino-crlf` | `node-crlf` | compile positive | raw UTF-8 identity，不做换行规范化 |
| leading BOM | `rhino-bom` | `node-bom` | compile positive | 单个 leading `EF BB BF` 保留在 input identity |
| valid Unicode | `rhino-unicode` | `node-unicode` | compile positive | source content positive，path authority 不扩张 |
| high token | `rhino-high-token` | `node-high-token` | compile positive | 65,536/file inclusive；现有 `+1`/project overflow 继续拒绝 |
| ASCII case-fold collision | `rhino-canonical-collision` | `node-canonical-collision` | pre-Program negative | production canonical collision stable negative |

每个 positive row 的 `files` 与 `rootNames` 都必须是两个元素、路径集合完全相等，且两个
roots 都是 direct compiler executable `.ts`，不是 `.d.ts`。其中除中文路径 row 外，路径应
保持在当前 production ordinary source path domain；中文路径 row 必须显式携带
当时的 `productionAdmission=direct-only/rejected-non-ascii`，不得使用与 P4 相同的
`production-admitted` 描述。

两个 collision rows 各使用一组只在 ASCII 大小写上不同的 source paths，例如
`/sources/Case.ts` 与 `/sources/case.ts`。它们的 frozen outcome 必须为：

- `programCreated=false`；
- `diagnostics=[]`，因为 rejection 发生在 TypeScript diagnostics 之前；
- `outputs=[]`；
- JVM 使用真实 production snapshot/archive authority 复验 ASCII case-fold rejection；
- direct harness 不得用 TypeScript `TS1149` 代替 production rejection。

## Boundary semantics

### 中文路径

中文路径 row 使用 NFC 的 `/sources/...中文....ts` VFS path，并通过 snapshot-only
CompilerHost 产生 exact JavaScript 与 Source Map。fixture path、output path 与 map
`sources` 均按原始 Unicode code units 和 UTF-8 bytes 计算 identity。

该 positive 只证明 TypeScript 6.0.3 compiler semantics。production 仍必须在 Program 前
拒绝非 printable-ASCII archive、manifest、snapshot、Worker、generated-mapping 与
private-output paths。P5 不修改任何 path grammar、schema revision 或 fingerprint。

### ASCII 空格路径

空格路径 row 使用例如 `/sources/space path.ts` 的 ordinary source path。空格 `0x20` 属于
当前 printable-ASCII domain，因此 direct compiler 与 production path validation 都应接受，
且 output/map source path 必须保留空格，不得 trim、collapse 或改成 host-native path。

该结论只适用于 ordinary `/sources/*.ts`。package metadata/target 的独立 safe-relative-path
grammar 仍可拒绝空格，P5 不改变其契约。

### CRLF

CRLF row 的 source text 必须真实包含 `\r\n` code units，而不是依赖 fixture 文件自身的
working-tree line ending。JSON 中应使用 escaped CRLF，避免 Git 或编辑器把测试语义转换为
LF。

必须同时冻结：

- source byte length、SHA-256 和 source-collection identity 计入每个 `\r`；
- identity 计算前不得 `trim()`、BOM-strip 或 CRLF-to-LF normalize；
- frozen profiles 的 `newLine=LF` 保持不变；
- emitted JavaScript 与 maps 不含 `\r`。

### Leading UTF-8 BOM

BOM row 只覆盖一个位于 source byte offset 0 的 UTF-8 BOM。fixture JSON 解析后 source text
必须以单个 `U+FEFF` 开头，渲染后的前三个 bytes 必须为 `EF BB BF`，且 source identity 必须
包含它。

Worker 当前 `TextDecoder("utf-8", { fatal: true, ignoreBOM: true })` 会把 leading BOM
保留为 `U+FEFF`，不是将其剥离。TypeScript compiler 应接受该 source；由于 profile 未启用
`emitBOM`，所有 outputs 的首字符不得为 `U+FEFF`。misplaced 或 duplicate BOM 不属于本 row
的 acceptance，不得虚构新的 source rejection policy。

### Valid Unicode

Unicode row 在 ASCII VFS path 下覆盖：

- CJK identifier 与 string literal；
- 至少一个 supplementary-plane scalar，例如 emoji；
- NFC 与 NFD 两种不同 code-unit sequence 的 source content。

source text 不做 Unicode normalization。JavaScript harness 必须在 UTF-8 hash 前显式拒绝 lone
surrogate，避免 Node UTF-8 encoder 静默替换为 `U+FFFD`；JVM contract 使用 strict
`CharsetEncoder` 独立复验。malformed UTF-8 与 unpaired surrogate 必须继续由既有
production/Worker negative gates 在 compiler 前拒绝。

### High token

high-token row 使用冻结的 `repeated-interface-v1` generator：

```text
line = "interface Boundary{}\r\n"
repetitions = 16,384
tokensPerLine = 4
expectedSourceTokens = 65,536
```

TypeScript scanner 必须使用与 Worker 相同的 ES2018、Standard、skip-trivia 规则，独立确认
exact 65,536 tokens。第二个 executable context root 保持很小。common/profile declarations
和两个 source roots 的 tokens 必须全部纳入 project aggregate，且每个 profile 的 total 都不
超过 262,144。

不得用 `";".repeat(65_536)` 作为 generator。该形式虽然 token 数正确，但会生成约 655 KiB
的 Source Map，越过当前 512 KiB/map Worker output fence；type-only repeated interface 可以
保留 exact token pressure，同时产生很小的 deterministic output。

P5 direct positive 不替代 production Worker admission。既有 desktop Worker gate 必须继续
证明：

- 65,537 tokens 的单 source 在 transfer/Program 前拒绝；
- 每个 source 均未超限但 project aggregate 大于 262,144 时拒绝；
- failure 后可运行 clean successor；
- 65,536/262,144 仍是 internal provisional Worker authority，不是 frozen public quota。

### Canonical collision

P5 只关闭当前 printable-ASCII domain 内的 exact duplicate/ASCII case-fold collision。
archive、manifest、snapshot、Worker VFS 与 output authority 必须继续在 Program 或 commit 前
fail closed，不能让 `Map` 后写覆盖前写，也不能依赖 platform filesystem case behavior。

NFC-equivalent Unicode path collision 仍属于 `R2-M3-S6`。P5 当时的 production 会先以 non-ASCII
path 拒绝两条候选，因此 P5 不证明 NFC collision ordering、Unicode case folding 或正式
schema behavior。

## Deterministic fixture contract

建议 checked-in fixture 使用：

```text
schema = autojs6.typescript.r2-m2-p5.compiler-boundaries
revision = 1
scope = desktop-compiler-only/no-production-admission
```

fixture 必须冻结：

- Rhino/Node authority 的 exact canonical options JSON 与 SHA-256；
- 14 rows 的 exact ID、顺序、kind、outcome 与 production relationship；
- generator descriptor 的 exact keys、line、repetition、rendered UTF-8 byte length、token count
  与 SHA-256；
- 每个 positive row 的 sorted source collection SHA-256；
- 每个 positive row 的 diagnostics、emitSkipped、4 个 output descriptors 与 output collection
  SHA-256；
- collision rows 的 pre-Program zero shape；
- declared/reversed、2 repetitions 与四类 escape counters。

Collection canonical 与 P4 相同：

```text
sorted records;
UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)
```

Unicode path 使用 UTF-8 写入 canonical input，不能使用 US-ASCII replacement。排序使用稳定
UTF-16 code-unit comparator，不得使用 locale-aware sort。fixture 不得包含未定稿 expected、
`expected: null`、record-mode 环境变量或自动改写 golden 的路径。

## Oracle 与 production 隔离

P5 direct harness 复用 P4 的 generated common/profile declarations、snapshot-only
CompilerHost 与 `ts.createIncrementalProgram`，但拥有独立 schema、fixture 和 tasks。所有
host filesystem reads 只允许发生在 fence 安装前加载 compiler、fixture 与 generated
declarations；compile action 内必须安装并实际核对：

- `ts.sys`；
- Node host filesystem；
- DNS/TCP/TLS/HTTP(S)；
- child process 与 worker threads；
- `eval`、`Function`、`node:vm` 和 emitted-JavaScript execution route。

P5 不允许修改：

- `R2CompilationProfileRegistry` options、revision 或 admission sets；
- archive/ZIP、manifest、snapshot 或 Worker path grammar；
- source-kind、package metadata/target 或 resolver policy；
- token/output quota values；
- native result、generated mapping 或 private-output schema；
- public Binder/AIDL、capability metadata、Node Runtime dependency 或 host routing。

尤其不得把中文 direct compile、Unicode emit、65,536-token compile 或 collision rejection
描述为 Rhino/Node runtime behavior、Android/Binder、device、public 或 end-to-end evidence。
emitted JavaScript 在 P5 中永不执行。

## JVM/static gates

JVM contract 必须至少证明：

1. strict parse fixture exact schema、authority、row、source/generator、expected/output/map keys；
2. 重建所有 source texts、UTF-8 identities、token counts 与 output collection SHA-256；
3. 中文 path direct-positive 但被当时的 production printable-ASCII authority 稳定拒绝；
4. ASCII space ordinary source path 被 Rhino/Node revision-1 source admission 接受；
5. CRLF/BOM/raw Unicode 未在 identity 前被规范化，lone surrogate fail closed；
6. 65,536-token generated source exact，profile aggregate 不超过 262,144；
7. collision pair 被真实 production ASCII case-fold authority pre-Program 拒绝；
8. Node revision 1/2 options identity 相同、runtime capabilities identity 不同，revision 2
   继续 Worker-result-only；
9. compiler/public/runtime flags 和 production path/schema authorities 保持原值。

static aggregate 还必须绑定 archive、snapshot、Worker、generated mapping 与 private output 的
printable-ASCII/collision markers，以及既有 fatal UTF-8、65,537/file、project-token overflow
和 clean-successor Worker gates。

## Gradle task contract

最终任务名：

- `verifyR2M2P5CompilerBoundariesDesktop`：执行 48 次 TypeScript compiles 与两个
  pre-Program collision negatives；
- `verifyR2M2P5CompilerBoundaries`：聚合 P4、P5 desktop/JVM、Worker token/UTF-8 regression、
  APK/API/source/public/freeze fences；
- `verifyR2ProjectCompilerCore`：P5 aggregate 的最终父 gate。

P5 已由最终 Core 关闭。desktop/JVM/APK static gate 不要求 ADB；本阶段没有把设备运行加入
P5 closure。

## 验收证据

2026-08-11 的最终验收结果如下：

- `verifyR2M2P5CompilerBoundariesDesktop` 对 7 类 boundary × 2 个冻结 profile 的 14 rows
  完成校验；12 个 positive rows 以 declared/reversed 两种顺序各重复 2 次，共执行 48 次
  TypeScript 6.0.3 compiles；
- 两个 canonical-collision negative rows 共完成 8 次 pre-Program rejection，两个 high-token
  positive rows 的 `+1` mutation 共完成 8 次 pre-Program token-overflow rejection；两类 negative
  均为零 compiler API call、零 output write；
- high-token generator 的单文件边界为 65,536 tokens，65,537 tokens 稳定拒绝；加入冻结
  declarations 与第二个 root 后，Rhino/Node profile aggregate 分别为 152,036 / 191,040 tokens，
  均未越过 262,144-token project ceiling；
- JVM contract、production authority regressions 与 P5 aggregate 均通过；中文路径保持
  direct-compiler positive / 当时的 production printable-ASCII negative，ordinary ASCII 空格 source path
  保持 production-compatible；
- 最终 `verifyR2ProjectCompilerCore`：`BUILD SUCCESSFUL in 4m 19s`，521 actionable tasks
  （109 executed、412 up-to-date）；
- 四项 public capability、runtime engine、end-to-end Node revision-2 admission 与 Node Runtime
  dependency 均保持关闭。本阶段未执行 emitted JavaScript，未运行 ADB，也未使用设备。

## 风险审计

### High

1. **为中文 direct positive 放宽 production path。** 这会同时改变 ZIP、manifest、snapshot、
   Worker、mapping、output 与 M3 schema 范围，不能作为 P5 fixture 工作夹带。
2. **用 TypeScript diagnostics 代替 collision authority。** collision 必须在 Program 前由
   production policy 拒绝；`TS1149` 不是 acceptance。
3. **identity 前规范化 CRLF/BOM/Unicode。** 这会使 manifest/hash authority 与真实 input
   bytes 脱节。
4. **高 token golden 越过 output fence。** 输入 token 合法不代表 output 合法；generator
   必须同时保持当前 output envelope。
5. **把 compiler proof 提升为 runtime/public。** P5 不运行 output、Binder、ADB 或设备，五项
   public/runtime flags 必须保持关闭。

### Medium

1. Node UTF-8 encoder 会替换 lone surrogate；hash 前必须先验证 Unicode scalar sequence。
2. `ignoreBOM=true` 容易被误读为 strip BOM；P5 必须按“保留 `U+FEFF`”冻结。
3. 空格 source path 可接受不代表 package-target path 可接受。
4. locale-aware sort/lowercase 会使 Unicode fixture 与 production ASCII fold 漂移。
5. actual resource line endings 不稳定；CRLF 应由 escaped text 或冻结 generator 产生。
6. Node revision 2 重复 golden 不增加 compiler evidence，并可能掩盖真正的 runtime authority
   差异。
7. P5 不能关闭 NFC collision；该项必须继续明确指向 `R2-M3-S6`。

## 关闭条件

以下关闭条件已全部满足：

- [x] 14-row checked-in fixture 完整封口，无 placeholder 或 record bypass；
- [x] 12 positive rows 的 48 次 TypeScript 6.0.3 compiles 全部 exact-match；
- [x] 两个 collision rows 均在 Program 前稳定拒绝并产生零 output；
- [x] JVM contract 重建 raw UTF-8、CRLF、BOM、Unicode、token 与 collection identities；
- [x] 中文 direct-positive/production-negative、空格 source production-compatible 边界均有真实
  authority gate；
- [x] 65,536 positive 与既有 `+1`/aggregate negative Worker gates 全部通过；
- [x] P5 aggregate 与最终 `verifyR2ProjectCompilerCore` 全部 PASS；
- [x] production profile/path/schema/token/native/public/runtime authorities 保持原值；
- [x] 文档记录真实 task 输出，并明确未运行 emitted JavaScript、ADB 或设备。
