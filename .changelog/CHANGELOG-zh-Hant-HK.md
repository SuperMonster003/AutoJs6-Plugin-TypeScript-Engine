******

### 發行歷史

******

# v0.1.0

###### 2026/07/27

* `提示` `compilerAvailable=false` 表示 R0 僅提供外掛發現和臨時編譯合約, 尚未能編譯或執行 TypeScript
* `新增` 獨立的 AutoJs6 TypeScript 編譯前端外掛工程, 精確鎖定 TypeScript 6.0.3, 並與 Node.js Runtime 外掛保持零依賴
* `新增` 透過受 `org.autojs.permission.PLUGIN` 保護的 `org.autojs.plugin.INFO` 和 `org.autojs.plugin.typescript.COMPILER` 服務提供外掛發現及編譯能力協商
* `新增` 編譯服務在獨立的 `:typescript_compiler` 程序運行, 臨時 Binder envelope 使用小型 `Bundle` 元數據和 `ParcelFileDescriptor` 輸入/輸出 archive
* `新增` R0 自動化門檻覆蓋 API contract, admission/rejection, compiler distribution lock, Node Runtime 獨立性, Lint 和 universal APK 內容
