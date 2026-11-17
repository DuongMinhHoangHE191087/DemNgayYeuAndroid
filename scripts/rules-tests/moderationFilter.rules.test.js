const fs = require('fs');
const path = require('path');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

// RULES_PATH lets a mutation check point at a scratch copy of the rules; default is the real file.
const RULES_PATH = process.env.RULES_PATH || path.resolve(__dirname, '../../firestore.rules');

// Must match the placeholder word in firestore.rules (containsBlockedWord). The owner supplies the real list.
const BLOCKED = 'blockedtestword';

describe('moderation filter rules', function () {
  this.timeout(20000);
  let testEnv;

  const SENDER = 'uid_sender';
  const TARGET = 'uid_target';

  // Fresh doc id per call: a second set() on the same id would be an update and fail for the wrong reason.
  let n = 0;
  const create = (extra) =>
    testEnv.authenticatedContext(SENDER).firestore().collection('invites').doc(`inv_mod_${n++}`)
      .set({ senderUid: SENDER, targetUid: TARGET, status: 'PENDING', ...extra });

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

  it('validNameAndBio_allowed', async () => {
    await assertSucceeds(create({ senderName: 'Minh', senderBio: 'Thích đi biển', loveNote: 'Yêu em' }));
  });

  it('vietnameseAccents_allowed', async () => {
    await assertSucceeds(create({ senderName: 'Nguyễn Thị Hồng Ánh', senderBio: 'Đà Lạt ơi, mình đi nhé' }));
  });

  it('nameOver100_denied', async () => {
    await assertSucceeds(create({ senderName: 'a'.repeat(100) }));
    await assertFails(create({ senderName: 'a'.repeat(101) }));
  });

  it('bioOver1000_denied', async () => {
    await assertSucceeds(create({ senderBio: 'a'.repeat(1000) }));
    await assertFails(create({ senderBio: 'a'.repeat(1001) }));
  });

  it('controlCharInName_denied', async () => {
    await assertFails(create({ senderName: 'Mi\u0000nh' }));
    await assertFails(create({ senderName: 'Mi\nnh' }));
    await assertFails(create({ senderName: 'Mi\u007fnh' }));
  });

  // The app's bio and love-note fields are multi-line (maxLines 3 and 2), so tab, LF and CR must pass there.
  it('multilineBioAndNote_allowed', async () => {
    await assertSucceeds(create({ senderBio: 'dòng 1\ndòng 2\r\n\tthụt vào', loveNote: 'a\nb' }));
  });

  it('controlCharInBioOrNote_denied', async () => {
    await assertFails(create({ senderBio: 'a\u0007b' }));
    await assertFails(create({ senderBio: 'a\nb\u0007' }));
    await assertFails(create({ loveNote: 'x\r\ny\u0000z' }));
    await assertFails(create({ senderBio: 'a\u007fb' }));
  });

  // RE2 '.' does not match a newline unless (?s): without it these slip past a '.*X.*' full match.
  it('controlCharAfterNewlineInName_denied', async () => {
    await assertFails(create({ senderName: 'Mi\nn\nh' }));
    await assertFails(create({ senderName: 'a\nb\u0007' }));
  });

  it('blockedWordOnAnotherLine_denied', async () => {
    await assertFails(create({ senderBio: `ok\n${BLOCKED}` }));
    await assertFails(create({ senderBio: `${BLOCKED}\nok` }));
    await assertFails(create({ loveNote: `a\nb\n${BLOCKED.toUpperCase()}\nc` }));
  });

  it('blockedWordInBio_denied', async () => {
    await assertFails(create({ senderBio: `hello ${BLOCKED} there` }));
    await assertFails(create({ senderBio: `${BLOCKED.toUpperCase()}!` }));
    await assertFails(create({ senderName: BLOCKED }));
    await assertFails(create({ loveNote: `a, ${BLOCKED}` }));
  });

  it('blockedWordAsSubstringOfName_allowed', async () => {
    await assertSucceeds(create({ senderName: `Ạ${BLOCKED}ạ` }));
    await assertSucceeds(create({ senderName: `x${BLOCKED}`, senderBio: `${BLOCKED}s` }));
  });

  it('absentBio_allowed', async () => {
    await assertSucceeds(create({ senderName: 'Minh' }));
    await assertSucceeds(create({ senderName: '', senderBio: '', loveNote: '' }));
    await assertSucceeds(create({}));
  });

  it('loveNoteOver1000_denied', async () => {
    await assertSucceeds(create({ loveNote: 'a'.repeat(1000) }));
    await assertFails(create({ loveNote: 'a'.repeat(1001) }));
  });
});
