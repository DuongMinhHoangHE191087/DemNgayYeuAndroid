#!/usr/bin/env node
// Release sign-off gate (DEC-04): checks docs/qa/release-signoff.json against the FP rows of the P8 parity matrix.
// Usage: node scripts/qa/check-release-signoff.mjs --stage external-testflight|app-store --build <n> --commit <sha>
//        node scripts/qa/check-release-signoff.mjs --stage list
//        node scripts/qa/check-release-signoff.mjs --require-evidence <file> [--build <n> --commit <sha>]
// Test seams: --root <dir> (evidence paths resolve here), --matrix, --signoff, --decisions.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { SHA40, verifyEvidence } from './lib/evidence.mjs';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const PLAN = 'docs/superpowers/plans/2026-10-13-kmp-ios';
const cells = (line) => line.replace(/^\s*\|/, '').replace(/\|\s*$/, '').split(/(?<!\\)\|/).map((c) => c.trim());
const nonEmpty = (v) => typeof v === 'string' ? v.trim() !== '' : v !== undefined && v !== null && v !== '';

// -> [{ id, priority }] for the `| FP-xxx |` rows, priority taken from the column headed "ưu tiên".
export function parseMatrix(text) {
  const lines = text.split(/\r?\n/);
  const header = lines.find((l) => /^\|\s*id\s*\|/.test(l) && l.includes('ưu tiên'));
  if (!header) throw new Error('matrix: không thấy bảng có cột "ưu tiên"');
  const col = cells(header).findIndex((c) => c === 'ưu tiên');
  return lines.filter((l) => /^\|\s*FP-\d+\s*\|/.test(l)).map((l) => {
    const c = cells(l);
    const priority = /^(P[012])\b/.exec(c[col] ?? '')?.[1];
    if (!priority) throw new Error(`matrix: ${c[0]} không có ưu tiên P0/P1/P2`);
    return { id: c[0], priority };
  });
}

// -> list of "<FP>: <problem>" strings (empty = gate passes).
export function checkSignoff({ stage, build, commit, matrix, entries, decisions, root }) {
  const errs = [];
  if (!nonEmpty(build)) errs.push('thiếu --build');
  if (!SHA40.test(commit ?? '')) errs.push('--commit phải là 40 ký tự hex');
  if (errs.length) return errs;
  const known = new Set(matrix.map((m) => m.id));
  for (const id of Object.keys(entries)) if (!known.has(id)) errs.push(`${id}: không có trong ma trận P8`);

  const decisionLine = (dec) => decisions.split(/\r?\n/).find((l) => new RegExp(`^\\|\\s*${dec}\\s*\\|`).test(l));
  const problems = (id, e) => {
    const bad = [];
    if (e.status === 'pass') {
      if (String(e.build) !== String(build)) bad.push(`build ${e.build} khác ${build}`);
      if (String(e.commit).toLowerCase() !== commit.toLowerCase()) bad.push('commit khác ứng viên nộp (bằng chứng cũ)');
      if (e.platform !== 'ios') bad.push('platform phải là ios');
      for (const k of ['device', 'date']) if (!nonEmpty(e[k])) bad.push(`thiếu ${k}`);
      if (!nonEmpty(e.evidence)) return [...bad, 'thiếu evidence'];
      const file = path.resolve(root, e.evidence);
      if (!fs.existsSync(file)) return [...bad, `file bằng chứng không tồn tại: ${e.evidence}`];
      return [...bad, ...verifyEvidence(fs.readFileSync(file, 'utf8'), { build, commit, device: e.device, date: e.date, fp: id })];
    }
    if (e.status === 'descoped') {
      if (!/^DEC-\d+$/.test(e.decision ?? '')) return ['descoped thiếu decision: DEC-nn'];
      const line = decisionLine(e.decision);
      if (!line) return [`${e.decision} không có trong DECISIONS.md`];
      return new RegExp(`\\b${id}\\b`).test(line) ? [] : [`${e.decision} không nhắc ${id}`];
    }
    return [`trạng thái "${e.status}" không được chấp nhận`];
  };

  for (const { id, priority } of matrix) {
    if (priority === 'P2') continue;
    const e = entries[id] ?? { status: 'pending' };
    const external = stage === 'external-testflight';
    if (e.status === 'pending') {
      if (!external || priority === 'P0') errs.push(`${id} (${priority}): còn pending`);
    } else if (e.status === 'waived') {
      if (!external) errs.push(`${id} (${priority}): waived không được chấp nhận ở ${stage}`);
      else if (priority !== 'P1') errs.push(`${id} (${priority}): chỉ P1 mới được waived`);
      else {
        if (!nonEmpty(e.reason)) errs.push(`${id}: waived thiếu reason`);
        if (!nonEmpty(e.owner)) errs.push(`${id}: waived thiếu owner`);
      }
    } else if (e.status === 'descoped' && external && priority === 'P0') {
      errs.push(`${id} (P0): external-testflight đòi pass`);
    } else {
      errs.push(...problems(id, e).map((p) => `${id} (${priority}): ${p}`));
    }
  }
  return errs;
}

function main(argv) {
  const opt = {};
  for (let i = 0; i < argv.length; i += 2) opt[argv[i].replace(/^--/, '')] = argv[i + 1];
  const root = path.resolve(opt.root ?? REPO);
  const fail = (lines, code = 1) => {
    console.error(lines.join('\n'));
    return code;
  };

  if (opt['require-evidence']) {
    const file = opt['require-evidence'];
    if (!fs.existsSync(file)) return fail([`không thấy file bằng chứng: ${file}`]);
    const errs = verifyEvidence(fs.readFileSync(file, 'utf8'), { build: opt.build, commit: opt.commit });
    return errs.length ? fail(errs.map((e) => `${file}: ${e}`)) : (console.log(`OK ${file}`), 0);
  }

  const read = (p) => fs.readFileSync(path.resolve(p), 'utf8');
  const matrix = parseMatrix(read(opt.matrix ?? path.join(root, PLAN, 'p8-feature-parity-matrix.md')));
  if (opt.stage === 'list') {
    for (const p of ['P0', 'P1', 'P2']) console.log(`${p}: ${matrix.filter((m) => m.priority === p).length}`);
    console.log(`total: ${matrix.length}`);
    return 0;
  }
  if (opt.stage !== 'external-testflight' && opt.stage !== 'app-store') {
    return fail(['usage: --stage external-testflight|app-store|list --build <n> --commit <sha> | --require-evidence <file>'], 2);
  }
  const signoffFile = opt.signoff ?? path.join(root, 'docs/qa/release-signoff.json');
  const entries = JSON.parse(read(signoffFile)).entries ?? {};
  const decisions = read(opt.decisions ?? path.join(root, PLAN, 'DECISIONS.md'));
  const errs = checkSignoff({ stage: opt.stage, build: opt.build, commit: opt.commit, matrix, entries, decisions, root });
  if (errs.length) return fail([`${opt.stage}: ${errs.length} vấn đề`, ...errs.map((e) => `  ${e}`)]);
  console.log(`OK ${opt.stage} build ${opt.build}`);
  return 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (e) {
    console.error(e.message);
    process.exitCode = 2;
  }
}
