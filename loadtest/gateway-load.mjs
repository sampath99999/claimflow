// Small load test against the running gateway. No dependencies, Node 20+.
//
//   node loadtest/gateway-load.mjs read  --seconds 30 --concurrency 20
//   node loadtest/gateway-load.mjs write --requests 300 --concurrency 10
//
// "read" mixes claim list (80%) and claim detail (20%) calls as an adjuster.
// "write" submits claims as a member, which also runs the eligibility call to member-service,
// the Postgres insert and the Kafka publish.

const BASE = process.env.GATEWAY_URL ?? 'http://localhost:8180';
const [mode = 'read', ...rest] = process.argv.slice(2);
const opts = Object.fromEntries(rest.join(' ').split('--').filter(Boolean).map((s) => s.trim().split(/\s+/)));
const concurrency = Number(opts.concurrency ?? 20);

async function login(username, password) {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error(`login failed for ${username}: ${res.status}`);
  return (await res.json()).accessToken;
}

function percentile(sorted, p) {
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

function report(label, latencies, errors, elapsedMs) {
  const sorted = [...latencies].sort((a, b) => a - b);
  const fmt = (n) => n.toFixed(1);
  console.log(`${label}: ${sorted.length} ok, ${errors} errors in ${(elapsedMs / 1000).toFixed(1)} s, concurrency ${concurrency}`);
  console.log(`  throughput ${fmt(sorted.length / (elapsedMs / 1000))} req/s`);
  console.log(`  p50 ${fmt(percentile(sorted, 50))} ms  p95 ${fmt(percentile(sorted, 95))} ms  p99 ${fmt(percentile(sorted, 99))} ms  max ${fmt(sorted.at(-1))} ms`);
}

async function timed(fn) {
  const start = performance.now();
  const res = await fn();
  await res.arrayBuffer();
  return { ok: res.ok, ms: performance.now() - start, status: res.status };
}

async function readTest() {
  const seconds = Number(opts.seconds ?? 30);
  const token = await login('ava', 'adjuster-demo');
  const headers = { Authorization: `Bearer ${token}` };
  const list = await (await fetch(`${BASE}/api/claims?size=20`, { headers })).json();
  const ids = list.content.map((c) => c.id);
  if (ids.length === 0) throw new Error('No claims found; submit a few first');

  const latencies = [];
  let errors = 0;
  // Warm up the JIT and connection pools before measuring.
  for (let i = 0; i < 200; i++) await timed(() => fetch(`${BASE}/api/claims?page=0&size=10`, { headers }));

  const deadline = Date.now() + seconds * 1000;
  const start = performance.now();
  await Promise.all(Array.from({ length: concurrency }, async (_, worker) => {
    let i = worker;
    while (Date.now() < deadline) {
      const url = i++ % 5 === 0
        ? `${BASE}/api/claims/${ids[i % ids.length]}`
        : `${BASE}/api/claims?page=0&size=10&status=SUBMITTED&status=IN_REVIEW`;
      const r = await timed(() => fetch(url, { headers }));
      if (r.ok) latencies.push(r.ms); else errors++;
    }
  }));
  report('read (list 80% / detail 20%)', latencies, errors, performance.now() - start);
}

async function writeTest() {
  const total = Number(opts.requests ?? 300);
  const token = await login('james', 'member-demo');
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  const serviceDate = new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10);
  const runId = Date.now() % 100000;
  let next = 0;
  const latencies = [];
  let errors = 0;
  const statuses = {};

  const start = performance.now();
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (next < total) {
      const n = next++;
      // Unique amount per request so the duplicate-claim rule does not reject them.
      const amount = (100 + runId / 1000 + n / 100).toFixed(2);
      const body = JSON.stringify({
        providerNpi: '1765432103',
        providerName: 'Load Test Clinic',
        serviceDate,
        diagnosisCode: 'J06.9',
        lines: [{ procedureCode: '99213', description: 'Office visit', units: 1, chargeAmount: Number(amount) }],
      });
      const r = await timed(() => fetch(`${BASE}/api/claims`, { method: 'POST', headers, body }));
      statuses[r.status] = (statuses[r.status] ?? 0) + 1;
      if (r.status === 201) latencies.push(r.ms); else errors++;
    }
  }));
  report('write (submit claim)', latencies, errors, performance.now() - start);
  console.log('  status codes', statuses);
}

await (mode === 'write' ? writeTest() : readTest());
