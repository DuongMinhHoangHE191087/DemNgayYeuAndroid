import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const SCRIPT = fileURLToPath(new URL('../check-sdk-inventory.mjs', import.meta.url));
const FIXTURE = fileURLToPath(new URL('../fixtures/sdk-inventory', import.meta.url));
const CONFIG = 'docs/qa/sdk-sources.json';
const DOC = 'docs/release/SDK_INVENTORY.md';
const LOCK = 'iosApp/Package.resolved';

// Copy the fixture repo into a temp dir so each test can mutate it freely.
function withTree(fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sdk-inv-'));
  try {
    fs.cpSync(FIXTURE, dir, { recursive: true });
    return fn(dir);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}
const read = (root, rel) => fs.readFileSync(path.join(root, rel), 'utf8');
const write = (root, rel, text) => fs.writeFileSync(path.join(root, rel), text);
const run = (root) => spawnSync(process.execPath, [SCRIPT, '--root', root], { encoding: 'utf8' });
const addPin = (root, identity) => {
  const lock = JSON.parse(read(root, LOCK));
  lock.pins.push({ identity, kind: 'remoteSourceControl', location: `https://example.com/${identity}.git`, state: { version: '1.0.0' } });
  write(root, LOCK, JSON.stringify(lock, null, 2));
};
const setIgnore = (root, ignore) => {
  const cfg = JSON.parse(read(root, CONFIG));
  cfg.ignore = ignore;
  write(root, CONFIG, JSON.stringify(cfg, null, 2));
};

test('pin_missing_from_inventory_fails', () =>
  withTree((root) => {
    addPin(root, 'google-mobile-ads');
    // listed only under the Android heading, which must not count as an iOS entry
    write(root, DOC, read(root, DOC) + '\n- google-mobile-ads (Gradle)\n');
    const r = run(root);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /"google-mobile-ads"/);
  }));

test('listed_pin_passes', () =>
  withTree((root) => {
    // lock identity GoogleSignIn is listed as "googlesignin": matching ignores case
    const r = run(root);
    assert.equal(r.status, 0, r.stderr);
    assert.match(r.stdout, /^OK:/m);
  }));

test('ignored_pin_without_reason_fails', () =>
  withTree((root) => {
    addPin(root, 'legacy-sdk');
    setIgnore(root, [{ identity: 'legacy-sdk' }]);
    const r = run(root);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /legacy-sdk[^\n]*reason/);
    // the same entry with a reason is accepted
    setIgnore(root, [{ identity: 'legacy-sdk', reason: 'Android-only, no iOS build yet' }]);
    assert.equal(run(root).status, 0);
  }));

test('pin_name_inside_a_longer_name_fails', () =>
  withTree((root) => {
    addPin(root, 'alamofire');
    write(root, DOC, read(root, DOC).replace('## iOS (SwiftPM)', '## iOS (SwiftPM)\n- alamofire-extras\n'));
    const r = run(root);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /"alamofire"/);
  }));

test('no_lock_file_passes_with_note', () =>
  withTree((root) => {
    fs.rmSync(path.join(root, 'iosApp'), { recursive: true, force: true });
    const r = run(root);
    assert.equal(r.status, 0, r.stderr);
    assert.match(r.stdout, /^NOTE:/m);
  }));

test('inventory_without_ios_heading_fails', () =>
  withTree((root) => {
    write(root, DOC, read(root, DOC).replace('## iOS (SwiftPM)', '## Mobile (SwiftPM)'));
    const r = run(root);
    assert.equal(r.status, 1);
    assert.match(r.stderr, /no heading containing "iOS"/);
  }));
