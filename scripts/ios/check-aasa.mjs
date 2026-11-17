// node scripts/ios/check-aasa.mjs --team-id <10 chars> --out <file> [--release]
// Fills <TeamID> in apple-app-site-association.template.json. Without --team-id the
// placeholder is kept (dev); with --release a real Team ID is required.
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const TEMPLATE = path.join(path.dirname(fileURLToPath(import.meta.url)), 'apple-app-site-association.template.json');
const PLACEHOLDER = '<TeamID>';

function parse(argv) {
  const opts = { release: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--release') opts.release = true;
    else if (a === '--team-id' || a === '--out') {
      const v = argv[++i];
      if (v === undefined || v.startsWith('--')) throw new Error(`${a} needs a value`);
      opts[a === '--team-id' ? 'teamId' : 'out'] = v;
    } else throw new Error(`unknown argument: ${a}`);
  }
  return opts;
}

function run(argv) {
  const { teamId, out, release } = parse(argv);
  if (teamId === undefined && release) throw new Error('release build needs a Team ID: pass --team-id <10 chars>');
  if (teamId !== undefined && !/^[A-Z0-9]{10}$/.test(teamId)) {
    throw new Error(`Team ID must be 10 chars of A-Z or 0-9, got "${teamId}"`);
  }
  const text = fs.readFileSync(TEMPLATE, 'utf8').replaceAll(PLACEHOLDER, teamId ?? PLACEHOLDER);
  const json = JSON.stringify(JSON.parse(text), null, 2) + '\n';
  if (out === undefined) process.stdout.write(json);
  else fs.writeFileSync(out, json, 'utf8');
}

try {
  run(process.argv.slice(2));
} catch (err) {
  process.stderr.write(`check-aasa: ${err.message}\n`);
  process.exitCode = 1;
}
