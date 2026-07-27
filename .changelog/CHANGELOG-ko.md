******

### 릴리스 기록

******

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
