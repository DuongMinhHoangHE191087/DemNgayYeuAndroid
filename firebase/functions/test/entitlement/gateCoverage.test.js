'use strict';

// M-84: quét tĩnh index.js và src/entitlement/index.js. Mọi callable PHẢI có hàng trong GATES (gatePolicy.js) và callable không `free`
// PHẢI gọi gateFor ngay sau limit. Thêm callable mới mà quên phân loại/quên cổng thì test này đỏ.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { GATES } = require('../../src/entitlement/gatePolicy');

const ROOT = path.join(__dirname, '..', '..');
const SOURCES = [path.join(ROOT, 'index.js'), path.join(ROOT, 'src', 'entitlement', 'index.js')].map((p) => fs.readFileSync(p, 'utf8'));
const ALLOWED_ON_REQUEST = ['appleNotifications'];

// [{ name, kind, body }] cho mọi `exports.<tên> = <kind>(`; body chạy tới export kế tiếp.
function exportsOf(src) {
  const found = [...src.matchAll(/^exports\.(\w+) = (\w+)\(/gm)];
  return found.map((m, i) => ({
    name: m[1],
    kind: m[2],
    body: src.slice(m.index, i + 1 < found.length ? found[i + 1].index : src.length),
  }));
}

const all = SOURCES.flatMap(exportsOf);
const callables = all.filter((e) => e.kind === 'onCall');

test('everyOnCallExport_isClassifiedInGatePolicy', () => {
  assert.ok(callables.length > 0, 'không tìm thấy callable nào');
  for (const { name } of callables) assert.ok(Object.hasOwn(GATES, name), `${name}: thiếu trong GATES (gatePolicy.js)`);
});

test('everyNonFreeCallable_callsGateFor', () => {
  for (const { name, body } of callables) {
    if (GATES[name]?.gate === 'free') continue;
    const re = new RegExp(`await limit\\('${name}', uid\\);\\r?\\n\\s*const \\{ tier \\} = await gateFor\\('${name}', uid\\);`);
    assert.match(body, re, `${name}: thiếu gateFor('${name}', uid) ngay sau await limit(...)`);
  }
});

test('gatePolicyHasNoStaleNames', () => {
  const names = new Set(callables.map((e) => e.name));
  for (const name of Object.keys(GATES)) assert.ok(names.has(name), `${name}: có trong GATES nhưng không còn callable onCall nào`);
});

// Callable đăng ký theo kiểu khác (exports['x'], gán hai bước...) sẽ lọt khỏi regex ở trên: mọi lời gọi onCall( phải là một export đã quét.
test('everyOnCallInvocation_isAScannedExport', () => {
  const invocations = SOURCES.reduce((n, src) => n + (src.match(/\bonCall\(/g) ?? []).length, 0);
  assert.equal(invocations, callables.length, 'có onCall( không nằm trong dạng `exports.<tên> = onCall(`, cổng quyền không kiểm được');
});

test('onRequestExports_areAllowListed', () => {
  const requests = all.filter((e) => e.kind === 'onRequest').map((e) => e.name);
  assert.deepEqual(requests.sort(), [...ALLOWED_ON_REQUEST].sort());
});
