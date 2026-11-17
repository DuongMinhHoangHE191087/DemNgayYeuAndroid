#!/usr/bin/env node
'use strict';

// Chuyển một purchase đã ràng buộc (hoặc đã có tombstone) sang uid khác, cho hỗ trợ khi người dùng hợp pháp mất tài khoản (p6 M-89).
//
//   node scripts/rebind-purchase.js --key google_abc --to-uid UID --evidence "Đơn GPA.1234 khớp Play Console" --operator ten.nguoi.duyet
//   node scripts/rebind-purchase.js ... --apply     # ghi thật; mặc định là dry run: chỉ đọc và in kế hoạch
//
// Chỉ nhận purchase Production; tài khoản đích phải tồn tại; bindLimits được kiểm lại; mọi thứ trong một transaction có ghi entitlementAudit.
// Đọc cấu hình từ process.env: FIREBASE_PROJECT_ID. Quyền Firebase Admin: GOOGLE_APPLICATION_CREDENTIALS hoặc gcloud auth application-default login.
// Không in token, mã mua hàng thô hay khoá: chỉ in key, uid, người thao tác và kết quả.
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { rebindPurchase } = require('../src/entitlement/rebindPurchase');
const { DEFAULT_POLICY } = require('../src/entitlement/resolveEntitlement');

const FLAGS = { '--key': 'key', '--to-uid': 'toUid', '--evidence': 'evidence', '--operator': 'operator' };

// Hàm thuần: argv (không gồm node và đường dẫn script) -> { key, toUid, evidence, operator, apply }. Ném lỗi khi thiếu hoặc lạ.
function parseArgs(argv) {
  const out = { key: null, toUid: null, evidence: null, operator: null, apply: false };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--apply') { out.apply = true; continue; }
    if (!Object.hasOwn(FLAGS, arg)) throw new Error(`Tham số lạ: ${arg}`);
    const value = argv[i + 1];
    if (typeof value !== 'string' || value.startsWith('--') || value.trim() === '') throw new Error(`${arg} cần một giá trị`);
    out[FLAGS[arg]] = value;
    i += 1;
  }
  for (const [flag, name] of Object.entries(FLAGS)) if (out[name] === null) throw new Error(`Thiếu ${flag}`);
  return out;
}

async function main() {
  const { apply, ...args } = parseArgs(process.argv.slice(2));
  const projectId = process.env.FIREBASE_PROJECT_ID;
  if (!projectId) throw new Error('Thiếu biến môi trường FIREBASE_PROJECT_ID');
  if (getApps().length === 0) initializeApp({ projectId });
  const deps = { db: getFirestore(), nowMs: Date.now, policy: DEFAULT_POLICY };
  console.log(`${apply ? 'APPLY' : 'DRY RUN'}: dự án ${projectId}, key ${args.key} -> uid ${args.toUid}, người thao tác ${args.operator}`);
  const res = await rebindPurchase(deps, { ...args, dryRun: !apply });
  const { entitlement, ...rest } = res; // entitlement không cần in
  console.log(JSON.stringify({ ...rest, vip: entitlement?.vip }));
  if (!res.ok) process.exitCode = 1;
  if (!apply && res.ok) console.log('Chưa ghi gì. Chạy lại với --apply sau khi chủ dự án duyệt.');
}

module.exports = { parseArgs };

if (require.main === module) {
  main().catch((err) => {
    console.error(`Dừng: ${err.message}`);
    process.exitCode = 1;
  });
}
