# R2-M2-C8 Declaration Profile Layering

> **Compose memo fragments (2026-10-08, 0.6.8).** The local 4.36.0 supplement adds
> `compose.memo(build, deps, options?)` and `ComposeMemoOptions` for the AutoJs6
> 6.8.0 / 5323 render fragment cache. The generated transform and runtime identity
> remain revision 7: no transform, compiler option, Node profile or public npm lock
> changed. Content hashes bind the new declaration source. The current Rhino layer
> is 53 files / 992257 bytes; common + Rhino is 161 files / 4777015 bytes, and the
> manifest still owns 118 files. The real Worker regression compiles keyed memo rows
> in a TSX render, executes the emitted calls and rejects six invalid memo inputs.
> Dated V2/F.3/F.2/F.1 identities and byte counts below remain historical.
>
> **Compose V2 components (2026-10-07, 0.6.7).** The local 4.35.0 supplement adds
> 25 V2 node types, 62 properties, slots, positional callbacks and TSX tags while
> retaining the frozen V1 and F.2 regions. The generator/runtime identity is now
> revision 7 because the JSX projection includes its independent V2 region.
> The public npm tarball lock, compiler options, Node profile and caller quotas
> are unchanged. Rhino is 53 files / 991341 bytes; common + Rhino is 161 files /
> 4776099 bytes, with 118 manifest-owned files. Quota fixtures and independent
> JVM/Gradle assertions are updated from the actual generated inventory.
>
> Full Worker verification compiles all 19 requested component families, executes
> emitted factory/namespace identities and callbacks, and rejects 16 invalid V2
> input categories. C8 Program/Worker layering, current quota checks, 534 selected
> app JVM cases and four build-logic cases pass. Debug/androidTest and lint pass;
> lint has zero errors and seven existing warnings. A real API 35 installation
> passes both packaged declaration inventory cases. Final vendored provenance is
> recorded in `build-logic/convention/src/main/resources/r2-compose/source.json`;
> signed artifacts and final host TSX validation are recorded in the F.4 stage
> evidence, `COMPOSE_V2_LOCAL_ACCEPTANCE.md`. The final signed compiler and renderer
> pass all nine real TSX cases plus five unchanged JS examples (14 total). The
> exact declaration source is `de51858b59ec88f75c56d94bcf4af600c54ff34f`.
> The dated F.3/F.2/F.1 identities below remain historical.
>

> **Compose dialogs (2026-10-07, 0.6.6).** The local 4.34.0 supplement adds
> `compose.dialog`, its alert/bottomSheet options and existing-session return type.
> The generated transform and runtime identity remain revision 6: no transform,
> compiler option, Node profile or public npm lock changed. Content hashes bind
> the new declaration source. The current Rhino layer is 53 files / 959861 bytes;
> common + Rhino is 161 files / 4744619 bytes, and the manifest still owns 118 files.
> The real Worker regression checks default alert and reactive sheet calls,
> preserved TSX/factory identities and eight invalid dialog input categories.
> Dated F.2/F.1 identities and byte counts below remain historical.
>
> Final source is declarations 4.34.0 commit
> `4512f0ef4a051c8286bd7a19cd3cd7f2d8cf50e6`. The full Worker, C8 Program/Worker
> layering, current quota, 534 selected app JVM cases and four build-logic cases
> pass. Debug/androidTest, lint and five signed release artifacts pass; lint has
> zero errors and seven existing warnings. Final signed x86_64
> `autojs6-plugin-typescript-engine-v0.6.6-x86_64-A20EA5C8.apk` is 3373593 bytes,
> SHA-256 `9c917ee44bda498ec963d88a1790b883a36af8b124bb9825ed83b620605774cc`.
> With clean host 5321 and the final signed renderer, an owned API 35 AVD passes
> all eight real TSX scenarios plus five unchanged JS examples (13 cases).
> This includes independent dialogs, state, real clicks and the normal close
> callback receiver. Logs are `app/build/compose-f3-engine-final-gate.log` and the
> host's `app/build/compose-f3-tsx-and-examples-final.log`; no remote publication
> or new physical-device/performance matrix is implied.
>

> **Compose interop integration (2026-10-07, 0.6.5).** Generator revision 6 adds the
> independent F.2 JSX projection alongside the unchanged generated V1 region.
> The real Rhino layer includes attach, AndroidView and its synchronous native
> factory input. The compiler-owned XML map adds the empty `compose` container,
> the supported textAllCaps aliases and the actual EventEmitter receiver for
> native onClick callbacks. No catch-all intrinsic or attribute index is added.
> The public 2.1.3 tarball lock, Node layer, compiler options and wire profiles
> remain unchanged. The supplement identifies its separate local source version
> and exact commit; it does not claim that all bundled Rhino APIs came from the
> newer declaration package.
>
> The current Rhino layer is 53 files / 958981 bytes; common + Rhino is 161 files /
> 4743739 bytes. The distribution still owns 118 files. Real Worker tests compile
> and execute the interop lowering shape using these actual declarations, and
> reject missing views, Promise/async factories, null containers and plain text
> children. A single JSX expression still has the historical global any type;
> actual XML element children and mixed-tree ownership remain runtime checks.
> Counts and generator identities in the dated sections below are historical.
>
> Final local source is declarations 4.33.0 commit
> `26dcf6cd6d4d175c179b49474d9b9ffc28939690`. The full real Worker suite, C8
> Program/Worker isolation, current input quota, 534 selected app JVM cases
> (529 plus five explicitly selected capability-fixture cases) and four
> build-logic cases pass. Debug/androidTest, lint and five signed release APKs
> pass. Debug lint has zero errors and seven existing warnings. The final
> x86_64 release `...-v0.6.5-x86_64-6954E0DF.apk` is 3372133 bytes, SHA-256
> `450568244d9eefab91093e23b0f17e2f8e7b025d12951c3dfbd5e929abad6503`.
> With host 5320 and the signed F2 renderer, an owned API 35 x86_64 AVD passed
> all seven real TSX scenarios, including the XML/AndroidView bridge. Ignored
> logs are `app/build/compose-f2-final-*`; this is local evidence, not a remote
> release or a new full physical-device matrix.

> **Compose TSX local integration (2026-10-04, 0.6.4).** The current generator is
> revision 5. The public Rhino npm tarball remains exactly `@sm003/autojs6-dts@2.1.3`,
> with its existing URL, digests and conversion lock. Two separately attributed,
> plugin-bundled declaration modules supplement that projection: the complete
> Compose API and its 29 generated JSX intrinsic names. They also declare the
> `compose`, `$compose` and `ComposeError` globals. The source snapshot, version,
> commit and normalized source hashes are in
> `build-logic/convention/src/main/resources/r2-compose/source.json`; this is local
> integration content, not a claim that declaration package 4.31.0 is on npm.
> `tools/sync-compose-declarations.py --declarations <checkout> --check` verifies
> the explicit maintenance source. Normal Gradle builds consume only repository
> resources, with LF enforced for reproducible hashes on Windows and Unix.
>
> The Rhino layer now has 53 files / 956,181 bytes; common + Rhino has 161 files /
> 4,740,939 bytes. The complete distribution owns 118 files. The Node layer and
> compiler profile/wire options are unchanged. Supplement output joins the normal
> layer/profile fingerprint and exact packaged-file inventory, while its source
> notice is also bound by the distribution fingerprint. The source kind explicitly
> identifies an npm projection with a plugin supplement. Generator and runtime
> loader require the new identity and inventory together.
>
> TypeScript has one global JSX expression type. The former XML-only `JSX.Element`
> brand would reject Compose factory return values and mislabel namespaced Compose
> expressions. The new projection matches the editor package by leaving that
> expression type unspecified. XML and Compose attributes, callbacks and ordinary
> API calls keep their precise declarations; mixed trees and fragment cardinality
> are enforced by the matching host. Namespace-tag attribute completion in the
> bundled TypeScript 6.0.3 remains limited; member tags are the recommended editor
> form. The historical counts and generator identities below describe their dated
> implementations and are not the current distribution.

> **S3-A1/A2 现行 authority（2026-08-23）。** 提交 `12db299` 与 `6b85685`
> 已取代本页下方记录的 C8/v1 初始实现：当前资产根为
> `assets/r2-declarations/v2/`, manifest schema 2 / generator 3；release package 为
> `autojs6-plugin-typescript-engine-declarations`, release version 必须等于
> `BuildConfig.VERSION_NAME`。Rhino 层
> 由公开 `@sm003/autojs6-dts@2.1.3` 的 50 个 internal declaration 模块与独立
> `index.d.ts` 组成（51 files / 878,019 bytes / 42,694 tokens）, layer fingerprint
> 为 `6de45f7a89fcafa99f70f5ddeb3d2a7cebdfb109cd9f8a4d3fc9b9eda29969a6`；Node
> 层仍为 59 files / 246,572 bytes, layer fingerprint 仍为
> `f38e4e384cd67fdad4134aa0400ba164704c987ee9683327fbe3eacdfa134603`。
>
> lock schema 2 继续以完整 tarball digest/SRI 锁定外部 Rhino npm 供应链, 但本仓库
> Node 声明层不再绑定私有 host repository/commit/build 或逐文件 source hash。
> `distribution.json` 中的逐文件 SHA-256 是实际 APK 文件 inventory/tamper authority,
> 不是私有源码版本锁；layer/profile fingerprint 保持纯内容身份, release 身份另行进入
> distribution fingerprint。当前 distribution 有 116 个 manifest-owned files,
> fingerprint 为 `208f46053ad924e79c7952ce518b39eca3e110cb84e9315dbedaf8495fbbf244`。
> **除本段外, 本页其余内容是 2026-08-02 的 C8/v1 历史闭环记录, 不再描述当前资产。**

> **R2-M3-S1 binding（2026-08-14）。** Formal input revision 1 现在绑定所选 C8 exact
> `declarationProfileFingerprint`，并由 plugin-local loader 派生 declaration inventory/count/bytes；
> caller wire 不能提供这些本地 authority。见
> [S1 Input Archive Schema v1](INPUT_ARCHIVE_SCHEMA_V1_R2_M3_S1.md)。本页的 M2 declaration source lock/provenance 继续保留。

日期：2026-08-02

状态：**internal C8 完成；production/public project compilation 仍未激活。**

## 目标与边界

C8 把 compiler-owned declarations 明确拆成 `common`、`rhino` 与 `node` 三层，并冻结
每层来源、转换、资产清单和 fingerprint。Rhino Program 只能装入 `common + rhino`，Node
Program 只能装入 `common + node`；声明入口是 compiler-only root，不得进入 caller
`selectedRootNames`、emit inventory 或 output manifest 的 source roots。

C8 不激活 provisional NodeNext profile，不改变 CommonJS/Node10 module semantics，不开放
`.mts/.cts`，也不提升四项 public capability。P1/P2 仍负责 Node profile 与 module-kind/output
规则，M3 再冻结 public R2 wire/output schema。

## Authority 更正

实施前重新核验了候选资产，而不是沿用相邻项目中的复制品：

- 截至 2026-08-02，npm registry 实际公开的 `@sm003/autojs6-dts` 最新版是 `2.1.3`；
  本机 Ace 镜像中的 `3.0.0`
  不是 npm 发布物，不能被描述为 npm authority；
- Rhino npm 包中的完整声明内容超过现有 8 MiB / Worker token envelope，且大文件在 TypeScript 6.0.3
  下无法通过 `65,536 tokens/file` 与 `262,144 tokens/project`；C8 因而锁定 npm tarball
  后执行 fail-closed conversion-v1，只投影由真实上游声明定义、且能在
  `skipLibCheck=false` 下独立验证的核心全局 surface；
- build 5253 的 Node declaration subtree 来自 AutoJs6 host commit
  `1d74c57aee7d4f6e300acedee395da6110fc8954`。该提交尚未进入公开 AutoJs6 仓库，因此
  C8 将 exact Git-blob snapshot vendoring 到本项目并逐文件锁 hash；clean build 不读取
  本机兄弟仓库，也不伪造公开 raw URL。

Rhino conversion-v1 只从 npm 2.1.3 的 `aj6-int-toast.d.ts`、`aj6-int-init.d.ts` 与
`aj6-int-globals.d.ts` 提取 genuine `Internal.Toast`、`Toast` option types、`toast`、`$toast`、
`HEIGHT`、`WIDTH`、三个 `sleep` overload 和 `toastLog`。转换要求输入 tarball、三个源文件
和每条目标 declaration 精确匹配；缺失、重复或漂移均中止生成，而不是退化为空层或 canary。

## 独立 distribution

C8 不修改已冻结的 TypeScript/R1 distribution。新增资产根为：

```text
assets/r2-declarations/v1/
  distribution.json
  layers/common/binding.json
  layers/rhino/index.d.ts
  layers/node/*.d.ts
  layers/node/package.json
  licenses/rhino/MPL-2.0.txt
  licenses/node/MPL-2.0.txt
  notices/rhino-source.txt
  notices/node-source.txt
```

`common` 继续引用 TypeScript 6.0.3 标准库的既有 authority：108 files、3,784,758 bytes、
fingerprint `6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f`。
`binding.json` 只是跨 distribution 绑定记录，不伪装成 common declaration，也不替换该
fingerprint。

生成后的 C8 authority 为：

| Authority | files | bytes | Worker tokens | fingerprint |
| --- | ---: | ---: | ---: | --- |
| common | 108 | 3,784,758 | 86,329 | `6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f` |
| rhino layer | 1 | 825 | 163 | `5101be87aa34b4fdc8f3b1a62fcac5ac54159bceaa95f2dd3e11a19a07adac22` |
| node layer | 59 | 246,572 | 39,167 | `f38e4e384cd67fdad4134aa0400ba164704c987ee9683327fbe3eacdfa134603` |

Rhino profile fingerprint 为
`eebe5ac0a8ca346a0a2c7951af9feb31cfb3a90073e763d978d4fa4bc1620cd6`，Node profile
fingerprint 为 `8cec5b8e610960a522cab3520784ceccd7bf6f4a008e79503e209b04b65012c4`。
独立 distribution 共 66 个 manifest-owned 文件、282,563 bytes，fingerprint 为
`7de0b902551ba283145119bf8a86fac14e56c0d99d4477e35d93394e7eb0fc60`。

Manifest 使用 exact-key schema。顶层固定包含 `schemaVersion`、`generator`、`sources`、
`layers`、`profiles`、`files` 与 `fingerprint`：

- `files` 是除 manifest 自身外所有 packaged bytes 的 sorted exact inventory；
- engine layer fingerprint 使用 `path NUL size NUL sha256 LF` 绑定真实 `.d.ts` inventory；
- Rhino profile fingerprint 域分隔绑定 profile id、ordered `common/rhino` layer
  name/fingerprint 以及 Rhino entry point；Node 对 `common/node` 做同样计算；
- distribution fingerprint 继续绑定 generator、source descriptors、layer/profile
  fingerprints 与全局 packaged inventory。

下载、生成与验证使用独立 staging/output root。验证任务重新读取 locked source 和输出，拒绝
symlink、absolute/traversal path、非 UTF-8、case-fold collision、missing、extra、size/hash
漂移及 license/source identity 漂移。Debug/Release APK gate 再对签名候选中的完整 asset
prefix 做 exact inventory 对照。

## Runtime 与 Program 隔离

Runtime 先独立验证 TypeScript standard-library manifest，再验证 C8 manifest 与所有实际
asset bytes。`R2TrustedDeclarations.fingerprint` 保留 R1/common identity；新增
profile/layer identities 仅用于 R2 internal declaration authority，避免在 M3 前重解释冻结的
R1 metadata key。

在 C8 关闭时，production registry 只选择 Rhino，因此 snapshot 只 materialize：

```text
/declarations/lib*.d.ts
/declarations/rhino/index.d.ts
```

P1 随后已把 Node layer 接入 internal/debug-only、config-none 的 NodeNext revision 1 snapshot；
public/production-shaped admission 仍保持 Rhino-only，不能由 public caller 选择 Node。
上游 Node snapshot 中 `input_observer.d.ts` 是 `index.d.ts` 不可达的唯一孤儿；它仍被 source
lock、layer fingerprint、runtime asset hash 与 APK exact inventory 覆盖，但不会被追加为第二个
compiler root。真实 Program 必须精确装入其余 58 个 index-reachable Node declarations。
Worker 在 ingress 和 Program VFS 两处执行双向隔离：Rhino project 拒绝任何
`/declarations/node/**`，Node project 拒绝任何 `/declarations/rhino/**`；所选 layer 若缺少
canonical entry，也会在 Program 前失败。

Worker 分开保留两个 root 集合：

- `rootNames`：caller/config 选择的 canonical `/sources/*.ts`，继续返回并绑定 output；
- `compilerRootNames`：`rootNames` 加当前 profile 的 compiler-owned declaration entry，只传给
  `createIncrementalProgram`。

Program 创建后必须精确回报 `compilerRootNames`，但 result 中仍只能出现 caller
`rootNames`。Declaration files 不进入 executable source/emit accounting。

## 预算

C8 不改变任何生产常量：ordinary admission 仍为 512 files、8 MiB/file、8 MiB total；Worker
仍为 65,536 tokens/file、262,144 tokens/project。Common declarations 计入既有 snapshot
预算，selected engine layer 也继续计入，不能通过“trusted”身份绕过 file、byte、path 或 token
fence。

当前 profile 的原始 caller 余量（尚未扣除 caller config/package 等其他输入）为：

| Profile | trusted files / bytes / tokens | caller 剩余 files / bytes / tokens |
| --- | --- | --- |
| production Rhino | 109 / 3,785,583 / 86,492 | 403 / 4,603,025 / 175,652 |
| verification-only Node | 167 / 4,031,330 / 125,496 | 345 / 4,357,278 / 136,648 |

最大 common 文件仍是 `lib.dom.d.ts`（2,349,483 bytes / 34,857 tokens）；最大 Node 文件是
`autojs_global.d.ts`（42,003 bytes / 5,407 tokens）。两者均未改变或逼近既有 per-file fence。

## 验收结果

C8 专属 gate 已同时证明：

- source lock、deterministic generation、tamper/missing/extra/collision 与 Debug/Release APK
  exact inventory；
- Kotlin runtime loader 的 common binding、profile selection、实际 asset hash 和 entry point；
- Rhino genuine `toast/sleep` 正例以及 Node `autojs6:profile` TS2307 反例；
- Node `autojs6:profile`/`toast` module 正例以及 Rhino-only `toast()`/`sleep()` 两个 TS2304
  反例；
- 两个真实 Program 的 source inventory 无反向 layer，compiler-only roots 不污染
  selected roots 或 emit；
- config-none exact shape、R1 metadata/AIDL freeze、public capability=false 与 C1-C7/C6
  aggregate gates 无回归。

2026-08-02 的串行结果：

- build-logic 完整 suite：19 tests PASS，覆盖 deterministic generation、tamper、missing、
  extra、source hash、tar symlink 与 case-fold collision；
- `:app:verifyR2M2C8DeclarationProgramLayering`：TypeScript 6.0.3、2 种 Program API、4/4
  profile isolation PASS；
- `:app:verifyR2M2C8DeclarationWorkerLayering`：compiler-only/user roots 为 1/1、Node 双层
  rejection 为 2、legacy common-only 为 1；
- `:app:verifyR2M2C8DeclarationLayering`：265 tasks，Debug/Release APK 均以 67 entries
  exact inventory PASS；
- `:app:verifyR2ProjectCompilerCore`：495 tasks，完整 R2 compiler core PASS。

统一执行形式为：

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2M2C8DeclarationLayering
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" `
  :app:verifyR2ProjectCompilerCore
```

## 后续

C8 关闭后，`R2-M2-P1` 已把锁定的 Node declaration layer 接入 internal/debug-only、
config-none 的 provisional NodeNext revision 1，并通过 production Worker framed gate；它仍只
接受 `.ts -> .js`，且不表示 Node compiler profile 已对 public caller 激活。下一项
`R2-M2-P2` 负责冻结 `.ts/.mts/.cts` module-kind、resolver v2 与 output/generated URI rules。
