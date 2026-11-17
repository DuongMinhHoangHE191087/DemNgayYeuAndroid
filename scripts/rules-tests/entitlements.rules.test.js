const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertFails,
} = require('@firebase/rules-unit-testing');

// Anti-bypass: server-owned entitlement collections are written/read only by the Admin SDK
// (which bypasses rules). Nobody on the client, owner included, may touch them.
describe('server-only entitlement collections (M-24)', function () {
  this.timeout(20000);
  let testEnv;
  const OWNER = 'uid_owner';
  const STRANGER = 'uid_stranger';
  const COLLECTIONS = [
    'entitlements',
    'purchases',
    'appAccountTokens',
    'entitlementAccounts',
    'notificationLog',
    'entitlementAudit',
  ];
  const DOC = { uid: OWNER, userId: OWNER, ownerUid: OWNER, tier: 'PREMIUM', isVip: true };

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'inlove-rules-test-entitlements',
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8'),
      },
    });
  });
  after(async () => { await testEnv.cleanup(); });
  beforeEach(async () => {
    await testEnv.clearFirestore();
    // Seed existing docs (server side) so get/update/delete are denied by rules, not by "missing doc".
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      for (const c of COLLECTIONS) {
        await db.collection(c).doc(OWNER).set(DOC);
      }
    });
  });

  const actors = {
    owner: () => testEnv.authenticatedContext(OWNER),
    stranger: () => testEnv.authenticatedContext(STRANGER),
    guest: () => testEnv.unauthenticatedContext(),
  };

  for (const col of COLLECTIONS) {
    for (const [who, ctx] of Object.entries(actors)) {
      describe(`${col} as ${who}`, () => {
        const coll = () => ctx().firestore().collection(col);
        it('denies get', async () => { await assertFails(coll().doc(OWNER).get()); });
        it('denies list', async () => { await assertFails(coll().get()); });
        it('denies create', async () => {
          await assertFails(coll().doc('new_doc').set(DOC));
          await assertFails(coll().add(DOC));
        });
        it('denies update', async () => {
          await assertFails(coll().doc(OWNER).update({ tier: 'PREMIUM', isVip: true }));
          await assertFails(coll().doc(OWNER).set({ isVip: true }, { merge: true }));
        });
        it('denies delete', async () => { await assertFails(coll().doc(OWNER).delete()); });
      });
    }
  }
});
