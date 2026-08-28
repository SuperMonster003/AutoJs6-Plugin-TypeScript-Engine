# 原生 npm 依赖

AutoJs6 TypeScript 项目支持不需要安装期生成物的纯 JavaScript/TypeScript npm package，
也支持把 WASM 文件作为普通项目/依赖资产使用的 package。它不加载 npm `.node` 原生
addon，也不在 Android 上执行 dependency 的 `preinstall`、`install` 或 `postinstall`。

这是一条可预测的平台边界：PC 上安装得到的 `.node` 通常面向 Windows、macOS 或 Linux，
并绑定具体 CPU/Node ABI；Android 项目内没有 node-gyp/Python/make/NDK 安装阶段。宿主会在
编译前直接指出依赖和文件，不会先忽略二进制、再在运行时报告模糊的“找不到模块”。

## 会看到的错误

| 错误码 | 触发条件 | 处理方式 |
|---|---|---|
| `ERR_AUTOJS6_TYPESCRIPT_NATIVE_DEPENDENCY_UNSUPPORTED` | dependency 含 `.node`、`binding.gyp`，或 `package.json` 顶层含 `gypfile: true` | 换用 pure JavaScript/TypeScript 或无需设备端安装步骤的 WASM 实现；不要只删除报错文件后继续使用原 package |
| `ERR_AUTOJS6_TYPESCRIPT_DEPENDENCY_INSTALL_SCRIPT_UNSUPPORTED` | dependency 的 `scripts` 对象含 `preinstall`、`install` 或 `postinstall` 精确键 | 选择发布包中已经包含全部运行资产的替代 package；如该脚本仅用于可选原生加速，改装作者提供的 browser/pure/WASM 变体 |

错误 detail 会同时给出 package、signal 与 `node_modules` 相对路径。Ace Editor 安装当前配套
版本后也会把相同 code/detail 显示为首条 TypeScript error，因此不必等到真机执行阶段才发现。

## 脚本字段的精确语义

只拒绝 npm 的三个安装生命周期键：

```json
{
  "scripts": {
    "preinstall": "...",
    "install": "...",
    "postinstall": "..."
  }
}
```

`test`、`build`、`lint` 等普通字段可以存在，但它们只是 inert package metadata，Android
永远不会执行它们。安装键即使命令为空或只是 `echo` 也会失败；项目不能根据命令文本猜测
一个 lifecycle 是否“碰巧安全”。项目根/monorepo workspace 自己的受控 metadata 规则仍
按配置文档执行，本页专指 `node_modules` 中的 dependency metadata。

## 排查依赖

在开发电脑的项目根可先检查直接和传递依赖：

```powershell
Get-ChildItem node_modules -Recurse -File -Filter *.node
Get-ChildItem node_modules -Recurse -File -Filter binding.gyp
```

再查看错误 detail 指向的 package `package.json`。原生信号可能来自传递依赖，不一定是你
直接写进 `dependencies` 的包。更新 lockfile 后重新打开 Ace 编辑会话并重新运行，宿主会
建立新的 exact dependency snapshot。

不要采用“删除 `.node`/`binding.gyp`、保留其 JavaScript wrapper”的方式绕过检查：多数
wrapper 随后仍会 `require()` 缺失的 binding。更可靠的替代顺序是：

1. package 官方 browser/pure JavaScript 入口；
2. 官方 WASM 变体，且 `.wasm` 已包含在发布包、不依赖 postinstall 下载或编译；
3. 用 AutoJs6/Android 已有 API 实现相同能力；
4. 如果能力本质上只能由 Android native library 提供，为它制作独立 AutoJs6 插件，而不是
   把桌面 Node addon 塞进 TypeScript 项目。

仓库以 `bcrypt@6.0.0` 作为真实验收包：它包含 `.node` prebuild、`binding.gyp` 和
`install: node-gyp-build`，稳定返回原生依赖错误。可离线复验：

```powershell
.\gradlew.bat :app:verifyT2NativeModuleBoundary --offline
```

依赖层配额、解析条件与普通包示例见[配置与配额参考](CONFIGURATION.md)和
[npm 真实包兼容矩阵](REAL_PACKAGE_MATRIX.md)。

