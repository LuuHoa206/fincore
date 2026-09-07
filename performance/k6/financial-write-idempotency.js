import http from 'k6/http'
import { check, sleep } from 'k6'

const apiBaseUrl = (__ENV.BASE_URL || 'http://localhost:8080/api/v1').replace(/\/+$/, '')

export const options = {
  scenarios: {
    same_request_retries: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 12 },
        { duration: '20s', target: 12 },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1200', 'p(99)<2000'],
  },
}

export function setup() {
  const email = requiredEnv('TEST_EMAIL')
  const password = requiredEnv('TEST_PASSWORD')
  const walletId = requiredEnv('TEST_WALLET_ID')
  const categoryId = requiredEnv('TEST_INCOME_CATEGORY_ID')
  const login = http.post(`${apiBaseUrl}/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'auth-login' },
  })

  const authenticated = check(login, {
    'login returns 200': (result) => result.status === 200,
    'login returns an access token': (result) => Boolean(result.json('accessToken')),
  })
  if (!authenticated) {
    throw new Error(`Could not authenticate the performance-test account (HTTP ${login.status}).`)
  }

  return {
    authorization: `Bearer ${login.json('accessToken')}`,
    idempotencyKey: `k6-idempotency-${Date.now()}-${Math.floor(Math.random() * 1000000)}`,
    payload: {
      walletId,
      categoryId,
      transactionType: 'INCOME',
      amount: 1,
      description: 'Disposable k6 idempotency test',
      occurredAt: new Date().toISOString(),
      applyAllocationRule: false,
    },
  }
}

export default function (session) {
  const response = http.post(`${apiBaseUrl}/transactions`, JSON.stringify(session.payload), {
    headers: {
      Authorization: session.authorization,
      'Content-Type': 'application/json',
      'Idempotency-Key': session.idempotencyKey,
    },
    tags: { endpoint: 'transaction-idempotency-retry' },
  })

  check(response, {
    'idempotent transaction returns 201': (result) => result.status === 201,
    'idempotent transaction returns one transaction id': (result) => Boolean(result.json('id')),
  })
  sleep(0.5)
}

function requiredEnv(name) {
  const value = __ENV[name]
  if (!value) throw new Error(`${name} must be set for the financial write performance test.`)
  return value
}
