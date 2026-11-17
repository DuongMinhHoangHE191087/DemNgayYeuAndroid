import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const SCRIPT = fileURLToPath(new URL('../check-required-tests.mjs', import.meta.url));
const REPO = fileURLToPath(new URL('../../..', import.meta.url));
const FIX = 'scripts/qa/fixtures/junit';

// <testcase> in Gradle / mocha style. status: pass | skip | fail | error
const tc = (cls, name, status = 'pass') => {
  const open = `<testcase classname="${cls}" name="${name}" time="0.001"`;
  if (status === 'pass') return `${open}/>`;
  const child = { skip: '<skipped/>', fail: '<failure message="boom"/>', error: '<error message="boom"/>' }[status];
  return `${open}>${child}</testcase>`;
};
const suite = (...cases) => `<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="s">${cases.join('')}</testsuite>`;
const CLS = 'com.example.FooTest';
const gate = (over = {}) => ({
  id: 'G1', runner: 'android', sourceGlob: 'src/**/*.kt', classGlob: CLS, tests: ['a', 'b'], minCases: 1, ...over,
});

// Build a temp working dir (cwd of the CLI) from {relPath: content}; `gates` is written to gates.json.
function withTree(files, gates, fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rt-'));
  try {
    for (const [rel, text] of Object.entries({ ...files, 'gates.json': JSON.stringify({ gates }) })) {
      fs.mkdirSync(path.dirname(path.join(dir, rel)), { recursive: true });
      fs.writeFileSync(path.join(dir, rel), text);
    }
    const run = (...args) => {
      const r = spawnSync(process.execPath, [SCRIPT, '--gates', 'gates.json', ...args], { cwd: dir, encoding: 'utf8' });
      return { code: r.status, out: r.stdout + r.stderr };
    };
    return fn(run);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}
const SRC = { 'src/Foo.kt': '// source exists' };
const rep = (...cases) => ({ 'r/android/a.xml': suite(...cases) });
const ANDROID = ['--junit', 'android=r/android'];

test('missing_required_test_fails_in_strict', () => {
  // source exists, report lacks `b`
  withTree({ ...SRC, ...rep(tc(CLS, 'a')) }, [gate()], (run) => {
    const r = run(...ANDROID, '--strict');
    assert.equal(r.code, 1);
    assert.match(r.out, /\bb\b/);
  });
  // gate whose source file does not exist yet is pending: strict turns it into a failure
  withTree({ ...rep(tc(CLS, 'a'), tc(CLS, 'b')) }, [gate()], (run) => {
    assert.equal(run(...ANDROID, '--strict').code, 1);
  });
});

test('missing_required_test_warns_without_strict', () => {
  withTree({ ...rep(tc(CLS, 'a'), tc(CLS, 'b')) }, [gate()], (run) => {
    const r = run(...ANDROID);
    assert.equal(r.code, 0);
    assert.match(r.out, /pending/i);
  });
  // but a missing test whose source exists always fails, strict or not
  withTree({ ...SRC, ...rep(tc(CLS, 'a')) }, [gate()], (run) => {
    assert.equal(run(...ANDROID).code, 1);
  });
});

test('skipped_test_does_not_count', () => {
  withTree({ ...SRC, ...rep(tc(CLS, 'a'), tc(CLS, 'b', 'skip')) }, [gate()], (run) => {
    const r = run(...ANDROID);
    assert.equal(r.code, 1);
    assert.match(r.out, /skipped/i);
  });
});

test('failed_test_fails', () => {
  for (const status of ['fail', 'error']) {
    withTree({ ...SRC, ...rep(tc(CLS, 'a'), tc(CLS, 'b', status)) }, [gate()], (run) => {
      assert.equal(run(...ANDROID).code, 1, status);
    });
  }
});

test('min_cases_is_enforced', () => {
  const files = { ...SRC, ...rep(tc(CLS, 'a'), tc(CLS, 'b')) };
  withTree(files, [gate({ tests: ['a'], minCases: 3 })], (run) => assert.equal(run(...ANDROID).code, 1));
  withTree(files, [gate({ tests: ['a'], minCases: 2 })], (run) => assert.equal(run(...ANDROID).code, 0));
});

test('class_glob_treats_braces_literally', () => {
  const G = 'users/{uid} entitlement (T2)';
  const g = [gate({ classGlob: G, tests: ['x'] })];
  withTree({ ...SRC, ...rep(tc(G, 'x')) }, g, (run) => assert.equal(run(...ANDROID).code, 0));
  // `{uid}` is not an alternation or a wildcard
  withTree({ ...SRC, ...rep(tc('users/abc entitlement (T2)', 'x')) }, g, (run) => assert.equal(run(...ANDROID).code, 1));
  // `.` is literal, `*` is the only wildcard
  withTree({ ...SRC, ...rep(tc('comXexampleXFooTest', 'x')) }, [gate({ tests: ['x'] })], (run) =>
    assert.equal(run(...ANDROID).code, 1));
  withTree({ ...SRC, ...rep(tc('com.example.BarTest', 'x')) }, [gate({ classGlob: 'com.example.*Test', tests: ['x'] })], (run) =>
    assert.equal(run(...ANDROID).code, 0));
});

test('gate_is_checked_only_in_its_runner_group', () => {
  const files = {
    ...SRC,
    'r/android/a.xml': suite(tc(CLS, 'a'), tc(CLS, 'b')),
    'r/backend/b.xml': suite(tc('other.Class', 'z')),
  };
  const both = ['--junit', 'android=r/android', '--junit', 'backend=r/backend'];
  // the android report has the tests, but the gate belongs to `backend`
  withTree(files, [gate({ runner: 'backend' })], (run) => assert.equal(run(...both).code, 1));
  withTree(files, [gate({ runner: 'android' })], (run) => assert.equal(run(...both).code, 0));
});

test('empty_tests_list_requires_all_cases_pass', () => {
  const g = (minCases) => [gate({ tests: [], minCases })];
  withTree({ ...SRC, ...rep(tc(CLS, 'a'), tc(CLS, 'b', 'skip')) }, g(1), (run) => assert.equal(run(...ANDROID).code, 1));
  withTree({ ...SRC, ...rep(tc(CLS, 'a'), tc(CLS, 'b', 'fail')) }, g(1), (run) => assert.equal(run(...ANDROID).code, 1));
  const ok = { ...SRC, ...rep(tc(CLS, 'a'), tc(CLS, 'b')) };
  withTree(ok, g(2), (run) => assert.equal(run(...ANDROID).code, 0));
  withTree(ok, g(3), (run) => assert.equal(run(...ANDROID).code, 1));
  // no case at all for the class is not a pass
  withTree({ ...SRC, ...rep(tc('other.Class', 'a')) }, g(0), (run) => assert.equal(run(...ANDROID).code, 1));
});

test('only_group_ignores_other_groups', () => {
  const gates = [gate({ id: 'AND' }), gate({ id: 'BACK', runner: 'backend', sourceGlob: 'nope/**/*.js' })];
  const files = { ...SRC, ...rep(tc(CLS, 'a'), tc(CLS, 'b')) };
  withTree(files, gates, (run) => {
    // backend gate is pending and has no reports, yet ignored: not even counted as pending under --strict
    const r = run(...ANDROID, '--only-group', 'android', '--strict');
    assert.equal(r.code, 0, r.out);
    assert.doesNotMatch(r.out, /BACK/);
    assert.equal(run(...ANDROID, '--strict').code, 1);
  });
  // an ignored group's real failure does not fail the job either
  withTree({ ...files, 'src/b.js': '', 'r/backend/b.xml': suite(tc('x', 'y', 'fail')) },
    [gate({ id: 'AND' }), gate({ id: 'BACK', runner: 'backend', sourceGlob: 'src/*.js', classGlob: 'x', tests: ['y'] })],
    (run) => assert.equal(run(...ANDROID, '--junit', 'backend=r/backend', '--only-group', 'android').code, 0));
});

test('strict_fails_when_a_referenced_group_has_no_reports', () => {
  const g = [gate({ runner: 'ios-ui' })];
  withTree(SRC, g, (run) => {
    // source exists but no `ios-ui` report directory was supplied at all
    assert.equal(run('--strict').code, 1);
    assert.equal(run().code, 0, 'non-strict only warns');
  });
  // a directory without any xml is also "no reports"
  withTree({ ...SRC, 'r/ui/readme.txt': 'x' }, g, (run) => {
    assert.equal(run('--junit', 'ios-ui=r/ui', '--strict').code, 1);
  });
});

test('commit_mismatch_fails', () => {
  const g = [gate({ tests: ['a'] })];
  const files = { ...SRC, ...rep(tc(CLS, 'a')) };
  withTree({ ...files, 'r/android/commit.txt': 'abc123\n' }, g, (run) => {
    assert.equal(run(...ANDROID, '--strict', '--commit', 'abc123').code, 0);
    assert.equal(run(...ANDROID, '--strict', '--commit', 'def456').code, 1);
  });
  // reports without commit.txt cannot be tied to the commit
  withTree(files, g, (run) => {
    assert.equal(run(...ANDROID, '--strict', '--commit', 'abc123').code, 1);
    assert.equal(run(...ANDROID, '--strict').code, 0, 'no --commit, no check');
  });
});

test('junit_root_maps_subdirs_to_groups', () => {
  const files = {
    ...SRC, 'src/b.js': '',
    'root/junit-android/a.xml': suite(tc(CLS, 'a')),
    'root/junit-backend/b.xml': suite(tc('B', 'z')),
  };
  const gates = [gate({ tests: ['a'] }), gate({ id: 'G2', runner: 'backend', sourceGlob: 'src/*.js', classGlob: 'B', tests: ['z'] })];
  withTree(files, gates, (run) => {
    assert.equal(run('--junit-root', 'root', '--strict').code, 0);
    // the `backend` gate cannot be satisfied by android's directory and vice versa
    assert.equal(run('--junit', 'android=root/junit-android', '--strict').code, 1);
  });
  // an explicit --junit adds to the root
  withTree(files, gates, (run) => {
    assert.equal(run('--junit-root', 'root', '--junit', 'ios-ui=root/junit-android').code, 0);
  });
});

test('all_present_passes', () => {
  const cli = (...args) => {
    const r = spawnSync(process.execPath, [SCRIPT, ...args], { cwd: REPO, encoding: 'utf8' });
    return { code: r.status, out: r.stdout + r.stderr };
  };
  const good = cli('--junit', `android=${FIX}/good`, '--gates', `${FIX}/gates-good.json`);
  assert.equal(good.code, 0, good.out);
  // Gradle, mocha xunit, node --test, swift test, fastlane scan and xcresult-to-junit side by side, each in its own group
  const all = cli('--junit-root', `${FIX}/all`, '--gates', `${FIX}/gates-all.json`, '--strict');
  assert.equal(all.code, 0, all.out);
  // node --test marks `skip` and `todo` alike as <skipped>; neither may satisfy a gate
  const gates = JSON.parse(fs.readFileSync(path.join(REPO, FIX, 'gates-all.json'), 'utf8')).gates;
  for (const [runner, classGlob, name] of [
    ['backend', 'Suite A', 'skipped one'], ['backend', 'Suite A', 'todo one'], ['ios-ui', 'AppShellSmokeTests', 'testPaywallOpens()'],
  ]) {
    const extra = { id: 'X', runner, sourceGlob: `${FIX}/all/**/*.xml`, classGlob, tests: [name], minCases: 0 };
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'rt-gates-'));
    try {
      const file = path.join(tmp, 'g.json');
      fs.writeFileSync(file, JSON.stringify({ gates: [...gates, extra] }));
      const r = cli('--junit-root', `${FIX}/all`, '--gates', file);
      assert.equal(r.code, 1, `${name}\n${r.out}`);
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  }
});
