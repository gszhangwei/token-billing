# Business Requirements Specification (BRS)

**Project**: Token Usage Billing
**Version**: 1.0
**Status**: Approved
**Date**: 2026-05-02
**Source story**: [../token-usage-billing-story.md](../token-usage-billing-story.md)

---

## 1. Business Vision

LLM API 平台依 token 使用量向客戶收費。每個客戶持有月度免費額度 (monthly included quota)；超出部分依方案規定費率 (overage rate per 1K tokens) 計費。本服務提供「提交用量、立即取得帳單」的端點，是平台營收結算的關鍵元件。

## 2. Business Goals

| ID | Goal | Measure |
|---|---|---|
| BG-1 | **Accurate Billing** — 帳單金額與實際 token 用量一致 | 所有計費誤差 ≤ $0.01 / 筆 |
| BG-2 | **Quota Management** — 客戶能在配額內免費使用 | 配額內請求 `total_charge = 0.00` |
| BG-3 | **Revenue Capture** — 超量使用不被遺漏 | 並發場景下總配額消耗 = 序列結果（強一致） |

## 3. Stakeholders

| 角色 | 利益 / 關切 |
|---|---|
| 平台營運（Sponsor） | 營收正確、可審計 |
| 客戶（API 使用者） | 計費透明、配額用量即時可見 |
| 開發團隊 | 契約清楚、可測試 |
| SRE | 服務可觀測、可監控異常 |
| 財務 / 稽核 | 帳單不可竄改、可追溯 |
| 客服 | 客訴查得到憑證 |
| 法務 / 合規 | 不收集 LLM prompt/completion 內容 |

## 4. Scope

### 4.1 In Scope (本次交付)

- 單一端點 `POST /api/usage` 接收 token 用量並回傳已計算之帳單。
- 依客戶當前訂閱方案的月配額與超量費率計算費用。
- 帳單持久化保存，供後續查詢使用。

### 4.2 Out of Scope (明確排除)

- 客戶 CRUD 端點。
- 歷史帳單查詢端點。
- 月配額重置排程 / cron job。
- 多幣種支援（本期固定 USD）。
- 跨客戶 rate limiting。
- 細粒度 RBAC（JWT scope ↔ customerId 授權矩陣）。

排除項目皆已列入 srs.md 之 Follow-up Backlog (FU-1 ~ FU-10) 並建議獨立 ticket 追蹤。

## 5. Business Constraints

| ID | Constraint | Source |
|---|---|---|
| BC-1 | 計費應採 IEEE 754 安全的十進位運算（`BigDecimal`），不得使用浮點數 | 財務合規 |
| BC-2 | 帳單記錄應為 append-only，禁止 UPDATE / DELETE | 財務合規、稽核 |
| BC-3 | 系統不得儲存 LLM prompt / completion 文字內容，僅儲存 token 計數 | 法務 / 隱私 |
| BC-4 | 端點需 JWT 驗證，token 由外部 IdP 發行 | 資訊安全 |

## 6. Assumptions

| ID | Assumption |
|---|---|
| BA-1 | 客戶資料、計費方案、訂閱關係由其他系統維護；本服務只讀取。 |
| BA-2 | 月配額為「日曆月（UTC）」概念，不採訂閱錨定週期 (anchor day)。 |
| BA-3 | 月內若發生方案變更，當下有效之方案決定該次計算的 quota 與費率；當月歷史用量不分方案累計。 |
| BA-4 | 所有金額皆以 USD 計價；多幣種非本期需求。 |
| BA-5 | 端點僅由內部 / 受信任服務呼叫；不對外公開。 |

## 7. Business-Level Acceptance Outcomes

當以下情境皆成立，視為 Business Goals 達成：

- BG-1：對於相同 (customer, prompt_tokens, completion_tokens) 之請求，計算結果可重現且符合「(超量 token / 1000) × 費率」公式。
- BG-2：當月累計用量 + 本次提交 ≤ 配額，回傳 `totalCharge = 0.00`。
- BG-3：同一客戶 50 個並發請求送入後，所有帳單之 `tokensFromQuota` 加總精確等於該月配額或實際用量（取小者），不超扣亦不漏扣。

## 8. Glossary

| 詞 | 定義 |
|---|---|
| Token | LLM 輸入或輸出之最小計費單位，整數。 |
| Prompt tokens | 輸入給模型的 token 數。 |
| Completion tokens | 模型輸出的 token 數。 |
| Total tokens | prompt + completion 之和。 |
| Monthly quota | 計費方案內含的月度免費 token 數。 |
| Overage tokens | 超出當月配額的 token 數。 |
| Overage rate per 1K | 每 1,000 個超量 token 的計費單價。 |
| Bill | 一次計費請求的計算結果記錄。 |
| Idempotency-Key | 用於避免重複扣費的客戶端去重識別碼。 |
| Calendar month (UTC) | 自 UTC 月初 00:00:00 起至月末 23:59:59.999 之區間。 |
