'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..', '..');
const RULES = fs.readFileSync(path.join(ROOT, 'firestore.rules'), 'utf8');

function extractPrivateDossierSection(rulesText) {
  const start = rulesText.indexOf('function privateDossierFields()');
  assert.notEqual(start, -1, 'privateDossierFields helper missing in firestore.rules');
  const nextSection = rulesText.indexOf('match /coupleCodes/', start);
  assert.notEqual(nextSection, -1, 'coupleCodes section boundary missing');
  return rulesText.slice(start, nextSection);
}

test('firestore.rules defines owner-only /users/{userId}/privateDossier/{docId} rule for docId == current', () => {
  const section = extractPrivateDossierSection(RULES);
  assert.match(section, /match \/users\/\{userId\}\/privateDossier\/\{docId\}/);
  assert.match(section, /allow get:\s*if docId == 'current' && isOwner\(userId\);/);
  assert.match(section, /allow list:\s*if false;/);
  assert.match(section, /allow create, update:\s*if docId == 'current' && isOwner\(userId\)\s*&&\s*validPrivateDossier\(request\.resource\.data\);/);
  assert.match(section, /allow delete:\s*if docId == 'current' && isOwner\(userId\);/);
});

test('privateDossier rule enforces strict fields, size limit, schemaVersion, and zero partner access', () => {
  const section = extractPrivateDossierSection(RULES);
  assert.doesNotMatch(section, /isRelationshipMember/);
  assert.doesNotMatch(section, /allow\s+(?:read|write|get|list)\s*:\s*if\s+true/);
  assert.match(section, /hasOnly\(\['payloadJson',\s*'schemaVersion',\s*'updatedAtEpochMs',\s*'updatedAt'\]\)/);
  assert.match(section, /hasAll\(\['payloadJson',\s*'schemaVersion',\s*'updatedAtEpochMs',\s*'updatedAt'\]\)/);
  assert.match(section, /d\.payloadJson is string && d\.payloadJson\.size\(\) <= 32768/);
  assert.match(section, /d\.schemaVersion == 1/);
  assert.match(section, /d\.updatedAtEpochMs is int/);
  assert.match(section, /d\.updatedAt is timestamp/);
});

test('privateDossier contract payload evaluator mirrors firestore.rules schema bounds', () => {
  const MAX_PAYLOAD_BYTES = 32768;
  const ALLOWED_KEYS = ['payloadJson', 'schemaVersion', 'updatedAtEpochMs', 'updatedAt'];

  const isValidDoc = (docId, authUid, ownerUid, data) => {
    if (docId !== 'current' || !authUid || authUid !== ownerUid) return false;
    if (!data || typeof data !== 'object') return false;
    const keys = Object.keys(data);
    if (keys.length !== ALLOWED_KEYS.length) return false;
    if (!ALLOWED_KEYS.every((k) => Object.prototype.hasOwnProperty.call(data, k))) return false;
    if (typeof data.payloadJson !== 'string' || Buffer.byteLength(data.payloadJson, 'utf8') > MAX_PAYLOAD_BYTES) return false;
    if (data.schemaVersion !== 1) return false;
    if (!Number.isInteger(data.updatedAtEpochMs)) return false;
    if (!(data.updatedAt instanceof Date)) return false;
    return true;
  };

  const valid = {
    payloadJson: '{"nickname":"Minh","secretSurpriseNotes":[{"id":"n1","note":"Matcha tin"}]}',
    schemaVersion: 1,
    updatedAtEpochMs: 1760000000000,
    updatedAt: new Date(),
  };

  assert.equal(isValidDoc('current', 'u1', 'u1', valid), true);
  assert.equal(isValidDoc('other', 'u1', 'u1', valid), false);
  assert.equal(isValidDoc('current', 'partner_u2', 'u1', valid), false);
  assert.equal(isValidDoc('current', 'u1', 'u1', { ...valid, extra: true }), false);
  assert.equal(isValidDoc('current', 'u1', 'u1', { ...valid, schemaVersion: 2 }), false);
  assert.equal(isValidDoc('current', 'u1', 'u1', { ...valid, payloadJson: 'a'.repeat(MAX_PAYLOAD_BYTES + 1) }), false);
});
