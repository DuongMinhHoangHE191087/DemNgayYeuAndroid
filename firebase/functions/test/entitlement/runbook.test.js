'use strict';

// Runbook cài đặt store-server (p6 M-27). Danh sách tên tham số lấy THẬT từ options.js, không hard-code:
// thêm hoặc bớt tham số mà runbook chưa cập nhật thì test này đỏ.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

const OPTIONS = path.join(__dirname, '..', '..', 'src', 'entitlement', 'options.js');
const CONFIG = path.join(__dirname, '..', '..', 'src', 'config.js');
const RUNBOOK = path.join(__dirname, '..', '..', '..', '..', 'docs', 'monetization', 'store-server-setup.md');

const DEFINE_CALL = /define(?:Secret|String)\(/g;
const DEFINE_NAME = /define(?:Secret|String)\(\s*['"]([A-Z0-9_]+)['"]/g;

// Từ khoá các bước bắt buộc của M-27: thiếu từ nào là runbook thiếu bước đó.
const KEYWORDS = [
  'ASSN', 'RTDN', 'Sandbox', 'App Check', 'appleNotifications', 'play-rtdn', 'sdk_kill_ios',
  '{"ads":true}', '{"ads":false}', 'ENTITLEMENT_SIGNING_KEY', 'EntitlementPublicKeys', 'make-signing-key',
  'LEGACY_CLAIM_UNTIL_MS', 'ACCOUNT_TOKEN_CUTOVER_MS', 'rebindPurchase', 'rebind-purchase.js', 'claim-denied',
];

const readOptions = () => fs.readFileSync(OPTIONS, 'utf8');
const paramNames = () => [...readOptions().matchAll(DEFINE_NAME)].map((m) => m[1]);
const runbook = () => fs.readFileSync(RUNBOOK, 'utf8');

test('options.js: regex lấy tên không sót lệnh define nào', () => {
  const names = paramNames();
  assert.ok(names.length > 0, 'không đọc được tham số nào từ options.js');
  assert.equal(names.length, (readOptions().match(DEFINE_CALL) ?? []).length);
});

test('runbook nhắc tới mọi tham số trong options.js (xoá một tên là đỏ)', () => {
  const doc = runbook();
  const missing = paramNames().filter((n) => !new RegExp(`(?<![A-Za-z0-9_])${n}(?![A-Za-z0-9_])`).test(doc));
  assert.deepEqual(missing, []);
});

test('runbook có đủ từ khoá các bước bắt buộc', () => {
  const doc = runbook();
  assert.deepEqual(KEYWORDS.filter((k) => !doc.includes(k)), []);
});

// Topic và region là giá trị thật trong code: runbook ghi sai thì RTDN im lặng không tới hàm, nên khớp chính xác (không chỉ chứa).
test('runbook ghi đúng topic RTDN và region của code', () => {
  const doc = runbook();
  const topic = readOptions().match(/topic:\s*'([^']+)'/)?.[1];
  const region = fs.readFileSync(CONFIG, 'utf8').match(/region:\s*'([^']+)'/)?.[1];
  assert.ok(topic && region, 'không đọc được topic hoặc region từ code');
  assert.ok(doc.includes(`projects/demngayyeuandroid/topics/${topic}\``), `thiếu tên topic đầy đủ: ${topic}`);
  assert.ok(doc.includes(`--region=${region}`), `thiếu region: ${region}`);
  assert.ok(doc.includes(`gcloud pubsub topics create ${topic} `), `thiếu lệnh tạo topic: ${topic}`);
});
