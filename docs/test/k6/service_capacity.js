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
  scenarios: {
    stepped_capacity: {
      executor: 'ramping-vus',
      startVUs: Number(__ENV.START_VUS || '20'),
      stages: [
        { duration: __ENV.STAGE_1_DURATION || '30s', target: Number(__ENV.STAGE_1_VUS || '50') },
        { duration: __ENV.STAGE_2_DURATION || '30s', target: Number(__ENV.STAGE_2_VUS || '100') },
        { duration: __ENV.STAGE_3_DURATION || '30s', target: Number(__ENV.STAGE_3_VUS || '150') },
        { duration: __ENV.STAGE_4_DURATION || '30s', target: Number(__ENV.STAGE_4_VUS || '200') },
        { duration: __ENV.STAGE_5_DURATION || '15s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
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
