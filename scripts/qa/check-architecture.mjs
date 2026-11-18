#!/usr/bin/env node
// Architecture & SOLID guardrail check for InLove (Android, KMP shared, iOS).
// Checks:
//   ARCH-01: Clean layer boundaries (no Room/Firestore in UI; no Android/appplugin/Firebase in shared commonMain)
//   ARCH-02: SOLID file-size budget (<= 550 lines for new/refactored packages) & refactored/legacy ratchets
//   ARCH-03: Design system unification (no OutlinedTextFieldDefaults.colors outside ui/designsystem;
//            no Color.Gray / Color.LightGray in refactored UI directories)
//   ARCH-04: Cross-platform monetization SKU parity (vip_lifetime, vip_monthly, vip_yearly)
//   ARCH-05: No internal/database jargon in UI string literals (app/.../ui and iosApp/InLoveIos)
// Usage: node scripts/qa/check-architecture.mjs [--root <dir>]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const SKIP_DIRS = new Set(['.git', '.gradle', '.build', 'build', 'DerivedData', 'node_modules', 'xcuserdata']);

export const DEFAULT_MAX_LINES = 550;

export const REFACTORED_FILE_CAPS = {
  'app/src/main/java/com/example/ui/screens/Dialogs.kt': 350,
  'app/src/main/java/com/example/ui/screens/LoveHomeScreen.kt': 550,
  'app/src/main/java/com/example/ui/screens/GiftScreen.kt': 650,
  'app/src/main/java/com/example/ui/screens/AppModals.kt': 680,
  'app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt': 1550,
};

export const LEGACY_RATCHET_CAPS = {
  'app/src/main/java/com/example/ui/screens/MemoriesGridScreen.kt': 1551,
  'app/src/main/java/com/example/ui/screens/CalendarScreen.kt': 1526,
  'app/src/main/java/com/example/ui/components/MilestoneBadgeTracker.kt': 1476,
  'app/src/main/java/com/example/ui/components/AnniversaryMemoriesWidget.kt': 1342,
  'app/src/main/java/com/example/ui/screens/AuthScreen.kt': 1201,
  'app/src/main/java/com/example/ui/screens/PairingScreen.kt': 1176,
  'app/src/main/java/com/example/data/repository/AuthRepository.kt': 1171,
  'app/src/main/java/com/example/ui/components/Material3DatePicker.kt': 1156,
  'app/src/main/java/com/example/ui/screens/SettingsScreen.kt': 1071,
  'app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt': 873,
  'app/src/main/java/com/example/ui/screens/PaywallScreen.kt': 756,
  'app/src/main/java/com/example/ui/components/LoveDaysCalculatorWidget.kt': 747,
  'app/src/main/java/com/example/ui/screens/OnboardingScreen.kt': 730,
  'app/src/main/java/com/example/ui/util/Localization.kt': 728,
  'app/src/main/java/com/example/MainActivity.kt': 665,
  'app/src/main/java/com/example/ui/components/CommonComponents.kt': 634,
  'app/src/main/java/com/example/ui/screens/EditMyProfileDialog.kt': 563,
};

export const EXPECTED_SKUS = ['vip_lifetime', 'vip_monthly', 'vip_yearly'];

const STRICT_PACKAGE_PREFIXES = [
  'shared/src/',
  'app/src/main/java/com/example/ui/designsystem/',
  'app/src/main/java/com/example/ui/dialogs/',
  'app/src/main/java/com/example/ui/viewmodel/controllers/',
  'app/src/main/java/com/example/ui/screens/gift/',
  'app/src/main/java/com/example/ui/screens/home/',
  'app/src/main/java/com/example/ui/screens/calendar/',
  'app/src/main/java/com/example/ui/screens/guide/',
  'app/src/main/java/com/example/ui/screens/paywall/',
  'app/src/main/java/com/example/data/dossier/',
  'app/src/main/java/com/example/data/ai/',
  'app/src/main/java/com/example/monetization/',
  'iosApp/',
];

const NO_GRAY_PREFIXES = [
  'app/src/main/java/com/example/ui/designsystem/',
  'app/src/main/java/com/example/ui/dialogs/',
  'app/src/main/java/com/example/ui/screens/gift/',
  'app/src/main/java/com/example/ui/screens/home/',
  'app/src/main/java/com/example/ui/screens/calendar/',
];

const toPosix = (p) => p.split(path.sep).join('/');

function* walkFiles(dir, exts, excludeDirs = new Set()) {
  if (!fs.existsSync(dir)) return;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => (a.name < b.name ? -1 : 1))) {
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name) || excludeDirs.has(entry.name)) continue;
      yield* walkFiles(path.join(dir, entry.name), exts, excludeDirs);
    } else if (entry.isFile() && (!exts || exts.has(path.extname(entry.name)))) {
      yield path.join(dir, entry.name);
    }
  }
}

// Blank out /* ... */ and // ... comments while preserving string literals and line breaks.
export function stripComments(src) {
  return src.replace(
    /"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\*[\s\S]*?\*\/|\/\/[^\r\n]*/g,
    (m) => (m.startsWith('//') || m.startsWith('/*') ? m.replace(/[^\n]/g, ' ') : m),
  );
}

export function countLines(src) {
  if (!src) return 0;
  return src.replace(/\r?\n$/, '').split(/\r?\n/).length;
}

function checkArch01(root) {
  const problems = [];
  const uiDir = path.join(root, 'app/src/main/java/com/example/ui');
  const uiForbidden = /^\s*import\s+(?:androidx\.room|com\.google\.firebase\.firestore)(?:\.|\s|;|$)/;
  for (const file of walkFiles(uiDir, new Set(['.kt', '.java']))) {
    const rel = toPosix(path.relative(root, file));
    const lines = stripComments(fs.readFileSync(file, 'utf8')).split(/\r?\n/);
    lines.forEach((line, idx) => {
      if (uiForbidden.test(line)) {
        problems.push(`${rel}:${idx + 1}: [ARCH-01] UI layer must not import Room or Firestore (${line.trim()})`);
      }
    });
  }

  const commonMainDir = path.join(root, 'shared/src/commonMain');
  const sharedForbidden = /^\s*import\s+(?:android|com\.app\.plugin|com\.google\.firebase)(?:\.|\s|;|$)/;
  for (const file of walkFiles(commonMainDir, new Set(['.kt']))) {
    const rel = toPosix(path.relative(root, file));
    const lines = stripComments(fs.readFileSync(file, 'utf8')).split(/\r?\n/);
    lines.forEach((line, idx) => {
      if (sharedForbidden.test(line)) {
        problems.push(`${rel}:${idx + 1}: [ARCH-01] KMP commonMain must not import platform SDK (${line.trim()})`);
      }
    });
  }
  return problems;
}

function budgetFor(rel) {
  if (rel in REFACTORED_FILE_CAPS) return { max: REFACTORED_FILE_CAPS[rel], label: 'refactored cap' };
  if (rel in LEGACY_RATCHET_CAPS) return { max: LEGACY_RATCHET_CAPS[rel], label: 'legacy ratchet' };
  if (STRICT_PACKAGE_PREFIXES.some((p) => rel.startsWith(p))) return { max: DEFAULT_MAX_LINES, label: 'SOLID file budget' };
  if (rel.startsWith('app/src/main/java/com/example/')) return { max: DEFAULT_MAX_LINES, label: 'SOLID file budget' };
  return null;
}

function checkArch02(root) {
  const problems = [];
  const seen = new Set();
  const scanRoots = ['app/src/main/java/com/example', 'shared/src', 'iosApp'];
  const exts = new Set(['.kt', '.swift']);

  for (const sub of scanRoots) {
    for (const file of walkFiles(path.join(root, sub), exts)) {
      const rel = toPosix(path.relative(root, file));
      if (seen.has(rel)) continue;
      seen.add(rel);
      const budget = budgetFor(rel);
      if (!budget) continue;
      const lines = countLines(fs.readFileSync(file, 'utf8'));
      if (lines > budget.max) {
        problems.push(`${rel}: [ARCH-02] ${lines} lines exceeds ${budget.label} (${budget.max})`);
      }
    }
  }
  return problems;
}

function checkArch03(root) {
  const problems = [];
  const uiDir = path.join(root, 'app/src/main/java/com/example/ui');
  const dsPrefix = 'app/src/main/java/com/example/ui/designsystem/';

  for (const file of walkFiles(uiDir, new Set(['.kt']))) {
    const rel = toPosix(path.relative(root, file));
    const lines = stripComments(fs.readFileSync(file, 'utf8')).split(/\r?\n/);

    if (!rel.startsWith(dsPrefix)) {
      lines.forEach((line, idx) => {
        if (/\bOutlinedTextFieldDefaults\.colors\s*\(/.test(line)) {
          problems.push(
            `${rel}:${idx + 1}: [ARCH-03] Use inLoveOutlinedTextFieldColors() from ui/designsystem instead of OutlinedTextFieldDefaults.colors(`,
          );
        }
      });
    }

    if (NO_GRAY_PREFIXES.some((p) => rel.startsWith(p))) {
      lines.forEach((line, idx) => {
        if (/\bColor\.(?:Gray|LightGray)\b/.test(line)) {
          problems.push(
            `${rel}:${idx + 1}: [ARCH-03] Forbidden low-contrast Color.Gray/LightGray in refactored UI; use TextDarkPlum or TextSubtlePlum`,
          );
        }
      });
    }
  }
  return problems;
}

function findMonetizationPolicy(root) {
  const canonical = path.join(root, 'shared/src/commonMain/kotlin/com/example/shared/monetization/MonetizationPolicy.kt');
  if (fs.existsSync(canonical)) return canonical;
  const commonMain = path.join(root, 'shared/src/commonMain');
  for (const file of walkFiles(commonMain, new Set(['.kt']))) {
    if (path.basename(file) === 'MonetizationPolicy.kt') return file;
  }
  return null;
}

function checkStoreKitSkus(file, rel) {
  let data;
  try {
    data = JSON.parse(fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
  } catch (e) {
    return [`${rel}: [ARCH-04] invalid StoreKit JSON (${e.message})`];
  }
  const found = new Set();
  for (const p of data.products ?? []) if (p?.productID) found.add(p.productID);
  for (const p of data.nonRenewingSubscriptions ?? []) if (p?.productID) found.add(p.productID);
  for (const g of data.subscriptionGroups ?? []) {
    for (const s of g?.subscriptions ?? []) if (s?.productID) found.add(s.productID);
  }
  const sorted = [...found].sort();
  if (JSON.stringify(sorted) !== JSON.stringify(EXPECTED_SKUS)) {
    return [
      `${rel}: [ARCH-04] SKU mismatch: found [${sorted.join(', ')}], expected [${EXPECTED_SKUS.join(', ')}]`,
    ];
  }
  return [];
}

function checkSourceSkus(files, root, { strictAnyVipLiteral }) {
  const foundExpected = new Set();
  const unexpected = [];
  const allVipRe = /"((?:inlove_)?vip_[a-z0-9_]+)"/g;
  const skuLikeRe = /"((?:inlove_)?vip_(?:weekly|monthly|quarterly|yearly|annual|lifetime|forever|trial|pro|plus|premium)[a-z0-9_]*|inlove_vip_[a-z0-9_]+)"/g;

  for (const file of files) {
    const rel = toPosix(path.relative(root, file));
    const cleaned = stripComments(fs.readFileSync(file, 'utf8'));
    for (const sku of EXPECTED_SKUS) {
      if (cleaned.includes(`"${sku}"`)) foundExpected.add(sku);
    }
    const re = strictAnyVipLiteral ? allVipRe : skuLikeRe;
    for (const m of cleaned.matchAll(re)) {
      if (!EXPECTED_SKUS.includes(m[1])) unexpected.push(`${rel}: unexpected SKU literal "${m[1]}"`);
    }
  }

  const problems = [];
  const missing = EXPECTED_SKUS.filter((s) => !foundExpected.has(s));
  if (missing.length) {
    const label = files.length === 1 ? toPosix(path.relative(root, files[0])) : 'iosApp/Packages/InLoveIosCore';
    problems.push(`${label}: [ARCH-04] missing SKU(s): ${missing.join(', ')}`);
  }
  for (const u of unexpected) problems.push(`[ARCH-04] ${u}`);
  return problems;
}

function checkArch04(root) {
  const vipProductIdsPath = path.join(root, 'app/src/main/java/com/example/billing/VipProductIds.kt');
  const policyPath = findMonetizationPolicy(root);
  const storekitPath = path.join(root, 'iosApp/StoreKit/InLove.storekit');
  const iosCoreDir = path.join(root, 'iosApp/Packages/InLoveIosCore');
  const iosCoreSources = path.join(iosCoreDir, 'Sources');

  const iosCoreScanRoot = fs.existsSync(iosCoreSources) ? iosCoreSources : iosCoreDir;
  const iosCoreSwiftFiles = fs.existsSync(iosCoreDir)
    ? [...walkFiles(iosCoreScanRoot, new Set(['.swift']), new Set(['Tests']))]
    : [];

  const present = [
    fs.existsSync(vipProductIdsPath),
    Boolean(policyPath && fs.existsSync(policyPath)),
    fs.existsSync(storekitPath),
    iosCoreSwiftFiles.length > 0,
  ];
  if (!present.some(Boolean)) return [];

  const problems = [];
  if (!fs.existsSync(vipProductIdsPath)) {
    problems.push('app/src/main/java/com/example/billing/VipProductIds.kt: [ARCH-04] missing monetization target');
  } else {
    problems.push(...checkSourceSkus([vipProductIdsPath], root, { strictAnyVipLiteral: true }));
  }

  if (!policyPath || !fs.existsSync(policyPath)) {
    problems.push('shared/src/commonMain/kotlin/com/example/shared/monetization/MonetizationPolicy.kt: [ARCH-04] missing monetization target');
  } else {
    problems.push(...checkSourceSkus([policyPath], root, { strictAnyVipLiteral: true }));
  }

  if (!fs.existsSync(storekitPath)) {
    problems.push('iosApp/StoreKit/InLove.storekit: [ARCH-04] missing monetization target');
  } else {
    problems.push(...checkStoreKitSkus(storekitPath, 'iosApp/StoreKit/InLove.storekit'));
  }

  if (!fs.existsSync(iosCoreDir)) {
    problems.push('iosApp/Packages/InLoveIosCore: [ARCH-04] missing monetization target');
  } else if (iosCoreSwiftFiles.length === 0) {
    problems.push('iosApp/Packages/InLoveIosCore: [ARCH-04] no .swift source files found');
  } else {
    problems.push(...checkSourceSkus(iosCoreSwiftFiles, root, { strictAnyVipLiteral: false }));
  }
  return problems;
}

const ARCH05_JARGON_RE =
  /cơ sở dữ liệu|from the database|in the database|\bSQLite\b|\bRoom DB\b|\bSharedPreferences\b|\bFirestore\b/i;

const LOG_CALL_LINE_RE =
  /^\s*(?:(?:android\.util\.)?Log\.[vdiewa]|Timber\.[vdiewa]|println|print|debugPrint|NSLog|os_log|logger\.)\b|\b(?:android\.util\.)?Log\.[vdiewa]\s*\(|\b(?:Timber\.[vdiewa]|NSLog|debugPrint|os_log)\s*\(/;

function extractStringLiterals(src, isKotlin) {
  const literals = [];
  let line = 1;
  for (let i = 0; i < src.length; i++) {
    const ch = src[i];
    if (ch === '\n') {
      line++;
      continue;
    }
    if (ch === '/' && src[i + 1] === '/') {
      while (i < src.length && src[i] !== '\n') i++;
      if (i < src.length && src[i] === '\n') line++;
      continue;
    }
    if (ch === '/' && src[i + 1] === '*') {
      i += 2;
      while (i < src.length && !(src[i] === '*' && src[i + 1] === '/')) {
        if (src[i] === '\n') line++;
        i++;
      }
      i++;
      continue;
    }
    if (isKotlin && ch === "'") {
      i++;
      while (i < src.length && src[i] !== "'" && src[i] !== '\n') {
        if (src[i] === '\\') i++;
        i++;
      }
      if (i < src.length && src[i] === '\n') line++;
      continue;
    }
    if (src.startsWith('"""', i)) {
      const startLine = line;
      i += 3;
      let text = '';
      while (i < src.length && !src.startsWith('"""', i)) {
        if (src[i] === '\n') line++;
        text += src[i++];
      }
      i += 2;
      literals.push({ lineNo: startLine, text });
      continue;
    }
    if (ch === '"') {
      const startLine = line;
      i++;
      let text = '';
      while (i < src.length && src[i] !== '"' && src[i] !== '\n') {
        if (src[i] === '\\' && i + 1 < src.length) {
          text += src[i] + src[i + 1];
          if (src[i + 1] === '\n') line++;
          i += 2;
          continue;
        }
        text += src[i++];
      }
      if (i < src.length && src[i] === '\n') line++;
      literals.push({ lineNo: startLine, text });
    }
  }
  return literals;
}

function checkArch05(root) {
  const problems = [];
  const targets = [
    { dir: path.join(root, 'app/src/main/java/com/example/ui'), exts: new Set(['.kt']), isKotlin: true },
    { dir: path.join(root, 'iosApp/InLoveIos'), exts: new Set(['.swift']), isKotlin: false },
  ];

  for (const { dir, exts, isKotlin } of targets) {
    for (const file of walkFiles(dir, exts)) {
      const rel = toPosix(path.relative(root, file));
      const src = fs.readFileSync(file, 'utf8');
      const lines = src.split(/\r?\n/);
      for (const { lineNo, text } of extractStringLiterals(src, isKotlin)) {
        const lineText = lines[lineNo - 1] ?? '';
        const prevLine = lineNo > 1 ? lines[lineNo - 2] ?? '' : '';
        if (LOG_CALL_LINE_RE.test(lineText) || /\b(?:Log\.[vdiewa]|Timber\.[vdiewa]|NSLog|debugPrint|os_log)\s*\(\s*$/.test(prevLine)) {
          continue;
        }
        const m = ARCH05_JARGON_RE.exec(text);
        if (m) {
          problems.push(`${rel}:${lineNo}: [ARCH-05] Internal/database jargon in UI string literal ("${m[0]}")`);
        }
      }
    }
  }
  return problems;
}

export function checkArchitecture(root) {
  return [
    ...checkArch01(root),
    ...checkArch02(root),
    ...checkArch03(root),
    ...checkArch04(root),
    ...checkArch05(root),
  ];
}

function main() {
  const args = process.argv.slice(2);
  const root = args.includes('--root') ? path.resolve(args[args.indexOf('--root') + 1]) : REPO;
  const problems = checkArchitecture(root);
  if (problems.length) {
    console.error(problems.join('\n'));
    console.error(`FAIL: ${problems.length} architecture violation(s)`);
    process.exit(1);
  }
  console.log('OK: architecture checks passed (ARCH-01..ARCH-05)');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();
