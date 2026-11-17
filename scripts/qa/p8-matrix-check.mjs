#!/usr/bin/env node
// P8 feature-parity matrix checker (V-06). Reads p8-feature-parity-matrix.md and the task tables of p1..p7.
// Usage: node scripts/qa/p8-matrix-check.mjs [--strict] [--matrix <path>]
// Exit: 0 clean, 1 problems found, 2 usage or read error. Pending owners are printed; --strict fails on them.
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const PLAN_DIR = path.join(REPO, 'docs', 'superpowers', 'plans', '2026-10-13-kmp-ios');
const DEFAULT_MATRIX = path.join(PLAN_DIR, 'p8-feature-parity-matrix.md');
const RAW_ROOTS = [path.join(REPO, 'shared', 'src'), path.join(REPO, 'app', 'src')];
const FORBIDDEN = 'TODO';
// Citations allowed without a row: raw golden files and aliases the matrix names on purpose.
const GV_WHITELIST = new Set(['GV-000', 'GV-050', 'GV-051', 'GV-052', 'GV-060', 'GV-061', 'GV-201', 'GV-202', 'GV-204']);
// ponytail: V-02 and V-03 are reserved on purpose (matrix line 646). Add an id only with a matching sentence in the matrix.
const V_RESERVED = new Set(['V-02', 'V-03']);
// Main tables: id pattern, column count (null = not checked), and the section that holds the table.
const MAIN = {
  FP: { section: 2, cols: 12, id: /^FP-\d{3}$/ },
  MQ: { section: 3, cols: 8, id: /^MQ-\d{3}$/ },
  GV: { section: 4, cols: 7, id: /^GV-\d{3}$/ },
  PF: { section: 5, cols: 7, id: /^PF-\d{3}$/ },
  V: { section: 9, cols: null, id: /^V-\d{2}$/ },
};
const TASK_ID = /^[A-Z]{1,2}-\d+[a-z]?$/;
const PENDING_OWNER = /^pending-owner:(?:P\d+:.+|[A-Z]{1,2}-\d+[a-z]?)$/;
const GAP_OWNER = /^GAP:P\d+$/;
const SINGLE = /\b(?:FP|MQ|GV|PF)-\d{3}\b|\bV-\d{2}\b/g;
// "A..B" or "A đến B", endpoints may be wrapped in backticks. The word is spelled with code points (U+0111 U+1EBF n) so the source stays NFC.
const RANGE = /\b((?:FP|MQ|GV|PF|V)-\d{2,3})`?\s*(?:\.\.|đến)\s*`?(?:(?:FP|MQ|GV|PF|V)-)?(\d{2,3})\b/g;
const REPLAY = /\b\w*ReplayTest\b/g;
const COVER_V = ['V-10', 'V-11', 'V-16'];
const REQUIRED_DEFS = ['FP-112', 'FP-213', 'GV-029', 'PF-061', 'MQ-110', 'MQ-111', 'MQ-112', 'MQ-113', 'MQ-114', 'MQ-309'];
const norm = (s) => s.normalize('NFC');

// Cells of one pipe-table row. \| is a literal pipe inside a cell.
function splitRow(line) {
  const t = line.trim();
  const inner = t.slice(1, t.endsWith('|') ? -1 : undefined);
  return inner.split(/(?<!\\)\|/).map((c) => c.replace(/\\\|/g, '|').trim());
}

// Markdown tables outside fenced code, each with the "## N." section it sits in.
// ponytail: line-based regex parser, not a Markdown AST. Ceiling: a table row wrapped over two lines is not seen.
function parseTables(lines) {
  const out = [];
  let inFence = false;
  let section = null;
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    if (/^\s*```/.test(l)) { inFence = !inFence; continue; }
    if (inFence) continue;
    const h = /^##\s+(\d+)\./.exec(l);
    if (h) section = Number(h[1]);
    if (/^\s*\|/.test(l) && i + 1 < lines.length && /^\s*\|[\s:|-]+\|\s*$/.test(lines[i + 1])) {
      const rows = [];
      let j = i + 2;
      while (j < lines.length && /^\s*\|/.test(lines[j])) { rows.push({ line: j + 1, cells: splitRow(lines[j]) }); j++; }
      out.push({ line: i + 1, section, header: splitRow(l), rows });
      i = j - 1;
    }
  }
  return out;
}

// Ids cited on one line. Singles exclude range spans; each range returns the ids it expands to.
function citations(line) {
  const ranges = [];
  let rest = line;
  for (const m of line.matchAll(RANGE)) {
    const [prefix, from] = m[1].split('-');
    const width = prefix === 'V' ? 2 : 3;
    const ids = [];
    for (let n = Number(from); n <= Number(m[2]); n++) ids.push(`${prefix}-${String(n).padStart(width, '0')}`);
    ranges.push({ prefix, ids });
    rest = rest.replace(m[0], ' '.repeat(m[0].length));
  }
  return { singles: [...rest.matchAll(SINGLE)].map((m) => m[0]), ranges };
}

// Task ids from every table that has id, depends and size columns (the p1..p7 task tables).
export function collectTaskIds(texts) {
  const ids = new Set();
  for (const text of texts) {
    for (const t of parseTables(norm(text).split(/\r?\n/))) {
      const h = t.header.map((c) => c.toLowerCase());
      if (h[0] !== 'id' || !h.some((c) => c.startsWith('depend')) || !h.some((c) => c.startsWith('size'))) continue;
      for (const r of t.rows) if (TASK_ID.test(r.cells[0])) ids.add(r.cells[0]);
    }
  }
  return ids;
}

// Raw golden JSON: every {nfc, nfd} pair must differ byte for byte.
export function checkRawVectors(file, text) {
  let data;
  try {
    data = JSON.parse(text.replace(/^﻿/, ''));
  } catch (e) {
    return [`${file}: not valid JSON (${e.message})`];
  }
  const problems = [];
  const walk = (node, at) => {
    if (Array.isArray(node)) return node.forEach((v, i) => walk(v, `${at}[${i}]`));
    if (node === null || typeof node !== 'object') return;
    const { nfc: a, nfd: b } = node;
    if (typeof a === 'string' && typeof b === 'string' && Buffer.from(a, 'utf8').equals(Buffer.from(b, 'utf8'))) {
      problems.push(`${file} ${at}: nfc and nfd are byte-identical`);
    }
    for (const [k, v] of Object.entries(node)) walk(v, `${at}.${k}`);
  };
  walk(data, '$');
  return problems;
}

// Returns { problems, pending, counts }. pending lists owner tokens still open (pending-owner:, GAP:).
export function checkMatrix(text, { taskIds = new Set(), strict = false } = {}) {
  const problems = [];
  const pending = [];
  const lines = norm(text).replace(/^﻿/, '').split(/\r?\n/);

  // (d) forbidden text, and lines that are only PENDING.
  lines.forEach((l, i) => {
    if (l.includes(FORBIDDEN)) problems.push(`forbidden text "${FORBIDDEN}" (line ${i + 1})`);
    if (/^\s*\|?\s*PENDING\s*\|?\s*$/.test(l)) problems.push(`line ${i + 1} consists only of PENDING`);
  });

  // A main table has header "id" and a matching id in its first data row. Table 4.1 has header "id P8".
  const tables = { FP: [], MQ: [], GV: [], PF: [], V: [] };
  let cross = null;
  for (const t of parseTables(lines)) {
    if (t.header[0] === 'id P8') { cross = t; continue; }
    if (t.header[0] !== 'id' || t.rows.length === 0) continue;
    const kind = Object.keys(MAIN).find((k) => MAIN[k].id.test(t.rows[0].cells[0]));
    if (!kind) continue;
    const spec = MAIN[kind];
    tables[kind].push(t);
    if (t.section !== spec.section) problems.push(`${kind} table at line ${t.line} is in section ${t.section}, expected ${spec.section}`);
    if (spec.cols && t.header.length !== spec.cols) problems.push(`${kind} table at line ${t.line} has ${t.header.length} columns, expected ${spec.cols}`);
    for (const r of t.rows) {
      if (!spec.id.test(r.cells[0])) problems.push(`${kind} table row at line ${r.line} has no ${kind} id in its first cell`);
      else if (spec.cols && r.cells.length !== spec.cols) problems.push(`${r.cells[0]} has ${r.cells.length} columns, expected ${spec.cols} (line ${r.line})`);
    }
  }
  if (!cross) problems.push('table 4.1 (header "id P8") not found');
  for (const k of Object.keys(MAIN)) if (tables[k].length === 0) problems.push(`${k} table not found`);

  // (a) definitions: first cell of each main-table row, each id exactly once.
  const defs = new Map();
  for (const k of Object.keys(MAIN)) {
    for (const t of tables[k]) for (const r of t.rows) defs.set(r.cells[0], [...(defs.get(r.cells[0]) ?? []), r.line]);
  }
  for (const [id, where] of defs) {
    if (where.length > 1) problems.push(`${id} defined ${where.length} times (lines ${where.join(', ')})`);
  }

  // (a) and (c) citations. Range endpoints are exempt; V ids inside ranges are expanded and must exist.
  const cites = [];
  lines.forEach((l, i) => {
    const { singles, ranges } = citations(l);
    for (const id of singles) cites.push([id, i + 1]);
    for (const r of ranges) if (r.prefix === 'V') for (const id of r.ids) cites.push([id, i + 1]);
  });
  const reported = new Set();
  for (const [id, line] of cites) {
    if (defs.has(id) || GV_WHITELIST.has(id) || V_RESERVED.has(id) || reported.has(`${id}@${line}`)) continue;
    reported.add(`${id}@${line}`);
    problems.push(`${id} cited but not defined (line ${line})`);
  }

  // (e) and (f) column lookups in the GV and V tables.
  const col = (t, prefix) => (t ? t.header.findIndex((c) => norm(c).startsWith(norm(prefix))) : -1);
  const gv = tables.GV[0];
  const vt = tables.V[0];
  const nIdx = col(gv, 'nơi sinh');
  const gIdx = col(gv, 'tệp golden');
  const fIdx = col(vt, 'files');
  if (gv && (nIdx < 0 || gIdx < 0)) problems.push('GV table needs "nơi sinh" and "tệp golden" columns');
  if (vt && fIdx < 0) problems.push('V table needs a "files" column');

  const replayNames = (s = '') => new Set([...s.matchAll(REPLAY)].map((m) => m[0]));
  const vRows = new Map();
  for (const r of tables.V.flatMap((t) => t.rows)) vRows.set(r.cells[0], r);
  const gvRows = new Map();
  for (const r of tables.GV.flatMap((t) => t.rows)) gvRows.set(r.cells[0], r);
  const cover = replayNames(COVER_V.map((id) => vRows.get(id)?.cells[fIdx] ?? '').join(' '));

  if (nIdx >= 0 && gIdx >= 0) {
    for (const [id, r] of gvRows) {
      for (const name of replayNames(r.cells[nIdx])) {
        if (!cover.has(name)) problems.push(`replay class ${name} (${id}, "nơi sinh") is not in the files of V-10, V-11 or V-16 (line ${r.line})`);
      }
      if (/\bNFC\b|\bNFD\b/.test(r.cells[gIdx] ?? '')) problems.push(`${id} "tệp golden" describes NFC or NFD (line ${r.line})`);
    }
  }

  // Table 4.1 rows that say "chỉ P8": each GV id there needs a replay class in V-10, V-11 or V-16.
  for (const row of cross ? cross.rows : []) {
    const joined = row.cells.join(' | ');
    if (!/chỉ\s+P8/.test(joined) || nIdx < 0) continue;
    const { singles, ranges } = citations(joined);
    const gvIds = new Set([...singles, ...ranges.filter((r) => r.prefix === 'GV').flatMap((r) => r.ids)].filter((id) => id.startsWith('GV-')));
    for (const id of gvIds) {
      const gr = gvRows.get(id);
      if (!gr) continue; // a missing GV row is already reported by the citation check
      const names = [...replayNames(gr.cells[nIdx])];
      if (names.length === 0) problems.push(`${id} is "chỉ P8" but its "nơi sinh" names no ReplayTest class (line ${gr.line})`);
      else if (!names.some((n) => cover.has(n))) problems.push(`${id} is "chỉ P8" but ${names.join(', ')} is not in the files of V-10, V-11 or V-16 (line ${row.line})`);
    }
  }

  // (f) required rows.
  for (const id of REQUIRED_DEFS) if (!defs.has(id)) problems.push(`${id} required but not defined`);
  for (let n = 7; n <= 16; n++) {
    const id = `V-${String(n).padStart(2, '0')}`;
    if (!vRows.has(id)) problems.push(`${id} required (V-07..V-16) but has no row in section 9`);
  }

  // (b) FP owner cell: a task id (V ids from section 9 count, as P8 owns them), a pending token, or skipped.
  // ponytail: V ids accepted as owners because matrix line 646 says the V table uses the task format. Revisit if V stops being task-shaped.
  for (const t of tables.FP) {
    for (const r of t.rows) {
      const id = r.cells[0];
      for (const part of r.cells[r.cells.length - 1].split(';').map((s) => s.trim())) {
        if (part === 'skipped') continue;
        if (PENDING_OWNER.test(part) || GAP_OWNER.test(part)) {
          pending.push(`${id} owner ${part} (line ${r.line})`);
          if (strict) problems.push(`${id} owner ${part} is still pending; --strict rejects it (line ${r.line})`);
          continue;
        }
        if (TASK_ID.test(part) && (taskIds.has(part) || vRows.has(part))) continue;
        problems.push(`${id} owner "${part}" is not a task id, pending-owner, GAP or skipped (line ${r.line})`);
      }
    }
  }

  const counts = Object.fromEntries(Object.keys(MAIN).map((k) => [k, tables[k].reduce((n, t) => n + t.rows.length, 0)]));
  return { problems, pending, counts };
}

function planTaskTexts() {
  return readdirSync(PLAN_DIR)
    .filter((f) => /^p[1-7]-.+\.md$/.test(f))
    .sort()
    .map((f) => readFileSync(path.join(PLAN_DIR, f), 'utf8'));
}

// Raw GV-*.json files under the source roots (generated golden vectors, if any).
function rawGoldenFiles(roots) {
  const out = [];
  const walk = (dir) => {
    for (const e of readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) { if (e.name !== 'build' && e.name !== 'node_modules') walk(p); }
      else if (/^GV-.+\.json$/.test(e.name)) out.push(p);
    }
  };
  for (const r of roots) if (existsSync(r)) walk(r);
  return out;
}

function main(argv) {
  const args = argv.slice(2);
  let strict = false;
  let matrixPath = DEFAULT_MATRIX;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--strict') strict = true;
    else if (args[i] === '--matrix' && args[i + 1]) matrixPath = path.resolve(args[++i]);
    else { console.error('usage: node scripts/qa/p8-matrix-check.mjs [--strict] [--matrix <path>]'); return 2; }
  }
  let text;
  let taskIds;
  try {
    text = readFileSync(matrixPath, 'utf8');
    taskIds = collectTaskIds(planTaskTexts());
  } catch (e) {
    console.error(`cannot read inputs: ${e.message}`);
    return 2;
  }
  const { problems, pending, counts } = checkMatrix(text, { taskIds, strict });
  for (const f of rawGoldenFiles(RAW_ROOTS)) {
    problems.push(...checkRawVectors(path.relative(REPO, f), readFileSync(f, 'utf8')));
  }
  for (const p of pending) console.log(`pending: ${p}`);
  if (problems.length > 0) {
    for (const p of problems) console.error(p);
    console.error(`${problems.length} problem(s)`);
    return 1;
  }
  console.log(`P8 matrix OK: FP ${counts.FP}, MQ ${counts.MQ}, GV ${counts.GV}, PF ${counts.PF}, V ${counts.V}; pending ${pending.length}`);
  return 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exitCode = main(process.argv);
}
