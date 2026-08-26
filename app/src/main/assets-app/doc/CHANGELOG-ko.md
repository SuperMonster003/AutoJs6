******

### 배포 기록

******

# v6.8.0

###### 2026/08/26

* `추가` Ace LSP 기본 파일 형식에 .mts/.cts 및 선언 파일 변형을 추가하여 외부 Ace 편집기가 실행과 동일한 Rhino/Node TypeScript 프로필을 선택하도록 개선; 이전 기본 선택은 마이그레이션하고 명시적 사용자 지정 접미사 집합은 그대로 유지
* `추가` APK Builder가 패키징 시 Rhino TypeScript 프로젝트를 컴파일합니다. 최종 APK에는 암호화된 JavaScript와 소스가 포함되지 않은 선택적 Source Map만 들어가며 TypeScript 소스와 컴파일러는 포함되지 않습니다. 암호화 프로젝트는 일회용 AES-256-GCM 스테이징을 사용하고 평문은 메모리에만 유지한 뒤 재암호화 후 0으로 지웁니다
* `추가` Node.js 24.5.0 스크립트 런타임, "nodejs" 지시문 또는 .node.js/.cjs/.mjs 등의 확장자를 통한 스크립트 실행 지원
* `추가` Node.js ESM/dynamic import()/네트워크 및 WebSocket/네이티브 네트워크 모듈/worker_threads/child_process/Java 상호 운용 기능을 정식 기능으로 기본 활성화하고, 권한 선언/허용 목록/샌드박스/리소스 예산 보호는 계속 적용
* `추가` Node.js 런타임에서 CommonJS/부분 ESM/로컬 전용 동적 import()/경량 TypeScript 소스 (.ts/.mts/.cts) 지원
* `추가` Node.js 런타임에서 실행 범위 온디맨드 암호화 모듈 소스 제공자를 지원하고 모듈 해석기가 실제로 선택한 후보만 검사 및 복호화
* `추가` Node.js 런타임에서 제어된 AutoJs6 브리지 모듈을 지원하고 Safe Node Profile 보안 정책을 적용
* `추가` Node.js 런타임에서 상주 서비스 프로세스 및 프로세스 범위 Node/V8 플랫폼 재사용을 지원하고 각 스크립트 실행마다 새로운 isolate와 Environment 생성
* `추가` Node.js 상주 런타임에서 유휴 상태 또는 메모리 압박 시 회수/명시적 종료/비정상 프로세스 격리 및 재구축 지원
* `추가` Node.js 외부 런타임 플러그인 백엔드에서 필수 `plugin` 및 폴백 가능한 `pluginOptional` 모드, 상주 Binder 세션 및 프로세스 런타임, 신뢰/계약/ABI 검증, 단일 활성 제로 큐 백프레셔 지원
* `추가` Node.js 외부 런타임 플러그인 백엔드에서 요청 범위 워크스페이스 아카이브 전송 v2, 명시적 입력 매핑, 출력 쓰기 반영, 삭제 tombstone 매니페스트를 지원하며 호스트 샌드박스를 스캔하지 않음
* `추가` Node.js Runtime 디버그 빌드에서 실행별로 localhost 전용 Inspector/CDP 세션을 명시적으로 활성화할 수 있으며, release 빌드, 원격 수신 및 블로킹 대기는 계속 금지
* `추가` 앱 패키징 기능은 Node.js 스크립트 프로젝트를 지원하고 필요한 런타임 라이브러리를 자동 포함
* `추가` 독립 TypeScript 컴파일러 플러그인 클라이언트, 고정된 AIDL/R2 아카이브 계약 검증, UTF-8 단일 파일 Rhino 컴파일, 제한된 진단 및 필수 Source Map 출력 지원
* `추가` 로컬 단일 파일 .ts 및 Rhino용 .tsx 스크립트를 Rhino 실행 전에 독립 컴파일러 플러그인으로 전달하고 UTF-8 소스 식별자와 Source Map 출력을 보존하며, 형식 오류를 실행 전에 거부하고 Node.js 플러그인이나 원본 TypeScript 실행으로 폴백하지 않음; 지원하지 않는 선언 파일과 컴파일러 사용 불가 상태는 명시적인 현지화 오류를 반환
* `추가` "ui" 모드 지시문을 사용하는 로컬 단일 파일 .ts/.tsx 스크립트에서 컴파일 후 Rhino Activity의 문자열 XML 및 네이티브 TSX 레이아웃 실행, 제어된 내장 태그, Fragment, 스칼라 속성, 뷰 조회, 클릭 콜백, 텍스트 갱신 및 명시적 UI 종료 지원
* `추가` TypeScript 컴파일러 진단을 소스 파일, 1 기반 줄과 열, 심각도 및 TS 오류 코드를 포함한 호스트 ScriptCompilationException 레코드로 노출하고, Console과 편집기 하단 로그 패널에 해당 소스 위치로 이동하는 깔끔한 클릭 가능 위치를 표시
* `추가` Rhino TypeScript 런타임 예외에서 컴파일러 Source Map v3를 사용해 생성된 JavaScript 스택 프레임을 원본 .ts 파일과 줄로 매핑하고, 제공되지 않은 열을 만들지 않으며, Console과 편집기 로그 패널의 매핑 위치를 클릭 가능하게 유지
* `추가` 로컬 단일 파일 .mts/.cts 스크립트를 독립 TypeScript 컴파일러 플러그인의 Node profile로 .mjs/.cjs에 컴파일한 뒤 Node.js Runtime 플러그인에서 실행하고, UTF-8 파일 이름을 보존하며 기존 TypeScript 정규식 제거를 우회; 프로젝트 package 메타데이터 지원 전까지 단일 파일 Node .ts를 명시적으로 거부
* `추가` Node TypeScript 런타임 오류에서 컴파일러 Source Map v3를 사용해 V8 스택 프레임을 원본 .mts/.cts 파일의 정확한 줄과 열로 매핑하고, Console과 편집기 로그 패널의 매핑 위치를 클릭해 바로 이동 가능
* `추가` TypeScript 프로젝트를 Rhino 또는 Node용 단일 Program으로 제한된 프로젝트 스냅샷에서 컴파일하고, 상대 import, CommonJS 순환 의존성, JSON 런타임 파일, package `type` 컨텍스트, 가져온 모듈 진단, 모듈별 Source Map 스택 매핑을 보존; package 메타데이터는 엄격하게 검증하고 lifecycle scripts를 거부
* `추가` TypeScript 컴파일 결과를 소스 그래프, 컴파일러 및 선언 지문, profile과 policy revision, package 메타데이터를 포함한 semantic key로 실행 간 캐시; 변경되지 않은 스크립트는 컴파일러 prewarm과 Worker 컴파일을 생략하고 인증된 출력 아카이브는 재사용 전에 다시 검증하며 손상되거나 quota를 초과한 항목은 일반 컴파일로 폴백
* `추가` 예약 작업, 브로드캐스트 작업, 바로 가기 및 Tasker/로컬 Run Intent가 포그라운드 실행과 동일한 TypeScript preparation 파이프라인을 공유; Tasker 사전 실행 JavaScript는 컴파일된 Rhino/Node 출력보다 먼저 실행되고 Source Map 줄 오프셋에도 접두부를 반영하여 컴파일을 우회한 원본 TypeScript 실행을 방지
* `추가` 독립 Python Runtime 플러그인 백엔드에서 로컬 .py 파일과 `type: python`으로 선언된 스크립트 프로젝트를 별도의 CPython 플러그인 프로세스에서 실행하고 고정 컴포넌트/서명/프로토콜/ABI 검증, 핫 플러그, JavaScript 폴백 금지를 지원
* `추가` Python 실행 통합은 파일 관리자/편집기/로컬 Run Intent/단일 파일 Import Intent/새 바로 가기/예약 작업/브로드캐스트 작업을 포함하고 단일 파일 또는 프로젝트 컨텍스트를 엄격하게 유지; 프로젝트에서 내부 import/상대 파일 접근/읽기 전용 `autojs6.app/device/execution/project` API 지원
* `추가` Python Runtime 실행은 ExecutionConfig 키 `autojs6.python.stdinSnapshot`을 통한 선택적이고 제한된 사전 제공 stdin 스냅샷을 지원하며, 엄격한 ByteArray/UTF-8 텍스트 허용, 공급자 기능/크기 협상, 비공개 읽기 전용 PFD 전송 및 정리를 적용
* `추가` Python Runtime 프로토콜 1.4는 실행별 명시적 엄격 JSON 결과와 선택적 output artifact를 협상하고 count, normalized path, file/aggregate size, exact PFD reference 및 SHA-256을 제한하며 Host는 typed result만 소비하고 stdout에서 추론하지 않음
* `추가` Python Runtime 프로토콜 1.5는 실행 범위 동기 Host capability broker를 추가하고 고정 plugin UID 검증, 엄격하고 제한된 JSON, 호출 quota, replay/lifetime 보호 및 실행 종료 후 사용 금지를 적용; 완성된 첫 번째 저위험 기능으로 Python script에 toast, clipboard 읽기/쓰기, app 실행 및 URL 열기, 동적 배터리/화면/밝기/볼륨 정보, 레벨별 Host console 출력 및 권한 인식 알림을 제공
* `추가` Python Runtime 1.5 Host broker는 현재 project root 또는 단일 script 디렉터리 안에서 제한된 UTF-8 text 읽기/쓰기, 존재/유형 확인 및 정렬된 직접 목록을 제공; 정규화된 상대 path와 안정적인 제한으로 실행 root 이탈을 차단
* `추가` Python Runtime 1.5 Host broker는 사용자가 승인한 foreground 실행에만 제한된 alert/confirm/prompt/select dialog를 제공; background 실행은 INTERACTIVE_NOT_ALLOWED로 실패하고 취소 또는 종료 정리는 활성 dialog UI를 닫음
* `추가` Python Runtime 1.5 Host broker는 절대 경로가 없는 현재 engine 정보, 결정적 self-stop 및 비 Python Host script에 대한 범위 제한 비동기 child 실행을 최대 16회 제공; provider가 단일 session을 유지하는 동안 중첩 Python은 NESTED_PYTHON_NOT_ALLOWED로 안정적으로 실패
* `추가` Python Runtime 1.5 Host broker는 제한된 accessibility click, long click, 시간 지정 press, swipe, Back 및 Home 동작을 제공; 좌표와 지속 시간을 엄격히 검증하고 accessibility service를 사용할 수 없으면 ACCESSIBILITY_UNAVAILABLE로 결정적으로 실패
* `추가` Python Runtime 1.5 Host broker는 실행 범위에 한정된 accessibility tree snapshot과 AND 조합 selector query를 제공하고 불투명한 node reference로 명시적 click 및 text 설정을 수행; snapshot, scan, 문자열, 보존 node 및 response에 고정 상한을 적용하며 accessibility 사용 불가, 불완전 scan 및 stale node는 안정적인 typed error로 실패
* `추가` Python Runtime 1.5 Host broker는 Android 11+ accessibility screenshot을 실행 범위의 PNG/JPEG byte로 제공하고 보존 이미지 1개, 순서가 보장된 32 KiB chunk, 4 MiB 인코딩 상한, SHA-256 metadata, 명시적 release 및 종료 시 byte 삭제를 적용; 권한 UI를 열지 않으며 accessibility 사용 불가, capture 실패, stale image handle 및 상한 초과는 안정적인 typed error로 실패
* `추가` Python Runtime 1.5 Host broker는 새 Android 11+ accessibility screenshot에서 선택적 영역과 채널별 threshold로 제한된 RGB color를 검색하고 image byte를 전송하거나 보존하지 않은 채 row-major 첫 좌표 또는 결정적인 미검출 결과를 반환
* `추가` Python Runtime 1.5 Host broker는 실행 범위 PNG/JPEG template 1개를 순서가 보장된 24 KiB chunk, 1 MiB 인코딩 상한 및 SHA-256 검증으로 받고 각 변 2048 pixel과 총 100만 pixel까지 decode한 뒤 불투명 template pixel과 RGB threshold를 사용해 새 Android 11+ accessibility screenshot을 결정적인 row-major 순서로 검색; lifecycle, region 및 comparison budget은 OpenCV runtime 의존성 없이 fail-closed로 적용되고 Android가 강제하는 333 ms screenshot request interval은 정확한 interval-too-short error에 한해 350 ms 후 기존 deadline 안에서 제한적으로 retry
* `추가` Python Runtime의 명시적 엄격 JSON 결과를 스크립트 종료 후 `[result]` 접두사와 함께 Host 콘솔에 기록하고, 검증된 output artifact를 프로젝트 또는 스크립트 디렉터리의 `.python-artifacts` 아래 실행 전용 디렉터리에 원자적으로 게시한 뒤 절대 경로를 출력하며 이 예약 디렉터리는 이후 프로젝트 스냅샷에서 제외
* `추가` 독립 Lua Runtime 플러그인 백엔드에서 로컬 .lua 파일을 별도의 PUC Lua 5.4.8 플러그인 프로세스에서 실행하고 고정 컴포넌트/서명/프로토콜/ABI 검증, 제한된 실행, JavaScript 폴백 금지를 지원
* `추가` Java/Kotlin용 독립 JVM Source Runtime 플러그인 백엔드에서 단일 .java/.kt 파일을 격리 프로세스로 컴파일 및 실행하고 package/import, 실시간 console 출력, 취소 가능한 sleep, 호스트 toast, 명시적 capability 협상을 지원
* `추가` JVM Source Runtime 지원 범위는 단일 파일 Java 8 (Java provider 최소 API 24) 및 Kotlin 2.3 (Kotlin provider 최소 API 26)이며, 이번 릴리스의 실제 기기 smoke는 API 31/arm64-v8a에서만 수행했고 검증하지 않은 API 수준은 지원을 주장하지 않음
* `추가` 플러그인 시스템에서 Node.js/ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/텍스트 변환/미디어 정보 등의 확장 API 지원
* `추가` 플러그인 시스템에서 버전 지정 TaggedWire 전송과 DEX 컴파일/AI Provider 텍스트 생성을 위한 v1 개발 프로토콜 팩을 지원하고 전용 적합성 테스트 앱 및 적대적 fixture 제공
* `추가` AutoJs6 공식 플러그인이 서명으로 보호된 버전 지정 읽기 전용 설정 계약을 통해 호스트 테마 색상/다크 모드 정책/언어의 모양 스냅샷을 읽도록 지원
* `추가` 플러그인 시스템에서 오프라인 프로젝트 문서 확장 인터페이스 지원
* `추가` 파일 관리자가 단일 파일 읽기 전용 Explorer Action 플러그인 확장을 지원하고 기본 파일 페이지의 더보기 메뉴에 플러그인 작업을 동적으로 로드
* `추가` 파일 관리자가 Explorer Action 프로토콜 v2의 기본 작업을 지원하여 이미지 보기, 오디오 재생, 비디오 재생을 3개의 독립 플러그인으로 제공
* `추가` 파일 관리자가 Explorer Action 프로토콜 v3 출력 트랜잭션을 지원하고 제한된 쓰기 전용 content URI/출력 검증/비정상 종료 복구/동일 디렉터리 파일의 원자적 게시 제공
* `추가` 파일 관리자 Explorer Action 프로토콜 v4가 순서가 있는 파일/폴더/혼합 대상과 같은 상위 폴더 다중 선택을 지원하고, 폴더 메뉴와 세로 배치된 5개 동작 선택 표시줄에 플러그인 압축을 표시하며, 요청 단위 호스트 파일 세션으로 제어된 읽기와 같은 위치의 원자적 출력을 제공
* `추가` 파일 관리자 Explorer Action 프로토콜 v5가 신뢰된 단일 파일 읽기 전용 플러그인을 지원하고, UID 고정 Binder 세션을 통해 페이지 단위 압축 파일 디렉터리를 네이티브 Explorer 목록/경로 표시줄/테마/뒤로 가기 스택에 표시
* `추가` 파일 관리자 Explorer Action 프로토콜 v6가 읽을 수 있는 문서, 이미지, 오디오 및 비디오 항목을 기존 호스트 뷰어로 미리 표시
* `추가` 파일 관리자 Explorer Action 프로토콜 v7, v8 및 v9에서 보류 파일 출력의 필수 재읽기 검증, 원자적 대상 교체, 클라이언트 종료 및 재시작 복구를 지원하는 호스트 소유 디렉터리 출력 트랜잭션을 제공하여 저장소 권한 없이 선택한 ZIP을 편집하고 같은 위치에 검증된 트리를 안전하게 풀 수 있음
* `추가` 플러그인 센터에서 전역 설정 페이지 및 사용 설명 페이지 지원
* `추가` 플러그인 센터에서 SDK 플러그인 자동 마운트 기능 지원
* `추가` 플러그인 센터에서 다중 변형의 우선순위 정렬 기능 지원
* `추가` 플러그인 센터에서 플러그인 온라인 설치/온라인 업데이트 지원
* `추가` 플러그인 센터에서 플러그인 시작 페이지 열기/플러그인 숨기기 지원
* `추가` 플러그인 센터에서 사용 가능한 ABI 교집합 감지 및 안내 지원
* `추가` 플러그인 센터에서 APK Builder Template 플러그인 지원, 앱 패키징에 필요한 템플릿을 필요할 때 설치/업데이트 가능
* `추가` 홈의 플러그인 탭에 있는 플로팅 버튼을 길게 눌러 플러그인 센터를 여는 기능 지원
* `추가` runtime.loadAar 메서드, AAR 파일 동적 로드용
* `추가` 명시적 runtime.loadJarWithR8 진입점에서 기본 비활성화된 정확한 구성 요소 선택, 동일 서명 ID 고정, 제한된 5개 산출물 R8 전송, 격리 캐시 채택 및 D8/dx 폴백 없는 실패 폐쇄 동작 지원
* `추가` ui.useAndroidLayout에서 Android 네이티브 레이아웃 위젯, include/merge, 스크립트 프로젝트의 res 참조 및 shape/selector/layer-list 등의 Drawable XML 지원
* `추가` JavaScript (Rhino) 모듈에서 power/settings/work_manager 호환 인터페이스와 AppInfo 래퍼를 지원하고 Auto.js Pro 9.3.11의 일부 Base64/Crypto/WebSocket/ByteString/automator/images/timers API를 보완
* `추가` 전역 JavaScript (Rhino) ai는 모든 로컬/온라인 요청을 AI Provider 플러그인 target으로 실행; 개발자 옵션은 신뢰된 3-Stone AI 설정이나 정확한 설치/활성화/업데이트 안내를 열고 호스트는 AI profile 또는 자격 증명을 저장하지 않으며 공급자 HTTP transport도 포함하지 않음
* `추가` 전역 JavaScript (Rhino) `ai.catalog` API가 로컬/온라인 통합 플러그인 target catalog를 공개; `plugin`과 `target`을 생략하면 공식 플러그인이 선언한 기본 target을 선택하고 `target`은 하나의 정확한 target을, `plugin`은 정확한 타사 컴포넌트를 선택. 응답은 reasoning, 전체 usage 및 시간, finish reason, profile, 실제 target metadata를 포함하고 안정적 오류는 경로를 자동 변경하지 않음
* `추가` 전역 JavaScript (Rhino) `ai.session` API가 영구 AI provider 플러그인 Conversation을 만들고 target/공급자/모델/생성 제어를 고정하며 이후 `ask`/`chat`/`stream` 턴에는 새 사용자 프롬프트만 전송. 정상 완료 후 컨텍스트를 유지하고 취소/시간 초과/프로토콜 실패/명시적 닫기 시 전체 세션 해제
* `추가` `ai.ask`/`ai.chat`/`ai.stream` 및 영구 `ai.session`의 AI provider 경로에서 `structuredJson`과 JSON 객체 `responseSchema` 지원; schema가 LiteRT-LM 네이티브 JSON Schema 제약 디코딩을 활성화하고 세션은 모든 턴에 같은 schema를 고정하며 완료 JSON 텍스트를 엄격하게 검증
* `추가` AI Provider V2 로컬 target은 명시적 `cpu`, `gpu`, `npu` backend profile을 선택형 target control로 공개; 기기별 가용성을 보고하고 사용 불가 profile은 CPU fallback 없이 실패하며 세션은 하나의 backend를 고정하고 GPU는 시스템 OpenCL 로드를 요구하며 NPU는 EAP runtime 미포함으로 사용 불가
* `추가` 전역 JavaScript (Rhino) yolo 모듈에서 기본 비활성화된 명시적 컴포넌트 YOLO 공급자 경로, 버전이 지정된 Binder/PFD 전송, 매니페스트 기반 NCNN 모델 로딩, 객체 탐지, 명시적 취소 및 리소스 정리, 공급자 자동 폴백 방지 지원
* `추가` 전역 JavaScript (Rhino) tts 모듈에서 시스템 음성 합성, 긴 텍스트 지능형 분할, 논리 큐, 오프라인 음성 필터링을 포함한 음성 및 언어 검색, 관찰 가능한 작업과 오디오 이벤트, 무음 삽입, 원자적 게시를 사용하는 파일 합성 지원; 시간 제한, 백프레셔, 런타임 정리 및 지정 엔진에서 다른 엔진으로의 폴백 방지 기능도 제공
* `추가` device.getMarketName 메서드, 기기의 "제품명" 확인용 _[`issue #513`](http://issues.autojs6.com/513)_
* `추가` 예약 작업에서 월간/연간/카운트다운 작업 및 반복 종료 조건 지원 _[`issue #294`](http://issues.autojs6.com/294)_ _[`issue #537`](http://issues.autojs6.com/537)_
* `추가` 파일 관리자에서 다중 선택 및 일괄 복사/이동/삭제 작업 지원
* `추가` 파일 관리자에서 Markdown 및 HTML 파일의 안전한 미리 보기를 지원하고 GFM 렌더링/다중 테마/사용자 지정 CSS/제어된 동일 디렉터리 리소스 로딩 제공
* `추가` Markdown 및 HTML 미리 보기에서 전체 화면 모드를 지원하고 기본적으로 비활성화된 "전체 화면 모드로 시작" 설정 제공
* `추가` 파일 관리자에서 Markdown/HTML 미리보기 및 이미지 보기/오디오/비디오 재생을 맨 왼쪽 아이콘으로 실행 지원; 앱을 시스템 미디어 뷰어로 등록할 수 있으며 오디오는 백그라운드 재생 및 알림 제어 지원
* `추가` 파일 관리자 상단에 고정된 경로 표시줄에서 현재 경로 표시/현재 디렉터리 강조/상위 디렉터리 바로 이동/긴 경로 끝 자동 스크롤/가로 탐색을 지원하고, 상위/현재 디렉터리 색상을 카테고리 레이블/파일 이름과 통일하며 다국어 문자와 구분 화살표를 글리프 알파 가중 잉크 중심에 맞춰 시각적으로 가운데 정렬
* `추가` 파일 관리자에서 이미지 정보 및 상세 EXIF 보기 지원
* `추가` 이미지 편집 및 형식 변환을 독립 Image Tools 플러그인으로 제공하며, 플러그인을 사용할 수 없을 때 해당 작업만 숨기고 이미지 보기/이미지 정보/EXIF 보기는 그대로 유지
* `추가` 이미지 뷰어에서 동일 디렉터리의 모든 이미지를 자동 탐색하고 손가락 초점에 동기화된 핀치 줌, 스와이프 및 도구 모음 이전/다음 버튼 탐색, 위치/해상도/크기/형식/수정 시간/EXIF를 표시하는 간결한 3줄 정보 표시줄, 전체 화면 모드/전체 화면 시작 설정/기록이 있는 배경색 설정을 지원하며 ActionBar/시스템 표시줄/아이콘의 색상과 명암 대비가 배경색에 자동 적응
* `추가` 파일 관리자에서 APK/APKS/XAPK/APKM/APKZ/AAB 파일의 패키지 세부 정보 및 매니페스트 보기 지원
* `추가` 패키지 설치 관리자에서 APK 및 APKS/XAPK/APKM/APKZ 컨테이너의 호환 가능한 분할 APK 세트를 지원하고 디바이스 분할 선택 및 서명 인증서 일관성 검사 제공
* `추가` 코드 편집기에서 외부 플러그인의 Ace 로딩을 지원하고 서명자/신뢰/Editor API 계약/호스트 버전/바이너리 ABI 호환성을 검증하며, 플러그인을 사용할 수 없을 때 네이티브 편집기로 자동 폴백
* `추가` 코드 편집기에서 Ace 외부 플러그인의 런타임 핫 플러그를 지원하고 플러그인 설치 또는 활성화 상태 변경에 따라 편집기 엔진 자동 전환
* `추가` Ace 편집기의 내장 로컬 TypeScript 스마트 완성을 정식 기능으로 기본 활성화하고 AutoJs6 API 완성/시그니처 도움말/호버 정보/진단을 지원 (설정에서 비활성화 가능)
* `추가` Ace 편집기 설정에서 android/libraries/resources/main-app AutoJs6 TypeScript 선언 그룹의 선택적 로드를 지원하고 의존 그룹을 자동으로 추가하며, 언어 서비스 시작 시간과 메모리 사용량을 줄이기 위해 모든 선택적 그룹을 기본적으로 비활성화
* `추가` Ace 편집기에서 바로 가기 패널/작업 기록/즐겨찾기/모바일 텍스트 선택/핀치 확대 및 축소 등의 기능 지원
* `추가` 코드 편집기에서 선택한 인코딩으로 파일 다시 로드 또는 변환, 선택적 BOM, 인코딩 위험 표시 및 실행 취소/다시 실행 지원
* `추가` 코드 편집기의 도구 모음 바로 아래 정보 표시줄에 파일 이름/실시간 커서 위치 (행/열)/파일 인코딩을 표시하고, 각 항목을 탭하여 파일 세부정보 보기/지정한 줄로 이동/인코딩 변경 가능
* `추가` 코드 편집기의 시스템 상태 표시줄/ActionBar/버튼 아이콘과 텍스트/더보기 및 중첩 메뉴/편집기 내 대화상자/네이티브 편집기의 커서와 핸들 및 선택 컨트롤이 현재 편집기 테마를 동적으로 따르고, 상태 표시줄 아이콘은 명암 대비를 자동으로 유지하여 상단부터 코드 영역까지 시각적 스타일 통일
* `추가` 코드 편집기에서 몰입형 전체 화면 모드를 지원하고 메뉴와 설정 모두에서 사용할 수 있는 기본 설정을 유지; 화면 가장자리에서 스와이프하면 시스템 표시줄을 일시적으로 표시하고 뒤로 가기는 편집기를 나가기 전에 전체 화면을 우선 종료
* `추가` 코드 편집기에서 하단 로그 패널 표시 지원 (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `추가` 코드 편집기 하단 로그 패널에서 스택 프레임의 코드로 이동 지원 (by [M17764017422](https://github.com/M17764017422)) _[`pr #502`](http://pr.autojs6.com/502)_
* `추가` 코드 편집기 하단 로그 패널에서 "실행/중지" 버튼 제공, 스크립트를 빠르게 시작/중지 가능
* `추가` 코드 편집기에서 전용 설정 페이지 및 편집기 내 설정 대화상자를 지원하고 테마 미리 보기/밝은 모드와 어두운 모드의 개별 설정/글꼴/거터/자동 줄 바꿈 등의 표시 옵션 제공
* `추가` 설정 페이지에서 "실행 후 하단 로그 패널 자동 표시", "코드 하이라이트 임계값" 등의 코드 편집기 설정 옵션 제공
* `추가` 패키징된 앱 설정에서 "로그" 및 "설정" 런처 바로 가기 표시/숨기기 지원 _[`issue #550`](http://issues.autojs6.com/550)_
* `추가` 앱 패키징에서 APK Builder Template 플러그인을 사용한 원격 빌드 지원
* `추가` 개발자 옵션에서 GitHub 액세스 토큰 설정 지원, 업데이트 확인 및 플러그인 인덱스 요청 한도 확장용
* `수정` 패키징된 앱의 Rhino CommonJS require가 종속 JavaScript 모듈을 복호화하지 못해 패키징 시 컴파일된 다중 파일 TypeScript 출력이 "Cannot read file"로 실패하는 문제
* `수정` Android Activity가 제거된 뒤에도 Node.js UI 핸들이 활성 상태로 남아 지연 업데이트를 허용하고 실행 정리 시점까지 공급자 소유 리소스를 유지할 수 있던 문제
* `수정` 모듈 탐색 및 작업 공간 아카이브 예산이 너무 작아 Node.js 플러그인 실행이 대규모 평문 종속성 그래프와 실제 node_modules 작업 공간을 거부할 수 있던 문제
* `수정` 파일이 많은 작업 디렉터리에서 불필요한 모듈 스캔과 후보 파일 전체 읽기로 인해 비어 있거나 단순한 Node.js 스크립트 시작이 느려지는 문제
* `수정` 일시적인 스크립트 실행기 UI가 숨겨질 때 상주 Node.js 프로세스가 잘못 회수되는 문제
* `수정` 패키지 앱의 대화형 장기 실행 작업을 정상 중지한 후 예상되는 상주 Node.js 프로세스 Binder 연결 해제 콜백으로 stopped 상태가 failed로 덮어쓰이는 문제
* `수정` Node.js console.assert가 어설션 실패 시 stderr에 실패 메시지만 기록해야 하지만 AssertionError 예외까지 잘못 발생시키는 문제
* `수정` 런타임 프로세스 안정화 대기로 인해 Node.js 스크립트 실행 결과 반환이 지연되는 문제
* `수정` 활성 Node.js 실행 취소 시 비정상 프로세스 격리 전에 실행 등록이 제거되어 완료 대기가 차단되고 복구가 지연될 수 있는 문제
* `수정` Node.js 외부 런타임 Binder 응답 유실 후 자동 폴백으로 동일한 스크립트가 중복 실행될 수 있는 문제
* `수정` Node.js TypeScript 프로젝트를 워크스페이스 아카이브로 실행한 뒤 런타임 전용 컴파일러 출력이 소스 디렉터리에 동기화되어 후속 실행에서 원본 프로젝트 파일과 충돌할 수 있는 문제
* `수정` Android 프라이빗 디렉터리 /data/user/0 및 /data/data 별칭을 정규화하여 플러그인 요청에 다시 기록하지 않아, 올바른 Node.js workingDirectory가 sandboxRoot를 벗어난 것으로 잘못 판단되는 문제
* `수정` 호스트에 Node 런타임이 없는 nodePlugin 빌드에서 모든 ABI의 JNI/AAR 라이브러리를 잘못 제외하여, libjackpal-termexec2.so 누락으로 Rhino Shell 초기화 중 앱이 충돌하는 문제
* `수정` apksigner의 인증서 다이제스트 출력 형식 변경으로 인해 Node.js R6 증거 수집 및 검증에서 Android Build Tools 37이 보고한 APK 서명자 ID를 거부하는 문제
* `수정` IntelliJ IDEA에서 Android 모듈을 빌드할 때 compileJava 작업 일치가 모호하여 실패하는 문제
* `수정` Android가 현재 할당된 네이티브 힙 바이트를 0으로 보고할 때 정상적인 Node.js R6 상주 런타임 soak 증거가 거부되는 문제
* `수정` 수집기가 최신 Inrt 템플릿을 준비하거나 대상 ABI를 전달하지 않아 Node.js R6 패키지 APK 증거를 건너뛰거나 arm64에 고정하는 문제
* `수정` ABI 간 앱 패키징에서 설치된 호스트 프로세스의 네이티브 라이브러리를 다른 ABI 디렉터리로 복사하여, 32비트 패키지 앱이 64비트 터미널 라이브러리를 로드하고 충돌하는 문제
* `수정` 엔진 생성 전에 TypeScript UI 스크립트 컴파일이 실패할 때 전역 실행 리스너가 존재하지 않는 엔진을 참조하여 진단 보고 중 호스트가 충돌할 수 있는 문제
* `수정` 스택 프레임 및 컴파일러 진단 링크가 1 기반 소스 열을 0 기반 편집기 오프셋으로 처리하여 커서가 한 문자 더 앞으로 이동하는 문제
* `수정` DEX 컴파일러 실험적 호스트 경로에서 요청 minApi와 호환되지 않는 DEX 버전을 허용하고, 유효한 65,536 field/method ID 한계를 잘못 거부하며, 대기 중인 pipe drain이 늦게 시작될 때 rollback된 임시 아티팩트를 다시 생성할 수 있는 문제
* `수정` runtime.loadJar에서 더 높은 Java 버전을 대상으로 컴파일된 class 파일을 정상적으로 동적 로드하지 못하는 문제
* `수정` app.isInstalled/kill 등의 메서드에서 패키지 이름 인수를 정상적으로 사용하지 못하는 문제 _[`issue #495`](http://issues.autojs6.com/495)_
* `수정` Root 권한이 없는 기기에서 shell 관련 메서드를 호출할 때 응답하지 않을 수 있는 문제
* `수정` dialogs.multiChoice 메서드가 반환한 다중 선택 결과가 잘못 문자열로 변환되는 문제 _[`issue #518`](http://issues.autojs6.com/518)_
* `수정` 스레드 Looper가 종료되는 동안 스크립트 다이얼로그를 닫을 때 timer 콜백을 예약할 수 없어 앱이 충돌하는 문제
* `수정` selector 모듈이 리플렉션으로 오버로드 메서드를 호출할 때 잘못된 메서드 시그니처를 선택할 수 있는 문제
* `수정` Rhino 엔진 업데이트 후 스코프 API 호환성 회귀로 일부 런타임 보조 기능이 오작동하는 문제
* `수정` Rhino 모듈 브리지가 null/undefined, Callable, 생성자, 프로토타입, realm 및 Kotlin 반환값을 잘못 처리하여 교차 realm 객체, 콜백 누락 또는 런타임 예외가 발생할 수 있는 문제
* `수정` Proxy로 래핑된 함수를 dialogs, events, timers, UI, selector 및 automator 등의 기존 모듈에서 콜백으로 사용할 수 없고, TimerThread 콜백 인수가 중첩 배열로 전달될 수 있는 문제
* `수정` Callable 및 BaseFunction 호환 오버로드로 인해 일반 JavaScript 함수가 EventEmitter.on 등의 Java 메서드를 호출할 때 인수 타입이 모호해지는 문제
* `수정` 이벤트 재귀 디스패치 중 또는 리스너가 예외를 던질 때 EventEmitter.once 리스너가 두 번 이상 호출될 수 있는 문제
* `수정` automator.home 등의 전역 동작을 처음 호출할 때 보조 Looper 초기화가 중단되면 NullPointerException이 발생할 수 있고, 제스처 Y 좌표에 가로 배율이 잘못 사용되는 문제
* `수정` 초기 JavaScript 모듈 마이그레이션의 인수 디스패치, 반환값, 숫자 경계, 이미지 좌표 및 임시 리소스 수명 주기가 원래 구현과 일치하지 않는 문제
* `수정` Android 14+에서 화면 캡처 권한을 요청할 때 미디어 프로젝션 포그라운드 서비스 시작 오류로 앱이 충돌할 수 있는 문제 _[`issue #516`](http://issues.autojs6.com/516)_
* `수정` 여러 스레드 또는 여러 파일에서 OCR을 반복 호출할 때 화면 캡처 권한을 반복해서 요청할 수 있는 문제 _[`issue #551`](http://issues.autojs6.com/551)_
* `수정` Paddle OCR 플러그인 서비스가 장시간 실행 후 연결이 끊어지고 자동 복구되지 않을 수 있는 문제 _[`issue #515`](http://issues.autojs6.com/515)_ _[`issue #525`](http://issues.autojs6.com/525)_
* `수정` Paddle OCR 플러그인이 실행 중이 아닐 때 자동으로 시작되지 않아 기능 호출이나 프로젝트 패키징에 실패하는 문제 _[`issue #517`](http://issues.autojs6.com/517)_
* `수정` 잘못된 `images.resize(ImageWrapper, width, height)` 호출에서 높이가 OpenCV 보간 값으로 전달되고 호환되지 않는 네이티브 예외 전개로 인해 스크립트 예외 보고 대신 앱이 충돌할 수 있는 문제
* `수정` MediaInfo 플러그인 라이브러리를 사용할 수 없거나 Binder 호출에 실패할 때 파일 관리자의 미디어 상세 정보가 "..." 상태로 남거나 앱이 비정상 종료되고 Android 메타데이터로 대체하지 못하는 문제
* `수정` 파일 관리자의 동영상 상세 정보 대화 상자에 내장 오디오 형식 및 메타데이터 필드가 잘못 표시되는 문제
* `수정` 일부 WebView 버전 및 실제 기기에서 Markdown 및 HTML 미리 보기의 기본 문서가 요청 인터셉터에 의해 잘못 거부되는 문제
* `수정` 이미지 뷰어의 핀치 줌이 손가락 움직임을 제대로 따라가지 않고 축소 시 원래 크기로 돌아가기 어려우며 처음 열 때 ActionBar가 일시적 또는 계속 테마 색상을 사용하는 문제
* `수정` ImageViewer가 소멸될 때 Glide가 이미 소멸된 Activity에서 이미지 로드를 시작하려 하여 앱이 충돌하는 문제
* `수정` 접근성 서비스에서 back 등의 작업을 연속 실행할 때 예기치 않게 중단될 수 있는 문제 _[`issue #527`](http://issues.autojs6.com/527)_
* `수정` 스크립트를 반복 실행한 후 메모리 사용량이 계속 증가할 수 있는 문제 (수정 시도) _[`issue #548`](http://issues.autojs6.com/548)_
* `수정` 외부 URI 또는 백그라운드에서 스크립트를 실행할 때 원래 경로가 유지되지 않아 상대 경로 해석에 오류가 발생할 수 있는 문제 _[`issue #545`](http://issues.autojs6.com/545)_
* `수정` Tasker에서 스크립트를 정상적으로 호출하고 실행하지 못하는 문제 _[`issue #501`](http://issues.autojs6.com/501)_
* `수정` 홈 화면의 문서 탭에서 ViewPager가 유효하지 않은 포인터 인덱스를 포함한 비정상 멀티터치 시퀀스를 수신하면 앱이 충돌하는 문제
* `수정` Ace 편집기의 IME 입력/텍스트 선택/코드 접기/핀치 확대 및 축소/스택 이동 등과 관련된 여러 상호 작용 문제
* `수정` 코드 편집기 설정의 기본 제목, fx 함수 패널, 왼쪽에 고정된 문서 서랍 및 Ace 텍스트 선택 도구 모음이 현재 편집기 테마가 아닌 AutoJs6 앱 색상을 사용할 수 있고, Ace 줄 작업이 중복 표시될 수 있는 문제
* `수정` 전체 화면 편집 모드에서 소프트 키보드가 다기능 키 표시줄을 덮고, 전체 화면 안내 Snackbar 아래의 내비게이션 바 영역이 투명하게 남을 수 있는 문제
* `수정` 코드 편집기 문서 사이드바에 테마 배경만 표시되고 내용이 보이지 않으며, 편집기 설정에 편집기 배경에서 생성된 안정적인 계층별 역할 색상이 없어 목록 스크롤 및 항목 재사용 후 제목, 부제목, 카테고리 및 컨트롤 색상이 사라지는 문제
* `수정` 전체 화면이 아닌 모드에서 소프트 키보드가 다기능 키 표시줄을 덮는 문제
* `수정` 코드 편집기 정보 표시줄에서 파일 확장자가 생략되거나 문서를 열 때 임시 파일 끝 커서 좌표가 잠시 표시되는 문제 및 앱과 편집기 테마의 명암이 다를 때 다기능 키 표시줄과 소프트 키보드 사이에 밝은 틈이 순간적으로 나타나는 문제
* `수정` 대용량 파일을 호환되지 않는 인코딩으로 다시 로드하여 매우 긴 텍스트 줄이 생성될 때 Ace 편집기가 장시간 응답하지 않고 네이티브 편집기로 대체되는 문제
* `수정` Ace 외부 플러그인의 편집기 또는 설정 화면 사용 중 플러그인을 비활성화하거나 제거하면 저장하지 않은 편집 상태가 손실되거나 화면 상태가 비정상적으로 남을 수 있는 문제
* `수정` 일부 상황에서 플러그인 센터 아이콘 캐시와 당겨서 새로 고침 상태가 일치하지 않을 수 있는 문제
* `수정` 홈의 플러그인 탭에서 설치된 플러그인 아이콘이 야간 모드 전환에 따라 동적으로 바뀌지 않는 문제
* `수정` 플러그인 릴리스에 여러 배포 변형이 포함된 경우 플러그인 센터에서 잘못된 APK를 설치할 수 있는 문제
* `수정` APK Builder Template 플러그인이 중복 표시되거나 업데이트를 잘못 알릴 수 있는 문제
* `수정` 오프라인 프로젝트 문서 플러그인 업데이트 중 무결성 검증에서 오래된 캐시 콘텐츠를 재사용할 수 있는 문제
* `수정` 파일 관리자의 APK 상세 정보에서 Manifest 버튼과 완전한 여러 줄 콘텐츠가 누락된 문제
* `수정` 파일 관리자가 APK를 분석할 때 ZIP 항목 이름의 대소문자를 잘못 무시하여 대소문자만 다른 항목을 포함한 일부 유효한 패키지를 중복 항목이 있는 것으로 오인하고 상세 보기 또는 설치를 차단하는 문제
* `수정` 외부 URI 캐시 가져오기에서 콘텐츠 제공자가 제공한 파일 이름을 신뢰하여 경로 탐색 또는 같은 이름의 파일 덮어쓰기가 발생할 수 있는 문제
* `수정` 파일 관리자에서 휴지통의 파일을 삭제한 후 목록 스크롤 위치가 초기화되는 문제
* `수정` 홈 서랍 스위치 항목의 유효 터치 영역이 지나치게 작은 문제
* `수정` 플로팅 버튼 메뉴에 표시되는 최근 앱의 패키지 이름 및 Activity 정보가 올바르지 않은 문제 _[`issue #529`](http://issues.autojs6.com/529)_
* `수정` 플로팅 버튼의 "스크립트 실행" 대화상자가 최소화된 후 복원되지 않을 수 있는 문제 (수정 시도) _[`issue #500`](http://issues.autojs6.com/500)_
* `수정` 시작 화면 배경이 노치 화면의 짧은 가장자리 컷아웃 영역까지 확장되지 않는 문제
* `수정` 파일 관리자가 Explorer Action으로 새로 커밋된 인접 출력을 새로 고치지 않거나 커밋된 출력 디렉터리를 파일로 표시할 수 있는 문제
* `개선` DEX 컴파일러 진단은 기존 제한 내에서 비식별화된 INFO/WARNING/ERROR 세부 정보와 선택적 논리 origin, 아카이브 entry 및 위치 메타데이터를 보존합니다; 개발자 옵션에는 프로세스 메모리에만 유지되는 최근 JAR 로드 경로와 BUSY, 시간 초과 및 폴백 안내가 표시됩니다
* `개선` TypeScript 컴파일은 준비, 캐시 조회, 컴파일러 바인딩/프리워밍, 원격 컴파일, 출력 처리, 전체 지연 시간, 아카이브 크기, 소스 수 및 호스트 힙 변화를 요청별 개인정보 보호 관측값으로 기록하고 컴파일러 시간을 ingress 전, ingress, 단계 사이, parse, semantic, emit 및 emit 후 구간으로 분해합니다; 형식이 잘못되거나 순서가 어긋난 progress callback은 무시되며 이 관측값은 성능 임계값을 적용하지 않습니다
* `개선` KeyCode 메서드의 인수 호환성 향상 _[`issue #492`](http://issues.autojs6.com/492)_
* `개선` KeyCode 메서드에서 Shizuku 권한으로 키 코드 명령 전송 지원
* `개선` 중요하지 않은 구성 요소의 초기화를 지연하여 앱 시작 성능 향상
* `개선` Rhino 내장 리소스 컴파일 결과 및 런타임 확장 함수 메타데이터를 캐시하여 스크립트 초기화 성능 향상
* `개선` Rhino 콘솔에서 대규모 배열을 출력할 때의 서식화 성능 향상
* `개선` Kotlin 모듈 확장 아키텍처를 리팩터링하여 여러 별칭과 이름별 독립적인 `$` 접두사 제어를 지원하는 선언적 마운트 사양을 도입하고, Rhino realm을 명시적으로 바인딩하고 tasks와 work_manager 의미를 분리했으며 모듈 호환성 및 의미 회귀 테스트를 보강
* `개선` Node.js 상주 프로세스 및 프로세스 범위 런타임을 재사용하여 스크립트 웜 스타트 성능 향상
* `개선` Node.js 시작 단계별 측정 및 신뢰할 수 있는 콜드/런타임 웜 성능 보고 지원
* `개선` Node.js 내장 네이티브 라이브러리 확인 결과를 캐시하여 반복적인 플러그인 검색 감소
* `개선` 승인된 Node.js 외부 런타임 후보 및 상주 Binder 세션을 캐시하고 상태 변경 시 무효화 및 원자적 재연결을 지원하여 플러그인 검색과 바인딩 오버헤드 감소
* `개선` Node.js 기본 실행 경로에서 프로젝트 전체 암호화 모듈 스캔을 제거하여 복잡한 스크립트 및 대규모 프로젝트의 시작 성능 향상
* `개선` Node.js 엔진 전환에 실제 Rhino/Node 다중 라운드 실행 회귀 테스트를 추가하여 외부 플러그인 상주 세션 재사용/복구/완전한 종료 처리 검증
* `개선` Node.js 외부 플러그인 상주 런타임에 soak 테스트 및 프로덕션 증거 게이트를 추가하여 기기/ABI 매트릭스/런타임 수명 주기/리소스 지표/8시간 및 24시간 안정성 검증
* `개선` Node.js 네이티브 검증에서 외부 런타임 플러그인의 소스 및 빌드 루트를 확인하고 검증하도록 개선
* `개선` 필수 보고서/현재 소스 증거/프로덕션 승인 산출물이 누락되거나 만료된 경우 Node.js 최종 통합 및 릴리스 게이트가 fail-closed로 판정되도록 개선
* `개선` Node.js raw 및 dynamic 입력에서 기존 정규식 기반 TypeScript stripping을 기본적으로 비활성화하고, 임시 요청 범위 호환 플래그를 명시적으로 활성화하지 않으면 컴파일러 출력을 요구
* `개선` Node.js TypeScript 계산식 import() specifier를 닫힌 컴파일 snapshot 안에서 .ts/.mts/.cts에서 미리 컴파일된 .js/.mjs/.cjs module로 결정적으로 매핑하고, 누락/모호/경계 이탈 target에는 안정적인 오류를 반환하며 런타임에 추가된 TypeScript source로 fallback하지 않도록 개선
* `개선` 독립 실행형 Python 스크립트에서 정확한 ModuleNotFoundError가 발생하면 콘솔에 안전한 안내를 추가하고, 비공개 경로를 노출하지 않으면서 sibling/local 모듈 import에는 `project.json`과 `type: python`을 지정한 명시적 Python 프로젝트 사용을 안내
* `개선` Python Runtime 출력 chunk를 도착 즉시 Host 콘솔에 기록하고 stdout/stderr별 증분 UTF-8 디코딩으로 chunk 사이에 나뉜 코드 포인트를 보존하며 종단 간 출력 상한을 16 MiB로 확대
* `개선` Python 프로젝트의 project.json에서 양의 정수 밀리초 timeout을 지원하고 기본값은 5분, 최대값은 30분으로 하며 Runtime 플러그인 상한도 계속 적용
* `개선` Python 프로젝트의 project.json에서 "entryMode": "module" 및 정규화된 점 구분 main 이름으로 명시적 모듈 엔트리를 지원하여 표준 Python 모듈 의미론으로 패키지 상대 import를 허용
* `개선` Python project workspace 제한을 압축 후 64 MiB, file entry 8192개, 추출 후 128 MiB로 확대하고 dispatch 전에 snapshot의 실제 3차원 요구량을 Runtime Provider capability와 대조하여 project-local pure-Python dependency를 지원
* `개선` Python 프로젝트의 project.json에서 인라인 텍스트 또는 명시적인 프로젝트 상대 파일로 유한 stdin 스냅샷을 지정할 수 있어 sys.stdin.read()가 대화형 프롬프트 없이 미리 제공된 입력을 읽도록 지원
* `개선` ML Kit OCR/ML Kit Barcode/Rapid OCR/OpenCV/Image Quantization/OpenCC/Pinyin/Pinyin4j/MediaInfo 관련 기능 플러그인화
* `개선` 파일 관리자의 미디어 상세 정보 조회를 단일 MediaInfo 스냅샷으로 통합하여 Binder 호출 및 대기 시간 감소
* `개선` 외부 파일 보기 및 미디어 재생 대체 경로를 읽기 전용 URI 접근으로 제한
* `개선` 파일 관리자의 기본 클릭/주요 버튼/미디어 상세 재생/overflow 메뉴 플러그인 작업을 명시적인 기본 파일 페이지 정책으로 통합하고 결정적인 단일 폴백 적용
* `개선` Explorer Action v7에서 압축 파일 플러그인이 숨겨진 대기 출력을 읽기 전용으로 다시 열어 최종 게시 전에 검증할 수 있으며 v4 클라이언트의 직접 commit 호환성 유지
* `개선` AI 텍스트 및 DEX 컴파일러 적합성 provider를 프로덕션 `plugins` 디렉터리에서 전용 `test-apps` build group으로 이동하여 적대적 Binder 회귀 테스트 범위를 유지하고 독립 프로덕션 플러그인의 릴리스 경계를 명확화
* `개선` DEX 컴파일러 실험적 호스트 경로를 기본 비활성화하고 명시적 컴포넌트를 요구하며, 정확한 컴포넌트 UID와 전체 서명자 집합 고정, 단일 디스패치 폴백, 의미 기반 캐시 출처, 제한된 콜백 입력, 격리된 Binder 시간 제한, 구조적 DEX ZIP 검증, reliable pipe 제한 drain과 EOF 시 peer error 확인, 원본/대상 digest로 보호되는 rollback 가능한 단일 게시, 비동기 종료 신호와 게시 commit 지점 사이의 원자적 중재, 취소/시간 초과/Binder death가 commit 전에 승리할 때 강제 rollback을 적용
* `개선` DEX 컴파일러 R1.1은 실험을 기본 비활성 상태로 유지하고 runtime.loadJar의 raw JAR가 명시적으로 선택한 호스트와 동일 서명의 정확한 컴포넌트 provider를 우선 사용하도록 하며, 실패 시 내장 D8/dx로 한 번만 폴백하고 취소 시에는 폴백하지 않으며 AAR 로딩은 내장 경로에 유지합니다
* `개선` DEX 컴파일러 R1은 기본 비활성 및 exact component 명시 opt-in을 유지한 채 완료: R1.1 production 7/7, R1.2 automation 4/4, R1.3 실제 provider 매트릭스 7/7, 종료 조건 3/3. API 34 production routing 8/8, host DEX 16 suites/149 tests, wire 4/24, fake-provider 5/25 및 plugin 48 tests가 통과했고 lint는 0 error. Canonical campaign f3c2b1af-be93-41e7-b541-f167f90e5cc1은 API 24/25 D8 CLI, API 26/28/34/36 x86_64 D8Command, API 31 QV arm64 multi-user 실제 기기의 실제 provider 7개 cell을 모두 PASS; 이전 실패/의도적 종료 campaign은 보존. last-waiter 협력 취소와 광범위한 복구는 미선택 R2 작업으로 남음
* `개선` DEX 컴파일러 실험적 적합성 테스트를 실제 Android Binder 전송 및 아티팩트 저장소로 확장하여 정확한 서명 컴포넌트, 적대적 callback/출력/timeout 시나리오, 대상 API 인식 DEX 검증 및 DexClassLoader 실행을 포함
* `개선` 기본 비활성화된 순수 AI 텍스트 호스트 정책에 결정적 신뢰 선택, 정확한 ID 고정 및 재검증, 호스트/프로바이더 기능 교집합, 모호한 후보 거부, 플러그인 관리 자격 증명 격리, 민감한 내용을 포함하지 않는 진단, 취소/종료 세션 경쟁의 결정적 소유권, request-bound 도구/descriptor JSON 검증, streaming 집계 length/SHA-256/quota 검증, 디코딩된 JSON 중복 key 거부, 민감한 데이터를 포함한 모델의 `toString` 요약, capability collection용 domain-separated cache key를 적용
* `개선` 연결된 AI Provider V2 호스트 경로는 exact-component PackageManager discovery와 Binder metadata negotiation, 페이지형 `listTargets` coordination, dispatch/callback별 identity reinspection, 일회용 catalog token, credential 없는 불변 target plan, 제한된 session/PFD transport, streaming credit, process fuse, 단일 terminal owner 및 멱등 cleanup을 명시적 `ai.*` 플러그인 경로에 통합
* `개선` Ace WebView 런타임/JavaScript bridge/내장 언어 서비스/정적 리소스를 별도로 설치 가능한 플러그인으로 모듈화하여 호스트 APK 결합도를 낮추고 독립 업데이트 지원
* `개선` 프로젝트 오프라인 문서를 플러그인화하여 호스트 APK 크기를 줄이고 독립 설치 및 업데이트 지원
* `개선` 오프라인 프로젝트 문서의 최초 로드 시 파일 목록 분석 및 전체 SHA-256 검증을 백그라운드 스레드로 이동하여 UI 차단 방지
* `개선` APK 빌드에 필요한 AXML/ARSC/분석/서명/키 저장소/암호화 도구 체인을 APK Builder Template 플러그인으로 완전히 이전하고 호스트는 경량 APK 정보 검사 사용
* `개선` 코드 편집기 엔진 선택에서 사용자의 명시적 설정을 존중하고 Ace를 사용할 수 없을 때 플러그인 센터 이동 경로 제공
* `개선` 코드 편집기 "실행" 버튼을 누른 후 "중지" 버튼으로 전환하여 스크립트를 빠르게 중지 가능
* `개선` 코드 편집기 작업 표시줄과 메뉴 계층을 정리하여 로그를 상시 작업으로 배치하고 파일 인코딩 및 읽기 전용 모드의 위치를 개선
* `개선` 코드 편집기의 테마/글꼴/도구 모음/읽기 전용 모드/접기/선택 메뉴 등의 상호 작용 개선
* `개선` 코드 편집기의 파일 인코딩 감지 및 저장 안전성 개선, 인코딩 변환은 사용자가 파일을 저장할 때만 디스크에 기록
* `개선` 코드 영역에 더 많은 공간을 확보하도록 코드 편집기 정보 표시줄의 높이와 가로 여백을 추가로 축소
* `개선` 하단 로그 패널에서 독립 로그 페이지의 스택 프레임을 눌러 소스 코드로 이동 가능
* `개선` 프로젝트 문서 소스 전환을 즉시 적용
* `개선` 파일 관리자는 Manifest 버튼을 누른 후에만 전체 바이너리 AndroidManifest.xml을 백그라운드에서 디코딩/서식화하며 대용량 텍스트 스크롤 및 핀치 줌 성능 개선
* `개선` 파일 관리자의 APK 상세 정보에서 최대 SDK는 매니페스트에 명시된 경우에만 표시하고 설치 항목을 이상으로 변경하여 이상이 있을 때만 표시
* `개선` 파일 관리자의 Markdown 미리보기에서 안전하게 정제된 GitHub에서 일반적으로 사용되는 원시 HTML 및 HTTPS 원격 이미지를 지원하고 좁은 화면의 표 스크롤과 기존 형식 숨김 주석 호환성 개선
* `개선` 플러그인 센터 "정렬" 및 "필터" 대화상자의 스타일과 기능 개선
* `개선` 플러그인 센터에서 일반적인 배포 변형을 기본으로 표시하고 기타 변형 전용 페이지 제공
* `개선` 플러그인 센터의 다국어 변경 기록/언어 캐시/공식 인증/인덱스 로딩 등의 기능 개선
* `개선` 프로젝트 문자열 리소스의 다국어 번역 범위/파일 분류/이름순 정렬/ASCII 문장 부호 통일
* `개선` APK 언어 리소스를 프로젝트에서 지원하는 10개 로캘 구성으로 제한하여 패키지 크기 감소
* `개선` 빌드 시스템에서 Kotlin/KSP/AGP/R8 호환 버전 동적 선택을 지원하고 Gradle 시작 메모리 부담 감소
* `개선` 미출시 공식 AI 플러그인을 3-Stone AI로 확정하고 프로세스 간 계약을 중립적인 `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, 새 Binder descriptor, AP schema domain 및 이름이 변경된 conformance fixture로 완전히 교체; 식별자 alias를 유지하지 않음
* `개선` AI Provider V2 페이지형 target discovery를 직접 구현: 각 target은 안정적인 target/provider/model 식별자, locality, 구성 및 가용 상태, capabilities, limits, controls, 허용된 HTTPS origins를 포함; credential byte가 Binder를 통과하지 않는 plugin-managed credential과 REMOTE/HYBRID 실행을 지원하며 교체된 wire alias는 유지하지 않음
* `의존성` 모듈화 Android AXML
* `의존성` 모듈화 Android ARSC Editor
* `의존성` 모듈화 APK Signer
* `의존성` 모듈화 ARSCLib 버전 1.3.8
* `의존성` 모듈화 Spongy Castle Prov 버전 1.58.0.0
* `의존성` 모듈화 Image Quantization 버전 2.17.0
* `의존성` 모듈화 OpenCV 버전 4.8.0
* `의존성` 모듈화 AndroidX Media3 버전 1.10.1
* `의존성` Apache Commons Codec 버전 1.15 추가
* `의존성` juniversalchardet 버전 2.0.4 추가
* `의존성` Android Annotations 버전 4.8.0 제거
* `의존성` APK Parser 로컬 모듈 제거
* `의존성` Glide KSP 버전 5.0.5 제거
* `의존성` ICU4J 버전 77.1 제거
* `의존성` Jaredrummler Android Device Names 버전 2.1.1 제거
* `의존성` JCIP Annotations 버전 1.0 제거
* `의존성` Junit Jupiter 버전 5.13.4 제거
* `의존성` Retrofit2 Converter Gson 버전 3.0.0 제거
* `의존성` Retrofit2 Kotlin Coroutines Adapter 버전 0.9.2 제거
* `의존성` Tiny Sign 버전 0.9 제거
* `의존성` app 모듈에서 중복 선언된 AndroidX Room KTX 버전 2.8.1 제거
* `의존성` AndroidX MultiDex 버전 2.0.1 제거
* `의존성` MLKit Barcode Scanning 버전 17.3.0 제거
* `의존성` MLKit Text Recognition Chinese 버전 16.0.1 제거
* `의존성` OpenCC 버전 1.2.2 제거
* `의존성` Pinyin4j 버전 2.5.1 제거
* `의존성` Jieba Analysis 버전 1.0.3-SNAPSHOT (수정된 버전) 제거
* `의존성` Rapid OCR 로컬 라이브러리 제거

# v6.7.0

###### 2026/03/14

* `추가` 플러그인 센터 기능, 플러그인 설치/제거/업데이트 등의 작업 지원 (진입: 홈 서랍 버튼/홈 탭)
* `추가` 버전 기록 기능, 편집 가능한 파일의 기록 버전 보기/복원 지원 (진입: 홈 서랍 버튼/파일 관리자 메뉴/코드 편집기 메뉴)
* `추가` 휴지통 기능, 삭제된 파일/폴더 보기/복원 지원 (진입: 홈 서랍 버튼)
* `추가` Paddle OCR (PP-OCRv5) 플러그인, 광학 문자 인식용
* `추가` cvt 모듈, 데이터 단위 변환용 (프로젝트 문서 > [단위 변환](https://docs.autojs6.com/#/cvt) 참조)
* `추가` fmt 모듈, 데이터 포맷팅용 (프로젝트 문서 > [포맷팅](https://docs.autojs6.com/#/fmt) 참조)
* `추가` zip 모듈, 파일 압축 및 해제 작업용 (Ref to [Auto.js Pro](https://g.pro.autojs.org/)) (프로젝트 문서 > [Zip](https://docs.autojs6.com/#/zip) 참조)
* `추가` mediainfo 모듈, 미디어 파일의 상세 정보 보기용 (프로젝트 문서 > [미디어 정보](https://docs.autojs6.com/#/mediainfo) 참조)
* `추가` cvt.bytes 메서드, 바이트 데이터 단위 변환용 (프로젝트 문서 > [단위 변환](https://docs.autojs6.com/#/cvt) 참조)
* `추가` fmt.bytes 메서드, 바이트 데이터 포맷팅용 (프로젝트 문서 > [포맷팅](https://docs.autojs6.com/#/fmt) 참조)
* `추가` s13n.bytes 메서드, 바이트 데이터 표준화용 (프로젝트 문서 > [표준화](https://docs.autojs6.com/#/s13n) 참조)
* `추가` app.isDualInstalled 메서드, 듀얼 앱 설치 여부 확인용 (Shizuku 또는 Root 권한 필요) _[`issue #450`](http://issues.autojs6.com/450)_
* `추가` device.getSharedDeviceId 메서드, 앱 간 통합 공유 디바이스 ID 획득용 _[`issue #455`](http://issues.autojs6.com/455)_
* `추가` device.setPointerLocation 등 Toggleable 계열 메서드, 포인터 위치 시스템 설정 항목 설정 또는 조회용 _[`issue #381`](http://issues.autojs6.com/381)_
* `추가` dialogs.build 메서드가 textAllCaps/(positive/negative/neutral)TextAllCaps 옵션 인자를 지원하여 버튼 텍스트의 전체 대문자 여부를 제어 가능
* `추가` images.loadAsync 메서드, 네트워크 이미지 리소스 비동기 획득용 _[`issue #327`](http://issues.autojs6.com/327)_
* `추가` ui.getNavigationBarHeight 메서드/navigationBarHeight 속성 (getter), 내비게이션 바 높이 획득용 _[`issue #456`](http://issues.autojs6.com/456)_
* `추가` ui.getVisible(Status/Navigation)BarHeight 메서드/visible(Status/Navigation)BarHeight 속성 (getter), 상태 표시줄 또는 내비게이션 바의 가시 높이를 빠르게 획득하기 위한 기능 _[`issue #456`](http://issues.autojs6.com/456)_
* `추가` http.put/del(ete)/head 메서드, 서로 다른 HTTP 요청 메서드로 네트워크 요청 전송용
* `추가` http.getAsync/postAsync/requestAsync 등의 메서드, 네트워크 요청 비동기 전송용 _[`issue #423`](http://issues.autojs6.com/423)_
* `추가` http 모듈의 요청 관련 메서드에서 얻는 body 객체에 stream/saveToFile/close 등의 메서드 추가 _[`issue #452`](http://issues.autojs6.com/452)_
* `추가` http 모듈의 요청 관련 메서드가 캐시 제어 옵션 인자 (cacheBody/bodyCacheThresholdBytes) 지원
* `추가` http 모듈의 요청 관련 메서드가 비보안 옵션 인자 (isInsecure/insecure) 지원, 인증서 관련 예외 무시에 사용 가능 _[`issue #417`](http://issues.autojs6.com/417)_
* `추가` http 모듈의 요청 관련 메서드가 options.client 옵션 지원, OkHttpClient.Builder (followRedirects 등) 설정 가능 _[`issue #454`](http://issues.autojs6.com/454)_
* `추가` auto.state 속성 (getter) 및 shizuku.state 속성 (getter), 접근성 서비스 상태 및 Shizuku 서비스 상태 획득용
* `추가` runtime.(set/is)JavaPrimitiveWrap 메서드, Java 원시 타입 래핑 전략 설정 또는 조회용 _[`issue #435`](http://issues.autojs6.com/435)_
* `추가` autojs.(restart/exit) 메서드, AutoJs6 앱 재시작 또는 종료용, 그리고 앱 재시작 시 인자로 지정된 스크립트 자동 실행 지원 _[`issue #460`](http://issues.autojs6.com/460)_
* `추가` UiObject#isShifted 메서드, 컨트롤 위치 변화 감지용 _[`issue #469`](http://issues.autojs6.com/469)_
* `추가` UiObject#snapshot 메서드, 컨트롤의 현재 상태 캡처용 _[`issue #469`](http://issues.autojs6.com/469)_
* `추가` engines.myEngine().getStartTime() 메서드, 스크립트 엔진 시작 타임스탬프 획득용 _[`issue #449`](http://issues.autojs6.com/449)_
* `추가` crash 전역 메서드, 크래시 보고 페이지 테스트용
* `추가` structuredClone 전역 메서드, JavaScript 객체의 깊은 복사용 ( [MDN](https://developer.mozilla.org/zh-CN/docs/Web/API/Window/structuredClone) 참조)
* `추가` 등가 계열 선택기 (UiSelector#id/text/...) 및 포함 계열 선택기 (UiSelector#xxxContains) 가 정규식 인자를 지원
* `추가` 선택기의 정규식 인자가 플래그 (i, m, s, u) 를 지원
* `추가` 정규식이 lookbehind 문법을 지원 _[`issue #464`](http://issues.autojs6.com/464)_
* `추가` 파일 관리자에 "이동 대상" 및 "복사 대상" 메뉴 항목 추가, 작업 중단 및 진행 상태 표시 지원
* `추가` 코드 편집기에 "다기능 키보드" - "기호 설정" 메뉴 항목 추가, 기호 편집 및 프로필 단위의 기호 생성/가져오기/내보내기 등 지원
* `추가` 홈 서랍에 "포인터 위치" 도구 추가
* `추가` 홈 서랍에 "모든 파일 관리 권한" 스위치 추가
* `추가` 홈 서랍에 "백그라운드에서 UI 표시" 스위치 추가 ( [Xiaomi/Vivo] 기기용)
* `추가` 설정 페이지에 "Java 원시 타입 래핑" 설정 옵션 추가 _[`issue #435`](http://issues.autojs6.com/435)_
* `추가` 설정 페이지에 "예약 작업 스케줄링 엔진" 설정 옵션 추가, AlarmManager/WorkManager/JobScheduler 지원 _[`issue #457`](http://issues.autojs6.com/457)_ _[`issue #434`](http://issues.autojs6.com/434)_ _[`issue #449`](http://issues.autojs6.com/449)_ _[`issue #388`](http://issues.autojs6.com/388)_ _[`issue #378`](http://issues.autojs6.com/378)_ _[`issue #163`](http://issues.autojs6.com/163)_ _[`issue #53`](http://issues.autojs6.com/53)_ _[`issue #21`](http://issues.autojs6.com/21)_
* `추가` 설정 페이지에 "앱 런처 아이콘" 설정 옵션 추가, 적응형 아이콘/투명 배경 아이콘 지원 _[`issue #405`](http://issues.autojs6.com/405)_
* `추가` 설정 페이지에 "재시작 전략" 설정 옵션 추가, 홈 서랍의 재시작 버튼이 빠른 재시작 전략을 사용할지 설정 가능
* `추가` 설정 페이지에 "화면 캡처 권한 요청 지연" 설정 옵션 추가, 권한 요청 창이 화면을 가리는 문제 방지
* `추가` 설정 페이지의 런처 바로가기에 "플러그인" 옵션 추가, 바로가기를 통해 플러그인 센터 페이지로 이동 가능
* `추가` 패키징 앱이 부팅 시 자동 실행을 지원하고, 설정 페이지에 스위치 추가
* `추가` 패키징 앱이 앱 바로가기 (로그 페이지/설정 페이지) 지원
* `추가` 앱 패키징 설정 페이지에 실행 취소/다시 실행/저장 버튼 추가
* `추가` 앱 패키징 설정 페이지에 프로필 메커니즘 추가, 프로필 생성/수정/삭제/가져오기/내보내기 지원 _[`issue #483`](http://issues.autojs6.com/483)_
* `추가` Scrapers 도구 (run-scrapers.mjs), Gradle 빌드 스크립트의 구조화 데이터/README 공통 데이터/README 템플릿 데이터 등의 자동 업데이트용
* `추가` Gradle Wrapper 전환 도구 (gradle-wrapper-switcher), 로컬 Gradle 메이저 버전 (예: Gradle 8.x 및 Gradle 9.x) 전환용
* `수정` notice 메서드의 인자 개수 및 타입 제한 오류 _[`issue #462`](http://issues.autojs6.com/462)_
* `수정` ui.R 속성 (getter) 을 정상적으로 사용할 수 없는 문제
* `수정` ui.post 메서드의 콜백 인자에서 예외가 발생할 때 조용히 실패할 수 있는 문제
* `수정` isJavaClass/isJavaPackage 등의 전역 메서드가 무효인 문제
* `수정` timers.keepAlive 메서드의 timeout 인자 기능이 무효인 문제
* `수정` floaty.window/rawWindow 메서드가 문자열 인자를 받을 수 없는 문제 _[`issue #467`](http://issues.autojs6.com/467)_
* `수정` 잘못된 컨텍스트가 전달되어 floaty.rawWindow 에서 일부 컨트롤 표시가 비정상적인 문제
* `수정` util.isString 메서드가 무효인 문제
* `수정` util.class[Name]/getClass[Name] 이 잘못된 결과를 반환할 수 있는 문제
* `수정` util.java.array 가 Boolean 등의 타입 인자를 정상적으로 사용할 수 없는 문제
* `수정` threads.disposable() 가 반환한 객체가 데이터 접근 시 예상치 않게 박싱될 수 있는 문제 _[`issue #435`](http://issues.autojs6.com/435)_
* `수정` console.build 메서드를 여러 번 호출할 때 로그 플로팅 창의 스타일 옵션이 정상적으로 초기화되지 않는 문제
* `수정` console.build 메서드의 (title/content)BackgroundColor 옵션이 투명도 또는 착색 옵션을 덮어쓰는 문제 _[`issue #458`](http://issues.autojs6.com/458)_
* `수정` console/toast 등의 메서드가 Java Double 을 표시할 때 끝의 0 이 사라질 수 있는 문제
* `수정` console/toast 등의 메서드로 BigInt 데이터 타입을 표시할 수 없는 문제
* `수정` toast 메서드가 Toast 인스턴스에 대한 강한 참조를 유지하여 메모리 누수를 유발할 수 있는 문제
* `수정` 일부 전역 객체가 JavaScript 프로토타입 속성과 메서드를 잃을 수 있는 문제
* `수정` XML 문법에서 JavaScript 식을 속성값으로 사용할 때 this 객체의 참조 대상이 잘못될 수 있는 문제
* `수정` dialogs.build 메서드의 linkify 옵션 인자 기능이 무효인 문제
* `수정` dialogs.build 메서드가 객체 인자의 원본 데이터를 손상시킬 수 있는 문제
* `수정` canvas 요소 컨트롤의 setMaxFps 메서드 내부 프레임레이트 계산 오류
* `수정` images.concat 메서드에서 세로 방향 결합 시 너비 계산값이 잘못되는 문제
* `수정` images.requestScreenCapture 메서드 호출 시 사용자가 권한 부여를 취소하면 앱이 크래시할 수 있는 문제
* `수정` images.captureScreen 메서드로 얻은 스크린샷에 방향 오류 또는 검은 여백이 생길 수 있는 문제 _[`issue #337`](http://issues.autojs6.com/337)_
* `수정` images 관련 일부 메서드에서 영역 인자 사용 시 1 이 잘못해서 백분율로 해석되는 문제
* `수정` images 관련 일부 메서드에서 예외 발생 시 oneShot 플래그 기능이 무효가 되는 문제 _[`issue #372`](http://issues.autojs6.com/372)_
* `수정` images 관련 일부 메서드가 메모리 누수를 일으킬 수 있는 문제 _[`issue #372`](http://issues.autojs6.com/372)_
* `수정` images.save 및 ImageWrapper#saveTo 메서드에서 전달된 경로가 존재하지 않을 때 파일을 정상적으로 쓸 수 없는 문제
* `수정` ImageWrapper#recycle 메서드의 이미지 리소스 회수 기능이 무효인 문제
* `수정` ocr 의 일부 오버로드 메서드가 정상적으로 동작하지 않을 수 있는 문제
* `수정` ocr.detect 메서드로 얻은 결과가 ocr.mode 와 일치하지 않을 수 있는 문제 _[`issue #468`](http://issues.autojs6.com/468)_
* `수정` auto.registerEvent 로 등록된 접근성 서비스 이벤트가 다른 스크립트에 의해 잘못 정리되는 문제 _[`issue #466`](http://issues.autojs6.com/466)_ _[`issue #343`](http://issues.autojs6.com/343#issuecomment-3263953918)_
* `수정` Android 10 에서 UiObject#child 메서드가 ArrayIndexOutOfBoundsException 을 발생시킬 수 있는 문제 _[`issue #416`](http://issues.autojs6.com/416)_
* `수정` 내장 모듈 메서드 호출 시 Rhino 컨텍스트가 불일치할 수 있는 문제
* `수정` 스크립트가 많은 플로팅 창을 만든 뒤 종료될 때 창이 남을 수 있는 문제
* `수정` 프로젝트 실행 시 project.json 의 설정 인자가 정상적으로 파싱되지 않을 수 있는 문제
* `수정` 프로젝트 패키징 시 project.json 의 excludedDirs 설정 인자가 설정 파일 파싱 실패를 유발하는 문제 _[`issue #428`](http://issues.autojs6.com/428)_
* `수정` Android 7.x 에서 패키징 기능이 정상적으로 동작하지 않을 수 있는 문제
* `수정` 스크립트 프로젝트 설정 파일 저장 시 원래 키 이름이 변경될 수 있는 문제
* `수정` 스크립트 프로젝트 설정 파일 내 빌드 버전 번호 또는 빌드 시간이 큰 숫자일 경우 앱이 크래시할 수 있는 문제
* `수정` ImageReader 를 자주 가져오거나 재구성할 때 버퍼에 사용 가능한 프레임이 일시적으로 없어 앱이 크래시할 수 있는 문제
* `수정` 입력 이벤트 관찰자 InputEventObserver 가 앱 시작 시 눈에 띄는 버벅임을 유발할 수 있는 문제
* `수정` Shizuku 사용자 서비스 프로세스가 정상적으로 종료되지 않아 프로세스가 누적되는 문제 _[`issue #474`](http://issues.autojs6.com/474)_
* `수정` 패키징 앱이 Paddle OCR 및 Rapid OCR 기능을 정상적으로 사용할 수 없는 문제
* `수정` 패키징 앱 설정 파일 project.json 의 key 값이 예상치 않게 변경될 수 있는 문제
* `수정` 앱 패키징 설정 페이지에서 "필요한 권한" 목록을 스크롤할 때 체크박스 아이콘이 깜빡일 수 있는 문제
* `수정` 릴리스 기록 페이지에서 일부 시스템은 폰트 차이로 인해 통계 데이터가 불완전하게 표시되는 문제
* `수정` 일부 기기에서 MLKit Google OCR 이 정상적으로 초기화되지 않는 문제 (시험 수정) _[`issue #8`](http://issues.autojs6.com/8#issuecomment-3117061768)_
* `수정` 일부 기기에서 파일 관리자의 기능 버튼 클릭 이벤트가 정상적으로 발생하지 않는 문제 (시험 수정) _[`issue #465`](http://issues.autojs6.com/465)_
* `수정` 일부 기기에서 코드 편집기의 빈 줄이 사각형 문자로 표시되는 문제 (시험 수정)
* `수정` 코드 편집기에서 소프트 키보드가 텍스트 내용을 가릴 수 있는 문제
* `수정` 코드 편집기가 읽기 전용 모드에서도 내용을 편집할 수 있는 문제
* `수정` 코드 편집기가 읽기 전용 모드에서 제목 영역 및 일부 메뉴 항목을 클릭하면 앱이 크래시하는 문제
* `수정` 코드 편집기에서 큰 파일을 불러오면 앱이 크래시할 수 있는 문제 (시험 수정)
* `수정` 코드 편집기가 새 파일을 자동으로 열 때 기능 버튼 상태 초기화가 비정상적인 문제
* `수정` 코드 편집기에서 파일 편집 중 백그라운드로 전환하면 앱이 크래시할 수 있는 문제
* `수정` Android 15+ 에서 코드 편집기의 fx 버튼을 눌러도 모듈 함수 빠른 패널이 표시되지 않는 문제 (시험 수정)
* `수정` ErrorDialogActivity 가 정상적으로 시작되지 않거나 짧은 시간 내 자동으로 사라질 수 있는 문제 _[`issue #479`](http://issues.autojs6.com/479)_ _[`issue #471`](http://issues.autojs6.com/471)_ _[`issue #414`](http://issues.autojs6.com/414)_ _[`issue #340`](http://issues.autojs6.com/340#issuecomment-2973485826)_
* `수정` Canvas 생성자가 받아들이는 인자 타입 오류 _[`issue #402`](http://issues.autojs6.com/402)_
* `수정` 크래시 보고 페이지에서 상세 정보 복사 기능이 무효인 문제
* `수정` Android 16+ 에서 사용자 정의 뒤로가기 로직이 무효가 되어 뒤로가기 동작이 비정상적인 문제
* `수정` 파일 관리자 검색 결과 표시 후 뒤로가기 버튼을 누르거나 탭을 전환하면 앱이 크래시할 수 있는 문제
* `수정` 파일 관리자에서 프로젝트 폴더 삭제 후 UI 가 자동으로 새로고침되지 않는 문제
* `수정` 작업 패널의 목록 항목이 자주 바뀌면 앱이 크래시할 수 있는 문제
* `수정` "3버튼" 제스처 내비게이션 사용 시 내비게이션 바 전경색이 야간 모드와 비정상적으로 연관되는 문제
* `수정` APK 파일 형식 정보 대화상자에서 앱 이름 및 SDK 정보를 얻지 못할 수 있는 문제
* `수정` 클라이언트 모드 연결 시, 짧은 시간 안에 연결 해제 후 다시 연결하면 핸드셰이크 타임아웃이 발생하고 재연결이 어려워지는 문제
* `수정` 클라이언트 모드 연결 시, 화면 회전이나 언어 전환처럼 Activity 재생성을 유발하는 작업 때문에 AutoJs6 가 항상 연결을 다시 구성하는 문제
* `수정` 서버 모드 연결 시, 화면 회전이나 언어 전환처럼 Activity 재생성을 유발하는 작업 때문에 VSCode 콘솔에 로그가 출력되지 않는 문제 _[`issue #385`](http://issues.autojs6.com/385)_
* `수정` VSCode 플러그인 연결 시 여러 방식으로 동시에 연결하면 로그 출력 수가 배로 증가하는 문제
* `수정` 레이아웃 분석 페이지에서 컬렉션형 컨트롤에 대해 코드 생성이 실패할 수 있는 문제 (시험 수정) _[`issue #328`](http://issues.autojs6.com/328)_
* `수정` 설정 페이지의 "'볼륨 작게' 키로 녹화 제어" 스위치 기능이 무효인 문제 _[`issue #480`](http://issues.autojs6.com/480)_
* `수정` 스크립트 녹화로 생성된 코드 파일의 좌표값이 실제 화면 픽셀값과 일치하지 않을 수 있는 문제 _[`issue #480`](http://issues.autojs6.com/480)_
* `수정` 스크립트 녹화로 생성된 코드 파일의 첫 동작 (탭, 스와이프 등) 이 항상 무시되는 문제
* `수정` 플로팅 버튼의 "스크립트 실행" 대화상자에서 백그라운드로 파일 작업을 하면 앱이 크래시할 수 있는 문제
* `수정` 홈 Activity 수명 주기가 끝난 뒤 홈에 다시 들어오면 플로팅 버튼 상태가 초기화될 수 있는 문제
* `수정` Xiaomi 기기에서 "다른 앱 위에 표시" 스위치가 잘못된 설정 페이지로 이동하는 문제
* `수정` 업데이트 확인에서 다운로드를 중단한 뒤에도 네트워크 패킷 획득이 백그라운드에서 계속되는 문제 (시험 수정)
* `수정` 빌드 도구에서 isCleanup[Paddle/Rapid]Ocr 옵션을 활성화했을 때 Rebuild Project 작업이 정상 완료되지 않는 문제
* `개선` Paddle OCR (PP-OCRv3) 관련 기능의 플러그인화 _[`issue #334`](http://issues.autojs6.com/334)_ _[`issue #331`](http://issues.autojs6.com/331)_ _[`issue #325`](http://issues.autojs6.com/325)_
* `개선` runtime.loadJar 메서드의 호환성 향상 (by [LYS86](https://github.com/LYS86)) _[`pr #475`](http://pr.autojs6.com/475)_
* `개선` ui.getStatusBarHeight 메서드/statusBarHeight 속성 (getter) 의 호환성을 일정 수준 향상
* `개선` console.log 등의 메서드가 전역 객체 (images, app, ocr 등) 출력 시 상세 정보 표시를 지원
* `개선` dialogs.build 메서드가 MaterialDialog.Builder 의 네이티브 옵션 인자를 지원
* `개선` dialogs.build 메서드의 animation/linkify 옵션 인자가 느슨한 문자열 매칭을 지원
* `개선` device.rotation/rotation/width/height 메서드로 얻는 화면 방향 및 너비/높이 데이터의 안정성과 일관성을 향상
* `개선` app.launchDual/startDualActivity 등의 메서드의 실행 효율 향상 및 호환성 강화
* `개선` android.graphics.Paint#setColor 가 ColorInt/ColorHex/ColorName 등의 색상 인자를 정상적으로 파싱 가능
* `개선` 내장 모듈 관련 메서드의 실인자 타입 예외 메시지에 타입 요약 정보를 추가
* `개선` 내장 모듈 (dayjs/cheerio/dayjs/i18n) 이 스크립트 실행 모드에서 벗어나 전역 지연 로드 방식으로 대체됨
* `개선` 콘솔 관련 메서드가 대용량 데이터를 출력할 때 상한에 따라 일부 내용을 적절히 생략하여 출력 시간을 줄임
* `개선` 콘솔 플로팅 창의 카운트다운 시작값을 6초에서 9초로 증가
* `개선` 콘솔 플로팅 창의 내부 구현을 락 프리 및 큐 처리로 전환하여 인자 설정 효율과 성공률을 향상
* `개선` 콘솔 플로팅 창 배경색 관련 API (투명도/착색/기준색) 가 Android 설계 규범에 더 잘 맞도록 개선 _[`issue #458`](http://issues.autojs6.com/458)_
* `개선` 파일 관리자에서 플로팅 버튼 확장 후 메뉴 항목 클릭 시 메뉴 접힘 타이밍을 최적화
* `개선` 파일 관리자/작업 패널이 파일/작업 개수 통계 정보 표시를 지원
* `개선` 코드 편집기에서 파일 저장 실패 시 자동으로 초안 저장하고 새 파일로 다른 이름 저장을 지원
* `개선` 코드 편집기에서 대용량 파일 로딩 시 일정 수준의 부드러움을 향상
* `개선` 코드 편집기의 "찾기/바꾸기" 가 상태 유지 및 검색 개수 실시간 표시를 지원
* `개선` 코드 편집기에서 저장을 요청할 때 편집기 종료 전에 저장 성공을 보장하여 저장 실패율을 낮춤
* `개선` 코드 편집기의 저장 버튼 상태가 사용자 주관 논리에 더 부합하도록 개선
* `개선` 코드 편집기가 "-", "=", "(", "[", "{", "<" 등의 키 길게 누르기 기능을 지원
* `개선` 앱 패키징 처리 대화상자에 "중단" 버튼 추가
* `개선` 앱 패키징 처리 대화상자의 표시 방식을 개선하고 소요 시간 통계를 추가
* `개선` 새 설정으로 패키징 앱을 설치할 때 기존 앱의 관련 설정을 덮어쓰도록 개선
* `개선` 앱 패키징 설정 페이지에서 필요한 권한 (WAKE_LOCK/INTERNET/WRITE_EXTERNAL_STORAGE/...) 을 기본 선택 _[`issue #397`](http://issues.autojs6.com/397)_
* `개선` 앱 패키징 설정 페이지에서 더 많은 옵션의 읽기 및 설정을 지원 (시작 시 로그 표시/시작 시 스플래시 표시/스플래시 슬로건 텍스트/...)
* `개선` 앱 패키징 설정 페이지에 포그라운드 서비스 스위치 추가 _[`issue #406`](http://issues.autojs6.com/406)_
* `개선` 스크립트 프로젝트 설정 파일 저장 시 키 이름 충돌 감지 메커니즘을 추가하여 키 이름의 모호성을 방지
* `개선` 크래시 보고 페이지가 두 손가락 확대/축소를 통한 글꼴 크기 조정 및 자주 쓰는 기능 버튼 추가를 지원
* `개선` 앱 런처 아이콘이 적응형 아이콘 기능을 지원 _[`issue #405`](http://issues.autojs6.com/405)_
* `개선` Rhino 엔진이 제네릭 시그니처 파싱에 실패할 때 원시 reflection 타입으로 폴백하여 낮은 Android 버전에서 reflection 메서드 사용성을 강화
* `개선` 홈 서랍의 스위치형 항목에 구분선을 추가하고, 제목 텍스트 영역 클릭 시 상세 대화상자를 표시하며, 필요에 따라 시스템 설정으로 빠른 이동을 지원
* `개선` AutoJs6 시작 또는 재시작 시 홈 서랍의 "클라이언트 모드" 제목 텍스트 영역을 눌러 연결 시도를 중단할 수 있음
* `개선` 클라이언트 모드 연결이 IPv6 주소 및 도메인 주소 연결을 지원
* `개선` 클라이언트 모드 연결이 특수 목적 IPv4 주소 (loopback/broadcast/multicast/reserved/...) 에 대한 감지 안내를 지원
* `개선` 클라이언트 모드 연결이 연결 상태 표시 및 관리 (주소 수정/연결 중단) 를 지원
* `개선` 서버 모드 연결이 연결된 클라이언트 수 표시를 지원
* `개선` 알림 권한 읽기가 자동 재연결 메커니즘 ([requestRebind](https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName))) 을 지원하여 알림 감시 관련 기능의 안정성을 향상
* `개선` 플로팅 버튼이 백그라운드에서 Activity 를 시작할 때의 안전성을 강화하여 앱 크래시를 방지
* `개선` 플로팅 버튼의 "더보기" 대화상자가 비동기 데이터 로딩 방식을 사용하여 표시의 부드러움을 향상
* `개선` 플로팅 버튼의 "스크립트 실행" 대화상자에 "홈" 메뉴 항목 추가
* `개선` 플로팅 버튼의 "스크립트 실행" 대화상자가 최소화 및 상태 복원을 지원하고, 가능한 한 창 상주 또는 자동 복원을 유지
* `개선` Shizuku 권한을 사용한 스크립트 녹화 및 녹화 재생을 지원 (재생의 부드러움은 일부 영향을 받을 수 있음) _[`issue #320`](http://issues.autojs6.com/320)_
* `개선` 테마 색상 설정 페이지에서 테마 색상 위치 지정에 빠른 위치 찾기 방식을 사용하여 효율 향상
* `개선` 폐기된 [LocalBroadcastManager](https://developer.android.com/jetpack/androidx/releases/localbroadcastmanager) 를 [LiveData](https://developer.android.com/topic/libraries/architecture/livedata) 및 [SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow) 로 대체
* `개선` Gradle 빌드 스크립트에서 7z 형식 파일의 해제 효율 향상
* `개선` Gradle 빌드 스크립트가 상세한 Android Studio IDE 버전 (예: "2025.1.4.7") 획득을 지원
* `개선` Gradle 빌드 스크립트가 VersionCodesList 클래스에 필요한 데이터 자동 생성에 대응하여 스크립트 시작 지연을 줄임
* `개선` [Version Catalogs](https://developer.android.com/build/migrate-to-catalogs) 를 사용하여 Gradle 의존성과 플러그인 버전을 중앙 관리
* `개선` Gradle 빌드 스크립트를 모듈화하고, 공유 빌드 로직을 build-logic 으로 이동해 convention plugin 으로 추상화
* `개선` Gradle convention plugin 을 사용하여 로컬 AAR 라이브러리 로딩 로직을 단순화
* `개선` Gradle 9 적응을 위해 "android.enableJetifier=true" Gradle 설정 옵션 제거
* `개선` 빌드 환경 차이를 줄이기 위해 sourceCompatibility/targetCompatibility 대신 [Toolchain](https://docs.gradle.org/current/userguide/toolchains.html) 사용
* `의존성` 모듈화 Expandable Layout 버전 1.6.0
* `의존성` 모듈화 Expandable RecyclerView 버전 3.0.0-RC1
* `의존성` 모듈화 Recyclerview Flexible Divider 버전 1.4.0
* `의존성` 현지화된 Root Shell 버전 1.6
* `의존성` AndroidX Activity (KTX) 버전 1.12.2 추가
* `의존성` AndroidX Core (KTX) 버전 1.15.0 추가
* `의존성` AndroidX Work Runtime 버전 2.11.0 추가
* `의존성` AndroidX Interpolator 버전 1.0.0 추가
* `의존성` Room RxJava2 버전 2.8.1 추가
* `의존성` Android SDK 버전 35 -> 36 으로 업그레이드
* `의존성` Gradle 8 버전 8.14 -> 8.14.4 로 업그레이드
* `의존성` Gradle 9 버전 8.14 -> 9.4.0 로 업그레이드
* `의존성` Rhino 버전 1.8.1-SNAPSHOT -> 2.0.0-SNAPSHOT 로 업그레이드
* `의존성` Desugar JDK Libs 버전 2.0.4 -> 2.1.5 로 업그레이드
* `의존성` Android Material 버전 1.12.0 -> 1.13.0 로 업그레이드
* `의존성` AndroidX AppCompat 버전 1.7.0 -> 1.7.1 로 업그레이드
* `의존성` AndroidX Room 버전 2.7.1 -> 2.7.2 로 업그레이드
* `의존성` AndroidX WebKit 버전 1.13.0 -> 1.14.0 로 업그레이드
* `의존성` Material Progressbar 버전 1.4.2 -> 1.6.1 로 업그레이드
* `의존성` Apache Commons 버전 3.16.0 -> 3.18.0 로 업그레이드
* `의존성` Retrofit2 Retrofit 버전 2.11.0 -> 2.12.0 로 업그레이드
* `의존성` Retrofit2 Converter Gson 버전 2.11.0 -> 2.12.0 로 업그레이드
* `의존성` Retrofit2 RxJava2 버전 2.11.0 -> 2.12.0 로 업그레이드
* `의존성` Joda Time 버전 2.12.7 -> 2.14.0 로 업그레이드
* `의존성` Gson 버전 2.11.0 -> 2.13.1 로 업그레이드
* `의존성` ARSCLib 버전 1.3.1 -> 1.3.5 로 업그레이드
* `의존성` Jsoup 버전 1.19.1 -> 1.20.1 로 업그레이드
* `의존성` Junit Jupiter 버전 5.10.3 -> 5.13.0 로 업그레이드
* `의존성` OpenCC 버전 1.2.0 -> 1.2.2 로 업그레이드
* `의존성` Android Support Annotation v7 버전 28.0.0 제거
* `의존성` Android Support AppCompat v7 버전 28.0.0 제거
* `의존성` Android Support RecyclerView v7 버전 28.0.0 제거

# v6.6.4

###### 2025/05/31

* `힌트` API 변경: ui.(status/navigation)BarAppearanceLight[By] -> ui.(status/navigation)BarIconLight[By]
* `추가` util.dpToPx/spToPx/pxToDp/pxToSp 메서드(픽셀 단위 변환용)
* `수정` 화면을 가로 모드로 회전하면 자막이 완전히 표시되지 않을 수 있는 문제
* `수정` 화면을 가로 모드로 회전하면 일부 페이지 콘텐츠가 사이드 내비게이션 바에 가려지는 문제
* `수정` Android 15 일부 페이지에서 상태 표시줄 배경 색칠 영역이 불완전한 문제 _[`issue #398`](http://issues.autojs6.com/398)_
* `수정` 코드 편집기가 신뢰도 낮은 인코딩으로 파일을 기록해 디코딩 오류가 발생할 수 있는 문제(수정 시도)
* `개선` 앱 및 개발자 페이지 레이아웃 호환성 개선 및 불필요한 레이아웃 분류 제거
* `개선` README.md 빌드 섹션에 여러 방법 추가로 대상 설정 페이지 찾기 용이화 _[`issue #404`](http://issues.autojs6.com/404)_
* `의존성` AndroidX ConstraintLayout 버전 2.2.1 추가

# v6.6.3

###### 2025/05/27

* `추가` 릴리스 기록 기능: 다국어 변경 로그와 통계를 보기
* `추가` timers.keepAlive 메서드 (전역으로 승격) 로 스크립트 활성 유지
* `추가` engines.on('start/stop/error', callback) 등의 이벤트 리스너로 엔진 전역 이벤트 수신
* `추가` images.detectMultiColors 메서드로 여러 지점 색상 검사 _[`issue #374`](http://issues.autojs6.com/374)_
* `추가` images.matchFeatures/detectAndComputeFeatures: 풀 해상도 이미지 검색 (참조 [Auto.js Pro](https://g.pro.autojs.org/)) _[`issue #366`](http://issues.autojs6.com/366)_
* `추가` images.compressToBytes 메서드로 이미지를 압축하여 바이트 배열 반환
* `추가` images.downsample 메서드로 해상도를 낮추고 새로운 ImageWrapper 생성
* `추가` ui.keepScreenOn 메서드로 UI 페이지가 포커스될 때 화면 켜짐 유지
* `추가` ui.root 프로퍼티 (getter) 로 UI 레이아웃 내 '윈도우 콘텐츠 루트 컨테이너' 노드를 가져옴
* `추가` webview 요소가 JsBridge 기반 웹 페이지 레이아웃 지원 (참조 [Auto.js Pro](https://g.pro.autojs.org/)) [예: Layout > Interactive HTML / Vue2 + Vant (SFC)] _[`issue #281`](http://issues.autojs6.com/281)_
* `추가` 레이아웃 인스펙터가 요소 숨기기 지원 (기여 [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #371`](http://pr.autojs6.com/371)_ _[`issue #355`](http://issues.autojs6.com/355)_
* `수정` Docs 탭 및 Docs 액티비티의 온라인 문서가 시스템 네비게이션 바에 가려질 수 있는 문제 수정
* `수정` 일부 페이지에서 Toolbar 버튼을 누르면 제목 클릭 이벤트가 잘못 발생하는 문제 수정
* `수정` 일부 기기에서 코드 편집기 빈 줄이 □로 표시되던 문제 (임시 수정)
* `수정` 테마 색상 설정의 컬러 피커 대화상자가 무한 중첩되는 문제 수정
* `수정` 접근성 서비스가 꺼진 상태에서 볼륨 업 키가 모든 스크립트를 중지하지 않는 문제 수정
* `수정` 예약 작업에서 사용자 정의 브로드캐스트 메시지를 편집할 때 IME가 입력 영역을 덮는 문제 수정
* `수정` webview 내부 컨트롤이 소프트 키보드를 정상적으로 호출하지 못하는 문제 수정
* `수정` APK 정보 대화상자가 앱 이름이나 SDK 정보를 가져오지 못하는 문제 수정
* `수정` 파일 관리자 예제에서 프로젝트 폴더 진입 시 하위 폴더 내용이 로드되지 않는 문제 수정
* `수정` Android 15 UI 모드에서 상태 표시줄이 상단 콘텐츠를 가리는 문제 수정
* `수정` Android 15 일부 페이지에서 상태 표시줄 배경색이 테마 색상과 동기화되지 않는 문제 수정
* `수정` dialogs 모듈이 customView 프로퍼티를 지원하지 않는 문제 수정 _[`issue #364`](http://issues.autojs6.com/364)_
* `수정` dialogs.input 의 expression 매개변수가 실행 결과를 반환하지 않는 문제 수정
* `수정` JavaAdapter 사용 시 ClassLoader 스택 오버플로우가 발생하는 문제 수정 _[`issue #376`](http://issues.autojs6.com/376)_
* `수정` console.setContentTextColor 가 기본 텍스트 색상을 리셋하는 문제 수정 _[`issue #346`](http://issues.autojs6.com/346)_
* `수정` console.setContentBackgroundColor 가 색상 이름을 허용하지 않는 문제 수정 _[`issue #384`](http://issues.autojs6.com/384)_
* `수정` images.compress 구현 수정: 다운샘플링 대신 인코딩 품질을 변경
* `수정` images.resize 메서드가 올바르게 동작하지 않는 문제 수정
* `수정` engines.all 이 ConcurrentModificationException 을 발생시킬 수 있는 문제 수정 _[`issue #394`](http://issues.autojs6.com/394)_
* `수정` README.md 일부 언어의 날짜 형식 오류 수정
* `수정` Gradle 빌드가 라이브러리 아카이브 크기 오류로 실패할 수 있는 문제 수정 _[`issue #389`](http://issues.autojs6.com/389)_
* `개선` 레이아웃 인스펙터 메뉴에 기능 그룹화를 위한 그라데이션 구분선 추가
* `개선` project.json 이 스크립트 프로젝트 permissions 옵션 지원 (기여 [wirsnow](https://github.com/wirsnow)) _[`pr #391`](http://pr.autojs6.com/391)_ _[`issue #362`](http://issues.autojs6.com/362)_
* `개선` 단일 파일 패키징 시 설치된 앱의 선언 권한을 자동 읽어 표시 _[`issue #362`](http://issues.autojs6.com/362)_
* `개선` 테마 색상 적용 범위 확대, 더 많은 위젯 지원
* `개선` 메인 페이지 드로어 폭이 가로 및 초광폭 화면에 적응
* `개선` '앱 정보' 및 '개발자 정보' 페이지에 가로 및 컴팩트 레이아웃 추가
* `개선` 설정 대화상자에 '기본값 사용' 메뉴 추가
* `개선` 파일 관리자 FAB가 영역 밖을 터치하면 자동으로 숨김
* `개선` 코드 포매터가 `??`, `?.`, `??=` 연산자 지원
* `개선` 코드 편집기가 GB18030 / UTF-16 (LE/BE) / Shift_JIS 등 인코딩의 파일 읽기·쓰기를 지원
* `개선` 코드 편집기가 파일 상세 정보(경로/인코딩/줄바꿈 형식/총 바이트 및 문자 수 등) 표시를 지원 _[`issue #395`](http://issues.autojs6.com/395)_
* `개선` Intent 작업(편집/보기/설치/전송/재생 등)의 오류 메시지 추가
* `개선` webview 요소의 url 속성이 상대 경로 지원
* `개선` ImageWrapper#saveTo 의 path 매개변수가 상대 경로 지원
* `개선` images.save 가 PNG 압축을 quality 매개변수로 지원 _[`issue #367`](http://issues.autojs6.com/367)_
* `개선` 무시한 업데이트 목록 및 클라이언트 모드 주소 초기화 기능 추가
* `개선` 버전 업데이트 정보가 현재 UI 언어로 표시
* `개선` 비동기 로드로 파일 관리자 리스트 스크롤 부드러움 향상
* `개선` 콘솔 스크립트 예외 메시지 내용 및 형식 개선
* `개선` 샘플 코드 폴더를 초기 내용으로 재설정 기능
* `개선` APK 서명 검증 속도 향상
* `개선` APK/미디어 파일 정보 대화상자 속도 및 표시 로직 최적화
* `개선` Gradle 빌드 스크립트가 최신 버전에 더 유연하게 대응 _[`discussion #369`](http://discussions.autojs6.com/369)_
* `의존성` 내장 Material Dialogs 버전 0.9.6.0 (모듈화)
* `의존성` 내장 Material Date Time Picker 버전 4.2.3 (모듈화)
* `의존성` 내장 libimagequant 버전 2.17.0 (현지화)
* `의존성` 내장 libpng 버전 1.6.49 (현지화)
* `의존성` 추가 ICU4J 버전 77.1
* `의존성` 추가 Jsoup 버전 1.19.1
* `의존성` 추가 Material Progressbar 버전 1.4.2
* `의존성` 추가 Flexmark Java HTML to Markdown 버전 0.64.8
* `의존성` 업데이트 Gradle 8.14-rc-1 -> 8.14
* `의존성` 업데이트 AndroidX Room 2.7.0 -> 2.7.1

# v6.6.2

###### 2025/04/16

* `추가` ui.statusBarAppearanceLight, statusBarAppearanceLightBy, navigationBarColor 등의 메서드
* `추가` ui.statusBarHeight 속성 (getter) 을 사용하여 상태 표시줄의 높이를 가져옴 _[`issue #357`](http://issues.autojs6.com/357)_
* `추가` images.flip 메서드를 통해 이미지를 반전 _[`issue #349`](http://issues.autojs6.com/349)_
* `추가` 설정 페이지에 '파일 확장자' 옵션 추가
* `추가` 테마 설정 페이지가 그룹화, 정렬, 검색, 기록, 컬러 피커 개선 등 새로운 레이아웃을 지원
* `수정` Android 15 에서 상태 표시줄 배경색이 테마 색상과 일치하지 않는 문제를 수정
* `수정` plugins.load 메서드가 플러그인을 올바르게 로드하지 못하는 문제를 수정 _[`issue #290`](http://issues.autojs6.com/290)_
* `수정` Android 7.x 에서 dx 라이브러리가 올바르게 동작하지 않는 문제를 수정 _[`issue #293`](http://issues.autojs6.com/293)_
* `수정` require 를 사용해 내장 모듈을 가져올 때 ScriptRuntime 의 동기화 상태에 문제가 발생하는 (임시 해결) 문제를 수정 _[`issue #298`](http://issues.autojs6.com/298)_
* `수정` notice 모듈에 getBuilder 등의 확장 메서드가 없는 문제를 수정 _[`issue #301`](http://issues.autojs6.com/301)_
* `수정` shizuku/shell 메서드가 문자열 파라미터를 받지 않는 문제를 수정 _[`issue #310`](http://issues.autojs6.com/310)_
* `수정` colors.pixel 메서드가 단일 채널 이미지 파라미터를 받지 않는 문제를 수정 _[`issue #350`](http://issues.autojs6.com/350)_
* `수정` engines.execScript / execScriptFile 메서드가 스크립트 실행 시 기본 작업 디렉토리를 잘못 설정하는 문제를 수정 _[`issue #358`](http://issues.autojs6.com/358)_ _[`issue #340`](http://issues.autojs6.com/340)_ _[`issue #339`](http://issues.autojs6.com/339)_
* `수정` floaty.window / floaty.rawWindow 가 서브 스레드에서 실행되지 않는 문제를 수정
* `수정` floaty.getClip 이 클립보드 내용을 올바르게 가져오지 못하는 문제를 수정 _[`issue #341`](http://issues.autojs6.com/341)_
* `수정` ui.inflate 의 반환 값에서 attr, on, click 등의 프로토타입 메서드가 누락되는 문제를 수정
* `수정` XML 구문을 사용하여 JavaScript 표현식을 속성 값으로 사용할 때, 스코프 컨텍스트가 올바르게 바인딩되지 않는 문제 _[`issue #319`](http://issues.autojs6.com/319)_
* `수정` 일부 메서드 호출 시 발생하는 예외가 try..catch 로 처리되지 않는 문제를 수정 _[`issue #345`](http://issues.autojs6.com/345)_
* `수정` 레이아웃 분석 페이지에서 코드 생성 중 앱이 종료되는 문제를 수정 _[`issue #288`](http://issues.autojs6.com/288)_
* `수정` 패키지화된 앱에서 shizuku 모듈을 제대로 사용할 수 없는 문제를 수정 _[`issue #227`](http://issues.autojs6.com/227)_ _[`issue #231`](http://issues.autojs6.com/231)_ _[`issue #284`](http://issues.autojs6.com/284)_ _[`issue #287`](http://issues.autojs6.com/287)_ _[`issue #304`](http://issues.autojs6.com/304)_
* `수정` 코드 에디터에서 줄 끝으로 이동할 때 커서가 다음 줄 맨 앞으로 이동하는 문제를 수정
* `수정` 설정 페이지의 다이얼로그 요소를 연속해서 빠르게 탭할 경우 앱이 종료되는 문제를 수정
* `개선` 패키지 앱 템플릿의 APK 파일 크기 최적화
* `개선` 앱 (및 패키지 앱) 이 더 많은 권한을 지원하도록 개선 _[`issue #338`](http://issues.autojs6.com/338)_
* `개선` 앱 패키징 설정 페이지에서 Pinyin 라이브러리 옵션을 지원합니다
* `개선` 패키지 앱 메인 페이지에서 상태 표시줄 배경과 텍스트 색상을 최적화
* `개선` 패키지 앱 설정 페이지에 전체 파일 접근 및 알림 전송과 같은 특수 권한용 스위치 추가 _[`issue #354`](http://issues.autojs6.com/354)_
* `개선` 컨트롤의 텍스트와 아이콘 색상이 테마의 밝기에 따라 자동으로 조정되도록 개선
* `개선` 컨트롤 색상과 배경 간의 대비가 낮을 때 시각적 경험 개선
* `개선` 컬러 피커의 HEX 입력란에서 클립보드에서 붙여넣기 시 호환성 개선
* `개선` 앱 내비게이션 바를 시각적 경험을 위해 투명 또는 반투명으로 설정
* `개선` 라이트 모드에서 상태 표시줄과 내비게이션 바의 기본 UI 모드가 `md_grey_50` 색상으로 설정되도록 개선
* `개선` 홈 드로어의 접근성 서비스 스위치가 스크립트 코드와 동기화되도록 개선
* `개선` 메인 페이지의 문서 페이지에 양방향 검색 버튼 추가
* `개선` 메인 페이지 '파일' 탭에서 길게 눌러 플로팅 버튼의 표시 상태를 전환할 수 있도록 개선
* `개선` 코드 에디터 타이틀이 폰트 크기를 자동으로 조절하도록 개선
* `개선` 로그 페이지의 플로팅 버튼 표시가 리스트 스크롤 동작과 연동되도록 개선
* `개선` 스크립트 프로젝트의 project.json 설정 파일이 더 많은 패키징 옵션을 지원하도록 개선 _[`issue #305`](http://issues.autojs6.com/305)_ _[`issue #306`](http://issues.autojs6.com/306)_
* `개선` project.json 설정 파일이 옵션 이름의 유연한 매칭과 별칭 호환을 지원하도록 개선
* `개선` APK 파일 정보 대화상자에 파일 크기와 서명 스킴 정보를 추가
* `개선` APK 파일 정보 대화상자가 텍스트 복사 및 앱 상세 정보로 이동하기 위한 클릭 리스너를 지원하도록 개선
* `개선` com.stardust 접두어를 가진 패키지를 복원하여 코드 호환성을 개선 _[`issue #290`](http://issues.autojs6.com/290)_
* `개선` floaty.window / floaty.rawWindow 메서드가 메인 스레드와 서브 스레드 모두에서 실행되도록 개선
* `개선` 글로벌 메서드 getClip 이 필요에 따라 floaty.getClip 을 사용하도록 하여 호환성을 개선
* `개선` files.path 및 관련 메서드가 null 경로일 경우에도 적절하게 처리되도록 개선
* `개선` 최신 공식 Rhino 엔진과의 동기화 및 코드 조정
* `개선` README.md 를 개선하여 프로젝트 빌드 및 실행 방법에 대한 문서를 보강 _[`issue #344`](http://issues.autojs6.com/344)_
* `의존성` Eclipse Paho Client Mqtt V3 버전 1.1.0 추가 _[`issue #330`](http://issues.autojs6.com/330)_
* `의존성` Android SDK 버전을 34 에서 35 로 업데이트
* `의존성` Gradle 을 8.12 에서 8.14-rc-1 로 업데이트
* `의존성` Rhino 를 1.8.0-SNAPSHOT 에서 1.8.1-SNAPSHOT 으로 업데이트
* `의존성` AndroidX Recyclerview 를 1.3.2 에서 1.4.0 으로 업데이트
* `의존성` AndroidX Room 을 2.6.1 에서 2.7.0 으로 업데이트
* `의존성` AndroidX WebKit 을 1.12.1 에서 1.13.0 으로 업데이트
* `의존성` Pinyin4j 를 2.5.0 에서 2.5.1 로 업데이트

# v6.6.1

###### 2025/01/01

* `추가` 중국어 병음 변환을 위한 Pinyin 모듈 (프로젝트 문서 참고 > [중국어 병음](https://docs.autojs6.com/#/pinyin))
* `추가` 중국어 병음 변환을 위한 Pinyin4j 모듈 (프로젝트 문서 참고 > [중국어 병음](https://docs.autojs6.com/#/pinyin4j))
* `추가` 컨트롤 또는 컨트롤 컬렉션이 유사한지 여부를 확인하는 UiObject#isSimilar 및 UiObjectCollection#isSimilar 메서드
* `추가` "currentComponent" 전역 메서드로, 현재 활성화된 구성 요소의 이름 정보를 가져오는 데 사용됩니다
* `수정` 일부 환경에서 이전 버전으로 롤백하여 프로젝트가 정상적으로 컴파일되지 않는 문제
* `수정` 존재하지 않는 메서드를 호출할 때 발생할 수 있는 "비원시 값" 예외
* `수정` 일부 기기에서 스크립트 바로가기를 정상적으로 추가할 수 없는 문제 (임시 수정) _[`issue #221`](http://issues.autojs6.com/221)_
* `수정` automator.click/longClick 메서드의 파라미터 유형 제한 오류 _[`issue #275`](http://issues.autojs6.com/275)_
* `수정` 선택자가 ConsString 유형의 파라미터를 지원하지 않는 문제 _[`issue #277`](http://issues.autojs6.com/277)_
* `수정` UiObjectCollection 인스턴스에서 메서드 및 속성이 누락된 문제
* `개선` 앱 패키징 설정 페이지에서 서명 설정, keystore 관리, 권한 설정을 지원합니다 (by [luckyloogn](https://github.com/luckyloogn)) _[`pr #286`](http://pr.autojs6.com/286)_
* `개선` 플로팅 윈도우의 현재 패키지 이름 및 현재 활동 이름의 인식 정확도가 향상되었습니다 (우선 순위: Shizuku > Root > A11Y)
* `개선` currentPackage 및 currentActivity 의 인식 정확도가 향상되었습니다 (우선 순위: Shizuku > Root > A11Y)
* `개선` 로그 활동 창의 개별 항목 텍스트 내용을 더블 클릭 또는 길게 눌러 선택하는 기능 복원 _[`issue #280`](http://issues.autojs6.com/280)_
* `개선` project.json 파일이 손상된 경우에도 가능한 한 중요한 정보를 복원
* `개선` 단일 파일을 패키징할 때 자동 생성된 패키지 이름 접미사를 간체자로부터 병음으로 변환 (다음 음도 지원)
* `개선` UiSelector#findOnce 및 UiSelector#find 메서드에서 음수 인수 지원
* `개선` app.startActivity/startDualActivity 메서드의 적응성이 향상되었습니다
* `개선` UI 요소 및 className 관련 선택자가 RecyclerView, Snackbar 등과 같은 더욱 다양한 패키지 이름 접두사 생략 형식을 지원
* `개선` Rhino 엔진의 최신 업스트림 코드를 동기화하고 기존 프로젝트에 맞게 조정합니다
* `의존성` Pinyin4j 버전 2.5.0 추가
* `의존성` Jieba Analysis 버전 1.0.3-SNAPSHOT (수정된 버전) 추가
* `의존성` Gradle 버전을 8.11.1 에서 8.12 로 업그레이드

# v6.6.0

###### 2024/12/02 - 내장 모듈 재작성, 신중한 업그레이드 필요

* `힌트` 기존 모듈이 스크립트 실행 효율성을 높이기 위해 Kotlin 으로 재작성되었으나, 개선을 위한 몇 가지 반복이 필요합니다
* `힌트` 내장된 init.js 파일은 기본적으로 비어 있지만, 개발자가 사용자 정의를 위해 모듈을 마운트할 수 있습니다
* `추가` axios 모듈 / cheerio 모듈 ([AutoX](https://github.com/kkevsekk1/AutoX) 참조)
* `추가` sqlite 모듈, SQLite 데이터베이스 간단 조작에 사용됩니다 ([Auto.js Pro](https://g.pro.autojs.org/) 참조) (프로젝트 문서 > [SQLite](https://docs.autojs6.com/#/sqlite) 참조)
* `추가` mime 모듈, MIME 유형 문자열을 처리하고 구문 분석하는 데 사용됩니다 (프로젝트 문서 > [MIME](https://docs.autojs6.com/#/mime) 참조)
* `추가` nanoid 모듈, 문자열 ID 생성기로 사용할 수 있습니다 ([ai/nanoid](https://github.com/ai/nanoid) 참조)
* `추가` sysprops 모듈, 런타임 환경의 구성 데이터를 얻는 데 사용됩니다 (프로젝트 문서 > [시스템 속성](https://docs.autojs6.com/#/sysprops) 참조)
* `추가` ocr 모듈은 [Rapid OCR](https://github.com/RapidAI/RapidOCR) 엔진을 지원합니다
* `추가` 레이아웃 분석은 창 전환을 지원합니다 (Auto.js Pro
* `추가` auto.clearCache 메소드는 제어 캐시를 지우는 것을 지원합니다 (Auto.js Pro
* `추가` threads.pool 메소드는 간단한 스레드 풀 응용 프로그램을 지원합니다 (Auto.js Pro
* `추가` images.matchTemplate 메서드는 useTransparentMask 옵션 매개변수를 지원하며, 투명 이미지 검색을 지원합니다 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `추가` images.requestScreenCaptureAsync 메소드는 UI 모드에서 비동기적으로 스크린 캡처 권한을 요청하는 데 사용됩니다  (Auto.js Pro
* `추가` images.requestScreenCapture 메서드는 isAsync 옵션 매개변수를 지원하며, 비동기 방식으로 화면 캡처를 가져오는 것을 지원합니다 (Ref to [Auto.js Pro](https://g.pro.autojs.org/))
* `추가` images.on('screen_capture', callback) 등의 이벤트 리스닝 메소드는 스크린 캡처가 사용 가능 이벤트를 모니터링하는 것을 지원합니다 (Auto.js Pro
* `추가` images.stopScreenCapture 메소드는 스크린 캡처 요청과 관련된 리소스를 적극 해제하는 것을 지원합니다 (Auto.js Pro
* `추가` images.psnr/mse/ssim/mssim/hist/ncc 및 images.getSimilarity 메소드는 이미지 유사성 측정값을 얻는 데 사용됩니다
* `추가` images.isGrayscale 메소드는 이미지가 그레이스케일인지 여부를 결정하는 데 사용됩니다
* `추가` images.invert 메소드는 이미지의 색상을 반전하는 데 사용됩니다
* `추가` s13n.point/time 메소드는 포인트 객체와 시간 객체를 정규화하는 데 사용됩니다 (프로젝트 문서 > [정규화](https://docs.autojs6.com/#/s13n) 참조)
* `추가` console 모듈의 gravity (중력), touchThrough (터치 스루), backgroundTint (배경 색조) 등의 관련 메소드 (프로젝트 문서 > [콘솔](https://docs.autojs6.com/#/console) 참조)
* `추가` Mathx.randomInt/Mathx.randomFloat 메소드는 주어진 범위 내의 난수 또는 랜덤 부동 소수점을 반환하는 데 사용됩니다
* `추가` app.launchDual/startDualActivity 등의 메소드는 듀얼 실행 애플리케이션 처리를 위해 사용됩니다 (Shizuku 또는 Root 권한이 필요) (실험적)
* `추가` app.kill 메소드는 애플리케이션을 강제로 종료하는 데 사용됩니다 (Shizuku 또는 Root 권한이 필요)
* `추가` floaty.getClip 메소드는 플로팅 창의 포커스를 통해 클립보드 내용을 간접적으로 얻는 데 사용됩니다
* `수정` Fragment 하위 클래스 (예: [DrawerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/drawer/DrawerFragment.kt#L369) / [ExplorerFragment](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/ui/main/scripts/ExplorerFragment.kt#L48) 등) 내의 View Binding 메모리 누수
* `수정` [ScreenCapture](https://github.com/SuperMonster003/AutoJs6/blob/17616504ab0bba93b30ab7abc67108ee5253f39a/app/src/main/java/org/autojs/autojs/core/image/capture/ScreenCapturer.java#L70) / [ThemeColorPreference](https://github.com/SuperMonster003/AutoJs6/blob/10960ddbee71f75ef80907ad5b6ab42f3e1bf31e/app/src/main/java/org/autojs/autojs/ui/settings/ThemeColorPreference.kt#L21) 등의 클래스 내의 인스턴스 메모리 누수
* `수정` Android 14 에서 스크린 캡처 권한을 요청할 때 앱이 충돌하는 문제 (by [chenguangming](https://github.com/chenguangming)) _[`pr #242`](http://pr.autojs6.com/242)_
* `수정` Android 14 에서 포어그라운드 서비스를 시작할 때 앱이 충돌하는 문제
* `수정` Android 14 에서 코드 에디터에서 실행 버튼을 클릭해도 점등되지 않는 문제
* `수정` 프로젝트 패키징 후 필요한 라이브러리 파일이 부족하여 앱이 정상적으로 실행되지 않을 수 있는 문제 _[`issue #202`](http://issues.autojs6.com/202)_ _[`issue #223`](http://issues.autojs6.com/223)_ _[`pr #264`](http://pr.autojs6.com/264)_
* `수정` 프로젝트 편집 시 지정된 아이콘 리소스가 없는 경우 앱이 충돌하는 문제 _[`issue #203`](http://issues.autojs6.com/203)_
* `수정` 스크린 캡처 권한 획득 시 지정된 화면 방향의 스크린샷 리소스를 매개변수로 얻지 못할 수 있는 문제
* `수정` 일부 기기에서 스크립트 바로가기를 정상적으로 추가할 수 없는 문제 (시도 수정) _[`issue #221`](http://issues.autojs6.com/221)_
* `수정` http 모듈 및 관련 메소드를 호출할 때 축적된 요청으로 인해 전송 지연이 발생하는 문제 _[`issue #192`](http://issues.autojs6.com/192)_
* `수정` AutoJs6 가 메인 액티비티 페이지에 들어가기 전에 Shizuku 서비스가 정상적으로 사용할 수 없는 문제 (시도 수정) _[`issue #255`](http://issues.autojs6.com/255)_
* `수정` random(min, max) 메소드에서 결과가 오버플로우할 가능성이 있는 문제
* `수정` pickup 메소드의 결과형 매개변수가 빈 배열을 올바르게 전달할 수 없는 문제
* `수정` UiObject#bounds() 에서 얻은 컨트롤 경계가 의도와 다르게 변경될 수 있는 문제
* `수정` text/button/input 요소의 텍스트 내용에 하프 사이즈의 이중 따옴표가 포함된 경우 올바르게 구문 분석되지 않는 문제
* `수정` text/textswitcher 요소의 autoLink 속성이 작동하지 않는 문제
* `수정` 다른 스크립트가 동일한 ScriptRuntime 객체를 잘못 공유할 가능성이 있는 문제
* `수정` 전역 변수 HEIGHT 및 WIDTH 가 Getter 동적 속성을 잃는 문제
* `수정` 스크립트 시작 시 RootShell 이 즉시 로드되어 시작이 느려지는 문제
* `수정` 콘솔 플로팅 창에서 배경색을 설정하면 사각형의 모서리가 사라지는 문제
* `수정` 서비스 이상 문제를 일으킬 수 있는 접근성 서비스의 자동 시작 (시도 수정)
* `수정` 홈페이지의 문서 페이지에서 왼쪽 또는 오른쪽으로 이동할 때 WebView 컨트롤이 ViewPager 전환을 유발할 수 있는 문제
* `수정` 파일 확장명이 대문자를 포함하는 경우 파일 관리자에서 인식하지 않는 문제
* `수정` 파일 관리자가 프로젝트 디렉토리에 처음 들어갔을 때 프로젝트를 자동으로 인식하지 못할 수 있는 문제
* `수정` 파일 관리자에서 폴더를 삭제 후 페이지가 자동 갱신되지 않는 문제
* `수정` 파일 관리자에서 파일 및 폴더를 정렬할 때 ASCII 의 선행 문자명을 멀리 두는 문제
* `수정` 코드 편집기의 디버그 기능에서 FAILED ASSERTION 예외 발생
* `수정` 코드 편집기에서 디버그 중 편집기를 닫은 후 다시 정상적으로 디버그할 수 없는 문제
* `수정` 코드 편집기에서 행의 끝으로 이동할 때 끝 문자 누락 가능성 있는 문제
* `수정` 메인 액티비티 페이지에서 로그 액티비티 페이지를 실행할 때 플래시 스크린 문제 발생 가능성
* `수정` 앱 패키지가 opencc 모듈을 정상적으로 사용할 수 없는 문제
* `개선` 앱 패키징 설정 페이지의 "사용 불가 ABI" 컨트롤에 대한 탭 안내 경험을 개선했습니다
* `개선` Shizuku 를 사용하여 '포인터 위치' 디스플레이 스위치를 제어하는 것을 지원
* `개선` Shizuku 를 사용하여 '미디어 프로젝션' 및 '보안 설정 수정' 권한 스위치를 제어하는 것을 지원
* `개선` automator.gestureAsync/gesturesAsync 가 콜백 함수 매개변수를 지원
* `개선` tasks 모듈이 동기화 방식으로 데이터베이스 작업을 수행하는 것을 지원, 데이터 액세스의 불일치 문제 방지
* `개선` 스크립트 실행 모드가 파이프 기호로 구분된 모드 매개변수 (예: 'ui|auto';처럼 시작) 를 지원
* `개선` 스크립트 실행 모드가 싱글 쿼테이션 및 백틱을 지원하여 세미콜론 생략 (예: 'ui'; 또는 'ui'처럼 시작) 가능
* `개선` 스크립트 실행 모드가 axios, cheerio, dayjs 등의 모드 매개변수로 내장 확장 모듈의 빠른 가져오기를 지원 (예: 'axios';처럼 시작)
* `개선` 스크립트 실행 모드가 x 또는 jsox 모드 매개변수로 JavaScript 내장 객체 확장 모듈의 빠른 활성화를 지원 (예: 'x';처럼 시작)
* `개선` img 요소의 src 및 path 속성이 로컬 상대 경로를 지원 (예: '<img src="a.png" />'처럼)
* `개선` 코드 편집기에서 Java 클래스나 패키지명을 가져오는 위치의 지능형 판단을 지원
* `개선` images 모듈이 이미지 매개변수로 경로의 직접 사용을 지원
* `개선` importPackage 가 문자열 매개변수를 지원
* `개선` 클라이언트 모드에서 IP 주소는 클립보드 가져오기를 지능적으로 인식하며, 공백 키 입력을 지능적으로 변환하는 기능도 지원합니다
* `개선` 파일 관리자가 새 파일을 생성할 때 기본 접두사 선택 및 적절한 숫자 접미사의 자동 생성을 지원
* `개선` 파일 관리 프로젝트 실행 시 예외 메시지 구체화 _[`issue #268`](http://issues.autojs6.com/268)_
* `개선` 파일 관리자가 더 많은 유형을 지원하고, 해당 아이콘 심볼을 표시 (800 이상의 파일 타입 지원)
* `개선` 파일 관리자에서 편집 가능한 파일 타입 (jpg/doc/pdf/...) 에 편집 버튼 추가
* `개선` 파일 관리자가 APK 파일의 기본 정보, Manifest 정보 및 권한 목록을 표시하는 것을 지원
* `개선` 파일 관리자가 오디오 및 비디오 같은 미디어 파일의 기본 정보 및 MediaInfo 정보를 표시하는 것을 지원
* `개선` 단일 파일 패키징 시 적절한 정규화 이름 패키지를 자동 입력하며, 유효하지 않은 문자 필터링을 권장
* `개선` 단일 파일 패키징 시 동일한 패키지 이름의 앱이 설치된 경우 아이콘을 자동 설정하고, 버전 번호와 버전명을 증가시키는 것을 지원
* `개선` 패키지 프로젝트 설정 파일이 abis/libs 옵션을 지원하고, 기본적으로 포함되는 ABI 아키텍처와 확장 라이브러리를 지정
* `개선` 패키지 프로젝트 설정 파일의 abis/libs 옵션이 무효하거나 사용할 수 없을 때 관련 메시지를 지원
* `개선` 불필요한 증가 방지를 위해 릭 캐너리를 릴리스 버전에서 제외
* `개선` 프로젝트 소스 코드 내 모든 영어 주석에 한국어 번역 추가하여 읽기 쉬운 주석 개선
* `개선` README 및 CHANGELOG 에 여러 언어 지원 (스크립트를 통한 자동 생성)
* `개선` Gradle 빌드 스크립트의 버전 적응 능력을 개선
* `의존성` MIME Util 버전 2.3.1 추가
* `의존성` Toaster 버전 12.6 추가
* `의존성` EasyWindow (for Toaster) 버전 10.3 추가
* `의존성` Gradle 버전 8.5 -> 8.11.1 업그레이드
* `의존성` Rhino 버전 1.7.15-SNAPSHOT 에서 1.8.0-SNAPSHOT 으로 업그레이드
* `의존성` Android Material 버전 1.10.0 에서 1.12.0 으로 업그레이드
* `의존성` AndroidX Annotation 버전 1.7.0 에서 1.9.1 으로 업그레이드
* `의존성` AndroidX AppCompat 버전 1.6.1 에서 1.7.0 으로 업그레이드
* `의존성` AndroidX WebKit 버전 1.8.0 에서 1.12.1 으로 업그레이드
* `의존성` Apache Commons 버전 3.13.0 에서 3.16.0 으로 업그레이드
* `의존성` ARSCLib 버전 1.2.4 에서 1.3.1 으로 업그레이드
* `의존성` Gson 버전 2.10.1 에서 2.11.0 으로 업그레이드
* `의존성` Jackson DataBind 버전 2.13.3 에서 2.13.4.2 로 업그레이드
* `의존성` Joda Time 버전 2.12.5 에서 2.12.7 로 업그레이드
* `의존성` LeakCanary 버전 2.12 에서 2.14 로 업그레이드
* `의존성` MLKit Barcode Scanning 버전 17.2.0 에서 17.3.0 으로 업그레이드
* `의존성` MLKit Text Recognition Chinese 버전 16.0.0 에서 16.0.1 로 업그레이드
* `의존성` Retrofit2 Converter Gson 버전 2.9.0 에서 2.11.0 으로 업그레이드
* `의존성` Retrofit2 Retrofit 버전 2.9.0 에서 2.11.0 으로 업그레이드
* `의존성` Desugar JDK Libs 버전 2.0.3 에서 2.0.4 로 업그레이드
* `의존성` Test Runner 버전 1.5.2 에서 1.6.2 로 업그레이드
* `의존성` Junit Jupiter 버전 5.10.0 에서 5.10.3 으로 업그레이드
* `의존성` OkHttp3 버전 5.0.0-alpha.11 에서 4.12.0 으로 다운그레이드

# v6.5.0

###### 2023/12/02

* `추가` opencc 모듈 (프로젝트 문서 > [중국어 변환](https://docs.autojs6.com/#/opencc)) (참조 [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-8cff73265af19c059547b76aca8882cbaa3209291406f52df1dafbbc78e80c46R268))
* `추가` UiSelector 에 [plus](https://docs.autojs6.com/#/uiObjectType?id=m-plus) 및 [append](https://docs.autojs6.com/#/uiObjectType?id=m-append) 메소드 추가 _[`issue #115`](http://issues.autojs6.com/115)_
* `추가` 앱 패키징 설정 페이지에서 ABI 및 라이브러리 필터링을 지원합니다 (Ref to [AutoX](https://github.com/kkevsekk1/AutoX)) _[`issue #189`](http://issues.autojs6.com/189)_
* `수정` 번들 앱 파일 크기 비정상적으로 큰 문제 (참조 [AutoX](https://github.com/kkevsekk1/AutoX) / [LZX284](https://github.com/SuperMonster003/AutoJs6/pull/187/files#diff-d932ac49867d4610f8eeb21b59306e8e923d016cbca192b254caebd829198856R61)) _[`issue #176`](http://issues.autojs6.com/176)_
* `수정` 번들 앱이 일부 예외 메시지를 표시하지 못하고 인쇄하지 않는 문제
* `수정` 앱 패키징 설정 페이지에서 앱 아이콘 선택 후 빈 아이콘이 표시될 수 있던 문제를 수정했습니다
* `수정` 번들 앱이 MLKit Google OCR 라이브러리를 포함할 때 발생할 수 있는 초기화되지 않은 컨텍스트 예외
* `수정` ocr.recognizeText/detect 메소드가 작동하지 않는 문제
* `수정` 일부 텍스트 (로그 페이지 등) 의 언어가 앱 설정 언어와 일치하지 않는 문제
* `수정` 홈페이지 드로워 스위치 항목에서 일부 언어가 글자 넘침 현상이 발생할 수 있는 문제
* `수정` 일부 장치에서 접근성 서비스가 켜진 후 즉시 자동으로 꺼지고 아무 메시지도 표시되지 않는 문제 _[`issue #181`](http://issues.autojs6.com/181)_
* `수정` 일부 장치에서 접근성 서비스가 켜진 후 물리 버튼이 앱 충돌을 유발하는 문제 (시도됨) _[`issue #183`](http://issues.autojs6.com/183)_ _[`issue #186`](http://issues.autojs6.com/186#issuecomment-1817307790)_
* `수정` auto(true) 로 접근성 서비스를 다시 시작한 후 발생하는 pickup 기능 오류 문제 (시도됨) _[`issue #184`](http://issues.autojs6.com/184)_
* `수정` floaty 모듈에서 떠있는 창을 만들 때 발생할 수 있는 앱 충돌 문제 (시도됨)
* `수정` app.startActivity 에서 약칭 매개변수를 사용할 수 없는 문제 _[`issue #182`](http://issues.autojs6.com/182)_ _[`issue #188`](http://issues.autojs6.com/188)_
* `수정` importClass 를 사용할 때 클래스 이름이 전역 변수와 충돌하면 코드가 예외를 발생시키는 문제 _[`issue #185`](http://issues.autojs6.com/185)_
* `수정` Android 7.x 에서 접근성 서비스를 사용할 수 없는 문제
* `수정` Android 14 에서 runtime.loadJar/loadDex 메소드가 정상적으로 작동하지 않는 문제 (시도됨)
* `수정` 안드로이드 시스템의 빠른 설정 패널에서 "레이아웃 범위 분석" 및 "레이아웃 계층 분석"을 사용할 수 없는 문제 _[`issue #193`](http://issues.autojs6.com/193)_
* `수정` 자동 업데이트 검사가 앱 [ANR](https://developer.android.com/topic/performance/vitals/anr) 을 유발할 수 있는 문제 (시도됨) _[`issue #186`](http://issues.autojs6.com/186)_
* `수정` 파일 관리자에서 샘플 코드 폴더 클릭 후 '위로' 버튼을 클릭 시 작업 디렉터리 페이지로 돌아가지 않는 문제 _[`issue #129`](http://issues.autojs6.com/129)_
* `수정` 코드 편집기의 바꾸기 기능 사용 시 바꾸기 버튼이 표시되지 않는 문제
* `수정` 코드 편집기에서 길게 눌러 삭제 시 앱 충돌 문제가 발생할 수 있는 문제 (시도됨)
* `수정` 코드 편집기에서 fx 버튼 클릭 시 모듈 함수 빠른 선택 패널이 표시되지 않는 문제
* `수정` 코드 편집기 모듈 함수 빠른 선택 패널에 표시되는 함수 이름이 넘칠 수 있는 문제
* `개선` 코드 편집기 모듈의 빠른 기능 패널이 야간 모드에 적응합니다
* `개선` 포장된 애플리케이션의 시작 페이지가 야간 모드에 적응하고 애플리케이션 아이콘의 레이아웃이 조정됩니다
* `개선` 앱 패키징 설정 페이지에서 소프트 키보드 ENTER 키로 커서 초점을 이동할 수 있도록 지원합니다
* `개선` 앱 패키징 설정 페이지에서 ABI 제목과 라이브러리 제목을 눌러 전체 선택 상태를 전환할 수 있도록 지원합니다
* `개선` 앱 패키징 설정 페이지에서 기본 ABI를 더 똑똑하게 선택하고, 선택할 수 없는 항목에 대한 안내 힌트를 추가했습니다
* `개선` 파일 관리자에서는 파일 및 폴더의 유형 및 특성에 따라 메뉴 항목의 표시가 조정됩니다
* `개선` 파일 관리자의 폴더 마우스 오른쪽 클릭 메뉴에 애플리케이션 포장 옵션이 추가됩니다
* `개선` 접근성 서비스가 활성화되었지만 제대로 작동하지 않는 경우, AutoJs6 홈페이지의 드로어 스위치에 비정상적인 상태가 반영됩니다
* `개선` 콘솔에는 오류 메시지를 인쇄할 때 상세한 스택 정보가 포함됩니다.
* `의존성` ARSCLib 버전 1.2.4 추가
* `의존성` Flexbox 버전 3.0.0 추가
* `의존성` Android OpenCC 버전 1.2.0 추가
* `의존성` Gradle 버전 8.5-rc-1 -> 8.5 업그레이드

# v6.4.2

###### 2023/11/15

* `추가` dialogs.build 메서드는 inputSingleLine 옵션 매개변수를 지원합니다
* `추가` console.setTouchable 메서드 _[`issue #122`](http://issues.autojs6.com/122)_
* `수정` ocr 모듈 일부 메서드가 영역 매개변수를 인식하지 못하는 문제 _[`issue #162`](http://issues.autojs6.com/162)_  _[`issue #175`](http://issues.autojs6.com/175)_
* `수정` Android 7.x 에서 새로운 버전을 발견했을 때 버전 세부 정보를 가져올 수 없는 문제
* `수정` Android 14 에서 스크린샷 권한을 요청할 때 애플리케이션이 충돌하는 문제
* `수정` 홈 화면 드로어에서 '플로팅 버튼' 스위치를 빠르게 전환할 때 애플리케이션이 충돌할 수 있는 문제
* `수정` 메뉴를 사용해 플로팅 버튼을 닫을 때 애플리케이션을 재시작하면 플로팅 버튼이 여전히 표시되는 문제
* `수정` Android 13 이상의 시스템 설정 페이지에서 AutoJs6 언어를 선택하고 전환한 후 적용되지 않는 문제
* `수정` 빌드 도구가 처음 빌드할 때 OpenCV 리소스를 자동으로 배포하지 못하는 문제
* `개선` 네이티브 bridges 모듈로 스크립트 실행 효율성 향상 (Ref to [aiselp](https://github.com/aiselp/AutoX/commit/7c41af6d2b9b36d00440a9c8b7e971d025f98327))
* `개선` 접근성 서비스 관련 코드를 리팩토링하여 접근성 서비스의 기능 안정성 향상 (실험성) _[`issue #167`](http://issues.autojs6.com/167)_
* `개선` UiObject 및 UiObjectCollection 의 출력 형식
* `개선` Gradle JDK 버전이 요구 사항을 충족하지 않을 때 빌드 도구에서 업그레이드 알림
* `의존성` Gradle 버전 8.4 -> 8.5-rc-1 로 업그레이드
* `의존성` Commons IO 버전 2.14.0 -> 2.8.0 으로 다운그레이드
* `의존성` Jackson DataBind 버전 2.14.3 -> 2.13.3 으로 다운그레이드

# v6.4.1

###### 2023/11/02

* `수정` 빌드 도구가 알 수 없는 플랫폼에 적응하지 못하는 문제 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `수정` 스크립트 종료 시 애플리케이션이 충돌할 수 있는 문제 _[`issue #159`](http://issues.autojs6.com/159)_
* `수정` http 모듈에서 응답 객체의 body.contentType 반환 값 유형 오류 _[`issue #142`](http://issues.autojs6.com/142)_
* `수정` device.width 및 device.height 반환 데이터가 정확하지 않은 문제 _[`issue #160`](http://issues.autojs6.com/160)_
* `수정` 코드 편집기에서 길게 눌러 삭제할 때 애플리케이션이 충돌할 수 있는 문제 (테스트 수정) _[`issue #156`](http://issues.autojs6.com/156)_
* `수정` 코드 편집기에서 역방향으로 텍스트를 선택한 후 일반 작업을 수행할 때 애플리케이션이 충돌할 수 있는 문제
* `수정` 일부 장치에서 AutoJs6 애플리케이션 아이콘을 길게 눌렀을 때 바로가기 메뉴가 표시되지 않는 문제
* `수정` 일부 장치에서 프로젝트를 패키징할 때 확인 버튼을 클릭해도 반응이 없는 문제
* `수정` app.sendBroadcast 및 app.startActivity 에서 약칭 매개변수를 사용할 수 없는 문제
* `수정` floaty 모듈의 JsWindow#setPosition 등 메서드를 처음 호출할 때의 기능 이상
* `개선` ADB 명령어 실행을 지원하기 위해 Termux 관련 권한 추가 _[`issue #136`](http://issues.autojs6.com/136)_
* `개선` http 모듈에서 가져온 응답 객체를 반복해서 body.string() 및 body.bytes() 메서드로 사용할 수 있음
* `개선` GitHub Actions 자동 패키징 지원 추가 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #158`](http://pr.autojs6.com/158)_
* `개선` 빌드 도구가 Temurin 플랫폼에 적응
* `의존성` Gradle 버전 8.4-rc-3 -> 8.4 로 업그레이드
* `의존성` Android dx 버전 1.11 -> 1.14 로 업그레이드

# v6.4.0

###### 2023/10/30

* `추가` ocr 모듈이 Paddle Lite 엔진을 지원 (by [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`pr #120`](http://pr.autojs6.com/120)_
* `추가` 패키징 기능이 내장 플러그인과 외부 플러그인을 모두 지원 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_
* `추가` WebSocket 모듈 (참고 프로젝트 문서 > [WebSocket](https://docs.autojs6.com/#/webSocketType))
* `추가` barcode / qrcode 모듈 (참고 프로젝트 문서 > [바코드](https://docs.autojs6.com/#/barcode) / [QR 코드](https://docs.autojs6.com/#/qrcode))
* `추가` shizuku 모듈 (참고 프로젝트 문서 > [Shizuku](https://docs.autojs6.com/#/shizuku)) 및 홈 드로어 권한 스위치
* `추가` device.rotation / device.orientation 등 메서드
* `추가` 내부 Java 클래스가 class 정적 속성 액세스를 지원
* `추가` 안드로이드 시스템 설정 페이지에서 애플리케이션 언어 선택 및 전환 지원 ( Android 13 이상)
* `추가` 설정 페이지에서 애플리케이션 아이콘을 길게 눌러 활성화되는 [애플리케이션 바로가기](https://developer.android.com/guide/topics/ui/shortcuts?hl=zh-cn) 지원, 문서 및 설정 페이지로 이동 가능
* `수정` 일부 PR 을 다시 병합하여 일부 스크립트가 정상적으로 종료되지 않는 문제 해결 (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `수정` 패키징 애플리케이션에서 AutoJs6 의 새로운 API 를 사용할 수 없는 문제 (by [LZX284](https://github.com/LZX284)) _[`pr #151`](http://pr.autojs6.com/151)_ _[`issue #149`](http://issues.autojs6.com/149)_
* `수정` 패키징 애플리케이션이 야간 모드에서 스타일 이상 발생
* `수정` VSCode 플러그인이 파일을 로컬에 저장할 때 파일 확장명 정보 손실 문제
* `수정` 코루틴 기능을 사용하여 프로젝트를 실행할 때 애플리케이션이 충돌하는 문제
* `수정` 재시작 또는 애플리케이션 종료 시 플로팅 버튼이 위치 상태 정보를 기록하지 않는 문제
* `수정` 장치 화면 방향이 변경될 때 업데이트된 장치 구성 정보를 가져올 수 없는 문제 _[`issue #153`](http://issues.autojs6.com/153)_
* `수정` 화면이 가로로 회전할 때 툴바 제목 폰트가 너무 작은 문제
* `수정` 화면이 가로로 회전할 때 애플리케이션 홈 탭이 너무 빽빽한 문제
* `수정` 화면이 가로로 회전할 때 플로팅 버튼이 화면에서 넘치는 문제 _[`issue #90`](http://issues.autojs6.com/90)_
* `수정` 화면이 여러 번 회전할 때 플로팅 버튼의 좌표 및 화면 가장자리 방향을 복원할 수 없는 문제
* `수정` 일부 장치에서 메시지 플로팅 프레임이 누락되거나 중복 표시되는 문제
* `수정` 여러 스크립트가 동시에 실행될 때 메시지 플로팅 프레임이 가려질 수 있는 문제 _[`issue #67`](http://issues.autojs6.com/67)_
* `수정` 레이아웃을 브로드캐스트 메시지로 분석할 때 레이아웃을 클릭해도 메뉴가 나타나지 않거나 애플리케이션이 충돌하는 문제
* `수정` 두 번째 및 이후에 생성된 WebSocket 인스턴스가 정상적으로 리스너를 트리거하지 않는 문제
* `수정` importPackage 의 전역 리디렉션 메서드를 철회하여 특정 범위에서 패키지 가져오기 예외 방지 _[`issue #88`](http://issues.autojs6.com/88)_
* `수정` 로그 액티비티 페이지에서 복사 또는 내보내기 기능을 사용할 때 애플리케이션이 충돌할 수 있는 문제
* `개선` 로그 액티비티 페이지의 내보내기 기능을 '발송' 기능으로 이름 변경하고 실질적 의미의 내보내기 기능으로 다시 구현
* `개선` 로그 액티비티 페이지에서 발송 기능이 항목 수가 너무 많을 때 자동으로 잘라내고 알림 기능 추가
* `개선` ocr 모듈이 Google MLKit 및 Paddle Lite 엔진을 모두 호환 (참고 프로젝트 문서 > [광학 문자 인식](https://docs.autojs6.com/#/ocr?id=p-mode))
* `개선` 접근성 서비스 자동 시작 성공률 향상
* `개선` Kotlin 어노테이션 처리를 kapt 에서 KSP 로 전환
* `개선` 빌드 도구가 IntelliJ Idea EAP 버전을 지원
* `개선` Java 배포 버전에 적응하여 '유효하지 않은 배포 버전' 문제 방지
* `개선` 빌드 도구가 IDE 및 관련 플러그인의 버전 다운그레이드 논리를 최적화하고 버전 예측 능력 향상
* `개선` VSCode 플러그인 1.0.7 에 적응
* `의존성` Rikka Shizuku 버전 13.1.5 추가
* `의존성` MLKit Barcode Scanning 버전 17.2.0 추가
* `의존성` Android SDK 버전 33 -> 34 로 업그레이드
* `의존성` OpenCV 버전 4.5.5 -> 4.8.0 으로 업그레이드 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `의존성` Gradle 버전 8.3-rc-1 -> 8.4-rc-3 로 업그레이드
* `의존성` Apache Commons Lang3 버전 3.12.0 -> 3.13.0 으로 업그레이드
* `의존성` Glide 버전 4.15.1 -> 4.16.0 으로 업그레이드
* `의존성` Android Analytics 버전 14.3.0 -> 14.4.0 으로 업그레이드
* `의존성` AndroidX WebKit 버전 1.7.0 -> 1.8.0 으로 업그레이드
* `의존성` AndroidX Preference 버전 1.2.0 -> 1.2.1 으로 업그레이드
* `의존성` AndroidX Annotation 버전 1.6.0 -> 1.7.0 으로 업그레이드
* `의존성` AndroidX Recyclerview 버전 1.3.0 -> 1.3.2 로 업그레이드
* `의존성` Android Material 버전 1.9.0 -> 1.10.0 으로 업그레이드
* `의존성` AndroidX AppCompat 버전 1.4.2 -> 1.6.1 으로 업그레이드
* `의존성` Commons IO 버전 2.8.0 -> 2.14.0 으로 업그레이드
* `의존성` Jackson DataBind 버전 2.13.3 -> 2.14.3 으로 업그레이드
* `의존성` Zeugma Solutions LocaleHelper 버전 1.5.1 제거

# v6.3.3

###### 2023/07/21

* `추가` 코드 편집기의 코드 주석 기능 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `추가` auto.stateListener 로 접근성 서비스 연결 상태 감시 (by [little-alei](https://github.com/little-alei)) _[`pr #98`](http://pr.autojs6.com/98)_
* `추가` UiObject 유형에 nextSibling / lastChild / offset 등 메서드 추가 (참고 프로젝트 문서 > [컨트롤 노드](https://docs.autojs6.com/#/uiObjectType))
* `수정` VSCode 플러그인이 스크립트 문자열의 총 길이가 네 자리 십진수 이상일 때 데이터를 분석하지 못하는 문제 _[`issue #91`](http://issues.autojs6.com/91)_ _[`issue #93`](http://issues.autojs6.com/93)_ _[`issue #100`](http://issues.autojs6.com/100)_ _[`issue #109`](http://issues.autojs6.com/109)_
* `수정` VSCode 플러그인이 파일을 정상적으로 저장하지 못하는 문제 _[`issue #92`](http://issues.autojs6.com/91)_ _[`issue #94`](http://issues.autojs6.com/93)_
* `수정` 플로팅 버튼 메뉴 항목 '접근성 서비스 관리' 클릭 후 페이지 이동이 발생하지 않는 문제
* `수정` runtime.requestPermissions 메서드가 누락된 문제 _[`issue #104`](http://issues.autojs6.com/104)_
* `수정` events.emitter 가 MainThreadProxy 매개변수를 지원하지 않는 문제 _[`issue #103`](http://issues.autojs6.com/103)_
* `수정` 코드 편집기에서 코드 포맷팅이 되지 않는 문제 _[`pr #78`](http://pr.autojs6.com/78)_
* `수정` JavaAdapter 사용 시 ClassLoader 호출 스택 오버플로우 문제 _[`issue #99`](http://issues.autojs6.com/99)_ _[`issue #110`](http://issues.autojs6.com/110)_
* `개선` 모듈 작용 범위 조정 (by [aiselp](https://github.com/aiselp)) _[`pr #75`](http://pr.autojs6.com/75)_ _[`pr #78`](http://pr.autojs6.com/78)_
* `개선` 출시 버전 애플리케이션 실행 시 서명 검증 제거 (by [LZX284](https://github.com/LZX284)) _[`pr #81`](http://pr.autojs6.com/81)_
* `개선` 코드 편집기의 코드 주석 기능 동작, 스타일 및 커서 위치 처리 _[`pr #98`](http://pr.autojs6.com/98)_ 기반
* `개선` 코드 주석 메뉴 항목 추가 _[`pr #98`](http://pr.autojs6.com/98)_ 기반
* `개선` VSCode 플러그인 1.0.6 에 적응
* `개선` UiObject#parent 메서드에 단계 매개변수 지원 추가 (참고 프로젝트 문서 > [컨트롤 노드](https://docs.autojs6.com/#/uiObjectType))
* `의존성` Gradle 버전 8.2 -> 8.3-rc-1 로 업그레이드

# v6.3.2

###### 2023/07/06

* `추가` crypto 모듈 (프로젝트 문서 참조 > [암호화](https://docs.autojs6.com/#/crypto)) _[`issue #70`](http://issues.autojs6.com/70)_
* `추가` UI 모드에 textswitcher / viewswitcher / viewflipper / numberpicker / video / search 등의 컨트롤 추가
* `추가` 로그 페이지에 로그 복사 및 내보내기 기능 추가 _[`issue #76`](http://issues.autojs6.com/76)_
* `추가` 클라이언트 모드에 IP 주소 히스토리 기능 추가
* `수정` 클라이언트 모드가 자동 연결되거나 서버 모드가 자동으로 시작된 후 IP 주소 정보를 표시하지 않는 문제
* `수정` 클라이언트 모드 및 서버 모드 연결 후 언어 또는 다크 모드를 변경할 때 연결이 끊기고 다시 연결할 수 없는 문제
* `수정` 클라이언트 모드에서 대상 주소 입력 시 사용자 지정 포트를 사용할 수 없는 문제
* `수정` 클라이언트 모드에서 대상 주소 입력 시 일부 문자가 AutoJs6 를 충돌시키는 문제
* `수정` VSCode 플러그인의 원격 명령 해석 실패로 인해 명령이 응답하지 못하는 문제 (실험적 수정)
* `수정` Android 7.x 에서 새로운 버전을 발견했을 때 버전 세부 정보를 가져올 수 없는 문제
* `수정` images.pixel 이 접근성 서비스의 스크린샷 픽셀 색상을 가져오지 못하는 문제 _[`issue #73`](http://issues.autojs6.com/73)_
* `수정` UI 모드에서 Android 네이티브 컨트롤 (대문자로 시작하는) 이 사전 설정된 컨트롤 속성을 사용할 수 없는 문제
* `수정` runtime.loadDex/loadJar 가 여러 파일을 로드할 때 첫 번째 파일만 유효한 문제 _[`issue #88`](http://issues.autojs6.com/88)_
* `수정` 일부 기기에서 애플리케이션 설치 후 런처에 문서 아이콘만 표시되는 문제 (실험적 수정) _[`issue #85`](http://issues.autojs6.com/85)_
* `개선` VSCode 플러그인 1.0.5 에 맞춤
* `개선` cheerio 모듈 지원 (Refer to [aiselp](https://github.com/aiselp/AutoX/commit/7176f5ad52d6904383024fb700bf19af75e22903)) _[`issue #65`](http://issues.autojs6.com/65)_
* `개선` JsWebSocket 인스턴스가 rebuild 메서드를 사용하여 인스턴스를 다시 생성하고 연결할 수 있도록 지원 _[`issue #69`](http://issues.autojs6.com/69)_
* `개선` base64 모듈이 number 배열 및 Java 바이트 배열을 주요 인수로 인코딩/디코딩할 수 있도록 지원
* `개선` JavaMail for Android 지원 추가 _[`issue #71`](http://issues.autojs6.com/71)_
* `개선` 버전 업데이트 정보를 가져올 때 Blob 데이터 유형을 사용하여 프록시 없는 네트워크 환경 적응성 향상
* `개선` 클라이언트 모드에서 연결 과정 중 홈 화면 드로어 하위 제목에 대상 IP 주소 표시
* `개선` 클라이언트 모드에서 대상 주소 입력 시 잘못된 입력에 대한 경고 메시지 표시
* `개선` 클라이언트 모드에서 소프트키보드 Enter 키를 사용하여 연결 지원
* `개선` 서버 모드가 시작된 후 항상 활성 상태 유지 (수동으로 종료하거나 애플리케이션 프로세스가 종료될 때까지) _[`issue #64`](http://issues.autojs6.com/64#issuecomment-1596990158)_
* `개선` AutoJs6 와 VSCode 플러그인의 양방향 버전 검출 및 비정상 감지 결과 경고 _[`issue #89`](http://issues.autojs6.com/89)_
* `개선` SMS 데이터 읽기 권한 (android.permission.READ_SMS) 추가 (기본 비활성화)
* `개선` findMultiColors 메서드 내부 구현 (by [LYS86](https://github.com/LYS86)) _[`pr #72`](http://pr.autojs6.com/72)_
* `개선` runtime.loadDex/loadJar/load 가 디렉토리 단위로 로드하거나 여러 파일을 동시에 로드할 수 있도록 지원
* `의존성` LeakCanary 버전 2.11 -> 2.12 업그레이드
* `의존성` Android Analytics 버전 14.2.0 -> 14.3.0 업그레이드
* `의존성` Gradle 버전 8.2-milestone-1 -> 8.2 업그레이드

# v6.3.1

###### 2023/05/26

* `추가` 알림 권한 및 홈 화면 드로어 스위치 추가 _[`issue #55`](http://issues.autojs6.com/55)_
* `추가` UI 모드가 간단한 Android 레이아웃 해석 지원 (참조 예제 코드 > 레이아웃 > 간단한 Android 레이아웃)
* `추가` UI 모드에 console / imagebutton / ratingbar / switch / textclock / togglebutton 등의 컨트롤 추가
* `추가` UI 모드의 컨트롤 색상 값이 [OmniColor](https://docs.autojs6.com/#/omniTypes?id=omnicolor) 유형 지원 (예: color="orange")
* `추가` UI 모드의 컨트롤이 attr 메서드를 통해 속성 설정 완전히 지원 (예: ui.text.attr('color', 'blue'))
* `추가` UI 모드의 컨트롤이 부울 속성 값을 기본 형식으로 지원 (예: clickable="true" 는 clickable 또는 isClickable 로 축약 가능)
* `추가` button 컨트롤이 isColored 및 isBorderless 부울 속성 지원
* `추가` console.resetGlobalLogConfig 메서드가 전역 로그 설정을 재설정
* `추가` web.newWebSocket 메서드가 Web Socket 인스턴스를 생성 (참조 프로젝트 문서 > [웹](https://docs.autojs6.com/#/web?id=m-newwebsocket))
* `수정` 파일 관리자에서 폴더 정렬이 비정상인 문제
* `수정` floaty 모듈로 생성된 플로팅 윈도우가 스타일 및 위치 조정을 할 수 없는 문제 _[`issue #60`](http://issues.autojs6.com/60)_
* `수정` floaty 모듈로 생성된 플로팅 윈도우가 시스템 상태바와 겹치는 문제
* `수정` http.postMultipart 메서드가 제대로 작동하지 않는 문제 _[`issue #56`](http://issues.autojs6.com/56)_
* `수정` Android 7.x 에서 스크립트를 실행할 수 없는 문제 _[`issue #61`](http://issues.autojs6.com/61)_
* `수정` sign.property 파일이 없을 때 프로젝트를 빌드할 수 없는 문제
* `수정` 최신 버전의 Android 시스템에서 AutoJs6 가 백그라운드로 전환 시 전면 알림 권한이 없어 충돌하는 문제 (API >= 33)
* `수정` console.show 메서드 호출 후 로그 창에서 FAB 버튼 클릭 시 로그를 지울 수 없는 문제
* `수정` 스크립트 편집기 디버깅 중 prototype NullPointerException 발생 문제
* `수정` 스크립트 편집기에서 스크립트가 원본 위치 대신 캐시 폴더에서 실행되어 내용 손실 가능성 방지
* `수정` 레이아웃 계층 분석 도구의 계층 색상 줄이 너무 많을 경우 컨트롤 이름 표시 문제 _[`issue #46`](http://issues.autojs6.com/46)_
* `개선` 레이아웃 분석 플로팅 윈도우에 종료 버튼 추가 _[`issue #63`](http://issues.autojs6.com/63)_
* `개선` 스크립트 절대 경로가 짧은 형식으로 표시되어 텍스트 길이를 줄이고 가독성 향상
* `개선` Error 를 Exception 으로 교체하여 예외 발생 시 AutoJs6 애플리케이션 충돌 방지
* `개선` View 바인딩 방식을 ButterKnife 에서 View Binding 으로 전환 _[`issue #48`](http://issues.autojs6.com/48)_
* `개선` 서버 모드가 비정상 종료될 경우 AutoJs6 시작 시 자동으로 시작 _[`issue #64`](http://issues.autojs6.com/64)_
* `개선` 클라이언트 모드가 비정상 종료될 경우 AutoJs6 시작 시 마지막으로 연결된 히스토리 주소로 자동 연결
* `의존성` LeakCanary 버전 2.10 -> 2.11 업그레이드
* `의존성` Android Material 버전 1.8.0 -> 1.9.0 업그레이드
* `의존성` AndroidX WebKit 버전 1.6.1 -> 1.7.0 업그레이드
* `의존성` OkHttp3 버전 3.10.0 -> 5.0.0-alpha.9 -> 5.0.0-alpha.11 업그레이드
* `의존성` MLKit Text Recognition Chinese 버전 16.0.0-beta6 -> 16.0.0 업그레이드

# v6.3.0

###### 2023/04/29

* `추가` ocr 모듈 (참조 프로젝트 문서 > [광학 문자 인식](https://docs.autojs6.com/#/ocr)) _[`issue #8`](http://issues.autojs6.com/8)_
* `추가` notice 모듈 (참조 프로젝트 문서 > [메시지 알림](https://docs.autojs6.com/#/notice))
* `추가` s13n 모듈 (참조 프로젝트 문서 > [표준화](https://docs.autojs6.com/#/s13n))
* `추가` Color 모듈 (참조 프로젝트 문서 > [색상 클래스](https://docs.autojs6.com/#/colorType))
* `추가` colors.setColorPaint 메서드, android.graphics.Paint 브러시 색상을 설정하기 위한 기능 (프로젝트 문서 > [Color Type](https://docs.autojs6.com/#/colorType?id=m-setpaintcolor) 참조)
* `추가` 화면을 항상 켜두는 기능 및 설정 옵션
* `추가` 독립적으로 애플리케이션 문서를 읽을 수 있는 추가 런처 (설정에서 숨기기 또는 표시 가능)
* `수정` colors.toString 메서드 기능 오류
* `수정` app.openUrl 메서드 자동 프로토콜 접두사 추가 기능 오류
* `수정` app.viewFile/editFile 의 인수로 지정된 파일이 존재하지 않을 때 발생하는 비정상 동작
* `수정` pickup 메서드의 콜백 함수가 호출되지 않는 문제
* `수정` 레이아웃 분석에 표시되는 컨트롤 정보에 bounds 속성 값에 마이너스 기호가 쉼표로 대체되는 문제
* `수정` bounds/boundsInside/boundsContains 선택자가 좁은 빈 사각형 (예를 들어, 경계가 뒤집힌 사각형) 을 제대로 필터링하지 못하는 문제 _[`issue #49`](http://issues.autojs6.com/49)_
* `수정` 테마를 변경하거나 언어를 변경한 후 홈 화면의 문서 탭을 클릭하거나 길게 클릭하면 애플리케이션이 충돌하는 문제
* `수정` 텍스트 편집기에서 두 손가락으로 글꼴 크기를 조정할 때 발생할 수 있는 흔들림 문제
* `수정` 빌드 스크립트의 일부 종속 요소를 다운로드하지 못하는 문제 (처음부터 통합됨) _[`issue #40`](http://issues.autojs6.com/40)_
* `수정` Tasker 에서 AutoJs6 작업 플러그인 (Action Plugin) 을 추가할 수 없는 문제 (실험적 수정) _[`issue #41`](http://issues.autojs6.com/41)_
* `수정` 높은 버전의 JDK 로 프로젝트를 컴파일할 때 ButterKnife 주석이 리소스 ID 를 해석하지 못하는 문제 _[`issue #48`](http://issues.autojs6.com/48)_
* `수정` 접근성 서비스에서 높은 확률로 서비스 예외가 발생하는 문제 (실험적 수정)
* `수정` images.medianBlur 의 size 파라미터 사용 방식이 문서와 일치하지 않는 문제
* `수정` engines 모듈이 스크립트의 전체 이름을 표시할 때 파일명과 확장자 사이에 점이 없어지는 문제
* `수정` 가중 RGB 거리 계산 알고리즘의 내부 구현에서 발생할 수 있는 계산 오류 (실험적 수정)
* `수정` console 모듈의 플로팅 윈도우 관련 메서드를 show 메서드 이전에 사용할 수 없는 문제
* `수정` console.setSize 등의 메서드가 적용되지 않는 문제 _[`issue #50`](http://issues.autojs6.com/50)_
* `수정` colors.material 색상 공간의 색상 상수 할당 오류
* `수정` UI 모드의 날짜 선택 컨트롤에서 minDate 와 maxDate 속성이 날짜 형식을 제대로 해석하지 못하는 문제
* `수정` 스크립트 실행 후 빠르게 홈의 "작업" 탭 페이지로 전환할 때 동일한 실행 중인 작업이 두 개 나타나는 문제
* `수정` 파일 관리 페이지를 다른 페이지에서 돌아올 때 페이지 상태가 초기화되는 문제 _[`issue #52`](http://issues.autojs6.com/52)_
* `수정` 파일 관리 페이지의 정렬 상태가 아이콘 표시 상태와 일치하지 않는 문제
* `개선` 파일 관리 페이지에 파일 및 폴더의 수정 시간을 표시
* `개선` 파일 관리 페이지에서 정렬 유형 상태를 기억하도록 지원
* `개선` README.md 에 프로젝트 컴파일 빌드 섹션 및 스크립트 개발 지원 섹션 추가 _[`issue #33`](http://issues.autojs6.com/33)_
* `개선` images 모듈 관련 메서드의 영역 (region) 옵션 파라미터가 다양한 입력 방식을 지원 (참조 프로젝트 문서 > [올인원 유형](https://docs.autojs6.com/#/omniTypes?id=omniregion))
* `개선` app.startActivity 의 페이지 약어 파라미터에 pref/homepage/docs/about 등의 형식 지원 추가
* `개선` web 모듈의 전역 메서드를 모듈 자체에 매핑하여 사용성 향상 (참조 프로젝트 문서 > [월드 와이드 웹](https://docs.autojs6.com/#/web))
* `개선` web.newInjectableWebView 메서드의 내부 기본 구현에 자주 사용하는 WebView 설정 옵션 일부 추가
* `개선` colors 모듈에 다양한 변환 메서드 및 유틸리티 메서드를 추가하고, 더 많은 정적 상수 및 인수로 사용할 수 있는 색상 이름 추가
* `개선` console 모듈에 여러 가지 콘솔 플로팅 윈도우 스타일 설정 방법 추가 및 build 생성기를 사용하여 창 스타일 통일 구성 지원
* `개선` 콘솔 플로팅 윈도우에서 제목 영역을 드래그하여 윈도우 위치 이동 지원
* `개선` 콘솔 플로팅 윈도우가 스크립트 종료 후 자동 지연 종료 지원
* `개선` 콘솔 플로팅 윈도우 및 해당 Activity 윈도우가 두 손가락으로 글꼴 크기 조정 지원
* `개선` http 모듈 관련 메서드는 타임아웃 옵션 매개변수 (timeout) 를 지원
* `개선` Gradle 빌드 스크립트가 JDK 버전 강제 다운그레이드를 지원 (fallback)
* `개선` Gradle 빌드 스크립트가 플랫폼 유형 및 버전에 따라 자동으로 적합한 빌드 도구 버전을 선택 (한정된 범위 내에서)
* `의존성` 로컬화된 Auto.js APK Builder 버전 1.0.3
* `의존성` 로컬화된 MultiLevelListView 버전 1.1
* `의존성` 로컬화된 Settings Compat 버전 1.1.5
* `의존성` 로컬화된 Enhanced Floaty 버전 0.31
* `의존성` MLKit Text Recognition Chinese 버전 16.0.0-beta6 부가 추가
* `의존성` Gradle 버전 8.0-rc-1 -> 8.2-milestone-1 업그레이드
* `의존성` Android Material 버전 1.7.0 -> 1.8.0 업그레이드
* `의존성` Glide 버전 4.14.2 -> 4.15.1 업그레이드
* `의존성` Joda Time 버전 2.12.2 -> 2.12.5 업그레이드
* `의존성` Android Analytics 버전 14.0.0 -> 14.2.0 업그레이드
* `의존성` AndroidX WebKit 버전 1.5.0 -> 1.6.1 업그레이드
* `의존성` AndroidX Recyclerview 버전 1.2.1 -> 1.3.0 업그레이드
* `의존성` Zip4j 버전 2.11.2 -> 2.11.5 업그레이드
* `의존성` Junit Jupiter 버전 5.9.2 -> 5.9.3 업그레이드
* `의존성` AndroidX Annotation 버전 1.5.0 -> 1.6.0 업그레이드
* `의존성` Jackson DataBind 버전 2.14.1 -> 2.14.2 업그레이드
* `의존성` Desugar JDK Libs 버전 2.0.0 -> 2.0.3 업그레이드

# v6.2.0

###### 2023/01/21

* `추가` 프로젝트 문서의 재설계 및 작성 (일부 완료)
* `추가` 서/프/러/아/일/한/영/번체 등 여러 언어 적응
* `추가` 작업 경로 설정 옵션에 경로 선택/기록/기본값 스마트 제안 기능 추가
* `추가` 파일 관리자가 임의 디렉토리의 상위 디렉토리로 이동 지원 ("내부 저장소" 디렉토리까지)
* `추가` 파일 관리자가 임의 디렉토리를 작업 경로로 빠르게 설정 지원
* `추가` 버전 업데이트 무시 및 무시한 업데이트 관리 기능
* `추가` 텍스트 편집기가 두 손가락으로 글꼴 크기를 조절 지원
* `추가` idHex 선택자 (UiSelector#idHex) (프로젝트 문서 참조 > [선택자](https://docs.autojs6.com/#/uiSelectorType))
* `추가` action 선택자 (UiSelector#action) (프로젝트 문서 참조 > [선택자](https://docs.autojs6.com/#/uiSelectorType))
* `추가` Match 시리즈 선택자 (UiSelector#xxxMatch) (프로젝트 문서 참조 > [선택자](https://docs.autojs6.com/#/uiSelectorType))
* `추가` 픽업 선택자 (UiSelector#pickup) (프로젝트 문서 참조 > [선택자](https://docs.autojs6.com/#/uiSelectorType)) _[`issue #22`](http://issues.autojs6.com/22)_
* `추가` 컨트롤 탐지 (UiObject#detect) (프로젝트 문서 참조 > [컨트롤 노드](https://docs.autojs6.com/#/uiObjectType))
* `추가` 컨트롤 나침반 (UiObject#compass) (프로젝트 문서 참조 > [컨트롤 노드](https://docs.autojs6.com/#/uiObjectType)) _[`issue #23`](http://issues.autojs6.com/23)_
* `추가` 글로벌 대기 메서드 wait (프로젝트 문서 참조 > [전역 객체](https://docs.autojs6.com/#/global?id=m-wait))
* `추가` 글로벌 스케일 메서드 cX/cY/cYx (프로젝트 문서 참조 > [전역 객체](https://docs.autojs6.com/#/global?id=m-wait))
* `추가` 글로벌 앱 유형 (프로젝트 문서 참조 > [응용 프로그램 열거형 클래스](https://docs.autojs6.com/#/appType))
* `추가` i18n 모듈 (banana-i18n 기반의 JavaScript 다국어 솔루션) (프로젝트 문서 참조 > 국제화)
* `수정` 소프트웨어 언어를 전환 후 발생할 수 있는 페이지 텍스트 깜박임 및 일부 페이지 버튼 기능 이상
* `수정` 작업 경로가 프로젝트일 때 소프트웨어 시작 후 프로젝트 도구 모음이 표시되지 않는 문제
* `수정` 작업 경로가 소프트웨어 언어 전환에 따라 자동 변경될 수 있는 문제 _[`issue #19`](http://issues.autojs6.com/19)_
* `수정` 예약 작업 시작 지연 현저 (테스트 수정) _[`issue #21`](http://issues.autojs6.com/21)_
* `수정` JavaScript 모듈명이 덮어쓰여지면서 존재하는 의존성을 가진 내부 모듈이 제대로 작동하지 않는 문제 _[`issue #29`](http://issues.autojs6.com/29)_
* `수정` 고버전 안드로이드 시스템에서 빠른 설정 패널의 관련 아이콘을 클릭한 후 패널이 자동으로 숨겨지지 않는 문제 (테스트 수정) _[`issue #7`](http://issues.autojs6.com/7)_
* `수정` 고버전 안드로이드 시스템에서 일부 페이지와 알림창 영역이 겹치지 않는 문제
* `수정` 안드로이드 10 이상 시스템에서 붓 색상을 설정하는 예제 코드가 정상적으로 작동하지 않는 문제
* `수정` 예제 코드인 "음악 관리"의 파일명을 "파일 관리"로 수정하고 정상 기능 복구
* `수정` 파일 관리자가 새로 고침 시 위치 이동 문제가 발생할 수 있는 문제
* `수정` ui 모듈 스코프 바인딩 오류로 인해 일부 UI 기반 스크립트가 컴포넌트 속성에 접근하지 못하는 문제
* `수정` 스크립트 녹화 후 입력 파일명 대화 상자가 외부 영역 클릭으로 인해 녹화 내용을 분실할 수 있는 문제
* `수정` 문서의 일부 챕터 제목이 화면 너비를 초과할 때 자동 줄 바꿈이 되지 않아 내용이 분실되는 문제
* `수정` 문서의 예제 코드 영역이 정상적으로 좌우로 슬라이드되지 않는 문제
* `수정` 문서 페이지를 새로 고침 시 예외적인 행동 및 새로 고침 취소 조작이 불가능한 문제 (테스트 수정)
* `수정` 애플리케이션 초기 설치 후 홈 화면의 드로어 나이트 모드 전환 관련 문제 해결
* `수정` 시스템 나이트 모드가 활성화될 때 애플리케이션 시작 후 강제 나이트 모드 적용 문제
* `수정` 나이트 모드 활성화 후 설정된 테마 색상이 적용되지 않는 문제
* `수정` 나이트 모드에서 일부 설정 옵션 텍스트와 배경색이 동일하여 읽기가 어려운 문제
* `수정` 정보 페이지의 기능 버튼 텍스트 길이가 너무 길어 텍스트가 완전히 표시되지 않는 문제
* `수정` 홈 화면 드로어 설정 항목 텍스트 길이가 길어 텍스트와 버튼이 겹치는 문제
* `수정` 홈 화면 드로어 권한 전환이 팝업 대화창 사라지 후 동기화되지 않는 문제가 발생할 수 있음
* `수정` Root 권한이 홈 화면 드로어 권한 전환 실패 시 ADB 도구 대화창이 계속 나타나지 않는 문제
* `수정` Root 권한이 처음 사용할 때 포인터 위치 표시가 권한이 없다고 나타나는 문제
* `수정` 아이콘 선택 페이지의 아이콘 요소가 제대로 배치되지 않는 문제
* `수정` 텍스트 편집기가 시작 중 나이트 모드 설정에 따라 깜박임이 발생할 수 있는 문제 (테스트 수정)
* `수정` 텍스트 편집기 글꼴 크기 설정 시 최대값이 제한되는 문제
* `수정` 일부 안드로이드 시스템에서 스크립트 실행 후 로그에서 실행 지속 시간 통계가 표시되지 않는 문제
* `수정` 플로팅 버튼 메뉴를 닫은 후 애플리케이션을 재시작할 때 플로팅 버튼이 여전히 표시되는 문제
* `수정` 레이아웃 계층 분석 중 리스트 항목을 길게 눌렀을 때 팝업 메뉴가 아래 화면에 넘치는 문제
* `수정` 안드로이드 7.x 시스템에서 나이트 모드 비활성화 시 내비게이션 버튼이 알아보기가 어려운 문제
* `수정` http.post 등의 메서드 요청이 닫히지 않는 예외
* `수정` colors.toString 메서드에서 Alpha 채널이 0 일 때 해당 채널 정보가 결과에서 손실되는 문제
* `개선` 자동 하위 호환성을 가능하게 하기 위해 Auto.js 4.x 버전의 공용 클래스 방향 재설정 (제한된 범위 내)
* `개선` 모든 프로젝트 모듈 병합하여 순환 참조 등 문제 회피 (임시 inrt 모듈 제거)
* `개선` Gradle 빌드 설정을 Groovy 에서 KTS 로 마이그레이션
* `개선` Rhino 예외 메시지에 다국어 지원 추가
* `개선` 홈 화면 드로어 권한 전환 오류 메시지 팝업
* `개선` 홈 화면 드로어 레이아웃이 상태바 하단에 맞춰 조정하여 상단 색상 막대와의 낮은 호환성 해결
* `개선` 업데이트/업데이트 다운로드/업데이트 알림 기능이 Android 7.x 시스템과 호환
* `개선` 설정 페이지의 재설계 (AndroidX 로 마이그레이션)
* `개선` 설정 페이지에서 설정 옵션을 길게 눌러 자세한 정보 확인 지원
* `개선` 나이트 모드: "시스템 따라가기" 옵션 추가 (Android 9 이상)
* `개선` 애플리케이션 시작 화면 나이트 모드에 맞게 조정
* `개선` 앱 아이콘에 숫자 식별자를 추가하여 다중 오픈소스 버전 공존 사용자 경험 개선
* `개선` 테마 색상에 더 많은 Material Design Color (재료 설계 색상) 옵션 추가
* `개선` 파일 관리자/작업 패널 등 리스트 항목 아이콘의 적정 경량화 및 테마 색 지원
* `개선` 홈화면 검색 창의 텍스트 힌트 텍스트의 나이트 모드 지원
* `개선` 다이얼로그/텍스트/Fab/AppBar/리스트 항목 등의 나이트 모드 지원
* `개선` 문서/설정/정보/테마 색상/레이아웃 분석 등의 페이지 및 플로팅 버튼 메뉴의 나이트 모드 지원
* `개선` 페이지 레이아웃이 RTL (오른쪽에서 왼쪽) 레이아웃과 최대한 호환되도록 조정
* `개선` 정보 페이지에 아이콘 애니메이션 효과 추가
* `개선` 정보 페이지 저작권 선언 텍스트의 자동 연도 업데이트
* `개선` 초기 설치 후 적합한 작업 디렉토리를 자동으로 결정하고 설정
* `개선` 문서 페이지의 두 손가락 확대 기능 사용 제한하여 문서 내용 표시 오류 방지
* `개선` 작업 패널 리스트 항목이 상대 경로로 작업 이름 및 경로 표시를 간략화
* `개선` 텍스트 편집기 버튼 텍스트 적절하게 줄여 텍스트가 넘치지 않도록 지원
* `개선` 텍스트 편집기 글꼴 크기 설정에 기본값 복구 지원
* `개선` 플로팅 버튼 클릭 응답 속도 개선
* `개선` 플로팅 버튼 레이아웃 분석 버튼 클릭 시 직접 범위 분석 수행
* `개선` 레이아웃 분석 테마 자동 적용 (플로팅 창은 앱 테마를 따르고, 빠른 설정 패널은 시스템 테마를 따름)
* `개선` 레이아웃 컨트롤 정보 리스트를 사용 빈도에 따라 재배열
* `개선` 레이아웃 컨트롤 정보 클릭 시 선택자 유형에 따라 자동으로 출력 형식 최적화
* `개선` 플로팅 창을 통해 파일 선택 시 뒤로 가기 키를 누르면 상위 디렉토리로 이동 지원 (창 닫지 않고)
* `개선` 클라이언트 모드에서 컴퓨터 주소 입력 시 숫자 유효성 검사 및 점 구분 기호 자동 변환 지원
* `개선` 클라이언트 및 서버가 연결되면 홈 화면 드로어에 해당 기기의 IP 주소 표시
* `개선` 일부 전역 객체 및 내장 모듈에 덮어쓰기 방지 추가 (프로젝트 문서 참조 > 전역 객체 > [덮어쓰기 방지](https://docs.autojs6.com/#/global?id=%e8%a6%86%e5%86%99%e4%bf%9d%e6%8a%a4))
* `개선` importClass 및 importPackage 문자열 매개변수 지원 및 가변 매개변수 지원
* `개선` ui.run 오류 시 예외 스택 추적 정보 출력 지원
* `개선` ui.R 및 auto.R 로 AutoJs6 의 리소스 ID 를 쉽게 얻을 수 있음
* `개선` app 모듈의 응용 관련 메서드가 App 유형 매개변수 및 응용 별명 매개변수 지원
* `개선` dialogs 모듈의 비동기 콜백 관련 메서드가 사전 채워진 매개변수 생략 지원
* `개선` app.startActivity 등이 url 옵션 매개변수 지원 (예제 코드 참조 > 응용 프로그램 > 인텐트)
* `개선` device 모듈이 IMEI 또는 하드웨어 시리얼 번호를 얻지 못했을 때 null 을 반환 (예외 발생 X)
* `개선` console.show 로그 플로팅 창 텍스트 밝기 개선하여 내용 식별성 향상
* `개선` ImageWrapper#saveTo 상대 경로로 이미지 파일 저장 지원
* `개선` colors 전역 객체 재설계 및 HSV / HSL 등 색상 모드 지원 추가 (프로젝트 문서 참조 > [컬러](https://docs.autojs6.com/#/color))
* `의존성` 로컬화된 Android Job 버전 1.4.3
* `의존성` 로컬화된 Android Plugin Client SDK For Locale 버전 9.0.0
* `의존성` 로컬화된 GitHub API 버전 1.306
* `의존성` Android SDK 버전 32 -> 33 업그레이드
* `의존성` JCIP Annotations 부가 추가 버전 1.0
* `의존성` AndroidX WebKit 부가 추가 버전 1.5.0
* `의존성` Commons IO 부가 추가 버전 2.8.0
* `의존성` Desugar JDK Libs 부가 추가 버전 2.0.0
* `의존성` Jackson DataBind 부가 추가 버전 2.13.3
* `의존성` Jaredrummler Android Device Names 부가 추가 버전 2.1.0
* `의존성` Jaredrummler Animated SVG View 부가 추가 버전 1.0.6
* `의존성` Jrummyapps ColorPicker 버전 2.1.7 을 Jaredrummler ColorPicker 버전 1.1.0 으로 교체
* `의존성` Gradle 버전 7.5-rc-1 -> 8.0-rc-1 업그레이드
* `의존성` Gradle 빌드 도구 버전 7.4.0-alpha02 -> 8.0.0-alpha09 업그레이드
* `의존성` Kotlin Gradle 플러그인 버전 1.6.10 -> 1.8.0-RC2 업그레이드
* `의존성` Android Material 버전 1.6.0 -> 1.7.0 업그레이드
* `의존성` AndroidX Annotation 버전 1.3.0 -> 1.5.0 업그레이드
* `의존성` AndroidX AppCompat 버전 1.4.1 -> 1.4.2 업그레이드
* `의존성` Android Analytics 버전 13.3.0 -> 14.0.0 업그레이드
* `의존성` Gson 버전 2.9.0 -> 2.10 업그레이드
* `의존성` Joda Time 버전 2.10.14 -> 2.12.1 업그레이드
* `의존성` Kotlinx Coroutines 버전 1.6.1-native-mt -> 1.6.1 업그레이드
* `의존성` OkHttp3 버전 3.10.0 -> 5.0.0-alpha.7 -> 5.0.0-alpha.9 업그레이드
* `의존성` Zip4j 버전 2.10.0 -> 2.11.2 업그레이드
* `의존성` Glide 버전 4.13.2 -> 4.14.2 업그레이드
* `의존성` Junit Jupiter 버전 5.9.0 -> 5.9.1 업그레이드

# v6.1.1

###### 2022/05/31

* `추가` 업데이트 확인/업데이트 다운로드/업데이트 알림 기능 (설정 페이지 참조) (현재 Android 7.x 시스템 미지원)
* `수정` 애플리케이션이 Android 10 시스템에서 외부 저장소를 읽고 쓸 수 없는 문제 _[`issue #17`](http://issues.autojs6.com/17)_
* `수정` 편집기 페이지 길게 누르기 시 애플리케이션이 충돌할 수 있는 문제 _[`issue #18`](http://issues.autojs6.com/18)_
* `수정` 편집기 페이지 길게 누르기 메뉴에서 "행 삭제" 및 "행 복사" 기능이 무효한 문제
* `수정` 편집기 페이지 옵션 메뉴에서 "붙여넣기" 기능이 누락된 문제
* `개선` 일부 예외 메시지 문자열의 리소스화 (en / zh)
* `개선` 저장되지 않은 내용 대화 상자의 버튼 배치 조정 및 색상 차별화 추가
* `의존성` github-api 버전 1.306 추가
* `의존성` retrofit2-rxjava2-adapter 버전 1.0.0 을 adapter-rxjava2 버전 2.9.0 으로 교체

# v6.1.0

###### 2022/05/26 - 패키지명 변경, 주의하여 업그레이드

* `힌트` 앱 패키지명을 org.autojs.autojs6 으로 변경하여 오픈소스 Auto.js 앱 패키지명과의 충돌을 피합니다.
* `추가` 홈 드로어에 "투영 미디어 권한" 스위치 추가 (Root / ADB 방식) (스위치 상태 감지는 실험적입니다)
* `추가` 파일 브라우저에서 숨김 파일 및 폴더 표시 지원 (설정 페이지 참조)
* `추가` 강제 Root 검사 기능 (설정 페이지 및 샘플 코드 참조)
* `추가` autojs 모듈 (샘플 코드 > AutoJs6 참조)
* `추가` tasks 모듈 (샘플 코드 > 작업 참조)
* `추가` console.launch() 메서드 로그 활동 페이지 시작
* `추가` util.morseCode 도구 (샘플 코드 > 도구 > 모스 부호 참조)
* `추가` util.versionCodes 도구 (샘플 코드 > 도구 > 안드로이드 버전 정보 검색 참조)
* `추가` util.getClass() 등 메서드 (샘플 코드 > 도구 > 클래스 및 클래스명 가져오기 참조)
* `추가` timers.setIntervalExt() 메서드 (샘플 코드 > 타이머 > 조건부 주기 실행 참조)
* `추가` colors.toInt() / rgba() 등 메서드 (샘플 코드 > 이미지 및 색상 > 기본 색상 변환 참조)
* `추가` automator.isServiceRunning() / ensureService() 메서드
* `추가` automator.lockScreen() 등 메서드 (샘플 코드 > 접근성 서비스 > Android 9 추가 참조)
* `추가` automator.headsethook() 등 메서드 (샘플 코드 > 접근성 서비스 > Android 11 추가 참조)
* `추가` automator.captureScreen() 메서드 (샘플 코드 > 접근성 서비스 > 스크린샷 가져오기 참조)
* `추가` dialogs.build 메서드는 animation, linkify 등의 옵션 매개변수를 지원합니다 (샘플 코드 > 대화상자 > 맞춤형 대화상자 참조)
* `수정` dialogs.build 의 inputHint, itemsSelectedIndex 등의 옵션 매개변수가 비정상적으로 동작합니다
* `수정` JsDialog#on('multi_choice') 콜백 매개변수 기능 오류
* `수정` UiObject#parent().indexInParent() 항상 -1 을 반환하는 문제 _[`issue #16`](http://issues.autojs6.com/16)_
* `수정` Promise.resolve() 가 반환하는 Thenable 이 스크립트 종료 시 호출되지 않을 수 있는 문제
* `수정` 패키지명 또는 클래스명에서 가능성 있는 오타 (boardcast -> broadcast / auojs -> autojs)
* `수정` images.requestScreenCapture() 에서 높은 버전의 안드로이드 시스템에서 앱이 충돌할 수 있는 문제 (API >= 31)
* `수정` images.requestScreenCapture() 여러 스크립트 인스턴스가 동시에 요청할 때 앱이 충돌할 수 있는 문제
* `수정` new RootAutomator() 호출 시 발생할 수 있는 멈춤 문제
* `개선` RootAutomator 는 Root 권한이 없을 때 인스턴스화되지 않습니다
* `개선` "앱 및 개발자 정보" 페이지 재설계
* `개선` 모든 내장 JavaScript 모듈 재구성
* `개선` 모든 Gradle 빌드 스크립트 재구성 및 공통 구성 스크립트 추가 (config.gradle)
* `개선` Gradle 빌드 도구 버전 관리 및 빌드 파일 자동 명명 지원
* `개선` Gradle 빌드 도구에 task 를 추가하여 빌드 파일에 CRC32 요약 부착 지원 (appendDigestToReleasedFiles)
* `개선` shell() 호출 시 예외를 반환 결과에 씁니다. (try/catch 불필요)
* `개선` Rhino 내장 JSON 을 사용하여 원래 json2 모듈 대체
* `개선` auto.waitFor() 타임아웃 매개변수 지원
* `개선` threads.start() 화살표 함수 매개변수 지원
* `개선` console.trace() 로그 레벨 매개변수 지원 (샘플 코드 > 콘솔 > 호출 스택 출력 참조)
* `개선` device.vibrate() 모드 진동 및 모스 부호 진동 지원 (샘플 코드 > 장치 > 모드 진동 / 모스 부호 진동 참조)
* `개선` 외부 저장소 읽기/쓰기 권한을 높은 버전의 안드로이드 시스템에 적응 (API >= 30)
* `개선` 콘솔 글꼴은 Material Color 를 사용하여 일반 및 야간 테마에서 글꼴 가독성 향상
* `개선` 모든 ImageWrapper 인스턴스를 약한 참조로 저장하고 스크립트 종료 시 자동으로 회수 (실험적)
* `의존성` CircleImageView 버전 3.1.0 추가
* `의존성` Android Analytics 버전 13.1.0 -> 13.3.0 업그레이드
* `의존성` Gradle 빌드 도구 버전 7.3.0-alpha06 -> 7.4.0-alpha02 업그레이드
* `의존성` Android Job 버전 1.4.2 -> 1.4.3 업그레이드
* `의존성` Android Material 버전 1.5.0 -> 1.6.0 업그레이드
* `의존성` CrashReport 버전 2.6.6 -> 4.0.4 업그레이드
* `의존성` Glide 버전 4.13.1 -> 4.13.2 업그레이드
* `의존성` Joda Time 버전 2.10.13 -> 2.10.14 업그레이드
* `의존성` Kotlin Gradle 플러그인 버전 1.6.10 -> 1.6.21 업그레이드
* `의존성` Kotlinx Coroutines 버전 1.6.0 -> 1.6.1-native-mt 업그레이드
* `의존성` LeakCanary 버전 2.8.1 -> 2.9.1 업그레이드
* `의존성` OkHttp3 버전 5.0.0-alpha.6 -> 5.0.0-alpha.7 업그레이드
* `의존성` Rhino 엔진 버전 1.7.14 -> 1.7.15-SNAPSHOT 업그레이드
* `의존성` Zip4j 버전 2.9.1 -> 2.10.0 업그레이드
* `의존성` Groovy JSON 버전 3.0.8 제거
* `의존성` Kotlin Stdlib JDK7 버전 1.6.21 제거

# v6.0.3

###### 2022/03/19

* `추가` 다국어 전환 기능 (아직 완벽하지 않음)
* `추가` recorder 모듈 (샘플 코드 > 타이머 참조)
* `추가` "보안 설정 수정 권한"을 사용하여 자동으로 접근성 서비스 및 스위치 설정
* `수정` 빠른 설정 패널에서 관련 아이콘을 클릭하고 나서 패널이 자동으로 닫히지 않는 문제 (임시 수정) _[`issue #7`](http://issues.autojs6.com/7)_
* `수정` toast 강제 표시 매개변수 사용 시 AutoJs6 가 충돌할 수 있는 문제
* `수정` Socket 데이터 전송 시 헤더 정보가 불완전할 때 AutoJs6 가 충돌할 수 있는 문제
* `개선` AutoJs6 시작 또는 재시작 시 옵션 설정에 따라 자동으로 접근성 서비스 활성화
* `개선` 플로팅 버튼 스위치를 활성화하면 접근성 서비스 자동 활성화 시도
* `개선` 모든 리소스 파일에 해당하는 영문 번역 추가
* `개선` 홈 드로어 레이아웃 약간 조정, 항목 간격 축소
* `개선` 홈 드로어에 전경 서비스 상태 스위치 동기화 추가
* `개선` 홈 드로어 확장 시 필요에 따라 스위치 상태를 즉시 동기화
* `개선` 포인터 위치 표시 상태 감지 및 결과 표시 추가
* `개선` 64 비트 운영 체제 지원 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `개선` 플로팅 버튼 초기화 시 투명도 설정을 즉시 적용 (클릭 후에 적용 필요 없음)
* `개선` 파일 내용 재설정 시 샘플 코드 파일인지 여부 감지 및 결과 알림 추가
* `개선` 번들 플러그인 다운로드 주소 GitHub -> JsDelivr 로 이전
* `의존성` Zeugma Solutions LocaleHelper 버전 1.5.1 추가
* `의존성` Android Material 버전 1.6.0-alpha02 -> 1.5.0 다운그레이드
* `의존성` Android SDK 버전 31 -> 32 업그레이드
* `의존성` Kotlinx Coroutines 버전 1.6.0-native-mt -> 1.6.0 업그레이드
* `의존성` OpenCV 버전 3.4.3 -> 4.5.4 -> 4.5.5 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) 업그레이드
* `의존성` OkHttp3 버전 3.10.0 -> 5.0.0-alpha.4 -> 5.0.0-alpha.6 업그레이드
* `의존성` Gradle 빌드 도구 버전 7.2.0-beta01 -> 7.3.0-alpha06 업그레이드
* `의존성` Auto.js-ApkBuilder 버전 1.0.1 -> 1.0.3 업그레이드
* `의존성` Glide Compiler 버전 4.12.0 -> 4.13.1 업그레이드
* `의존성` Gradle 릴리스 버전 7.4-rc-2 -> 7.4.1 업그레이드
* `의존성` Gson 버전 2.8.9 -> 2.9.0 업그레이드

# v6.0.2

###### 2022/02/05

* `추가` images.bilateralFilter() 양방향 필터링 이미지 처리 방법
* `수정` 여러 번 toast 호출 시 마지막 호출만 적용되는 문제
* `수정` toast.dismiss() 가 무효화될 수 있는 문제
* `수정` 클라이언트 모드 및 서버 모드 스위치가 정상적으로 작동하지 않을 수 있는 문제
* `수정` 클라이언트 모드 및 서버 모드 스위치 상태가 정상적으로 새로 고침되지 않는 문제
* `수정` Android 7.x 에서 UI 모드 텍스트 요소 분석 오류 (Ref to [TonyJiangWJ](https://github.com/TonyJiangWJ)) _[`issue #4`](http://issues.autojs6.com/4)_ _[`issue #9`](http://issues.autojs6.com/9)_
* `개선` sleep() 의 ScriptInterruptedException 예외를 무시
* `의존성` AndroidX AppCompat (Legacy) 버전 1.0.2 추가
* `의존성` AndroidX AppCompat 버전 1.4.0 -> 1.4.1 업그레이드
* `의존성` AndroidX Preference 버전 1.1.1 -> 1.2.0 업그레이드
* `의존성` Rhino 엔진 버전 1.7.14-SNAPSHOT -> 1.7.14 업그레이드
* `의존성` OkHttp3 버전 3.10.0 -> 5.0.0-alpha.3 -> 5.0.0-alpha.4 업그레이드
* `의존성` Android Material 버전 1.6.0-alpha01 -> 1.6.0-alpha02 업그레이드
* `의존성` Gradle 빌드 도구 버전 7.2.0-alpha06 -> 7.2.0-beta01 업그레이드
* `의존성` Gradle 릴리스 버전 7.3.3 -> 7.4-rc-2 업그레이드

# v6.0.1

###### 2022/01/01

* `추가` VSCode 플러그인 지원 클라이언트 (LAN) 및 서버 (LAN/ADB) 방식 연결 지원 (참조 [Auto.js Pro](https://g.pro.autojs.org/))
* `추가` base64 모듈 (참조 [Auto.js Pro](https://g.pro.autojs.org/))
* `추가` isInteger/isNullish/isObject/isPrimitive/isReference 글로벌 메소드 추가
* `추가` polyfill (Object.getOwnPropertyDescriptors) 추가
* `추가` polyfill (Array.prototype.flat) 추가
* `개선` global.sleep 확장 지원 랜덤 범위/음수 호환
* `개선` global.toast 확장 지원 시간 제어/강제 덮어쓰기 제어/해제
* `개선` 패키지명 객체 전역화 (okhttp3/androidx/de)
* `의존성` Android Material 버전 업그레이드 1.5.0-beta01 -> 1.6.0-alpha01
* `의존성` Gradle 빌드 도구 버전 업그레이드 7.2.0-alpha04 -> 7.2.0-alpha06
* `의존성` Kotlinx Coroutines 버전 업그레이드 1.5.2-native-mt -> 1.6.0-native-mt
* `의존성` Kotlin Gradle 플러그인 버전 업그레이드 1.6.0 -> 1.6.10
* `의존성` Gradle 릴리즈 버전 업그레이드 7.3 -> 7.3.3

# v6.0.0

###### 2021/12/01

* `추가` 홈 드로어 하단에 앱 재시작 버튼 추가
* `추가` 홈 드로어에 배터리 최적화 무시/다른 앱 위에 표시 스위치 추가
* `수정` 앱 초기 설치 후 일부 영역의 테마 색상 렌더링 이상 문제
* `수정` sign.property 파일이 없을 때 프로젝트를 빌드할 수 없는 문제
* `수정` 일회성 작업의 달 저장 및 불러오기 오류
* `수정` 앱 설정 페이지의 토글 색상이 테마 변경에 따르지 않는 문제
* `수정` 패키지 플러그인 및 패키지 플러그인 다운로드 주소 인식 불가 문제
* `수정` 홈 드로어 "사용 상황 권한 확인" 스위치 상태가 동기화되지 않을 수 있는 문제
* `수정` TemplateMatching.fastTemplateMatching 의 잠재적인 Mat 메모리 누수 문제
* `개선` Rhino 엔진 버전 업그레이드 1.7.7.2 -> 1.7.13 -> 1.7.14-SNAPSHOT
* `개선` OpenCV 버전 업그레이드 3.4.3 -> 4.5.4
* `개선` ViewUtil.getStatusBarHeight 호환성 향상
* `개선` 홈 드로어 사용자 로그인 관련 모듈 제거 및 레이아웃 자리 비우기
* `개선` 홈에서 커뮤니티 및 마켓 탭을 제거하고 레이아웃 정렬 개선
* `개선` 일부 설정 옵션의 기본 토글 상태 변경
* `개선` 정보 페이지에 SinceDate 추가 및 저작권 표기 개선
* `개선` JSON 모듈을 2017-06-12 버전으로 업그레이드하고 cycle.js 통합
* `개선` 앱 포그라운드시 자동 업데이트 확인 기능 제거 및 업데이트 확인 관련 버튼 삭제
* `개선` AppOpsKt#isOpPermissionGranted 내부 코드 로직 개선
* `개선` ResourceMonitor 에서 ReentrantLock 사용 안전성 개선 (참조 [TonyJiangWJ](https://github.com/TonyJiangWJ))
* `개선` Maven Central 등의 저장소를 사용하여 JCenter 저장소 대체
* `개선` 중복된 로컬 라이브러리 파일을 분리 및 제거
* `의존성` Android SDK 버전 업그레이드 28 -> 30 -> 31
* `의존성` CrashReport 버전 로컬라이제이션 2.6.6
* `의존성` MutableTheme 버전 로컬라이제이션 1.0.0
* `의존성` AndroidX Preference 버전 추가 1.1.1
* `의존성` SwipeRefreshLayout 버전 추가 1.1.0
* `의존성` Android Analytics 버전 업그레이드 7.0.0 -> 13.1.0
* `의존성` Android Annotations 버전 업그레이드 4.5.2 -> 4.8.0
* `의존성` Gradle 빌드 도구 버전 업그레이드 3.2.1 -> 4.1.0 -> 7.0.3 -> 7.2.0-alpha04
* `의존성` Android Job 버전 업그레이드 1.2.6 -> 1.4.2
* `의존성` Android Material 버전 업그레이드 1.1.0-alpha01 -> 1.5.0-beta01
* `의존성` AndroidX MultiDex 버전 업그레이드 2.0.0 -> 2.0.1
* `의존성` Apache Commons Lang3 버전 업그레이드 3.6 -> 3.12.0
* `의존성` Appcompat 버전 업그레이드 1.0.2 -> 1.4.0
* `의존성` ButterKnife Gradle 플러그인 버전 업그레이드 9.0.0-rc2 -> 10.2.1 -> 10.2.3
* `의존성` ColorPicker 버전 업그레이드 2.1.5 -> 2.1.7
* `의존성` Espresso Core 버전 업그레이드 3.1.1-alpha01 -> 3.5.0-alpha03
* `의존성` Eventbus 버전 업그레이드 3.0.0 -> 3.2.0
* `의존성` Glide Compiler 버전 업그레이드 4.8.0 -> 4.12.0
* `의존성` Gradle Build Tool 버전 업그레이드 29.0.2 -> 30.0.2
* `의존성` Gradle 릴리즈 버전 업그레이드 4.10.2 -> 6.5 -> 7.0.2 -> 7.3
* `의존성` Groovy-Json 플러그인 버전 업그레이드 3.0.7 -> 3.0.8
* `의존성` Gson 버전 업그레이드 2.8.2 -> 2.8.9
* `의존성` Java 버전 업그레이드 8 -> 11 -> 16
* `의존성` Joda Time 버전 업그레이드 2.9.9 -> 2.10.13
* `의존성` Junit 버전 업그레이드 4.12 -> 4.13.2
* `의존성` Kotlin Gradle 플러그인 버전 업그레이드 1.3.10 -> 1.4.10 -> 1.6.0
* `의존성` Kotlinx Coroutines 버전 업그레이드 1.0.1 -> 1.5.2-native-mt
* `의존성` LeakCanary 버전 업그레이드 1.6.1 -> 2.7
* `의존성` LicensesDialog 버전 업그레이드 1.8.1 -> 2.2.0
* `의존성` Material Dialogs 버전 업그레이드 0.9.2.3 -> 0.9.6.0
* `의존성` OkHttp3 버전 업그레이드 3.10.0 -> 5.0.0-alpha.2 -> 5.0.0-alpha.3
* `의존성` Reactivex RxJava2 RxAndroid 버전 업그레이드 2.0.1 -> 2.1.1
* `의존성` Reactivex RxJava2 버전 업그레이드 2.1.2 -> 2.2.21
* `의존성` Retrofit2 Converter Gson 버전 업그레이드 2.3.0 -> 2.9.0
* `의존성` Retrofit2 Retrofit 버전 업그레이드 2.3.0 -> 2.9.0
* `의존성` Zip4j 버전 업그레이드 1.3.2 -> 2.9.1