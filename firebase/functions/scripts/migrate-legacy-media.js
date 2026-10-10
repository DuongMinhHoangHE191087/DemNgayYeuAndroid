#!/usr/bin/env node
'use strict';

// Chuyển memory cũ sang mô hình mới: tệp public trên Cloudinary -> riêng tư dưới inlove_mem_<memoryId>,
// có bản ghi mediaAssets active, và bỏ link public cũ khỏi memory. Ảnh preset của app không bị đụng tới.
//
//   node scripts/migrate-legacy-media.js             # dry run (mặc định): chỉ đọc, in kế hoạch, không ghi gì
//   node scripts/migrate-legacy-media.js --apply     # ghi thật; chỉ chạy khi chủ dự án đã duyệt
//   node scripts/migrate-legacy-media.js --limit 50  # chỉ xử lý 50 memory đầu (đợt nhỏ, tránh giới hạn tốc độ Admin API)
//
// Đọc cấu hình từ process.env, không đọc .env:
//   FIREBASE_PROJECT_ID, CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET, CLOUDINARY_TOKEN_KEY
//   CLOUDINARY_TOKEN_KEY không dùng để chuyển tệp; script yêu cầu nó để dùng chung một cấu hình đã kiểm tra với Functions.
//   Quyền Firebase Admin: GOOGLE_APPLICATION_CREDENTIALS, hoặc gcloud auth application-default login.
//   Dung lượng chuyển vào được cộng vào storage_<cặp đôi> và global_storage, nên trần toàn hệ thống vẫn được giữ.
// Không bao giờ xoá tệp. Không in đường dẫn tệp hay dữ liệu memory, chỉ in memoryId và kết quả.
// Chạy lại an toàn: mỗi bước kiểm tra trạng thái trước khi làm, và memory đổi giữa chừng thì bị bỏ qua (outcome "changed").
const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const cloudinarySdk = require('cloudinary');
const cfg = require('../src/config');
const policy = require('../src/policy');
const { createCloudinary } = require('../src/cloudinary');
const { planLegacyMemory, memoryPatch, parseLegacyUrl } = require('../src/legacy');
const { storageKey, GLOBAL_STORAGE_KEY } = require('../src/media');

const MEMORY_PATH = /^relationships\/([^/]+)\/memories\/([^/]+)$/;
// Các field mà migration đọc và có thể ghi. Nếu chúng đổi giữa lúc đọc và lúc ghi thì bỏ qua memory đó.
const LEGACY_FIELDS = ['mediaType', 'photoUri', 'videoUri', 'cloudinaryUrl', 'cloudinaryPublicId', 'isCloudinaryStored', 'authorId'];

class Changed extends Error {}

function requireEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Thiếu biến môi trường ${name}`);
  return value;
}

function parseLimit(argv) {
  const at = argv.indexOf('--limit');
  if (at < 0) return Infinity;
  const limit = Number(argv[at + 1]);
  if (!Number.isInteger(limit) || limit < 1) throw new Error('--limit phải là số nguyên dương');
  return limit;
}

// Ảnh preset của app là tài nguyên công khai dùng chung: không bao giờ chuyển.
async function loadPresetUrls(db) {
  const urls = new Set();
  for (const id of ['photos', 'avatars', 'wallpapers']) {
    const snap = await db.collection('preset_assets').doc(id).get();
    for (const url of snap.get('urls') ?? []) urls.add(url);
  }
  return urls;
}

// null nghĩa là xoá field. Giá trị khác được ghi nguyên.
const toUpdate = (patch) => Object.fromEntries(
  Object.entries(patch).map(([key, value]) => [key, value === null ? FieldValue.delete() : value]),
);

const fingerprint = (data) => JSON.stringify(LEGACY_FIELDS.map((field) => data[field] ?? null));

// Tệp cũ phải nằm trong giới hạn hiện tại. Vượt giới hạn thì báo để xử lý tay, không chuyển. Trả về lý do hoặc null.
function legacyProblem(resource, kind) {
  if (resource.resource_type !== policy.resourceTypeOf(kind)) return 'loại tài nguyên không khớp';
  if (!(resource.bytes > 0 && resource.bytes <= policy.maxBytesOf(kind))) return 'vượt giới hạn dung lượng';
  if (kind === 'VIDEO' && !(resource.duration <= cfg.limits.videoMaxSeconds + 1)) return 'video vượt giới hạn thời lượng';
  return null;
}

// Ghi memory trong transaction. Nếu memory đã đổi so với lúc đọc thì ném Changed, không ghi gì.
// asset (tuỳ chọn): tạo mediaAssets active và cộng dung lượng của cặp đôi và toàn hệ thống,
// chỉ khi bản ghi chưa có (chạy lại không cộng đôi).
async function writeMemory(db, ref, seen, patch, asset) {
  await db.runTransaction(async (tx) => {
    const current = await tx.get(ref);
    if (!current.exists) throw new Changed('memory đã bị xoá');
    if (fingerprint(current.data()) !== fingerprint(seen)) throw new Changed('memory vừa thay đổi, chạy lại sau');
    if (asset) {
      const assetRef = db.collection('mediaAssets').doc(ref.id);
      const existing = await tx.get(assetRef);
      if (!existing.exists) {
        tx.set(assetRef, asset);
        const increment = { bytes: FieldValue.increment(asset.bytes) };
        tx.set(db.collection('mediaQuota').doc(storageKey(asset.relationshipId)), increment, { merge: true });
        tx.set(db.collection('mediaQuota').doc(GLOBAL_STORAGE_KEY), increment, { merge: true });
      }
    }
    if (Object.keys(patch).length > 0) tx.update(ref, toUpdate(patch));
  });
}

// Quyết định và (nếu --apply) thực hiện cho một memory. Trả về { outcome, reason? }.
async function handleMemory({ db, cloud, cloudName, presetUrls, apply, nowMs, ref, relationshipId, memoryId, memory }) {
  const plan = planLegacyMemory({ memoryId, memory, cloudName, presetUrls });
  if (plan.action === 'skip') return { outcome: 'skip', reason: plan.reason };

  if (plan.action === 'keep' || plan.action === 'clear') {
    if (Object.keys(plan.patch).length === 0) return { outcome: 'unchanged' };
    if (apply) await writeMemory(db, ref, memory, plan.patch, null);
    return { outcome: plan.action === 'keep' ? 'cleaned' : 'cleared' };
  }

  const { from, to, kind, patch } = plan;
  let outcome = 'already-moved';
  let resource = await cloud.fetchResource(to, kind);
  if (!resource) {
    const source = await cloud.fetchResource(from.publicId, kind, 'upload');
    if (!source) {
      // Không còn tệp nào để chuyển: chỉ bỏ link cũ khỏi memory.
      const cleanup = memoryPatch({ memory, presetUrls, publicId: '' });
      if (apply && Object.keys(cleanup).length > 0) await writeMemory(db, ref, memory, cleanup, null);
      return { outcome: 'missing' };
    }
    const problem = legacyProblem(source, kind);
    if (problem) return { outcome: 'skip', reason: problem };
    if (!apply) return { outcome: 'would-migrate' };
    await cloud.moveToPrivate(from.publicId, to, kind);
    resource = await cloud.fetchResource(to, kind);
    if (!resource) throw new Error('đã đổi tên nhưng không thấy tệp đích');
    outcome = 'migrated';
  }
  if (!apply) return { outcome };

  const asset = {
    state: 'active',
    ownerUid: memory.authorId ?? null,
    relationshipId,
    kind,
    format: resource.format,
    publicId: to,
    declaredBytes: resource.bytes,
    bytes: resource.bytes,
    countedBytes: resource.bytes,
    confirmedAt: new Date(nowMs),
    orphanCheckAt: new Date(nowMs + cfg.timing.orphanAfterMs),
    migratedFrom: from.publicId,
  };
  await writeMemory(db, ref, memory, patch, asset);
  return { outcome };
}

async function main() {
  const apply = process.argv.includes('--apply');
  const limit = parseLimit(process.argv);
  const projectId = requireEnv('FIREBASE_PROJECT_ID');
  const cloudName = requireEnv('CLOUDINARY_CLOUD_NAME');
  const cloud = createCloudinary({
    sdk: cloudinarySdk,
    cloudName,
    apiKey: requireEnv('CLOUDINARY_API_KEY'),
    apiSecret: requireEnv('CLOUDINARY_API_SECRET'),
    tokenKey: requireEnv('CLOUDINARY_TOKEN_KEY'),
  });
  if (getApps().length === 0) initializeApp({ projectId });
  const db = getFirestore();
  const presetUrls = await loadPresetUrls(db);

  // ponytail: --limit cắt sau khi đã tải toàn bộ collection group nên vẫn tốn lượt đọc; chạy lại với cùng limit chỉ xét lại N mục đầu (mục đã chuyển trả về already-moved). Nâng cấp: truy vấn có thứ tự và startAfter để tiếp tục từ chỗ dừng.
  // Chỉ lấy memory nằm dưới relationships/{id}/memories; bỏ qua mọi collection khác cùng tên.
  const docs = (await db.collectionGroup('memories').get()).docs
    .filter((doc) => MEMORY_PATH.test(doc.ref.path))
    .slice(0, limit);
  console.log(`${apply ? 'APPLY' : 'DRY RUN'}: ${docs.length} memory, dự án ${projectId}, cloud ${cloudName}`);

  const nowMs = Date.now();
  const counts = {};
  for (const doc of docs) {
    const [, relationshipId, memoryId] = MEMORY_PATH.exec(doc.ref.path);
    let result;
    try {
      result = await handleMemory({
        db, cloud, cloudName, presetUrls, apply, nowMs,
        ref: doc.ref, relationshipId, memoryId, memory: doc.data(),
      });
    } catch (err) {
      const reason = err?.message ?? err?.error?.message ?? String(err);
      result = { outcome: err instanceof Changed ? 'changed' : 'failed', reason };
    }
    counts[result.outcome] = (counts[result.outcome] ?? 0) + 1;
    console.log(JSON.stringify({ memoryId, relationshipId, ...result }));
  }
  console.log(JSON.stringify({ summary: counts }));

  // Collection cấp cao nhất "memories" nằm ngoài phạm vi chuyển ở trên. Chỉ đếm để chủ dự án quyết định xoá hay chuyển riêng.
  const isLegacyLink = (value) => parseLegacyUrl(value) !== null && !presetUrls.has(value);
  const topLevel = await db.collection('memories').get();
  const withLinks = topLevel.docs.filter((doc) => ['cloudinaryUrl', 'photoUri', 'videoUri'].some((field) => isLegacyLink(doc.get(field))));
  console.log(JSON.stringify({ topLevelMemories: topLevel.size, topLevelWithLegacyLinks: withLinks.length }));

  if (counts.failed) process.exitCode = 1;
  if (!apply) console.log('Chưa ghi gì. Chạy lại với --apply sau khi chủ dự án duyệt.');
}

module.exports = { legacyProblem, fingerprint, toUpdate, MEMORY_PATH };

if (require.main === module) {
  main().catch((err) => {
    console.error(`Dừng: ${err.message}`);
    process.exitCode = 1;
  });
}
