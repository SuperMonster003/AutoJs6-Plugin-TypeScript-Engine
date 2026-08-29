# T6-3 模板宿主真机终验

状态：2026-08-29 完成。

## 结论

`templates/` 的三个顶层模板集已在由当前 AutoJs6 宿主源码构建的可安装候选上按原目录
零修改跑通：

- Rhino 单文件 TSX UI；
- Rhino 三源码文件相对导入；
- Node ESM/CommonJS 双独立项目。

Node 模板集包含两个可分别启动的 project，因此本轮实际执行了四个 `project.json` 入口。
最终部署目录与仓库模板逐文件 SHA-256 为 `20/20` 一致；所有项目都通过宿主的持久化
project launch context 启动，没有把源码文件改写为临时脚本，也没有在设备端修改模板。
Roadmap T6-3 与其归档来源 S7-4 因此同时关闭。

## 候选身份

最终执行所用的源码与构建元数据现均已提交。Node 最终重建自动刷新了 build 65 的
`version.properties` 时间；真机执行后该生成值才原样提交为 `31f5bc6`，期间没有再次改变
APK 或可执行源码。APK 的本地 SHA-256 与设备安装目录中的 `base.apk` SHA-256 逐项相同。

| 组件 | 测试源码提交 | 包 / 版本 | 候选 APK | 大小与 SHA-256 |
|---|---|---|---|---|
| AutoJs6 宿主 | `d7c7ebf5a15826ef2b54fa69854b020b6df577e4` | `org.autojs.autojs6` / `6.8.0` (`5276`) | `app/build/outputs/apk/app/debug/autojs6-v6.8.0-universal.apk` | `47,682,601` bytes / `ca9080dbb149b0df846f1137328bf627e6c22a773e998962600a7772b32c9283` |
| TypeScript Engine | `664b5ce279f3e21bf22ddc4272f71a9a38d7a293` | `io.github.supermonster003.autojs6.plugin.typescript` / `0.6.0` (`6`) | `app/build/outputs/apk/debug/autojs6-plugin-typescript-engine-v0.6.0-universal.apk` | `5,874,748` bytes / `5d6525b6a2eacd6b54c6160f8d4fd88c56161df5d780dbc16dde7329a95dc119` |
| Node.js Runtime | `31f5bc6b6ccb7ac61c6034039b4caaaafcca9b5b` | `io.github.supermonster003.autojs6.plugin.nodejs` / `1.2.0` (`65`) | `app/build/outputs/apk/debug/autojs6-plugin-nodejs-runtime-v1.2.0-arm64-v8a.apk` | `30,868,107` bytes / `f5c55e4a6001a5fc3558fa6b4135690f83b972825eaa3102dee77106a823cdc2` |

三份 APK 均由 `apksigner 37.0.0` 验证为 APK Signature Scheme v2、单 signer，证书
SHA-256 都是
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`。
这里记录的是 T6-3 本地 debug 集成候选，不替换
[v0.6.0 发布说明](../releases/v0.6.0.md) 中仍待 T6-4 重建和公开分发的 release 资产。

## 设备与运行环境

| 项目 | 值 |
|---|---|
| ADB serial | `968e9f18` |
| 设备 | Xiaomi `23046RP50C` |
| Android | `15` / API `35` |
| ABI | `arm64-v8a,armeabi-v7a,armeabi`；三个已安装包实际 `primaryCpuAbi=arm64-v8a` |
| 物理显示 | `1800x2880`；本轮横屏脚本运行时为 `2880x1800` |
| WebView | `com.google.android.webview 130.0.6723.86` |

## 原目录与字节身份

仓库 `templates/` 被直接推送到：

```text
/sdcard/AutoJs6/T6-3-final-20260829-1/templates
```

推送结果为 `20 files pushed, 0 skipped`。随后对每个仓库文件运行本地 SHA-256，并对
对应设备文件运行 `sha256sum`，结果 `20/20` 相同。按下列排序内容以 UTF-8/LF 拼接所得的
清单 SHA-256 为
`0847becf53c942761d1060e3d0fc7c28ff7d0a95b8041391e21f7c63d14b4b04`：

```text
4c9dbd7fd5cc08fa37692f90dce37f8c0a813d50de44319b56c82ee4d513e507  node-esm-cjs/cjs/main.cts
8005a3491db7d92f36ac66369861589f9c47123d3a7c71e643fc2c06168cd45a  node-esm-cjs/cjs/package.json
65465339df8fed7490a144ea4fd08eb3c8a3e694028b7b7c4265ade1956c0c44  node-esm-cjs/cjs/project.json
34a6858fef676dd82c6fe7d1ce70d4e7dec079d66161c800ba9e528c0df10684  node-esm-cjs/cjs/tsconfig.json
3930093cdf6fc2e42f1fff863191eae47f3b5c022410fcf8f0b7070245390ae4  node-esm-cjs/esm/main.mts
3ca9d4afd21425087cf31893b8f9f63c81b0b8408db5e343ca76e5f8aa26ab9a  node-esm-cjs/esm/package.json
2e505f8c64739afb043062c713854ff1eb7a9df0c7dbc8995893391fa9d7485b  node-esm-cjs/esm/project.json
78220ed01f789173d57b8c80dff0b12ac317840ac584b61291a36e2e8a88dbfb  node-esm-cjs/esm/tsconfig.json
1af2968bf4a609e49792ad16462187e350ed0985dc014a29666895321bc5946e  node-esm-cjs/README.md
aedffd0a3cb4b3fccf1cd0dcc7ffc4dc8fa1316fe13a988e60bdf556209ab9b6  README.md
e61ba003dc9318f10e47776b0747316d3885cae3e228fd29e54a94e0a5d320a4  rhino-multi-file/lib/device-report.ts
87380739160b8a2dee8e67650c23d770df2403017a2e3724c28ad3529543add0  rhino-multi-file/lib/format-report.ts
134991e04a098583f610918a79dd6c8f8622030e61ce251cf1c9fa2f5e4b74b4  rhino-multi-file/main.ts
c20474aa341c22839041d4de6a3b2b812200dcf120ba4a6f4872c14d190ad802  rhino-multi-file/project.json
16b7da5925cbd95bcd51c48cb6040752883b5872394397f6d2bbdf8f04efe862  rhino-multi-file/README.md
e206a16f5ab48f875fded61aba820c7907fe087d2905d92e27169f1ec7592881  rhino-multi-file/tsconfig.json
f6b9d61273a3a61a2957e3c0fcb4baa6682b02e4111976569bf04646f6594fc4  rhino-single-ui/main.tsx
e27f019be6c69865df6280dc03b589ccc28556e73d62d1dc21564e408c92e4dd  rhino-single-ui/project.json
9372dfeb4348d4b98f1c1c6e995c38f724b82e8c5f5a8557bb03e7be3a0c496e  rhino-single-ui/README.md
a8873ca2f29eeac172451dce03005bd061a513d8f3585f514b3ad9b495cf7b61  rhino-single-ui/tsconfig.json
```

每次运行都通过宿主正式的 project shortcut 入口，并显式携带持久化项目身份：

```powershell
adb -s 968e9f18 shell am start -W `
  -n org.autojs.autojs6/org.autojs.autojs.external.shortcut.ShortcutActivity `
  --es path <project-directory> `
  --es source_kind autojs_project_v1 `
  --es project_root <project-directory>
```

## 最终宿主候选运行回执

以下结果都来自安装哈希为
`ca9080dbb149b0df846f1137328bf627e6c22a773e998962600a7772b32c9283` 的最终宿主
候选、最终 Node build 65 与同一份哈希核对后的模板目录。为了核对最终 APK 而进行的复跑命中
了跨执行 compiler cache；命中仍完整经过项目恢复、宿主路由、脚本引擎启动和运行时 API。

| Project | 编译回执 | 运行回执 | 结果 |
|---|---|---|---|
| `rhino-single-ui` | `profile=rhino outcome=emitted cache=hit totalMs=3645 inputBytes=2463 sourceFiles=1` | 日志 `Starting TSX UI on a 2880px screen`；UI 标题 `TypeScript TSX template`；源码按钮 `Click me` 由 Android 主题显示为 `CLICK ME`，bounds `[60,883][2820,1003]`，点击 `(1440,943)` 后日志 `TSX template button clicked` | PASS |
| `rhino-multi-file` | `profile=rhino outcome=emitted cache=hit totalMs=7869 inputBytes=3497 sourceFiles=3` | `AutoJs6 TypeScript: 2880x1800, API 35`；`安卓 SDK 版本: 35` | PASS |
| `node-esm-cjs/esm` | `profile=node outcome=emitted cache=hit totalMs=6049 inputBytes=2542 sourceFiles=1` | toast `Node ESM TypeScript template works`；`package=org.autojs.autojs6 installed=true screenOn=true` | PASS |
| `node-esm-cjs/cjs` | `profile=node outcome=emitted cache=hit totalMs=7771 inputBytes=2638 sourceFiles=1` | toast `Node CommonJS TypeScript template works`；`package=org.autojs.autojs6 installed=true screenOn=true` | PASS |

四次最终复跑均出现 `ScriptEngineService: onStart`，且未出现 `ERR_AUTOJS6`、
`onException` 或 `ScriptException`。

## 终验中发现并关闭的问题

终验不是绕过错误取得绿灯，而是在候选源码与模板中关闭了三个真实缺口后重新部署原目录：

1. 宿主原先用 APK 打包完整性规则读取所有 `project.json`。没有 `packageName`、
   `versionName`、`versionCode` 的最小脚本项目会被误判无效，持久化启动因而退回
   `main.js`。宿主提交 `1d9d8f99f` 分离 execution 与 packaging validation；最终设备上的
   `AutoJsProjectPersistedLaunchContextInstrumentationTest` 为 `OK (5 tests)`。
2. Node LOAD_AS_FILE 在探测不存在的 `index.mts/index.cts` 时过早执行 raw TypeScript
   fail-closed，既会遮蔽后续 JavaScript 候选，也让 ESM 缺少 CommonJS 已有的“npm 包优先、
   AutoJs6 facade 后备”行为。Node 提交 `cbc5996` 将策略延后到候选实际存在之后，并为
   ESM 恢复受控 facade fallback；最终 APK 上相关原生回归为 `OK (6 tests)`，同时锁定
   raw TypeScript 拒绝、snapshot 映射、路径拒绝、live binding 与 npm precedence。
3. Node 模板调用 `app.isInstalled()` 却只声明宽泛的 `app`，宿主能力模型要求细粒度
   `app.query`。Engine 提交 `664b5ce` 修正 ESM/CJS 两份 `project.json`、用户文档和模板
   契约校验器。修正后的模板被整棵重新推送并完成上述 `20/20` 哈希核对，因此“零修改”是
   用户拿到最终模板后直接运行，而不是在设备上手改配置。

## 构建与定向门禁

- Engine：`verifyS7UserGuidesAndTemplates --offline` 通过，报告 `4 docs, 3 template
  sets/4 runnable projects`；最终 `:app:assembleDebug` 为 `BUILD SUCCESSFUL`，95 个
  actionable tasks（8 executed、87 up-to-date）。
- Node：最终 `:app:assembleDebug :app:assembleDebugAndroidTest` 为 `BUILD SUCCESSFUL`，
  97 个 actionable tasks（10 executed、87 up-to-date）；设备定向 conformance 为
  `OK (6 tests)`。
- Host：最终 `:app:assembleAppDebug :app:assembleAppDebugAndroidTest` 为
  `BUILD SUCCESSFUL`，681 个 actionable tasks（4 executed、677 up-to-date）；项目启动
  路由设备测试为 `OK (5 tests)`。

宿主同一工作区还包含 Explorer Action protocol v20 的归档目录创建改造。其两项 UI
可访问性设备测试通过，但设备上另行安装的 Archive Manager 插件仍未声明 v20
`canCreateDirectory`，因此该旁路集成测试在能力断言处失败。T6-3 四个模板不发现、不调用
Archive Manager，此版本配对问题不参与本项验收，也没有被误报为 T6-3 绿灯。

## 验收映射

| Roadmap 验收句 | 证据 |
|---|---|
| 三个模板真机全部零修改跑通 | 三个顶层模板集、四个 project 均 PASS；同一远端目录逐文件 `20/20` SHA-256 相同 |
| 使用可安装宿主候选 | 当前宿主源码 clean HEAD 构建的 universal APK 已安装；本地/设备 SHA-256 相同 |
| S7-4 归档条目闭环 | S7-4 已改为完成并链接本页；T6 只剩外部发布链 T6-4 |
