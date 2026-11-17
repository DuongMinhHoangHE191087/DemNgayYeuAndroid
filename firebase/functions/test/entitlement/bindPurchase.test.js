'use strict';

// Bảng chân trị bindPurchase (p6 M-12): hàm thuần, mặc định TỪ CHỐI, không đọc db.
const test = require('node:test');
const assert = require('node:assert/strict');
const { bindPurchase } = require('../../src/entitlement/bindPurchase');
const { normalizeApple } = require('../../src/entitlement/normalizeApple');
const { DEFAULT_POLICY } = require('../../src/entitlement/resolveEntitlement');
const { DAY, BASE_NOW_MS, makePurchase, makeStoredPurchase } = require('./builders');

const NOW = BASE_NOW_MS;
const U = 'uid-claimer';
const OTHER = 'uid-other';
const CUTOVER = NOW - 10 * DAY;

const apple = (o = {}) => makePurchase(o);
// Google không token, mua trước cutover.
const googleLegacy = (o = {}) => makePurchase({
  store: 'google', key: 'google_abc', lookupId: 'tok', accountToken: null, purchaseTimeMs: CUTOVER - 40 * DAY, ...o,
});
const legacyOk = (o = {}) => ({ untilMs: NOW + 30 * DAY, uidClaims: 0, cutoverMs: CUTOVER, uidCreatedMs: CUTOVER - 100 * DAY, ...o });
const unfinished = (n, o = {}) => Array.from({ length: n }, (_, i) =>
  makeStoredPurchase({ key: `apple_${7000 + i}`, boundAtMs: NOW - 10 * DAY, ...o }));

// claim mặc định: Apple, token đã đăng ký cho chính người claim.
function run(o = {}) {
  return bindPurchase({
    claimerUid: U,
    purchase: apple(),
    tokenOwner: { uid: U },
    predecessorOwner: null,
    existing: null,
    uidPurchases: [],
    nowMs: NOW,
    policy: DEFAULT_POLICY,
    legacy: legacyOk(),
    ...o,
  });
}
const notify = (o = {}) => run({ claimerUid: null, ...o });
const deny = (reason) => ({ ok: false, reason });
const bound = (uid, legacy = false) => ({ ok: true, uid, orphan: false, legacy });
const ORPHAN = { ok: true, uid: null, orphan: true };

// ---------- đường claim ----------
test('familyShared_unsupported', () => {
  assert.deepEqual(run({ purchase: apple({ ownership: 'FAMILY_SHARED' }) }), deny('family-shared-unsupported'));
  assert.deepEqual(run({ purchase: apple({ ownership: 'garbage' }) }), deny('family-shared-unsupported'));
});

test('familyShared_unsupported_evenIfBoundToOtherUid', () => {
  assert.deepEqual(
    run({ purchase: apple({ ownership: 'FAMILY_SHARED' }), existing: { uid: OTHER }, tokenOwner: { uid: OTHER } }),
    deny('family-shared-unsupported'));
  assert.deepEqual(
    run({ purchase: apple({ ownership: 'FAMILY_SHARED' }), existing: { tombstone: true } }),
    deny('family-shared-unsupported'));
});

test('tokenRegistered_ownerBinds', () => {
  assert.deepEqual(run(), bound(U));
  assert.deepEqual(run({ purchase: googleLegacy({ accountToken: 'tk' }) }), bound(U));
});

test('tokenRegistered_otherUid_alreadyBound', () => {
  assert.deepEqual(run({ tokenOwner: { uid: OTHER } }), deny('already-bound'));
});

test('tokenOwnerDeleted_ownerDeleted', () => {
  assert.deepEqual(run({ tokenOwner: { deleted: true } }), deny('owner-deleted'));
});

test('appleNoToken_tokenRequired', () => {
  assert.deepEqual(run({ purchase: apple({ accountToken: null }), tokenOwner: null }), deny('token-required'));
  // Apple không bao giờ đi đường legacy, dù legacy đủ điều kiện.
  assert.deepEqual(run({ purchase: apple({ accountToken: null, purchaseTimeMs: CUTOVER - DAY }), tokenOwner: null, legacy: legacyOk() }), deny('token-required'));
});

test('appleUnknownToken_tokenRequired', () => {
  assert.deepEqual(run({ purchase: apple({ accountToken: 'forged' }), tokenOwner: null }), deny('token-required'));
});

test('googleNoToken_evidenceOk_firstClaimerBinds', () => {
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null }), bound(U, true));
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ uidClaims: 2 }) }), bound(U, true));
});

test('googleNoToken_purchaseAfterCutover_tokenRequired', () => {
  assert.deepEqual(run({ purchase: googleLegacy({ purchaseTimeMs: CUTOVER + DAY }), tokenOwner: null }), deny('token-required'));
  assert.deepEqual(run({ purchase: googleLegacy({ purchaseTimeMs: CUTOVER }), tokenOwner: null }), deny('token-required'));
  assert.deepEqual(run({ purchase: googleLegacy({ purchaseTimeMs: null }), tokenOwner: null }), deny('token-required'));
});

test('googleNoToken_accountCreatedAfterCutover_tokenRequired', () => {
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ uidCreatedMs: CUTOVER + DAY }) }), deny('token-required'));
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ uidCreatedMs: CUTOVER }) }), deny('token-required'));
});

test('googleNoToken_cutoverUnset_tokenRequired', () => {
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ cutoverMs: null }) }), deny('token-required'));
});

test('googleNoToken_authCreationUnknown_tokenRequired', () => {
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ uidCreatedMs: null }) }), deny('token-required'));
});

test('googleNoToken_legacyWindowClosed_legacyWindowClosed', () => {
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ untilMs: NOW - 1 }) }), deny('legacy-window-closed'));
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ untilMs: NOW }) }), deny('legacy-window-closed'));
});

test('googleNoToken_legacyWindowUnset_closed', () => {
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ untilMs: null }) }), deny('legacy-window-closed'));
});

test('googleNoToken_legacyFourthPerUid_legacyWindowClosed', () => {
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ uidClaims: 3 }) }), deny('legacy-window-closed'));
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ uidClaims: null }) }), deny('legacy-window-closed'));
});

test('googleUnknownToken_tokenRequired_notLegacyPath', () => {
  // Token lạ (không có bản ghi appAccountTokens) không bao giờ được coi như "không token".
  assert.deepEqual(run({ purchase: googleLegacy({ accountToken: 'forged' }), tokenOwner: null }), deny('token-required'));
});

test('theftGoogleForgedToken_legacyWindowOpen_tokenRequired', () => {
  assert.deepEqual(
    run({ claimerUid: 'attacker', purchase: googleLegacy({ accountToken: 'attacker-made-up' }), tokenOwner: null, legacy: legacyOk({ uidClaims: 0 }) }),
    deny('token-required'));
});

test('theftGoogleNoToken_attackerAccountCreatedAfterCutover_tokenRequired', () => {
  assert.deepEqual(
    run({ claimerUid: 'attacker', purchase: googleLegacy(), tokenOwner: null, legacy: legacyOk({ uidCreatedMs: CUTOVER + 5 * DAY }) }),
    deny('token-required'));
});

test('existingBinding_otherUid_alreadyBound_evenIfExpired', () => {
  assert.deepEqual(run({ purchase: apple({ status: 'expired' }), existing: { uid: OTHER } }), deny('already-bound'));
  assert.deepEqual(run({ purchase: apple({ status: 'expired' }), existing: { uid: OTHER }, tokenOwner: { uid: U } }), deny('already-bound'));
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, existing: { uid: OTHER } }), deny('already-bound'));
});

test('existingBinding_ownerDeleted_tombstone_ownerDeleted', () => {
  assert.deepEqual(run({ existing: { tombstone: true } }), deny('owner-deleted'));
  assert.deepEqual(run({ existing: { tombstone: true }, tokenOwner: { uid: U } }), deny('owner-deleted'));
});

test('sameUidReclaim_idempotent_noLimitsApplied', () => {
  const full = unfinished(10);
  assert.deepEqual(run({ existing: { uid: U }, uidPurchases: full }), bound(U));
  // Restore Purchases vẫn chạy dù token không còn khớp và Sandbox đã đủ 3.
  assert.deepEqual(run({ existing: { uid: U }, purchase: apple({ accountToken: null, environment: 'Sandbox' }), tokenOwner: null, uidPurchases: unfinished(3, { environment: 'Sandbox' }) }), bound(U));
});

test('bindLimitsExceeded_tooManyActive', () => {
  assert.deepEqual(run({ uidPurchases: unfinished(10) }), deny('too-many-active'));
  assert.deepEqual(run({ uidPurchases: unfinished(9) }), bound(U));
  // trần 5 ràng buộc mới / 24 giờ
  assert.deepEqual(run({ uidPurchases: unfinished(5, { boundAtMs: NOW - 1000 }) }), deny('too-many-active'));
});

test('sandboxFourthBinding_tooManyActive', () => {
  const sandbox = apple({ environment: 'Sandbox', key: 'apple_sbx' });
  assert.deepEqual(run({ purchase: sandbox, uidPurchases: unfinished(3, { environment: 'Sandbox', status: 'expired' }) }), deny('too-many-active'));
  assert.deepEqual(run({ purchase: sandbox, uidPurchases: unfinished(2, { environment: 'Sandbox', status: 'expired' }) }), bound(U));
});

test('legacyPath_bindLimitsStillApply', () => {
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, uidPurchases: unfinished(10) }), deny('too-many-active'));
});

test('missingBoundAtInHistory_countsAsNew_tooManyActive', () => {
  // M-88: purchase lưu thiếu boundAtMs bị tính là mới, nên không bao giờ làm lỏng trần.
  const noBoundAt = unfinished(5).map((p) => { const { boundAtMs, ...rest } = p; return rest; });
  assert.deepEqual(run({ uidPurchases: noBoundAt }), deny('too-many-active'));
});

// ---------- đường thông báo ----------
test('notificationWithoutOwner_orphan', () => {
  assert.deepEqual(notify({ tokenOwner: null }), ORPHAN);
  assert.deepEqual(notify({ purchase: apple({ accountToken: null }), tokenOwner: null }), ORPHAN);
  assert.deepEqual(notify({ purchase: apple({ accountToken: 'forged' }), tokenOwner: null }), ORPHAN);
  // thông báo Google không token không bao giờ đi đường legacy
  assert.deepEqual(notify({ purchase: googleLegacy(), tokenOwner: null }), ORPHAN);
});

test('notificationExistingAliveKeepsUid', () => {
  assert.deepEqual(notify({ existing: { uid: OTHER }, tokenOwner: null }), bound(OTHER));
  assert.deepEqual(notify({ existing: { uid: OTHER }, tokenOwner: { uid: U }, uidPurchases: unfinished(10) }), bound(OTHER));
});

test('notificationWithRegisteredToken_bindsToTokenOwner', () => {
  assert.deepEqual(notify({ tokenOwner: { uid: OTHER } }), bound(OTHER));
});

test('notificationOnTombstone_orphanNoGrant', () => {
  assert.deepEqual(notify({ existing: { tombstone: true }, tokenOwner: { uid: U } }), ORPHAN);
  assert.deepEqual(notify({ tokenOwner: { deleted: true } }), ORPHAN);
});

test('notificationBindOverLimit_orphanNoGrant', () => {
  assert.deepEqual(notify({ tokenOwner: { uid: OTHER }, uidPurchases: unfinished(10) }), { ok: true, uid: null, orphan: true, limited: true });
});

test('notificationFamilyShared_orphanNoGrant', () => {
  assert.deepEqual(notify({ purchase: apple({ ownership: 'FAMILY_SHARED' }), tokenOwner: { uid: OTHER } }), ORPHAN);
});

// ---------- kịch bản trộm ----------
test('theftBeforeFirstClaim_tokenBearingPurchase_alreadyBound', () => {
  // Token gắn lúc mua đã đăng ký cho nạn nhân; người cầm mã giao dịch claim trước khi nạn nhân mở app.
  assert.deepEqual(run({ claimerUid: 'attacker', tokenOwner: { uid: 'victim' }, existing: null }), deny('already-bound'));
});

test('theftAfterOwnerDeletion_tombstone_ownerDeleted', () => {
  assert.deepEqual(run({ claimerUid: 'attacker', existing: { tombstone: true }, tokenOwner: { deleted: true } }), deny('owner-deleted'));
  assert.deepEqual(run({ claimerUid: 'attacker', existing: null, tokenOwner: { deleted: true } }), deny('owner-deleted'));
});

// ---------- dòng dõi (predecessorOwner, M-90) ----------
test('predecessorOwnerLive_notification_bindsToPredecessorOwner', () => {
  assert.deepEqual(notify({ predecessorOwner: { uid: OTHER }, tokenOwner: null, purchase: googleLegacy() }), bound(OTHER));
});

test('predecessorOwnerLive_claimByOtherUid_alreadyBound', () => {
  assert.deepEqual(run({ predecessorOwner: { uid: OTHER } }), deny('already-bound'));
  assert.deepEqual(run({ predecessorOwner: { uid: OTHER }, tokenOwner: { uid: U } }), deny('already-bound'));
});

test('predecessorOwner_beatsTokenOwnerOfDeletedOrOldUid', () => {
  assert.deepEqual(run({ predecessorOwner: { uid: U }, tokenOwner: { deleted: true } }), bound(U));
  assert.deepEqual(notify({ predecessorOwner: { uid: OTHER }, tokenOwner: { deleted: true } }), bound(OTHER));
  assert.deepEqual(notify({ predecessorOwner: { uid: OTHER }, tokenOwner: { uid: 'old' } }), bound(OTHER));
});

test('predecessorOwnerRebound_repurchaseFollowsNewOwner', () => {
  const ctx = { predecessorOwner: { uid: 'new' }, tokenOwner: { uid: 'old' } };
  assert.deepEqual(run({ ...ctx, claimerUid: 'new' }), bound('new'));
  assert.deepEqual(run({ ...ctx, claimerUid: 'old' }), deny('already-bound'));
  assert.deepEqual(notify(ctx), bound('new'));
});

test('predecessorOwnerLegacyBound_repurchaseNoLegacyWindowNeeded', () => {
  const closed = legacyOk({ untilMs: null, cutoverMs: null, uidCreatedMs: null });
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: null, predecessorOwner: { uid: U }, legacy: closed }), bound(U));
  assert.deepEqual(notify({ purchase: googleLegacy(), tokenOwner: null, predecessorOwner: { uid: U }, legacy: closed }), bound(U));
});

test('predecessorTombstone_claimOwnerDeleted_notificationOrphan', () => {
  assert.deepEqual(run({ predecessorOwner: { tombstone: true } }), deny('owner-deleted'));
  assert.deepEqual(notify({ predecessorOwner: { tombstone: true }, tokenOwner: { uid: U } }), ORPHAN);
});

test('predecessorOwner_lineageReplacement_skipsBindLimits', () => {
  const full = unfinished(10);
  assert.deepEqual(run({ predecessorOwner: { uid: U }, uidPurchases: full }), bound(U));
  assert.deepEqual(notify({ predecessorOwner: { uid: U }, uidPurchases: full }), bound(U));
});

test('predecessorOwner_familyShared_stillDenied', () => {
  const p = apple({ ownership: 'FAMILY_SHARED' });
  assert.deepEqual(run({ purchase: p, predecessorOwner: { uid: U } }), deny('family-shared-unsupported'));
  assert.deepEqual(notify({ purchase: p, predecessorOwner: { uid: U } }), ORPHAN);
});

// ---------- mặc định từ chối ----------
test('tokenOwnerButPurchaseCarriesNoToken_neverBinds', () => {
  // Dữ liệu mâu thuẫn (chủ token mà giao dịch không mang token): không ai được gắn.
  const p = apple({ accountToken: null });
  assert.deepEqual(run({ purchase: p, tokenOwner: { uid: U } }), deny('token-required'));
  assert.deepEqual(notify({ purchase: p, tokenOwner: { uid: OTHER } }), ORPHAN);
});

test('malformedPurchase_denied_neverSuccess', () => {
  // normalizeApple trả notOurs cho giao dịch hỏng; đưa thẳng vào cũng không thành công.
  const bad = normalizeApple({ transaction: null, bundleId: 'b', asOfMs: NOW });
  assert.equal(bad.reason, 'malformed-transaction');
  for (const purchase of [bad, null, undefined, {}, 'x', apple({ store: 'other' }), apple({ key: '' })]) {
    assert.equal(run({ purchase }).ok, false);
    assert.equal(run({ purchase, tokenOwner: { uid: U } }).ok, false);
    const n = notify({ purchase });
    assert.equal(n.ok === true && n.uid !== null, false);
  }
});

test('malformedOwnerShapes_neverGrant', () => {
  for (const bad of [{}, { uid: '' }, { uid: 5 }, { uid: null }, 'x', 0, []]) {
    for (const key of ['existing', 'tokenOwner', 'predecessorOwner']) {
      assert.equal(run({ [key]: bad }).ok, false, `claim ${key} ${JSON.stringify(bad)}`);
      assert.deepEqual(notify({ [key]: bad }), ORPHAN, `notify ${key} ${JSON.stringify(bad)}`);
    }
  }
});

test('badTokenOwner_alreadyBound_notFallbackToLegacy', () => {
  // Chủ token hình dạng lạ không được coi như "chưa có chủ": không rơi xuống đường legacy hay token-required.
  assert.deepEqual(run({ tokenOwner: {} }), deny('already-bound'));
  assert.deepEqual(run({ purchase: googleLegacy(), tokenOwner: {}, legacy: legacyOk() }), deny('already-bound'));
});

test('googleAccountTokenUndefined_tokenRequired_notLegacy', () => {
  // Thiếu hẳn trường accountToken (undefined) khác với null: không đi đường legacy.
  const p = googleLegacy();
  delete p.accountToken;
  assert.deepEqual(run({ purchase: p, tokenOwner: null, legacy: legacyOk() }), deny('token-required'));
});

test('throwingInput_denied_neverGrantsNorOrphans', () => {
  // Đầu vào làm hàm ném lỗi phải ra từ chối, không phải ORPHAN thành công.
  const trap = () => { throw new Error('boom'); };
  const throwingPurchase = { store: 'apple', key: 'apple_1', get ownership() { return trap(); } };
  assert.deepEqual(run({ purchase: throwingPurchase }), deny('invalid-input'));
  assert.deepEqual(notify({ purchase: throwingPurchase }), deny('invalid-input'));
  assert.deepEqual(bindPurchase({ claimerUid: U, get purchase() { return trap(); } }), deny('invalid-input'));
});

test('invalidClaimerUid_denied', () => {
  for (const claimerUid of ['', 5, {}, undefined]) {
    assert.equal(run({ claimerUid }).ok, false);
  }
});

test('garbageCall_neverThrowsNeverGrants', () => {
  for (const args of [undefined, null, {}, { claimerUid: U }, { claimerUid: U, purchase: apple() }]) {
    const r = bindPurchase(args);
    assert.equal(r.ok === true && r.uid !== null, false);
  }
  assert.equal(run({ uidPurchases: null }).ok, false);
  assert.equal(run({ policy: null }).ok, false);
  assert.equal(run({ nowMs: NaN }).ok, false);
});
