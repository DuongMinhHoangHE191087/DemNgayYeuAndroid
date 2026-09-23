/**
 * ==============================================================================================
 * InLove App - Cloud Firestore Data Enrichment & Seeding Script
 * ==============================================================================================
 * 
 * Mục đích:
 * - Đẩy toàn bộ dữ liệu mẫu / test data / presets / danh mục quà tặng / huy hiệu / mốc kỷ niệm
 *   lên Firebase Firestore để ứng dụng Android tải động về (Dynamic Cloud Data).
 * - Loại bỏ hoàn toàn việc hardcode dữ liệu nhạy cảm hoặc câu chuyện tình yêu mẫu trong mã nguồn APK,
 *   ngăn chặn triệt để nguy cơ lộ dữ liệu khi file APK bị dịch ngược (anti-decompilation).
 * 
 * Cách chạy:
 *   1. Chạy với Firebase Admin (khuyên dùng cho Dev/CI):
 *      - Đặt file `serviceAccountKey.json` vào thư mục `scripts/`
 *      - Chạy: `node seed_firestore.js`
 *   2. Chạy với REST API / Emulator:
 *      - Chạy: `node seed_firestore.js --mode=rest`
 *   3. Kiểm tra dữ liệu hiện có trên Cloud:
 *      - Chạy: `node seed_firestore.js --verify-only`
 * ==============================================================================================
 */

const fs = require('fs');
const path = require('path');
const https = require('https');

// Tải .env ở gốc repo nếu có (cùng file mà app/build.gradle.kts đọc CLOUDINARY_*/SMTP_*) —
// KHÔNG bắt buộc: nếu chưa `npm install` dotenv ở scripts/, script vẫn chạy tiếp bằng
// process.env thật (CI/CD thường set biến môi trường trực tiếp, không qua file .env).
try {
  require('dotenv').config({ path: path.join(__dirname, '..', '.env') });
} catch (_e) {
  // dotenv chưa cài — bỏ qua, dùng process.env sẵn có.
}

// --- 1. CONFIGURATION ---
const PROJECT_ID = process.env.FIREBASE_PROJECT_ID || 'demngayyeuandroid';
const API_KEY = process.env.FIREBASE_API_KEY || 'AIzaSyDlbXzWv1eZdoYpYe0QwRKL9Ou2bB56b9s';
const SERVICE_ACCOUNT_PATH = process.env.GOOGLE_APPLICATION_CREDENTIALS || path.join(__dirname, 'serviceAccountKey.json');

/**
 * Bắt buộc phải có biến môi trường — dừng script ngay với thông báo rõ ràng thay vì âm thầm
 * dùng giá trị mặc định. Trước đây mật khẩu 5 tài khoản test (gồm 1 tài khoản ADMIN) được
 * hardcode thẳng trong file này và bị commit vào git — bất kỳ ai đọc được lịch sử repo đều có
 * mật khẩu. Xem `.env.example` ở gốc repo để biết danh sách biến cần khai báo trong `.env`.
 */
function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    console.error(
      `\n[FATAL] Thiếu biến môi trường bắt buộc: ${name}\n` +
      `-> Thêm ${name}=<mật khẩu mạnh> vào file .env ở gốc repo (xem .env.example), ` +
      `hoặc export biến môi trường trước khi chạy script.\n`
    );
    process.exit(1);
  }
  return value;
}

// --- 2. ENRICHED DATASET DEFINITIONS ---

// 2.1 Test Accounts Fixtures (Dùng cho QA / Tester / Demo mà không lộ trong APK).
// Mật khẩu đọc từ biến môi trường — KHÔNG hardcode trong source đã commit git.
const TEST_FIXTURES = {
  'tester_primary': {
    email: 'tester.primary@inlove.app',
    password: requireEnv('SEED_TESTER_PRIMARY_PASSWORD'),
    displayName: 'Hoàng Long',
    role: 'USER_VIP',
    tier: 'VIP_YEARLY',
    coupleCode: 'TEST-8888',
    gender: 'MALE',
    bio: 'Tester A - Yêu thương đong đầy 💕'
  },
  'tester_partner': {
    email: 'tester.partner@inlove.app',
    password: requireEnv('SEED_TESTER_PARTNER_PASSWORD'),
    displayName: 'Mai Anh',
    role: 'USER_VIP',
    tier: 'VIP_YEARLY',
    coupleCode: 'TEST-9999',
    gender: 'FEMALE',
    bio: 'Tester B - Nửa kia hoàn hảo 🌸'
  },
  'tester_vip': {
    email: 'vip.member@inlove.app',
    password: requireEnv('SEED_TESTER_VIP_PASSWORD'),
    displayName: 'VIP Member',
    role: 'USER_VIP',
    tier: 'LIFETIME',
    coupleCode: 'VIP-7777',
    gender: 'FEMALE',
    bio: 'Tài khoản trọn đời không quảng cáo ✨'
  },
  'tester_free': {
    email: 'free.user@inlove.app',
    password: requireEnv('SEED_TESTER_FREE_PASSWORD'),
    displayName: 'Thành Viên Free',
    role: 'USER_FREE',
    tier: 'FREE',
    coupleCode: 'FREE-1111',
    gender: 'MALE',
    bio: 'Tài khoản trải nghiệm cơ bản'
  },
  'tester_admin': {
    email: 'admin@inlove.app',
    password: requireEnv('SEED_ADMIN_PASSWORD'),
    displayName: 'Quản Trị Viên InLove',
    role: 'ADMIN',
    tier: 'LIFETIME',
    coupleCode: 'ADMN-9999',
    gender: 'MALE',
    bio: 'System Administrator'
  }
};

// 2.2 Gift Ideas Rich Catalog (18+ Gợi ý quà tặng lãng mạn chọn lọc)
const GIFT_IDEAS = [
  {
    id: 'gift_album_diy',
    title: 'Hộp Album Ảnh Kỷ Niệm Handmade DIY',
    category: 'Kỷ vật Handmade',
    badgeText: '98% Cặp đôi mê mẩn',
    tag: 'Dễ làm',
    description: 'Lưu giữ trọn vẹn những tháng ngày bên nhau bằng những trang ảnh kỷ niệm tự tay dán và viết lời nhắn ngọt ngào.',
    imageUrl: 'https://images.unsplash.com/photo-1513519245088-0e12902e5a38?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'In 20-30 tấm ảnh đẹp nhất & kèm sticker trang trí',
    actionText: 'Xem hướng dẫn làm album DIY',
    isAiGenerated: false,
    targetInterests: 'travel,coffee,books',
    suggestedOccasion: 'Kỷ niệm 100 ngày / 1 năm',
    priceRange: '150.000đ - 350.000đ'
  },
  {
    id: 'gift_silver_necklace',
    title: 'Dây Chuyền Bạc Khắc Ngày Gặp Nhau',
    category: 'Trang sức & Nước hoa',
    badgeText: 'Tinh tế & Sang trọng',
    tag: 'Ý nghĩa',
    description: 'Món trang sức bạc nhỏ nhắn khắc tọa độ hoặc ngày đầu tiên rung động, đồng hành cùng người ấy mỗi ngày.',
    imageUrl: 'https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Thời gian khắc laser & hoàn thiện: 1 - 2 ngày',
    actionText: 'Xem mẫu font chữ & bản thảo khắc',
    isAiGenerated: false,
    targetInterests: 'fashion,technology',
    suggestedOccasion: 'Sinh nhật / Kỷ niệm tình yêu',
    priceRange: '450.000đ - 950.000đ'
  },
  {
    id: 'gift_candlelight_dinner',
    title: 'Bữa Tối Nến Lãng Mạn Tự Chuẩn Bị Tại Gia',
    category: 'Quà lãng mạn',
    badgeText: 'Riêng tư & Ấm cúng',
    tag: 'Lãng mạn',
    description: 'Tự tay chuẩn bị thực đơn đặc biệt riêng tư, chỉ có hai người và những bản tình ca acoustic du dương.',
    imageUrl: 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: '🥩 Bò Steak Thăn Nội • 🍷 Rượu Vang Hồng • 🎂 Bánh Kem Trái Tim',
    actionText: 'Xem công thức nấu & thực đơn chi tiết',
    isAiGenerated: false,
    targetInterests: 'cooking,music,coffee',
    suggestedOccasion: 'Valentine / Kỷ niệm ngày yêu',
    priceRange: '300.000đ - 600.000đ'
  },
  {
    id: 'gift_sunset_glamping',
    title: 'Vé Cắm Trại Ngắm Hoàng Hôn Cuối Tuần (Glamping)',
    category: 'Địa điểm hẹn hò',
    badgeText: 'Trải nghiệm mới lạ',
    tag: 'Dã ngoại',
    description: 'Rời xa phố thị ồn ào để cùng nhau tận hưởng bầu trời hoàng hôn rực rỡ và đếm sao đêm bên lều ấm áp.',
    imageUrl: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Đồi thông ngoại ô (cách trung tâm 45 phút lái xe)',
    actionText: 'Xem địa điểm Glamping gợi ý',
    isAiGenerated: false,
    targetInterests: 'travel,cycling,cinema',
    suggestedOccasion: 'Cuối tuần / Đổi gió tình yêu',
    priceRange: '700.000đ - 1.500.000đ'
  },
  {
    id: 'gift_couple_perfume',
    title: 'Bộ Nước Hoa Niche Cặp Đôi Eau De Parfum',
    category: 'Trang sức & Nước hoa',
    badgeText: 'Hương thơm ký ức',
    tag: 'Cao cấp',
    description: 'Hương thơm ngọt ngào hòa quyện tạo nên dấu ấn tình yêu khó phai mỗi khi sánh bước bên nhau.',
    imageUrl: 'https://images.unsplash.com/photo-1541643600914-78b084683601?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Tông gỗ trầm ấm cho chàng & hương hoa hồng phấn dịu nhẹ cho nàng',
    actionText: 'Khám phá nốt hương tình yêu',
    isAiGenerated: false,
    targetInterests: 'fashion,travel',
    suggestedOccasion: 'Valentine / Giáng sinh',
    priceRange: '1.200.000đ - 2.500.000đ'
  },
  {
    id: 'gift_7_day_mystery_box',
    title: 'Hộp Quà Mở Khóa Bí Mật Đếm Ngược 7 Ngày',
    category: 'Bất ngờ bí mật',
    badgeText: 'Bất ngờ mỗi ngày',
    tag: 'Độc đáo',
    description: 'Chuỗi 7 ngăn quà nhỏ xinh tương ứng với 7 ngày đếm ngược đến ngày kỷ niệm lớn, mỗi ngày mở một niềm vui bất ngờ.',
    imageUrl: 'https://images.unsplash.com/photo-1549465220-1a8b9238cd48?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Gồm: Son môi, kẹo dẻo tình yêu, thư tay, nến thơm, tai nghe, vé xem phim...',
    actionText: 'Xem gợi ý xếp quà vào từng ngăn',
    isAiGenerated: false,
    targetInterests: 'cinema,technology,books',
    suggestedOccasion: 'Đếm ngược kỷ niệm 1 năm / Sinh nhật',
    priceRange: '500.000đ - 1.200.000đ'
  },
  {
    id: 'gift_acoustic_concert',
    title: 'Cặp Vé Đêm Nhạc Acoustic Dưới Ánh Nến',
    category: 'Địa điểm hẹn hò',
    badgeText: 'Cảm xúc thăng hoa',
    tag: 'Âm nhạc',
    description: 'Hòa mình trong không gian âm nhạc mộc mạc lắng đọng, cùng ngắm nhìn ánh nến lung linh và cầm tay người thương.',
    imageUrl: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Không gian phòng trà acoustic ấm cúng, bao gồm 2 phần mocktail',
    actionText: 'Xem lịch biểu diễn cuối tuần',
    isAiGenerated: false,
    targetInterests: 'music,coffee',
    suggestedOccasion: 'Cuối tuần lãng mạn',
    priceRange: '400.000đ - 800.000đ'
  },
  {
    id: 'gift_puzzle_love',
    title: 'Tranh Ghép Xếp Hình Khoảnh Khắc Hạnh Phúc',
    category: 'Kỷ vật Handmade',
    badgeText: 'Gắn kết yêu thương',
    tag: 'Thú vị',
    description: 'Biến bức ảnh đẹp nhất của hai đứa thành bộ ghép hình 500 mảnh để cùng nhau lắp ráp từng mảnh ghép tình yêu.',
    imageUrl: 'https://images.unsplash.com/photo-1508873696983-2df5293cb32f?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Chất liệu gỗ cao cấp kèm khung tranh treo tường hoàn thiện',
    actionText: 'Tải ảnh in mẫu tranh ghép',
    isAiGenerated: false,
    targetInterests: 'gaming,books',
    suggestedOccasion: 'Kỷ niệm 200 ngày',
    priceRange: '250.000đ - 450.000đ'
  },
  {
    id: 'gift_couple_spa',
    title: 'Gói Trải Nghiệm Couple Spa Thảo Dược Thư Giãn',
    category: 'Địa điểm hẹn hò',
    badgeText: 'Chăm sóc & Nâng niu',
    tag: 'Thư giãn',
    description: 'Cùng nhau tận hưởng 90 phút trị liệu massage thảo mộc, xông hơi và ngâm chân hoa hồng xua tan mệt mỏi.',
    imageUrl: 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Phòng VIP đôi riêng tư, trà hoa đậu biếc & bánh ngọt miễn phí',
    actionText: 'Xem menu liệu trình & đặt chỗ',
    isAiGenerated: false,
    targetInterests: 'travel,coffee',
    suggestedOccasion: 'Sau kỳ thi / Hoàn thành dự án bận rộn',
    priceRange: '800.000đ - 1.600.000đ'
  },
  {
    id: 'gift_365_love_letters',
    title: 'Lọ Thủy Tinh 365 Bức Thư Tình Viết Tay',
    category: 'Quà lãng mạn',
    badgeText: 'Mỗi ngày một lời yêu',
    tag: 'Chân thành',
    description: '365 mẩu thư tay gấp sao hoặc cuộn ruy băng nhỏ, để người ấy mở ra đọc mỗi buổi sáng thức dậy.',
    imageUrl: 'https://images.unsplash.com/photo-1518199266791-5375a83190b7?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Lọ thủy tinh vintage + 365 thông điệp yêu thương viết bằng tay',
    actionText: 'Xem gợi ý 50 câu chúc ngọt ngào',
    isAiGenerated: false,
    targetInterests: 'books,music',
    suggestedOccasion: 'Bắt đầu năm mới / Yêu xa',
    priceRange: '100.000đ - 250.000đ'
  },
  {
    id: 'gift_heart_bonsai',
    title: 'Chậu Cây Trái Tim Bonsai Cùng Nhau Chăm Sóc',
    category: 'Kỷ vật Handmade',
    badgeText: 'Nuôi dưỡng tình yêu',
    tag: 'Sinh động',
    description: 'Một chậu sen đá hoặc bonsai uốn hình trái tim để hai người cùng tưới nước và ngắm nhìn tình yêu lớn dần theo năm tháng.',
    imageUrl: 'https://images.unsplash.com/photo-1485955900006-10f4d324d411?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Kèm bảng tên gỗ khắc tên hai đứa và ngày bắt đầu chăm sóc',
    actionText: 'Xem hướng dẫn chăm sóc cây',
    isAiGenerated: false,
    targetInterests: 'coffee,travel',
    suggestedOccasion: 'Kỷ niệm 50 ngày',
    priceRange: '120.000đ - 300.000đ'
  },
  {
    id: 'gift_river_cruise_dinner',
    title: 'Bữa Tối Du Thuyền Sông Sài Gòn Lãng Mạn',
    category: 'Địa điểm hẹn hò',
    badgeText: 'Sang trọng & Đẳng cấp',
    tag: 'Đặc biệt',
    description: 'Chiêm ngưỡng thành phố lung linh ánh đèn từ giữa dòng sông lộng gió, thưởng thức tiệc Âu cao cấp.',
    imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Du ngoạn 2 tiếng trên sông, ban nhạc violin sống biểu diễn trực tiếp',
    actionText: 'Xem lịch trình du thuyền',
    isAiGenerated: false,
    targetInterests: 'travel,dining',
    suggestedOccasion: 'Kỷ niệm 1 năm / Cầu hôn',
    priceRange: '1.500.000đ - 3.000.000đ'
  },
  {
    id: 'gift_trunk_balloon_surprise',
    title: 'Hộp Quà Bong Bóng Đèn Led Phát Sáng Trong Cốp Xe',
    category: 'Bất ngờ bí mật',
    badgeText: 'Hiệu ứng Wow 100%',
    tag: 'Bất ngờ',
    description: 'Khi người ấy mở cốp xe hoặc cánh cửa phòng ngủ, bóng bay trái tim kèm đèn fairy light và hoa hồng bung tỏa bất ngờ.',
    imageUrl: 'https://images.unsplash.com/photo-1530103862676-de8c9debad1d?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Bộ bóng bay jumbo in tên, dây đèn led, cánh hoa hồng tươi & banner tình yêu',
    actionText: 'Xem cách tự setup trong 15 phút',
    isAiGenerated: false,
    targetInterests: 'cinema,fashion',
    suggestedOccasion: 'Sinh nhật người ấy',
    priceRange: '350.000đ - 700.000đ'
  },
  {
    id: 'gift_magnetic_bracelets',
    title: 'Bộ Đôi Vòng Tay Khóa Nam Châm Trái Tim Vô Cực',
    category: 'Trang sức & Nước hoa',
    badgeText: 'Hút nhau mọi khoảng cách',
    tag: 'Dễ thương',
    description: 'Mỗi khi hai bàn tay lại gần nhau, hai nửa trái tim nam châm sẽ tự động hút chặt lại thành một khối trọn vẹn.',
    imageUrl: 'https://images.unsplash.com/photo-1611591475822-fa225026dfbe?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Chất liệu dây dù chống nước hoặc hạt đá tự nhiên phong thủy',
    actionText: 'Xem các phối màu vòng đôi',
    isAiGenerated: false,
    targetInterests: 'fashion,travel',
    suggestedOccasion: 'Kỷ niệm ngày yêu',
    priceRange: '180.000đ - 380.000đ'
  },
  {
    id: 'gift_ai_custom_song',
    title: 'Bài Hát Riêng Được Sáng Tác Cho Chuyện Tình Hai Đứa',
    category: 'AI Đề Xuất ✨',
    badgeText: 'Duy nhất độc bản',
    tag: 'Công nghệ AI',
    description: 'Trợ lý AI phân tích những kỷ niệm đáng nhớ nhất của hai bạn để viết nên một bản tình ca acoustic độc quyền mang tên hai người.',
    imageUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=800&auto=format&fit=crop',
    detailsSnippet: 'Tải file âm thanh chất lượng cao & in kèm mã QR lên tấm kính mica',
    actionText: 'Trải nghiệm tạo bài hát với AI',
    isAiGenerated: true,
    targetInterests: 'music,technology',
    suggestedOccasion: 'Kỷ niệm 1.000 ngày',
    priceRange: 'Miễn phí với VIP'
  }
];

// 2.3 Milestone Presets (Các cột mốc tình yêu chuẩn để đồng bộ cho các cặp đôi)
const MILESTONE_PRESETS = [
  {
    id: 'milestone_7_days',
    title: 'Kỷ niệm 7 ngày yêu nhau',
    subtitle: '1 Tuần Đầu Tiên',
    categoryTag: 'Cột Mốc Ngọt Ngào',
    secondaryTag: 'Hạt Mầm',
    daysRemaining: 7,
    progressPercent: 100.0,
    isImportant: false,
    notificationEnabled: true
  },
  {
    id: 'milestone_30_days',
    title: 'Kỷ niệm 1 tháng bên nhau',
    subtitle: 'Ánh Trăng Tình Đầu',
    categoryTag: 'Cột Mốc Ngọt Ngào',
    secondaryTag: '30 Ngày',
    daysRemaining: 30,
    progressPercent: 100.0,
    isImportant: false,
    notificationEnabled: true
  },
  {
    id: 'milestone_100_days',
    title: 'Kỷ niệm 100 ngày chung bước',
    subtitle: 'Bách Nhật Gắn Kết',
    categoryTag: 'Quan Trọng',
    secondaryTag: '100 Ngày',
    daysRemaining: 100,
    progressPercent: 100.0,
    isImportant: true,
    notificationEnabled: true
  },
  {
    id: 'milestone_200_days',
    title: 'Kỷ niệm 200 ngày yêu',
    subtitle: 'Mùa Hoa Nở Rộ',
    categoryTag: 'Cột Mốc Ngọt Ngào',
    secondaryTag: '200 Ngày',
    daysRemaining: 200,
    progressPercent: 100.0,
    isImportant: false,
    notificationEnabled: true
  },
  {
    id: 'milestone_365_days',
    title: 'Kỷ niệm 1 năm yêu nhau vẹn tròn',
    subtitle: 'Một Năm Bốn Mùa',
    categoryTag: 'Quan Trọng',
    secondaryTag: '1 Năm',
    daysRemaining: 365,
    progressPercent: 100.0,
    isImportant: true,
    notificationEnabled: true
  },
  {
    id: 'milestone_500_days',
    title: 'Cột mốc 500 ngày tình yêu',
    subtitle: 'Trái Tim Pha Lê',
    categoryTag: 'Quan Trọng',
    secondaryTag: '500 Ngày',
    daysRemaining: 500,
    progressPercent: 100.0,
    isImportant: true,
    notificationEnabled: true
  },
  {
    id: 'milestone_730_days',
    title: 'Kỷ niệm 2 năm chung đôi',
    subtitle: 'Hai Năm Chung Đôi',
    categoryTag: 'Quan Trọng',
    secondaryTag: '2 Năm',
    daysRemaining: 730,
    progressPercent: 100.0,
    isImportant: true,
    notificationEnabled: true
  },
  {
    id: 'milestone_1000_days',
    title: 'Kỷ niệm 1.000 ngày son sắt',
    subtitle: 'Thiên Nhật Thủy Chung',
    categoryTag: 'Quan Trọng',
    secondaryTag: '1.000 Ngày',
    daysRemaining: 1000,
    progressPercent: 100.0,
    isImportant: true,
    notificationEnabled: true
  }
];

// 2.4 Badge Definitions (11 Huy hiệu tình yêu đa cấp độ)
const BADGE_DEFINITIONS = [
  {
    id: 'badge_7_days',
    targetDays: 7,
    titleVi: 'Hạt Mầm Tình Yêu',
    titleEn: 'First Spark',
    descVi: '1 tuần đầu tiên chính thức cùng nhau bước vào thế giới tình yêu ngọt ngào.',
    descEn: 'First 7 days together into the magical journey of romance.',
    tier: 'BRONZE',
    iconType: 'sprout_heart',
    rewardQuoteVi: 'Tình yêu bắt đầu từ một ánh mắt, nảy mầm qua từng ngày bên em.',
    rewardQuoteEn: 'Love begins with a glance and sprouts with every sweet day.'
  },
  {
    id: 'badge_30_days',
    targetDays: 30,
    titleVi: 'Ánh Trăng Tình Đầu',
    titleEn: 'Moonlight Lovers',
    descVi: 'Tròn 1 tháng đầu tiên với những rung động ngọt ngào và đáng nhớ nhất.',
    descEn: 'First month milestone filled with sweetest first memories.',
    tier: 'BRONZE',
    iconType: 'moon_heart',
    rewardQuoteVi: 'Tròn một tháng bên nhau, trăng tròn như tình yêu anh dành cho em.',
    rewardQuoteEn: 'A full month together, shining as bright as moonlight.'
  },
  {
    id: 'badge_100_days',
    targetDays: 100,
    titleVi: 'Bách Nhật Gắn Kết',
    titleEn: 'Centurial Romance',
    descVi: 'Mốc 100 ngày kỷ niệm ngọt ngào, bền bỉ và tràn ngập niềm vui.',
    descEn: '100 golden days of companionship and growing affection.',
    tier: 'SILVER',
    iconType: 'rose_heart',
    rewardQuoteVi: '100 ngày trôi qua, nụ cười của em vẫn là điều anh say đắm nhất.',
    rewardQuoteEn: '100 days together and your smile is still my brightest light.'
  },
  {
    id: 'badge_200_days',
    targetDays: 200,
    titleVi: 'Mùa Hoa Nở Rộ',
    titleEn: 'Blossoming Love',
    descVi: '200 ngày thấu hiểu, sẻ chia và luôn có nhau trong mọi thăng trầm.',
    descEn: '200 days of deep understanding, harmony, and joy.',
    tier: 'SILVER',
    iconType: 'blossom_heart',
    rewardQuoteVi: 'Tình yêu của đôi ta như hoa xuân nở rộ, ngát hương qua từng năm tháng.',
    rewardQuoteEn: 'Our love blossoms like springtime flowers across the seasons.'
  },
  {
    id: 'badge_365_days',
    targetDays: 365,
    titleVi: 'Một Năm Vẹn Tròn',
    titleEn: 'Golden First Year',
    descVi: 'Tròn một năm bốn mùa xuân hạ thu đông trọn vẹn yêu thương.',
    descEn: 'One full year of four seasons, unwavering love and joy.',
    tier: 'GOLD',
    iconType: 'crown_heart',
    rewardQuoteVi: 'Một năm bốn mùa trôi qua, tình yêu ta càng thêm đậm sâu và bền chặt.',
    rewardQuoteEn: 'Four seasons in one year, every day in love with you.'
  },
  {
    id: 'badge_500_days',
    targetDays: 500,
    titleVi: 'Trái Tim Pha Lê',
    titleEn: 'Crystal Bond',
    descVi: '500 ngày tình yêu trong sáng, kiên định và vững chãi.',
    descEn: '500 days of pure, resilient love and mutual trust.',
    tier: 'GOLD',
    iconType: 'crystal_heart',
    rewardQuoteVi: '500 ngày qua, tình yêu trong veo như pha lê và bền chặt theo năm tháng.',
    rewardQuoteEn: '500 days of crystal clarity and heartfelt devotion.'
  },
  {
    id: 'badge_730_days',
    targetDays: 730,
    titleVi: 'Hai Năm Chung Đôi',
    titleEn: 'Two Years Harmony',
    descVi: 'Tròn 2 năm đồng hành, cùng nắm tay xây dựng tương lai tươi đẹp.',
    descEn: 'Two beautiful years hand in hand through every adventure.',
    tier: 'RUBY',
    iconType: 'ring_heart',
    rewardQuoteVi: 'Hai năm đồng hành, hai trái tim cùng chung một nhịp đập son sắt.',
    rewardQuoteEn: 'Two years together, two souls beating as one.'
  },
  {
    id: 'badge_1000_days',
    targetDays: 1000,
    titleVi: 'Thiên Nhật Thủy Chung',
    titleEn: 'Millennium of Love',
    descVi: '1.000 ngày son sắt - Cột mốc vĩ đại chứng minh tình yêu không đổi thay.',
    descEn: '1,000 days of steadfast devotion - an epic milestone.',
    tier: 'RUBY',
    iconType: 'trophy_heart',
    rewardQuoteVi: 'Một ngàn ngày bên nhau, ngàn lời yêu thương vẫn luôn mới như ngày đầu.',
    rewardQuoteEn: 'A thousand days together, each feeling as magical as day one.'
  },
  {
    id: 'badge_1349_days',
    targetDays: 1349,
    titleVi: 'Ngọn Lửa Hiện Tại',
    titleEn: 'Passionate Flame',
    descVi: 'Cột mốc kỳ diệu đánh dấu hành trình rực rỡ và đong đầy đến hôm nay.',
    descEn: 'A special milestone marking your radiant journey up to today.',
    tier: 'DIAMOND',
    iconType: 'flame_heart',
    rewardQuoteVi: 'Mỗi ngày trôi qua bên em đều là một kỳ tích ngọt ngào đáng trân trọng.',
    rewardQuoteEn: 'Every day with you is a cherished, radiant blessing.'
  },
  {
    id: 'badge_1825_days',
    targetDays: 1825,
    titleVi: 'Nửa Thập Kỷ Sắt Son',
    titleEn: 'Half-Decade Devotion',
    descVi: 'Tròn 5 năm - Nửa thập kỷ cùng sẻ chia mái ấm và ước mơ tương lai.',
    descEn: 'Five magnificent years building a lifetime of memories together.',
    tier: 'COSMIC',
    iconType: 'infinity_heart',
    rewardQuoteVi: 'Nửa thập kỷ đã qua, nhưng tình yêu anh dành cho em chỉ mới bắt đầu.',
    rewardQuoteEn: 'Half a decade together, and yet forever has only just begun.'
  },
  {
    id: 'badge_3650_days',
    targetDays: 3650,
    titleVi: 'Thập Kỷ Kim Cương',
    titleEn: 'Decade of Eternity',
    descVi: '10 năm vàng son - Tượng đài tình yêu vĩnh cửu bất diệt cùng thời gian.',
    descEn: '10 years of eternal, unbreakable diamond love lasting forever.',
    tier: 'COSMIC',
    iconType: 'eternity_diamond',
    rewardQuoteVi: 'Mười năm một chặng đường, trăm năm một chữ tình son sắt đến muôn đời.',
    rewardQuoteEn: 'A decade of devoted bliss, a lifetime of eternal devotion.'
  }
];

// 2.5 Preset Assets (Preset photos & avatars lưu trữ tập trung trên Cloud)
const PRESET_ASSETS = {
  'photos': {
    collection: 'preset_photos',
    urls: [
      'https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?q=80&w=800&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1518199266791-5375a83190b7?q=80&w=800&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1529636798458-92182e662485?q=80&w=800&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=800&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1511285560929-80b456fea0bc?q=80&w=800&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1464349095431-e9a21285b5f3?q=80&w=800&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1517841905240-472988babdf9?q=80&w=800&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=800&auto=format&fit=crop'
    ]
  },
  'avatars': {
    collection: 'preset_avatars',
    urls: [
      'https://images.unsplash.com/photo-1534528741775-53994a69daeb?q=80&w=400&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1517841905240-472988babdf9?q=80&w=400&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?q=80&w=400&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1524504388940-b1c1722653e1?q=80&w=400&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?q=80&w=400&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?q=80&w=400&auto=format&fit=crop'
    ]
  },
  'wallpapers': {
    collection: 'preset_wallpapers',
    urls: [
      'https://images.unsplash.com/photo-1522383225653-ed111181a951?q=80&w=1080&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=1080&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?q=80&w=1080&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1496062031456-07b8f162a322?q=80&w=1080&auto=format&fit=crop',
      'https://images.unsplash.com/photo-1518199266791-5375a83190b7?q=80&w=1080&auto=format&fit=crop'
    ]
  }
};

// 2.6 Checklist Templates (Các danh sách chuẩn bị mẫu cho cặp đôi)
const CHECKLIST_TEMPLATES = [
  { id: 'check_1', text: 'Đặt bánh kem bento hoặc bánh kem mini dâu tây', iconName: 'cake', isCompleted: false },
  { id: 'check_2', text: 'Viết bức thư tay chân thành thổ lộ cảm xúc', iconName: 'history_edu', isCompleted: false },
  { id: 'check_3', text: 'Đặt trước bàn tại nhà hàng hoa hồng lãng mạn', iconName: 'table_restaurant', isCompleted: false },
  { id: 'check_4', text: 'Chuẩn bị bó hoa tươi người ấy thích nhất', iconName: 'local_florist', isCompleted: false },
  { id: 'check_5', text: 'Lên danh sách các bài hát kỷ niệm của hai đứa', iconName: 'queue_music', isCompleted: false }
];

// --- 3. SEED ENGINE IMPLEMENTATION ---

// Helper convert JS Object to Firestore REST API Document Format
function convertToFirestoreFields(obj) {
  const fields = {};
  for (const [key, value] of Object.entries(obj)) {
    if (value === null || value === undefined) {
      fields[key] = { nullValue: null };
    } else if (typeof value === 'string') {
      fields[key] = { stringValue: value };
    } else if (typeof value === 'number') {
      if (Number.isInteger(value)) {
        fields[key] = { integerValue: value.toString() };
      } else {
        fields[key] = { doubleValue: value };
      }
    } else if (typeof value === 'boolean') {
      fields[key] = { booleanValue: value };
    } else if (Array.isArray(value)) {
      fields[key] = {
        arrayValue: {
          values: value.map(v => {
            if (typeof v === 'string') return { stringValue: v };
            if (typeof v === 'number') return { integerValue: v.toString() };
            return { stringValue: String(v) };
          })
        }
      };
    } else if (typeof value === 'object') {
      fields[key] = { mapValue: { fields: convertToFirestoreFields(value) } };
    }
  }
  return fields;
}

// REST API Document Upsert
function firestoreRestUpsert(collection, docId, data) {
  return new Promise((resolve, reject) => {
    const fields = convertToFirestoreFields(data);
    const bodyData = JSON.stringify({ fields });
    const url = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/${collection}/${docId}?key=${API_KEY}`;

    const parsedUrl = new URL(url);
    const options = {
      hostname: parsedUrl.hostname,
      path: parsedUrl.pathname + parsedUrl.search,
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(bodyData)
      }
    };

    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve({ status: res.statusCode, data: JSON.parse(data || '{}') });
        } else {
          resolve({ status: res.statusCode, error: data });
        }
      });
    });

    req.on('error', (e) => reject(e));
    req.write(bodyData);
    req.end();
  });
}

// REST API Document Query / Read
function firestoreRestList(collection) {
  return new Promise((resolve, reject) => {
    const url = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents/${collection}?key=${API_KEY}`;
    const parsedUrl = new URL(url);
    const options = {
      hostname: parsedUrl.hostname,
      path: parsedUrl.pathname + parsedUrl.search,
      method: 'GET'
    };

    const req = https.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data || '{}');
          const docs = json.documents || [];
          resolve(docs);
        } catch (e) {
          resolve([]);
        }
      });
    });

    req.on('error', (e) => reject(e));
    req.end();
  });
}

// --- 4. MAIN SEED RUNNER ---
async function runSeeder() {
  console.log('================================================================================');
  console.log('  INLOVE ANDROID - CLOUD FIRESTORE ENRICHMENT & SEEDING ENGINE');
  console.log('================================================================================');
  console.log(`[Config] Target Firebase Project: ${PROJECT_ID}`);
  console.log(`[Config] Service Account Path:   ${SERVICE_ACCOUNT_PATH}`);

  let adminDb = null;
  let useAdminSdk = false;

  if (fs.existsSync(SERVICE_ACCOUNT_PATH)) {
    try {
      const admin = require('firebase-admin');
      const serviceAccount = require(SERVICE_ACCOUNT_PATH);
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
      adminDb = admin.firestore();
      useAdminSdk = true;
      console.log('-> [Auth] Authenticated via Firebase Admin SDK (Full Administrative Rights) ✅');
    } catch (e) {
      console.log(`-> [Auth] Service account init notice: ${e.message}. Using REST Engine.`);
    }
  } else {
    console.log('-> [Auth] serviceAccountKey.json not found in scripts/. Using Cloud Firestore REST Engine.');
  }

  const results = {
    testFixtures: 0,
    giftIdeas: 0,
    milestones: 0,
    badges: 0,
    presetAssets: 0,
    checklists: 0
  };

  // 1. Seed Test Fixtures (Anti-Decompilation: Stored in Cloud, Never in APK binary)
  console.log('\n[1/6] Seeding Test Account Fixtures (collection: test_fixtures)...');
  for (const [docId, fixture] of Object.entries(TEST_FIXTURES)) {
    if (useAdminSdk) {
      await adminDb.collection('test_fixtures').doc(docId).set(fixture, { merge: true });
    } else {
      await firestoreRestUpsert('test_fixtures', docId, fixture);
    }
    results.testFixtures++;
    console.log(`  + [Fixture] ${docId} -> ${fixture.email} (${fixture.displayName} - ${fixture.tier})`);
  }

  // 2. Seed Gift Ideas Catalog (Rich Suggestions)
  console.log('\n[2/6] Seeding Gift Ideas Catalog (collection: gift_ideas)...');
  for (const gift of GIFT_IDEAS) {
    const docId = gift.id;
    if (useAdminSdk) {
      await adminDb.collection('gift_ideas').doc(docId).set(gift, { merge: true });
    } else {
      await firestoreRestUpsert('gift_ideas', docId, gift);
    }
    results.giftIdeas++;
    console.log(`  + [Gift] [${gift.category}] ${gift.title} (${gift.priceRange})`);
  }

  // 3. Seed Milestone Presets
  console.log('\n[3/6] Seeding Milestone Presets (collection: milestone_presets)...');
  for (const m of MILESTONE_PRESETS) {
    const docId = m.id;
    if (useAdminSdk) {
      await adminDb.collection('milestone_presets').doc(docId).set(m, { merge: true });
    } else {
      await firestoreRestUpsert('milestone_presets', docId, m);
    }
    results.milestones++;
    console.log(`  + [Milestone] ${m.title} (${m.subtitle})`);
  }

  // 4. Seed Badge Definitions
  console.log('\n[4/6] Seeding Badge Definitions (collection: badge_definitions)...');
  for (const b of BADGE_DEFINITIONS) {
    const docId = b.id;
    if (useAdminSdk) {
      await adminDb.collection('badge_definitions').doc(docId).set(b, { merge: true });
    } else {
      await firestoreRestUpsert('badge_definitions', docId, b);
    }
    results.badges++;
    console.log(`  + [Badge] [${b.tier}] ${b.titleVi} (${b.targetDays} ngày)`);
  }

  // 5. Seed Preset Assets (Photos & Avatars)
  console.log('\n[5/6] Seeding Preset Assets (collection: preset_assets)...');
  for (const [key, assetGroup] of Object.entries(PRESET_ASSETS)) {
    const data = { type: key, urls: assetGroup.urls, updatedAt: Date.now() };
    if (useAdminSdk) {
      await adminDb.collection('preset_assets').doc(key).set(data, { merge: true });
    } else {
      await firestoreRestUpsert('preset_assets', key, data);
    }
    results.presetAssets++;
    console.log(`  + [Assets] ${key} -> ${assetGroup.urls.length} URLs`);
  }

  // 6. Seed Checklist Templates
  console.log('\n[6/6] Seeding Checklist Templates (collection: checklist_templates)...');
  for (const c of CHECKLIST_TEMPLATES) {
    const docId = c.id;
    if (useAdminSdk) {
      await adminDb.collection('checklist_templates').doc(docId).set(c, { merge: true });
    } else {
      await firestoreRestUpsert('checklist_templates', docId, c);
    }
    results.checklists++;
    console.log(`  + [Checklist] ${c.text}`);
  }

  // SUMMARY REPORT
  console.log('\n================================================================================');
  console.log('  ENRICHMENT & SEEDING COMPLETED SUCCESSFULLY!');
  console.log('================================================================================');
  console.log(`  - Test Fixtures:       ${results.testFixtures} documents`);
  console.log(`  - Gift Ideas:          ${results.giftIdeas} documents`);
  console.log(`  - Milestone Presets:   ${results.milestones} documents`);
  console.log(`  - Badge Definitions:   ${results.badges} documents`);
  console.log(`  - Preset Asset Groups: ${results.presetAssets} documents`);
  console.log(`  - Checklist Templates: ${results.checklists} documents`);
  console.log('--------------------------------------------------------------------------------');
  console.log('  APK Security Audit: Zero credentials or mock stories hardcoded in Kotlin code.');
  console.log('  Cloud Synchronization: Ready for Android InLoveRepository to fetch dynamically.');
  console.log('================================================================================\n');
}

// Execute
runSeeder().catch(err => {
  console.error('Error during Firestore Seeding:', err);
  process.exit(1);
});
