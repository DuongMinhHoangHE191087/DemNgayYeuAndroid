'use strict';

const lib = require('@apple/app-store-server-library');

const { APIException, VerificationException, VerificationStatus, Environment } = lib;
// Chỉ Production và Sandbox: XCODE / LOCAL_TESTING làm thư viện bỏ qua kiểm tra chuỗi chứng chỉ và chữ ký, không bao giờ tạo verifier cho chúng.
const ENVIRONMENTS = Object.freeze([Environment.PRODUCTION, Environment.SANDBOX]);
const AUTO_RENEWABLE = 'Auto-Renewable Subscription';

const fail = (code, message) => Object.assign(new Error(message), { code });
const unavailable = (message) => fail('unavailable', message);
const unverified = () => fail('unverified', 'unverified');

// 404 / 400 từ getTransactionInfo nghĩa là "không có ở môi trường này"; mọi lỗi khác (401, 429, 5xx, mạng) là unavailable.
function isNotFound(e) {
  return e instanceof APIException && (e.httpStatusCode === 404 || e.httpStatusCode === 400);
}

function callFailure(e) {
  if (e instanceof APIException) return unavailable(`apple http ${e.httpStatusCode}`);
  return unavailable(`apple request failed: ${e && e.message}`);
}

// Lỗi verifier: RETRYABLE (OCSP, mạng) là tạm thời; mọi VerificationException khác là chữ ký/chuỗi/ứng dụng sai. Lỗi lạ coi như tạm thời, không bao giờ coi là đã xác minh.
function verifyFailure(e) {
  if (e instanceof VerificationException) {
    return e.status === VerificationStatus.RETRYABLE_VERIFICATION_FAILURE ? unavailable('apple verification retryable') : unverified();
  }
  return unavailable(`apple verifier failed: ${e && e.message}`);
}

async function verified(fn) {
  try {
    return await fn();
  } catch (e) {
    throw verifyFailure(e);
  }
}

function defaultClientFactory({ privateKey, keyId, issuerId, bundleId }) {
  return (environment) => new lib.AppStoreServerAPIClient(privateKey, keyId, issuerId, bundleId, environment);
}

function defaultVerifierFactory({ rootCertificates, enableOnlineChecks = true, bundleId, appAppleId, SignedDataVerifier = lib.SignedDataVerifier }) {
  return (environment) => new SignedDataVerifier(rootCertificates, enableOnlineChecks, environment, bundleId, appAppleId);
}

function createAppleStore({ bundleId, appAppleId, keyId, issuerId, privateKey, rootCertificates, enableOnlineChecks = true, clientFactory, verifierFactory }) {
  if (!bundleId) throw new Error('bundleId is required');
  if (!verifierFactory && !appAppleId) throw new Error('appAppleId is required for the Production verifier');
  const makeClient = clientFactory || defaultClientFactory({ privateKey, keyId, issuerId, bundleId });
  const makeVerifier = verifierFactory || defaultVerifierFactory({ rootCertificates, enableOnlineChecks, bundleId, appAppleId });

  async function lookup(environment, transactionId) {
    const client = makeClient(environment);
    let response;
    try {
      response = await client.getTransactionInfo(transactionId);
    } catch (e) {
      if (isNotFound(e)) return null;
      throw callFailure(e);
    }
    if (!response || !response.signedTransactionInfo) throw unavailable('apple empty transaction response');
    const verifier = makeVerifier(environment);
    const transaction = await verified(() => verifier.verifyAndDecodeTransaction(response.signedTransactionInfo));
    if (transaction.type !== AUTO_RENEWABLE) return { environment, transaction, renewalInfo: null, status: null };

    let statuses;
    try {
      statuses = await client.getAllSubscriptionStatuses(transactionId);
    } catch (e) {
      if (e instanceof APIException && e.httpStatusCode === 404) return { environment, transaction, renewalInfo: null, status: null };
      throw callFailure(e);
    }
    const items = ((statuses && statuses.data) || []).flatMap((group) => (group && group.lastTransactions) || []);
    const item = items.find((i) => i && i.originalTransactionId === transaction.originalTransactionId);
    if (!item || !item.signedTransactionInfo) return { environment, transaction, renewalInfo: null, status: null };
    const latest = await verified(() => verifier.verifyAndDecodeTransaction(item.signedTransactionInfo));
    // Mục khớp phải thật sự cùng chuỗi gia hạn theo dữ liệu đã ký, không chỉ theo trường chưa ký.
    if (latest.originalTransactionId !== transaction.originalTransactionId) throw unverified();
    const renewalInfo = item.signedRenewalInfo ? await verified(() => verifier.verifyAndDecodeRenewalInfo(item.signedRenewalInfo)) : null;
    return { environment, transaction: latest, renewalInfo, status: item.status ?? null };
  }

  async function fetchState(transactionId) {
    for (const environment of ENVIRONMENTS) {
      const result = await lookup(environment, transactionId);
      if (result) return result;
    }
    return null;
  }

  // Chỉ lỗi của chính verifyAndDecodeNotification mới được đổi môi trường hoặc coi là app khác; lỗi ở bước giải mã giao dịch luôn đi qua `verified`.
  async function verifyNotification(signedPayload) {
    for (const environment of ENVIRONMENTS) {
      const verifier = makeVerifier(environment);
      let payload;
      try {
        payload = await verifier.verifyAndDecodeNotification(signedPayload);
      } catch (e) {
        if (e instanceof VerificationException) {
          if (e.status === VerificationStatus.INVALID_APP_IDENTIFIER) return { foreign: true };
          if (e.status === VerificationStatus.INVALID_ENVIRONMENT && environment === Environment.PRODUCTION) continue;
        }
        throw verifyFailure(e);
      }
      const data = payload.data || null;
      const transaction = data && data.signedTransactionInfo ? await verified(() => verifier.verifyAndDecodeTransaction(data.signedTransactionInfo)) : null;
      const renewalInfo = data && data.signedRenewalInfo ? await verified(() => verifier.verifyAndDecodeRenewalInfo(data.signedRenewalInfo)) : null;
      return {
        notificationType: payload.notificationType,
        subtype: payload.subtype ?? null,
        notificationUUID: payload.notificationUUID,
        signedDateMs: payload.signedDate,
        environment,
        bundleId,
        status: (data && data.status) ?? null,
        transaction,
        renewalInfo,
      };
    }
    throw unverified();
  }
  return { fetchState, verifyNotification };
}

module.exports = { createAppleStore, defaultVerifierFactory };
