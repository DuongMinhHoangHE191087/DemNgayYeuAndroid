const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

// RULES_PATH lets a mutation check point at a scratch copy of the rules; default is the real file.
const RULES_PATH = process.env.RULES_PATH || path.resolve(__dirname, '../../firestore.rules');

describe('blockedUsers and reports rules', function () {
  this.timeout(20000);
  let testEnv;

  const A = 'uid_a'; // blocker / reporter
  const B = 'uid_b'; // blocked / reported
  const C = 'uid_c'; // third party
  const BLOCK_ID = `${A}_${B}`;

  const db = (uid) => testEnv.authenticatedContext(uid).firestore();
  const block = (uid, id = BLOCK_ID) => db(uid).collection('blockedUsers').doc(id);
  const reports = (uid) => db(uid).collection('userReports');
  const goodBlock = () => ({ blockerUid: A, blockedUid: B, createdAt: 1 });
  const goodReport = (over = {}) => ({
    reporterUid: A, reportedUid: B, inviteId: 'inv_1', reason: 'spam', createdAt: 1, ...over,
  });

  const seedBlock = (id = BLOCK_ID, data = goodBlock()) =>
    testEnv.withSecurityRulesDisabled((ctx) => ctx.firestore().collection('blockedUsers').doc(id).set(data));

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

  describe('blockedUsers create', () => {
    it('blockerCanCreateBlock', async () => {
      await assertSucceeds(block(A).set(goodBlock()));
    });

    it('blockIdMismatch_denied', async () => {
      await assertFails(block(A, `${A}_${C}`).set(goodBlock()));
      await assertFails(block(A, 'random_id').set(goodBlock()));
    });

    it('blockSelf_denied', async () => {
      await assertFails(block(A, `${A}_${A}`).set({ blockerUid: A, blockedUid: A, createdAt: 1 }));
    });

    it('blockExtraKey_denied', async () => {
      await assertFails(block(A).set({ ...goodBlock(), note: 'x' }));
    });

    it('blockMissingCreatedAt_denied', async () => {
      await assertFails(block(A).set({ blockerUid: A, blockedUid: B }));
    });

    it('forgedBlockerUid_denied', async () => {
      await assertFails(block(C).set(goodBlock()));
    });

    it('unauthenticatedBlock_denied', async () => {
      const anon = testEnv.unauthenticatedContext().firestore();
      await assertFails(anon.collection('blockedUsers').doc(BLOCK_ID).set(goodBlock()));
    });
  });

  describe('blockedUsers read, update, delete', () => {
    beforeEach(() => seedBlock());

    it('blockerCanGetAndListOwn', async () => {
      await assertSucceeds(block(A).get());
      await assertSucceeds(db(A).collection('blockedUsers').where('blockerUid', '==', A).get());
    });

    it('blockedUserCannotRead', async () => {
      await assertFails(block(B).get());
      await assertFails(db(B).collection('blockedUsers').where('blockerUid', '==', A).get());
    });

    it('thirdPartyAndUnfilteredList_denied', async () => {
      await assertFails(block(C).get());
      await assertFails(db(A).collection('blockedUsers').get());
    });

    it('blockUpdate_denied', async () => {
      await assertFails(block(A).update({ createdAt: 2 }));
      await assertFails(block(A).set({ blockerUid: A, blockedUid: C, createdAt: 1 }));
    });

    it('onlyBlockerCanDelete', async () => {
      await assertFails(block(B).delete());
      await assertFails(block(C).delete());
      await assertSucceeds(block(A).delete());
    });
  });

  describe('invites vs blockedUsers', () => {
    const inviteFrom = (sender, target) =>
      db(sender).collection('invites').doc('inv_1').set({ senderUid: sender, targetUid: target, status: 'PENDING' });

    it('blockedSenderCannotInvite', async () => {
      // A blocked B, so B cannot invite A.
      await seedBlock();
      await assertFails(inviteFrom(B, A));
    });

    it('unblockedSenderCanInvite', async () => {
      await assertSucceeds(inviteFrom(B, A));
    });

    it('blockOfAnotherUserDoesNotAffectInvite', async () => {
      await seedBlock(`${A}_${C}`, { blockerUid: A, blockedUid: C, createdAt: 1 });
      await assertSucceeds(inviteFrom(B, A));
    });
  });

  describe('userReports', () => {
    it('reporterCanCreateReport', async () => {
      await assertSucceeds(reports(A).add(goodReport()));
    });

    it('reportReasonFakeAccount_allowed', async () => {
      for (const reason of ['spam', 'harassment', 'fake_account', 'other']) {
        await assertSucceeds(reports(A).add(goodReport({ reason })));
      }
    });

    it('reportReasonExactly20_allowed', async () => {
      await assertSucceeds(reports(A).add(goodReport({ reason: 'x'.repeat(20) })));
    });

    it('reportReasonLongerThan20_denied', async () => {
      await assertFails(reports(A).add(goodReport({ reason: 'x'.repeat(21) })));
    });

    it('reportReasonEmptyOrNotString_denied', async () => {
      await assertFails(reports(A).add(goodReport({ reason: '' })));
      await assertFails(reports(A).add(goodReport({ reason: 5 })));
    });

    it('reportExtraKey_denied', async () => {
      await assertFails(reports(A).add(goodReport({ note: 'x' })));
    });

    it('reportMissingReason_denied', async () => {
      const { reason, ...rest } = goodReport();
      await assertFails(reports(A).add(rest));
    });

    it('forgedReporterUid_denied', async () => {
      await assertFails(reports(C).add(goodReport()));
    });

    it('unauthenticatedReport_denied', async () => {
      const anon = testEnv.unauthenticatedContext().firestore();
      await assertFails(anon.collection('userReports').add(goodReport()));
    });

    it('clientCannotReadUpdateOrDeleteReport', async () => {
      let id;
      await testEnv.withSecurityRulesDisabled(async (ctx) => {
        id = (await ctx.firestore().collection('userReports').add(goodReport())).id;
      });
      await assertFails(reports(A).doc(id).get());
      await assertFails(reports(A).get());
      await assertFails(reports(A).doc(id).update({ reason: 'other' }));
      await assertFails(reports(A).doc(id).delete());
    });
  });
});
