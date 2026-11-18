'use strict';

// M-83: bảng phân loại callable (free/tiered/vip) và evaluateGate thuần. Mặc định TỪ CHỐI; Sandbox không bao giờ là VIP.
const test = require('node:test');
const assert = require('node:assert/strict');
const { GATES, MEDIA_FREE_ALLOWED, evaluateGate } = require('../../src/entitlement/gatePolicy');

const ent = (o = {}) => ({ vip: true, state: 'active', environment: 'Production', ...o });
const NONE = { vip: false, state: 'none', environment: null };

test('free_alwaysAllowed_tierNull', () => {
  for (const e of [NONE, ent(), ent({ environment: 'Sandbox' }), null, undefined]) {
    assert.deepEqual(evaluateGate({ gate: 'free', entitlement: e }), { tier: null });
  }
});

test('vip_requiresRealVip', () => {
  assert.deepEqual(evaluateGate({ gate: 'vip', entitlement: ent() }), { tier: 'vip' });
});

test('vip_deniedForNoPurchase', () => {
  for (const e of [NONE, null, undefined, {}]) {
    assert.deepEqual(evaluateGate({ gate: 'vip', entitlement: e }), { denied: 'vip-required' });
  }
  // vip phải đúng là true, giá trị "truthy" khác không qua.
  for (const v of ['true', 1, {}]) {
    assert.deepEqual(evaluateGate({ gate: 'vip', entitlement: ent({ vip: v }) }), { denied: 'vip-required' });
  }
});

test('vip_deniedForSandbox', () => {
  assert.deepEqual(evaluateGate({ gate: 'vip', entitlement: ent({ environment: 'Sandbox' }) }), { denied: 'vip-required' });
  for (const env of [null, undefined, 'production', 'Xcode']) {
    assert.deepEqual(evaluateGate({ gate: 'vip', entitlement: ent({ environment: env }) }), { denied: 'vip-required' });
  }
});

test('vip_deniedForStale', () => {
  assert.deepEqual(evaluateGate({ gate: 'vip', entitlement: { ...NONE, state: 'stale' } }), { denied: 'vip-required' });
});

test('vip_deniedForExpiredOrRevoked', () => {
  for (const state of ['expired', 'revoked', 'retry', 'pending']) {
    assert.deepEqual(evaluateGate({ gate: 'vip', entitlement: { ...NONE, state } }), { denied: 'vip-required' });
  }
});

test('vip_deniedReasonIsVipRequired', () => {
  assert.equal(evaluateGate({ gate: 'vip', entitlement: NONE }).denied, 'vip-required');
});

test('tiered_vipGetsTierVip', () => {
  for (const freeAllowed of [true, false]) {
    assert.deepEqual(evaluateGate({ gate: 'tiered', freeAllowed, entitlement: ent() }), { tier: 'vip' });
  }
});

test('tiered_freeGetsTierFreeWhenAllowed', () => {
  assert.deepEqual(evaluateGate({ gate: 'tiered', freeAllowed: true, entitlement: NONE }), { tier: 'free' });
  assert.deepEqual(evaluateGate({ gate: 'tiered', freeAllowed: true, entitlement: null }), { tier: 'free' });
});

test('tiered_freeDeniedWhenNotAllowed', () => {
  for (const freeAllowed of [false, undefined, null, 'true', 1]) {
    assert.deepEqual(evaluateGate({ gate: 'tiered', freeAllowed, entitlement: NONE }), { denied: 'vip-required' });
  }
});

test('tiered_sandboxIsFreeTier', () => {
  const sb = ent({ environment: 'Sandbox' });
  assert.deepEqual(evaluateGate({ gate: 'tiered', freeAllowed: true, entitlement: sb }), { tier: 'free' });
  assert.deepEqual(evaluateGate({ gate: 'tiered', freeAllowed: false, entitlement: sb }), { denied: 'vip-required' });
});

test('unknownGate_throws', () => {
  for (const gate of ['nope', undefined, null, '', 'VIP']) {
    assert.throws(() => evaluateGate({ gate, freeAllowed: true, entitlement: ent() }), Error);
  }
});

test('table_classifiesTenCallables', () => {
  assert.equal(Object.keys(GATES).length, 10);
  for (const n of ['acceptCoupleInvite', 'requestAccountDeletion', 'getAccountToken', 'claimPurchase', 'getEntitlement', 'signAvatarUpload']) {
    assert.equal(GATES[n].gate, 'free', n);
  }
  for (const n of ['signMemoryUpload', 'confirmMemoryUpload', 'getMemoryMediaUrl', 'generateGiftAdvice']) {
    assert.equal(GATES[n].gate, 'tiered', n);
  }
  assert.equal(GATES.generateGiftAdvice.freeAllowed, true);
});

test('table_mediaAreTieredWithFreeAllowedTrue', () => {
  assert.equal(MEDIA_FREE_ALLOWED, true);
  for (const n of ['signMemoryUpload', 'confirmMemoryUpload', 'getMemoryMediaUrl']) {
    assert.equal(GATES[n].freeAllowed, MEDIA_FREE_ALLOWED, n);
  }
});

test('table_isFrozen', () => {
  assert.ok(Object.isFrozen(GATES));
  for (const g of Object.values(GATES)) assert.ok(Object.isFrozen(g));
  assert.throws(() => { GATES.newCallable = { gate: 'free' }; }, TypeError);
  assert.throws(() => { GATES.signMemoryUpload.freeAllowed = false; }, TypeError);
});
