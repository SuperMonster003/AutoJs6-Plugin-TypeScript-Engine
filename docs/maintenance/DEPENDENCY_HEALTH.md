# 依赖健康巡检

## 固定节奏

每年 1/4/7/10 月各检查一次；若某个 release candidate 距上一条记录超过 90 天，发版
前补跑。巡检分成两个明确阶段：

1. **仓库快照检查（可离线）**：以 `gradle/data`、Gradle wrapper、
   `version.properties` 和 version catalog 为权威，确认实际选中组合兼容并跑完整
   `check`。日常与本轮使用这一阶段。
2. **上游元数据刷新（需单独授权联网）**：运行仓库已有 scrape/inject 工具，审阅
   Android/Gradle/Kotlin/KSP 官方来源带来的 diff，再进入升级分支。未刷新时不得把
   结果描述为“互联网上的最新版本”。刷新元数据与发布远端资产是两个独立授权动作。

默认只选择 stable；alpha/beta/RC 必须有明确实验目标，不替换正式构建基线。升级组合
顺序为 Gradle wrapper → AGP → Kotlin → KSP/R8 → JDK/SDK → catalog libraries，任何
一步不满足内置矩阵就停止，而不是靠关闭版本检查继续。

## 2026-08-25 首次巡检记录

模式：本地、离线、repository compatibility snapshot。网络请求、远端发布和依赖版本
修改均为 0。机器可读记录见 [dependency-inspections.json](dependency-inspections.json)。

实际 `gradlew help --offline --console=plain` 选中：

| 组件 | 版本 | 仓库快照结论 |
|---|---:|---|
| Gradle wrapper | `9.6.1` | 当前 wrapper |
| Android Gradle Plugin | `9.3.0` | `agp-releases.list` 中最高 stable；`9.4.0-alpha05` 不晋升 |
| Kotlin Gradle Plugin | `2.3.21` | Gradle 9.6 compatibility entry |
| KSP | `2.3.10` | Kotlin 2.3 release family；最低 AGP 8.10.0 |
| AGP bundled R8 | `8.13.19` | Kotlin 2.3 最低要求 8.13.19 |
| JDK / JVM target | `21` / `21` | 项目支持 17–26，建议/选中 21 |
| compileSdk / targetSdk / minSdk | `36` / `36` / `24` | 与 v0.6.0 发布说明一致 |

兼容关系复核：AGP 9.3 要求 Gradle 9.5.0 或更高，当前为 9.6.1；Java 21 要求
Gradle 8.5 或更高；KSP 2.3.10 要求 AGP 8.10.0 或更高。四项均满足。相关矩阵快照
日期为 AGP/Gradle 2026-07-17、Gradle/Kotlin 2026-06-28、KSP/AGP 2026-07-10、
KSP releases 2026-07-13、Java/Gradle 2026-03-06。

version catalog 也完成逐项盘点：Commons Compress 1.28.0、Foojay resolver 1.0.0、
JUnit 4.13.2、AndroidX test JUnit 1.3.0、runner 1.7.0、WebKit 1.14.0、XZ 1.12。
所有版本均精确锁定且离线解析成功；因为本轮没有刷新上游元数据，这一结论只表示仓库
健康和可复现，不声称这些 library 是上游最新版本。

执行记录：

```powershell
.\gradlew.bat help --offline --console=plain
.\gradlew.bat check --offline
```

两项均通过；默认 `check` 还会校验本记录与 wrapper、版本属性、兼容矩阵和 catalog
没有漂移。结论为 **PASS / 本轮无需升级**。WebView 不是 Gradle dependency，但属于
运行平台依赖，最低线与复核方法单独冻结在 [WebView 支持策略](WEBVIEW_SUPPORT.md)。

## 后续每次巡检

1. 新增一条 `dependency-inspections.json` record，保留旧记录，不原地改写历史结论。
2. 运行 `gradlew help --offline --console=plain`，把实际选中版本而非期望版本入档。
3. 核对 AGP→Gradle、Gradle→Kotlin、KSP→AGP、Kotlin→R8、Java→Gradle 五条关系；
   检查 catalog 不含 `+`、`latest.*`、SNAPSHOT 或无理由的 preview。
4. 若已授权刷新上游元数据，先单独提交/审阅矩阵 diff，再决定版本升级；升级后重新生成
   lock/产物，跑 `check --offline`、release APK 门禁和设备 smoke。
5. 同时按 [发布前检查清单](RELEASE_CHECKLIST.md)复核 WebView 最低线和固定 PSS 观察。
6. 巡检发现问题但本轮不升级时记录原因和 follow-up issue；没有远端权限时先写本地
   Roadmap/记录，不虚构 issue URL。
