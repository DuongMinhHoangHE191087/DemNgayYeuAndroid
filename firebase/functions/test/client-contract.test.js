'use strict';

// Hợp đồng giữa app Android và Functions. App không import config.js, nên test này đọc mã Kotlin như văn bản
// và so với config.js. Đổi tên, đổi số hay đổi định dạng ở một phía mà quên phía kia thì test này báo ngay.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cfg = require('../src/config');

const ROOT = path.join(__dirname, '..', '..', '..');
const readRepo = (...parts) => fs.readFileSync(path.join(ROOT, ...parts), 'utf8');

// Mã chung dần chuyển sang shared/commonMain; thử bản shared trước, rồi lùi về bản app. `rel` dùng dấu `/`.
function readKotlin(rel, root = ROOT) {
  const candidates = [
    path.join(root, 'shared', 'src', 'commonMain', 'kotlin', 'com', 'example', ...rel.split('/')),
    path.join(root, 'app', 'src', 'main', 'java', 'com', 'example', ...rel.split('/')),
  ];
  const found = candidates.find((p) => fs.existsSync(p));
  if (!found) throw new Error(`không tìm thấy ${rel} ở: ${candidates.join(' | ')}`);
  return fs.readFileSync(found, 'utf8');
}

const MEDIA_KT = readKotlin('domain/media/MemoryMedia.kt');
const REPO_KT = readKotlin('data/media/CloudMemoryMediaRepository.kt');
const CLOUD_REGION_KT = readKotlin('data/cloud/CloudRegion.kt');
const INDEX_JS = readRepo('firebase', 'functions', 'index.js');

// "10L * 1024 * 1024" -> số byte
function bytesConst(source, name) {
  const m = new RegExp(`${name} = (\\d+)L \\* 1024 \\* 1024`).exec(source);
  assert.ok(m, `không tìm thấy ${name}`);
  return Number(m[1]) * 1024 * 1024;
}

function intConst(source, name) {
  const m = new RegExp(`${name} = (\\d+)\\b`).exec(source);
  assert.ok(m, `không tìm thấy ${name}`);
  return Number(m[1]);
}

// mapOf("image/jpeg" to "jpg", ...) -> { 'image/jpeg': 'jpg', ... }
const mimeMap = (block) => Object.fromEntries(
  [...block.matchAll(/"((?:image|video)\/[^"]+)" to "([^"]+)"/g)].map((m) => [m[1], m[2]]),
);

test('public_id: tiền tố app và server dùng giống nhau', () => {
  assert.equal(/MEMORY_PUBLIC_ID_PREFIX = "([^"]+)"/.exec(MEDIA_KT)?.[1], cfg.publicIdPrefix);
});

test('giới hạn dung lượng và thời lượng: app khớp config.js', () => {
  assert.equal(bytesConst(MEDIA_KT, 'IMAGE_MAX_BYTES'), cfg.limits.imageMaxBytes);
  assert.equal(bytesConst(MEDIA_KT, 'VIDEO_MAX_BYTES'), cfg.limits.videoMaxBytes);
  assert.equal(intConst(MEDIA_KT, 'VIDEO_MAX_SECONDS'), cfg.limits.videoMaxSeconds);
});

test('avatar: tiền tố và giới hạn byte của app khớp config.js', () => {
  assert.equal(/AVATAR_PUBLIC_ID_PREFIX = "([^"]+)"/.exec(MEDIA_KT)?.[1], cfg.avatarPublicIdPrefix);
  assert.equal(bytesConst(MEDIA_KT, 'AVATAR_MAX_BYTES'), cfg.limits.avatarMaxBytes);
});

test('avatar: lý do "chưa cấu hình" giống nhau giữa server (details.reason) và AvatarPublisher', () => {
  const server = /'failed-precondition',\s*'[^']+',\s*\{\s*reason:\s*'([^']+)'/.exec(readRepo('firebase', 'functions', 'src', 'avatar.js'))?.[1];
  const client = /e\.reason == "([^"]+)"/.exec(readKotlin('data/media/AvatarPublisher.kt'))?.[1];
  assert.ok(server && client, 'không tìm thấy reason ở server hoặc ở AvatarPublisher');
  assert.equal(client, server);
});

test('định dạng upload: MIME trong app khớp mimeFormats của config.js (cả ảnh và video)', () => {
  const image = /IMAGE_FORMATS = mapOf\(([\s\S]*?)\)/.exec(MEDIA_KT);
  const video = /VIDEO_FORMATS = mapOf\(([^)]*)\)/.exec(MEDIA_KT);
  assert.ok(image && video, 'không tìm thấy IMAGE_FORMATS hoặc VIDEO_FORMATS');
  assert.deepEqual(mimeMap(image[1]), { ...cfg.mimeFormats.IMAGE });
  assert.deepEqual(mimeMap(video[1]), { ...cfg.mimeFormats.VIDEO });
});

test('region: app gọi Functions cùng region với config.js', () => {
  assert.equal(/const val CLOUD_FUNCTIONS_REGION = "([^"]+)"/.exec(CLOUD_REGION_KT)?.[1], cfg.region);
});

test('region: không file Kotlin nào ngoài CloudRegion.kt gõ cứng region hay region của FirebaseFunctions.getInstance', () => {
  const main = path.join(ROOT, 'app', 'src', 'main', 'java');
  const offenders = fs.readdirSync(main, { recursive: true })
    .filter((f) => f.endsWith('.kt') && path.basename(f) !== 'CloudRegion.kt')
    .filter((f) => /FirebaseFunctions\.getInstance\("|"asia-southeast1"/.test(fs.readFileSync(path.join(main, f), 'utf8')));
  assert.deepEqual(offenders, []);
});

test('callable: mọi hàm app gọi đều được server export', () => {
  for (const name of ['signMemoryUpload', 'confirmMemoryUpload', 'getMemoryMediaUrl']) {
    assert.match(REPO_KT, new RegExp(`"${name}"`), `app không gọi ${name}`);
    assert.match(INDEX_JS, new RegExp(`exports\\.${name} = onCall\\(`), `server không export ${name}`);
  }
  // signAvatarUpload do AvatarUploader (T19) gọi, nên chỉ kiểm phía server ở đây.
  assert.match(INDEX_JS, /exports\.signAvatarUpload = onCall\(/, 'server không export signAvatarUpload');
});

// Thư mục tạm giả lập repo: `shared` và/hoặc `app` có cùng tệp `x/Y.kt` với nội dung khác nhau.
function withTempRepo(layout, fn) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'client-contract-'));
  try {
    for (const [file, content] of Object.entries(layout)) {
      fs.mkdirSync(path.dirname(path.join(root, file)), { recursive: true });
      fs.writeFileSync(path.join(root, file), content);
    }
    fn(root);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
}
const SHARED_Y = 'shared/src/commonMain/kotlin/com/example/x/Y.kt';
const APP_Y = 'app/src/main/java/com/example/x/Y.kt';

test('resolver: ưu tiên bản shared khi có cả hai', () => {
  withTempRepo({ [SHARED_Y]: 'SHARED', [APP_Y]: 'APP' }, (root) => {
    assert.equal(readKotlin('x/Y.kt', root), 'SHARED');
  });
});

test('resolver: lùi về bản app khi thiếu shared', () => {
  withTempRepo({ [APP_Y]: 'APP' }, (root) => {
    assert.equal(readKotlin('x/Y.kt', root), 'APP');
  });
});

test('resolver: thiếu cả hai thì lỗi nêu đủ hai đường dẫn', () => {
  withTempRepo({}, (root) => {
    assert.throws(
      () => readKotlin('x/Y.kt', root),
      (e) => e.message.includes(path.join(root, ...SHARED_Y.split('/'))) && e.message.includes(path.join(root, ...APP_Y.split('/'))),
    );
  });
});
// ---- Hồ sơ người dùng (P12): rules `users/{uid}` và ProfileProjection.kt phải khớp nhau. ----
// ProfileProjection.kt (P10) có thể chưa có: đọc LƯỜI trong thân test, để các test khác vẫn chạy.
const profileKt = () => readKotlin('data/sync/ProfileProjection.kt');
// Bỏ comment `//`, nhưng giữ `https://` trong chuỗi (trước `//` đó không phải khoảng trắng).
const rulesText = () => readRepo('firestore.rules').replace(/(^|\s)\/\/.*$/gm, '$1');
const rulesNames = (rules) => {
  const m = /function userProfileFields\(\)\s*\{\s*return \[([\s\S]*?)\];/.exec(rules);
  assert.ok(m, 'không tìm thấy userProfileFields() trong firestore.rules');
  return [...m[1].matchAll(/'([^']+)'/g)].map((x) => x[1]);
};
const kotlinNames = (kt) => {
  const m = /PROFILE_SYNC_FIELDS\s*=\s*listOf\(([\s\S]*?)\)/.exec(kt);
  assert.ok(m, 'không tìm thấy PROFILE_SYNC_FIELDS trong ProfileProjection.kt');
  return [...m[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]);
};
// Thân khối `match /users/{userId} { ... }`, ghép ngoặc nhọn.
function usersBlock(rules) {
  const head = 'match /users/{userId}';
  const at = rules.indexOf(head);
  assert.ok(at >= 0, 'không tìm thấy khối match /users/{userId}');
  const open = rules.indexOf('{', at + head.length);
  let depth = 0;
  for (let i = open; i < rules.length; i += 1) {
    if (rules[i] === '{') depth += 1;
    else if (rules[i] === '}') {
      depth -= 1;
      if (depth === 0) return rules.slice(open, i + 1);
    }
  }
  throw new Error('khối match /users/{userId} không đóng ngoặc');
}

test('users rules: allowlist trong firestore.rules bằng PROFILE_SYNC_FIELDS của app', () => {
  const rules = rulesNames(rulesText());
  const kotlin = kotlinNames(profileKt());
  assert.equal(new Set(rules).size, rules.length, 'allowlist rules có tên trùng');
  assert.equal(new Set(kotlin).size, kotlin.length, 'PROFILE_SYNC_FIELDS có tên trùng');
  assert.equal(kotlin.length, 8);
  assert.deepEqual([...rules].sort(), [...kotlin].sort());
});

test('users rules: allowlist không chứa trường nhạy cảm hay do server sở hữu', () => {
  const banned = ['password', 'hash', 'salt', 'pin', 'security', 'email', 'partner', 'relationship', 'status',
    'couple', 'vip', 'subscription', 'role', 'entitle', 'uid', 'age', 'zodiac'];
  for (const name of [...rulesNames(rulesText()), ...kotlinNames(profileKt())]) {
    for (const word of banned) {
      assert.ok(!name.toLowerCase().includes(word), `trường "${name}" chứa chuỗi cấm "${word}"`);
    }
  }
});

test('users rules: giới hạn size() trong rules bằng hằng PROFILE_*_MAX của app', () => {
  const rules = rulesText();
  const kt = profileKt();
  const limit = (field) => {
    const m = new RegExp(`d\\.${field}\\.size\\(\\) <= (\\d+)`).exec(rules);
    assert.ok(m, `không tìm thấy giới hạn size() của ${field} trong rules`);
    return Number(m[1]);
  };
  assert.equal(limit('displayName'), intConst(kt, 'PROFILE_NAME_MAX'));
  assert.equal(limit('bio'), intConst(kt, 'PROFILE_BIO_MAX'));
  assert.equal(limit('interestsCsv'), intConst(kt, 'PROFILE_INTERESTS_MAX'));
  assert.equal(limit('avatarUrl'), intConst(kt, 'PROFILE_AVATAR_MAX'));
});

test('users rules: create và update đều đi qua hasOnly(allowlist) và validUserProfile, không dùng diff', () => {
  const block = usersBlock(rulesText());
  const allows = [...block.matchAll(/allow\s+([^:]+):\s*if\s+([^;]+);/g)];
  const writes = allows.filter((m) => /\b(create|update)\b/.test(m[1]));
  assert.ok(writes.length >= 1, 'không có allow create/update nào');
  for (const [, ops, expr] of writes) {
    assert.ok(expr.includes('keys().hasOnly(userProfileFields())'), `allow ${ops.trim()} thiếu keys().hasOnly(userProfileFields())`);
    assert.ok(expr.includes('validUserProfile(request.resource.data)'), `allow ${ops.trim()} thiếu validUserProfile(request.resource.data)`);
  }
  assert.ok(!block.includes('affectedKeys'), 'khối users không được dùng affectedKeys');
  assert.ok(!block.includes('diff('), 'khối users không được dùng diff(');
});

test('users rules: firestore.rules không có \\| (rules không hỗ trợ)', () => {
  assert.ok(!readRepo('firestore.rules').includes('\\|'));
});

test('users rules: chỉ accountDeletion.js trong functions/src nhắc tới users/{uid}', () => {
  const src = path.join(ROOT, 'firebase', 'functions', 'src');
  const offenders = fs.readdirSync(src, { recursive: true })
    .filter((f) => f.endsWith('.js') && path.basename(f) !== 'accountDeletion.js')
    .filter((f) => /users\/|collection\(\s*['"]users['"]/.test(fs.readFileSync(path.join(src, f), 'utf8')));
  assert.deepEqual(offenders, []);
});