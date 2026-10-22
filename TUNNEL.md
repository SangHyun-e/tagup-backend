# 로컬 서버 외부 공개 (Cloudflare Tunnel)

AWS 없이 로컬 백엔드를 인터넷에 공개해 실기기·친구 테스트를 하기 위한 문서.
**비용 0원**, 도메인·계정·신용카드 모두 불필요.

---

## 1. 원리

노트북은 공유기 뒤에 있어 외부에서 들어올 수 없다. Cloudflare Tunnel은
**반대 방향**으로 푼다 — `cloudflared`가 Cloudflare 엣지로 나가는 연결을
하나 열어 계속 유지하고, 외부 요청은 그 연결을 타고 내려온다.

```
[친구 폰] ──HTTPS──> [Cloudflare 엣지] ──상시 아웃바운드 터널──> [cloudflared] ──> localhost:8080
```

- 공유기 포트포워딩 불필요 (나가는 연결만 사용)
- 공인 IP 불필요
- HTTPS 자동 (Cloudflare 인증서)
- 집 IP가 노출되지 않음

---

## 2. 준비 (최초 1회)

### cloudflared 설치

```bash
brew install cloudflared
```

### Firebase 자격증명

터널 모드는 **Firebase 인증이 필수**다. 없으면 서버가 기동을 거부한다.
이유는 아래 [보안](#4-보안-왜-local-프로파일을-그대로-쓰면-안-되는가) 참고.

1. Firebase 콘솔 > 프로젝트 설정 > 서비스 계정 > **새 비공개 키 생성**
2. 내려받은 JSON을 안전한 경로에 둔다 (레포 밖 권장)
3. `tagup-backend/.env` 생성 — **gitignore 대상이라 커밋되지 않는다**

```bash
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_CREDENTIALS_PATH=/absolute/path/to/serviceAccountKey.json
```

---

## 3. 실행

```bash
./scripts/tunnel.sh
```

스크립트가 하는 일:

1. `local-tunnel` 프로파일로 서버 기동
2. `/actuator/health` 가 응답할 때까지 대기
3. Quick Tunnel 연결 → `https://xxx.trycloudflare.com` 발급
4. **그 주소가 바깥에서 실제로 열릴 때까지 확인**
5. 열린 주소를 `tagup-frontend/.env.local` 의 `EXPO_PUBLIC_API_BASE_URL` 에 자동 기록
6. [감시](#35-감시-자동-재연결) — 주소가 죽으면 자동으로 다시 연결
7. `Ctrl+C` 시 서버·터널 함께 종료

발급된 주소를 앱에 반영하려면 **Expo를 캐시 비우고 재시작**해야 한다.

```bash
npx expo start -c
```

---

## 3.5 감시 (자동 재연결)

### 왜 필요한가

터널이 죽는 방식이 고약하다. `cloudflared` 프로세스는 **살아 있는 채로** 연결만 잃고
`Retrying connection in up to 1m4s` 를 영원히 반복한다. `ps` 로 보면 멀쩡하고,
포트도 열려 있고, 로그도 계속 쌓인다. 그런데 바깥에서는 아무도 접속할 수 없다.

프로세스를 지켜보는 감시로는 이걸 잡을 수 없다. 실제로 두 번 당했다.

| 날짜 | 증상 | 잃은 시간 |
|---|---|---|
| 2026-10-06 | 02:19 연결 끊김, 프로세스는 생존 | 하루 |
| 2026-10-07 | 07:57 연결 끊김, 프로세스는 13일간 생존 | 13일 |

그래서 감시는 **공개 주소로 실제 요청을 보내 본다.** Cloudflare 엣지를 한 바퀴 돌아
돌아오는지가 유일하게 믿을 수 있는 신호다.

### 동작

```
30초마다  →  로컬 /actuator/health      (서버가 살았나)
          →  공개주소 /actuator/health   (바깥에서 열리나)

3번 연속 실패  →  cloudflared 종료 → 새로 연결
               →  새 주소가 열릴 때까지 확인 (최대 150초)
               →  .env.local 갱신 + 알림
```

- **로컬이 죽었으면 터널은 건드리지 않는다.** 다시 연결해도 고쳐지지 않는 문제라,
  멀쩡한 터널만 버리게 된다. 대신 알림을 띄운다.
- **새 주소는 열리는 걸 확인한 뒤에 기록한다.** Quick Tunnel 주소는 DNS에 퍼지는 데
  30~90초가 걸린다(측정값: 80초). 이걸 기다리지 않으면 멀쩡한 터널을 계속 죽이는
  무한 재연결에 빠진다 — 만들다가 실제로 3분에 4번 갈아치웠다.
- 재연결이 거푸 실패하면(=인터넷 자체가 끊김) 대기 시간을 30초→600초까지 늘린다.

### 조절

| 환경변수 | 기본값 | 뜻 |
|---|---|---|
| `WATCHDOG_INTERVAL` | 30 | 확인 주기(초) |
| `WATCHDOG_FAIL_THRESHOLD` | 3 | 몇 번 연속 실패하면 다시 연결할지 |
| `WATCHDOG_READY_TIMEOUT` | 150 | 새 주소가 열리기를 기다리는 최대 시간(초) |

> **이름 앞에 `TUNNEL_` 을 붙이지 말 것.** `cloudflared` 가 `TUNNEL_*` 환경변수를
> 제 설정으로 읽어간다. `TUNNEL_GRACE_PERIOD=30` 을 쓰다가 당했는데, cloudflared 는
> **종료코드 0에 아무 메시지도 없이** 즉시 죽는다. 로그가 비어 원인을 찾기 고약하다.
> 스크립트가 시작할 때 `TUNNEL_*` 이 있으면 경고해 준다.

### 남는 것

- **주소는 여전히 바뀐다.** 재연결될 때마다 앱은 `npx expo start -c` 로 다시 켜야 한다.
  이건 Quick Tunnel 의 한계라 감시로는 못 고친다. 고정 주소가 필요하면 Named Tunnel
  (아래 5번).
- 감시는 스크립트가 떠 있는 동안만 돈다. 노트북을 껐다 켜면 다시 실행해야 한다.

### 상태 읽기

`logs/tunnel-status.json` 에 한 줄로 남는다. 아침 리포트 같은 데서 읽어가면 된다.

```json
{
  "url": "https://xxx.trycloudflare.com",
  "health": "ok",
  "lastOkAt": "2026-10-22 10:03:58",
  "restartCount": 1,
  "startedAt": "2026-10-22 10:00:30",
  "updatedAt": "2026-10-22 10:03:58"
}
```

`health` 값: `ok` / `unreachable` / `local-unhealthy` / `reconnect-failed` / `starting`

재연결 이력은 `logs/tunnel-restarts.log` 에 쌓인다 — 며칠에 몇 번 끊기는지가 여기 보인다.

---

## 4. 보안: 왜 `local` 프로파일을 그대로 쓰면 안 되는가

`local`은 "내 노트북에서만 접속한다"는 전제로 짜여 있다. 터널을 열면 그 전제가
깨지므로 `local-tunnel` 프로파일을 따로 둔다.

| 항목 | `local` | `local-tunnel` | 이유 |
|---|---|---|---|
| H2 콘솔 | 켜짐 (`/h2-console`) | **꺼짐** | `sa` / 무암호. 주소만 알면 DB 전체 조회·삭제 가능 |
| Swagger | 켜짐 | **꺼짐** | 공개 주소에서 API 목록을 노출할 이유가 없음 |
| Firebase 인증 | 선택 | **필수** | 아래 참고 |
| DB | 인메모리 + `create-drop` | **파일 + `update`** | 재시작해도 계정·방·배팅 유지 |
| `/api/v1/admin/**` | 등록됨 | **미등록** | 컨트롤러가 `@Profile("local")` |

### Firebase를 필수로 만든 이유

`FirebaseTokenFilter`는 `FirebaseApp`이 초기화되지 않으면 **로컬 개발 모드**로
떨어져, `Authorization` 헤더의 문자열을 그대로 uid로 신뢰한다. 로컬에서는
편의지만 공개된 주소에서는 uid만 알면 사칭이 된다.

따라서 `local-tunnel` 프로파일은 `tagup.security.require-firebase=true` 를 켜서,
Firebase 초기화에 실패하면 **조용히 인증 없이 뜨는 대신 기동을 중단**한다.

### 그래도 남는 것

Quick Tunnel 주소는 랜덤이라 사실상 아무도 찾지 못하지만, 이것은 자물쇠가
아니라 운이다. **테스트용으로만 쓰고, 끝나면 `Ctrl+C`로 닫을 것.**

---

## 5. Quick Tunnel의 한계와 다음 단계

주소가 **실행할 때마다 바뀐다.** 개발 중에는 스크립트가 `.env.local`을 자동으로
고쳐주므로 문제되지 않지만, 앱스토어에 올리는 순간 이야기가 달라진다 —
스토어 앱은 API 주소가 바이너리에 박혀 나가므로 주소가 바뀌면 전부 먹통이 된다.

| 단계 | 방식 | 비용 |
|---|---|---|
| 지금 (Expo Go 테스트) | Quick Tunnel | 0원 |
| 스토어 출시 | 도메인 + Named Tunnel 또는 EC2 | 도메인 연 1~2만원 |

Named Tunnel로 넘어가려면 도메인이 Cloudflare 네임서버에 등록돼 있어야 한다.
그 시점에 `cloudflared tunnel login` → `create` → `route dns` 순으로 전환한다.

---

## 6. 문제 해결

| 증상 | 원인 / 조치 |
|---|---|
| `FIREBASE_PROJECT_ID 가 설정되지 않았습니다` | `.env` 생성 (2번 항목) |
| `포트 8080 이 이미 사용 중` | `lsof -ti tcp:8080 \| xargs kill` |
| 앱이 여전히 옛 주소로 붙음 | Expo 캐시 — `npx expo start -c`. Expo Go 를 완전히 종료했다 켜야 할 때도 있다 |
| 주소가 저절로 바뀌어 있음 | 감시가 끊긴 터널을 다시 연결한 것. `logs/tunnel-restarts.log` 확인 |
| `cloudflared` 가 아무 말 없이 즉시 죽음 | `TUNNEL_*` 환경변수 충돌 — [3.5 감시](#35-감시-자동-재연결) 참고 |
| 주소가 계속 바뀜(몇 분에 한 번) | `WATCHDOG_READY_TIMEOUT` 이 DNS 전파보다 짧음. 기본값(150초)으로 되돌릴 것 |
| 서버는 뜨는데 앱에서 401 | Firebase 프로젝트가 앱(`.env.local`)과 서버(`.env`)에서 서로 다른지 확인 |
| 데이터를 초기화하고 싶음 | `rm -rf data/` 후 재실행 |
