'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { buildClaims, signEntitlementToken, parseSigningKey, verifyEntitlementToken } = require('../../src/entitlement/entitlementToken');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');

const HOUR = 3600000;
const NOW = 1_800_000_000_000;
const kp = () => crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
const pemOf = (k) => k.privateKey.export({ type: 'pkcs8', format: 'pem' });
const spkiOf = (k) => k.publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
const KEY = kp();
const KID = 'k1';
const KEYS = { [KID]: spkiOf(KEY) };
const b64u = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');

const vipEnt = (o = {}) => ({ vip: true, planId: 'vip_month', state: 'active', expiresAtMs: NOW + 30 * 24 * HOUR, source: 'google', environment: 'Production', verifiedAtMs: NOW - HOUR, ...o });
const noVipEnt = { vip: false, planId: null, state: 'none', expiresAtMs: null, source: null, environment: null, verifiedAtMs: null };
const build = (o = {}) => buildClaims({ uid: 'u1', nonce: 'n'.repeat(16), entitlement: vipEnt(), verifiedAtMs: NOW - HOUR, nowMs: NOW, policy: DEFAULT_POLICY, ...o });
const sign = (claims = build(), key = KEY, kid = KID) => signEntitlementToken(claims, { kid, pem: pemOf(key) });
const ctx = (o = {}) => ({ nowMs: NOW + 1000, uid: 'u1', nonce: 'n'.repeat(16), ...o });
const rejects = (token, keys = KEYS, c = ctx()) => assert.throws(() => verifyEntitlementToken(token, keys, c), { message: 'invalid entitlement token' });
// Dựng token thủ công (header/payload tuỳ ý) ký đúng bằng khoá thật: để thử riêng từng kiểm tra.
function forge(header, payload, key = KEY) {
  const data = `${b64u(header)}.${b64u(payload)}`;
  const sig = crypto.sign('sha256', Buffer.from(data), { key: key.privateKey, dsaEncoding: 'ieee-p1363' });
  return `${data}.${sig.toString('base64url')}`;
}

test('claims_haveAllFieldsAndV1', () => {
  assert.deepEqual(build(), {
    v: 1, uid: 'u1', vip: true, planId: 'vip_month', expiresAtMs: NOW + 30 * 24 * HOUR, source: 'google',
    environment: 'Production', iat: NOW, notAfterMs: NOW + 72 * HOUR, nonce: 'n'.repeat(16),
  });
});
test('nonce_echoed', () => assert.equal(build({ nonce: 'abc_DEF-0123456789' }).nonce, 'abc_DEF-0123456789'));
test('uid_bound', () => assert.equal(build({ uid: 'other' }).uid, 'other'));
test('notAfter_isMinOfExpiryTtlAndStale', () => {
  assert.equal(build({ entitlement: vipEnt({ expiresAtMs: NOW + HOUR }) }).notAfterMs, NOW + HOUR);
  assert.equal(build({ verifiedAtMs: NOW - 90 * HOUR }).notAfterMs, NOW - 90 * HOUR + DEFAULT_POLICY.maxStaleMs);
  assert.equal(build().notAfterMs, NOW + DEFAULT_POLICY.tokenTtlMs);
});
test('lifetime_cappedByTtl', () => assert.equal(build({ entitlement: vipEnt({ expiresAtMs: null }) }).notAfterMs, NOW + 72 * HOUR));
test('lifetime_cappedByStaleWindow', () => {
  const c = build({ entitlement: vipEnt({ expiresAtMs: null }), verifiedAtMs: NOW - 90 * HOUR });
  assert.equal(c.notAfterMs, NOW + 6 * HOUR);
});
test('adminGrantNoExpiry_cappedByTtl', () => {
  const c = build({ entitlement: vipEnt({ expiresAtMs: null, source: 'admin', planId: 'admin', verifiedAtMs: null }), verifiedAtMs: null });
  assert.equal(c.notAfterMs, NOW + 72 * HOUR);
});
test('notVip_notAfterIsNowPlusTtl', () => {
  const c = build({ entitlement: noVipEnt, verifiedAtMs: null });
  assert.deepEqual([c.vip, c.planId, c.expiresAtMs, c.source, c.environment, c.notAfterMs], [false, null, null, null, null, NOW + 72 * HOUR]);
});
test('notVip_ignoresStaleVerifiedAt', () => assert.equal(build({ entitlement: noVipEnt, verifiedAtMs: NOW - 500 * HOUR }).notAfterMs, NOW + 72 * HOUR));
test('vip_notAfterAfterIat', () => { const c = build(); assert.ok(c.notAfterMs > c.iat); });
test('vip_staleBeyondWindow_notAfterNotAfterIat', () => {
  const c = build({ verifiedAtMs: NOW - 200 * HOUR });
  assert.ok(c.notAfterMs <= c.iat); // đã quá hạn ngay khi ký: client xử là không VIP
});
test('buildClaims_badInputThrows', () => {
  for (const o of [{ uid: '' }, { uid: 5 }, { nonce: undefined }, { nonce: '' }, { nowMs: NaN }, { entitlement: null }]) {
    assert.throws(() => build(o), TypeError, JSON.stringify(o));
  }
});

test('header_hasEs256AndKid', () => {
  const h = JSON.parse(Buffer.from(sign().split('.')[0], 'base64url').toString());
  assert.deepEqual(h, { alg: 'ES256', kid: KID });
});
test('signature_verifiesWithPublicKey_p1363', () => {
  const [h, p, s] = sign().split('.');
  assert.ok(crypto.verify('sha256', Buffer.from(`${h}.${p}`), { key: KEY.publicKey, dsaEncoding: 'ieee-p1363' }, Buffer.from(s, 'base64url')));
});
test('signature_is64Bytes', () => assert.equal(Buffer.from(sign().split('.')[2], 'base64url').length, 64));
test('tamperedPayload_failsVerify', () => {
  const [h, p, s] = sign().split('.');
  const c = JSON.parse(Buffer.from(p, 'base64url').toString());
  for (const [k, v] of Object.entries({ vip: false, uid: 'u2', nonce: 'x'.repeat(16), notAfterMs: c.notAfterMs + 1, planId: 'vip_life', expiresAtMs: null, source: 'apple', environment: 'Sandbox', iat: 1, v: 2 })) {
    rejects(`${h}.${b64u({ ...c, [k]: v })}.${s}`);
  }
});
test('wrongKey_failsVerify', () => rejects(sign(build(), kp())));
test('roundTrip_returnsClaims', () => {
  assert.deepEqual(verifyEntitlementToken(sign(), KEYS, ctx()), build());
  const nv = build({ entitlement: noVipEnt, verifiedAtMs: null });
  assert.deepEqual(verifyEntitlementToken(sign(nv), KEYS, ctx()), nv);
});
test('verify_acceptsKeyObjectAndSelectsByKid', () => {
  const other = kp();
  assert.ok(verifyEntitlementToken(sign(), { old: spkiOf(other), [KID]: KEY.publicKey }, ctx()));
  rejects(sign(), { old: spkiOf(other) }); // kid không có trong danh sách
});
test('verify_wrongUid_rejected', () => rejects(sign(), KEYS, ctx({ uid: 'u2' })));
test('verify_wrongNonce_rejected', () => rejects(sign(), KEYS, ctx({ nonce: 'z'.repeat(16) })));
test('verify_missingExpectedUidOrNonce_rejected', () => {
  rejects(sign(), KEYS, ctx({ uid: undefined }));
  rejects(sign(), KEYS, ctx({ nonce: undefined }));
  rejects(sign(), KEYS, ctx({ uid: '' }));
});
test('verify_expiryUsesInjectedNow', () => {
  const t = sign();
  const na = build().notAfterMs;
  assert.ok(verifyEntitlementToken(t, KEYS, ctx({ nowMs: na - 1 })));
  rejects(t, KEYS, ctx({ nowMs: na }));
  rejects(t, KEYS, ctx({ nowMs: na + 1 }));
  rejects(t, KEYS, ctx({ nowMs: undefined }));
  rejects(t, KEYS, ctx({ nowMs: '1' }));
});
test('verify_algNone_rejected', () => {
  const p = b64u(build());
  rejects(`${b64u({ alg: 'none', kid: KID })}.${p}.`);
  rejects(`${b64u({ alg: 'none', kid: KID })}.${p}.${Buffer.alloc(64).toString('base64url')}`);
  rejects(`${b64u({ alg: 'None', kid: KID })}.${p}.`);
});
test('verify_algConfusion_rejected', () => {
  const p = b64u(build());
  // HS256 dùng chính khoá công khai làm bí mật HMAC
  const data = `${b64u({ alg: 'HS256', kid: KID })}.${p}`;
  const mac = crypto.createHmac('sha256', Buffer.from(KEYS[KID], 'base64')).update(data).digest('base64url');
  rejects(`${data}.${mac}`);
  for (const alg of ['RS256', 'ES384', 'es256', 'ES256 ', '', null, undefined]) rejects(forge({ alg, kid: KID }, build()));
});
test('verify_headerMustBeExactlyAlgAndKid', () => {
  rejects(forge({ alg: 'ES256', kid: KID, jku: 'http://evil' }, build()));
  rejects(forge({ alg: 'ES256', kid: KID, crit: ['x'] }, build()));
  rejects(forge({ alg: 'ES256' }, build()));
  rejects(forge({ alg: 'ES256', kid: 7 }, build()));
  rejects(forge({ alg: 'ES256', kid: '__proto__' }, build()));
  rejects(forge({ alg: 'ES256', kid: 'constructor' }, build()));
});
test('verify_tamperedHeaderOrSignature_rejected', () => {
  const [h, p, s] = sign().split('.');
  rejects(`${b64u({ kid: KID, alg: 'ES256' })}.${p}.${s}`); // đổi thứ tự khoá = đổi byte
  const sig = Buffer.from(s, 'base64url');
  sig[10] ^= 1;
  rejects(`${h}.${p}.${sig.toString('base64url')}`);
});
test('verify_signatureNot64Bytes_rejected', () => {
  const data = `${b64u({ alg: 'ES256', kid: KID })}.${b64u(build())}`;
  const der = crypto.sign('sha256', Buffer.from(data), KEY.privateKey); // DER, không phải P1363
  rejects(`${data}.${der.toString('base64url')}`);
  rejects(`${data}.`);
  rejects(`${data}.${Buffer.alloc(63).toString('base64url')}`);
  rejects(`${data}.${Buffer.alloc(65).toString('base64url')}`);
});
test('verify_malformedShape_rejected', () => {
  const t = sign();
  for (const bad of [undefined, null, 5, {}, '', 'a.b', `${t}.x`, ` ${t}`, `${t}\n`, t.replace('.', '+'), t.replace(/\./, '=.')]) rejects(bad);
});
test('verify_nonCanonicalBase64url_rejected', () => {
  const [h, p, s] = sign().split('.');
  // Ký tự cuối của chữ ký 64 byte có bit thừa: biến thể cùng byte nhưng khác chuỗi phải bị loại.
  const alt = s.slice(0, -1) + (s.endsWith('A') ? 'B' : 'A');
  if (Buffer.from(alt, 'base64url').equals(Buffer.from(s, 'base64url'))) rejects(`${h}.${p}.${alt}`);
  rejects(`${h}.${p}.${s}==`);
});
test('verify_badClaimShape_rejected', () => {
  const ok = build();
  const bads = [
    { ...ok, v: 2 }, { ...ok, extra: 1 }, { ...ok, vip: 'true' }, { ...ok, notAfterMs: 'x' }, { ...ok, iat: null },
    { ...ok, uid: 5 }, { ...ok, nonce: 5 }, { ...ok, notAfterMs: ok.iat }, { ...ok, expiresAtMs: 'x' },
    { ...build({ entitlement: noVipEnt, verifiedAtMs: null }), planId: 'vip_life' }, // không VIP mà mang gói
    { ...ok, planId: null }, // VIP mà thiếu gói
  ];
  delete ok.nonce;
  bads.push(ok);
  for (const c of bads) rejects(forge({ alg: 'ES256', kid: KID }, c));
  for (const c of [null, [], 'x', 5]) rejects(forge({ alg: 'ES256', kid: KID }, c));
});
test('verify_badPublicKey_rejected_noLeak', () => {
  const rsa = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  const secp = crypto.generateKeyPairSync('ec', { namedCurve: 'secp256k1' });
  for (const k of ['garbage', '', 5, null, rsa.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'), secp.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'), rsa.publicKey, secp.publicKey]) {
    rejects(sign(), { [KID]: k });
  }
  rejects(sign(), null);
  assert.throws(() => verifyEntitlementToken(sign(), undefined, ctx()), { message: 'invalid entitlement token' });
});
test('verify_allFailuresIdentical_noKeyLeak', () => {
  const pem = pemOf(KEY);
  const msgs = new Set();
  for (const t of ['x', sign(build(), kp()), sign(build({ uid: 'u9' }))]) {
    try { verifyEntitlementToken(t, KEYS, ctx()); } catch (e) { msgs.add(`${e.name}|${e.message}`); assert.ok(!String(e.stack).includes(pem.split('\n')[1])); }
  }
  assert.deepEqual([...msgs], ['Error|invalid entitlement token']);
});
test('sign_wrongKeyTypeThrows_noLeak', () => {
  const rsa = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
  const pem = rsa.privateKey.export({ type: 'pkcs8', format: 'pem' });
  const b = pem.split('\n')[1];
  for (const bad of [{ kid: KID, pem }, { kid: KID, pem: 'junk-' + b }, { kid: '', pem: pemOf(KEY) }, { pem: pemOf(KEY) }]) {
    assert.throws(() => signEntitlementToken(build(), bad), (e) => !String(e.message + e.stack).includes(b));
  }
});

test('parseSigningKey_valid', () => {
  const pem = pemOf(KEY);
  assert.deepEqual(parseSigningKey(JSON.stringify({ kid: KID, pem })), { kid: KID, pem });
  const k = parseSigningKey(JSON.stringify({ kid: KID, pem }));
  assert.ok(verifyEntitlementToken(signEntitlementToken(build(), k), KEYS, ctx()));
});
test('parseSigningKey_badJson', () => {
  for (const s of ['', '{', 'null', '[]', '"x"', '5', undefined, null]) assert.throws(() => parseSigningKey(s), { message: 'invalid signing key' });
  assert.throws(() => parseSigningKey(JSON.stringify({ kid: KID, pem: 'not a pem' })), { message: 'invalid signing key' });
  assert.throws(() => parseSigningKey(JSON.stringify({ kid: KID, pem: 5 })), { message: 'invalid signing key' });
});
test('parseSigningKey_nonP256', () => {
  const rsa = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey.export({ type: 'pkcs8', format: 'pem' });
  const secp = crypto.generateKeyPairSync('ec', { namedCurve: 'secp256k1' }).privateKey.export({ type: 'pkcs8', format: 'pem' });
  const p384 = crypto.generateKeyPairSync('ec', { namedCurve: 'P-384' }).privateKey.export({ type: 'pkcs8', format: 'pem' });
  for (const pem of [rsa, secp, p384]) {
    assert.throws(() => parseSigningKey(JSON.stringify({ kid: KID, pem })), (e) => e.message === 'invalid signing key' && !e.message.includes(pem.split('\n')[1]));
  }
});
test('parseSigningKey_missingKid', () => {
  const pem = pemOf(KEY);
  for (const o of [{ pem }, { kid: '', pem }, { kid: 5, pem }, { kid: 'a b', pem }]) {
    assert.throws(() => parseSigningKey(JSON.stringify(o)), { message: 'invalid signing key' });
  }
});
