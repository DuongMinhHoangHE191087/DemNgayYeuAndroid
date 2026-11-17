const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

// RULES_PATH lets a mutation check point at a scratch copy of the rules; default is the real file.
const RULES_PATH = process.env.RULES_PATH || path.resolve(__dirname, '../../firestore.rules');

describe('relationships rules', function () {
  this.timeout(20000);
  let testEnv;

  const A = 'uid_a';
  const B = 'uid_b';
  const OTHER = 'uid_other';
  const REL = 'rel_1';

  const db = (uid) => testEnv.authenticatedContext(uid).firestore();
  const rel = (uid) => db(uid).collection('relationships').doc(REL);

  // Every member-identifying field is set, so each lock is tested on its own.
  const seedRel = (data = {}) =>
    testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection('relationships').doc(REL).set({
        partnerAId: A, partnerBId: B, user1: A, user2: B, user1Uid: A, user2Uid: B,
        status: 'ACTIVE', note: 'x', ...data,
      });
    });

  const seedDoc = (docPath, data) =>
    testEnv.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(docPath).set(data));

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
    it('is always denied, even for the would-be partners', async () => {
      const data = { partnerAId: A, partnerBId: B, status: 'ACTIVE' };
      await assertFails(rel(A).set(data));
      await assertFails(rel(B).set(data));
      await assertFails(rel(OTHER).set(data));
    });

    it('is denied for unauthenticated', async () => {
      const anon = testEnv.unauthenticatedContext().firestore();
      await assertFails(anon.collection('relationships').doc(REL).set({ partnerAId: A, status: 'ACTIVE' }));
    });
  });

  describe('read', () => {
    beforeEach(() => seedRel());

    it('both members can get; non-member and unauthenticated cannot', async () => {
      await assertSucceeds(rel(A).get());
      await assertSucceeds(rel(B).get());
      await assertFails(rel(OTHER).get());
      await assertFails(testEnv.unauthenticatedContext().firestore().collection('relationships').doc(REL).get());
    });

    it('a member can list their own relationships; non-member and unfiltered list are denied', async () => {
      await assertSucceeds(db(A).collection('relationships').where('partnerAId', '==', A).get());
      await assertSucceeds(db(B).collection('relationships').where('partnerBId', '==', B).get());
      await assertFails(db(OTHER).collection('relationships').where('partnerAId', '==', A).get());
      await assertFails(db(A).collection('relationships').get());
    });
  });

  describe('update', () => {
    beforeEach(() => seedRel());

    it('a member can change a non-identity field and move ACTIVE to TERMINATED', async () => {
      await assertSucceeds(rel(A).update({ note: 'y' }));
      await assertSucceeds(rel(B).update({ status: 'TERMINATED' }));
    });

    it('a non-member cannot update', async () => {
      await assertFails(rel(OTHER).update({ note: 'y' }));
    });

    it('a member cannot replace partnerAId', async () => {
      await assertFails(rel(A).update({ partnerAId: OTHER }));
      await assertFails(rel(B).update({ partnerAId: OTHER }));
    });

    it('a member cannot replace partnerBId', async () => {
      await assertFails(rel(A).update({ partnerBId: OTHER }));
    });

    it('a member cannot replace user1 or user2', async () => {
      await assertFails(rel(A).update({ user1: OTHER }));
      await assertFails(rel(B).update({ user2: OTHER }));
    });

    it('a member cannot replace user1Uid or user2Uid', async () => {
      await assertFails(rel(A).update({ user1Uid: OTHER }));
      await assertFails(rel(B).update({ user2Uid: OTHER }));
    });

    it('TERMINATED cannot go back to ACTIVE', async () => {
      await seedRel({ status: 'TERMINATED' });
      await assertFails(rel(A).update({ status: 'ACTIVE' }));
      await assertFails(rel(B).update({ status: 'ACTIVE' }));
      await assertSucceeds(rel(A).update({ note: 'still terminated' }));
    });
  });

  describe('delete', () => {
    beforeEach(() => seedRel());

    // Current behaviour (see Risks): any member may delete, ACTIVE or not.
    it('either member can delete; non-member cannot', async () => {
      await assertFails(rel(OTHER).delete());
      await assertSucceeds(rel(A).delete());
      await seedRel();
      await assertSucceeds(rel(B).delete());
    });

    it('a member can delete a TERMINATED relationship', async () => {
      await seedRel({ status: 'TERMINATED' });
      await assertSucceeds(rel(A).delete());
    });
  });

  for (const [sub, idField] of [['memories', 'authorId'], ['anniversaries', null]]) {
    describe(`relationships/{id}/${sub}`, () => {
      const ID = 'item_1';
      const col = (uid) => db(uid).collection('relationships').doc(REL).collection(sub);
      const base = (uid) => ({ relationshipId: REL, syncId: ID, title: 't', ...(idField ? { [idField]: uid } : {}) });
      const itemPath = `relationships/${REL}/${sub}/${ID}`;

      beforeEach(() => seedRel());

      it('member can create, read, update and delete', async () => {
        await assertSucceeds(col(A).doc(ID).set(base(A)));
        await assertSucceeds(col(B).doc(ID).get());
        await assertSucceeds(col(B).doc(ID).update({ title: 'u' }));
        await assertSucceeds(col(A).doc(ID).delete());
      });

      it('non-member can neither read nor write', async () => {
        await seedDoc(itemPath, base(A));
        await assertFails(col(OTHER).doc(ID).get());
        await assertFails(col(OTHER).doc(ID).set(base(OTHER)));
        await assertFails(col(OTHER).doc(ID).update({ title: 'u' }));
        await assertFails(col(OTHER).doc(ID).delete());
      });

      it('writes need an ACTIVE relationship, reads stay open to members', async () => {
        await seedRel({ status: 'TERMINATED' });
        await seedDoc(itemPath, base(A));
        await assertSucceeds(col(A).doc(ID).get());
        await assertFails(col(A).doc(ID).set(base(A)));
        await assertFails(col(A).doc(ID).update({ title: 'u' }));
        await assertFails(col(A).doc(ID).delete());
      });

      it('a doc naming another relationship or a wrong syncId is refused', async () => {
        await assertFails(col(A).doc(ID).set({ ...base(A), relationshipId: 'rel_other' }));
        await assertFails(col(A).doc(ID).set({ ...base(A), syncId: 'something_else' }));
      });
    });
  }

  describe('top-level memories (legacy)', () => {
    const ID = 'mem_1';
    const mem = (uid) => db(uid).collection('memories').doc(ID);

    beforeEach(() => seedDoc(`memories/${ID}`, { authorUid: A, partnerUid: B, relationshipId: REL }));

    it('create and update are denied for everyone', async () => {
      await assertFails(db(A).collection('memories').doc('new').set({ authorUid: A }));
      await assertFails(mem(A).update({ title: 'u' }));
      await assertFails(mem(B).update({ title: 'u' }));
    });

    it('author and partner can get; stranger cannot', async () => {
      await assertSucceeds(mem(A).get());
      await assertSucceeds(mem(B).get());
      await assertFails(mem(OTHER).get());
    });

    it('only the author can delete', async () => {
      await assertFails(mem(B).delete());
      await assertFails(mem(OTHER).delete());
      await assertSucceeds(mem(A).delete());
    });
  });

  describe('top-level anniversaries', () => {
    const ID = 'ann_1';
    const ann = (uid) => db(uid).collection('anniversaries').doc(ID);

    beforeEach(async () => {
      await seedRel();
      await seedDoc(`anniversaries/${ID}`, { authorUid: A, relationshipId: REL, title: 't' });
    });

    it('author and relationship member can get; stranger cannot', async () => {
      await assertSucceeds(ann(A).get());
      await assertSucceeds(ann(B).get());
      await assertFails(ann(OTHER).get());
    });

    it('create needs author, owner, or relationship membership', async () => {
      const col = (uid) => db(uid).collection('anniversaries');
      await assertSucceeds(col(A).doc('n1').set({ authorUid: A }));
      await assertSucceeds(col(B).doc('n2').set({ authorUid: 'someone', relationshipId: REL }));
      await assertFails(col(OTHER).doc('n3').set({ authorUid: A, relationshipId: REL, userId: A }));
    });

    it('update cannot change authorUid or relationshipId', async () => {
      await assertSucceeds(ann(B).update({ title: 'u' }));
      await assertFails(ann(B).update({ authorUid: B }));
      await assertFails(ann(A).update({ relationshipId: 'rel_other' }));
      await assertFails(ann(OTHER).update({ title: 'u' }));
    });

    it('member can delete; stranger cannot', async () => {
      await assertFails(ann(OTHER).delete());
      await assertSucceeds(ann(B).delete());
    });
  });
});
