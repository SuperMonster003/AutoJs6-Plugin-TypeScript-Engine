******

### Release History

******

# v0.2.0

###### 2026/07/27

* `Hint` `compilerAvailable=false` remains authoritative in v0.2.0: R1 only prepares and validates the compiler distribution and IPC/FD transport; project compilation stays unavailable until R2
* `Feature` Established a reproducible official TypeScript 6.0.3 distribution pipeline that verifies SHA-1, SHA-256, and SRI SHA-512, safely extracts a strict tar entry allowlist, and emits canonical `distribution.json` plus compiler, standard-library, generator, and distribution fingerprints
* `Feature` Hardened debug/release APK content gates to rehash packaged bytes against `distribution.json`, require exactly one allowlisted `lib/typescript.js`, and verify the declarations, license, and third-party notice
* `Feature` Covered real remote `:typescript_compiler` Binder discovery/capabilities and synchronous R0 rejection with instrumentation, verifying that caller-owned FDs remain valid after rejection and that invalid or shared descriptors trigger no callback during a bounded observation window
* `Dependency` Added AndroidX Test Runner version 1.7.0
* `Dependency` Added AndroidX Test JUnit Extension version 1.3.0

# v0.1.0

###### 2026/07/27

* `Hint` `compilerAvailable=false` indicates that R0 only exposes plugin discovery and a provisional compilation contract; it cannot compile or execute TypeScript yet
* `Feature` Established the standalone AutoJs6 TypeScript compiler frontend plugin project, pinned TypeScript 6.0.3, and kept zero dependencies on the Node.js Runtime plugin
* `Feature` Provided plugin discovery and compiler capability negotiation through the `org.autojs.plugin.INFO` and `org.autojs.plugin.typescript.COMPILER` services protected by `org.autojs.permission.PLUGIN`
* `Feature` Ran the compiler service in the dedicated `:typescript_compiler` process, with a provisional Binder envelope carrying small `Bundle` metadata and input/output archives through `ParcelFileDescriptor`
* `Feature` Added R0 automated gates for the API contract, admission/rejection, compiler distribution lock, Node Runtime independence, Lint, and universal APK contents
