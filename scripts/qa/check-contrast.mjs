#!/usr/bin/env node
// WCAG 2.x contrast gate for the theme colours. Pairs in docs/qa/contrast-pairs.json name constants
// from Color.kt. A pair under its threshold fails unless it is marked knownFailing; --strict makes
// knownFailing pairs fail too.
// Usage: node scripts/qa/check-contrast.mjs [--strict]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
// ponytail: fixed path; repoint to shared/ when P4 moves the colour constants there.
export const COLOR_FILE = path.join(REPO, 'app/src/main/java/com/example/ui/theme/Color.kt');
export const PAIRS_FILE = path.join(REPO, 'docs/qa/contrast-pairs.json');
// kind: text = 4.5; large (large text) and ui (UI components) = 3.
const THRESHOLD = { text: 4.5, large: 3, ui: 3 };

const linear = (c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);

function luminance(hex) {
  const h = hex.replace('#', '');
  const [r, g, b] = [0, 2, 4].map((i) => linear(parseInt(h.slice(i, i + 2), 16) / 255));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrastRatio(fgHex, bgHex) {
  const a = luminance(fgHex);
  const b = luminance(bgHex);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

// `val Name = Color(0xAARRGGBB)` -> { Name: '#RRGGBB' }.
// ponytail: regex scan, not a Kotlin parser. Alpha is dropped (palette colours are opaque); a
// multi-line Color(...) is missed and then reports as an unknown colour name, so it fails loudly.
export function loadColors(kotlinSrc) {
  const colors = {};
  for (const m of kotlinSrc.matchAll(/^val\s+(\w+)\s*=\s*Color\(0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})\)/gm)) {
    colors[m[1]] = `#${m[2]}`;
  }
  return colors;
}

export function checkPairs(pairs, colors, { strict = false } = {}) {
  const lines = [];
  let failures = 0;
  let known = 0;
  for (const p of pairs) {
    const label = `${p.fg}/${p.bg}`;
    const fg = colors[p.fg];
    const bg = colors[p.bg];
    const need = THRESHOLD[p.kind];
    if (!fg || !bg || need === undefined) {
      lines.push(`FAIL ${label}: unknown colour name or kind "${p.kind}"`);
      failures++;
      continue;
    }
    const ratio = contrastRatio(fg, bg);
    if (ratio >= need) continue;
    const detail = `${label} ${ratio.toFixed(2)} < ${need}`;
    if (p.knownFailing && !strict) {
      known++;
      lines.push(`KNOWN ${detail} (fixBy ${p.fixBy ?? '-'})`);
    } else {
      failures++;
      lines.push(`FAIL ${detail}`);
    }
  }
  return { failures, known, lines };
}

function main() {
  const strict = process.argv.includes('--strict');
  const colors = loadColors(fs.readFileSync(COLOR_FILE, 'utf8'));
  const { pairs } = JSON.parse(fs.readFileSync(PAIRS_FILE, 'utf8'));
  const { failures, known, lines } = checkPairs(pairs, colors, { strict });
  for (const line of lines) console.log(line);
  console.log(`checked ${pairs.length} pairs: ${failures} failing, ${known} known${strict ? ' (strict)' : ''}`);
  process.exitCode = failures > 0 ? 1 : 0;
}

// Entry point by file name, so importing this module (the test does) has no side effects.
// A path-equality guard can miss under symlinks or junctions and would then skip the gate silently.
if (/[\\/]check-contrast\.mjs$/.test(process.argv[1] ?? '')) main();
