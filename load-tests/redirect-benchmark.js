import http from 'k6/http';
import { check, sleep } from 'k6';

// -----------------------------------------------------------------------------
// k6 Load Test: Ultra-Low-Latency Redirect Verification
// Evaluates p95 and p99 SLA under concurrent traffic
// -----------------------------------------------------------------------------

export const options = {
  stages: [
    { duration: '10s', target: 50 },   // Warm-up ramp
    { duration: '30s', target: 200 },  // Sustained high traffic
    { duration: '10s', target: 0 },    // Ramp down
  ],
  thresholds: {
    // Senior SLA Criteria: 95% of redirect requests must complete in under 10ms
    http_req_duration: ['p(95)<10', 'p(99)<25'],
    http_req_failed: ['rate<0.01'], // < 1% error rate
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost';
const TEST_CODE = __ENV.TEST_SHORT_CODE || 'testUrl1';

export function setup() {
  // Pre-seed benchmark test URL so test is completely self-contained
  http.post(`${BASE_URL}/api/v1/urls`, JSON.stringify({
    longUrl: 'https://google.com/search?q=system+design+interview',
    customAlias: TEST_CODE,
  }), {
    headers: { 'Content-Type': 'application/json' },
  });
  return { code: TEST_CODE };
}

export default function () {
  const params = {
    redirects: 0, // Do NOT follow redirect so we measure pure redirect proxy overhead
    headers: {
      'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
      'Referer': 'https://google.com/search?q=scalelink',
      'X-Forwarded-For': '203.0.113.195',
    },
  };

  const res = http.get(`${BASE_URL}/${TEST_CODE}`, params);

  check(res, {
    'status is 302 Found': (r) => r.status === 302,
    'has Location header': (r) => r.headers['Location'] !== undefined,
  });

  sleep(0.05); // 50ms pause between virtual user iterations
}
