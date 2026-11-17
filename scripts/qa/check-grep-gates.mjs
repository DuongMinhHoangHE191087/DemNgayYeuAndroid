#!/usr/bin/env node
// Grep gate runner: replaces `rg` for forbidden-pattern gates. Node built-ins only.
// Usage: node scripts/qa/check-grep-gates.mjs [--root <dir>] [--config <gates.json>]
// Exit: 0 = clean, 1 = at least one match (printed as file:line), 2 = bad config or arguments.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const ALLOWED = ['id', 'pattern', 'paths', 'exceptGlobs', 'reason'];
const args = process.argv.slice(2);
const opt = (name, dflt) => (args.includes(name) ? path.resolve(args[args.indexOf(name) + 1]) : dflt);

// ponytail: globs support only *, ** and ?; .gitignore is not read. Add [..] or {a,b} when a gate needs them.
function globToRegExp(glob) {
  let re = '';
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === '*' && glob[i + 1] === '*') {
      if (glob[i + 2] === '/') { re += '(?:.*/)?'; i += 2; } else { re += '.*'; i += 1; }
    } else if (c === '*') re += '[^/]*';
    else if (c === '?') re += '[^/]';
    else re += c.replace(/[.+^${}()|[\]\\]/g, '\\$&');
  }
  return new RegExp('^' + re + '$');
}

function loadGates(file) {
  const parsed = JSON.parse(fs.readFileSync(file, 'utf8').replace(/^﻿/, ''));
  if (!Array.isArray(parsed)) throw new Error(`${file}: must be an array of gates`);
  return parsed.map((g, i) => {
    const where = `${file} gate #${i + 1}`;
    if (typeof g !== 'object' || g === null || Array.isArray(g)) throw new Error(`${where}: must be an object`);
    for (const key of Object.keys(g)) {
      if (!ALLOWED.includes(key)) throw new Error(`${where}: unknown field "${key}" (allowed: ${ALLOWED.join(', ')})`);
    }
    if (typeof g.id !== 'string' || !g.id) throw new Error(`${where}: missing id`);
    if (typeof g.pattern !== 'string') throw new Error(`${where}: missing pattern`);
    if (typeof g.reason !== 'string') throw new Error(`${where}: missing reason`);
    if (!Array.isArray(g.paths) || g.paths.length === 0 || !g.paths.every((p) => typeof p === 'string')) {
      throw new Error(`${where}: paths must be a non-empty array of strings`);
    }
    const exceptGlobs = g.exceptGlobs ?? [];
    if (!Array.isArray(exceptGlobs) || !exceptGlobs.every((p) => typeof p === 'string')) {
      throw new Error(`${where}: exceptGlobs must be an array of strings`);
    }
    let re;
    try { re = new RegExp(g.pattern); } catch (e) { throw new Error(`${where}: invalid pattern (${e.message})`); }
    return { id: g.id, re, paths: g.paths, except: exceptGlobs.map(globToRegExp), reason: g.reason };
  });
}

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    return e.isDirectory() ? walk(p) : e.isFile() ? [p] : [];
  });
}

function scan(root, gate) {
  const files = new Set();
  for (const p of gate.paths) {
    const abs = path.join(root, p);
    if (!fs.existsSync(abs)) continue; // scan dir not created yet (e.g. before P2): nothing to scan
    (fs.statSync(abs).isDirectory() ? walk(abs) : [abs]).forEach((f) => files.add(f));
  }
  const hits = [];
  for (const file of [...files].sort()) {
    const rel = path.relative(root, file).split(path.sep).join('/');
    if (gate.except.some((rx) => rx.test(rel))) continue;
    const lines = fs.readFileSync(file, 'utf8').replace(/^﻿/, '').split(/\r?\n/);
    lines.forEach((line, i) => {
      if (gate.re.test(line)) hits.push(`${rel}:${i + 1}: [${gate.id}] ${gate.reason}`);
    });
  }
  return hits;
}

try {
  const root = opt('--root', REPO);
  const gates = loadGates(opt('--config', path.join(root, 'docs', 'qa', 'grep-gates.json')));
  const hits = gates.flatMap((g) => scan(root, g));
  if (hits.length) {
    console.log(hits.join('\n'));
    console.error(`FAIL: ${hits.length} match(es)`);
    process.exitCode = 1;
  } else {
    console.log(`OK: ${gates.length} gate(s), 0 match`);
  }
} catch (e) {
  console.error(`check-grep-gates: ${e.message}`);
  process.exitCode = 2;
}
