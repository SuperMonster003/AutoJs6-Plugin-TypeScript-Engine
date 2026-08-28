# TypeScript issue 分诊手册

本手册把用户或维护者反馈收敛为“接收 -> 脱敏 -> 最小复现 -> 分类 -> 修复 -> 验证
-> changelog 决策 -> 关闭”的轻量闭环。仓库中的
`.github/ISSUE_TEMPLATE/typescript-bug.yml` 目前只是本地候选文件; 在 AutoJs6 宿主
发布且 owner 授权远端操作前, 不创建 GitHub issue、不推送模板、不上传附件。

## 1. 接收与去重

每条反馈先取得这些稳定身份, 缺项时继续询问而不是猜测:

- AutoJs6 版本/build、TypeScript 插件版本、Android API、active WebView package 与完整
  版本;
- Rhino 或 Node profile、入口扩展名、最近 `package.json` 的 `type`、是否使用受控
  `tsconfig.json`;
- 稳定 `ERR_AUTOJS6_TYPESCRIPT_*` error code、config detail code 或 Node resolution
  policy/diagnostic code pair;
- 实际/预期行为、首次异常版本、缓存启用/关闭结果、最小复现步骤;
- 脱敏 diagnostics 与对应的 `typescript-compile-perf schema=2` 记录。

错误码解释以[错误码目录](../user/ERRORS.md)为准, 不用易变的 message 文本充当 issue
身份。相同 stable code、profile、版本边界和最小复现归入同一 issue; 只有 message
相似而身份不同的反馈不能直接合并。

## 2. 安全采集证据

原始 `diagnostics.json` 的 message 与 URI path 可能含用户标识符或文件名; 完整
logcat 更可能含其他应用信息。二者都不能直接上传。先把原始文件放在已忽略的 build
目录, 再运行本地采集器:

```powershell
adb -s <serial> logcat -d -v threadtime `
  > build/reports/issue-triage/logcat.txt

node tools/collect-typescript-issue-evidence.mjs `
  --diagnostics <path-to-diagnostics.json> `
  --logcat build/reports/issue-triage/logcat.txt
```

采集器:

- 不联网、不调用 adb、不读取源码或 Source Map;
- 只接受 formal `autojs6.typescript.diagnostics/revision=1`, 上限 512 KiB;
- 删除 request/project identity、完整 URI path 和所有 diagnostic message, 只保留
  profile/revision、phase、`sources/declarations/packages` domain、range、category、
  numeric code 与稳定 resolver policy code;
- 从 logcat 前缀后只提取 exact `typescript-compile-perf schema=2` 或固定 PSS
  `typescript-compiler-perf schema=1` key set; 未知、缺失、重复 key、路径式 value 或
  夹带 request ID 的行一律丢弃;
- 只向 `build/reports/issue-triage/<timestamp>/` 写
  `diagnostics.redacted.json`、`typescript-performance.log`、`manifest.json` 和检查
  说明。显式 `--output-dir` 也只能选择该已忽略目录的子目录。

分享前仍须人工打开每个生成文件。若问题必须靠源码复现, 另做最小项目并将账号、
token、URL、业务名称、设备 ID 和私有路径替换为占位值。不要上传完整 APK、Source
Map、未经检查的 logcat 或原始 diagnostics。

## 3. 复现与分类

复现记录固定包含:

1. 最小文件树、入口和受控 tsconfig;
2. exact AutoJs6/plugin/TypeScript/WebView identity;
3. 冷缓存与热缓存各一次, 并记录是否跨 profile;
4. 一条失败命令或 UI 操作序列及 stable terminal code;
5. 预期的 diagnostics/JS/map/manifest 行为;
6. 可自动执行的 regression test, 或无法自动化的明确理由。

| 分类 | 判断 | 下一步 |
|---|---|---|
| expected boundary | 受控 tsconfig、npm 裸包、`packages/*` 外的任意 package 拓扑、配额或 WebView 119 下限按契约 fail closed | 指向文档; 若提示不清楚则另开 docs/UX improvement |
| compiler regression | 相同 snapshot/config 在旧版成功、新版失败或 emit/diagnostics 身份改变 | 最小 fixture + compiler/host 层测试 |
| host integration | 插件 direct smoke 成功, AutoJs6 路由、缓存、诊断跳转或栈映射失败 | 在宿主复现并验证跨仓 cache/route test |
| environment | 只在特定 API/WebView/provider/signing/install 状态出现 | 加入支持矩阵, 不用 Android API 替代 WebView identity |
| performance | 功能正确但 schema 2 阶段或 PSS 历史出现可重复变化 | 同设备/fixture 复跑; `gate=none` 时不凭一次采样阻断 |
| maintenance/tooling | distribution、声明、打包或验证图阻止安全维护 | 保持供应链/内容身份约束, 用隔离 fixture 修复 |

分类后给出 severity、影响版本/profile、owner 与下一证据, 不用“可能是 WebView/缓存”
作为结论。

## 4. 修复、验证和 changelog

修复前先提交能稳定失败的最小 fixture 或说明为何只能做设备测试。修复不能为了让
issue 消失而放宽 archive path、digest、schema、quota、profile 或 capability 门禁。
至少执行与风险相称的组合:

```powershell
.\gradlew.bat <targeted-test> --offline --console=plain
.\gradlew.bat :app:verifyCompilerSmoke --offline --console=plain
.\gradlew.bat check --offline --console=plain
```

涉及 release、签名、WebView 或设备特有行为时, 还要在受影响矩阵复跑同一签名 APK
并记录 `INSTRUMENTATION_CODE: -1`。涉及宿主缓存/路由时, 在同级 AutoJs6 仓库执行
对应冻结测试, 但不据此获得修改或发布宿主的额外授权。

changelog 结论必须显式写入 issue:

- 用户可感知 bug 修复 -> 当前版本 `fix`;
- 用户可感知能力或行为改善 -> `feature` / `improvement`;
- Gradle `implementation` 等依赖变化 -> `dependency`;
- 仅测试、内部维护工具、无 production 行为/产物变化 -> `none-maintenance-only`,
  不伪造用户 changelog, 但在 Roadmap/维护记录保留证据。

需要 changelog 时更新十语言 `lang_*.json`, 运行生成脚本并通过 `--check`。关闭前把
测试结果、设备/版本矩阵、回归范围、隐私检查和 changelog 决策逐项勾清。

## 5. 本地真实闭环

[LOCAL-TS-2026-08-25-01](issue-triage/LOCAL-TS-2026-08-25-01.md) 记录了 S10-3
演练发现的真实 maintenance issue: declaration generator 把 6.0.3 identity 重复硬编码,
导致三摘要锁定的 6.0.2 baseline 无法进入声明生成。该记录保留原始失败、最小复现、
根因、修复、回归测试、API 35 真机/API 37 emulator smoke 和
`none-maintenance-only` 决策, 未创建远端 issue。
