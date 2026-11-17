import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { checkFakes } from '../check-fakes.mjs';

const SCRIPT = fileURLToPath(new URL('../check-fakes.mjs', import.meta.url));
const FIXTURE = fileURLToPath(new URL('../fixtures/fakes', import.meta.url));
const FAKE = 'shared/src/commonTest/kotlin/demo/FakeOrderGateway.kt';
const JSON_PATH = 'docs/qa/fakes.json';

// Copy the fixture tree into a temp dir so each test can mutate it freely.
function withTree(fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fakes-'));
  try {
    fs.cpSync(FIXTURE, dir, { recursive: true });
    return fn(dir);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}
const write = (root, rel, text) => {
  fs.mkdirSync(path.dirname(path.join(root, rel)), { recursive: true });
  fs.writeFileSync(path.join(root, rel), text);
};
const mv = (root, from, to) => {
  fs.mkdirSync(path.dirname(path.join(root, to)), { recursive: true });
  fs.renameSync(path.join(root, from), path.join(root, to));
};
const cli = (root) => spawnSync(process.execPath, [SCRIPT, '--root', root], { encoding: 'utf8' });

test('gateway_without_fake_fails', () =>
  withTree((root) => {
    fs.rmSync(path.join(root, FAKE));
    const problems = checkFakes(root);
    assert.equal(problems.length, 1);
    assert.match(problems[0], /OrderGateway/);
    assert.equal(cli(root).status, 1);
  }));

test('gateway_with_fake_passes', () =>
  withTree((root) => {
    assert.deepEqual(checkFakes(root), []);
  }));

test('fake_in_commonMain_testing_dir_passes', () =>
  withTree((root) => {
    // nested on purpose: DEC-251 uses core/fakes/ and data/remote/fakes/
    for (const dir of ['testing', 'fakes']) {
      const at = `shared/src/commonMain/kotlin/demo/core/${dir}/FakeOrderGateway.kt`;
      mv(root, FAKE, at);
      assert.deepEqual(checkFakes(root), [], `Fake in ${dir}/ must count`);
      mv(root, at, FAKE);
    }
  }));

test('fake_in_other_commonMain_dir_fails', () =>
  withTree((root) => {
    for (const dir of ['demo', 'demo/mocks', 'demo/fakes_old']) {
      const at = `shared/src/commonMain/kotlin/${dir}/FakeOrderGateway.kt`;
      mv(root, FAKE, at);
      const problems = checkFakes(root);
      assert.equal(problems.length, 1, `Fake in ${dir}/ must not count`);
      assert.match(problems[0], /OrderGateway/);
      mv(root, at, FAKE);
    }
  }));

test('exempt_without_reason_fails', () =>
  withTree((root) => {
    // CallableInvoker matches the regex (DEC-252): only an exempt entry keeps it quiet
    write(root, 'shared/src/commonMain/kotlin/demo/CallableInvoker.kt', 'package demo\n\ninterface CallableInvoker\n');
    assert.deepEqual(checkFakes(root), []);
    write(root, JSON_PATH, JSON.stringify({ exempt: [] }));
    const missing = checkFakes(root);
    assert.equal(missing.length, 1);
    assert.match(missing[0], /CallableInvoker/);
    write(root, JSON_PATH, JSON.stringify({ exempt: [{ name: 'CallableInvoker' }] }));
    const noReason = checkFakes(root);
    assert.equal(noReason.length, 1);
    assert.match(noReason[0], /reason/);
    write(root, JSON_PATH, JSON.stringify({ exempt: [{ name: 'CallableInvoker', reason: '   ' }] }));
    assert.equal(checkFakes(root).length, 1);
  }));

test('no_shared_dir_passes', () =>
  withTree((root) => {
    fs.rmSync(path.join(root, 'shared'), { recursive: true, force: true });
    assert.deepEqual(checkFakes(root), []);
    assert.equal(cli(root).status, 0);
  }));
