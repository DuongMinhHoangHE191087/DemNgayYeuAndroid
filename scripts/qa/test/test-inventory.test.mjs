import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildInventory, checkInventory } from '../test-inventory.mjs';

const FIXTURE = fileURLToPath(new URL('../fixtures/inventory-tree', import.meta.url));
const SYNC = 'app/src/test/java/com/example/SyncWorkerTest.kt';
const SYNC_TESTS = ['pushesPending', 'retriesOnFailure', 'fails on bad token', 'a', 'b'];

// Copy the fixture tree into a temp dir so each test can mutate it freely.
function withTree(fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'inv-'));
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
const read = (root, rel) => fs.readFileSync(path.join(root, rel), 'utf8');
const dropTest = (root, name) =>
  write(root, SYNC, read(root, SYNC).replace(new RegExp(`@Test\\s+fun ${name}\\(\\) \\{\\}\\n?`), ''));

test('countsMatchFixtureTree', () => {
  const { files } = buildInventory(FIXTURE);
  assert.deepEqual(Object.keys(files).sort(), [
    'app/src/test/java/com/example/SyncWorkerTest.kt',
    'firebase/functions/test/policy.test.js',
    'iosApp/UITests/LoginUITests.swift',
    'shared/src/commonTest/kotlin/DateMathTest.kt',
  ]);
  assert.equal(files[SYNC].count, 5);
  assert.deepEqual(files[SYNC].tests, SYNC_TESTS);
  assert.equal(files['firebase/functions/test/policy.test.js'].count, 3);
  assert.deepEqual(files['iosApp/UITests/LoginUITests.swift'].tests, ['testLoginShowsError', 'testLoginSucceeds']);
  assert.equal(files['shared/src/commonTest/kotlin/DateMathTest.kt'].count, 1);
});

test('newTestsPass', () =>
  withTree((root) => {
    const inv = buildInventory(root);
    write(root, SYNC, read(root, SYNC).replace(/\}\n$/, '  @Test\n  fun brandNew() {}\n}\n'));
    write(root, 'app/src/test/NewTest.kt', '@Test\nfun extra() {}\n');
    assert.deepEqual(checkInventory(root, inv), []);
  }));

test('dropWithoutRecordFails', () =>
  withTree((root) => {
    const inv = buildInventory(root);
    dropTest(root, 'a');
    const problems = checkInventory(root, inv);
    assert.equal(problems.length, 1);
    assert.match(problems[0], new RegExp(`${SYNC}#a`));
    // a deleted entry with a reason makes it legal
    inv.files[SYNC].deleted = [{ test: 'a', reason: 'obsolete' }];
    assert.deepEqual(checkInventory(root, inv), []);
    // ...but not without a reason
    inv.files[SYNC].deleted = [{ test: 'a' }];
    assert.equal(checkInventory(root, inv).length, 1);
  }));

test('moveWithRecordPasses', () =>
  withTree((root) => {
    const inv = buildInventory(root);
    dropTest(root, 'a');
    write(root, 'app/src/test/MovedTest.kt', '@Test\nfun a() {}\n');
    inv.files[SYNC].moved = [{ test: 'a', to: 'app/src/test/MovedTest.kt' }];
    assert.deepEqual(checkInventory(root, inv), []);
  }));

test('partialSplitKeepsUnionPasses', () =>
  withTree((root) => {
    const inv = buildInventory(root);
    dropTest(root, 'a');
    dropTest(root, 'b');
    // destination also holds an unrelated extra test
    write(root, 'app/src/test/SplitTest.kt', '@Test\nfun a() {}\n@Test\nfun b() {}\n@Test\nfun unrelated() {}\n');
    inv.files[SYNC].moved = [
      { test: 'a', to: 'app/src/test/SplitTest.kt' },
      { test: 'b', to: 'app/src/test/SplitTest.kt' },
    ];
    assert.deepEqual(checkInventory(root, inv), []);
    assert.equal(buildInventory(root).files[SYNC].count, 3);
  }));

test('renamedTestPasses', () =>
  withTree((root) => {
    const inv = buildInventory(root);
    dropTest(root, 'a');
    write(root, 'app/src/test/RenamedTest.kt', '@Test\nfun `a renamed`() {}\n');
    inv.files[SYNC].moved = [{ test: 'a', to: 'app/src/test/RenamedTest.kt', as: 'a renamed' }];
    assert.deepEqual(checkInventory(root, inv), []);
    // wrong "as" name must fail
    inv.files[SYNC].moved = [{ test: 'a', to: 'app/src/test/RenamedTest.kt', as: 'other' }];
    assert.equal(checkInventory(root, inv).length, 1);
  }));

test('movedTestMissingInDestinationFails', () =>
  withTree((root) => {
    const inv = buildInventory(root);
    dropTest(root, 'a');
    inv.files[SYNC].moved = [{ test: 'a', to: 'app/src/test/Nowhere.kt' }]; // file absent
    assert.equal(checkInventory(root, inv).length, 1);
    write(root, 'app/src/test/Nowhere.kt', '@Test\nfun somethingElse() {}\n'); // file lacks test
    assert.equal(checkInventory(root, inv).length, 1);
  }));
