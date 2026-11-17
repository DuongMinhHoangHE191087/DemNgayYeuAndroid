// Q-25: tag + existing build numbers -> {marketingVersion,build,prerelease,channel}. Pure, no network.
// build = max(all --builds, floor) + 1 (one counter for the whole bundle). Exit 2 on bad input.
import { readFileSync } from 'node:fs';

const die = (msg) => { console.error(`version.mjs: ${msg}`); process.exit(2); };
const POS_INT = /^[1-9]\d*$/;

const opts = {};
const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i += 2) {
  const k = argv[i];
  if (!['--tag', '--builds', '--floor'].includes(k) || i + 1 >= argv.length) die(`bad argument ${k}`);
  opts[k.slice(2)] = argv[i + 1];
}

const m = /^v(\d+\.\d+\.\d+)(?:-(rc|beta)\.\d+)?$/.exec(opts.tag ?? '');
if (!m) die(`invalid tag ${JSON.stringify(opts.tag)}`);
const [, marketingVersion, suffix] = m;
const channel = { rc: 'testflight-internal', beta: 'testflight-external' }[suffix] ?? 'app-store';

const nums = [];
if (opts.builds) {
  for (const s of opts.builds.split(',')) {
    if (!POS_INT.test(s) || !Number.isSafeInteger(+s)) die(`invalid build number ${JSON.stringify(s)}`);
    nums.push(+s);
  }
}
if (opts.floor !== undefined) {
  let floor;
  try { floor = JSON.parse(readFileSync(opts.floor, 'utf8')).floor; } catch (e) { die(`cannot read floor: ${e.message}`); }
  if (!Number.isSafeInteger(floor) || floor < 0) die('floor must be a non-negative integer');
  nums.push(floor);
}

console.log(JSON.stringify({
  marketingVersion,
  build: Math.max(0, ...nums) + 1,
  prerelease: suffix !== undefined,
  channel,
}));
