#!/usr/bin/env node
// Strings guard: every en/vi resource pair listed in docs/qa/strings.json must agree.
// Checks: same keys on both sides, same placeholders by position, CLDR plural quantities, vi text in NFC.
// Usage: node scripts/qa/check-strings.mjs   (exit 0 = OK, exit 1 = issues on stderr)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const VALID_QUANTITIES = ['zero', 'one', 'two', 'few', 'many', 'other'];
// Per-locale CLDR requirements, applied to each side on its own (en = base, vi = translation).
const REQUIRED_QUANTITIES = { en: ['one', 'other'], vi: ['other'] };

const COMMENT_RE = /<!--[\s\S]*?-->/g;
const STRING_RE = /<string\s+name="([^"]+)"[^>]*(?<!\/)>([\s\S]*?)<\/string>/g;
const PLURALS_RE = /<plurals\s+name="([^"]+)"[^>]*(?<!\/)>([\s\S]*?)<\/plurals>/g;
const ITEM_RE = /<item\s+quantity="([^"]+)"[^>]*(?<!\/)>([\s\S]*?)<\/item>/g;
// Placeholder type by position: %1$s and %s both give "s"; a literal %% is not a placeholder.
// ponytail: positional types only, so an explicit-index reorder with different types (%2$d %1$s vs %1$s %2$d) is flagged. Add an index-aware compare when a translation needs to reorder.
const PLACEHOLDER_RE = /%%|%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?([a-zA-Z])/g;

const ph = (text) => `[${[...text.matchAll(PLACEHOLDER_RE)].filter((m) => m[0] !== '%%').map((m) => m[1]).join(',')}]`;
const isNfc = (text) => text.normalize('NFC') === text;

// ponytail: regex scan, not an XML parser. Covers <string name> and <plurals>/<item quantity>; no CDATA,
// <string-array> or translatable="false". The count guard turns a misparse into a failure instead of a silent pass.
function parse(xml, lang) {
  const src = xml.replace(COMMENT_RE, '');
  const strings = new Map([...src.matchAll(STRING_RE)].map((m) => [m[1], m[2]]));
  const plurals = new Map(
    [...src.matchAll(PLURALS_RE)].map((m) => [m[1], new Map([...m[2].matchAll(ITEM_RE)].map((i) => [i[1], i[2]]))]),
  );
  const found = (src.match(/<(?:string|plurals)\s+name=/g) ?? []).length;
  const parsed = strings.size + plurals.size;
  const issues = parsed === found ? [] : [`${lang}: parsed ${parsed} of ${found} <string>/<plurals> entries`];
  return { strings, plurals, issues };
}

function quantityIssues(key, lang, items) {
  const invalid = [...items.keys()].filter((q) => !VALID_QUANTITIES.includes(q));
  const missing = REQUIRED_QUANTITIES[lang].filter((q) => !items.has(q));
  return [
    ...invalid.map((q) => `invalid quantity "${q}" in ${key} (${lang})`),
    ...missing.map((q) => `missing quantity "${q}" in ${key} (${lang})`),
  ];
}

// Compares a base (en) and a translation (vi) strings.xml; returns issue messages (empty = pass).
export function checkStrings(baseXml, viXml) {
  const en = parse(baseXml, 'en');
  const vi = parse(viXml, 'vi');
  const issues = [...en.issues, ...vi.issues];

  for (const k of en.strings.keys()) if (!vi.strings.has(k)) issues.push(`missing key in vi: ${k}`);
  for (const k of vi.strings.keys()) if (!en.strings.has(k)) issues.push(`extra key in vi: ${k}`);
  for (const k of en.plurals.keys()) if (!vi.plurals.has(k)) issues.push(`missing plurals in vi: ${k}`);
  for (const k of vi.plurals.keys()) if (!en.plurals.has(k)) issues.push(`extra plurals in vi: ${k}`);

  for (const [k, enText] of en.strings) {
    const viText = vi.strings.get(k);
    if (viText === undefined) continue;
    if (ph(enText) !== ph(viText)) issues.push(`placeholder mismatch in ${k}: en ${ph(enText)} vs vi ${ph(viText)}`);
  }
  for (const [k, viText] of vi.strings) if (!isNfc(viText)) issues.push(`vi text not NFC: ${k}`);

  for (const [k, enItems] of en.plurals) {
    const viItems = vi.plurals.get(k);
    if (viItems === undefined) continue;
    issues.push(...quantityIssues(k, 'en', enItems), ...quantityIssues(k, 'vi', viItems));
    // Each vi item must carry the placeholders of en "other".
    const ref = enItems.get('other');
    for (const [q, text] of viItems) {
      if (ref !== undefined && ph(text) !== ph(ref)) issues.push(`placeholder mismatch in ${k}[${q}]: vi ${ph(text)} vs en other ${ph(ref)}`);
      if (!isNfc(text)) issues.push(`vi text not NFC: ${k}[${q}]`);
    }
  }
  return issues;
}

// Checks every { base, vi } directory pair (each holds a strings.xml) under root.
export function checkPairs(root, pairs) {
  const issues = [];
  for (const { base, vi } of pairs) {
    const files = [base, vi].map((dir) => path.join(root, dir, 'strings.xml'));
    const missing = files.filter((f) => !fs.existsSync(f));
    if (missing.length > 0) {
      issues.push(...missing.map((f) => `missing file: ${path.relative(root, f)}`));
      continue;
    }
    const [baseXml, viXml] = files.map((f) => fs.readFileSync(f, 'utf8'));
    issues.push(...checkStrings(baseXml, viXml).map((m) => `${vi}: ${m}`));
  }
  return issues;
}

// CLI: runs only when executed directly, so the tests can import this file.
if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const { pairs = [] } = JSON.parse(fs.readFileSync(path.join(ROOT, 'docs', 'qa', 'strings.json'), 'utf8'));
  const issues = pairs.length > 0 ? checkPairs(ROOT, pairs) : ['docs/qa/strings.json: no pairs configured'];
  if (issues.length > 0) {
    for (const issue of issues) console.error(issue);
    process.exitCode = 1;
  } else {
    console.log(`strings OK: ${pairs.length} pair(s)`);
  }
}
