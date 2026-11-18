#!/usr/bin/env node
// Unified QA gate runner for CI and pre-commit / pre-PR local checks.
// Runs:
//   1. check-workflows.mjs
//   2. check-grep-gates.mjs
//   3. check-fakes.mjs
//   4. check-architecture.mjs
//   5. node --test scripts/qa/test/*.test.mjs
//   6. node --test "firebase/functions/test/**/*.test.js"
// Usage: node scripts/qa/run-all-gates.mjs [--root <dir>]
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

function collectFiles(dir, suffix) {
  if (!fs.existsSync(dir)) return [];
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => (a.name < b.name ? -1 : 1))) {
    if (entry.name === 'node_modules' || entry.name === '.git') continue;
    const abs = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...collectFiles(abs, suffix));
    else if (entry.isFile() && entry.name.endsWith(suffix)) out.push(abs);
  }
  return out;
}

function summarizeOutput(res) {
  const combined = `${res.stdout ?? ''}\n${res.stderr ?? ''}`.trim();
  const lines = combined ? combined.split(/\r?\n/) : [];
  const passLine = [...lines].reverse().find((l) => /^ℹ pass \d+/.test(l));
  const failLine = [...lines].reverse().find((l) => /^ℹ fail \d+/.test(l));
  if (passLine) {
    const p = passLine.replace(/^ℹ pass\s+/, '');
    const f = failLine ? failLine.replace(/^ℹ fail\s+/, '') : '0';
    return `${p} passed, ${f} failed`;
  }
  const okLine = lines.find((l) => /^OK[: ]/.test(l));
  if (res.status === 0 && okLine) return okLine;
  const failMsg = [...lines].reverse().find((l) => /^FAIL[: ]/.test(l)) ?? lines[lines.length - 1] ?? `exit ${res.status}`;
  return failMsg.slice(0, 72);
}

function runGate(root, gate) {
  console.log(`\n==> [${gate.name}]`);
  const t0 = Date.now();
  const args = gate.args();
  if (args === null) {
    const ms = Date.now() - t0;
    console.error(`FAIL: no matching test files found for ${gate.name}`);
    return { name: gate.name, ok: false, ms, details: 'no matching test files' };
  }
  const res = spawnSync(process.execPath, args, { cwd: root, encoding: 'utf8' });
  const ms = Date.now() - t0;
  if (res.stdout) process.stdout.write(res.stdout);
  if (res.stderr) process.stderr.write(res.stderr);
  const ok = res.status === 0;
  return { name: gate.name, ok, ms, details: summarizeOutput(res) };
}

function printSummary(results) {
  const wName = Math.max(20, ...results.map((r) => r.name.length));
  const wStatus = 6;
  const wTime = 8;
  const header = `${'Gate'.padEnd(wName)}  ${'Status'.padEnd(wStatus)}  ${'Duration'.padStart(wTime)}  Details`;
  const rule = `${'-'.repeat(wName)}  ${'-'.repeat(wStatus)}  ${'-'.repeat(wTime)}  ${'-'.repeat(36)}`;
  console.log('\n=== InLove QA Gates Summary ===');
  console.log(header);
  console.log(rule);
  for (const r of results) {
    const status = r.ok ? 'PASS' : 'FAIL';
    const dur = `${r.ms}ms`.padStart(wTime);
    console.log(`${r.name.padEnd(wName)}  ${status.padEnd(wStatus)}  ${dur}  ${r.details}`);
  }
  const passed = results.filter((r) => r.ok).length;
  console.log(`\nTotal: ${passed}/${results.length} gate(s) passed.`);
}

export function runAllGates(root = REPO) {
  const qaTests = () => {
    const files = collectFiles(path.join(root, 'scripts', 'qa', 'test'), '.test.mjs');
    return files.length ? ['--test', ...files] : null;
  };
  const fnTests = () => {
    const files = collectFiles(path.join(root, 'firebase', 'functions', 'test'), '.test.js');
    return files.length ? ['--test', ...files] : null;
  };

  const gates = [
    {
      name: 'check-workflows',
      args: () => [path.join(root, 'scripts/qa/check-workflows.mjs'), path.join(root, '.github/workflows')],
    },
    {
      name: 'check-grep-gates',
      args: () => [path.join(root, 'scripts/qa/check-grep-gates.mjs'), '--root', root],
    },
    {
      name: 'check-fakes',
      args: () => [path.join(root, 'scripts/qa/check-fakes.mjs'), '--root', root],
    },
    {
      name: 'check-architecture',
      args: () => [path.join(root, 'scripts/qa/check-architecture.mjs'), '--root', root],
    },
    {
      name: 'qa-unit-tests',
      args: qaTests,
    },
    {
      name: 'functions-unit-tests',
      args: fnTests,
    },
  ];

  const results = gates.map((g) => runGate(root, g));
  printSummary(results);
  return results.every((r) => r.ok) ? 0 : 1;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const root = args.includes('--root') ? path.resolve(args[args.indexOf('--root') + 1]) : REPO;
  process.exitCode = runAllGates(root);
}
