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
#   4. 감시(watchdog) — 공개 주소가 죽으면 터널을 자동으로 다시 연결
#   5. Ctrl+C 시 서버·터널 함께 종료
#
# Quick Tunnel 주소는 실행할 때마다 바뀐다. 고정 주소가 필요해지면
# (= 앱스토어 출시 시점) 도메인을 사서 Named Tunnel로 전환하면 된다.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_ENV="$(dirname "$BACKEND_DIR")/tagup-frontend/.env.local"
LOG_DIR="$BACKEND_DIR/logs"
# 서버 로그는 이어 쓴다(>>). 덮어쓰면 재시작 한 번에 지난 경기 기록이 통째로 사라진다 —
# 2026-09-09 실경기 로그(타석 311건)를 그렇게 잃었다.
SERVER_LOG="$LOG_DIR/tunnel-server.log"
# 터널 로그는 매 실행마다 비운다. 발급 주소를 여기서 읽어내는데, 남겨두면 옛 주소를 집어간다.
TUNNEL_LOG="$LOG_DIR/tunnel-cloudflared.log"
# 재연결 기록은 이어 쓴다. 며칠에 몇 번 끊겼는지가 여기 남아야 패턴이 보인다.
RESTART_LOG="$LOG_DIR/tunnel-restarts.log"
# 바깥(아침 리포트 등)에서 터널 상태를 한 줄로 읽어갈 수 있게 남긴다.
STATUS_FILE="$LOG_DIR/tunnel-status.json"
PORT=8080

# ── 감시 설정 ─────────────────────────────────────────────────────────
#
# 이름 앞에 TUNNEL_ 을 붙이지 말 것. cloudflared 가 TUNNEL_* 환경변수를 제 설정으로
# 읽어간다. 실제로 TUNNEL_GRACE_PERIOD 를 쓰다가 당했는데, cloudflared 는
# **종료코드 0에 아무 메시지도 없이** 즉시 죽는다 — 로그가 비어 원인을 찾기가 고약하다.
#
# 공개 주소를 얼마나 자주 두드려 볼지
CHECK_INTERVAL="${WATCHDOG_INTERVAL:-30}"
# 연속 몇 번 실패해야 죽은 것으로 볼지. 한 번 실패는 흔하다 — 와이파이가 잠깐
# 끊기거나 Cloudflare 엣지가 연결을 바꾸는 중일 수 있다. 3번(약 90초) 연속일 때만 손댄다.
FAIL_THRESHOLD="${WATCHDOG_FAIL_THRESHOLD:-3}"
# 새로 받은 주소가 바깥에서 실제로 열릴 때까지 기다려 주는 시간.
# Quick Tunnel 주소는 DNS에 퍼지는 데 30~60초가 걸린다. 이걸 기다리지 않고 바로
# 감시를 시작하면 **멀쩡한 터널을 계속 죽이는 무한 재연결**에 빠진다 —
# 실제로 2026-10-22 테스트에서 3분에 4번 갈아치웠다.
READY_TIMEOUT="${WATCHDOG_READY_TIMEOUT:-150}"
# 재연결이 거푸 실패하면(=인터넷 자체가 끊김) 쉬는 시간을 늘린다
BACKOFF_MIN=30
BACKOFF_MAX=600

mkdir -p "$LOG_DIR"

SERVER_PID=""
TUNNEL_PID=""
TUNNEL_URL=""
RESTART_COUNT=0

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

# .env 는 set -a 로 읽혀 전부 export 된다. 거기에 TUNNEL_* 이 섞여 있으면
# cloudflared 가 그걸 제 설정으로 집어삼키고 조용히 죽는다. 미리 알려준다.
conflicting="$(env | grep -oE '^TUNNEL_[A-Z_]+' | tr '\n' ' ' || true)"
if [[ -n "${conflicting// /}" ]]; then
  echo "⚠ TUNNEL_* 환경변수가 설정돼 있습니다: $conflicting"
  echo "  cloudflared 가 이걸 제 설정으로 읽어 아무 말 없이 죽을 수 있습니다. 이름을 바꾸세요."
fi

if lsof -ti tcp:"$PORT" >/dev/null 2>&1; then
  echo "✗ 포트 $PORT 이 이미 사용 중입니다.  lsof -ti tcp:$PORT | xargs kill"; exit 1
fi

# ---------------------------------------------------------------- 서버 기동
echo "▸ 서버 기동 중 (프로파일: local-tunnel)..."
cd "$BACKEND_DIR"
printf '\n===== 기동 %s =====\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$SERVER_LOG"
./gradlew bootRun --args='--spring.profiles.active=local-tunnel' \
  >> "$SERVER_LOG" 2>&1 &
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

# ---------------------------------------------------------------- 터널 함수

now() { date '+%Y-%m-%d %H:%M:%S'; }

# cloudflared 를 띄우고 발급된 주소를 TUNNEL_URL 에 담는다. 실패하면 1을 돌려준다.
start_tunnel() {
  # 로그를 비우고 시작한다. 남겨두면 아래 grep 이 옛 주소를 집어간다.
  : > "$TUNNEL_LOG"
  cloudflared tunnel --url "http://localhost:$PORT" >> "$TUNNEL_LOG" 2>&1 &
  TUNNEL_PID=$!

  TUNNEL_URL=""
  for _ in $(seq 1 60); do
    TUNNEL_URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$TUNNEL_LOG" | head -1 || true)
    [[ -n "$TUNNEL_URL" ]] && break
    if ! kill -0 "$TUNNEL_PID" 2>/dev/null; then
      TUNNEL_PID=""
      return 1
    fi
    sleep 1
  done
  [[ -n "$TUNNEL_URL" ]]
}

stop_tunnel() {
  [[ -n "$TUNNEL_PID" ]] || return 0
  kill "$TUNNEL_PID" 2>/dev/null || true
  # 얌전히 안 죽으면 확실히 죽인다. 멈춰(STOP) 있는 프로세스는 TERM 을 받지 못해
  # 그대로 남고, 그러면 다음 cloudflared 와 둘이 남아 포트를 두고 엉킨다.
  for _ in $(seq 1 5); do
    kill -0 "$TUNNEL_PID" 2>/dev/null || break
    sleep 1
  done
  kill -9 "$TUNNEL_PID" 2>/dev/null || true
  wait "$TUNNEL_PID" 2>/dev/null || true
  TUNNEL_PID=""
}

# 발급 주소를 앱 설정에 적는다
write_env_url() {
  if [[ ! -f "$FRONTEND_ENV" ]]; then
    echo "⚠ $FRONTEND_ENV 이 없어 자동 갱신을 건너뛰었습니다. 수동으로 넣으세요."
    return
  fi
  cp "$FRONTEND_ENV" "$FRONTEND_ENV.bak"
  if grep -q '^EXPO_PUBLIC_API_BASE_URL=' "$FRONTEND_ENV"; then
    sed -i '' "s|^EXPO_PUBLIC_API_BASE_URL=.*|EXPO_PUBLIC_API_BASE_URL=$TUNNEL_URL|" "$FRONTEND_ENV"
  else
    printf '\nEXPO_PUBLIC_API_BASE_URL=%s\n' "$TUNNEL_URL" >> "$FRONTEND_ENV"
  fi
}

# 바깥에서 읽어갈 상태 한 줄. 아침 리포트가 "터널 몇 시간째 멀쩡함 / 밤새 3번 끊김"을
# 말할 수 있으려면 이 파일이 있어야 한다.
write_status() {
  local health="$1" last_ok="$2"
  cat > "$STATUS_FILE" <<JSON
{
  "url": "$TUNNEL_URL",
  "health": "$health",
  "lastOkAt": "$last_ok",
  "restartCount": $RESTART_COUNT,
  "startedAt": "$STARTED_AT",
  "updatedAt": "$(now)"
}
JSON
}

# 주소가 바뀌면 앱은 Expo 를 다시 켜야 붙는다. 조용히 바꿔두면 사장님은
# "앱이 또 먹통"으로만 겪게 되므로 알림을 띄운다.
notify() {
  command -v osascript >/dev/null 2>&1 || return 0
  osascript -e "display notification \"$1\" with title \"태그업 터널\"" >/dev/null 2>&1 || true
}

# 공개 주소가 바깥에서 실제로 열리는지. 프로세스가 살아 있는지가 아니라
# **Cloudflare 엣지를 한 바퀴 돌아** 돌아오는지를 본다 — 죽을 때 프로세스는 멀쩡하다.
public_ok() {
  curl -fsS --max-time 10 "$TUNNEL_URL/actuator/health" >/dev/null 2>&1
}

local_ok() {
  curl -fsS --max-time 5 "http://localhost:$PORT/actuator/health" >/dev/null 2>&1
}

# 갓 만든 주소가 바깥에서 열릴 때까지 기다린다. 열리지 않으면 실패로 본다.
# 무작정 몇 초 쉬고 넘어가는 대신 **실제로 되는지**를 확인해야, 아직 퍼지지 않은
# 주소를 앱 설정에 적어두거나 멀쩡한 터널을 다시 죽이는 일이 없다.
wait_public_ready() {
  local waited=0
  while (( waited < READY_TIMEOUT )); do
    if public_ok; then
      echo "▸ [$(now)] 공개 주소 확인됨 (${waited}초)"
      return 0
    fi
    sleep 5
    waited=$(( waited + 5 ))
  done
  return 1
}

banner() {
  cat <<EOF

  ┌──────────────────────────────────────────────────────────┐
     $TUNNEL_URL
  └──────────────────────────────────────────────────────────┘

$1

  다음:
    1. Expo 를 재시작해야 .env 가 반영됩니다 →  npx expo start -c
    2. 친구는 같은 Expo 프로젝트를 열면 이 주소로 붙습니다

  감시: ${CHECK_INTERVAL}초마다 공개 주소를 확인하고, ${FAIL_THRESHOLD}번 연속 실패하면 다시 연결합니다.

  로그:  tail -f $SERVER_LOG
         tail -f $TUNNEL_LOG
         tail -f $RESTART_LOG

  Ctrl+C 로 서버와 터널을 함께 종료합니다.

EOF
}

# ---------------------------------------------------------------- 터널 연결
STARTED_AT="$(now)"
echo "▸ Cloudflare Tunnel 연결 중..."

# 첫 연결도 한 번에 안 될 수 있다. 방금 닫은 터널이 있으면 Cloudflare 가 잠깐 거절하는데,
# 그 한 번 때문에 서버까지 내려버리면 기껏 띄운 걸 다시 올려야 한다.
connected=0
for attempt in 1 2 3; do
  if start_tunnel; then
    echo "▸ 주소 발급됨 ($TUNNEL_URL). 바깥에서 열릴 때까지 기다립니다..."
    if wait_public_ready; then connected=1; break; fi
    echo "⚠ 주소는 받았지만 ${READY_TIMEOUT}초 안에 열리지 않았습니다 ($attempt/3)."
  else
    echo "⚠ 연결 실패 ($attempt/3)."
    tail -5 "$TUNNEL_LOG" 2>/dev/null || true
  fi
  stop_tunnel
  sleep 10
done
if (( connected == 0 )); then
  echo "✗ 터널 연결 실패:"; tail -20 "$TUNNEL_LOG"; exit 1
fi

write_env_url
banner "▸ tagup-frontend/.env.local 갱신됨 (백업: .env.local.bak)"
printf '[%s] 최초 연결 %s\n' "$(now)" "$TUNNEL_URL" >> "$RESTART_LOG"
write_status "starting" ""

# ---------------------------------------------------------------- 감시
#
# 터널이 죽는 방식이 고약하다. cloudflared 는 **살아 있는 채로** 연결만 잃고
# "Retrying connection in up to 1m4s" 를 영원히 반복한다. 프로세스를 지켜보는
# 감시는 아무것도 잡아내지 못한다 — 2026-10-07 에 13일, 10-06 에 하루를 그렇게 날렸다.
# 그래서 공개 주소로 실제 요청을 보내 본다.

fail_count=0
local_fail=0
server_warned=0
backoff=$BACKOFF_MIN
last_ok="$(now)"

while true; do
  # 서버가 살았는지는 프로세스가 아니라 health 로 본다.
  # gradlew bootRun 은 자식 JVM 을 따로 띄우므로, 래퍼가 먼저 빠져도 서버는 계속
  # 응답한다. 프로세스만 보고 죽었다고 판단하면 **멀쩡히 돌아가는 서버를 내리게 된다.**
  if ! local_ok; then
    local_fail=$(( local_fail + 1 ))
    echo "⚠ [$(now)] 로컬 서버가 응답하지 않습니다 ($local_fail). 터널은 그대로 둡니다."
    write_status "local-unhealthy" "$last_ok"
    # 터널을 다시 연결해도 고쳐지지 않는 문제라 손대지 않는다. 다만 오래 가면 알린다.
    if (( local_fail == FAIL_THRESHOLD && server_warned == 0 )); then
      server_warned=1
      notify "서버가 응답하지 않습니다. 로그를 확인하세요."
      printf '[%s] 서버 응답 없음\n' "$(now)" >> "$RESTART_LOG"
    fi
    sleep "$CHECK_INTERVAL"
    continue
  fi
  if (( local_fail > 0 )); then
    echo "▸ [$(now)] 로컬 서버 복구됨"
    local_fail=0
    server_warned=0
  fi

  if public_ok; then
    if (( fail_count > 0 )); then
      echo "▸ [$(now)] 공개 주소 복구됨"
    fi
    fail_count=0
    backoff=$BACKOFF_MIN
    last_ok="$(now)"
    write_status "ok" "$last_ok"
    sleep "$CHECK_INTERVAL"
    continue
  fi

  fail_count=$(( fail_count + 1 ))
  echo "⚠ [$(now)] 공개 주소 응답 없음 ($fail_count/$FAIL_THRESHOLD)"
  write_status "unreachable" "$last_ok"

  if (( fail_count < FAIL_THRESHOLD )); then
    sleep "$CHECK_INTERVAL"
    continue
  fi

  # ── 재연결 ──
  old_url="$TUNNEL_URL"
  echo "▸ [$(now)] 터널을 다시 연결합니다 (이전 주소: $old_url)"
  stop_tunnel

  if start_tunnel && wait_public_ready; then
    RESTART_COUNT=$(( RESTART_COUNT + 1 ))
    # 주소가 실제로 열리는 걸 확인한 다음에 적는다. 안 열리는 주소를 적어두면
    # 앱은 Expo 를 다시 켜고도 먹통이 된다.
    write_env_url
    printf '[%s] 재연결 #%d  %s → %s\n' "$(now)" "$RESTART_COUNT" "$old_url" "$TUNNEL_URL" >> "$RESTART_LOG"
    banner "▸ 주소가 바뀌었습니다. 앱에 반영하려면 Expo 를 다시 켜세요 (npx expo start -c)"
    notify "터널 주소가 바뀌었습니다. Expo를 다시 켜세요."
    fail_count=0
    backoff=$BACKOFF_MIN
    last_ok="$(now)"
    write_status "ok" "$last_ok"
  else
    # 주소를 못 받았다 = 인터넷이 없거나 Cloudflare 가 거절했다. 쉬었다 다시 시도한다.
    echo "✗ [$(now)] 재연결 실패. ${backoff}초 후 다시 시도합니다."
    printf '[%s] 재연결 실패 — %d초 대기\n' "$(now)" "$backoff" >> "$RESTART_LOG"
    write_status "reconnect-failed" "$last_ok"
    sleep "$backoff"
    backoff=$(( backoff * 2 ))
    (( backoff > BACKOFF_MAX )) && backoff=$BACKOFF_MAX
    fail_count=$(( FAIL_THRESHOLD - 1 ))
  fi
done
