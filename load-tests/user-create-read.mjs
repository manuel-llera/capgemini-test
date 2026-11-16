import { randomUUID } from 'node:crypto';
import { performance } from 'node:perf_hooks';

const baseUrl = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const userIdToCheck = process.env.USER_ID || '1';
const iterations = Number(process.env.ITERATIONS || 10);
const pauseMs = Number(process.env.PAUSE_MS || 1000);
const runId = randomUUID().replaceAll('-', '');
const emailPrefix = `loadtest.${runId}`;

if (process.env.ALLOW_TEST_WRITES !== 'yes') {
  console.error(
    'This scenario creates database records. Confirm the application uses the test profile, ' +
      'then set ALLOW_TEST_WRITES=yes to continue.',
  );
  process.exit(2);
}

const target = new URL(baseUrl);
if (
  target.protocol !== 'http:'
  || !['localhost', '127.0.0.1', '[::1]'].includes(target.hostname)
) {
  throw new Error('This load probe only allows a local BASE_URL.');
}

if (!Number.isInteger(iterations) || iterations < 1 || iterations > 100) {
  throw new Error('ITERATIONS must be an integer between 1 and 100.');
}

if (!Number.isInteger(pauseMs) || pauseMs < 0) {
  throw new Error('PAUSE_MS must be a non-negative integer.');
}

function percentile(samples, fraction) {
  if (samples.length === 0) {
    return 'n/a';
  }
  const sorted = [...samples].sort((left, right) => left - right);
  return `${sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)].toFixed(1)}ms`;
}

function summarize(label, samples) {
  console.log(
    `${label}: count=${samples.length}, p50=${percentile(samples, 0.5)}, ` +
      `p95=${percentile(samples, 0.95)}, p99=${percentile(samples, 0.99)}`,
  );
}

function userForIteration(index) {
  const unique = randomUUID().replaceAll('-', '');
  const phone = String(Number.parseInt(unique.slice(0, 8), 16) % 1_000_000_000).padStart(
    9,
    '0',
  );

  return {
    name: 'pablo',
    email: `${emailPrefix}.${index}@example.com`,
    phone,
    rol: 'admin',
    dni: `${unique.slice(0, 8).toUpperCase()}A`,
  };
}

async function request(url, options) {
  return fetch(url, { ...options, signal: AbortSignal.timeout(10000) });
}

const createLatencies = [];
const readLatencies = [];
const failures = [];
let completedFlows = 0;

console.log(
  `Create/read probe: ${iterations} sequential user flows against ${baseUrl}; ` +
    `pause=${pauseMs}ms`,
);

const readinessResponse = await request(
  `${baseUrl}/users/${encodeURIComponent(userIdToCheck)}`,
);
if (readinessResponse.status !== 200) {
  throw new Error(
    `Readiness check failed: GET /users/${userIdToCheck} returned HTTP ${readinessResponse.status}.`,
  );
}
await readinessResponse.arrayBuffer();

const runStartedAt = performance.now();

for (let index = 1; index <= iterations; index += 1) {
  const user = userForIteration(index);
  let createdUserId;
  const createStartedAt = performance.now();
  let createLatencyRecorded = false;

  try {
    const createResponse = await request(`${baseUrl}/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(user),
    });
    const createBody = await createResponse.text();
    createLatencies.push(performance.now() - createStartedAt);
    createLatencyRecorded = true;

    if (createResponse.status !== 201) {
      failures.push(`create ${index}: HTTP ${createResponse.status} (${createBody})`);
    } else {
      const created = JSON.parse(createBody);
      if (!Number.isSafeInteger(created.id) || created.id <= 0) {
        failures.push(`create ${index}: response did not contain a valid user ID`);
      } else {
        createdUserId = created.id;
      }
    }
  } catch (error) {
    if (!createLatencyRecorded) {
      createLatencies.push(performance.now() - createStartedAt);
    }
    failures.push(`create ${index}: ${error.message}`);
  }

  if (createdUserId !== undefined) {
    const readStartedAt = performance.now();
    let readLatencyRecorded = false;
    try {
      const readResponse = await request(
        `${baseUrl}/users/${encodeURIComponent(createdUserId)}`,
      );
      const readBody = await readResponse.text();
      readLatencies.push(performance.now() - readStartedAt);
      readLatencyRecorded = true;

      if (readResponse.status !== 200) {
        failures.push(`read ${index}: HTTP ${readResponse.status} (${readBody})`);
      } else {
        const retrieved = JSON.parse(readBody);
        if (retrieved.id !== createdUserId || retrieved.email !== user.email) {
          failures.push(`read ${index}: returned user does not match the created user`);
        } else {
          completedFlows += 1;
        }
      }
    } catch (error) {
      if (!readLatencyRecorded) {
        readLatencies.push(performance.now() - readStartedAt);
      }
      failures.push(`read ${index}: ${error.message}`);
    }
  }

  if (index < iterations && pauseMs > 0) {
    await new Promise((resolve) => setTimeout(resolve, pauseMs));
  }
}

const elapsedSeconds = (performance.now() - runStartedAt) / 1000;

console.log(
  `Results: flows=${completedFlows}/${iterations}, failures=${failures.length}, ` +
    `elapsed=${elapsedSeconds.toFixed(1)}s, flows/s=${(completedFlows / elapsedSeconds).toFixed(2)}`,
);
summarize('POST /users', createLatencies);
summarize('GET /users/{id}', readLatencies);
console.log(`Cleanup SQL: DELETE FROM users WHERE email LIKE '${emailPrefix}.%@example.com';`);

if (failures.length > 0) {
  console.error(`First failure: ${failures[0]}`);
  process.exitCode = 1;
}