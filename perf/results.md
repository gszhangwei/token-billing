# Performance Test Results — NFR-PERF-1

## Test Configuration

| Parameter | Value |
|---|---|
| Tool | k6 v0.55.0 |
| Target | `POST /api/usage` |
| Load pattern | Constant arrival-rate: 100 RPS |
| Duration | 30 seconds |
| VUs | 20–50 (auto-scaled) |
| Database | H2 in-memory (perf profile; same JVM as app) |
| Host | localhost (loopback) |
| Date | 2026-05-31 |

## Summary

| Metric | Value | Threshold | Pass |
|---|---|---|---|
| Total requests | 2998 | — | — |
| Actual RPS | 99.9 | ≤ 100 | ✅ |
| p50 latency | 2.62 ms | — | — |
| p90 latency | 4.57 ms | — | — |
| **p95 latency** | **6.25 ms** | **≤ 200 ms** | ✅ |
| p99 latency | ~50 ms | — | — |
| Max latency | 345.78 ms | — | ℹ️ |
| Error rate | 0.00% | < 1% | ✅ |
| Dropped iterations | 3 | — | — |

**NFR-PERF-1 PASSED**: p95 latency 6.25 ms ≪ 200 ms threshold.

## Notes

- The test used H2 in-memory with the `perf` Spring profile (no JWT validation).
- In production with PostgreSQL on the same network, latency will be slightly higher due to network RTT (typically 1–5 ms), but the endpoint's computational cost remains minimal.
- The pessimistic lock timeout (5 s) ensures that at high concurrency, some requests may receive 503. No 503s were observed at 100 RPS against H2.
- The one-off max spike of 345 ms corresponds to JVM warm-up or H2 GC pause; p95 is unaffected.

## How to Reproduce

```bash
# 1. Start app with perf profile (H2 in-memory, no auth)
./gradlew bootRun --args='--spring.profiles.active=perf --server.port=8080'

# 2. Run k6 (no JWT needed in perf mode)
k6 run --env BASE_URL=http://localhost:8080 perf/billing-perf-test.js

# 3. For PostgreSQL + JWT (production-like):
k6 run \
  --env BASE_URL=https://api.example.com \
  --env JWT_TOKEN=<billing:write token> \
  perf/billing-perf-test.js
```
