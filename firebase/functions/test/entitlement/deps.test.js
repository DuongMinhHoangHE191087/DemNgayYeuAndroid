'use strict';

// M-05: hai thư viện mới phải nạp được và xuất đúng API mà các task server sau dùng.
// require đặt trong từng test để mỗi test đỏ riêng khi chưa cài.
const test = require('node:test');
const assert = require('node:assert/strict');

test('appleLibrary_exportsVerifierClientAndEnvironment', () => {
  const apple = require('@apple/app-store-server-library');
  const names = ['SignedDataVerifier', 'AppStoreServerAPIClient', 'Environment', 'APIException', 'VerificationException', 'VerificationStatus'];
  for (const name of names) assert.notEqual(apple[name], undefined, `thiếu export ${name}`);
});

test('googleAuth_exportsGoogleAuth', () => {
  const { GoogleAuth } = require('google-auth-library');
  assert.equal(typeof GoogleAuth, 'function');
});
