# 채팅 중계 메시지 (FE 계약)

> 2026-10-02 변경. 서버가 **완성된 문장 대신 데이터**를 보낸다.

## 무엇이 바뀌었나

예전에는 서버가 `"🔴 박민우 아웃 · 이닝 종료"` 같은 문자열을 Firestore에 저장하고 앱이 그대로
출력했다. 그래서 **중계 줄의 모양을 앱에서 바꿀 수 없었고**, 기기마다 다르게 그려지는 이모지가
디자인을 좌우했다.

이제 타자·결과·득점을 `live` 필드로 함께 보낸다. 앱이 아이콘과 색으로 그린다.

## 문서 모양

`rooms/{chatKey}/messages` 에 쌓이는 시스템 메시지:

```json
{
  "roomId": "1",
  "senderId": "system",
  "senderNickname": "태그업",
  "type": "LIVE",
  "liveKind": "AT_BAT_RESULT",
  "content": "박민우 세이프 · 2점",
  "live": {
    "inning": 7,
    "half": "TOP",
    "batter": "박민우",
    "pitcher": "홍건희",
    "result": "SAFE",
    "runsScored": 2,
    "endedInning": false
  },
  "createdAt": "<serverTimestamp>"
}
```

### `liveKind` 두 가지

| 값 | 언제 | `live` 에 담기는 것 |
|---|---|---|
| `AT_BAT_START` | 타석이 시작될 때 (배팅 창이 열린다) | `inning`, `half`, `batter`, `pitcher`, `pitcherChanged` |
| `AT_BAT_RESULT` | 타석이 끝났을 때 | 위 + `result`, `runsScored`, `endedInning` |

- `half` — `"TOP"`(초) / `"BOTTOM"`(말)
- `result` — `"OUT"` / `"SAFE"` / `"UNKNOWN"`. KBO는 안타·볼넷·뜬공을 구분해주지 않는다.
  **없는 정보를 지어내 쓰지 말 것** (예: "안타!")
- `pitcherChanged` — 직전 타석과 투수가 다르면 `true`. 앱에서 "투수 교체" 줄을 따로 보여줄 수 있다

### 비어 있는 값은 아예 없다

`inning`이나 `pitcher`를 판별하지 못하면 **키 자체를 보내지 않는다.** 빈 문자열이나 null이 아니라
없는 것이므로, 앱은 `live.pitcher ?? null` 식으로 읽고 없으면 그 부분을 그리지 않으면 된다.

## `content` 는 남아 있다

- 푸시 알림 본문
- `live` 를 모르는 **구버전 앱**의 대비책
- 이모지는 뺐다. 앱은 `live` 가 있으면 그것으로 그리고, 없을 때만 `content` 를 글자 그대로 출력할 것

## 지난 메시지

**이미 쌓인 메시지에는 `live` 가 없고 이모지가 박혀 있다.** 앱은 두 경우를 모두 다뤄야 한다 —
`live` 가 있으면 새 방식, 없으면 `content` 를 그대로 출력. 과거 메시지를 고치지는 않는다.
