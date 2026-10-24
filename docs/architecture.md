# 구조와 배치 기준

새 코드를 어디에 둘지 판단할 때 보는 문서. 파일을 만들기 전에 읽는다.

---

## 도메인이 하는 일

패키지 목록을 외우는 것보다, 각 도메인이 무엇을 담당하는지를 아는 게 중요하다.

| 도메인 | 한 줄 |
|---|---|
| `domain/stock` | 데이터를 만든다. 외부 API에서 수집해 스냅샷으로 저장 |
| `domain/custom` | 사용자별 커스텀 데이터. 섹터 트리, 종목 배정·별칭, 색상·시가총액 구간, 스냅샷, 화면 설정 |
| `domain/view` | 데이터를 읽어 화면용으로 준다. 조회·집계·응답 DTO |
| `domain/notification` | 데이터를 밖으로 내보낸다. 텔레그램 발송, 장애 에스컬레이션 |
| `domain/renderer` | 화면을 이미지로 만든다. 렌더러 서버 호출 |
| `domain/auth` | 누구인가. Google 로그인, JWT·캡처 토큰, 계정, 가입 초기화와 신규 업종 전파 |

앱 전역 인프라는 도메인 밖에 둔다.

| 위치 | 내용 |
|---|---|
| `config/` | 앱 전역 Spring 배선 (`RestClient`, `CacheManager`, `JPAQueryFactory`) |
| `handler/` | 전역 예외 처리 (`@RestControllerAdvice`) |
| `runner/` | 앱 시작 시 훅 (`ApplicationRunner`) |
| `common/` | 비즈니스가 공통으로 import하는 것. `cache` · `enums` · `event` · `exception` · `util` |

---

## 데이터 흐름

```
외부 API (키움)
    │
    ▼
domain/stock/collector          수집
    │
    ▼
스냅샷 테이블 (sector_price_snapshot 등)
    │
    ├──────────────────────────────┐
    ▼                              ▼
domain/custom                   domain/view
사용자별 섹터 체계로 분류         조회·집계
    │                              │
    └──────────┬───────────────────┘
               ▼
    ┌──────────┴──────────┐
    ▼                     ▼
REST API (화면)      domain/notification
                     (+ domain/renderer로 이미지 캡처)
                          ▼
                       텔레그램
```

스케줄러(`domain/stock/scheduler/CollectionScheduler`)가 이 흐름 전체의 트리거다.
수집 → 텔레그램 발송이 같은 호출 안에서 순차 실행된다. 별도 스케줄로 나누면 두 트리거의 실행 순서를
보장할 수 없기 때문이다. 섹터 등락률은 저장하지 않고 조회 시점에 종목 행에서 합산한다.

사용자 범위: `domain/custom`의 모든 조회·변경은 인증 주체의 `userId`로 범위를 건다
(`CurrentUser`). 로그인 없이 도는 경로(텔레그램 캡션)는 `marketry.owner-user-id`의 데이터를 쓴다.

---

## 의존 방향

- `common` → `domain` 역의존 금지. `common`은 순수 공유 라이브러리다
- 도메인 간 순환 금지. 순환이 생기면 이벤트로 끊는다
  - 실제 사례: `StockInfoCollector`(stock)가 새 업종을 만들면 전 사용자에게 섹터를 만들어야 하는데,
    `stock → auth/custom` 직접 의존은 역방향과 순환이 된다. 그래서
    `common/event/IndustryInfoCreatedEvent`를 발행하고 `IndustrySectorPropagationService`가 수신한다
- `view`는 다른 도메인을 읽기만 한다. `view`가 데이터를 만들거나 바꾸지 않는다
- 외부 API 클라이언트는 소비하는 도메인에 귀속시킨다
  (`KiwoomApiClient` → `domain/stock/client`, `TelegramClient` → `domain/notification/client`)

---

## 레이어별 배치

도메인 안에서는 아래 서브패키지를 쓴다. 루트 직속에 파일을 두지 않는다.

| 서브패키지 | 넣는 것 |
|---|---|
| `entity/` | JPA 엔티티 |
| `repository/` | Spring Data 리포지토리 (+ QueryDSL `*RepositoryCustom`/`*RepositoryImpl`) |
| `service/` | 비즈니스 로직, 캐시 서비스 |
| `controller/` | REST 컨트롤러 |
| `scheduler/` | `@Scheduled` 트리거 |
| `collector/` | 배치 수집기 (외부 API 폴링해서 저장) |
| `client/` | 외부 HTTP 호출자만. 요청·응답 타입은 `dto/` |
| `dto/` | 요청·응답 타입 |
| `enums/` | 도메인 enum (`enum`이 예약어라 복수형) |
| `properties/` | `@ConfigurationProperties` |
| `config/` | 도메인 종속 `@Configuration` |
| `exception/` | 도메인 특화 예외 |

진입점(`controller`, `scheduler`)은 도메인 바로 아래에 둔다. `collector` 밑에 중첩시키지 않는다.

수집기 내부에서만 쓰는 중간 표현은 `dto/`가 아니라 private nested record로 둔다. `dto/`는 외부
경계(HTTP 요청·응답) 전용이다.

---

## 어디에 둘지 애매할 때

### 판단 순서

1. "이 코드가 하는 일이 위 도메인 중 무엇인가?"
2. 그래도 애매하면: "이게 없어지면 어느 기능이 안 되나?" 그 기능의 도메인에 둔다
3. 여러 도메인에 걸치면: 주된 소비자의 도메인에 둔다
4. 주된 소비자도 여럿이면: 도메인별로 쪼개고, 그것들을 부르는 얇은 조율 계층을 트리거 쪽에 둔다
   (아래 예시 참고)

### 새 파일을 만들기 전에

- 같은 역할의 클래스가 이미 있는가? → 있으면 거기에 메서드를 추가한다
- 기존 클래스의 확장으로 될 일인가? → 새 타입을 만들기 전에 확인한다
- 두 곳에서 쓰이는가? → 한 곳이면 그 도메인에, 여러 곳이면 3~4번 판단으로

### 예시 — 여러 도메인에 걸치는 배치 작업

스냅샷 데이터 정리 배치가 두 도메인의 테이블을 지운다고 하자(예전에 `sector_price_snapshot`과
카테고리 집계 테이블이 그랬다. 집계 테이블은 없어졌지만 판단 기준은 같다).

**나쁜 배치**: 한 클래스가 두 도메인의 리포지토리를 직접 호출한다. 도메인 경계를 넘고, 나중에
한 도메인만 고칠 때도 그 클래스를 건드려야 한다.

**좋은 배치**: 삭제 로직을 각 도메인의 서비스에 두고, 스케줄러가 둘을 호출한다.

```
domain/stock/service/SectorPriceSnapshotService
    └ deleteSnapshotsBefore(...)

domain/<다른 도메인>/service/...Service
    └ deleteSnapshotsBefore(...)

domain/stock/scheduler/  (또는 별도 스케줄러)
    └ 위 둘을 호출하는 얇은 조율 메서드
```

각 도메인은 자기 데이터의 수명만 알고, 스케줄러는 "언제 도는가"만 안다.

---

## 명명

- API/TR 번호 기반 이름 금지: `Ka20001Request` → `SectorCurrentPriceRequest`
- 약어보다 풀네임: `prevMarketCap` 대신 `prevMarketCapitalization`
- 메서드명은 동사로 시작하고 역할이 드러나게: `message()` → `createMessage()`
- 클래스명은 책임 중심으로: `AlertService` → `EscalationService`
- enum static factory는 `valueOf`와 혼동되지 않게: `codeOf()`, `from()`
- 저장용 enum과 조회 파라미터용 enum을 섞지 않는다: DB에 저장되는 `IntradayInvestorType`에
  "합산 조회" 같은 API 전용 값을 끼우지 않는다. 그런 의미는 `MarketQuery`, `IntradayInvestorQuery`
  같은 Query enum으로 분리한다
- "전체/통합"은 enum 상수가 아니라 Query enum으로: `Market.ALL` → `MarketQuery.ALL_STOCK`

---

## 캐시

- `common/cache/CacheService<T>` 인터페이스로 통일한다: `getCache()` / `evict()`
- `@Cacheable` value는 컴파일타임 상수여야 하므로 `CacheKey` 상수 클래스에 모은다
- 캐시 매니저가 두 개다
  - `cacheManager`(기본) — 종목 정보. TTL 없이 명시적 evict
  - 종목 가격 캐시 전용 매니저(`domain/stock/config/SectorPriceCacheConfig`) — `(마켓, 시각)` 키,
    크기 상한 있음
- 사용자별 캐시를 새로 만들 때는 키가 사용자 수만큼 늘어나므로 `maximumSize`를 반드시 건다
- 캐시 evict는 트랜잭션 커밋 후에 한다. 커밋 전에 비우면 다른 스레드가 커밋 전 데이터를 읽어
  캐시에 굳힐 수 있다

---

## 예외

`common/exception/BusinessException`은 sealed이고 서브타입은 넷이다.

| 타입 | HTTP | 용도 |
|---|---|---|
| `BadRequestException` | 400 | 잘못된 요청 |
| `NotFoundException` | 404 | 대상 없음 |
| `ConflictException` | 409 | 중복·충돌 |
| `EscalateException` | 500 | 개발자에게 즉시 텔레그램 알림이 가는 장애 |

`EscalateException`을 쓸지 말지의 판단 기준은 `docs/decisions.md`를 본다. 아무 500에나 붙이는 게
아니다.

예외를 던지면 이후 흐름이 멈추는 곳(수집기처럼 다음 수집기가 계속 돌아야 하는 경우)에서는 던지는 대신
`EscalateException.wrap(...)`으로 정규화해서 `EscalationPublisher.report()`에 넘긴다.

---

## 이 문서의 범위

여기 없는 세부 코딩 규칙(제어 흐름, 스트림, BigDecimal 비교 등)은 `docs/rules/style.md`에 있다.
