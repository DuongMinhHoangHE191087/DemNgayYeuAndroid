const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

// RULES_PATH lets a mutation check point at a scratch copy of the rules; default is the real file.
const RULES_PATH = process.env.RULES_PATH || path.resolve(__dirname, '../../firestore.rules');

// Collections with no normal client path: server-only (accountDeletions, test_fixtures),
// the couple-code lookup (coupleCodes) and the public catalogs.
describe('system collections rules', function () {
  this.timeout(20000);
  let testEnv;

  const OWNER = 'uid_owner';
  const STRANGER = 'uid_stranger';
  const CODE = 'AB12CD';
  const NEW_CODE = 'ZZ99YY';

  const db = (uid) => testEnv.authenticatedContext(uid).firestore();
  const anon = () => testEnv.unauthenticatedContext().firestore();
  const actors = { owner: () => db(OWNER), stranger: () => db(STRANGER), guest: anon };

  // Seeded with rules disabled, so each denial comes from rules and not from a missing document.
  const seed = (docPath, data) =>
    testEnv.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(docPath).set(data));

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'inlove-rules-test-system',
      firestore: { rules: fs.readFileSync(RULES_PATH, 'utf8') },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  // Server-only: nobody on the client may read or write, the owner included.
  for (const [col, docId] of [['accountDeletions', OWNER], ['test_fixtures', 'fx_1']]) {
    for (const [who, actor] of Object.entries(actors)) {
      describe(`${col} as ${who}`, () => {
        const coll = () => actor().collection(col);
        beforeEach(() => seed(`${col}/${docId}`, { uid: OWNER, note: 'server only' }));

        it('denies read (get and list)', async () => {
          await assertFails(coll().doc(docId).get());
          await assertFails(coll().get());
        });

        it('denies write (create, update, delete)', async () => {
          await assertFails(coll().doc('new_doc').set({ uid: OWNER }));
          await assertFails(coll().doc(docId).update({ note: 'hacked' }));
          await assertFails(coll().doc(docId).delete());
        });
      });
    }
  }

  describe('coupleCodes/{code}', () => {
    const code = (uid, id = CODE) => db(uid).collection('coupleCodes').doc(id);
    beforeEach(() => seed(`coupleCodes/${CODE}`, { code: CODE, ownerUid: OWNER, createdAt: 1 }));

    it('owner can publish a new code under their own uid', async () => {
      await assertSucceeds(code(OWNER, NEW_CODE).set({ code: NEW_CODE, ownerUid: OWNER, createdAt: 2 }));
    });

    it('owner can delete their own code', async () => {
      await assertSucceeds(code(OWNER).delete());
    });

    it('owner cannot change an existing code (codes are immutable)', async () => {
      await assertFails(code(OWNER).update({ createdAt: 3 }));
      await assertFails(code(OWNER).set({ code: CODE, ownerUid: OWNER, createdAt: 3 }));
    });

    it('stranger cannot publish a code that names someone else as owner', async () => {
      await assertFails(code(STRANGER, NEW_CODE).set({ code: NEW_CODE, ownerUid: OWNER, createdAt: 2 }));
    });

    it('stranger cannot hijack, overwrite or delete the owner code', async () => {
      await assertFails(code(STRANGER).set({ code: CODE, ownerUid: STRANGER, createdAt: 2 }));
      await assertFails(code(STRANGER).update({ ownerUid: STRANGER }));
      await assertFails(code(STRANGER).delete());
    });

    it('any signed-in user can get a known code; nobody can list codes', async () => {
      await assertSucceeds(code(STRANGER).get());
      await assertFails(db(STRANGER).collection('coupleCodes').get());
      await assertFails(db(OWNER).collection('coupleCodes').get());
    });

    it('guest cannot read, create or delete a code', async () => {
      await assertFails(anon().collection('coupleCodes').doc(CODE).get());
      await assertFails(anon().collection('coupleCodes').doc(NEW_CODE).set({ code: NEW_CODE, ownerUid: OWNER }));
      await assertFails(anon().collection('coupleCodes').doc(CODE).delete());
    });
  });

  // Rules say `allow read: if true`, so guests can read too; only signed-in reads are asserted here.
  describe('public catalogs', () => {
    const CATALOGS = ['gift_ideas', 'milestone_presets', 'badge_definitions', 'preset_assets', 'checklist_templates'];
    for (const col of CATALOGS) {
      describe(col, () => {
        beforeEach(() => seed(`${col}/item_1`, { title: 't' }));

        it('signed-in user can read a document and list the collection', async () => {
          await assertSucceeds(db(OWNER).collection(col).doc('item_1').get());
          await assertSucceeds(db(STRANGER).collection(col).get());
        });

        it('writes are refused for signed-in users and guests', async () => {
          for (const writer of [db(OWNER), anon()]) {
            await assertFails(writer.collection(col).doc('item_2').set({ title: 'x' }));
            await assertFails(writer.collection(col).doc('item_1').update({ title: 'x' }));
            await assertFails(writer.collection(col).doc('item_1').delete());
          }
        });
      });
    }
  });
});
