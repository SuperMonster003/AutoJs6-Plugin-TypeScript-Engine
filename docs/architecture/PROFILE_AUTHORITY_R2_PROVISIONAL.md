# R2 Provisional Profile and Config Authority

> **S1-5 current-state amendment（2026-08-23）。** 本页后续的 Node revision 2
> Worker-result-only/Public closed 描述仅是历史 provenance。当前 exact Node revision 2 已加入
> end-to-end 与 public config-none admission；revision 1 继续作为 private/debug compatibility
> default。接入字段与当前边界见
> [S1-5 NodeNext Public Admission](NODE_NEXT_PUBLIC_ADMISSION_S1_5.md)。

> **R2-M3-S1 binding（2026-08-14）。** 本页中的 request-manifest revision `2/3` 只保留为
> pre-S1 provenance，不是当前 formal alias/fallback。Formal input revision 1 现在绑定 exact
> profile revision 与 C8 `declarationProfileFingerprint`，见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。profile/options authority 与历史 receipt 继续保留。

> 当前路线提示（2026-08-11）：本文保留 P2/C1-C4 authority foundation 建立时的历史
> 边界。C5-C8、P1-P5 与 X1 后续已完成；X1 只开放 private Rhino/Node
> revision-1 end-to-end authority，Node revision 2 继续 Worker-result-only，Public/external/runtime
> 仍关闭。实时状态见 [R2 Roadmap](../roadmap/R2.md) 与
> [X1 evidence](PUBLIC_SHAPED_DETERMINISTIC_BUNDLE_R2_M2_X1.md)。

状态：R2-P2 与 R2-M2-C1/C2/C3/C4 内部契约，尚未冻结为 public schema。

## 目的

P2 将 provisional Rhino profile、`configKind=none` 和固定 compiler options 收敛到
`R2CompilationProfileRegistry.PROVISIONAL_RHINO`。它消除 request parser、VFS、
framed Worker、native result parser、output packager 与 capability fingerprint
各自维护 revision/options 常量的漂移风险。

P2 本身不是 `tsconfig.json` 或 NodeNext 支持，也不开放
`compilerAvailable`、`projectCompilation`、`semanticDiagnostics` 或
`sourceMapV3`。R2-M2-C2 已在不修改该 production authority 的前提下加入独立的
schema-2/revision-3 raw-config internal path；R2-M2-C3 又只在该分支建立 41-key
effective-options authority；R2-M2-C4 已关闭 transformer/plugin、package/deferred path、
Binder 与 host/network escape。C5/C6 尚未正向建立 package metadata/NodeNext，C7 完成前
该分支仍不进入 Program。

## Authority

production-active config-none 的唯一 authority 绑定：

- `profileSchemaRevision=1`
- `profileRevision=1`
- `configSchemaRevision=1`
- `configKind=none`
- `targetProfile=rhino`
- 完整 18 项固定 effective options
- canonical effective-options JSON 与 SHA-256
- runtime-capability canonical input 与 SHA-256

这份历史 18-key canonical effective-options JSON 使用固定 key 顺序、TypeScript
numeric enum 值和显式默认值。caller、input manifest 与 Worker 均不能提交或覆盖
`effectiveOptionsFingerprint`；该值只由 plugin authority 计算并写入 output，历史
JSON 与 fingerprint 保持 byte-for-byte 不变。

C3 controlled overlay 使用独立的 effective-options revision `1`：在历史 18-key
inventory 上补齐 16 个 C1 boolean key 与 7 个 profile-managed TypeScript 6.0.3 pin，
形成 exact 41-key canonical JSON。其 precedence、TS5052-compatible invalid combinations、
pure canonical SHA 与 config-end commit boundary 见
[R2-M2-C3 Effective Options Authority](CONTROLLED_TSCONFIG_R2_M2_C3.md)。该 overlay
不是 production registry 的替代品，也不改变 config-none authority。

## 端到端绑定

同一个 authority 依次通过下列边界：

1. additive public metadata 必须显式携带匹配的 config/profile revision 与
   `configKind`；缺失或不匹配时在 FD ownership transfer 前拒绝；
2. provisional request manifest revision 2 使用 exact keys，并同时绑定
   profile/config authority、runtime fingerprint、archive/manifest/file digest；
3. `R2ProjectSnapshot` 保存 exact authority，而不是只保存 profile 字符串；
4. `projectInputStart` frame 携带四个 revision/kind 字段；Worker 对完整 frame shape
   和唯一 Rhino/config-none authority 做 exact 校验；
5. Worker result 回传同一 authority 与完整 18 项 options；native parser 拒绝
   missing、unknown 或任何值漂移；
6. provisional output manifest/diagnostics revision 2 重新写入 authority；
   output packager 在形成私有 ZIP 前再次比较 accepted authority、Worker result 和
   effective options。

任一 revision/kind/option 不一致都不能进入 artifact commit。

## 兼容与非目标

- frozen AIDL、`TypeScriptCompilerContract`、ZIP container v1 及 compat AAR/checksum
  不修改；config keys 只增加在 post-v1 `TypeScriptR2Contract`。
- provisional request/output exact shape 都提升到 revision 2；revision 1 不被静默
  当作新 shape 接受。
- 普通 frozen-v1 request 仍按 R0 拒绝；P2 preview 的 `compilerOptionsJson` 只接受缺省或
  精确 `{}`，不建立 inline authority。
- [R2-M2-C1 Controlled tsconfig Policy](CONTROLLED_TSCONFIG_R2_M2_C1.md) 已冻结
  `configKind=tsconfig` 的 closed-world grammar、defaults 与 policy codes；
  [R2-M2-C2](CONTROLLED_TSCONFIG_R2_M2_C2.md) 已完成 raw identity/framing/parser，但
  当前 P2 production authority 继续只接受 `configKind=none`；
  [R2-M2-C3](CONTROLLED_TSCONFIG_R2_M2_C3.md) 已完成 internal effective option merge，
  C7 前继续 fail closed。
- [R2-M2-C4](COMPILER_ESCAPE_CLOSURE_R2_M2_C4.md) 已冻结 compiler escape policy、三层
  reserved path fence、exact frames 与 snapshot-only host；不改变 production registry。
- C5 package metadata、C6 NodeNext、正式
  schema v1、64 MiB file-backed input、128/256 MiB output 和 device matrix 仍开放。

## 无设备验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:verifyR2ProjectCompilerDesktop
.\gradlew.bat :app:verifyR2M2C2ControlledTsConfigParser
.\gradlew.bat :app:verifyR2M2C3ControlledTsConfigEffectiveOptions
.\gradlew.bat :app:verifyR2M2C4CompilerEscapeClosure
.\gradlew.bat :plugin-api:typescript-api:verifyTypeScriptApiV1Freeze
.\gradlew.bat verifyR2PublicPreviewFixture
```

desktop golden 直接执行 distribution 中的 TypeScript 和实际
`compiler-worker.js`；JVM tests 覆盖 canonical JSON/fingerprint、manifest exact
shape、frame/result mutation 和 output authority。最后一个聚合任务同时验证
debug/release APK、R8 marker、lint、外部 fixture 签名与 binary manifest shape，
但不替代设备 instrumentation。
