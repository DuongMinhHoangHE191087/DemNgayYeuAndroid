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

  it('allows create with valid profile fields', async () => {
    await assertSucceeds(me().set({ uid: ME, displayName: 'An', bio: 'hi', age: 25, isProfileSetup: true }));
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

  it('allows valid profile update and keeps pre-existing server fields untouched', async () => {
    await seed({ displayName: 'An', isVip: true, subscriptionTier: 'PREMIUM', role: 'USER_VIP' });
    await assertSucceeds(me().update({ displayName: 'Bo', bio: 'new', avatarUrl: 'x' }));
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
});
