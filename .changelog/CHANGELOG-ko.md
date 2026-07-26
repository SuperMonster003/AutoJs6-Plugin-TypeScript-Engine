******

### 릴리스 기록

******

# v0.1.0

###### 2026/07/27

* `안내` `compilerAvailable=false` 는 R0가 플러그인 검색과 임시 컴파일 계약만 제공하며 아직 TypeScript를 컴파일하거나 실행할 수 없음을 나타냄
* `추가` 독립 AutoJs6 TypeScript 컴파일러 프런트엔드 플러그인 프로젝트를 구성하고 TypeScript 6.0.3을 고정했으며 Node.js Runtime 플러그인 의존성을 0으로 유지
* `추가` `org.autojs.permission.PLUGIN` 으로 보호되는 `org.autojs.plugin.INFO` 및 `org.autojs.plugin.typescript.COMPILER` 서비스를 통한 플러그인 검색과 컴파일러 기능 협상
* `추가` 전용 `:typescript_compiler` 프로세스에서 컴파일러 서비스를 실행하고 임시 Binder envelope로 작은 `Bundle` 메타데이터와 `ParcelFileDescriptor` 입출력 archive를 전송
* `추가` API contract, admission/rejection, compiler distribution lock, Node Runtime 독립성, Lint, universal APK 내용을 검증하는 R0 자동화 게이트
