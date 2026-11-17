#!/usr/bin/env node
// Fake guard: every `interface` / `fun interface` in shared/src/commonMain whose name matches interfaceNameRegex
// needs `class|object Fake<Name>` in a commonMain dir named testing/ or fakes/ (any depth) or anywhere in shared/src/commonTest.
// Names listed under "exempt" in docs/qa/fakes.json need no Fake, but every exempt entry needs a reason.
// Usage: node scripts/qa/check-fakes.mjs [--root <dir>]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const interfaceNameRegex = /(Gateway|Api|Invoker|Storage|Store|Clock|Engine)$/;

function* kotlinFiles(dir) {
  if (!fs.existsSync(dir)) return;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) yield* kotlinFiles(p);
    else if (e.name.endsWith('.kt')) yield p;
  }
}

// ponytail: regex scan, not a Kotlin parser. Only comments are stripped, so "interface X" inside a string literal would still count.
const code = (src) => src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');

// -> list of problem strings (empty = OK). No shared/ dir means nothing to check.
export function checkFakes(root) {
  const shared = path.join(root, 'shared');
  if (!fs.existsSync(shared)) return [];
  const commonMain = path.join(shared, 'src/commonMain');
  const commonTest = path.join(shared, 'src/commonTest');
  const rel = (f) => path.relative(root, f).split(path.sep).join('/');
  const problems = [];

  const cfg = path.join(root, 'docs/qa/fakes.json');
  const exempt = fs.existsSync(cfg) ? JSON.parse(fs.readFileSync(cfg, 'utf8')).exempt ?? [] : [];
  for (const e of exempt) {
    if (typeof e.reason !== 'string' || !e.reason.trim()) problems.push(`docs/qa/fakes.json: exempt "${e.name}" has no reason`);
  }
  const exemptNames = new Set(exempt.map((e) => e.name));

  // A Fake counts when it sits in commonTest, or in a commonMain dir named testing/ or fakes/.
  const fakes = new Set();
  const addFakes = (f) => {
    for (const m of code(fs.readFileSync(f, 'utf8')).matchAll(/\b(?:class|object)\s+Fake(\w+)\b/g)) fakes.add(m[1]);
  };
  for (const f of kotlinFiles(commonTest)) addFakes(f);
  for (const f of kotlinFiles(commonMain)) {
    const dirs = path.relative(commonMain, f).split(/[\\/]/).slice(0, -1);
    if (dirs.includes('testing') || dirs.includes('fakes')) addFakes(f);
  }

  for (const f of kotlinFiles(commonMain)) {
    for (const m of code(fs.readFileSync(f, 'utf8')).matchAll(/\binterface\s+(\w+)/g)) {
      const name = m[1];
      if (!interfaceNameRegex.test(name) || fakes.has(name) || exemptNames.has(name)) continue;
      problems.push(
        `${rel(f)}: interface ${name} has no class/object Fake${name} (put it in commonMain testing/ or fakes/, in shared/src/commonTest, or add an exempt entry with a reason)`,
      );
    }
  }
  return problems;
}

function main() {
  const args = process.argv.slice(2);
  const root = args.includes('--root') ? path.resolve(args[args.indexOf('--root') + 1]) : REPO;
  const problems = checkFakes(root);
  if (problems.length) {
    console.error(problems.join('\n'));
    console.error(`FAIL: ${problems.length} problem(s)`);
    process.exit(1);
  }
  console.log('OK: every matching interface in shared/src/commonMain has a Fake or an exempt entry');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
