#!/usr/bin/env node
// SDK inventory guard: every SwiftPM pin in the lock files matched by docs/qa/sdk-sources.json `lockFiles` must
// appear, case-insensitively, under a heading containing "iOS" in `inventoryDoc`, unless `ignore` lists it with a
// non-empty `reason`. Gradle deps are intentionally not checked here.
// Usage: node scripts/qa/check-sdk-inventory.mjs [--root <dir>]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const reEsc = (s) => s.replace(/[.+^${}()|[\]\\]/g, '\\$&');

// Repo-relative files matching a glob such as iosApp/**/Package.resolved; walks only the literal prefix dir.
function globFiles(root, glob) {
  const segs = glob.split('/');
  const cut = segs.findIndex((s) => /[*?]/.test(s));
  const base = path.join(root, ...segs.slice(0, cut < 0 ? -1 : cut));
  const out = [];
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p);
      else {
        const rel = path.relative(root, p).split(path.sep).join('/');
        if (path.matchesGlob(rel, glob)) out.push(rel);
      }
    }
  };
  if (fs.existsSync(base)) walk(base);
  return out;
}

// Text under each heading that contains "iOS", up to the next heading of the same or a higher level.
function iosSections(md) {
  const lines = md.split(/\r?\n/);
  const heads = [];
  let fence = false;
  lines.forEach((line, i) => {
    if (/^\s*(```|~~~)/.test(line)) fence = !fence;
    else if (!fence && /^#{1,6}\s/.test(line)) heads.push({ i, level: line.match(/^#+/)[0].length, line });
  });
  return heads
    .filter((h) => h.line.includes('iOS'))
    .map((h) => {
      const next = heads.find((n) => n.i > h.i && n.level <= h.level);
      return lines.slice(h.i + 1, next ? next.i : lines.length).join('\n');
    });
}

// -> { problems: [string] (empty = OK), out: [string] (stdout lines) }
function check(root) {
  const cfg = JSON.parse(fs.readFileSync(path.join(root, 'docs/qa/sdk-sources.json'), 'utf8'));
  const problems = [];
  const ignored = new Set();
  for (const e of cfg.ignore ?? []) {
    if (e.identity && String(e.reason ?? '').trim()) ignored.add(e.identity.toLowerCase());
    else problems.push(`docs/qa/sdk-sources.json: ignore entry "${e.identity ?? '?'}" has no reason`);
  }
  const lockFiles = cfg.lockFiles.flatMap((g) => globFiles(root, g));
  if (!lockFiles.length) {
    return { problems, out: [`NOTE: no lock file matches ${cfg.lockFiles.join(', ')}; SDK inventory check skipped (Gradle deps are not checked by design)`] };
  }
  const pins = new Map(); // lower-cased identity -> { name, file }
  for (const file of lockFiles) {
    const lock = JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
    if (!Array.isArray(lock.pins)) {
      problems.push(`${file}: no top-level "pins" array (Package.resolved v1 is not supported)`);
      continue;
    }
    for (const p of lock.pins) {
      if (p.identity) pins.set(p.identity.toLowerCase(), { name: p.identity, file });
      else problems.push(`${file}: pin without "identity"`);
    }
  }
  const sections = iosSections(fs.readFileSync(path.join(root, cfg.inventoryDoc), 'utf8'));
  if (!sections.length) return { problems: [...problems, `${cfg.inventoryDoc}: no heading containing "iOS"`], out: [] };
  const text = sections.join('\n');
  for (const [key, { name, file }] of pins) {
    if (ignored.has(key) || new RegExp(`(?<![\\w-])${reEsc(key)}(?![\\w-])`, 'i').test(text)) continue;
    problems.push(`SwiftPM pin "${name}" (${file}) is not listed under an iOS heading in ${cfg.inventoryDoc}`);
  }
  return { problems, out: problems.length ? [] : [`OK: ${pins.size} SwiftPM pin(s) checked against ${cfg.inventoryDoc}`] };
}

function main() {
  const args = process.argv.slice(2);
  const root = args.includes('--root') ? path.resolve(args[args.indexOf('--root') + 1]) : REPO;
  const { problems, out } = check(root);
  out.forEach((line) => console.log(line));
  if (problems.length) {
    console.error(problems.join('\n'));
    console.error(`FAIL: ${problems.length} problem(s)`);
    process.exitCode = 1;
  }
}

main();
