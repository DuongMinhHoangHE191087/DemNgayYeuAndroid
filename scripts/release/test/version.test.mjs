import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const script = join(dirname(fileURLToPath(import.meta.url)), '..', 'version.mjs');

function run(...args) {
  const r = spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' });
  return { code: r.status, out: r.stdout.trim(), json: r.status === 0 ? JSON.parse(r.stdout) : null };
}

function floorFile(floor) {
  const f = join(mkdtempSync(join(tmpdir(), 'q25-')), 'build-floor.json');
  writeFileSync(f, JSON.stringify({ floor }));
  return f;
}

test('tag_to_marketing_version', () => {
  assert.equal(run('--tag', 'v1.2.3', '--builds', '1').json.marketingVersion, '1.2.3');
  assert.equal(run('--tag', 'v10.20.30-rc.4', '--builds', '1').json.marketingVersion, '10.20.30');
});

test('rc_tag_is_internal_channel', () => {
  const j = run('--tag', 'v1.2.3-rc.1', '--builds', '1').json;
  assert.equal(j.channel, 'testflight-internal');
  assert.equal(j.prerelease, true);
});

test('beta_tag_is_external_channel', () => {
  const j = run('--tag', 'v1.2.3-beta.2', '--builds', '1').json;
  assert.equal(j.channel, 'testflight-external');
  assert.equal(j.prerelease, true);
});

test('plain_tag_is_app_store_channel', () => {
  const j = run('--tag', 'v1.2.3', '--builds', '1').json;
  assert.equal(j.channel, 'app-store');
  assert.equal(j.prerelease, false);
});

test('build_is_max_across_all_trains_plus_one', () => {
  assert.equal(run('--tag', 'v1.2.3', '--builds', '40,41,38').json.build, 42);
  assert.equal(run('--tag', 'v1.2.3', '--builds', '38,41,40').json.build, 42);
  assert.equal(run('--tag', 'v1.2.3', '--builds', '40,41,38').out,
    '{"marketingVersion":"1.2.3","build":42,"prerelease":false,"channel":"app-store"}');
});

test('floor_applies_when_api_returns_nothing', () => {
  assert.equal(run('--tag', 'v1.2.3', '--builds', '', '--floor', floorFile(50)).json.build, 51);
  assert.equal(run('--tag', 'v1.2.3', '--builds', '10', '--floor', floorFile(50)).json.build, 51);
  assert.equal(run('--tag', 'v1.2.3', '--builds', '60', '--floor', floorFile(50)).json.build, 61);
});

test('build_fallback_1_when_no_input', () => {
  assert.equal(run('--tag', 'v1.2.3').json.build, 1);
  assert.equal(run('--tag', 'v1.2.3', '--builds', '').json.build, 1);
});

test('invalid_tag_exits_2', () => {
  for (const tag of ['1.2.3', 'v1.2', 'v1.2.3-alpha.1', 'v1.2.3-rc', 'v1.2.3-rc.1x', 'v1.2.3-rc.1\nx', '']) {
    assert.equal(run('--tag', tag, '--builds', '1').code, 2, JSON.stringify(tag));
  }
  assert.equal(run('--builds', '1').code, 2);
});

test('non_numeric_build_exits_2', () => {
  for (const b of ['40,abc', '40,,41', '0', '-3', '4.5', '01', '40, 41']) {
    assert.equal(run('--tag', 'v1.2.3', '--builds', b).code, 2, b);
  }
});

test('rerun_same_inputs_same_build', () => {
  const a = run('--tag', 'v1.2.3', '--builds', '40,41,38');
  const b = run('--tag', 'v1.2.3', '--builds', '40,41,38');
  assert.equal(a.out, b.out);
});

test('existing_build_never_reissued', () => {
  const builds = [1, 7, 42, 43, 100];
  const { build } = run('--tag', 'v1.2.3', '--builds', builds.join(',')).json;
  assert.ok(!builds.includes(build));
  assert.equal(build, 101);
});
