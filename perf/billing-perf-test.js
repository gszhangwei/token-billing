/**
 * k6 performance test for POST /api/usage
 *
 * Target: NFR-PERF-1 — p95 latency ≤ 200 ms at ≤100 RPS with DB on same network.
 *
 * Usage:
 *   k6 run --env BASE_URL=http://localhost:8080 --env JWT_TOKEN=<bearer-token> billing-perf-test.js
 *
 * For local H2 testing (no auth), omit JWT_TOKEN and set BASE_URL to the no-auth endpoint.
 *
 * Environment variables:
 *   BASE_URL   - Base URL of the application (default: http://localhost:8080)
 *   JWT_TOKEN  - Bearer token with billing:write scope (required for JWT-protected instances)
 */

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const p95Latency = new Trend('billing_p95_latency', true);
const requestCount = new Counter('billing_request_count');
const errorRate = new Rate('billing_error_rate');

export const options = {
    scenarios: {
        steady_load: {
            executor: 'constant-arrival-rate',
            rate: 100,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 20,
            maxVUs: 50,
        },
    },
    thresholds: {
        'http_req_duration{scenario:steady_load}': ['p(95)<200'],
        'billing_error_rate': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN || '';

const CUSTOMERS = ['CUST-001', 'CUST-002', 'CUST-003'];

export default function () {
    const customerId = CUSTOMERS[Math.floor(Math.random() * CUSTOMERS.length)];
    const promptTokens = Math.floor(Math.random() * 5000) + 100;
    const completionTokens = Math.floor(Math.random() * 2000) + 50;

    const headers = {
        'Content-Type': 'application/json',
    };
    if (JWT_TOKEN) {
        headers['Authorization'] = `Bearer ${JWT_TOKEN}`;
    }

    const payload = JSON.stringify({
        customerId: customerId,
        promptTokens: promptTokens,
        completionTokens: completionTokens,
    });

    const res = http.post(`${BASE_URL}/api/usage`, payload, { headers });

    const success = check(res, {
        'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
        'response has billId or problem': (r) => {
            if (r.status === 201) {
                const body = JSON.parse(r.body);
                return body.billId !== undefined;
            }
            return true;
        },
        'latency under 500ms': (r) => r.timings.duration < 500,
    });

    p95Latency.add(res.timings.duration);
    requestCount.add(1);
    errorRate.add(!success);
}
