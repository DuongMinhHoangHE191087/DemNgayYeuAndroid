'use strict';

// Client REST Google Play Developer API (androidpublisher v3), chỉ phần xác minh và acknowledge mua hàng.
// Mọi lỗi tạm thời hoặc lỗi cấu hình server đều ném code 'unavailable' để người gọi KHÔNG cấp quyền; chỉ 404/410/400 mới là "không tìm thấy".
// Không import firebase-admin (DEC-166).
const API = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/';
const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const TIMEOUT_MS = 10000;
// 400 gồm token sai loại (trạng thái HTTP của token sai loại là UNVERIFIED #115): người gọi thử cả hai đường.
const NOT_FOUND = new Set([404, 410, 400]);

const unavailable = (msg) => Object.assign(new Error(msg), { code: 'unavailable' });

function defaultGetAccessToken() {
  const { GoogleAuth } = require('google-auth-library');
  const auth = new GoogleAuth({ scopes: [SCOPE] });
  return async () => auth.getAccessToken();
}

function createGoogleStore({ packageName, getAccessToken, fetchFn = fetch } = {}) {
  const tokenFn = getAccessToken || defaultGetAccessToken();
  const base = `${API}${encodeURIComponent(packageName)}/purchases/`;

  // Trả Response; mọi lỗi lấy token, mạng hoặc hết thời gian đều thành 'unavailable'.
  async function send(method, path) {
    let token;
    try {
      token = await tokenFn();
    } catch (e) {
      throw unavailable(`google access token failed: ${e && e.message}`);
    }
    if (!token) throw unavailable('google access token empty');
    try {
      return await fetchFn(base + path, {
        method,
        headers: { Authorization: `Bearer ${token}` },
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
    } catch (e) {
      throw unavailable(`google request failed: ${e && e.message}`);
    }
  }

  async function get(path) {
    const res = await send('GET', path);
    if (NOT_FOUND.has(res.status)) return null;
    if (res.status < 200 || res.status >= 300) throw unavailable(`google http ${res.status}`);
    try {
      return await res.json();
    } catch (e) {
      throw unavailable(`google response unreadable: ${e && e.message}`);
    }
  }

  async function acknowledge(path) {
    const res = await send('POST', path);
    if (res.status < 200 || res.status >= 300) throw unavailable(`google acknowledge http ${res.status}`);
  }

  const enc = encodeURIComponent;
  return {
    getSubscription: (purchaseToken) => get(`subscriptionsv2/tokens/${enc(purchaseToken)}`),
    getProduct: (purchaseToken) => get(`productsv2/tokens/${enc(purchaseToken)}`),
    acknowledgeSubscription: ({ productId, purchaseToken }) =>
      acknowledge(`subscriptions/${enc(productId)}/tokens/${enc(purchaseToken)}:acknowledge`),
    acknowledgeProduct: ({ productId, purchaseToken }) =>
      acknowledge(`products/${enc(productId)}/tokens/${enc(purchaseToken)}:acknowledge`),
  };
}

module.exports = { createGoogleStore };
