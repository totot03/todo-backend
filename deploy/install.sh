#!/usr/bin/env bash
# deploy/install.sh — todo-backend 최초 1회 설치 스크립트 (Amazon Linux 2023, systemd)
#
# 전제:
#   - sudo로 실행한다: sudo bash install.sh
#   - 저장소의 deploy/ 디렉토리 레이아웃을 그대로 유지한 채 실행되어야 한다
#     (deploy/systemd/todolist.service, deploy/systemd/todolist.conf를 상대경로로 찾는다).
#     로컬 deploy/ 디렉토리 전체를 WinSCP로 /home/ec2-user/deploy/ 에 업로드한 뒤 실행한다.
#   - jar 파일(todo-backend-*.jar, *.original 제외)과 todolist.env는 미리 WinSCP로
#     /home/ec2-user/ 에 업로드되어 있어야 한다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR=/etc/todolist
SERVICE_NAME=todolist
HOME_DIR=/home/ec2-user
HEALTH_URL="http://127.0.0.1:8080/api/health"

log() { echo "[install] $*"; }
err() { echo "[install][ERROR] $*" >&2; }

if [[ "${EUID}" -ne 0 ]]; then
  err "root 권한이 필요합니다. sudo bash install.sh 로 실행하세요."
  exit 1
fi

# 1) Java 21 설치 확인
if ! command -v java >/dev/null 2>&1; then
  log "Java가 설치되어 있지 않습니다. Amazon Corretto 21을 설치합니다."
  dnf install -y java-21-amazon-corretto-headless
else
  log "Java가 이미 설치되어 있습니다: $(java -version 2>&1 | head -n1)"
fi

if ! java -version 2>&1 | head -n1 | grep -q '"21'; then
  err "설치된 Java 버전이 21이 아닙니다. dnf install -y java-21-amazon-corretto-headless 를 확인하세요."
  exit 1
fi

# 2) 앱 디렉토리 준비
log "디렉토리를 준비합니다: ${APP_DIR}"
mkdir -p "${APP_DIR}"
chown root:ec2-user "${APP_DIR}"
chmod 750 "${APP_DIR}"

# 3) jar 배치 (버전이 붙은 파일명을 고정된 app.jar로. .original은 thin jar이므로 제외)
mapfile -t CANDIDATES < <(find "${HOME_DIR}" -maxdepth 1 -type f -name 'todo-backend-*.jar' ! -name '*.original' -printf '%T@ %p\n' | sort -rn | awk '{print $2}')
if [[ "${#CANDIDATES[@]}" -eq 0 ]]; then
  err "${HOME_DIR}에서 todo-backend-*.jar(.original 제외)를 찾지 못했습니다. WinSCP로 먼저 업로드하세요."
  exit 1
fi
JAR_SRC="${CANDIDATES[0]}"
[[ "${#CANDIDATES[@]}" -gt 1 ]] && log "경고: jar가 여러 개 있습니다. 가장 최근 파일 사용: ${JAR_SRC}"

log "jar 배치: ${JAR_SRC} -> ${APP_DIR}/app.jar"
mv "${JAR_SRC}" "${APP_DIR}/app.jar.new"
mv "${APP_DIR}/app.jar.new" "${APP_DIR}/app.jar"   # 같은 파일시스템 내 rename = 원자적 교체
chown ec2-user:ec2-user "${APP_DIR}/app.jar"
chmod 640 "${APP_DIR}/app.jar"

# 4) todolist.env 배치 (비밀값 포함 — 이동 후 홈 디렉토리 원본은 남기지 않는다)
ENV_SRC="${HOME_DIR}/todolist.env"
if [[ ! -f "${ENV_SRC}" ]]; then
  err "${ENV_SRC} 가 없습니다. deploy/env/todolist.env.example 을 참고해 값을 채운 뒤 WinSCP로 업로드하세요."
  exit 1
fi
log "todolist.env 배치: ${ENV_SRC} -> ${APP_DIR}/todolist.env"
mv "${ENV_SRC}" "${APP_DIR}/todolist.env"
chown root:ec2-user "${APP_DIR}/todolist.env"
chmod 640 "${APP_DIR}/todolist.env"

# 5) todolist.conf 배치 (JVM 옵션, 비밀값 없음 — 이미 있으면 손대지 않는다)
if [[ -f "${APP_DIR}/todolist.conf" ]]; then
  log "${APP_DIR}/todolist.conf 가 이미 존재합니다. 덮어쓰지 않습니다."
else
  log "todolist.conf 배치: ${SCRIPT_DIR}/systemd/todolist.conf -> ${APP_DIR}/todolist.conf"
  cp "${SCRIPT_DIR}/systemd/todolist.conf" "${APP_DIR}/todolist.conf"
  chown root:ec2-user "${APP_DIR}/todolist.conf"
  chmod 644 "${APP_DIR}/todolist.conf"
fi

# 6) systemd 유닛 설치 (버전관리 대상이므로 매번 최신으로 덮어쓴다)
log "systemd 유닛 설치: ${SCRIPT_DIR}/systemd/todolist.service -> /etc/systemd/system/${SERVICE_NAME}.service"
cp "${SCRIPT_DIR}/systemd/todolist.service" "/etc/systemd/system/${SERVICE_NAME}.service"
chmod 644 "/etc/systemd/system/${SERVICE_NAME}.service"

systemctl daemon-reload
systemctl enable --now "${SERVICE_NAME}"

# 7) 헬스체크 (JVM 기동 + DB 연결까지 감안해 최대 60초 재시도)
log "헬스체크를 시작합니다: ${HEALTH_URL}"
for i in $(seq 1 20); do
  if curl -fsS "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
    log "헬스체크 성공. 배포가 정상 완료되었습니다."
    exit 0
  fi
  sleep 3
done

err "헬스체크 실패 (60초 초과). 서비스 상태와 로그를 확인하세요:"
err "  systemctl status ${SERVICE_NAME}"
err "  journalctl -u ${SERVICE_NAME} -n 100 --no-pager"
exit 1
