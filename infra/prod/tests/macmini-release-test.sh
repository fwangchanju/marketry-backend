#!/bin/bash
# 운영 Docker·SSH·환경 파일을 사용하지 않는 Actions runner 회귀 검사.
set -euo pipefail
script="$(cd "$(dirname "$0")/../../scripts" && pwd)/macmini-release.sh"
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
test_root=$(mktemp -d /tmp/marketry-release-test.XXXXXX)
trap 'rm -rf "$test_root"' EXIT
export RUNNER_TEMP="$test_root" GITHUB_OUTPUT="$test_root/output" MOCK_ROOT="$test_root"
export MACMINI_HOST=macmini.example MACMINI_USER=deploy
export EVENT_NAME=workflow_dispatch REF_NAME=refs/heads/main
export REQUESTED_SERVER=macmini REQUESTED_TARGET=application DEPLOY_ENABLED=true
export TARGET=application
count=0
status=0
new_case() {
  : > "$GITHUB_OUTPUT"; : > "$test_root/trace"
  rm -f "$test_root/"*.digest "$test_root/error" "$test_root/ssh-fail" "$test_root/ssh-response" "$test_root/create-fail"
  unset NEW_DIGEST OLD_DIGEST IMAGE_TAG AUTO_SERVER
  export EVENT_NAME=workflow_dispatch REF_NAME=refs/heads/main
  export REQUESTED_SERVER=macmini REQUESTED_TARGET=application DEPLOY_ENABLED=true TARGET=application
}
run() {
  status=0
  /bin/bash "$script" "$@" > "$test_root/stdout" 2> "$test_root/stderr" || status=$?
}
pass() { count=$((count + 1)); echo "PASS: $*"; }
expect_ok() { [ "$status" = 0 ] || { cat "$test_root/stderr"; exit 1; }; }
expect_fail() { [ "$status" != 0 ] || { echo '예상한 실패가 발생하지 않았습니다.'; exit 1; }; }
no_remote() { [ ! -s "$test_root/trace" ]; }
digest() { printf 'sha256:%064x' "$1"; }

# 자식 Bash에도 함수만 전달한다. 실제 실행 파일로 대체하지 않는다.
docker() {
  printf 'docker %s\n' "$*" >> "$MOCK_ROOT/trace"
  [ "$1 $2" = 'buildx imagetools' ] || return 88
  case "$3" in
    inspect)
      [ "$4" = --format ] || return 88
      local tag="${6##*:}"
      if [ -f "$MOCK_ROOT/error" ]; then cat "$MOCK_ROOT/error" >&2; return 1; fi
      if [ -f "$MOCK_ROOT/$tag.digest" ]; then cat "$MOCK_ROOT/$tag.digest"; return 0; fi
      echo "ERROR: ghcr.io/fwangchanju/market-monitor:$tag: not found" >&2
      return 1 ;;
    create)
      [ "$4" = -t ] || return 88
      if [ -f "$MOCK_ROOT/create-fail" ] && [ "${5##*:}" = "$(cat "$MOCK_ROOT/create-fail")" ]; then return 1; fi
      printf '%s\n' "${6##*@}" > "$MOCK_ROOT/${5##*:}.digest"
      return 0 ;;
    *) return 88 ;;
  esac
}
ssh() {
  local command="${!#}"
  printf 'ssh %s\n' "$command" >> "$MOCK_ROOT/trace"
  if [ "$command" = connection-check ]; then
    if [ -f "$MOCK_ROOT/ssh-response" ]; then cat "$MOCK_ROOT/ssh-response"; return 2; fi
    if [ -f "$MOCK_ROOT/ssh-fail" ]; then return 255; fi
    echo 'gate: 거부됨: 허용되지 않는 명령 형식입니다. 형식: deploy <application|nginx|renderer|all> <tag>' >&2
    return 2
  fi
  [[ "$command" =~ ^deploy\ (application|nginx|renderer|all)\ [A-Za-z0-9._-]+$ ]] || return 88
  [ ! -f "$MOCK_ROOT/ssh-fail" ]
}
export -f docker ssh

new_case
unset DEPLOY_ENABLED
run route; expect_fail; no_remote
export REQUESTED_TARGET=connection-check
run route; expect_ok; grep -Fxq target=connection-check "$GITHUB_OUTPUT"; no_remote
pass 'DB 이관 전 실제 배포 차단과 접속 검사 허용'

new_case
export REF_NAME=refs/heads/topic
run route; expect_fail; no_remote
export REF_NAME=refs/heads/main REQUESTED_SERVER=other
run route; expect_fail; no_remote
export REQUESTED_SERVER=oracle REQUESTED_TARGET=connection-check
run route; expect_fail; no_remote
export REQUESTED_SERVER=macmini REQUESTED_TARGET=unknown
run route; expect_fail; no_remote
pass 'non-main·잘못된 server/target·오라클 접속 검사 거부'

new_case
export REQUESTED_SERVER=oracle
run route; expect_ok; grep -Fxq server=oracle "$GITHUB_OUTPUT"
export EVENT_NAME=repository_dispatch AUTO_SERVER=macmini
run route; expect_ok; grep -Fxq server=macmini "$GITHUB_OUTPUT"; grep -Fxq target=nginx "$GITHUB_OUTPUT"
unset AUTO_SERVER
: > "$GITHUB_OUTPUT"
run route; expect_ok; grep -Fxq server=oracle "$GITHUB_OUTPUT"; no_remote
pass '기존 수동 배포와 프론트 이벤트의 기본 오라클·명시 맥미니 라우팅'

new_case
run connection-check; expect_ok
[ "$(cat "$test_root/trace")" = 'ssh connection-check' ]
[ ! -s "$GITHUB_OUTPUT" ]
touch "$test_root/ssh-fail"
run connection-check; expect_fail
rm "$test_root/ssh-fail"
echo 'arbitrary shell output' > "$test_root/ssh-response"
run connection-check; expect_fail
pass '접속 검사는 게이트 거부 응답·종료코드 모두 확인하며 배포 0회'

new_case
export MACMINI_SSH_KEY=mock-key
run ssh-setup; expect_ok
[ "$(cat "$RUNNER_TEMP/marketry-ssh/key")" = mock-key ]
python3 -c 'import os,stat; assert stat.S_IMODE(os.stat(os.environ["RUNNER_TEMP"]+"/marketry-ssh/key").st_mode)==0o600'
run ssh-cleanup; expect_ok; [ ! -e "$RUNNER_TEMP/marketry-ssh" ]; no_remote
pass 'runner SSH 키 권한과 삭제'

new_case
digest 2 > "$test_root/main.digest"; digest 1 > "$test_root/deployed.digest"
run prepare-images; expect_ok
export NEW_DIGEST="$(digest 2)" OLD_DIGEST="$(digest 1)" IMAGE_TAG="deploy-$(printf '%064x' 2)"
# main이 이동해도 준비한 불변 태그와 다이제스트만 배포·승격한다.
digest 3 > "$test_root/main.digest"
run deploy; expect_ok; grep -Fxq "ssh deploy application $IMAGE_TAG" "$test_root/trace"
run update-pointers; expect_ok
[ "$(cat "$test_root/deployed.digest")" = "$NEW_DIGEST" ]
[ "$(cat "$test_root/previous.digest")" = "$OLD_DIGEST" ]
pass '배포 도중 main 이동에도 고정 이미지 배포·포인터 갱신'

new_case
export NEW_DIGEST="$(digest 1)" OLD_DIGEST="$(digest 1)"
run update-pointers; expect_ok; no_remote
pass '동일 이미지 재배포 시 previous 유지'

new_case
export TARGET=rollback
digest 2 > "$test_root/deployed.digest"; digest 1 > "$test_root/previous.digest"
run prepare-images; expect_ok
export NEW_DIGEST="$(digest 1)" OLD_DIGEST="$(digest 2)" IMAGE_TAG="deploy-$(printf '%064x' 1)"
run deploy; expect_ok; grep -Fxq "ssh deploy application $IMAGE_TAG" "$test_root/trace"
run update-pointers; expect_ok
[ "$(cat "$test_root/deployed.digest")" = "$NEW_DIGEST" ]
[ "$(cat "$test_root/previous.digest")" = "$OLD_DIGEST" ]
pass 'rollback은 앱만 배포하고 고정 deployed/previous 교환'

new_case
digest 1 > "$test_root/main.digest"
run prepare-images; expect_ok; grep -Fxq old_digest= "$GITHUB_OUTPUT"
export TARGET=rollback
run prepare-images; expect_fail
pass '최초 배포 허용과 포인터 없는 rollback 차단'

new_case
for error in 'unauthorized: authentication required' 'network timeout' 'unexpected response: not found'; do
  echo "$error" > "$test_root/error"
  run prepare-images; expect_fail
  ! grep -q '^ssh\|imagetools create' "$test_root/trace"
done
rm "$test_root/error"
echo invalid > "$test_root/deployed.digest"
run prepare-images; expect_fail
pass '인증·통신·다이제스트 형식 오류를 태그 부재로 처리하지 않음'

new_case
digest 2 > "$test_root/main.digest"; digest 1 > "$test_root/deployed.digest"
for id in 2 1; do
  printf 'deploy-%064x\n' "$id" > "$test_root/create-fail"
  run prepare-images; expect_fail
  ! grep -q '^ssh ' "$test_root/trace"
done
pass '배포 이미지·원복 이미지 태그 고정 실패 시 게이트 호출 차단'

new_case
for target in nginx renderer; do
  export TARGET="$target"
  run prepare-images; expect_ok
  export IMAGE_TAG=latest
  run deploy; expect_ok
  run update-pointers; expect_ok
  ! grep -q '^docker ' "$test_root/trace"
done
export TARGET=all IMAGE_TAG="deploy-$(printf '%064x' 1)"
run deploy; expect_ok
[ "$(grep -c '^ssh deploy all ' "$test_root/trace")" = 1 ]
pass '단독 nginx·renderer는 앱 포인터 미변경, all은 게이트 1회'

new_case
export IMAGE_TAG=main
touch "$test_root/ssh-fail"
run deploy; expect_fail
rm "$test_root/ssh-fail"
export IMAGE_TAG=deployed
run deploy; expect_ok
! grep -q '^docker ' "$test_root/trace"
export IMAGE_TAG='bad;command'
run deploy; expect_fail
pass '게이트 실패 감지·앱 원복 호출·포인터 유지·잘못된 태그 차단'

# 실제 YAML의 if 식을 모조 needs/steps로 평가한다. SSH·Docker를 호출하지 않는다.
ruby -ryaml -rjson -e 'puts JSON.generate(YAML.load_file(ARGV[0]))' "$repo/.github/workflows/release.yml" > "$test_root/workflow.json"
python3 - "$test_root/workflow.json" <<'PY'
import json, re, sys
from types import SimpleNamespace as NS
workflow=json.load(open(sys.argv[1]))
jobs=workflow['jobs']
def node(value):
    if isinstance(value,dict): return NS(**{k:node(v) for k,v in value.items()})
    return value
def evaluate(expression, **context):
    text=expression.replace('${{','').replace('}}','').strip()
    text=text.replace('&&',' and ').replace('||',' or ')
    text=re.sub(r'!(?!=)', ' not ', text)
    return bool(eval(' '.join(text.split()), {'__builtins__':{}}, dict(context,always=lambda:True,cancelled=lambda:False)))
def needs(server='macmini',nginx='skipped',renderer='skipped',route='success',target='application'):
    return node(dict(route=dict(result=route,outputs=dict(server=server,target=target)),nginx=dict(result=nginx),renderer=dict(result=renderer)))
condition=jobs['macmini']['if']
for target,n,r in [('application','skipped','skipped'),('rollback','skipped','skipped'),('connection-check','skipped','skipped'),('nginx','success','skipped'),('renderer','skipped','success'),('all','success','success')]:
    assert evaluate(condition,needs=needs(nginx=n,renderer=r,target=target)), target
for n,r,route,server in [('failure','success','success','macmini'),('success','cancelled','success','macmini'),('skipped','skipped','failure','macmini'),('success','success','success','oracle')]:
    assert not evaluate(condition,needs=needs(server=server,nginx=n,renderer=r,route=route))
assert set(jobs['macmini']['needs'])=={'route','nginx','renderer'}
for job,target in [('nginx','nginx'),('renderer','renderer')]:
    for server in ['macmini','oracle']:
        for requested in ['application','nginx','renderer','all','rollback','connection-check']:
            enabled=evaluate(jobs[job]['if'], github=node(dict(event_name='workflow_dispatch')), inputs=node(dict(target=requested)),needs=needs(server=server))
            assert enabled==(requested in [target,'all'])
    for step in jobs[job]['steps']:
        if step.get('uses','').startswith('appleboy/ssh-action'):
            assert not evaluate(step['if'],needs=needs())
assert evaluate(jobs['nginx']['if'],github=node(dict(event_name='repository_dispatch')),inputs=node(dict(target='')),needs=needs())
assert not evaluate(jobs['application']['if'],needs=needs(),github=node(dict(event_name='workflow_dispatch')),inputs=node(dict(target='application')))
steps=jobs['macmini']['steps']
revert=next(s for s in steps if s.get('id')=='revert')
update=next(s for s in steps if s.get('name')=='Update deploy pointers')
report=next(s for s in steps if s.get('name')=='Report failed deployment')
for target in ['application','all','rollback','nginx','renderer']:
    for deployed in ['','deploy-old']:
        state=node(dict(deploy=dict(outcome='failure'),images=dict(outputs=dict(revert_tag=deployed))))
        assert evaluate(revert['if'],env=node(dict(TARGET=target)),steps=state)==(target in ['application','all'] and bool(deployed))
        assert not evaluate(update['if'],steps=state)
        assert evaluate(report['if'],steps=state)
assert next(s for s in steps if s.get('id')=='deploy')['continue-on-error'] is True
assert revert['continue-on-error'] is True
assert 'exit 1' in report['run']
assert steps[-1]['if']=='always()'
assert workflow['concurrency']['cancel-in-progress'] is False
assert 'release-deploy' in workflow['concurrency']['group']
print('PASS: 실제 YAML의 배포 의존성·빌드 실패 차단·오라클 분리·게이트 실패 원복 조건')
PY
count=$((count + 1))
echo "${count}개 맥미니 Release 회귀 검사 통과"
