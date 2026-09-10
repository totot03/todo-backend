#!/usr/bin/env bash
# deploy/redeploy.sh — todo-backend 재배포 스크립트 (Amazon Linux 2023, systemd)
#
# 전제: sudo로 실행. install.sh로 최초 설치가 끝난 상태에서만 사용한다.
# 새 jar(및 선택적으로 새 todolist.env)를 /home/ec2-user 에 WinSCP로 올린 뒤 실행한다.
# 기존 app.jar/todolist.env는 .bak으로 백업하며, 재시작 후 헬스체크 실패 시
# 자동으로 .bak을 복원하고 재시작을 한 번 더 시도한다.
# 단일 인스턴스이므로 완전 무중단은 아니며, 재시작 수초~수십초의 다운타임이 발생한다.
set -euo pipefail

APP_DIR=/etc/todolist
SERVICE_NAME=todolist
HOME_DIR=/home/ec2-user
HEALTH_URL="http://127.0.0.1:8080/api/health"

log() { echo "[redeploy] $*"; }
err() { echo "[redeploy][ERROR] $*" >&2; }

health_check() {
  for i in $(seq 1 20); do
    if curl -fsS "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 3
  done
  return 1
}

if [[ "${EUID}" -ne 0 ]]; then
  err "root 권한이 필요합니다. sudo bash redeploy.sh 로 실행하세요."
  exit 1
fi

if [[ ! -f "${APP_DIR}/app.jar" ]]; then
  err "${APP_DIR}/app.jar 가 없습니다. 최초 설치는 install.sh를 사용하세요."
  exit 1
fi

# 1) 새 jar 탐색
mapfile -t CANDIDATES < <(find "${HOME_DIR}" -maxdepth 1 -type f -name 'todo-backend-*.jar' ! -name '*.original' -printf '%T@ %p\n' | sort -rn | awk '{print $2}')
if [[ "${#CANDIDATES[@]}" -eq 0 ]]; then
  err "${HOME_DIR}에서 새 jar를 찾지 못했습니다. WinSCP로 먼저 업로드하세요."
  exit 1
fi
JAR_SRC="${CANDIDATES[0]}"
[[ "${#CANDIDATES[@]}" -gt 1 ]] && log "경고: jar가 여러 개 있습니다. 가장 최근 파일 사용: ${JAR_SRC}"

# 2) 기존 app.jar 백업 (직전 1세대만 보관)
log "기존 app.jar 백업: ${APP_DIR}/app.jar -> ${APP_DIR}/app.jar.bak"
cp -f "${APP_DIR}/app.jar" "${APP_DIR}/app.jar.bak"

# 3) 새 jar 반영 (같은 파일시스템 내 rename = 원자적 교체)
log "새 jar 반영: ${JAR_SRC} -> ${APP_DIR}/app.jar"
mv "${JAR_SRC}" "${APP_DIR}/app.jar.new"
mv "${APP_DIR}/app.jar.new" "${APP_DIR}/app.jar"
chown ec2-user:ec2-user "${APP_DIR}/app.jar"
chmod 640 "${APP_DIR}/app.jar"

# 4) todolist.env 갱신 (선택 사항 — 비밀값 회전 등으로 새로 올렸을 때만)
ENV_SRC="${HOME_DIR}/todolist.env"
if [[ -f "${ENV_SRC}" ]]; then
  log "새 todolist.env 발견 — 갱신합니다."
  cp -f "${APP_DIR}/todolist.env" "${APP_DIR}/todolist.env.bak"
  mv "${ENV_SRC}" "${APP_DIR}/todolist.env"
  chown root:ec2-user "${APP_DIR}/todolist.env"
  chmod 640 "${APP_DIR}/todolist.env"
fi

# 5) 재시작 + 헬스체크
log "서비스를 재시작합니다."
systemctl restart "${SERVICE_NAME}"

if health_check; then
  log "헬스체크 성공. 배포가 정상 완료되었습니다."
  exit 0
fi

# 6) 실패 시 자동 롤백
err "헬스체크 실패. 이전 버전으로 롤백을 시도합니다."
cp -f "${APP_DIR}/app.jar.bak" "${APP_DIR}/app.jar"
if [[ -f "${APP_DIR}/todolist.env.bak" ]]; then
  cp -f "${APP_DIR}/todolist.env.bak" "${APP_DIR}/todolist.env"
fi
systemctl restart "${SERVICE_NAME}"

if health_check; then
  err "롤백 완료: 이전 버전으로 정상 복구되었습니다. 새 jar의 문제를 확인한 뒤 다시 배포하세요."
  err "  journalctl -u ${SERVICE_NAME} -n 200 --no-pager"
  exit 1
fi

err "롤백 후에도 헬스체크에 실패했습니다. 수동 개입이 필요합니다:"
err "  systemctl status ${SERVICE_NAME}"
err "  journalctl -u ${SERVICE_NAME} -n 200 --no-pager"
err "  (DB 연결 / RDS 보안그룹 / todolist.env 값 등을 확인하세요)"
exit 1
