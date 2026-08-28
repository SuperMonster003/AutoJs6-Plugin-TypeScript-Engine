# T2-6 原生依赖与安装生命周期边界

状态：已完成（2026-08-28）。本项定义的是 Android/Node ABI 与工具链的明确平台边界，
不是对纯 JavaScript npm 生态的额外限制。

## 结论

TypeScript 项目依赖只要出现下列任一信号，就在编译归档建立阶段明确失败，不再把信号
过滤掉后留下含糊的 module-not-found，也不会尝试在设备上运行 npm lifecycle：

| 优先级 | 信号 | 稳定结果 |
|---:|---|---|
| 0 | 任意依赖文件以 `.node` 结尾 | `ERR_AUTOJS6_TYPESCRIPT_NATIVE_DEPENDENCY_UNSUPPORTED` |
| 1 | 任意依赖文件名为 `binding.gyp` | `ERR_AUTOJS6_TYPESCRIPT_NATIVE_DEPENDENCY_UNSUPPORTED` |
| 2 | dependency `package.json` 顶层 `gypfile: true` | `ERR_AUTOJS6_TYPESCRIPT_NATIVE_DEPENDENCY_UNSUPPORTED` |
| 3 | dependency `package.json.scripts` 含精确键 `preinstall`、`install` 或 `postinstall` | `ERR_AUTOJS6_TYPESCRIPT_DEPENDENCY_INSTALL_SCRIPT_UNSUPPORTED` |

同一项目有多个信号时，先按上表优先级，再按 canonical dependency path、signal 字符串
排序，保证宿主、插件、Worker、Ace 和离线门禁选出同一首个失败。`build`、`test` 等
非安装脚本仍是 inert JSON 元数据：允许打包但永不执行。生命周期命令内容不参与判断；
即使脚本只写 `echo`，精确安装键也会被拒绝，因为 Android snapshot 从不承诺执行它。

当前 dependency-layer schema revision 仍为 `1`，native-boundary policy revision 升至
`2`，fingerprint 为
`c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a`。resolver policy
随边界绑定升至 revision `4`，fingerprint 为
`d8207539237d2a08a6b97b530c5e6215f4b73311fdd6954ce9f8004717ebd635`。普通 caller 配额、
依赖层配额与 Binder 方法均未改变。

## 为什么必须明确拒绝

`.node` 是按操作系统、CPU ABI、Node ABI/N-API 和 C/C++ 运行库构建的动态库。PC 上
`npm install` 得到的 Darwin、Windows 或 Linux prebuild 不能当作 Android 库加载；即使
package 带 `binding.gyp`，当前宿主也不提供 npm、node-gyp、Python、make、C/C++ 编译器、
Android NDK 构建环境或安装脚本执行阶段。静默忽略这些文件会让类型检查可能通过，而运行
阶段才以错误包名、缺失 binding 或动态链接异常失败。

因此正确体验是：在用户点击运行后、进入 Binder 编译前，直接报告具体 package、signal、
path、稳定错误码和可行动 workaround。可替代实现优先选择 pure JavaScript/TypeScript；
计算密集型能力也可选择 package 自带、无需 postinstall 生成产物的 WASM 发布版本。

## 分层实现

1. AutoJs6 宿主遍历原始 `node_modules` 时，先收集 `.node`/`binding.gyp`，再应用 T2-1
   文本后缀过滤；对保留的每个 dependency `package.json` 检查 `gypfile` 与三个安装键。
   这样原生信号不会在归档前消失。
2. TypeScript Engine Plugin 对收到的 dependency archive 再执行同一 path/metadata 校验。
   手工构造、旧宿主或恶意归档不能绕过宿主 preflight；失败映射到两个公开稳定错误码。
3. compiler Worker 在 framed 只读 VFS 接收记录时保留最后一道防御。它不会加载原生文件，
   而是在记录进入 Program 前按同一错误码终止。
4. Ace Editor 以同一 policy revision 捕获项目类型层。原生二进制不会进入编辑器 VFS，
   但 `dependencyBoundaryCode`/`dependencyBoundaryDetail` 会成为第 0 行的首条 error 诊断，
   因而编辑、编译和运行边界一致。

## 固定真实包证据

离线 fixture 保存 npm 原始 `bcrypt@6.0.0` tarball（MIT，npm SHA-512 integrity
`sha512-cU8v/EGSrnH+HnxV2z0J7/blxH8gq7Xh2JFT6Aroax7UohdmiJJlxApMxtKfuI7z68NvvVcmR78k2LbT6efhRg==`）。
它同时包含 10 个 Darwin/Linux/Windows `.node` prebuild、`binding.gyp` 和
`scripts.install = "node-gyp-build"`。`verifyT2NativeModuleBoundary` 解开 tar header、核对
integrity/package identity，按生产优先级得到 12 个边界候选，并稳定选择：

```text
ERR_AUTOJS6_TYPESCRIPT_NATIVE_DEPENDENCY_UNSUPPORTED
native-binary
prebuilds/darwin-arm64/bcrypt.node
```

同一门禁实际启动生产 Worker + TypeScript 6.0.3，分别投喂 `.node`、`binding.gyp`、
`gypfile`、三个 lifecycle key，并确认 `build`/`test` 不触发边界。Kotlin 测试还覆盖宿主
原始目录 preflight、插件 archive 校验、错误码映射与 Ace 冻结类型层诊断。

```powershell
.\gradlew.bat :app:verifyT2NativeModuleBoundary --offline
```

用户侧判断与替代方案见[原生 npm 依赖](../user/NATIVE_DEPENDENCIES.md)。

