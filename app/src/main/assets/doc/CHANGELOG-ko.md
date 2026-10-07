******

### 릴리스 기록

******

# v0.6.6

###### 2026/10/07

* `안내` 독립 대화상자에는 AutoJs6 6.8.0 / 5321과 dialog-v1을 지원하는 Compose UI가 필요합니다. TSX에는 TypeScript Engine 0.6.6도 필요합니다. UI가 아닌 스크립트에는 호스트의 오버레이 권한이 필요합니다
* `개선` compose.dialog는 UI 또는 일반 스크립트에서 대화상자와 모달 하단 시트를 표시하고 업데이트 및 종료 가능한 세션을 반환합니다

# v0.6.5

###### 2026/10/07

* `안내` 상호 운용에는 일치하는 AutoJs6 6.8.0 / 5320 호스트와 AndroidView 확장을 지원하는 Compose UI가 필요합니다. TSX에는 TypeScript Engine 0.6.5도 필요합니다. 기본 V1 렌더링의 최소 호스트는 계속 5316입니다
* `개선` Compose UI TSX: XML `<compose>` 컨테이너와 compose.attach로 UI 페이지나 기존 플로팅 창에 독립적인 Compose 세션을 삽입합니다. compose.AndroidView는 기존 Android View 또는 동기 팩토리가 반환하는 View를 표시합니다

# v0.6.4

###### 2026/10/05

* `개선` 일치하는 로컬 AutoJs6 호스트에서 Rhino 프로젝트의 Compose UI TSX 속성과 콜백을 검사할 수 있습니다. 기존 XML TSX도 계속 사용할 수 있습니다
* `개선` Android 앱 정보 아이콘에 Icon Studio의 그림과 밝은 배경 및 어두운 배경을 사용하고 플러그인 센터의 투명 그림과 기존 런처 옵션을 유지

# v0.6.3

###### 2026/09/19

* `수정` 공유 빌드 플러그인 1.8.3을 통해 AGP 9.1의 SDK XML v4 파싱 경고 및 JVM 단위 테스트 조립 작업에서 APK 네이티브 라이브러리 정렬 검사가 잘못 실행되는 문제 해결
* `개선` compileSdk 와 targetSdk 를 37 (Android 17) 로 올리며, 플러그인 동작은 새 대상 버전의 영향을 받지 않음

# v0.6.2

###### 2026/09/13

* `수정` 플러그인 센터에서 보호된 진입점으로 새 설치를 활성화하고 설치된 패키지의 메타데이터를 표시
* `수정` 빌드 환경 언어와 관계없이 플러그인 메타데이터의 빌드 날짜를 영어로 통일
* `개선` 호스트 활성화, 메타데이터, 다국어 문서 및 서명된 APK 수집을 공통 규칙에 맞게 정리

# v0.6.1

###### 2026/09/12

* `개선` 64비트 네이티브 라이브러리의 16 KB 페이지 정렬을 빌드 시 검증, manifest 계약 검사 및 JSON 보고서 지원
* `개선` 공유 Gradle 플랫폼 플러그인으로 빌드 도구 버전을 선택하고, 로컬 호환성 데이터를 제거하며 유지 관리 검사를 일치시킴

# v0.6.0

###### 2026/08/29

* `안내` AutoJs6 6.8.0 Alpha7 (build 5253) 이상에서 project compilation을 활성화함; Node profile은 Node.js Runtime plugin도 필요하지만 Rhino profile은 필요하지 않음
* `안내` compile과 execution은 시작 시점의 동일한 exact snapshot을 사용함; bare/scoped npm package는 compile 단계에서 resolve되고 Rhino CommonJS 또는 Node.js Runtime plugin을 통해 runtime에서 load됨
* `안내` Android 7.0 / API 24에서는 Chrome/WebView 119 이상이 필요함; API 24/25에 포함된 구형 provider는 지원하지 않으며 fail closed 처리됨
* `추가` compiler-backed Node TypeScript project가 실행 중 생성된 `.ts/.mts/.cts` 파일을 이제 on-demand compile할 수 있습니다: Node는 byte count와 SHA-256으로 고정된 PFD를 통해 source를 host로 전달하고, host는 resident project session을 재사용하며, type diagnostics와 Source Map stack mapping을 유지하면서 path escape, symbolic link, ambiguous target을 계속 거부합니다
* `추가` dependency-layer policy revision 2와 resolver policy revision 4를 추가함: host archive와 production Worker가 `.node`, `binding.gyp`, `gypfile`, 설치 lifecycle hook을 안정적인 native/install-script 오류 및 pure JavaScript/WASM 안내와 함께 결정적으로 거부합니다
* `추가` Rhino 및 Node profile에서 dayjs 1.11.13, lodash 4.17.21, zod 4.1.5, uuid 13.0.0, nanoid 5.1.5를 검증하는 오프라인 실제 패키지 승인 매트릭스를 추가했으며, resolver policy revision 3은 번들 선언을 덮어쓰지 않고 런타임 JavaScript에서 설치된 `@types`로 폴백합니다
* `추가` compiler와 Ace의 dependency package type을 동기화함: package `types`/`typings`, TypeScript 6 `typesVersions`, nested declaration import 및 installed `@types`가 동일한 frozen type layer에 포함되고 dayjs completion, hover, strict TS2322 diagnostic이 Rhino/Node profile에서 일치함
* `추가` `compilerAvailable`, `projectCompilation`, `semanticDiagnostics`, `sourceMapV3`를 함께 활성화함 (capability mask 15); 완전한 TypeScript Program compile, structured diagnostics 및 짝을 이루는 external Source Map v3 output을 제공
* `추가` UTF-8 path와 AutoJs6 native TSX UI lowering을 지원하는 public Rhino `.ts/.tsx` project compilation을 추가함; controlled tsconfig revision 3는 Rhino와 NodeNext 모두에 20개 boolean option 및 profile-scoped `target`/`lib`를 제공하며 revision 2 Rhino request도 계속 호환됨
* `추가` `.mts/.cts`와 project-scoped `.ts`를 위한 public NodeNext revision 2 compilation을 추가하고 source kind 및 package type에 따라 `.mjs/.cjs/.js`를 출력함
* `추가` compiler를 AutoJs6 execution, clickable compile diagnostics, runtime stack mapping, multi-file project, dynamic import mapping, Tasker/scheduled entry 및 APK Builder encrypted-project in-memory flow에 통합함; package에는 compile된 JS/map만 포함하며 TS source는 포함하지 않음
* `추가` Ace editor declarations와 TypeScript 6.0.3 behavior를 execution 전 compile과 일치시킴
* `추가` controlled tsconfig revision 4에서 Rhino와 NodeNext에 project-local `baseUrl`/`paths` alias를 추가함; resolution은 frozen snapshot 내부로 제한하고 emit specifier는 명시적 runtime-relative module로 다시 쓰며 revision 2/3 identity는 정확히 호환됨
* `추가` Node ESM `.mts` 및 root가 `type: module`인 `.ts` project에서 top-level `await`를 활성화함; controlled ES2022 설정은 짝을 이루는 Source Map과 함께 `.mjs/.js`를 출력하며 Node.js Runtime plugin에서 직접 실행할 수 있음
* `추가` `packages/<name>` 및 `packages/@scope/<name>` 아래에 controlled Node workspace package boundary를 추가함; 동일 project의 `.ts`는 nearest `package.json.type`에 따라 ESM과 CommonJS를 함께 사용할 수 있으며 `scripts`는 계속 금지됨
* `추가` 호환 Node Runtime은 ESM entry와 dynamic `import()`를 V8 native linker로 실행하여 순환 의존성, 변경 가능한 export 및 re-export live binding을 보존함; 4개 module `.mts` graph는 `initial=1`에서 `updated=42`로 갱신됨
* `추가` 별도 quota의 read-only `node_modules` dependency layer와 bare/scoped package resolution을 추가함; subpath `exports`, `types`/`import`/`require` condition, `types`/`typings`/`module`/`main`/`index` fallback 및 dayjs + zod full Program acceptance를 포함함; 동일한 exact layer가 runtime에 전달되어 Rhino는 CommonJS/JSON `require` entry를 선택하고 ESM-only를 stable error로 처리하며 Node는 dependency를 write-back하지 않는 private workspace overlay에서 ESM/CJS/dual package를 실행함
* `개선` 명시적 opt-in 프로젝트 session revision 1 추가: service가 caller/project별로 파생한 identity로 제한된 Worker realm을 유지하고 digest-versioned SourceFile, incremental builder, 변경되지 않은 output을 실행 간 재사용; legacy request는 완전 cold 경로를 유지하며 메모리 압력 시 모든 session 폐기
* `개선` deterministic cross-execution cache reuse를 추가함; 측정된 hit는 18-63 ms에 완료되고 bounded input batching은 관측 device의 ingress cost를 40%-57% 줄임
* `개선` ordinary output limit을 512 artifacts, JavaScript 또는 map당 16 MiB, aggregate 64 MiB로 높이고 ordinary caller input을 revision 3의 4096 files, file당 8 MiB, aggregate 64 MiB로 승격함; built-in declarations는 caller quota에서 제외되며 revision 2 host의 512 files / 8 MiB compatibility path도 유지함
* `개선` cold project ingress에서 middle frame을 최대 6개로 batching하되 payload chunk는 최대 4개로 유지함; 고정 109-file fixture가 97에서 68 envelopes로 줄었고 4개 Android/WebView 조합에서 compiler와 ingress median이 모두 개선됨

# v0.5.0

###### 2026/07/27

* `안내` R1의 모든 종료 게이트가 완료되었으며 API 24/25의 Chrome 119 modern-provider runtime matrix도 통과함; production project compilation은 R2 범위이므로 `compilerAvailable=false`가 계속 공식 상태를 나타냄
* `수정` service destruction에서 WebView, Worker, MessagePort, ServiceWorker client, pending request, Handler callback을 해제한 뒤 close report를 게시하고 waiter를 깨우도록 변경; deadline과 terminal publication 사이의 race도 제거
* `수정` `/proc` 및 FD 관측을 `(pid,starttime)` 에 연결하고 사용할 수 없거나 일부만 존재하는 shell 출력을 fail closed로 처리하며 process exit polling 중 UiAutomation finish race를 방지
* `개선` Android 12 및 Android 13 각 device에서 10개의 cold compiler process와 100회의 hot prewarm을 검증; compiler service FD는 staging archive를 남기지 않고 정확히 baseline으로 복귀
* `개선` API 33 및 WebView 109에서 none, Node-only, Ace-only, Node+Ace installation state를 통과; static gate로 production source, manifest, direct dependency의 Node/Ace discovery 및 dependency를 금지

# v0.4.0

###### 2026/07/27

* `안내` `compilerAvailable=false` 는 v0.4.0에서도 공식 상태를 나타냄: API 24는 Bundle/FD transport gate만 통과하며 이전 WebView는 원본 TypeScript 6.0.3 compiler JavaScript를 parse할 수 없음; API 24/25 modern-provider matrix는 아직 완료되지 않음
* `수정` deserialization 전에 raw Bundle transport의 FD를 거부하고 prewarm, compilation, cancellation reject path에서 transport copy를 해제
* `수정` Bundle metadata의 string array를 실제 runtime type이 `String[]` 인 경우로 제한하고 top-level null value, Parcelable array, Binder, Serializable, nested Bundle을 거부
* `수정` timeout recovery 실행을 epoch로 병합하여 stale recovery가 replacement session을 제거하지 못하도록 제한; 연속된 두 번의 1 ms timeout deterministic gate로 queued probe의 recovery를 검증
* `개선` Android 12 및 Android 13에서 실제 WebView renderer exit gate를 통과: 첫 exit는 정확히 한 번 retry하고 두 번째 exit는 안정적으로 실패하며 이후 일반 probe는 recovery에 성공
* `개선` Android 12 및 Android 13 각 device에서 `:typescript_compiler` process를 10회 restart한 뒤에도 distribution/declarations fingerprints가 안정적임; API 24는 별도로 6개 Bundle/FD transport gate를 통과

# v0.3.0

###### 2026/07/27

* `안내` `compilerAvailable=false` 는 v0.3.0에서도 공식 상태를 나타냄: R1 prewarm은 제한된 compiler host의 golden probe만 실행하며 R2 완료 전에는 project compilation을 사용할 수 없음
* `추가` 전용 `:typescript_compiler` process에 private HTTPS asset origin, 정확한 resource allowlist, WebMessagePort, 제한된 network, file, content, storage, window capabilities를 적용한 WebView/Worker compiler host를 구축
* `추가` R1 prewarm에서 실제 TypeScript 6.0.3 runtime을 로드하고 runtime version, enum emit, structured syntax diagnostic, Source Map v3, 고정 output fingerprints, inline source content가 없음을 검증
* `개선` Android 12 및 Android 13 각 device에서 100회 hot probe와 timeout 후 Worker rebuild recovery를 검증하고 Android 13의 독립 helper로 minified release도 검증하면서 모든 project compilation capabilities를 false로 유지
* `의존성` AndroidX WebKit 버전 1.14.0 추가

# v0.2.0

###### 2026/07/27

* `안내` `compilerAvailable=false` 는 v0.2.0에서도 공식 상태를 나타냄: R1은 compiler distribution 및 IPC/FD transport를 준비하고 검증할 뿐이며 R2 완료 전에는 project compilation을 사용할 수 없음
* `추가` 공식 TypeScript 6.0.3 distribution의 재현 가능한 pipeline에서 SHA-1, SHA-256, SRI SHA-512 세 digest를 검증하고 엄격한 tar entry allowlist만 안전하게 추출하며 canonical `distribution.json`과 compiler, standard libraries, generator, distribution fingerprints를 생성
* `추가` Debug/release APK content gate가 `distribution.json`에 따라 실제 package bytes를 다시 검증하고 allowlist의 `lib/typescript.js`가 하나만 존재하는지와 declarations, license, third-party notice의 무결성을 확인
* `추가` Instrumentation으로 실제 원격 `:typescript_compiler` 프로세스의 Binder discovery/capability 및 R0 동기 거부를 검증하고, 거부 후에도 호출자가 소유한 FD는 유효하며 잘못되었거나 공유된 descriptor가 제한된 짧은 관찰 시간 동안 callback을 발생시키지 않음을 확인
* `의존성` AndroidX Test Runner 버전 1.7.0 추가
* `의존성` AndroidX Test JUnit Extension 버전 1.3.0 추가

# v0.1.0

###### 2026/07/27

* `안내` `compilerAvailable=false` 는 R0가 플러그인 검색과 임시 컴파일 계약만 제공하며 아직 TypeScript를 컴파일하거나 실행할 수 없음을 나타냄
* `추가` 독립 AutoJs6 TypeScript 컴파일러 프런트엔드 플러그인 프로젝트를 구성하고 TypeScript 6.0.3을 고정했으며 Node.js Runtime 플러그인 의존성을 0으로 유지
* `추가` `org.autojs.permission.PLUGIN` 으로 보호되는 `org.autojs.plugin.INFO` 및 `org.autojs.plugin.typescript.COMPILER` 서비스를 통한 플러그인 검색과 컴파일러 기능 협상
* `추가` 전용 `:typescript_compiler` 프로세스에서 컴파일러 서비스를 실행하고 임시 Binder envelope로 작은 `Bundle` 메타데이터와 `ParcelFileDescriptor` 입출력 archive를 전송
* `추가` API contract, admission/rejection, compiler distribution lock, Node Runtime 독립성, Lint, universal APK 내용을 검증하는 R0 자동화 게이트
