# AutoJs6 TypeScript Engine Plugin — Roadmap

> 本路线于 2026-08-22 重写, 替换旧的 R0–R8 证据驱动路线。旧路线及其全部
> 设备/收据证据归档于 [docs/roadmap/ROADMAP_LEGACY_R2_ERA.md](docs/roadmap/ROADMAP_LEGACY_R2_ERA.md)
> 与 docs/roadmap/R2*.md, 仅作历史参考, 不再是工作分母。
>
> 重写原则 (来自项目 owner 的明确要求):
> **先让用户的 TS 脚本跑起来, 出了问题修问题**。不追求一次性完美方案,
> 不以测试与安全边界的堆砌为产品本体, 不把验证正确性当成交付物。
> 每一项的完成标准是"用户可感知的行为", 不是"某个 verify 任务变绿"。

## 现状基线 (2026-08-22 重写, 2026-08-25 更新)

已经真实具备、且本路线直接继承的资产:

- **编译内核可用**: WebView + Web Worker 中的官方 TypeScript 6.0.3
  完整 `Program` 编译 (非 transpile-only), 输出 JS + Source Map v3 +
  结构化 diagnostics, `noEmitOnError` 生效。
- **端到端管线已通**: input ZIP → 校验 → Worker 编译 → output ZIP
  (modules/ + maps/ + diagnostics.json + compilation-manifest.json) →
  写回宿主 FD → callback。Rhino profile 与 NodeNext revision 2 均已在
  真机走通。
- **契约已冻结**: AIDL v1 (6 transaction, append-only)、input/output
  archive schema v1、diagnostics schema v1、双向兼容 AAR 门禁。
- **声明分发机制可用**: TypeScript 6.0.3 标准库 + Rhino profile 51 个
  模块化 AutoJs6 d.ts + Node profile 59 个 bridge d.ts 已打包进 APK。
  声明 release 与插件版本一致, layer/profile fingerprint 可直接作为缓存
  身份；外部 Rhino npm tarball 保持 digest/SRI 锁定, 本地 Node 层不再与
  私有宿主 commit 或逐文件 source hash 耦合。
- **进程隔离成熟**: `:typescript_compiler` 独立进程、Binder/FD 传输、
  超时/取消/进程死亡恢复均已在 API 24–35 真机验证过。

重写时的真实缺口 (以下 1–5 已由 S1–S3 当前主线关闭):

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
| S1 | 插件解禁: 让编译能力真正可用 | (对宿主开发者) capability=true, 真实脚本不再被配额/ASCII/固定配置拦下 | 已完成 (6/6 必需；S1-7 已决定不做) |
| S2 | 宿主最小集成: `.ts` 在 Rhino 上跑起来 | 用户点击运行 `.ts`, 脚本执行 | 已完成 (6/6) |
| S3 | 体验补齐: 诊断、声明、栈映射 | 编译错误/运行错误定位到 TS 源码; AutoJs6 API 有类型 | 已完成 (A1-A3、B1、C1-C3) |
| S4 | Node profile、项目级能力与原生 TSX | `.mts/.cts`、多文件项目、tsconfig 常用项、TSX UI DSL | 进行中 (S4-1、S4-3、S4-4、S4-6 已完成；S4-2 主执行路径已迁移, 删除旧实现待办) |
| S5 | 周边整合: 缓存、打包、编辑器 | 二次运行快、APK 打包、Ace 提示一致 | 已完成 (5/5) |
| S6 | 发布收尾: 版本、日志、正式 APK | 用户可安装含完整 TS 能力的正式版本 | 进行中 (4/6；本地产物与四设备门禁已完成，远端随宿主发布延期) |
| S7 | 文档、模板与分发 | 新用户凭文档与模板即可上手 | 已完成 (6/6；S7-4/S7-6 分别由 T6-3/T6-2 闭环) |
| S8 | 真实项目能力扩容 | 配置/配额/monorepo/ESM 循环按需解锁 | 未开始 |
| S9 | npm 受控生态 | 纯 JS npm 包可导入 | 未开始 (Post-GA) |
| S10 | 性能、升级与维护 | 冷编译更快; TS 版本可升级 | 已完成 (5/5) |

依赖关系: S1 → S2 → S3 → S4 → S5 严格递进不是必须的 —— S1 与 S2 可
并行 (S2 的宿主代码先按 same-UID debug 路径联调), S3 各项相互独立,
S4/S5 在 S2 跑通后即可按需插队。唯一硬前提: **S2 之前必须完成 S1-1
(capability 解禁), 否则宿主门禁永远不放行**。

修 bug 原则: 任何阶段跑出来的异常, 直接开 issue 修复, 不回头补
"边界矩阵"。已删除的旧 verify 任务不再恢复; 保留的少量门禁见 S1-6。

S6+ 依赖关系: S6-5 本地正式 APK 已存在；2026-08-25 owner 明确决定宿主项目
先发布，本项目在此之前不创建远端 Release/索引，因此 S6-6 与依赖真实发布周期的
S6-2 合理延期，不再阻塞仓库内主线。S7 文档和本地模板已直接推进；原等待宿主候选的
S7-4 后由 T6-3 于 2026-08-29 完成，S7-6 也由 T6-2 同日闭环。S8 各项由真实用户反馈触发,
无固定顺序; S9 整体是可否决的 Post-GA 探索; S10 与所有阶段并行,
按发版节奏例行执行。
验证基线沿用 S1-6: 本地一律 `gradlew check --offline`, 仅
`verifyCompilerDistributionLock` 与 S10-3 官方预检/首次 acquisition 允许联网。S7 新增的
`verifyS7UserGuidesAndTemplates` 只消费锁定的本地 compiler/declarations，验证文档
代码片段、模板 emit 与公开错误/配置常量，不访问网络。

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
- [x] **S1-7 简化握手 (won't-do)**: 5 秒 ack 握手保留
      但把未 ack 定为宽限 30 秒, 或在 additive key 中允许
      `ackMode=implicit`。不动 AIDL。
      验收: 宿主不发 acknowledge 也能收到完成回调 (implicit 模式)。
      2026-08-25 决定不实现：AutoJs6 宿主已完整接入显式 acknowledge，
      S2-S5 的三设备验收和后台入口均未暴露握手复杂度问题；新增 implicit
      分支只会扩大终态、超时和兼容测试矩阵，当前没有用户收益。若未来出现
      不能发送 acknowledge 的真实调用方，再以新需求重新评估，不预留半开启协议。

## S2 宿主最小集成: `.ts` 在 Rhino 上跑起来

目标: AutoJs6 主仓库落地最薄的一条消费链。**本阶段代码全部在
AutoJs6 主仓库**, 本插件仓库只配合联调与修 bug。
完成标志 = 用户在 AutoJs6 里点击运行一个单文件 `hello.ts`,
Rhino 执行并输出 toast。

- [x] **S2-1 同步 typescript-api**: `plugin-api/typescript-api` 以
      源码或 AAR 进入 AutoJs6 主仓库; 主 APK manifest 加 compiler
      service 的 package visibility query。
      宿主提交 `240083c00` 已按插件源码同步 API module、Gradle 依赖与
      manifest query，并通过 API 单测及宿主 Kotlin 编译。
- [x] **S2-2 薄 compiler client**: 基于既有 AidlPluginHost 绑定
      `org.autojs.plugin.typescript.COMPILER`, 实现 contract
      version/hash 校验、prewarm、startCompilation + ack、进度/完成
      回调、超时与错误透传。错误映射为可本地化的用户消息
      ("未安装 TypeScript 插件" 等)。
      宿主提交 `0938deed2` 已实现独占 binding lease、完整握手、取消/超时、
      严格 terminal/output 解析与 capability 门禁；`1f986545d` 为编译器
      缺失/停用场景提供本地化的插件中心安装/启用指引。停用插件的 API 37
      负向探针已精确观察到中文指引并确认无引擎执行。
- [x] **S2-3 输入归档构造器**: 宿主把单个 `.ts` 文件 (虚拟单文件项目)
      打成 input-archive v1 ZIP + request-manifest.json, 计算所需
      SHA-256。多文件项目此阶段不做。
      宿主提交 `0938deed2` 已实现确定性 ZIP、严格 JSON、逐项 SHA-256、
      有界 output archive/diagnostics 解析；中文文件名经真实 Public Binder
      编译并保留 UTF-8 source identity。
- [x] **S2-4 路由改造**: `.ts` 从 Node 文件判断中移除, 成为独立
      TypeScript source kind; 默认引擎 Rhino; `.d.ts` 不可执行。S2 阶段
      `.tsx` 明确报错, 后由 S4-6 独立解禁。路由优先级为显式 Node project > 普通单文件
      TypeScript > Rhino fallback；普通 `.ts` 中尚未支持的 Node directive
      明确失败，不静默改走其他引擎；`"ui"` directive 则进入 Rhino
      Activity，并支持字符串 XML 布局。原生 TSX DSL 在本阶段留到 S4。
      宿主提交 `6d6450315` 引入 `TypeScriptFileSource` 并覆盖普通文件、编辑器
      cache/semantic path 路由；`1f986545d` 对普通 TSX 与声明文件返回本地化
      明确错误；`c8088ae7b` 补齐 UI Activity 路由。显式 Node project 保留
      既有行为，Node profile 与原生 TSX DSL 统一留到 S4。
- [x] **S2-5 执行前编译挂接**: 在 RunnableScriptExecution 创建引擎前
      调用编译; 编译产物 (JS 字符串) 直接喂给 RhinoJavaScriptEngine;
      编译失败不创建引擎, 诊断以现有 console 错误格式展示 (文件/行/列
      文本即可, 精确映射留给 S3)。主线程不同步等待。
      宿主提交 `6c1a893af` 将编译前移到脚本工作线程、引擎创建之前；成功
      产物封装为只读 compiled source，Source Map/diagnostics 随源进入 Rhino，
      loop 执行只编译一次。TS2322 负向测试确认 marker 未执行且
      `execution.engine == null`。宿主提交 `c8088ae7b` 将同一原则扩展到
      `ScriptExecuteActivity`：UI 线程外编译，成功后才创建引擎；创建引擎前
      失败可安全报告诊断并清理 execution。
- [x] **S2-6 冒烟脚本集**: 5–10 个真实脚本 (toast、ui 布局、
      click/swipe、http、images 截图) 的 `.ts` 版本在真机/模拟器跑通。
      **跑不通的直接修** (插件或宿主), 每修一个记 CHANGELOG。
      已完成真实可执行脚本：`hello.ts` 调用 toast 并继续执行、
      读取 `device` 属性并验证全局/`automator` 手势绑定、向本机回环服务发起
      `http.get`、通过 `images.read/pixel/recycle` 读取并检查 PNG、在受控悬浮窗
      上真实触发 click/swipe，以及 `"ui"` 模式下加载字符串 XML、查找视图、
      处理点击、更新文本并显式 `ui.finish()`；另覆盖 UTF-8 emit、类型错误
      fail-closed 与 S2 阶段的 TSX 明确拒绝。MediaProjection 在设备初始未授权状态下，
      由无障碍分别自动完成拒绝与允许分支：拒绝时 `requestScreenCapture()`
      确实返回 `false`；允许时选择整屏、捕获真实帧并检查尺寸、像素、回收与
      前台服务退出。宿主提交 `fdbdf8892` 的 12 项套件在 API 31/35/37
      各 12/12、合计 36/36 通过。API 37 上将 Node Runtime 插件置为
      `disabled-user` 后，完整套件仍 12/12 通过，测试结束后已恢复原始
      `default` 状态。经确认，原生 TSX DSL 不作为 S2 验收条件，独立进入
      S3/S4；其后已由 S3-A3/S4-6 完成类型层与执行链。

退出标准: Node Runtime 插件未安装或停用的设备上, 上述冒烟脚本全部可运行;
TypeScript 插件未安装时给出明确安装提示而非崩溃。**已达成**：API 37 的
Node Runtime 停用矩阵为 12/12，TypeScript 插件缺失/停用负向探针返回明确
安装/启用指引且未创建引擎。

## S3 体验补齐: 诊断、声明、栈映射

目标: 从"能跑"到"好用"。三条线相互独立, 可并行。

**A. Rhino 声明层扩容** (本仓库)

- [x] **S3-A1** 解除 Rhino 层仅 825 字节的裁剪: 以
      `@sm003/autojs6-dts` 为源, 按模块拆分文件绕过单文件 65,536
      token 限制; 若项目 262,144 token 预算不足, 提高预算或按需
      加载 (用户项目引用到的全局才注入)。
      验收: `device.width`、`images.captureScreen()`、`ui.layout()`
      等常用 API 在 strict 下通过类型检查。
      提交 `12db299` 已将公开上游的 50 个 AutoJs6 internal declaration
      模块逐模块生成, 再加独立 `index.d.ts`, 共 51 files / 878,019 bytes /
      42,694 TypeScript tokens；最大单文件 6,839 tokens, 不再依赖单文件
      巨型投影。`device/files/storages/colors/http/images/automator/ui` 等真实
      API 与字符串 XML UI 均在 strict 用户源码中通过；上游兼容性
      `@ts-nocheck` 仅存在于 compiler-owned trusted d.ts, 用户源码仍保持
      strict 且没有全局打开 `skipLibCheck`。API 31/33/35/37 上的设备、手势、
      本地 HTTP、图片、UI 与类型错误拒绝矩阵均通过。
- [x] **S3-A2** 声明包版本随插件版本发布, 不再要求与某个私有 host
      commit 逐文件锁 hash (保留 fingerprint 用于缓存 key 即可)。
      提交 `6b85685` 将资产根升级为 `r2-declarations/v2`, manifest schema 2 /
      generator 3；顶层 release 固定为
      `autojs6-plugin-typescript-engine-declarations@0.5.0`, runtime 要求其版本
      与 `BuildConfig.VERSION_NAME` 完全一致。lock schema 2 只对外部 Rhino
      tarball 保留 digest/SRI 供应链锁, Node 层仅声明本仓库 source directory、
      license 与 package entry, 不再记录私有 repository/commit/host build 或
      逐文件 source hash。生成 manifest 仍记录每个 APK 文件的 SHA-256 以验证
      成品完整性；layer/profile fingerprint 保持纯内容身份, release 变化只改变
      release/distribution 身份, 可直接服务后续缓存 key。生成、runtime、Program、
      NodeNext 与 APK 校验均通过, 新包在 API 31/33/35/37 上完成 profile 读取、
      strict API 编译和字符串 XML UI 执行回归。
- [x] **S3-A3 原生 TSX DSL 类型层**: 独立定义 JSX namespace、内置元素、
      属性与事件类型，不复用字符串 XML 的宽泛签名。
      验收: 一个包含布局、id 引用和点击处理器的真实 `.tsx` UI 脚本在
      strict 下通过类型检查，错误属性能给出稳定诊断。
      提交 `2e27bb8` 将 JSX 类型作为 Rhino declaration `index.d.ts` 的独立
      compiler-owned 投影生成：定义私有 factory/fragment、`JSX.Element`、
      显式 intrinsic element 集合、分组属性与 `onClick` 事件签名，不提供
      字符串索引签名。真实 strict `.tsx` 布局、id 查找、点击闭包编译成功；
      未声明属性稳定返回 TypeScript diagnostic，且不会产生可执行输出。

**B. 编译诊断直达编辑器** (宿主仓库)

- [x] **S3-B1** diagnostics.json (schema v1) 解析为宿主
      ScriptCompilationException, console/日志面板显示 TS 文件、行、
      列与 TS 错误码, 点击跳转编辑器对应位置。
      宿主提交 `286187e2e` 新增稳定的 `ScriptCompilationException` 与结构化
      diagnostic 模型，将 TypeScript schema v1 的文件、1 基行列、严重级别、
      TS 错误码及 related information 投影为无 Java/Binder 栈污染的 Console
      文本；日志链接统一转换为编辑器 0 基坐标，并支持含空格的 TS 文件名。
      单元测试与 API 31/33/35/37 真机上的编译拒绝、Console 解析和实际点击
      分发测试均通过，且类型错误脚本未创建 Rhino 引擎或执行原始源码。

**C. 运行时栈映射** (宿主仓库为主)

- [x] **S3-C1** 宿主注册 compilation-manifest 中的 JS↔map 配对,
      实现 Source Map v3 解析 (mappings VLQ 解码)。
      宿主提交 `62d8a02a2` 在现有 output manifest/descriptor 配对校验之后，
      将 entry JavaScript、配对 map、执行期 generated source name 与原 TS
      source name 注册为类型化 execution binding；新增严格且有界的 Source Map
      v3 consumer，校验 `version/file/sourceRoot/sources/names/mappings`，按规范
      跨段累计并解码 base64 VLQ，拒绝畸形、溢出、越界与非 canonical 映射。
- [x] **S3-C2** Rhino 运行异常栈: 生成 JS 的文件/行 → 原 TS 文件/行
      (Rhino 无列号, `column=null`, UI 不虚构)。
      同一宿主提交在 TypeScript Rhino engine tag 中保存上述 binding，并在
      `RhinoException.scriptStack` 写入 Console 前仅改写匹配当前 generated source
      的帧；其他 Rhino/bootstrap 帧保持原样。映射后的帧使用 `.ts:line`，刻意
      不沿用 Rhino 会虚构的 `:0` 列；含空格文件名的 V8 栈也能被日志链接解析。
      单元测试覆盖 VLQ 增量、列选择、畸形输入、无关栈不改写与无列输出；真实
      抛错脚本在 API 31/33/35/37 上均映射到原 `.ts` 第 3 行，Console 中不再
      出现 `.generated.js`，并通过当前文件解析与点击分发回归。
- [x] **S3-C3** wrapper/bootstrap 双层 map 组合 (若宿主包了 IIFE/
      module wrapper, 先扣 wrapper 偏移再查 compiler map)。
      当前 Rhino 路径没有 IIFE/module wrapper：compiler emit 原文经过 identity
      `preprocess` 后，以 `compileReader(..., line=1)` 直接编译，因此 wrapper
      offset 精确为 0，不需要第二层 map 或人为扣行。上述四设备真实抛错测试
      已证明 compiler map 可直接命中原 TS 行；未来若引入 wrapper，必须先增加
      wrapper map/offset 组合，本结论不授权静默偏移。

退出标准: 一个抛异常的 `.ts` 脚本, console 里编译错误与运行错误
都能点击跳回 `.ts` 源码行。**已达成**：编译期 TS2322 与运行期 Rhino
异常均输出可点击的原 `.ts` 位置；API 31/33/35/37 的三项组合回归
(编译拒绝、运行映射、实际点击分发) 均为 3/3 通过。

## S4 Node profile 与项目级能力

目标: 补齐与 Node.js Runtime 插件的组合, 以及真实多文件项目。

- [x] **S4-1 Node 引擎适配** (宿主 + Node 插件仓库): Node adapter
      消费同一 CompilationBundle, `.ts/.mts/.cts` 按 NodeNext
      revision 2 产物执行 (`.js/.mjs/.cjs`); Node 栈映射到 TS
      文件/行/列。Node Runtime 不可用时给明确错误, 不影响 Rhino 路径。
      - [x] 单文件 `.mts/.cts` 已成为 compiler-backed Node source：宿主按
        Node profile revision 2 构造归档并严格校验 `.mjs/.cjs`、配对 map、
        generated mapping authority 与输出 manifest，再将生成 JavaScript 交给
        Node.js Runtime 插件。UTF-8/中文文件名贯穿插件 public admission、宿主
        output reader 与运行时映射。插件提交 `adbedbc`，宿主提交
        `0ae1ec30e`。
      - [x] Node ESM/CJS 运行异常已组合 compiler map 与 Node Runtime 的
        bootstrap/wrapper line map：V8 frame 可回到原 `.mts/.cts` 的精确行列，
        Binder flatten 后的重复 `at` frame 仍能拆分，Console 与编辑器日志位置
        可点击。Node Runtime 提交 `61f3a36`，宿主同上。
      - [x] API 31、API 35 实机与 API 37 模拟器分别通过 `.mts` 路由、`.cts`
        路由、ESM 栈映射、CJS 栈映射及 Node `.ts` 负向门禁中的 4 项组合，
        合计 12/12；两种异常均映射到原 TypeScript 第 4 行第 11 列。
      - [x] Node `.ts` 现以最近的 `project.json`/根 `package.json` 作为明确项目
        boundary，由编译插件发布并校验 package metadata schema/policy/inventory
        authority；`type: module` 产出 ESM `.js`，`type: commonjs` 或缺失 `type`
        产出 CommonJS `.js`。宿主提交 `622bfddcc`，编译插件提交 `2e35f64`。
        不带任何项目/package 上下文的裸 Node `.ts` 仍明确拒绝，这是避免猜测
        module kind 的既定边界；一个只有入口 `.ts` 与根 `package.json` 的目录
        已可作为单文件项目执行。
      - [x] 导入的 CommonJS 模块现在先扣除 Node `Function()` wrapper 与 strict
        prologue 的 3 行偏移，再组合逐模块 compiler Source Map；Node Runtime
        提交 `92f3be6`。导入模块异常在三台设备上精确回到 `lib/fail.ts:3:11`，
        调用方回到 `main.ts:2:5`，生成的 `fail.js` 不再泄漏到最终栈。
- [ ] **S4-2 退役正则 stripping**: NodeTypeScriptStripper 先挂
      legacy feature flag, 默认走插件编译; 稳定一个版本后删除。
      插件未安装时提示安装, 不静默回退 stripping。
      - [x] `.mts/.cts` 已默认且唯一走编译插件，不再触发旧 stripping，
        也没有编译器缺失时的 stripping fallback。
      - [x] 宿主可静态发现的 Node `.ts` 项目已全部先编译再执行，不再经过
        stripping；编译器缺失、package authority 不匹配或项目快照变化均直接
        失败，不静默执行原 TypeScript。
      - [x] Node Runtime 的 entry、预装 `moduleSources`、
        `runtimeModuleSources` 与动态 provider 四条 raw TypeScript 边界已统一受
        请求级临时开关 `legacyTypeScriptStrippingEnabled` 控制；缺省值为
        `false`，旧调用方省略该键也会 fail closed。AutoJs6 宿主始终显式发送
        `false`，生产链不会进入旧 stripping。原始 `.ts/.mts/.cts` 稳定返回
        `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`，`.tsx` 则继续保持既有
        `ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION` 语义；结果诊断会明确记录
        `legacy_stripping_enabled=false` 与 `precompiled_only`。
      - [x] Node Runtime 提交 `8e08b3b`，AutoJs6 宿主提交 `d282ff3e0`。
        Node 单元测试、API 镜像门禁、宿主聚焦构建均通过；最新 APK 上的完整
        Node 一致性套件在 API 35 / 31 / 37 分别通过 7/7，宿主
        `.mts/.cts -> compiler -> Node` 聚焦用例在同三台设备分别通过 1/1。
      - [ ] 为覆盖一个已发布版本的迁移观察期，旧 erasure 实现暂时只保留在
        显式 opt-in 后；当前生产宿主没有任何 `true` 调用点。观察期结束后删除
        兼容键、正则 erasure 实现及其专属兼容测试，再勾选 S4-2 整项。
        2026-08-25 冻结 rollout：v0.6.0 是迁移观察版本，观察期从该版本实际
        发布开始，至少覆盖一个完整发布周期；期间只接受回归修复，不新增
        stripping 调用点。后继版本开发开始前核对宿主与 Node Runtime issue，
        若没有必须保留的兼容报告，即在 Node Runtime 仓库执行删除并完成本项。
- [x] **S4-3 多文件项目**: 宿主项目 (project.json + tsconfig.json)
      按目录打包进 input archive; 插件内已有的受控模块解析
      (C6/C7 成果) 直接消费; import 图、循环依赖、JSON 导入按
      现有 Worker 能力放行。
      验收: 一个 10+ 文件、含相对 import 与 JSON 导入的项目在
      Rhino 与 Node 各跑通一例。
      - [x] 宿主以 canonical project root 构造一次性、有界且确定排序的项目
        快照：caller 上限保持 512 files / 8 MiB，支持 UTF-8 目录与文件名，
        纳入 `.ts/.mts/.cts/.d.ts` 及运行期 `.js/.mjs/.cjs/.json`，排除
        `.git/.gradle/.idea/build/dist/node_modules`。编译完成后、执行物化前会
        重新校验每个源码与 package 文件的路径、大小及 SHA-256，源码在两阶段
        之间变化即 fail closed。
      - [x] 根 `package.json` 仅允许受控 metadata：严格 JSON、有界深度/节点，
        `type` 只接受 `module`/`commonjs`/缺失，任何 `scripts` 字段都拒绝。
        当前不猜测嵌套 package boundary；发现项目内第二个 `package.json` 即
        明确拒绝，待未来有独立 monorepo authority 后再开放。生成模块与已有
        runtime support 文件发生大小写折叠路径冲突时同样拒绝。
      - [x] output reader 不再假设单模块：逐一校验 N 份 JS 与 N 份 map、每个
        source path、Node generated mapping/catalog authority，并建立多 binding
        栈映射。Rhino 将 CommonJS 模块与 JSON 物化到引擎生命周期目录；Node
        以只读 module-source overlay 传输生成模块、JSON 与 package context。
        Source Map 匹配优先精确路径，其次唯一最长多段 suffix，最后才在 basename
        唯一时回退；同名模块存在歧义时保持未映射。
      - [x] 三设备验收全部通过：API 31、API 35 实机与 API 37 模拟器各 5/5。
        Rhino 用 10+ 模块、相对 import、CommonJS 循环依赖及 JSON 得到 `83`；
        Node CommonJS 覆盖同一组能力；Node ESM 另以 10+ 模块的两层无环依赖
        得到 `83`；导入文件中的 TS2322 返回该文件 URI；跨模块运行异常逐帧
        映射回原 TypeScript。API 37 在最终生命周期修正后又复跑 5/5。
      - [x] 实现提交：编译插件 `2e35f64`，AutoJs6 宿主 `622bfddcc`，Node
        Runtime `92f3be6`。同时以 `dae705a` 同步 S3 Rhino 声明扩容后遗留的
        runtime fingerprint、配额与 smoke fixture；本仓 `gradlew check --offline`
        116 个默认任务全部通过。

      已知边界：Node Runtime 当前的 partial ESM adapter 会把 named import 物化为
      局部快照，尚不具备标准 ESM cycle 所需的 live binding；因此 S4-3 只宣称并
      验收 CommonJS 循环依赖，ESM 验收保持无环。完整 ESM linker 属于后续 Node
      Runtime 专项，不能用 `NaN` 结果冒充循环依赖已支持。
- [x] **S4-4 dynamic specifier 映射**: `.ts/.mts/.cts` 动态 import
      到生成扩展名的映射生效; 未命中给稳定错误码; 运行中出现的
      外部 TS 文件明确拒绝 (维持首个 GA 的 snapshot 边界)。
      - [x] 宿主从本次真实 emit 的模块建立原始 TypeScript 路径清单，并通过
        请求级 closed-snapshot 标志与确定排序的清单交给 Node Runtime。运行时
        求值后的 `import()` specifier 只有在原始 `.ts/.mts/.cts` 路径与对应
        `.js/.mjs/.cjs` 产物同时存在于初始快照时才允许映射；普通 Node 请求
        缺省关闭该模式，不改变非 TypeScript 模块解析。
      - [x] 快照内缺失或运行时新增的 TypeScript 目标稳定返回
        `ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND`；无扩展名目标命中
        多个 TypeScript 源时返回
        `ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS`；越出项目根目录继续
        返回 `ERR_AUTOJS6_FS_PATH_ESCAPE`。三类拒绝均不会查询动态 source
        provider，也不会回退读取晚到的 TypeScript 文件。
      - [x] Node Runtime 完整 Android 一致性套件在 API 35 / 31 / 37 上分别
        以 2.696 s / 3.077 s / 2.208 s 通过 9/9；宿主 Node TypeScript 聚焦
        回归在同三台设备分别以 128.601 s / 99.919 s / 37.48 s 通过 7/7，
        覆盖三种扩展名计算式导入总和 `42`、静态 ESM 控制导入、缺失、歧义、
        越界及 provider 零回退。Node Runtime 提交 `3f1b3b1`，AutoJs6 宿主
        提交 `ebbf1b8d2`；最终离线构建分别通过 101 与 685 个 Gradle 任务。
- [ ] **S4-5 输入配额按需上调**: 若真实项目撞到 512 files / 8 MiB,
      直接上调至传输层上限 (4096 files / 64 MiB) 并实测一个大项目。
      不预先做容量证明, 撞到再调。
- [x] **S4-6 原生 TSX DSL 执行链** (插件 + 宿主): 在受控 profile 中定义
      JSX lowering/runtime 契约，宿主放行 `.tsx` 并将产物送入 Rhino UI
      Activity；该链路独立于 S2 的字符串 XML 实现，不以正则转换或静默
      fallback 冒充支持。
      验收: 一个原生 TSX 布局脚本能渲染、响应点击、更新文本并主动关闭，
      同时保留 Source Map 与编译诊断。
      - [x] 插件新增 exact Rhino profile revision 2，固定 classic JSX lowering
        (`jsx=React`, `jsxFactory=__autojs6Tsx`,
        `jsxFragmentFactory=__autojs6TsxFragment`)；`.ts/.tsx` 统一进入完整
        TypeScript Program，`.tsx -> .js` 与配对 Source Map 贯穿 framed
        transport、归档、诊断、模块映射和 capability metadata。revision 1
        保持原字节级 authority 并继续准入，冻结 API v1 常量未修改。
      - [x] 宿主在内部 source policy 中仅为 Rhino 放行 `.tsx`，并以私有全局
        factory 构造有界 descriptor tree；`ui.layout()` 将其安全转义为现有 XML
        inflater 输入，再绑定 `onClick` Callable。支持单根布局、Fragment、
        字符串/数值/布尔属性、文本子节点和自动事件 id；元素数、深度、属性数、
        数组长度与 XML 字节数均有明确上限，非法值直接失败。
      - [x] 插件提交 `2e27bb8`，AutoJs6 宿主提交 `37e1ab2be`。插件
        `gradlew check --offline` 116 个任务与 `assembleDebug` 通过；宿主归档、
        路由、TSX 渲染单测通过。同一 strict `.tsx` UI 脚本在 API 31、API 35
        实机与 API 37 x86_64 模拟器上均完成真实 Binder 编译、布局渲染、点击、
        文本更新及 `ui.finish()`。API 37 测试只点击一次性兼容提示的 `OK`，
        不选择 `Don't Show Again`，且不依赖无障碍服务或 MediaProjection 权限。

## S5 周边整合: 缓存、打包、编辑器

目标: 日常使用体验与发布链路。各项独立, 按用户反馈排优先级。

- [x] **S5-1 编译缓存** (宿主): cache key = 源码图 hash + compiler
      fingerprint + profile revision + declarations fingerprint;
      命中则跳过编译; loop 执行天然复用 (S2-5 已保证单次执行内不
      重复编译, 此项做跨执行缓存)。
      验收: 未修改脚本二次运行不出现编译等待。
      - [x] 宿主使用带版本、长度分隔的二进制语义键，覆盖完整源码图、入口与
        project identity、受控配置、Node package metadata、编译器 distribution、
        声明/profile/runtime capability 指纹及全部 profile/policy revision；
        每次 Binder 请求才变化的 request id 与传输归档认证指纹不进入缓存键。
      - [x] 编译输出以宿主私有、崩溃可恢复的 generation + commit-manifest
        形式持久化；ZIP 与索引均为 owner-read-only，命中时仍经现有严格 output
        reader 复核 ZIP hash、manifest hash、逐文件 hash、路径与当前源码绑定。
        篡改、残缺、超额或权限异常条目会失效并回退正常编译；缓存故障不会使
        原本有效的编译失败。缓存上限为 32 项 / 256 MiB，按最近使用时间淘汰。
      - [x] 命中发生在 dedicated binding 与 prewarm 之前。API 31、API 35 实机
        与 API 37 x86_64 模拟器分别完成独立 client 的连续两次真实执行：首次
        13,260 / 12,570 / 5,253 ms，第二次 38 / 38 / 28 ms；第二次均无专用
        Binder 连接或 Worker 编译，缓存 generation 未变化，设备侧归档与索引
        权限均验证为 `0400`。
      - [x] AutoJs6 宿主提交 `8333c180f`。语义键失效矩阵、持久化恢复/篡改/
        配额/陈旧 handle 单测、跨 request 严格 replay 单测、聚焦 TypeScript
        单测、AndroidTest 编译及 debug/test APK 构建均通过；三设备聚焦
        instrumentation 各通过 1/1。
- [x] **S5-2 APK Builder**: 打包时 build-time 编译, APK 内只含 JS
      (+可选 map), 不含 TS 源码与 compiler; 加密项目在内存/加密
      staging 中解密-编译-清理。
      - [x] 宿主在归档前扫描目录或单文件 Rhino `.ts/.tsx` 项目，经真实
        TypeScript 插件完成项目级 type-check/emit；Node `.mts/.cts` 与 Node
        项目在打包端明确拒绝，不回退 stripping 或把原始 TypeScript 装入 APK。
        生成的 CommonJS 模块由专用 Rhino launcher 启动，原始 `project.json`
        会替换为指向 launcher 的净化配置。
      - [x] Builder 请求及最终 APK 均剔除 `.ts/.tsx/.d.ts`、`tsconfig.json`
        与编译器；仅叠加 JS、外部 Source Map 和不含源代码的编译清单。
        Source Map 强制移除 `sourcesContent`，并重写为最终 APK 内的生成路径。
      - [x] 加密 TypeScript 的解密操作在宿主内存中完成；APK 打包绕过跨执行
        明文编译缓存。冻结的 seekable-FD 编译协议所需输入/输出归档使用宿主
        私有的一次性文件，并在正常、失败和下次进程启动的 orphan 路径上 fail-closed
        清理。宿主到 Builder 的生成 JS 使用一次性 AES-256-GCM staging；Builder
        校验版本、完整路径清单与 GCM tag，仅在内存中恢复明文并立即转为最终
        APK 脚本加密，随后清零源码、生成 JS、密钥、nonce 与中间密文缓冲区。
      - [x] 宿主单测覆盖 emit 规划、源码/config 快照漂移、Node fail-closed、
        staging 往返/篡改拒绝、临时归档并发/orphan 清理及异常分支内存清零；
        APK Builder Template Kotlin 编译、宿主 debug/androidTest APK 构建和
        Runtime Kit 协议 v3 生成均通过。普通 JavaScript 项目回归也在三设备
        各通过 1/1，证明无 TypeScript staging metadata 时的既有打包路径不变。
      - [x] API 31、API 35 arm64 实机与 API 37 x86_64 模拟器均完成加密多文件
        TypeScript 项目真实远程打包及冷启动，输出标记均为
        `AUTOJS6_S5_TYPESCRIPT_APK_OK_42`。离线逐项审计三个 APK：无 TS/config/
        compiler，三个生成 JS 均为最终加密文件，map 均为 v3 且无
        `sourcesContent`；原项目与持久编译缓存未变化，临时归档无残留。
      - [x] 编译插件提交 `238d573`，APK Builder Template 提交 `e7f4918`，
        AutoJs6 宿主提交 `a495af8ec`。验证用临时 Builder 已在三设备恢复为原
        `versionCode=5244`，生成测试应用与测试包已卸载；无障碍服务和
        MediaProjection 权限均未改动。
- [x] **S5-3 后台/定时/Tasker 入口**: 所有非前台执行入口共用
      S2-5 的 preparation 管线 (验证一遍, 缺口就修)。
      - [x] 宿主执行链审计确认 Alarm/WorkManager/JobService 定时任务、广播
        `IntentTask`、Tasker/本地 Run Intent 及快捷方式最终都进入
        `ScriptEngineService`，非 UI 执行统一由 `RunnableScriptExecution` 在创建
        引擎前调用 `TypeScriptExecutionCompiler.prepare`；UI Activity 使用同一
        preparation 判定。
      - [x] 修复 Tasker `preExecuteScript + .ts/.tsx/.mts/.cts` 被包装为
        `SequenceScriptSource` 后绕过编译的缺口：preparation 现在从序列中提取
        compiler-backed 文件源，按真实文件源选择 Rhino/Node profile，并把前置
        JavaScript 以稳定换行重新接到已编译入口之前；旧 `.js` 序列行为不变。
      - [x] Rhino 单文件/多文件 materialization 与 Node 单文件/项目请求均计入
        前置脚本的 generated-line offset；只给项目入口加前缀，导入模块保持原
        Source Map 坐标，运行栈不会因 Tasker 前缀错位。
      - [x] 新增路由/引擎选择/Source Map offset 单测及设备 instrumentation：
        同一 strict `.ts` 经 Tasker、定时任务、广播任务、快捷方式四种适配器执行，
        另覆盖 Tasker 前缀 + Rhino 多文件项目与 Tasker 前缀 + Node `.mts`。
        聚焦单测、AndroidTest 编译及 debug/test APK 离线构建通过；API 31
        arm64、API 35 arm64、API 37 x86_64 三设备各通过 3/3。测试不依赖
        无障碍或 MediaProjection，未调用权限变更命令；测试 APK 验证后已卸载。
      - [x] AutoJs6 宿主提交 `d0d353961`。
- [x] **S5-4 Ace 编辑器一致性** (Ace 插件仓库): 编辑器诊断与执行前
      诊断使用同版 TypeScript 与同一 profile 选项; 编辑器不可用不
      影响运行 (S2 已保证, 此处只做提示一致性)。
      - [x] Ace 内置 TypeScript 锁定为与编译插件一致的 `6.0.3`，并按执行端
        revision 2 固定选项选择 Rhino (`ES2018 + strict + CommonJS/Node10 +
        classic JSX factory`) 或 Node (`ES2018 + strict + NodeNext`)；版本不匹配
        时语义服务 fail closed，静态补全回退仍可用且不进入脚本执行链。
      - [x] profile 路由与宿主一致：独立 `.ts/.tsx` 使用 Rhino，`.mts/.cts`
        使用 Node，最近 `project.json` 声明 `type/projectType: node` 的项目内
        `.ts` 也使用 Node。宿主与 Ace 的默认 LSP 后缀同时新增 `.mts/.cts`、
        `.d.mts/.d.cts`；旧默认集合自动迁移，用户显式自定义集合不被覆盖。
      - [x] 诊断快照和 JS runtime state 暴露 TypeScript 版本、profile 与 revision；
        离线 runtime verifier 精确核对固定 compiler options、strict 诊断与版本
        拒绝。Ace `check`、debug APK、AndroidTest APK 及宿主偏好单测均通过。
      - [x] API 31 arm64、API 35 arm64、API 37 x86_64 三设备各通过 profile
        路由与资源封装 2/2 测试；测试包已卸载，三设备保留 Ace
        `1.1.10 (21)`，无障碍与 MediaProjection 均未改动。
      - [x] Ace 仍是有界单文档提示层：受控项目 tsconfig override 与跨文件
        诊断继续以执行前编译器为权威，不把编辑器可用性变成执行前提。Ace
        插件提交 `1300654b6`，AutoJs6 宿主提交 `d1dbd9533`。
- [x] **S5-5 性能观察**: 记录典型脚本的编译耗时与内存 (打点即可,
      不设 p95 门禁); 明显过慢的场景 (如冷启动 WebView) 用 prewarm
      缓解。出现具体性能问题再专项优化。
      - [x] 宿主每次编译恰好输出一条 `typescript-compile-perf schema=2`
        结构化观察，覆盖准备、缓存查找、专用 Binder 绑定 + prewarm、远端往返、
        插件自身编译、输出处理及总耗时，并记录归档字节数、源文件数和宿主 Java
        heap 起止/变化。宿主复用冻结的 progress schema revision 1，把 compiler
        进一步拆成 ingress 前、ingress、阶段间隔、parse、semantic、emit 与 emit 后；
        无 AIDL/schema 扩展，未知、重复、格式错误或乱序事件 fail-soft 忽略，不影响
        编译结果。成功、diagnostics、失败和协程取消均有稳定 outcome；cache hit 的
        Binder/compiler/phase 字段均为 `na`。日志不含源码、路径、requestId 或错误
        消息，不设置阈值或 release gate。
      - [x] schema 2 的优化前基线纠正了 schema 1 粗粒度观察的误判：四设备
        parse + semantic + emit 合计仅 `487–661 ms`，compiler 时间的主要部分实际是
        约 160 个 declaration/source 文件通过 WebMessage/Worker 的 ingress，而不是
        TypeScript Program 本身。优化前单文件 Rhino `total/compiler/ingress` 为：
        API 31 arm64 `12986/10927/9664 ms`，API 33 arm64 `4523/2995/1866 ms`，
        API 35 arm64 `18503/16483/15103 ms`，API 37 x86_64
        `9296/4945/3260 ms`。
      - [x] 针对该具体问题加入 bounded input batching：project start/end 仍各自
        单独 ACK，中间 config/file logical frame 最多 4 条组成一个 envelope；每条
        inner frame 的 exact shape、全局 sequence、逐文件 SHA-256、UTF-8/token/quota、
        cursor ownership 与原 handler 顺序不变，Worker 全部成功后只 ACK 最后一条
        sequence。AIDL、archive/result schema、profile fingerprint、progress phase 边界及
        每 job realm teardown 均不变。标准库设备用例的 379 logical frames 降为
        97 envelopes (-74.4%)，最大 envelope `350201 B`，低于既有 576 KiB 上限。
      - [x] 同一四设备 cache miss → hit 回归的优化后 `total/compiler/ingress` 为：
        API 31 `9215/7105/5785 ms` (ingress -40.1%，total -29.0%，hit 63 ms)，
        API 33 `2537/1471/802 ms` (-57.0%，-43.9%，hit 18 ms)，API 35
        `11683/9310/7919 ms` (-47.6%，-36.9%，hit 36 ms)，API 37
        `7145/3490/1836 ms` (-43.7%，-23.1%，hit 44 ms)。这是设备观察而非
        benchmark 门禁；prepare/bind、GC 与系统负载仍会引入波动。
      - [x] 独立 8 文件 Rhino 项目 (input archive 3798 B) 的公开编译耗时与
        活动态 PSS：API 31 为 compiler 12081 ms、compiler service / renderer /
        combined median `64548 / 97112 / 168054 KiB`；API 35 为 15418 ms、
        `90458 / 95241 / 191542 KiB`；API 37 为 6835 ms、
        `94490 / 130557 / 225047 KiB`。内存采样只用 5 秒 DEBUG pause 保持同一
        模块图存活，pause 不进入前一轮正常耗时；只校验样本有效，不设性能上限。
      - [x] prewarm 决策：schema 1 阶段只能确认按需 prewarm 与持久 cache 有效，
        不能把剩余 compiler aggregate 直接归因于 Program；schema 2 已纠正这一点。
        当前 bind + prewarm 观察约 `450–1772 ms`，cache hit 为 `18–63 ms` 且在绑定前
        返回。为挪走该区间而主动常驻高内存 renderer 仍得不偿失，因此不增加进程
        常驻/启动期 prewarm；本轮只优化已由 phase 数据证明的 ingress 往返，并继续
        保留每项目 realm teardown。
      - [x] 宿主 TypeScript 单测全集、AndroidTest Kotlin 编译及 debug/test APK
        离线构建通过；插件 `check --offline` (116 tasks)、桌面 4607-file ingress、
        64+8 MiB retained-heap、escape closure、debug/test APK 均通过。API 31/33/35
        arm64 与 API 37 x86_64 的宿主 cache 回归、插件标准库 batch instrumentation
        各通过 1/1。四台设备上的宿主/插件 instrumentation 测试包均已卸载，主包
        保留；清理前后无障碍 secure settings 逐值一致。测试未调用无障碍或
        MediaProjection 权限变更命令。初始观察提交仍为宿主 `035b01250` / 插件
        `07f8fc9`；phase 分解提交为宿主 `441d480d4`，bounded batching 提交为插件
        `0e1e2fc`。

## S6 发布收尾: 版本、日志、正式 APK

目标: 把 S1–S5 已验证的能力变成一个用户可安装的正式发布。启动本阶段时
CHANGELOG 停在 v0.5.0 (2026-07-27, 仍写着 `compilerAvailable=false`),
与真实能力严重脱节；2026-08-25 已形成 v0.6.0 本地正式签名候选并通过
四设备门禁。S4-2 的完整发布周期观察与 S6-6 对外上架按 owner 决定等待宿主
先行发布；等待期间继续推进不依赖远端的 S7+ 工作。

- [x] **S6-1 收尾 S1-7 简化握手决策**: 明确"做"或"放弃"并在
      ROADMAP 记录结论。若做: additive `ackMode=implicit` 或 30 秒
      宽限, 不动 AIDL; 若放弃: 在 S1-7 条目标注 won't-do 与理由
      (宿主集成已完成且未抱怨握手繁琐)。
      验收: S1 阶段不再有未决项。
      结论: won't-do；显式 ACK 保持为唯一协议路径，理由与重开条件已记录在
      S1-7。
- [ ] **S6-2 收尾 S4-2 观察期**: 随下一个正式版本发布启动观察期;
      观察期满 (一个发布周期) 后删除 Node Runtime 的
      `legacyTypeScriptStrippingEnabled` 兼容键、正则 erasure 实现
      与专属兼容测试, 勾选 S4-2 整项。
      验收: Node Runtime 仓库不再包含 stripping 代码路径。
      rollout 已冻结：v0.6.0 实际发布即开始观察，删除目标为其后继版本；
      因尚未发布且 Node Runtime 仍保留显式 opt-in 代码，本项保持未勾选。按
      2026-08-25 owner 决定，计时从宿主先行发布、本插件随后实际分发时开始；
      本地开发等待不虚构为发布观察期。
- [x] **S6-3 版本号与 CHANGELOG 追平**: 确定新版本号 (建议 0.6.0
      或直接 1.0.0-beta); 用 `.changelog/lang_*.json` 补记 S1–S5
      的用户可见变化 (capability 开启、中文路径、tsconfig 子集、
      NodeNext、TSX、缓存、APK 打包), 运行
      `py .python/generate_changelog.py` 生成十语言 CHANGELOG。
      验收: `--check` 通过; CHANGELOG 首条不再是
      `compilerAvailable=false` 时代的内容。
      结论: 版本冻结为 `0.6.0` / build `6`；十份 `lang_*.json` 已补记
      S1–S5 用户可见能力并生成十语言 Markdown，
      `py .python/generate_changelog.py --check` 通过。首条发行记录现为
      capability mask 15 的 v0.6.0，而非旧的关闭状态。
- [x] **S6-4 README 现状化**: 首屏"当前状态"段重写为面向用户的能力
      清单 (能编译什么、怎么装、已知边界), 旧 R2 演进细节移入
      details 折叠或归档文档。
      验收: 新用户只读 README 前 50 行即可知道插件能做什么。
      结论: README 首屏现已覆盖双 profile、安装要求、最小示例、配额与
      已知边界；旧 R2 长篇证据移入历史 `details`，并补记 WebView 119+
      兼容基线与当前 release-smoke 用法。
- [x] **S6-5 正式 release APK 发布链**: 用 `sign.properties` 正式
      签名构建 release APK; 人工安装矩阵至少覆盖 API 24 (最低)、
      API 31/35 (实机)、API 37 (模拟器) 各跑一个冒烟脚本; 产物
      SHA-256 记录进发布说明。
      验收: 一个可对外分发的 signed release APK + 校验和。
      结论: `app/build/outputs/apk/release/autojs6-plugin-typescript-engine-v0.6.0-universal.apk`
      已用正式证书构建；S10-1 最终重建后大小 `3,079,657` bytes，APK SHA-256 为
      `bfe3f3e1caba319b50e80e77b323b782bf2e484833c51ea0f4ea27494ec4e380`，
      签名证书 SHA-256 为
      `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`；
      `apksigner` 确认 APK Signature Scheme v2、单一 signer。framework-only
      `release-smoke` 以同证书发现 release service，经公开 Binder 契约预热并
      编译 `hello.ts`，核对 mask 15、TypeScript 6.0.3、零诊断、JS、map 与
      formal output manifest。下列四台均返回 `INSTRUMENTATION_CODE: -1`：
      - API 24 / Android 7.0 x86 AVD / Chrome-WebView `119.0.6045.194`；
      - API 31 / Android 12 Sony XQ-AT72 / WebView `145.0.7632.120`；
      - API 35 / Android 15 Xiaomi 23046RP50C / WebView `130.0.6723.86`；
      - API 37 / Android 17 x86_64 AVD / WebView `145.0.7632.218`。
      另以 API 24 / Chrome 69 复核旧 provider 负基线：页面在
      `compiler-host.js:76` fail closed 并得到有界 Worker probe timeout；该结果
      符合既有“Chrome 119+ 才是正式 API 24/25 provider”策略，不计入成功矩阵。
      release compiler-host、TypeScript distribution 与 R2 declaration distribution
      三项 APK 门禁，以及完整 `check --offline` 均通过。发布说明见
      `docs/releases/v0.6.0.md`。
- [ ] **S6-6 插件市场/索引登记**: 按 AutoJs6 插件分发渠道 (官方插件
      索引或 GitHub Releases) 上架, 宿主"插件中心安装指引" (S2-2)
      指向的下载路径真实可用。
      验收: 在一台全新设备上, 按宿主提示完成安装并运行 `hello.ts`。
      延期结论: 2026-08-25 owner 明确当前插件暂不具备远端发布条件，必须等待
      AutoJs6 宿主项目先发布。本项保持未勾选；不创建 git remote、不上传 APK、
      不登记索引。S6-5 产物与发布说明保留，宿主发布后再恢复本项。该延期不阻塞
      S7 文档/本地模板及其他不依赖远端的 Roadmap 工作。

## S7 文档、模板与分发

目标: 吸收旧 R8 中仍有效的文档/模板项。能力已齐但零文档, 新用户
无从下手; 各项相互独立, 可按反馈排序。

- [x] **S7-1 用户文档: 快速上手**: 安装插件 → 写 `hello.ts` → 运行;
      Rhino/Node 双 profile 的扩展名矩阵 (`.ts/.tsx` Rhino,
      `.mts/.cts` 与 Node 项目 `.ts` Node); `"ui"` 与 TSX 两种 UI
      写法各一个最小示例。
      验收: 文档内所有示例复制即可运行。
      结论: `docs/user/QUICK_START.md` 已覆盖安装/WebView 要求、独立
      `hello.ts`、双 profile/module-kind 矩阵、最小 project.json、字符串 XML UI
      与原生 TSX。三个带 `verify-s7` 标记的完整片段均使用锁定 TypeScript 6.0.3、
      实际 Rhino declarations 和公开 compiler options 完成零诊断 JS/map emit；
      hello 与两条 UI 能力已分别由 S6-5 release-smoke 和 S4-6 设备行为覆盖。
- [x] **S7-2 用户文档: 配置参考**: 受控 tsconfig 18 个布尔项、
      warning-ignore 语义、fail-closed 选项清单 (`paths`/`outFile`/
      transformer 等) 与原因; project.json/package.json `type` 对
      module kind 的影响; 输入/输出配额数字。
      验收: 用户不读源码即可判断某 tsconfig 键是否生效。
      结论: `docs/user/CONFIGURATION.md` 已逐项列出 18 个布尔项及默认/依赖，
      顶层 selector 语义与配置复杂度预算，完整 profile/escape/output/transformer
      分类，Rhino/Node project/package 边界，以及普通输入、输出和 diagnostics
      配额；同时明确 Node `configKind=none`，模板内 tsconfig 仅供编辑器使用。
- [x] **S7-3 错误目录**: 汇总稳定错误码
      (`ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`、
      `ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_NOT_FOUND`、
      `ERR_AUTOJS6_TYPESCRIPT_SNAPSHOT_MODULE_AMBIGUOUS`、
      `ERR_AUTOJS6_TYPESCRIPT_ARCHIVE_QUOTA` 等) + 触发条件 + 用户侧解法。
      验收: 每个公开错误码在文档有唯一条目。
      结论: `docs/user/ERRORS.md` 已为 17 个宿主/插件终态错误、11 个受控
      tsconfig detail code、warning `910001/910002` 与 18 对 Node resolver
      identity/numeric diagnostic 各提供唯一表项、触发条件和用户解法；门禁直接从
      `TypeScriptCompilerContract` 抽取 13 个插件终态、固定核对 4 个宿主运行时终态，
      并从 `TypeScriptR2Contract`、配置 policy 与 resolver enum 抽取其余清单；
      新增/漂移未文档化时 fail。
- [x] **S7-4 项目模板**: 发布三个可直接运行的模板仓库/压缩包 —
      Rhino 单文件+UI (含 TSX)、Rhino 多文件项目、Node ESM/CJS
      项目; 各含 project.json、tsconfig.json 与 3+ 真实 API 调用。
      验收: 三个模板在真机全部零修改跑通。
      当前进度: `templates/` 已交付三个顶层模板集：Rhino 单文件 TSX、Rhino
      三模块相对 import、Node ESM/CJS 双独立变体；共四个可运行 project，均含
      project.json/tsconfig 与至少三项实际 API，Node 变体另含最小 package.json。
      `verifyS7UserGuidesAndTemplates --offline` 使用真实 profile declarations 对全部
      source 做 Program type-check + JS/map emit，并校验 package boundary/API inventory。
      新门禁已纳入默认 `check`；本轮 `gradlew check --offline` 以 117 个 actionable
      tasks（7 executed、110 up-to-date）通过，Rhino/Node Worker smoke 同时为绿。
      后续 T6-3 已在当前 AutoJs6 源码构建候选上完成原目录终验：20 个文件本地/设备
      SHA-256 全部一致，Rhino TSX、Rhino 多文件、Node ESM/CJS 四个 project 全部
      真机通过；候选身份、运行回执与修复闭环见
      [T6-3 模板宿主真机终验](../architecture/TEMPLATE_HOST_ACCEPTANCE_T6_3.md)。
- [x] **S7-5 StackTrace 与 Source Map 契约文档**: 明确 Node 到
      文件/行/列、Rhino 到文件/行 (column=null); release APK map
      默认无 `sourcesContent`; 隐私边界 (日志不含源码)。
      验收: 契约页与 S3-C/S4-1 实测行为一致。
      结论: `docs/user/SOURCE_MAPS.md` 已冻结编译诊断、Node/V8 与 Rhino 三种
      定位精度，exact → 唯一最长路径后缀 → 唯一 basename 的多文件选图规则，
      歧义/损坏 map 的 fail-closed 行为，发布 APK 剥离 `sourcesContent` 以及日志、
      diagnostics、map 的隐私边界；内容与宿主 S3-C/S4-1 当前实现逐项核对。
- [x] **S7-6 多语言 strings 核对**: 插件与宿主新增的用户可见提示
      (安装指引、编译失败、TSX 拒绝等) 在十语言 strings 补全,
      至少 zh-Hans/en 人工校对。
      验收: 无 hardcoded 中文/英文残留在用户可见路径。
      当前进度: 插件市场说明已从旧 R1 占位文案更新为完整编译能力，并补齐
      ar/en/es/fr/ja/ko/ru/zh-Hans/zh-Hant-HK/zh-Hant-TW 十语言资源；宿主既有
      安装/编译失败提示也覆盖十语言。后续 T6-2 已将宿主 TSX 运行时 29 条提示全部
      资源化并完成十语言门禁与 zh-Hans/en 人工校对，见
      [T6-2 TSX 运行时十语言提示](../architecture/TSX_RUNTIME_LOCALIZATION_T6_2.md)。

## S8 真实项目能力扩容

目标: 按真实脚本反馈解锁当前保守边界。每一项都有明确的"用户撞墙"
触发条件, 不预先做容量证明; 撞到才做, 做了就实测。

- [ ] **S8-1 输入配额上调落地 (原 S4-5)**: 真实项目撞到 512 files /
      8 MiB 时, 上调至传输层上限 (4096 files / 64 MiB) 并用一个
      大项目实测 (建议含 1000+ files 或 30+ MiB 源码)。
      验收: 大项目编译成功; 配额数字更新进 S7-2 文档。
- [ ] **S8-2 受控 tsconfig 增量扩展**: 收集被 warning-ignore 拦下的
      高频键 (候选: `target` 降级、`lib` 子集、`removeComments`、
      `noUnusedLocals` 类之外的检查项); 每个新键单独评估是否破坏
      冻结 profile/输出拓扑, 安全的进 revision 3。
      验收: revision 3 policy + fingerprint 发布, 旧 revision 请求
      不受影响 (append-only)。
- [ ] **S8-3 嵌套 package boundary (monorepo 初步)**: 解除"项目内
      第二个 package.json 即拒绝"的限制, 支持 pnpm/yarn workspace
      风格的子包 `type` 声明; 保持无 scripts、无任意裸包。
      验收: 一个含 2 子包 (一 ESM 一 CJS) 的项目在 Node 跑通。
- [ ] **S8-4 Node ESM live-binding 循环依赖**: 跟进 Node Runtime
      的完整 ESM linker 专项, 解除 S4-3 已知边界 (partial ESM
      adapter 无 live binding, ESM 验收只能无环)。本插件侧无工作,
      只做联调验收。
      验收: ESM 循环依赖项目得到正确结果而非 NaN/TDZ 假值。
- [ ] **S8-5 编辑器项目级诊断 (原 R7 project mode)**: Ace 从单文档
      提示升级为消费宿主 project snapshot 的跨文件诊断; 停止屏蔽
      TS2307/TS2792 类模块解析错误; 与执行前编译 diagnostics
      golden 对齐。工作量大, 独立评估后可再拆子项。
      验收: 编辑器内 import 路径写错能实时标红, 且与运行前
      编译报错一致。
- [ ] **S8-6 运行中动态编译评估 (Post-GA 门槛)**: 对"运行中出现的
      外部 TS 文件"当前明确拒绝 (snapshot 边界)。若用户反馈强烈,
      按旧 R7 冻结的前提条件 (APK 内 compiler host、沙箱、内存
      生命周期、包体门槛) 立项评估; 否则保持拒绝并在文档声明。
      验收: 形成 go/no-go 决策记录; go 则另立专项路线。

## S9 npm 受控生态 (Post-GA, 独立评估)

目标: 当前 caller 任意裸包导入 fail closed, 用户无法使用哪怕纯 JS
的 npm 工具库 (dayjs/lodash 类)。这是与"完整 TypeScript 开发体验"
差距最大的一块, 但安全面大, 整体作为一个可否决的探索阶段。

- [ ] **S9-1 需求画像**: 收集真实用户想用的包清单; 区分纯 JS 无
      依赖、带类型声明、带原生模块三类; 明确首批只考虑纯 JS +
      `.d.ts` 的包。
- [ ] **S9-2 受控供应链设计**: 复用 distribution lock 模式 —
      宿主/用户显式导入 tarball, digest/SRI 锁定, 无 install
      scripts, 无网络; 包内容进入 input archive 的独立受信层,
      不计入 caller 配额; 复用既有 node_modules 拒绝规则的豁免
      通道设计。
      验收: 设计文档 + 安全评审结论 (可以是否决)。
- [ ] **S9-3 最小实现**: 若 S9-2 通过 — resolver 对锁定包放行
      裸 specifier, Rhino (CommonJS) 与 Node 双 profile 各实测
      一个真实包 (如 dayjs); 类型声明进入编辑器与编译一致。
      验收: `import dayjs from "dayjs"` 在两个 profile 编译执行
      通过。

## S10 性能、升级与维护

目标: 长期健康度。S5-5 已建立 schema 2 观测基线 (单文件 Rhino
total 2.5–11.7 s, cache hit 18–63 ms, ingress 已优化 -40%–57%),
剩余大头是 WebView/Worker 冷启动与 compiler service PSS。

- [x] **S10-1 冷启动继续压缩**: 按 schema 2 数据, bind + prewarm
      仍占 450–1772 ms, ingress 后的 compiler 阶段在低端机仍到
      9 s。候选手段 (各自独立评估, 用数据说话): 声明层按需注入
      (用户项目未引用的全局不进 Program)、标准库 SourceFile
      快照复用、envelope 批量上限再调。不设 p95 门禁, 但每项
      优化前后都记录同设备对比。
      验收: 至少一项优化落地且四设备观测改善, 或给出"已到
      平台边界"的数据结论。
      结论: production `projectInputBatch` 从最多 4 条 middle frame 调整为最多 6 条，
      但 Base64 payload chunk 仍最多 4 条，batchable middle frame 另受 92 KiB encoded
      上限约束；start/end、window=1、576 KiB hard envelope、result stop-and-wait 与所有
      logical-frame authority 均不变。固定 109-file / 379-frame 标准库 workload 从
      97 降为 68 envelopes，最大实测 `350433 B`。API 31/35 真机及 API 36/37 AVD 的
      `compiler/ingress` median 分别改善 `1.7%/3.3%`、`5.6%/7.2%`、
      `7.1%/8.5%`、`12.6%/13.9%`；固定 max5/max6 在 WebView 134 的回退推动最终
      6-total/4-payload 设计。Android/page/Worker 三层边界、五 payload fail-closed +
      clean successor、机器历史与 ignored-report 复跑脚本已纳入默认离线 `check`。
      声明按需注入因 triple-slash lib reference 正确性风险否决；跨 job SourceFile 复用
      因 exact realm teardown 隔离契约延期。完整方法、原始样本及宿主 schema 2/插件
      schema 1 边界见 `docs/maintenance/COLD_START.md`。
      最终签名 v0.6.0（SHA-256
      `bfe3f3e1caba319b50e80e77b323b782bf2e484833c51ea0f4ea27494ec4e380`）已在 API 35
      真机与 API 37 reference AVD 再通过 discovery、mask-15 metadata 和 `hello.ts`
      Program release smoke。API 31/35/37 已恢复该 release，API 36 恢复为未安装插件，
      四台 AndroidTest/release-smoke helper 均已清理。
- [x] **S10-2 内存观察持续化**: S5-5 的 PSS 采样 (compiler +
      renderer combined 168–225 MiB) 转为每次发版前跑一次的
      固定观察脚本; 只记录不设门禁; 明显回归时开 issue。
      验收: 发布 checklist 含内存观察一项, 有历史可比数据。
      结论: `tools/observe-release-memory.ps1` 现复用既有真实 Binder/WebView
      instrumentation 的固定 8 文件 Rhino workload、5 秒 active graph hold 与
      compiler service + 精确关联 renderer PSS；要求显式 `-AllowInstall`，默认只把
      JSON/raw instrumentation/logcat 写入 ignored build report，`-Promote` 才追加受
      版本控制的历史。`docs/maintenance/RELEASE_CHECKLIST.md` 冻结同设备比较、
      `gate=none`、不设阈值及明显回归的 issue 处理规则；S5-5 的 API 31/35/37
      `168054/191542/225047 KiB` 已迁移为首批结构化历史，无需伪造新采样。
      新脚本又在同一 API 37 / WebView `145.0.7632.218` reference emulator 完成
      真实闭环并显式晋升一条 v0.6.0 记录：compiler 3595 ms，service / renderer /
      combined median `82244 / 114584 / 196448 KiB`；AndroidTest 包已清理，结果低于
      同标签既有 combined 225047 KiB，未见内存回归。
- [x] **S10-3 TypeScript 版本升级演练**: 走一次完整 distribution
      lock 升级流程 (当前版本 → 当时最新稳定 6.x): 三摘要核对、
      declarations 重新生成、按声明内容决定 profile fingerprint 是否滚动、缓存自然
      失效验证、smoke 全绿。若当前已经是最新稳定 6.x, 用前一稳定版 → 当前版的隔离
      重放完成同等演练。形成 UPGRADE.md 操作手册。
      验收: 实际候选或隔离重放后的当前编译产物在 reference device 跑通; 手册可被
      他人照做。
      结论: 2026-08-25 官方 npm registry 审计确认稳定 6.x 仅有 6.0.2/6.0.3,
      production 6.0.3 已是最新稳定 6.x；总体 latest 7.0.2 属于不同原生编译器
      架构, 本轮 fail closed 并留待单独架构决策, 因而没有制造无意义的 production
      lock 变更。新增只写 ignored build report 的官方预检脚本、三摘要锁定的隔离
      6.0.2 baseline 和 `verifyTypeScriptUpgradeRehearsal`: compiler distribution
      fingerprint 从
      `e2e90fd9f4ab1a8e2dbd2907f131828a7be3a04c2dd7105badc97d4664a4d5b9`
      滚动为
      `3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658`；
      两版标准库 108 文件/3784758 bytes 完全相同, Rhino/Node profile 指纹据内容
      身份正确保持稳定, 宿主则由 `compilerVersion+compilerDistributionFingerprint`
      自然失效, 其冻结 cache-key 单测通过。`docs/maintenance/UPGRADE.md` 已覆盖完整
      操作、回滚与远端边界；当前签名 v0.6.0 在 API 35 / WebView 130 小米真机与
      API 37 / WebView 145 reference emulator 重装后, metadata 与 `hello.ts`
      Program release smoke 均通过 (`INSTRUMENTATION_CODE: -1`), 两台设备的
      helper 均已清理。结构化审计和防漂移 verifier 已纳入默认离线 `check`。
- [x] **S10-4 依赖健康巡检**: AGP/Kotlin/Gradle 季度升级例行化
      (兼容矩阵已由中央平台插件内建); WebView 最低支持版本策略
      写入文档 (当前已知 Chrome 51/55 旧 provider 语法不兼容,
      Chrome 119+ 验证通过)。
      验收: 一次完整巡检记录 + WebView 支持策略文档化。
      结论: `docs/maintenance/DEPENDENCY_HEALTH.md` 已冻结季度节奏、stable-only
      默认选择、离线仓库快照与需另行授权的上游刷新边界；2026-08-25 首次巡检确认
      Gradle 9.6.1 / AGP 9.3.0 / Kotlin 2.3.21 / KSP 2.3.10 / R8 8.13.19 /
      JDK 21 满足五条内置兼容关系，catalog 七项均精确锁定，本轮无需升级且未联网。
      `docs/maintenance/WEBVIEW_SUPPORT.md` 将 Android API 24 与 provider major 119
      冻结为独立最低线，保留 Chrome 51/55/69 负基线、119/130/145 正矩阵和升降版
      证据规则。机器记录与 policy verifier 已纳入默认离线 `check`，版本或文档漂移
      会要求新增巡检记录。
- [x] **S10-5 issue 分诊模板**: 建立用户反馈 → 复现脚本 → 修复 →
      CHANGELOG 的轻量闭环模板 (延续"修 bug 原则"); 提供收集
      diagnostics.json 与 typescript-compile-perf 日志的指引。
      验收: 模板在仓库本地部署并至少走通一个真实 issue; 远端启用继续等待宿主发布
      与 owner 授权。
      结论: `.github/ISSUE_TEMPLATE/typescript-bug.yml` 已冻结 AutoJs6/plugin/
      Android/WebView/profile/entry/stable-code/minimal-reproduction 等结构化 intake,
      并要求先确认隐私。`tools/collect-typescript-issue-evidence.mjs` 不联网、不调用
      adb, 只在 ignored `build/reports/issue-triage/` 输出证据: formal diagnostics
      删除 request/project identity、URI path 与 message；logcat 仅接受 exact
      `typescript-compile-perf schema=2` / `typescript-compiler-perf schema=1` key
      set。含私有 ID/path/message/noise 的 fixture 实测只留下 numeric diagnostic
      identity 与两条安全性能记录, 夹带 `requestId/sourcePath` 的伪记录被丢弃。
      `docs/maintenance/ISSUE_TRIAGE.md` 已冻结接收、去重、分类、修复、设备/离线验证和
      十语言 changelog/`none-maintenance-only` 决策。真实本地 issue
      `LOCAL-TS-2026-08-25-01` 完整走通了 S10-3 declaration 6.0.3 重复耦合的失败、
      最小复现、修复、回归、真机 smoke 与关闭。模板/采集器/案例的动态隐私 verifier
      已纳入默认离线 `check`；本轮没有创建或上传远端 issue。

## 长期不变约束 (从旧路线继承, 仍然成立)

- TypeScript 版本精确锁定 (当前 6.0.3), 升级走 distribution lock 流程。
- 插件是编译前端, 不执行 JS、不发现/依赖 Node Runtime 插件、不读宿主
  任意路径、Worker 无网络。
- AIDL v1 transaction prefix 冻结, 新能力走 additive Bundle key。
- Source Map 强制产出; 不用生成 JS 冒充 TS 坐标。
- 禁止项目自定义 transformer/compiler plugin/lifecycle script。
- `.tsx`/JSX 不属于 S2 最小闭环；其已完成的类型层与执行链继续按
  S3-A3、S4-6 独立维护，不并入字符串 XML 转换，不使用正则或静默 fallback。
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
