// Tests for the grep-gate runner (Q-07). Run: node --test scripts/qa/test/check-grep-gates.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const RUNNER = fileURLToPath(new URL('../check-grep-gates.mjs', import.meta.url));
const FIXTURES = fileURLToPath(new URL('../fixtures/grep-gates/', import.meta.url));
const REAL_CONFIG = fileURLToPath(new URL('../../../docs/qa/grep-gates.json', import.meta.url));

// forbidden and clean use the shipped GG-01 config, so the real regex is checked too.
function run(root, config) {
  return spawnSync(process.execPath, [RUNNER, '--root', path.join(FIXTURES, root), '--config', config], {
    encoding: 'utf8',
  });
}

test('forbidden_import_in_commonMain_fails', () => {
  const r = run('forbidden-import', REAL_CONFIG);
  assert.equal(r.status, 1, r.stdout + r.stderr);
  assert.ok(r.stdout.includes('shared/src/commonMain/kotlin/com/example/data/Repo.kt:3'), r.stdout);
});

test('clean_tree_passes', () => {
  const r = run('clean', REAL_CONFIG);
  assert.equal(r.status, 0, r.stdout + r.stderr);
  assert.match(r.stdout, /^OK/);
});

test('exception_glob_is_honored', () => {
  const r = run('excepted', path.join(FIXTURES, 'excepted', 'gates.json'));
  assert.equal(r.status, 1, r.stdout + r.stderr);
  assert.ok(r.stdout.includes('Other.kt'), r.stdout);
  assert.ok(!r.stdout.includes('Actual.kt'), r.stdout);
});

test('unknown_gate_field_is_an_error', () => {
  const r = run('unknown-field', path.join(FIXTURES, 'unknown-field', 'gates.json'));
  assert.equal(r.status, 2, r.stdout + r.stderr);
  assert.ok(r.stderr.includes('severity'), r.stderr);
});
