******

### 發行歷史

******

# v0.6.0

###### 2026/08/28

* `提示` 項目編譯現已面向 AutoJs6 6.8.0 Alpha7 (build 5253) 或更高版本啟用; Node profile 另需 Node.js Runtime 插件, Rhino profile 不需要
* `提示` 編譯與執行現共用啟動時的一份精確 snapshot; npm 裸包/scoped package 可於編譯期解析, 並透過 Rhino CommonJS 或 Node.js Runtime 插件在執行期載入
* `提示` Android 7.0 / API 24 需要 Chrome/WebView 119 或更高版本; API 24/25 系統預載的舊 provider 不受支援並會 fail closed
* `新增` 新增 dependency-layer policy revision 2 及 resolver policy revision 4: 宿主 archive 和 production Worker 以固定次序拒絕 `.node`, `binding.gyp`, `gypfile` 及安裝生命週期 hook, 傳回穩定 native/install-script 錯誤及純 JavaScript/WASM 替代指引
* `新增` 離線真實套件驗收矩陣在 Rhino 與 Node profile 覆蓋 dayjs 1.11.13, lodash 4.17.21, zod 4.1.5, uuid 13.0.0 及 nanoid 5.1.5; resolver policy revision 3 現可由執行階段 JavaScript 回退至已安裝的 `@types`, 且不會覆蓋套件自帶宣告
* `新增` 同步編譯器與 Ace 的依賴套件類型: package `types`/`typings`, TypeScript 6 `typesVersions`, 巢狀聲明匯入及已安裝 `@types` 現進入同一凍結類型層; dayjs 補全, hover 與 strict TS2322 診斷在 Rhino 和 Node profile 之間一致
* `新增` 同時啟用 `compilerAvailable`, `projectCompilation`, `semanticDiagnostics` 和 `sourceMapV3` (capability mask 15), 提供完整 TypeScript Program 編譯, 結構化診斷與配對的外部 Source Map v3 輸出
* `新增` 新增公開 Rhino `.ts/.tsx` 項目編譯, 支援 UTF-8 路徑與原生 AutoJs6 TSX UI lowering; controlled tsconfig revision 3 同時涵蓋 Rhino 和 NodeNext, 提供 20 個 boolean 選項及按 profile 限定的 `target`/`lib`, revision 2 Rhino 請求保持相容
* `新增` 新增公開 NodeNext revision 2 編譯, 支援 `.mts/.cts` 與項目範圍內的 `.ts`, 並按源碼種類和 package type 產出 `.mjs/.cjs/.js`
* `新增` 編譯器已接入 AutoJs6 執行鏈, 可點擊的編譯診斷與運行時 stack mapping, 多文件項目, dynamic import 映射, Tasker/排程入口及 APK Builder 加密項目記憶體流程; 打包產物只含編譯後的 JS/map, 不含 TS 源碼
* `新增` Ace 編輯器的 declarations 與 TypeScript 6.0.3 行為現已和執行前編譯保持一致
* `新增` controlled tsconfig revision 4 為 Rhino 與 NodeNext 新增項目內 `baseUrl`/`paths` 路徑別名; 解析只限凍結 snapshot, 生成 specifier 會改寫為明確的運行時相對模組, revision 2/3 身份保持精確相容
* `新增` Node ESM `.mts` 與根 `type: module` `.ts` 項目現已支援頂層 `await`; controlled ES2022 配置會產出帶配對 Source Map 的 `.mjs/.js`, 可由 Node.js Runtime 插件直接執行
* `新增` 新增受控 Node workspace package boundary, 支援 `packages/<name>` 與 `packages/@scope/<name>`; 同一項目的 `.ts` 可按最近 `package.json.type` 混用 ESM 與 CommonJS, `scripts` 繼續禁止
* `新增` 兼容 Node Runtime 現以 V8 native linker 執行 ESM 入口與 dynamic `import()`, 支援循環依賴, 可變匯出及 re-export live binding; 四模組 `.mts` 圖可由 `initial=1` 更新為 `updated=42`
* `新增` 新增獨立配額的唯讀 `node_modules` 依賴層與 bare/scoped package 解析, 支援子路徑 `exports`, `types`/`import`/`require` 條件及 `types`/`typings`/`module`/`main`/`index` fallback, 並以 dayjs + zod 完整 Program 驗收; 同一精確依賴層現可進入執行期, Rhino 選擇 CommonJS/JSON `require` 入口並為 ESM-only 提供穩定錯誤, Node 透過不回寫依賴的私有 workspace overlay 執行 ESM/CJS/dual package
* `優化` 新增確定性的跨執行 cache 復用; 實測命中耗時 18-63 ms, 有界輸入 batching 在觀測裝置上降低 ingress 成本 40%-57%
* `優化` 普通輸出上限提升至 512 artifacts, 單個 JavaScript 或 map 16 MiB, 合計 64 MiB; 普通 caller 輸入升級至 revision 3 的 4096 files, 單文件 8 MiB, 合計 64 MiB, 插件內置 declarations 不佔 caller 配額, revision 2 舊宿主繼續兼容 512 files / 8 MiB
* `優化` 冷項目 ingress 進一步採用最多 6 條中間 frame 且最多 4 條 payload chunk 的 batching; 固定 109-file 測試由 97 降至 68 個 envelopes, 四組 Android/WebView 的 compiler 與 ingress 中位數均有改善

# v0.5.0

###### 2026/07/27

* `提示` R1 全部退出門禁現已完成, 包括 API 24/25 Chrome 119 modern-provider runtime matrix; `compilerAvailable=false` 仍為權威狀態, 因為生產項目編譯屬於 R2
* `修復` service destruction 先完成 WebView, Worker, MessagePort, ServiceWorker client, pending request 和 Handler callback 清理, 再發佈 close report 並喚醒等待方; 同時消除 deadline 與終態 publication 的競態
* `修復` `/proc` 與 FD 觀測綁定 `(pid,starttime)`, 對不可用或部分 shell 輸出 fail closed, 並避免程序退出輪詢觸發 UiAutomation finish 競態
* `優化` Android 12 和 Android 13 真機各驗證 10 個 cold compiler process 與 100 次 hot prewarm; compiler service FD 最終精確回到 baseline, 且不殘留 staging archive
* `優化` API 33 與 WebView 109 通過 none, Node-only, Ace-only 和 Node+Ace 四種安裝態; 靜態門禁禁止 production source, manifest 和 direct dependency 發現或依賴 Node/Ace

# v0.4.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.4.0 中仍為權威狀態: API 24 僅通過 Bundle/FD transport 門禁, 舊 WebView 無法解析原始 TypeScript 6.0.3 compiler JavaScript; API 24/25 modern-provider matrix 仍待完成
* `修復` Binder 在反序列化前拒絕 Bundle raw transport 中的 FD, 並在 prewarm, compilation 和 cancellation 拒絕路徑釋放傳輸副本
* `修復` Bundle metadata 驗證僅接受實際 runtime type 為 `String[]` 的字串陣列, 並拒絕頂層 null value, Parcelable array, Binder, Serializable 和 nested Bundle
* `修復` 以 epoch 合併 timeout recovery 執行, 防止 stale recovery 銷毀替換 session; 確定性連續 1 ms timeout 門禁驗證排隊 probe 可恢復
* `優化` Android 12 和 Android 13 真機通過真實 WebView renderer 退出門禁: 首次退出只重試一次, 第二次退出穩定失敗, 隨後的普通 probe 可恢復
* `優化` Android 12 和 Android 13 真機各完成 10 輪 `:typescript_compiler` 進程重啟並保持 distribution/declarations fingerprints 穩定; API 24 另通過 6 項 Bundle/FD transport 門禁

# v0.3.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.3.0 中仍為權威狀態: R1 prewarm 僅執行受限 compiler host golden 探針, R2 完成前仍不可進行項目編譯
* `新增` 獨立 `:typescript_compiler` 進程中的 WebView/Worker compiler host 使用私有 HTTPS asset origin, 精確資源 allowlist, WebMessagePort 和收緊的 network, file, content, storage 及 window 權限
* `新增` R1 prewarm 實際載入 TypeScript 6.0.3 並驗證 runtime version, enum emit, 結構化語法診斷, Source Map v3, 固定輸出 fingerprints 和無 inline source content
* `優化` Android 12 和 Android 13 真機門禁覆蓋每部裝置 100 次熱探針及逾時後的 Worker 重建恢復, Android 13 亦透過獨立 helper 驗證 minified release, 同時保持全部項目編譯能力為 false
* `依賴` 附加 AndroidX WebKit 版本 1.14.0

# v0.2.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.2.0 中仍為權威狀態: R1 僅準備及驗證 compiler distribution 和 IPC/FD transport, R2 完成前仍不可進行項目編譯
* `新增` TypeScript 6.0.3 官方發行包管線驗證 SHA-1, SHA-256 和 SRI SHA-512 三種摘要, 透過嚴格 tar entry allowlist 安全擷取檔案, 並生成 canonical `distribution.json` 以及 compiler, standard libraries, generator 和 distribution fingerprints
* `新增` Debug/release APK 內容門檻依據 `distribution.json` 重新驗證實際打包位元組, 確保 APK 僅包含一份 allowlist 內的 `lib/typescript.js`, 且 declarations, license 和 third-party notice 完整一致
* `新增` Instrumentation 覆蓋真實遠端 `:typescript_compiler` 程序的 Binder 服務發現/能力及 R0 同步拒絕, 驗證拒絕後呼叫方持有的 FD 仍然有效, 且無效或共用 descriptor 在有界短觀察窗內未觸發 callback
* `依賴` 附加 AndroidX Test Runner 版本 1.7.0
* `依賴` 附加 AndroidX Test JUnit Extension 版本 1.3.0

# v0.1.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 表示 R0 僅提供外掛發現和臨時編譯合約, 尚未能編譯或執行 TypeScript
* `新增` 獨立的 AutoJs6 TypeScript 編譯前端外掛工程, 精確鎖定 TypeScript 6.0.3, 並與 Node.js Runtime 外掛保持零依賴
* `新增` 透過受 `org.autojs.permission.PLUGIN` 保護的 `org.autojs.plugin.INFO` 和 `org.autojs.plugin.typescript.COMPILER` 服務提供外掛發現及編譯能力協商
* `新增` 編譯服務在獨立的 `:typescript_compiler` 程序運行, 臨時 Binder envelope 使用小型 `Bundle` 元數據和 `ParcelFileDescriptor` 輸入/輸出 archive
* `新增` R0 自動化門檻覆蓋 API contract, admission/rejection, compiler distribution lock, Node Runtime 獨立性, Lint 和 universal APK 內容
