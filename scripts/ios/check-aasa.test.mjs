// node --test scripts/ios/check-aasa.test.mjs
// The matcher below models Apple AASA "components" as the plan describes them:
// "/" is a path glob ('*' = zero or more chars, '?' = exactly one char); a "?" object
// requires every key in the URL query, with a value matching its glob ("?*" = non-empty).
// This is our reading of the rules, NOT verified on a real device: how iOS treats "?"
// is measured by task N-42c.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const DIR = path.dirname(fileURLToPath(import.meta.url));
const CLI = path.join(DIR, 'check-aasa.mjs');
const TEMPLATE = path.join(DIR, 'apple-app-site-association.template.json');
const BUNDLE = 'com.aistudio.inlove.kmrv';
const TEAM = 'ABCDE12345';

// ---- AASA component matcher ------------------------------------------------
const globToRe = (g) =>
  new RegExp('^' + g.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.') + '$');

function componentMatches(c, url) {
  const u = new URL(url);
  if (!globToRe(c['/']).test(u.pathname)) return false;
  return Object.entries(c['?'] ?? {}).every(([k, glob]) =>
    u.searchParams.getAll(k).some((v) => globToRe(glob).test(v)),
  );
}

const template = () => JSON.parse(fs.readFileSync(TEMPLATE, 'utf8'));
const templateMatches = (url) =>
  template().applinks.details.some((d) => d.components.some((c) => componentMatches(c, url)));

// ---- template: path matching -----------------------------------------------
test('templateMatchesPairQueryLink', () => {
  assert.equal(templateMatches('https://inlove.app/pair?code=LOVE-9966'), true);
  assert.equal(templateMatches('https://inlove.app/pair'), false); // "?" needs the code key
  assert.equal(templateMatches('https://inlove.app/pair?code='), false); // and a non-empty value
});

test('templateMatchesPairPathLink', () => {
  assert.equal(templateMatches('https://inlove.app/pair/LOVE-9966'), true);
});

test('templateDoesNotMatchTabLink', () => {
  assert.equal(templateMatches('https://inlove.app/tab/home'), false);
});

test('templateDoesNotMatchRoot', () => {
  assert.equal(templateMatches('https://inlove.app/'), false);
});

test('templateDoesNotMatchLookalikePaths', () => {
  for (const p of ['/pairs?code=LOVE-9966', '/xpair?code=LOVE-9966', '/pair2', '/other?code=LOVE-9966', '/pair-x/LOVE-9966']) {
    assert.equal(templateMatches('https://inlove.app' + p), false, p);
  }
});

// ---- CLI -------------------------------------------------------------------
const cli = (args) => spawnSync(process.execPath, [CLI, ...args], { encoding: 'utf8' });

// Runs the CLI with --out pointing into a fresh temp dir; fn gets the result and the out path.
function withOut(args, fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aasa-'));
  try {
    const file = path.join(dir, 'apple-app-site-association.json');
    return fn(cli([...args, '--out', file]), file);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test('teamIdMustBeTenAlphanumerics', () => {
  withOut(['--team-id', TEAM], (res) => assert.equal(res.status, 0, res.stderr));
  for (const bad of ['abcde12345', 'ABCDE1234', 'ABCDE123456', 'ABCDE1234-', '<TeamID>', '']) {
    withOut(['--team-id', bad], (res, file) => {
      assert.equal(res.status, 1, `accepted Team ID "${bad}"`);
      assert.match(res.stderr, /Team ID/);
      assert.equal(fs.existsSync(file), false);
    });
  }
});

test('releaseModeRejectsPlaceholderTeamId', () => {
  withOut(['--release'], (res, file) => {
    assert.equal(res.status, 1);
    assert.match(res.stderr, /Team ID/);
    assert.equal(fs.existsSync(file), false);
  });
  withOut(['--release', '--team-id', '<TeamID>'], (res) => {
    assert.equal(res.status, 1);
    assert.match(res.stderr, /Team ID/);
  });
  withOut(['--release', '--team-id', TEAM], (res) => assert.equal(res.status, 0, res.stderr));
});

test('generatedFileKeepsBundleId', () => {
  withOut(['--team-id', TEAM], (res, file) => {
    assert.equal(res.status, 0, res.stderr);
    const text = fs.readFileSync(file, 'utf8');
    assert.equal(JSON.parse(text).applinks.details[0].appIDs[0], `${TEAM}.${BUNDLE}`);
    assert.doesNotMatch(text, /<TeamID>/);
  });
});

test('generatedFileIsLfUtf8WithoutBom', () => {
  withOut(['--team-id', TEAM], (res, file) => {
    assert.equal(res.status, 0, res.stderr);
    const bytes = fs.readFileSync(file);
    assert.equal(bytes[0], 0x7b, 'must start with "{" (no BOM)');
    assert.equal(bytes.includes(0x0d), false, 'no CR bytes');
    assert.equal(bytes[bytes.length - 1], 0x0a, 'ends with LF');
    assert.match(bytes.toString('utf8'), /^\{\n  "applinks": \{\n/);
  });
});

test('devModeKeepsPlaceholderAndPrintsToStdout', () => {
  const res = cli([]);
  assert.equal(res.status, 0, res.stderr);
  assert.equal(JSON.parse(res.stdout).applinks.details[0].appIDs[0], `<TeamID>.${BUNDLE}`);
  assert.ok(res.stdout.endsWith('}\n'));
});

test('unknownOrValuelessArgumentFails', () => {
  for (const args of [['--teamid', TEAM], ['--team-id'], ['--out'], ['--team-id', '--release']]) {
    const res = cli(args);
    assert.equal(res.status, 1, JSON.stringify(args));
    assert.match(res.stderr, /unknown argument|needs a value/);
  }
});
