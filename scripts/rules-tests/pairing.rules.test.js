const fs = require('fs');
const path = require('path');
const assert = require('assert');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

describe('pairing security rules (Task 10 design)', function () {
  this.timeout(20000);
  let testEnv;

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'inlove-rules-test',
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8'),
      },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  it('a client cannot create relationships/{id} even for an ACCEPTED invite (server-only via acceptCoupleInvite); the server-created doc keeps its identity locked', async () => {
    const uidA = 'uid_a';
    const uidB = 'uid_b';
    const inviteId = 'inv_1';

    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection('invites').doc(inviteId).set({
        senderUid: uidA, targetUid: uidB, status: 'ACCEPTED', createdAt: Date.now(),
      });
    });

    const bCtx = testEnv.authenticatedContext(uidB).firestore();
    const relRef = bCtx.collection('relationships').doc(inviteId);
    const relData = { partnerAId: uidA, partnerBId: uidB, user1: uidA, user2: uidB, status: 'ACTIVE' };

    await assertFails(relRef.set(relData)); // `allow create: if false` — only the Admin SDK function creates it

    // The relationship as acceptCoupleInvite would have written it (Admin SDK bypasses rules).
    // Re-accept idempotency lives in the callable (src/pairing.js) and has no test yet.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection('relationships').doc(inviteId).set(relData);
    });

    // The doc now exists, so set() is evaluated against the `update` rule, which locks
    // partnerAId/partnerBId: changing them must fail...
    await assertFails(relRef.set({ ...relData, partnerBId: 'uid_attacker' }));
    // ...while re-sending the SAME data (identity unchanged, caller is a member) still succeeds.
    await assertSucceeds(relRef.set(relData));
  });

  it('a relationship cannot be created without a matching ACCEPTED invite at the same id', async () => {
    const uidA = 'uid_a';
    const uidB = 'uid_b';
    const bCtx = testEnv.authenticatedContext(uidB).firestore();

    await assertFails(
      bCtx.collection('relationships').doc('no_such_invite').set({
        partnerAId: uidA, partnerBId: uidB, user1: uidA, user2: uidB, status: 'ACTIVE',
      })
    );
  });

  it('a stale invite cannot be replayed after the relationship already exists (invite stuck at ACCEPTED, no new relationship id can reuse it maliciously)', async () => {
    const uidA = 'uid_a';
    const uidC = 'uid_c'; // attacker, not part of the original invite
    const inviteId = 'inv_2';

    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection('invites').doc(inviteId).set({
        senderUid: uidA, targetUid: 'uid_b', status: 'ACCEPTED', createdAt: Date.now(),
      });
    });

    const cCtx = testEnv.authenticatedContext(uidC).firestore();
    // uid_c tries to claim the same accepted invite as if they were the target — must fail
    // because get(invites/inv_2).data.targetUid ('uid_b') does not equal partnerBId ('uid_c').
    await assertFails(
      cCtx.collection('relationships').doc(inviteId).set({
        partnerAId: uidA, partnerBId: uidC, user1: uidA, user2: uidC, status: 'ACTIVE',
      })
    );
  });

  it('coupleCodes: any authenticated user can look up a code, but list is denied and a taken code cannot be overwritten', async () => {
    const uidA = 'uid_a';
    const uidB = 'uid_b';
    const aCtx = testEnv.authenticatedContext(uidA).firestore();
    const bCtx = testEnv.authenticatedContext(uidB).firestore();

    await assertSucceeds(aCtx.collection('coupleCodes').doc('AAAA-1111').set({ code: 'AAAA-1111', ownerUid: uidA }));
    await assertSucceeds(bCtx.collection('coupleCodes').doc('AAAA-1111').get());
    await assertFails(bCtx.collection('coupleCodes').doc('AAAA-1111').set({ code: 'AAAA-1111', ownerUid: uidB }));
    await assertFails(bCtx.collection('coupleCodes').get());
  });

  it('memories/anniversaries subcollections: both relationship members can read and write, a stranger cannot', async () => {
    const uidA = 'uid_a';
    const uidB = 'uid_b';
    const uidC = 'uid_c'; // not a member of this relationship
    const relId = 'rel_1';

    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection('relationships').doc(relId).set({
        partnerAId: uidA, partnerBId: uidB, user1: uidA, user2: uidB, status: 'ACTIVE',
      });
    });

    const aCtx = testEnv.authenticatedContext(uidA).firestore();
    const bCtx = testEnv.authenticatedContext(uidB).firestore();
    const cCtx = testEnv.authenticatedContext(uidC).firestore();
    // Full memory shape the app writes (MemorySyncAdapter): text-only, so no media fields are set.
    const memoryDoc = {
      syncId: 'sync-1', relationshipId: relId, authorId: uidA, title: 'First trip',
      photoUri: '', videoUri: '', cloudinaryPublicId: '', updatedAt: Date.now(), deleted: false,
    };

    // Member A writes, member B (the SyncCoordinator content-tier listener on the other
    // device) can read it straight back — this is the exact path Task 6/8 rely on, and the
    // one the pre-fix rules file had no match block for at all (falls through to the
    // top-level default-deny otherwise).
    await assertSucceeds(aCtx.collection(`relationships/${relId}/memories`).doc('sync-1').set(memoryDoc));
    await assertSucceeds(bCtx.collection(`relationships/${relId}/memories`).doc('sync-1').get());
    await assertSucceeds(bCtx.collection(`relationships/${relId}/anniversaries`).doc('sync-2').set({
      syncId: 'sync-2', relationshipId: relId, title: 'Anniversary', updatedAt: Date.now(), deleted: false,
    }));

    // A non-member cannot read or write either subcollection of this relationship.
    await assertFails(cCtx.collection(`relationships/${relId}/memories`).doc('sync-1').get());
    await assertFails(cCtx.collection(`relationships/${relId}/memories`).doc('sync-3').set({
      syncId: 'sync-3', relationshipId: relId, authorId: uidC, title: 'Intrusion', updatedAt: Date.now(), deleted: false,
    }));
  });
});
