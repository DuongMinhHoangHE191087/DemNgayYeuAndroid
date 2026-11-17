#!/usr/bin/env node
// Workflow policy guard for .github/workflows/*.yml. Per-line scan, not a YAML parser.
// Usage: node scripts/qa/check-workflows.mjs [dir]   (default: <repo>/.github/workflows; a missing dir exits 0)
// ponytail: indentation-based scan. YAML anchors, flow mappings (on: {push: ...}), multi-line flow lists
// and expression runs-on (${{ matrix.os }}) are not followed. Upgrade to a real YAML parser if these show up.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const PIN = /^(?:v\d+\.\d+\.\d+|[0-9a-f]{40})$/;

const indentOf = (s) => s.length - s.trimStart().length;
const unquote = (s) => s.trim().replace(/^(['"])(.*)\1$/, '$2');
// Drop a YAML comment: '#' at line start or after whitespace. A quoted '#' is not special-cased.
const code = (line) => line.replace(/(^|\s)#.*$/, '');

// Indices of the non-blank lines nested under line i.
function children(lines, i) {
  const out = [];
  for (let j = i + 1; j < lines.length; j++) {
    if (lines[j].trim() === '') continue;
    if (indentOf(lines[j]) <= indentOf(lines[i])) break;
    out.push(j);
  }
  return out;
}

// Direct child keys of line i: [{ at, key, value }].
function keys(lines, i) {
  const kids = children(lines, i);
  const level = kids.length ? indentOf(lines[kids[0]]) : 0;
  const out = [];
  for (const j of kids) {
    const m = /^\s*([\w-]+)\s*:\s*(.*)$/.exec(lines[j]);
    if (m && indentOf(lines[j]) === level) out.push({ at: j, key: m[1], value: m[2].trim() });
  }
  return out;
}

// Items of a flow list [a, b], a scalar, or a block list nested under line at.
function list(lines, at, value) {
  if (value.startsWith('[')) return value.replace(/^\[|\]$/g, '').split(',').map(unquote).filter(Boolean);
  if (value !== '') return [unquote(value)];
  return children(lines, at).map((j) => unquote(lines[j].trim().replace(/^-\s*/, ''))).filter(Boolean);
}

// Trigger name -> { branches, tags }, each null when the filter is absent.
// Covers `on: push`, `on: [push, pull_request]` and the block form.
function triggers(lines, on) {
  const out = new Map();
  for (const name of lines[on].replace(/^on\s*:/, '').split(/[\s,[\]]+/).filter(Boolean)) {
    out.set(name, { branches: null, tags: null });
  }
  for (const k of keys(lines, on)) {
    const filter = { branches: null, tags: null };
    for (const f of keys(lines, k.at)) {
      if (f.key === 'branches' || f.key === 'tags') filter[f.key] = list(lines, f.at, f.value);
    }
    out.set(k.key, filter);
  }
  return out;
}

function checkWorkflow(file, text) {
  const lines = text.split(/\r?\n/).map(code);
  const problems = [];
  const at = (i, rule, msg) => problems.push(`${file}:${i + 1}: [${rule}] ${msg}`);
  const whole = (rule, msg) => problems.push(`${file}: [${rule}] ${msg}`);

  lines.forEach((line, i) => {
    // Local actions (./...) live in this repo and cannot be pinned; everything else needs vX.Y.Z or a full SHA.
    const u = /^\s*(?:-\s+)?uses\s*:\s*['"]?([^'"\s]+)/.exec(line);
    if (u && !u[1].startsWith('./') && !PIN.test(u[1].slice(u[1].lastIndexOf('@') + 1))) {
      at(i, 'unpinned-action', `uses ${u[1]} is not pinned to vX.Y.Z or a 40-hex SHA`);
    }
    if (/\bpull_request_target\b/.test(line)) at(i, 'pull-request-target', 'pull_request_target is banned');
  });

  const onAt = lines.findIndex((l) => /^on\s*:/.test(l));
  const trig = onAt < 0 ? new Map() : triggers(lines, onAt);
  const jobsAt = lines.findIndex((l) => /^jobs\s*:/.test(l));
  const jobs = jobsAt < 0 ? [] : keys(lines, jobsAt);

  if (trig.has('pull_request')) {
    lines.forEach((line, i) => {
      if (/\bsecrets\./.test(line) || /^\s*(?:-\s+)?secrets\s*:\s*inherit\b/.test(line)) {
        at(i, 'pr-secrets', 'pull_request workflow must not use secrets');
      }
    });
  }

  for (const [name, f] of trig) {
    if ((name === 'pull_request' || name === 'push') && f.branches && !f.branches.includes('kmp-ios')) {
      whole('kmp-ios-branch', `${name} branches [${f.branches.join(', ')}] must list kmp-ios`);
    }
  }

  const gated = jobs.some((j) => keys(lines, j.at).some((k) => k.key === 'environment'));
  if (gated) {
    for (const [name, f] of trig) {
      const tagPush = name === 'push' && f.tags && !f.branches;
      if (name !== 'workflow_dispatch' && !tagPush) {
        whole('environment-trigger', `a job uses environment: but ${name} can start it; only tag push or workflow_dispatch`);
      }
    }
  }

  for (const j of jobs) {
    const ks = keys(lines, j.at);
    const runsOn = ks.find((k) => k.key === 'runs-on');
    const macos = runsOn && list(lines, runsOn.at, runsOn.value).some((l) => /^macos/i.test(l));
    if (macos && !ks.some((k) => k.key === 'timeout-minutes')) {
      at(j.at, 'macos-timeout', `job ${j.key} runs on macOS without timeout-minutes`);
    }
  }
  return problems;
}

export function checkDir(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((f) => /\.ya?ml$/.test(f))
    .sort()
    .flatMap((f) => checkWorkflow(f, fs.readFileSync(path.join(dir, f), 'utf8')));
}

function main() {
  const dir = path.resolve(process.argv[2] ?? path.join(REPO, '.github', 'workflows'));
  if (!fs.existsSync(dir)) {
    console.log(`no workflows dir at ${dir}; nothing to check`);
    return;
  }
  const problems = checkDir(dir);
  if (problems.length) {
    console.error(problems.join('\n'));
    console.error(`FAIL: ${problems.length} workflow policy problem(s)`);
    process.exit(1);
  }
  console.log(`OK: workflow policy holds for ${dir}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
