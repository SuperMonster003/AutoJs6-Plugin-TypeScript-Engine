# AutoJs6 TypeScript Engine Plugin — Roadmap

> 本路线于 2026-08-22 重写, 替换旧的 R0–R8 证据驱动路线。旧路线及其全部
> 设备/收据证据归档于 [docs/roadmap/ROADMAP_LEGACY_R2_ERA.md](docs/roadmap/ROADMAP_LEGACY_R2_ERA.md)
> 与 docs/roadmap/R2*.md, 仅作历史参考, 不再是工作分母。
>
> 重写原则 (来自项目 owner 的明确要求):
> **先让用户的 TS 脚本跑起来, 出了问题修问题**。不追求一次性完美方案,
> 不以测试与安全边界的堆砌为产品本体, 不把验证正确性当成交付物。
> 每一项的完成标准是"用户可感知的行为", 不是"某个 verify 任务变绿"。

## 现状基线 (2026-08-22, 重写时点)

已经真实具备、且本路线直接继承的资产:

- **编译内核可用**: WebView + Web Worker 中的官方 TypeScript 6.0.3
  完整 `Program` 编译 (非 transpile-only), 输出 JS + Source Map v3 +
  结构化 diagnostics, `noEmitOnError` 生效。
- **端到端管线已通**: input ZIP → 校验 → Worker 编译 → output ZIP
  (modules/ + maps/ + diagnostics.json + compilation-manifest.json) →
  写回宿主 FD → callback。Rhino profile 与 NodeNext revision 1 均已在
  真机走通。
- **契约已冻结**: AIDL v1 (6 transaction, append-only)、input/output
  archive schema v1、diagnostics schema v1、双向兼容 AAR 门禁。
- **声明分发机制可用**: TypeScript 6.0.3 标准库 + Node profile 59 个
  AutoJs6 bridge 模块 d.ts 已打包进 APK 并逐文件锁 hash。
- **进程隔离成熟**: `:typescript_compiler` 独立进程、Binder/FD 传输、
  超时/取消/进程死亡恢复均已在 API 24–35 真机验证过。

当前真实缺口 (本路线要解决的):

1. **宿主一行消费代码都没有**: AutoJs6 主仓库没有 compiler client、
   没有 `.ts` 路由、没有引擎适配。插件编译得再好, 用户脚本也跑不起来。
2. **capability 广告全为 false**: 宿主按广告位判断会永远不发起编译。
3. **限制过紧, 脱离真实脚本**: 路径仅限 printable ASCII (中文文件名
   直接拒绝)、输出总量 1 MiB、compilerOptions 强制 `"{}"`、tsconfig
   被显式挡回、`.mts/.cts` 不准入。
4. **Rhino 声明层只有 toast/sleep 等几个符号**: 用户写 `device.xxx`
   在 strict 模式下直接报错, 无法通过类型检查。
5. **运行时栈映射不存在**: 运行错误指向生成 JS, 不指向原 TS。

方向判定: **架构方向正确** (编译前端插件 + 宿主路由 + 双引擎适配,
边界清晰、契约已冻结、内核已验证), 问题只在推进方式 —— 旧路线把
80% 精力花在证据链与边界矩阵上, 从未抵达用户。本路线保留架构与已冻结
契约, 把剩余精力全部投向"跑起来"。

## 阶段总览

| 阶段 | 主题 | 用户可感知结果 | 状态 |
|---|---|---|---|
| S1 | 插件解禁: 让编译能力真正可用 | (对宿主开发者) capability=true, 真实脚本不再被配额/ASCII/固定配置拦下 | 已完成 (6/6 必需；S1-7 可选) |
| S2 | 宿主最小集成: `.ts` 在 Rhino 上跑起来 | 用户点击运行 `.ts`, 脚本执行 | 未开始 |
| S3 | 体验补齐: 诊断、声明、栈映射 | 编译错误/运行错误定位到 TS 源码; AutoJs6 API 有类型 | 未开始 |
| S4 | Node profile 与项目级能力 | `.mts/.cts`、多文件项目、tsconfig 常用项 | 未开始 |
| S5 | 周边整合: 缓存、打包、编辑器 | 二次运行快、APK 打包、Ace 提示一致 | 未开始 |

依赖关系: S1 → S2 → S3 → S4 → S5 严格递进不是必须的 —— S1 与 S2 可
并行 (S2 的宿主代码先按 same-UID debug 路径联调), S3 各项相互独立,
S4/S5 在 S2 跑通后即可按需插队。唯一硬前提: **S2 之前必须完成 S1-1
(capability 解禁), 否则宿主门禁永远不放行**。

修 bug 原则: 任何阶段跑出来的异常, 直接开 issue 修复, 不回头补
"边界矩阵"。已删除的旧 verify 任务不再恢复; 保留的少量门禁见 S1-6。

---

## S1 插件解禁: 让编译能力真正可用

目标: 移除"契约层可用、广告层关闭"的错位与脱离真实脚本的限制。
全部工作在本仓库完成, 不依赖宿主。

- [x] **S1-1 capability 广告翻转**: `typescriptCompilerAvailable`、
      `projectCompilation`、`semanticDiagnostics`、`sourceMapV3` 置
      `true` (mask 15); `nodeRuntimeRequired` 保持 `false`。删除
      "promotion 需 M5 审计"的流程约束。
      验收: `getCompilerInfo()` 返回四项 true; 现有 same-UID debug
      编译回归通过。
- [x] **S1-2 非 ASCII 路径放行**: ZIP 条目路径、诊断 URI、模块映射从
      printable-ASCII 收紧为 UTF-8 (仍禁控制字符、`..` 逃逸、绝对
      路径)。中文/空格/Unicode 文件名可编译。
      验收: 含中文文件名与中文目录的项目编译成功, 诊断 URI 正确回显。
- [x] **S1-3 输出配额放宽**: 单 JS/map 512 KiB → 16 MiB, 输出总量
      1 MiB → 64 MiB (保持在既有传输层上限内, 不新增公开传输层)。输入维持
      512 files / 8 MiB 总量, 但**插件自带声明不再计入 caller 配额**。
      验收: 一个 emit 后 2 MiB 的单文件脚本编译成功。
      已在 API 37 模拟器与 Android 12 实机通过 5 MiB source 的 Public Binder
      回归；caller + Rhino declarations 的 retained snapshot 超过旧 8 MiB，最终
      JavaScript 超过 2 MiB，formal output 成功提交。
- [x] **S1-4 tsconfig 受控子集激活**: `configKind="tsconfig"` 不再
      返回 not-activated; 启用已实现的 18 个受控布尔项 (`strict` 家族、
      `noUnused*`、`skipLibCheck`)。越界键报 warning 并忽略, 不拒绝
      整个请求 (fail-open on unknown keys, 保持对 `paths`/`outFile`/
      自定义 transformer 等结构性选项的拒绝)。
      验收: 带 `{"compilerOptions":{"strict":false}}` 的项目按非
      strict 编译通过。
      Public Rhino classifier、Worker 与 output provenance 已贯通；未知 top-level/
      compiler option 分别形成 warning `910001/910002`，结构性选项仍 fail closed。
      focused Public Binder 验收已通过 API 31、API 35 实机与 API 37 模拟器。
      设计与接入字段见
      [S1-4 Public Controlled tsconfig Activation](docs/architecture/CONTROLLED_TSCONFIG_ACTIVATION_S1_4.md)。
- [x] **S1-5 NodeNext revision 2 准入**: `.ts/.mts/.cts` →
      `.js/.mjs/.cjs` 已在 Worker 实现, 将其加入 endToEnd admitted
      authorities, 走完输出归档。
      验收: 含 `.mts` 的项目以 node profile 编译, 产出 `.mjs` 与配对 map。
      Public classifier 现在只接受 exact Node revision 2；revision 1 继续作为
      private/debug compatibility default，不对外开放。`getCompilerInfo()` 通过
      additive `node*` keys 发布 Node profile/config/runtime/declaration identity，Node
      config 仍精确为 `configKind=none`。推进中发现并修复了可信 Node declarations 的
      ambient bare-module 引用被误送入 caller package resolver 的真实缺口；caller 任意裸包
      仍 fail closed。focused Public Binder 验收已在 API 31、API 35 实机与 API 37
      模拟器同时产出 `.mjs/.cjs` 及配对 map。设计与接入字段见
      [S1-5 NodeNext Public Admission](docs/architecture/NODE_NEXT_PUBLIC_ADMISSION_S1_5.md)。
- [x] **S1-6 验证层瘦身**: `check` 只保留 — 单元测试、
      `verifyCompilerDistributionLock` (供应链锁)、AIDL 冻结门禁、
      一个覆盖 Rhino/Node 各一次真实编译的 smoke 任务。其余 80+ 个
      `verifyR2M*` 证据任务从默认图摘除 (保留任务定义可手动跑, 或
      直接删除, 二选一执行时决定)。CI/本地验证默认 `--offline`,
      仅 distribution lock 任务允许联网。
      验收: 干净 checkout 后 `gradlew check --offline` < 5 分钟通过。
      当前 `check` 最终依赖在所有 Android project 配置完成后统一替换；历史 lint/APK/release/
      device/`verifyR2M*` 任务仍注册但只允许显式调用。`verifyCompilerSmoke` 使用真实 Worker
      分别执行一个 Rhino 双模块编译与一个 Node revision-2 `.mts/.cts` 编译。供应链获取与
      byte-for-byte 校验统一由 `verifyCompilerDistributionLock` 持有。清理 project Gradle/Kotlin/
      native/build outputs 后，Windows 本地 `gradlew check --offline` 以 116 个全部执行任务在
      28.1 秒通过；详见
      [S1-6 Daily Verification](docs/architecture/DAILY_VERIFICATION_S1_6.md)。
- [ ] **S1-7 简化握手 (可选, 若宿主集成时嫌繁)**: 5 秒 ack 握手保留
      但把未 ack 定为宽限 30 秒, 或在 additive key 中允许
      `ackMode=implicit`。不动 AIDL。
      验收: 宿主不发 acknowledge 也能收到完成回调 (implicit 模式)。

## S2 宿主最小集成: `.ts` 在 Rhino 上跑起来

目标: AutoJs6 主仓库落地最薄的一条消费链。**本阶段代码全部在
AutoJs6 主仓库**, 本插件仓库只配合联调与修 bug。
完成标志 = 用户在 AutoJs6 里点击运行一个单文件 `hello.ts`,
Rhino 执行并输出 toast。

- [ ] **S2-1 同步 typescript-api**: `plugin-api/typescript-api` 以
      源码或 AAR 进入 AutoJs6 主仓库; 主 APK manifest 加 compiler
      service 的 package visibility query。
- [ ] **S2-2 薄 compiler client**: 基于既有 AidlPluginHost 绑定
      `org.autojs.plugin.typescript.COMPILER`, 实现 contract
      version/hash 校验、prewarm、startCompilation + ack、进度/完成
      回调、超时与错误透传。错误映射为可本地化的用户消息
      ("未安装 TypeScript 插件" 等)。
- [ ] **S2-3 输入归档构造器**: 宿主把单个 `.ts` 文件 (虚拟单文件项目)
      打成 input-archive v1 ZIP + request-manifest.json, 计算所需
      SHA-256。多文件项目此阶段不做。
- [ ] **S2-4 路由改造**: `.ts` 从 Node 文件判断中移除, 成为独立
      TypeScript source kind; 默认引擎 Rhino; `.d.ts` 不可执行;
      `.tsx` 明确报错。路由优先级 directive > project type >
      TS default > Rhino。
- [ ] **S2-5 执行前编译挂接**: 在 RunnableScriptExecution 创建引擎前
      调用编译; 编译产物 (JS 字符串) 直接喂给 RhinoJavaScriptEngine;
      编译失败不创建引擎, 诊断以现有 console 错误格式展示 (文件/行/列
      文本即可, 精确映射留给 S3)。主线程不同步等待。
- [ ] **S2-6 冒烟脚本集**: 5–10 个真实脚本 (toast、ui 布局、
      click/swipe、http、images 截图) 的 `.ts` 版本手工跑通。
      **跑不通的直接修** (插件或宿主), 每修一个记 CHANGELOG。

退出标准: Node Runtime 插件未安装的设备上, 上述冒烟脚本全部可运行;
TypeScript 插件未安装时给出明确安装提示而非崩溃。

## S3 体验补齐: 诊断、声明、栈映射

目标: 从"能跑"到"好用"。三条线相互独立, 可并行。

**A. Rhino 声明层扩容** (本仓库)

- [ ] **S3-A1** 解除 Rhino 层仅 825 字节的裁剪: 以
      `@sm003/autojs6-dts` 为源, 按模块拆分文件绕过单文件 65,536
      token 限制; 若项目 262,144 token 预算不足, 提高预算或按需
      加载 (用户项目引用到的全局才注入)。
      验收: `device.width`、`images.captureScreen()`、`ui.layout()`
      等常用 API 在 strict 下通过类型检查。
- [ ] **S3-A2** 声明包版本随插件版本发布, 不再要求与某个私有 host
      commit 逐文件锁 hash (保留 fingerprint 用于缓存 key 即可)。

**B. 编译诊断直达编辑器** (宿主仓库)

- [ ] **S3-B1** diagnostics.json (schema v1) 解析为宿主
      ScriptCompilationException, console/日志面板显示 TS 文件、行、
      列与 TS 错误码, 点击跳转编辑器对应位置。

**C. 运行时栈映射** (宿主仓库为主)

- [ ] **S3-C1** 宿主注册 compilation-manifest 中的 JS↔map 配对,
      实现 Source Map v3 解析 (mappings VLQ 解码)。
- [ ] **S3-C2** Rhino 运行异常栈: 生成 JS 的文件/行 → 原 TS 文件/行
      (Rhino 无列号, `column=null`, UI 不虚构)。
- [ ] **S3-C3** wrapper/bootstrap 双层 map 组合 (若宿主包了 IIFE/
      module wrapper, 先扣 wrapper 偏移再查 compiler map)。

退出标准: 一个抛异常的 `.ts` 脚本, console 里编译错误与运行错误
都能点击跳回 `.ts` 源码行。

## S4 Node profile 与项目级能力

目标: 补齐与 Node.js Runtime 插件的组合, 以及真实多文件项目。

- [ ] **S4-1 Node 引擎适配** (宿主 + Node 插件仓库): Node adapter
      消费同一 CompilationBundle, `.ts/.mts/.cts` 按 NodeNext
      revision 2 产物执行 (`.js/.mjs/.cjs`); Node 栈映射到 TS
      文件/行/列。Node Runtime 不可用时给明确错误, 不影响 Rhino 路径。
- [ ] **S4-2 退役正则 stripping**: NodeTypeScriptStripper 先挂
      legacy feature flag, 默认走插件编译; 稳定一个版本后删除。
      插件未安装时提示安装, 不静默回退 stripping。
- [ ] **S4-3 多文件项目**: 宿主项目 (project.json + tsconfig.json)
      按目录打包进 input archive; 插件内已有的受控模块解析
      (C6/C7 成果) 直接消费; import 图、循环依赖、JSON 导入按
      现有 Worker 能力放行。
      验收: 一个 10+ 文件、含相对 import 与 JSON 导入的项目在
      Rhino 与 Node 各跑通一例。
- [ ] **S4-4 dynamic specifier 映射**: `.ts/.mts/.cts` 动态 import
      到生成扩展名的映射生效; 未命中给稳定错误码; 运行中出现的
      外部 TS 文件明确拒绝 (维持首个 GA 的 snapshot 边界)。
- [ ] **S4-5 输入配额按需上调**: 若真实项目撞到 512 files / 8 MiB,
      直接上调至传输层上限 (4096 files / 64 MiB) 并实测一个大项目。
      不预先做容量证明, 撞到再调。

## S5 周边整合: 缓存、打包、编辑器

目标: 日常使用体验与发布链路。各项独立, 按用户反馈排优先级。

- [ ] **S5-1 编译缓存** (宿主): cache key = 源码图 hash + compiler
      fingerprint + profile revision + declarations fingerprint;
      命中则跳过编译; loop 执行天然复用 (S2-5 已保证单次执行内不
      重复编译, 此项做跨执行缓存)。
      验收: 未修改脚本二次运行不出现编译等待。
- [ ] **S5-2 APK Builder**: 打包时 build-time 编译, APK 内只含 JS
      (+可选 map), 不含 TS 源码与 compiler; 加密项目在内存/加密
      staging 中解密-编译-清理。
- [ ] **S5-3 后台/定时/Tasker 入口**: 所有非前台执行入口共用
      S2-5 的 preparation 管线 (验证一遍, 缺口就修)。
- [ ] **S5-4 Ace 编辑器一致性** (Ace 插件仓库): 编辑器诊断与执行前
      诊断使用同版 TypeScript 与同一 profile 选项; 编辑器不可用不
      影响运行 (S2 已保证, 此处只做提示一致性)。
- [ ] **S5-5 性能观察**: 记录典型脚本的编译耗时与内存 (打点即可,
      不设 p95 门禁); 明显过慢的场景 (如冷启动 WebView) 用 prewarm
      缓解。出现具体性能问题再专项优化。

## 长期不变约束 (从旧路线继承, 仍然成立)

- TypeScript 版本精确锁定 (当前 6.0.3), 升级走 distribution lock 流程。
- 插件是编译前端, 不执行 JS、不发现/依赖 Node Runtime 插件、不读宿主
  任意路径、Worker 无网络。
- AIDL v1 transaction prefix 冻结, 新能力走 additive Bundle key。
- Source Map 强制产出; 不用生成 JS 冒充 TS 坐标。
- 禁止项目自定义 transformer/compiler plugin/lifecycle script。
- `.tsx`/JSX 不在本路线内, 需要时单独立项。
- 不恢复正则 stripping 作为任何形式的 fallback。

## 已明确放弃 (相对旧路线)

- M3 剩余 schema/quota 证据项 (S4–S6/O1–O3/X1–X3)、M4 全部 16 项
  release hardening 矩阵、M5 promotion 审计: **不再执行**。schema v1
  已冻结够用; 超时/取消/进程恢复已有真机证据; 其余按"出了问题修问题"
  处理。
- 每切片的 targeted + Core 双轮构建收据、SHA-256 receipt、
  baseline-intersection timeout 校准流程: 不再作为完成定义。
- 128 MiB/256 MiB 输出、TEST_ONLY capacity evidence 矩阵、
  external-UID fixture 的持续维护: 冻结现状, 不再扩展。
