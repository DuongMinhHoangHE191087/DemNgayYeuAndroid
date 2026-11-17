import test from 'node:test';
import assert from 'node:assert/strict';
import { checkMatrix, checkRawVectors, collectTaskIds } from '../p8-matrix-check.mjs';

// In-memory P8 matrix: a well-formed fixture (positive control) and one-rule mutations of it.
const TASKS = new Set(['B-01', 'D-02', 'U-03', 'D-25a']);
const pad = (n, w) => String(n).padStart(w, '0');
const HEAD = {
  fp: ['id', 'khu vực', 'tính năng (vi)', 'nguồn Android (file:ký hiệu)', 'hành vi chính (≤2 câu)', 'dữ liệu/state chạm tới', 'VIP?', 'rủi ro iOS', 'phần chịu trách nhiệm (D/B/U/N/M/Q)', 'cách kiểm chứng', 'ưu tiên', 'task sở hữu (id có trong bảng task, hoặc pending-owner / GAP)'],
  mq: ['id', 'tên', 'FP liên quan', 'thiết bị', 'điều kiện tiên quyết', 'các bước', 'kết quả mong đợi', 'thời lượng'],
  gv: ['id', 'hàm được kiểm (file:dòng)', 'đầu vào và đầu ra', 'số vector tối thiểu và tên các biên', 'tệp golden', 'nơi sinh (JUnit Android, host win)', 'ghi chú cho bên dùng (P2 và P7)'],
  cross: ['id P8', 'tệp tạm của phần khác (task sinh; task đọc)', 'quan hệ', 'tên tệp chính thức và việc cần làm'],
  pf: ['id', 'file:dòng', 'pitfall', 'hậu quả trên iOS', 'cách chặn', 'task/phần xử lý', 'cách kiểm'],
  v: ['id', 'phase', 'files (owned)', 'change', 'test or command', 'depends on', 'model', 'host (win/mac)', 'size (S<=0.5d, M<=2d)'],
};
// Replay classes named in the GV "nơi sinh" column, keyed by GV number.
const REPLAY = { 5: 'ZodiacReplayTest', 6: 'AgeReplayTest', 7: 'StartDateReplayTest', 8: 'BadgeThemeReplayTest', 23: 'LocaleStringsReplayTest', 24: 'CoupleCodeReplayTest', 27: 'MediaValidateReplayTest', 29: 'PersonalizedGiftReplayTest' };
const ROOT = 'shared/src/commonTest/kotlin/com/example/golden';
const V_FILES = {
  'V-10': `${ROOT}/LocaleStringsReplayTest.kt (new), ${ROOT}/CoupleCodeReplayTest.kt (new), ${ROOT}/PersonalizedGiftReplayTest.kt (new)`,
  'V-11': 'shared/src/iosTest/kotlin/com/example/golden/GoldenReplayIosTest.kt (new)',
  'V-16': ['ZodiacReplayTest', 'AgeReplayTest', 'StartDateReplayTest', 'BadgeThemeReplayTest', 'MediaValidateReplayTest'].map((c) => `${ROOT}/${c}.kt (new)`).join(', '),
};
const fpRow = (id, owner) => [id, 'Trang chủ', 'Tính năng mẫu', 'HomeScreen.kt:10', 'Hiển thị mẫu.', 'state mẫu', 'Không', 'Không', 'U', 'T:HomeTest', 'P1', owner];

function fixture() {
  return {
    prose: [
      'Tham chiếu: FP-001, FP-010, MQ-001, GV-001, PF-001, GV-000 (tệp tự kiểm), GV-050 và GV-204 (bảng 4.1).',
      'Dải: FP-401 đến FP-402, GV-005..008, MQ-110 đến MQ-114, V-07 đến V-15.',
    ],
    fp: [fpRow('FP-001', 'B-01'), fpRow('FP-002', 'D-02'), fpRow('FP-003', 'skipped'), fpRow('FP-010', 'U-03'), fpRow('FP-112', 'D-02'), fpRow('FP-213', 'U-03'), fpRow('FP-615', 'skipped')],
    mq: ['MQ-001', 'MQ-110', 'MQ-111', 'MQ-112', 'MQ-113', 'MQ-114', 'MQ-309'].map((id) => [id, 'Kịch bản', 'FP-001', 'Simulator', 'Không', 'Bước 1', 'Kết quả', '5 phút']),
    gv: Array.from({ length: 29 }, (_, i) => {
      const n = i + 1;
      const junit = REPLAY[n] ? `JUnit \`${REPLAY[n].replace('Replay', 'Golden')}\`; host \`${REPLAY[n]}\`` : 'JUnit';
      return [`GV-${pad(n, 3)}`, 'hàm', 'đầu vào → đầu ra', '3 ca', `\`gv-${pad(n, 3)}.json\``, junit, ''];
    }),
    cross: [
      ['GV-001', '`GV-001-love-days.json`', 'KHỚP', 'không'],
      ['GV-012, GV-013, GV-014', '`GV-012-holidays.json`', 'GỘP', 'không'],
      ['GV-017 đến GV-021', '`GV-017-password-hash.json`', 'KHỚP', 'không'],
      ['GV-050', '`GV-050-reminder-plan.json`', 'BÍ DANH', 'không'],
      ['GV-204', '`GV-204-holiday-seed-rows.json`', 'P2 sở hữu', 'không'],
      ['chỉ P8', 'GV-005..008, GV-023, GV-024, GV-027, GV-029', 'chưa phần nào sinh', 'sinh: V-01; phát lại: V-10 (023, 024, 029), V-16 (005, 006, 007, 008, 027), V-11'],
    ],
    pf: Array.from({ length: 61 }, (_, i) => [`PF-${pad(i + 1, 3)}`, 'file:1', 'pitfall', 'hậu quả', 'chặn', 'task', 'kiểm']),
    v: ['V-01', 'V-04', 'V-05', 'V-06', 'V-07', 'V-08', 'V-09', 'V-10', 'V-11', 'V-12', 'V-13', 'V-14', 'V-15', 'V-16']
      .map((id) => [id, 'P1', V_FILES[id] ?? `app/src/test/java/com/example/${id}.kt (new)`, 'đổi', 'test', 'F-12', 'haiku', 'win', 'S']),
  };
}

function render(p) {
  const table = (head, rows) => [
    `| ${head.join(' | ')} |`,
    `| ${head.map(() => '---').join(' | ')} |`,
    ...rows.map((r) => `| ${r.join(' | ')} |`),
  ].join('\n');
  return [
    '# P8 - bản dựng thử', '',
    '## 1. Phương pháp & cách đọc', '', ...p.prose, '',
    '## 2. Bảng tính năng `FP-xxx`', '', table(HEAD.fp, p.fp), '',
    '## 3. Kịch bản kiểm tay `MQ-xxx`', '', table(HEAD.mq, p.mq), '',
    '## 4. Golden vectors `GV-xxx`', '', table(HEAD.gv, p.gv), '',
    '### 4.1 Đối chiếu', '', table(HEAD.cross, p.cross), '',
    '## 5. Pitfall `PF-xxx`', '', table(HEAD.pf, p.pf), '',
    '## 9. Task xác minh `V-xx`', '', table(HEAD.v, p.v), '',
  ].join('\n');
}

const run = (p, opts = {}) => checkMatrix(render(p), { taskIds: TASKS, ...opts });

test('positive control: a well-formed matrix has no problems, even under --strict', () => {
  const r = run(fixture(), { strict: true });
  assert.deepEqual(r.problems, []);
  assert.deepEqual(r.pending, []);
});

test('an FP owner that is not a task id fails', () => {
  const p = fixture();
  p.fp[0] = fpRow('FP-001', 'Z-99');
  const { problems } = run(p);
  assert.ok(problems.some((m) => m.includes('FP-001') && m.includes('Z-99')), problems.join('\n'));
});

test('a duplicate FP id fails', () => {
  const p = fixture();
  p.fp.push(fpRow('FP-001', 'B-01'));
  const { problems } = run(p);
  assert.ok(problems.some((m) => m.startsWith('FP-001 defined 2 times')), problems.join('\n'));
});

test('an undefined GV- citation fails', () => {
  const p = fixture();
  p.prose.push('Sai: GV-077 không có dòng nào.');
  const { problems } = run(p);
  assert.ok(problems.some((m) => m.startsWith('GV-077 cited but not defined')), problems.join('\n'));
});

test('a cited FP row that is deleted is reported as cited but not defined', () => {
  const p = fixture();
  p.fp = p.fp.filter((r) => r[0] !== 'FP-010');
  const { problems } = run(p);
  assert.ok(problems.some((m) => m.includes('FP-010 cited but not defined')), problems.join('\n'));
});

test('a pending-owner token passes normally and fails under --strict', () => {
  const p = fixture();
  p.fp[0] = fpRow('FP-001', 'pending-owner:D-25a');
  const normal = run(p);
  assert.deepEqual(normal.problems, []);
  assert.equal(normal.pending.length, 1);
  const strict = run(p, { strict: true });
  assert.ok(strict.problems.some((m) => m.includes('pending-owner:D-25a')), strict.problems.join('\n'));
});

test('a skipped owner passes under --strict', () => {
  const p = fixture();
  p.fp[0] = fpRow('FP-001', 'skipped');
  assert.deepEqual(run(p, { strict: true }).problems, []);
});

test('the forbidden TODO text fails', () => {
  const p = fixture();
  p.prose.push('Phần này còn TODO.');
  const { problems } = run(p);
  assert.ok(problems.some((m) => m.includes('TODO')), problems.join('\n'));
});

test('a line that is only PENDING fails', () => {
  const p = fixture();
  p.prose.push('PENDING');
  const { problems } = run(p);
  assert.ok(problems.some((m) => m.includes('PENDING')), problems.join('\n'));
});

test('a missing V-10 row fails and names PersonalizedGiftReplayTest', () => {
  const p = fixture();
  p.v = p.v.filter((r) => r[0] !== 'V-10');
  const { problems } = run(p);
  assert.ok(problems.some((m) => m.includes('PersonalizedGiftReplayTest')), problems.join('\n'));
});

test('a missing V-16 row fails and names ZodiacReplayTest', () => {
  const p = fixture();
  p.v = p.v.filter((r) => r[0] !== 'V-16');
  const { problems } = run(p);
  assert.ok(problems.some((m) => m.includes('ZodiacReplayTest')), problems.join('\n'));
});

test('identical NFC and NFD vectors fail, distinct ones pass', () => {
  const same = JSON.stringify({ vectors: [{ name: 'a', nfc: 'é', nfd: 'é' }] });
  assert.equal(checkRawVectors('GV-900-demo.json', same).length, 1);
  const distinct = JSON.stringify({ vectors: [{ name: 'b', nfc: 'é', nfd: 'é' }] });
  assert.deepEqual(checkRawVectors('GV-900-demo.json', distinct), []);
});

test('collectTaskIds reads only tables with depends and size headers', () => {
  const task = ['| id | việc | depends on | model | host (win/mac) | size (S<=0.5d, M<=2d) |', '| --- | --- | --- | --- | --- | --- |', '| B-01 | việc | F-12 | haiku | win | S |'].join('\n');
  const other = ['| id | việc |', '| --- | --- |', '| Z-09 | khác |'].join('\n');
  assert.deepEqual([...collectTaskIds([task, other])], ['B-01']);
});
