# 维护入口

本目录把不依赖远端发布的长期维护动作固化为可重复执行的本地流程。它不会改变
“AutoJs6 宿主先发布，本项目再创建 GitHub Release/插件索引条目”的当前顺序。

- [发布前检查清单](RELEASE_CHECKLIST.md)：离线门禁、签名产物、设备 smoke、固定
  PSS 观察与远端步骤的授权边界。
- [WebView 支持策略](WEBVIEW_SUPPORT.md)：Android API 与 provider 是两条独立基线，
  当前最低支持 Chrome/WebView 119。
- [依赖健康巡检](DEPENDENCY_HEALTH.md)：中央平台插件所选
  AGP/Kotlin/Gradle/KSP/R8/JDK 的季度检查规则、当前决策与历史巡检记录。
- [TypeScript 6.x 升级手册](UPGRADE.md)：官方 registry 预检、三摘要 lock、
  distribution/declarations 重建、条件式 profile 指纹、宿主缓存失效和设备 smoke。
- [TypeScript 升级审计](typescript-upgrades/2026-08-25.json)：最新稳定 6.x
  核对与隔离 6.0.2 -> 6.0.3 重放的机器证据。
- [Issue 分诊手册](ISSUE_TRIAGE.md)：结构化 intake、脱敏 diagnostics/performance
  采集、最小复现、修复验证与 changelog 决策闭环。
- [本地真实 issue](issue-triage/LOCAL-TS-2026-08-25-01.md)：S10-3 发现的
  declaration upgrade coupling 从失败到关闭的完整记录。
- [冷启动与 ingress 观察](COLD_START.md)：S10-1 的 6-total/4-payload batching
  决策、四设备 A/B 原始样本、被否决候选与无阈值复跑方式。
- [冷启动观察历史](cold-start-observations.json)：固定 109-file Rhino 标准库
  workload 的 max4 基线与最终 6/4 候选机器记录。
- [执行前耗时基线](startup-latency-observations.json)：startup-latency 线路图的
  S1/S2/S3 三场景端到端记录 (宿主 + 插件 boot 行), 由
  `tools/observe-startup-latency.ps1` 采集, 无阈值.
- [内存观察历史](memory-observations.json)：固定 8 文件 Rhino workload 的结构化、
  无阈值历史数据。
- [依赖巡检历史](dependency-inspections.json)：每次巡检所采用的仓库兼容矩阵快照、
  实际选中版本和结论。

日常 `gradlew check --offline` 会验证这些记录仍与代码中的 instrumentation identity、
compiler/declaration distribution、Gradle wrapper、`version.properties`、
中央平台插件传入的实际版本决策、issue 模板/采集隐私边界和文档一致。消费仓不保存
`gradle/data` 兼容矩阵；该数据只由中央平台仓库维护。日常门禁不会连接设备、刷新网络
元数据、上传 APK 或创建远端 issue。设备侧冷启动复跑需显式执行
`tools/observe-cold-start.ps1 -AllowInstall`，不属于默认 `check` 的副作用。
