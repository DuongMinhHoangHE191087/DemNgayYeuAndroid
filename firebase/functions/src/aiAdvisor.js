'use strict';

// Server-side AI Gift & Love-Note Concierge (p6 / Win-Win Monetization).
// Enforces monthly per-uid quota in Firestore (`aiAdvisorQuota/{uid}_{YYYYMM}`):
//   - vip: 60 calls / month
//   - free: 2 calls / month starter quota (throws permission-denied 'vip-required' when exhausted)
// Calls Gemini `gemini-2.5-flash` structured JSON when GEMINI_API_KEY is configured,
// and gracefully falls back to a bilingual server smart engine when unconfigured or unavailable.
const { FieldValue } = require('firebase-admin/firestore');
const { HttpsError } = require('firebase-functions/v2/https');
const cfg = require('./config');

const FREE_MONTHLY_QUOTA = cfg.aiAdvisor?.freeMonthlyQuota ?? 2;
const VIP_MONTHLY_QUOTA = cfg.aiAdvisor?.vipMonthlyQuota ?? 60;
const GEMINI_MODEL = cfg.aiAdvisor?.geminiModel ?? 'gemini-2.5-flash';
const GEMINI_ENDPOINT = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;
const QUOTA_TTL_MS = 45 * 24 * 60 * 60 * 1000;

const VALID_TONES = Object.freeze(['SWEET', 'PLAYFUL', 'DEEPLY_ROMANTIC', 'THOUGHTFUL']);
const VALID_REGIONS = Object.freeze(['VN', 'INTL']);
const VALID_LANGUAGES = Object.freeze(['vi', 'en']);

function monthKeyFor(nowMs) {
  const d = new Date(nowMs);
  const year = d.getUTCFullYear();
  const month = String(d.getUTCMonth() + 1).padStart(2, '0');
  return `${year}${month}`;
}

function stripPii(text) {
  return String(text ?? '')
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, '[email]')
    .replace(/(?:\+?\d[\d\s().-]{7,}\d)/g, '[phone]')
    .trim();
}

function cleanString(value, fieldName, maxLen, fallback = '') {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== 'string') {
    throw new HttpsError('invalid-argument', `${fieldName} phải là chuỗi`);
  }
  const trimmed = value.trim();
  if (trimmed.length > maxLen) {
    throw new HttpsError('invalid-argument', `${fieldName} vượt quá ${maxLen} ký tự`);
  }
  return trimmed;
}

function cleanStringArray(value, fieldName, maxItems, maxItemLen, { lowercase = false, uppercase = false } = {}) {
  if (value === undefined || value === null) return Object.freeze([]);
  if (!Array.isArray(value)) {
    throw new HttpsError('invalid-argument', `${fieldName} phải là danh sách`);
  }
  if (value.length > maxItems) {
    throw new HttpsError('invalid-argument', `${fieldName} tối đa ${maxItems} mục`);
  }
  const seen = new Set();
  const out = [];
  for (const raw of value) {
    if (typeof raw !== 'string') {
      throw new HttpsError('invalid-argument', `Mục trong ${fieldName} phải là chuỗi`);
    }
    let item = raw.trim();
    if (!item) continue;
    if (item.length > maxItemLen) {
      throw new HttpsError('invalid-argument', `Mục trong ${fieldName} vượt quá ${maxItemLen} ký tự`);
    }
    if (lowercase) item = item.toLowerCase();
    else if (uppercase) item = item.toUpperCase();
    if (!seen.has(item)) {
      seen.add(item);
      out.push(item);
    }
  }
  return Object.freeze(out);
}

function cleanIntInRange(value, fieldName, min, max, fallback) {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== 'number' || !Number.isFinite(value) || !Number.isInteger(value)) {
    throw new HttpsError('invalid-argument', `${fieldName} phải là số nguyên`);
  }
  if (value < min || value > max) {
    throw new HttpsError('invalid-argument', `${fieldName} phải trong khoảng [${min}, ${max}]`);
  }
  return value;
}

function parseAiGiftInput(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new HttpsError('invalid-argument', 'Dữ liệu yêu cầu gợi ý quà không hợp lệ');
  }

  let language;
  if (data.language !== undefined && data.language !== null) {
    const rawLang = cleanString(data.language, 'language', 10).toLowerCase();
    if (!VALID_LANGUAGES.includes(rawLang)) {
      throw new HttpsError('invalid-argument', 'language phải là "vi" hoặc "en"');
    }
    language = rawLang;
  } else {
    language = data.isEnglish === true ? 'en' : 'vi';
  }

  const defaultOccasion = language === 'en' ? 'Love Anniversary' : 'Kỷ niệm tình yêu';
  const occasion = cleanString(data.occasion ?? data.occasionTitle, 'occasion', 120, defaultOccasion) || defaultOccasion;
  const occasionId = cleanString(data.occasionId, 'occasionId', 64, '');

  const daysUntil = cleanIntInRange(data.daysUntil ?? data.daysUntilOccasion, 'daysUntil', 0, 3650, 7);
  const loveDays = cleanIntInRange(data.loveDays, 'loveDays', 0, 50000, 0);

  const rawBudget = data.budgetMaxVnd;
  let budgetMaxVnd = 0;
  if (rawBudget !== undefined && rawBudget !== null) {
    if (typeof rawBudget !== 'number' || !Number.isFinite(rawBudget) || rawBudget < 0 || rawBudget > 1_000_000_000) {
      throw new HttpsError('invalid-argument', 'budgetMaxVnd phải là số >= 0 và <= 1,000,000,000');
    }
    budgetMaxVnd = Math.round(rawBudget);
  }

  const rawRegion = cleanString(data.region ?? data.occasionRegion, 'region', 16, 'VN').toUpperCase();
  const region = rawRegion === '' ? 'VN' : rawRegion;
  if (!VALID_REGIONS.includes(region)) {
    throw new HttpsError('invalid-argument', 'region phải là "VN" hoặc "INTL"');
  }

  const rawTone = cleanString(data.tone, 'tone', 32, 'SWEET').toUpperCase();
  const tone = rawTone === '' ? 'SWEET' : rawTone;
  if (!VALID_TONES.includes(tone)) {
    throw new HttpsError('invalid-argument', `tone phải thuộc ${VALID_TONES.join(', ')}`);
  }

  const loveLanguages = cleanStringArray(data.loveLanguages, 'loveLanguages', 5, 40, { uppercase: true });
  const interests = cleanStringArray(data.interests ?? data.interestCategories, 'interests', 14, 40, { lowercase: true });
  const freeformLikes = stripPii(cleanString(data.freeformLikes, 'freeformLikes', 300, ''));
  const allergies = cleanStringArray(data.allergies, 'allergies', 15, 60, { lowercase: true });
  const dislikes = cleanStringArray(
    data.dislikes ?? data.dislikesOrTaboos ?? data.taboos,
    'dislikes',
    15,
    60,
    { lowercase: true },
  );
  const favoriteColors = cleanStringArray(data.favoriteColors, 'favoriteColors', 8, 30);
  const favoriteScents = cleanStringArray(data.favoriteScents, 'favoriteScents', 8, 40);
  const favoriteFoods = cleanStringArray(data.favoriteFoods, 'favoriteFoods', 10, 50);
  const goToDrinks = cleanString(data.goToDrinks, 'goToDrinks', 100, '');
  const pastGifts = cleanStringArray(data.pastGifts, 'pastGifts', 15, 80, { lowercase: true });
  const extraNote = stripPii(cleanString(data.extraNote ?? data.promptHint, 'extraNote', 240, ''));

  return Object.freeze({
    language,
    occasion,
    occasionId,
    daysUntil,
    loveDays,
    budgetMaxVnd,
    region,
    tone,
    loveLanguages,
    interests,
    freeformLikes,
    allergies,
    dislikes,
    favoriteColors,
    favoriteScents,
    favoriteFoods,
    goToDrinks,
    pastGifts,
    extraNote,
  });
}

const RESPONSE_SCHEMA = Object.freeze({
  type: 'OBJECT',
  required: ['recommendations', 'loveNotes', 'tabooWarnings', 'prepTimeline'],
  properties: {
    recommendations: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        required: ['id', 'title', 'category', 'reason', 'preparationSteps', 'estimatedCostVnd', 'priceLabel', 'preparationLeadDays'],
        properties: {
          id: { type: 'STRING' },
          title: { type: 'STRING' },
          category: { type: 'STRING' },
          reason: { type: 'STRING' },
          preparationSteps: { type: 'ARRAY', items: { type: 'STRING' } },
          estimatedCostVnd: { type: 'INTEGER' },
          priceLabel: { type: 'STRING' },
          preparationLeadDays: { type: 'INTEGER' },
          matchedInterests: { type: 'ARRAY', items: { type: 'STRING' } },
        },
      },
    },
    loveNotes: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        required: ['id', 'tone', 'title', 'body'],
        properties: {
          id: { type: 'STRING' },
          tone: { type: 'STRING' },
          title: { type: 'STRING' },
          body: { type: 'STRING' },
        },
      },
    },
    tabooWarnings: { type: 'ARRAY', items: { type: 'STRING' } },
    prepTimeline: { type: 'ARRAY', items: { type: 'STRING' } },
  },
});

function buildGeminiPayload(input) {
  const isEn = input.language === 'en';
  const systemText = isEn
    ? [
      'You are InLove Concierge, a warm and practical relationship gift & love-note advisor.',
      'Return strictly valid JSON matching the schema. Never recommend items violating allergies, dislikes/taboos, or repeating pastGifts.',
      'Respect budgetMaxVnd (0 means flexible) and daysUntil urgency (if <= 1 day, suggest same-day local/digital/experience options).',
      'Do not include any PII or placeholders like [User Name]. Keep tone genuine and non-cringey.',
    ].join(' ')
    : [
      'Bạn là InLove Concierge — trợ lý gợi ý quà tặng và lời chúc tình yêu tinh tế, thực tế.',
      'Luôn trả về JSON thuần đúng schema. Tuyệt đối KHÔNG gợi ý món vi phạm dị ứng (allergies), điều kiêng kỵ/không thích (dislikes) hoặc trùng quà đã tặng (pastGifts).',
      'Tôn trọng ngân sách tối đa budgetMaxVnd (0 = linh hoạt) và độ gấp theo daysUntil (nếu <= 1 ngày thì ưu tiên trải nghiệm, đặt nhanh trong ngày, thiệp/coupon tình yêu).',
      'Không đưa thông tin định danh cá nhân (PII). Viết tự nhiên, chân thành, không sáo rỗng.',
    ].join(' ');

  const profileSummary = {
    language: input.language,
    region: input.region,
    occasion: input.occasion,
    occasionId: input.occasionId || undefined,
    daysUntil: input.daysUntil,
    loveDays: input.loveDays,
    budgetMaxVnd: input.budgetMaxVnd,
    tone: input.tone,
    loveLanguages: input.loveLanguages,
    interests: input.interests,
    freeformLikes: input.freeformLikes || undefined,
    allergies: input.allergies,
    dislikes: input.dislikes,
    favoriteColors: input.favoriteColors,
    favoriteScents: input.favoriteScents,
    favoriteFoods: input.favoriteFoods,
    goToDrinks: input.goToDrinks || undefined,
    pastGifts: input.pastGifts,
    extraNote: input.extraNote || undefined,
  };

  const userPrompt = isEn
    ? `Generate 4 personalized gift recommendations, 3 love-note drafts in tone=${input.tone}, taboo/allergy warnings, and a concise prepTimeline for this couple context (no PII):\n${JSON.stringify(profileSummary)}`
    : `Hãy tạo 4 gợi ý quà tặng cá nhân hóa, 3 mẫu lời chúc theo tone=${input.tone}, cảnh báo kiêng kỵ/dị ứng, và lịch chuẩn bị prepTimeline cho ngữ cảnh sau (không PII):\n${JSON.stringify(profileSummary)}`;

  return {
    systemInstruction: { parts: [{ text: systemText }] },
    contents: [{ role: 'user', parts: [{ text: userPrompt }] }],
    generationConfig: {
      temperature: 0.7,
      maxOutputTokens: 2048,
      responseMimeType: 'application/json',
      responseSchema: RESPONSE_SCHEMA,
    },
  };
}

function violatesConstraints(textBlob, input) {
  const hay = textBlob.toLowerCase();
  for (const a of input.allergies) {
    if (a && hay.includes(a)) return true;
  }
  for (const d of input.dislikes) {
    if (d && hay.includes(d)) return true;
  }
  for (const p of input.pastGifts) {
    if (p && p.length >= 4 && hay.includes(p)) return true;
  }
  return false;
}

function parseGeminiResponse(rawBody, input) {
  try {
    let text = rawBody?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (typeof text !== 'string' || !text.trim()) return null;
    text = text.trim().replace(/^```json\s*/i, '').replace(/^```\s*/i, '').replace(/\s*```$/i, '');
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.recommendations)) return null;

    const recs = parsed.recommendations
      .filter((r) => r && typeof r.title === 'string' && r.title.trim())
      .map((r, idx) => ({
        id: String(r.id || `ai_rec_${idx + 1}`).slice(0, 64),
        title: r.title.trim().slice(0, 140),
        category: String(r.category || (input.language === 'en' ? 'Meaningful' : 'Ý Nghĩa')).trim().slice(0, 60),
        reason: String(r.reason || '').trim().slice(0, 320),
        preparationSteps: Array.isArray(r.preparationSteps)
          ? r.preparationSteps.map((s) => String(s).trim()).filter(Boolean).slice(0, 5)
          : [],
        estimatedCostVnd: Number.isFinite(Number(r.estimatedCostVnd)) ? Math.max(0, Math.round(Number(r.estimatedCostVnd))) : 0,
        priceLabel: String(r.priceLabel || formatPriceLabel(Number(r.estimatedCostVnd) || 0, input.language)).slice(0, 60),
        preparationLeadDays: Number.isFinite(Number(r.preparationLeadDays)) ? Math.max(0, Math.round(Number(r.preparationLeadDays))) : 1,
        matchedInterests: Array.isArray(r.matchedInterests)
          ? r.matchedInterests.map((s) => String(s).trim().toLowerCase()).filter(Boolean).slice(0, 6)
          : [],
      }))
      .filter((r) => !violatesConstraints(`${r.title} ${r.reason} ${r.category}`, input))
      .filter((r) => input.budgetMaxVnd <= 0 || r.estimatedCostVnd <= Math.round(input.budgetMaxVnd * 1.15))
      .slice(0, 5);

    if (recs.length === 0) return null;

    const notes = Array.isArray(parsed.loveNotes)
      ? parsed.loveNotes
        .filter((n) => n && typeof n.body === 'string' && n.body.trim())
        .map((n, idx) => ({
          id: String(n.id || `ai_note_${idx + 1}`).slice(0, 64),
          tone: VALID_TONES.includes(String(n.tone || '').toUpperCase()) ? String(n.tone).toUpperCase() : input.tone,
          title: String(n.title || (input.language === 'en' ? 'Love Note' : 'Lời nhắn yêu thương')).trim().slice(0, 100),
          body: n.body.trim().slice(0, 600),
        }))
        .slice(0, 4)
      : [];

    const tabooWarnings = Array.isArray(parsed.tabooWarnings)
      ? parsed.tabooWarnings.map((s) => String(s).trim()).filter(Boolean).slice(0, 6)
      : [];
    const prepTimeline = Array.isArray(parsed.prepTimeline)
      ? parsed.prepTimeline.map((s) => String(s).trim()).filter(Boolean).slice(0, 6)
      : [];

    return {
      occasion: input.occasion,
      language: input.language,
      tone: input.tone,
      recommendations: recs,
      loveNotes: notes.length > 0 ? notes : buildSmartLoveNotes(input),
      tabooWarnings: tabooWarnings.length > 0 ? tabooWarnings : buildTabooWarnings(input),
      prepTimeline: prepTimeline.length > 0 ? prepTimeline : buildPrepTimeline(input),
    };
  } catch {
    return null;
  }
}

function formatPriceLabel(costVnd, language) {
  if (costVnd <= 0) return language === 'en' ? 'Heartfelt / Free' : 'Tự tay / 0đ';
  if (costVnd < 200_000) return language === 'en' ? 'Under 200k VND' : 'Dưới 200k';
  if (costVnd <= 500_000) return '200k - 500k VND';
  if (costVnd <= 1_000_000) return '500k - 1M VND';
  return language === 'en' ? 'Over 1M VND' : 'Trên 1 triệu';
}

const SMART_CATALOG = Object.freeze([
  {
    id: 'srv_coffee_date',
    interests: ['coffee'],
    loveLanguages: ['QUALITY_TIME'],
    costVnd: 280_000,
    leadDays: 1,
    sameDay: true,
    categoryVi: 'Trải Nghiệm',
    categoryEn: 'Experience',
    titleVi: 'Buổi hẹn cà phê thủ công & bánh ngọt ấm áp',
    titleEn: 'Specialty pour-over coffee & pastry date',
    reasonVi: 'Phù hợp người thích cà phê và trân trọng thời gian chất lượng bên nhau.',
    reasonEn: 'Ideal for coffee lovers who cherish unhurried quality time together.',
    stepsVi: ['Chọn quán cà phê yên tĩnh hoặc đặt hạt rang mới', 'Chuẩn bị thêm một tấm thiệp viết tay nhỏ'],
    stepsEn: ['Pick a cozy specialty cafe or fresh-roasted beans', 'Pair with a short handwritten card'],
    tags: ['cà phê', 'coffee', 'bánh', 'cake'],
  },
  {
    id: 'srv_scented_candle',
    interests: ['flowers_scents', 'skincare'],
    loveLanguages: ['RECEIVING_GIFTS', 'PHYSICAL_TOUCH'],
    costVnd: 350_000,
    leadDays: 1,
    sameDay: true,
    categoryVi: 'Lãng Mạn',
    categoryEn: 'Romantic',
    titleVi: 'Nến thơm tinh dầu thiên nhiên & hoa khô ép thiệp',
    titleEn: 'Botanical scented candle & pressed-flower card',
    reasonVi: 'Mang lại cảm giác thư giãn nhẹ nhàng và lưu giữ mùi hương kỷ niệm.',
    reasonEn: 'Creates a calming evening atmosphere with a memorable scent.',
    stepsVi: ['Ưu tiên nốt hương người ấy thích nếu có trong hồ sơ', 'Gói giấy kraft kèm lời nhắn riêng'],
    stepsEn: ['Match their favorite scent notes from the dossier', 'Wrap simply with a personal note'],
    tags: ['nến', 'candle', 'hương', 'scent', 'hoa', 'flower'],
  },
  {
    id: 'srv_handmade_letter',
    interests: ['handmade', 'books'],
    loveLanguages: ['WORDS_OF_AFFIRMATION'],
    costVnd: 120_000,
    leadDays: 1,
    sameDay: true,
    categoryVi: 'Ý Nghĩa',
    categoryEn: 'Meaningful',
    titleVi: 'Hộp thư tay 5 điều biết ơn & ảnh in Polaroid',
    titleEn: 'Handwritten gratitude jar & Polaroid prints',
    reasonVi: 'Chạm đúng ngôn ngữ yêu thương lời nói khẳng định, chi phí gọn nhưng giàu cảm xúc.',
    reasonEn: 'Directly speaks to Words of Affirmation with high emotional value.',
    stepsVi: ['In 4–6 tấm ảnh kỷ niệm gần nhất', 'Viết 5 kỷ niệm hoặc điều bạn trân trọng nhất'],
    stepsEn: ['Print 4–6 favorite recent couple photos', 'Write 5 heartfelt moments you cherish'],
    tags: ['thư', 'letter', 'ảnh', 'photo', 'handmade'],
  },
  {
    id: 'srv_home_dinner',
    interests: ['cooking'],
    loveLanguages: ['ACTS_OF_SERVICE', 'QUALITY_TIME'],
    costVnd: 320_000,
    leadDays: 1,
    sameDay: true,
    categoryVi: 'Hành Động Chăm Sóc',
    categoryEn: 'Acts of Service',
    titleVi: 'Bữa tối tự nấu theo món tủ & playlist riêng hai đứa',
    titleEn: 'Home-cooked favorite dinner & couple playlist',
    reasonVi: 'Thể hiện sự quan tâm bằng hành động thực tế và tránh được món gây dị ứng.',
    reasonEn: 'Shows tangible care through Acts of Service with full allergy control.',
    stepsVi: ['Kiểm tra kỹ danh sách dị ứng/kiêng kỵ trước khi đi chợ', 'Chuẩn bị đồ uống hợp gu người ấy'],
    stepsEn: ['Double-check allergies and dislikes before shopping', 'Prepare their go-to drink on the side'],
    tags: ['nấu ăn', 'dinner', 'food', 'bữa tối'],
  },
  {
    id: 'srv_cinema_evening',
    interests: ['cinema', 'music'],
    loveLanguages: ['QUALITY_TIME', 'PHYSICAL_TOUCH'],
    costVnd: 380_000,
    leadDays: 1,
    sameDay: true,
    categoryVi: 'Trải Nghiệm',
    categoryEn: 'Experience',
    titleVi: 'Vé xem phim ghế đôi / đêm nhạc acoustic',
    titleEn: 'Couple-seat cinema night or acoustic live set',
    reasonVi: 'Dễ đặt ngay kể cả sát ngày, tạo không gian gần gũi và kỷ niệm mới.',
    reasonEn: 'Easy to book even last-minute while creating shared memories.',
    stepsVi: ['Đặt trước vé online để chọn hàng ghế đẹp', 'Rủ đi dạo nhẹ sau buổi chiếu'],
    stepsEn: ['Book tickets online ahead for cozy seats', 'Follow with an unhurried evening walk'],
    tags: ['phim', 'cinema', 'movie', 'nhạc', 'music'],
  },
  {
    id: 'srv_skincare_care',
    interests: ['skincare', 'fashion'],
    loveLanguages: ['RECEIVING_GIFTS', 'ACTS_OF_SERVICE'],
    costVnd: 490_000,
    leadDays: 2,
    sameDay: true,
    categoryVi: 'Chăm Sóc',
    categoryEn: 'Self-Care',
    titleVi: 'Set dưỡng môi & kem tay hương dịu kèm hộp quà',
    titleEn: 'Gentle lip balm & hand-care gift set',
    reasonVi: 'Thiết thực dùng mỗi ngày và an toàn hơn mỹ phẩm đặc trị nếu chưa rõ loại da.',
    reasonEn: 'Practical daily care that stays safe even without exact skin-type notes.',
    stepsVi: ['Kiểm tra thành phần hương liệu nếu có dị ứng', 'Kèm thiệp nhắc nghỉ ngơi giữ sức khỏe'],
    stepsEn: ['Check ingredients against known sensitivities', 'Add a caring note to rest well'],
    tags: ['skincare', 'dưỡng', 'mỹ phẩm', 'cream'],
  },
  {
    id: 'srv_tech_desk',
    interests: ['technology', 'gaming'],
    loveLanguages: ['RECEIVING_GIFTS', 'ACTS_OF_SERVICE'],
    costVnd: 650_000,
    leadDays: 3,
    sameDay: false,
    categoryVi: 'Tiện Ích',
    categoryEn: 'Tech & Daily Gear',
    titleVi: 'Đèn bàn / đế sạc gọn bàn làm việc kèm sticker kỷ niệm',
    titleEn: 'Minimal desk lamp or MagSafe stand with custom tag',
    reasonVi: 'Hợp người thích công nghệ hoặc gaming, nhắc nhớ đến bạn mỗi ngày làm việc.',
    reasonEn: 'Great for tech and gaming fans—useful every single day.',
    stepsVi: ['Chọn màu sắc hợp gu của người ấy', 'Kiểm tra chuẩn cổng sạc đang dùng'],
    stepsEn: ['Match their favorite color palette', 'Confirm device connector compatibility'],
    tags: ['công nghệ', 'tech', 'game', 'gaming', 'sạc'],
  },
  {
    id: 'srv_wellness_fitness',
    interests: ['fitness', 'travel'],
    loveLanguages: ['QUALITY_TIME', 'ACTS_OF_SERVICE'],
    costVnd: 450_000,
    leadDays: 2,
    sameDay: true,
    categoryVi: 'Năng Động',
    categoryEn: 'Active & Wellness',
    titleVi: 'Bình giữ nhiệt khắc tên / ngày yêu & buổi chạy bộ picnic',
    titleEn: 'Engraved insulated bottle & morning park picnic',
    reasonVi: 'Kết hợp thói quen vận động lành mạnh và thời gian hít thở cùng nhau.',
    reasonEn: 'Blends daily wellness with fun outdoor time together.',
    stepsVi: ['Chọn màu sắc yêu thích của người ấy', 'Chuẩn bị trái cây hoặc đồ uống mang theo'],
    stepsEn: ['Pick their favorite color', 'Pack light fruit or their favorite drink'],
    tags: ['thể thao', 'fitness', 'bình', 'picnic', 'travel'],
  },
  {
    id: 'srv_weekend_getaway',
    interests: ['travel'],
    loveLanguages: ['QUALITY_TIME'],
    costVnd: 1_500_000,
    leadDays: 7,
    sameDay: false,
    categoryVi: 'Du Lịch',
    categoryEn: 'Getaway',
    titleVi: 'Chuyến staycation 2N1Đ đổi gió gần thành phố',
    titleEn: 'Cozy 2D1N boutique staycation nearby',
    reasonVi: 'Lý tưởng nếu còn nhiều ngày chuẩn bị cho dịp cột mốc hoặc lễ lớn.',
    reasonEn: 'Memorable reset when you have lead time to book ahead.',
    stepsVi: ['Đặt phòng sớm để giữ view đẹp và giá tốt', 'Lên lịch nhẹ nhàng, không nhồi nhét'],
    stepsEn: ['Book early for better rates and quiet rooms', 'Keep the itinerary relaxed'],
    tags: ['du lịch', 'travel', 'staycation', 'khách sạn'],
  },
  {
    id: 'srv_pet_lover',
    interests: ['pets', 'handmade'],
    loveLanguages: ['RECEIVING_GIFTS', 'QUALITY_TIME'],
    costVnd: 300_000,
    leadDays: 3,
    sameDay: false,
    categoryVi: 'Cá Nhân Hóa',
    categoryEn: 'Personalized',
    titleVi: 'Móc khóa / tranh vẽ chibi hai bạn cùng thú cưng',
    titleEn: 'Custom couple & pet illustration keychain or frame',
    reasonVi: 'Đánh trúng sở thích thú cưng với dấu ấn riêng không đụng hàng.',
    reasonEn: 'Personalizes their love for pets into a one-of-a-kind keepsake.',
    stepsVi: ['Chọn bức ảnh rõ nét của hai bạn hoặc thú cưng', 'Đặt trước ít nhất 3 ngày'],
    stepsEn: ['Pick a bright photo to illustrate', 'Order at least 3 days ahead'],
    tags: ['thú cưng', 'pet', 'mèo', 'chó', 'tranh'],
  },
  {
    id: 'srv_love_coupons',
    interests: [],
    loveLanguages: ['ACTS_OF_SERVICE', 'PHYSICAL_TOUCH', 'WORDS_OF_AFFIRMATION'],
    costVnd: 0,
    leadDays: 0,
    sameDay: true,
    categoryVi: 'Tình Cảm',
    categoryEn: 'Heartfelt',
    titleVi: 'Bộ 6 "Phiếu Đặc Quyền Yêu Thương" tự thiết kế',
    titleEn: 'Custom 6-card "Love Privilege Coupons" pack',
    reasonVi: 'Cứu cánh hoàn hảo khi sát giờ hoặc ngân sách tiết kiệm mà vẫn cực kỳ tinh tế.',
    reasonEn: 'Zero-budget, same-day life-saver packed with thoughtful promises.',
    stepsVi: ['Ghi các quyền lợi như: massage 20 phút, nấu bữa tối, chọn phim không cãi', 'Gấp phong bì hoặc gửi kèm tin nhắn'],
    stepsEn: ['Include perks like 20-min massage, dinner duty, or movie pick veto', 'Tuck into an envelope or message'],
    tags: ['coupon', 'phiếu', 'massage'],
  },
]);

function buildSmartLoveNotes(input) {
  const isEn = input.language === 'en';
  const daysPhrase = input.loveDays > 0
    ? (isEn ? `${input.loveDays} days` : `${input.loveDays} ngày`)
    : (isEn ? 'every day' : 'từng ngày');
  const occ = input.occasion;

  if (isEn) {
    return [
      {
        id: 'note_sweet',
        tone: input.tone,
        title: `Sweet ${occ} Note`,
        body: `Happy ${occ}! Thank you for ${daysPhrase} of patience, warmth, and all the tiny moments we share. Today is just an excuse to celebrate us a little louder.`,
      },
      {
        id: 'note_playful',
        tone: 'PLAYFUL',
        title: 'Playful & Lighthearted',
        body: `Happy ${occ} to my favorite person! Official reminder: you get extra hugs, zero chore complaints, and first dibs on snacks today.`,
      },
      {
        id: 'note_deep',
        tone: 'DEEPLY_ROMANTIC',
        title: 'Deeply Romantic',
        body: `Through ${daysPhrase}, you've turned ordinary days into my favorite place to be. Wishing us a warm ${occ} and many more quiet mornings together.`,
      },
    ];
  }

  return [
    {
      id: 'note_sweet',
      tone: input.tone,
      title: `Lời chúc ${occ} ngọt ngào`,
      body: `Chúc mừng ${occ} của tụi mình! Cảm ơn vì ${daysPhrase} luôn kiên nhẫn, dịu dàng và cùng nhau đi qua biết bao điều bình dị. Mong tụi mình luôn vui và thương nhau thật lâu.`,
    },
    {
      id: 'note_playful',
      tone: 'PLAYFUL',
      title: 'Hóm hỉnh dễ thương',
      body: `Alo ${occ} tới rồi nha! Hôm nay đặc cách cho bạn quyền chọn món ăn, ôm miễn phí không giới hạn và miễn mọi deadline việc nhà!`,
    },
    {
      id: 'note_deep',
      tone: 'DEEPLY_ROMANTIC',
      title: 'Sâu lắng chân thành',
      body: `Đi cùng nhau suốt ${daysPhrase}, điều mình trân trọng nhất vẫn là cảm giác bình yên mỗi khi có bạn bên cạnh. Chúc hai tụi mình một ${occ} thật ấm áp và trọn vẹn.`,
    },
  ];
}

function buildTabooWarnings(input) {
  const isEn = input.language === 'en';
  const warnings = [];
  if (input.allergies.length > 0) {
    warnings.push(
      isEn
        ? `Allergy guard: avoid ingredients or scents containing ${input.allergies.join(', ')}.`
        : `Lưu ý dị ứng: tuyệt đối tránh thành phần/mùi hương liên quan tới ${input.allergies.join(', ')}.`,
    );
  }
  if (input.dislikes.length > 0) {
    warnings.push(
      isEn
        ? `Personal dislikes noted: skip ${input.dislikes.join(', ')}.`
        : `Điều người ấy không thích: tránh ${input.dislikes.join(', ')}.`,
    );
  }
  if (input.region === 'VN') {
    warnings.push(
      isEn
        ? 'Cultural tip (VN): avoid gifting clocks, sharp items, or handkerchiefs on romantic occasions.'
        : 'Mẹo văn hóa (VN): tránh tặng đồng hồ, vật sắc nhọn hoặc khăn tay vào dịp kỷ niệm tình yêu.',
    );
  }
  return warnings;
}

function buildPrepTimeline(input) {
  const isEn = input.language === 'en';
  if (input.daysUntil <= 1) {
    return isEn
      ? [
        'Today: finalize an immediate local pick, bouquet, or home-cooked surprise.',
        '2 hours before: write a heartfelt card or send your personalized love note.',
      ]
      : [
        'Hôm nay: chốt quà/trải nghiệm làm ngay trong ngày (quán quen, hoa, bữa tối).',
        'Trước 2 giờ: viết thiệp tay hoặc gửi lời chúc riêng thật chân thành.',
      ];
  }
  if (input.daysUntil <= 5) {
    return isEn
      ? [
        `D-${input.daysUntil}: order gift or reserve table/tickets now to avoid sold-out slots.`,
        'D-1: confirm reservation, wrap gift, and draft your love note.',
      ]
      : [
        `Trước ${input.daysUntil} ngày: chốt đặt quà giao nhanh hoặc giữ bàn hẹn hò ngay.`,
        'Trước 1 ngày: kiểm tra lại lịch hẹn, gói quà và chuẩn bị thiệp.',
      ];
  }
  return isEn
    ? [
      '7+ days ahead: order personalized items or book popular dinner spots early.',
      '3 days ahead: inspect packaging and double-check allergy/taboo checklist.',
      'Day-of: pair the gift with a calm moment and your handwritten note.',
    ]
    : [
      'Trước 7+ ngày: đặt sớm quà cá nhân hóa hoặc giữ chỗ nhà hàng/chuyến đi.',
      'Trước 3 ngày: kiểm tra gói quà và rà lại danh sách dị ứng/kiêng kỵ.',
      'Đúng ngày: trao quà trong không gian thoải mái kèm lời nhắn riêng.',
    ];
}

function buildServerSmartAdvice(input) {
  const isEn = input.language === 'en';
  const scored = SMART_CATALOG
    .filter((item) => input.budgetMaxVnd <= 0 || item.costVnd <= input.budgetMaxVnd)
    .filter((item) => {
      const blob = `${item.titleVi} ${item.titleEn} ${item.tags.join(' ')}`;
      return !violatesConstraints(blob, input);
    })
    .map((item) => {
      let score = 10;
      const matched = item.interests.filter((k) => input.interests.includes(k));
      score += matched.length * 25;
      const langMatch = item.loveLanguages.some((l) => input.loveLanguages.includes(l));
      if (langMatch) score += 15;
      if (input.daysUntil <= 1 && item.sameDay) score += 20;
      if (input.daysUntil <= 1 && !item.sameDay) score -= 30;
      if (item.leadDays <= input.daysUntil) score += 10;
      return { item, matched, score };
    })
    .sort((a, b) => b.score - a.score)
    .slice(0, 4);

  const picks = scored.length > 0
    ? scored
    : [{ item: SMART_CATALOG[SMART_CATALOG.length - 1], matched: [], score: 1 }];

  const recommendations = picks.map(({ item, matched }) => ({
    id: item.id,
    title: isEn ? item.titleEn : item.titleVi,
    category: isEn ? item.categoryEn : item.categoryVi,
    reason: isEn ? item.reasonEn : item.reasonVi,
    preparationSteps: isEn ? [...item.stepsEn] : [...item.stepsVi],
    estimatedCostVnd: item.costVnd,
    priceLabel: formatPriceLabel(item.costVnd, input.language),
    preparationLeadDays: item.leadDays,
    matchedInterests: matched,
  }));

  return {
    occasion: input.occasion,
    language: input.language,
    tone: input.tone,
    recommendations,
    loveNotes: buildSmartLoveNotes(input),
    tabooWarnings: buildTabooWarnings(input),
    prepTimeline: buildPrepTimeline(input),
  };
}

async function consumeMonthlyQuota({ db, uid, tier, nowMs }) {
  const monthKey = monthKeyFor(nowMs);
  const limit = tier === 'vip' ? VIP_MONTHLY_QUOTA : FREE_MONTHLY_QUOTA;
  const effectiveTier = tier === 'vip' ? 'vip' : 'free';
  const ref = db.collection('aiAdvisorQuota').doc(`${uid}_${monthKey}`);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const used = Number.isInteger(snap.get('count')) ? snap.get('count') : 0;
    if (used >= limit) {
      if (effectiveTier !== 'vip') {
        throw new HttpsError('permission-denied', 'vip-required', {
          reason: 'vip-required',
          tier: effectiveTier,
          limit,
          used,
          monthKey,
        });
      }
      throw new HttpsError('resource-exhausted', 'Bạn đã dùng hết 60 lượt AI trong tháng này', {
        reason: 'monthly-quota-exhausted',
        tier: effectiveTier,
        limit,
        used,
        monthKey,
      });
    }
    const nextUsed = used + 1;
    tx.set(ref, {
      uid,
      monthKey,
      tier: effectiveTier,
      count: FieldValue.increment(1),
      updatedAtMs: nowMs,
      expiresAt: new Date(nowMs + QUOTA_TTL_MS),
    }, { merge: true });
    return {
      tier: effectiveTier,
      used: nextUsed,
      limit,
      remaining: Math.max(0, limit - nextUsed),
      monthKey,
    };
  });
}

async function generateGiftAdvice({
  db,
  uid,
  tier,
  input,
  geminiApiKey = '',
  fetchImpl = globalThis.fetch,
  nowMs = Date.now(),
}) {
  if (!uid || typeof uid !== 'string') {
    throw new HttpsError('unauthenticated', 'Cần đăng nhập để dùng Trợ lý Quà tặng AI');
  }
  const cleanInput = input && Object.isFrozen(input) ? input : parseAiGiftInput(input ?? {});
  const quota = await consumeMonthlyQuota({ db, uid, tier, nowMs });

  const apiKey = typeof geminiApiKey === 'string' ? geminiApiKey.trim() : '';
  if (apiKey && typeof fetchImpl === 'function') {
    try {
      const payload = buildGeminiPayload(cleanInput);
      const signal = typeof AbortSignal?.timeout === 'function' ? AbortSignal.timeout(12000) : undefined;
      const res = await fetchImpl(GEMINI_ENDPOINT, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': apiKey,
        },
        body: JSON.stringify(payload),
        ...(signal ? { signal } : {}),
      });
      if (res?.ok) {
        const body = await res.json();
        const parsed = parseGeminiResponse(body, cleanInput);
        if (parsed) {
          return {
            source: 'gemini_ai',
            model: GEMINI_MODEL,
            ...parsed,
            quota,
          };
        }
      }
    } catch {
      // Fall back gracefully to server_smart_engine so local/emulator/dev or upstream outages never break UX.
    }
  }

  return {
    source: 'server_smart_engine',
    model: 'smart-rule-engine-v2',
    ...buildServerSmartAdvice(cleanInput),
    quota,
  };
}

module.exports = {
  FREE_MONTHLY_QUOTA,
  VIP_MONTHLY_QUOTA,
  GEMINI_MODEL,
  GEMINI_ENDPOINT,
  monthKeyFor,
  parseAiGiftInput,
  buildGeminiPayload,
  parseGeminiResponse,
  buildServerSmartAdvice,
  generateGiftAdvice,
};
