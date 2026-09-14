# DB 마이그레이션

**Flyway/Liquibase 미도입 → 수동 적용.** 마이그레이션 도구 도입 전까지 이 규칙을 지킨다.

## ⚠ `ddl-auto: update` 를 믿지 말 것 (2026-09-14 실제 사고)

`local` / `local-tunnel` 은 `ddl-auto: update` 라 스키마가 알아서 따라온다고 착각하기 쉽다.
**행이 들어있는 테이블에 `NOT NULL` 컬럼을 추가하는 건 실패한다.** 그런데 Hibernate 는
예외를 던지지 않고 WARN 만 남기고 넘어가므로, 서버는 정상 기동한 것처럼 보이고
그 컬럼을 읽는 API 만 500 이 된다.

```
WARN  GenerationTarget encountered exception accepting command :
      Error executing DDL "alter table if exists bets add column type enum (...) not null"
      via JDBC [NULL not allowed for column "TYPE";]
...
ERROR Column "B1_0.TYPE" not found
```

V4(`type VARCHAR(20) NOT NULL DEFAULT 'WIN_LOSE'`)에는 DEFAULT 가 있어 prod 는 멀쩡한데,
로컬 H2 는 Hibernate 가 DEFAULT 없이 만들려다 실패한 것이다.

**대처** — 로컬에서 NOT NULL 컬럼이 추가되는 마이그레이션을 만들면 둘 중 하나를 한다.
1. `rm -rf data/` 로 DB 를 비우고 새로 만든다 (테스트 데이터가 아깝지 않을 때)
2. 해당 DDL 을 H2 에 직접 적용한다
   ```bash
   java -cp <h2.jar> org.h2.tools.Shell \
     -url "jdbc:h2:file:$(pwd)/data/tagupdb;MODE=MySQL;AUTO_SERVER=TRUE" -user sa -password "" \
     -sql "ALTER TABLE bets ADD COLUMN IF NOT EXISTS type VARCHAR(20) DEFAULT 'WIN_LOSE' NOT NULL;"
   ```

기동 로그에서 `GenerationTarget encountered exception` 을 grep 하면 이런 실패를 미리 잡을 수 있다.

## ⚠ 가장 중요한 사실

prod는 `ddl-auto: validate` 다. **Hibernate가 테이블을 만들어주지 않고, 엔티티와 스키마가
1개 컬럼이라도 어긋나면 서버가 기동조차 하지 않는다.** 엔티티를 바꿨는데 여기 SQL을
안 만들면 다음 배포가 실패한다.

## 파일 목록

| 파일 | 내용 |
|---|---|
| `V1__initial_schema.sql` | 최초 배포용 전체 스키마 (2026-08-26 기준: users·teams·rooms·room_members·games·bets) |
| `V2__add_user_devices.sql` | 푸시 알림 기기 등록 |
| `V3__add_room_watching_game.sql` | 더그아웃의 '오늘 보는 경기' |
| `V4__add_at_bat_bet.sql` | 타석 배팅 (`type`, `at_bat_*`, `bet_on_team_id` NULL 허용) |

## 적용 방법

```bash
# 1) DB 생성 (최초 1회) — 한글 저장을 위해 utf8mb4 필수
mysql -h <RDS_ENDPOINT> -u <ADMIN> -p \
  -e "CREATE DATABASE tagupdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2) 스키마 적용
mysql -h <RDS_ENDPOINT> -u <USER> -p tagupdb < V1__initial_schema.sql
```

구단 10개(`teams`)는 `DataInitializer`가 첫 기동 시 자동 삽입하므로 시드 SQL이 필요 없다.
경기 데이터도 기동 시 크롤러가 채운다.

## 엔티티를 변경했을 때

1. **새 파일 추가** — `V2__<변경내용>.sql` (기존 파일은 절대 수정하지 않는다. 이미 적용된 DB가 있으므로)
2. 아래 방법으로 Hibernate가 기대하는 DDL을 뽑아 대조
3. CHANGELOG에 마이그레이션 필요 사실 기재

### Hibernate 기대 DDL 추출

임시 테스트를 만들어 컨텍스트를 한 번 띄우면 `build/generated-schema.sql`에 MySQL DDL이 떨어진다.

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
    "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
    "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/generated-schema.sql"
})
class SchemaExportTest { @Test void export() {} }
```

> 테스트 자체는 실패해도 무방하다(H2에 MySQL 방언을 물리므로). DDL 파일은 정상 생성된다.

### 검증 (배포 전 필수)

실제 MySQL에 적용해보고 `validate`로 앱을 띄워본다. 통과하면 배포해도 안전하다.

```bash
docker run -d --name tagup-schema-check \
  -e MYSQL_ROOT_PASSWORD=verifypw -e MYSQL_DATABASE=tagupdb -p 13306:3306 \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

docker exec -i tagup-schema-check mysql -uroot -pverifypw tagupdb < V1__initial_schema.sql

./gradlew bootRun --args='--spring.profiles.active=local --server.port=8091 \
  --spring.datasource.url=jdbc:mysql://localhost:13306/tagupdb?characterEncoding=UTF-8 \
  --spring.datasource.username=root --spring.datasource.password=verifypw \
  --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver \
  --spring.jpa.hibernate.ddl-auto=validate \
  --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect'

docker rm -f tagup-schema-check   # 정리
```

> MySQL CLI에서 한글이 `????`로 보이면 `--default-character-set=utf8mb4` 를 붙일 것.
> 데이터가 깨진 게 아니라 클라이언트 표시 문제다.

## 향후 과제

컬럼 추가가 잦아지면 **Flyway 도입**을 권한다. 현재 파일 구조(`V1__`, `V2__`)를 그대로
`spring.flyway` 설정만 켜면 되도록 이름 규칙을 맞춰뒀다.
