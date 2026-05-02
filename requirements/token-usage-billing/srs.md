# System / Software Requirements Specification (SRS)

**Project**: Token Usage Billing
**Version**: 1.0
**Status**: Approved
**Date**: 2026-05-02
**Parents**: [brs.md](brs.md) → [strs.md](strs.md)

---

## 1. System Overview

Spring Boot 3.5 / Java 21 微服務，提供單一端點 `POST /api/usage`：接收 token 用量、扣抵當月配額、計算並持久化帳單、回傳結果。資料儲存於 PostgreSQL 14+（測試使用 H2 2.x）；Flyway 管理 schema migration。

```
[Client] --(JWT, Idempotency-Key, JSON)--> [POST /api/usage]
                                              |
                          +-------------------+--------------------+
                          |                   |                    |
                  [JWT 驗證]           [欄位驗證]          [DB transaction]
                                                                   |
                                +--------------+----------+--------+--------+
                                |              |          |        |        |
                          PESSIMISTIC      讀方案      讀當月    Idempo-   寫 bills
                          LOCK customer   (subscriptions  用量      tency     (append)
                                          + plans)     SUM(bills) 查找/比對
                                                                   |
                                                            BigDecimal 計算
                                                                   |
                                                              回 201 / 200
```

## 2. Functional Requirements

### 2.1 Endpoint Contract

#### SRS-F-1 — Endpoint
The system **SHALL** expose `POST /api/usage`，`Content-Type: application/json; charset=utf-8`，`Accept: application/json`。

- Source: Story Scope In
- Priority: Must
- Verification: 整合測試以 MockMvc 呼叫並驗證 status & body

#### SRS-F-2 — Request Body Schema
Request body **SHALL** 為 JSON 物件，包含且僅包含以下欄位：

```json
{
  "customerId": "CUST-001",
  "promptTokens": 30000,
  "completionTokens": 20000
}
```

- `customerId`：String，必填，regex `^[A-Za-z0-9_\-]{1,50}$`。
- `promptTokens`：Integer，必填，`>= 0`，`<= 2_000_000_000`。
- `completionTokens`：Integer，必填，`>= 0`，`<= 2_000_000_000`。
- 多餘欄位：HTTP 400（`spring.jackson.deserialization.fail-on-unknown-properties=true`）。
- 欄位命名風格：camelCase。

> **註**：上界 2 × 10⁹ 是為避免 `total_tokens = prompt + completion` 溢出 Java `int`（max ≈ 2.147 × 10⁹）。

#### SRS-F-3 — Idempotency-Key Header
Request **MAY** 攜帶 `Idempotency-Key` HTTP header：

- 格式：`^[A-Za-z0-9_\-]{8,255}$`
- 缺失：走一般 POST 流程（每次新建 bill）。
- 攜帶且 24 小時內 (`bills.created_at >= now() - interval '24 hours'`) 已存在同 (`customerId`, `idempotency_key`) 記錄：
  - 若 `(promptTokens, completionTokens)` 與舊 bill 一致 → 回 **HTTP 200** + 舊 bill body，附加 response header `Idempotent-Replayed: true`。**不**扣配額、**不**新增 row。
  - 否則 → 回 **HTTP 422**，`title="Idempotency-Key reused with different payload"`。
- 同 header、同 payload、但已超過 24 小時 → 視為新請求。

#### SRS-F-4 — Success Response Schema
Status `201 Created`（idempotent 回放為 `200 OK`），body：

```json
{
  "billId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "CUST-001",
  "promptTokens": 30000,
  "completionTokens": 20000,
  "totalTokens": 50000,
  "tokensFromQuota": 20000,
  "overageTokens": 30000,
  "totalCharge": 0.60,
  "currency": "USD",
  "calculatedAt": "2026-05-02T08:31:42.123Z"
}
```

- `billId`：UUID v4。
- `totalCharge`：JSON number（非 string），固定 2 位小數。
- `currency`：固定字串 `"USD"`。
- `calculatedAt`：RFC 3339 / ISO-8601，UTC（`Z` 結尾），毫秒精度。

#### SRS-F-5 — Error Response Schema (RFC 7807)
所有非成功回應 **SHALL** 採 `application/problem+json`：

```json
{
  "type": "about:blank",
  "title": "Customer not found",
  "status": 404,
  "detail": "Customer 'CUST-999' does not exist",
  "instance": "/api/usage"
}
```

完整 status / title 對照表：

| 情境 | Status | title |
|---|---|---|
| 成功新建 | 201 Created | （無 — body = bill） |
| Idempotent 回放命中 | 200 OK | （無 — body = 舊 bill） |
| JWT 缺失或無效 | 401 Unauthorized | `Unauthorized` |
| JWT 缺 `billing:write` scope | 403 Forbidden | `Forbidden` |
| Body 解析 / 欄位缺失 / 多餘欄位 | 400 Bad Request | `Invalid request body` |
| `customerId` regex 不過 | 400 | `Invalid customer ID format` |
| `promptTokens` 或 `completionTokens` 為負 | 400 | `Token count cannot be negative` |
| `customerId` 不存在 | 404 Not Found | `Customer not found` |
| 客戶無有效訂閱 (AC6) | 409 Conflict | `No active subscription` |
| Idempotency-Key 重用但 payload 不同 (AC7) | 422 Unprocessable Entity | `Idempotency-Key reused with different payload` |
| 客戶有多筆有效訂閱（資料異常） | 500 Internal Server Error | `Data integrity error` |
| Pessimistic lock 取得逾時 | 503 Service Unavailable | `Concurrent billing in progress, retry later` |
| 其他未預期錯誤 | 500 | `Internal server error` |

### 2.2 Validation Order

#### SRS-F-6 — Validation Pipeline
驗證 **SHALL** 依下列順序短路執行：

1. JWT 驗證（401 / 403）
2. Body parsing & required fields（400 `Invalid request body`）
3. Field format & range（400 — `Invalid customer ID format` / `Token count cannot be negative`）
4. Customer existence（404）
5. Active subscription resolution（409 / 500）
6. Idempotency lookup & payload compare（200 / 422）
7. Pessimistic lock 取得（503 on timeout）
8. 計算與寫入（500 on unexpected）

### 2.3 Active Subscription Resolution

#### SRS-F-7 — Subscription Lookup
The system **SHALL** 以 `today = LocalDate.now(ZoneOffset.UTC)` 解析有效訂閱：

```sql
SELECT s.*, p.monthly_quota, p.overage_rate_per_1k
FROM customer_subscriptions s
JOIN pricing_plans p ON p.id = s.plan_id
WHERE s.customer_id = :customerId
  AND s.effective_from <= :today
  AND (s.effective_to IS NULL OR s.effective_to >= :today)
```

- 0 筆 → AC6（HTTP 409）。
- 1 筆 → 採用其 `monthly_quota` 與 `overage_rate_per_1k`。
- ≥ 2 筆 → HTTP 500，並 `log.error("Multiple active subscriptions for customerId={}", customerId)`。

#### SRS-F-8 — Plan-Agnostic Monthly Usage
當月歷史用量 **SHALL** 不分方案累計：

```sql
SELECT COALESCE(SUM(total_tokens), 0)
FROM bills
WHERE customer_id = :customerId
  AND calculated_at >= date_trunc('month', now() AT TIME ZONE 'UTC')
  AND calculated_at <  date_trunc('month', now() AT TIME ZONE 'UTC') + INTERVAL '1 month'
```

當下有效之方案決定該次計算的 `quota` 與 `rate`；月內方案變更不重設配額。

### 2.4 Calculation Logic

#### SRS-F-9 — Token Apportionment
```
totalTokens       = promptTokens + completionTokens
remainingQuota    = max(0, monthlyQuota - currentMonthUsage)
tokensFromQuota   = min(totalTokens, remainingQuota)
overageTokens     = totalTokens - tokensFromQuota
```

#### SRS-F-10 — Charge Calculation
**SHALL** 全程以 `BigDecimal` + `MathContext(precision=10)` 運算：

```
totalCharge = (overageTokens / 1000) × overageRatePer1k    -- BigDecimal, 全程不四捨五入
            .setScale(2, RoundingMode.HALF_EVEN)            -- 最終轉為 2 位小數
```

捨入規則：`HALF_EVEN`（銀行家捨入）。捨入發生時機：**僅在最終寫入 `bills.total_charge` 前一次**，中間值不得提前捨入。

##### Worked Examples

| Case | overageTokens | rate | 中間值 | 最終 totalCharge |
|---|---|---|---|---|
| AC4 | 30,000 | 0.0200 | 30 × 0.0200 = 0.6000000000 | 0.60 |
| 12,345 | 12,345 | 0.0150 | 12.345 × 0.0150 = 0.185175 | 0.19 (HALF_EVEN) |
| 999 | 999 | 0.0200 | 0.999 × 0.0200 = 0.019980 | 0.02 (HALF_EVEN, 4 進位) |
| 5 | 5 | 0.0150 | 0.005 × 0.0150 = 0.000075 | 0.00 |
| 12,500 | 12,500 | 0.0150 | 12.5 × 0.0150 = 0.187500 | 0.18 (HALF_EVEN, 5 向偶數捨) |

### 2.5 Concurrency Control

#### SRS-F-11 — Pessimistic Lock
The system **SHALL** 在計算與寫入帳單之 transaction 開始時，對 `customers` row 取得 `PESSIMISTIC_WRITE` lock：

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "5000")})
Optional<Customer> findByIdForUpdate(String id);
```

- Lock timeout：5,000 ms。
- Timeout 觸發 → 回 HTTP 503，`title="Concurrent billing in progress, retry later"`，並 `log.warn("Lock acquisition timeout for customerId={}", customerId)`。
- Lock 範圍：同 customer 序列化；跨 customer 並行不受影響。

### 2.6 Persistence

#### SRS-F-12 — Bill Insert
寫入 `bills` 之欄位（migration 補齊後）：

| 欄位 | 來源 |
|---|---|
| `id` | `UUID.randomUUID()` |
| `customer_id` | request |
| `prompt_tokens` | request |
| `completion_tokens` | request |
| `total_tokens` | 計算 |
| `included_tokens_used` | 計算（即 API 回傳的 `tokensFromQuota`）|
| `overage_tokens` | 計算 |
| `total_charge` | 計算（HALF_EVEN, 2dp）|
| `idempotency_key` | request header（可 NULL）— **新增欄位（V2 migration）** |
| `created_at` | DB `DEFAULT CURRENT_TIMESTAMP` — **新增欄位（V2 migration）** |
| `calculated_at` | 業務時間戳，於 service 層設為 `Instant.now()` |

#### SRS-F-13 — V2 Flyway Migration
新增 migration `V2__Add_idempotency_to_bills.sql`：

```sql
ALTER TABLE bills
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- PostgreSQL: partial unique index
CREATE UNIQUE INDEX idx_bills_idempotency
    ON bills (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_bills_created_at ON bills (created_at);
```

H2 2.x 不支援 partial index 之 `WHERE` 條件；測試 profile 改用 `UNIQUE (customer_id, idempotency_key)` 並接受「同 customer 不能存兩個 NULL key」之差異；該差異於 NFR-PORT-1 標註。

## 3. Non-Functional Requirements

### 3.1 Performance

| ID | Description | Target | Verification |
|---|---|---|---|
| NFR-PERF-1 | `POST /api/usage` p95 latency | ≤ 200 ms（一般負載 ≤ 100 RPS、DB 同網段） | k6 / JMeter |
| NFR-PERF-2 | 同 customer 並發吞吐 | ≥ 10 RPS | 並發測試 |
| NFR-PERF-3 | 跨 customer 吞吐（單節點） | ≥ 500 RPS | 負載測試 |

### 3.2 Security

| ID | Description |
|---|---|
| NFR-SEC-1 | 端點 **SHALL** 要求合法 JWT bearer token；token 由外部 IdP 發行；issuer 由 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 設定。Token 缺失或無效 → HTTP 401。 |
| NFR-SEC-2 | JWT **SHALL** 包含 scope `billing:write`；缺少 → HTTP 403。 |
| NFR-SEC-3 | build.gradle **SHALL** 加入 `org.springframework.boot:spring-boot-starter-oauth2-resource-server`。 |
| NFR-SEC-4 | 不在本期：JWT subject 與 `customerId` 之授權矩陣（細粒度 RBAC）— 列為 FU-6。 |

### 3.3 Observability

| ID | Description |
|---|---|
| NFR-OBS-1 | **SHALL** 加入 `spring-boot-starter-actuator`，暴露 `/actuator/health`、`/actuator/metrics`、`/actuator/prometheus`。 |
| NFR-OBS-2 | 自訂 metrics：<br>• `billing.requests.total{customer_id, status}` — counter<br>• `billing.overage.charge{customer_id, plan_id}` — distribution summary<br>• `billing.lock.contention.total` — counter<br>• `billing.idempotency.replay.total` — counter |
| NFR-OBS-3 | 健康檢查 SHALL 包含 DB 連線狀態。 |

### 3.4 Logging

| ID | Description |
|---|---|
| NFR-LOG-1 | 每筆請求 **SHALL** 輸出 structured JSON log（INFO），欄位：`requestId`、`customerId`、`promptTokens`、`completionTokens`、`billId`、`totalCharge`、`durationMs`。 |
| NFR-LOG-2 | 錯誤 **SHALL** log 含 stack trace（ERROR）；`customerId` 必須出現在 MDC。 |
| NFR-LOG-3 | `application.yml` 之 `spring.jpa.show-sql=true` **SHALL** 在 production profile 關閉。 |
| NFR-LOG-4 | Log **SHALL NOT** 完整輸出 `Idempotency-Key`（截前 8 字元即可）；**SHALL NOT** 輸出任何密鑰或 JWT 內容。 |

### 3.5 Audit

| ID | Description |
|---|---|
| NFR-AUDIT-1 | `bills` 表 **SHALL** 視為 append-only。應用程式層僅提供 INSERT；Repository **SHALL NOT** 暴露 `update` / `delete` 方法。 |
| NFR-AUDIT-2 | DB 層 GRANT 限制（撤銷 application user 對 `bills` 之 UPDATE / DELETE 權限）列為 FU-7。 |

### 3.6 Privacy

| ID | Description |
|---|---|
| NFR-PRIV-1 | 系統 **SHALL NOT** 儲存任何 prompt / completion 文字內容；僅儲存 token 計數。 |

### 3.7 Portability

| ID | Description |
|---|---|
| NFR-PORT-1 | Schema migration **SHALL** 同時相容 PostgreSQL 14+ 與 H2 2.x。Partial unique index 若 H2 不支援，**SHALL** 在 H2 profile 退化為 `UNIQUE (customer_id, idempotency_key)`，並於 migration 註解標明差異。 |

## 4. Data Model

承襲既有 schema（`V1__Create_tables.sql`），**新增** V2 migration（見 SRS-F-13）。完整概念圖：

```
customers (1) ──< customer_subscriptions >── (1) pricing_plans
    │
    └──< bills (append-only)
            ├─ idempotency_key (NULLABLE, 24h TTL)
            └─ created_at  (idempotency 查詢用)
```

關鍵不變式：
- `customers.id` 之 partial unique「有效訂閱」於本期不強制（FU-4）。
- `bills` 不可變；任何更正須以新 row + 業務沖銷流程處理（不在本期）。

## 5. Error Handling Reference

完整 RFC 7807 範例：

```http
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Idempotency-Key reused with different payload",
  "status": 422,
  "detail": "Key '8f3b...' was previously used with different (promptTokens, completionTokens)",
  "instance": "/api/usage"
}
```

## 6. Follow-up Backlog (Out of Scope, 建議獨立 ticket)

| FU-ID | 描述 | 來源 |
|---|---|---|
| FU-1 | Customer CRUD 端點 | Story Scope Out |
| FU-2 | 歷史 bill 查詢端點 (`GET /api/bills?...`) | Story Scope Out |
| FU-3 | 月配額重置 cron 與訂閱 anchor day 設計 | Q1 |
| FU-4 | Customer 多筆有效訂閱之 partial unique index migration | Q2c |
| FU-5 | 跨客戶 rate limiting | Q7f |
| FU-6 | JWT subject ↔ customerId 細粒度授權矩陣（RBAC） | Q7e |
| FU-7 | DB GRANT 限制 `bills` 表 UPDATE / DELETE 權限 | NFR-AUDIT-2 |
| FU-8 | Idempotency-Key 24h 視窗實作驗證與監控告警 | Q3.4 |
| FU-9 | 多幣種支援（解綁 USD 硬編碼） | Q5d |
| FU-10 | 月份切換瞬間（UTC 23:59:59.999）之請求行為驗證 | Q1 |

## 7. Requirements Traceability Matrix (RTM)

| Requirement ID | Source AC / NFR | Decision Origin | Verification |
|---|---|---|---|
| SRS-F-1 endpoint | Story §Scope In | Story baseline | Integration test |
| SRS-F-2 request schema | Story §Request fields | Q6a, Q6b | Schema validation test |
| SRS-F-3 idempotency header | AC7 | Q3.1, Q3.2, Q3.3, Q3.4 | Replay & mismatch test |
| SRS-F-4 success response | AC5 | Q5d, Q6a, Q6c | Contract test |
| SRS-F-5 error envelope | AC1, AC2, AC6, AC7 | Q6d, Q6e | Per-status test |
| SRS-F-6 validation order | — | Q6f | Sequence test (mock parser) |
| SRS-F-7 subscription lookup | AC6 | Q2a, Q2b, Q2c | Edge-case tests (0/1/n subs) |
| SRS-F-8 plan-agnostic month | AC3, AC4 | Q1, Q2d | Mid-month plan switch test |
| SRS-F-9 apportionment | AC3, AC4 | Story formula | Worked-example tests |
| SRS-F-10 charge calc | AC4 | Q5a, Q5b, Q5c | BigDecimal precision tests |
| SRS-F-11 pessimistic lock | — | Q4 | 50-thread concurrency test |
| SRS-F-12 bill insert | AC5 | DB schema baseline | Integration test |
| SRS-F-13 V2 migration | — | Q3 (idempotency 持久化) | Migration apply test |
| NFR-PERF-1/2/3 | — | Q7a | k6 benchmark |
| NFR-SEC-1/2/3 | — | Q7e=C1 | Security integration test |
| NFR-OBS-1/2/3 | — | Q7b | Manual probe `/actuator/*` |
| NFR-LOG-1/2/3/4 | — | Q7c | Log assertion test |
| NFR-AUDIT-1 | — | Q7d | Code review (no update/delete API) |
| NFR-PRIV-1 | — | Q7g | Code review |
| NFR-PORT-1 | — | Q7h | H2 + Testcontainers parallel run |

## 8. Document Control

| Section | Owner | Status |
|---|---|---|
| BRS | Sponsor | Approved |
| StRS | Product / 客戶代表 | Approved |
| SRS | Tech lead | Approved |

任何後續變更 **SHALL** 透過 PR 修改本檔，並於 Changelog 記錄；變更影響 AC1–AC7 則須走 change-control（重新 elicit + sign-off）。
