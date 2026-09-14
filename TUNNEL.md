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
4. 그 주소를 `tagup-frontend/.env.local` 의 `EXPO_PUBLIC_API_BASE_URL` 에 자동 기록
5. `Ctrl+C` 시 서버·터널 함께 종료

발급된 주소를 앱에 반영하려면 **Expo를 캐시 비우고 재시작**해야 한다.

```bash
npx expo start -c
```

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
| 앱이 여전히 옛 주소로 붙음 | Expo 캐시 — `npx expo start -c` |
| 서버는 뜨는데 앱에서 401 | Firebase 프로젝트가 앱(`.env.local`)과 서버(`.env`)에서 서로 다른지 확인 |
| 데이터를 초기화하고 싶음 | `rm -rf data/` 후 재실행 |
