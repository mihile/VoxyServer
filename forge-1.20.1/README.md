# VoxyServer: Minecraft 1.20.1 Forge

기존 VoxyServer Fabric 1.20.1 포트를 Forge용으로 옮긴 별도 프로젝트입니다.
서버에서 청크를 LOD로 변환하고 이 모드가 설치된 클라이언트에 전송합니다.
기존 월드 가져오기, 블록 변경 반영, 차원 이동, 클라이언트별 설정과 해시
동기화를 포함합니다. 싱글플레이 통합 서버에서는 전송 기능을 시작하지 않습니다.

## 설치

Minecraft 1.20.1, Forge 47.3.0 이상, Java 17이 필요합니다. 양쪽에 서로 다른
VoxyServer JAR을 설치합니다. 두 JAR을 같은 `mods` 폴더에 넣으면 안 됩니다.

| 위치 | 필요한 모드 |
| --- | --- |
| 전용 서버 | `VoxyServer-Forge-1.2.4+1.20.1-forge.2-server.jar`, 기준 Neo Voxy JAR |
| 클라이언트 | `VoxyServer-Forge-1.2.4+1.20.1-forge.2-client.jar`, 기준 Neo Voxy JAR, Embeddium 0.3.31 이상 |

기준 Neo Voxy JAR은 로컬 `neovoxy` 프로젝트의 다음 파일입니다.

`editions/forge-1.20.1-client/build/libs/neo-voxy-0.4.0beta-mc1.20.1-forge-client.jar`

이름에 `client`가 들어가지만 서버 엔진이 사용하는 공통 코드도 들어 있으므로,
검증에서는 원본 Neo Voxy JAR을 서버에도 설치했습니다. Neo Voxy의 서버 시작 로그에
Embeddium 렌더러 클래스를 찾지 못했다는 경고가 발생할 수 있습니다. 테스트한 전용
서버에서는 그 경고 이후 가져오기와 전송이 정상 동작했으며, 서버에 Embeddium을
설치할 필요는 없었습니다. Neo Voxy는 이 프로젝트에 포함하지 않습니다.

서버용 JAR에는 전용 서버에 없는 LWJGL 코어와 네이티브 라이브러리가 들어 있습니다.
클라이언트용 JAR에는 Minecraft가 제공하는 LWJGL과의 충돌을 피하기 위해 들어 있지
않습니다. 기준 Neo Voxy의 슬림 JAR은 Windows/Linux x86_64용 네이티브를 포함하며,
실행 검증은 Windows x86_64에서 수행했습니다.

모드가 없는 일반 클라이언트도 서버에 접속할 수 있지만 LOD 데이터는 받지 않습니다.
Forge 포트는 Forge 네트워크 채널을 사용하므로 Fabric용 VoxyServer와 섞어 사용할 수
없습니다. 기존 Fabric 월드를 옮길 때는 기존 `world/voxyserver` 캐시를 백업하고
새로 생성하는 것을 권장합니다. 두 Voxy 구현 간 기존 저장소 호환성은 검증하지 않았습니다.

클라이언트 설정은 서버에 접속한 상태에서 Forge 모드 목록의 VoxyServer 설정 화면을
열어 변경할 수 있습니다. 설정 파일은 `config/voxyserver-client.json`입니다. 서버
설정은 `config/voxyserver.json`이고 기존 월드 가져오기는 서버 콘솔에서
`voxyserver import existing all`을 실행합니다. 자세한 명령과 설정 항목은 상위
프로젝트의 README를 참고하세요.

접속 직후 Neo Voxy가 `Not creating renderer due to null instance`를 기록할 수
있습니다. 연결 정보가 준비될 때까지 초기화를 미루기 때문에 먼저 발생하는 메시지이며,
정상 초기화 시 이어서 `Voxy render system created`가 기록됩니다.

## 빌드

먼저 기준 Neo Voxy 1.20.1 Forge 프로젝트를 빌드한 뒤 이 디렉터리에서 실행합니다.

```powershell
$env:JAVA_HOME = $env:JAVA_HOME_17
.\gradlew.bat build
```

기본 경로 대신 다른 Neo Voxy 빌드 결과를 사용할 때는 다음 속성을 지정합니다.

```powershell
.\gradlew.bat build '-Pvoxy_jar=C:/path/to/neo-voxy-forge-client.jar'
```

설치용 파일은 `build/libs/`의 `-server.jar`와 `-client.jar`입니다. `-sources.jar`는
소스 열람용입니다. 기준 Neo Voxy는 로컬 빌드 의존성이며 VoxyServer JAR에 복사하거나
재배포하지 않습니다.

## 검증

기준 소스는 Neo Voxy 커밋 `84ac197d92627aeeea167702757f14bf84c2f26f`의 1.20.1
Forge 에디션이며, 사용한 Neo Voxy JAR의 SHA-256은
`059778ddd66ceab01702945644e56039106408ffc5e7b257882e043c575aeb22`입니다.

- Java 17에서 Forge 빌드와 28개 프로토콜·캐시·생명주기 관련 단위 테스트 통과.
- 격리된 Forge 47.3.0 전용 서버에서 오버월드 529개와 네더 81개 청크 가져오기,
  디스크 저장 및 정상 종료 확인.
- 원본 Neo Voxy와 Embeddium 0.3.31을 사용한 격리 클라이언트에서 접속, 오버월드
  1,728개 LOD 섹션 저장, 설정 화면 저장, 블록 변경 전파, 네더 이동·복귀, 재접속과
  캐시 재사용 확인.
  클라이언트 검증은 배포 JAR을 Loom 개발 매핑으로 변환해 실행했습니다.
- 모드가 없는 1.20.1 프로토콜 클라이언트의 로그인과 월드 진입 확인.

다른 모드팩과 셰이더 설정, 장시간 성능은 검증하지 않았습니다.

추가로 `.forge.2`를 사용해 실제 선릿밸리 모드팩의 복사본에서 셰이더 사용, LOD
전송, 블록 변경, 차원 이동과 재접속을 검증했습니다. 자세한 결과와 범위는
[선릿밸리 검증 보고서](docs/SUNLIT_VALIDATION_20261115.md)를 참고하세요.
