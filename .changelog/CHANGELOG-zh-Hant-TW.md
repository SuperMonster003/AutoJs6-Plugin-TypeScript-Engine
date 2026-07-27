******

### 發行歷史

******

# v0.2.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 在 v0.2.0 中仍為權威狀態: R1 僅準備並驗證 compiler distribution 和 IPC/FD transport, R2 完成前仍無法進行專案編譯
* `新增` TypeScript 6.0.3 官方發行套件管線驗證 SHA-1, SHA-256 和 SRI SHA-512 三種摘要, 透過嚴格 tar entry allowlist 安全擷取檔案, 並產生 canonical `distribution.json` 以及 compiler, standard libraries, generator 和 distribution fingerprints
* `新增` Debug/release APK 內容門檻依據 `distribution.json` 重新驗證實際封裝位元組, 確保 APK 僅包含一份 allowlist 內的 `lib/typescript.js`, 且 declarations, license 和 third-party notice 完整一致
* `新增` Instrumentation 涵蓋真實遠端 `:typescript_compiler` 行程的 Binder 服務探索/能力與 R0 同步拒絕, 驗證拒絕後呼叫端持有的 FD 仍然有效, 且無效或共用 descriptor 在有界短觀察窗內未觸發 callback
* `依賴` 附加 AndroidX Test Runner 版本 1.7.0
* `依賴` 附加 AndroidX Test JUnit Extension 版本 1.3.0

# v0.1.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 表示 R0 僅提供外掛探索和暫定編譯合約, 尚無法編譯或執行 TypeScript
* `新增` 獨立的 AutoJs6 TypeScript 編譯前端外掛工程, 精確鎖定 TypeScript 6.0.3, 並與 Node.js Runtime 外掛保持零依賴
* `新增` 透過受 `org.autojs.permission.PLUGIN` 保護的 `org.autojs.plugin.INFO` 和 `org.autojs.plugin.typescript.COMPILER` 服務提供外掛探索及編譯能力協商
* `新增` 編譯服務在獨立的 `:typescript_compiler` 行程執行, 暫定 Binder envelope 使用小型 `Bundle` 中繼資料和 `ParcelFileDescriptor` 輸入/輸出 archive
* `新增` R0 自動化門檻涵蓋 API contract, admission/rejection, compiler distribution lock, Node Runtime 獨立性, Lint 和 universal APK 內容
