#!/usr/bin/env node
// Kiểm cấu hình nền tảng iOS (bảng 4.7 của p5-native-features.md), không phụ thuộc gói ngoài, chạy được trên Windows.
// Dùng: node scripts/ios/check-platform-config.mjs --section <app-info|bgtask|widget|entitlements|privacy|strings|icon>
//        [--target app|widget|all] [--release] [--root <thư mục>]
// In `OK <section>` (exit 0) hoặc `FAIL <section>: <lý do>` (exit 1).
// Khi một part thêm khóa: thêm vào bảng 4.7 và vào đây cùng lúc.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const GROUP = 'group.com.aistudio.inlove.kmrv';
const PROTECTION = 'NSFileProtectionCompleteUntilFirstUserAuthentication';
const REFRESH_ID = 'com.aistudio.inlove.kmrv.refresh';
const FORBIDDEN_INFO_KEYS = [/^NSLocation/, /^NSMicrophoneUsageDescription$/, /^NSPhotoLibraryAddUsageDescription$/, /^NSFaceIDUsageDescription$/];
// Danh sách mã lý do cho phép theo nhóm API (r1). Mã lạ hoặc sai nhóm là FAIL.
const ALLOWED_REASONS = {
  NSPrivacyAccessedAPICategoryUserDefaults: ['CA92.1', '1C8F.1'],
  NSPrivacyAccessedAPICategorySystemBootTime: ['35F9.1'],
  NSPrivacyAccessedAPICategoryFileTimestamp: ['C617.1', '3B52.1', '0A2A.1'],
  NSPrivacyAccessedAPICategoryDiskSpace: ['E174.1', '85F4.1'],
};

const APP = 'iosApp/iosApp';
const WIDGET = 'iosApp/InLoveWidget';
const FILES = {
  appInfo: `${APP}/Info.plist`,
  appEnt: `${APP}/iosApp.entitlements`,
  appPrivacy: `${APP}/PrivacyInfo.xcprivacy`,
  widgetInfo: `${WIDGET}/Info.plist`,
  widgetEnt: `${WIDGET}/InLoveWidget.entitlements`,
  widgetPrivacy: `${WIDGET}/PrivacyInfo.xcprivacy`,
  assets: `${APP}/Assets.xcassets`,
};

// ---- parsers ---------------------------------------------------------------
const unescapeXml = (s) =>
  s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

/** XML plist -> giá trị JS (dict, array, string, number, boolean). */
export function parsePlist(xml) {
  const body = xml.replace(/<\?[\s\S]*?\?>|<!--[\s\S]*?-->|<!DOCTYPE[^>]*>/g, '');
  const tokens = [];
  const re = /<(\/?)([A-Za-z]+)[^>]*?(\/?)>|([^<]+)/g;
  for (let m; (m = re.exec(body)); ) {
    if (m[4] !== undefined) tokens.push({ t: 'text', text: m[4] });
    else tokens.push({ t: m[1] ? 'close' : m[3] ? 'self' : 'open', name: m[2] });
  }
  let i = 0;
  const skipWs = () => { while (tokens[i]?.t === 'text' && !tokens[i].text.trim()) i++; };
  const fail = (msg) => { throw new Error(`invalid plist: ${msg}`); };
  const textUntilClose = (name) => {
    let s = '';
    while (tokens[i]?.t === 'text') s += tokens[i++].text;
    if (tokens[i]?.t !== 'close' || tokens[i].name !== name) fail(`expected </${name}>`);
    i++;
    return unescapeXml(s);
  };
  const value = () => {
    skipWs();
    const k = tokens[i++];
    if (!k || k.t === 'text' || k.t === 'close') fail('unexpected token');
    const { name } = k;
    if (name === 'true' || name === 'false') {
      if (k.t === 'open') textUntilClose(name);
      return name === 'true';
    }
    if (k.t === 'self') return name === 'dict' ? {} : name === 'array' ? [] : name === 'integer' || name === 'real' ? 0 : '';
    if (name === 'dict') {
      const o = {};
      for (;;) {
        skipWs();
        if (tokens[i]?.t === 'close' && tokens[i].name === 'dict') { i++; return o; }
        if (tokens[i]?.t !== 'open' || tokens[i].name !== 'key') fail('expected <key>');
        i++;
        const key = textUntilClose('key');
        o[key] = value();
      }
    }
    if (name === 'array') {
      const a = [];
      for (;;) {
        skipWs();
        if (tokens[i]?.t === 'close' && tokens[i].name === 'array') { i++; return a; }
        a.push(value());
      }
    }
    const s = textUntilClose(name);
    return name === 'integer' || name === 'real' ? Number(s) : s;
  };
  skipWs();
  if (tokens[i]?.name !== 'plist') fail('missing <plist>');
  i++;
  const v = value();
  skipWs();
  if (tokens[i]?.t !== 'close' || tokens[i].name !== 'plist') fail('expected </plist>');
  i++;
  skipWs();
  if (i < tokens.length) fail('unexpected content after </plist>');
  return v;
}

/** `"key" = "value";` -> {key: value}; bỏ chú thích. */
export function parseStrings(text) {
  const clean = text.replace(/^\uFEFF/, '').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
  const un = (s) => s.replace(/\\(["\\nt])/g, (_, c) => ({ n: '\n', t: '\t' })[c] ?? c);
  const out = {};
  for (const m of clean.matchAll(/"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;/g)) out[un(m[1])] = un(m[2]);
  return out;
}

// ---- file access -----------------------------------------------------------
function read(root, rel) {
  const f = path.join(root, rel);
  if (!fs.existsSync(f)) throw new Error(`missing file ${rel}`);
  return fs.readFileSync(f, 'utf8');
}
function loadPlist(root, rel) {
  try {
    const v = parsePlist(read(root, rel));
    if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('root is not a dict');
    return v;
  } catch (e) {
    throw new Error(`${rel}: ${e.message}`);
  }
}
const arr = (v) => (Array.isArray(v) ? v : []);

// ---- sections: mỗi hàm trả mảng lý do lỗi ----------------------------------
function appInfo(root) {
  const p = [];
  const info = loadPlist(root, FILES.appInfo);
  const eq = (key, want) => { if (info[key] !== want) p.push(`${key} must be ${JSON.stringify(want)}`); };
  if (typeof info.CFBundleDisplayName !== 'string' || !info.CFBundleDisplayName) p.push('CFBundleDisplayName missing');
  eq('CFBundleDevelopmentRegion', 'en');
  for (const l of ['vi', 'en']) if (!arr(info.CFBundleLocalizations).includes(l)) p.push(`CFBundleLocalizations must contain ${l}`);
  if (info.UILaunchScreen?.UIColorName !== 'LaunchBackground') p.push('UILaunchScreen.UIColorName must be "LaunchBackground"');
  eq('UIUserInterfaceStyle', 'Light');
  const ori = arr(info.UISupportedInterfaceOrientations);
  if (!ori.length || ori.some((o) => o !== 'UIInterfaceOrientationPortrait')) p.push('UISupportedInterfaceOrientations must be portrait only');
  eq('CADisableMinimumFrameDurationOnPhone', true);
  eq('ITSAppUsesNonExemptEncryption', false);
  if (!arr(info.CFBundleURLTypes).some((t) => arr(t?.CFBundleURLSchemes).includes('inlove'))) p.push('CFBundleURLTypes must contain scheme "inlove"');
  if (typeof info.NSCameraUsageDescription !== 'string' || !info.NSCameraUsageDescription.trim()) p.push('NSCameraUsageDescription missing or empty');
  for (const k of Object.keys(info)) if (FORBIDDEN_INFO_KEYS.some((re) => re.test(k))) p.push(`forbidden key ${k}`);
  return p;
}

function bgtask(root) {
  const p = [];
  const info = loadPlist(root, FILES.appInfo);
  if (!arr(info.BGTaskSchedulerPermittedIdentifiers).includes(REFRESH_ID)) p.push(`BGTaskSchedulerPermittedIdentifiers must contain ${REFRESH_ID}`);
  if (!arr(info.UIBackgroundModes).includes('fetch')) p.push('UIBackgroundModes must contain fetch');
  return p;
}

function widget(root) {
  const id = loadPlist(root, FILES.widgetInfo).NSExtension?.NSExtensionPointIdentifier;
  return id === 'com.apple.widgetkit-extension' ? [] : [`NSExtension.NSExtensionPointIdentifier must be "com.apple.widgetkit-extension" (got ${JSON.stringify(id)})`];
}

function entitlements(root, targets, release) {
  const p = [];
  const common = (ent, label) => {
    if (!arr(ent['com.apple.security.application-groups']).includes(GROUP)) p.push(`${label}: application-groups must contain ${GROUP}`);
    if (ent['com.apple.developer.default-data-protection'] !== PROTECTION) p.push(`${label}: default-data-protection must be ${PROTECTION}`);
  };
  if (targets.includes('app')) {
    const ent = loadPlist(root, FILES.appEnt);
    common(ent, 'app');
    if ('aps-environment' in ent) p.push('app: aps-environment must not be present (no server push)');
    if (release && !arr(ent['com.apple.developer.associated-domains']).includes('applinks:inlove.app')) p.push('app: --release requires associated-domains applinks:inlove.app');
  }
  if (targets.includes('widget')) {
    const ent = loadPlist(root, FILES.widgetEnt);
    common(ent, 'widget');
    if ('com.apple.developer.associated-domains' in ent) p.push('widget: must not have associated-domains');
    const extra = Object.keys(ent).filter((k) => k !== 'com.apple.security.application-groups' && k !== 'com.apple.developer.default-data-protection');
    if (extra.length) p.push(`widget: unexpected entitlement keys ${extra.join(', ')}`);
  }
  return p;
}

function privacy(root, targets) {
  const p = [];
  for (const target of targets) {
    const rel = target === 'app' ? FILES.appPrivacy : FILES.widgetPrivacy;
    const m = loadPlist(root, rel);
    if (typeof m.NSPrivacyTracking !== 'boolean') p.push(`${target}: NSPrivacyTracking must be a boolean`);
    else if (target === 'widget' && m.NSPrivacyTracking) p.push('widget: NSPrivacyTracking must be false');
    const apis = arr(m.NSPrivacyAccessedAPITypes);
    if (target === 'widget' && apis.length) p.push('widget: must not declare any required-reason API (widget only reads JSON)');
    for (const [n, entry] of apis.entries()) {
      const cat = entry?.NSPrivacyAccessedAPIType;
      const reasons = arr(entry?.NSPrivacyAccessedAPITypeReasons);
      if (typeof cat !== 'string' || !cat) { p.push(`${target}: NSPrivacyAccessedAPITypes[${n}] has no category`); continue; }
      const allowed = ALLOWED_REASONS[cat];
      if (!allowed) { p.push(`${target}: unknown category ${cat}`); continue; }
      if (!reasons.length) p.push(`${target}: ${cat} has no reason code`);
      for (const r of reasons) if (!allowed.includes(r)) p.push(`${target}: reason ${r} not allowed for ${cat} (allowed: ${allowed.join(', ')})`);
    }
  }
  return p;
}

function strings(root) {
  const p = [];
  const info = loadPlist(root, FILES.appInfo);
  const keys = ['CFBundleDisplayName', ...Object.keys(info).filter((k) => /^NS.*UsageDescription$/.test(k))];
  for (const lang of ['vi', 'en']) {
    const rel = `${APP}/${lang}.lproj/InfoPlist.strings`;
    if (!fs.existsSync(path.join(root, rel))) { p.push(`missing ${lang}.lproj/InfoPlist.strings`); continue; }
    const s = parseStrings(read(root, rel));
    for (const k of keys) if (!s[k]) p.push(`${k} not localized in ${lang}.lproj`);
  }
  return p;
}

function icon(root) {
  const p = [];
  const dir = `${FILES.assets}/AppIcon.appiconset`;
  let contents;
  try { contents = JSON.parse(read(root, `${dir}/Contents.json`)); } catch (e) { return [`${dir}/Contents.json: ${e.message}`]; }
  const e1024 = arr(contents.images).filter((i) => i.size === '1024x1024');
  if (!e1024.length) p.push('AppIcon has no 1024x1024 entry');
  for (const e of e1024) if (!e.filename || !fs.existsSync(path.join(root, dir, e.filename))) p.push(`AppIcon 1024x1024 entry points to missing file ${e.filename ?? '(no filename)'}`);
  if (!fs.existsSync(path.join(root, `${FILES.assets}/LaunchBackground.colorset/Contents.json`))) p.push('missing LaunchBackground.colorset');
  return p;
}

export const SECTIONS = ['app-info', 'bgtask', 'widget', 'entitlements', 'privacy', 'strings', 'icon'];

/** Trả mảng lý do lỗi; rỗng = OK. */
export function check({ section, target = 'all', release = false, root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..') }) {
  const targets = target === 'all' ? ['app', 'widget'] : [target];
  try {
    if (!SECTIONS.includes(section)) return [`unknown section ${section} (use ${SECTIONS.join(', ')})`];
    if (!['app', 'widget', 'all'].includes(target)) return [`unknown target ${target}`];
    switch (section) {
      case 'app-info': return appInfo(root);
      case 'bgtask': return bgtask(root);
      case 'widget': return widget(root);
      case 'entitlements': return entitlements(root, targets, release);
      case 'privacy': return privacy(root, targets);
      case 'strings': return strings(root);
      default: return icon(root);
    }
  } catch (e) {
    return [e.message];
  }
}

function main(argv) {
  const opts = { release: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--release') opts.release = true;
    else if (a === '--section' || a === '--target' || a === '--root') opts[a.slice(2)] = argv[++i];
    else { console.log(`FAIL ${opts.section ?? '-'}: unknown argument ${a}`); return 1; }
  }
  if (opts.root) opts.root = path.resolve(opts.root);
  const problems = check(opts);
  if (problems.length) { console.log(`FAIL ${opts.section}: ${problems.join('; ')}`); return 1; }
  console.log(`OK ${opts.section}`);
  return 0;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) process.exitCode = main(process.argv.slice(2));
