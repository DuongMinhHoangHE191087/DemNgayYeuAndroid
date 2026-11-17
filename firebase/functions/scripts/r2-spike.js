#!/usr/bin/env node
'use strict';

// Spike R2 (task R2-09): đo hành vi thật của Cloudflare R2 với URL presigned, trước khi nối code thật.
//
//   $env:R2_ACCOUNT_ID / R2_BUCKET / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY = ... (token tạm, chỉ cho spike)
//   node firebase/functions/scripts/r2-spike.js --out docs/superpowers/spikes/r2-spike.md
//   node firebase/functions/scripts/r2-spike.js --dry-run --out <tệp ngoài docs/superpowers/spikes>
//
// Báo cáo được kiểm bởi scripts/ui/spike_gate.py (khoá = tên tệp, nên tệp phải tên r2-spike.md).
// KHÔNG BAO GIỜ in giá trị biến môi trường, URL, host, chữ ký hay nội dung lỗi của fetch (cause của undici chứa host =
// account id). Mọi lỗi chỉ in tên metric + mã HTTP hoặc "network-error". Chỉ gọi mức object (PUT/GET/DELETE một key).
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { createObjectStore } = require('../src/objectStore');

// Bảng này phải bằng khoá "r2-spike" trong scripts/ui/spike_requirements.json (gate so từng op/ngưỡng).
const METRICS = {
  put_ok: ['==', 1],
  put_wrong_content_type_rejected: ['==', 1],
  put_overwrite_rejected: ['==', 1],
  put_after_expiry_rejected: ['==', 1],
  content_length_enforced: ['>=', 0],
  get_ok: ['==', 1],
  get_content_type_matches: ['==', 1],
  range_206_with_total: ['==', 1],
  tail_range_206: ['==', 1],
  unsigned_range_206: ['==', 1],
  get_after_expiry_rejected: ['==', 1],
  delete_then_404: ['==', 1],
  delete_missing_tolerated: ['==', 1],
  p50_put_1mib_ms: ['<=', 5000],
  p50_range_get_ms: ['<=', 1000],
};
const OPS = { '==': (a, b) => a === b, '>=': (a, b) => a >= b, '<=': (a, b) => a <= b };
const HUGE = 999999999; // giá trị "hỏng" cho hai metric thời gian
const ENV_NAMES = ['R2_ACCOUNT_ID', 'R2_BUCKET', 'R2_ACCESS_KEY_ID', 'R2_SECRET_ACCESS_KEY'];

const passes = (name, v) => OPS[METRICS[name][0]](v, METRICS[name][1]);

// Tất cả đạt -> PASS; chỉ put_overwrite_rejected trượt -> FALLBACK:staging-copy (R2-12); còn lại FAIL.
function verdictOf(results) {
  const failed = Object.keys(METRICS).filter((n) => !passes(n, results[n]));
  if (!failed.length) return 'RESULT: PASS';
  if (failed.length === 1 && failed[0] === 'put_overwrite_rejected') return 'RESULT: FALLBACK:staging-copy';
  return 'RESULT: FAIL';
}

const synthetic = (over = {}) => ({
  ...Object.fromEntries(Object.entries(METRICS).map(([n, [, thr]]) => [n, thr])),
  ...over,
});

function selfCheck() {
  const cases = [
    [synthetic(), 'RESULT: PASS'],
    [synthetic({ put_overwrite_rejected: 0 }), 'RESULT: FALLBACK:staging-copy'],
    [synthetic({ put_ok: 0 }), 'RESULT: FAIL'],
  ];
  for (const [r, want] of cases) {
    if (verdictOf(r) !== want) {
      console.error(`self-check failed: expected ${want}`);
      process.exit(1);
    }
  }
}

function buildReport({ results, notes, dryRun }) {
  const lines = ['# R2 spike: presigned PUT/GET/Range/DELETE trên Cloudflare R2', ''];
  if (dryRun) lines.push('DRY-RUN with synthetic values: this is NOT a measurement of R2.', '');
  lines.push(`Date: ${new Date().toISOString()}`, `Node: ${process.version}`, '', '## Kết quả từng metric', '');
  for (const n of Object.keys(METRICS)) lines.push(`- ${n} = ${results[n]} (${notes[n]})`);
  lines.push(
    '',
    '## Ý nghĩa',
    '',
    '- content_length_enforced = 0: R2 không ép content-length đã ký, hãy rút reservationTtlMs còn 15 phút.',
    '- put_overwrite_rejected = 0: If-None-Match không chặn ghi đè, kết luận FALLBACK:staging-copy (task R2-12).',
    '',
    verdictOf(results),
    ...Object.entries(METRICS).map(([n, [op, thr]]) => `METRIC ${n} ${results[n]} ${op} ${thr}`),
  );
  return lines.join('\n') + '\n';
}

// ponytail: chạy tuần tự, mỗi bước độc lập; ceiling ~1 phút. Bước lỗi chỉ ghi giá trị hỏng, không dừng báo cáo.
async function measure() {
  const store = createObjectStore({
    accountId: process.env.R2_ACCOUNT_ID,
    bucket: process.env.R2_BUCKET,
    accessKeyId: process.env.R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.R2_SECRET_ACCESS_KEY,
    fetchImpl: (url, init = {}) => fetch(url, { ...init, signal: AbortSignal.timeout(30000) }),
  });
  const tag = crypto.randomBytes(6).toString('hex');
  const created = [];
  let n = 0;
  const newKey = () => {
    const k = `r2spike_${tag}_${n++}`;
    created.push(k);
    return k;
  };
  const fakeJpeg = (len) => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), crypto.randomBytes(len - 4)]);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const codeOf = (e) => /^R2 (?:GET|DELETE) (\d+)/.exec(e?.message ?? '')?.[1] ?? 'network-error';
  const median = (xs) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];

  // fetch không bao giờ ném ra ngoài: lỗi mạng -> status 'network-error' (không mang message).
  const http = async (url, init) => {
    try {
      const res = await fetch(url, { ...init, signal: AbortSignal.timeout(30000) });
      return { status: res.status, ct: res.headers.get('content-type'), buf: Buffer.from(await res.arrayBuffer()) };
    } catch {
      return { status: 'network-error', ct: null, buf: Buffer.alloc(0) };
    }
  };
  // PUT như OkHttp: header từ signUpload TRỪ content-length (body là Buffer nên fetch tự đặt Content-Length).
  const put = async (key, { sign = 1024, send = sign, signCt = 'image/jpeg', sendCt = signCt, ttl = 60, waitMs = 0, body } = {}) => {
    const { uploadUrl, headers } = store.signUpload({ publicId: key, contentType: signCt, contentLength: sign, expiresInSeconds: ttl });
    const { 'content-length': _omit, ...h } = headers;
    h['content-type'] = sendCt;
    if (waitMs) await sleep(waitMs);
    return (await http(uploadUrl, { method: 'PUT', headers: h, body: body ?? fakeJpeg(send) })).status;
  };

  const results = {};
  const notes = {};
  // fn trả [giá trị, ghi chú]; ném lỗi -> giá trị hỏng + mã.
  const step = async (name, fn) => {
    try {
      [results[name], notes[name]] = await fn();
    } catch (e) {
      results[name] = name.startsWith('p50_') ? HUGE : 0;
      notes[name] = codeOf(e);
    }
  };
  const expectStatus = (want) => async (status) => [status === want ? 1 : 0, `HTTP ${status}`];

  try {
    const key = newKey();
    const body = fakeJpeg(1024);
    await step('put_ok', async () => expectStatus(200)(await put(key, { body })));
    await step('put_wrong_content_type_rejected', async () => expectStatus(403)(await put(newKey(), { sendCt: 'image/png' })));
    await step('put_overwrite_rejected', async () => expectStatus(412)(await put(key)));
    await step('put_after_expiry_rejected', async () => expectStatus(403)(await put(newKey(), { ttl: 1, waitMs: 3000 })));
    await step('content_length_enforced', async () => expectStatus(403)(await put(newKey(), { sign: 1024, send: 1025 })));

    const get = await http(store.deliveryUrl(key, 'IMAGE', 300, 'jpg'));
    await step('get_ok', async () => [get.status === 200 && get.buf.equals(body) ? 1 : 0, `HTTP ${get.status}`]);
    await step('get_content_type_matches', async () => [get.ct === 'image/jpeg' ? 1 : 0, `HTTP ${get.status}`]);
    await step('range_206_with_total', async () => {
      const r = await store.fetchResource(key, 'IMAGE');
      return [r && r.bytes === 1024 && r.head.length === 32 ? 1 : 0, r ? 'ok' : 'null'];
    });
    await step('tail_range_206', async () => {
      const r = await http(store.deliveryUrl(key, 'IMAGE', 300, 'jpg'), { headers: { Range: 'bytes=-16' } });
      return [r.status === 206 && r.buf.length === 16 ? 1 : 0, `HTTP ${r.status}`];
    });
    await step('unsigned_range_206', async () => {
      const r = await http(store.deliveryUrl(key, 'IMAGE', 300, 'jpg'), { headers: { Range: 'bytes=100-199' } });
      return [r.status === 206 && r.buf.length === 100 ? 1 : 0, `HTTP ${r.status}`];
    });
    await step('get_after_expiry_rejected', async () => {
      const url = store.deliveryUrl(key, 'IMAGE', 1, 'jpg');
      await sleep(3000);
      return expectStatus(403)((await http(url)).status);
    });
    await step('p50_range_get_ms', async () => {
      const ms = [];
      for (let i = 0; i < 10; i++) {
        const t = Date.now();
        if (!(await store.fetchResource(key, 'IMAGE'))) return [HUGE, 'HTTP 404'];
        ms.push(Date.now() - t);
      }
      return [median(ms), 'ms'];
    });
    await step('p50_put_1mib_ms', async () => {
      const ms = [];
      for (let i = 0; i < 5; i++) {
        const t = Date.now();
        const s = await put(newKey(), { sign: 1048576 });
        if (s !== 200) return [HUGE, `HTTP ${s}`];
        ms.push(Date.now() - t);
      }
      return [median(ms), 'ms'];
    });
    await step('delete_then_404', async () => {
      await store.destroy(key, 'IMAGE');
      return expectStatus(404)((await http(store.deliveryUrl(key, 'IMAGE', 60, 'jpg'))).status);
    });
    await step('delete_missing_tolerated', async () => {
      await store.destroy(newKey(), 'IMAGE');
      return [1, 'no throw'];
    });
  } finally {
    const failed = (await Promise.all(created.map((k) => store.destroy(k, 'IMAGE').then(() => 0, () => 1)))).some(Boolean);
    if (failed) console.error('cleanup incomplete: delete objects with prefix r2spike_ in the dashboard');
  }
  return { results, notes };
}

async function main() {
  const argv = process.argv.slice(2);
  const dryRun = argv.includes('--dry-run');
  const outIdx = argv.indexOf('--out');
  const out = outIdx >= 0 ? argv[outIdx + 1] : undefined;

  if (dryRun && out && path.resolve(out).replace(/\\/g, '/').includes('docs/superpowers/spikes')) {
    console.error('--dry-run must not write into docs/superpowers/spikes (a fake PASS must never land there)');
    process.exit(2);
  }
  selfCheck();

  let report;
  if (dryRun) {
    const notes = Object.fromEntries(Object.keys(METRICS).map((k) => [k, 'synthetic']));
    report = buildReport({ results: synthetic(), notes, dryRun });
  } else {
    const missing = ENV_NAMES.filter((k) => !process.env[k]);
    if (missing.length) {
      console.error(`Missing env: ${missing.join(', ')}`);
      process.exit(2);
    }
    report = buildReport({ ...(await measure()), dryRun });
  }

  if (out) {
    fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });
    fs.writeFileSync(out, report, 'utf8');
    console.log(`wrote ${out}`);
  } else {
    process.stdout.write(report);
  }
}

main().catch(() => {
  console.error('r2-spike: unexpected error'); // không in message: có thể chứa host
  process.exit(1);
});
