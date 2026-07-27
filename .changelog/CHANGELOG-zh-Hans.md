******

### 发行历史

******

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
