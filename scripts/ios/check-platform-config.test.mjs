// node --test scripts/ios/check-platform-config.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { check, parsePlist, parseStrings } from './check-platform-config.mjs';

const CLI = path.join(path.dirname(fileURLToPath(import.meta.url)), 'check-platform-config.mjs');
const GROUP = 'group.com.aistudio.inlove.kmrv';
const PROTECTION = 'NSFileProtectionCompleteUntilFirstUserAuthentication';
const REFRESH = 'com.aistudio.inlove.kmrv.refresh';
const CAMERA_VI = 'InLove cần dùng máy ảnh để bạn chụp ảnh đại diện và ảnh kỷ niệm.';

// ---- fixture helpers -------------------------------------------------------
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
function ser(v) {
  if (typeof v === 'string') return `<string>${esc(v)}</string>`;
  if (typeof v === 'boolean') return v ? '<true/>' : '<false/>';
  if (typeof v === 'number') return `<integer>${v}</integer>`;
  if (Array.isArray(v)) return `<array>${v.map(ser).join('')}</array>`;
  return `<dict>${Object.entries(v).map(([k, x]) => `<key>${esc(k)}</key>${ser(x)}`).join('')}</dict>`;
}
const plist = (obj) =>
  `<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n<plist version="1.0">\n${ser(obj)}\n</plist>\n`;

function good() {
  return {
    appInfo: {
      CFBundleDisplayName: 'InLove',
      CFBundleDevelopmentRegion: 'en',
      CFBundleLocalizations: ['vi', 'en'],
      UILaunchScreen: { UIColorName: 'LaunchBackground' },
      UIUserInterfaceStyle: 'Light',
      UISupportedInterfaceOrientations: ['UIInterfaceOrientationPortrait'],
      CADisableMinimumFrameDurationOnPhone: true,
      ITSAppUsesNonExemptEncryption: false,
      CFBundleURLTypes: [{ CFBundleURLSchemes: ['inlove'] }],
      BGTaskSchedulerPermittedIdentifiers: [REFRESH],
      UIBackgroundModes: ['fetch'],
      NSCameraUsageDescription: CAMERA_VI,
    },
    appEnt: {
      'com.apple.security.application-groups': [GROUP],
      'com.apple.developer.default-data-protection': PROTECTION,
      'com.apple.developer.devicecheck.appattest-environment': 'production',
    },
    widgetInfo: {
      CFBundleDisplayName: 'InLove',
      NSExtension: { NSExtensionPointIdentifier: 'com.apple.widgetkit-extension' },
    },
    widgetEnt: {
      'com.apple.security.application-groups': [GROUP],
      'com.apple.developer.default-data-protection': PROTECTION,
    },
    appPrivacy: {
      NSPrivacyTracking: false,
      NSPrivacyTrackingDomains: [],
      NSPrivacyCollectedDataTypes: [],
      NSPrivacyAccessedAPITypes: [
        { NSPrivacyAccessedAPIType: 'NSPrivacyAccessedAPICategoryUserDefaults', NSPrivacyAccessedAPITypeReasons: ['CA92.1'] },
        { NSPrivacyAccessedAPIType: 'NSPrivacyAccessedAPICategorySystemBootTime', NSPrivacyAccessedAPITypeReasons: ['35F9.1'] },
      ],
    },
    widgetPrivacy: {
      NSPrivacyTracking: false,
      NSPrivacyTrackingDomains: [],
      NSPrivacyCollectedDataTypes: [],
      NSPrivacyAccessedAPITypes: [],
    },
    strings: {
      vi: `/* vi */\n"CFBundleDisplayName" = "InLove";\n"NSCameraUsageDescription" = "${CAMERA_VI}";\n`,
      en: `"CFBundleDisplayName" = "InLove";\n"NSCameraUsageDescription" = "InLove needs the camera so you can take profile and memory photos.";\n`,
    },
    icon: {
      appIcon: { images: [{ filename: 'AppIcon-1024.png', idiom: 'universal', platform: 'ios', size: '1024x1024' }], info: { author: 'xcode', version: 1 } },
      appIconFile: true,
      launchColorset: true,
    },
  };
}

function write(fx) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'plat-cfg-'));
  const put = (rel, text) => {
    const f = path.join(root, rel);
    fs.mkdirSync(path.dirname(f), { recursive: true });
    fs.writeFileSync(f, text, 'utf8');
  };
  put('iosApp/iosApp/Info.plist', plist(fx.appInfo));
  put('iosApp/iosApp/iosApp.entitlements', plist(fx.appEnt));
  put('iosApp/iosApp/PrivacyInfo.xcprivacy', plist(fx.appPrivacy));
  put('iosApp/InLoveWidget/Info.plist', plist(fx.widgetInfo));
  put('iosApp/InLoveWidget/InLoveWidget.entitlements', plist(fx.widgetEnt));
  put('iosApp/InLoveWidget/PrivacyInfo.xcprivacy', plist(fx.widgetPrivacy));
  if (fx.strings.vi != null) put('iosApp/iosApp/vi.lproj/InfoPlist.strings', fx.strings.vi);
  if (fx.strings.en != null) put('iosApp/iosApp/en.lproj/InfoPlist.strings', fx.strings.en);
  const assets = 'iosApp/iosApp/Assets.xcassets';
  put(`${assets}/AppIcon.appiconset/Contents.json`, JSON.stringify(fx.icon.appIcon));
  if (fx.icon.appIconFile) put(`${assets}/AppIcon.appiconset/AppIcon-1024.png`, 'png');
  if (fx.icon.launchColorset) put(`${assets}/LaunchBackground.colorset/Contents.json`, '{"colors":[]}');
  return root;
}

const run = (fx, opts) => check({ root: write(fx), ...opts });
const passes = (fx, opts) => assert.deepEqual(run(fx, opts), []);
const failsWith = (fx, opts, re) => {
  const problems = run(fx, opts);
  assert.ok(problems.length > 0, 'expected a failure but section passed');
  assert.match(problems.join(' | '), re);
};

// ---- parsers ---------------------------------------------------------------
test('plistParserReadsNestedValues', () => {
  const v = parsePlist(plist({ a: 'x &amp; y', b: [true, false, 3], c: { d: [] } }));
  assert.deepEqual(v, { a: 'x &amp; y', b: [true, false, 3], c: { d: [] } });
});

test('plistParserRejectsMissingCloseAndTrailingContent', () => {
  const ok = plist({ a: 'x' });
  assert.deepEqual(parsePlist(`${ok}\n\n`), { a: 'x' });
  assert.throws(() => parsePlist(ok.replace('</plist>\n', '')), /expected <\/plist>/);
  assert.throws(() => parsePlist(ok.replace('</plist>', '</dict>')), /expected <\/plist>/);
  assert.throws(() => parsePlist(`${ok}</content>\n`), /after <\/plist>/);
  assert.throws(() => parsePlist(`${ok}stray\n`), /after <\/plist>/);
});

test('stringsParserIgnoresCommentsAndUnescapes', () => {
  const m = parseStrings('/* c */\n// line\n"A" = "x \\"q\\" // not a comment";\n"B" = "y";\n');
  assert.deepEqual(m, { A: 'x "q" // not a comment', B: 'y' });
});

// ---- app-info / bgtask -----------------------------------------------------
test('appInfoPassesOnCompleteFixture', () => {
  passes(good(), { section: 'app-info' });
  passes(good(), { section: 'bgtask' });
  passes(good(), { section: 'widget' });
  passes(good(), { section: 'entitlements' });
  passes(good(), { section: 'privacy' });
  passes(good(), { section: 'strings' });
  passes(good(), { section: 'icon' });
});

test('appInfoFailsWhenVietnameseLocalizationMissing', () => {
  const fx = good();
  fx.appInfo.CFBundleLocalizations = ['en'];
  failsWith(fx, { section: 'app-info' }, /CFBundleLocalizations.*vi/);
});

test('appInfoFailsOnLandscapeOrientation', () => {
  const fx = good();
  fx.appInfo.UISupportedInterfaceOrientations = ['UIInterfaceOrientationPortrait', 'UIInterfaceOrientationLandscapeLeft'];
  failsWith(fx, { section: 'app-info' }, /Landscape|orientation/i);
});

test('appInfoFailsWhenCameraDescriptionEmptyOrForbiddenKeyPresent', () => {
  let fx = good();
  fx.appInfo.NSCameraUsageDescription = '';
  failsWith(fx, { section: 'app-info' }, /NSCameraUsageDescription/);
  fx = good();
  delete fx.appInfo.NSCameraUsageDescription;
  failsWith(fx, { section: 'app-info' }, /NSCameraUsageDescription/);
  fx = good();
  fx.appInfo.NSLocationWhenInUseUsageDescription = 'x';
  failsWith(fx, { section: 'app-info' }, /NSLocationWhenInUseUsageDescription/);
  fx = good();
  fx.appInfo.NSMicrophoneUsageDescription = 'x';
  failsWith(fx, { section: 'app-info' }, /NSMicrophoneUsageDescription/);
  fx = good();
  fx.appInfo.NSPhotoLibraryAddUsageDescription = 'x';
  failsWith(fx, { section: 'app-info' }, /NSPhotoLibraryAddUsageDescription/);
});

test('appInfoFailsOnWrongScalarsAndUrlScheme', () => {
  let fx = good();
  fx.appInfo.UIUserInterfaceStyle = 'Dark';
  failsWith(fx, { section: 'app-info' }, /UIUserInterfaceStyle/);
  fx = good();
  fx.appInfo.ITSAppUsesNonExemptEncryption = true;
  failsWith(fx, { section: 'app-info' }, /ITSAppUsesNonExemptEncryption/);
  fx = good();
  fx.appInfo.CFBundleURLTypes = [{ CFBundleURLSchemes: ['other'] }];
  failsWith(fx, { section: 'app-info' }, /inlove/);
  fx = good();
  fx.appInfo.UILaunchScreen = {};
  failsWith(fx, { section: 'app-info' }, /LaunchBackground/);
});

test('bgtaskFailsWhenIdentifierMissing', () => {
  let fx = good();
  fx.appInfo.BGTaskSchedulerPermittedIdentifiers = [];
  failsWith(fx, { section: 'bgtask' }, new RegExp(REFRESH.replace(/\./g, '\\.')));
  fx = good();
  fx.appInfo.UIBackgroundModes = ['audio'];
  failsWith(fx, { section: 'bgtask' }, /fetch/);
});

test('widgetFailsWithoutWidgetKitExtensionPoint', () => {
  const fx = good();
  fx.widgetInfo.NSExtension.NSExtensionPointIdentifier = 'com.apple.widget-extension';
  failsWith(fx, { section: 'widget' }, /NSExtensionPointIdentifier/);
});

// ---- entitlements ----------------------------------------------------------
test('entitlementsFailWhenAppGroupMissing', () => {
  let fx = good();
  fx.appEnt['com.apple.security.application-groups'] = [];
  failsWith(fx, { section: 'entitlements', target: 'app' }, /application-groups/);
  fx = good();
  fx.widgetEnt['com.apple.security.application-groups'] = ['group.other'];
  failsWith(fx, { section: 'entitlements', target: 'widget' }, /application-groups/);
  // target 'all' sees the widget failure too
  failsWith(fx, { section: 'entitlements' }, /application-groups/);
});

test('entitlementsFailWhenDataProtectionMissing', () => {
  let fx = good();
  delete fx.appEnt['com.apple.developer.default-data-protection'];
  failsWith(fx, { section: 'entitlements', target: 'app' }, /default-data-protection/);
  fx = good();
  fx.widgetEnt['com.apple.developer.default-data-protection'] = 'NSFileProtectionComplete';
  failsWith(fx, { section: 'entitlements', target: 'widget' }, /default-data-protection/);
});

test('entitlementsFailWhenApsEnvironmentPresent', () => {
  const fx = good();
  fx.appEnt['aps-environment'] = 'production';
  failsWith(fx, { section: 'entitlements', target: 'app' }, /aps-environment/);
});

test('widgetEntitlementsRejectAssociatedDomains', () => {
  const fx = good();
  fx.widgetEnt['com.apple.developer.associated-domains'] = ['applinks:inlove.app'];
  failsWith(fx, { section: 'entitlements', target: 'widget' }, /associated-domains/);
});

test('releaseModeRequiresApplinksDomain', () => {
  let fx = good();
  passes(fx, { section: 'entitlements', target: 'app' });
  failsWith(fx, { section: 'entitlements', target: 'app', release: true }, /applinks:inlove\.app/);
  fx = good();
  fx.appEnt['com.apple.developer.associated-domains'] = ['applinks:inlove.app'];
  passes(fx, { section: 'entitlements', target: 'app', release: true });
});

// ---- privacy ---------------------------------------------------------------
test('privacyFailsWhenReasonCodeMissing', () => {
  const fx = good();
  fx.appPrivacy.NSPrivacyAccessedAPITypes[0].NSPrivacyAccessedAPITypeReasons = [];
  failsWith(fx, { section: 'privacy', target: 'app' }, /reason/i);
});

test('privacyFailsOnUnapprovedReasonCode', () => {
  const fx = good();
  fx.appPrivacy.NSPrivacyAccessedAPITypes[0].NSPrivacyAccessedAPITypeReasons = ['CA92.1', 'ZZZZ.9'];
  failsWith(fx, { section: 'privacy', target: 'app' }, /ZZZZ\.9/);
});

test('privacyFailsWhenCodeBelongsToOtherCategory', () => {
  const fx = good();
  fx.appPrivacy.NSPrivacyAccessedAPITypes[0].NSPrivacyAccessedAPITypeReasons = ['35F9.1'];
  failsWith(fx, { section: 'privacy', target: 'app' }, /35F9\.1/);
});

test('privacyAcceptsApprovedCodesFromAllFourCategories', () => {
  const fx = good();
  fx.appPrivacy.NSPrivacyAccessedAPITypes = [
    { NSPrivacyAccessedAPIType: 'NSPrivacyAccessedAPICategoryUserDefaults', NSPrivacyAccessedAPITypeReasons: ['CA92.1', '1C8F.1'] },
    { NSPrivacyAccessedAPIType: 'NSPrivacyAccessedAPICategorySystemBootTime', NSPrivacyAccessedAPITypeReasons: ['35F9.1'] },
    { NSPrivacyAccessedAPIType: 'NSPrivacyAccessedAPICategoryFileTimestamp', NSPrivacyAccessedAPITypeReasons: ['C617.1', '3B52.1', '0A2A.1'] },
    { NSPrivacyAccessedAPIType: 'NSPrivacyAccessedAPICategoryDiskSpace', NSPrivacyAccessedAPITypeReasons: ['E174.1', '85F4.1'] },
  ];
  passes(fx, { section: 'privacy', target: 'app' });
});

test('privacyFailsOnNonBoolTrackingOrUnknownCategoryOrWidgetApi', () => {
  let fx = good();
  fx.appPrivacy.NSPrivacyTracking = 'NO';
  failsWith(fx, { section: 'privacy', target: 'app' }, /NSPrivacyTracking/);
  fx = good();
  fx.appPrivacy.NSPrivacyAccessedAPITypes[0].NSPrivacyAccessedAPIType = 'NSPrivacyAccessedAPICategoryFoo';
  failsWith(fx, { section: 'privacy', target: 'app' }, /NSPrivacyAccessedAPICategoryFoo/);
  fx = good();
  fx.widgetPrivacy.NSPrivacyAccessedAPITypes = [
    { NSPrivacyAccessedAPIType: 'NSPrivacyAccessedAPICategoryUserDefaults', NSPrivacyAccessedAPITypeReasons: ['CA92.1'] },
  ];
  failsWith(fx, { section: 'privacy', target: 'widget' }, /widget/i);
});

// ---- strings / icon --------------------------------------------------------
test('stringsFailWhenUsageKeyNotLocalized', () => {
  let fx = good();
  fx.strings.en = '"CFBundleDisplayName" = "InLove";\n';
  failsWith(fx, { section: 'strings' }, /NSCameraUsageDescription.*en|en.*NSCameraUsageDescription/);
  fx = good();
  fx.strings.vi = null; // vi.lproj missing entirely
  failsWith(fx, { section: 'strings' }, /vi\.lproj/);
  fx = good();
  fx.appInfo.NSContactsUsageDescription = 'x'; // new usage key, strings not updated
  failsWith(fx, { section: 'strings' }, /NSContactsUsageDescription/);
  fx = good();
  fx.strings.vi = `"NSCameraUsageDescription" = "${CAMERA_VI}";\n`; // no display name
  failsWith(fx, { section: 'strings' }, /CFBundleDisplayName/);
});

test('iconFailsWithout1024Entry', () => {
  let fx = good();
  fx.icon.appIcon.images[0].size = '60x60';
  failsWith(fx, { section: 'icon' }, /1024/);
  fx = good();
  fx.icon.appIconFile = false; // entry points to a missing file
  failsWith(fx, { section: 'icon' }, /AppIcon-1024\.png/);
  fx = good();
  fx.icon.launchColorset = false;
  failsWith(fx, { section: 'icon' }, /LaunchBackground/);
});

// ---- CLI -------------------------------------------------------------------
const cli = (args) => spawnSync(process.execPath, [CLI, ...args], { encoding: 'utf8' });

test('cliExitCodeIsOneOnFailure', () => {
  const fx = good();
  fx.appInfo.CFBundleLocalizations = ['en'];
  const root = write(fx);
  const bad = cli(['--section', 'app-info', '--root', root]);
  assert.equal(bad.status, 1);
  assert.match(bad.stdout, /^FAIL app-info: .*vi/);
  const ok = cli(['--section', 'bgtask', '--root', root]);
  assert.equal(ok.status, 0);
  assert.equal(ok.stdout.trim(), 'OK bgtask');
  const rel = cli(['--section', 'entitlements', '--target', 'app', '--release', '--root', root]);
  assert.equal(rel.status, 1);
  assert.match(rel.stdout, /^FAIL entitlements: .*applinks:inlove\.app/);
  // missing root content and unknown section also fail with 1
  assert.equal(cli(['--section', 'icon', '--root', fs.mkdtempSync(path.join(os.tmpdir(), 'plat-empty-'))]).status, 1);
  assert.equal(cli(['--section', 'nope', '--root', root]).status, 1);
});
