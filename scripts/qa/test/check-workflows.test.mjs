import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { checkDir } from '../check-workflows.mjs';

const CHECKER = fileURLToPath(new URL('../check-workflows.mjs', import.meta.url));
const GOOD = fileURLToPath(new URL('../fixtures/workflows/good', import.meta.url));
const CI = 'ci.yml';
const JOB = 'jobs:\n  pkg:\n    runs-on: ubuntu-24.04\n    steps:\n      - uses: actions/checkout@v4.2.2\n';

// Temp dir seeded with { name: text } files; removed after fn runs.
function withFiles(files, fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wf-'));
  try {
    for (const [name, text] of Object.entries(files)) fs.writeFileSync(path.join(dir, name), text);
    return fn(dir);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

// Temp copy of the good fixture, so each test can mutate it freely.
function withGood(fn) {
  return withFiles({}, (dir) => {
    fs.cpSync(GOOD, dir, { recursive: true });
    return fn(dir);
  });
}

// `from` must occur exactly once, so a changed fixture cannot turn a mutation into a no-op.
function mutate(dir, file, from, to) {
  const p = path.join(dir, file);
  const src = fs.readFileSync(p, 'utf8');
  assert.equal(src.split(from).length, 2, `${file} must contain ${JSON.stringify(from)} exactly once`);
  fs.writeFileSync(p, src.replace(from, () => to));
}

const cli = (dir) => spawnSync(process.execPath, [CHECKER, dir], { encoding: 'utf8' });

// Exactly one problem, and it must carry the given rule tag.
function onlyProblem(dir, rule) {
  const problems = checkDir(dir);
  assert.equal(problems.length, 1, problems.join('\n') || 'expected exactly one problem');
  assert.match(problems[0], new RegExp(`\\[${rule}\\]`));
}

test('unpinned_action_fails', () => {
  for (const ref of ['actions/checkout@v4', 'actions/checkout']) {
    withGood((dir) => {
      mutate(dir, CI, 'actions/checkout@v4.2.2', ref);
      onlyProblem(dir, 'unpinned-action');
    });
  }
  withGood((dir) => {
    mutate(dir, CI, 'actions/checkout@v4.2.2', 'actions/checkout@v4');
    const r = cli(dir);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /\[unpinned-action\]/);
  });
});

test('pull_request_target_fails', () =>
  withGood((dir) => {
    mutate(dir, CI, '  pull_request:\n', '  pull_request_target:\n');
    onlyProblem(dir, 'pull-request-target');
  }));

test('secret_in_pr_workflow_fails', () => {
  // a secrets. expression, and secrets: inherit (hands every repo secret to the called workflow)
  for (const extra of ['    env:\n      TOKEN: ${{ secrets.TOKEN }}\n', '    secrets: inherit\n']) {
    withGood((dir) => {
      mutate(dir, CI, 'runs-on: ubuntu-24.04\n', `runs-on: ubuntu-24.04\n${extra}`);
      onlyProblem(dir, 'pr-secrets');
    });
  }
});

test('missing_timeout_on_macos_job_fails', () =>
  withGood((dir) => {
    mutate(dir, CI, '    timeout-minutes: 45\n', '');
    onlyProblem(dir, 'macos-timeout');
  }));

test('kmp_ios_branch_required_in_pr_workflows', () => {
  withGood((dir) => {
    mutate(dir, CI, '      - kmp-ios\n', '');
    onlyProblem(dir, 'kmp-ios-branch');
  });
  withFiles({ 'push.yml': `on:\n  push:\n    branches: [main]\n${JOB}` }, (dir) => onlyProblem(dir, 'kmp-ios-branch'));
  withFiles({ 'push.yml': `on:\n  push:\n    branches: [main, kmp-ios]\n${JOB}` }, (dir) =>
    assert.deepEqual(checkDir(dir), []),
  );
});

test('tag_only_push_is_exempt_from_branch_rule', () => {
  // no branches: filter, so the kmp-ios rule does not apply
  withFiles({ 'tag.yml': `on:\n  push:\n    tags: ['v*']\n${JOB}` }, (dir) => assert.deepEqual(checkDir(dir), []));
  // environment: gating allows a tag-only push, but not a push that also runs on branches
  const gated = (trigger) =>
    `on:\n${trigger}jobs:\n  pkg:\n    runs-on: ubuntu-24.04\n    environment: prod\n    steps:\n      - uses: actions/checkout@v4.2.2\n`;
  withFiles({ 'tag.yml': gated("  push:\n    tags: ['v*']\n") }, (dir) => assert.deepEqual(checkDir(dir), []));
  withFiles({ 'tag.yml': gated('  push:\n    branches: [main, kmp-ios]\n') }, (dir) =>
    onlyProblem(dir, 'environment-trigger'),
  );
});

test('no_workflows_dir_passes', () =>
  withFiles({}, (dir) => {
    const missing = path.join(dir, '.github', 'workflows');
    assert.deepEqual(checkDir(missing), []);
    assert.equal(cli(missing).status, 0);
  }));

test('good_fixture_passes', () => {
  assert.deepEqual(checkDir(GOOD), []);
  assert.equal(cli(GOOD).status, 0);
});
