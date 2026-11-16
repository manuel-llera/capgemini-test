import { performance } from 'node:perf_hooks';

const baseUrl = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const userId = process.env.USER_ID || '1';
const pauseMs = 1000;
const stages = [
  { name: 'baseline', workers: 1, durationSeconds: 30 },
  { name: 'light-load', workers: 5, durationSeconds: 60 },
];

function percentile(samples, fraction) {
  const sorted = [...samples].sort((left, right) => left - right);
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}

async function runStage(stage) {
  const samples = [];
  const failures = [];
  const startedAt = performance.now();
  const deadline = Date.now() + stage.durationSeconds * 1000;
  const url = `${baseUrl}/users/${encodeURIComponent(userId)}`;

  async function worker() {
    while (Date.now() < deadline) {
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

      if (Date.now() < deadline) {
        await new Promise((resolve) => setTimeout(resolve, pauseMs));
      }
    }
  }

  await Promise.all(Array.from({ length: stage.workers }, () => worker()));
  const elapsedSeconds = (performance.now() - startedAt) / 1000;
  const requestsPerSecond = samples.length / elapsedSeconds;

  console.log(
    `${stage.name}: workers=${stage.workers}, requests=${samples.length}, ` +
      `failures=${failures.length}, req/s=${requestsPerSecond.toFixed(2)}, ` +
      `p50=${percentile(samples, 0.5)?.toFixed(1) ?? 'n/a'}ms, ` +
      `p95=${percentile(samples, 0.95)?.toFixed(1) ?? 'n/a'}ms, ` +
      `p99=${percentile(samples, 0.99)?.toFixed(1) ?? 'n/a'}ms`,
  );

  if (failures.length > 0) {
    console.error(`First failure: ${failures[0]}`);
    process.exitCode = 1;
  }
}

console.log(`Read-only load probe: GET ${baseUrl}/users/${userId}`);
for (const stage of stages) {
  await runStage(stage);
}
