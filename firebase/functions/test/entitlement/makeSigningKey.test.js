'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { generateSigningKey, secretJson } = require('../../scripts/make-signing-key');
const { parseSigningKey, verifyEntitlementToken } = require('../../src/entitlement/entitlementToken');

const SCRIPT = path.join(__dirname, '..', '..', 'scripts', 'make-signing-key.js');
const GOLDEN_FILE = path.join(__dirname, '..', '..', '..', '..', 'shared', 'src', 'commonTest', 'resources', 'golden', 'GV-063-entitlement-token.json');
const CASE_NAMES = ['valid_vip_monthly', 'valid_vip_lifetime', 'valid_not_vip', 'tampered_payload', 'tampered_signature', 'wrong_key_same_kid', 'expired_notAfter'];
const SIG_INVALID = ['tampered_payload', 'tampered_signature', 'wrong_key_same_kid'];

test('generate_returnsKidPemAndSpki', () => {
  const k = generateSigningKey();
  assert.deepEqual(Object.keys(k).sort(), ['kid', 'pem', 'spkiBase64']);
  assert.equal(typeof k.kid, 'string');
  assert.equal(typeof k.pem, 'string');
  assert.equal(typeof k.spkiBase64, 'string');
  assert.notEqual(generateSigningKey().pem, k.pem);
});

test('kid_isStableHashOfSpki', () => {
  const k = generateSigningKey();
  const want = crypto.createHash('sha256').update(Buffer.from(k.spkiBase64, 'base64')).digest().subarray(0, 8).toString('hex');
  assert.equal(k.kid, want);
  assert.match(k.kid, /^[0-9a-f]{16}$/);
});

test('pem_isP256Pkcs8', () => {
  const k = generateSigningKey();
  assert.ok(k.pem.startsWith('-----BEGIN PRIVATE KEY-----'));
  const key = crypto.createPrivateKey(k.pem);
  assert.equal(key.asymmetricKeyType, 'ec');
  assert.equal(key.asymmetricKeyDetails.namedCurve, 'prime256v1');
});

test('spkiBase64_matchesPem', () => {
  const k = generateSigningKey();
  const derived = crypto.createPublicKey(crypto.createPrivateKey(k.pem)).export({ type: 'spki', format: 'der' }).toString('base64');
  assert.equal(k.spkiBase64, derived);
});

test('output_isSecretJsonShape', () => {
  const k = generateSigningKey();
  const json = secretJson(k);
  assert.deepEqual(Object.keys(JSON.parse(json)).sort(), ['kid', 'pem']);
  assert.deepEqual(parseSigningKey(json), { kid: k.kid, pem: k.pem });
  // CLI: stdout chỉ là JSON bí mật (để pipe vào secrets:set); khoá công khai đi stderr, không có khoá riêng ở đó.
  const r = spawnSync(process.execPath, [SCRIPT], { encoding: 'utf8' });
  assert.equal(r.status, 0);
  const parsed = parseSigningKey(r.stdout.trim());
  assert.equal(r.stdout.trim().split('\n').length, 1);
  assert.ok(r.stderr.includes(parsed.kid));
  assert.ok(!r.stderr.includes('PRIVATE KEY'));
  assert.ok(!r.stderr.includes(parsed.pem.split('\n')[1]));
});

const golden = () => JSON.parse(fs.readFileSync(GOLDEN_FILE, 'utf8'));
const decode = (jws) => JSON.parse(Buffer.from(jws.split('.')[1], 'base64url').toString('utf8'));
const verifyCase = (g, c) => verifyEntitlementToken(c.jws, { [g.kid]: g.publicKeySpkiBase64 }, { nowMs: c.claims.iat, uid: c.claims.uid, nonce: c.claims.nonce });

test('golden_hasEveryNamedCaseWithSignatureValidFlag', () => {
  const g = golden();
  assert.ok(g.kid.startsWith('test-'));
  assert.equal(typeof g.nowMs, 'number');
  assert.deepEqual(g.cases.map((c) => c.name), CASE_NAMES);
  for (const c of g.cases) {
    assert.deepEqual(Object.keys(c).sort(), ['claims', 'jws', 'name', 'signatureValid']);
    assert.equal(c.signatureValid, !SIG_INVALID.includes(c.name), c.name);
  }
  assert.ok(!fs.readFileSync(GOLDEN_FILE, 'utf8').includes('PRIVATE KEY'));
});

test('goldenToken_verifiesWithGoldenPublicKey', () => {
  const g = golden();
  for (const c of g.cases.filter((x) => x.signatureValid)) {
    assert.deepEqual(verifyCase(g, c), c.claims, c.name);
  }
  const expired = g.cases.find((c) => c.name === 'expired_notAfter');
  assert.ok(expired.claims.notAfterMs <= g.nowMs);
  assert.throws(() => verifyEntitlementToken(expired.jws, { [g.kid]: g.publicKeySpkiBase64 }, { nowMs: g.nowMs, uid: expired.claims.uid, nonce: expired.claims.nonce }));
});

test('goldenToken_claimsMatchGolden', () => {
  const g = golden();
  for (const c of g.cases) assert.deepEqual(decode(c.jws), c.claims, c.name);
  const by = Object.fromEntries(g.cases.map((c) => [c.name, c.claims]));
  assert.equal(by.valid_vip_monthly.vip, true);
  assert.equal(by.valid_vip_monthly.planId, 'monthly');
  assert.equal(by.valid_vip_lifetime.planId, 'lifetime');
  assert.equal(by.valid_vip_lifetime.expiresAtMs, null);
  assert.equal(by.valid_not_vip.vip, false);
  assert.equal(by.valid_vip_monthly.iat, g.nowMs);
});

test('goldenTamperedToken_failsVerify', () => {
  const g = golden();
  for (const name of ['tampered_payload', 'tampered_signature']) {
    const c = g.cases.find((x) => x.name === name);
    assert.throws(() => verifyCase(g, c), { message: 'invalid entitlement token' }, name);
  }
  // bản hợp lệ + sửa payload ngay tại đây cũng phải đỏ
  const ok = g.cases.find((x) => x.name === 'valid_vip_monthly');
  const [h, p, s] = ok.jws.split('.');
  const forged = { ...decode(ok.jws), vip: false, planId: null, expiresAtMs: null, source: null, environment: null };
  const tampered = `${h}.${Buffer.from(JSON.stringify(forged)).toString('base64url')}.${s}`;
  assert.notEqual(p, tampered.split('.')[1]);
  assert.throws(() => verifyCase(g, { ...ok, jws: tampered }), { message: 'invalid entitlement token' });
});

test('goldenWrongKeyToken_failsVerify', () => {
  const g = golden();
  const c = g.cases.find((x) => x.name === 'wrong_key_same_kid');
  assert.equal(JSON.parse(Buffer.from(c.jws.split('.')[0], 'base64url')).kid, g.kid);
  assert.throws(() => verifyCase(g, c), { message: 'invalid entitlement token' });
});
