# 배포 가이드 (EC2 + RDS)

> 최초 배포용. 2026-09-04 기준 — Java 25 / Spring Boot 4.1.1

## ⚠ 가장 먼저 알아야 할 것

prod 프로파일은 **`ddl-auto: validate`** 다. Hibernate가 테이블을 만들지 않으며,
**스키마를 먼저 넣지 않으면 서버가 기동조차 하지 않는다.** 아래 2단계를 건너뛰면 배포는 실패한다.

---

## 1. AWS 리소스 준비

### RDS (MySQL 8)
- 엔진 MySQL 8.0
- **파라미터 그룹에서 `character_set_server=utf8mb4`, `collation_server=utf8mb4_unicode_ci`**
  (기본값 latin1이면 한글이 깨진다)
- 보안 그룹: EC2의 보안 그룹에서 3306 인바운드 허용

### EC2 (Ubuntu 권장)
```bash
# Docker + Compose 설치
sudo apt update && sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER && newgrp docker

# 배포 디렉터리
mkdir -p ~/tagup/nginx
```
- 보안 그룹: 80, 443 인바운드 (SSH는 본인 IP만)

---

## 2. DB 스키마 적용 (필수 · 최초 1회)

```bash
# DB 생성 — utf8mb4 필수
mysql -h <RDS_ENDPOINT> -u <ADMIN> -p \
  -e "CREATE DATABASE tagupdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 스키마 적용 (순서대로)
mysql -h <RDS_ENDPOINT> -u <USER> -p tagupdb < src/main/resources/db/migration/V1__initial_schema.sql
mysql -h <RDS_ENDPOINT> -u <USER> -p tagupdb < src/main/resources/db/migration/V2__add_user_devices.sql

# 확인 — 7개 테이블이 나와야 한다
mysql -h <RDS_ENDPOINT> -u <USER> -p tagupdb -e "SHOW TABLES;"
# bets games room_members rooms teams user_devices users
```

> 구단 10개와 경기 데이터는 **첫 기동 시 자동으로 채워진다** (DataInitializer + 크롤러). 시드 SQL 불필요.

---

## 3. EC2에 `.env` 생성

`.env.example`을 참고해 `~/tagup/.env` 작성:

```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://<RDS_ENDPOINT>:3306/tagupdb?useSSL=true&characterEncoding=UTF-8
DB_USERNAME=tagup
DB_PASSWORD=<실제 비밀번호>
FIREBASE_PROJECT_ID=<Firebase 프로젝트 ID>
FIREBASE_CREDENTIALS=<서비스 계정 JSON을 base64 인코딩한 값>
```

**FIREBASE_CREDENTIALS 만드는 법** — Firebase Console → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성:
```bash
cat firebase-service-account.json | base64 | tr -d '\n'
```
이 값이 있어야 **정산 채팅 발송**이 동작한다 (없으면 로컬처럼 조용히 생략된다).

---

## 4. GitHub Secrets 등록

레포 Settings → Secrets and variables → Actions:

| 이름 | 값 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| `EC2_USER` | `ubuntu` 등 SSH 사용자 |
| `EC2_SSH_KEY` | **개인키 전문** (`-----BEGIN ...` 포함) |

---

## 5. 배포 실행

CD는 **`main` 브랜치 push에서만** 동작한다. 즉 배포 = `develop → main` PR 머지.

```bash
gh pr create --base main --head develop --title "deploy: 최초 프로덕션 배포"
# 머지하면 자동으로: 이미지 빌드 → GHCR push → EC2 배포 → 헬스체크
```

Actions 탭에서 진행 상황을 볼 수 있다. 마지막 단계가 헬스체크이며,
**기동에 실패하면 로그 60줄을 자동 출력**한다.

---

## 6. 배포 후 확인

```bash
curl http://<EC2_HOST>/actuator/health          # {"status":"UP"}
curl http://<EC2_HOST>/api/v1/teams             # 구단 10개 (인증 불필요)
```

EC2에서:
```bash
cd ~/tagup && docker compose logs -f app
# "경기 저장 완료" 로그가 보이면 크롤러 정상
```

**프론트엔드**: `.env.local`의 `EXPO_PUBLIC_API_BASE_URL`을 EC2 주소로 변경.

---

## 문제 해결

| 증상 | 원인 / 조치 |
|---|---|
| 기동 실패, 로그에 `SchemaManagementException` | 2단계 스키마 미적용. V1·V2를 적용할 것 |
| 기동 실패, `Access denied` / `Communications link failure` | RDS 보안 그룹에서 EC2 3306 인바운드 미허용, 또는 `.env`의 DB 정보 오류 |
| 한글이 `????` | RDS 파라미터 그룹 charset이 utf8mb4가 아님. 변경 후 **DB 재생성 필요** |
| EC2에서 `docker compose pull` 401 | GHCR 패키지가 private이고 토큰 권한 부족. 패키지를 public으로 바꾸거나 `read:packages` PAT 사용 |
| 정산 채팅이 안 감 | `FIREBASE_CREDENTIALS` 미설정 (없으면 조용히 생략됨) |
| 푸시가 안 옴 | FE `app.json`에 `extra.eas.projectId` 필요 (`npx eas init`) + **실기기** 필요 |

## 롤백

```bash
cd ~/tagup
docker compose down
docker pull ghcr.io/sanghyun-e/tagup-backend:<이전 커밋 SHA>
# docker-compose.yml의 image 태그를 해당 SHA로 바꾼 뒤
docker compose up -d
```
