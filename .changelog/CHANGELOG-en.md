******

### Release History

******

# v0.1.0

###### 2026/07/27

* `Hint` `compilerAvailable=false` indicates that R0 only exposes plugin discovery and a provisional compilation contract; it cannot compile or execute TypeScript yet
* `Feature` Established the standalone AutoJs6 TypeScript compiler frontend plugin project, pinned TypeScript 6.0.3, and kept zero dependencies on the Node.js Runtime plugin
* `Feature` Provided plugin discovery and compiler capability negotiation through the `org.autojs.plugin.INFO` and `org.autojs.plugin.typescript.COMPILER` services protected by `org.autojs.permission.PLUGIN`
* `Feature` Ran the compiler service in the dedicated `:typescript_compiler` process, with a provisional Binder envelope carrying small `Bundle` metadata and input/output archives through `ParcelFileDescriptor`
* `Feature` Added R0 automated gates for the API contract, admission/rejection, compiler distribution lock, Node Runtime independence, Lint, and universal APK contents
