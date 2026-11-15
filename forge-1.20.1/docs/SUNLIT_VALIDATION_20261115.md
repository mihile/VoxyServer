# 선릿밸리 Forge 모드팩 검증 — 2026-11-15

실제 `선릿밸리` 클라이언트(352개 JAR)와 `C:/Sunlit_Valley` 서버(315개 JAR)의
모드·설정 및 서버 월드를 `build/sunlit-fixture/`에 복사해 검증했다. 원본은 수정하지
않았다. 서버에는 Neo Voxy와 VoxyServer 서버용을, 클라이언트에는 VoxyServer
클라이언트용과 테스트 하네스만 추가했다. Forge 47.4.0, GraalVM Java 17.0.19,
로컬 루프백과 격리 월드를 사용했다. 테스트 서버는 종료했다.

기존 `.forge.1`에서는 셰이더를 켜고 재접속하면 원격 LOD는 수신하지만 Voxy 렌더러가
다시 생성되지 않는 문제가 재현됐다. Iris가 파이프라인을 재사용하면 최초 생성 훅이
다시 호출되지 않기 때문이었다. `.forge.2`는 렌더러가 계속 없을 때 생성 훅을 기다린
후 한 번 생성한다. 실제 선릿밸리 설정으로 재시험하여 재접속 뒤에도
`IrisVoxyRenderPipeline`이 생성되는 것을 확인했다.

검증 결과:

- 실제 서버 월드 복사본의 오버월드 4,102개 청크와 다른 세 차원 가져오기 완료.
- 실제 클라이언트 및 Emberbium/Emberlens 셰이더 사용 상태에서 서버 접속과 LOD 수신.
- 설정 화면, 블록 변경 후 LOD 해시 변경, 네더 이동과 복귀, 재접속 및 캐시 재사용.
- 재접속 시 LOD 13,320개 해시와 저장 섹션 13,320개 확인, 하네스 `PASS`, 클라이언트
  종료 코드 0, 서버 정상 저장·종료. 네더에서는 392개 저장 섹션을 확인했다.
- Java 17 빌드와 기존 28개 단위 테스트 통과.

셰이더 uniform 누락 로그와 AdvancedLootInfo의 NPE는 테스트에도 나타났으나, 모드
추가 전 원본 클라이언트 로그에도 동일한 메시지가 있었다. 테스트 실행기는 별도 실행
인자를 사용하므로 Crash Assistant 보조 프로세스가 Log4j를 찾지 못했으며, 게임
프로세스 자체는 정상 실행됐다. 픽셀 단위 화면 비교, 장시간 플레이, 성능 및 다른
셰이더 설정은 검증하지 않았다.

증거: `build/sunlit-fixture/client/forge-verification-result.txt`,
`build/sunlit-fixture/client-process.log`, `build/sunlit-fixture/server/logs/latest.log`.
