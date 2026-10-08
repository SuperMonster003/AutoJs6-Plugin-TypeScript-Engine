# Compose UI memo local compiler acceptance

Date: 2026-10-08. TypeScript Engine 0.6.8 / build90 adds the local declaration for
`compose.memo`, the roadmap F.6 render fragment API of AutoJs6 6.8.0 / 5323. It does
not upgrade TypeScript 6.0.3, change caller quotas or replace the public npm 2.1.3
source lock.

The local supplement is declarations 4.36.0, exact committed source
`5102e03ec1635561e6f4a63c75a979f8a5b41cf6`. Only the hash-locked module changes; the
JSX region and its hash are unchanged. Generator and runtime identity remain
revision 7 because no transform changed. The Rhino layer is 53 files / 992257 bytes;
common + Rhino is 161 files / 4777015 bytes. The manifest owns 118 files and Node
stays 59 files / 246572 bytes. The quota fixture, its JVM assertions and the Gradle
inventory agree; no caller budget was increased.

Without this supplement a TS or TSX script that calls `compose.memo` fails the
compiler's semantic check, because the Engine compiles with `noEmitOnError`.

## Validation

- 534 app JVM tests in 59 classes and 21 build-logic tests in 3 classes pass without skips.
- The full real Worker compiler gate passes. Its new memo case compiles keyed memo
  rows inside a TSX render, executes the emitted output in a sandbox to confirm the
  dependencies, keys and fragment results, and rejects six invalid inputs: missing
  dependencies, a scalar dependency, a non-node result, a boolean key, an unknown
  option and an async builder.
- Declaration distribution, TypeScript distribution, compiler lock, C8 Program and
  Worker layering and the current input-quota fixture pass.
- Debug/androidTest, lint and the five signed release artifacts pass. Lint has zero
  errors and seven existing warnings. Debug and release native page alignment pass.
- On an owned API 35 x86_64 AVD the final signed x86_64 compiler, the clean 5323
  host commit `9fe6dde65f` with its memo TSX fixture and Compose UI 1.1.0 / 36 pass all ten real TSX
  scenarios and the five unchanged JavaScript examples (15 tests, 61.873 s). The
  memo scenario renders 50 keyed rows, keeps every row on an unrelated update and
  rebuilds only the edited row. This is test duration, not a performance baseline.

The gate log is `app/build/compose-f6-engine-final-gate.log`.

## Signed compiler artifacts

| ABI | File in app/releases/0.6.8 | Bytes | SHA-256 |
| --- | --- | --- | --- |
| Universal | autojs6-plugin-typescript-engine-v0.6.8-universal-BE0F7E98.apk | 3427788 | 39ddc056b83bcec5322d58ae1588d5c1b054223372cddd750675ffecfd223e1d |
| x86_64, installed | autojs6-plugin-typescript-engine-v0.6.8-x86_64-E9801C08.apk | 3378385 | a1a27e0220e6778ef970f8cf5469d2395c421e2796383461c8cbf94d1c5cec77 |
| arm64-v8a | autojs6-plugin-typescript-engine-v0.6.8-arm64-v8a-BE18FAE6.apk | 3378372 | d570e3bae07fe44d3048a6a97f3d30ef0684cca49abfc3c3f3656c91fe935d75 |
| armeabi-v7a | autojs6-plugin-typescript-engine-v0.6.8-armeabi-v7a-E3F076EB.apk | 3377114 | 5335213b74f0728c90c7b6819c66e40231dc0b6344356925564cbabec522ccf1 |
| x86 | autojs6-plugin-typescript-engine-v0.6.8-x86-C92D5B19.apk | 3377362 | f4cce7ff43acbb33528b243c6ca64848a7493a156927b4903525fa6468bf6bc0 |

The implementation commit uses build90 to match its next reachable Git count.
No remote push, npm publication or public Release occurred.
