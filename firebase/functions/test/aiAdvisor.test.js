'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('../src/config');
const runtime = require('../src/entitlement/runtime');
const { GATES } = require('../src/entitlement/gatePolicy');
const { createFakeFirestore } = require('./helpers/fakeFirestore');
const { makeDeps } = require('./entitlement/depsBuilder');
const { BASE_NOW_MS, HOUR, makeStoredPurchase } = require('./entitlement/builders');
const aiAdvisor = require('../src/aiAdvisor');

const NOW = Date.UTC(2026, 10, 18, 12, 0, 0); // 202611

// ─── Input validation ────────────────────────────────────────────────────────

test('parseAiGiftInput: rejects null, primitives, and arrays', () => {
  for (const bad of [null, undefined, 'hello', 42, []]) {
    assert.throws(() => aiAdvisor.parseAiGiftInput(bad), (e) => e.code === 'invalid-argument');
  }
});

test('parseAiGiftInput: validates language, region, tone, numbers, and array types', () => {
  assert.throws(() => aiAdvisor.parseAiGiftInput({ language: 'fr' }), (e) => e.code === 'invalid-argument');
  assert.throws(() => aiAdvisor.parseAiGiftInput({ region: 'JP' }), (e) => e.code === 'invalid-argument');
  assert.throws(() => aiAdvisor.parseAiGiftInput({ tone: 'ANGRY' }), (e) => e.code === 'invalid-argument');
  assert.throws(() => aiAdvisor.parseAiGiftInput({ daysUntil: -1 }), (e) => e.code === 'invalid-argument');
  assert.throws(() => aiAdvisor.parseAiGiftInput({ daysUntil: 1.5 }), (e) => e.code === 'invalid-argument');
  assert.throws(() => aiAdvisor.parseAiGiftInput({ budgetMaxVnd: -500 }), (e) => e.code === 'invalid-argument');
  assert.throws(() => aiAdvisor.parseAiGiftInput({ interests: 'coffee' }), (e) => e.code === 'invalid-argument');
  assert.throws(() => aiAdvisor.parseAiGiftInput({ allergies: [123] }), (e) => e.code === 'invalid-argument');
});

test('parseAiGiftInput: normalizes aliases, strips PII, and applies safe defaults', () => {
  const parsed = aiAdvisor.parseAiGiftInput({
    isEnglish: true,
    occasionTitle: 'Valentine',
    daysUntilOccasion: 3,
    budgetMaxVnd: 500000,
    occasionRegion: 'intl',
    tone: 'playful',
    interestCategories: ['Coffee', 'MUSIC', 'coffee'],
    dislikesOrTaboos: ['Durian'],
    freeformLikes: 'Loves tea, contact me@example.com or +84 912 345 678',
    promptHint: 'Call 0987654321',
  });
  assert.equal(parsed.language, 'en');
  assert.equal(parsed.occasion, 'Valentine');
  assert.equal(parsed.daysUntil, 3);
  assert.equal(parsed.budgetMaxVnd, 500000);
  assert.equal(parsed.region, 'INTL');
  assert.equal(parsed.tone, 'PLAYFUL');
  assert.deepEqual(parsed.interests, ['coffee', 'music']);
  assert.deepEqual(parsed.dislikes, ['durian']);
  assert.equal(parsed.freeformLikes.includes('me@example.com'), false);
  assert.equal(parsed.freeformLikes.includes('912 345 678'), false);
  assert.equal(parsed.extraNote.includes('0987654321'), false);
  assert.ok(Object.isFrozen(parsed));
});

// ─── Payload & Gemini parsing ────────────────────────────────────────────────

test('buildGeminiPayload: produces structured JSON schema request without PII', () => {
  const input = aiAdvisor.parseAiGiftInput({
    language: 'vi',
    occasion: 'Thất Tịch',
    daysUntil: 5,
    budgetMaxVnd: 400000,
    interests: ['coffee', 'handmade'],
    allergies: ['lạc'],
    tone: 'DEEPLY_ROMANTIC',
  });
  const payload = aiAdvisor.buildGeminiPayload(input);
  assert.equal(payload.generationConfig.responseMimeType, 'application/json');
  assert.ok(payload.generationConfig.responseSchema.properties.recommendations);
  assert.ok(payload.generationConfig.responseSchema.properties.loveNotes);
  assert.match(payload.contents[0].parts[0].text, /Thất Tịch/);
  assert.equal(payload.contents[0].parts[0].text.includes('uid-'), false);
});

test('generateGiftAdvice: uses Gemini 2.5 Flash response when configured and valid', async () => {
  const db = createFakeFirestore();
  let calledUrl = '';
  let calledHeaders = null;
  const fakeFetch = async (url, init) => {
    calledUrl = url;
    calledHeaders = init.headers;
    return {
      ok: true,
      json: async () => ({
        candidates: [{
          content: {
            parts: [{
              text: JSON.stringify({
                recommendations: [
                  {
                    id: 'gem_1',
                    title: 'Set cà phê cold brew thủ công & thiệp vẽ tay',
                    category: 'Trải Nghiệm',
                    reason: 'Đúng gu cà phê buổi sáng của người ấy.',
                    preparationSteps: ['Đặt trước 2 ngày', 'Ghi thiệp viết tay'],
                    estimatedCostVnd: 320000,
                    priceLabel: '200k - 500k VND',
                    preparationLeadDays: 2,
                    matchedInterests: ['coffee'],
                  },
                  {
                    id: 'gem_unsafe',
                    title: 'Bánh quy bơ đậu phộng thơm giòn',
                    category: 'Đồ Ăn',
                    reason: 'Ăn vặt ngon miệng.',
                    preparationSteps: ['Mua ở tiệm'],
                    estimatedCostVnd: 150000,
                    priceLabel: 'Dưới 200k',
                    preparationLeadDays: 1,
                    matchedInterests: [],
                  },
                ],
                loveNotes: [
                  { id: 'n1', tone: 'SWEET', title: 'Buổi sáng ngọt', body: 'Chúc em ngày vui!' },
                ],
                tabooWarnings: ['Kiểm tra thành phần đậu phộng trước khi mua.'],
                prepTimeline: ['Trước 2 ngày: đặt cà phê và viết thiệp.'],
              }),
            }],
          },
        }],
      }),
    };
  };

  const result = await aiAdvisor.generateGiftAdvice({
    db,
    uid: 'uid-vip',
    tier: 'vip',
    input: { occasion: 'Kỷ niệm 100 ngày', interests: ['coffee'], allergies: ['đậu phộng'], budgetMaxVnd: 500000 },
    geminiApiKey: 'test-gemini-key',
    fetchImpl: fakeFetch,
    nowMs: NOW,
  });

  assert.equal(calledUrl, 'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent');
  assert.equal(calledHeaders['x-goog-api-key'], 'test-gemini-key');
  assert.equal(result.source, 'gemini_ai');
  assert.equal(result.model, 'gemini-2.5-flash');
  assert.equal(result.recommendations.length, 1);
  assert.equal(result.recommendations[0].id, 'gem_1');
  assert.equal(result.quota.tier, 'vip');
  assert.equal(result.quota.used, 1);
  assert.equal(result.quota.limit, 60);
  assert.equal(result.quota.remaining, 59);
});

test('generateGiftAdvice: gracefully falls back to server_smart_engine when unconfigured or Gemini errors', async () => {
  const db = createFakeFirestore();
  // Case 1: unconfigured key
  const noKey = await aiAdvisor.generateGiftAdvice({
    db,
    uid: 'uid-1',
    tier: 'free',
    input: { language: 'en', occasion: 'Valentine', interests: ['coffee'], budgetMaxVnd: 400000, daysUntil: 1 },
    geminiApiKey: '',
    nowMs: NOW,
  });
  assert.equal(noKey.source, 'server_smart_engine');
  assert.ok(noKey.recommendations.length >= 1);
  assert.ok(noKey.loveNotes.length >= 2);
  assert.ok(noKey.prepTimeline.length >= 1);
  assert.ok(noKey.recommendations.every((r) => r.estimatedCostVnd <= 400000));
  assert.equal(noKey.quota.used, 1);
  assert.equal(noKey.quota.remaining, 1);

  // Case 2: Gemini throws / returns malformed JSON
  const failedGemini = await aiAdvisor.generateGiftAdvice({
    db,
    uid: 'uid-1',
    tier: 'free',
    input: { language: 'vi', interests: ['handmade'], allergies: ['nến'] },
    geminiApiKey: 'bad-key',
    fetchImpl: async () => { throw new Error('network down'); },
    nowMs: NOW,
  });
  assert.equal(failedGemini.source, 'server_smart_engine');
  assert.equal(failedGemini.recommendations.some((r) => r.title.toLowerCase().includes('nến')), false);
  assert.equal(failedGemini.quota.used, 2);
  assert.equal(failedGemini.quota.remaining, 0);
});

// ─── Monthly quota enforcement ───────────────────────────────────────────────

test('quota: free tier gets 2 calls/month then throws permission-denied vip-required without writing', async () => {
  const db = createFakeFirestore();
  await aiAdvisor.generateGiftAdvice({ db, uid: 'u-free', tier: 'free', input: {}, nowMs: NOW });
  await aiAdvisor.generateGiftAdvice({ db, uid: 'u-free', tier: 'free', input: {}, nowMs: NOW });

  const writesBefore = db.writes;
  await assert.rejects(
    aiAdvisor.generateGiftAdvice({ db, uid: 'u-free', tier: 'free', input: {}, nowMs: NOW }),
    (e) => e.code === 'permission-denied' && e.message === 'vip-required' && e.details?.reason === 'vip-required',
  );
  assert.equal(db.writes, writesBefore);

  // Next month resets quota
  const nextMonth = Date.UTC(2026, 11, 1, 0, 0, 0);
  const dec = await aiAdvisor.generateGiftAdvice({ db, uid: 'u-free', tier: 'free', input: {}, nowMs: nextMonth });
  assert.equal(dec.quota.monthKey, '202612');
  assert.equal(dec.quota.used, 1);
});

test('quota: vip tier gets 60 calls/month then throws resource-exhausted', async () => {
  const db = createFakeFirestore();
  const key = `aiAdvisorQuota/u-vip_${aiAdvisor.monthKeyFor(NOW)}`;
  await db.collection('aiAdvisorQuota').doc(`u-vip_${aiAdvisor.monthKeyFor(NOW)}`).set({ count: 59 });

  const last = await aiAdvisor.generateGiftAdvice({ db, uid: 'u-vip', tier: 'vip', input: {}, nowMs: NOW });
  assert.equal(last.quota.used, 60);
  assert.equal(last.quota.limit, 60);
  assert.equal(last.quota.remaining, 0);
  assert.equal(db.read(key).count, 60);

  await assert.rejects(
    aiAdvisor.generateGiftAdvice({ db, uid: 'u-vip', tier: 'vip', input: {}, nowMs: NOW }),
    (e) => e.code === 'resource-exhausted',
  );
});

// ─── Index wiring & entitlement integration ──────────────────────────────────

test('index wiring: generateGiftAdvice respects App Check, rateLimit, gateFor, and entitlement tier', async () => {
  const INDEX_SRC = fs.readFileSync(path.join(__dirname, '..', 'index.js'), 'utf8');
  const deps = makeDeps();
  const p = makeStoredPurchase({
    asOfMs: BASE_NOW_MS - HOUR,
    verifiedAtMs: BASE_NOW_MS - HOUR,
    lastCheckedAtMs: BASE_NOW_MS - HOUR,
    ackState: 'done',
    ackAttempts: null,
    ackNextAtMs: null,
  });
  await deps.db.collection('purchases').doc(p.key).set(p);
  await deps.db.collection('entitlementAccounts').doc(p.uid).set({ purchaseKeys: [p.key] });

  const secret = (name) => ({ name, value: () => (name === 'GEMINI_API_KEY' ? '' : `v-${name}`) });
  const events = [];
  const modules = {
    'firebase-admin/app': { initializeApp: () => {}, getApps: () => [] },
    'firebase-admin/firestore': { getFirestore: () => deps.db },
    'firebase-admin/auth': { getAuth: () => ({}) },
    'firebase-functions/v2/https': { onCall: (opts, handler) => Object.assign(handler, { opts }), HttpsError },
    'firebase-functions/v2/firestore': { onDocumentWritten: (opts, handler) => handler },
    'firebase-functions/v2/scheduler': { onSchedule: (opts, handler) => handler },
    'firebase-functions/params': { defineSecret: secret, defineString: secret },
    'firebase-functions/logger': { info() {} },
    './src/config': cfg,
    './src/policy': {},
    './src/media': {},
    './src/pairing': {},
    './src/accountDeletion': {},
    './src/avatar': {},
    './src/aiAdvisor': aiAdvisor,
    './src/objectStore': { createObjectStore: () => ({}) },
    './src/entitlement': {},
    './src/entitlement/options': { STORE_SECRETS: [secret('STORE_SECRET')] },
    './src/entitlement/runtime': {
      gateFor: async (name, uid) => {
        events.push(`gateFor:${name}:${uid}`);
        return runtime.gateFor(name, uid, deps, GATES);
      },
    },
    './src/rateLimit': { enforce: async ({ name, uid }) => { events.push(`limit:${name}:${uid}`); } },
  };
  const exp = {};
  vm.runInContext(`(function (exports, require, module) {${INDEX_SRC}\n})`, vm.createContext({}))(
    exp,
    (id) => modules[id],
    { exports: exp },
  );

  assert.equal(exp.generateGiftAdvice.opts.enforceAppCheck, true);

  const vipRes = await exp.generateGiftAdvice({ auth: { uid: p.uid }, data: { occasion: 'Anniversary' } });
  assert.equal(vipRes.quota.tier, 'vip');
  assert.equal(vipRes.quota.limit, 60);
  assert.deepEqual(events.slice(0, 2), [`limit:generateGiftAdvice:${p.uid}`, `gateFor:generateGiftAdvice:${p.uid}`]);

  const freeRes = await exp.generateGiftAdvice({ auth: { uid: 'uid-free-user' }, data: { occasion: 'Valentine' } });
  assert.equal(freeRes.quota.tier, 'free');
  assert.equal(freeRes.quota.limit, 2);
});
