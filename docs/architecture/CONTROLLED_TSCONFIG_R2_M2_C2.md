# R2-M2-C2 Raw tsconfig Identity and Parser Wiring

> **R2-M3-S1 supersession（2026-08-14）。** 本页保留 M2 的 pre-S1 input provenance；正文中的
> request-manifest revisions `2/3` 与当时的 Worker start shape 不是当前 formal alias/fallback。
> 当前只接受 `autojs6.typescript.input-archive/revision=1`，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。非 input-schema 的 C2 parser/policy 结论继续保留。

日期：2026-08-01

状态：**C2 internal contract complete；尚未开放 production tsconfig compilation。**
当前 production authority 仍为
`profileSchemaRevision=1/profileRevision=1/configSchemaRevision=1/configKind=none`，
四项 compiler capability 仍全部为 `false`。C2 只建立 raw config identity、独立
framing、plugin-owned parser 与 revisioned parsed-config state；
[R2-M2-C3](CONTROLLED_TSCONFIG_R2_M2_C3.md) 已在其后建立 41-key effective authority，
[R2-M2-C4](COMPILER_ESCAPE_CLOSURE_R2_M2_C4.md) 又关闭 transformer/plugin、
package/deferred path、Binder 与 host/network escape；实际 Program/root activation 仍等待
C7。

## 目的与边界

C1 已冻结 closed-world policy，但没有运输或解释 caller config。C2 补齐以下链路：

```text
control metadata
  <-> request-manifest revision 3 config descriptor
  <-> exact archive-root tsconfig.json bytes
  <-> native config framing digest
  <-> Worker parser result
```

任一 identity、quota、revision 或 policy authority 不一致都在 JSONC 解释前 fail
closed。只有完整通过 transport/integrity gates 的 bytes 才能进入 config diagnostics
分类。这一切都不修改 frozen v1 AIDL/Binder transaction prefix，也不复用
`compilerOptionsJson` 建立第二套 option authority。

## Revision 与 identity

### Additive public metadata

C2 为未来受控 config request 预留以下 additive Bundle key：

| Key | 值/语义 |
| --- | --- |
| `configSchemaRevision` | controlled tsconfig 使用 `2` |
| `configKind` | `tsconfig` |
| `configByteLength` | Bundle `Long`；exact raw bytes；inclusive `0..49152` |
| `configSha256` | exact lowercase SHA-256 |
| `configPolicyRevision` | `1` |
| `configPolicyFingerprint` | C1 policy fingerprint |

C1 policy fingerprint 固定为：

```text
849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d
```

这些 key 必须被 public-metadata detection 识别，不能绕回历史 R0 路径。revision 与
schema 字段使用 Bundle `Int`，`configByteLength` 使用 `Long`；错误数字类型不得宽松
转换。即使 C3 internal authority 已完成，完整、合法的 controlled metadata 在 public
activation 前仍必须同步以 `ERROR_CONTRACT_MISMATCH` 拒绝；unsupported
schema/kind/policy 同样归类为 contract mismatch，缺失、类型错误、非法长度或摘要则为
`ERROR_INVALID_REQUEST`。拒绝发生在 FD ownership transfer/coordinator admission 前；
service 可以先做 read-only descriptor validation，但不得接管 FD ownership。

### Archive manifest

历史 provisional request-manifest revision `2` 的 exact shape 继续只表示
`configSchemaRevision=1/configKind=none`，不得原地重新解释。受控 raw config 使用新的
revision `3`，并增加 exact `config` object：

```json
{
  "path": "tsconfig.json",
  "size": 123,
  "sha256": "<64 lowercase hex>",
  "policyRevision": 1,
  "policyFingerprint": "849f...24d"
}
```

revision 3 archive 的 caller entries 精确为 request manifest、root-level
`tsconfig.json` 与已声明 source entries。两层预算不能混淆：frozen archive/container
上限是 `4096 entries/64 MiB`，因此 revision 3 最多是 `4094 sources + manifest +
config`；framing 前的 production Worker admission 仍是 source、plugin-owned trusted
declarations 与 config 合计 `512 files/8 MiB total`。config 不是
source/declaration VFS file，不进入 TypeScript token budget，也不能成为 module/root；
它自身另受 inclusive `48 KiB` 上限约束。

### 三方绑定与重复校验

authority metadata、manifest descriptor 与实际 ZIP entry 的 path/size/SHA/policy 必须
逐字段相同。loader 从真实 entry stream 计算 size/hash，不能信任 ZIP header 或 manifest。
snapshot 只保留 immutable config metadata 与 bounded cursor，不把 file-backed config
长期物化到 native heap。framer 在读完 cursor 时再次计算 SHA-256，Worker 收完 decoded
bytes 后再计算一次。authority/manifest/实际 ZIP acceptance 的差异为
`ERROR_ARCHIVE_INTEGRITY`；已经 acceptance 后的 snapshot/framer/Worker 再校验差异表示
plugin-owned state 或 transport 漂移，fail closed 为 `ERROR_COMPILER_UNAVAILABLE`。两者都
不得继续解析，但不能把内部漂移重新归因给 caller archive。

heap-backed test snapshot 必须 defensive-copy config bytes；release/failure/cancel/timeout
均清零 config buffer、当前 frame buffer 与 Worker `Uint8Array`。file-backed cursor 继续
遵循 exact open/read/close telemetry 和 single-thread stop-and-wait ownership。

## 独立 input framing

受控 config 不伪装成 source file。controlled `projectInputStart` 在历史 exact fields
之外携带：

```text
configByteLength
configSha256
configPolicyRevision
configPolicyFingerprint
```

随后在任何 source file frame 之前发送：

```text
projectInputConfigStart(kind, requestId, sequence, path, byteLength, sha256)
  -> ACK
projectInputConfigChunk(kind, requestId, sequence, chunkIndex, data)*
  -> ACK each
projectInputConfigEnd(kind, requestId, sequence, chunkCount)
  -> native digest recheck
  -> Worker digest/UTF-8/parser/policy validation
  -> ACK only after success
```

历史 config-none `projectInputStart` exact shape 与 file state machine 保持不变。
config frame 与 file frame 共用全局连续 `sequence`、canonical padded Base64、64 KiB raw
frame 上限和 window=1 ACK。config 失败后不得继续 source framing、Program creation 或 emit。

## Plugin-owned JSONC parser

Worker parser 是 raw config 的唯一解释者。它先做 bounded preflight，再进行 closed-world
policy mapping；不得调用 host filesystem/network，也不得使用正则删除 comment。

revision 1 grammar 精确支持：

- UTF-8，最多一个开头 BOM；拒绝 invalid UTF-8 与 lone surrogate；
- JSON double-quoted object key/string、标准 decimal number、`true/false/null`；
- `//` 与 `/* ... */` comment；
- object/array 最后一个 element 后单个 trailing comma；
- 每一层 object 都检测 decoded-key duplicate，不采用 TypeScript last-wins；
- root value 计入 depth/node，inclusive depth `16`、nodes `4096`；
- selector 原始 element 在校验/normalization/dedup 前合计最多 `512`；
- selector 以 unescaped 与 NFC-normalized UTF-8 byte size 较大者计量，inclusive
  `4096` bytes/item。

parser 显式拒绝 JavaScript/TypeScript 宽松语法，包括 single-quoted/unquoted key、array
hole、hex number、`.5`、`1.`、numeric separator、非有限数和多余 trailing token。
comments、comma、BOM 与 object property name 不计 JSON node。

closed-world mapping 继续由 C1 policy 定义：只允许五个 top-level key 与 18 个 boolean
compiler option；profile-owned、已知未开放、未知 property 使用各自稳定 policy code。
C2 parsed result 只保存字段 presence、显式 boolean option 与 NFC-normalized selector；
不填 TypeScript/default/profile option，不计算 effective-options fingerprint，也不执行 C7
的 snapshot root/glob selection。

## Failure ordering 与 typed result

判定顺序固定为：

1. metadata/schema/kind/policy contract；
2. raw byte quota；
3. metadata ↔ manifest ↔ actual ZIP size/hash acceptance integrity；
4. post-acceptance native framer/Worker size/hash drift；
5. encoding、JSONC、duplicate、complexity；
6. closed-world top-level/compiler-option policy；
7. C3 effective-options merge/canonical/combination validation（现已由 C3 执行）；
8. C7 root selection（仍未执行）。

前 1–4 类使用既有 transport/contract terminal：

- metadata 缺失/错误 Bundle 类型、declared length 越界或摘要 lexical 畸形：
  `ERROR_INVALID_REQUEST`；
- unsupported revision/kind/policy：`ERROR_CONTRACT_MISMATCH`；
- actual ZIP config bytes 超过 `48 KiB`：`ERROR_ARCHIVE_QUOTA`；
- acceptance 前 raw size/hash 不一致：`ERROR_ARCHIVE_INTEGRITY`；
- acceptance 后 plugin-owned snapshot/framer/Worker 漂移：
  `ERROR_COMPILER_UNAVAILABLE`。

第 5–6 类形成内部 typed config failure：`stage=config`、
`outcome=diagnostics`、`errorCode=ERROR_COMPILATION_DIAGNOSTICS`、稳定
`policyCode=CONFIG_*`、emitted module count 为 `0`。message 有界且不回显完整 caller
内容。M3 才冻结并提交正式 diagnostics-only ZIP schema，因此 C2 不声称已经产生 public
diagnostics artifact。

在 C2 closure 时，成功 parse 只创建 revisioned internal parsed-config state。当前 C3
已把 config-end commit point 延伸为 parse → merge → combination validation → canonical/
fingerprint → immutable state freeze；只有全部成功才 ACK config end。由于 C7 尚未执行
selector/root matching，受控请求到达 project end 时仍 fail closed，不进入现有
config-none compiler path，也不创建 compiler result 或 ZIP。

## 非激活证明

C2 完成后仍要求：

- `R2CompilationProfileRegistry.PROVISIONAL_RHINO` 保持 schema `1` / kind `none`；
- request-manifest revision `2` 的 config-none exact shape 与原 desktop golden 不变；
- `compilerOptionsJson` 继续缺省或精确为 `{}`；service 可先执行 non-owning descriptor
  inspection，但 C4 已让任何其他值在 coordinator admission/FD ownership transfer 前同步拒绝；
- ordinary public request 即使携带完整 C2 metadata 仍同步拒绝；
- current config-none Worker compile golden 继续通过；
- `typescriptCompilerAvailable`、`typescriptProjectCompilation`、
  `typescriptSemanticDiagnostics`、`typescriptSourceMapV3` 全部为 `false`。

## 验收

原子 gate：

```powershell
.\gradlew.bat :app:verifyR2M2C2ControlledTsConfigParser
```

gate 覆盖 API/app JVM、frozen/additive API freeze、archive revision 2/3 exact shape、
file-backed cursor/digest/zeroization、native framing ACK/integrity、Worker JSONC/policy
golden、public reason classification 以及上述非激活 fences。它不运行设备；C2 没有改变
公开执行路径，因此设备 promotion matrix 仍由后续 activation/release task 负责。

C4 的独立 gate 在 C2 之上继续锁定 package/deferred path、transformer/plugin、Binder 与
host/network closure；下一项 C5 只能通过新的 manifest revision 增加正向 package metadata。

2026-08-01 实际结果：

```text
R2-M2-C1 controlled tsconfig policy verified: 18 options,
849f882618d294d61683cc5930ecd4855f53435d7a673ceda08ad127504fa24d
R2-M2-C2 controlled tsconfig parser verified: 3 accepted, 30 rejected fixtures
R2 provisional Rhino desktop golden passed (TypeScript 6.0.3).
BUILD SUCCESSFUL
114 actionable tasks: 6 executed, 108 up-to-date
```

app JVM 汇总为 214 tests / 0 failures / 0 errors / 0 skipped；API v1 freeze、Binder
transaction prefix、compiler-host source policy、旧 config-none/P3b 4,607-file/retained-heap
golden 均通过。C2 没有 public activation 或 promotion-relevant runtime 行为变化，因此本项
未新增设备矩阵。
