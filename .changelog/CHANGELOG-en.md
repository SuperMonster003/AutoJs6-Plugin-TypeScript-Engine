******

### Release History

******

# v0.4.0

###### 2026/07/27

* `Hint` `compilerAvailable=false` remains authoritative in v0.4.0: API 24 only passes the Bundle/FD transport gates because its old WebView cannot parse the original TypeScript 6.0.3 compiler JavaScript; the API 24/25 modern-provider matrix remains pending
* `Fix` Rejected FDs in raw Bundle transport before deserialization and released their transport copies across prewarm, compilation, and cancellation rejection paths
* `Fix` Restricted Bundle metadata string arrays to the actual `String[]` runtime type and rejected top-level null values, Parcelable arrays, Binder, Serializable, and nested Bundle values
* `Fix` Coalesced timeout recovery execution by epoch so stale recovery work cannot destroy a replacement session; deterministic consecutive 1 ms timeout coverage now verifies that the queued probe recovers
* `Improvement` Passed real WebView renderer-exit gates on Android 12 and Android 13: the first exit receives exactly one retry, a second exit fails deterministically, and a subsequent ordinary probe recovers
* `Improvement` Kept distribution/declarations fingerprints stable across 10 `:typescript_compiler` process restarts on each Android 12 and Android 13 device; API 24 separately passed six Bundle/FD transport gates

# v0.3.0

###### 2026/07/27

* `Hint` `compilerAvailable=false` remains authoritative in v0.3.0: R1 prewarm only runs the restricted compiler-host golden probe; project compilation stays unavailable until R2
* `Feature` Established a WebView/Worker compiler host in the dedicated `:typescript_compiler` process with a private HTTPS asset origin, exact resource allowlist, WebMessagePort, and restricted network, file, content, storage, and window capabilities
* `Feature` Made R1 prewarm load the real TypeScript 6.0.3 runtime and verify its runtime version, enum emit, structured syntax diagnostic, Source Map v3, fixed output fingerprints, and absence of inline source content
* `Improvement` Covered 100 hot probes per Android 12 and Android 13 device plus Worker rebuild recovery after a timeout, and verified the minified release through a standalone Android 13 helper while keeping every project-compilation capability false
* `Dependency` Added AndroidX WebKit version 1.14.0

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
