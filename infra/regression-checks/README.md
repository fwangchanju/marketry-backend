# 인프라 회귀 검증

배포 게이트·Actions 배포 분기·개발 스냅샷 복원의 보호 동작이 이후 변경에서도 유지되는지 확인한다.
일회성 배포 확인 스크립트가 아니므로 배포 후에도 유지한다.
실제 운영 Docker·SSH·환경 파일은 사용하지 않고 명령을 모조해 검증한다.
이 검증의 성공은 실제 운영 배포 성공을 뜻하지 않는다.

- `gate-regression-check.sh`: 배포 태그, 헬스 실패, 이미지 보존·정리와 nginx 재시작 판정
- `macmini-release-regression-check.sh`: 서버 분기, 이미지 고정, 실패 원복과 포인터 보존
- `restore-snapshot-regression-check.sh`: 개발 DB 대상 격리, 잘못된 덤프 거부와 실패 처리

- `image-names-regression-check.sh`: 이미지 이름 이전의 재실행·포인터 보존·인증 실패·다이제스트 검증
- `paths-regression-check.sh`: 관련 변경 시에만 CI 회귀 검증을 선택하는지 확인

CI의 `build` 체크는 항상 유지하며 이 검증 묶음만 변경 경로에 따라 실행한다.
