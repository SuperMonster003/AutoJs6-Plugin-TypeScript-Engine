# TypeScript 6.x 升级手册

本手册覆盖插件当前 JavaScript compiler host 所支持的稳定 TypeScript 6.x 发行线。
它把候选发现、三摘要锁定、发行物与声明生成、缓存失效、真实 Worker 编译和设备
smoke 串成一个可复核流程。所有远端发布动作都不在本手册授权范围内; AutoJs6
宿主发布前, 不得创建 GitHub Release、上传 APK 或修改插件索引。

## 适用边界

- 只接受形如 ``6.x.y`` 的稳定版, 不接受 ``dev``、``beta``、``rc`` 或浮动 tag。
- TypeScript 7.x 使用不同的原生编译器架构, 不能按普通 distribution lock 升级处理。
  如需评估 7.x, 必须先单独决定 Android/WebView 装载方式、产物供应链、API 兼容面、
  内存预算和回滚方案。7.0.2 的 T3-4 原型已完成并因缺少 programmatic API 决定
  no-go；数据与重新开启条件见
  [T3 TypeScript 7 原生后端评估](../roadmap/T3_TYPESCRIPT_7_NATIVE_EVALUATION.md)。
- ``tools/prepare-typescript-upgrade.mjs`` 是唯一允许联网的准备步骤。它只读取 npm
  官方 registry, 将报告、候选 lock 和 tarball 写入已忽略的
  ``build/reports/typescript-upgrade/preflight/``, 不修改受版本控制的源码。
- 日常门禁仍是 ``gradlew check --offline``; distribution 获取完成后, 后续生成、测试
  和打包必须能够离线重放。

## 0. 建立干净、可回退的起点

先记录当前工作区, 确认没有把其他人的修改误算进升级:

```powershell
git status --short
rg -n "TYPESCRIPT_VERSION|typescript/[0-9]+\.[0-9]+\.[0-9]+" .
```

升级只改下文列出的当前身份与验证文件。历史 changelog、旧架构记录和已经冻结的
验收证据保留原版本号, 不做全仓机械替换。若工作区已有无关修改, 应避开重叠文件,
不要使用 ``git reset --hard`` 或删除整个 build/workspace。

## 1. 从官方 registry 准备候选

```powershell
node tools/prepare-typescript-upgrade.mjs --major 6
```

人工检查生成的 ``preflight-typescript-6.x.json``:

- ``latestStableVersion`` 必须是稳定 6.x;
- ``release.tarball`` 必须是
  ``https://registry.npmjs.org/typescript/-/typescript-<version>.tgz``;
- registry SHA-1、SHA-512 SRI 与脚本重新下载计算的值必须一致;
- 脚本独立计算的 SHA-256 必须存在;
- ``sourceMutation`` 必须是 ``none``;
- 若 ``status=already-current``, 不应为了制造版本变化而改 production lock。

如果存在更新, 对候选 lock 做人工 diff, 再把五个发行身份字段
``version/source/sha1/sha256/integrity`` 精确复制到
``compiler-distribution.lock.json``, 并同步
``version.properties:TYPESCRIPT_VERSION``。不要复制 npm 的浮动 ``latest`` tag。

## 2. 验证 compiler distribution

第一次获取候选 tarball 时显式允许运行唯一联网 acquisition task; 确认本地已有正确
tarball 后, 全部后续命令加 ``--offline``:

```powershell
.\gradlew.bat :app:verifyCompilerDistributionLock --console=plain
.\gradlew.bat :app:verifyTypeScriptDistribution --offline --console=plain
```

核对生成的
``app/build/generated/typescriptCompilerAssets/assets/typescript/<version>/distribution.json``:

- package/version 与 lock 一致;
- tarball 三摘要与 lock 一致;
- compiler、compiler API、标准库和总 distribution fingerprint 均由实际字节生成;
- 标准库 file count/total bytes 不靠手工猜测;
- allowlist 外文件没有进入 APK assets。

用旧、新 manifest 做结构化 diff, 把 compiler distribution fingerprint 记录进当次
升级审计。只要 compiler distribution 字节变化, 该 fingerprint 必须变化。

## 3. 重新绑定声明 distribution

将新 compiler manifest 的这些字段同步到
``r2-declarations.lock.json:common``:

- ``version``;
- ``distributionFingerprint``;
- ``standardLibrariesFingerprint``;
- ``declarationFileCount``;
- ``declarationTotalBytes``。

``r2-declarations.lock.json`` 是 canonical JSON: 必须保持单行、键按 canonical encoder
排序且文件末尾只有一个换行。随后运行:

```powershell
.\gradlew.bat :app:verifyR2DeclarationDistribution --offline --console=plain
```

声明指纹遵守内容身份, 不是版本计数器:

- 标准库声明字节变化时, common layer 与受其影响的 Rhino/Node profile fingerprint
  必须变化;
- 标准库声明字节完全相同时, profile fingerprint 必须保持不变;
- 即使 profile 不变, compiler version 与 compiler distribution fingerprint 已变化,
  宿主 semantic cache key 仍会自然失效;
- overall declaration distribution 可能因为 binding 中的 compiler version/fingerprint
  变化而变化, 这不等于 profile 声明内容变化。

## 4. 清理当前身份中的版本耦合

```powershell
rg -n "<旧版本>|typescript/<旧版本>|COMMON_.*FINGERPRINT|fileCount.*108" .
```

逐条分类:

- 必须更新: ``version.properties``、两个 production lock、当前 Worker/compiler authority、
  发行 smoke 的 expected compiler version、当前用户文档、当前发布说明和 Ace 版本一致性
  检查;
- 按实际 manifest 更新: 标准库数量/字节等 runtime defensive checks;
- 不更新: 历史 changelog、旧 Roadmap 结论、架构决策记录、旧版本验收证据;
- 测试 fixture 若故意代表旧版本, 应改名或加注释说明其历史用途, 不能伪装成当前身份。

声明生成器必须从经过验证的 compiler distribution manifest 与 declaration lock 推导
``typescript/<version>`` 路径和 common inventory。稳定 6.x 之外的版本必须 fail closed。

## 5. 验证缓存自然失效

插件响应中的 ``compilerVersion``、``compilerDistributionFingerprint``、
``declarationsFingerprint`` 和 ``declarationProfileFingerprint`` 都要与生成 manifest
一致。AutoJs6 宿主把 compiler version 与 distribution fingerprint 纳入 semantic
cache key, 因此即使声明 profile 未变化, compiler 升级也不能命中旧编译结果。

在同级宿主仓库执行冻结的 key 测试:

```powershell
Push-Location ..\AutoJs6
.\gradlew.bat :app:testAppDebugUnitTest `
  --tests "org.autojs.autojs.core.plugin.typescript.TypeScriptCompilationCacheKeyTest" `
  --offline --console=plain
Pop-Location
```

测试必须显式证明 compiler version 或 compiler distribution fingerprint 任一变化都会
产生不同 cache key。这里只读验证宿主, 不授权修改或发布宿主仓库。

## 6. 编译、打包与设备 smoke

先跑本仓库全部离线门禁:

```powershell
.\gradlew.bat :app:verifyCompilerSmoke --offline --console=plain
.\gradlew.bat check --offline --console=plain
.\gradlew.bat :app:assembleRelease `
  :release-smoke:assembleDebug `
  :app:verifyReleaseCompilerHostApk `
  :app:verifyReleaseTypeScriptDistributionApk `
  :app:verifyReleaseR2DeclarationDistributionApk `
  --offline --console=plain
```

``verifyCompilerSmoke`` 必须让 Rhino 与 Node profile 都通过真实 Worker ``Program``
编译。随后在至少一台受支持 WebView 的 reference device 安装签名 release target 与
同证书的 framework-only helper:

```powershell
adb -s <serial> install -r `
  app/build/outputs/apk/release/autojs6-plugin-typescript-engine-v<version>-universal.apk
adb -s <serial> install -r release-smoke/build/outputs/apk/debug/release-smoke-debug.apk
adb -s <serial> shell am instrument -w `
  io.github.supermonster003.autojs6.plugin.typescript.releasesmoke/.R1ReleaseSmokeInstrumentation
```

设备结果必须核对 service discovery、prewarm、compiler metadata 中的新版本/指纹、
零 diagnostics、JS、Source Map v3 与 formal output manifest。记录设备型号、API、
WebView provider/version、APK SHA-256 和 ``INSTRUMENTATION_CODE: -1``, 然后卸载
helper。若 compiler 版本实际变化, 不得复用旧 APK 的设备证据。

## 7. 记录、回滚与远端边界

- 更新 ``docs/maintenance/typescript-upgrades/YYYY-MM-DD.json``, 保存官方来源、
  三摘要、旧/新 distribution 与 profile 指纹、缓存测试、Worker/device smoke 和决策。
- 更新当前 README、Roadmap、发布说明与十语言 changelog 中真正面向用户的版本变化;
  用项目既有 changelog ``--check`` 门禁验证生成文件。
- 升级失败时, 只回退本次列出的 source/lock/doc 修改, 重新生成旧 distribution 并跑
  离线门禁。不要通过删除 Gradle cache、整个 build 目录或工作区来掩盖身份不一致。
- GitHub Release、APK 上传、插件市场/索引和公开下载链接必须继续等待 AutoJs6 宿主
  发布及 owner 明确授权。

## 2026-08-25 S10-3 演练

官方 npm registry 审计显示稳定 6.x 只有 6.0.2 与 6.0.3, 当前 production 6.0.3
已经是最新稳定 6.x; npm 总体 ``latest`` 7.0.2 越过本手册架构边界。因此 production
lock 未变, 改用隔离的前一稳定版重放真实升级:

```powershell
.\gradlew.bat :app:downloadTypeScriptUpgradeRehearsalTarball --console=plain
.\gradlew.bat :app:verifyTypeScriptUpgradeRehearsal --offline --console=plain
```

第一条命令只用于首次获取三摘要锁定的 6.0.2 baseline; tarball 验证落盘后, 第二条
命令必须离线重放。它与 production 6.0.3 分别生成 compiler 和 R2 declarations。
实测 compiler distribution fingerprint 从
``e2e90fd9f4ab1a8e2dbd2907f131828a7be3a04c2dd7105badc97d4664a4d5b9``
变为
``3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658``;
标准库声明 fingerprint 均为
``6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f``,
所以两个 profile fingerprint 按内容身份保持稳定。完整机器证据见
[2026-08-25 升级审计](typescript-upgrades/2026-08-25.json)。同一签名 v0.6.0
APK (SHA-256
``765b3068aeb4cdd52580e45540e987b8ba6b86500e928f7deee236eb3d25b98a``)
已在 API 35 / WebView 130 小米真机与 API 37 / WebView 145 reference emulator
通过 release smoke, 两端 helper 均已清理。

上述 APK 哈希是 S10-3 演练发生时的不可变设备审计快照。随后 S10-1 修改 production
input batching 并重建同版本最终候选，当前发布哈希以 `docs/releases/v0.6.0.md` 为准；
历史升级 JSON 不反向改写。compiler/declaration lock 与本次演练结论没有因此变化。
