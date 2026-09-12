# 依赖健康巡检

## 固定节奏

每年 1/4/7/10 月各检查一次；若某个 release candidate 距上一条记录超过 90 天，发版
前补跑。巡检分成两个明确阶段：

1. **中央决策复核（插件已缓存时可离线）**：以根 settings 应用的中央平台插件、
   Gradle wrapper、`version.properties` 和 version catalog 为权威。Gradle 将平台插件
   已经决定的 AGP/Kotlin/KSP/R8 模式及 Java/JVM target 传给维护门禁，仓库不再保存或
   解析自己的 `gradle/data` 兼容矩阵。
2. **上游元数据刷新（需单独授权联网）**：只在
   `AutoJs6-Gradle-Platform-Versions` 仓库运行 scrape/inject 工具、审阅官方来源 diff
   并发布新的不可变插件版本。消费仓只在新版本发布后升级 settings 中的插件版本；
   未刷新时不得把结果描述为“互联网上的最新版本”。

默认只选择 stable；alpha/beta/RC 必须有明确实验目标，不替换正式构建基线。升级组合
顺序为 Gradle wrapper → AGP → Kotlin → KSP/R8 → JDK/SDK → catalog libraries，任何
一步不满足内置矩阵就停止，而不是靠关闭版本检查继续。

## 2026-09-02 中央平台决策复核

模式：本地、离线、central platform decision。当前权威为
`io.github.supermonster003.autojs6-platform-versions:1.7.0`；消费仓的维护门禁直接接收
Gradle 配置阶段已经解析出的版本，不复制中央兼容数据，也不自行重算版本组合。

实际中央决策为：

| 组件 | 版本 | 消费仓结论 |
|---|---:|---|
| Gradle wrapper | `9.6.1` | 当前 wrapper |
| Android Gradle Plugin | `9.4.0` | 中央平台插件决策 |
| Kotlin Gradle Plugin | `2.3.21` | 中央平台插件决策 |
| KSP | `2.3.11` | 中央平台插件决策 |
| R8 | `AGP-bundled` | 中央决策未注入外部 R8 classpath |
| JDK / JVM target | `21` / `21` | 仓库约定的实际选择 |
| compileSdk / targetSdk / minSdk | `36` / `36` / `24` | 当前项目属性 |

机器可读记录见 [dependency-inspections.json](dependency-inspections.json)。默认维护门禁
会将其中最后一条记录与本次 Gradle 传入的实际决策逐项比较；中央数据变化导致选择变化
时，先验证构建，再追加新记录并更新本节。兼容矩阵的来源、抓取日期和约束关系由中央
平台仓库测试，不在消费仓重复维护。

执行记录：

```powershell
.\gradlew.bat help --offline --console=plain
.\gradlew.bat check --offline
```

两项通过时，结论为 **PASS**。离线运行仍要求平台插件及构建依赖已在 Gradle 缓存中，
不应把缓存命中描述为消费仓拥有兼容数据副本。

## 2026-08-25 首次仓库快照巡检记录（历史）

模式：本地、离线、repository compatibility snapshot。网络请求、远端发布和依赖版本
修改均为 0。此段保留当时以消费仓矩阵复核的历史证据，不再代表当前权威来源。机器可读
记录见 [dependency-inspections.json](dependency-inspections.json)。

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
2. 运行 `gradlew help --offline --console=plain`，把中央平台插件实际选中的版本而非期望
   版本入档；不要从平台仓库复制兼容矩阵到本仓库。
3. 检查 catalog 不含 `+`、`latest.*`、SNAPSHOT 或无理由的 preview。AGP→Gradle、
   Gradle→Kotlin、KSP→AGP、Kotlin→R8、Java→Gradle 的关系由中央平台仓库维护和测试。
4. 若中央平台插件发布新版本，先审阅其 release/data diff，再升级 settings 中的插件版本；
   升级后重新生成 lock/产物，跑 `check --offline`、release APK 门禁和设备 smoke。
5. 同时按 [发布前检查清单](RELEASE_CHECKLIST.md)复核 WebView 最低线和固定 PSS 观察。
6. 巡检发现问题但本轮不升级时记录原因和 follow-up issue；没有远端权限时先写本地
   Roadmap/记录，不虚构 issue URL。
