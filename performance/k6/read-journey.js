import http from 'k6/http'
import { check, group, sleep } from 'k6'

const apiBaseUrl = (__ENV.BASE_URL || 'http://localhost:8080/api/v1').replace(/\/+$/, '')
const testPeriod = __ENV.TEST_PERIOD ? `?period=${encodeURIComponent(__ENV.TEST_PERIOD)}` : ''

export const options = {
  scenarios: {
    authenticated_read_journey: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 5 },
        { duration: '45s', target: 10 },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
}

export function setup() {
  const email = requiredEnv('TEST_EMAIL')
  const password = requiredEnv('TEST_PASSWORD')
  const response = http.post(`${apiBaseUrl}/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'auth-login' },
  })

  const authenticated = check(response, {
    'login returns 200': (result) => result.status === 200,
    'login returns an access token': (result) => Boolean(result.json('accessToken')),
  })
  if (!authenticated) {
    throw new Error(`Could not authenticate the performance-test account (HTTP ${response.status}).`)
  }

  return { authorization: `Bearer ${response.json('accessToken')}` }
}

export default function (session) {
  const params = { headers: { Authorization: session.authorization } }
  group('authenticated read journey', () => {
    const [profile, dashboard, transactions] = http.batch([
      ['GET', `${apiBaseUrl}/users/me`, null, { ...params, tags: { endpoint: 'current-user' } }],
      ['GET', `${apiBaseUrl}/reports/dashboard${testPeriod}`, null, { ...params, tags: { endpoint: 'dashboard' } }],
      ['GET', `${apiBaseUrl}/transactions?page=0&size=10`, null, { ...params, tags: { endpoint: 'transaction-history' } }],
    ])

    check(profile, { 'current user returns 200': (result) => result.status === 200 })
    check(dashboard, { 'dashboard returns 200': (result) => result.status === 200 })
    check(transactions, { 'transaction history returns 200': (result) => result.status === 200 })
  })
  sleep(1)
}

function requiredEnv(name) {
  const value = __ENV[name]
  if (!value) throw new Error(`${name} must be set for the performance test.`)
  return value
}
