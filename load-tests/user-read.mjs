import { performance } from 'node:perf_hooks';

const baseUrl = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const userId = process.env.USER_ID || '1';
const stages = [
  { name: 'baseline', workers: 1, durationSeconds: 30 },
  { name: 'load-5', workers: 5, durationSeconds: 30 },
  { name: 'load-10', workers: 10, durationSeconds: 30 },
  { name: 'load-20', workers: 20, durationSeconds: 30 },
];

const target = new URL(baseUrl);
if (
  target.protocol !== 'http:'
  || !['localhost', '127.0.0.1', '[::1]'].includes(target.hostname)
) {
  throw new Error('This load probe only allows a local BASE_URL.');
}

if (!/^[1-9]\d*$/.test(userId)) {
  throw new Error('USER_ID must be a positive integer.');
}

function percentile(samples, fraction) {
  if (samples.length === 0) {
    return null;
  }
  const sorted = [...samples].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}

async function runStage(stage) {
  const samples = [];
  const failures = [];
  const startedAt = performance.now();
  const deadline = startedAt + stage.durationSeconds * 1000;
  const url = `${baseUrl}/users/${encodeURIComponent(userId)}`;

  async function worker() {
    while (performance.now() < deadline) {
      const requestStartedAt = performance.now();
      try {
        const response = await fetch(url, { signal: AbortSignal.timeout(10000) });
        await response.arrayBuffer();
        samples.push(performance.now() - requestStartedAt);
        if (response.status !== 200) {
          failures.push(`HTTP ${response.status}`);
        }
      } catch (error) {
        samples.push(performance.now() - requestStartedAt);
        failures.push(error.message);
      }
    }
  }

  await Promise.all(Array.from({ length: stage.workers }, () => worker()));
  const elapsedSeconds = (performance.now() - startedAt) / 1000;
  const requestsPerSecond = samples.length / elapsedSeconds;
  const formatPercentile = (fraction) => {
    const value = percentile(samples, fraction);
    return value === null ? 'n/a' : `${value.toFixed(1)}ms`;
  };

  console.log(
    `${stage.name}: workers=${stage.workers}, duration=${elapsedSeconds.toFixed(1)}s, ` +
      `requests=${samples.length}, failures=${failures.length}, ` +
      `req/s=${requestsPerSecond.toFixed(2)}, p50=${formatPercentile(0.5)}, ` +
      `p95=${formatPercentile(0.95)}, p99=${formatPercentile(0.99)}`,
  );

  if (failures.length > 0) {
    console.error(`First failure: ${failures[0]}`);
    process.exitCode = 1;
  }
}

async function warmUp(url, durationSeconds) {
  const deadline = performance.now() + durationSeconds * 1000;
  let requests = 0;

  while (performance.now() < deadline) {
    const response = await fetch(url, { signal: AbortSignal.timeout(10000) });
    await response.arrayBuffer();
    if (response.status !== 200) {
      throw new Error(`Warm-up failed: GET returned HTTP ${response.status}.`);
    }
    requests += 1;
  }

  console.log(`warm-up: duration=${durationSeconds}s, requests=${requests}`);
}

const url = `${baseUrl}/users/${encodeURIComponent(userId)}`;
console.log(
  `Read-only concurrency probe: GET ${url}; ` +
    `stages=${stages.map((stage) => stage.workers).join(',')}`,
);
const readinessResponse = await fetch(url, { signal: AbortSignal.timeout(10000) });
await readinessResponse.arrayBuffer();
if (readinessResponse.status !== 200) {
  throw new Error(`Readiness check failed: GET returned HTTP ${readinessResponse.status}.`);
}

await warmUp(url, 5);

for (const stage of stages) {
  await runStage(stage);
}
