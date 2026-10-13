// node _tools/ref-check.mjs [--all] -> dangling id references across plan files (read-only; run from anywhere)
// definition = first cell of a markdown table row that is an id (F-01, FP-001, DEC-10 ...)
// reference  = any mention of a known-prefix id anywhere in p*.md / DECISIONS.md / _addendum / _shared-context / 00-master
// exit code 0 always; read the DANGLING/DUPLICATE lines. GV-* temp names are counted, not listed (P8 section 4.1 reconciles them).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const dir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const files = fs.readdirSync(dir).filter((f) => /^(p\d.*|DECISIONS|_addendum.*|_shared-context|00-master)\.md$/.test(f)).sort();
const PRE = '(?:FP|MQ|GV|PF|DEC|F|D|B|U|N|M|Q|V)';
const ID = new RegExp(`(?<![A-Za-z0-9_-])${PRE}-\\d+[a-wyz]?(?![A-Za-z0-9_-])`, 'g');
const defs = new Map(); // id -> [file:line]
const refs = []; // {id,file,line,text}
for (const f of files) {
  const lines = fs.readFileSync(path.join(dir, f), 'utf8').replace(/^﻿/, '').split(/\r?\n/);
  lines.forEach((ln, i) => {
    const m = /^\|\s*(\*{0,2})(`?)((?:FP|MQ|GV|PF|DEC|F|D|B|U|N|M|Q|V)-\d+[a-z]?)\2\1\s*\|/.exec(ln);
    if (m) {
      const arr = defs.get(m[3]) || [];
      arr.push(`${f}:${i + 1}`);
      defs.set(m[3], arr);
    }
    let r;
    ID.lastIndex = 0;
    while ((r = ID.exec(ln))) refs.push({ id: r[0], file: f, line: i + 1, text: ln.trim().slice(0, 140) });
  });
}
const VACANT = /bỏ trống|không liên tục|không tái dùng|vacant/i;
const vacantIds = new Set(refs.filter((r) => VACANT.test(fs.readFileSync(path.join(dir, r.file), 'utf8').split(/\r?\n/)[r.line - 1])).map((r) => r.id));
const dangling = new Map(); // id -> refs
let gvTemp = 0;
for (const r of refs) {
  if (defs.has(r.id) || vacantIds.has(r.id)) continue;
  if (r.id.startsWith('GV-')) { gvTemp++; continue; } // GV namespaces are reconciled by P8 section 4.1 + master pass
  (dangling.get(r.id) || dangling.set(r.id, []).get(r.id)).push(r);
}
console.log(`vacant-by-design ids: ${[...vacantIds].filter((i) => !defs.has(i)).join(' ')}; GV refs not defined as first-cell rows (temp names, see P8 4.1): ${gvTemp}`);
// duplicates only count when both definitions sit in the same file within one contiguous table block
const dupes = [...defs].filter(([, v]) => {
  if (v.length < 2) return false;
  const byFile = {};
  for (const w of v) { const [f, l] = w.split(':'); (byFile[f] ||= []).push(+l); }
  return Object.entries(byFile).some(([f, ls]) => {
    if (ls.length < 2) return false;
    const L = fs.readFileSync(path.join(dir, f), 'utf8').split(/\r?\n/);
    ls.sort((a, b) => a - b);
    for (let k = 1; k < ls.length; k++) {
      let same = true;
      for (let n = ls[k - 1]; n <= ls[k]; n++) if (!L[n - 1].startsWith('|')) { same = false; break; }
      if (same) return true;
    }
    return false;
  });
});
console.log(`files=${files.length} defs=${defs.size} refs=${refs.length} dangling-ids=${dangling.size} duplicate-defs=${dupes.length}`);
const byPre = {};
for (const id of defs.keys()) { const p = id.split('-')[0]; byPre[p] = (byPre[p] || 0) + 1; }
console.log('defs by prefix:', JSON.stringify(byPre));
const showAll = process.argv.includes('--all');
const rows = [...dangling].sort((a, b) => a[0].localeCompare(b[0], 'en', { numeric: true }));
for (const [id, rs] of rows) {
  const where = rs.slice(0, showAll ? 99 : 3).map((r) => `${r.file.replace(/\.md$/, '')}:${r.line}`).join(', ');
  console.log(`DANGLING ${id} x${rs.length} · ${where}${!showAll && rs.length > 3 ? ' …' : ''}`);
}
for (const [id, v] of dupes) console.log(`DUPLICATE ${id} · ${v.join(', ')}`);
