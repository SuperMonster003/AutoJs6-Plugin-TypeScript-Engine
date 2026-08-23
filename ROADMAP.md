# AutoJs6 TypeScript Engine Plugin — Roadmap

> 本路线于 2026-08-22 重写, 替换旧的 R0–R8 证据驱动路线。旧路线及其全部
> 设备/收据证据归档于 [docs/roadmap/ROADMAP_LEGACY_R2_ERA.md](docs/roadmap/ROADMAP_LEGACY_R2_ERA.md)
> 与 docs/roadmap/R2*.md, 仅作历史参考, 不再是工作分母。
>
> 重写原则 (来自项目 owner 的明确要求):
> **先让用户的 TS 脚本跑起来, 出了问题修问题**。不追求一次性完美方案,
> 不以测试与安全边界的堆砌为产品本体, 不把验证正确性当成交付物。
> 每一项的完成标准是"用户可感知的行为", 不是"某个 verify 任务变绿"。

## 现状基线 (2026-08-22 重写, 2026-08-24 更新)

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
| S1 | 插件解禁: 让编译能力真正可用 | (对宿主开发者) capability=true, 真实脚本不再被配额/ASCII/固定配置拦下 | 已完成 (6/6 必需；S1-7 可选) |
| S2 | 宿主最小集成: `.ts` 在 Rhino 上跑起来 | 用户点击运行 `.ts`, 脚本执行 | 已完成 (6/6) |
| S3 | 体验补齐: 诊断、声明、栈映射 | 编译错误/运行错误定位到 TS 源码; AutoJs6 API 有类型 | 核心完成 (A1-A2、B1、C1-C3；原生 TSX A3 后置) |
| S4 | Node profile、项目级能力与原生 TSX | `.mts/.cts`、多文件项目、tsconfig 常用项、TSX UI DSL | 进行中 (S4-1、S4-3、S4-4 已完成；S4-2 主执行路径已迁移, 删除旧实现待办) |
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
      TypeScript source kind; 默认引擎 Rhino; `.d.ts` 不可执行;
      `.tsx` 明确报错。当前路由优先级为显式 Node project > 普通单文件
      TypeScript > Rhino fallback；普通 `.ts` 中尚未支持的 Node directive
      明确失败，不静默改走其他引擎；`"ui"` directive 则进入 Rhino
      Activity，并支持字符串 XML 布局。原生 TSX DSL 仍明确拒绝。
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
      fail-closed 与 TSX 明确拒绝。MediaProjection 在设备初始未授权状态下，
      由无障碍分别自动完成拒绝与允许分支：拒绝时 `requestScreenCapture()`
      确实返回 `false`；允许时选择整屏、捕获真实帧并检查尺寸、像素、回收与
      前台服务退出。宿主提交 `fdbdf8892` 的 12 项套件在 API 31/35/37
      各 12/12、合计 36/36 通过。API 37 上将 Node Runtime 插件置为
      `disabled-user` 后，完整套件仍 12/12 通过，测试结束后已恢复原始
      `default` 状态。经确认，原生 TSX DSL 不作为 S2 验收条件，独立进入
      S3/S4；当前 `.tsx` 继续显式拒绝。

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
- [ ] **S3-A3 原生 TSX DSL 类型层**: 独立定义 JSX namespace、内置元素、
      属性与事件类型，不复用字符串 XML 的宽泛签名。
      验收: 一个包含布局、id 引用和点击处理器的真实 `.tsx` UI 脚本在
      strict 下通过类型检查，错误属性能给出稳定诊断。

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
- [ ] **S4-6 原生 TSX DSL 执行链** (插件 + 宿主): 在受控 profile 中定义
      JSX lowering/runtime 契约，宿主放行 `.tsx` 并将产物送入 Rhino UI
      Activity；该链路独立于 S2 的字符串 XML 实现，不以正则转换或静默
      fallback 冒充支持。
      验收: 一个原生 TSX 布局脚本能渲染、响应点击、更新文本并主动关闭，
      同时保留 Source Map 与编译诊断。

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
- `.tsx`/JSX 不属于 S2 最小闭环；其类型层与执行链分别按 S3-A3、S4-6
  独立推进，在完成前继续显式拒绝。
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
