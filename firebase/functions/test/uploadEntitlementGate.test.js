'use strict';

// V-13: bằng chứng cổng quyền ở server cho việc KÝ upload ảnh (signMemoryUpload). Nạp index.js THẬT trong `vm`,
// nối gateFor THẬT (runtime.gateFor) + media.reserveUpload THẬT + policy THẬT; chỉ giả Firestore (fakeFirestore), kho đối tượng R2 và rate limit.
// Ba ca: (1) VIP-only + không quyền/hết hạn => từ chối TRƯỚC khi ký; (2) có quyền => ký, hạn mức theo bậc; (3) mặc định freeAllowed=true => free được ký tới hạn mức uid.
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('../src/config');
const policy = require('../src/policy');
const media = require('../src/media');
const runtime = require('../src/entitlement/runtime');
const { GATES, MEDIA_FREE_ALLOWED } = require('../src/entitlement/gatePolicy');
const { makeDeps } = require('./entitlement/depsBuilder');
const { BASE_NOW_MS, HOUR, makeStoredPurchase } = require('./entitlement/builders');

const INDEX_SRC = fs.readFileSync(path.join(__dirname, '..', 'index.js'), 'utf8');
const REL = 'rel-1';
// Chỉ signMemoryUpload là VIP-only (hai callable còn lại giữ cổng mặc định) để bắt lỗi nối nhầm tên callable vào gateFor.
const VIP_ONLY = { ...GATES, signMemoryUpload: { gate: 'tiered', freeAllowed: false } };
const SIGNED_HEADERS = { 'content-type': 'image/jpeg', 'content-length': '1000', 'if-none-match': '*' };

// deps (db giả + đồng hồ + store giả) dùng chung cho gateFor và cho index.js (getFirestore trả đúng db đó).
function load({ gates = GATES, deps = makeDeps() } = {}) {
  const signCalls = [];
  const cloudFake = {
    signUpload: (args) => { signCalls.push(args); return { uploadUrl: 'https://upload.test', headers: SIGNED_HEADERS }; },
  };
  const secret = (name) => ({ name, value: () => `v-${name}` });
  const modules = {
    'firebase-admin/app': { initializeApp: () => {}, getApps: () => [] },
    'firebase-admin/firestore': { getFirestore: () => deps.db },
    'firebase-admin/auth': { getAuth: () => ({}) },
    'firebase-functions/v2/https': { onCall: (opts, handler) => Object.assign(handler, { opts }), HttpsError },
    'firebase-functions/v2/firestore': { onDocumentWritten: (opts, handler) => handler },
    'firebase-functions/v2/scheduler': { onSchedule: (opts, handler) => handler },
    'firebase-functions/params': { defineSecret: secret, defineString: secret },
    'firebase-functions/logger': { info() {} },
    './src/config': cfg,
    './src/policy': policy,
    './src/media': media,
    './src/pairing': {},
    './src/accountDeletion': {},
    './src/avatar': {},
    './src/aiAdvisor': {},
    './src/objectStore': { createObjectStore: () => cloudFake },
    './src/entitlement': {},
    './src/entitlement/options': { STORE_SECRETS: [secret('STORE_SECRET')] },
    './src/entitlement/runtime': { gateFor: (name, uid) => runtime.gateFor(name, uid, deps, gates) },
    './src/rateLimit': { enforce: async () => {} }, // rate limit đã có test riêng; ở đây không làm nhiễu hạn mức uid
  };
  const fakeRequire = (id) => {
    if (!(id in modules)) throw new Error(`uploadEntitlementGate test: require chưa được giả: ${id}`);
    return modules[id];
  };
  const exp = {};
  vm.runInContext(`(function (exports, require, module) {${INDEX_SRC}\n})`, vm.createContext({}))(exp, fakeRequire, { exports: exp });
  return { sign: exp.signMemoryUpload, signCalls, deps };
}

// Request ký một ảnh JPEG hợp lệ; mỗi lần dùng memoryId mới (đã có bản ghi cho memoryId thì bị từ chối).
const request = (uid) => ({
  auth: { uid },
  data: { relationshipId: REL, memoryId: crypto.randomUUID(), kind: 'IMAGE', sizeBytes: 1000, mimeType: 'image/jpeg' },
});

const seedRelationship = (deps, uid) => deps.db.collection('relationships').doc(REL).set({ partnerAId: uid, status: 'ACTIVE' });

// Quyền VIP hợp lệ của uid-1 (cùng cách gateFor_looksUpTheCallersOwnPurchases); overrides đổi sang hết hạn.
async function seedPurchase(deps, overrides = {}) {
  const p = makeStoredPurchase({
    asOfMs: BASE_NOW_MS - HOUR, verifiedAtMs: BASE_NOW_MS - HOUR, lastCheckedAtMs: BASE_NOW_MS - HOUR, ackState: 'done', ackAttempts: null, ackNextAtMs: null,
    ...overrides,
  });
  await deps.db.collection('purchases').doc(p.key).set(p);
  await deps.db.collection('entitlementAccounts').doc(p.uid).set({ purchaseKeys: [p.key] });
  return p.uid;
}

const isVipRequired = (e) => e.code === 'permission-denied' && e.message === 'vip-required' && e.details?.reason === 'vip-required';

// Ký tối đa `n` lần cho uid; trả số lần được ký trước khi gặp lỗi đầu tiên (hoặc n) cùng lỗi đó.
async function signUntilDenied(sign, uid, n) {
  let signed = 0;
  for (; signed < n; signed += 1) {
    try {
      await sign(request(uid));
    } catch (error) {
      return { signed, error };
    }
  }
  return { signed, error: null };
}

test('signMemoryUpload_vipOnlyAndNoEntitlement_deniedBeforeSigning', async () => {
  const { sign, signCalls, deps } = load({ gates: VIP_ONLY });
  await seedRelationship(deps, 'uid-1');
  const req = request('uid-1');
  await assert.rejects(sign(req), isVipRequired);
  assert.equal(signCalls.length, 0, 'Kho đối tượng bị gọi ký dù người dùng không có quyền');
  assert.equal(deps.db.read(`mediaAssets/${req.data.memoryId}`), undefined, 'đã giữ chỗ phiên upload dù bị từ chối');
  assert.equal(deps.db.read(`mediaQuota/user_uid-1_${policy.dayKey(Date.now())}`), undefined, 'đã tính hạn mức dù bị từ chối');
});

test('signMemoryUpload_vipOnlyAndExpiredEntitlement_deniedBeforeSigning', async () => {
  const deps = makeDeps();
  const uid = await seedPurchase(deps, { status: 'expired', expiresAtMs: BASE_NOW_MS - 2 * HOUR });
  const { sign, signCalls } = load({ gates: VIP_ONLY, deps });
  await seedRelationship(deps, uid);
  await assert.rejects(sign(request(uid)), isVipRequired);
  assert.equal(signCalls.length, 0, 'Kho đối tượng bị gọi ký dù quyền đã hết hạn');
});

test('signMemoryUpload_vipOnlyAndValidEntitlement_signedWithVipLimit', async () => {
  const deps = makeDeps();
  const uid = await seedPurchase(deps);
  const { sign, signCalls } = load({ gates: VIP_ONLY, deps });
  await seedRelationship(deps, uid);
  const req = request(uid);
  const res = await sign(req);
  assert.equal(signCalls.length, 1);
  assert.deepEqual({ ...res.headers }, SIGNED_HEADERS);
  assert.equal(res.fields, undefined);
  assert.equal(signCalls[0].contentType, 'image/jpeg');
  assert.equal(signCalls[0].contentLength, 1000);
  assert.equal(signCalls[0].expiresInSeconds, 7200);
  assert.equal(res.uploadUrl, 'https://upload.test');
  assert.equal(res.publicId, policy.publicIdFor(req.data.memoryId));
  // Hạn mức theo bậc vip (đọc từ policy.limitsFor, không bịa số): ký đủ hạn mức rồi lượt kế bị chặn bằng resource-exhausted.
  const vipMax = policy.limitsFor('vip').userDailyUploads;
  const { signed, error } = await signUntilDenied(sign, uid, vipMax + 5);
  assert.equal(signed, vipMax - 1, 'đã dùng 1 lượt ở trên');
  assert.equal(error?.code, 'resource-exhausted');
  assert.equal(signCalls.length, vipMax, 'lượt vượt hạn mức không được ký');
});

test('signMemoryUpload_defaultGatesFreeAllowed_freeUserSignedUntilUidLimit', async () => {
  assert.equal(MEDIA_FREE_ALLOWED, true, 'mặc định phải giữ hành vi Android: người dùng không VIP vẫn dùng media');
  const { sign, signCalls, deps } = load(); // GATES thật, không quyền
  await seedRelationship(deps, 'uid-1');
  const freeMax = policy.limitsFor('free').userDailyUploads;
  const { signed, error } = await signUntilDenied(sign, 'uid-1', freeMax + 5);
  assert.equal(signed, freeMax);
  assert.equal(error?.code, 'resource-exhausted');
  assert.equal(signCalls.length, freeMax, 'lượt vượt hạn mức uid không được ký');
});
