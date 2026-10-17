# 맥미니 배포 워크플로 연결 지시서

## 범위와 전제

기존 Release에 `server` 입력(`oracle` 기본값, `macmini`)과 `connection-check` 대상을 추가한다.
기존 오라클 배포와 이미지 빌드·main 승격은 유지한다. 프론트 자산 발행 이벤트는 저장소 변수
`RELEASE_SERVER`가 없으면 오라클로 보내고 이관 완료 뒤 `macmini`로 전환한다.
설치된 게이트·백업은 사용자가 `424358a` 원본과 일치를 확인했다. 등록된 Tailscale·맥미니 시크릿 이름은 유지한다.
운영 서버·Docker·환경 파일은 직접 접근하지 않는다. DB 이관·터널 변경·운영 앱 배포는 이번 작업에서 실행하지 않는다.

## 실행 순서와 보호

1. 수동 실행은 main에서만 허용한다. 잘못된 server/target 값은 실패시킨다.
2. 맥미니 `connection-check`는 배포 허용 변수와 무관하게 실행한다. Tailscale 공식 액션 v4와 기존 OAuth 시크릿,
   `tag:ci` 임시 노드를 쓴다. SSH는 기존 맥미니 시크릿과 일반 OpenSSH를 사용한다.
   게이트가 거부하는 고정 문자열 `connection-check`를 보내 종료 코드 2와 게이트의 고정 거부 메시지를 모두 확인한다.
   원격 셸 스크립트·환경변수 전달·Docker 호출·DB 덤프·컨테이너 기동·포인터 변경은 하지 않는다.
   거부 응답은 이 검사의 예상 결과이며 검증을 통과한 경우에만 성공으로 판정한다.
3. 모든 맥미니 실제 배포는 저장소 변수 `MACMINI_DEPLOY_ENABLED == 'true'`일 때만 허용한다.
   기존 앱 중지·최종 DB 덤프·맥미니 DB 복원 뒤 사용자가 README의 수동 첫 앱·nginx·렌더러 기동과 내부 캡처를 확인한 뒤에만 이 변수를 켠다. 이번 작업에서는 켜지 않는다.
4. 수동 배포와 프론트 자산 발행 배포를 공통 concurrency로 직렬화하고 실행 중인 배포는 취소하지 않는다.
5. nginx·renderer 이미지는 기존 job에서 빌드한다. 맥미니 경로에서는 오라클 SSH 단계를 건너뛴다.
   `all`은 두 빌드 성공 뒤 게이트를 한 번만 호출한다. 게이트가 앱 요청 태그와 nginx·renderer latest를 나눈다.
6. 맥미니 실제 배포는 `deploy <application|nginx|renderer|all> <tag>`만 보낸다.
   rollback은 application previous에 해당한다. 헬스체크는 게이트 종료 코드로 판정한다.
7. 앱 대상 태그와 deployed/previous 다이제스트는 배포 전에 고정한다. 게이트가 digest 입력을 받지 않으므로
   고정한 다이제스트를 `deploy-<digest hex>` 태그로 만들어 전달한다.
   선택 포인터 조회는 명시적인 manifest unknown 또는 해당 이미지 참조의 not found만 부재로 보고 인증·통신·형식 오류는 실패시킨다. 포인터 갱신도 고정된 다이제스트를 사용한다.
   일반 배포 성공 때 deployed/previous를 갱신하고 같은 이미지 재배포는 previous를 유지한다.
   rollback 성공 때 두 포인터를 교환한다. nginx·renderer 단독 배포는 앱 포인터를 바꾸지 않는다.
8. 게이트 실패 자체를 continue-on-error로 수집한다. 일반 앱 배포 또는 all 실패 때 기존 deployed가 있으면
   application만 고정된 deployed로 복구하고 포인터는 유지한다. 복구 성공 여부와 무관하게 원래 배포는 실패로 끝낸다.
   rollback 실패는 자동 복구하지 않는다. 최초 배포 실패는 복구할 이미지가 없음을 표시한다.
9. SSH 키는 runner 임시 디렉터리에 600 권한으로 저장하고 항상 삭제한다. 비밀값을 출력하지 않는다.
   새 runner의 호스트 키는 Tailscale로 보호되는 연결에서 accept-new로 최초 등록하고 이후 동일 파일로 검증한다.

## 완료와 검증

지시서를 별도 에이전트가 실제 코드와 대조해 검토한 뒤 PR로 병합한다. 구현도 별도 PR로 진행한다.
워크플로 actionlint, 게이트 Bash 회귀 검사, 컴파일·일반 테스트·포맷 검사를 통과한다.
운영 접근 없이 모조 SSH·레지스트리로 접속 검사 성공/실패, 앱 배포·동일 이미지·롤백 포인터,
게이트 실패 원복·원복 실패·최초 실패·nginx/renderer 포인터 미변경을 검사한다.
워크플로의 main/server/배포 활성화 보호와 all·단독·repository_dispatch 분기도 검증한다.
non-main·잘못된 입력 거부, 허용 변수 미설정 시 SSH 0회, connection-check의 배포·포인터 변경 0회,
all 빌드 실패 시 게이트 0회, 단독 배포의 skipped 의존 허용, 맥미니 경로의 오라클 SSH 0회를 포함한다.
Actions 전용 구현은 `infra/scripts/macmini-release.sh`, 모조 검사는 `infra/prod/tests/macmini-release-test.sh`에 둔다.
PR 병합 뒤 실제 Actions에서는 connection-check만 실행한다. 실제 앱 배포와 고의적인 운영 장애 검증은 DB 이관 이후 별도 단계다.
워크플로 권한으로 푸시가 거부되거나 도구가 동작을 거부하면 우회하지 않고 멈춘다.
