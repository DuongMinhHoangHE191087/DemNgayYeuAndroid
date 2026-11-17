// Evidence files (docs/qa/runs/*.md): `key: value` lines. Shared by the release sign-off checks.
export const SHA40 = /^[0-9a-f]{40}$/i;

const KEYS = ['RESULT', 'build', 'commit', 'platform', 'device', 'date', 'covers'];

// -> { results: [..], build, commit, platform, device, date, covers: [ids] }; first line wins, except RESULT (all lines kept).
export function parseEvidence(text) {
  const out = { results: [], covers: [] };
  for (const line of text.split(/\r?\n/)) {
    const m = /^([A-Za-z]+):[ \t]*(.*?)[ \t]*$/.exec(line);
    if (!m || !KEYS.includes(m[1])) continue;
    if (m[1] === 'RESULT') out.results.push(m[2]);
    else if (m[1] === 'covers') out.covers.push(...m[2].split(/[,;\s]+/).filter(Boolean));
    else if (out[m[1]] === undefined) out[m[1]] = m[2];
  }
  return out;
}

// -> list of problems (empty = valid). `expect` may hold build, commit, device, date and fp (id that `covers:` must list);
// build/commit/device/date are compared only when given, but platform must always be ios and device/date non-empty.
export function verifyEvidence(text, expect = {}) {
  const e = parseEvidence(text);
  const errs = [];
  if (!e.results.length || e.results.some((r) => r !== 'PASS')) errs.push('thiếu hoặc sai dòng "RESULT: PASS"');
  for (const k of ['build', 'commit', 'platform', 'device', 'date']) if (!e[k]) errs.push(`thiếu dòng "${k}:"`);
  if (e.platform && e.platform !== 'ios') errs.push(`platform là "${e.platform}", phải là ios`);
  if (e.commit && !SHA40.test(e.commit)) errs.push('commit phải là 40 ký tự hex');
  if (expect.build !== undefined && e.build && e.build !== String(expect.build)) errs.push(`build ${e.build} khác ${expect.build}`);
  if (expect.commit !== undefined && e.commit && e.commit.toLowerCase() !== String(expect.commit).toLowerCase()) {
    errs.push(`commit ${e.commit} khác ${expect.commit}`);
  }
  for (const k of ['device', 'date']) if (expect[k] !== undefined && e[k] && e[k] !== String(expect[k])) errs.push(`${k} khác file sign-off`);
  if (expect.fp && !e.covers.includes(expect.fp)) errs.push(`covers không liệt kê ${expect.fp}`);
  return errs;
}
