# T1-2 Controlled tsconfig Revision 3

状态：2026-08-26 已实现并进入日常 `check`。本文冻结 T1-2 revision 3 权威；T1-3
已 append-only 发布 revision 4，当前权威见
[T1-3 Controlled `baseUrl`/`paths` Revision 4](CONTROLLED_PATH_ALIASES_T1_3_REVISION4.md)。
R2-M2-C1/C2/C3 继续作为 revision 2 的历史冻结文档，不回写其当时结论。

## 决策

revision 3 采用 append-only 协议升级，不改变 `configSchemaRevision=2`。当前 Rhino
revision 2 与 NodeNext revision 2 都可以携带根 `tsconfig.json`；缺少配置时仍使用
`configKind=none` 和各自 profile 默认值。

| 身份 | profile | 布尔项 | 值类型项 | 状态 |
|---|---|---:|---|---|
| policy revision 2 | Rhino | 18 | 无 | 永久兼容，原 fingerprint 不变 |
| policy revision 2 | NodeNext | — | — | 拒绝为 contract mismatch，不做歧义升级 |
| policy revision 3 | Rhino | 20 | `target`、`lib` | 兼容；仅 ES2018 |
| policy revision 3 | NodeNext | 20 | `target`、`lib` | 兼容；ES2018–ES2022 |

revision 3 policy fingerprint：
`1f77d37080466f2752176fb5aa1b96e9a3f3dbdc89b853f30b4a0fed4313e90a`。

revision 2 policy fingerprint：
`64ef1cf36d6531f84088cec49127e6407b107ab86eeaf7f4512358fd1343598b`。

revision 2 默认 effective-options fingerprint：
`5b529b507e94fafe30ccce6de4d69f89604bc0f1430b7b4ee8f12259fe03a807`。

## 选项语义

revision 3 在 revision 2 的 18 个布尔项上追加 `removeComments` 与
`preserveConstEnums`。`target` 接受 profile 白名单内、不区分大小写的 ES 年份；
`lib` 接受同一白名单内的非空数组，去重并按 code-unit 排序，内部规范化为
`lib.es20xx.d.ts`。`lib` 省略时跟随 `target`，但显式数组不要求每项小于或等于
`target`。

Rhino 的 target/lib universe 只有 ES2018。Node 的 universe 是 ES2018、ES2019、
ES2020、ES2021、ES2022。标准库 resolver 只从锁定的 TypeScript distribution 读取
对应 ECMAScript lib 及其引用闭包，不引入 DOM 或 caller 路径。

revision 3 从历史 profile-managed/escape/output 集合中分别移出 `target`、`lib`、
`removeComments`。在本 revision 中，`paths`、`baseUrl`、`outFile`、compiler plugin 与
transformer 等结构性能力仍按既有稳定 detail code 拒绝；其中 `paths`/`baseUrl` 后由
revision 4 正式开放，不回写 revision 3。未知非结构性项仍产生
`910001`/`910002` warning 后忽略。

## 身份与传输

policy revision/fingerprint、原始 config digest、规范化 effective options 和其
fingerprint 都进入 snapshot、archive、Worker start/result、CompilerHostProtocol 与
缓存身份。Node 的 controlled-config start 固定为 config-first 34-field 形状；Rhino
保持 27-field 形状。宿主只接受精确的 revision/fingerprint 组合：不存在“看到未知
revision 就用最新解析”的路径。

示例 effective-options fingerprints：

- Rhino ES2018：
  `780e50f808f2eb399b794ed9a8f4e53c2aacfa01a75671e8fa05485f304eff85`
- NodeNext ES2022：
  `1a55e601b7c5b55764dc2219afc0320f99c9ada39b1d0dfa98d78687b338a52d`

## 验证

`verifyT1ControlledTsConfigRevision3` 独立重建 canonical policy 并校验 fingerprint，
随后通过实际 framed Worker 协议执行两次完整 `Program` 编译：

- Rhino 选择 `lib.es2018.d.ts`，验证 `removeComments=true`、
  `preserveConstEnums=true`、warning-ignore、JS/map 配对，并执行 CommonJS 得到 `14`；
- NodeNext 选择 `lib.es2022.d.ts`，验证 ES2022 class fields、保留注释、
  `preserveConstEnums=true`、`.cts -> .cjs`、JS/map 配对，并执行 CommonJS 得到 `42`。

同一门禁还验证 Rhino ES2022、Node ES2023 lib、`paths` 的稳定拒绝，以及 revision 2
解析、默认 effective fingerprint 和 Rhino-only admission。Kotlin 单元测试另冻结
snapshot/archive/public admission、Node 34-field transport、metadata advertisement 与
CompilerHost result 解析。该任务挂在 `verifyCompilerSmoke` 下，因此根项目日常
`check --offline` 会自动执行。

API 35 Xiaomi 23046RP50C / WebView `130.0.6723.86` 另通过两条公开 Binder 验证：
Rhino rev3 的 strict/warning-ignore 与 NodeNext ES2022 target/lib/const-enum emit。首次
运行定位到 output archive authority 的 Rhino-only 残留条件；修复后 Node `.cts` 产出
`.cjs + map`，manifest 中的 profile/config/effective fingerprint 与请求逐项一致，两条
测试均返回 `INSTRUMENTATION_CODE: -1`。

用户可见配置清单与兼容说明见
[配置与配额参考](../user/CONFIGURATION.md)。
