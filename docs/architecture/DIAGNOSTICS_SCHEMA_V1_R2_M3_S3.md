# R2-M3-S3 Diagnostics JSON Schema v1

状态：**已完成（PASS，2026-08-22）**。本文冻结 `R2-M3-S3` 的 formal diagnostics JSON
schema revision 1：project-scoped URI、UTF-16 range、TypeScript category/code/message、related
information、canonical ordering 与 ordinary size/count limits。canonical fixture、JVM/API/source
gate、TypeScript 6.0.3 desktop Worker、public-preview consumer compile、Debug/Release APK、
AndroidTest compile-and-package、S3 aggregate 与最终无排除 Core 均已在当前源码上通过。本项没有运行 ADB、
connected tests 或 device instrumentation，也没有扩大 Public/runtime evidence。

> **S1-2 amendment（2026-08-23）。** schema revision 保持 1；snapshot source path
> 现接受 canonical UTF-8，`uri` 继续是 printable ASCII，并对每个 UTF-8 byte 使用大写
> `%HH` 编码。此前合法的 ASCII URI 完全不变。

## Schema identity 与版本边界

`diagnostics.json` 的唯一 formal identity 为：

```text
path                     = diagnostics.json
schema                   = autojs6.typescript.diagnostics
revision                 = 1
diagnosticsQuotaRevision = 1
```

成功提交 output archive 后，terminal Bundle 额外携带：

```text
diagnosticsSchemaRevision = 1
```

compiler metadata 也发布同一 revision，供未来 host 在不解析 archive 之前协商 consumer 版本。
cancel、timeout、failed 或 output commit 失败的 terminal 不携带该 key，且 caller output 保持空。

旧内部 identity：

```text
autojs6.typescript.diagnostics.provisional@2
```

只作为拒绝 fixture 与 S2 历史边界保留，不是 formal revision 1 alias。unknown schema/revision
必须 fail closed，consumer 不得按字段相似度猜测、降级或把旧 revision 重解释为 v1。

diagnostics schema revision 与以下版本相互独立：

- `outputArchiveSchemaRevision=1` 描述 archive manifest/outcome/provenance/artifact authority；
- `outputArchiveTransportVersion=1` 描述 ZIP/PFD transport；
- `diagnosticsQuotaRevision=1` 描述本 diagnostics document 的 ordinary semantic quota；
- frozen v1 API 中较大的 diagnostics count/byte 常量仍是 archive transport/validation ceiling，
  不是 ordinary diagnostics schema authority。

## Exact document envelope

formal document 使用 canonical UTF-8、无 BOM，并按以下顺序精确写出 top-level keys：

```text
schema
revision
diagnosticsQuotaRevision
requestId
projectId
targetProfile
profileSchemaRevision
profileRevision
configSchemaRevision
configKind
positionEncoding
lineBase
columnBase
diagnosticCount
relatedInformationCount
diagnostics
```

`diagnosticCount` 必须等于 `diagnostics.length`；`relatedInformationCount` 必须等于所有 primary
diagnostic 的 `relatedInformation.length` 之和。profile/config/request/project identity 来自 S1 accepted
input 与 S2 output authority，不能由 Worker result 覆盖。未知或缺失字段不是兼容扩展点；新字段需要新
schema revision。

空诊断仍生成完整 formal document，而不是缺省 `diagnostics.json`：

```json
{
  "schema": "autojs6.typescript.diagnostics",
  "revision": 1,
  "diagnosticsQuotaRevision": 1,
  "requestId": "...",
  "projectId": "...",
  "targetProfile": "rhino",
  "profileSchemaRevision": 1,
  "profileRevision": 1,
  "configSchemaRevision": 1,
  "configKind": "none",
  "positionEncoding": "utf-16-code-unit",
  "lineBase": 1,
  "columnBase": 1,
  "diagnosticCount": 0,
  "relatedInformationCount": 0,
  "diagnostics": []
}
```

## Primary diagnostic shape

每条 primary diagnostic 的 exact base keys 与顺序为：

```text
phase
uri
range
category
code
message
relatedInformation
```

`phase` 只接受以下固定顺序域：

```text
options → syntactic → global → resolution → semantic → emit
```

只有 `phase=resolution` 的内部 controlled-resolution diagnostic 才携带条件字段：

```json
"nodeResolution": {"policyCode": "..."}
```

该字段仍绑定既有 stable policy-code/diagnostic-code authority；它不改变 v1 base shape，也不允许
普通 TypeScript diagnostic 注入 resolver evidence。

primary diagnostic 的 canonical sort key 为 phase order、source path、start、length、line、column、
category、code、message。排序使用接受后的 canonical snapshot path，而不是 caller alias 或生成 URI；
因此相同 accepted snapshot 与 compiler result 得到 byte-deterministic diagnostics document。

## Project-scoped canonical URI

file-associated primary 与 related diagnostic 不再把 Worker 内部 `sourcePath` 暴露到 formal JSON。
packager 根据 exact accepted snapshot path 生成：

```text
autojs://source/p-<lowercase-sha256(projectId UTF-8)>/<canonical-domain-path>
```

合法 path domain 只有：

```text
/sources/**
/declarations/**
/packages/**
```

规则如下：

- `projectId` 先按 strict UTF-8 编码，再计算 lowercase SHA-256；namespace 固定带 `p-` 前缀；
- path 逐 segment 编码，ASCII unreserved `A-Z a-z 0-9 - . _ ~` 原样保留，其余 byte 使用大写
  `%HH`；`/` 只作为 canonical segment separator；
- 接受的 snapshot path 本身受 strict UTF-8 byte length、depth、控制字符、`.`/`..`、backslash 与 source-kind
  admission 约束；URI 生成不会重新接受 archive parser 已拒绝的名字；
- URI 最长 4096 个 printable ASCII characters；超限 fail closed；
- `/sources/` domain 与现有 generated-module mapping 的 `originalSourceUri` 使用相同 project
  namespace 与 encoding，因此 diagnostics、Source Map/module manifest 可引用同一 original identity；
- URI 只取决于 `projectId + accepted path`，与 entry-file selection、caller alias、host filesystem、
  staging filename 或 Worker execution order 无关。

canonical fixture 中：

```text
projectId = diagnostics-schema-project
path      = /sources/nested/hash# percent%.ts

uri = autojs://source/
      p-9876095ce2297318967f87b9d9e90bd863e8c90940a23bb3b74079cfaa74526a/
      sources/nested/hash%23%20percent%25.ts
```

上面的换行只用于阅读；wire value 是单行 URI。

fileless diagnostic 写 `uri=null` 且必须写 `range=null`。file-associated diagnostic 可以有
`uri!=null, range=null`，表示已知文件但 TypeScript 没有提供 exact span。`uri=null, range!=null`
永远非法。

## Range 与坐标语义

非空 range 的 exact shape 为：

```json
{"start": 7, "length": 5, "line": 2, "column": 3}
```

冻结语义：

- `start` 与 `length` 是 TypeScript Compiler API 的 UTF-16 code-unit 数量，zero-based；
- `line` 与 `column` 是 human-facing UTF-16 line/character location，各自 one-based；
- `start >= 0`、`length >= 0`、`line >= 1`、`column >= 1`；
- `start + length` 必须能在 signed 32-bit range 内表示；
- 四个字段必须全部存在或 range 整体为 `null`，partial range 一律拒绝；
- 位置必须关联 accepted snapshot 中的合法 source/declaration/package path，不能引用 host file、
  输出文件或 snapshot 外路径。

document envelope 显式写出：

```text
positionEncoding = utf-16-code-unit
lineBase          = 1
columnBase        = 1
```

consumer 因此不需要从 TypeScript 版本、Java/Kotlin `String` 或 Source Map conventions 猜测坐标单位。

## Category、code 与 message

v1 直接冻结 TypeScript `DiagnosticCategory` 的 numeric identity：

| category | value |
| --- | ---: |
| warning | 0 |
| error | 1 |
| suggestion | 2 |
| message | 3 |

其他整数全部拒绝。`code` 必须是正整数；controlled resolution diagnostic 还必须与其 frozen
policy code 的 stable diagnostic code 完全匹配。`message` 使用 TypeScript nested message chain 的
稳定 flatten 结果，最多 4096 个 UTF-16 characters；JSON writer 按 canonical escaping 输出，不把
换行、引号或 non-ASCII text 重新解释为 URI/path。

category `1` 仍是 blocking error authority：存在 blocking diagnostic 时只能得到
diagnostics-only outcome、`emitSkipped=true`、module/map inventory empty。warning/suggestion/message
本身不能把非法 output/no-emit combination 变成可表示 outcome。

## Related information

S3 首次把 TypeScript `diagnostic.relatedInformation` 从 Worker 完整保留到 formal archive。每条 related
record 的 exact keys 与顺序为：

```text
uri
range
category
code
message
```

related record 不重复 `phase`。它继承 primary diagnostic 的 phase 进行 source-path admission：例如
`/packages/**` 仍只允许 controlled resolution phase 使用。它有与 primary 相同的 URI、range、
category、code、message/nullability 规则，并且必须属于同一个 exact accepted snapshot。

Worker 的内部 framed-result payload 只在 TypeScript 实际提供 related information 时附加该数组，
以保留旧 M2 no-related payload 的 exact shape regression；native parser 将字段缺失规范化为 empty
list。formal `diagnostics.json` 则**总是**为每条 primary diagnostic 写
`"relatedInformation": []` 或非空数组，consumer 不需要处理字段缺失。

native model 对 related list 做 defensive immutable copy。packager 在每条 primary 内按 source path、
range、category、code、message 排序，然后才生成 URI 与 JSON，因此 caller 后续修改原 list 或 Worker
返回顺序都不能改变 committed bytes。

## Frozen ordinary limits

`diagnosticsQuotaRevision=1` 冻结以下 inclusive limits：

| domain | v1 limit |
| --- | ---: |
| primary diagnostics | 1,000 |
| related records per primary | 1,000 |
| related records across document | 1,000 |
| primary/related message | 4,096 UTF-16 characters |
| complete canonical diagnostics document | 512 KiB UTF-8 |

512 KiB 覆盖完整 document：top-level envelope、所有 primary diagnostics、所有 related records、
URI/range 与 JSON punctuation/escaping 都计入；它不是只对 Worker diagnostics array 或 message payload
计数。边界值可接受，`+1` 拒绝。

Worker 与 native protocol parser 使用 `1,000 / 512 KiB` 做保守 early prefilter，并分别限制 per-primary
及 total related count。private packager 在 canonical ordering、URI 转换与完整 JSON serialization 后
重新测量 UTF-8 bytes；只有这个 post-serialization 检查能授权 archive commit。若 envelope/URI/escaping
使 Worker array 虽通过而完整 document 超限，packager 仍拒绝。

frozen v1 transport 中既有的 `10,000 diagnostics / 8 MiB diagnostics entry` 只用于 ZIP/PFD validation
与兼容性，不是 ordinary schema capacity。S3 没有修改 frozen v1 Binder/AIDL 或 transport 常量，也
不允许 consumer 用较大的 transport ceiling 绕过 schema quota。

## Validation 与 failure policy

生产链路按以下顺序关闭：

```text
TypeScript diagnostic
  → Worker flatten/category/code/range/related/count/byte prefilter
  → strict framed-result exact-key parser
  → immutable native diagnostic model + exact-snapshot membership
  → canonical sort + project URI + full diagnostics JSON
  → 512 KiB post-serialization check
  → S2 artifact size/SHA-256/inventory binding
  → deterministic ZIP commit
```

以下情况全部 fail closed：

- unknown/legacy diagnostics identity；
- unknown category、non-positive code、oversized message；
- partial/negative/overflowing range，或 null URI 携带 range；
- 非 canonical path、错误 phase/path domain、snapshot 外 primary/related path；
- primary、per-primary related、total related 或完整 document byte count 超限；
- declared count 与实际 arrays 不一致；
- diagnostics artifact 的 S2 size/hash/source inventory 与 committed bytes 不一致。

失败发生在 archive commit 前，映射为既有 output transport failure/empty-output terminal，不自动 retry，
也不留下 private staging archive。专门的 packager regression 使用 129 条、每条 4096 characters 的
diagnostic，证明完整 document 超过 512 KiB 时在 ZIP commit 前拒绝并清理 staging。

## Consumer 与 capability fence

`TypeScriptPluginMetadata`、successful completion Bundle 与 public-preview test consumer 使用同一
`TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION` authority。public-preview 对 emitted 与
diagnostics-only archive 都读取 bounded `diagnostics.json`，校验 exact schema/revision、quota revision、
request/project identity、position encoding/base 与 count；失败 terminal 则校验 revision key 缺失。

S3 只冻结 producer/consumer contract，不改变以下状态：

- `typescriptCompilerAvailable=false`；
- `typescriptProjectCompilation=false`；
- `typescriptSemanticDiagnostics=false`；
- `typescriptSourceMapV3=false`；
- `typescriptNodeRuntimeRequired=false`；
- Node revision 2 继续是 Worker-result/private-output authority；
- AutoJs6 host consumer、external production route 与 Rhino/Node runtime execution 仍未开启；
- device runtime、Public E2E、M4 Release matrix 与 M5 promotion 均未被本静态/编译证据替代。

## Canonical fixture 与验证 gates

canonical fixture 位于：

```text
app/src/test/resources/r2-diagnostics-schema/s3-v1.json
```

fixture identity 为
`autojs6.typescript.r2-m3-s3.diagnostics-schema/revision=1`。它机器复核 exact root/document/primary/
related/range shapes、URI namespace/encoding、category identities、ordinary/transport limit separation、
consumer example、failure policy 与 no-promotion fence。

验证入口：

- `:app:verifyR2M3S3DiagnosticsSchemaV1Fixture`；
- `:app:verifyR2M3S3DiagnosticsSchemaV1`；
- `:app:verifyR2ProjectCompilerCore`（最终无排除回归）。

| evidence | 当前状态 | 可证明范围 |
| --- | --- | --- |
| focused affected tests | **PASS** | 69 tests；schema/fixture/model/protocol/archive/related-information |
| full app JVM tests | **PASS** | 477 tests，0 failures/errors/skipped |
| TypeScript API JVM tests | **PASS** | 26 tests，0 failures/errors/skipped |
| S3 fixture gate | **PASS** | formal fixture、public/native/Worker/packager/public-preview source consumers |
| targeted S3 aggregate | **PASS** | 348 actionable：88 executed、260 up-to-date；`BUILD SUCCESSFUL in 3m` |
| final no-exclusion Core | **PASS** | 537 actionable：159 executed、378 up-to-date；`BUILD SUCCESSFUL in 4m 39s` |
| ADB/device/Public runtime | **NOT RUN / NOT CLAIMED** | S3 不需要且不能据此扩大 static/schema evidence |

## 后续边界

S3 完成后 M3 从 `8/18` 推进到 `9/18`。下一活动项是 `R2-M3-S4`：让 manifest 显式携带
original/generated URI、module kind、JS/map pairing、file size/SHA-256 与 runtime-capability
revision。S3 不提前关闭 S4/S5/S6、O1/O2/O3 或 M3 exit criteria；尤其不把 diagnostics URI
冻结等同于完整 emitted-manifest/runtime handoff 已完成。
