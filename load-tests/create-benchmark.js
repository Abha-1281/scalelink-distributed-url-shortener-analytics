import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '5s', target: 20 },
    { duration: '15s', target: 50 },
    { duration: '5s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<50'], // Link creation write path SLA < 50ms
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost';

export default function () {
  const payload = JSON.stringify({
    longUrl: `https://example.com/item/${Math.floor(Math.random() * 1000000)}`,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Forwarded-For': `192.168.1.${__VU % 250}`, // Distribute IPs to test rate limiting
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/urls`, payload, params);

  check(res, {
    'status is 201 Created or 429 RateLimited': (r) => r.status === 201 || r.status === 429,
  });

  sleep(0.1);
}
