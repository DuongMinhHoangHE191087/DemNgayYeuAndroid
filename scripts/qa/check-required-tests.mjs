#!/usr/bin/env node
// Required-tests gate: every gate in docs/qa/required-gates.json names tests that must exist AND pass in the JUnit XML
// reports of its own runner group. Missing / skipped / failed required test => exit 1.
// Usage: node scripts/qa/check-required-tests.mjs --junit <group>=<dir> [--junit ...] [--junit-root <dir>]
//        [--gates <file>] [--only-group <group>] [--commit <sha>] [--strict]
// Gate: {id, runner, sourceGlob?, classGlob, tests[], minCases?}; other fields are ignored.
// Paths (reports, gates, sourceGlob) are relative to the current directory. Exit codes: 0 ok, 1 gate failed, 2 bad usage.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const GROUPS = ['android', 'backend', 'ios-shared', 'ios-packages', 'ios-ui'];

const esc = (s) => s.replace(/[.*+?^${}()|[\]\\\/]/g, '\\$&');

// classGlob: `*` is the only wildcard, every other character (braces, parens, dots...) is literal.
export const classRe = (glob) => new RegExp(`^${glob.split('*').map(esc).join('.*')}$`);

// sourceGlob: `**`, `*`, `?`; always matched against '/'-separated paths.
const fileRe = (glob) =>
  new RegExp(
    '^' +
      glob
        .replace(/\\/g, '/')
        .split(/(\*\*\/|\*\*|\*|\?)/)
        .map((p) => ({ '**/': '(?:.*/)?', '**': '.*', '*': '[^/]*', '?': '[^/]' })[p] ?? esc(p))
        .join('') +
      '$',
  );

// true when at least one file under `cwd` matches the glob (walk starts at the wildcard-free prefix).
export function sourceExists(glob, cwd) {
  const norm = glob.replace(/\\/g, '/');
  const segs = norm.split('/');
  const stat = segs.findIndex((s) => /[*?]/.test(s));
  if (stat === -1) return fs.existsSync(path.join(cwd, norm)) && fs.statSync(path.join(cwd, norm)).isFile();
  const re = fileRe(norm);
  const walk = (rel) => {
    const abs = path.join(cwd, rel);
    if (!fs.existsSync(abs)) return false;
    for (const e of fs.readdirSync(abs, { withFileTypes: true })) {
      if (e.name === 'node_modules' || e.name === '.git') continue;
      const r = rel ? `${rel}/${e.name}` : e.name;
      if (e.isDirectory() ? walk(r) : re.test(r)) return true;
    }
    return false;
  };
  return walk(segs.slice(0, stat).join('/'));
}

const decode = (s) =>
  s
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(+d))
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');

// JUnit XML -> [{cls, file, name, status: pass|skip|fail}]. ponytail: regex scan, not an XML parser; fine for the
// flat <testcase> shape every runner emits, upgrade to a real parser only if a runner nests testcases.
export function parseJunit(xml) {
  const text = xml.replace(/<!\[CDATA\[[\s\S]*?\]\]>/g, '').replace(/<!--[\s\S]*?-->/g, '');
  const cases = [];
  const re = /<testcase\b((?:[^>"']|"[^"]*"|'[^']*')*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
  for (const m of text.matchAll(re)) {
    const attrs = {};
    for (const a of m[1].matchAll(/([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g)) attrs[a[1]] = decode(a[2] ?? a[3]);
    const body = m[2] ?? '';
    cases.push({
      cls: attrs.classname ?? '',
      file: attrs.file ?? '',
      name: attrs.name ?? '',
      status: /<(failure|error)\b/.test(body) ? 'fail' : /<skipped\b/.test(body) ? 'skip' : 'pass',
    });
  }
  return cases;
}

function* xmlFiles(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) yield* xmlFiles(p);
    else if (e.name.toLowerCase().endsWith('.xml')) yield p;
  }
}

// Returns {fails: string[], passed: number} for one gate against the cases of its group.
export function evalGate(gate, cases) {
  const re = classRe(gate.classGlob);
  const mine = cases.filter((c) => re.test(c.cls) || re.test(c.file));
  const fails = [];
  if (gate.tests.length) {
    for (const name of gate.tests) {
      const m = mine.filter((c) => c.name === name);
      if (!m.length) fails.push(`missing: ${name}`);
      else if (m.some((c) => c.status === 'fail')) fails.push(`failed: ${name}`);
      else if (!m.some((c) => c.status === 'pass')) fails.push(`skipped: ${name}`);
    }
  } else if (!mine.length) {
    fails.push(`no case matches classGlob ${gate.classGlob}`);
  } else {
    for (const c of mine) if (c.status !== 'pass') fails.push(`${c.status === 'fail' ? 'failed' : 'skipped'}: ${c.cls}.${c.name}`);
  }
  const passed = mine.filter((c) => c.status === 'pass').length;
  if (passed < gate.minCases) fails.push(`only ${passed} passing cases, minCases is ${gate.minCases}`);
  return { fails, passed };
}

const usage = (msg) => {
  console.error(`check-required-tests: ${msg}`);
  process.exit(2);
};

function parseArgs(argv) {
  const o = { junit: [], junitRoot: null, gates: 'docs/qa/required-gates.json', onlyGroup: null, commit: null, strict: false };
  const val = (i) => (i + 1 < argv.length ? argv[i + 1] : usage(`${argv[i]} needs a value`));
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--strict') o.strict = true;
    else if (a === '--junit') {
      const v = val(i++), k = v.indexOf('=');
      if (k < 1) usage(`--junit expects <group>=<dir>, got ${v}`);
      if (!GROUPS.includes(v.slice(0, k))) usage(`unknown group ${v.slice(0, k)} (one of ${GROUPS.join(', ')})`);
      o.junit.push([v.slice(0, k), v.slice(k + 1)]);
    } else if (a === '--junit-root') o.junitRoot = val(i++);
    else if (a === '--gates') o.gates = val(i++);
    else if (a === '--only-group') {
      o.onlyGroup = val(i++);
      if (!GROUPS.includes(o.onlyGroup)) usage(`unknown group ${o.onlyGroup}`);
    } else if (a === '--commit') o.commit = val(i++);
    else usage(`unknown argument ${a}`);
  }
  return o;
}

function loadGates(file) {
  let raw;
  try {
    raw = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    return usage(`cannot read gates ${file}: ${e.message}`);
  }
  if (!Array.isArray(raw?.gates)) usage(`${file}: "gates" must be an array`);
  return raw.gates.map((g, i) => {
    const w = `${file}: gate #${i}${g?.id ? ` (${g.id})` : ''}`;
    if (typeof g?.id !== 'string' || !g.id) usage(`${w} needs id`);
    if (!GROUPS.includes(g.runner)) usage(`${w}: runner must be one of ${GROUPS.join(', ')}`);
    if (typeof g.classGlob !== 'string' || !g.classGlob) usage(`${w} needs classGlob`);
    if (g.tests != null && !Array.isArray(g.tests)) usage(`${w}: tests must be an array`);
    return { ...g, tests: g.tests ?? [], minCases: g.minCases ?? 0 };
  });
}

export function main(argv, cwd = process.cwd()) {
  const o = parseArgs(argv);
  const gates = loadGates(path.resolve(cwd, o.gates));
  const dirs = Object.fromEntries(GROUPS.map((g) => [g, []]));
  if (o.junitRoot) {
    const root = path.resolve(cwd, o.junitRoot);
    if (!fs.existsSync(root)) usage(`--junit-root ${o.junitRoot} does not exist`);
    for (const e of fs.readdirSync(root, { withFileTypes: true })) {
      const g = e.isDirectory() && e.name.match(/^junit-(.+)$/)?.[1];
      if (g && GROUPS.includes(g)) dirs[g].push(path.join(root, e.name));
    }
  }
  for (const [g, d] of o.junit) dirs[g].push(path.resolve(cwd, d));

  const loaded = {}; // group -> cases[] or null (no xml found)
  const cases = (g) => {
    if (g in loaded) return loaded[g];
    let n = 0;
    const all = [];
    for (const d of dirs[g].filter((x) => fs.existsSync(x) && fs.statSync(x).isDirectory()))
      for (const f of xmlFiles(d)) (n++, all.push(...parseJunit(fs.readFileSync(f, 'utf8'))));
    return (loaded[g] = n ? all : null);
  };

  const failed = [], soft = []; // soft = only fatal under --strict
  const used = new Set();
  let ran = 0;
  for (const g of gates) {
    if (o.onlyGroup && g.runner !== o.onlyGroup) continue;
    ran++;
    if (g.sourceGlob && !sourceExists(g.sourceGlob, cwd)) {
      soft.push(`PENDING ${g.id}: no file matches sourceGlob ${g.sourceGlob}`);
      continue;
    }
    const c = cases(g.runner);
    if (!c) {
      soft.push(`NO-REPORTS ${g.id}: group ${g.runner} has no JUnit xml`);
      continue;
    }
    used.add(g.runner);
    const r = evalGate(g, c);
    if (r.fails.length) failed.push(`FAIL ${g.id}: ${r.fails.join('; ')}`);
    else console.log(`PASS ${g.id} (${r.passed} passing cases)`);
  }

  if (o.commit) {
    for (const g of used)
      for (const d of dirs[g].filter((x) => fs.existsSync(x))) {
        const f = path.join(d, 'commit.txt');
        const got = fs.existsSync(f) ? fs.readFileSync(f, 'utf8').trim() : null;
        if (got !== o.commit) soft.push(`COMMIT ${g}: ${d} ${got === null ? 'has no commit.txt' : `is for ${got}`}, expected ${o.commit}`);
      }
  }

  for (const l of failed) console.error(l);
  for (const l of soft) console[o.strict ? 'error' : 'warn'](o.strict ? l : `warning: ${l}`);
  const bad = failed.length + (o.strict ? soft.length : 0);
  console.log(`${ran} gate(s), ${failed.length} failed, ${soft.length} pending/unverified${o.strict ? ' (strict)' : ''}`);
  return bad ? 1 : 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) process.exit(main(process.argv.slice(2)));
