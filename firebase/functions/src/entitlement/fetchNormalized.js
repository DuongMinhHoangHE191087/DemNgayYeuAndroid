'use strict';

// Hỏi store rồi chuẩn hoá thành NormalizedPurchase (p6 M-100). Trả { purchase } hoặc { notOurs: true, reason }; lỗi `unavailable` của store ném lại nguyên.
// BẢO MẬT: asOfMs KHÔNG BAO GIỜ là lúc phản hồi về (phản hồi chậm sau một REFUND sẽ thành "mới hơn" và phục hồi VIP chưa trả tiền).
// preRequestMs đọc TRƯỚC khi gọi store; Apple dùng signedDate đã xác minh của ảnh chụp, Google dùng preRequestMs.
const { HttpsError } = require('firebase-functions/v2/https');
const { normalizeApple } = require('./normalizeApple');
const { normalizeGoogle } = require('./normalizeGoogle');

const NOT_FOUND = { notOurs: true, reason: 'not-found' };
const wrap = (n) => (n.notOurs ? n : { purchase: n });
const isUnavailable = (e) => e?.code === 'unavailable';

async function fetchApple(deps, id, preRequestMs) {
  const state = await deps.apple.fetchState(id);
  if (!state) return NOT_FOUND;
  // Ảnh chụp mới nhất mà store đã ký (giao dịch hoặc renewalInfo); thiếu cả hai thì preRequestMs.
  const signed = [state.transaction?.signedDate, state.renewalInfo?.signedDate].filter(Number.isFinite);
  const asOfMs = signed.length > 0 ? Math.max(...signed) : preRequestMs;
  return wrap(normalizeApple({ ...state, bundleId: deps.bundleId, asOfMs }));
}

// Trả { subscription, product } (cả hai có thể null) hoặc ném lỗi.
async function lookupGoogle(deps, id, kind) {
  if (kind === 'subscription') return { subscription: await deps.google.getSubscription(id), product: null };
  if (kind === 'product') return { subscription: null, product: await deps.google.getProduct(id) };
  let subscription = null;
  let firstError = null;
  try {
    subscription = await deps.google.getSubscription(id);
  } catch (e) {
    if (!isUnavailable(e)) throw e;
    firstError = e; // token sai loại cũng có thể ra unavailable (UNVERIFIED #115): vẫn thử product
  }
  if (subscription) return { subscription, product: null };
  const product = await deps.google.getProduct(id);
  if (product == null && firstError) throw firstError;
  return { subscription: null, product };
}

async function fetchGoogle(deps, id, kind, preRequestMs) {
  const { subscription, product } = await lookupGoogle(deps, id, kind);
  if (subscription == null && product == null) return NOT_FOUND;
  return wrap(normalizeGoogle({ purchaseToken: id, subscription, product, asOfMs: preRequestMs }));
}

async function fetchNormalized(deps, { store, id, kind } = {}) {
  if (store !== 'apple' && store !== 'google') throw new HttpsError('invalid-argument', 'unknown store');
  const preRequestMs = deps.nowMs();
  return store === 'apple' ? fetchApple(deps, id, preRequestMs) : fetchGoogle(deps, id, kind, preRequestMs);
}

module.exports = { fetchNormalized };
