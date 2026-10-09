# L2-1 声明层预装入 Worker

> 状态: 2026-10-09 实现, 桌面门禁与 JVM 单测通过; 设备验收数据见文末与
> [startup-latency-roadmap.md](../../startup-latency-roadmap.md) L2-1. 同日 L2-7 把 bundle 的
> 字节校验挪到原生资产入口, 把 token 计数锁定在构建期, 见文末 "L2-7 校验位置移动" 小节.

## 问题

L0 基线 (Xiaomi 23046RP50C / API 35) 显示单文件 Rhino 脚本的每次编译都要把插件自有的声明层
(TypeScript 标准库 108 文件 3,784,758 B + Rhino 层 53 文件 992,257 B) 以 base64 分帧经
WebMessagePort 送进 Worker: `compilerIngressMs` 冷 2.9 s, 热 2.6 s, 是执行前等待的最大单项;
冷 Program 对这些声明的 parse 再占 1.4 s. 这些字节在每个插件版本内是不变的, 没有理由逐 job
传输.

## 方案

1. **构建期 bundle.** `GenerateR2DeclarationWorkerBundles` (build-logic
   `R2DeclarationWorkerBundles`) 从已校验的声明分发 (`assets/r2-declarations/v2`) 与 TypeScript
   分发 (`assets/typescript/<version>`) 生成三个同源脚本:
   `declarations-common.js` (标准库), `declarations-rhino.js`, `declarations-node.js`, 以及
   `bundles.json` (每个 bundle 的 SHA-256, 层指纹, profile 指纹与 token 总数). 每条记录保留 VFS
   路径, UTF-8 字节数与 SHA-256, 与原生侧 `declarationInventoryFingerprint` 使用的三元组完全
   一致, 另带 L2-7 起由 `r2-declaration-token-counts.lock.json` 锁定的 `tokenCount`.
   输出确定; `VerifyR2DeclarationWorkerBundles` 重新生成并逐字节比对, 已进默认 `check`.
2. **资产 allowlist.** `CompilerAssetOrigin` 新增三条映射 (`/typescript-compiler-r1/declarations-*.js`
   -> `r2-declaration-worker-bundles/declarations-*.js`), CSP 不变 (`script-src 'self'` 已覆盖
   `importScripts`). R1 源码策略门禁与逃逸策略门禁把 Worker 允许的 `importScripts` 字面量集合
   固定为 `typescript.js` 加这三个文件.
3. **Worker 预装与校验.** `loadDeclarationLayer(layer)` 以字面量 `importScripts` 装载 bundle,
   校验 bundle 与每条记录的形状 (路径前缀与后缀, SHA-256 格式, 字节数与 token 数为安全整数且
   token 数不超过 `maximumTokensPerFile`, 无重复路径, 字节与 token 总数与注册值一致), 结果按层
   缓存于 Worker 生命周期; 失败不缓存. L2-1 首版还在这里对每条记录重编码 UTF-8, 重算 SHA-256 并
   扫描 token, L2-7 起这三步分别由原生资产入口与构建期锁承担 (见文末). Worker 在发布 `ready`
   后异步预热: 先装载 common + rhino 层, 再按
   `lib.es2018.d.ts` 的 `/// <reference lib>` 链与 Rhino 层预创建 `SourceFile`
   (`version = sha256`). L2-7 起每个预热步按 12 ms 时间片解析若干文件后 `setTimeout(0)` 让出
   消息循环 (嵌套定时器被钳到 4 ms, 逐文件让出会把一半窗口花在等待上), 遇到活动 job 即以 50 ms
   轮询让路; 先到的 job 复用已完成的部分, 其余自己解析. Worker 生命周期内首个 `projectInputStart`
   以 `declarationWarmUp` 消息上报一次预热状态 (bundle 的 import / 校验 / 摘要 / token 计数耗时,
   已解析文件数与字节数, 剩余队列, 首个项目相对 `ready` 的到达时刻), 原生侧仅记一行
   `typescript-compiler-warmup schema=1`; 没有 bundle 的桌面 shim 不会产生该消息.
4. **输入端合并.** `projectInputEnd` 时若 `state.files` 中没有任何 `/declarations/` 记录, Worker
   取 common 层加上第一个使合并清单指纹等于 start 帧 `declarationInventoryFingerprint` 的引擎层;
   同时校验文件数, 字节总数, 路径预算, 大小写折叠碰撞与 token 预算, 并把记录插入 `state.files`
   头部 (与历史传输顺序一致). 任何不匹配都 `projectError`, 不回退到传输. 仍收到声明帧的请求
   (桌面 fixture, 旧回放) 走原路径不变.
5. **共享声明 SourceFile.** `createProjectCompilerHost.getSourceFile` 对来自预装层 (路径 + 摘要
   命中 `preloadedDeclarationDigests`) 的 `/declarations/` 文件使用 Worker 级
   `sharedDeclarationSourceFiles` (键: 路径 + 脚本目标 + implied module format, 命中时校验
   `version`), 冷 job, 预热与不同常驻会话之间复用同一解析结果; 由调用方分帧的声明仍只用 per-job
   cache, 保留原有 retained-heap 边界. Node revision 2 项目不参与共享. 键必须从
   `getSourceFile` 收到的 `CreateSourceFileOptions` 对象里取字段: TypeScript 5+ 的 Program 传的是
   选项对象而不是 `ScriptTarget` 数字, L2-1 首版把对象直接拼进模板串得到 `[object Object]`, 预热
   用数字目标写入的条目因此永远不命中 (L2-7 修正, 桌面门禁现在断言首个 Program 不再重解析任何
   预热文件).
6. **原生分帧器.** `R2ProjectInputFramer` 新增 `declarationTransport` 参数:
   `FRAMED` (默认, 历史行为, in-memory 快照与全部既有 fixture 继续使用) 与 `PRELOADED`
   (协调器的生产路径). `PRELOADED` 下 start/end 帧的 `fileCount`, `totalBytes`,
   `retainedFileCount`, 声明计数/字节/指纹全部不变, 只是不再发出 `/declarations/` 文件的
   start/chunk/end 帧; 遥测 `totalPayloadBytes` 只计实际传输字节.

## 不变量

- 公开 AIDL, input/output archive schema, 配额修订与指纹算法全部不变; 旧宿主 + 新插件, 新宿主 +
  旧插件均可用 (预装是插件内部的 host <-> Worker 协议, 同一 APK 内成对发布).
- 完整性: bundle 来源于已校验分发, Worker 逐文件重算摘要, 合并前再比对原生广告的清单指纹;
  三处任一不符即失败, 不存在 "用 Worker 自己的声明替代宿主承认的声明" 的路径.
- Worker 仍无网络, CSP 不放宽; `fetch`/`XMLHttpRequest` 等禁用全局不变.
- 所有标准库仍在 Worker 可得, triple-slash `lib` reference 行为不变 (比 "按需注入" 更保守).

## 门禁

- `verifyR2DeclarationWorkerBundles`: bundle 重生成逐字节一致.
- `verifyL2DeclarationPreloadDesktop` (`verify-l2-declaration-preload.js`): 用生成的 bundle 启动
  Worker; 单源文件请求只分帧 1 个文件即编译成功, 产物含 Rhino 声明类型化的源码; 与全量分帧
  传输的产物逐字节一致; 篡改 `declarationInventoryFingerprint` 被拒绝; 第二次编译复用共享
  SourceFile. 已进默认 `check`.
- `R2ProjectFramedTransportTest.preloadedDeclarationTransportKeepsInventoryAuthorityButFramesOnlyCallerFiles`:
  PRELOADED 下 start/end 帧计数与字节不变, 只有 caller 文件被分帧, 遥测只计传输字节; FRAMED 不变.
- `R2ProjectCompilerInstrumentationTest.preloadedDeclarationsSkipFramingAndMatchFramedOutput`
  (设备): 同一快照 FRAMED 与 PRELOADED 的输出相同, PRELOADED 只有 5 个逻辑帧.
- 既有桌面门禁 (`verifyR2ProjectCompilerDesktop`, `verifyCompilerSmokeRhino`,
  `verifyT3ResidentSessions`, 声明分层) 与 R1 源码策略门禁继续通过; 其中源码策略门禁与逃逸
  门禁各修正一条自 T3-3 起已过期的 `createIncrementalProgram` 形状断言.

## 设备验收

Xiaomi 23046RP50C / API 35 / WebView 130, release 宿主 + 插件, 单文件 Rhino, 每场景 5 次 p50
(记录 `20261009-l2-api35-23046rp50c-preload`, 插件 `db3429c`, 宿主 `97e4751951`; 全部字段见
[startup-latency-observations.json](../maintenance/startup-latency-observations.json)):

| 口径 | L1 之后 | L2-1 之后 |
| --- | ---: | ---: |
| S1 `compilerIngressMs` | 2924 | 159 |
| S1 `compilerParseMs` | 1431 | 1611 |
| S1 `userWaitMs` | 7543 | 5276 |
| S2 `compilerIngressMs` | 2587 | 16 |
| S2 `compilerParseMs` | 28 | 23 |
| S2 `userWaitMs` | 3555 | 907 |
| S3 `userWaitMs` | 106 | 108 |

- 单文件请求只分帧 1 个 caller 文件 (5 个逻辑帧), 两个场景的声明层传输都降到两位数毫秒.

## L2-7 校验位置移动 (2026-10-09)

L2-7 的打点 (线路图记录 `20261009-l2-api35-23046rp50c-warmup-probe`) 显示 Xiaomi 上首个项目在
Worker ready 后约 1.0 s 到达, 而 common bundle 的 `importScripts` 约 155 ms 之后, Worker 内逐
记录的 UTF-8 重编码 + SHA-256 约 330 ms 与 token 扫描约 480 ms 还没有做完, 预热一个文件都没
解析. 这两步对插件自有, 构建期锁定的声明层没有运行时信息量, 于是按线路图第 10 节 "只允许每进程
校验一次与校验位置移动" 的口径移动位置:

1. **原生资产入口校验 bundle 字节.** `R2DeclarationWorkerBundleAuthority` 在进程内读取一次
   `bundles.json` (严格 JSON, 精确键集), 把它的标准库指纹, 各层 `layerFingerprint`,
   `profileFingerprint`, 文件数与字节总数逐项和已经逐字节校验过的 `R2TrustedDeclarations`
   (common, Rhino profile, Node profile) 比对, 任一不符即拒绝整个清单. `CompilerAssetOrigin`
   对三个 bundle 资产不再直接流式返回, 而是每次请求完整读取, 校验与清单一致的 SHA-256 与大小后
   再返回; 校验失败返回 403, Worker 的 `importScripts` 抛错, 该层视为不可用, 首个项目随后在
   清单指纹比对处 fail closed, 与 L2-1 的失败路径一致. 字节不缓存在原生内存中.
2. **token 计数构建期锁定.** `tools/sync-declaration-token-counts.mjs` 用与 Worker
   `countSourceTokens` 完全相同的扫描配置 (`ts.createScanner(ES2018, skipTrivia, Standard)`)
   统计三层 220 个文件的 token 数并写入根目录 `r2-declaration-token-counts.lock.json`
   (schemaVersion 1, 每条为 VFS 路径 -> SHA-256 + tokenCount, 另记扫描器配置与 TypeScript
   版本). 生成器 `R2DeclarationWorkerBundles` 要求每个层文件在锁中有同摘要的条目且锁中没有多余
   条目, 否则构建失败并提示重跑工具; 每条记录写入 `tokenCount`, 每层写入 `totalTokens`,
   `bundles.json` 同步携带 `totalTokens`. 新门禁 `verifyR2DeclarationTokenCounts` 在 `check` 中
   用 `--check` 重算并逐字节比对锁文件, 同时断言单文件不超过 `maximumTokensPerFile`, common +
   rhino 与 common + node 各自低于 `maximumProjectTokens` (当前 86,329 + 44,530 与 86,329 +
   39,167, 上限 262,144). TypeScript 或声明层升级后必须重跑工具并提交锁, 见
   [UPGRADE.md](../maintenance/UPGRADE.md) 第 3 节.
3. **Worker 只做形状与配额校验.** 预装记录的 `byteLength`, `sha256`, `tokenCount` 都来自
   bundle, Worker 校验形状与总数后直接用于 `/declarations/` 记录; 合并时仍按三元组推导清单指纹
   并与 start 帧广告的 `declarationInventoryFingerprint` 比对, 仍替分帧路径重放文件数, 字节数,
   路径预算, 大小写折叠与 token 预算. 由调用方分帧的声明不受影响, 仍逐文件重算摘要与 token.
4. **遥测.** `declarationWarmUp` 报告保留每层的 `importMs` 与 `verifyMs`, 去掉已无意义的
   `digestMs` / `tokenMs`; `verifyMs` 现在只含形状校验.

不变量: 配额修订, 指纹算法, 公开 AIDL 与归档 schema 不变; 校验链为 "声明分发逐字节校验 ->
`bundles.json` 指纹绑定 -> bundle 整文件 SHA-256 -> Worker 清单指纹", 每一环都 fail closed.
- 预解析子项没有兑现: 首个 job 在 Worker ready 后很快到达, `setTimeout` 分片预热几乎没有
  窗口, S1 的冷 parse 反而略升; 后续作为线路图 L2-7 单独处理 (先打点 bundle 校验耗时与
  预热进度, 再决定同步预解析或把校验挪到原生侧). L2-7 的打点与复核发现真正原因是上文第 5 点
  的共享键不匹配 (预热结果从未被 Program 命中), 窗口不足只是次要因素; 数据与结论见线路图 L2-7.
- 首轮采集 (记录 `20261009-l2-api35-23046rp50c-preload-host-unfixed`, 宿主 `b4b4e36805`)
  中 S2 退化到 4.8 s. 排查确认插件的常驻判定正确 (新增 `typescript-compiler-trim`,
  `typescript-compiler-realm-teardown`, `typescript-compiler-service-destroy` 观测日志均未
  出现异常路径), 是宿主在每次编译后丢掉自己的专用绑定导致编译服务被销毁; 根因与修复见
  [startup-latency-roadmap.md](../../startup-latency-roadmap.md) L3-1 (宿主
  `TypeScriptCompilerWarmBinding.isAlive` 对已释放租约仍报告存活). 修复后 S2 5 次全部命中
  常驻会话.
