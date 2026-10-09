# 冷启动与 ingress 观察

本页冻结 S10-1 的优化决策、四设备 A/B 数据和可重复观察方式。它只讨论插件内部
`compiler` 与 `ingress` 阶段，不把一次局部观测包装成端到端启动门禁；所有记录均为
`gate=none`，不设 p95 或固定毫秒阈值。

## 结论

production project input 仍以 64 KiB raw chunk、window=1 envelope/ACK 传输，但中间
`projectInputBatch` 从“最多 4 帧”调整为：

- 最多 6 条 config/file middle frame；
- 其中最多 4 条可以是 `projectInputConfigChunk` / `projectInputFileChunk`；
- 每条可批处理 middle frame 的编码尺寸最多 92 KiB；
- start/end 继续单独发送，外层 hard envelope 继续为 576 KiB；
- result transport、logical frame exact shape、sequence、digest、UTF-8/token/quota、cursor
  ownership、AIDL、archive/result schema 和 profile fingerprint 均不变。

固定标准库 workload 为 109 files、超过 3 MiB、379 logical frames。它从 97 个
envelope 降到 68 个（-29.9%），最大实测 envelope 从 350201 B 变为 350433 B：只增加
232 B（约 0.07%），仍是同一约 350 KiB 尺寸级别并明显低于 589824 B hard limit。
Android native、compiler page 与 Worker 三层都独立执行 6/4 边界；五个 payload chunk
会在进入原 frame handler 前失败并清理当前 operation。

## 为什么不是直接把上限设为 6

首个候选允许一批包含 6 个 64 KiB payload chunk，最大 envelope 达 525251 B。API 31、
35、37 有改善，但 API 36 / WebView 134 的 7-run 对照出现明显回退。随后固定 5 帧在
该 AVD 上也没有改善：

| API 36 候选 | compiler median | ingress median | 相对 max4 |
| --- | ---: | ---: | ---: |
| max4 基线 | 2656 ms | 1316 ms | — |
| 固定 max5 | 2826 ms | 1375 ms | +6.4% / +4.5% |
| 固定 max6 | 4350 ms | 1963 ms | +63.8% / +49.2% |
| 最终 max6 total / max4 payload | 2468 ms | 1204 ms | -7.1% / -8.5% |

因此最终方案只合并 file/config start/end 等小控制帧，不引入第五个大 payload chunk。
它保留减少 ACK 往返的收益，同时避开旧 provider/AVD 对约 525 KiB WebMessage 的高方差
路径。

## 四设备观测

表中是每个版本同设备奇数次样本的中位数；原始样本、采样次数与捕获方式保存在
[cold-start-observations.json](cold-start-observations.json)。负数表示时间下降。

| 设备 / active WebView | max4 compiler → 6/4 | max4 ingress → 6/4 | 改善 |
| --- | ---: | ---: | ---: |
| API 31 Sony XQ-AT72 / 145.0.7632.120 | 2025 → 1991 ms | 1166 → 1128 ms | -1.7% / -3.3% |
| API 35 Xiaomi 23046RP50C / 130.0.6723.86 | 7069 → 6671 ms | 5853 → 5434 ms | -5.6% / -7.2% |
| API 36 x86_64 AVD / 134.0.6998.135 | 2656 → 2468 ms | 1316 → 1204 ms | -7.1% / -8.5% |
| API 37 x86_64 AVD / 145.0.7632.218 | 2572 → 2247 ms | 1316 → 1133 ms | -12.6% / -13.9% |

API 36 的 raw 样本仍显示明显调度方差，所以这里不声称稳定 p95；验收结论仅是四个
Android/WebView 组合的中位数均改善。API 37 另在同一空闲 AVD 上临时重建 max4，完成
受控对照后恢复最终 6/4 构建。

## 与宿主 schema 2 的关系

S5-5 的宿主 `typescript-compile-perf schema=2` 先定位出 bind + prewarm 与 compiler
ingress 是主要冷路径。本轮 AndroidTest 在插件内记录
`typescript-cold-start-perf schema=1`，使用同一
`R2ProjectExecutionEvent.Phase` 的 ingress/parse/semantic/emit started/completed 时钟，
并额外记录 logical frames、envelopes 与最大编码 envelope。因此这里可隔离验证 transport
变化，却不声称 bind + prewarm、宿主 archive 准备或输出处理也按相同比例改善。

本轮未修改 sibling AutoJs6 checkout。尝试重建宿主 AndroidTest 时，被该工作区中与本
项目无关的既存 Python Binder `onHeartbeat(ByteArray)` 接口漂移阻断；没有为了取得一条
端到端日志而改写宿主用户改动。宿主恢复可构建后，可按相同设备再补 schema 2 总耗时，
但这不影响插件 ingress A/B 的方向性结论。

## 其他候选的取舍

- 声明按需注入暂不实施。受控 tsconfig 虽不开放 `lib`，源码仍可通过 triple-slash lib
  reference 选择标准库；在完整 Program 解析前推断“未引用声明”会改变正确性边界。
- `SourceFile` 已在单次 job 内 lazy-create/cache。成功或失败后 exact WebView realm 必须
  退休，跨 job 复用 JavaScript `SourceFile` 会与现有隔离/清理契约冲突；除非先建立新的
  immutable compiler realm 设计，否则不以牺牲 teardown 证明换取冷启动数字。
- 576 KiB hard envelope 不上调。当前方案在原约 350 KiB 尺寸级别取得收益，没有理由
  扩大 native-to-page 单消息风险面。

## 复跑

以下脚本构建本地 debug/test APK、安装到明确指定的验证设备、串行执行奇数次固定
workload，并把 JSON 与原始输出写入 ignored `build/reports/cold-start/`：

```powershell
.\tools\observe-cold-start.ps1 `
  -Serial emulator-5554 `
  -DeviceLabel api37-reference `
  -Runs 5 `
  -AllowInstall
```

脚本会替换目标设备上的插件 APK，结束时只卸载 AndroidTest helper，不会自动恢复原
release APK；因此只能用于专用验证设备。比较时必须保持 device label、Android API、
WebView 完整版本、fixture 和 build type 一致。明显回退应先保存 raw report、重复复跑，
再决定回退 total-frame 上限；不得删除四 payload/hard-envelope 防线来追求单次快样本。

## 执行前耗时基线 (startup-latency, 2026-10-09 起)

本页以上内容只覆盖插件内部 `compiler` / `ingress` 阶段. 用户真正感知的 "点运行到脚本开始
执行" 还包含宿主发现, 归档准备, 专用绑定与 prewarm, 输出处理, 引擎创建, 以及 compiler
进程自身的冷启动. 这些端到端数据按
[startup-latency-roadmap.md](../../startup-latency-roadmap.md) 的三场景口径采集:

- S1 全冷: compiler 进程不存在且脚本内容已改动 (持久缓存未命中);
- S2 热路径: 进程存活且宿主绑定保留, 修改一行后 30 s 内再运行;
- S3 缓存命中: 未修改, 立即重复运行.

采集工具是 `tools/observe-startup-latency.ps1`, 它不安装或卸载任何 APK, 只推送
`tools/fixtures/startup-latency/single-file.ts` 并通过宿主导出的 `RunIntentActivity` 运行,
然后读取四条结构化 logcat 行: 宿主 `typescript-compile-perf schema=3`,
`typescript-execution-perf schema=1`, `typescript-prewarm schema=1`, 以及插件
`typescript-compiler-boot schema=1` (进程级一次, 字段为自进程启动起的毫秒偏移: 服务
onCreate, 声明层加载, 监督者注册, WebView 创建, 页面就绪, Worker 就绪与 Worker 内
`importScripts("typescript.js")` 耗时). 同一次运行窗口内出现 boot 行即判定该次为冷进程.

```powershell
.\tools\observe-startup-latency.ps1 `
  -Serial 968e9f18 `
  -DeviceLabel api35-23046rp50c `
  -Runs 5
```

2026-10-09 的 L0 基线 (Xiaomi 23046RP50C / API 35 / WebView 130, release 宿主 6.8.0/5328 与
插件 0.6.8/91, 单文件 Rhino fixture) `userWaitMs` p50: S1 9864 ms, S2 4348 ms, S3 137 ms;
S1 中 discovery 1796, bind + prewarm 1490, compiler 6455 (beforeIngress 1148, ingress 2888,
parse 1433, semantic 797, emit 114) ms. 这与本页上方 S10-1 的结论一致: 声明层传输仍是
最大单项, 其后是 compiler 进程冷启动与每 job 的声明重载.

原始报告写入 ignored `build/reports/startup-latency/`; 入库记录放在
[startup-latency-observations.json](startup-latency-observations.json), 每条记录保留全部
原始样本与中位数, 只追加不改写, 仍为 `gate=none`. 同设备中位数相对上一记录劣化 > 15%
视为回归, 先保存原始报告复跑再决定, 不以放宽校验或传输防线换取数字.
