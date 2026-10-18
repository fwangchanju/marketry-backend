#!/bin/bash
# CI 실행 범위가 일반 앱/문서 변경으로 불필요하게 넓어지지 않는지 확인한다.
set -euo pipefail
selector="$(cd "$(dirname "$0")" && pwd)/relevant-paths.sh"
for path in infra/prod/gate.sh infra/prod/compose.yml infra/prod/nginx.conf infra/scripts/macmini-release.sh infra/local/restore-snapshot.sh infra/regression-checks/README.md .github/workflows/ci.yml .github/workflows/migrate-image-names.yml; do
  printf '%s\n' "$path" | /bin/bash "$selector"
done
if printf '%s\n' src/main/java/Example.java docs/operations.md infra/prod/README.md | /bin/bash "$selector"; then exit 1; fi
if /bin/bash "$selector" < /dev/null; then exit 1; fi
echo 'PASS: 관련 인프라 변경만 회귀 검증 선택, 앱·문서·빈 변경은 건너뜀'
