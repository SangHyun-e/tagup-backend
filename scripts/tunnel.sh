#!/usr/bin/env bash
#
# 로컬 백엔드를 Cloudflare Quick Tunnel로 외부에 공개한다.
#
#   ./scripts/tunnel.sh
#
# 하는 일:
#   1. local-tunnel 프로파일로 서버 기동 (H2 파일 DB, H2 콘솔·Swagger OFF, Firebase 필수)
#   2. cloudflared Quick Tunnel 연결 → https://xxx.trycloudflare.com 발급
#   3. 발급된 주소를 tagup-frontend/.env.local 의 EXPO_PUBLIC_API_BASE_URL 에 자동 기록
#   4. Ctrl+C 시 서버·터널 함께 종료
#
# Quick Tunnel 주소는 실행할 때마다 바뀐다. 고정 주소가 필요해지면
# (= 앱스토어 출시 시점) 도메인을 사서 Named Tunnel로 전환하면 된다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_ENV="$(dirname "$BACKEND_DIR")/tagup-frontend/.env.local"
LOG_DIR="$BACKEND_DIR/logs"
SERVER_LOG="$LOG_DIR/tunnel-server.log"
TUNNEL_LOG="$LOG_DIR/tunnel-cloudflared.log"
PORT=8080

mkdir -p "$LOG_DIR"

SERVER_PID=""
TUNNEL_PID=""

cleanup() {
  echo ""
  echo "▸ 정리 중..."
  [[ -n "$TUNNEL_PID" ]] && kill "$TUNNEL_PID" 2>/dev/null || true
  [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null || true
  # gradlew bootRun 은 자식 JVM 을 남기므로 포트 기준으로 한 번 더 정리
  sleep 1
  lsof -ti tcp:"$PORT" 2>/dev/null | xargs kill 2>/dev/null || true
  echo "▸ 종료됨. 데이터는 $BACKEND_DIR/data/ 에 남아 있습니다."
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------- 사전 점검
command -v cloudflared >/dev/null 2>&1 || {
  echo "✗ cloudflared 가 없습니다.  brew install cloudflared"; exit 1; }

# .env 가 있으면 Firebase 자격증명을 여기서 읽는다 (gitignore 대상)
if [[ -f "$BACKEND_DIR/.env" ]]; then
  set -a; source "$BACKEND_DIR/.env"; set +a
  echo "▸ .env 로드됨"
fi

if [[ -z "${FIREBASE_PROJECT_ID:-}" ]]; then
  cat <<'MSG'
✗ FIREBASE_PROJECT_ID 가 설정되지 않았습니다.

  터널 모드는 Firebase 인증이 필수입니다. 설정하지 않으면
  Authorization 헤더의 문자열이 그대로 uid 로 신뢰되어(로컬 개발 모드)
  공개된 주소에서 남의 계정 사칭이 가능해집니다.

  tagup-backend/.env 를 만들고 아래를 채우세요 (git 에 올라가지 않습니다):

    FIREBASE_PROJECT_ID=your-project-id
    FIREBASE_CREDENTIALS_PATH=/절대/경로/serviceAccountKey.json

  서비스 계정 키: Firebase 콘솔 > 프로젝트 설정 > 서비스 계정 > 새 비공개 키 생성
MSG
  exit 1
fi

if lsof -ti tcp:"$PORT" >/dev/null 2>&1; then
  echo "✗ 포트 $PORT 이 이미 사용 중입니다.  lsof -ti tcp:$PORT | xargs kill"; exit 1
fi

# ---------------------------------------------------------------- 서버 기동
echo "▸ 서버 기동 중 (프로파일: local-tunnel)..."
cd "$BACKEND_DIR"
./gradlew bootRun --args='--spring.profiles.active=local-tunnel' \
  > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!

for i in $(seq 1 120); do
  if curl -fsS "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; then
    echo "▸ 서버 기동 완료 (${i}초)"
    break
  fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "✗ 서버가 죽었습니다. 마지막 로그 40줄:"; tail -40 "$SERVER_LOG"; exit 1
  fi
  [[ $i -eq 120 ]] && { echo "✗ 120초 안에 기동하지 못했습니다:"; tail -40 "$SERVER_LOG"; exit 1; }
  sleep 1
done

# ---------------------------------------------------------------- 터널 연결
echo "▸ Cloudflare Tunnel 연결 중..."
: > "$TUNNEL_LOG"
cloudflared tunnel --url "http://localhost:$PORT" > "$TUNNEL_LOG" 2>&1 &
TUNNEL_PID=$!

TUNNEL_URL=""
for i in $(seq 1 60); do
  TUNNEL_URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$TUNNEL_LOG" | head -1 || true)
  [[ -n "$TUNNEL_URL" ]] && break
  if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
    echo "✗ 터널 연결 실패:"; tail -20 "$TUNNEL_LOG"; exit 1
  fi
  sleep 1
done

[[ -z "$TUNNEL_URL" ]] && { echo "✗ 60초 안에 터널 주소를 받지 못했습니다:"; tail -20 "$TUNNEL_LOG"; exit 1; }

# ------------------------------------------------- 프론트엔드 .env.local 갱신
if [[ -f "$FRONTEND_ENV" ]]; then
  cp "$FRONTEND_ENV" "$FRONTEND_ENV.bak"
  if grep -q '^EXPO_PUBLIC_API_BASE_URL=' "$FRONTEND_ENV"; then
    sed -i '' "s|^EXPO_PUBLIC_API_BASE_URL=.*|EXPO_PUBLIC_API_BASE_URL=$TUNNEL_URL|" "$FRONTEND_ENV"
  else
    printf '\nEXPO_PUBLIC_API_BASE_URL=%s\n' "$TUNNEL_URL" >> "$FRONTEND_ENV"
  fi
  ENV_NOTE="▸ tagup-frontend/.env.local 갱신됨 (백업: .env.local.bak)"
else
  ENV_NOTE="⚠ $FRONTEND_ENV 이 없어 자동 갱신을 건너뛰었습니다. 수동으로 넣으세요."
fi

cat <<EOF

  ┌──────────────────────────────────────────────────────────┐
     $TUNNEL_URL
  └──────────────────────────────────────────────────────────┘

$ENV_NOTE

  다음:
    1. Expo 를 재시작해야 .env 가 반영됩니다 →  npx expo start -c
    2. 친구는 같은 Expo 프로젝트를 열면 이 주소로 붙습니다

  로그:  tail -f $SERVER_LOG
         tail -f $TUNNEL_LOG

  Ctrl+C 로 서버와 터널을 함께 종료합니다.

EOF

wait "$TUNNEL_PID"
