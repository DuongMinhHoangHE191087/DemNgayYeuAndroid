import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkStrings, checkPairs } from '../check-strings.mjs';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const RES = path.join('app', 'src', 'main', 'res');

const doc = (...entries) => `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n${entries.join('\n')}\n</resources>\n`;
const str = (name, text) => `    <string name="${name}">${text}</string>`;
const plural = (name, items) =>
  `    <plurals name="${name}">${items.map(([q, text]) => `<item quantity="${q}">${text}</item>`).join('')}</plurals>`;

test('missing_key_in_vi_fails', () => {
  const en = doc(str('app_name', 'InLove'), str('greeting', 'Hello %s'));

  const missing = checkStrings(en, doc(str('app_name', 'InLove')));
  assert.equal(missing.length, 1);
  assert.match(missing[0], /missing key in vi: greeting/);

  const extra = checkStrings(en, doc(str('app_name', 'InLove'), str('greeting', 'Xin chào %s'), str('stray', 'x')));
  assert.equal(extra.length, 1);
  assert.match(extra[0], /extra key in vi: stray/);

  assert.deepEqual(checkStrings(en, doc(str('app_name', 'InLove'), str('greeting', 'Xin chào %s'))), []);
});

test('placeholder_mismatch_fails', () => {
  const en = doc(str('reg', '%1$s đã đăng ký %2$d'));

  const dropped = checkStrings(en, doc(str('reg', '%1$s đã đăng ký')));
  assert.equal(dropped.length, 1);
  assert.match(dropped[0], /placeholder mismatch in reg/);

  const retyped = checkStrings(en, doc(str('reg', '%1$s đã đăng ký %2$s')));
  assert.equal(retyped.length, 1);
  assert.match(retyped[0], /placeholder mismatch in reg/);

  // %s and %1$s are the same placeholder by position
  assert.deepEqual(checkStrings(en, doc(str('reg', '%s đã đăng ký %d'))), []);
});

test('non_nfc_vietnamese_fails', () => {
  const en = doc(str('name', 'Love'));
  const decomposed = 'Tâm'; // "Tam" + combining circumflex, not NFC

  const bad = checkStrings(en, doc(str('name', decomposed)));
  assert.equal(bad.length, 1);
  assert.match(bad[0], /vi text not NFC: name/);

  assert.deepEqual(checkStrings(en, doc(str('name', 'Tâm'))), []);

  const enPlural = doc(plural('days', [['one', '%d day'], ['other', '%d days']]));
  const badPlural = checkStrings(enPlural, doc(plural('days', [['other', `%d ${decomposed}`]])));
  assert.equal(badPlural.length, 1);
  assert.match(badPlural[0], /not NFC/);
});

test('plural_quantity_mismatch_fails', () => {
  const en = doc(plural('days', [['one', '%d day'], ['other', '%d days']]));

  // vi must have "other": a one-only vi plural fails
  const noOther = checkStrings(en, doc(plural('days', [['one', '%d ngày']])));
  assert.equal(noOther.length, 1);
  assert.match(noOther[0], /missing quantity "other" in days \(vi\)/);

  // a quantity outside the six CLDR categories fails
  const bogus = checkStrings(en, doc(plural('days', [['other', '%d ngày'], ['some', '%d ngày']])));
  assert.equal(bogus.length, 1);
  assert.match(bogus[0], /invalid quantity "some" in days \(vi\)/);

  // en must have "one" as well as "other"
  const enNoOne = checkStrings(doc(plural('days', [['other', '%d days']])), doc(plural('days', [['other', '%d ngày']])));
  assert.equal(enNoOne.length, 1);
  assert.match(enNoOne[0], /missing quantity "one" in days \(en\)/);
});

test('matching_files_pass', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'strings-'));
  const write = (rel, text) => {
    fs.mkdirSync(path.dirname(path.join(dir, rel)), { recursive: true });
    fs.writeFileSync(path.join(dir, rel), text);
  };
  const pairs = [{ base: 'values', vi: 'values-vi' }];
  try {
    write('values/strings.xml', doc(str('app_name', 'InLove'), str('reg', '%1$s đã đăng ký %2$d')));
    write('values-vi/strings.xml', doc(str('app_name', 'InLove'), str('reg', '%s đã đăng ký %d')));
    assert.deepEqual(checkPairs(dir, pairs), []);

    // Negative controls: the files are really read, so a bad vi file or a missing one is reported.
    write('values-vi/strings.xml', doc(str('app_name', 'InLove')));
    assert.match(checkPairs(dir, pairs).join('\n'), /missing key in vi: reg/);
    fs.rmSync(path.join(dir, 'values-vi'), { recursive: true });
    assert.match(checkPairs(dir, pairs).join('\n'), /missing file: .*values-vi/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('repo_en_vi_pair_passes', () => {
  const en = fs.readFileSync(path.join(REPO, RES, 'values', 'strings.xml'), 'utf8');
  const vi = fs.readFileSync(path.join(REPO, RES, 'values-vi', 'strings.xml'), 'utf8');
  assert.deepEqual(checkStrings(en, vi), []);
});
