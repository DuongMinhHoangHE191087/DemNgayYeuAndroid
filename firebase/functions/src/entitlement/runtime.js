'use strict';

// Phần chạy thật của entitlement (p6 M-23): requireUid/limit (cùng dạng index.js) và makeRealDeps() dựng `deps` cho các handler.
// BẢO MẬT: mọi tham số/secret chỉ được đọc LƯỜI qua getter (hàm không bind secret như getAccountToken không bao giờ chạm tới), có bộ nhớ đệm
// theo instance; secret đổi thì deploy lại. Cấu hình sai (mốc rỗng/hỏng) thì thành null = đóng đường legacy (mặc định từ chối).
const crypto = require('node:crypto');
const { getFirestore } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { HttpsError } = require('firebase-functions/v2/https');
const rateLimit = require('../rateLimit');
const opt = require('./options');
const { createAppleStore } = require('./apple');
const { createGoogleStore } = require('./google');
const { parseSigningKey } = require('./entitlementToken');
const { DEFAULT_POLICY } = require('./resolveEntitlement');
const { appleRootCertificates } = require('./appleRootCertificates');

// Giới hạn tần suất theo uid; gọi ngay sau requireUid, trước mọi logic nghiệp vụ. Vượt giới hạn: resource-exhausted.
const limit = (name, uid) => rateLimit.enforce({ db: getFirestore(), uid, name, nowMs: Date.now() });

// Khách (chưa đăng nhập) không có uid: từ chối trước khi chạm dữ liệu.
function requireUid(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Cần đăng nhập để dùng tính năng mua hàng');
  return uid;
}

// Chỉ số nguyên dương an toàn (mili giây); rỗng, chữ, âm, số thập phân, 0, quá lớn đều null.
function parseMs(raw) {
  const s = String(raw ?? '').trim();
  if (!/^\d+$/.test(s)) return null;
  const n = Number(s);
  return Number.isSafeInteger(n) && n > 0 ? n : null;
}

// Thời điểm tạo tài khoản Firebase Auth; mọi lỗi/giá trị lạ trả null (applyStoreState coi null là không đủ bằng chứng legacy).
async function authCreatedMs(uid) {
  try {
    const ms = Date.parse((await getAuth().getUser(uid)).metadata?.creationTime);
    return Number.isFinite(ms) ? ms : null;
  } catch {
    return null;
  }
}

// Getter tính một lần khi dùng lần đầu rồi nhớ; ném lỗi thì KHÔNG nhớ (lần sau thử lại, không bao giờ trả giá trị nửa vời).
function lazyGetters(deps, factories) {
  for (const [name, make] of Object.entries(factories)) {
    let done = false;
    let value;
    Object.defineProperty(deps, name, {
      enumerable: true,
      get() {
        if (!done) {
          value = make();
          done = true;
        }
        return value;
      },
    });
  }
  return deps;
}

let cached = null;

function makeRealDeps() {
  cached ??= lazyGetters({
    nowMs: () => Date.now(),
    randomUUID: () => crypto.randomUUID(),
    authCreatedMs,
    policy: DEFAULT_POLICY,
  }, {
    db: () => getFirestore(),
    bundleId: () => opt.APPLE_BUNDLE_ID.value(),
    packageName: () => opt.GOOGLE_PACKAGE_NAME.value(),
    legacyClaimUntilMs: () => parseMs(opt.LEGACY_CLAIM_UNTIL_MS.value()),
    accountTokenCutoverMs: () => parseMs(opt.ACCOUNT_TOKEN_CUTOVER_MS.value()),
    signingKey: () => parseSigningKey(opt.ENTITLEMENT_SIGNING_KEY.value()),
    apple: () => {
      const appAppleId = Number(opt.APPLE_APP_APPLE_ID.value());
      return createAppleStore({
        bundleId: opt.APPLE_BUNDLE_ID.value(),
        appAppleId: Number.isSafeInteger(appAppleId) && appAppleId > 0 ? appAppleId : undefined, // thiếu thì createAppleStore ném lỗi, không đoán
        keyId: opt.APPLE_IAP_KEY_ID.value(),
        issuerId: opt.APPLE_IAP_ISSUER_ID.value(),
        privateKey: opt.APPLE_IAP_PRIVATE_KEY.value(),
        rootCertificates: appleRootCertificates(),
      });
    },
    google: () => createGoogleStore({ packageName: opt.GOOGLE_PACKAGE_NAME.value() }),
  });
  return cached;
}

// Cổng quyền cho một callable (p6 M-84): gọi ngay sau limit. Trả { tier } hoặc ném permission-denied 'vip-required' (enforceGate);
// callable chưa phân loại trong GATES thì ném `internal` (lỗi cấu hình, không lộ chi tiết ra client).
// ponytail: require lười để wiring.test.js (vm, fakeRequire chỉ giả module cũ) vẫn nạp được runtime.js; đưa lên đầu file khi test đó giả thêm hai module này.
async function gateFor(name, uid, deps = makeRealDeps(), gates = require('./gatePolicy').GATES) {
  const g = Object.hasOwn(gates, name) ? gates[name] : null;
  if (!g) throw new HttpsError('internal', 'Gate chưa được cấu hình');
  return require('./requireEntitlement').enforceGate(deps, { uid, gate: g.gate, freeAllowed: g.freeAllowed });
}

module.exports = { requireUid, limit, makeRealDeps, gateFor };
