******

### 发行历史

******

# v0.6.0

###### 2026/08/28

* `提示` 项目编译现已面向 AutoJs6 6.8.0 Alpha7 (build 5253) 或更高版本启用; Node profile 还需要 Node.js Runtime 插件, Rhino profile 不需要
* `提示` 编译与执行现共用启动时的一份精确快照; npm 裸包/scoped package 可在编译期解析, 并通过 Rhino CommonJS 或 Node.js Runtime 插件在运行期装载
* `提示` Android 7.0 / API 24 需要 Chrome/WebView 119 或更高版本; API 24/25 系统自带的旧 provider 不受支持并会 fail closed
* `新增` 新增 dependency-layer policy revision 2 与 resolver policy revision 4: 宿主 archive 和 production Worker 以固定顺序拒绝 `.node`, `binding.gyp`, `gypfile` 与安装生命周期 hook, 返回稳定 native/install-script 错误及纯 JavaScript/WASM 替代指引
* `新增` 离线真实包验收矩阵在 Rhino 与 Node profile 中覆盖 dayjs 1.11.13, lodash 4.17.21, zod 4.1.5, uuid 13.0.0 和 nanoid 5.1.5; resolver policy revision 3 现可从运行时 JavaScript 回退到已安装的 `@types`, 且不会覆盖包自带声明
* `新增` 同步编译器与 Ace 的依赖包类型: package `types`/`typings`, TypeScript 6 `typesVersions`, 嵌套声明导入和已安装 `@types` 现进入同一冻结类型层; dayjs 补全, hover 与 strict TS2322 诊断在 Rhino 和 Node profile 间一致
* `新增` 同时启用 `compilerAvailable`, `projectCompilation`, `semanticDiagnostics` 和 `sourceMapV3` (capability mask 15), 提供完整 TypeScript Program 编译, 结构化诊断与配对的外部 Source Map v3 输出
* `新增` 新增公开 Rhino `.ts/.tsx` 项目编译, 支持 UTF-8 路径与原生 AutoJs6 TSX UI lowering; controlled tsconfig revision 3 同时覆盖 Rhino 和 NodeNext, 提供 20 个 boolean 选项及按 profile 限定的 `target`/`lib`, revision 2 Rhino 请求保持兼容
* `新增` 新增公开 NodeNext revision 2 编译, 支持 `.mts/.cts` 与项目范围内的 `.ts`, 并按源码种类和 package type 产出 `.mjs/.cjs/.js`
* `新增` 编译器已接入 AutoJs6 执行链, 可点击的编译诊断与运行时栈映射, 多文件项目, dynamic import 映射, Tasker/定时入口及 APK Builder 加密项目内存流程; 打包产物只含编译后的 JS/map, 不含 TS 源码
* `新增` Ace 编辑器的声明与 TypeScript 6.0.3 行为现已和执行前编译保持一致
* `新增` controlled tsconfig revision 4 为 Rhino 与 NodeNext 新增项目内 `baseUrl`/`paths` 路径别名; 解析仅限冻结 snapshot, 生成 specifier 会改写为显式运行时相对模块, revision 2/3 身份保持精确兼容
* `新增` Node ESM `.mts` 与根 `type: module` `.ts` 项目现已支持顶层 `await`; controlled ES2022 配置会产出带配对 Source Map 的 `.mjs/.js`, 可由 Node.js Runtime 插件直接执行
* `新增` 新增受控 Node workspace package boundary, 支持 `packages/<name>` 与 `packages/@scope/<name>`; 同一项目中的 `.ts` 可按最近 `package.json.type` 混用 ESM 与 CommonJS, `scripts` 继续禁止
* `新增` 兼容 Node Runtime 现以 V8 native linker 执行 ESM 入口与 dynamic `import()`, 支持循环依赖, 可变导出及 re-export live binding; 四模块 `.mts` 图可从 `initial=1` 更新为 `updated=42`
* `新增` 新增独立配额的只读 `node_modules` 依赖层与 bare/scoped package 解析, 支持子路径 `exports`, `types`/`import`/`require` 条件及 `types`/`typings`/`module`/`main`/`index` 回退, 并以 dayjs + zod 完整 Program 验收; 同一精确依赖层现可进入运行时, Rhino 选择 CommonJS/JSON `require` 入口并为 ESM-only 提供稳定错误, Node 通过不回写依赖的私有 workspace overlay 执行 ESM/CJS/dual package
* `优化` 新增显式 opt-in 的项目会话 revision 1: 服务按 caller/project 派生身份, 在有界 Worker realm 中跨执行复用摘要版本化 SourceFile, 增量 builder 与未变输出; 旧请求仍完全冷编译, 内存压力会丢弃全部会话
* `优化` 新增确定性的跨执行缓存复用; 实测命中耗时 18-63 ms, 有界输入 batching 在观测设备上降低 ingress 成本 40%-57%
* `优化` 普通输出上限提升到 512 artifacts, 单个 JavaScript 或 map 16 MiB, 合计 64 MiB; 普通 caller 输入升级到 revision 3 的 4096 files, 单文件 8 MiB, 合计 64 MiB, 插件内置声明不占 caller 配额, revision 2 旧宿主继续兼容 512 files / 8 MiB
* `优化` 冷项目 ingress 进一步采用最多 6 条中间帧且最多 4 条 payload chunk 的 batching; 固定 109 文件用例从 97 降至 68 个 envelopes, 四组 Android/WebView 的 compiler 与 ingress 中位数均改善

# v0.5.0

###### 2026/07/27

* `提示` R1 全部退出门禁现已完成, 包括 API 24/25 Chrome 119 modern-provider runtime matrix; `compilerAvailable=false` 仍为权威状态, 因为生产项目编译属于 R2
* `修复` service destruction 先完成 WebView, Worker, MessagePort, ServiceWorker client, pending request 和 Handler callback 清理, 再发布 close report 并唤醒等待方; 同时消除 deadline 与终态 publication 的竞态
* `修复` `/proc` 与 FD 观测绑定 `(pid,starttime)`, 对不可用或部分 shell 输出 fail closed, 并避免进程退出轮询触发 UiAutomation finish 竞态
* `优化` Android 12 和 Android 13 真机各验证 10 个 cold compiler process 与 100 次 hot prewarm; compiler service FD 最终精确回到 baseline, 且不残留 staging archive
* `优化` API 33 与 WebView 109 通过 none, Node-only, Ace-only 和 Node+Ace 四种安装态; 静态门禁禁止 production source, manifest 和 direct dependency 发现或依赖 Node/Ace

# v0.4.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.4.0 中仍为权威状态: API 24 只通过 Bundle/FD transport 门禁, 旧 WebView 无法解析原始 TypeScript 6.0.3 compiler JavaScript; API 24/25 modern-provider matrix 仍待完成
* `修复` Binder 在反序列化前拒绝 Bundle raw transport 中的 FD, 并在 prewarm, compilation 和 cancellation 拒绝路径释放传输副本
* `修复` Bundle metadata 校验仅接受实际运行时类型为 `String[]` 的字符串数组, 并拒绝顶层 null value, Parcelable array, Binder, Serializable 和 nested Bundle
* `修复` 以 epoch 合并 timeout recovery 执行, 防止 stale recovery 销毁替换 session; 确定性连续 1 ms timeout 门禁验证排队 probe 可恢复
* `优化` Android 12 和 Android 13 真机通过真实 WebView renderer 退出门禁: 首次退出只重试一次, 第二次退出稳定失败, 随后的普通 probe 可恢复
* `优化` Android 12 和 Android 13 真机各完成 10 轮 `:typescript_compiler` 进程重启并保持 distribution/declarations fingerprints 稳定; API 24 另通过 6 项 Bundle/FD transport 门禁

# v0.3.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.3.0 中仍为权威状态: R1 prewarm 仅执行受限 compiler host golden 探针, R2 完成前仍不可进行项目编译
* `新增` 独立 `:typescript_compiler` 进程中的 WebView/Worker compiler host 使用私有 HTTPS asset origin, 精确资源 allowlist, WebMessagePort 和收紧的 network, file, content, storage 及 window 权限
* `新增` R1 prewarm 实际加载 TypeScript 6.0.3 并校验 runtime version, enum emit, 结构化语法诊断, Source Map v3, 固定输出 fingerprints 和无 inline source content
* `优化` Android 12 和 Android 13 真机门禁覆盖每台设备 100 次热探针及超时后的 Worker 重建恢复, Android 13 还通过独立 helper 验证 minified release, 同时保持全部项目编译能力为 false
* `依赖` 附加 AndroidX WebKit 版本 1.14.0

# v0.2.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.2.0 中仍为权威状态: R1 仅准备并验证 compiler distribution 和 IPC/FD transport, R2 完成前仍不可进行项目编译
* `新增` TypeScript 6.0.3 官方发行包管线验证 SHA-1, SHA-256 和 SRI SHA-512 三种摘要, 通过严格 tar entry allowlist 安全提取文件, 并生成 canonical `distribution.json` 以及 compiler, standard libraries, generator 和 distribution fingerprints
* `新增` Debug/release APK 内容门禁依据 `distribution.json` 重新验证实际打包字节, 确保 APK 仅包含一份 allowlist 内的 `lib/typescript.js`, 且 declarations, license 和 third-party notice 完整一致
* `新增` Instrumentation 覆盖真实远程 `:typescript_compiler` 进程的 Binder 服务发现/能力和 R0 同步拒绝, 验证拒绝后调用方持有的 FD 仍然有效, 且无效或共享 descriptor 在有界短观察窗内未触发 callback
* `依赖` 附加 AndroidX Test Runner 版本 1.7.0
* `依赖` 附加 AndroidX Test JUnit Extension 版本 1.3.0

# v0.1.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 表示 R0 仅提供插件发现和临时编译协议, 尚不能编译或执行 TypeScript
* `新增` 独立的 AutoJs6 TypeScript 编译前端插件工程, 精确锁定 TypeScript 6.0.3, 并与 Node.js Runtime 插件保持零依赖
* `新增` 通过受 `org.autojs.permission.PLUGIN` 保护的 `org.autojs.plugin.INFO` 和 `org.autojs.plugin.typescript.COMPILER` 服务提供插件发现与编译能力协商
* `新增` 编译服务运行于独立的 `:typescript_compiler` 进程, 临时 Binder envelope 使用小型 `Bundle` 元数据和 `ParcelFileDescriptor` 输入/输出 archive
* `新增` R0 自动化门禁覆盖 API contract, admission/rejection, compiler distribution lock, Node Runtime 独立性, Lint 和 universal APK 内容
