'use strict';
// B-13: mọi callable (onCall) phải có enforceAppCheck: true; trigger/schedule/webhook không bị kiểm.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// Lấy chuỗi `{ ... }` cân bằng ngoặc bắt đầu tại vị trí `start` (ký tự '{').
function balancedBraces(src, start) {
  let depth = 0;
  for (let i = start; i < src.length; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}' && --depth === 0) return src.slice(start, i + 1);
  }
  return '';
}

// Trả về danh sách callable thiếu cờ. Đối số đầu của onCall là literal `{...}` hoặc tên const object.
function findViolations(src) {
  const bad = [];
  let count = 0;
  for (const m of src.matchAll(/\bonCall\(\s*/g)) {
    const at = m.index + m[0].length;
    const name = (src.slice(0, m.index).match(/exports\.(\w+)\s*=\s*$/) || [])[1] || `@${m.index}`;
    let opts = '';
    if (src[at] === '{') opts = balancedBraces(src, at);
    else {
      const id = (src.slice(at).match(/^(\w+)/) || [])[1];
      const decl = id && new RegExp(`\\bconst\\s+${id}\\s*=\\s*\\{`).exec(src);
      if (decl) opts = balancedBraces(src, decl.index + decl[0].length - 1);
    }
    count++;
    if (!/\benforceAppCheck\s*:\s*true\b/.test(opts)) bad.push(name);
  }
  return { count, bad };
}

test('mọi onCall trong index.js có enforceAppCheck: true', () => {
  const src = fs.readFileSync(path.join(__dirname, '..', 'index.js'), 'utf8');
  const { count, bad } = findViolations(src);
  assert.ok(count >= 5, `phải tìm thấy các callable (thấy ${count})`);
  assert.deepEqual(bad, [], `callable thiếu enforceAppCheck: true: ${bad.join(', ')}`);
});

test('đối chứng: phát hiện callable thiếu cờ (literal và options dùng chung)', () => {
  const sample = `
    const shared = { region: 'x', maxInstances: 1 };
    const ok = { enforceAppCheck: true };
    exports.a = onCall({ region: 'x' }, async () => {});
    exports.b = onCall(shared, async () => {});
    exports.c = onCall(ok, async () => {});
    exports.d = onCall({ enforceAppCheck: true }, async () => {});
    exports.e = onCall({ enforceAppCheck: false }, async () => {});
    exports.t = onDocumentWritten({ document: 'x' }, async () => {});
  `;
  const { count, bad } = findViolations(sample);
  assert.equal(count, 5);
  assert.deepEqual(bad, ['a', 'b', 'e']);
});
