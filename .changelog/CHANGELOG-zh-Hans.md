******

### 发行历史

******

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
