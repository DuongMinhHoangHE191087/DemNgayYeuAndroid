'use strict';

// M-17: claimPurchase. Client chỉ gửi {store, id}; mọi trường khác bị bỏ qua, server tự quyết quyền sở hữu qua applyStoreState/bindPurchase.
// Mặc định TỪ CHỐI; phản hồi {vip, planId, state} chỉ là thông tin không ký, không bao giờ là căn cứ cấp VIP.
const test = require('node:test');
const assert = require('node:assert/strict');
const { HttpsError } = require('firebase-functions/v2/https');
const { claimPurchase, parseClaimInput } = require('../../src/entitlement/claimPurchase');
const { googleKey } = require('../../src/entitlement/normalizeGoogle');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');
const { makeDeps, fakeApple, fakeGoogle, unavailableError } = require('./depsBuilder');
const { BASE_NOW_MS: T, DAY, HOUR } = require('./builders');
const { appleTransaction, appleRenewalInfo } = require('./fixtures/apple');
const { googleSubscription, googleProduct, iso } = require('./fixtures/google');

const TOKEN = '11111111-1111-4111-8111-111111111111'; // chủ: uid-1
const UNKNOWN_TOKEN = '33333333-3333-4333-8333-333333333333'; // chưa đăng ký cho ai
const GTOKEN = 'google-token-1';
const AKEY = 'apple_1000';
const GKEY = googleKey(GTOKEN);
const AUDIT_PREFIX = 'entitlementAudit/claim-denied_';
const PENDING_ACK = 'ACKNOWLEDGEMENT_STATE_PENDING';

const appleState = (txOver = {}, extra = {}) => ({ environment: 'Production', transaction: appleTransaction(txOver), renewalInfo: null, status: 1, ...extra });
const appleDeps = (state, opts = {}) => makeDeps({ apple: fakeApple({ states: { 1000: state } }), ...opts });
// Đăng ký TOKEN và chưa ack mặc định để kiểm đường acknowledge.
const gsub = (o = {}) => googleSubscription({ externalAccountIdentifiers: { obfuscatedExternalAccountId: TOKEN }, acknowledgementState: PENDING_ACK, ...o });
const googleDeps = (sub, opts = {}) => makeDeps({ google: fakeGoogle({ subscriptions: { [GTOKEN]: sub } }), ...opts });
const legacyOpts = (o = {}) => ({ legacyClaimUntilMs: T + DAY, accountTokenCutoverMs: T - DAY, authCreatedMs: async () => T - 100 * DAY, ...o });

const seedOwner = async (deps, uid, token = TOKEN) => {
  await deps.db.collection('appAccountTokens').doc(token).set({ uid, createdAtMs: 0 });
  await deps.db.collection('entitlementAccounts').doc(uid).set({ token });
};
const claimApple = (deps, extra = {}) => claimPurchase(deps, { uid: 'uid-1', store: 'apple', id: '1000', ...extra });
const claimGoogle = (deps, extra = {}) => claimPurchase(deps, { uid: 'uid-1', store: 'google', id: GTOKEN, ...extra });
const methods = (gate) => gate.calls.map((c) => c.method);
const stored = (deps, path) => deps.db.read(path);
const auditDocs = (deps) => Object.keys(deps.db.snapshot()).filter((p) => p.startsWith(AUDIT_PREFIX));
// Ảnh chụp không tính các tài liệu claim-denied: bị từ chối thì chỉ được thêm đúng các tài liệu đó.
const withoutClaimAudit = (deps) => Object.fromEntries(Object.entries(deps.db.snapshot()).filter(([p]) => !p.startsWith(AUDIT_PREFIX)));

async function assertHttps(promise, code, message, details) {
  await assert.rejects(promise, (e) => {
    assert.ok(e instanceof HttpsError, `HttpsError mong đợi, nhận ${e}`);
    assert.equal(e.code, code);
    if (message !== undefined) assert.equal(e.message, message);
    if (details !== undefined) assert.deepEqual(e.details, details);
    return true;
  });
}
const assertDenied = (promise, reason) => assertHttps(promise, 'permission-denied', reason, { reason });

// ---- cấp quyền ----

test('appleSubscription_grants', async () => {
  const deps = appleDeps(appleState());
  await seedOwner(deps, 'uid-1');
  assert.deepEqual(await claimApple(deps), { vip: true, planId: 'monthly', state: 'active' });
  assert.equal(stored(deps, `purchases/${AKEY}`).uid, 'uid-1');
  assert.equal(stored(deps, 'entitlements/uid-1').vip, true);
  assert.deepEqual(methods(deps.apple), ['fetchState']);
  assert.deepEqual(deps.google.calls, []);
  assert.deepEqual(auditDocs(deps), []);
});

test('appleLifetime_grants', async () => {
  const deps = appleDeps(appleState({ productId: 'vip_lifetime', type: 'Non-Consumable', expiresDate: undefined }, { status: undefined }));
  await seedOwner(deps, 'uid-1');
  assert.deepEqual(await claimApple(deps), { vip: true, planId: 'lifetime', state: 'active' });
  assert.equal(stored(deps, 'entitlements/uid-1').expiresAtMs, null);
});

test('appleRevoked_recordedNotGranted', async () => {
  const deps = appleDeps(appleState({ revocationDate: T - 1000 }, { status: 5 }));
  await seedOwner(deps, 'uid-1');
  assert.deepEqual(await claimApple(deps), { vip: false, planId: 'monthly', state: 'revoked' });
  assert.equal(stored(deps, `purchases/${AKEY}`).revokedAtMs, T - 1000);
  assert.equal(stored(deps, 'entitlements/uid-1').vip, false);
  assert.deepEqual(auditDocs(deps), []);
});

test('googleSubscription_grantsThenAcknowledges', async () => {
  const deps = googleDeps(gsub());
  await seedOwner(deps, 'uid-1');
  assert.deepEqual(await claimGoogle(deps), { vip: true, planId: 'monthly', state: 'active' });
  assert.deepEqual(methods(deps.google), ['getSubscription', 'acknowledgeSubscription']);
  assert.deepEqual(deps.google.calls[1].args[0], { productId: 'vip_monthly', purchaseToken: GTOKEN });
  assert.equal(stored(deps, `purchases/${GKEY}`).ackState, 'done');
  assert.equal(stored(deps, 'entitlements/uid-1').vip, true);
});

test('googleAcknowledge_notCalledWhenBindRejected', async () => {
  const deps = googleDeps(gsub());
  await seedOwner(deps, 'uid-2'); // TOKEN thuộc uid-2, uid-1 không được claim
  await assertDenied(claimGoogle(deps), 'already-bound');
  assert.deepEqual(methods(deps.google), ['getSubscription']);
  assert.equal(stored(deps, `purchases/${GKEY}`), undefined);
});

test('googleAckFailure_stillGranted_ackPending', async () => {
  const deps = googleDeps(gsub());
  await seedOwner(deps, 'uid-1');
  deps.google.fail('acknowledgeSubscription', unavailableError());
  assert.deepEqual(await claimGoogle(deps), { vip: true, planId: 'monthly', state: 'active' });
  const p = stored(deps, `purchases/${GKEY}`);
  assert.equal(p.uid, 'uid-1');
  assert.equal(p.ackState, 'pending');
  assert.equal(p.ackAttempts, 1);
  assert.equal(stored(deps, 'entitlements/uid-1').vip, true);
});

test('googleAckStoreErrorSwallowed_grantStillReturned', async () => {
  const deps = googleDeps(gsub());
  await seedOwner(deps, 'uid-1');
  // Google ack lỗi rồi bản ghi biến mất: ackIfPending ném lỗi ghi (update NOT_FOUND). Quyền đã cấp vẫn được trả, không ném ra client.
  deps.google.acknowledgeSubscription = async () => {
    await deps.db.collection('purchases').doc(GKEY).delete();
    throw unavailableError();
  };
  assert.deepEqual(await claimGoogle(deps), { vip: true, planId: 'monthly', state: 'active' });
});

// ---- từ chối ----

test('notOurs_permissionDenied', async () => {
  // Bundle không phải của app: mọi lý do notOurs đều ra cùng một lỗi, không ghi gì, không audit.
  const deps = appleDeps(appleState({ bundleId: 'com.other.app' }));
  await seedOwner(deps, 'uid-1');
  const before = deps.db.snapshot();
  await assertDenied(claimApple(deps), 'not-ours');
  assert.deepEqual(deps.db.snapshot(), before);
});

test('storeNotFound_notOurs', async () => {
  const deps = makeDeps();
  await assertDenied(claimApple(deps), 'not-ours');
  await assertDenied(claimGoogle(deps), 'not-ours');
  assert.deepEqual(deps.db.snapshot(), {});
});

test('alreadyBound_permissionDenied', async () => {
  const deps = appleDeps(appleState());
  await seedOwner(deps, 'uid-1');
  await deps.db.collection('purchases').doc(AKEY).set({ store: 'apple', key: AKEY, uid: 'uid-2', asOfMs: T - DAY });
  const before = withoutClaimAudit(deps);
  await assertDenied(claimApple(deps), 'already-bound');
  assert.deepEqual(withoutClaimAudit(deps), before);
  assert.equal(stored(deps, `purchases/${AKEY}`).uid, 'uid-2');
});

test('ownerDeleted_permissionDenied', async () => {
  const deps = appleDeps(appleState());
  await seedOwner(deps, 'uid-1');
  await deps.db.collection('purchases').doc(AKEY).set({ store: 'apple', key: AKEY, uid: null, tombstone: { uidHash: 'x', atMs: 1 } });
  await assertDenied(claimApple(deps), 'owner-deleted');
  assert.equal(stored(deps, 'entitlements/uid-1'), undefined);
});

test('tokenRequired_permissionDenied', async () => {
  // Apple mang token chưa đăng ký cho ai: không bao giờ có đường legacy.
  const deps = appleDeps(appleState({ appAccountToken: UNKNOWN_TOKEN }), legacyOpts());
  const before = withoutClaimAudit(deps);
  await assertDenied(claimApple(deps), 'token-required');
  assert.deepEqual(withoutClaimAudit(deps), before);
  assert.equal(stored(deps, `purchases/${AKEY}`), undefined);
});

test('legacyWindowClosed_permissionDenied', async () => {
  const deps = googleDeps(gsub({ externalAccountIdentifiers: undefined }), legacyOpts({ legacyClaimUntilMs: T - 1 }));
  await assertDenied(claimGoogle(deps), 'legacy-window-closed');
  assert.equal(stored(deps, `purchases/${GKEY}`), undefined);
  assert.equal(stored(deps, `entitlementAudit/legacy_${GKEY}`), undefined);
  assert.equal(auditDocs(deps).length, 1);
});

test('googleUnknownToken_tokenRequired_noLegacyPath', async () => {
  // Có token lạ thì không được dùng đường legacy dù cửa sổ đang mở và uid đủ cũ.
  const deps = googleDeps(gsub({ externalAccountIdentifiers: { obfuscatedExternalAccountId: UNKNOWN_TOKEN } }), legacyOpts());
  await assertDenied(claimGoogle(deps), 'token-required');
  assert.equal(stored(deps, `entitlementAudit/legacy_${GKEY}`), undefined);
  assert.equal(stored(deps, `purchases/${GKEY}`), undefined);
  assert.deepEqual(methods(deps.google), ['getSubscription']);
});

test('googleNoToken_authCreatedAfterCutover_tokenRequired', async () => {
  const deps = googleDeps(gsub({ externalAccountIdentifiers: undefined }), legacyOpts({ authCreatedMs: async () => T + 1 }));
  await assertDenied(claimGoogle(deps), 'token-required');
  assert.equal(stored(deps, `purchases/${GKEY}`), undefined);
});

test('googleNoToken_authCreatedMsNull_tokenRequired', async () => {
  const deps = googleDeps(gsub({ externalAccountIdentifiers: undefined }), legacyOpts({ authCreatedMs: async () => null }));
  await assertDenied(claimGoogle(deps), 'token-required');
  assert.equal(stored(deps, `purchases/${GKEY}`), undefined);
});

test('googleNoToken_withEvidence_grantsAndAuditsLegacy', async () => {
  const deps = googleDeps(gsub({ externalAccountIdentifiers: undefined }), legacyOpts());
  assert.deepEqual(await claimGoogle(deps), { vip: true, planId: 'monthly', state: 'active' });
  const audit = stored(deps, `entitlementAudit/legacy_${GKEY}`);
  assert.equal(audit.type, 'legacy-bind');
  assert.equal(audit.uid, 'uid-1');
  assert.equal(stored(deps, 'entitlementAccounts/uid-1').legacyClaims, 1);
  assert.equal(stored(deps, `purchases/${GKEY}`).uid, 'uid-1');
  assert.deepEqual(auditDocs(deps), []);
});

test('familyShared_permissionDenied', async () => {
  const deps = appleDeps(appleState({ inAppOwnershipType: 'FAMILY_SHARED' }));
  await seedOwner(deps, 'uid-1');
  const before = deps.db.snapshot();
  await assertDenied(claimApple(deps), 'family-shared-unsupported');
  assert.deepEqual(deps.db.snapshot(), before);
});

test('tooManyActive_permissionDenied', async () => {
  // Trần 0 purchase chưa kết thúc: mọi ràng buộc mới đều vượt trần.
  const deps = appleDeps(appleState(), { policy: { ...DEFAULT_POLICY, maxUnfinished: 0 } });
  await seedOwner(deps, 'uid-1');
  const before = deps.db.snapshot();
  await assertDenied(claimApple(deps), 'too-many-active');
  assert.deepEqual(deps.db.snapshot(), before);
});

test('xcodeEnvironment_permissionDenied', async () => {
  const deps = appleDeps(appleState({ environment: 'Xcode' }, { environment: 'Xcode' }));
  await seedOwner(deps, 'uid-1');
  const before = deps.db.snapshot();
  await assertDenied(claimApple(deps), 'not-ours');
  assert.deepEqual(deps.db.snapshot(), before);
});

test('tokenOfOtherUid_permissionDenied', async () => {
  const deps = appleDeps(appleState());
  await seedOwner(deps, 'uid-2'); // TOKEN đã đăng ký cho uid-2
  await seedOwner(deps, 'uid-1', UNKNOWN_TOKEN);
  await assertDenied(claimApple(deps), 'already-bound');
  assert.equal(stored(deps, `purchases/${AKEY}`), undefined);
  assert.equal(stored(deps, 'entitlements/uid-1'), undefined);
});

test('invalidUid_permissionDenied_noWrites', async () => {
  // Lý do lạ (invalid-input) cũng bị từ chối, không lộ chi tiết, không ghi gì.
  const deps = appleDeps(appleState());
  await assertDenied(claimApple(deps, { uid: '' }), 'denied');
  assert.deepEqual(deps.db.snapshot(), {});
});

test('storeTransient_unavailable', async () => {
  const apple = appleDeps(appleState());
  apple.apple.fail('fetchState', unavailableError());
  await assertHttps(claimApple(apple), 'unavailable');
  const google = googleDeps(gsub());
  google.google.fail('getSubscription', unavailableError());
  google.google.fail('getProduct', unavailableError());
  await assertHttps(claimGoogle(google), 'unavailable');
  assert.deepEqual(apple.db.snapshot(), {});
  assert.deepEqual(google.db.snapshot(), {});
});

test('clientSuppliedFields_ignored', async () => {
  const deps = googleDeps(gsub());
  await seedOwner(deps, 'uid-1');
  const res = await claimGoogle(deps, {
    kind: 'product', claimerUid: 'victim', planId: 'lifetime', productId: 'vip_lifetime', vip: true, state: 'active', key: 'google_x', dedupeKey: 'k',
  });
  assert.deepEqual(res, { vip: true, planId: 'monthly', state: 'active' });
  assert.deepEqual(methods(deps.google), ['getSubscription', 'acknowledgeSubscription']); // không getProduct: kind của client bị bỏ qua
  assert.equal(stored(deps, `purchases/${GKEY}`).uid, 'uid-1');
  assert.equal(stored(deps, 'entitlements/victim'), undefined);
  assert.equal(stored(deps, 'notificationLog/k'), undefined);
  assert.deepEqual(parseClaimInput({ store: 'apple', id: '1000', uid: 'victim', kind: 'product', vip: true }), { store: 'apple', id: '1000' });
});

// ---- audit claim-denied ----

test('claimDeniedAudit_onePerHourPerPurchase', async () => {
  const deps = appleDeps(appleState({ appAccountToken: UNKNOWN_TOKEN }));
  const hour = Math.floor(deps.nowMs() / HOUR);
  await assertDenied(claimApple(deps), 'token-required');
  assert.deepEqual(stored(deps, `${AUDIT_PREFIX}${AKEY}_${hour}`), { uid: 'uid-1', key: AKEY, reason: 'token-required', atMs: T });
  deps.clock.advance(1000);
  await assertDenied(claimApple(deps, { uid: 'uid-2' }), 'token-required');
  assert.equal(auditDocs(deps).length, 1, 'cùng purchase, cùng giờ: một tài liệu');
  deps.clock.advance(HOUR);
  await assertDenied(claimApple(deps), 'token-required');
  assert.deepEqual(auditDocs(deps).sort(), [`${AUDIT_PREFIX}${AKEY}_${hour}`, `${AUDIT_PREFIX}${AKEY}_${hour + 1}`]);
  // purchase khác thì tài liệu khác.
  deps.apple.states[1001] = appleState({ originalTransactionId: '1001', appAccountToken: UNKNOWN_TOKEN });
  await assertDenied(claimApple(deps, { id: '1001' }), 'token-required');
  assert.equal(auditDocs(deps).length, 3);
});

test('claimDeniedAudit_notWrittenForFamilySharedOrTooManyActive', async () => {
  const family = appleDeps(appleState({ inAppOwnershipType: 'FAMILY_SHARED' }));
  await seedOwner(family, 'uid-1');
  await assertDenied(claimApple(family), 'family-shared-unsupported');
  assert.deepEqual(auditDocs(family), []);
  const many = appleDeps(appleState(), { policy: { ...DEFAULT_POLICY, maxUnfinished: 0 } });
  await seedOwner(many, 'uid-1');
  await assertDenied(claimApple(many), 'too-many-active');
  assert.deepEqual(auditDocs(many), []);
});

test('claimDeniedAuditWriteFails_stillPermissionDenied', async () => {
  const deps = appleDeps(appleState({ appAccountToken: UNKNOWN_TOKEN }));
  let attempts = 0;
  const collection = deps.db.collection;
  deps.db.collection = (name) => {
    const c = collection(name);
    if (name !== 'entitlementAudit') return c;
    return { ...c, doc: (id) => ({ ...c.doc(id), set: async () => { attempts += 1; throw new Error('audit down'); } }) };
  };
  await assertDenied(claimApple(deps), 'token-required');
  assert.equal(attempts, 1);
  assert.deepEqual(auditDocs(deps), []);
});

// ---- phản hồi ----

test('responseState_table', async () => {
  const apple = (state) => async () => { const d = appleDeps(state); await seedOwner(d, 'uid-1'); return [d, claimApple]; };
  const google = (o) => async () => { const d = googleDeps(gsub(o)); await seedOwner(d, 'uid-1'); return [d, claimGoogle]; };
  const grace = appleRenewalInfo({ gracePeriodExpiresDate: T + 3 * DAY });
  const rows = [
    ['apple active', apple(appleState()), { vip: true, planId: 'monthly', state: 'active' }],
    ['apple grace with real grace end', apple(appleState({}, { status: 4, renewalInfo: grace })), { vip: true, planId: 'monthly', state: 'grace' }],
    ['apple grace without grace end', apple(appleState({}, { status: 4 })), { vip: false, planId: 'monthly', state: 'expired' }],
    ['apple retry', apple(appleState({}, { status: 3 })), { vip: false, planId: 'monthly', state: 'retry' }],
    ['apple expired', apple(appleState({}, { status: 2 })), { vip: false, planId: 'monthly', state: 'expired' }],
    ['apple revoked', apple(appleState({ revocationDate: T - 1 }, { status: 5 })), { vip: false, planId: 'monthly', state: 'revoked' }],
    ['google pending', google({ subscriptionState: 'SUBSCRIPTION_STATE_PENDING' }), { vip: false, planId: 'monthly', state: 'pending' }],
    ['google on hold', google({ subscriptionState: 'SUBSCRIPTION_STATE_ON_HOLD' }), { vip: false, planId: 'monthly', state: 'retry' }],
    ['google expired', google({
      subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED', lineItems: [{ productId: 'vip_yearly', expiryTime: iso(T - DAY) }],
    }), { vip: false, planId: 'yearly', state: 'expired' }],
  ];
  for (const [name, setup, expected] of rows) {
    const [deps, run] = await setup();
    assert.deepEqual(await run(deps), expected, name);
  }
});

test('sameUidReclaim_idempotent', async () => {
  const deps = googleDeps(gsub());
  await seedOwner(deps, 'uid-1');
  const first = await claimGoogle(deps);
  const snapshot = deps.db.snapshot();
  const writes = deps.db.writes;
  assert.deepEqual(await claimGoogle(deps), first);
  assert.deepEqual(deps.db.snapshot(), snapshot);
  assert.equal(deps.db.writes, writes);
  assert.equal(methods(deps.google).filter((m) => m === 'acknowledgeSubscription').length, 1);
  // Cổng chỉ ack khi purchase còn `pending`: lifetime Google đi qua acknowledgeProduct.
  const lifetime = makeDeps({ google: fakeGoogle({ products: { [GTOKEN]: googleProduct({ obfuscatedExternalAccountId: TOKEN, acknowledgementState: PENDING_ACK }) } }) });
  await seedOwner(lifetime, 'uid-1');
  assert.deepEqual(await claimGoogle(lifetime), { vip: true, planId: 'lifetime', state: 'active' });
  assert.deepEqual(methods(lifetime.google), ['getSubscription', 'getProduct', 'acknowledgeProduct']);
});

// ---- parseClaimInput ----

test('parseClaimInput_rejectsBadInput', () => {
  const bad = [
    undefined, null, 'apple', 42, [], {},
    { store: 'amazon', id: 'x' }, { store: 'APPLE', id: 'x' }, { store: undefined, id: 'x' }, { store: 1, id: 'x' },
    { store: 'apple' }, { store: 'apple', id: '' }, { store: 'apple', id: 123 }, { store: 'google', id: null }, { store: 'google', id: ['a'] },
    { store: 'google', id: 'a'.repeat(4097) },
  ];
  for (const data of bad) {
    assert.throws(() => parseClaimInput(data), (e) => e instanceof HttpsError && e.code === 'invalid-argument', JSON.stringify(data)?.slice(0, 60));
  }
  const max = 'a'.repeat(4096);
  assert.deepEqual(parseClaimInput({ store: 'google', id: max, extra: 1 }), { store: 'google', id: max });
  assert.deepEqual(parseClaimInput({ store: 'apple', id: '1000' }), { store: 'apple', id: '1000' });
});
