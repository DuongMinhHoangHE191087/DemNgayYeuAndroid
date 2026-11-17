import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const CLI = fileURLToPath(new URL('../check-release-signoff.mjs', import.meta.url));
const FIX = fileURLToPath(new URL('../fixtures/signoff', import.meta.url));
const SHA = 'a'.repeat(40);
const OTHER = 'b'.repeat(40);
const BUILD = '7';

// Fixture: P0 = FP-001, FP-002; P1 = FP-101, FP-102; P2 = FP-201. DEC-02 descopes FP-101, DEC-01 does not.
function withDir(fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'signoff-'));
  try {
    fs.cpSync(FIX, dir, { recursive: true });
    return fn(dir);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

// Writes an evidence file (override value null drops the line) and returns the signoff entry that points at it.
function pass(dir, id, entry = {}, ev = {}) {
  const fields = { RESULT: 'PASS', build: BUILD, commit: SHA, platform: 'ios', device: 'iPhone 15', date: '2026-10-20', covers: id, ...ev };
  const text = Object.entries(fields).filter(([, v]) => v !== null).map(([k, v]) => `${k}: ${v}`).join('\n');
  fs.mkdirSync(path.join(dir, 'ev'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'ev', `${id}.md`), `# evidence\n${text}\n`);
  return { status: 'pass', evidence: `ev/${id}.md`, build: BUILD, commit: SHA, platform: 'ios', device: 'iPhone 15', date: '2026-10-20', ...entry };
}
const p0 = (dir) => ({ 'FP-001': pass(dir, 'FP-001'), 'FP-002': pass(dir, 'FP-002') });
const p1 = (dir) => ({ 'FP-101': pass(dir, 'FP-101'), 'FP-102': pass(dir, 'FP-102') });

function run(dir, entries, stage, extra = ['--build', BUILD, '--commit', SHA]) {
  fs.writeFileSync(path.join(dir, 'signoff.json'), JSON.stringify({ entries }));
  return cli(dir, ['--stage', stage, ...extra]);
}
function cli(dir, args) {
  const r = spawnSync(process.execPath, [CLI, '--root', dir, '--matrix', path.join(dir, 'matrix.md'),
    '--decisions', path.join(dir, 'DECISIONS.md'), '--signoff', path.join(dir, 'signoff.json'), ...args], { encoding: 'utf8' });
  return { code: r.status, out: r.stdout + r.stderr };
}
const ok = (r) => assert.equal(r.code, 0, r.out);
const bad = (r, needle) => {
  assert.equal(r.code, 1, r.out);
  if (needle) assert.match(r.out, needle);
};

test('p0_pending_blocks_external_testflight', () => withDir((d) => {
  ok(run(d, p0(d), 'external-testflight'));
  bad(run(d, { 'FP-001': pass(d, 'FP-001'), 'FP-002': { status: 'pending' } }, 'external-testflight'), /FP-002/);
  bad(run(d, { 'FP-001': pass(d, 'FP-001') }, 'external-testflight'), /FP-002/); // missing entry = pending
}));

test('p1_pending_blocks_app_store_only', () => withDir((d) => {
  ok(run(d, p0(d), 'external-testflight'));
  bad(run(d, p0(d), 'app-store'), /FP-101/);
  ok(run(d, { ...p0(d), ...p1(d) }, 'app-store'));
}));

test('waiver_without_reason_fails', () => withDir((d) => {
  const waived = { status: 'waived', owner: 'hoang' };
  bad(run(d, { ...p0(d), 'FP-101': waived }, 'external-testflight'), /FP-101/);
  bad(run(d, { ...p0(d), 'FP-101': { ...waived, reason: '  ' } }, 'external-testflight'), /FP-101/);
  ok(run(d, { ...p0(d), 'FP-101': { ...waived, reason: 'cho Beta' } }, 'external-testflight'));
}));

test('waiver_without_owner_fails', () => withDir((d) => {
  const waived = { status: 'waived', reason: 'cho Beta' };
  bad(run(d, { ...p0(d), 'FP-101': waived }, 'external-testflight'), /FP-101/);
  ok(run(d, { ...p0(d), 'FP-101': { ...waived, owner: 'hoang' } }, 'external-testflight'));
  // waiver is P1-only: a waived P0 never counts
  bad(run(d, { ...p0(d), 'FP-002': { ...waived, owner: 'hoang' } }, 'external-testflight'), /FP-002/);
}));

test('waived_p1_rejected_for_app_store', () => withDir((d) => {
  const entries = { ...p0(d), 'FP-101': { status: 'waived', reason: 'cho Beta', owner: 'hoang' }, 'FP-102': pass(d, 'FP-102') };
  ok(run(d, entries, 'external-testflight'));
  bad(run(d, entries, 'app-store'), /FP-101/);
}));

test('descoped_needs_recorded_decision', () => withDir((d) => {
  const all = (fp101) => ({ ...p0(d), 'FP-101': fp101, 'FP-102': pass(d, 'FP-102') });
  bad(run(d, all({ status: 'descoped' }), 'app-store'), /FP-101/);
  bad(run(d, all({ status: 'descoped', decision: 'DEC-99' }), 'app-store'), /FP-101/); // id not in DECISIONS.md
  bad(run(d, all({ status: 'descoped', decision: 'DEC-01' }), 'app-store'), /FP-101/); // line does not mention FP-101
  ok(run(d, all({ status: 'descoped', decision: 'DEC-02' }), 'app-store'));
}));

test('pass_without_evidence_fails', () => withDir((d) => {
  const noField = { ...pass(d, 'FP-001') };
  delete noField.evidence;
  bad(run(d, { ...p0(d), 'FP-001': noField }, 'external-testflight'), /FP-001/);
  bad(run(d, { ...p0(d), 'FP-001': { ...pass(d, 'FP-001'), evidence: 'ev/nope.md' } }, 'external-testflight'), /FP-001/);
  const noDevice = pass(d, 'FP-001');
  delete noDevice.device;
  bad(run(d, { ...p0(d), 'FP-001': noDevice }, 'external-testflight'), /FP-001/);
  bad(run(d, { ...p0(d), 'FP-001': { ...pass(d, 'FP-001'), commit: 'abc123' } }, 'external-testflight'), /FP-001/); // not 40 hex
}));

test('stale_commit_fails', () => withDir((d) => {
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', { commit: OTHER }, { commit: OTHER }) }, 'external-testflight'), /FP-001/);
  // entry claims the right commit but the evidence file is from another one
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', {}, { commit: OTHER }) }, 'external-testflight'), /FP-001/);
}));

test('build_mismatch_fails', () => withDir((d) => {
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', { build: '6' }, { build: '6' }) }, 'external-testflight'), /FP-001/);
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', {}, { build: '6' }) }, 'external-testflight'), /FP-001/);
  bad(run(d, p0(d), 'external-testflight', ['--build', '8', '--commit', SHA]), /FP-00/); // candidate build is 8
}));

test('platform_must_be_ios', () => withDir((d) => {
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', { platform: 'android' }) }, 'external-testflight'), /FP-001/);
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', {}, { platform: 'android' }) }, 'external-testflight'), /FP-001/);
}));

test('list_prints_counts_per_priority', () => withDir((d) => {
  const r = cli(d, ['--stage', 'list']);
  ok(r);
  assert.match(r.out, /P0:\s*2\b/);
  assert.match(r.out, /P1:\s*2\b/); // "P1 (skipped: ...)" still counts as P1
  assert.match(r.out, /P2:\s*1\b/);
}));

test('evidence_without_result_pass_fails', () => withDir((d) => {
  const file = (text) => {
    fs.writeFileSync(path.join(d, 'e.md'), text);
    return path.join(d, 'e.md');
  };
  const body = (over = '') => `build: 7\ncommit: ${SHA}\nplatform: ios\ndevice: iPhone 15\ndate: 2026-10-20\n${over}`;
  const args = (f, more = []) => ['--require-evidence', f, ...more];
  ok(cli(d, args(file(`RESULT: PASS\n${body()}`))));
  ok(cli(d, args(file(`RESULT: PASS\n${body()}`), ['--build', BUILD, '--commit', SHA])));
  bad(cli(d, args(file(body()))), /RESULT/); // no RESULT line
  bad(cli(d, args(file(`RESULT: FAIL\n${body()}`))), /RESULT/);
  bad(cli(d, args(file(`RESULT: PASS\n${body().replace(/device:.*\n/, '')}`))), /device/);
  bad(cli(d, args(file(`RESULT: PASS\n${body()}`), ['--build', '8', '--commit', SHA])), /build/);
  bad(cli(d, args(file(`RESULT: PASS\n${body()}`), ['--build', BUILD, '--commit', OTHER])), /commit/);
}));

test('evidence_covers_must_list_fp_id', () => withDir((d) => {
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', {}, { covers: 'FP-002' }) }, 'external-testflight'), /FP-001/);
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', {}, { covers: null }) }, 'external-testflight'), /FP-001/);
  bad(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', {}, { covers: 'FP-0011' }) }, 'external-testflight'), /FP-001/); // prefix is not a match
  ok(run(d, { ...p0(d), 'FP-001': pass(d, 'FP-001', {}, { covers: 'FP-002, FP-001, MQ-301' }) }, 'external-testflight'));
}));
