# AutoJs6 TypeScript Engine Plugin

AutoJs6 的完整 TypeScript 编译前端插件. 插件负责把 TypeScript 项目转换为
JavaScript, Source Map 和结构化诊断, 最终 JavaScript 仍由 AutoJs6 选择的
Rhino 或 Node.js 引擎执行.

> 当前状态: R0 插件基线已完成, R1 正在进行. v0.2.0 已加入经过三摘要验证的
> TypeScript 6.0.3 compiler distribution 与真实跨进程 Binder/FD 门禁.
> `compilerAvailable=false` 仍为权威状态; R2 完成前尚不能编译或执行
> TypeScript 项目.

## 固定基线

- AutoJs6 开发基线: `6.8.0 Alpha7`, build `5253`.
- Android: min SDK `24`, compile/target SDK `36`.
- TypeScript: 精确锁定 `6.0.3`.
- 插件 ID / engine: `typescript`.
- 插件 variant: `typescript_6_0_3`.
- Compiler action: `org.autojs.plugin.typescript.COMPILER`.
- Compiler process: `:typescript_compiler`.

## 核心边界

```text
AutoJs6 host
  -> 读取或解密项目并构造受限 VFS archive
  -> TypeScript compiler plugin
       -> JS + Source Map + diagnostics + manifest
  -> AutoJs6 host
       -> Rhino adapter 或 Node adapter
```

- 插件是编译前端, 不是第三个 JavaScript VM.
- 插件不读取宿主私有项目路径, 只消费宿主显式传入的归档.
- 插件不依赖, 不发现, 不启用 Node.js Runtime 插件.
- Node Runtime 未安装或未启用时, Rhino TypeScript 仍应可编译并执行.
- Ace 编辑器和编译插件可以共享 TypeScript 发行包规范, 但不共享 WebView,
  Language Service, AST 或生命周期.
- 首个 GA 的 Rhino profile 支持 `.ts`, Node profile 支持 `.ts/.mts/.cts`.
- 首个 GA 只编译执行前 snapshot; 运行中外部 TS 与 bundled compiler 延至
  Post-GA.
- StackTrace 契约为 Node 文件/行/列, Rhino 文件/行; 两者编译诊断均到列.

## Roadmap

最终集成计划和逐项状态见 [ROADMAP.md](ROADMAP.md).

多语言发行记录见
[CHANGELOG-zh-Hans.md](.changelog/CHANGELOG-zh-Hans.md). 修改
`.changelog/lang_*.json` 后运行:

```powershell
py .python/generate_changelog.py
py .python/generate_changelog.py --check
```

## 构建与验证

```powershell
.\gradlew.bat verifyR0
.\gradlew.bat verifyR1CompilerDistribution
```

`verifyR1CompilerDistribution` 从 lock 内的精确 npm URL 获取 TypeScript
tarball, 验证 SHA-1, SHA-256 和 SRI SHA-512, 安全生成发行目录, 并重新校验
debug/release APK 中的实际资产字节.

连接 Android 设备后运行真实 `:typescript_compiler` Binder/FD 门禁:

```powershell
.\gradlew.bat verifyR1BinderFdDebug
```

R0 的细分任务仍可分别运行:

```powershell
.\gradlew.bat :plugin-api:typescript-api:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:verifyNodeRuntimeIndependence
.\gradlew.bat :app:verifyCompilerDistributionLock
.\gradlew.bat :app:assembleDebug
```

Debug/release APK 分别输出到 `app/build/outputs/apk/debug/` 与
`app/build/outputs/apk/release/`.

## License

项目使用 [MPL-2.0](LICENSE). TypeScript 发行物使用其上游许可证; R1 开始
打包发行物时必须同时包含 `LICENSE.txt` 和 `ThirdPartyNoticeText.txt`.
