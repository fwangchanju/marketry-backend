# 커밋 컨벤션

메시지 형식은 이 문서를, 문체는 `docs/rules/writing.md`를 따른다.

## 형식
<type>: <설명>

## 타입

- feat: 새로운 기능 추가
- fix: 버그 수정
- refactor: 동작 변경 없는 코드 구조 개선 (변수/메서드명, 로직 재배치, 패턴 통일 등)
- style: 코드 스타일 개선 (포매팅, 네이밍 컨벤션 정리 등. 이제부터는 지금까지 refactor로 해왔던
  스타일성 개선도 여기로 포함)
- chore: 빌드 설정, 의존성 등 그 외 잡무성 변경
- test: 테스트 코드 추가/수정
- migrate: DB 마이그레이션 스크립트 변경
- docs: 문서(README 등)만 수정, 코드 변경 없음
- perf: 성능 개선 목적의 변경 (동작/구조는 그대로, 더 빠르게 만드는 게 목적일 때 refactor 대신 사용)
- ci: CI/CD 파이프라인, GitHub Actions 워크플로 설정 변경
- build: 빌드 도구/의존성 관련 변경 (build.gradle 등. chore보다 더 구체적으로 표현하고 싶을 때 사용)
- revert: 이전 커밋을 되돌리는 경우

## 브랜치 명

claude/<type>/<작업-내용-kebab-case>

- <type>은 위 커밋 타입과 동일한 기준으로 선택한다.
- 작업 내용은 영문 kebab-case로 간결하게 요약하되, 최대 3단어 이하로 작성한다.

예: claude/feat/widget-followups, claude/fix/admin-tokens-env

