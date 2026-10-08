# AutoJs6 TypeScript Engine - 执行前耗时压缩线路图

> 状态: L0 未开始. 创建于 2026-10-09, 基于插件 v0.6.8 (commit `27e6100`) 与
> sibling `AutoJs6` 宿主 (commit `7953ab502b`) 的源码调研.
>
> 问题: 插件安装后运行 `.ts` 脚本, 从点击运行到脚本开始执行有数秒可感知停顿.
> 已确认发生在 "首次运行或久未运行后" 与 "修改脚本后再次运行" 两类场景.
>
> 范围: 本仓库 (插件) 与 sibling `AutoJs6` 宿主同步修改. 条目用 `(插件)` /
> `(宿主)` 标注归属; 宿主条目在宿主仓库实施与验证, 结论回填本文件.
>
> 与 [ROADMAP.md](ROADMAP.md) 的关系: T3 (常驻会话, 启动期预热, 模块级增量)
> 与 S10-1 (ingress 批处理) 已完成的工作是本线路图的起点, 不重做. 本线路图只
> 解决 "执行前用户可感知等待" 这一个问题; 完成后在 ROADMAP.md 阶段总览追加一行
> 指向本文件.

## 0. 一页结论

用户感知的停顿不是 TypeScript 编译本身. 历史 schema 2 数据显示单文件脚本的
parse + semantic + emit 四设备合计仅 487-661 ms. 停顿来自编译之前与之外的固定
开销叠加:

1. **声明层每个 job 全量经 WebMessagePort 送进 Worker.** 标准库 108 文件
   3,784,758 B 加 Rhino 层约 1.3 MB, 以 base64 (约 6.7 MB), 64 KiB 分块, 每个
   envelope 一次 ACK 传输; 单文件脚本约 160 个逻辑文件入 Worker. 历史 API 35
   真机 ingress 7.9 s, API 31 5.8 s. 这是最大单项, 冷热路径都付.
2. **compiler 进程冷启动固定成本.** 进程 fork, `onCreate` 在主线程同步读取并
   SHA-256 校验约 5 MB 声明资产, 监督者进程注册, WebView 初始化, Worker 解析
   9,144,216 B 未压缩且无 V8 code cache 的 `typescript.js`. 历史 bind + prewarm
   观察 450-1772 ms, 且 WebView 在首个 probe 到达后才懒创建, 不与任何事并行.
3. **声明层在一个冷 job 内被完整加载并校验三次**, 每次读取做两遍 SHA-256.
4. **宿主流程完全串行.** 等待启动预热 -> 发现 (bind compiler 服务并
   `getCompilerInfo`) -> 构建输入归档 -> 查缓存 -> 专用 bind + prewarm ->
   `startCompilation` -> 读输出 -> 同步写持久缓存. 发现与绑定没有并行, 缓存
   命中之前也要先完成发现.
5. **脚本线程全程阻塞且无进度提示**, 停顿完全暴露给用户.
6. **release 构建存在被掩盖的竞态.** 首个 prewarm / `startCompilation` 要求
   监督者进程已注册, 未就绪立即失败且宿主不重试; 目前靠 `onCreate` 的重活
   "掩护". 提速会暴露它, 必须同步修复.

线路图按 "先测后改, 先零内存代价后结构改动, 常驻策略由数据决定" 排序:
L0 三场景基线 -> L1 快速收益 -> L2 冷路径结构压缩 -> L3 热路径 -> L4 常驻
策略 -> L5 验收与文档.

## 1. 目标与验收口径

2026-10-09 已与 owner 确认采用以下目标 (p50, 参考机 Xiaomi 23046RP50C / API 35,
单文件 Rhino 脚本):

| 场景 | 定义 | 目标 |
| --- | --- | --- |
| S1 全冷 | 插件 compiler 进程不存在, 脚本内容已改动 (持久缓存未命中) | <= 1.5 s |
| S2 热路径 | 进程存活且宿主绑定保留, 修改一行后 30 s 内再次运行 | <= 500 ms |
| S3 缓存命中 | 未修改, 立即重复运行 | <= 300 ms |

- 指标 "用户可感知等待" = 从运行入口 (ScriptExecution 创建) 到 Rhino 引擎
  开始执行生成 JS 的第一条语句. 主口径用宿主 `typescript-compile-perf` 的扩展
  字段 (L0-1), 辅口径用秒表法交叉验证 (L0-7). 定义见第 11 节.
- 对照设备: Sony XQ-AT72 / API 31 与 API 37 x86_64 AVD; WebView 版本随记录.
- 目标是门槛不是上限. 每阶段结束记录同设备前后对比; 未达标场景写明剩余差距
  与归因, 不以 verify 任务变绿代替.

## 2. 现状链路与耗时归因

### 2.1 一次冷运行经过的步骤

| 步 | 位置 | 做什么 | S1 | S2 | S3 |
| --- | --- | --- | :-: | :-: | :-: |
| A | 宿主 | `RunnableScriptExecution.execute` / `ScriptExecuteActivity.prepareTypeScriptAndRun` 调 `TypeScriptExecutionCompiler.prepare`, 脚本线程阻塞直到返回 | x | x | x |
| B | 宿主 | `awaitPendingPrewarm`: 启动预热在跑则等它 | x | | |
| C | 宿主->插件 | 发现: `AidlPluginHost.discover` 对 COMPILER 动作池化 bindService (30 s 空闲解绑) 并 `getCompilerInfo`; 此步触发 compiler 进程启动 | x | x | x |
| D | 插件 | compiler 进程 `onCreate`: 监督者客户端绑定 `:typescript_supervisor`, admission epoch, 孤儿清理, WebView 数据目录, `TypeScriptWebViewCompilerHost` 构造 (主线程加载并校验 Rhino 声明 profile) | x | | |
| E | 插件 | 首次 `getCompilerInfo` 加载 Node 声明 profile 计算指纹 (进程内缓存) | x | | |
| F | 宿主 | 输入归档: 读源文件, SHA-256, 写 ZIP 到 cacheDir | x | x | x |
| G | 宿主 | 持久缓存查找 (命中即返回) | x | x | x |
| H | 宿主->插件 | 专用绑定 + prewarm: 第二条 binding; release 下要求监督者已注册; probe 懒创建 WebView, 加载页面, Worker `importScripts("typescript.js")`, ready | x | 45 ms | |
| I | 插件 | `startCompilation` -> coordinator: 输入 FD 暂存并校验, ZIP 解析, 每 job 重新 `loadForProfile`, 快照合并声明层, 分帧经 WebMessagePort -> page -> Worker; Worker `atob` + `TextDecoder` + `crypto.subtle` 逐文件 SHA-256, 重建 VFS, 校验清单指纹 | x | x | |
| J | 插件 | Worker `createIncrementalProgram` (常驻会话命中时只重建变更 SourceFile), emit, 结果 stop-and-wait 回传, 原生校验并写输出 FD | x | x | |
| K | 宿主 | 读输出 ZIP, 校验, 同步写持久缓存, 构造 `CompiledTypeScriptSource`, 创建 Rhino 引擎 | x | x | 部分 |

### 2.2 已确认的开销来源 (按预期收益排序)

1. **声明层每 job 全量传输.** `R2ProjectArchive.kt:2553` 每 job 调
   `R2TrustedDeclarations.loadForProfile`; `R2FileBackedProjectSnapshot.kt:508`
   只把声明从 caller 配额里剔除但仍留在 retained 记录; Worker
   `compiler-worker.js:10475-10560` 用 `retainedRecords = caller + declarations`
   校验清单指纹, 没有任何跳过或复用路径. 分帧参数见
   [COLD_START.md](docs/maintenance/COLD_START.md): 64 KiB raw chunk, 6/4
   批处理, 576 KiB envelope 上限, window = 1.
2. **compiler 进程冷启动.** `typescript.js` 9,144,216 B 未 minify, 经
   `shouldInterceptRequest` + `Cache-Control: no-store` 提供
   (`CompilerAssetOrigin.kt`), V8 code cache 不可用; WebView 在
   `ensureSessionAndDispatchOnMain` 收到首个 pending 才创建
   (`TypeScriptWebViewCompilerHost.kt:1531`), 与 Binder 绑定, 监督者注册和宿主
   归档准备全部串行.
3. **声明层重复加载与双重哈希.** `TypeScriptWebViewCompilerHost.kt:100` (构造,
   主线程, Rhino profile), `TypeScriptCompilerPluginService.kt:1352` (首次 info,
   Node profile), `R2ProjectArchive.kt:2553` (每 job). `R2TrustedDeclarations.kt`
   的 `loadTypeScriptStandardLibraries` 读取时哈希一次, `validateAndCopyLayerFiles`
   再编码再哈希一次; `R2DeclarationDistribution.kt` 对层文件同样流程. 资产在 APK
   内压缩存储, 每次读取都要 inflate.
4. **宿主串行流程.** `TypeScriptCompilerPluginClient.kt:328-575`: 发现在缓存
   查找之前; bind + prewarm 与归档准备串行; `publishCached` 同步阻塞返回.
   `AidlPluginHost.kt:69-183` 每次 `selectOrThrow` 都重新 `discover`, 没有
   PluginInfo 缓存.
5. **release 构建首个请求对监督者注册的硬门控.**
   `TypeScriptCompilerPluginService.kt:185-200` 与 `412-428`: 未就绪立即返回
   `ERROR_COMPILER_UNAVAILABLE`; 宿主 `retryOnRemoteException = false` 且该错误
   不是 RemoteException, 不重试. 注册链路见
   `R2CompilerProcessSupervisorControl.kt:852-880, 1031-1110`.
6. **启动预热触发与保活.** `App.kt:441-444`: 应用启动 3.6 s 后预热, 无用户
   信号 60 s 后释放; `EditActivity.kt:241` 打开编辑器预热. 从文件列表直接运行
   `.ts`, 应用启动 3.6 s 内运行, 或闲置超过 60 s 后运行均为全冷.
7. **无进度提示.** `ScriptExecuteActivity.kt:152-170`,
   `RunnableScriptExecution.java:33-45`.

### 2.3 历史观测 (全部需在 L0 用当前版本复测)

| 来源 | 设备 | 数据 |
| --- | --- | --- |
| S5-5 (2026-08-24, max4 批处理后) 单文件 Rhino total / compiler / ingress | API 31 | 9215 / 7105 / 5785 ms |
| 同上 | API 33 | 2537 / 1471 / 802 ms |
| 同上 | API 35 Xiaomi | 11683 / 9310 / 7919 ms |
| 同上 | API 37 AVD | 7145 / 3490 / 1836 ms |
| S5-5 cache hit (仅查找耗时, 不含发现) | 四设备 | 18-63 ms |
| S10-1 (2026-08-25) 109 文件 3 MiB workload compiler / ingress | API 35 Xiaomi | 6671 / 5434 ms |
| T3-2 (2026-08-28) 启动预热 / 预热后首次 bind+prewarm | API 35 Xiaomi | 1626 / 45 ms |
| T3-3 (2026-08-28) 10 文件 core compiler cold -> warm | API 35 Xiaomi | 539 -> 156 ms |
| S5-5 8 文件项目活动态 PSS compiler service / renderer | API 35 Xiaomi | 90,458 / 95,241 KiB |

读法: ingress 是 compiler 耗时的主体, 其次是 bind + prewarm; Program 本身不是
瓶颈. 原始样本见 [cold-start-observations.json](docs/maintenance/cold-start-observations.json),
[memory-observations.json](docs/maintenance/memory-observations.json) 与
[S 时代归档](docs/roadmap/ROADMAP_LEGACY_S_ERA.md).

## 3. 阶段总览

| 阶段 | 主题 | 归属 | 预期收益 | 依赖 | 状态 |
| --- | --- | --- | --- | --- | --- |
| L0 | 测量基线与归因 | 插件 + 宿主 | 三场景可复现数据, 决定 L1/L2 顺序 | 无 | 未开始 |
| L1 | 零内存代价的快速收益 | 插件 + 宿主 | 去掉重复加载, 串行等待, 竞态; 进度提示 | L0 | 未开始 |
| L2 | 冷路径结构压缩 | 插件 | 声明层不再经传输; typescript.js 解析耗时下降 | L0, L1-1 | 未开始 |
| L3 | 热路径 (改动后重编) | 插件 + 宿主 | S2 达标 | L1, L2-1 | 未开始 |
| L4 | 常驻策略 | 插件 + 宿主 | 以数据决定保活默认值 | L1, L2 | 未开始 |
| L5 | 验收, 门禁与文档 | 插件 + 宿主 | 三设备前后对比, 回归门禁, 发布 | L1-L4 | 未开始 |

依赖关系: L0 先行; L1 各条目互相独立可并行, 但 L1-4 若 L0-6 复现竞态则提到
L1 首位; L2-1 依赖 L1-1 (进程级声明缓存是预装 bundle 的校验基础); L3 在 L2-1
落地后测; L4 在 L1/L2 完成后用新基线做实验; L5 收尾.

## 4. L0 测量基线与归因

目标: 用当前版本在参考机上建立 S1/S2/S3 基线, 把第 2 节的归因从 "源码推断"
变成 "同设备毫秒数", 并复核 release 竞态. 本阶段不改变任何行为.

- [ ] **L0-1 (宿主) perf schema 3 打点扩展**: `TypeScriptCompilationPerformance`
      增加 `discoveryMs` (selectOrThrow), `awaitPrewarmMs`, `cachePublishMs`,
      `engineCreateMs` (Rhino 引擎创建含 Source Map 绑定), `userWaitMs`
      (入口到引擎 ready), `coldProcess` (发现前 compiler 进程是否存在, 由
      `getCompilerInfo` 附带的进程 uptime 或宿主 `pidof` 判定). 全部 additive,
      旧字段与顺序不变, `schema=3`.
      验收: `toLogLine` 单测覆盖新字段; cache hit 时 Binder/compiler 字段仍为
      `na`; 日志不含源码, 路径, requestId.
- [ ] **L0-2 (插件) 启动期打点**: service 输出一条
      `typescript-compiler-boot schema=1` (tag `TypeScriptCompiler`): onCreate
      总耗时, 声明加载耗时, 监督者注册就绪耗时, WebView 创建 -> page ready ->
      worker ready 各段; Worker 的 `ready` 消息附带 `bootstrapMs`
      (`importScripts` 前后 `performance.now` 差). DEBUG 与 release 都输出.
      验收: 一次冷启动恰好一条; 字段非负; 不含敏感信息; 桌面 Worker 门禁
      (`verify-r2-project-compiler.js`) 接受带 `bootstrapMs` 的 ready 消息.
- [ ] **L0-3 三场景采集脚本**: 新增 `tools/observe-startup-latency.ps1`
      (参数 `-Serial`, `-DeviceLabel`, `-Runs` 奇数, `-HostPackage` 默认
      `org.autojs.autojs6`, `-Scenario S1|S2|S3|all`). S1 前执行
      `adb shell am kill io.github.supermonster003.autojs6.plugin.typescript`
      并用 `pidof` 确认 `:typescript_compiler` 不存在, 然后改动 fixture 一行;
      S2 改动一行后立即运行; S3 不改动. 运行入口优先用宿主已有外部运行方式,
      没有则提示人工点击并等待日志. 收集 `typescript-compile-perf`,
      `typescript-prewarm`, `typescript-compiler-boot` 行, 写
      `build/reports/startup-latency/<label>-<timestamp>.json` (ignored) 并打印
      中位数表.
      验收: 在 Xiaomi API 35 上完成一轮 5 次 x 3 场景; 脚本幂等, 结束后恢复
      fixture 内容.
- [ ] **L0-4 fixture 与记录文件**: fixture 放 `tools/fixtures/startup-latency/single-file.ts`
      (单文件 `.ts` 约 60 行, 覆盖 `console`, `app`, `device`, `files` 等典型
      API 与一个类型注解, 首行打印 `Date.now()`); 记录文件
      `docs/maintenance/startup-latency-observations.json` (schemaVersion 1:
      fixture, device, webView, buildType, scenario, samples, medians,
      provenance, `gate: "none"`), 并在
      [COLD_START.md](docs/maintenance/COLD_START.md) 增加 "执行前耗时基线" 小节
      链接本文件.
      验收: Xiaomi API 35 + 至少一台对照设备的基线入库; 增加与 S10 同款的
      防漂移 verify (只校验 schema 与引用, 不设阈值).
- [ ] **L0-5 归因表回填与排序确认**: 用 L0-4 数据把 2.1 表各步填上实际毫秒
      (至少 C, D+E, H, I 的 ingress, J, K). 若数据与第 2.2 节排序不符, 调整
      L1/L2 条目顺序并在第 12 节记录理由.
- [ ] **L0-6 release 竞态核查**: 用签名 release 构建做 20 次 S1, 统计
      `Compiler process supervisor registration is not ready` 出现次数与首次
      运行失败次数.
      验收: 数字入库; 出现 > 0 即把 L1-4 提到 L1 首位.
- [ ] **L0-7 秒表法交叉验证**: fixture 首行时间戳与宿主运行入口日志时间差,
      与 `userWaitMs` 偏差 < 10%; 超出则修正打点位置后重测.

## 5. L1 零内存代价的快速收益

目标: 不增加常驻内存, 不改传输协议, 去掉重复工作与串行等待, 修掉竞态, 给
用户进度反馈. 每条独立可回退.

- [ ] **L1-1 (插件) 声明层进程级单次加载**: `R2TrustedDeclarations` 增加按
      profile 的进程内缓存 (线程安全, 失败不缓存, 任何校验失败仍抛出),
      `loadForProfile` 复用; `TypeScriptWebViewCompilerHost` 构造, service info
      指纹, `R2ProjectArchiveLoader` 三处共享同一实例. 去掉对 loader 已校验
      字节的二次编码与哈希: `validateAndCopyLayerFiles` 对来自 loader 的输入只
      做一次完整校验, 对测试构造的输入保持现有校验.
      验收: 每 job 声明加载耗时 (L0-2 / L0-1 字段) 接近 0; 首次加载耗时下降
      >= 40%; `check --offline` 内 distribution / declaration layering 门禁
      全绿; 指纹值与改动前逐字节相同.
      回退: 常量关闭缓存即回到逐次加载.
- [ ] **L1-2 (插件) onCreate 关键路径精简**: 声明校验与
      `R2PrivateTransientOrphanScavenger` 移到后台线程, 首个 job / prewarm 前
      join; 主线程只做 `prepareCompilerProcess`, host 对象构造与 coordinator
      装配. 失败语义不变: 任一后台校验失败仍 fail closed 到首个请求, 并保持
      `R2ServiceShutdownCleanupUnprovenException` 路径.
      验收: onCreate 主线程耗时 < 50 ms (不含 WebView); 孤儿清理与 supervisor
      instrumentation 全绿.
- [ ] **L1-3 (插件) WebView 会话提前创建**: onCreate 末尾 post
      `createSessionOnMain` (无 pending 时也创建), 让 WebView 初始化 +
      `typescript.js` 解析与 Binder 绑定, 监督者注册, 宿主归档准备并行; prewarm
      到达时若 worker 已 ready 直接返回. generation / teardown / renderer-gone
      语义不变; 无请求时的释放时机在 L4 决定, L4 前沿用现状 (保持到
      `onTrimMemory` 或 close).
      验收: `bindPrewarmMs` p50 下降 >= 该设备 WebView 启动段耗时的 70%;
      R1 lifecycle, process-restart, renderer-gone, service-destruction
      instrumentation 全绿.
- [ ] **L1-4 (插件) 监督者就绪的有界等待**: release 下 prewarm /
      `startCompilation` 等待注册就绪最多 3000 ms (常量, 可调) 后才返回
      unavailable; 监督者绑定提前到进程最早时机 (`Application.onCreate` 或
      service 类加载). 不改 DEBUG 行为与现有 fault seam.
      验收: L0-6 复测 20 次 S1 零失败; supervisor 全部 instrumentation 全绿;
      等待耗时进入 L0-2 打点.
- [ ] **L1-5 (宿主) 发现结果缓存**: `AidlPluginHost` 按
      `packageName + versionCode + lastUpdateTime` 缓存 `Discovered`
      (serviceInfo + pluginInfo); 监听 `PACKAGE_ADDED / REPLACED / REMOVED /
      CHANGED` 失效; Binder 死亡只失效连接不失效信息; 插件中心提供显式刷新.
      缓存对所有 AIDL 插件宿主生效, 但本线路图只验收 TypeScript 路径.
      验收: S2 / S3 的 `discoveryMs` p50 < 5 ms; 插件升级后首次运行使用新指纹
      (单测 + 真机); 插件被禁用/卸载后选择失败信息与现状一致.
- [ ] **L1-6 (宿主) 缓存查找前移并与绑定并行**: 用缓存的 authority 先算
      cacheKey 查持久缓存, 命中则不绑定; 未命中时 bind + prewarm 与 `prepare`
      用 `async` 并行, `startCompilation` 前 join; authority 与广告值不同时走
      现有重新 prepare 路径.
      验收: S3 日志不出现 `bindPrewarmMs`; S1 `totalMs` 约等于
      `max(prepareMs, bindPrewarmMs) + compilerMs + outputMs`.
- [ ] **L1-7 (宿主) 持久缓存写入移出关键路径**: `publishCached` 在输出返回后
      异步执行 (mutex 之外, 失败只记日志, 不影响本次执行).
      验收: `outputMs` 不含写缓存; 连续 S3 仍 100% 命中.
- [ ] **L1-8 (宿主) 用户可感知进度**: 运行入口 200 ms 内给出非阻塞提示
      (控制台 / 日志面板一行 "正在编译 TypeScript" + `ScriptExecuteActivity`
      轻量加载态), 用现有 R2 progress 回调阶段 (ingress / parse / semantic /
      emit) 更新, 完成或失败即清除; JS 脚本无任何变化; 十语言字符串按仓库
      规范.
      验收: 真机录屏; 回调到 UI 状态映射有单测; 无新的主线程阻塞.

## 6. L2 冷路径结构压缩

目标: 让单文件脚本的 ingress 从约 160 文件降到个位数文件, 并压缩
`typescript.js` 解析耗时. 只改内部协议与构建产物, 不改公开 AIDL / archive
schema / caller 配额.

- [ ] **L2-1 (插件) 声明层预装入 Worker (主方案)**:
      - 构建期: 新任务 `GenerateR2DeclarationWorkerBundles` 从已校验的
        distribution / declaration 目录生成 `declarations-common.js`,
        `declarations-rhino.js`, `declarations-node.js`
        (`self.__autojs6DeclarationLayers.register(layer, { fingerprint,
        files })`); bundle 指纹写入 distribution manifest;
        `VerifyR2DeclarationDistribution` 同时校验 debug / release APK 内
        bundle 实际字节.
      - 资产: `CompilerAssetOrigin` 允许列表与 `verifyR1CompilerHost`
        allowlist 增加三个路径; CSP 不变 (`script-src 'self'` 已覆盖
        `importScripts`).
      - Worker: `projectInputStart` 携带的
        `declarationProfileFingerprint` / `declarationInventoryFingerprint`
        对应的层未装载时 `importScripts` 装载, 逐文件 SHA-256 一次并与清单
        比对 (任一不符 -> `projectError`, 不回退到传输); 装载结果按指纹缓存
        于 Worker 生命周期; `retainedRecords = caller + 预装声明`, 现有指纹,
        计数, 配额校验保持.
      - 原生: `R2ProjectInputFramer` 跳过 `/declarations/` 的
        start / chunk / end 帧 (仍计入 `retainedInventoryFingerprint`,
        `declarationFileCount`, `declarationByteLength`); start 帧增加
        `declarationTransport = "preloaded"`; 缺省视为 legacy 全量传输, 桌面
        fixture 与旧回放继续可用.
      - 不变: 公开 AIDL, input / output archive schema, caller 配额,
        source map, 诊断 schema; triple-slash `lib` reference 仍可选择任意
        标准库 (所有 lib 都在 Worker 可得, 比传输方案更完整).
      验收: 单文件脚本 ingress 逻辑帧从约 480 降到 < 12; Xiaomi S1 的
      `compilerIngressMs` p50 下降 >= 80%; `verify-r2-project-compiler.js`,
      declaration layering, Rhino / NodeNext smoke,
      `R2ProjectCompilerInstrumentationTest` 全绿; 含
      `/// <reference lib="dom" />` 的用例输出与改动前一致; legacy 全量传输
      路径保留一个 verify 用例.
      回退: 去掉 start 字段即回到全量传输.
- [ ] **L2-1b (插件, 仅当 L2-1 被否决时) Worker 级声明缓存**: 新增
      `projectInputDeclarationReference` 帧, Worker 持有上一 job 的声明层
      (按指纹) 时跳过传输; 首个冷 job 仍全量. 只改善热路径, 不满足 S1 目标.
- [ ] **L2-2 (插件) typescript.js 解析耗时压缩**: 以 L0-2 `bootstrapMs` 定量
      后二选一. 方案 A: 构建期从锁定 tarball 生成 minified 产物 (生成器与
      esbuild 版本锁定, 产物指纹写入 `distribution.json`,
      `verifyTypeScriptDistribution` 同时校验源 tarball 与产物; `runtimeEntry`
      指向产物; `ts.version` 不变; UPGRADE.md 增加生成步骤). 方案 B: 保持
      原文件, 核实 WebView 对 `importScripts` 的 lazy compile 行为, 只在
      确认无收益时放弃.
      验收: Worker ready p50 下降 >= 30% (Xiaomi); `THIRD_PARTY_NOTICES.md`
      与 license 条目不变; `check --offline` 全绿; 升级演练 (UPGRADE.md) 可照做.
- [ ] **L2-3 (插件) 输入传输剩余开销 (条件触发)**: L2-1 后只剩 caller 文件;
      若 10 文件项目 ingress 仍 > 150 ms, 评估 androidx.webkit
      `WebMessageCompat` ArrayBuffer (`WEB_MESSAGE_ARRAY_BUFFER`, 不可用时回退
      base64) 与 Worker 侧哈希合并.
- [ ] **L2-4 (插件) 结果回传 (只记录)**: 单文件的 stop-and-wait 结果分帧预计
      不是瓶颈; L0 数据中 egress > 100 ms 再立项.
- [ ] **L2-5 (插件) 资产存储方式 (数据决定)**: 评估
      `androidResources.noCompress` 对 `.js` / `.d.ts` 的影响 (APK 体积 vs
      inflate 时间); 若采用, changelog hint 说明体积变化.

## 7. L3 热路径 (改动后重编)

目标: S2 <= 500 ms. L1 / L2-1 落地后再测, 本阶段多为核查与小修.

- [ ] **L3-1 (插件 + 宿主) 单文件常驻会话命中核查**: S2 场景确认 Worker
      resident session hit (telemetry / DEBUG 日志); miss 时沿
      `projectId -> compilerAuthority -> 宿主绑定保留` 链路定位并修复.
      验收: 10 次编辑 - 重跑 100% hit; `compilerParseMs + compilerSemanticMs`
      相对 S1 下降 >= 70% (与 T3-3 口径一致).
- [ ] **L3-2 (宿主) 输出读取与引擎创建**: `outputMs + engineCreateMs` 单文件
      p50 < 80 ms; 超出时优化输出 ZIP 读取 (STORED 直读) 与 Source Map 绑定解析.
- [ ] **L3-3 (插件) 每 job 固定原生开销**: 输入暂存复制与两次 SHA-256
      (`R2PrivateInputStaging`), ZIP 校验, 输出暂存与 commit; 打点后若合计
      > 100 ms, 合并重复哈希但保留至少一次完整校验.
      验收: 单文件 job 的 Worker 外原生开销 < 100 ms.
- [ ] **L3-4 (宿主) 绑定复用核查**: L1-6 并行化后, 真实编译仍复用启动预热 /
      上次编译保留的 live binding, 不再建第二条 dedicated binding.
      验收: S2 日志 `bindPrewarmMs` <= 50 ms.

## 8. L4 常驻策略 (数据驱动)

目标: 用新基线下的实验数据决定保活默认值; 在此之前不延长任何保活.

- [ ] **L4-1 内存观察扩展**: `tools/observe-release-memory.ps1` 增加 "空预热
      idle" 与 "一次编译后 idle 5 min" 采样点, 三进程 (compiler service,
      renderer, supervisor) PSS 入 `memory-observations.json`.
- [ ] **L4-2 (插件) 半常驻方案实验**: WebView / renderer 空闲 N 秒后销毁但
      service 进程保留 (声明已加载, 监督者已注册, 下次只付 WebView + Worker
      启动); 对比 "全保留", "半常驻", "全释放" 三种策略的 S1 耗时与 PSS.
- [ ] **L4-3 (宿主 + 插件) 保活默认值决策**: 以 L4-1 / L4-2 决定启动预热保活
      时长 (当前 60 s), 真实编译后的保活是否随前后台变化, `onTrimMemory`
      等级映射; 写入
      [RESIDENT_PROJECT_SESSIONS_T3.md](docs/architecture/RESIDENT_PROJECT_SESSIONS_T3.md)
      决策小节与本文件第 12 节.
- [ ] **L4-4 (宿主) 更多预热触发信号**: 最近运行列表含 `.ts` 时应用启动立即
      预热 (跳过 3.6 s 延迟); 文件 / 脚本列表中 `.ts` 条目被长按或进入其所在
      目录时预热; 定时任务 / Tasker 触发 TS 脚本前按计划提前预热.
      验收: 应用冷启动后立即运行最近的 `.ts`, 等待达到 S2 目标.

## 9. L5 验收, 门禁与文档

- [ ] **L5-1 三场景回归门禁**: `observe-startup-latency.ps1` 纳入
      [RELEASE_CHECKLIST.md](docs/maintenance/RELEASE_CHECKLIST.md) 第 4 节
      (与 `observe-cold-start.ps1` 并列); observations JSON 的防漂移 verify
      纳入默认 `check` (只校验 schema 与引用, `gate=none`, 与 S10 一致).
- [ ] **L5-2 文档更新**: COLD_START.md 增加 "L2 后的 ingress 组成" 并标注被
      取代的旧结论; RESIDENT_PROJECT_SESSIONS_T3.md 补 L4 决策;
      README-DEVELOPMENT.md 已知边界 (声明层预装, 常驻策略); `docs/user/`
      补 "首次运行较慢" 的说明; ROADMAP.md 阶段总览追加一行指向本文件.
- [ ] **L5-3 changelog**: 每个用户可见变化 (更快的首次运行, 进度提示, 内存
      策略, APK 体积变化) 进入 `.changelog` 十语言 JSON (`improvement` /
      `hint`), 运行 `py .python/generate_markdown.py` 与 `--check`.
- [ ] **L5-4 真机验收矩阵**: Xiaomi API 35 + Sony API 31 + AVD API 37 三场景
      前后对比写入 observations JSON; 未达标场景在第 12 节记录差距与原因.
- [ ] **L5-5 发布**: `VERSION_NAME` minor 升级, 十语言 changelog 版本 key
      同步, 签名 release 与 `appendDigestToReleasedFiles`, 宿主配套版本写入
      changelog hint; 对外分发仍按 owner 决定.

## 10. 架构不变量与明确不做的事

- 公开 AIDL v1 冻结; 新能力只走 additive Bundle key 与内部帧字段; 旧宿主 +
  新插件, 新宿主 + 旧插件两种组合保持可用 (L2-1 的 legacy 全量传输路径保留).
- 正确性 fail-closed 不降级: 编译器与声明资产完整性校验保持, 只允许 "每进程
  校验一次" 与 "校验位置移动", 不允许跳过; 指纹不匹配永远失败, 不回退.
- Worker 无网络, CSP 不放宽; 新增资产只走现有 allowlist 并纳入
  `verifyR1CompilerHost`.
- 不恢复正则 stripping; 不引入 typescript-go (T3-4 no-go 条件未变); 不改
  TypeScript 版本 (6.0.3).
- 不为性能牺牲源码快照一致性, Source Map 真实性或输出完整性校验.
- 常驻内存默认值只能由 L4 数据驱动改变, L1 / L2 不顺手延长保活.
- 不在本线路图内做编辑器 (Ace) 侧的提速; 编辑器预热入口保持现状.

## 11. 测量与记录规范

- 日志: 宿主 tag `TypeScriptCompiler`, 行 `typescript-compile-perf schema=3`,
  `typescript-prewarm schema=1`; 插件 tag `TypeScriptCompiler`, 行
  `typescript-compiler-boot schema=1`. 采集命令:

  ```powershell
  adb -s <serial> logcat -c
  adb -s <serial> logcat -v time -s TypeScriptCompiler:I
  ```

- 场景操作:

  ```powershell
  # S1: 杀掉插件后台进程并确认 compiler 进程不存在, 再改动 fixture 一行
  adb -s <serial> shell am kill io.github.supermonster003.autojs6.plugin.typescript
  adb -s <serial> shell pidof io.github.supermonster003.autojs6.plugin.typescript:typescript_compiler
  # S2: 上一次运行完成后 30 s 内改动一行再运行
  # S3: 不改动, 立即再次运行
  ```

  `am force-stop` 会把插件置为 stopped 状态并触发 OEM 激活路径, 不用于 S1.
  宿主侧若持有预热绑定, `am kill` 不会杀掉 compiler 进程; S1 需先让宿主释放
  (闲置 > 60 s, 或在 L4 前用 DEBUG 入口释放) 并以 `pidof` 为准.
- 样本: 每场景奇数次 (默认 5), 取中位数, 记录全部原始样本; 比较时保持 device
  label, API, WebView 完整版本, fixture, build type 一致. 不设 p95 门禁; 同
  设备中位数相对上一记录劣化 > 15% 视为回归, 先保存原始报告复跑再决定.
- 记录文件: `docs/maintenance/startup-latency-observations.json`, 每条记录含
  `id`, `observedAt`, `releaseVersion`, `hostVersion`, `buildType`, `device`,
  `webViewVersion`, `scenario`, `samples` (每次运行的 schema 3 字段),
  `medians`, `provenance`. 历史记录只追加不改写.
- "用户可感知等待" 的两个口径: 主口径 `userWaitMs` (L0-1); 辅口径 fixture
  首行 `Date.now()` 与宿主入口日志的时间差 (L0-7).

## 12. 决策记录

| 日期 | 决策 | 结论 |
| --- | --- | --- |
| 2026-10-09 | 现象范围 | 首次运行 / 久未运行后慢, 修改脚本后运行慢; 缓存命中路径不在抱怨内, 但 L1-5 / L1-6 顺带覆盖 |
| 2026-10-09 | 改动范围 | 插件 + 宿主同步修改, 条目分别标注归属 |
| 2026-10-09 | 常驻策略 | 由数据决定 (L4), 之前不延长保活 |
| 2026-10-09 | 验收目标 | S1 <= 1.5 s, S2 <= 500 ms, S3 <= 300 ms (p50, Xiaomi API 35, 单文件 Rhino) |
| 待定 | L2-2 方案 A / B | L0-2 `bootstrapMs` 出来后决定 |
| 待定 | L4-3 保活默认值 | L4-1 / L4-2 数据出来后决定 |
| 待定 | L2-5 资产 noCompress | L0 数据与 APK 体积对比后决定 |
