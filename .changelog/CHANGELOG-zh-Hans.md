******

### 发行历史

******

# v0.1.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 表示 R0 仅提供插件发现和临时编译协议, 尚不能编译或执行 TypeScript
* `新增` 独立的 AutoJs6 TypeScript 编译前端插件工程, 精确锁定 TypeScript 6.0.3, 并与 Node.js Runtime 插件保持零依赖
* `新增` 通过受 `org.autojs.permission.PLUGIN` 保护的 `org.autojs.plugin.INFO` 和 `org.autojs.plugin.typescript.COMPILER` 服务提供插件发现与编译能力协商
* `新增` 编译服务运行于独立的 `:typescript_compiler` 进程, 临时 Binder envelope 使用小型 `Bundle` 元数据和 `ParcelFileDescriptor` 输入/输出 archive
* `新增` R0 自动化门禁覆盖 API contract, admission/rejection, compiler distribution lock, Node Runtime 独立性, Lint 和 universal APK 内容
