******

### リリース履歴

******

# v0.1.0

###### 2026/07/27

* `ヒント` `compilerAvailable=false` は R0 がプラグイン検出と暫定コンパイル契約のみを公開し, TypeScript のコンパイルまたは実行はまだできないことを示す
* `追加` 独立した AutoJs6 TypeScript コンパイラフロントエンドプラグインプロジェクトを構築し, TypeScript 6.0.3 を固定して Node.js Runtime プラグインへの依存をゼロに維持
* `追加` `org.autojs.permission.PLUGIN` で保護された `org.autojs.plugin.INFO` と `org.autojs.plugin.typescript.COMPILER` サービスによるプラグイン検出とコンパイラ機能ネゴシエーション
* `追加` 専用の `:typescript_compiler` プロセスでコンパイラサービスを実行し, 暫定 Binder envelope で小さな `Bundle` メタデータと `ParcelFileDescriptor` による入出力 archive を転送
* `追加` API contract, admission/rejection, compiler distribution lock, Node Runtime 非依存性, Lint, universal APK 内容を対象とする R0 自動ゲート
