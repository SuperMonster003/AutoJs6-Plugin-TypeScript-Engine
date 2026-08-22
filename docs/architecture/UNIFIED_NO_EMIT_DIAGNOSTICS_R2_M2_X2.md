# R2-M2-X2 Unified No-emit Diagnostics

日期：2026-08-11

状态：**已完成。desktop Worker、JVM/native、focused Android runtime、targeted X2
aggregate 与最终无排除 Core 已全部通过；`R2-M2-X2` 已关闭，M2 升至 `17/18`，下一项为
`R2-M2-X3` configuration/resolution failure closure。**

## 决策摘要

X2 关闭四类已进入 TypeScript Program 的 blocking diagnostics：

| Roadmap 名称 | Internal phase | Source position |
| --- | --- | --- |
| options error | `options` | 全部为 `null` |
| syntax error | `syntactic` | exact snapshot source/range |
| global error | `global` | 全部为 `null` |
| type error | `semantic` | exact snapshot source/range |

首个 revision 精确覆盖 provisional Rhino revision 1 与 NodeNext revision 1。NodeNext
revision 2 继续只进入 Worker-result admission，不因 X2 进入 private/public Binder
end-to-end。

“停止 emit”精确定义为：

- TypeScript `builder.emit()` 调用次数为 0；
- Worker result 为 `emitSkipped=true`；
- compiler outputs 为空；
- emitted module count 为 0；
- private archive 仍合法提交 diagnostics-only bundle，精确包含 manifest 与 diagnostics。

Worker 为了完成 phase receipt、result serialization、hash/output validation，仍可进入
`R2ProjectExecutionPhase.EMIT`。该 lifecycle 名称不表示调用了 TypeScript
`builder.emit()`，两者不得混为一谈。

## Blocking category

只有 TypeScript `DiagnosticCategory.Error`（protocol category `1`）阻断 emit。Warning、
Suggestion 与 Message 可以进入统一 diagnostic model，但不能使 error-free compilation
变成 `emitSkipped=true`，也不能删除本应产生的 outputs。

`R2ProjectCompilation` 自身、framed-result parser 与 private-output stage 必须一致约束：

```text
has category=1 diagnostic
  => emitSkipped=true && outputs=[]

has no category=1 diagnostic
  => emitSkipped=false
```

resolution 与 emit phase 的既有 blocking behavior 继续服从同一 invariant；X2 不改变其
独立 evidence/authority。

## Options/configuration boundary

X2 的 `options` 不是非法受控 tsconfig。C3 已冻结 TS5052-compatible invalid option
combination：它在 config-end ACK 前映射为 `CONFIG_INVALID_VALUE`，不创建 Program，也不生成
diagnostics ZIP。该行为属于 configuration failure，并继续由 C3/X3 负责。

X2 只覆盖 Program 已构造后 `program.getOptionsDiagnostics()` 返回 Error 的 defensive path。
由于正常冻结 options 不应产生此类错误，desktop harness 可使用 test-only TypeScript Proxy
注入真实 diagnostic object；不得增加 shipping request key、BuildConfig hook 或 public option
override。

## Global diagnostic boundary

正常 generated declaration distribution 不应产生 global Error。X2 可以在 desktop-only
Program seam 注入 deterministic global diagnostic；Android shipping path 不为此加入 fault
hook。source-reachable Android matrix 只需覆盖 syntactic 与 semantic，因为它们可由合法 snapshot
source 自然产生。

## Unified diagnostic record

每项 internal diagnostic 冻结为九个字段：

```text
phase, category, code, message,
sourcePath, start, length, line, column
```

`options` 与 `global` 的五个 source/range 字段必须全部为 `null`。`syntactic` 与
`semantic` 必须携带 snapshot 内 exact source path，以及完整、非负且 1-based 的 line/column
位置。partial position 必须 fail closed。

Node resolver-v2 的 additive `nodeResolution` evidence 只属于 `resolution` phase，不进入上述
四类 X2 record，也不改变九字段共同核心。

## Deduplication and order

Production collector 的 diagnostic identity 故意不包含 phase，并按以下顺序 consume：

```text
options -> syntactic -> global -> semantic -> emit
```

若同一 category/code/source/range/message identity 在多个 phase 重复出现，只保留第一次出现的
phase。X2 冻结该 first-phase precedence；不得在本阶段把 phase 加入 identity 后扩大 diagnostic
count。不同 identity 的 private diagnostics JSON 继续按 phase/source/range/category/code/message
确定性排序。

## Evidence matrix

| Layer | Required proof | 状态 |
| --- | --- | --- |
| desktop Worker | TypeScript 6.0.3 的四 phase Error、`builder.emit=0`、zero outputs、Warning non-blocking、first-phase precedence 与 6 个 clean successors | PASS |
| JVM fixture/model | exact fixture/options identity、blocking invariant、四 phase framed parse | PASS |
| private output | 四 phase 均为 exact two-entry deterministic diagnostics-only ZIP | PASS |
| Android Binder | Rhino r1 + Node r1 的 syntactic/semantic natural failures，经真实 PFD/ACK/callback 提交 diagnostics-only ZIP | PASS |
| aggregate | X1 regression、APK/API/source/public fences、Node r2 Worker-result-only、五项 capability/dependency metadata false | PASS |
| focused device/Core | explicit-serial Android receipt 与无排除 `verifyR2ProjectCompilerCore` | PASS |

## 执行证据

- X2-only direct harness：TypeScript `6.0.3`，4 个 Error phase、1 个 duplicate precedence、
  1 个 Warning non-blocking 与 6 个 clean successors 全部 PASS；
- `:app:verifyR2M2X2DiagnosticStopEmitModel`：无排除，327 actionable tasks（92 executed、
  235 up-to-date），`BUILD SUCCESSFUL in 5m 43s`；该 aggregate 承载 desktop Worker、
  JVM fixture/model、framed/private-output、X1 regression 与 APK/API/source/public fences；
- focused `R2BinderCompilationInstrumentationTest#rhinoAndNodeRevisionOneStopEmitForSourceDiagnosticsThroughPublicShapedService`：
  QV710AF65F（Sony XQ-AT72，Android 12 / API 31，WebView
  `com.google.android.webview 145.0.7632.120`）返回 `Time: 34.657`、`OK (1 test)` 与
  `INSTRUMENTATION_CODE: -1`；收尾 active instrumentation、target process 与 host ADB
  transaction 均为 0；
- `:app:verifyR2ProjectCompilerCore`：无排除，523 actionable tasks（111 executed、
  412 up-to-date），`BUILD SUCCESSFUL in 4m 41s`。

### Android artifact identity

| Artifact | Size | SHA-256 | Signer SHA-256 |
| --- | ---: | --- | --- |
| app APK | 5,887,418 bytes | `87349F05E41B5497B868F218581946CB9E8D6C65C842529332BCE917EA960EC7` | `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213` |
| AndroidTest APK | 1,559,520 bytes | `1240969836087481576DC1E7574D232477E1372B9F2D8CF3A224C060B3DD411E` | `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213` |

该设备证据只关闭由合法 snapshot source 自然触发的 syntactic/semantic matrix；options/global
继续由 desktop-only defensive seam 证明，不存在 shipping fault hook。

## 关闭结论

X2 已同时关闭 Worker no-emit、native model/framing、private diagnostics-only ZIP、
Rhino/Node revision-1 public-shaped service diagnostics 与最终 Core regression。M2 现为
`17/18`，下一项是 `R2-M2-X3`。Node revision 2 仍为 Worker-result-only；external Public、
runtime execution、四项 compiler capability 与 `NODE_RUNTIME_REQUIRED` 均保持关闭。

## Non-goals

X2 不完成：

- 正式 public diagnostics/ZIP schema freeze（M3）；
- invalid tsconfig/configuration failure（C3/X3）；
- resolver/package target failure（C6/P2/X3）；
- distinct-UID Public promotion、Node revision-2 end-to-end 或 capability activation；
- emitted JavaScript runtime execution。
