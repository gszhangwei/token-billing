# Stakeholder Requirements Specification (StRS)

**Project**: Token Usage Billing
**Version**: 1.0
**Status**: Approved
**Date**: 2026-05-02
**Parent**: [brs.md](brs.md)

---

## 1. Stakeholder Needs

| ID | Need | Stakeholder | Rationale |
|---|---|---|---|
| StR-1 | 提交一次用量即取得帳單 | 客戶 | 即時可見、可入帳 |
| StR-2 | 配額內免費 | 客戶 | 與行銷文案一致 |
| StR-3 | 超量收費正確且可驗證 | 平台營運、財務 | 營收完整 |
| StR-4 | 同一筆用量不被重複扣費 | 客戶 | 網路重試常見 |
| StR-5 | 並發提交時計費正確 | 平台營運 | 高峰期常見 |
| StR-6 | 端點受身份驗證保護 | 資安 | 防未授權呼叫 |
| StR-7 | 系統異常時錯誤訊息可區分 | 客服、客戶端工程師 | 故障排除效率 |
| StR-8 | 帳單可審計、不可竄改 | 財務、稽核 | 合規 |

## 2. Epic & Story

### Epic E-1：Token 用量計費（本期交付）

#### User Story US-1（即本檔對應之 story）

**As** an LLM API platform service caller
**I want** to submit a customer's prompt/completion token counts
**So that** I receive an authoritative bill that consumes the customer's monthly quota first and charges overage according to the customer's pricing plan.

INVEST 檢查：
- **I**ndependent — 不依賴其他 epic
- **N**egotiable — Q1–Q8 已協商
- **V**aluable — 直接對應 BG-1/2/3
- **E**stimable — 預估 5–8 人日（含測試與 OAuth2 整合）
- **S**mall — 僅一個端點
- **T**estable — AC1–AC7 全部可自動化驗證

## 3. Acceptance Criteria

承襲 source story 的 AC1–AC5，並新增 AC6（無有效訂閱）與 AC7（idempotency 行為）。

### AC1 — Customer ID 不存在
**Given** request 攜帶之 `customerId` 不存在於 `customers` 表
**When** 後端接收請求
**Then** 回 HTTP 404，`title="Customer not found"`。

### AC2 — Token 數為負
**Given** `promptTokens < 0` 或 `completionTokens < 0`
**When** 後端執行欄位驗證
**Then** 回 HTTP 400，`title="Token count cannot be negative"`。

### AC3 — 配額內計費
**Given** 客戶月配額 100,000、當月已用 60,000
**When** 提交 30,000 tokens
**Then** 帳單顯示：`tokensFromQuota=30000`、`overageTokens=0`、`totalCharge=0.00`、`currency="USD"`。

### AC4 — 超出配額計費
**Given** 客戶月配額 100,000、當月已用 80,000、`overageRatePer1k=0.0200`
**When** 提交 50,000 tokens
**Then** 帳單顯示：`tokensFromQuota=20000`、`overageTokens=30000`、`totalCharge=0.60`、`currency="USD"`。

### AC5 — 成功回應
**Given** 合法請求
**When** 帳單成功計算
**Then** 回 HTTP 201，body 包含：`billId`、`customerId`、`promptTokens`、`completionTokens`、`totalTokens`、`tokensFromQuota`、`overageTokens`、`totalCharge`、`currency`、`calculatedAt`。

### AC6 — 客戶存在但無有效訂閱（**新增**，源自 Q2b）
**Given** 客戶存在於 `customers` 表
**And** 該客戶在請求發生日 (UTC) **沒有任何** 滿足 `effective_from <= today AND (effective_to IS NULL OR effective_to >= today)` 的訂閱記錄
**When** 後端嘗試解析計費方案
**Then** 回 HTTP 409，`title="No active subscription"`。

### AC7 — Idempotency-Key 行為（**新增**，源自 Q3）
**Given** 客戶端在 24 小時內以相同 `Idempotency-Key` header 重送請求
**When** 後端比對 payload
**Then**：
- 若 `(customerId, promptTokens, completionTokens)` 三元組相同 → 回 HTTP **200 OK** + 既有帳單（不扣配額、不新增 row）。
- 若三元組不同 → 回 HTTP **422 Unprocessable Entity**，`title="Idempotency-Key reused with different payload"`。

未攜帶 `Idempotency-Key` header 時，按一般 POST 行為（每次新建帳單）。

## 4. Definition of Ready (DoR)

故事可進入 Sprint 之前必須滿足：

- [ ] AC1–AC7 全部明確
- [ ] V2 Flyway migration 設計草稿已出（為 idempotency 加欄位 + partial unique index）
- [ ] 外部 IdP 已選定且 `issuer-uri` 已知（NFR-SEC-1 前置）
- [ ] BigDecimal 計算 worked example 已加入 srs.md
- [ ] 開發環境 PostgreSQL 14+ 與 H2 2.x 可啟動
- [ ] 既有種子資料 (CUST-001~003) 足以覆蓋 AC

## 5. Definition of Done (DoD)

故事完成需滿足：

- [ ] AC1–AC7 各有對應自動化測試（`@SpringBootTest` + `MockMvc` + Testcontainers PostgreSQL，避免 H2 與 PG 行為分歧）
- [ ] 並發測試：同 customer 50 並發請求，總配額消耗 == 序列結果
- [ ] 性能測試：p95 latency ≤ 200 ms（一般負載 ≤ 100 RPS）
- [ ] OpenAPI / Swagger 規格產出（`springdoc-openapi-starter-webmvc-ui`）
- [ ] Audit log 樣本 review 通過
- [ ] V2 migration 在 dev / staging 各執行一次無誤
- [ ] CHANGELOG / release note 已更新
- [ ] `/actuator/prometheus` 上可見 `billing.*` 自訂 metrics
- [ ] Code review 通過

## 6. MoSCoW Prioritization

| Item | Priority | 理由 |
|---|---|---|
| AC1 customer 不存在 → 404 | **Must** | 商業正確性基線 |
| AC2 負數 token → 400 | **Must** | 輸入安全 |
| AC3 配額內 → $0.00 | **Must** | 故事核心 |
| AC4 超配額計費 | **Must** | Revenue Capture |
| AC5 成功 201 + body | **Must** | 客戶端契約 |
| AC6 無有效訂閱 → 409 | **Must** | 邊界完整性 |
| AC7 idempotency 行為 | **Must** | 防重複扣費 |
| NFR-SEC-1/2 JWT Resource Server | **Must** | 安全 |
| NFR-AUDIT bills append-only | **Must** | 計費合規 |
| NFR-PERF-1 p95 ≤ 200ms | **Should** | 玩具規模可寬鬆 |
| NFR-OBS actuator + metrics | **Should** | 可在 1.x patch 補 |
| NFR-PORT-1 H2 / PG 雙容 | **Could** | 測試便利性 |
