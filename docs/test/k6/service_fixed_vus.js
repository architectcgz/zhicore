import http from 'k6/http';
import { check, sleep } from 'k6';

const targetName = __ENV.TARGET_NAME || 'service';
const targetUrl = __ENV.TARGET_URL;
const authToken = __ENV.AUTH_TOKEN || '';
const userId = __ENV.USER_ID || '';
const userRoles = __ENV.USER_ROLES || '';
const extraHeadersJson = __ENV.EXTRA_HEADERS_JSON || '{}';
const sleepMs = Number(__ENV.SLEEP_MS || '0');

if (!targetUrl) {
  throw new Error('TARGET_URL is required');
}

const extraHeaders = JSON.parse(extraHeadersJson);
const headers = {
  ...extraHeaders,
};

if (authToken) {
  headers.Authorization = `Bearer ${authToken}`;
}
if (userId) {
  headers['X-User-Id'] = userId;
}
if (userRoles) {
  headers['X-User-Roles'] = userRoles;
}

export const options = {
  vus: Number(__ENV.VUS || '100'),
  duration: __ENV.DURATION || '20s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
  const response = http.get(targetUrl, {
    headers,
    tags: { target: targetName },
  });

  check(response, {
    'status is 200': (r) => r.status === 200,
  });

  if (sleepMs > 0) {
    sleep(sleepMs / 1000);
  }
}
