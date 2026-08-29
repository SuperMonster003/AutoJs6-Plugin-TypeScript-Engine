# AutoJs6 TypeScript Engine Plugin — Roadmap

> 本路线于 2026-08-25 重写 (T 时代), 替换 2026-08-22 启动、2026-08-25 收束的
> S 时代路线。S 时代全文及全部设备结论归档于
> [docs/roadmap/ROADMAP_LEGACY_S_ERA.md](docs/roadmap/ROADMAP_LEGACY_S_ERA.md);
> 更早的 R0–R8 证据驱动路线见
> [docs/roadmap/ROADMAP_LEGACY_R2_ERA.md](docs/roadmap/ROADMAP_LEGACY_R2_ERA.md)。
>
> 重写原则 (2026-08-25 项目 owner 明确要求):
>
> 1. **目标升级**: S 时代目标是"让用户的 TS 脚本跑起来", 已达成。T 时代目标是
>    **完整 TypeScript 开发体验** —— npm 生态、路径别名、增量编译、项目级编辑器
>    智能、运行时动态编译。差距在功能, 不在稳定性。
> 2. **不设用户反馈门**: 项目尚未发布, 不存在任何用户反馈。S 时代大量条目以
>    "真实用户撞墙后触发"、"收集反馈后评估"、"观察期满后删除"为前提, 这些门在
>    零用户状态下永远不会开启, 等于无限期搁置。T 时代一律改为**主动实现 + 自测
>    验收**, 交付节奏由本仓库自己驱动。
> 3. **安全由 Android 与既有架构兜底**: `:typescript_compiler` 独立进程、Worker
>    无网络、宿主权限模型、进程沙箱已经是足够的安全基线。不再为单个功能追加
>    安全评审文档、可否决评估、容量证明或额外观察期作为验收物。fail-closed 只
>    保留在**破坏正确性**的位置 (源码快照一致性、Source Map 真实性、契约版本
>    校验), 不再用于拦截功能。
> 4. **完成标准不变**: 仍以"用户可感知的行为"为完成定义, 不以 verify 任务变绿
>    为交付物。
>
> 修 bug 原则延续: 任何阶段跑出来的异常, 直接修复并记 CHANGELOG, 不回头补
> 边界矩阵。验证基线延续 S1-6: 本地一律 `gradlew check --offline`, 仅供应链
> lock 与升级预检允许联网; 新功能验收 = 聚焦单测 + 真机/模拟器冒烟, 不新建
> 证据矩阵, 防漂移 verifier 仅在文档/契约确需锁定时增加。

## 现状基线 (S 时代终点, 2026-08-25)

T 时代直接继承的已验证资产:

- **编译内核**: WebView + Web Worker 中的官方 TypeScript 6.0.3 完整 `Program`
  编译, JS + Source Map v3 + 结构化 diagnostics, `noEmitOnError` 生效;
  Rhino profile (revision 1/2, 含原生 TSX classic lowering) 与 NodeNext
  revision 2 均已四设备验证。
- **契约**: AIDL v1 冻结 (additive Bundle key 扩展)、input/output archive
  schema v1、diagnostics schema v1、双向兼容 AAR 门禁。
- **宿主全链集成** (AutoJs6 主仓库): compiler client、`.ts/.tsx/.mts/.cts`
  路由、执行前编译、多文件项目快照、编译缓存 (语义键 + 持久化, 命中 18–63 ms)、
  APK 打包 (build-time 编译, 产物无 TS 源码)、后台/定时/Tasker 入口统一管线。
- **诊断与栈映射**: 编译错误与 Rhino/Node 运行异常均可点击跳回原 `.ts`
  行 (Node 精确到列); 多文件 Source Map 选图规则冻结于
  [docs/user/SOURCE_MAPS.md](docs/user/SOURCE_MAPS.md)。
- **声明层**: TypeScript 标准库 + Rhino profile 51 个模块化 AutoJs6 d.ts +
  Node profile 59 个 bridge d.ts + 原生 TSX JSX 类型投影, 随插件版本发布。
- **受控配置**: tsconfig 18 个布尔项 (strict 家族、`noUnused*`、`skipLibCheck`)
  激活, 未知键 warning-ignore, 结构性选项 fail closed。
- **Node profile**: `.mts/.cts` 与 project/package boundary 驱动的 Node `.ts`,
  ESM/CJS 按 `type` 产出, dynamic specifier 快照映射, CommonJS 循环依赖可用
 ；ESM 入口与 dynamic `import()` 已由 V8 native linker 提供循环 live binding。
- **编辑器一致性**: Ace 插件锁定同版 TypeScript 6.0.3 与同 profile 选项,
  当前为有界单文档提示层。
- **发布链**: v0.6.0 正式签名 APK 本地就绪 (S6-5, API 24/31/35/37 门禁通过),
  对外分发按 owner 决定等待宿主项目先发布。
- **维护体系**: 升级演练 (UPGRADE.md)、依赖巡检、内存观察、issue 分诊模板
  均已例行化并纳入默认离线 `check`。

### S 时代完成档案

S1 插件解禁、S2 宿主最小集成、S3 诊断/声明/栈映射、S4 Node profile 与项目
能力、S5 缓存/打包/编辑器、S6 发布收尾 (本地部分)、S7 文档与模板 (本地部分)、
S10 性能与维护 —— 逐项状态、设备矩阵与提交号见
[S 时代归档](docs/roadmap/ROADMAP_LEGACY_S_ERA.md)。其中 S10 五项作为例行
维护基线继续有效:

- [x] **S10-1 冷启动继续压缩**: production `projectInputBatch` 4→6 middle
      frames, 标准库 workload 379 logical frames 从 97 降至 68 envelopes,
      四设备 `compiler/ingress` median 改善 1.7%–13.9%; 声明按需注入因
      triple-slash lib reference 正确性风险否决, 跨 job SourceFile 复用当时
      因 realm teardown 契约延期 (T3 重开)。方法与原始样本见
      [docs/maintenance/COLD_START.md](docs/maintenance/COLD_START.md)。
- [x] **S10-2 内存观察持续化**: `tools/observe-release-memory.ps1` 固定
      workload 观察 + 受版本控制历史, 发布 checklist 已含该项;
      v0.6.0 已有结构化记录。
- [x] **S10-3 TypeScript 版本升级演练**: 6.0.2→6.0.3 隔离重放走通完整
      distribution lock 流程, `docs/maintenance/UPGRADE.md` 可照做; 6.0.3
      为最新稳定 6.x, TypeScript 7 原生架构评估在 T3-4 立项。
- [x] **S10-4 依赖健康巡检**: 季度节奏 + WebView 支持策略 (API 24 独立
      最低线, provider major 119+) 文档化并入默认门禁。
- [x] **S10-5 issue 分诊模板**: 结构化 intake、隐私安全证据采集器与分诊
      闭环本地部署, 已走通真实 issue; 远端启用随 T6-4。

### S 时代未完成项去向

| 旧编号 | 内容 | 新去向 |
|---|---|---|
| S4-2 尾项 / S6-2 | 删除 legacy 正则 stripping (原设发布观察期) | T6-1 (立即执行) |
| S4-5 / S8-1 | 输入配额上调 (原设"撞墙触发") | T1-1 (主动上调) |
| S6-6 | 插件市场/索引登记 | T6-4 (外部依赖保留) |
| S7-4 终验 | 模板宿主真机零修改运行 | T6-3 (随宿主候选) |
| S7-6 | 十语言 strings 核对 | T6-2 |
| S8-2 | 受控 tsconfig 扩展 (原设反馈触发) | T1-2 (主动扩容) |
| S8-3 | 嵌套 package boundary / monorepo | T1-6 |
| S8-4 | Node ESM live-binding 循环依赖 | T1-7 |
| S8-5 | 编辑器项目级诊断 | T4 (整阶段) |
| S8-6 | 运行中动态编译 (原 Post-GA 可否决) | T5-1 (主动实现) |
| S9-1/2/3 | npm 受控生态 (原 Post-GA 可否决探索) | T2 (升级为主线阶段) |

## 阶段总览

| 阶段 | 主题 | 用户可感知结果 | 状态 |
|---|---|---|---|
| T1 | 语言与配置齐平桌面 TS | 大项目、路径别名、装饰器、顶层 await、更多 tsconfig 键直接可用 | 已完成（T1-1–T1-7） |
| T2 | npm 生态 | `import dayjs from "dayjs"` 在双 profile 编译执行, 类型与补全同步 | 已完成（T2-1–T2-6） |
| T3 | 编辑-运行循环提速 | 改一行重跑接近秒级; 冷启动等待继续下降 | 已完成（T3-1–T3-4） |
| T4 | 编辑器完整体验 | 跨文件诊断、补全、跳转、重命名、快速修复 | 已完成（T4-1–T4-5） |
| T5 | 运行时动态 TS | 运行中出现的 TS 文件可编译执行; TS 字符串即时执行 | 已完成（T5-1–T5-2） |
| T6 | 遗留收尾与发布 | 旧 stripping 代码删除; 十语言提示; 对外发布 | 进行中（T6-1 已完成；T6-2 待执行；T6-3/4 外部依赖） |

依赖关系: T1 优先启动 —— T1-1 配额与 T1-3 别名解析是 T2 的基础设施;
T2、T3、T4 相互独立可并行; T5-1 依赖 T3-1 的常驻编译会话; T6-1 已完成，
T6-2 不依赖其他阶段、可立即执行; T6-3/T6-4 等待宿主项目发布节奏。
各阶段内部条目无强顺序, 按实现便利插队即可。

---

## T1 语言与配置齐平桌面 TS

目标: 用户在桌面 TypeScript 项目里的日常写法, 搬到设备上不需要改。
主要工作在本仓库 (Worker/resolver/policy), 宿主与 Ace 同步消费。

- [x] **T1-1 输入配额直接上调** (原 S4-5/S8-1): caller 输入从
      512 files / 8 MiB 上调至传输层保险丝 4096 files / 64 MiB, 不再等待
      "真实项目撞墙"。同步更新 CONFIGURATION.md 配额表与 S7-2 时代的文案。
      验收: 一个 1000+ files 或 30+ MiB 源码的项目编译成功; 四设备之一
      实测一次大项目端到端。
      结论 (2026-08-25): 发布 `inputQuotaRevision=3`（4096 files / 单文件
      8 MiB / 合计 64 MiB），插件 declarations 排除在 caller 计数外；旧宿主
      revision 2 的 512 files / 8 MiB 契约继续受理。桌面 Worker golden 已干净
      编译 1025 caller files、32+ MiB 输入并产出 JS + map；API 35 Xiaomi
      23046RP50C / WebView 130 又通过公开 Binder 的 1025-file 端到端用例
      （327223 input bytes，20183 ms）。归档层单独允许第 4097 个 manifest
      entry、512 KiB expanded 与
      1 MiB container overhead，因而 manifest 不会暗中吃掉 caller 的第 4096 个
      文件或 64 MiB 语义预算。
- [x] **T1-2 受控 tsconfig 大扩容** (原 S8-2): revision 3 policy 从 18 个
      布尔项扩展为 20 个布尔项及 `target`/`lib` 两个值类型选项；Rhino 固定
      ES2018，Node 可选 ES2018–ES2022。`removeComments`、
      `preserveConstEnums`、`exactOptionalPropertyTypes`、
      `noPropertyAccessFromIndexSignature`、`noImplicitOverride` 等均进入真实
      effective options。未知键保持 warning-ignore；`paths`、`outFile` 和
      自定义 transformer 等结构性选项继续拒绝。append-only，旧 revision 2
      Rhino 请求保持原始 policy/effective fingerprint。
      验收: revision 3 fingerprint 发布；带 `"target":"ES2022"` 的 Node
      项目与带值类型检查项的 Rhino 项目各编译执行一例。
      结论 (2026-08-26): 发布 `configPolicyRevision=3`，policy fingerprint 为
      `1f77d37080466f2752176fb5aa1b96e9a3f3dbdc89b853f30b4a0fed4313e90a`；
      当前 Rhino 与 NodeNext revision 2 都可携带受控配置，Node transport
      config-first start 冻结为 34 fields，Rhino 保持 27 fields。真实 Worker
      Program 门禁分别选择 `lib.es2018.d.ts` 与 `lib.es2022.d.ts`，编译 Source
      Map 后执行生成的 CommonJS，得到 `14` 与 `42`；同时证明注释移除/保留、
      `const enum` 保留、ES2022 class fields 及 warning/rejection 行为。旧
      revision 2 policy fingerprint
      `64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b`
      和默认 effective fingerprint
      `5b529b507e94fafe30ccce6de4d69f89604bc0f1430b7b4ee8f12259fe03a807`
      均由兼容用例逐字锁定。API 35 Xiaomi 23046RP50C / WebView
      130.0.6723.86 又通过公开 Binder 的 Node ES2022 rev3 与 Rhino
      warning-ignore 用例；首次真机运行发现并关闭 output archive 仍把受控配置
      限定为 Rhino 的残留条件，修复后两例均为 `INSTRUMENTATION_CODE: -1`。
- [x] **T1-3 `paths`/`baseUrl` 路径别名**: 解除对 `paths` 的结构性拒绝,
      在受控 resolver 内实现编译期别名解析 (`@/utils` 风格 → 项目内相对
      路径), 禁止逃逸项目根。双 profile 生效, Ace 编辑器 (T4) 与运行时
      模块映射使用同一张别名表, Source Map 与栈映射不受影响。
      验收: 一个使用 `@/` 别名的 10+ 文件项目在 Rhino 与 Node 各编译
      执行一例, 别名 import 的编译错误与运行异常仍映射回原 TS。
      结论 (2026-08-26): append-only 发布 `configPolicyRevision=4`，policy
      fingerprint 为
      `4a9a5e70150f81e1ec9a5e2f749cc081f93f207f092300c96a79abfdcc152dab`；
      revision 2/3 身份与 effective fingerprint 保持逐字兼容。`baseUrl`/`paths`
      先规范化为可复用的 project-relative immutable alias table，再只在已接纳
      `/sources` snapshot 中解析；emit 前由插件内部固定 transformer 把别名改写为
      带实际 `.js/.mjs/.cjs` 后缀的 output-relative specifier，caller transformer
      仍不可注入。桌面门禁在 Rhino/Node 各编译并执行同一 12 文件 `@/` 项目，均得
      `55`，同时验证 TS2307 保留原 TS 位置、运行异常映射回
      `src/features/fault.ts:2`，alias effective fingerprints 分别为
      `b2da71259b531113171de449e3226a8b5d7870620063feb4ac81459dedc4146b`
      与
      `341d3f22c61fd7488bae6de0ed6003376e62cdfdf394ac153677d7dc3b67c546`。
      API 35 Xiaomi 23046RP50C / WebView 130.0.6723.86 又通过两个 public
      profile 的 Binder 12-file compile/archive 验收；同一 alias table 已独立于
      Android 路径，可由 T4 编辑器服务直接复用。
- [x] **T1-4 装饰器**: 支持 TS 标准装饰器 (necessary 时评估
      `experimentalDecorators` legacy 模式二选一或并存; `emitDecoratorMetadata`
      依赖 reflect 运行时, 单独评估)。
      验收: 一个使用类/方法装饰器的脚本在双 profile 编译执行通过。
      结论 (2026-08-26): 现有 TypeScript 6.0.3 canonical options 在省略
      `experimentalDecorators` 时已经采用标准 Stage-3 语义，官方 108-file 标准库
      闭包也已通过 `lib.es5.d.ts` 引用 `lib.decorators.d.ts`，因此本项不制造无语义
      变化的 policy revision 5；revision 4 fingerprint 保持不变。新增
      `verifyT1StandardDecorators` 用真实 common/profile declarations 与当前 revision 4
      effective options 编译 typed 类/方法装饰器，在 Rhino ES2018 与 Node ES2022
      生成物中只观察到 `__esDecorate`/`__runInitializers`，实际执行均得到 `42`，并
      锁定 `method:apply|class:apply|class:init|method:init|method:call`。尝试加入 legacy
      与 metadata 两个键只产生两条 `910002` warning，canonical options/fingerprint
      不变；这两项待 T2 具备完整 reflect 依赖运行时后再以 append-only revision 独立
      开放。API 35 Xiaomi 23046RP50C / WebView 130.0.6723.86 的公开 Binder 又对
      Rhino `.ts` 与 Node `.cts` 完成 standard helper、Source Map、archive 与
      effective fingerprint 验收，结果 `OK (1 test)`。设计与证据见
      [T1-4 标准装饰器](docs/architecture/STANDARD_DECORATORS_T1_4.md)。
- [x] **T1-5 Node profile 顶层 await**: Node profile 使用 NodeNext 模块语义与
      controlled ES2022 target/lib，顶层 await 在 `.mts` 与 `type: module` 项目可用。
      验收: 一个顶层 `await sleep()` 脚本在 Node profile 真机执行通过。
      结论 (2026-08-26): 保留 NodeNext revision 2 与 config policy revision 4；module
      继续由 NodeNext 管理 `.mts/.cts` 和 package boundary，显式 ES2022 target/lib 已由
      revision 4 提供。默认 ES2018 也满足 TypeScript 顶层 await 的 target 下限，因此不
      制造无新增功能的 profile revision。新增 `verifyT1NodeTopLevelAwait` 直接加载
      production Worker、TypeScript 6.0.3 与正式声明层，在同一 Program 中把 `.mts`
      编译为 ESM `.mjs`、把根 `package.json` 为 `type: module` 的 `.ts` 编译为 ESM
      `.js`，两份输出与 Source Map 配对并由桌面 Node dynamic import 实际执行，均在
      `await sleep(4)` 后返回 `42`；NodeNext/ES2022 effective fingerprint 为
      `b77bfac45d80ad99a9a65168ecd97a10607f0574189e521e3355eb080b4a97ff`。API 35
      Xiaomi 23046RP50C / WebView 130.0.6723.86 的公开 Binder 用例结果为
      `Time: 11.53`、`OK (1 test)`；同机 AutoJs6 `6.8.0 (5276)` + Node Runtime
      `1.2.0` 又让两个独立项目真实执行 `await sleep(25)` 并输出 `42`，结果为
      `Time: 17.071`、`OK (1 test)`。设计与证据见
      [T1-5 Node 顶层 await](docs/architecture/NODE_TOP_LEVEL_AWAIT_T1_5.md)。
- [x] **T1-6 嵌套 package boundary / monorepo 初步** (原 S8-3): 解除
      "项目内第二个 package.json 即拒绝", 支持 workspace 风格子包各自的
      `type` 声明; 保持无 scripts 执行。
      验收: 一个含 2 子包 (一 ESM 一 CJS) 的项目在 Node 跑通。
      结论 (2026-08-27): 不增加 AIDL/archive/profile/policy revision，直接激活既有 package
      metadata schema/policy v1 的多 root、多文件 inventory；AutoJs6 宿主现收集根
      `package.json` 及 `packages/<name>` / `packages/@scope/<name>` 下的受控 metadata，
      将完整 inventory 纳入 manifest、hash、cache key、output authority 与 runtime project。
      Native 与 production Worker 新增 `/sources` 到项目 package 命名空间的统一投影，并按
      longest containing boundary 为普通 `.ts` 选择 `module`/`commonjs`；此前子包永远回落
      到根 boundary 的隐藏路径错位同时关闭。`verifyT1NestedPackageBoundaries` 让真实
      TypeScript 6.0.3 incremental Program 生成并实际执行 workspace ESM/CommonJS 两份
      `.js + .map`，均返回 `42`，生成物 SHA-256 分别为
      `c311b411058914e446b3af99db59945a671a3fcd879cb4760893a465377068ef` 与
      `77cffdbf86a640479036b33703f4558434e9ef72bcfbc1aab514a9b4d5014e73`。API 35
      Xiaomi 23046RP50C / WebView 130.0.6723.86 的公开 Binder 双子包用例 1/1 通过
      (`BUILD SUCCESSFUL in 20s`)；同机 AutoJs6 `6.8.0 (5276)` + Node Runtime `1.2.0`
      又在单个 `.mts` 项目中静态导入一 ESM、一 CommonJS 子包并输出 `42`，同为 1/1
      通过 (`BUILD SUCCESSFUL in 22s`)。根和所有子包的顶层 `scripts` 继续统一拒绝；
      caller `node_modules`/bare import/`exports` 留给 T2。设计与证据见
      [T1-6 嵌套 package boundary](docs/architecture/NESTED_PACKAGE_BOUNDARIES_T1_6.md)。
- [x] **T1-7 Node ESM live-binding 循环依赖** (原 S8-4): 跟进 Node Runtime
      完整 ESM linker 专项, 解除 partial ESM adapter 无 live binding 的
      已知边界。本插件侧无工作, 只做联调验收。
      验收: ESM 循环依赖项目得到正确结果而非 NaN/TDZ 假值。
      结论 (2026-08-27): 配套 Node Runtime 已让 ESM entry 与 dynamic `import()` 使用
      `vm.SourceTextModule` / `vm.SyntheticModule` linker；既有 scoped resolver、module
      source overlay、package resolution 与 import attributes 继续作为解析权威，V8 则
      负责 instantiate/evaluate、TDZ、top-level await、export cell、re-export 与循环图。
      TypeScript 插件无 production protocol 变更，但新增四模块 `.mts` production Worker
      门禁、公开 Binder output authority 验收和宿主端到端用例。桌面 Node 实跑输出
      `t1-7.initial=1`、`t1-7.updated=42`，状态指纹为
      `d78ad1b93ed0c52b6f13b1464edf0c365c85b0db249f839f1bf9f05af7aae95f`；API 35
      Xiaomi 23046RP50C / WebView 130 的插件 Binder 与 Node Runtime linker 用例均 1/1
      通过。Runtime 结果新增 additive 诊断 `esm_linker=vm_source_text_module` 与
      `esm_live_bindings=true`；CommonJS `require(esm)` 的同步 adapter 继续作为明确互操作
      边界。设计与证据见
      [T1-7 Node ESM live binding](docs/architecture/NODE_ESM_LIVE_BINDINGS_T1_7.md)。

## T2 npm 生态

目标: 补上与"完整 TypeScript 开发体验"差距最大的一块 —— 纯 JS npm 包
可导入、可类型检查、可补全。设备端保持零网络: 用户在 PC 上 `npm install`,
项目连同 `node_modules` 进入设备; 插件把依赖当只读受信层消费, 不执行任何
install/lifecycle script (本来也不存在执行路径)。原 S9 的"可否决评审"取消,
直接进入实现。

- [x] **T2-1 依赖层引入与打包策略**: `node_modules/` 从拒绝规则改为独立
      只读依赖层进入 input archive, 不计 caller 配额; 依赖层 fingerprint
      以 lockfile (package-lock/pnpm-lock) hash 或内容 hash 计算, 进入
      编译缓存键。打包策略以实测选型: (a) 全量打包 + 排除规则
      (docs/tests/bin 等) + 独立上限; (b) 宿主侧静态 import 扫描做可达性
      裁剪; (c) 依赖层独立归档、独立缓存、只在 lockfile 变化时重传。
      验收: 一个含 3+ 直接依赖的真实项目完成端到端编译, 依赖层上限数字
      写入 CONFIGURATION.md。
      **已完成（2026-08-27）**：选定 (a)，依赖层 schema/policy revision `1`
      使用 `dependencies/node_modules/` 与 `dependencies/lockfiles/` 独立 namespace，
      policy fingerprint 为
      `7564a07d9f9e980dab795312b1dfbfe719775fb3392536149efd558dc70fa68b`。
      宿主完整打包 `.cjs/.cts/.js/.json/.jsx/.mjs/.mts/.ts/.tsx`，排除 docs/tests/
      bench/coverage/`.bin`、Markdown 与 Source Map；嵌套依赖保留，符号链接遍历拒绝，
      dependency `package.json.scripts` 仅作为 inert JSON 字节。8192 files / 8 MiB
      single / 64 MiB aggregate / 8 MiB lockfile / 1.5 MiB paths 的独立配额以及
      12289 entries / 140 MiB container / 132 MiB expanded 的加法 transport envelope
      已写入公开契约与用户文档，ordinary caller 仍为 4096 files / 64 MiB。
      `package-lock` 优先、`pnpm-lock` 次之、无锁时 exact content inventory；无论是否
      有 lockfile，实际路径/size/SHA-256 inventory 都进入 cache-key format revision `3`。
      插件将依赖写入私有 immutable file-backed spool，并提供独立只读 cursor；本项
      验收时尚未把它 framed 到 Worker，bare import 由下一项独立激活。
      插件聚焦测试与宿主 `TypeScriptProjectArchiveTest`/
      `TypeScriptCompilationCacheKeyTest` 全绿；API 35 小米 23046RP50C 上，AutoJs6
      真机用例 `dependencyLayerWithThreeDirectPackagesCompilesThroughRealPluginBinder`
      以 dayjs/lodash/zod 三直接依赖完成宿主归档 → Binder → 插件验证/私有 spool →
      TypeScript Program；随后仅改动 `node_modules/dayjs/index.js` 再编译，生成 JS 不变但
      两轮都明确为 `CACHE_MISS` 且均有真实 compiler elapsed，证明依赖内容参与缓存身份。
      结果 `Time: 18.052`、`OK (1 test)`、`INSTRUMENTATION_CODE: -1`。设计与证据见
      [T2-1 独立只读依赖层](docs/architecture/DEPENDENCY_LAYER_T2_1.md)。
- [x] **T2-2 bare specifier 解析**: resolver 对依赖层放行裸包名 —— scoped
      包、子路径导出、`exports` 条件解析 (`require`/`import`/`types` 按
      profile 与 module kind 选择)、`main`/`module` 回退。未命中给稳定
      错误码, 不静默回退。
      验收: `import dayjs from "dayjs"` 与 `import { z } from "zod"`
      (子路径/条件导出各覆盖一例) 编译通过。
      **已完成（2026-08-27）**：dependency resolver policy revision `1`、fingerprint
      `e4bb5345bcf6c38f37e7feef94d5eed42f2ce1b5e88a0e3ff5dcf315a3c3872d` 已进入公开
      metadata 与 framed start authority。T2-1 exact inventory 在普通文件之后投影到
      `/node_modules/`，独立核对 file/byte/path、lock identity 与 layer fingerprint，
      不改变 caller inventory/token 配额或无依赖层的旧 frame shape。生产 Worker 以真实
      TypeScript 6.0.3 NodeNext resolver 选择 `types`/`import`/`require` 条件，支持 scoped
      package、`zod/mini` 子路径与 `types`/`typings`/`module`/`main`/`index` fallback；包内
      miss 映射为既有稳定 `NODE_RESOLUTION_*` 诊断。桌面门禁以 dayjs + zod 完整 Program
      semantic/`.mjs` emit 覆盖根包与 `zod/mini`，并另覆盖 scoped import/require 双条件、
      module/main 优先级与 `NODE_RESOLUTION_EXPORTS_NO_MATCH`；Kotlin 测试覆盖可逆 VFS 投影
      和依赖帧严格后置。API 35 小米 23046RP50C 上，宿主真机用例
      `bareDayjsAndZodExportsCompileThroughRealPluginBinder` 又从 archive 经 Binder、只读 Worker
      VFS 完成同一完整 Program 编译，结果 `Time: 10.457`、`OK (1 test)`、
      `INSTRUMENTATION_CODE: -1`。运行期装载明确未计入本项，仍由 T2-3 完成。设计与证据见
      [T2-2 bare specifier 与依赖解析](docs/architecture/DEPENDENCY_RESOLUTION_T2_2.md)。
- [x] **T2-3 运行时装载**: Rhino 走既有 CommonJS 物化链路 (支持 CJS 入口
      的包; ESM-only 包在 Rhino 给稳定错误, 是否用 allowJs 降级转译由
      实测数据决定); Node 走 module-source overlay, ESM/CJS 双格式与
      dual package 均可执行。
      验收: 同一依赖项目在 Rhino 与 Node 各真机执行一例。
      **已完成（2026-08-28）**：编译输出现在把 T2-1 exact dependency layer 一直保留到
      execution；宿主在 runtime 建立前重新核对每个 `node_modules` 文件的 canonical path、
      size 与 SHA-256，漂移统一返回
      `ERR_AUTOJS6_TYPESCRIPT_RUNTIME_DEPENDENCY_CHANGED`。Node 将已验证字节作为 host-only
      `workspaceModuleSources` 注入既有私有 workspace ZIP/PFD，未增加 Binder 数组；所有
      overlay 路径均为 ephemeral，插件侧修改/删除不会同步回宿主依赖。Rhino 将同一快照
      物化到单次执行临时 `node_modules`，CommonJS `require()` 支持 bare/scoped package、
      分层嵌套依赖、`exports` 的 `require`/`node`/`default`、`main`/`index` 与 JSON；dual
      package 选择 CJS 分支。实测决定不对 ESM-only package 做隐藏 allowJs 转译，而返回
      `ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY` 并提示 Node profile，另有 invalid
      package、unsupported entry 与 path-not-exported 稳定码。current Rhino 同时复用
      T2-1/T2-2 dependency framing 与 Worker resolver，未改 dependency/resolver policy
      identity。宿主/插件聚焦单测全绿；API 35 小米 23046RP50C 真机用例
      `executesSameDualPackageSnapshotThroughRhinoAndNodeProfiles` 让同一 `dual-runtime` 安装在
      Rhino 执行 `exports.require` 得到 `cjs:42`，再在 Node 执行 `exports.import` 得到
      `esm:42`，结果 `Time: 19.304`、`OK (1 test)`、`INSTRUMENTATION_CODE: -1`。设计与
      错误边界见
      [T2-3 依赖运行时装载](docs/architecture/DEPENDENCY_RUNTIME_LOADING_T2_3.md)。
- [x] **T2-4 类型与编辑器同步**: 包内 `types`/`typings`、`@types/*`、
      `typesVersions` 进入 Program; Ace (T4) 消费同一类型层, strict 下
      真实包的类型检查与补全一致。
      验收: `dayjs()` 的返回类型在编译与编辑器中均正确推导, 错误用法
      产生一致诊断。
      **已完成（2026-08-28）**：dependency resolver policy 升至 revision `2`，fingerprint
      `6bf402612d6aec48947d11d1bbb44848cef3cbeb54d11560d230224b233e3dda`；新增
      `package-types`/`typings`、自动 installed `@types` 与 TypeScript 6
      `typesVersions` 三类 type-reference authority。Worker 从 T2-1 frozen inventory
      派生排序去重且不可变的 `types` Program overlay，支持 scoped `@types/scope__name`、
      declaration 内相对导入与嵌套 package；该 overlay 只参与 Program，不改写对外
      provisional/controlled effective-options 契约，依赖 fingerprint 已负责 cache identity。
      Ace 在后台按相同 project boundary、后缀/排除规则、8192 files / 64 MiB 配额及
      inventory/layer fingerprint 冻结 `node_modules`，映射到只读
      `file:///autojs6/editor/node_modules/`，并将同一声明 URI 与 `@types` 名称交给内置
      TypeScript 6.0.3 language service；有项目类型层时不再屏蔽 TS2307/TS2792/TS7016。
      `verifyT2DependencyTypes` 与 Ace runtime verifier 均以 dayjs 的 TS6
      `typesVersions` 分支、嵌套 `internal.d.ts` 和 ambient `@types` 验证：补全只包含
      `format`/`unix`，hover 为 `const value: Dayjs`，Rhino/Node 错误用法均只产生
      TS2322。API 35 小米 23046RP50C 的 Ace Android bridge smoke 结果
      `Time: 0.412`、`OK (1 test)`；宿主 Binder 双 profile 完整 Program/emit/diagnostic
      用例结果 `Time: 35.364`、`OK (1 test)`，并锁定 Program-only 类型注入不得泄漏到
      公开选项元数据。设计与证据见
      [T2-4 依赖类型与编辑器同步](docs/architecture/DEPENDENCY_TYPES_EDITOR_T2_4.md)。
- [x] **T2-5 真实包验收矩阵**: dayjs、lodash、zod、uuid、nanoid (ESM-only,
      Node 侧) 各一个最小脚本按 profile 适用性执行; 每包记录边界与
      workaround, 形成用户文档一页。
      验收: 矩阵全绿或边界有稳定错误码 + 文档条目。
      **已完成（2026-08-28）**：仓库固定 dayjs `1.11.13`、lodash `4.17.21`、
      zod `4.1.5`、uuid `13.0.0`、nanoid `5.1.5` 与
      `@types/lodash@4.17.25` 的原始 npm tarball 和 SHA-512 integrity。离线门禁按
      T2-1 生产过滤规则得到 2749 files / 5,810,879 bytes / 136,057 path bytes，随后
      用生产 Worker + TypeScript 6.0.3 在 Rhino/Node 下完成十次 strict Program 编译。
      dayjs/lodash/zod 的适用 CJS/ESM 入口与 uuid/nanoid 的 Node ESM 入口均实际执行并
      核对结果；两个 ESM-only 包在 Rhino 统一命中
      `ERR_AUTOJS6_TYPESCRIPT_RHINO_ESM_ONLY_DEPENDENCY`。真实 lodash 首轮同时发现裸模块
      在 `main` JavaScript 处过早停止的问题，dependency resolver policy 因此升级到
      revision `3`（fingerprint
      `6241375f2ea49ac2c2d2b3b79b28b7c1ee4adde75c989defc61e252c89ced2bd`）：只有 package
      自身未提供类型且 TypeScript 只解析到 runtime JS 时，才自动回退 installed
      `@types`；移除 `@types/lodash` 后仍精确得到 TS7016。宿主聚焦测试另以这五个真实
      release 的 manifest shape 锁定 Rhino 对 dayjs/lodash/zod 的入口选择及 uuid/nanoid
      稳定边界。API 35 真机又以当前宿主、TypeScript 插件和 Ace 插件 APK 通过真实 Binder
      dayjs/zod 编译（`Time: 11.141`）、Rhino/Node 类型层一致性（`Time: 34.08`）与 Ace
      Android bridge 冻结类型发布（`Time: 0.403`）三条聚焦用例，均为 `OK (1 test)`。
      完整矩阵、安装方式与 workaround 见
      [npm 真实包兼容矩阵](docs/user/REAL_PACKAGE_MATRIX.md)。
- [x] **T2-6 原生模块边界**: `.node`/node-gyp/postinstall 依赖明确拒绝并
      给稳定错误码与文档解释 (设备端无编译工具链, 这是平台边界而非
      保守限制)。
      验收: 一个含原生依赖的项目得到明确错误而非崩溃或静默失败。
      结论 (2026-08-28): dependency-layer policy 升级为 revision `2`
      (fingerprint
      `c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a`),
      resolver policy 升级为 revision `4` (fingerprint
      `d8207539237d2a08a6b97b530c5e6215f4b73311fdd6954ce9f8004717ebd635`)。
      宿主原始 `node_modules` 快照、插件 archive、production Worker 与 Ace 类型层现在按
      `native-binary` → `binding-gyp` → `gypfile` →
      `scripts.preinstall|install|postinstall` 的固定优先级拒绝依赖；公开结果保留
      `ERR_AUTOJS6_TYPESCRIPT_NATIVE_DEPENDENCY_UNSUPPORTED` 或
      `ERR_AUTOJS6_TYPESCRIPT_DEPENDENCY_INSTALL_SCRIPT_UNSUPPORTED`, 普通
      `build`/`test` scripts 继续作为惰性元数据接受。固定 MIT fixture
      `bcrypt@6.0.0` 的原始 npm tarball 含 10 个 `.node` 文件、`binding.gyp` 与
      `scripts.install`, Node 门禁实际启动 TypeScript 6.0.3 production Worker 并验证
      12 个信号及确定性首错。API 35 Xiaomi 23046RP50C 上，当前宿主/TypeScript/Ace
      APK 又分别通过双错误码预检 (`Time: 0.859`)、dayjs/lodash/zod 真实 Binder
      正向编译 (`Time: 17.919`) 与 resolver revision 4 类型 bridge (`Time: 0.391`),
      均为 `OK (1 test)`。设计、用户 workaround 与复现证据见
      [T2-6 原生依赖边界](docs/architecture/NATIVE_DEPENDENCY_BOUNDARY_T2_6.md) 和
      [原生 npm 依赖](docs/user/NATIVE_DEPENDENCIES.md)。

## T3 编辑-运行循环提速

目标: 把"改一行 → 重新运行"的等待从秒级压到接近即时。S10-1 中因契约
搁置的手段按新原则重开。

- [x] **T3-1 常驻编译会话**: 解除"每 job realm teardown"的契约地位
      (进程级隔离保留, 隔离粒度重新设计), 提供 per-project 常驻 Worker
      会话: 标准库与声明层 SourceFile 常驻 (S10-1 延期项自然获得),
      incremental Program / languageService 按文件失效重用。会话语义走
      additive AIDL key; 不同项目、宿主重启、内存压力时会话丢弃并全冷
      回退, 行为与现状一致。
      验收: 10 文件项目修改单文件后重编 (含传输) 在中端机 p50 < 1 s;
      全冷路径回归不变; 跨项目无状态泄漏 (聚焦单测)。
      结论 (2026-08-28): 发布 additive `projectSessionRevision=1`；service 从
      Binder calling UID + project ID 派生不出 realm 的 opaque SHA-256 key，旧
      request 缺省仍逐 job teardown。Worker 会话最多 3 项/估算合计 64 MiB，
      compiler authority 变化、失败、进程重启与内存压力均全冷回退。API 35
      Xiaomi 23046RP50C / WebView 130 两轮 10 文件端到端 warm p50 分别为
      462 ms、328 ms；不同 project key 冷编译不同输出且没有串线，trim 后 exact
      generation 退休。实现、兼容语义与原始数据见
      [T3 常驻项目会话](docs/architecture/RESIDENT_PROJECT_SESSIONS_T3.md)。
- [x] **T3-2 宿主启动期 prewarm**: bind + prewarm (当前 450–1772 ms) 移出
      用户首次运行等待路径 —— 宿主启动或进入编辑器时后台预热; 保活与
      内存价码用 S10-2 观察脚本记录, 数据决定默认策略。
      验收: 首次运行的用户可感知等待显著下降 (同设备前后对比记录)。
      结论 (2026-08-28): AutoJs6 宿主仅在插件广告 exact revision `1` 时 opt in，
      应用主进程启动后 3.6 秒或进入 TypeScript 编辑器时异步 bind + prewarm；真实
      编译与编辑器访问保留至多一个 live binding。无用户信号的启动预热在 60 秒
      空闲窗口后释放，内存压力、应用清理/退出、Binder 死亡和预热失败均安全回到
      完整冷编译。该保守策略参考 API 35 既有 8 文件 active-realm 观察值：compiler
      service / renderer median PSS 为 90,458 / 95,241 KiB，合计 191,542 KiB（不是
      空预热的精确 PSS）。Xiaomi 23046RP50C / API 35 真机宿主验收中，后台预热
      1,626 ms 后首次用户编译 bind/prewarm 仅 45 ms，等待迁移 97.2%；同一 10 文件
      项目改动叶模块后 core compiler 从 539 ms 降至 156 ms（71.1%），输出包含新值。
      宿主实现、兼容与资源生命周期见
      [T3 常驻项目会话](docs/architecture/RESIDENT_PROJECT_SESSIONS_T3.md)。
- [x] **T3-3 模块级增量缓存**: 跨执行缓存从"全项目未变才命中"细化到
      模块级 —— 只重编译受影响文件及其依赖闭包, 未变模块直接复用产物。
      与 T3-1 会话叠加或作为无会话时的降级路径。
      验收: 20 文件项目改动 1 文件, 二次运行编译耗时相对全量下降 >70%
      (同设备记录)。
      结论 (2026-08-28): SourceFile 以接受后的 SHA-256 为 version，复用
      `EmitAndSemanticDiagnosticsBuilderProgram`、affected diagnostics 与未变输出；
      warm 仍返回完整 output inventory。桌面三轮下降 87.1%–92.7%，每轮只 emit
      2/40 outputs；上述 API 35 真机两轮 20 文件 compiler-phase p50 分别从
      535.388→138.255 ms、598.246→151.117 ms，均下降 74%。不同项目和 legacy
      cold fallback 由同一默认门禁覆盖，详情同上文实现记录。
- [x] **T3-4 typescript-go (TypeScript 7) 原生后端评估**: S10-3 已确认
      7.0.2 属于原生编译器架构、当前 fail closed。立项评估: arm64/x86_64
      原生二进制 (或 WASM 构建) 在 Android 的可行性、与冻结契约的兼容
      方式 (作为新 profile revision 或并行后端)、体积与许可; 做一个
      编译耗时原型对比 (预期数量级提升, 且可摆脱 WebView 依赖与
      provider 版本矩阵)。
      验收: 原型数据 + go/no-go 决策记录; go 则另立专项路线。
      结论 (2026-08-28): Android binary 可行——源码 tag `typescript/v7.0.2`
      已交叉编译 arm64（27.94 MiB）与 x86_64（29.12 MiB），arm64 在 API
      28/31/33/35 四设备运行并成功编译同一 60 文件样本；桌面相同输入 p50
      相对 6.0.3 快 6.15×。但官方明确 7.0 不提供 programmatic API，无法等价
      承载当前内存 VFS、resolver/transformer、结构化结果与取消契约，因此当前
      **no-go**，不另立专项；TypeScript 7.1 新 API 稳定后按条件重开。构建摘要、
      体积/许可、四设备数据与重开条件见
      [T3-4 原生后端评估](docs/roadmap/T3_TYPESCRIPT_7_NATIVE_EVALUATION.md)。

## T4 编辑器完整体验

目标: Ace 从"有界单文档提示层"升级为项目级智能 (原 S8-5 整体扩容)。
工作主体在 Ace 插件仓库, 宿主提供 project snapshot 与依赖层。

- [x] **T4-1 项目级诊断**: 消费宿主 project snapshot 的跨文件诊断;
      停止屏蔽 TS2307/TS2792 类模块解析错误; 与执行前编译 diagnostics
      golden 对齐。
      验收: import 路径写错实时标红, 且与运行前编译报错一致。
      完成 (2026-08-28): AutoJs6 editor ABI v2 以可选 provider 发布与执行前
      编译相同的有界源码 inventory（512 文件、单文件 512 KiB、总计 8 MiB），
      Ace 在后台逐文件复验 UTF-8 长度、SHA-256 与完整 inventory fingerprint 后，
      将源码和 T2 依赖类型层原子投影到只读虚拟项目；当前未保存 buffer 始终覆盖
      snapshot 中的主文件。只有完整且经验证的 project snapshot 才解除
      TS2307/TS2792/TS7016 屏蔽，缺失、损坏或超额输入继续安全降级为单文档语义，
      同时宿主仍兼容 editor ABI v1 插件。Rhino/Node 桌面 golden 均为
      `[2307, 2322]`，API 35 真机经真实宿主跨 APK 路径确认错误 import 精确产生
      `[2307]`。契约、兼容矩阵、安全边界与验收证据见
      [T4-1 项目级诊断](docs/architecture/PROJECT_DIAGNOSTICS_T4_1.md)。
- [x] **T4-2 跨文件智能**: 补全、hover、签名帮助、定义跳转覆盖项目内
      文件与 T2 依赖层类型。
      验收: 跨文件符号补全命中; 定义跳转打开目标文件并定位。
      完成 (2026-08-28): Ace 的 TypeScript 6 Language Service 已在 Rhino/Node
      profile 中统一覆盖项目源码与冻结依赖声明的 completion、hover、signature help
      和 definition location，并提供 F12、Ctrl/Command+点击及移动端“转到定义”。
      editor ABI contract 3 只传递绑定项目根、规范相对路径、源码/依赖 inventory
      fingerprint、内容 SHA-256 与零基范围的类型化目标；宿主在后台重新捕获当前
      snapshot，拒绝 symlink、越界、过期 inventory、内容变化及不匹配的未保存
      buffer，再对当前文件精确 jump，或以 `EditActivity.editFileAt` 打开目标文件并
      定位。桌面 verifier 在两个 profile 均命中项目补全/hover/双参数签名、项目
      definition 与 dayjs 声明 definition；Xiaomi 23046RP50C / API 35 最终产物通过
      Ace `6/6` 与真实宿主跨 APK `2/2`，其中 `shared.ts` 精确定位到 `(0, 16)`。
      契约、安全边界、兼容矩阵与完整验收见
      [T4-2 跨文件智能](docs/architecture/CROSS_FILE_INTELLIGENCE_T4_2.md)。
- [x] **T4-3 auto-import 与 quick fix**: 缺失 import 自动补全建议、
      拼写纠正等 TS codefix 子集。
      验收: 引用未导入符号时一键补 import。
      完成 (2026-08-29): Ace 直接读取 TypeScript 6 Language Service 的
      `import`/`spelling` codefix，并只接受无 command、非新文件、仅当前文档的有序
      UTF-16 edit；Ctrl/Command+. 与移动端“快速修复”均可触发，多个候选先展示选择，
      单个候选直接进入授权。editor ABI contract 4 的类型化请求不含 path、URI、command
      或第二文件，只携带 action 类型、诊断码、有界 edits 与修改前后 SHA-256。宿主针对
      当前 Ace buffer 独立校验只读/生命周期、完整 UTF-16、base hash、offset/代理项边界，
      反向重放 edits 并核对 result hash 后才给一次性批准；Ace 在批准前后再次核对 text
      revision，并把结果作为一次可撤销修改写回。桌面 verifier 在 Rhino 生成
      `./shared`、Node 生成 `./shared.mjs`，同时验证拼写修正且原 `[2307, 2322]` golden
      不变。Xiaomi 23046RP50C / API 35 最终产物通过 Ace `6/6`（4.25 s）与真实宿主
      跨 APK `2/2`（4.442 s）；后者从 `[2304, 2307]` 一键导入
      `projectMagicAnswer` 后严格只剩 `[2307]`。完整契约、安全预算、兼容性与证据见
      [T4-3 auto-import 与 quick fix](docs/architecture/AUTO_IMPORT_QUICK_FIX_T4_3.md)。
- [x] **T4-4 跨文件重命名**: 项目范围 rename 符号, 与编译语义一致。
      验收: 重命名一个跨 3 文件引用的函数, 全部引用点更新且编译通过。
      完成 (2026-08-29): Ace 直接使用 TypeScript 6.0.3 Language Service 的
      `getRenameInfo` 与 `findRenameLocations`, 通过 F2 或移动端“重命名项目符号”生成与当前
      project snapshot 精确绑定的候选。editor ABI contract 5 只携带项目 inventory 身份、
      规范相对路径、每文件 base/result SHA-256 与有界 UTF-16 edits; 插件不能表达绝对目标、
      dependency、command 或文件创建/删除, 也不写磁盘。宿主独立重捕获并重放全部 edit,
      展示文件/引用预览, 冻结输入, 按稳定顺序取得多文件写锁, 在目标同目录写入并同步 backup
      与 staged 内容, 逐文件发布; 任一步失败按逆序验证回滚, 活动 Ace buffer 仅由宿主作为一次
      undoable replacement 更新。Xiaomi 23046RP50C / API 35 的真实跨 APK 用例在 4.618 s
      内把 `answer` 的 3 文件 5 处引用全部改为 `projectAnswer`, 无临时文件残留, 新 snapshot
      diagnostics 为 0。生产 TypeScript 6.0.3 又以 Rhino/CommonJS 与 Node/NodeNext 独立直编译
      该三文件结果, 两者均 0 error 且各 emit 3 JS + 3 source maps。契约、回滚边界与完整证据见
      [T4-4 跨文件重命名](docs/architecture/PROJECT_RENAME_T4_4.md)。
- [x] **T4-5 保存即诊断**: 增量语言服务常驻编辑会话 (复用 T3-1 思路或
      Ace 内建 LS), 保存/停顿即刷新诊断; 大项目设防抖与预算, 不阻塞
      输入。
      验收: 中端机 20 文件项目保存后诊断刷新 < 1 s。
      完成 (2026-08-29): Ace 内建 TypeScript 6.0.3 Language Service 现常驻编辑
      会话。普通输入采用 450 ms generation 防抖, 过期 timer 不能发布; 宿主仅在
      精确 revision 保存成功后触发同文档项目重捕获, 以 75 ms 合并重复请求并在后台
      executor 读取完整 contract 2 快照。源码 URI 拓扑不变时, Ace 事务式读取全部
      兄弟文件, 只更新变化文本及 script version, 保持活动 buffer 权威和同一 service
      instance; 拓扑、profile、依赖或任一来源变化不可证明时安全重建。保存 snapshot
      应用后使用 50 ms 调度, 同时保留 512 文件 / 单文件 512 KiB / 总计 8 MiB、
      524,288 UTF-16 code units 活动文档、100 条诊断及连续两次 1 s 慢操作熔断预算。Xiaomi
      23046RP50C / API 35 的真实 20 文件跨 APK 用例在保存完成后 201 ms 发布
      TS2322, 包含保存共 234 ms, 诊断计算 18 ms, service revision 始终为 1;
      最终 Ace 真机 8/8、宿主真机 4/4 通过。完整数据流、取消语义、限制与证据见
      [T4-5 保存即诊断](docs/architecture/SAVE_DIAGNOSTICS_T4_5.md)。

## T5 运行时动态 TS

目标: 运行中的动态性不再是被拒绝的边界 (原 S8-6 从"保持拒绝"改为实现)。

- [x] **T5-1 运行中出现的 TS 文件按需编译**: dynamic import 未命中
      编译快照时, 不再返回 `ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND`,
      而是触发按需编译 (复用 T3-1 常驻会话) 后继续执行; Source Map 与
      栈映射同标准; 路径逃逸与歧义仍是稳定错误; 编译失败给包含诊断的
      运行时错误。
      验收: 脚本运行中生成/下发一个新 `.ts` 文件并 `import()` 成功执行;
      类型错误的动态文件得到可读诊断而非崩溃。
      完成 (2026-08-29): module-source provider 以独立于 runtime contract 2 的 additive
      v3 增加 `compile_missing_typescript`。Node Runtime 只从执行专属私有 workspace
      no-follow 精确读取新出现的 `.ts/.mts/.cts`, 通过带精确字节数与 SHA-256 的只读
      PFD 交给宿主; 宿主不把源码写回项目, 而是把内存 overlay 加入完整项目 archive,
      以相同项目身份复用 T3 resident compiler session 并关闭跨执行结果缓存。生成的
      `.js/.mjs/.cjs` 经过路径与额度复验后进入运行时模块表, Source Map binding 与启动
      snapshot 合并用于最终 V8 栈回映。普通 provider 操作仍保持 5 s 上限, v3 编译使用
      独立 30 s 有界预算; compiler-backed Node 项目仅在没有显式 `nodeProjectTimeoutMs`
      时把项目默认总预算提高到 30 s。v1/v2 provider、raw TypeScript 直派、Rhino 动态
      文件与 T5-2 字符串执行语义均不变。Xiaomi 23046RP50C / API 35 最终产物的三项
      真实跨 APK 验收分别在 19.372 s、17.251 s 与 19.517 s 通过: 动态 `.mts` 执行并
      保持歧义/逃逸稳定错误, TS2322 返回
      `ERR_AUTOJS6_TYPESCRIPT_COMPILATION_FAILED`, 运行异常精确映射到
      `late-stack.mts:3` 且不泄漏 `.mjs` 帧。协议、安全边界与证据见
      [T5-1 运行时 TypeScript 按需编译](docs/architecture/ON_DEMAND_TYPESCRIPT_T5_1.md)。
- [x] **T5-2 TS 字符串即时执行**: engines 层提供执行 TypeScript 源字符串
      的入口 (新 API 或现有入口自动识别), 即时编译执行, 诊断与栈映射
      同标准; 控制台/日志面板可交互执行 TS 片段。
      验收: `engines.execScript` 等价入口传入 TS 字符串直接运行; 控制台
      输入一行 TS 得到求值结果。
      完成 (2026-08-29): 宿主 `engines.execScript(name, source)` 在名称为
      `.ts/.tsx/.mts/.cts` 时自动建立有稳定语义身份的内存 TypeScript source,
      复用既有独立 compiler archive、缓存、诊断和 Source Map 执行准备链路;
      `.ts/.tsx` 固定进入 Rhino, `.mts/.cts` 固定进入 Node, declaration 与非规范
      单文件名在执行前拒绝, 普通 JavaScript 行为不变。Loop-based Rhino execution
      现将最终求值结果传回 listener。完整日志页和编辑器日志底部面板共用单行输入,
      显示输入及 `=>` 结果并保留当前脚本工作目录。Xiaomi 23046RP50C / Android 15
      真机最终 4/4 核心验收及独立控制台端到端验收通过: Rhino 内存 TS 返回 42,
      `.mts` 内存 TS 经 Node 执行并打印 42, TS2322 阻止执行, 运行异常映射到
      `inline-runtime.ts:3`, 控制台打印 `=> 42`。
      设计、兼容边界与证据见
      [T5-2 TypeScript 字符串即时执行](docs/architecture/IN_MEMORY_TYPESCRIPT_T5_2.md)。

## T6 遗留收尾与发布

目标: 清空 S 时代尾款; 对外发布保持外部依赖项原状。

- [x] **T6-1 删除 legacy stripping** (原 S4-2 尾项/S6-2): 原定"发布观察期
      满后删除", 但项目未发布、观察对象为空集, 按新原则直接执行 —— 删除
      Node Runtime 的 `legacyTypeScriptStrippingEnabled` 兼容键、正则
      erasure 实现与专属兼容测试。
      验收: Node Runtime 仓库不再包含 stripping 代码路径, 一致性套件
      全绿; 原始 `.ts/.mts/.cts` 继续稳定返回
      `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`。— 2026-08-29 完成：删除 802 行
      `NodeTypeScriptStripper`、请求常量、宿主发送点、provider 私有明文准备协议及专属
      测试；用无转译能力的单一 source policy 锁定入口、module source、provider 与 native
      边界。修复编译读取与执行 admission 的次序后，snapshot/provider v3 按需编译保持
      可用，而 raw TypeScript 直达路径统一 fail closed。Node JVM/构建/镜像/样例门禁
      全绿，Android 9/12/15 三台真机完整 conformance 各 10/10、合计 30/30；宿主契约
      镜像与请求构造同步通过。设计、兼容边界与设备证据见
      [T6-1 legacy stripping 退役](docs/architecture/LEGACY_STRIPPING_REMOVAL_T6_1.md)。
- [ ] **T6-2 十语言 strings 收尾** (原 S7-6): 宿主 TSX 运行时残留的
      hardcoded 提示资源化, 十语言补全, zh-Hans/en 人工校对。
      验收: 用户可见路径无 hardcoded 中文/英文残留。
- [ ] **T6-3 模板宿主真机终验** (原 S7-4 终验): 三套模板在可安装宿主
      候选上按原目录零修改跑通。依赖宿主候选 APK。
      验收: 三个模板真机全部零修改跑通, S7-4 归档条目闭环。
- [ ] **T6-4 对外发布链** (原 S6-6): 按 2026-08-25 owner 决定, 等待
      AutoJs6 宿主项目先发布后执行 —— 上传 v0.6.0 (或届时版本) APK、
      登记插件索引、启用远端 issue 模板; 发布前跑
      `docs/maintenance/RELEASE_CHECKLIST.md` (含内存观察) 与升级预检。
      验收: 全新设备按宿主提示完成安装并运行 `hello.ts`。

## 架构不变量 (T 时代继续有效)

- TypeScript 版本精确锁定 (当前 6.0.3), 升级走 distribution lock 流程
  (UPGRADE.md); typescript-go 若采纳, 以同等强度的供应链锁定接入。
- 插件是编译前端: 不执行用户 JS、不发现/依赖 Node Runtime 插件、不读
  宿主任意路径、Worker 无网络。
- AIDL v1 transaction prefix 冻结, 新能力一律走 additive Bundle key。
- Source Map 强制产出, 不用生成 JS 冒充 TS 坐标, 不虚构列号。
- 源码快照一致性与输出完整性校验保持 fail closed (这是正确性, 不是
  保守性)。
- 不执行任何包/项目的 install/lifecycle/transformer script。
- 不恢复正则 stripping 作为任何形式的 fallback。

## 已明确移除的旧门槛 (相对 S 时代)

- **用户反馈触发门**: "真实项目撞到再调"、"收集高频键再评估"、"反馈强烈
  再立项" 全部移除 —— 零用户状态下这些门等于无限期搁置, 与目标相悖。
- **发布观察期作为删除旧代码的前提**: 无已发布版本时观察对象为空集,
  遗留代码直接删除 (T6-1)。
- **Post-GA 可否决评审**: 原 S9 npm 生态、原 S8-6 动态编译不再是
  "独立评估后可整体否决"的探索, 而是主线阶段 (T2/T5)。
- **安全评审文档作为验收物**: 新功能不再要求设计文档 + 安全评审结论
  才能动工; Android 进程模型与架构不变量兜底, 出问题按修 bug 原则处理。
- **每 job realm teardown 的契约地位**: 为 T3-1 常驻会话让路, 进程级
  隔离保留, Worker realm 生命周期允许重新设计。
- **容量/性能证明前置**: 配额上调、批量参数调整不再要求先建证据矩阵,
  实测一例 + 记录即可。
