# 타석 배팅 — FE 연동 계약

> BE Sprint 4 완료분. **FE 화면만 붙이면 동작한다.**
> 이 프로젝트는 FE-BE 계약 불일치가 4번 있었으므로, 추측하지 말고 이 문서를 기준으로 한다.
> BE 쪽 계약은 `ApiContractTest`가 고정하고 있어 필드가 바뀌면 테스트가 깨진다.

---

## 1. 흐름

```
① 서버가 타석 시작을 감지 → 채팅방에 LIVE 메시지 발송
      ⚾ 7회초 박민우 타석 (투수 홍건희)
                ↓  30초 안에
② 유저 A: 아웃/세이프 중 하나에 배팅          POST .../bets/at-bat
                ↓
③ 유저 B: 콜! (자동으로 반대편)                PUT /bets/{id}/accept
                ↓
④ 타석 종료 → 서버가 즉시 정산 → 채팅에 결과
      🟢 박민우 세이프 · 2점!
```

**어느 타석인지는 요청에 담지 않는다.** 서버가 정한다 — 화면이 낡았을 때 엉뚱한 타석에
걸리거나, 결과를 본 뒤 과거 타석을 지정하는 것을 막기 위해서다.

---

## 2. 타석 배팅 생성

```
POST /api/v1/rooms/{roomId}/bets/at-bat
Authorization: Bearer <Firebase ID Token>
```

```json
{ "betOnResult": "OUT", "content": "커피 한 잔" }
```

| 필드 | 타입 | 비고 |
|---|---|---|
| `betOnResult` | `"OUT"` \| `"SAFE"` | **필수.** `UNKNOWN`은 정산 결과값이라 보내면 400 |
| `content` | string (≤100) | 필수 |

이닝·타자·경기 ID는 **보내지 않는다.**

### 실패 응답

전부 `400`이고 `{ "success": false, "message": "..." }` 형태다. 문구로 분기하지 말고
상황별 안내만 띄우면 된다.

| 상황 | 메시지 |
|---|---|
| 더그아웃이 보는 경기가 없음 | 경기를 지정하거나 더그아웃의 관전 경기를 먼저 설정해주세요. |
| 경기가 진행 중이 아님 | 진행 중인 경기에서만 타석 배팅을 걸 수 있습니다. |
| 지금 타석을 못 읽음 | 지금 진행 중인 타석이 없습니다. |
| **30초 창이 지남** | 이 타석은 배팅이 마감됐습니다. 다음 타석을 기다려주세요. |
| `UNKNOWN` 전송 | 아웃 또는 세이프만 선택할 수 있습니다. |

---

## 3. 콜 (기존 API 그대로)

```
PUT /api/v1/bets/{betId}/accept
```

콜한 사람은 **자동으로 반대편**에 선다. 팀이나 결과를 고르지 않는다.
제안자가 `OUT`에 걸었으면 콜한 사람은 `SAFE`다.

---

## 4. `BetResponse` 변경점

**기존 필드는 그대로다.** 아래 둘이 추가됐다.

```jsonc
{
  "id": 3,
  "type": "AT_BAT",          // ← 추가: "WIN_LOSE" | "AT_BAT"
  "proposer": { "id": 1, "nickname": "철수" },
  "receiver": { "id": 2, "nickname": "영희" },

  "betOnTeamId": null,        // 타석 배팅에서는 null
  "betOnTeam": null,          // 타석 배팅에서는 null  ← 기존 코드가 여기서 터질 수 있음
  "atBat": {                  // ← 추가: 승패 배팅에서는 null
    "inning": 7,
    "half": "초",             // "초" | "말"
    "batter": "박민우",
    "betOnResult": "OUT"
  },

  "content": "커피 한 잔",
  "status": "ACCEPTED",       // PENDING | ACCEPTED | FINISHED | CANCELLED
  "proposerResult": null,     // WIN | LOSE | DRAW
  "game": { "id": 683, "homeTeam": "두산", "awayTeam": "NC", "gameDate": "2026-09-15" },
  "createdAt": "2026-09-15T19:12:03"
}
```

> ⚠️ **`betOnTeam`이 null일 수 있다.** 승패 배팅만 있던 시절 코드가
> `bet.betOnTeam.shortName`을 그냥 읽고 있으면 타석 배팅에서 터진다. `type`으로 분기할 것.

### `proposerResult`

| 값 | 뜻 |
|---|---|
| `WIN` | 제안자가 건 결과가 맞았다 |
| `LOSE` | 틀렸다 |
| `DRAW` | **무효** — 결과를 판정할 수 없었다 (아래 참고) |

---

## 5. 채팅 메시지 (Firestore)

기존 경로 그대로: `rooms/{chatKey}/messages`

```jsonc
{
  "roomId": "10",
  "senderId": "system",        // 기존 시스템 메시지와 동일
  "senderNickname": "태그업",
  "content": "⚾ 7회초 박민우 타석 (투수 홍건희)",
  "type": "LIVE",              // ← 신규 (기존 배팅 안내는 "BET")
  "liveKind": "AT_BAT_START",  // AT_BAT_START | AT_BAT_RESULT
  "createdAt": <serverTimestamp>
}
```

현재 FE는 `senderId === 'system' || type === 'BET'`을 시스템 카드로 그리므로
**이대로도 화면에 보인다.** `liveKind`로 분기하면 더 낫다:

- `AT_BAT_START` → **여기에 아웃/세이프 배팅 버튼을 붙인다.** 30초 뒤 비활성화
- `AT_BAT_RESULT` → 결과 표시만

### 문구 예시

```
🔄 투수 교체 — 홍건희
⚾ 7회초 박민우 타석 (투수 홍건희)
🟢 박민우 세이프 · 2점!
🔴 오스틴 아웃 · 이닝 종료
❔ 손아섭 결과 확인 불가
```

---

## 6. 알아둬야 할 제약

**아웃 / 세이프 2지선다뿐이다.** KBO 응답으로는 안타·볼넷·실책·뜬공·땅볼을 구분할 수 없다.
UI에 "안타 맞힐래?" 같은 문구를 쓰면 안 된다.

**배팅 창은 30초다.** 타석 평균은 약 2분이지만, 폴링(15초)만큼 *결과는 났는데 서버는
모르는* 구간이 생겨 정보 비대칭이 발생한다. 그래서 초반 30초로 제한한다.
타이머는 **LIVE 메시지를 받은 시각**부터 세면 대체로 맞는다 (서버 기준과 근소한 차이).

**약 6%는 판정 불가다.** 그 타석 배팅은 `proposerResult: "DRAW"`로 무효 처리된다.
채팅에 `❔ ... 결과 확인 불가`가 함께 뜨므로, 무효 사유를 그 메시지로 설명할 수 있다.

**중계 메시지가 많다.** 타석당 2건 × 경기당 70~100타석 = 한 경기 140~200건.
대화를 밀어내면 BE에서 `tagup.live.relay-enabled`로 줄인다 — FE에서 필터링하지 말고 알려줄 것.

---

## 7. 연동 확인용 curl

```bash
BASE=https://<터널주소>
TOKEN=<Firebase ID Token>

# 1) 방의 관전 경기 확인 (watchingGame 이 있어야 한다)
curl -s "$BASE/api/v1/rooms/10" -H "Authorization: Bearer $TOKEN"

# 2) 타석 배팅
curl -s -X POST "$BASE/api/v1/rooms/10/bets/at-bat" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"betOnResult":"OUT","content":"커피 한 잔"}'

# 3) 다른 계정으로 콜
curl -s -X PUT "$BASE/api/v1/bets/3/accept" -H "Authorization: Bearer $TOKEN2"
```
