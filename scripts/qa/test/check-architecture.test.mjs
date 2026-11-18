import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { checkArchitecture, EXPECTED_SKUS } from '../check-architecture.mjs';

const CHECKER = fileURLToPath(new URL('../check-architecture.mjs', import.meta.url));

function withTempTree(files, fn) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'arch-'));
  try {
    for (const [rel, content] of Object.entries(files)) {
      const abs = path.join(dir, rel);
      fs.mkdirSync(path.dirname(abs), { recursive: true });
      fs.writeFileSync(abs, content, 'utf8');
    }
    return fn(dir);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

const makeLines = (n, prefix = 'val line') =>
  Array.from({ length: n }, (_, i) => `${prefix}${i} = ${i}`).join('\n') + '\n';

const GOOD_STOREKIT = JSON.stringify(
  {
    products: [{ productID: 'vip_lifetime', type: 'NonConsumable' }],
    subscriptionGroups: [
      {
        name: 'InLove VIP',
        subscriptions: [
          { productID: 'vip_monthly', type: 'RecurringSubscription' },
          { productID: 'vip_yearly', type: 'RecurringSubscription' },
        ],
      },
    ],
  },
  null,
  2,
);

const GOOD_MONETIZATION_FILES = {
  'app/src/main/java/com/example/billing/VipProductIds.kt': `package com.example.billing
/** Legacy IDs were inlove_vip_monthly/inlove_vip_yearly/inlove_vip_lifetime (must be ignored in KDoc) */
object VipProductIds {
  const val MONTHLY = "vip_monthly"
  const val YEARLY = "vip_yearly"
  const val LIFETIME = "vip_lifetime"
}
`,
  'shared/src/commonMain/kotlin/com/example/shared/monetization/MonetizationPolicy.kt': `package com.example.shared.monetization
object ProTierCatalog {
  const val MONTHLY = "vip_monthly"
  const val YEARLY = "vip_yearly"
  const val LIFETIME = "vip_lifetime"
}
`,
  'iosApp/StoreKit/InLove.storekit': GOOD_STOREKIT,
  'iosApp/Packages/InLoveIosCore/Sources/InLoveIosCore/SubscriptionModels.swift': `public enum InLoveProductID: String, CaseIterable {
  case monthly = "vip_monthly"
  case yearly = "vip_yearly"
  case lifetime = "vip_lifetime"
}
`,
};

test('arch01_ui_importing_room_or_firestore_fails_and_comments_are_ignored', () => {
  withTempTree(
    {
      'app/src/main/java/com/example/ui/screens/GoodScreen.kt': `package com.example.ui.screens
// import androidx.room.Dao
/* import com.google.firebase.firestore.FirebaseFirestore */
import androidx.compose.runtime.Composable
`,
      'app/src/main/java/com/example/ui/screens/BadRoom.kt': `package com.example.ui.screens
import androidx.room.Entity
`,
      'app/src/main/java/com/example/ui/dialogs/BadFirestore.kt': `package com.example.ui.dialogs
import com.google.firebase.firestore.FirebaseFirestore
`,
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.equal(problems.length, 2, problems.join('\n'));
      assert.ok(problems.some((p) => /BadRoom\.kt:2: \[ARCH-01\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /BadFirestore\.kt:2: \[ARCH-01\]/.test(p)), problems.join('\n'));
    },
  );
});

test('arch01_commonMain_importing_platform_sdk_fails', () => {
  withTempTree(
    {
      'shared/src/commonMain/kotlin/com/example/shared/Clean.kt': `package com.example.shared
import kotlinx.datetime.LocalDate
`,
      'shared/src/commonMain/kotlin/com/example/shared/Leaky.kt': `package com.example.shared
import android.content.Context
import com.app.plugin.ads.AdsHelper
import com.google.firebase.Firebase
`,
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.equal(problems.length, 3, problems.join('\n'));
      assert.ok(problems.every((p) => p.includes('[ARCH-01]') && p.includes('Leaky.kt')));
    },
  );
});

test('arch02_new_packages_enforce_550_line_cap', () => {
  withTempTree(
    {
      'shared/src/commonMain/kotlin/com/example/shared/Exact.kt': makeLines(550),
      'shared/src/commonMain/kotlin/com/example/shared/TooLong.kt': makeLines(551),
      'app/src/main/java/com/example/ui/designsystem/BigTokens.kt': makeLines(551),
      'app/src/main/java/com/example/ui/screens/guide/TooLongGuide.kt': makeLines(551),
      'app/src/main/java/com/example/ui/screens/paywall/TooLongPaywall.kt': makeLines(551),
      'iosApp/InLoveIos/Views/HugeView.swift': makeLines(551, 'let row'),
      'iosApp/Packages/InLoveIosCore/.build/checkouts/Ignored.swift': makeLines(900, 'let ign'),
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.equal(problems.length, 5, problems.join('\n'));
      assert.ok(problems.some((p) => p.includes('TooLong.kt') && p.includes('[ARCH-02]')));
      assert.ok(problems.some((p) => p.includes('BigTokens.kt') && p.includes('[ARCH-02]')));
      assert.ok(problems.some((p) => p.includes('TooLongGuide.kt') && p.includes('[ARCH-02]')));
      assert.ok(problems.some((p) => p.includes('TooLongPaywall.kt') && p.includes('[ARCH-02]')));
      assert.ok(problems.some((p) => p.includes('HugeView.swift') && p.includes('[ARCH-02]')));
    },
  );
});

test('arch02_refactored_caps_and_legacy_ratchet_enforced', () => {
  withTempTree(
    {
      'app/src/main/java/com/example/ui/screens/Dialogs.kt': makeLines(350),
      'app/src/main/java/com/example/ui/screens/LoveHomeScreen.kt': makeLines(550),
      'app/src/main/java/com/example/ui/screens/GiftScreen.kt': makeLines(650),
      'app/src/main/java/com/example/ui/screens/AppModals.kt': makeLines(680),
      'app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt': makeLines(1550),
      'app/src/main/java/com/example/ui/screens/PaywallScreen.kt': makeLines(756),
    },
    (dir) => {
      assert.deepEqual(checkArchitecture(dir), []);
    },
  );

  withTempTree(
    {
      'app/src/main/java/com/example/ui/screens/Dialogs.kt': makeLines(351),
      'app/src/main/java/com/example/ui/screens/LoveHomeScreen.kt': makeLines(551),
      'app/src/main/java/com/example/ui/screens/GiftScreen.kt': makeLines(651),
      'app/src/main/java/com/example/ui/screens/AppModals.kt': makeLines(681),
      'app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt': makeLines(1551),
      'app/src/main/java/com/example/ui/screens/PaywallScreen.kt': makeLines(757),
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.equal(problems.length, 6, problems.join('\n'));
      assert.ok(problems.every((p) => p.includes('[ARCH-02]')));
    },
  );
});

test('arch03_outlined_textfield_colors_and_gray_rules', () => {
  withTempTree(
    {
      'app/src/main/java/com/example/ui/designsystem/InLoveInputs.kt': `package com.example.ui.designsystem
fun inLoveOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
  // Color.Gray allowed only in comments
)
`,
      'app/src/main/java/com/example/ui/screens/AuthScreen.kt': `package com.example.ui.screens
fun old() = OutlinedTextFieldDefaults.colors()
val legacyHint = Color.Gray
`,
      'app/src/main/java/com/example/ui/screens/gift/GiftCard.kt': `package com.example.ui.screens.gift
val bad1 = Color.Gray
val bad2 = Color.LightGray
`,
      'app/src/main/java/com/example/ui/dialogs/MyDialog.kt': `package com.example.ui.dialogs
val bad3 = Color.Gray
`,
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.equal(problems.length, 4, problems.join('\n'));
      assert.ok(problems.some((p) => /AuthScreen\.kt:2: \[ARCH-03\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /MyDialog\.kt:2: \[ARCH-03\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /GiftCard\.kt:2: \[ARCH-03\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /GiftCard\.kt:3: \[ARCH-03\]/.test(p)), problems.join('\n'));
    },
  );
});

test('arch04_sku_parity_passes_on_canonical_set_and_fails_on_drift', () => {
  withTempTree(GOOD_MONETIZATION_FILES, (dir) => {
    assert.deepEqual(checkArchitecture(dir), []);
  });

  withTempTree(
    {
      ...GOOD_MONETIZATION_FILES,
      'shared/src/commonMain/kotlin/com/example/shared/monetization/MonetizationPolicy.kt': `package com.example.shared.monetization
object ProTierCatalog {
  const val MONTHLY = "inlove_vip_monthly"
  const val YEARLY = "vip_yearly"
  const val LIFETIME = "vip_lifetime"
}
`,
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.ok(problems.some((p) => p.includes('[ARCH-04]') && p.includes('vip_monthly')));
      assert.ok(problems.some((p) => p.includes('[ARCH-04]') && p.includes('inlove_vip_monthly')));
    },
  );

  withTempTree(
    {
      ...GOOD_MONETIZATION_FILES,
      'iosApp/StoreKit/InLove.storekit': JSON.stringify({
        products: [{ productID: 'vip_lifetime' }],
        subscriptionGroups: [{ subscriptions: [{ productID: 'vip_monthly' }, { productID: 'vip_weekly' }] }],
      }),
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.equal(problems.length, 1);
      assert.match(problems[0], /InLove\.storekit: \[ARCH-04\]/);
      assert.match(problems[0], new RegExp(EXPECTED_SKUS.join(', ')));
    },
  );

  withTempTree(
    {
      'app/src/main/java/com/example/billing/VipProductIds.kt':
        GOOD_MONETIZATION_FILES['app/src/main/java/com/example/billing/VipProductIds.kt'],
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.equal(problems.length, 3, problems.join('\n'));
      assert.ok(problems.every((p) => p.includes('[ARCH-04]') && p.includes('missing monetization target')));
    },
  );
});

test('arch05_internal_db_jargon_in_ui_string_literals_fails_and_logs_comments_pass', () => {
  withTempTree(
    {
      'app/src/main/java/com/example/ui/screens/CleanUi.kt': `package com.example.ui.screens
import android.util.Log
// Firestore and SQLite and cơ sở dữ liệu in comments are fine
/* Room DB and SharedPreferences in block comments are fine */
fun helperFromFirestore() {
  val prefs = getSharedPreferences("inlove_prefs", 0)
  Log.d("InLove", "Synced Firestore SQLite Room DB from the database")
  Timber.i("Saved in the database")
  val warm = "Đã lưu lời nhắc vào danh sách kỷ niệm."
}
`,
      'app/src/main/java/com/example/data/DataLayer.kt': `package com.example.data
val internalNote = "Loaded from SQLite Room DB and Firestore"
`,
      'app/src/main/java/com/example/ui/screens/LeakyUi.kt': `package com.example.ui.screens
val vi = "Xóa khỏi cơ sở dữ liệu?"
val en = "Delete from the database?"
val fb = "Link via Firestore"
val room = "Saved to Room DB and SQLite"
val sp = "Check SharedPreferences"
`,
      'iosApp/InLoveIos/Views/CleanView.swift': `import SwiftUI
// Firestore SQLite in the database comment
let msg = "Saved to your couple space."
func debug() {
  print("Firestore sync debug")
  NSLog("SQLite fallback")
}
`,
      'iosApp/InLoveIos/Views/LeakyView.swift': `import SwiftUI
let banner = "Synced in the database via Firestore"
`,
    },
    (dir) => {
      const problems = checkArchitecture(dir);
      assert.equal(problems.length, 6, problems.join('\n'));
      assert.ok(problems.some((p) => /LeakyUi\.kt:2: \[ARCH-05\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /LeakyUi\.kt:3: \[ARCH-05\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /LeakyUi\.kt:4: \[ARCH-05\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /LeakyUi\.kt:5: \[ARCH-05\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /LeakyUi\.kt:6: \[ARCH-05\]/.test(p)), problems.join('\n'));
      assert.ok(problems.some((p) => /LeakyView\.swift:2: \[ARCH-05\]/.test(p)), problems.join('\n'));
    },
  );
});

test('cli_exits_zero_when_clean_and_one_on_violation', () => {
  withTempTree(GOOD_MONETIZATION_FILES, (dir) => {
    const ok = spawnSync(process.execPath, [CHECKER, '--root', dir], { encoding: 'utf8' });
    assert.equal(ok.status, 0, ok.stdout + ok.stderr);
    assert.match(ok.stdout, /^OK:/);
  });

  withTempTree(
    {
      'app/src/main/java/com/example/ui/screens/Leak.kt': 'import androidx.room.Dao\n',
    },
    (dir) => {
      const bad = spawnSync(process.execPath, [CHECKER, '--root', dir], { encoding: 'utf8' });
      assert.equal(bad.status, 1);
      assert.match(bad.stderr, /\[ARCH-01\]/);
    },
  );
});
