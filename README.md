# Pleos Concierge

차 상태에 맞춰 화면이 바뀌는 Pleos Connect(Android Automotive OS)용 앱입니다.

- **차를 세워 뒀을 때**: 온도·바람·시트 같은 캐빈 설정을 보여 줍니다.
- **달릴 때**: 조작 카드는 숨기고 속도·배터리처럼 꼭 필요한 정보만 남깁니다.
- **도착했을 때**: 목적지 주변에서 차를 세우면 픽업 안내를 보여 줍니다.
- 차 정보를 확인할 수 없으면 조작을 잠급니다. 목적지에 가까워졌다고 창문 같은 장치를 자동으로 움직이지 않습니다.

현대자동차그룹 차량용 앱 개발 공모전 "Code your App into Pleos App Market" 출품작입니다.

## APK

| 파일 | 용도 | 패키지 |
|---|---|---|
| `apk/PleosConcierge_release.apk` | 실제 차량 서비스 연결용 | `com.pleos.challenge.concierge` |
| `apk/PleosConcierge_DEMO_release.apk` | 가상 데이터 체험용(실제 차량 SDK를 쓰지 않음) | `com.pleos.challenge.concierge.demo` |

- 둘 다 release 빌드(debuggable 아님), 같은 전용 인증서로 서명했습니다.
- 검증 환경: 공식 Pleos 에뮬레이터(API 34, x86_64, 1280×800).

```powershell
adb install apk/PleosConcierge_DEMO_release.apk
adb install apk/PleosConcierge_release.apk
```

### DEMO 앱 둘러보기
- 처음 화면은 정차 상태예요. 온도·바람 세기를 바꿔 볼 수 있어요.
- 오른쪽 위 **어시스턴트** 버튼: 커피를 미리 주문하는 대화를 시연해요. **정해진 대본을 보여 주는 시연**이고, 실제 음성 인식·AI 답변·주문·결제는 하지 않아요.
- 왼쪽 위 `concierge` 글자를 길게 누르면 시연 설정이 열려요. 정차 / 주행(가상 내비 화면) / 도착(픽업 안내) / 신호 없음(조작 잠금)을 고를 수 있어요.
- 내비 경로, 속도 60 km/h, 배터리 72%, 픽업 번호 A-12는 모두 시연용 가상 값이에요.

### 실제 차량용 앱
공식 개발자 프로젝트 등록, 서비스 승인, 차량 Capability가 있어야 동작합니다. 권한이나 차량 값이 확인되지 않으면 조작이 잠긴 화면이 나오는 게 정상입니다.

## 소스로 빌드

- JDK 17 이상(검증: JDK 21), Android SDK Platform 35 / Build-tools 35.0.0, Gradle 8.10.2 wrapper, AGP 8.8.2, Kotlin 2.0.21. minSdk 28, compile/target 35.
- Pleos 공식 Maven 저장소에서 Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, FusedLocation 1.1.3을 받습니다.

1. `JAVA_HOME`을 설정하고, `local.properties`의 `sdk.dir`에 Android SDK 경로를 적습니다.
2. 테스트와 디버그 빌드: `./gradlew.bat :core:test :app:testDemoDebugUnitTest :app:assembleDemoDebug :app:assembleVehicleDebug`
3. release: `./tools/build_release.ps1 -CreateKey`(새 인증서) 또는 `./tools/build_release.ps1`(이 PC에 이미 만든 인증서). 서명 키는 이 저장소에 없습니다.

## 구조

```
core/   상태 판단(정차·주행·도착·잠금), 안전 가드, SDK 어댑터 계약 — 순수 Kotlin, 단위 테스트
app/    Android 앱(UI, 실제 SDK 연결, DEMO 시나리오, 가상 내비 화면, 어시스턴트 대화 시연)
```

- 차량 제어는 연결·권한·같은 출처의 최신 P단·속도 0·UX 제한 없음이 모두 확인될 때만 요청합니다. 요청이 접수된 것과 차에서 실제 값이 확인된 것을 구분해 보여 줍니다.
- 단위 테스트: core 23건, DEMO 앱 48건 통과(2026-11-08).

## 검증하지 않은 것
실차에서의 공조·시트·창문 제어, 실제 내비 경로·위치 알림·TTS 재생, 실제 주행 성능, Market 심사 통과는 확인하지 않았습니다.

## 출처
`docs/third_party_notices.md`를 보세요. 앱 속 차량 그림은 AI로 만든 이미지이며 실제 차종이 아닙니다.
