# Compose UI V2 local compiler acceptance

Date: 2026-10-08. TypeScript Engine 0.6.7 / build89 adds the local Compose UI V2
declaration projection for the existing F.4 roadmap item. It does not upgrade
TypeScript 6.0.3, change caller quotas or replace the public npm 2.1.3 source lock.

The local supplement is declarations 4.35.0, exact committed source
`de51858b59ec88f75c56d94bcf4af600c54ff34f`. Its module and three independent JSX
regions are normalized and hash-locked under
`build-logic/convention/src/main/resources/r2-compose/source.json`. The V1 and
F.2 regions remain intact; V2 adds 25 nodes across 19 component families, with
typed properties, required slots and positional event arguments.

Generator and runtime identity are revision 7 because the projection now includes
the V2 JSX region. Rhino remains 53 files, now 991341 bytes; common + Rhino is
161 files / 4776099 bytes. The manifest owns 118 files. Node stays 59 files /
246572 bytes. The generated inventory, independent JVM assertions and Gradle
quota fixture all agree; no caller budget was increased.

## Validation

- 534 selected app JVM tests and four build-logic tests pass without skips.
- The full real Worker compiler gate passes, including all 19 V2 families,
  preserved emitted factory/namespace identities, executed positional callbacks,
  and 16 new invalid-source categories.
- C8 Program and Worker profile isolation and current input-quota gates pass.
- Debug/androidTest, lint and all five signed release/native-alignment gates pass.
  Lint has zero errors and seven existing warnings.
- Two actual API 35 packaged declaration inventory tests pass on the debug build.
- The final signed x86_64 compiler and signed Compose UI build33 run nine actual
  TSX scenarios and the five unchanged JavaScript examples: all 14 pass in
  60.797 s on the owned API 35 AVD. This is test duration, not a performance
  baseline. The normal installed-provider launch path compiles and executes the
  new mixed dialog/AndroidView/navigation/pager/chip fixture.

The final host is the clean 6.8.0 / 5322 source commit
`6a166c38415e7299ba034cd062dcc4c51a4b543a`. Final renderer is
`autojs6-plugin-compose-ui-v1.1.0-ffee2730.apk`, SHA-256
`3ece8a8885b0a2b9ab7edf2e1eeaf5aeeeebc3b6a0b3f4936b868da5ae0ab755`.
The full compiler gate is `app/build/compose-f4-engine-final-gate.log`; the host
device record is `app/build/compose-f4/final-signed-tsx-and-examples.log`.

## Signed compiler artifacts

| ABI | File in app/releases/0.6.7 | Bytes | SHA-256 |
| --- | --- | --- | --- |
| Universal | autojs6-plugin-typescript-engine-v0.6.7-universal-E06D46A9.apk | 3427008 | b25a65ff3111387235259cb976c8468a9a7c18e05e5e6e6862ce697c946d6605 |
| x86_64, installed | autojs6-plugin-typescript-engine-v0.6.7-x86_64-2CC81742.apk | 3377605 | 67a7e8501a1c4cfcf342aa5a8026f692a0c6492f0bc7029d2ef2ede71041a1d4 |

The existing concurrent platform commit `f788baf` is preserved. The final build
uses platform/native-alignment 1.9.0, Gradle 9.6.1, AGP 9.4.1, Kotlin 2.3.21,
R8 8.13.19 and JDK 21. The implementation commit uses build89 to match its next
reachable Git count. No remote push, npm publication or public Release occurred.
