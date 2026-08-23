# S1-6 Daily Verification

状态：**完成（2026-08-23）**。

本切片把日常 `check` 从历史 release-hardening 证据总线恢复为开发反馈门禁。所有旧任务定义、
fixture 与手动复核入口均保留；变化只发生在默认 lifecycle task 的最终依赖图，不删除既有证据。

## 默认图

Android Gradle Plugin 会延迟向各 module 的 `check` 附加依赖，因此根构建在
`gradle.projectsEvaluated` 后执行最终替换。无前缀运行：

```text
gradlew check --offline
  ├─ app + typescript-api debug unit tests
  ├─ verifyCompilerDistributionLock
  │    ├─ pinned TypeScript distribution generate + byte verification
  │    └─ pinned Rhino/Node declaration distribution generate + byte verification
  ├─ verifyTypeScriptApiV1Freeze
  └─ verifyCompilerSmoke
       ├─ one Rhino Worker compile: 2 x .ts -> 2 x .js + paired maps
       └─ one exact Node rev2 Worker compile: .mts/.cts -> .mjs/.cjs + paired maps
```

`common-plugin-api`、`r1-compat-v1`、`r2-public-preview` 与 `release-smoke` 当前没有单元测试源；
它们的 `check` lifecycle task 因此不再隐式编译、lint 或组装 fixture。AIDL freeze 自身仍需构造
release AAR 以逐项比较 frozen `classes.jar`，这些是该门禁的必要输入，不是恢复了 release matrix。

应用单元测试需要生成后的 declaration assets，两个 Worker smoke 需要生成后的
`typescript.js`。为避免多个日常任务各自拥有供应链入口，它们统一依赖
`verifyCompilerDistributionLock`；该任务同时校验 lock metadata、下载所得 tarball digest 与
生成目录的逐字节 canonical 内容。这是默认图中唯一允许触发 pinned distribution 获取的门禁。
`--offline` 禁止普通 Gradle repository resolution 访问网络；若 project-local pinned tarball 尚未
生成，上述 supply-chain gate 仍是刻意保留的唯一网络例外。

## 保留的手动证据

历史任务继续按原名注册。例如：

```text
gradlew :app:verifyR2M2X3StableConfigurationAndResolutionFailures --offline
gradlew verifyR2PublicPreviewFixture --offline
gradlew :app:lintDebug --offline
gradlew :app:assembleRelease --offline
```

它们不再被任一 module 的默认 `check` 引用。需要 release、APK、外部 UID、设备时序或特定历史
契约证据时显式调用对应任务即可。

## 验收证据

在 Windows 11 / JDK 21 / Gradle 9.6.1 环境，先执行 Gradle `clean`，再删除 project-local
`.gradle`、`.kotlin`、`app/.cxx` 与 build-logic outputs，随后运行
`gradlew check --offline --console=plain`：

- 116 个 actionable tasks，116 个 executed；
- Rhino smoke 与 Node revision-2 smoke 均通过；
- app 与 typescript-api 单元测试通过；
- distribution lock 与 AIDL/API freeze 通过；
- 总耗时 28.1 秒，低于 Roadmap 的 5 分钟上限；
- dry-run 中无 lint、APK assembly、device task 或 `verifyR2M*` task。

因此六个必需 S1 切片已闭环；`S1-7` 仍是宿主联调时才决定是否需要的可选握手简化，当前活动
路径进入 S2 AutoJs6 宿主最小集成。
