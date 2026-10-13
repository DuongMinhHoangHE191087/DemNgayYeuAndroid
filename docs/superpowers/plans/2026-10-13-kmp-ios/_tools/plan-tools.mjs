// usage: node plan-tools.mjs status | lint | tasks | stats | log
//   stats   per-phase days (mac/win split), critical path, most contended files
//   log     merge reviews/2026-10-13-kmp-ios/log-fragments/*.md rows into the main review log + accept/reject totals
//   status  per-part size, task count, leftover placeholders
//   lint    validate every task table in p*.md (exit 1 on errors)
//   tasks   write TASKS.md (tick-able coding checklist grouped by wave, keeps existing ticks)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const repo = path.join(root, '..', '..', '..', '..');
const repoDirs = new Set(fs.readdirSync(repo, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name));
const parts = fs.readdirSync(root).filter((f) => /^p\d-.*\.md$/.test(f)).sort();
const clean = (s) => s.replace(/[`*]/g, '').replace(/<br\s*\/?>/gi, ' ').trim();
const cells = (line) =>
  line.replace(/\\\|/g, '\u0001').replace(/^\s*\|/, '').replace(/\|\s*$/, '').split('|').map((c) => c.replace(/\u0001/g, '|').trim());
const COLS = { id: /^id$/, phase: /^phase/, files: /^files/, change: /^change/, test: /^test/, deps: /^depend/, model: /^model/, host: /^host/, size: /^size/ };

function parse() {
  const tasks = [], errors = [];
  for (const f of parts) {
    const lines = fs.readFileSync(path.join(root, f), 'utf8').split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].trim().startsWith('|')) continue;
      const head = cells(lines[i]).map((c) => clean(c).toLowerCase());
      if (head[0] !== 'id' || !head.some((h) => /^depend/.test(h)) || !head.some((h) => /^size/.test(h))) continue;
      const idx = {};
      for (const [k, re] of Object.entries(COLS)) idx[k] = head.findIndex((h) => re.test(h));
      const miss = Object.entries(idx).filter(([, v]) => v < 0).map(([k]) => k);
      if (miss.length) errors.push(`${f}:${i + 1} task table lacks column(s): ${miss.join(', ')}`);
      for (i += 2; i < lines.length && lines[i].trim().startsWith('|'); i++) {
        const c = cells(lines[i]);
        if (c.length < head.length) { errors.push(`${f}:${i + 1} row has ${c.length} cells, expected ${head.length}`); continue; }
        const g = (k) => (idx[k] >= 0 ? c[idx[k]] : '');
        const dep = clean(g('deps'));
        tasks.push({
          part: f.slice(0, 2), file: f, line: i + 1, id: clean(g('id')), phase: clean(g('phase')), files: g('files'), change: g('change'), test: g('test'),
          deps: /^(—|-|–|none|n\/a|)$/i.test(dep) ? [] : dep.split(/[,;\s]+/).filter((t) => /^[A-Z]{1,2}-\d+[a-z]?$/.test(t)),
          depRaw: dep, model: clean(g('model')).toLowerCase(), host: clean(g('host')).toLowerCase(), size: clean(g('size')).split(/[\s(]/)[0].toUpperCase(),
        });
      }
      i--;
    }
  }
  return { tasks, errors };
}

function analyse(tasks, errors) {
  const byId = new Map();
  for (const t of tasks) {
    if (!/^[A-Z]{1,2}-\d+[a-z]?$/.test(t.id)) errors.push(`${t.file}:${t.line} bad id "${t.id}"`);
    if (byId.has(t.id)) errors.push(`${t.file}:${t.line} duplicate id ${t.id} (also ${byId.get(t.id).file}:${byId.get(t.id).line})`);
    byId.set(t.id, t);
    if (!['S', 'M'].includes(t.size)) errors.push(`${t.id} size "${t.size}" must be S or M (split L)`);
    if (!['haiku', 'sonnet'].includes(t.model)) errors.push(`${t.id} model "${t.model}" must be haiku or sonnet`);
    if (!['win', 'mac'].includes(t.host)) errors.push(`${t.id} host "${t.host}" must be win or mac`);
    if (!/^P[0-6]$/.test(t.phase)) errors.push(`${t.id} phase "${t.phase}" must be P0..P6`);
    if (!t.change || /^(—|-|tbd|todo)$/i.test(t.change)) errors.push(`${t.id} empty change`);
    if (!t.test || /^(—|-|n\/a|tbd|todo)$/i.test(clean(t.test))) errors.push(`${t.id} has no test/command`);
    if (/\b(see (the )?(previous|above)|similar to|xem task|như trên)\b/i.test(t.change)) errors.push(`${t.id} change is not self-contained ("see previous"/"similar to")`);
  }
  for (const t of tasks) for (const d of t.deps) if (!byId.has(d)) errors.push(`${t.id} depends on unknown ${d}`);
  // levels (waves) + cycle detection
  const level = new Map(), state = new Map();
  const visit = (id, stack) => {
    if (state.get(id) === 2) return level.get(id);
    if (state.get(id) === 1) { errors.push(`dependency cycle: ${[...stack, id].join(' -> ')}`); return 0; }
    state.set(id, 1);
    const t = byId.get(id);
    let lv = 0;
    for (const d of t.deps) if (byId.has(d)) {
      lv = Math.max(lv, visit(d, [...stack, id]) + 1);
      if (byId.get(d).phase > t.phase) errors.push(`${id} (${t.phase}) depends on later phase ${d} (${byId.get(d).phase})`);
    }
    state.set(id, 2); level.set(id, lv);
    return lv;
  };
  for (const t of tasks) visit(t.id, []);
  // file ownership: tasks owning the same file must be ordered (reachable) or all carry shared-file:
  const reach = new Map();
  const ancestors = (id) => {
    if (reach.has(id)) return reach.get(id);
    const s = new Set(); reach.set(id, s);
    for (const d of byId.get(id).deps) if (byId.has(d)) { s.add(d); for (const a of ancestors(d)) s.add(a); }
    return s;
  };
  const owners = new Map(), warns = [];
  for (const t of tasks) {
    const shared = /shared-file:/i.test(t.files);
    for (const raw of t.files.replace(/shared-file:/gi, ' ').split(/[,;]|<br\s*\/?>/i)) {
      const p = raw.replace(/`/g, '').trim().replace(/\s*\(.*\)$/, '').replace(/\\/g, '/');
      if (!p || /^(—|-|new|none)$/i.test(p)) continue;
      if (!owners.has(p)) owners.set(p, []);
      owners.get(p).push({ id: t.id, shared });
      // warn only: path under an existing top-level dir that is missing and not marked new (hallucinated path?)
      if (p.includes('/') && !p.endsWith('/') && !/[*{}<>]/.test(p) && repoDirs.has(p.split('/')[0]) && !/\b(new|create|tạo|mới)\b/i.test(raw) && !fs.existsSync(path.join(repo, p)))
        warns.push(`${t.id} path not in repo: ${p} (write "(new)" after it if this task creates it)`);
    }
  }
  for (const [p, os] of owners) for (let a = 0; a < os.length; a++) for (let b = a + 1; b < os.length; b++) {
    const x = os[a], y = os[b];
    if (x.shared && y.shared) continue;
    if (!byId.has(x.id) || !byId.has(y.id)) continue;
    if (!ancestors(x.id).has(y.id) && !ancestors(y.id).has(x.id)) errors.push(`file ${p} owned by ${x.id} and ${y.id} with no ordering (add depends-on or shared-file:)`);
  }
  // waves = DAG levels, pushed later while another task already holds one of the same files (`shared-file:` pairs that lint leaves unordered must still never run in the same wave)
  const wave = new Map(), lastWave = new Map();
  const fileKeys = (t) => t.files.replace(/shared-file:/gi, ' ').split(/[,;]|<br\s*\/?>/i).map((raw) => raw.replace(/`/g, '').trim().replace(/\s*\(.*\)$/, '').replace(/\\/g, '/')).filter((p) => p && !/^(—|-|new|none)$/i.test(p));
  let pushed = 0;
  for (const t of [...tasks].sort((a, b) => (level.get(a.id) ?? 0) - (level.get(b.id) ?? 0))) {
    let w = 0;
    for (const d of t.deps) if (wave.has(d)) w = Math.max(w, wave.get(d) + 1);
    const byDeps = w;
    for (const p of fileKeys(t)) if (lastWave.has(p)) w = Math.max(w, lastWave.get(p) + 1);
    if (w > byDeps) pushed++;
    wave.set(t.id, w);
    for (const p of fileKeys(t)) lastWave.set(p, w);
  }
  return { byId, level, wave, pushed, warns };
}

const cmd = process.argv[2];
if (cmd === 'status') {
  for (const f of parts) {
    const s = fs.readFileSync(path.join(root, f), 'utf8');
    const left = (s.match(/^\s*(TODO[-(]?P?\d*\)?|PENDING)\s*$/gim) || []).length + (/^>\s*(Trạng thái|TRẠNG THÁI).*(ĐANG LẬP|KHUNG)/im.test(s) ? 1 : 0);
    console.log(`${f.padEnd(36)} ${String(s.split('\n').length).padStart(5)} lines ${String(Math.round(s.length / 1024)).padStart(4)} KB  tasks=${String(parse().tasks.filter((t) => t.file === f).length).padStart(3)}  placeholders=${left}`);
  }
} else if (cmd === 'lint' || cmd === 'tasks') {
  const { tasks, errors } = parse();
  const { byId, level, wave, pushed, warns } = analyse(tasks, errors);
  const days = tasks.reduce((n, t) => n + (t.size === 'S' ? 0.5 : t.size === 'M' ? 2 : 0), 0);
  const count = (k) => Object.entries(tasks.reduce((m, t) => ((m[t[k]] = (m[t[k]] || 0) + 1), m), {})).map(([a, b]) => `${a}=${b}`).join(' ');
  console.log(`tasks=${tasks.length} waves=${Math.max(-1, ...wave.values()) + 1} (dag-levels=${Math.max(-1, ...level.values()) + 1}, ${pushed} task pushed to a later wave by shared files) upper-bound-days=${days}\n  by part: ${count('part')}\n  by host: ${count('host')}\n  by size: ${count('size')}\n  by model: ${count('model')}`);
  if (cmd === 'lint') {
    errors.forEach((e) => console.log('ERROR ' + e));
    warns.slice(0, 40).forEach((w) => console.log('WARN ' + w));
    if (warns.length > 40) console.log(`WARN ... and ${warns.length - 40} more`);
    console.log(errors.length ? `FAIL ${errors.length} error(s), ${warns.length} warning(s)` : `OK (${warns.length} warning(s))`);
    process.exit(errors.length ? 1 : 0);
  }
  if (errors.length) { errors.forEach((e) => console.log('ERROR ' + e)); console.log('Fix lint errors first.'); process.exit(1); }
  const out = path.join(root, 'TASKS.md');
  const ticked = new Set(fs.existsSync(out) ? [...fs.readFileSync(out, 'utf8').matchAll(/^- \[x\] \*\*([A-Z]{1,2}-\d+[a-z]?)\*\*/gm)].map((m) => m[1]) : []);
  const waves = new Map();
  for (const t of tasks) { const l = wave.get(t.id); if (!waves.has(l)) waves.set(l, []); waves.get(l).push(t); }
  let md = `# TASKS — danh sách code theo thứ tự (sinh bởi _tools/plan-tools.mjs tasks; đừng sửa tay trừ dấu tick)\n\nLàm theo wave tăng dần; task trong cùng wave độc lập và không chung file nên chạy song song được. Tick \`[x]\` sau khi diff đã kiểm và lệnh nghiệm thu xanh. Chi tiết đầy đủ ở bảng task trong file phần (\`part\`).\n`;
  for (const l of [...waves.keys()].sort((a, b) => a - b)) {
    md += `\n## Wave ${l} (${waves.get(l).length} task)\n`;
    for (const t of waves.get(l).sort((a, b) => a.id.localeCompare(b.id, 'en', { numeric: true }))) {
      const what = clean(t.change).replace(/\s+/g, ' ');
      md += `- [${ticked.has(t.id) ? 'x' : ' '}] **${t.id}** (${t.phase} · ${t.host} · ${t.size} · ${t.model}) ${what.length > 160 ? what.slice(0, 157) + '...' : what} — test: ${clean(t.test).replace(/\s+/g, ' ').slice(0, 120)}${t.deps.length ? ` — deps: ${t.deps.join(', ')}` : ''} — ${t.file}\n`;
    }
  }
  fs.writeFileSync(out, md);
  console.log(`wrote TASKS.md (${tasks.length} tasks, ${ticked.size} already ticked)`);
} else if (cmd === 'stats') {
  // deterministic numbers for 00-master.md and the Gate message: per-phase days, mac/win split, critical path, shared files
  const { tasks, errors } = parse();
  const { byId } = analyse(tasks, errors);
  if (errors.length) console.log(`(${errors.length} lint error(s) - numbers may be off; run lint)`);
  const w = (t) => (t.size === 'S' ? 0.5 : t.size === 'M' ? 2 : 0);
  const ph = {};
  for (const t of tasks) { const r = (ph[t.phase] ||= { n: 0, d: 0, mac: 0, win: 0 }); r.n++; r.d += w(t); r[t.host === 'mac' ? 'mac' : 'win'] += w(t); }
  console.log('phase  tasks  days(sum)  mac-days  win-days');
  for (const p of Object.keys(ph).sort()) console.log(`${p.padEnd(6)} ${String(ph[p].n).padStart(5)} ${String(ph[p].d).padStart(10)} ${String(ph[p].mac).padStart(9)} ${String(ph[p].win).padStart(9)}`);
  const memo = new Map();
  const longest = (id) => {
    if (memo.has(id)) return memo.get(id);
    memo.set(id, { d: 0, path: [] }); // cycle guard
    let b = { d: 0, path: [] };
    for (const x of byId.get(id).deps) if (byId.has(x)) { const c = longest(x); if (c.d > b.d) b = c; }
    const r = { d: b.d + w(byId.get(id)), path: [...b.path, id] };
    memo.set(id, r);
    return r;
  };
  let top = { d: 0, path: [] };
  for (const id of byId.keys()) { const c = longest(id); if (c.d > top.d) top = c; }
  console.log(`critical path: ${top.d} days over ${top.path.length} tasks: ${top.path.join(' > ')}`);
  const own = new Map();
  for (const t of tasks) for (const raw of t.files.replace(/shared-file:/gi, ' ').split(/[,;]|<br\s*\/?>/i)) {
    const p = raw.replace(/`/g, '').trim().replace(/\s*\(.*\)$/, '').replace(/\\/g, '/');
    if (p && !/^(—|-|new|none)$/i.test(p)) own.set(p, [...(own.get(p) || []), t.id]);
  }
  const hot = [...own].filter(([, v]) => v.length > 2).sort((a, b) => b[1].length - a[1].length).slice(0, 12);
  console.log('most contended files (>2 owners): ' + (hot.map(([p, v]) => `${p} x${v.length}`).join('; ') || 'none'));
} else if (cmd === 'log') {
  // merge per-part triage fragments into the shared review log (agents never edit the main log: avoids write races)
  const rev = path.join(repo, 'docs', 'superpowers', 'reviews');
  const logFile = path.join(rev, '2026-10-13-kmp-ios-log.md');
  const fragDir = path.join(rev, '2026-10-13-kmp-ios', 'log-fragments');
  const read = (f) => fs.readFileSync(f, 'utf8').replace(/^﻿/, ''); // PowerShell 5.1 writes a BOM
  const lines = read(logFile).split(/\r?\n/);
  const hi = lines.findIndex((l) => /^\|\s*round\s*\|/i.test(l));
  if (hi < 0) { console.log('log header row "| round |" not found'); process.exit(1); }
  const rows = [];
  if (fs.existsSync(fragDir)) for (const f of fs.readdirSync(fragDir).filter((x) => x.endsWith('.md')).sort())
    for (const l of read(path.join(fragDir, f)).split(/\r?\n/)) if (/^\|\s*\d+x?\s*\|/.test(l)) rows.push({ l, c: cells(l) }); // round "1x" = CROSS-PART routing rows (x1-*.md), totalled apart from Codex triage
  const stat = {};
  for (const r of rows) {
    const key = `r${r.c[0]} ${(/\[(p\d|master)\]/i.exec(r.c[2] || '') || [, '?'])[1].toLowerCase()}`;
    const v = /^(accept|partial|reject)/i.exec((r.c[3] || '').replace(/[*`_]/g, ''));
    const sev = (/\b(BLOCKER|MAJOR|MINOR)\b/.exec(r.c[2] || '') || [, ''])[1];
    const s = (stat[key] ||= { n: 0, accept: 0, partial: 0, reject: 0, unknown: 0, BLOCKER: 0, MAJOR: 0, MINOR: 0 });
    s.n++; s[v ? v[1].toLowerCase() : 'unknown']++; if (sev) s[sev]++;
  }
  let md = lines.slice(0, hi + 2).join('\n') + '\n' + rows.map((r) => r.l).join('\n') + '\n\n## Tổng kết (sinh tự động bởi `plan-tools.mjs log`)\n\n| vòng/phần | findings | accept | partial | reject | chưa rõ | BLOCKER | MAJOR | MINOR |\n|---|---|---|---|---|---|---|---|---|\n';
  for (const k of Object.keys(stat).sort()) { const s = stat[k]; md += `| ${k} | ${s.n} | ${s.accept} | ${s.partial} | ${s.reject} | ${s.unknown} | ${s.BLOCKER} | ${s.MAJOR} | ${s.MINOR} |\n`; }
  fs.writeFileSync(logFile, md);
  console.log(`wrote ${path.relative(repo, logFile)}: ${rows.length} rows, ${Object.keys(stat).length} round/part groups`);
} else console.log('usage: node plan-tools.mjs status | lint | tasks | stats | log');
