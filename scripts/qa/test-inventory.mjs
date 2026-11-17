#!/usr/bin/env node
// Test inventory guard: every test recorded in docs/qa/test-inventory.json must still exist
// (same file), or be listed under `moved` (and really exist in the destination), or `deleted` with a reason.
// Usage: node scripts/qa/test-inventory.mjs --init | --check [--root <dir>] [--inventory <file>]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const EXTS = new Set(['.kt', '.java', '.swift', '.js', '.mjs', '.cjs', '.ts']);

const subdirs = (dir) =>
  fs.existsSync(dir) ? fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name) : [];

// Test roots (repo-relative); `shared/src/*Test` and `iosApp/Packages/*/Tests` are expanded, missing dirs skipped.
function testRoots(root) {
  return [
    'app/src/test',
    'app/src/androidTest',
    ...subdirs(path.join(root, 'shared/src')).filter((d) => d.endsWith('Test')).map((d) => `shared/src/${d}`),
    ...subdirs(path.join(root, 'iosApp/Packages')).map((p) => `iosApp/Packages/${p}/Tests`),
    'iosApp/UITests',
    'scripts/rules-tests',
    'firebase/functions/test',
    'firebase/rules-tests/test',
  ].filter((r) => fs.existsSync(path.join(root, r)));
}

function* walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => (a.name < b.name ? -1 : 1))) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) yield* walk(p);
    else if (EXTS.has(path.extname(e.name))) yield p;
  }
}

// Test names in source order. ponytail: regex scan, not a parser; commented-out `it(` is still counted.
export function extractTests(file, src) {
  const ext = path.extname(file);
  let re;
  if (ext === '.swift') re = /\bfunc\s+(test\w*)\s*\(/g;
  else if (ext === '.kt' || ext === '.java') re = /@Test\b(?:\([^)]*\))?[\s\S]*?\b(?:fun\s+(`[^`]+`|\w+)|void\s+(\w+)\s*\()/g;
  else re = /(?<![\w.$])(?:it|test)(?:\.\w+)?\(\s*(['"`])((?:\\.|(?!\1).)*)\1/g;
  const names = [];
  const lineStart = /^[ \t]*\/\/|^[ \t]*\*/;
  for (const m of src.matchAll(re)) {
    // skip matches whose line is a // or * comment
    const ls = src.lastIndexOf('\n', m.index) + 1;
    if (lineStart.test(src.slice(ls, src.indexOf('\n', m.index) < 0 ? undefined : src.indexOf('\n', m.index)))) continue;
    const raw = ext === '.swift' ? m[1] : ext === '.kt' || ext === '.java' ? m[1] ?? m[2] : m[2];
    names.push(raw.replace(/^`|`$/g, ''));
  }
  return names;
}

// -> { "<repo-relative path>": [test names] } for files with at least one test.
export function scanRoot(root) {
  const out = {};
  for (const r of testRoots(root)) {
    for (const f of walk(path.join(root, r))) {
      const tests = extractTests(f, fs.readFileSync(f, 'utf8'));
      if (tests.length) out[path.relative(root, f).split(path.sep).join('/')] = tests;
    }
  }
  return out;
}

export function buildInventory(root) {
  const files = {};
  for (const [f, tests] of Object.entries(scanRoot(root))) files[f] = { count: tests.length, tests };
  return { files };
}

const testsIn = (root, rel) => {
  const p = path.join(root, rel);
  return fs.existsSync(p) && fs.statSync(p).isFile() ? extractTests(rel, fs.readFileSync(p, 'utf8')) : [];
};

// -> list of problem strings (empty = OK). New, unrecorded tests are always fine.
export function checkInventory(root, inventory) {
  const problems = [];
  for (const [file, rec] of Object.entries(inventory.files ?? {})) {
    const here = new Set(testsIn(root, file));
    for (const t of rec.tests ?? []) {
      if (here.has(t)) continue;
      const mv = (rec.moved ?? []).find((m) => m.test === t);
      if (mv) {
        const as = mv.as ?? t;
        if (testsIn(root, mv.to).includes(as)) continue;
        problems.push(`${file}#${t}: moved to ${mv.to}#${as} but it is not there`);
        continue;
      }
      const del = (rec.deleted ?? []).find((d) => d.test === t);
      if (del?.reason) continue;
      problems.push(`${file}#${t}: missing (no moved/deleted record${del ? ' with a reason' : ''})`);
    }
  }
  return problems;
}

function main() {
  const args = process.argv.slice(2);
  const opt = (name, dflt) => (args.includes(name) ? path.resolve(args[args.indexOf(name) + 1]) : dflt);
  const root = opt('--root', REPO);
  const invPath = opt('--inventory', path.join(root, 'docs/qa/test-inventory.json'));
  if (args.includes('--init')) {
    const inv = buildInventory(root);
    fs.mkdirSync(path.dirname(invPath), { recursive: true });
    fs.writeFileSync(invPath, JSON.stringify(inv, null, 2) + '\n');
    const n = Object.values(inv.files).reduce((s, v) => s + v.count, 0);
    console.log(`wrote ${path.relative(root, invPath)}: ${Object.keys(inv.files).length} files, ${n} tests`);
  } else if (args.includes('--check')) {
    const problems = checkInventory(root, JSON.parse(fs.readFileSync(invPath, 'utf8')));
    if (problems.length) {
      console.error(problems.join('\n'));
      console.error(`FAIL: ${problems.length} test(s) lost`);
      process.exit(1);
    }
    console.log('OK: every inventoried test is present, moved or deleted with a reason');
  } else {
    console.error('usage: test-inventory.mjs --init | --check [--root <dir>] [--inventory <file>]');
    process.exit(2);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
