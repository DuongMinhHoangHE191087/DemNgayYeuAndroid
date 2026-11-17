'use strict';

// Xử lý RTDN của Google Play qua Pub/Sub (p6 M-20). Server là nguồn sự thật, mặc định TỪ CHỐI:
// nội dung thông báo CHỈ là gợi ý (loại + purchaseToken), trạng thái luôn tra lại store bằng fetchNormalized; không bao giờ tin trường nào trong payload.
// Chỉ lỗi tạm của store và lỗi ack được ném lại (Pub/Sub gửi lại); thông báo hỏng/lạ/của gói khác thì trả bình thường để không gửi lại vô hạn.
const { ackIfPending } = require('./ackIfPending');
const { applyStoreState } = require('./applyStoreState');
const { fetchNormalized } = require('./fetchNormalized');
const { googleKey } = require('./normalizeGoogle');

const SUBSCRIPTION_REVOKED = 12;
const VOIDED_SUBSCRIPTION = 1; // voidedPurchaseNotification.productType: 1 = đăng ký, 2 = mua một lần
const VOIDED_ONE_TIME = 2;
// Các trường NormalizedPurchase (§4.10) lấy từ bản đã lưu; uid, boundAtMs, supersededBy, tombstone... không bao giờ đi theo.
const PURCHASE_FIELDS = [
  'store', 'key', 'lookupId', 'productId', 'planId', 'kind', 'environment', 'accountToken', 'ownership', 'acknowledged', 'status',
  'expiresAtMs', 'graceExpiresAtMs', 'revokedAtMs', 'asOfMs', 'predecessorLookupId', 'latestTransactionId', 'revokedTransactionId',
  'purchaseTimeMs',
];

const ignored = (reason) => ({ ignored: reason });
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const str = (v) => (typeof v === 'string' && v !== '' ? v : null);

// base64 -> JSON object; hỏng thì null.
function decode(data) {
  if (typeof data !== 'string' || data === '') return null;
  try {
    const body = JSON.parse(Buffer.from(data, 'base64').toString('utf8'));
    return isObj(body) ? body : null;
  } catch {
    return null;
  }
}

// Từ payload suy ra { token, kind, revoke } (kind: gợi ý cho fetchNormalized, undefined = thử cả hai); loại lạ thì null.
function classify(body) {
  const sub = body.subscriptionNotification;
  if (isObj(sub)) return { token: str(sub.purchaseToken), kind: 'subscription', revoke: sub.notificationType === SUBSCRIPTION_REVOKED };
  const one = body.oneTimeProductNotification;
  if (isObj(one)) return { token: str(one.purchaseToken), kind: 'product', revoke: false };
  const voided = body.voidedPurchaseNotification;
  if (isObj(voided)) {
    const kind = voided.productType === VOIDED_SUBSCRIPTION ? 'subscription' : (voided.productType === VOIDED_ONE_TIME ? 'product' : undefined);
    return { token: str(voided.purchaseToken), kind, revoke: true };
  }
  return null;
}

// Bản đã lưu của purchase (chỉ các trường §4.10), null nếu chưa biết.
async function loadStored(deps, key) {
  const snap = await deps.db.collection('purchases').doc(key).get();
  if (!snap.exists) return null;
  const doc = snap.data();
  const p = {};
  for (const f of PURCHASE_FIELDS) p[f] = doc[f] === undefined ? null : doc[f];
  return p;
}

async function handleGoogleNotification(deps, { data, messageId } = {}) {
  // Pub/Sub luôn có messageId; thiếu thì khoá dedupe sẽ thành `google_undefined` và nuốt mọi thông báo sau, nên bỏ qua (ack) thay vì áp dụng.
  if (str(messageId) === null) return ignored('bad-message-id');
  const body = decode(data);
  if (!body) return ignored('bad-payload');
  if (isObj(body.testNotification)) return ignored('test');
  // Phản hồi của store không có packageName nên phải kiểm ở đây.
  if (body.packageName !== deps.packageName) return ignored('foreign-package');
  const event = classify(body);
  if (!event || event.token === null) return ignored('unknown-notification');
  const key = googleKey(event.token);

  // Lỗi tạm của store ném lại nguyên để retry.
  const fetched = await fetchNormalized(deps, { store: 'google', id: event.token, kind: event.kind });
  let purchase = fetched.purchase ?? null;
  if (event.revoke) {
    // Thu hồi: ép revoked kể cả khi store trả active/null. Purchase chưa biết mà store cũng không có thì bỏ qua.
    const known = await loadStored(deps, key);
    purchase ??= known;
    if (!purchase) return ignored('unknown-purchase');
    const eventMs = Number(body.eventTimeMillis);
    // asOfMs phải mới hơn bản đã lưu, không thì applyStoreState coi là stale và bỏ lỡ lần thu hồi (thu hồi Google dính mãi nên không rủi ro).
    const asOfMs = Math.max(purchase.asOfMs, deps.nowMs(), (known?.asOfMs ?? -Infinity) + 1);
    purchase = { ...purchase, status: 'revoked', revokedAtMs: Number.isFinite(eventMs) ? eventMs : deps.nowMs(), asOfMs };
  }
  if (!purchase) return ignored('not-ours');

  // Dấu dedupe nằm cùng transaction với bản ghi (applyStoreState): commit hỏng thì lần gửi lại vẫn được áp dụng.
  const res = await applyStoreState(deps, { purchase, claimerUid: null, dedupeKey: `google_${messageId}` });
  // Luôn ack (kể cả duplicate): lần trước có thể đã commit rồi mới lỗi ack. force bỏ lịch lùi và ném lại lỗi để Pub/Sub gửi lại.
  await ackIfPending(deps, { key: purchase.key, force: true });
  return res;
}

module.exports = { handleGoogleNotification };
