const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

// Mô hình media: docs/architecture/MEDIA_CLOUDINARY.md. Dữ liệu giả, không dùng uid hay URL thật.
const REL = 'rel_media';
const OWNER = 'uid_owner';
const PARTNER = 'uid_partner';
const STRANGER = 'uid_stranger';
const MEM = '0f8fad5b-d9cb-469f-a165-70867728950e';
const PUBLIC_ID = `inlove_mem_${MEM}`;
const PRESET = 'https://images.example.com/preset/cat.jpg';
const LEGACY_LINK = 'https://res.cloudinary.com/inlove-test/image/upload/v1/inlove/abc.jpg';

describe('media security rules (docs/architecture/MEDIA_CLOUDINARY.md)', function () {
  this.timeout(20000);
  let testEnv;

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'inlove-rules-media',
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
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await db.doc(`relationships/${REL}`).set({
        partnerAId: OWNER, partnerBId: PARTNER, user1: OWNER, user2: PARTNER, status: 'ACTIVE',
      });
      await db.doc('preset_assets/photos').set({ type: 'photos', urls: [PRESET] });
    });
  });

  const asOwner = () => testEnv.authenticatedContext(OWNER).firestore();
  const asPartner = () => testEnv.authenticatedContext(PARTNER).firestore();
  const asStranger = () => testEnv.authenticatedContext(STRANGER).firestore();
  const memories = (db) => db.collection(`relationships/${REL}/memories`);

  const seedAsset = (overrides = {}) => testEnv.withSecurityRulesDisabled((ctx) =>
    ctx.firestore().doc(`mediaAssets/${MEM}`).set({
      state: 'active', ownerUid: OWNER, relationshipId: REL, kind: 'IMAGE', publicId: PUBLIC_ID, ...overrides,
    }));

  const memoryDoc = (overrides = {}) => ({
    syncId: MEM, relationshipId: REL, authorId: OWNER, title: 'Trip', mediaType: 'IMAGE',
    photoUri: '', videoUri: '', cloudinaryPublicId: '', updatedAt: Date.now(), deleted: false,
    ...overrides,
  });

  it('a text-only memory is created by its author', async () => {
    await assertSucceeds(memories(asOwner()).doc(MEM).set(memoryDoc()));
  });

  it('a preset photo listed in preset_assets/photos is allowed', async () => {
    await assertSucceeds(memories(asOwner()).doc(MEM).set(memoryDoc({ photoUri: PRESET })));
  });

  it('a photo that is not a listed preset (device path, unlisted or public link) is refused', async () => {
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ photoUri: 'content://media/external/images/1' })));
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ photoUri: 'https://images.example.com/other.jpg' })));
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ photoUri: LEGACY_LINK })));
  });

  it('public links, Cloudinary storage flags and local video URIs are never stored', async () => {
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryUrl: LEGACY_LINK })));
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ isCloudinaryStored: true })));
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ mediaType: 'VIDEO', videoUri: '/data/user/0/clip.mp4' })));
  });

  it('a publicId with an active asset owned by the author in this couple is allowed', async () => {
    await seedAsset();
    await assertSucceeds(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
  });

  it('a video memory is allowed when its confirmed asset is a video', async () => {
    await seedAsset({ kind: 'VIDEO' });
    await assertSucceeds(memories(asOwner()).doc(MEM).set(memoryDoc({ mediaType: 'VIDEO', cloudinaryPublicId: PUBLIC_ID })));
  });

  it('a publicId without a confirmed active asset is refused (none, reserved, deleting, expired, deleted)', async () => {
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
    for (const state of ['reserved', 'deleting', 'expired', 'deleted']) {
      await seedAsset({ state });
      await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
    }
  });

  it('a publicId whose asset was uploaded by the partner, or belongs to another couple, is refused', async () => {
    await seedAsset({ ownerUid: PARTNER });
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
    await seedAsset({ relationshipId: 'rel_other' });
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
  });

  it('a publicId that is not inlove_mem_<this memory id> is refused, even with a matching asset', async () => {
    await seedAsset({ publicId: 'inlove/other' });
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: 'inlove/other' })));
  });

  it('authorId must be the caller, and a stranger cannot create a memory', async () => {
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ authorId: PARTNER })));
    await assertFails(memories(asStranger()).doc(MEM).set(memoryDoc({ authorId: STRANGER })));
  });

  it('an update cannot attach a file to a memory that has none', async () => {
    await seedAsset();
    await assertSucceeds(memories(asOwner()).doc(MEM).set(memoryDoc()));
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
  });

  it('the partner can edit the text and keep the file, but cannot change the author', async () => {
    await seedAsset();
    await assertSucceeds(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
    await assertSucceeds(memories(asPartner()).doc(MEM).set(memoryDoc({ title: 'Edited', cloudinaryPublicId: PUBLIC_ID })));
    await assertFails(memories(asPartner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID, authorId: PARTNER })));
  });

  it('an update may clear the file', async () => {
    await seedAsset();
    await assertSucceeds(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
    await assertSucceeds(memories(asPartner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: '' })));
  });

  it('a legacy memory that still holds a public link cannot be rewritten until it is migrated', async () => {
    await testEnv.withSecurityRulesDisabled((ctx) =>
      ctx.firestore().doc(`relationships/${REL}/memories/${MEM}`).set(memoryDoc({ photoUri: LEGACY_LINK })));
    await assertFails(memories(asPartner()).doc(MEM).set(memoryDoc({ title: 'Edited', photoUri: LEGACY_LINK })));
  });

  it('a stranger cannot read or write media memories, and nobody writes to a terminated couple', async () => {
    await seedAsset();
    await assertSucceeds(memories(asOwner()).doc(MEM).set(memoryDoc({ cloudinaryPublicId: PUBLIC_ID })));
    await assertFails(memories(asStranger()).doc(MEM).get());
    await assertFails(memories(asStranger()).doc(MEM).set(memoryDoc({ title: 'Intrusion' })));
    await testEnv.withSecurityRulesDisabled((ctx) => ctx.firestore().doc(`relationships/${REL}`).update({ status: 'TERMINATED' }));
    await assertFails(memories(asOwner()).doc(MEM).set(memoryDoc({ title: 'After end', cloudinaryPublicId: PUBLIC_ID })));
  });

  it('clients can never read or write media bookkeeping (mediaAssets, mediaQuota)', async () => {
    await seedAsset();
    await assertFails(asOwner().doc(`mediaAssets/${MEM}`).get());
    await assertFails(asOwner().doc(`mediaAssets/${MEM}`).update({ state: 'active' }));
    await assertFails(asOwner().doc(`mediaQuota/storage_${REL}`).get());
    await assertFails(asOwner().doc(`mediaQuota/storage_${REL}`).set({ bytes: 0 }));
  });

  it('the legacy top-level memories collection accepts no new rows; the author can delete their own', async () => {
    await assertFails(asOwner().collection('memories').doc('legacy_1').set({ authorUid: OWNER, title: 'x' }));
    await testEnv.withSecurityRulesDisabled((ctx) =>
      ctx.firestore().doc('memories/legacy_2').set({ authorUid: OWNER, title: 'x' }));
    await assertFails(asPartner().collection('memories').doc('legacy_2').delete());
    await assertSucceeds(asOwner().collection('memories').doc('legacy_2').delete());
  });
});
