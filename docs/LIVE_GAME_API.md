# 진행 중 경기 상태 API (FE 계약)

> 2026-10-01 신설. 홈 화면 문구·경기 카드, 채팅 헤더의 실시간 점수가 이 값을 쓴다.

## 왜 점수를 따로 주는가

KBO 일정 API는 **경기 중에 점수 칸을 `0vs0`으로 고정**해서 보낸다. 예전에 이 값을 믿었다가
경기 시작 3분 만에 "0:0 무승부"로 내기가 정산된 사고가 있었고, 그래서 크롤러는 **진행 중 경기의
점수를 버린다**. 따라서:

| 상황 | 점수를 어디서 읽나 |
|---|---|
| 예정 | 점수 없음 |
| **진행 중** | **`live.awayScore` / `live.homeScore`** |
| 종료 | `awayScore` / `homeScore` (기존 필드) |

`GameResponse.homeScore`는 진행 중에 `null`이다. 이걸 0으로 표시하면 안 된다.

## 1. 경기 목록·상세에 함께 내려간다

`GET /api/v1/games/today`, `GET /api/v1/games?date=`, `GET /api/v1/games/{gameId}`

```json
{
  "success": true,
  "data": [{
    "id": 712,
    "kboGameId": "20261001_삼성_두산",
    "status": "IN_PROGRESS",
    "homeScore": null,
    "awayScore": null,
    "inning": 7,
    "stadium": "잠실",
    "homeTeam": { "id": 2, "name": "두산 베어스", "shortName": "두산", "logoUrl": null },
    "awayTeam": { "id": 5, "name": "삼성 라이온즈", "shortName": "삼성", "logoUrl": null },
    "live": {
      "inning": 7,
      "half": "TOP",
      "awayScore": 3,
      "homeScore": 5,
      "out": 2,
      "ball": 1,
      "strike": 2,
      "bases": { "first": true, "second": true, "third": false },
      "batter": "박민우",
      "pitcher": "홍건희",
      "updatedAt": "2026-10-01T10:30:12Z"
    }
  }]
}
```

- `live`는 **진행 중이고 수집이 살아 있을 때만** 채워진다. 그 외에는 `null`이다.
  (목록·상세 응답 안에서는 `"live": null`로 내려온다. 아래 2번의 단일 조회와 다르니 주의)
- `/api/v1/games/upcoming`은 예정 경기만 담으므로 `live`가 항상 `null`이다.

## 2. 그 경기만 짧은 주기로 볼 때

`GET /api/v1/games/{gameId}/live` → `data`가 위 `live` 객체 그대로.

**상태가 없으면 `data` 키 자체가 빠진다.** 공통 응답 래퍼가 null 필드를 제외하기 때문이다:

```json
{ "success": true }
```

`data`가 `null`로 오는 게 아니라 **아예 없으므로**, 앱에서 `res.data?.live` 식으로 안전하게 읽어야 한다.

채팅 화면처럼 한 경기만 자주 갱신할 때 쓴다. 목록 전체를 다시 받을 필요가 없다.

## 3. `live`가 null인 경우를 반드시 처리할 것

세 가지가 전부 `null`로 온다. 앱에서 구분할 필요는 없고, **"지금 상태를 모른다"로 똑같이 그리면 된다.**

1. 경기가 진행 중이 아니다 (예정·종료·취소)
2. 서버의 실시간 수집이 꺼져 있다 (`tagup.live.enabled=false`)
3. **수집이 멈춰 값이 오래됐다** — 노트북 절전, 네트워크 끊김 등. 기본 60초(`tagup.live.state-ttl-ms`)가 지난 값은 서버가 내주지 않는다

3번이 실제로 자주 일어난다. 마지막 점수를 "지금 점수"처럼 계속 들고 있으면 멈춘 경기를
생중계처럼 보여주게 되므로, `live`가 사라지면 화면에서도 실시간 표시를 내려야 한다.

## 필드 메모

- `half` — `"TOP"`(초) / `"BOTTOM"`(말)
- `bases` — 각 루에 주자가 있는지. KBO는 주자의 타순 번호를 주는데 서버가 불리언으로 바꿔 보낸다
- `batter` / `pitcher` — 초/말에 따라 서버가 이미 판별해둔 값이다. 앱이 다시 뒤집지 말 것
- `updatedAt` — 이 값을 받은 시각(UTC). "몇 초 전" 표시에 쓸 수 있다
- `out` / `ball` / `strike` — 타석 배팅 창(타석 시작 30초)과는 무관하다. 그 판단은 서버가 한다
