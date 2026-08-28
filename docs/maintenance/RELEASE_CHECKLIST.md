# 发布前检查清单

本清单适用于本地 release candidate，也适用于宿主发布后获得明确授权的正式发布。
远端上传只是最后一步；在 AutoJs6 宿主尚未发布期间，前面的本地步骤仍可完整执行，
最后一步记录为“等待宿主发布”，不得自行创建 GitHub Release 或市场条目。

## 1. 冻结候选内容

- [ ] `version.properties` 的版本、build number、TypeScript 版本与发布说明一致。
- [ ] 十语言 changelog 已生成且 `py .python/generate_changelog.py --check` 通过。
- [ ] 用户文档、模板、已知边界和本次 capability advertisement 与 APK 一致。
- [ ] 工作区没有被本轮意外覆盖的用户改动；发布范围已人工复核。

## 2. 跑本地离线门禁

```powershell
.\gradlew.bat help --offline --console=plain
.\gradlew.bat check --offline
.\gradlew.bat :app:assembleRelease --offline
.\gradlew.bat :app:verifyReleaseCompilerHostApk `
  :app:verifyReleaseTypeScriptDistributionApk `
  :app:verifyReleaseR2DeclarationDistributionApk --offline
```

- [ ] `help` 输出的 Gradle/AGP/Kotlin/KSP/R8/JDK 组合与最新一条
  [依赖巡检记录](dependency-inspections.json)一致。
- [ ] 所有命令退出码为 0；不以历史大型专项门禁代替当前 `check`。
- [ ] 正式证书、APK SHA-256、证书 SHA-256、文件大小和唯一 signer 已写入本版
  `docs/releases/` 发布说明。

## 3. 跑 release 行为矩阵

- [ ] 最低 Android API 24 + Chrome/WebView 119 运行 framework-only release smoke。
- [ ] API 31、API 35 实机与 API 37 模拟器各运行同一 smoke；记录 active provider
  的完整版本，而不只记录 Android 版本。
- [ ] Rhino 与 Node profile 的代表性项目、诊断、JavaScript、Source Map 和 formal
  output manifest 与本版发布说明一致。
- [ ] 若最低 provider 策略发生变化，先按 [WebView 支持策略](WEBVIEW_SUPPORT.md)
  补齐正/负边界证据，再改 README 和发布说明。

## 4. 跑固定冷启动/ingress 观察

固定观察复用
`R2ProjectCompilerInstrumentationTest#packagedStandardLibrariesCrossTheFormerSingleMessageLimit`：
109 files、超过 3 MiB、379 logical frames，并记录
`typescript-cold-start-perf schema=1 ... gate=none`。当前 6-total/4-payload transport
应形成 68 个 envelopes，详见[冷启动与 ingress 观察](COLD_START.md)。

```powershell
.\tools\observe-cold-start.ps1 `
  -Serial emulator-5554 `
  -DeviceLabel api37-reference `
  -Runs 5 `
  -AllowInstall
```

- [ ] 在相同 device label、Android API、WebView 完整版本和 build type 下与上一条记录
  比较 compiler/ingress median；不跨设备比较绝对值。
- [ ] 原始 JSON/instrumentation/logcat 已保存在 ignored
  `build/reports/cold-start/`，异常值与设备负载已人工复核。
- [ ] 此项为 `gate=none`，不设置 p95 或固定毫秒阈值。明显回退先重复复跑并开 issue，
  不通过放宽四 payload/576 KiB 防线掩盖问题。

脚本会替换目标设备上的插件 APK，结束时只卸载 AndroidTest helper，不会恢复原 release
APK；只能在专用验证设备上使用。S10-1 的四设备原始 A/B 已写入
[cold-start-observations.json](cold-start-observations.json)，不应为补格式伪造数据。

## 5. 跑固定内存观察

固定观察复用
`R2BinderCompilationInstrumentationTest#recordsTypicalCompilationTimingAndActivePssWithoutPerformanceThreshold`：
8 个 Rhino 源文件、5 秒 active graph hold、3 个 PSS 样本，同时读取 compiler
service 与其精确关联的 sandbox renderer。日志身份固定为
`typescript-compiler-perf schema=1 ... gate=none`。

这一步会安装/替换选定设备上的 debug 插件 APK。必须使用专用验证设备；脚本只会在
结束时卸载 AndroidTest 包，不会恢复原 release APK。`-AllowInstall` 是显式副作用确认，
`-Promote` 才会把成功记录追加到受版本控制的历史：

```powershell
.\tools\observe-release-memory.ps1 `
  -Serial emulator-5554 `
  -DeviceLabel api37-reference `
  -AllowInstall `
  -Promote
```

不传 `-Promote` 时，JSON、instrumentation 输出和过滤后的 logcat 只写入已忽略的
`build/reports/release-memory/`。先检查原始输出，再决定是否纳入
[内存观察历史](memory-observations.json)。

- [ ] 每个候选版本至少在同一台 reference device 上记录一次；更换设备标签、Android
  API 或 WebView major 后建立新序列，不把不同设备的绝对数值直接比较。
- [ ] 与相同设备标签、相同 fixture 的上一条记录并排查看 compiler service、renderer
  与 combined median PSS，同时参考 compiler time 和设备负载。
- [ ] 此项只观察，`gate=none`，不设置数值阈值或 p95 门禁。若人工复跑后仍出现明显
  回归，先保存 raw report 并创建性能 issue，再决定是否阻止发布。

S5-5 的 API 31/35/37 三条数据已经迁移为 v0.6.0 首批可比历史，因此无需为“补齐
格式”伪造一次新测量。

## 6. 完成或明确延后分发

- [ ] 宿主版本已经通过自己的发布流程，且 owner 明确授权本项目的远端目标。
- [ ] 仅上传已经过上述检查、哈希完全一致的 APK；真实下载入口再次核对签名和 SHA-256。
- [ ] 从公开入口在一台干净设备安装并运行 `hello.ts`，随后更新插件索引/安装指引。
- [ ] 若宿主尚未发布，在发布说明与 Roadmap 记录延后原因；本地候选可以继续维护，
  但不得把本地路径写成公开下载入口。
