# R2-M2-P1 Provisional NodeNext Profile

日期：2026-08-02

状态：**internal/debug-only P1 已完成；production/public project compilation 仍未激活。**

## 目标与边界

P1 在 C8 已锁定的 declaration profile layering 上建立第二套精确 compiler profile：
`PROVISIONAL_NODE_NEXT`。它只服务 same-UID `DEBUG_PROBE` 内部链路，用于验证 NodeNext
options、Node declarations、snapshot/framing、Worker Program、native result parser 与私有 output
之间是否共享同一 authority。

P1 不是 Node Runtime 集成，也不是 public Node compilation。它不接受任意 `tsconfig.json` 或
package metadata，不开放 `.mts/.cts`，不决定 package `type` 对 `.ts` module kind 的影响，也不
改变 frozen AIDL、public API 或 R1 distribution。上述 module-kind、resolver v2 与 output URI
语义由 P2 单独冻结。

P1 的 source/output 边界固定为：

```text
input source:  canonical /sources/*.ts
output file:   deterministic *.js
configKind:    none
origin:        DEBUG_PROBE
```

因此，P1 的 NodeNext 名称表示 TypeScript compiler options 与 Node declaration environment，
不表示已经开放 Node executable runtime 或全部 NodeNext source extensions。

## Profile authority

Node profile 使用独立、精确匹配的 authority：

- `targetProfile=node`
- `profileSchemaRevision=1`
- `profileRevision=1`
- `configSchemaRevision=1`
- `configKind=none`
- `module=199`（TypeScript `NodeNext`）
- `moduleResolution=99`（TypeScript `NodeNext`）
- `target=ES2018`
- 其余 fixed compiler options 与既有 config-none profile 保持同一 closed-world inventory

canonical effective-options JSON、effective-options fingerprint 与 runtime-capabilities fingerprint
均属于该 Node authority。request admission、snapshot、framed transport、Worker start/result、native
result parser、coordinator 与 output archive 必须以完整 tuple 和 fingerprint 绑定，不能只比较
`targetProfile=node` 字符串。

P1 只接受 `configKind=none`。任何 raw config、package metadata 或 retained package target 随 Node
P1 request 出现都必须 fail closed；controlled `tsconfig` 仍是 Rhino-only 的既有内部分支。

## Declaration layering 与隔离

P1 复用 C8 独立 distribution，不复制或重新解释声明资产：

- Rhino Program：`common + rhino`；
- Node Program：`common + node`；
- declaration entry 是 compiler-only root，不进入 caller `selectedRootNames`、emit inventory 或
  output manifest source roots。

隔离在 archive/snapshot materialization、framed transport ingress 与 Worker VFS/Program 三层执行。
每层都必须双向拒绝交叉污染：Rhino profile 不得携带 `/declarations/node/**`，Node profile 不得
携带 `/declarations/rhino/**`。Node profile 还必须存在 canonical
`/declarations/node/index.d.ts` entry；不能在缺层时退化为 common-only Program。

这条规则同时保留 C8 的 caller budget、path、byte、token 与 manifest hash fence。Node 声明的
trusted 身份不允许绕过任何 admission budget。

## Runtime、public 与输出边界

P1 不增加 Node Runtime 依赖：编译继续由 APK 内置 TypeScript 6.0.3 Worker 执行，不探测、不加载
也不调用宿主 `node` executable。Node desktop oracle 只属于构建期验证工具，不能成为 APK 或
运行时依赖。

四项 public capability 必须继续为 `false`：

```text
compilerAvailable=false
projectCompilation=false
semanticDiagnostics=false
sourceMapV3=false
```

public preview/request admission 仍只识别既有 Rhino boundary；即使 caller 提交完整、正确的 Node
fingerprint，也不能通过 public origin 激活 P1。P1 的私有 artifact 仍只允许 `.ts -> .js`，并以
Node authority 的 effective-options fingerprint 绑定 result 与 output manifest。

`.mts -> .mjs`、`.cts -> .cjs`、package `type`、`sourceFile.impliedNodeFormat` 与 generated URI
规则均不得从 P1 行为反推。P2 实施这些语义时必须把 Node `profileRevision` 从 `1` 提升到 `2`，
禁止在 revision 1 下静默扩大 source/output 语义。

## 验收结果

P1 专属 gate 与完整 compiler-core regression 已证明：

- TypeScript 6.0.3 真实 Program 使用 exact NodeNext `199/99` 与 ES2018 options；
- Node Program 精确装入 `common + node`，Rhino/Node cross-layer mutation 双向失败；
- config-none、`.ts`-only、`.js` output 与 contextual result/snapshot binding；
- debug-only admission、public Node rejection 与四项 public capability=false；
- 无 Node Runtime/API/AIDL/source-map surface 扩张；
- C8 declaration distribution、APK inventory、JVM/source/API 与完整 R2 compiler core 无回归。

**当前结果：P1 已通过。** 2026-08-02 的实际证据为：

- desktop oracle 使用 TypeScript 6.0.3 的 `createProgram` 与 `createIncrementalProgram`，并在
  同一 VM 内通过 production `compiler-worker.js` framed protocol 执行 2 次 Node Program；
- Program 精确装入 108 个 common 与 59 个 Node declaration files；结果只携带 user source
  provenance，declaration compiler roots 不泄漏；
- 2 次 Rhino-layer 污染在 Program 前失败且无 output，随后 2 次 clean successor 均生成同一
  `.js/.js.map`；`ts.sys`、网络、child process、Worker thread 与 Node Runtime 调用均为 0；
- native archive/result tests 覆盖 exact Node layer、common-only/cross-profile rejection，以及
  target、profile revision、module 与 moduleResolution 漂移拒绝；
- `:app:verifyR2M2P1ProvisionalNodeNextProfile`：267 actionable tasks，PASS；
- `:app:verifyR2ProjectCompilerCore`：497 actionable tasks，PASS。

上述结论只关闭 internal M2-P1。四项 public capability 与 `NODE_RUNTIME_REQUIRED` 仍为
`false`，不得据此宣称 public project compilation 已激活。

建议统一执行形式为：

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2M2P1ProvisionalNodeNextProfile
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2ProjectCompilerCore
```

## 后续

P1 验收关闭后，P2 才能在 Node `profileRevision=2` 中加入 `.mts/.cts`、受控 package `type`、
`impliedNodeFormat`、resolver v2 与 deterministic generated URI/output pairing。P2 不得修改 C6 v1
的历史语义或把 P1 debug-only profile 误写为 public availability。
