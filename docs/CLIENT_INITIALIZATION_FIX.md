# Minecraft 1.20.1 클라이언트 초기화 수정 (port.2)

## 확인한 설치 상태

2026-10-09에 사용자가 지정한 클라이언트/서버의 JAR, latest.log, 설정,
저장소 및 해시 파일을 먼저 읽었다. 두 설치의 Voxy와 VoxyServer는 각각
동일한 SHA-256이었다.

- Voxy port.7: `b81ff5c12d2989c3b7bb9f3d0b90783c0fb1b606068a19a506acb8745681a34c`
- VoxyServer port.1: `a4c7947b19b58a1cabe5803723659954d2dc8c74e971cc7d0ba4274dd17215c2`

실제 로그의 15:38:32 초기화는 Netty Client IO #6에서 발생했다. 당시 클라이언트
저장소는 `.voxy/saves/UNKNOWN/8935c0b5f7a5360b4880bc14d0ff6c7f/storage`에
있었으며, 재확인한 레거시 해시 파일은 2,360개(37,764바이트)였다.
사용자가 제시한 2,348개는 이전 시점의 관측값이다. 서버는 동일한 월드 ID의
저장소를 사용하고 있었고, 서버 LOD 수신 전체가 실패한 상황은 아니다.

## 원인

설치된 Voxy JAR의 `javap` 결과와 로컬 Voxy port.7 소스를 대조했다.
Mojang 이름으로 표현하면 호출 순서는 다음과 같다.

```
Netty: ClientPacketListener.handleLogin HEAD
  Voxy MixinClientPacketListener -> ClientSessionEvents.sessionStart()
    inSession = true
    VoxyCommon.createInstance() -> new VoxyClientInstance()
      VoxyInstance super constructor RETURN
        VoxyServer VoxyInstanceMixin -> new RemoteIngestService()
      VoxyClientInstance.getBasePath()
        Minecraft.gameMode == null -> Network handle null -> UNKNOWN
  vanilla PacketUtils.ensureRunningOnSameThread(...) -> client thread 재실행
```

VoxyServer의 생성자 mixin은 이미 생성 중인 Voxy 인스턴스에 서비스를 붙인다.
VoxyServer 패킷 수신기가 Voxy 인스턴스를 최초 생성하는 것은 아니다.
문제의 최초 호출은 Voxy 자체의 로그인 HEAD injection이다. vanilla의 스레드
검사보다 앞서 실행되므로 Netty에서 호출된다. 메인 스레드 재실행 시에는
`inSession`이 이미 true여서 올바른 경로로 다시 생성되지 않는다.

VoxyServer 수신 콜백만 `client.execute()`로 감싸면 이 앞선 생성은 남는다.
또한 메인 스레드에 있다는 사실만으로 해당 연결의 gameMode, 플레이어, 월드,
ServerData가 준비되었다고 보장할 수 없다.

## 변경 범위

VoxyServer JAR의 호환 mixin으로 Voxy의 조기 sessionStart를 보류한다.
Voxy 본체의 소스와 설치 JAR은 수정하지 않는다. 이 패치는 조사한 port.7의
ClientSessionEvents와 렌더러 인터페이스에 의존한다.

- `VoxySessionStartMixin`: 직접 sessionStart를 막고 준비 검사 후의 호출만 허용.
- `ClientLoginMixin`: vanilla handleLogin RETURN에서 연결을 등록.
- `GameModeConnectionAccessor`: gameMode가 현재 연결에 속하는지 확인.
- `ClientLodReceiver`: 현재 핸들러, 살아 있는 연결, gameMode, 플레이어,
  월드, ServerData가 모두 일치하는 client tick에서 생성. 생성한 실제 경로를
  해당 서버의 예상 경로와 대조한다. 지연 생성 후 렌더러도 연결한다.
- `ClientLodNetworking`, `ConnectionInbox`: Netty에서는 파싱과 연결별 순서 큐만 처리.
  프로토콜/설정/스트림 세션/LOD 순서를 보존하고 초기화 후 handshake를 한 번 전송.
  배치 대기 한도 초과는 OVERFLOW ACK로 서버 재전송을 유도한다.
- `ClientDisconnectMixin`: Voxy 종료 전에 이전 연결과 대기 작업을 무효화.
- `RemoteIngestService`: 각 작업이 특정 WorldEngine과 캐시 키를 참조하고
  엔진 reference를 유지한다. 작업 스레드에서 새 전역 인스턴스를 조회하지 않는다.
  차원/epoch/generation 변경으로 이전 작업을 취소하며 완료 ACK는 원래 연결에만 보낸다.
- `ClientLodSettings`: manifest도 원래 엔진과 연결/세션 generation에 귀속.
- `ClientCacheKey`, `ClientLodHashStore`: 해시 파일도 실제 서버 저장소 경로의
  SHA-256 namespace와 월드 ID로 분리.
- `VoxyInstanceMixin`, `IVoxyServerIngestAccess`, `VoxyClientInstanceMixin`:
  remote ingest 플래그의 스레드 가시성을 보장하고 실제 클라이언트 override에서
  로컬 ingest를 중지한다. 기본 클래스에만 injection하면 override에 적용되지 않는다.
- `voxyserver.client.mixins.json`, `gradle.properties`: mixin 등록 및 port.2 버전.
- `ConnectionInboxTest`, `ClientCacheKeyTest`: 연결 교체/지연 callback/대기 한도,
  서버와 차원별 키 분리 회귀 테스트.

## 캐시 식별 및 기존 UNKNOWN 처리

Voxy의 정상 멀티플레이 경로는 `.voxy/saves/<ServerData.ip의 :를 _로 치환>/<worldId>/storage`다.
서버 목록의 이름이 아니라 접속 주소를 쓴다. 예를 들어 `127.0.0.1:25563`은
`127.0.0.1_25563`, `localhost:25563`은 `localhost_25563`이다.
이번 기존 로그에는 localhost가 서버 프로필 주소로 기록되어 있어, 실제 재접속
로그의 `client cache ready`에서 선택된 주소와 root를 확인해야 한다.

WorldIdentifier의 32자리 ID는 biome seed와 dimension registry key의 SHA-256에서
생성된다. 서버 주소는 포함하지 않는다. 같은 월드를 복제한 서버가 같은 월드 ID를
가질 수 있으므로 서버 root와 월드 ID를 함께 사용해야 한다. 차원마다 월드 ID도 다르다.

실제 서버 `C:\Prominence_2_4.1.2\world\level.dat`를 읽어 world seed에서
vanilla의 biome seed를 계산하고 port.7의 ID 공식을 적용한 결과도 확인했다.

| 차원 | 계산한 월드 ID |
| --- | --- |
| minecraft:overworld | `8935c0b5f7a5360b4880bc14d0ff6c7f` (기존 양쪽 저장소와 일치) |
| minecraft:the_nether | `acef8a0e4c60fbb821330ebd31355beb` |
| minecraft:the_end | `429a22360e28f9b569d62f479f6d64b8` |

계산에 world seed를 변경하거나 저장소를 열어 쓰는 작업은 하지 않았다.

새 해시 파일 경로는 `voxyserver/hashes/<SHA256(normalized absolute storage root)>/<worldId>.bin`이다.
기존 전역 `<worldId>.bin`은 어느 서버의 기록인지 충분히 식별하지 못하므로 자동으로
새 namespace에 복사하지 않는다. 파일의 int count + long key/hash 형식은 그대로다.
접속 주소의 별칭 변경이나 게임 폴더 이동 시 새 namespace를 사용하여 재수신할 수 있다.

기존 UNKNOWN 저장소와 레거시 해시 파일은 삭제하거나 이동하지 않았다.
권장 처리는 그대로 보존하고, 올바른 서버 root의 빈 저장소에 서버 LOD를 다시 받는 것이다.
오프라인 이전을 원한다면 게임/서버 종료와 전체 백업 후, UNKNOWN을 만든 원래 서버,
biome seed, 차원 key, 블록/바이옴 registry 및 mapper, 저장소 설정의 일치를 확인해야 한다.
월드 ID 일치만으로 서버가 같다고 단정하거나 storage 폴더만 실행 중 옮기면 안 된다.
이 조건을 확인하지 않은 자동 마이그레이션은 구현하지 않았다.

## 검증 결과

15개 JUnit 테스트와 Java 17 타깃 빌드/배포 JAR remap을 통과했다.
`build/lifecycle-build.log`, `build/test-results/test/TEST-*.xml`에 결과가 있다.

사용 중인 Voxy와 SHA-256이 같은 port.7 JAR, Fabric API 0.92.6, Sodium 0.5.13을
사용하는 별도 그래픽 클라이언트와 두 로컬 Fabric 서버에서 검증했다.
배포용 port.2 JAR 자체를 Java 17 + production mappings로 실행한 결과다.
사용자 PrismLauncher 인스턴스/실제 서버의 월드는 테스트에 사용하지 않았다.

| 항목 | 결과 |
| --- | --- |
| 첫 접속 및 반복 재접속 | 정상. 초기화/RemoteIngestService 모두 Render thread. Network handle null 0건 |
| 초기화 수 | 정상 연결마다 한 번. 초기화 전에 끊은 연결에서는 인스턴스 생성 없음 |
| 서버 A 캐시 | `.voxy/saves/127.0.0.1_25585/<worldId>/storage` |
| 복제 서버 B 캐시 | `.voxy/saves/127.0.0.1_25586/<worldId>/storage`. 같은 오버월드 ID라도 서버별 저장소/해시 파일 분리 |
| 수신·저장 | 오버월드 hash 1,728개 / 실제 저장 section 1,728개. 네더 각각 200개 |
| 렌더러 | 현재 차원의 엔진과 동일 객체. geometry section 79~114개, 모델 bake와 GPU frame 실행 기록 확인 |
| 차원 이동 | 네더 왕복, 서로 다른 월드 ID/저장소 및 epoch 처리 정상 |
| 빠른 접속 해제 | 지연 초기화 없음. 다음 연결의 정상 초기화/저장/렌더러 확인 |
| 처리 중 해제 | 테스트 전용 worker barrier로 실제 배치 7개가 대기/처리 중인 상태 재현. 취소 및 재접속 성공 |
| 작업 정리 | 종료 후 inbox, pending LOD, reserved batch/byte, pending manifest/chunk가 비고 Remote Ingest worker가 종료됨 |
| 종료 | 테스트 클라이언트/두 서버 모두 exit 0. lifecycle harness PASS |

테스트 결과:

- `build/lifecycle-production-first.log`, `build/lifecycle-production-first-result.txt`:
  배포 JAR 첫 접속·재접속·차원 왕복·복제 서버·빠른 해제 전체 PASS.
- `build/lifecycle-cleanup-check.log`: 각 접속 해제 후 CLEANED 검사 통과.
  처리 중 해제를 빠른 CPU에서 확실히 재현하기 위해 이 실행은 종료하고 별도 스트레스 실행을 했다.
- `build/lifecycle-stress-client.log`, `build/lifecycle-stress-client/lifecycle-result.txt`:
  배치 7개 취소, 작업/엔진 참조 정리, 재접속 후 hash/storage 각각 200개 및 렌더러 확인, PASS.
- `verification/lifecycle/`: 재현용 소스와 실행 방법. 테스트 모드와 worker barrier는 배포 JAR에 포함되지 않는다.

Voxy의 `setLevel/allChanged`에서 지연 인스턴스보다 먼저 수행하는 기존 렌더러 생성
시도는 `Not creating renderer due to null instance`를 기록할 수 있다. 이후 준비된
tick에서 명시적으로 렌더러를 생성하며 위 검증에서 정상 동작했다. 이 로그를
숨기는 패치는 하지 않았다. offline 테스트 계정의 인증 경고와 설치하지 않은
optional Iris mixin 경고도 실제 서버 연동 오류로 집계하지 않았다.

실제 Prominence 전체 모드팩, 셰이더/리소스팩 조합과 장시간/고부하 사용은 아직
실행 검증하지 않았다. 캐시 분리는 접속 주소/저장소 root와 월드 ID에 따른 분리이며,
서버가 기존 seed/차원 ID를 그대로 쓰면서 데이터를 교체하는 경우까지 새로운
월드로 판별하는 별도 서버 UUID 프로토콜을 추가한 것은 아니다.

## 빌드 및 실제 설치

설치용 파일: `build/libs/VoxyServer-1.2.4+1.20.1-port.2.jar` (1,280,743바이트)

SHA-256: `ac65e052107e3f9f5b0eba613e269eeb31fcb83d5e8d2b207860348ebcd121f8`

2026-10-09 16:41:31 KST에 게임/서버 Java 프로세스가 없고 25563 포트가
listen 중이 아님을 재확인한 후 양쪽 설치를 port.2로 교체했다. 설치된 두 파일의
SHA-256을 빌드 산출물과 대조했다. Voxy port.7 JAR, 설정, 로그, 기존 캐시는 유지했다.

- 클라이언트: `C:\Users\maste\AppData\Roaming\PrismLauncher\instances\Prominence™ II- Hasturian Era\minecraft\mods\VoxyServer-1.2.4+1.20.1-port.2.jar`
- 서버: `C:\Prominence_2_4.1.2\mods\VoxyServer-1.2.4+1.20.1-port.2.jar`

기존 port.1 JAR은 각 Minecraft/서버 root의
`voxyserver-backups/20261009-164131/VoxyServer-1.2.4+1.20.1-port.1.jar`에
원래 SHA-256 그대로 보관했다. mods에는 새 버전만 남겼다.
설치 후 실제 Prominence 게임이나 실제 서버를 자동으로 시작하지 않았다.

화면 글리치나 FPS 저하와 이 초기화 문제 사이의 인과관계는 현재 로그만으로
확정할 수 없다. 이번 수정 및 검증은 생성 시점, 저장소 식별, 스트림 수명주기 범위다.
