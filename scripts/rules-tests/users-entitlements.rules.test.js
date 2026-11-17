const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

describe('users/{uid} entitlement protection (T2)', function () {
  this.timeout(20000);
  let testEnv;
  const ME = 'uid_me';
  const OTHER = 'uid_other';
  const SENSITIVE = {
    isVip: true,
    subscriptionTier: 'PREMIUM',
    role: 'ADMIN',
  };

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'inlove-rules-test-users',
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8'),
      },
    });
  });
  after(async () => { await testEnv.cleanup(); });
  beforeEach(async () => { await testEnv.clearFirestore(); });

  const me = () => testEnv.authenticatedContext(ME).firestore().collection('users').doc(ME);
  const seed = async (data, uid = ME) => testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection('users').doc(uid).set(data);
  });
  const readBack = async () => testEnv.withSecurityRulesDisabled(async (ctx) => (
    (await ctx.firestore().collection('users').doc(ME).get()).data()
  ));

  // B-01: bản chiếu hồ sơ đủ 8 key hợp lệ, ghi đè toàn bộ. `extra` ghi đè hoặc thêm key.
  // Nếu emulator báo sentinel không hợp lệ thì dùng
  // require('firebase/compat/app').firestore.FieldValue.serverTimestamp() thay cho serverTimestamp().
  const validProfile = (extra = {}) => {
    const { serverTimestamp } = require('firebase/firestore');
    return {
      displayName: 'An',
      birthDate: '05/05/1995',
      gender: 'MALE',
      bio: 'hi',
      interestsCsv: 'music,travel',
      avatarUrl: '',
      schemaVersion: 1,
      updatedAt: serverTimestamp(),
      ...extra,
    };
  };
  // Doc hợp lệ đã có trên server (seed bỏ qua rules nên updatedAt là Date thường).
  const seedValid = async (extra = {}) => seed({ ...validProfile(), updatedAt: new Date(), ...extra });

  it('allows create with valid profile fields', async () => {
    await assertSucceeds(me().set(validProfile()));
  });

  for (const [k, v] of Object.entries(SENSITIVE)) {
    it(`denies create with ${k}`, async () => {
      await assertFails(me().set({ displayName: 'An', [k]: v }));
    });
    it(`denies update that adds ${k}`, async () => {
      await seed({ displayName: 'An' });
      await assertFails(me().update({ [k]: v }));
    });
    it(`denies update that modifies ${k}`, async () => {
      await seed({ displayName: 'An', isVip: false, subscriptionTier: 'FREE', role: 'USER_FREE' });
      await assertFails(me().update({ [k]: v }));
    });
    it(`denies update that deletes ${k}`, async () => {
      const { deleteField } = require('firebase/firestore');
      await seed({ displayName: 'An', isVip: false, subscriptionTier: 'FREE', role: 'USER_FREE' });
      await assertFails(me().update({ [k]: deleteField() }));
    });
  }

  it('denies create with an unknown field', async () => {
    await assertFails(me().set({ displayName: 'An', entitlement: 'pro' }));
  });

  it('denies mixed valid + sensitive update', async () => {
    await seed({ displayName: 'An' });
    await assertFails(me().update({ displayName: 'Bo', isVip: true }));
  });

  // Tên giữ nguyên vì kho kiểm kê (inventory S16). Ý nghĩa nay là: ghi đè toàn bộ bằng bản chiếu hợp lệ
  // thì dọn sạch các trường server cũ (isVip, email, ...) của doc bản trước.
  it('allows valid profile update and keeps pre-existing server fields untouched', async () => {
    await seed({ displayName: 'An', isVip: true, subscriptionTier: 'PREMIUM', role: 'USER_VIP', email: 'an@example.invalid' });
    await assertSucceeds(me().set(validProfile({ displayName: 'Bo', bio: 'new' })));
    const after = await readBack();
    if (after.isVip !== undefined || after.email !== undefined) {
      throw new Error('isVip/email must be gone after the whole-document overwrite');
    }
  });

  it('denies spoofing uid field', async () => {
    await assertFails(me().set({ uid: OTHER, displayName: 'An' }));
  });

  it('denies other UID read/create/update/delete', async () => {
    await seed({ displayName: 'An' });
    const other = testEnv.authenticatedContext(OTHER).firestore().collection('users').doc(ME);
    await assertFails(other.get());
    await assertFails(other.update({ displayName: 'Hack' }));
    await assertFails(other.delete());
    const other2 = testEnv.authenticatedContext(OTHER).firestore().collection('users').doc('uid_new');
    await assertFails(other2.set({ displayName: 'X' }));
  });

  it('denies unauthenticated access', async () => {
    const anon = testEnv.unauthenticatedContext().firestore().collection('users').doc(ME);
    await assertFails(anon.set({ displayName: 'X' }));
  });

  it('allows owner to delete own profile', async () => {
    await seed({ displayName: 'An' });
    await assertSucceeds(me().delete());
  });

  it('denies bio over 1000 chars', async () => {
    await assertFails(me().set(validProfile({ bio: 'x'.repeat(1001) })));
  });

  it('denies displayName over 100 chars', async () => {
    await assertFails(me().set(validProfile({ displayName: 'x'.repeat(101) })));
  });

  it('denies malformed birthDate', async () => {
    await assertFails(me().set(validProfile({ birthDate: '5/5/95' })));
  });

  it('denies unknown gender', async () => {
    await assertFails(me().set(validProfile({ gender: 'X' })));
  });

  it('denies file avatarUrl', async () => {
    await assertFails(me().set(validProfile({ avatarUrl: 'file:///x' })));
  });

  it('allows https and empty avatarUrl', async () => {
    await assertSucceeds(me().set(validProfile({ avatarUrl: 'https://x/y' })));
    await assertSucceeds(me().set(validProfile({ avatarUrl: '' })));
  });

  it('denies extra key email', async () => {
    await assertFails(me().set(validProfile({ email: 'an@example.invalid' })));
  });

  it('denies extra key partnerId', async () => {
    await assertFails(me().set(validProfile({ partnerId: 'uid_other' })));
  });

  it('denies extra key age', async () => {
    await assertFails(me().set(validProfile({ age: 30 })));
  });

  it('denies schemaVersion 2', async () => {
    await assertFails(me().set(validProfile({ schemaVersion: 2 })));
  });

  it('denies numeric updatedAt', async () => {
    await assertFails(me().set(validProfile({ updatedAt: 123 })));
  });

  it('denies update that deletes a required key', async () => {
    const { deleteField } = require('firebase/firestore');
    await seedValid();
    await assertFails(me().update({ bio: deleteField() }));
  });

  it('allows partial update on a valid doc', async () => {
    await seedValid();
    await assertSucceeds(me().update({ bio: 'x' }));
  });

  it('denies partial update on a legacy doc', async () => {
    await seedValid({ email: 'an@example.invalid' });
    await assertFails(me().update({ bio: 'x' }));
  });
});
