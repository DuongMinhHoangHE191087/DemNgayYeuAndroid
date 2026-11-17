const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

// RULES_PATH lets a mutation check point at a scratch copy of the rules; default is the real file.
const RULES_PATH = process.env.RULES_PATH || path.resolve(__dirname, '../../firestore.rules');

describe('invites rules', function () {
  this.timeout(20000);
  let testEnv;

  const SENDER = 'uid_sender';
  const TARGET = 'uid_target';
  const OTHER = 'uid_other';
  const ID = 'inv_1';

  const db = (uid) => testEnv.authenticatedContext(uid).firestore();
  const invite = (uid) => db(uid).collection('invites').doc(ID);

  const seed = (data = {}) =>
    testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection('invites').doc(ID).set({
        senderUid: SENDER, targetUid: TARGET, status: 'PENDING', createdAt: 1, ...data,
      });
    });

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'inlove-rules-test',
      firestore: { rules: fs.readFileSync(RULES_PATH, 'utf8') },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  describe('create', () => {
    it('sender can create a PENDING invite for someone else', async () => {
      await assertSucceeds(invite(SENDER).set({ senderUid: SENDER, targetUid: TARGET, status: 'PENDING' }));
    });

    it('sender cannot create an ACCEPTED invite', async () => {
      await assertFails(invite(SENDER).set({ senderUid: SENDER, targetUid: TARGET, status: 'ACCEPTED' }));
    });

    it('sender cannot invite themselves', async () => {
      await assertFails(invite(SENDER).set({ senderUid: SENDER, targetUid: SENDER, status: 'PENDING' }));
    });

    it('cannot forge senderUid', async () => {
      await assertFails(invite(OTHER).set({ senderUid: SENDER, targetUid: TARGET, status: 'PENDING' }));
    });

    it('unauthenticated cannot create', async () => {
      const anon = testEnv.unauthenticatedContext().firestore();
      await assertFails(anon.collection('invites').doc(ID).set({ senderUid: SENDER, targetUid: TARGET, status: 'PENDING' }));
    });
  });

  describe('update', () => {
    beforeEach(() => seed());

    it('target can move PENDING to ACCEPTED', async () => {
      await assertSucceeds(invite(TARGET).update({ status: 'ACCEPTED' }));
    });

    it('target can move PENDING to DECLINED', async () => {
      await assertSucceeds(invite(TARGET).update({ status: 'DECLINED' }));
    });

    it('target cannot move PENDING to CANCELLED', async () => {
      await assertFails(invite(TARGET).update({ status: 'CANCELLED' }));
    });

    it('sender can move PENDING to CANCELLED', async () => {
      await assertSucceeds(invite(SENDER).update({ status: 'CANCELLED' }));
    });

    it('sender cannot accept or decline their own invite', async () => {
      await assertFails(invite(SENDER).update({ status: 'ACCEPTED' }));
      await assertFails(invite(SENDER).update({ status: 'DECLINED' }));
    });

    it('target cannot change targetUid', async () => {
      await assertFails(invite(TARGET).update({ status: 'ACCEPTED', targetUid: OTHER }));
    });

    it('target cannot touch a field other than status', async () => {
      await assertFails(invite(TARGET).update({ createdAt: 2 }));
      await assertFails(invite(TARGET).update({ status: 'ACCEPTED', createdAt: 2 }));
    });

    it('a decided invite cannot change status again', async () => {
      await seed({ status: 'ACCEPTED' });
      await assertFails(invite(TARGET).update({ status: 'DECLINED' }));
      await assertFails(invite(SENDER).update({ status: 'CANCELLED' }));
    });

    it('third party cannot update', async () => {
      await assertFails(invite(OTHER).update({ status: 'ACCEPTED' }));
      await assertFails(invite(OTHER).update({ status: 'CANCELLED' }));
    });
  });

  describe('read and delete', () => {
    beforeEach(() => seed());

    it('sender and target can get; third party cannot', async () => {
      await assertSucceeds(invite(SENDER).get());
      await assertSucceeds(invite(TARGET).get());
      await assertFails(invite(OTHER).get());
    });

    it('list filtered to own invites works; third party list and unfiltered list are denied', async () => {
      await assertSucceeds(db(SENDER).collection('invites').where('senderUid', '==', SENDER).get());
      await assertSucceeds(db(TARGET).collection('invites').where('targetUid', '==', TARGET).get());
      await assertFails(db(OTHER).collection('invites').where('senderUid', '==', SENDER).get());
      await assertFails(db(SENDER).collection('invites').get());
    });

    it('third party cannot delete', async () => {
      await assertFails(invite(OTHER).delete());
    });

    it('target cannot delete; sender can', async () => {
      await assertFails(invite(TARGET).delete());
      await assertSucceeds(invite(SENDER).delete());
    });
  });
});
