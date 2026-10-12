# SDK Release-Grade (1.3.1 → 1.4.0 → 2.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Đưa `com.app.plugin:appplugin` qua toàn bộ cổng G1–G10 của `DOC/65_SDK_RELEASE_GRADE_CRITERIA.md`: 0 đường crash ở bản release, điểm mù R8 được test khoá, consent đúng theo TCF, một policy tần suất, đo lường đúng, không hỏng câm.

**Architecture:** Ba phase, mỗi phase một bản SDK. Phase A (Task 1–7) chỉ sửa lỗi và thêm cổng kiểm, không đổi hành vi hợp đồng. Phase B (Task 8–17) thêm lớp luật (`TcfConsent`, `AnalyticsConsent`, `AdsPolicy`, `SdkKillSwitch`) dưới dạng **lớp thuần, test không cần Android**, rồi nối vào `AdsHelper`/`ConsentManager`/`AppPluginManager` ở đúng một điểm mỗi lớp. Phase C (2.0) tách module; chỉ có đề cương và cổng, cần chủ dự án chốt doc 60 K trước.

**Tech Stack:** Kotlin, AGP 9.2.1 (Kotlin built-in), Gradle 9.4.1, JUnit 4 + Mockito 5 (`unitTests.isReturnDefaultValues = true`), GMA 25.4.0, UMP 4.0.0, Firebase BoM 33.4.0, AppsFlyer 6.16.2, Billing 9.0.0, adb/emulator, bash.

## Global Constraints

- `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` cho mọi lệnh Gradle. Lệnh test đầy đủ: `./gradlew :testDebugUnitTest` (kỳ vọng ≥ 597 test, 0 fail, 0 skipped mới).
- Hiến pháp Điều 6.5 và DOC/65 mục 6.2: mỗi test mới phải được chạy **đỏ** trước, output đỏ lưu vào `DOC/testing/task<N>-red.log`, output xanh vào `DOC/testing/task<N>-green.log` (`./gradlew :testDebugUnitTest --tests '<Lớp>' > DOC/testing/task<N>-red.log 2>&1; echo exit=$?`).
- Hiến pháp Điều 17: một task một commit, message tiếng Việt kiểu `fix(ads): …`, kết thúc bằng dòng `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Không tag, không publish, không `git push` khi chủ dự án chưa nói.
- Repo app (`~/AndroidStudioProjects/speakerclean`, `InvoiceMakerAI`, `app-template`) **chỉ đọc**.
- Thiết bị: đặt `ANDROID_SERIAL`; **không** dùng Samsung `R5CX10P9EKZ` và Redmi `65c9b673`. Emulator riêng: `emulator -avd Pixel_8_Pro -read-only -no-window -no-audio -no-boot-anim -no-snapshot-save -gpu swiftshader_indirect -port 5570` → `ANDROID_SERIAL=emulator-5570`; tắt bằng `adb -s emulator-5570 emu kill`. Chỉ kill process mình tạo, theo PID.
- Không đổi hành vi mặc định mà không ghi vào `CHANGELOG.md` mục `## [Unreleased]` → "Phải làm khi nâng cấp".
- Version: `val sdkVersion` trong `build.gradle.kts` là nguồn duy nhất. Phase A kết thúc ở `1.3.1`, Phase B ở `1.4.0`.
- Nhánh làm việc: `fix/1.3.1` (đang có 3 bản sửa chưa commit: `IapHelper.kt`, `AdsBase.kt`, `AdsHelper.kt` + 4 test mới). **Task 0** dưới đây commit chúng trước.
- Mọi file test dùng package trùng với lớp được test; Mockito mock `Context`/`Resources`/`SharedPreferences` theo mẫu `src/test/java/com/app/plugin/ads/Full2ChainDismissTest.kt`.

---

## Task 0: Commit các bản sửa đang treo trên `fix/1.3.1`

**Files:**
- Modify (đã sửa, chưa commit): `src/main/java/com/app/plugin/iap/IapHelper.kt`, `src/main/java/com/app/plugin/ads/AdsBase.kt`, `src/main/java/com/app/plugin/ads/AdsHelper.kt`, `CHANGELOG.md`, `DOC/64_SDK_1_3_0_DEVICE_TEST.md`
- Test (mới, chưa commit): `src/test/java/com/app/plugin/iap/BillingSetupOutcomeTest.kt`, `src/test/java/com/app/plugin/ads/ShowWaterfallCallbackTest.kt`, `src/test/java/com/app/plugin/ads/Full2CallbackParityTest.kt`, `src/test/java/com/app/plugin/ads/Full2ChainDismissTest.kt`

- [ ] **Step 1: Chạy toàn bộ unit test**

Run: `./gradlew :testDebugUnitTest > DOC/testing/task0-green.log 2>&1; echo exit=$?; grep -E "tests completed|BUILD" DOC/testing/task0-green.log`
Expected: `exit=0`, `BUILD SUCCESSFUL`, không có dòng `FAILED`.

- [ ] **Step 2: Commit ba bản sửa và bốn test, không commit file log/outputs**

```bash
git add src/main/java/com/app/plugin/iap/IapHelper.kt src/main/java/com/app/plugin/ads/AdsBase.kt \
  src/main/java/com/app/plugin/ads/AdsHelper.kt CHANGELOG.md DOC/64_SDK_1_3_0_DEVICE_TEST.md \
  src/test/java/com/app/plugin/iap/BillingSetupOutcomeTest.kt \
  src/test/java/com/app/plugin/ads/ShowWaterfallCallbackTest.kt \
  src/test/java/com/app/plugin/ads/Full2CallbackParityTest.kt \
  src/test/java/com/app/plugin/ads/Full2ChainDismissTest.kt
git commit -m "fix(ads,iap): Full2 chuyển callback tới app; không onAdShowFail giữa waterfall; phân loại kết quả reconnect Billing 9

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

`DOC/testing/*.log`, `outputs/`, `.vscode/` giữ nguyên untracked (không add).

---

# Phase A — 1.3.1 "Không crash ở bản release"

## Task 1: Consumer rules giữ constructor của Room, WorkManager, Firebase registrar

**Files:**
- Modify: `consumer-rules.pro` (thêm ở cuối)
- Test: `src/test/java/com/app/plugin/until/ConsumerRulesTest.kt`

**Interfaces:** không có. Kết quả: app nào dùng SDK không còn sập `WorkDatabase_Impl` và không mất registrar Firebase ở bản R8 full mode.

- [ ] **Step 1: Viết test hợp đồng**

```kotlin
package com.app.plugin.until

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Điều 8.4: keep rule cho thứ R8 không nhìn thấy. Ba nhóm rule dưới đây từng thiếu (đo 2026-09-24):
 * bản release của app demo sập lúc mở vì `WorkDatabase_Impl` mất constructor, và Crashlytics/FCM
 * chết câm vì registrar Firebase mất `<init>()`.
 */
class ConsumerRulesTest {
    private val rules = File("consumer-rules.pro").readText()

    @Test
    fun `giu constructor cua RoomDatabase ma WorkManager cua GMA keo theo`() {
        assertTrue(rules.contains("-keep class * extends androidx.room.RoomDatabase { <init>(); }"))
    }

    @Test
    fun `giu constructor cua InputMerger va ListenableWorker`() {
        assertTrue(rules.contains("-keep class * extends androidx.work.InputMerger { <init>(); }"))
        assertTrue(rules.contains(
            "-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }"))
    }

    @Test
    fun `giu constructor cua ComponentRegistrar cua Firebase`() {
        assertTrue(rules.contains(
            "-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }"))
    }
}
```

- [ ] **Step 2: Chạy test, phải đỏ**

Run: `./gradlew :testDebugUnitTest --tests 'com.app.plugin.until.ConsumerRulesTest' > DOC/testing/task1-red.log 2>&1; echo exit=$?`
Expected: `exit=1`, 3 test FAILED với `AssertionError`.

- [ ] **Step 3: Thêm rule vào cuối `consumer-rules.pro`**

```
# ── Room / WorkManager (kéo theo bởi play-services-ads 25.4 và unity-ads 4.19) ──────────────
# Room 2.2.5 (từ work-runtime 2.7.0) chỉ keep lớp con RoomDatabase, không keep <init>(). R8 full
# mode xoá constructor của WorkDatabase_Impl, androidx.startup ném lỗi trước màn đầu tiên và bản
# release sập lúc mở (đo 2026-09-24 trên app demo, targetSdk 36). Chỉ ghim WorkManager 2.9.1 không
# đủ; rule này thì đủ. Speaker Clean và Invoice sống nhờ rule riêng của app; app-template thiếu.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
# WorkManager dựng InputMerger và Worker từ tên lớp (Invoice: backup không bao giờ chạy).
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# ── Firebase components (firebase-components 18.0.0 đi cùng BoM 33.4.0) ────────────────────────
# Rule của Firebase giữ lớp registrar nhưng không giữ <init>(); ComponentDiscovery tạo registrar
# bằng reflection nên Crashlytics, FCM, Installations chết câm ở bản release (NoSuchMethodException).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
```

- [ ] **Step 4: Chạy test, phải xanh**

Run: `./gradlew :testDebugUnitTest --tests 'com.app.plugin.until.ConsumerRulesTest' > DOC/testing/task1-green.log 2>&1; echo exit=$?`
Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add consumer-rules.pro src/test/java/com/app/plugin/until/ConsumerRulesTest.kt
git commit -m "fix(r8): consumer rules giữ <init> của RoomDatabase, InputMerger, ListenableWorker và ComponentRegistrar

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 2: SDK tự mang `res/raw/appplugin_keep.xml` cho mọi resource đọc bằng tên

**Files:**
- Create: `src/main/res/raw/appplugin_keep.xml`
- Test: `src/test/java/com/app/plugin/until/KeepXmlTest.kt`

**Interfaces:** không có. Kết quả: app bật `shrinkResources` vẫn giữ `admob_*`, `adfb_*`, `admax_*`, `ads_cf_*`, `flagTest`, `*_test_device`, `facebook_app_id`, `af_app_id`, `admax_devKey`.

- [ ] **Step 1: Viết test hợp đồng — mỗi tên resource SDK đọc phải khớp một mẫu trong keep.xml**

```kotlin
package com.app.plugin.until

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Điều 8.4. SDK đọc resource của app bằng `AppUtil.getResource*` (getIdentifier) nên shrinkResources
 * không thấy tham chiếu. Đo 2026-09-24: 8 ad unit → 0 khi bật shrinkResources mà không có keep.xml.
 * Test này quét đúng các lời gọi đó (kể cả tên động trong `ResIdDefault`) và bắt keep.xml phủ hết.
 */
class KeepXmlTest {
    private val keepFile = File("src/main/res/raw/appplugin_keep.xml")

    private fun patterns(): List<Regex> {
        val keep = Regex("tools:keep=\"([^\"]+)\"").find(keepFile.readText())
            ?: error("appplugin_keep.xml không có tools:keep")
        return keep.groupValues[1].split(',')
            .map { it.trim().removePrefix("@") }
            .map { Regex("^" + it.replace(".", "\\.").replace("*", ".*") + "$") }
    }

    private val literalCall =
        Regex("AppUtil\\.getResource(String|Int|Ids)\\(\\s*\\w+,\\s*\"([A-Za-z0-9_]+)\"")

    private fun namesReadByCode(): Set<String> {
        val names = mutableSetOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            literalCall.findAll(f.readText()).forEach { m ->
                val type = if (m.groupValues[1] == "Int") "integer" else "string"
                names += "$type/${m.groupValues[2]}"
            }
        }
        // Tên động: AdsBase.loadIdsDefault() đọc getKeyForResDf(), mọi giá trị nằm trong ResIdDefault.
        Regex("\"([a-z_0-9]+)\"").findAll(File("src/main/java/com/app/plugin/ads/ResIdDefault.kt").readText())
            .forEach { names += "string/${it.groupValues[1]}" }
        return names
    }

    @Test
    fun `keep xml ton tai va co tools keep`() {
        assertTrue(keepFile.path, keepFile.exists())
        assertTrue(patterns().isNotEmpty())
    }

    @Test
    fun `moi resource SDK doc bang ten deu duoc keep`() {
        val ps = patterns()
        val missing = namesReadByCode().filter { n -> ps.none { it.matches(n) } }.sorted()
        assertTrue("Thiếu trong appplugin_keep.xml:\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
```

- [ ] **Step 2: Chạy test, phải đỏ (file chưa tồn tại)**

Run: `./gradlew :testDebugUnitTest --tests 'com.app.plugin.until.KeepXmlTest' > DOC/testing/task2-red.log 2>&1; echo exit=$?`
Expected: `exit=1`, `keep xml ton tai va co tools keep` FAILED.

- [ ] **Step 3: Tạo `src/main/res/raw/appplugin_keep.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  Hiến pháp Điều 8.4: SDK đọc các resource này của app bằng tên (AppUtil.getResource*, tức
  getIdentifier), nên shrinkResources của app không thấy tham chiếu và xoá chúng. Đo 2026-09-24:
  8 ad unit → 0 trong resources.arsc khi app bật shrinkResources mà không có file này.
  KeepXmlTest bắt mọi tên mới đọc bằng code phải có ở đây.
-->
<resources xmlns:tools="http://schemas.android.com/tools"
    tools:keep="@string/admob_*,@string/adfb_*,@string/admax_*,@string/ads_cf_*,@integer/ads_cf_*,@string/flagTest,@string/admob_test_device,@string/fb_test_device,@string/max_test_device,@string/facebook_app_id,@string/af_app_id,@string/admax_devKey,@raw/appplugin_keep" />
```

- [ ] **Step 4: Chạy test, phải xanh**

Run: `./gradlew :testDebugUnitTest --tests 'com.app.plugin.until.KeepXmlTest' > DOC/testing/task2-green.log 2>&1; echo exit=$?`
Expected: `exit=0`.

- [ ] **Step 5: Kiểm bằng bản release của app demo (cổng G3, chưa cần Task 5: dùng harness tạm)**

```bash
cp app/build.gradle.kts /tmp/app.gradle.bak
sed -i '' 's/targetSdk = 34/targetSdk = 36/; s/isMinifyEnabled = false/isMinifyEnabled = true\n            isShrinkResources = true\n            signingConfig = signingConfigs.getByName("debug")/' app/build.gradle.kts
./gradlew :app:assembleRelease > /tmp/task2-release.log 2>&1; echo build=$?
unzip -p app/build/outputs/apk/release/app-release.apk resources.arsc | LC_ALL=C grep -a -o "ca-app-pub-[0-9/~]*" | wc -l
cp /tmp/app.gradle.bak app/build.gradle.kts
```
Expected: dòng cuối in `8` (bằng số `ca-app-pub-` trong `app/src/main/res/values/ads_ids.xml`). Nếu in `0`: keep.xml của thư viện không được merge — khi đó chuyển file này sang `app/src/main/res/raw/keep.xml` **và** ghi vào CHANGELOG "Phải làm khi nâng cấp: app tự thêm keep.xml (chép từ SDK)". Ghi kết quả đo (con số + lệnh) vào commit message.

- [ ] **Step 6: Commit**

```bash
git add src/main/res/raw/appplugin_keep.xml src/test/java/com/app/plugin/until/KeepXmlTest.kt
git commit -m "fix(r8): SDK mang res/raw/appplugin_keep.xml; KeepXmlTest khoá mọi resource đọc bằng tên

Đo trên app demo release (R8 + shrinkResources): ad unit trong resources.arsc 0 → 8.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 3: Banner — detach khỏi container cũ trước khi gắn (crash "child already has a parent")

**Files:**
- Create: `src/main/java/com/app/plugin/ads/BannerAttach.kt`
- Modify: `src/main/java/com/app/plugin/ads/AdsHelper.kt:1076-1092` (nhánh `bnCurrAdd` trong `loadBannerCircle.onLoaded`)
- Test: `src/test/java/com/app/plugin/ads/BannerAttachTest.kt`

**Interfaces:**
- Produces: `internal object BannerAttach { fun attach(parent: ViewGroup, view: View): Boolean }` — true khi view đã nằm trong `parent` sau lời gọi.

- [ ] **Step 1: Viết test**

```kotlin
package com.app.plugin.ads

import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Crash monkey 2026-09-24: `IllegalStateException: The specified child already has a parent` tại
 * loadBannerCircle.onLoaded. Sau khi Activity tạo lại, banner trong cache vẫn gắn ở container cũ
 * rồi bị addView vào container mới. Mọi đường gắn banner phải đi qua BannerAttach.
 */
class BannerAttachTest {

    @Test
    fun `banner con gan o container cu thi go ra truoc roi moi gan`() {
        val view = mock(View::class.java)
        val old = mock(ViewGroup::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(old).thenReturn(null)

        assertTrue(BannerAttach.attach(parent, view))

        val order = inOrder(old, parent)
        order.verify(old).removeView(view)
        order.verify(parent).addView(view)
    }

    @Test
    fun `banner da nam dung container thi khong gan lai`() {
        val view = mock(View::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(parent)

        assertTrue(BannerAttach.attach(parent, view))

        verify(parent, never()).removeView(view)
        verify(parent, never()).addView(view)
    }

    @Test
    fun `go khong duoc thi bao false va khong addView`() {
        val view = mock(View::class.java)
        val old = mock(ViewGroup::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(old)

        assertFalse(BannerAttach.attach(parent, view))

        verify(parent, never()).addView(view)
    }
}
```

- [ ] **Step 2: Chạy test, phải đỏ (lớp chưa có → lỗi biên dịch)**

Run: `./gradlew :testDebugUnitTest --tests 'com.app.plugin.ads.BannerAttachTest' > DOC/testing/task3-red.log 2>&1; echo exit=$?`
Expected: `exit=1`, `Unresolved reference: BannerAttach`.

- [ ] **Step 3: Viết `BannerAttach.kt`**

```kotlin
package com.app.plugin.ads

import android.view.View
import android.view.ViewGroup
import com.app.plugin.until.LogUtil

/**
 * Gắn banner vào container. Banner trong cache có thể còn gắn ở container của Activity đã bị huỷ
 * (clearActivity() không gỡ nó), và `addView` một view đang có cha ném IllegalStateException.
 * Trả về true khi view đã nằm trong [parent] sau lời gọi.
 */
internal object BannerAttach {
    fun attach(parent: ViewGroup, view: View): Boolean {
        val old = view.parent
        if (old === parent) return true
        if (old is ViewGroup) {
            try {
                old.removeView(view)
            } catch (e: Exception) {
                LogUtil.logE("banner detach failed: ${e.message}")
                return false
            }
        }
        if (view.parent != null) {
            LogUtil.logE("banner still attached after detach, skip addView")
            return false
        }
        parent.addView(view)
        return true
    }
}
```

- [ ] **Step 4: Nối vào `AdsHelper.loadBannerCircle.onLoaded` — thay khối từ `if (bnCurrAdd != null) {` tới hết `else { … }` bằng:**

```kotlin
                            if (bnCurrAdd != adView) {
                                if (bnCurrAdd != null) bnViewParent?.removeAllViews()
                                bnCurrAdd = adView
                            }
                            val parent = bnViewParent
                            if (adView != null && parent != null && !BannerAttach.attach(parent, adView)) {
                                LogEventServer.logAdShowFailWithReason(
                                    placement = plcf.placement,
                                    reason = Constants.AdShowFailReason.VIEW_ATTACH_NULL_IN_CALL_BACK
                                )
                            }
```

Giữ nguyên dòng `adEcmp?.checkDestroyAd()` ngay sau đó.

- [ ] **Step 5: Chạy test mới và toàn bộ test banner**

Run: `./gradlew :testDebugUnitTest --tests 'com.app.plugin.ads.BannerAttachTest' --tests 'com.app.plugin.ads.BannerLoadGuardTest' --tests 'com.app.plugin.ads.AdsLifecycleRegressionTest' > DOC/testing/task3-green.log 2>&1; echo exit=$?`
Expected: `exit=0`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/app/plugin/ads/BannerAttach.kt src/main/java/com/app/plugin/ads/AdsHelper.kt src/test/java/com/app/plugin/ads/BannerAttachTest.kt
git commit -m "fix(ads): gỡ banner khỏi container cũ trước khi gắn lại sau khi Activity tạo lại

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 4: Full2 — không dùng lại dialog với Activity đã chết; `show()` có chốt (BadTokenException)

**Files:**
- Create: `src/main/java/com/app/plugin/ads/NtFullDialogHost.kt`
- Modify: `src/main/java/com/app/plugin/ads/adsmob/AdsMobPlNtFull.kt` (fields 53–55, `prepareChainedShow` 282–300, `discardPreparedDialog` 308–312, show path 335–349, `showWidthDialog` 475–512)
- Modify: `src/main/java/com/app/plugin/ads/adsfb/AdsFbPlNtFull.kt` (`showWidthDialog` 358–392 và chỗ gọi nó)
- Modify: `src/main/java/com/app/plugin/ads/adsmax/AdsMaxPlNtFull.kt` (`showWidthDialog` 306–340 và chỗ gọi nó)
- Test: `src/test/java/com/app/plugin/ads/NtFullDialogHostTest.kt`

**Interfaces:**
- Produces: `internal object NtFullDialogHost { fun isUsable(activity: Activity?): Boolean; fun show(dialog: Dialog, onFail: (Throwable) -> Unit): Boolean }`

- [ ] **Step 1: Viết test**

```kotlin
package com.app.plugin.ads

import android.app.Activity
import android.app.Dialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Crash monkey 2026-09-24: `WindowManager.BadTokenException` tại AdsMobPlNtFull.showWidthDialog —
 * dialog dựng sẵn cho Activity đã chết được show(). Meta và MAX cùng mẫu.
 */
class NtFullDialogHostTest {

    @Test
    fun `activity null, dang finish hoac da destroy thi khong dung duoc`() {
        assertFalse(NtFullDialogHost.isUsable(null))
        val finishing = mock(Activity::class.java)
        `when`(finishing.isFinishing).thenReturn(true)
        assertFalse(NtFullDialogHost.isUsable(finishing))
        val destroyed = mock(Activity::class.java)
        `when`(destroyed.isDestroyed).thenReturn(true)
        assertFalse(NtFullDialogHost.isUsable(destroyed))
        assertTrue(NtFullDialogHost.isUsable(mock(Activity::class.java)))
    }

    @Test
    fun `show nem loi cua so chet thi tra false, bao ly do va khong nem tiep`() {
        val dialog = mock(Dialog::class.java)
        doThrow(IllegalStateException("window dead")).`when`(dialog).show()
        var failure: Throwable? = null

        assertFalse(NtFullDialogHost.show(dialog) { failure = it })

        assertEquals("window dead", failure?.message)
    }

    @Test
    fun `show binh thuong thi tra true`() {
        assertTrue(NtFullDialogHost.show(mock(Dialog::class.java)) { throw AssertionError("không được gọi") })
    }
}
```

- [ ] **Step 2: Chạy test, phải đỏ**

Run: `./gradlew :testDebugUnitTest --tests 'com.app.plugin.ads.NtFullDialogHostTest' > DOC/testing/task4-red.log 2>&1; echo exit=$?`
Expected: `exit=1`, `Unresolved reference: NtFullDialogHost`.

- [ ] **Step 3: Viết `NtFullDialogHost.kt`**

```kotlin
package com.app.plugin.ads

import android.app.Activity
import android.app.Dialog

/**
 * Chốt chung cho ba lớp native full (AdMob, Meta, MAX) trước khi show dialog. `BadTokenException`
 * và `IllegalStateException` từ `Dialog.show()` đều là RuntimeException của một cửa sổ đã chết:
 * bắt ở đây, trả false, để lớp gọi báo `onAdShowFail` thay vì làm app sập.
 */
internal object NtFullDialogHost {
    fun isUsable(activity: Activity?): Boolean =
        activity != null && !activity.isFinishing && !activity.isDestroyed

    fun show(dialog: Dialog, onFail: (Throwable) -> Unit): Boolean = try {
        dialog.show()
        true
    } catch (e: RuntimeException) {
        onFail(e)
        false
    }
}
```

- [ ] **Step 4: Sửa `AdsMobPlNtFull.kt`**

(a) Thêm field cạnh `preparedIsDelay` (dòng 55):
```kotlin
    private var preparedActivity: Activity? = null
```
(thêm `import android.app.Activity` nếu file chưa có).

(b) Trong `prepareChainedShow()`, sau `preparedIsDelay = false` thêm `preparedActivity = activity`. Trong `discardPreparedDialog()` thêm `preparedActivity = null`.

(c) Show path (dòng 335–349): thay `if (mActivity == null) {` bằng `if (!NtFullDialogHost.isUsable(mActivity)) {` (giữ nguyên thân nhánh), và thay ba dòng cuối
```kotlin
            showWidthDialog()
            ecmpShow.isLoaded = false
            ecmpShow.adObject = null
            return true
```
bằng
```kotlin
            val shown = showWidthDialog()
            ecmpShow.isLoaded = false
            ecmpShow.adObject = null
            return shown
```

(d) `showWidthDialog` thành `private fun showWidthDialog(): Boolean`; điều kiện dùng lại thêm Activity; `show()` qua chốt:
```kotlin
    private fun showWidthDialog(): Boolean {
        if (nativeAdShow != null) {
            val host = mActivity
            if (!NtFullDialogHost.isUsable(host)) {
                LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} show activity dead")
                hideNativeFull()
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                    showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, Constants.AdShowFailReason.ACT_DESTROY
                )
                return false
            }
            // Dùng lại dialog dựng sẵn chỉ khi cùng quảng cáo, cùng cờ delay VÀ cùng Activity: dialog
            // gắn window token của Activity tạo ra nó, Activity chết thì show() ném BadTokenException.
            val reusable = preparedDialog?.takeIf {
                preparedNativeAd === nativeAdShow && preparedIsDelay == mIsDelay && preparedActivity === host
            }
            if (reusable == null) discardPreparedDialog()
            preparedDialog = null
            preparedNativeAd = null
            preparedActivity = null
            val dialog = reusable ?: AdsMobNtFullView(host!!, nativeAdShow!!, timeShowBtClose, mIsDelay, true)
            mDialogNativeFull = dialog
            AppPluginManager.instance.registerMyAppLifecycle(this)
            dialog.setOnDismissListener {
                AppPluginManager.instance.unRegisterMyAppLifecycle(this)
                hideNativeFull()
                hideBlackSafeAreaView()
                LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} ondissmiss")
                adsEcmpShow?.isLoaded = false
                adsEcmpShow?.adObject = null
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdDismissed(
                    showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId
                )
                mDialogNativeFull = null
            }
            val shown = NtFullDialogHost.show(dialog) { e ->
                LogUtil.logE("ads full nt admob$adsType $showPl show failed: ${e.message}")
                AppPluginManager.instance.unRegisterMyAppLifecycle(this)
                hideNativeFull()
                adsPlacementEcmp.isShowing = false
                adsPlacementEcmp.adsShowCb?.onAdShowFail(
                    showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, Constants.AdShowFailReason.ACT_DESTROY
                )
            }
            if (!shown) return false
            adsPlacementEcmp.adsShowCb?.onAdShowed(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, 0
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addBg4Ad(mActivity)
            }
            return true
        } else {
            LogUtil.logD("ads full nt admob$adsType $showPl ${adsPlacementEcmp.originPl} show faild ad null")
            hideNativeFull()
            adsPlacementEcmp.adsShowCb?.onAdShowFail(
                showPl, adsPlacementEcmp.originPl, adsEcmpShow?.adsId, "nativeAd null"
            )
            return false
        }
    }
```
Thêm `import com.app.plugin.ads.NtFullDialogHost` và `import com.app.plugin.until.Constants` nếu thiếu.

- [ ] **Step 5: Sửa `AdsFbPlNtFull.kt` và `AdsMaxPlNtFull.kt` cùng mẫu**

Cả hai file: thêm field `preparedActivity`, gán trong `prepareChainedShow`, xoá trong `discardPreparedDialog`; `showWidthDialog(): Boolean` với chốt `isUsable`, điều kiện dùng lại `preparedNativeAd === nativeAdShow && preparedActivity === host`; thay `mDialogNativeFull!!.show()` bằng `NtFullDialogHost.show(dialog) { … }` y hệt (d), đổi tiền tố log `fb`/`max`; chỗ gọi `showWidthDialog()` trả `shown` như (c). Constructor: Meta `AdsFbNtFullView(host!!, nativeAdShow!!, timeShowBtClose, false, true)`, MAX `AdsMaxNtFullView(host!!, nativeAdLoader!!, nativeAdShow!!, timeShowBtClose, false, true)`.

- [ ] **Step 6: Chạy test mới + toàn bộ test Full2**

Run: `./gradlew :testDebugUnitTest --tests 'com.app.plugin.ads.NtFullDialogHostTest' --tests 'com.app.plugin.ads.Full2*' --tests 'com.app.plugin.ads.AdsDeliveryRegressionTest' > DOC/testing/task4-green.log 2>&1; echo exit=$?`
Expected: `exit=0`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/app/plugin/ads/NtFullDialogHost.kt src/main/java/com/app/plugin/ads/adsmob/AdsMobPlNtFull.kt src/main/java/com/app/plugin/ads/adsfb/AdsFbPlNtFull.kt src/main/java/com/app/plugin/ads/adsmax/AdsMaxPlNtFull.kt src/test/java/com/app/plugin/ads/NtFullDialogHostTest.kt
git commit -m "fix(ads): native full không dùng lại dialog của Activity đã chết; show() có chốt BadTokenException

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 5: App demo build như app thật (targetSdk 36, R8 full, shrinkResources, StrictMode)

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Modify: `app/src/main/java/com/app/plugin/demo/DemoApplication.kt`

**Interfaces:** không có. Kết quả: mọi lỗi chỉ-release của SDK lộ ra trên app demo, ở CI và trên máy.

- [ ] **Step 1: `app/build.gradle.kts` — targetSdk 36, release có R8 + shrink, ký debug để cài được**

Thay khối `defaultConfig` và `buildTypes` bằng:
```kotlin
    defaultConfig {
        applicationId = "test.sdk.zd"
        minSdk = 26
        // Điều 20.1: từ 31/08/2026 app trên Play phải target 36. Demo target đúng như app thật.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Điều 5.2 / 8: bản release là sản phẩm thật. R8 full mode + shrinkResources bật để mọi
            // lỗi chỉ-release của SDK (keep rule, keep.xml) lộ ra ở đây, không phải ở app thật.
            isMinifyEnabled = true
            isShrinkResources = true
            // Ký debug: demo không phát hành; CI và emulator cài trực tiếp bản release.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

- [ ] **Step 2: Tạo `app/proguard-rules.pro` cố ý rỗng**

```
# App demo CỐ Ý không có keep rule riêng. Mọi thứ SDK cần phải nằm trong consumer-rules.pro và
# res/raw/appplugin_keep.xml của SDK. Thêm rule ở đây là giấu lỗi của SDK khỏi app thật.
```

- [ ] **Step 3: `DemoApplication.onCreate` — StrictMode khi `flagTest` bật, trước `super.onCreate()`**

```kotlin
package com.app.plugin.demo

import android.os.StrictMode
import android.util.Log
import com.app.plugin.MonetizationSdk
import com.app.plugin.app.AppPluginBase
import com.app.plugin.until.AppUtil

class DemoApplication : AppPluginBase() {

    override fun onCreate() {
        // Cổng G2 (DOC/65): đọc/ghi đĩa trên main thread của SDK phải = 0. Chỉ bật khi flagTest.
        if (AppUtil.isTestFlagOn(this)) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().detectNetwork()
                    .penaltyLog().build()
            )
        }
        super.onCreate()
        // … phần MonetizationSdk.configure giữ nguyên …
```

- [ ] **Step 4: Dựng release và soi**

Run:
```bash
./gradlew :app:assembleRelease > DOC/testing/task5-release.log 2>&1; echo exit=$?
unzip -p app/build/outputs/apk/release/app-release.apk resources.arsc | LC_ALL=C grep -a -o "ca-app-pub-[0-9/~]*" | wc -l
ls -la app/build/outputs/mapping/release/mapping.txt
```
Expected: `exit=0`, `8`, mapping.txt tồn tại.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/proguard-rules.pro app/src/main/java/com/app/plugin/demo/DemoApplication.kt
git commit -m "test(demo): app demo build như app thật: targetSdk 36, R8 full, shrinkResources, StrictMode khi flagTest

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 6: Cổng tự động: `tools/release_gate.sh`, `tools/device_gate.sh`, CI

**Files:**
- Create: `tools/release_gate.sh`, `tools/device_gate.sh`
- Modify: `.github/workflows/ci.yml` (thêm job `release-gate`)
- Create: `.github/workflows/device-gate.yml`

**Interfaces:**
- Produces: hai script trả exit 0/1, ghi log vào `DOC/testing/release-gate-*.log` và `DOC/testing/device-gate-<serial>-*.log`. Task 7, 17 và mọi task đụng ads gọi lại chúng.

- [ ] **Step 1: `tools/release_gate.sh`**

```bash
#!/usr/bin/env bash
# Cổng G3 (DOC/65): dựng app demo như app thật rồi soi APK và mapping. Exit 1 khi bất kỳ mục nào fail.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
APK=app/build/outputs/apk/release/app-release.apk
MAP=app/build/outputs/mapping/release/mapping.txt
mkdir -p DOC/testing
LOG=DOC/testing/release-gate-$(date +%Y%m%d-%H%M).log
fail=0

./gradlew :app:assembleRelease > /tmp/release-gate-build.log 2>&1
echo "build exit=$?" | tee "$LOG"
[ -f "$APK" ] || { echo "FAIL: không có APK (xem /tmp/release-gate-build.log)" | tee -a "$LOG"; exit 1; }

units=$(unzip -p "$APK" resources.arsc | LC_ALL=C grep -a -o "ca-app-pub-[0-9/~]*" | wc -l | tr -d ' ')
expected=$(grep -o "ca-app-pub-[0-9/~]*" app/src/main/res/values/ads_ids.xml | wc -l | tr -d ' ')
echo "ad units in resources.arsc=$units (need >= $expected)" | tee -a "$LOG"
[ "$units" -ge "$expected" ] || fail=1

[ -f "$MAP" ] || { echo "FAIL: không có mapping.txt" | tee -a "$LOG"; exit 1; }
total=$(grep -cE '^com\.app\.plugin\.[A-Za-z0-9_.$]+ -> ' "$MAP")
kept=$(grep -E '^com\.app\.plugin\.[A-Za-z0-9_.$]+ -> ' "$MAP" | awk '{ if ($1 == substr($3, 1, length($3) - 1)) n++ } END { print n + 0 }')
renamed=$((total - kept))
pct=0; [ "$total" -gt 0 ] && pct=$((renamed * 100 / total))
echo "sdk classes total=$total renamed=$renamed pct=$pct% (need >= 90)" | tee -a "$LOG"
[ "$pct" -ge 90 ] || fail=1

ZIPALIGN="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* 2>/dev/null | tail -1)/zipalign"
if [ -x "$ZIPALIGN" ]; then
  "$ZIPALIGN" -c -P 16 -v 4 "$APK" | tail -1 | tee -a "$LOG"
fi

echo "RESULT: $([ $fail -eq 0 ] && echo PASS || echo FAIL)" | tee -a "$LOG"
exit $fail
```

- [ ] **Step 2: `tools/device_gate.sh`**

```bash
#!/usr/bin/env bash
# Cổng G1/G2 (DOC/65) trên một máy: cài APK release, cold start, 4 chuỗi R8, StrictMode, monkey, FATAL/ANR.
set -u
: "${ANDROID_SERIAL:?đặt ANDROID_SERIAL (không dùng R5CX10P9EKZ, 65c9b673)}"
export PATH="$PATH:${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools"
cd "$(dirname "$0")/.."
APK=app/build/outputs/apk/release/app-release.apk
PKG=test.sdk.zd
SEEDS=${SEEDS:-1 2 3}
EVENTS=${EVENTS:-3000}
mkdir -p DOC/testing
LOG=DOC/testing/device-gate-$ANDROID_SERIAL-$(date +%Y%m%d-%H%M).log
fail=0

adb uninstall $PKG > /dev/null 2>&1
adb install -r "$APK" | tee "$LOG"
adb logcat -c; adb logcat -c -b crash; adb logcat -c -b events
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/com.app.plugin.demo.MainActivity --ez force_eea true | grep -E "TotalTime|WaitTime" | tee -a "$LOG"
adb shell sleep 20

r8=$(adb logcat -d | grep --text -cE "ClassNotFound|NoSuchMethod|NoClassDef|FATAL")
echo "r8 strings after cold start=$r8 (need 0)" | tee -a "$LOG"; [ "$r8" -eq 0 ] || fail=1
strict=$(adb logcat -d | grep --text -A14 "StrictMode" | grep --text -c "com.app.plugin")
echo "strictmode frames com.app.plugin=$strict (target 0, cổng từ 1.4.0)" | tee -a "$LOG"

for s in $SEEDS; do
  adb shell monkey -p $PKG --throttle 50 --pct-syskeys 0 --ignore-crashes --ignore-timeouts -s "$s" "$EVENTS" > "/tmp/monkey-$s.log" 2>&1
  echo "monkey seed=$s exit=$? crash/anr lines=$(grep -cE 'CRASH|ANR' "/tmp/monkey-$s.log")" | tee -a "$LOG"
done

fatal=$(adb logcat -d -b crash | grep --text -c "FATAL EXCEPTION")
anr=$(adb logcat -d -b events | grep --text -c am_anr)
echo "FATAL=$fatal am_anr=$anr (need 0/0)" | tee -a "$LOG"
{ [ "$fatal" -eq 0 ] && [ "$anr" -eq 0 ]; } || fail=1
adb logcat -d -b crash >> "$LOG"

echo "RESULT: $([ $fail -eq 0 ] && echo PASS || echo FAIL)" | tee -a "$LOG"
exit $fail
```

`chmod +x tools/release_gate.sh tools/device_gate.sh`.

- [ ] **Step 3: Chạy cả hai script trên máy dev, lần đầu phải thấy chúng "cắn"**

Run trước (đối chứng, Điều 6.7): `git stash push consumer-rules.pro src/main/res/raw/appplugin_keep.xml -m gate-control` → `tools/release_gate.sh; echo exit=$?` → kỳ vọng `ad units … =0`, `RESULT: FAIL`, `exit=1`. Rồi `git stash pop` → chạy lại → `RESULT: PASS`. Lưu cả hai log.

Rồi emulator: `export ANDROID_SERIAL=emulator-5570` (khởi động theo Global Constraints) → `tools/device_gate.sh; echo exit=$?` → kỳ vọng `RESULT: PASS`, `FATAL=0 am_anr=0`.

- [ ] **Step 4: CI — thêm job vào `.github/workflows/ci.yml` sau job `verify`**

```yaml
  release-gate:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "21"
      - uses: gradle/actions/setup-gradle@v5
      - name: Release gate (R8 full + shrinkResources trên app demo)
        run: tools/release_gate.sh
      - name: Upload gate log
        if: always()
        uses: actions/upload-artifact@v6
        with:
          name: release-gate
          path: DOC/testing/release-gate-*.log
          retention-days: 14
```
`app/google-services.json` đã có trong repo (xem `ls app`), nên job không cần secret.

- [ ] **Step 5: `.github/workflows/device-gate.yml` (chạy tay và hằng đêm; [cần kiểm chứng] runner có KVM)**

```yaml
name: Device gate

on:
  workflow_dispatch:
  schedule:
    - cron: "0 19 * * *"   # 02:00 giờ Việt Nam

permissions:
  contents: read

jobs:
  emulator:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    strategy:
      fail-fast: false
      matrix:
        api-level: [26, 30, 34]
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: "21"
      - uses: gradle/actions/setup-gradle@v5
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Build release demo
        run: ./gradlew :app:assembleRelease
      - name: Run device gate on emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          arch: x86_64
          target: google_apis
          script: ANDROID_SERIAL=emulator-5554 EVENTS=1500 tools/device_gate.sh
      - name: Upload gate log
        if: always()
        uses: actions/upload-artifact@v6
        with:
          name: device-gate-${{ matrix.api-level }}
          path: DOC/testing/device-gate-*.log
          retention-days: 14
```

- [ ] **Step 6: Commit**

```bash
git add tools/release_gate.sh tools/device_gate.sh .github/workflows/ci.yml .github/workflows/device-gate.yml
git commit -m "ci: cổng release (R8, keep.xml, mapping) mỗi PR và cổng thiết bị (crash, ANR, 4 chuỗi R8) chạy tay/hằng đêm

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 7: Kiểm lại toàn bộ Phase A trên emulator và máy thật; chốt 1.3.1

**Files:**
- Modify: `build.gradle.kts` (`sdkVersion = "1.3.1"`), `CHANGELOG.md` (đổi `## [Unreleased]` thành `## [1.3.1] - <ngày>` và thêm mục cho Task 1–4), `DOC/64_SDK_1_3_0_DEVICE_TEST.md` (thêm `## F. Kiểm lại 1.3.1`)

- [ ] **Step 1: Unit test đầy đủ ×3 (0 flaky)**

Run: `for i in 1 2 3; do ./gradlew :testDebugUnitTest > DOC/testing/task7-unit-$i.log 2>&1; echo run$i exit=$?; done`
Expected: 3 lần `exit=0`.

- [ ] **Step 2: `tools/release_gate.sh` → PASS.** Ghi 3 con số (ad units, pct rename, zipalign) vào DOC/64 §F.

- [ ] **Step 3: `tools/device_gate.sh` trên emulator API 37 (`emulator-5570`) và emulator API 26 (tạo: `sdkmanager "system-images;android-26;google_apis;arm64-v8a"`; nếu image arm64 cho 26 không có trên máy Apple Silicon thì dùng API 28 và ghi rõ) → cả hai PASS.**

- [ ] **Step 4: Kịch bản tay trên emulator API 37 (chạy lại 4 kịch bản hôm 2026-09-24, dùng `run_step.sh` trong scratchpad của phiên đó hoặc viết lại):** consent cài mới EEA (0 dòng init trước cú bấm), Interstitial → Full2, Rewarded → Full2 (thưởng 1 lần), xoay 4 lần khi Full2 hiện rồi đóng (không FATAL). Ghi thời gian handoff.

- [ ] **Step 5: Máy thật.** Hỏi chủ dự án serial được dùng (không phải hai serial cấm). Có máy: chạy `tools/device_gate.sh` + kịch bản Step 4, ghi serial và model vào DOC/64 §F. Không có máy: ghi "chưa kiểm trên máy thật" vào DOC/64 §F và **không** ghi 1.3.1 là "xong Phase A" trong DOC/65 mục 5.

- [ ] **Step 6: Cập nhật CHANGELOG (mục `### Sửa` thêm 4 dòng: Room/registrar rules, keep.xml, banner detach, Full2 Activity guard) và `sdkVersion = "1.3.1"`. Không tag.**

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts CHANGELOG.md DOC/64_SDK_1_3_0_DEVICE_TEST.md
git commit -m "chore(release): 1.3.1 — kiểm lại trên emulator API 26/37 (và máy thật nếu có); CHANGELOG

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

# Phase B — 1.4.0 "Đúng luật, đo đúng, không hỏng câm"

## Task 8: Tier consent đọc từ TCF theo purpose; lưu và khôi phục tier

**Files:**
- Create: `src/main/java/com/app/plugin/consent/TcfConsent.kt`
- Modify: `src/main/java/com/app/plugin/consent/ConsentState.kt` (thêm `TierStore`, lưu trong `setTier`, `restore()`)
- Modify: `src/main/java/com/app/plugin/consent/ConsentManager.kt:483-485` (`updateTier`)
- Modify: `src/main/java/com/app/plugin/app/AppPluginBase.kt` (sau `PreferenceHelper.getInstance().init(this)`: cài store và `restore()`)
- Test: `src/test/java/com/app/plugin/consent/TcfConsentTest.kt`, thêm test vào `src/test/java/com/app/plugin/consent/ConsentStateTest.kt`

**Interfaces:**
- Produces: `internal data class TcfSignals(gdprApplies: Int?, purposeConsents: String, purposeLegitimateInterests: String, vendorConsents: String)`; `internal object TcfConsent { fun read(context: Context): TcfSignals; fun read(prefs: SharedPreferences): TcfSignals; fun tierOf(s: TcfSignals): ConsentTier; fun gdprApplies(context: Context): Boolean }`; `interface ConsentState.TierStore { fun save(name: String); fun load(): String? }`; `ConsentState.installStore(store)`; `ConsentState.restore()`.

- [ ] **Step 1: Viết `TcfConsentTest`**

```kotlin
package com.app.plugin.consent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Do not consent" trên form UMP cho IABTCF_PurposeConsents = 0000000000 nhưng ConsentManager vẫn
 * dịch OBTAINED thành GRANTED (đo 2026-09-24). Tier phải đọc từ TCF theo purpose.
 * Luật theo yêu cầu CMP của Google cho AdMob: quảng cáo giới hạn cần purpose 1 + vendor 755 +
 * purpose 2,7,9,10 (consent hoặc LI); quảng cáo cá nhân hoá cần thêm purpose 3 và 4.
 */
class TcfConsentTest {
    private val googleOk = "0".repeat(754) + "1"

    private fun signals(purposes: String, li: String = "0000000000", vendors: String = googleOk, gdpr: Int? = 1) =
        TcfSignals(gdprApplies = gdpr, purposeConsents = purposes, purposeLegitimateInterests = li, vendorConsents = vendors)

    @Test fun `dong y het thi GRANTED`() =
        assertEquals(ConsentTier.GRANTED, TcfConsent.tierOf(signals("1111111111")))

    @Test fun `khong purpose 3 va 4 thi PARTIAL`() =
        assertEquals(ConsentTier.PARTIAL, TcfConsent.tierOf(signals("1100001011")))

    @Test fun `purpose 2 7 9 10 qua legitimate interest van du cho PARTIAL`() =
        assertEquals(ConsentTier.PARTIAL, TcfConsent.tierOf(signals("1000000000", li = "0100001011")))

    @Test fun `tu choi het thi DENIED`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.tierOf(signals("0000000000")))

    @Test fun `khong co vendor Google thi DENIED`() =
        assertEquals(ConsentTier.DENIED, TcfConsent.tierOf(signals("1111111111", vendors = "1")))

    @Test fun `ngoai pham vi GDPR thi GRANTED`() =
        assertEquals(ConsentTier.GRANTED, TcfConsent.tierOf(signals("0000000000", gdpr = 0)))

    @Test fun `chua co IABTCF thi UNKNOWN`() =
        assertEquals(ConsentTier.UNKNOWN, TcfConsent.tierOf(signals("", gdpr = null)))
}
```

- [ ] **Step 2: Chạy, phải đỏ** — `./gradlew :testDebugUnitTest --tests 'com.app.plugin.consent.TcfConsentTest' > DOC/testing/task8-red.log 2>&1; echo exit=$?` → `exit=1`, unresolved reference.

- [ ] **Step 3: Viết `TcfConsent.kt`**

```kotlin
package com.app.plugin.consent

import android.content.Context
import android.content.SharedPreferences

/** Các khoá IABTCF_* mà UMP ghi vào default SharedPreferences của app (TCF v2.2 CMP API). */
internal data class TcfSignals(
    val gdprApplies: Int?,
    val purposeConsents: String,
    val purposeLegitimateInterests: String,
    val vendorConsents: String
)

internal object TcfConsent {
    const val GOOGLE_VENDOR_ID = 755
    private const val P_STORAGE = 1
    private const val P_BASIC_ADS = 2
    private const val P_PROFILE = 3
    private const val P_PERSONALISED_ADS = 4
    private const val P_MEASURE_ADS = 7
    private const val P_MARKET_RESEARCH = 9
    private const val P_IMPROVE = 10

    fun read(context: Context): TcfSignals =
        read(context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE))

    fun read(prefs: SharedPreferences): TcfSignals = try {
        TcfSignals(
            gdprApplies = if (prefs.contains("IABTCF_gdprApplies")) prefs.getInt("IABTCF_gdprApplies", 0) else null,
            purposeConsents = prefs.getString("IABTCF_PurposeConsents", "") ?: "",
            purposeLegitimateInterests = prefs.getString("IABTCF_PurposeLegitimateInterests", "") ?: "",
            vendorConsents = prefs.getString("IABTCF_VendorConsents", "") ?: ""
        )
    } catch (e: Exception) {
        TcfSignals(null, "", "", "")
    }

    fun gdprApplies(context: Context): Boolean = read(context).gdprApplies == 1

    /**
     * GRANTED: đủ điều kiện quảng cáo cá nhân hoá. PARTIAL: chỉ đủ cho quảng cáo giới hạn/không cá
     * nhân hoá → brain không tích luỹ hồ sơ (ConsentState.allowsProfiling). DENIED: thiếu purpose 1
     * hoặc vendor Google. UNKNOWN: UMP chưa ghi gì (chưa hỏi).
     */
    fun tierOf(s: TcfSignals): ConsentTier {
        val gdpr = s.gdprApplies ?: return ConsentTier.UNKNOWN
        if (gdpr == 0) return ConsentTier.GRANTED
        val storage = has(s.purposeConsents, P_STORAGE)
        val google = has(s.vendorConsents, GOOGLE_VENDOR_ID)
        val basic = listOf(P_BASIC_ADS, P_MEASURE_ADS, P_MARKET_RESEARCH, P_IMPROVE)
            .all { has(s.purposeConsents, it) || has(s.purposeLegitimateInterests, it) }
        if (!storage || !google || !basic) return ConsentTier.DENIED
        val personalised = has(s.purposeConsents, P_PROFILE) && has(s.purposeConsents, P_PERSONALISED_ADS)
        return if (personalised) ConsentTier.GRANTED else ConsentTier.PARTIAL
    }

    private fun has(bits: String, index1: Int): Boolean =
        index1 >= 1 && bits.length >= index1 && bits[index1 - 1] == '1'
}
```

- [ ] **Step 4: Chạy, phải xanh** — cùng lệnh, log `task8-green.log`, `exit=0`.

- [ ] **Step 5: Lưu tier — thêm vào `ConsentStateTest`**

```kotlin
    private class MemoryStore : ConsentState.TierStore {
        var saved: String? = null
        override fun save(name: String) { saved = name }
        override fun load(): String? = saved
    }

    @Test
    fun `tier duoc luu khi doi va khoi phuc khi mo lai`() {
        val store = MemoryStore()
        ConsentState.installStore(store)
        ConsentState.setTier(ConsentTier.DENIED)
        assertEquals("DENIED", store.saved)

        ConsentState.resetForTest()          // giả lập process mới
        ConsentState.installStore(store)
        ConsentState.restore()
        assertEquals(ConsentTier.DENIED, ConsentState.tier())
    }

    @Test
    fun `gia tri la trong store thi giu UNKNOWN`() {
        val store = MemoryStore().apply { saved = "rac" }
        ConsentState.installStore(store)
        ConsentState.restore()
        assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
    }
```
Chạy → đỏ (`installStore` chưa có). Rồi thêm vào `ConsentState`:

```kotlin
    interface TierStore {
        fun save(name: String)
        fun load(): String?
    }

    @Volatile
    private var store: TierStore? = null

    fun installStore(s: TierStore) { store = s }

    /** Gọi lúc mở process, trước khi UMP trả lời, để tầng phục vụ của phiên trước có hiệu lực ngay. */
    fun restore() {
        val name = store?.load() ?: return
        val tier = ConsentTier.values().firstOrNull { it.name == name } ?: return
        setTier(tier)
    }
```
Trong `setTier`, ngay sau `current = tier` thêm `try { store?.save(tier.name) } catch (e: Exception) { }`. Trong `resetForTest` thêm `store = null`.

- [ ] **Step 6: Nối vào SDK**

`ConsentManager.updateTier` (dòng 483–485):
```kotlin
    private fun updateTier(ci: ConsentInformation) {
        val fromTcf = appContext?.let { TcfConsent.tierOf(TcfConsent.read(it)) } ?: ConsentTier.UNKNOWN
        val tier = if (ci.consentStatus == ConsentInformation.ConsentStatus.OBTAINED && fromTcf != ConsentTier.UNKNOWN) fromTcf else translate(ci)
        LogUtil.logD("consent tier=$tier (ump=${ci.consentStatus}, tcf=$fromTcf)")
        ConsentState.setTier(tier)
```
`ConsentManager` hiện không giữ context (đã kiểm: `grep -n appContext ConsentManager.kt` trống). Thêm `@Volatile private var appContext: Context? = null` và gán `appContext = activity.applicationContext` ở dòng đầu của `gatherConsent` (dòng 165) và `updateConsentInfo` (dòng 199).

`AppPluginBase.onCreate`, ngay sau `PreferenceHelper.getInstance().init(this)`:
```kotlin
        ConsentState.installStore(object : ConsentState.TierStore {
            override fun save(name: String) = PreferenceHelper.getInstance().setValue("consent_tier", name)
            override fun load(): String? = PreferenceHelper.getInstance().get("consent_tier", "").ifEmpty { null }
        })
        ConsentState.restore()
```

- [ ] **Step 7: Toàn bộ test consent xanh** — `./gradlew :testDebugUnitTest --tests 'com.app.plugin.consent.*' > DOC/testing/task8-green.log 2>&1; echo exit=$?` → `exit=0`.

- [ ] **Step 8: Kiểm trên emulator (cổng G4):** cài mới `--ez force_eea true`, bấm "Do not consent" → logcat `consent tier=DENIED (ump=OBTAINED, tcf=DENIED)`; mở lại app → log `restore` cho DENIED trước khi UMP trả lời. Lưu `DOC/testing/task8-device.log`.

- [ ] **Step 9: CHANGELOG `## [Unreleased]` → "Đổi": tier consent đọc TCF; "Do not consent" giờ là DENIED nên brain/CRM/offerwall tắt đúng. Commit**

```bash
git add src/main/java/com/app/plugin/consent/TcfConsent.kt src/main/java/com/app/plugin/consent/ConsentState.kt src/main/java/com/app/plugin/consent/ConsentManager.kt src/main/java/com/app/plugin/app/AppPluginBase.kt src/test/java/com/app/plugin/consent/TcfConsentTest.kt src/test/java/com/app/plugin/consent/ConsentStateTest.kt CHANGELOG.md
git commit -m "fix(consent): tier đọc từ IABTCF theo purpose (Google vendor 755); lưu và khôi phục tier qua phiên

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 9: Firebase Analytics (Consent Mode v2), FCM và AppsFlyer chỉ chạy sau consent

**Files:**
- Create: `src/main/java/com/app/plugin/consent/AnalyticsConsent.kt`
- Modify: `src/main/java/com/app/plugin/appsflyer/AppsflyerHelper.kt:24-35` (`initHelper` → `startAfterConsent`)
- Modify: `src/main/java/com/app/plugin/ads/AdSdkStartup.kt` (thêm task `analytics`)
- Modify: `src/main/java/com/app/plugin/app/AppPluginManager.kt:44-52` (bỏ `Firebase.messaging.isAutoInitEnabled = true` và `AppsflyerHelper.instance.initHelper(context)`; gọi `AdSdkStartup.requestAnalytics(context)`)
- Modify: `src/main/AndroidManifest.xml` (mặc định Consent Mode = denied, FCM không auto-init)
- Test: `src/test/java/com/app/plugin/consent/AnalyticsConsentTest.kt`

**Interfaces:**
- Produces: `internal object AnalyticsConsent { data class Settings(analyticsStorage, adStorage, adUserData, adPersonalization: Boolean); fun settingsFor(tier: ConsentTier, gdprApplies: Boolean): Settings; fun apply(context: Context) }`; `AppsflyerHelper.startAfterConsent(context, tier, gdprApplies)`; `AdSdkStartup.requestAnalytics(context)`.

- [ ] **Step 1: Test bảng ánh xạ**

```kotlin
package com.app.plugin.consent

import org.junit.Assert.assertEquals
import org.junit.Test

/** Consent Mode v2: bốn loại, denied cho tới khi biết; ngoài GDPR thì granted. */
class AnalyticsConsentTest {
    private fun s(a: Boolean, b: Boolean, c: Boolean, d: Boolean) = AnalyticsConsent.Settings(a, b, c, d)

    @Test fun `ngoai GDPR thi cap het`() =
        assertEquals(s(true, true, true, true), AnalyticsConsent.settingsFor(ConsentTier.UNKNOWN, gdprApplies = false))

    @Test fun `GRANTED thi cap het`() =
        assertEquals(s(true, true, true, true), AnalyticsConsent.settingsFor(ConsentTier.GRANTED, gdprApplies = true))

    @Test fun `PARTIAL thi luu tru duoc, khong dung du lieu nguoi dung, khong ca nhan hoa`() =
        assertEquals(s(true, true, false, false), AnalyticsConsent.settingsFor(ConsentTier.PARTIAL, gdprApplies = true))

    @Test fun `DENIED va UNKNOWN trong EEA thi tu choi het`() {
        assertEquals(s(false, false, false, false), AnalyticsConsent.settingsFor(ConsentTier.DENIED, gdprApplies = true))
        assertEquals(s(false, false, false, false), AnalyticsConsent.settingsFor(ConsentTier.UNKNOWN, gdprApplies = true))
    }
}
```
Chạy → đỏ (`task9-red.log`).

- [ ] **Step 2: `AnalyticsConsent.kt`**

```kotlin
package com.app.plugin.consent

import android.content.Context
import com.app.plugin.until.LogUtil
import com.google.firebase.analytics.FirebaseAnalytics

internal object AnalyticsConsent {
    data class Settings(
        val analyticsStorage: Boolean,
        val adStorage: Boolean,
        val adUserData: Boolean,
        val adPersonalization: Boolean
    )

    fun settingsFor(tier: ConsentTier, gdprApplies: Boolean): Settings = when {
        !gdprApplies -> Settings(true, true, true, true)
        tier == ConsentTier.GRANTED -> Settings(true, true, true, true)
        tier == ConsentTier.PARTIAL -> Settings(true, true, false, false)
        else -> Settings(false, false, false, false)
    }

    /** Gọi mỗi khi tier đổi. Firebase giữ giá trị này qua các phiên. */
    fun apply(context: Context) {
        val s = settingsFor(ConsentState.tier(), TcfConsent.gdprApplies(context))
        try {
            val fa = FirebaseAnalytics.getInstance(context)
            fa.setAnalyticsCollectionEnabled(s.analyticsStorage)
            fa.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status(s.analyticsStorage),
                    FirebaseAnalytics.ConsentType.AD_STORAGE to status(s.adStorage),
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to status(s.adUserData),
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to status(s.adPersonalization)
                )
            )
            LogUtil.logD("analytics consent applied: $s")
        } catch (e: Exception) {
            LogUtil.logE("analytics consent apply failed: ${e.message}")
        }
    }

    private fun status(granted: Boolean) =
        if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
}
```
Chạy test → xanh (`task9-green.log`).

- [ ] **Step 3: AppsFlyer sau consent — thay `initHelper` bằng:**

```kotlin
    @Volatile private var started = false

    /**
     * Khởi động AppsFlyer sau khi consent đã có câu trả lời (AdSdkStartup). Gọi lại là no-op.
     * Consent đưa cho AppsFlyer bằng AppsFlyerConsent 4 trường (6.16.x):
     * (isUserSubjectToGDPR, hasConsentForDataUsage, hasConsentForAdsPersonalization, hasConsentForAdStorage).
     */
    fun startAfterConsent(context: Context, tier: ConsentTier, gdprApplies: Boolean) {
        if (started) return
        val idApp = AppUtil.getResourceString(context, "af_app_id")
        if (idApp.isEmpty()) {
            LogUtil.logE("af_app_id missing — AppsFlyer disabled (add string resource af_app_id to enable)")
            return
        }
        val consent = if (gdprApplies) {
            val usable = tier == ConsentTier.GRANTED || tier == ConsentTier.PARTIAL
            AppsFlyerConsent(true, usable, tier == ConsentTier.GRANTED, usable)
        } else {
            AppsFlyerConsent(false)
        }
        AppsFlyerLib.getInstance().setConsentData(consent)
        AppsFlyerLib.getInstance().init(idApp, this, context)
        AppsFlyerLib.getInstance().start(context)
        started = true
    }
```
(import `com.appsflyer.AppsFlyerConsent`, `com.app.plugin.consent.ConsentTier`). Xoá `initHelper`.

- [ ] **Step 4: `AdSdkStartup` thêm task thứ ba**

```kotlin
    private val analytics = Task("analytics") { startAnalytics(it) }
    private val tasks = listOf(adNetworks, metaCore, analytics)

    fun requestAnalytics(context: Context) = request(analytics, context)

    private fun startAnalytics(context: Context) {
        AnalyticsConsent.apply(context)
        ConsentState.onChange { AnalyticsConsent.apply(context) }
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled = true
        } catch (e: Exception) {
            LogUtil.logE("fcm auto-init failed: ${e.message}")
        }
        AppsflyerHelper.instance.startAfterConsent(context, ConsentState.tier(), TcfConsent.gdprApplies(context))
    }
```
(import `com.app.plugin.appsflyer.AppsflyerHelper`, `com.app.plugin.consent.AnalyticsConsent`, `com.app.plugin.consent.ConsentState`, `com.app.plugin.consent.TcfConsent`.)

- [ ] **Step 5: `AppPluginManager.initPlugin`**: giữ `FirebaseApp.initializeApp(context)`; xoá dòng `Firebase.messaging.isAutoInitEnabled = true`; thay `AppsflyerHelper.instance.initHelper(context)` bằng `com.app.plugin.ads.AdSdkStartup.requestAnalytics(context)`. `AdSdkStartup` là `internal` cùng module nên gọi được.

- [ ] **Step 6: Manifest SDK — thêm trong `<application>`:**

```xml
        <!-- Consent Mode v2: từ chối cho tới khi AnalyticsConsent.apply() đặt theo tier (Điều 13.3, doc 60 E). -->
        <meta-data android:name="google_analytics_default_allow_analytics_storage" android:value="false" />
        <meta-data android:name="google_analytics_default_allow_ad_storage" android:value="false" />
        <meta-data android:name="google_analytics_default_allow_ad_user_data" android:value="false" />
        <meta-data android:name="google_analytics_default_allow_ad_personalization_signals" android:value="false" />
        <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />
```

- [ ] **Step 7: Kiểm trên emulator (G4):** cài mới EEA: trước cú bấm consent, `adb logcat -d | grep -E "AppsFlyer_|FirebaseMessaging" | wc -l` = 0 và `adb shell setprop log.tag.FA VERBOSE; adb shell setprop log.tag.FA-SVC VERBOSE` rồi sau khi bấm thấy `Setting consent`; mở lại app ở chế độ không EEA (không `force_eea`): AppsFlyer start trong 1 s. Lưu `DOC/testing/task9-device.log`.

- [ ] **Step 8: CHANGELOG "Phải làm khi nâng cấp": AppsFlyer, FCM và Firebase Analytics chỉ chạy sau khi UMP trả lời; app không chạy UMP thì không có analytics — đặt `consentHandledExternally` không đủ, phải gọi `ConsentManager.updateConsentInfo`. Commit**

```bash
git add src/main/java/com/app/plugin/consent/AnalyticsConsent.kt src/main/java/com/app/plugin/appsflyer/AppsflyerHelper.kt src/main/java/com/app/plugin/ads/AdSdkStartup.kt src/main/java/com/app/plugin/app/AppPluginManager.kt src/main/AndroidManifest.xml src/test/java/com/app/plugin/consent/AnalyticsConsentTest.kt CHANGELOG.md
git commit -m "feat(consent): Consent Mode v2 cho Firebase Analytics; FCM và AppsFlyer khởi động sau consent với AppsFlyerConsent

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 10: Một policy tần suất `ads_policy_android` (doc 62 F)

**Files:**
- Create: `src/main/java/com/app/plugin/ads/AdsPolicy.kt`
- Modify: `src/main/java/com/app/plugin/ads/AdsHelper.kt` (field + `applyPolicy`, hook trong `showInterstitial` sau kiểm remove-ads, hook trong `showOpenAd` sau kiểm `suppressOpenAdUntilMillis`, `fullScreenAdPolicy` thành `var`)
- Modify: `src/main/java/com/app/plugin/until/Constants.kt` (`AdShowFailReason` thêm 3 hằng)
- Modify: `src/main/java/com/app/plugin/firbase/FirHelperConfig.kt` (`RemoteCfSnapshot` + `readRemoteCf` + `applyRemoteCf`)
- Test: `src/test/java/com/app/plugin/ads/AdsPolicyTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class AdsPolicy(
    val schemaVersion: Int = 1,
    val noInterstitialFirstSession: Boolean = true,
    val appOpenFromSession: Int = 2,
    val interstitialMinGapSeconds: Int = 240,
    val screensBeforeInterstitial: Int = 0,
    val postClickCooldownSeconds: Int = 5,
    val appOpenMaxAgeMinutes: Int = 240
) {
    fun interstitialVerdict(sessionCount: Int, secondsSinceLast: Long): String?   // null = cho phép; ngược lại là reason
    fun appOpenVerdict(sessionCount: Int): String?
    companion object { val DEFAULT = AdsPolicy(); fun parse(json: String?): AdsPolicy }
}
```
`Constants.AdShowFailReason.POLICY_FIRST_SESSION = "policy_first_session"`, `POLICY_MIN_GAP = "policy_min_gap"`, `POLICY_APP_OPEN_SESSION = "policy_app_open_session"`. `AdsHelper.adsPolicy: AdsPolicy`, `AdsHelper.applyPolicy(p: AdsPolicy)`.

- [ ] **Step 1: Test**

```kotlin
package com.app.plugin.ads

import com.app.plugin.until.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Doc 62 F: luật tần suất nằm ở một chỗ, mặc định an toàn khi Remote Config chưa về (Điều 7.3). */
class AdsPolicyTest {

    @Test fun `khong co config thi dung mac dinh doc 62`() {
        assertEquals(AdsPolicy.DEFAULT, AdsPolicy.parse(null))
        assertEquals(AdsPolicy.DEFAULT, AdsPolicy.parse(""))
        assertEquals(AdsPolicy.DEFAULT, AdsPolicy.parse("{ rac"))
        assertEquals(240, AdsPolicy.DEFAULT.interstitialMinGapSeconds)
        assertEquals(2, AdsPolicy.DEFAULT.appOpenFromSession)
        assertEquals(0, AdsPolicy.DEFAULT.screensBeforeInterstitial)
    }

    @Test fun `truong thieu giu mac dinh, truong co thi doc`() {
        val p = AdsPolicy.parse("""{"schema_version":1,"interstitial_min_gap_seconds":30}""")
        assertEquals(30, p.interstitialMinGapSeconds)
        assertEquals(AdsPolicy.DEFAULT.appOpenFromSession, p.appOpenFromSession)
    }

    @Test fun `gia tri ngoai khoang bi kep lai`() {
        val p = AdsPolicy.parse("""{"interstitial_min_gap_seconds":-5,"app_open_from_session":0,"app_open_max_age_minutes":99999,"post_click_cooldown_seconds":900}""")
        assertEquals(0, p.interstitialMinGapSeconds)
        assertEquals(1, p.appOpenFromSession)
        assertEquals(1440, p.appOpenMaxAgeMinutes)
        assertEquals(60, p.postClickCooldownSeconds)
    }

    @Test fun `schema la thi dung mac dinh`() =
        assertEquals(AdsPolicy.DEFAULT, AdsPolicy.parse("""{"schema_version":2,"interstitial_min_gap_seconds":1}"""))

    @Test fun `phien dau khong interstitial`() =
        assertEquals(Constants.AdShowFailReason.POLICY_FIRST_SESSION, AdsPolicy.DEFAULT.interstitialVerdict(sessionCount = 1, secondsSinceLast = 9_999))

    @Test fun `chua du khoang cach thi chan`() =
        assertEquals(Constants.AdShowFailReason.POLICY_MIN_GAP, AdsPolicy.DEFAULT.interstitialVerdict(sessionCount = 2, secondsSinceLast = 239))

    @Test fun `du khoang cach va khong phai phien dau thi cho phep`() =
        assertNull(AdsPolicy.DEFAULT.interstitialVerdict(sessionCount = 2, secondsSinceLast = 240))

    @Test fun `app open tu phien thu hai`() {
        assertEquals(Constants.AdShowFailReason.POLICY_APP_OPEN_SESSION, AdsPolicy.DEFAULT.appOpenVerdict(sessionCount = 1))
        assertNull(AdsPolicy.DEFAULT.appOpenVerdict(sessionCount = 2))
    }
}
```
Chạy → đỏ (`task10-red.log`).

- [ ] **Step 2: `Constants.AdShowFailReason` thêm**

```kotlin
        // Luật tần suất thống nhất (AdsPolicy, doc 62 F). Tách khỏi NOT_ALLOW_SHOW để đối soát được.
        const val POLICY_FIRST_SESSION = "policy_first_session"
        const val POLICY_MIN_GAP = "policy_min_gap"
        const val POLICY_APP_OPEN_SESSION = "policy_app_open_session"
```

- [ ] **Step 3: `AdsPolicy.kt`**

```kotlin
package com.app.plugin.ads

import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Luật tần suất duy nhất của SDK (doc 62 F), cấu hình bằng khoá Remote Config `ads_policy_android`.
 * Mặc định là chuẩn portfolio; app đặt khoá riêng để ghi đè. Brain chỉ được chặt hơn, không nới.
 *
 * `screensBeforeInterstitial` mặc định 0 (tắt), khác doc 62 F ghi 4: app không gọi
 * `AdsHelper.recordScreenChange()` sẽ bị chặn interstitial vĩnh viễn sau lượt đầu (Điều 7.4).
 * App nào báo màn thì bật qua Remote Config.
 */
data class AdsPolicy(
    val schemaVersion: Int = 1,
    val noInterstitialFirstSession: Boolean = true,
    val appOpenFromSession: Int = 2,
    val interstitialMinGapSeconds: Int = 240,
    val screensBeforeInterstitial: Int = 0,
    val postClickCooldownSeconds: Int = 5,
    val appOpenMaxAgeMinutes: Int = 240
) {
    /** @return lý do chặn (một hằng `AdShowFailReason`) hoặc null nếu cho phép. */
    fun interstitialVerdict(sessionCount: Int, secondsSinceLast: Long): String? = when {
        noInterstitialFirstSession && sessionCount <= 1 -> Constants.AdShowFailReason.POLICY_FIRST_SESSION
        secondsSinceLast < interstitialMinGapSeconds -> Constants.AdShowFailReason.POLICY_MIN_GAP
        else -> null
    }

    fun appOpenVerdict(sessionCount: Int): String? =
        if (sessionCount < appOpenFromSession) Constants.AdShowFailReason.POLICY_APP_OPEN_SESSION else null

    companion object {
        const val SCHEMA_VERSION = 1
        val DEFAULT = AdsPolicy()

        fun parse(json: String?): AdsPolicy {
            if (json.isNullOrBlank()) return DEFAULT
            val o: JsonObject = try {
                JsonParser.parseString(json).asJsonObject
            } catch (e: Exception) {
                LogUtil.logE("ads_policy_android không phải JSON object: ${e.message}")
                return DEFAULT
            }
            val schema = o.int("schema_version", SCHEMA_VERSION)
            if (schema != SCHEMA_VERSION) {
                LogUtil.logE("ads_policy_android schema $schema không hỗ trợ, dùng mặc định")
                return DEFAULT
            }
            return AdsPolicy(
                schemaVersion = schema,
                noInterstitialFirstSession = o.bool("no_interstitial_first_session", DEFAULT.noInterstitialFirstSession),
                appOpenFromSession = o.int("app_open_from_session", DEFAULT.appOpenFromSession).coerceIn(1, 100),
                interstitialMinGapSeconds = o.int("interstitial_min_gap_seconds", DEFAULT.interstitialMinGapSeconds).coerceIn(0, 3600),
                screensBeforeInterstitial = o.int("screens_before_interstitial", DEFAULT.screensBeforeInterstitial).coerceIn(0, 50),
                postClickCooldownSeconds = o.int("post_click_cooldown_seconds", DEFAULT.postClickCooldownSeconds).coerceIn(0, 60),
                appOpenMaxAgeMinutes = o.int("app_open_max_age_minutes", DEFAULT.appOpenMaxAgeMinutes).coerceIn(1, 1440)
            )
        }

        private fun JsonObject.int(key: String, def: Int): Int =
            try { if (has(key) && get(key).isJsonPrimitive) get(key).asInt else def } catch (e: Exception) { def }

        private fun JsonObject.bool(key: String, def: Boolean): Boolean =
            try { if (has(key) && get(key).isJsonPrimitive) get(key).asBoolean else def } catch (e: Exception) { def }
    }
}
```
Chạy test → xanh (`task10-green.log`).

- [ ] **Step 4: Nối vào `AdsHelper`**

(a) Field (cạnh dòng 108, đổi `val` thành `var`):
```kotlin
    private var fullScreenAdPolicy = FullScreenAdPolicy()

    @Volatile
    var adsPolicy: AdsPolicy = AdsPolicy.DEFAULT
        private set

    fun applyPolicy(policy: AdsPolicy) {
        adsPolicy = policy
        fullScreenAdPolicy = FullScreenAdPolicy(policy.postClickCooldownSeconds * 1000L)
        setPostAdScreenCooldown(policy.screensBeforeInterstitial)
        LogUtil.logD("ads policy schema=${policy.schemaVersion} gap=${policy.interstitialMinGapSeconds}s appOpenFrom=${policy.appOpenFromSession}")
    }
```
(b) `showInterstitial`, ngay sau khối `isRemoveAds(4) || isDisableAds(4)`:
```kotlin
        if (isDeltaTime) {
            val verdict = adsPolicy.interstitialVerdict(
                sessionCount = AppPluginManager.instance.getSessionApp(),
                secondsSinceLast = System.currentTimeMillis() / 1000 - fullTimeShow
            )
            if (verdict != null) {
                LogUtil.logD("ads full helper $realPl showInterstitial policy $verdict")
                LogEventServer.logAdShowFailWithReason(placement = realPl, reason = verdict)
                return false
            }
        }
```
(c) `showOpenAd`, ngay sau khối `suppressOpenAdUntilMillis`:
```kotlin
        adsPolicy.appOpenVerdict(AppPluginManager.instance.getSessionApp())?.let { verdict ->
            LogUtil.logD("ads full helper $realPl showOpenAd policy $verdict")
            LogEventServer.logAdShowFailWithReason(placement = realPl, reason = verdict)
            return false
        }
```
(d) `isIntersAllowShowAds` trả `false` hiện không log lý do (cổng G6): thêm `LogEventServer.logAdShowFailWithReason(placement = realPl, reason = Constants.AdShowFailReason.NOT_ALLOW_SHOW)` ở nhánh `!isIntersAllowShowAds() && isDeltaTime` trong `showInterstitial`.

- [ ] **Step 5: Remote Config — `FirHelperConfig`**: `RemoteCfSnapshot` thêm `val adsPolicy: String`; `readRemoteCf` thêm `adsPolicy = remoteConfig.getString("ads_policy_android")`; `applyRemoteCf` thêm ngay sau `parserAdsConfig`:
```kotlin
        AdsHelper.instance.applyPolicy(AdsPolicy.parse(snapshot.adsPolicy))
```

- [ ] **Step 6: Toàn bộ test ads** — `./gradlew :testDebugUnitTest --tests 'com.app.plugin.ads.*' > DOC/testing/task10-green.log 2>&1; echo exit=$?` → `exit=0`. Nếu test có sẵn gọi `showInterstitial` ở "phiên 1" (mock `AppPluginManager` trả 0): những test đó dựng `AdsHelper` bằng reflection, `getSessionApp()` trả 0 → policy chặn. Sửa test bằng cách gọi `helper.applyPolicy(AdsPolicy(noInterstitialFirstSession = false, interstitialMinGapSeconds = 0))` trong `setUp`, không sửa policy.

- [ ] **Step 7: Emulator (G5):** cài mới, phiên 1: bấm Interstitial → log `policy policy_first_session`; mở lại (phiên 2): hiện; bấm lại trong 240 s → `policy_min_gap`. Lưu `DOC/testing/task10-device.log`.

- [ ] **Step 8: CHANGELOG "Phải làm khi nâng cấp": mặc định 240 s và phiên đầu không interstitial; app muốn giữ hành vi cũ đặt `ads_policy_android` = `{"schema_version":1,"no_interstitial_first_session":false,"interstitial_min_gap_seconds":30,"app_open_from_session":1}` TRƯỚC khi phát hành bản dùng SDK 1.4.0. Commit**

```bash
git add src/main/java/com/app/plugin/ads/AdsPolicy.kt src/main/java/com/app/plugin/ads/AdsHelper.kt src/main/java/com/app/plugin/until/Constants.kt src/main/java/com/app/plugin/firbase/FirHelperConfig.kt src/test/java/com/app/plugin/ads/AdsPolicyTest.kt CHANGELOG.md
git commit -m "feat(ads): một policy tần suất ads_policy_android (doc 62 F): phiên đầu không interstitial, cách 240 s, app open từ phiên 2

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 11: App open hết hạn sau 4 giờ

**Files:**
- Modify: `src/main/java/com/app/plugin/ads/AdsEcmps.kt:47-70` (`getAdLoaded` nhận `maxAgeMillis`)
- Modify: `src/main/java/com/app/plugin/ads/adsmob/AdsMobPlOpen.kt:142` (truyền tuổi tối đa từ policy)
- Modify: `src/main/java/com/app/plugin/until/Constants.kt` (`AD_EXPIRED = "ad_expired"`)
- Test: thêm vào `src/test/java/com/app/plugin/ads/AdsEcmpsTest.kt`

**Interfaces:**
- Produces: `AdsEcmps.getAdLoaded(maxAgeMillis: Long = Long.MAX_VALUE, nowMillis: Long = System.currentTimeMillis()): AdsEcmp?`

- [ ] **Step 1: Test (thêm vào `AdsEcmpsTest`, dùng `ecmps` của `setUp`)**

```kotlin
    @Test
    fun `quang cao qua tuoi thi khong tra ve va duoc danh dau chua load`() {
        ecmps.listFromData("ca-app-pub-1/100;ca-app-pub-1/200")
        val old = ecmps.list[0].apply { isLoaded = true; adObject = Any(); loadedAtMillis = 1_000L }
        val fresh = ecmps.list[1].apply { isLoaded = true; adObject = Any(); loadedAtMillis = 5_000_000L }

        val picked = ecmps.getAdLoaded(maxAgeMillis = 4 * 60 * 60 * 1000L, nowMillis = 5_000_000L)

        assertSame(fresh, picked)
        assertFalse(old.isLoaded)
        assertNull(old.adObject)
    }

    @Test
    fun `khong truyen tuoi thi hanh vi cu giu nguyen`() {
        ecmps.listFromData("ca-app-pub-1/100;ca-app-pub-1/200")
        val old = ecmps.list[0].apply { isLoaded = true; adObject = Any(); loadedAtMillis = 1_000L }
        assertSame(old, ecmps.getAdLoaded())
    }
```
Chạy → đỏ (`task11-red.log`, không có tham số `maxAgeMillis`).

- [ ] **Step 2: `AdsEcmps.getAdLoaded`**

```kotlin
    /**
     * @param maxAgeMillis tuổi tối đa của quảng cáo đã tải. AdMob: app open hết hạn sau 4 giờ [P];
     *   quảng cáo quá tuổi bị bỏ (đánh dấu chưa load, huỷ object) để vòng load kế tiếp tải mới.
     */
    fun getAdLoaded(maxAgeMillis: Long = Long.MAX_VALUE, nowMillis: Long = System.currentTimeMillis()): AdsEcmp? {
        if (list.isEmpty()) {
            LogEventServer.logAdShowFailWithReason(
                placement = adPl.getLoadPlacement(),
                reason = "listAdsEcmp empty"
            )
        }
        for (item in list) {
            val plm = item.adEcmps.adPl.getLoadPlacement()
            if (item.isLoaded && item.adObject == null) {
                LogEventServer.logAdShowFailWithReason(placement = plm, reason = Constants.AdShowFailReason.LOADED_BUT_ADS_NULL)
            }
            if (!item.isLoaded && item.adObject != null) {
                LogEventServer.logAdShowFailWithReason(placement = plm, reason = Constants.AdShowFailReason.LOAD_FAIL_BUT_ADS_NOT_NULL)
            }
            if (item.isLoaded && item.adObject != null) {
                val age = nowMillis - item.loadedAtMillis
                if (item.loadedAtMillis > 0 && age > maxAgeMillis) {
                    LogEventServer.logAdShowFailWithReason(placement = plm, reason = Constants.AdShowFailReason.AD_EXPIRED)
                    val stale = item.adObject
                    item.isLoaded = false
                    item.adObject = null
                    try { adPl.destroyAd(stale) } catch (e: Exception) { }
                    continue
                }
                return item
            }
        }
        return null
    }
```
`Constants.AdShowFailReason` thêm `const val AD_EXPIRED = "ad_expired"`.

- [ ] **Step 3: `AdsMobPlOpen.show` dòng 142:**
```kotlin
        val ecmpShow = adsPlacementEcmp.adEcmps?.getAdLoaded(
            maxAgeMillis = AdsHelper.instance.adsPolicy.appOpenMaxAgeMinutes * 60_000L
        )
```
Làm y hệt cho lớp app open của MAX nếu có (`grep -rn "AppOpenAd\|MaxAppOpenAd" src/main/java/com/app/plugin/ads/adsmax`).

- [ ] **Step 4: Test xanh** — `./gradlew :testDebugUnitTest --tests 'com.app.plugin.ads.AdsEcmpsTest' > DOC/testing/task11-green.log 2>&1; echo exit=$?` → `exit=0`. Nếu `adPl` mock không có `destroyAd` stub thì Mockito trả false, không sao.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/app/plugin/ads/AdsEcmps.kt src/main/java/com/app/plugin/ads/adsmob/AdsMobPlOpen.kt src/main/java/com/app/plugin/until/Constants.kt src/test/java/com/app/plugin/ads/AdsEcmpsTest.kt
git commit -m "fix(ads): app open quá 4 giờ (ads_policy app_open_max_age_minutes) không hiện, tải lại

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 12: Remove-ads theo mask từng sản phẩm (Điều 13.4)

**Files:**
- Modify: `src/main/java/com/app/plugin/iap/Entitlements.kt` (thêm `setRemoveAdsMasks`, `removeAdsMask()`, `recompute` dùng mask)
- Modify: `src/main/java/com/app/plugin/MonetizationSdk.kt` (`Config.removeAdsMasks`, bước 1 áp mask, cảnh báo id lạ)
- Test: thêm vào `src/test/java/com/app/plugin/iap/EntitlementsTest.kt`

**Interfaces:**
- Produces: `Entitlements.setRemoveAdsMasks(masks: Map<String, Int>)`; `Entitlements.removeAdsMask(): Int` (OR của mask các sản phẩm đang active; sản phẩm trong `removeAdsProducts` không có mask riêng = `RemoveAdsMask.ALL`); `MonetizationSdk.Config.removeAdsMasks: Map<String, Int>`.

- [ ] **Step 1: Test**

```kotlin
    @Test
    fun `goi re chi go dinh dang gian doan, rewarded van con`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.setRemoveAdsMasks(mapOf("no_ads_lite" to com.app.plugin.ads.RemoveAdsMask.INTERRUPTIVE))
        Entitlements.grant("no_ads_lite")

        val mask = Entitlements.removeAdsMask()
        assertEquals(com.app.plugin.ads.RemoveAdsMask.INTERRUPTIVE, mask)
        assertFalse(com.app.plugin.ads.RemoveAdsMask.isRemoved(mask, com.app.plugin.ads.RemoveAdsMask.FLAG_REWARDED))
        assertTrue(Entitlements.hasRemoveAds())
    }

    @Test
    fun `goi Pro khong khai mask thi go het`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.grant("premium_monthly")
        assertEquals(com.app.plugin.ads.RemoveAdsMask.ALL, Entitlements.removeAdsMask())
    }

    @Test
    fun `khong so huu gi thi mask rong`() {
        Entitlements.setRemoveAdsMasks(mapOf("no_ads_lite" to com.app.plugin.ads.RemoveAdsMask.INTERRUPTIVE))
        assertEquals(com.app.plugin.ads.RemoveAdsMask.NONE, Entitlements.removeAdsMask())
        assertFalse(Entitlements.hasRemoveAds())
    }
```
Chạy → đỏ (`task12-red.log`).

- [ ] **Step 2: `Entitlements`**

Thêm field và hàm:
```kotlin
    private const val KEY_REMOVE_ADS_MASKS = "entitlements_remove_ads_masks"

    @Volatile
    private var removeAdsMasks: Map<String, Int> = loadMasks()

    /** Mask theo sản phẩm (Điều 13.4): gói rẻ gỡ INTERRUPTIVE, gói Pro gỡ ALL. Sản phẩm trong
     *  `removeAdsProducts` mà không có ở đây mặc định ALL — hành vi cũ giữ nguyên. */
    @Synchronized
    fun setRemoveAdsMasks(masks: Map<String, Int>) {
        removeAdsMasks = masks.mapValues { RemoveAdsMask.sanitize(it.value) }
        PreferenceHelper.getInstance().setValue(
            KEY_REMOVE_ADS_MASKS, removeAdsMasks.entries.joinToString(SEP) { "${it.key}:${it.value}" })
        recompute()
    }

    fun removeAdsMask(): Int = active.fold(RemoveAdsMask.NONE) { acc, id ->
        acc or (removeAdsMasks[id] ?: if (id in removeAdsProducts) RemoveAdsMask.ALL else RemoveAdsMask.NONE)
    }

    private fun loadMasks(): Map<String, Int> =
        PreferenceHelper.getInstance().get(KEY_REMOVE_ADS_MASKS, "").split(SEP)
            .mapNotNull { e -> e.split(':').takeIf { it.size == 2 }?.let { (k, v) -> v.toIntOrNull()?.let { k to it } } }
            .toMap()
```
`hasRemoveAds()` thành `fun hasRemoveAds(): Boolean = removeAdsMask() != RemoveAdsMask.NONE`. `recompute()` thành `AdsHelper.instance.setRemoveAdsMask(removeAdsMask())`. `clearAll()` thêm `removeAdsMasks = emptyMap(); PreferenceHelper.getInstance().remove(KEY_REMOVE_ADS_MASKS)`. Import `com.app.plugin.ads.RemoveAdsMask`.

- [ ] **Step 3: `MonetizationSdk.Config`** thêm
```kotlin
        /** Mask gỡ quảng cáo theo sản phẩm (RemoveAdsMask.*). Sản phẩm trong removeAdsProducts
         *  không có ở đây gỡ ALL. */
        var removeAdsMasks: Map<String, Int> = emptyMap()
```
Bước 1 của `apply`: sau `setRemoveAdsProducts` thêm `if (config.removeAdsMasks.isNotEmpty()) { Entitlements.setRemoveAdsMasks(config.removeAdsMasks); order += "entitlements.removeAdsMasks" }`. `checkConfig`: `for (id in c.removeAdsMasks.keys - declared) w += "removeAdsMasks chứa '$id' không có trong danh sách sản phẩm — kiểm tra lại id."`.

- [ ] **Step 4: Test xanh** — `./gradlew :testDebugUnitTest --tests 'com.app.plugin.iap.EntitlementsTest' --tests 'com.app.plugin.MonetizationSdkTest' > DOC/testing/task12-green.log 2>&1; echo exit=$?` → `exit=0`.

- [ ] **Step 5: CHANGELOG "Thêm": `removeAdsMasks`. Commit**

```bash
git add src/main/java/com/app/plugin/iap/Entitlements.kt src/main/java/com/app/plugin/MonetizationSdk.kt src/test/java/com/app/plugin/iap/EntitlementsTest.kt CHANGELOG.md
git commit -m "feat(iap): remove-ads theo mask từng sản phẩm; gói rẻ giữ rewarded và native inline

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 13: `ad_impression` không đếm đôi khi AdMob đã nối Firebase

**Files:**
- Create: `src/main/java/com/app/plugin/ads/AdRevenueRouting.kt`
- Modify: `src/main/java/com/app/plugin/firbase/FirHelper.kt:81-104` (bọc khối `logEvent(AD_IMPRESSION)` của AdMob)
- Modify: `src/main/java/com/app/plugin/app/AppPluginManager.kt` (đọc resource `ads_admob_firebase_linked`)
- Modify: `src/main/res/raw/appplugin_keep.xml` (thêm `@integer/ads_admob_firebase_linked`)
- Test: `src/test/java/com/app/plugin/ads/AdRevenueRoutingTest.kt`

**Interfaces:**
- Produces: `internal object AdRevenueRouting { @Volatile var admobLinkedToFirebase: Boolean; fun shouldLogFirebaseAdImpression(platform: String): Boolean }`. App khai `<integer name="ads_admob_firebase_linked">1</integer>` khi đã liên kết AdMob với Firebase trên console.

- [ ] **Step 1: Test**

```kotlin
package com.app.plugin.ads

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Doc 62 mục 2.3: AdMob đã liên kết Firebase thì Firebase tự ghi ad_impression; SDK ghi thêm là đếm đôi. */
class AdRevenueRoutingTest {
    @After fun tearDown() { AdRevenueRouting.admobLinkedToFirebase = false }

    @Test fun `mac dinh SDK van ghi ad_impression cho admob`() =
        assertTrue(AdRevenueRouting.shouldLogFirebaseAdImpression("admob"))

    @Test fun `da lien ket thi khong ghi cho admob, van ghi cho mang khac`() {
        AdRevenueRouting.admobLinkedToFirebase = true
        assertFalse(AdRevenueRouting.shouldLogFirebaseAdImpression("admob"))
        assertTrue(AdRevenueRouting.shouldLogFirebaseAdImpression("applovin"))
    }
}
```
Chạy → đỏ (`task13-red.log`).

- [ ] **Step 2: `AdRevenueRouting.kt`**

```kotlin
package com.app.plugin.ads

internal object AdRevenueRouting {
    /** App đặt integer resource `ads_admob_firebase_linked` = 1 khi AdMob đã liên kết Firebase. */
    @Volatile
    var admobLinkedToFirebase: Boolean = false

    fun shouldLogFirebaseAdImpression(platform: String): Boolean =
        !(platform == "admob" && admobLinkedToFirebase)
}
```

- [ ] **Step 3: `FirHelper.logAdRevAdmob`** — bọc `try { … firebaseAnalytics.logEvent(AD_IMPRESSION) … }` bằng `if (AdRevenueRouting.shouldLogFirebaseAdImpression("admob")) { … }`; các dòng AppsFlyer, `AdsHelper.onAdImpression`, `LogEventServer.logAdRevenue` giữ nguyên (không phụ thuộc liên kết).

- [ ] **Step 4: `AppPluginManager.initPlugin`**, sau `mContext = context`: `AdRevenueRouting.admobLinkedToFirebase = AppUtil.getResourceInt(context, "ads_admob_firebase_linked") == 1`. Chạy `KeepXmlTest` → đỏ vì tên mới → thêm `@integer/ads_admob_firebase_linked` vào `tools:keep` → xanh. Đây là đúng cơ chế mong muốn của Task 2.

- [ ] **Step 5: Test xanh** — `./gradlew :testDebugUnitTest --tests 'com.app.plugin.ads.AdRevenueRoutingTest' --tests 'com.app.plugin.until.KeepXmlTest' > DOC/testing/task13-green.log 2>&1; echo exit=$?` → `exit=0`.

- [ ] **Step 6: CHANGELOG "Thêm": resource `ads_admob_firebase_linked`. Commit**

```bash
git add src/main/java/com/app/plugin/ads/AdRevenueRouting.kt src/main/java/com/app/plugin/firbase/FirHelper.kt src/main/java/com/app/plugin/app/AppPluginManager.kt src/main/res/raw/appplugin_keep.xml src/test/java/com/app/plugin/ads/AdRevenueRoutingTest.kt CHANGELOG.md
git commit -m "fix(analytics): không ghi ad_impression lên Firebase cho AdMob khi app đã liên kết AdMob ↔ Firebase

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 14: Hợp đồng tên event và hàng đợi event có trần, có backoff

**Files:**
- Create: `src/main/java/com/app/plugin/logevent/LogEventQueue.kt`
- Modify: `src/main/java/com/app/plugin/logevent/LogEventServer.kt:83-145` (`logEventToServer` đi qua hàng đợi)
- Modify: `src/main/java/com/app/plugin/ads/AdsPlBase.kt:55` (tên event động thành snake_case)
- Modify: hai tên event camelCase (`grep -rn 'reloadSplash_acnull\|setAc_waitreloadsplash' src/main/java`)
- Test: `src/test/java/com/app/plugin/logevent/EventNameContractTest.kt`, `src/test/java/com/app/plugin/logevent/LogEventQueueTest.kt`

**Interfaces:**
- Produces: `internal class LogEventQueue(maxPending = 500, maxInFlight = 4, baseBackoffMs = 1_000, maxBackoffMs = 60_000) { data class Item(name: String, body: String, var attempts: Int = 0); fun offer(item): Boolean; fun next(): Item?; fun onSuccess(); fun onFailure(item): Long /* ms chờ */; val pendingCount: Int; val inFlight: Int; val dropped: Long; fun backoffFor(attempt: Int): Long }`

- [ ] **Step 1: Test hợp đồng tên event (Điều 9.2)**

```kotlin
package com.app.plugin.logevent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Điều 9.2: tên event ≤ 40 ký tự, snake_case, không tiền tố firebase_/google_/ga_. Firebase vứt event
 * sai tên mà không báo (DUC-RUT §2.1). Quét mọi literal đi vào logEvent/logEventToServer và mọi
 * hằng trong Constants.LogEvent.
 */
class EventNameContractTest {
    private val valid = Regex("^[a-z][a-z0-9_]{0,39}$")
    private val banned = listOf("firebase_", "google_", "ga_")

    private fun names(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val call = Regex("(?:logEvent|logEventToServer)\\(\\s*\"([^\"$]+)\"")
        val const = Regex("const val [A-Z_0-9]+ = \"([^\"]+)\"")
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            call.findAll(text).forEach { out += f.name to it.groupValues[1] }
            if (f.name == "Constants.kt") {
                val block = text.substringAfter("object LogEvent {", "").substringBefore("\n    }")
                const.findAll(block).forEach { out += f.name to it.groupValues[1] }
            }
        }
        return out
    }

    @Test
    fun `moi ten event hop le`() {
        val bad = names().filter { (_, n) -> !valid.matches(n) || banned.any { n.startsWith(it) } }
        assertTrue("Tên event sai hợp đồng:\n" + bad.joinToString("\n") { "${it.first}: ${it.second}" }, bad.isEmpty())
    }

    @Test
    fun `khong con ten event ghep tu ten lop`() {
        val text = File("src/main/java/com/app/plugin/ads/AdsPlBase.kt").readText()
        assertTrue(!text.contains("this::class.simpleName"))
    }
}
```
Chạy → đỏ (`task14-red.log`): `reloadSplash_acnull`, `setAc_waitreloadsplash`, và test thứ hai.

- [ ] **Step 2: Đổi tên** `reloadSplash_acnull` → `reload_splash_ac_null`, `setAc_waitreloadsplash` → `set_ac_wait_reload_splash`. `AdsPlBase.kt:55`:
```kotlin
                FirHelper.instance.logEvent("ads_pl_${this::class.simpleName.orEmpty().lowercase()}_$adsType")
```
(kết quả ví dụ `ads_pl_adsmobplinters_0`, ≤ 40 ký tự — kiểm bằng `grep -rn "class Ads.*Pl" src/main/java | awk '{print length($2)}' | sort -n | tail -1` < 28). Vì test quét literal, dòng này dùng template nên không bị quét; test thứ hai đảm bảo dạng mới. Chạy test 1 → xanh.

- [ ] **Step 3: Test hàng đợi**

```kotlin
package com.app.plugin.logevent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Cổng G6: trần 500, tối đa 4 request đang bay, backoff 1→60 s, bỏ bản ghi cũ nhất khi đầy. */
class LogEventQueueTest {
    private fun item(i: Int) = LogEventQueue.Item("ev", "body$i")

    @Test fun `day thi bo ban ghi cu nhat`() {
        val q = LogEventQueue(maxPending = 3)
        (1..4).forEach { q.offer(item(it)) }
        assertEquals(3, q.pendingCount)
        assertEquals(1L, q.dropped)
        assertEquals("body2", q.next()?.body)
    }

    @Test fun `khong qua maxInFlight request dang bay`() {
        val q = LogEventQueue(maxInFlight = 2)
        (1..3).forEach { q.offer(item(it)) }
        assertNotNull(q.next()); assertNotNull(q.next())
        assertNull(q.next())
        q.onSuccess()
        assertNotNull(q.next())
    }

    @Test fun `that bai thi tra lai dau hang va backoff tang dan toi tran`() {
        val q = LogEventQueue()
        assertEquals(1_000L, q.backoffFor(1))
        assertEquals(2_000L, q.backoffFor(2))
        assertEquals(32_000L, q.backoffFor(6))
        assertEquals(60_000L, q.backoffFor(7))
        assertEquals(60_000L, q.backoffFor(20))
        q.offer(item(1)); q.offer(item(2))
        val first = q.next()!!
        q.onFailure(first)
        assertEquals(0, q.inFlight)
        assertEquals("body1", q.next()?.body)
    }

    @Test fun `qua 5 lan that bai thi bo`() {
        val q = LogEventQueue()
        q.offer(item(1))
        repeat(6) { val it2 = q.next()!!; q.onFailure(it2) }
        assertEquals(0, q.pendingCount)
    }
}
```
Chạy → đỏ.

- [ ] **Step 4: `LogEventQueue.kt`**

```kotlin
package com.app.plugin.logevent

import kotlin.math.min

internal class LogEventQueue(
    private val maxPending: Int = 500,
    private val maxInFlight: Int = 4,
    private val baseBackoffMs: Long = 1_000L,
    private val maxBackoffMs: Long = 60_000L,
    private val maxAttempts: Int = 5
) {
    data class Item(val name: String, val body: String, var attempts: Int = 0)

    private val pending = ArrayDeque<Item>()
    var inFlight: Int = 0
        private set
    var dropped: Long = 0
        private set
    val pendingCount: Int get() = synchronized(this) { pending.size }

    @Synchronized
    fun offer(item: Item): Boolean {
        if (pending.size >= maxPending) { pending.removeFirst(); dropped++ }
        pending.addLast(item)
        return true
    }

    @Synchronized
    fun next(): Item? {
        if (inFlight >= maxInFlight) return null
        val item = pending.removeFirstOrNull() ?: return null
        inFlight++
        return item
    }

    @Synchronized
    fun onSuccess() { if (inFlight > 0) inFlight-- }

    /** @return số ms nên chờ trước khi gọi [next] lại. */
    @Synchronized
    fun onFailure(item: Item): Long {
        if (inFlight > 0) inFlight--
        item.attempts++
        if (item.attempts <= maxAttempts) pending.addFirst(item) else dropped++
        return backoffFor(item.attempts)
    }

    fun backoffFor(attempt: Int): Long =
        min(maxBackoffMs, baseBackoffMs shl (attempt - 1).coerceIn(0, 16))
}
```
Chạy → xanh (`task14-green.log`).

- [ ] **Step 5: Nối vào `LogEventServer.logEventToServer`**: thay phần `val call = logService…call.enqueue(…)` bằng `queue.offer(LogEventQueue.Item(eventName, requestBody.toJson())); pump()`, với:
```kotlin
    private val queue = LogEventQueue()
    @Volatile private var pumpScheduledUntil = 0L

    private fun pump() {
        while (true) {
            val item = queue.next() ?: return
            val call = logService.logActionsToServer(item.body.toRequestBody("application/json".toMediaType()))
            call.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) { queue.onSuccess(); pump() } else retry(item)
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) = retry(item)
            })
        }
    }

    private fun retry(item: LogEventQueue.Item) {
        val delayMs = queue.onFailure(item)
        val until = System.currentTimeMillis() + delayMs
        if (until <= pumpScheduledUntil) return
        pumpScheduledUntil = until
        requestScope.launch { kotlinx.coroutines.delay(delayMs); pump() }
    }
```
`createRequestBody` hiện trả `RequestBody`; đổi để trả chuỗi JSON (giữ hàm cũ làm wrapper nếu test dùng). `onResult` của decision log: gọi `onResult?.invoke(true)` trong `onResponse` thành công và `invoke(false)` khi item bị bỏ (attempts > 5) — thêm trường `onResult: ((Boolean) -> Unit)?` vào `Item`.

- [ ] **Step 6: Toàn bộ test logevent + brain (decision log dùng đường này)** — `./gradlew :testDebugUnitTest --tests 'com.app.plugin.logevent.*' --tests 'com.app.plugin.brain.DecisionLog*' > DOC/testing/task14-green.log 2>&1; echo exit=$?` → `exit=0`.

- [ ] **Step 7: CHANGELOG "Đổi": 2 tên event; "Thêm": hàng đợi. Commit**

```bash
git add src/main/java/com/app/plugin/logevent/LogEventQueue.kt src/main/java/com/app/plugin/logevent/LogEventServer.kt src/main/java/com/app/plugin/ads/AdsPlBase.kt src/test/java/com/app/plugin/logevent/EventNameContractTest.kt src/test/java/com/app/plugin/logevent/LogEventQueueTest.kt CHANGELOG.md
git add -u src/main/java   # hai file chứa tên event đổi
git commit -m "feat(logevent): hàng đợi event có trần 500, 4 request đang bay, backoff; test hợp đồng tên event (Điều 9.2)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 15: Ngân sách main thread lúc mở app (StrictMode frames `com.app.plugin` = 0)

**Files:**
- Modify: `src/main/java/com/app/plugin/until/PreferenceHelper.kt` (thêm `setValues`)
- Modify: `src/main/java/com/app/plugin/app/AppPluginBase.kt:36-52` (một lần edit; đo thời gian)
- Modify: `src/main/java/com/app/plugin/brain/DecisionLogger.kt:40-47` (`attachQueue` không đọc file trên main)
- Test: `src/test/java/com/app/plugin/brain/DecisionLoggerAttachTest.kt`

**Interfaces:**
- Produces: `PreferenceHelper.setValues(values: Map<String, Any?>)`; `DecisionLogger.queuedCount()` trả giá trị cache (`-1` khi chưa đếm xong); log `AppPlugin: AppPluginBase.onCreate main-thread ms=<n>`.

- [ ] **Step 1: Test — `attachQueue` không chạm file trên luồng gọi**

```kotlin
package com.app.plugin.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Đo 2026-09-24: DecisionLogger đọc file ~129 ms trên main thread lúc mở app. Đếm phải chạy nền. */
class DecisionLoggerAttachTest {
    @Test
    fun `attachQueue tra ve ngay va queuedCount co gia tri sau khi dem nen xong`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "dlq-" + System.nanoTime()).apply { mkdirs() }
        File(dir, "brain_decision_queue.log").writeText("a\nb\nc\n")   // DecisionLogQueue.FILE_NAME
        val latch = CountDownLatch(1)

        val t0 = System.nanoTime()
        DecisionLogger.attachQueue(dir, onCounted = { latch.countDown() })
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000

        assertTrue("attachQueue mất ${elapsedMs} ms", elapsedMs < 50)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(3, DecisionLogger.queuedCount())
    }
}
```
Chạy → đỏ (không có tham số `onCounted`).

- [ ] **Step 2: `DecisionLogger`**

```kotlin
    @Volatile
    private var cachedQueued: Int = -1

    private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "appplugin-decision-log").apply { isDaemon = true }
    }

    fun attachQueue(dir: java.io.File, maxRecords: Int = 5000, onCounted: (() -> Unit)? = null) {
        val q = DecisionLogQueue(dir, maxRecords)
        queue = q
        cachedQueued = -1
        io.execute {
            try { cachedQueued = q.size() } catch (e: Exception) { cachedQueued = 0 }
            onCounted?.invoke()
            flushQueue()
        }
    }

    /** -1 khi chưa đếm xong (vừa attach). */
    fun queuedCount(): Int = cachedQueued
```
Mọi chỗ trong `DecisionLogger` gọi `q.enqueue(...)`/`q.removeAll(...)` cập nhật `cachedQueued` (`+1` sau enqueue thành công, `= q.size()` sau removeAll trong luồng io). `flushQueue()` đã chạy trên io ở trên; các lời gọi khác của `flushQueue()` bọc `io.execute { … }`.

- [ ] **Step 3: `PreferenceHelper.setValues`**

```kotlin
    /** Một lần edit cho nhiều khoá: một lần ghi đĩa thay vì N. */
    fun setValues(values: Map<String, Any?>) {
        try {
            preferences?.edit { editor ->
                for ((key, value) in values) when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                    else -> Log.e("PreferenceHelper", "Not yet implemented: $key")
                }
            }
        } catch (e: Exception) {
            Log.e("PreferenceHelper", "Error: $e")
        }
    }
```

- [ ] **Step 4: `AppPluginBase.onCreate`** — thay 9 dòng `setValue` bằng một `setValues`, đo thời gian:

```kotlin
    override fun onCreate() {
        super<MultiDexApplication>.onCreate()
        if (!isMainProcess()) return
        val t0 = android.os.SystemClock.elapsedRealtime()
        PreferenceHelper.getInstance().init(this)
        ConsentState.installStore(/* như Task 8 */)
        ConsentState.restore()
        val timeOpen = PreferenceHelper.getInstance().get(Constants.Common.TIME_OPEN, 0) + 1
        PreferenceHelper.getInstance().setValues(
            mapOf(
                Constants.Common.TIME_OPEN to timeOpen,
                Constants.Common.IS_FETCH_FIREBASE to false,
                Constants.EXPERIMENT_GROUP to 4,
                Constants.InitAds.NT_INTRO_LOADED to false,
                Constants.InitAds.NT_LANGUAGE_2_LOADED to false,
                Constants.InitAds.NT_LANGUAGE_LOADED to false,
                Constants.InitAds.FULL_SPLASH_LOADED to false,
                Constants.InitAds.FULL_2_SPLASH_LOADED to false,
                Constants.InitAds.RECT_SPLASH_LOADED to false,
                Constants.InitAds.FULL_SPLASH_SHOWED to false,
                Constants.InitAds.IS_LOAD_FULL to false
            )
        )
        // … phần processName/initPlugin giữ nguyên …
        Log.d("AppPlugin", "AppPluginBase.onCreate main-thread ms=${android.os.SystemClock.elapsedRealtime() - t0}")
    }
```

- [ ] **Step 5: Test xanh** — `./gradlew :testDebugUnitTest --tests 'com.app.plugin.brain.*' > DOC/testing/task15-green.log 2>&1; echo exit=$?` → `exit=0`. Nếu test brain sẵn có gọi `queuedCount()` ngay sau `attachQueue` và kỳ vọng số, chuyển chúng sang `onCounted` latch như Step 1.

- [ ] **Step 6: Đo trên emulator (G2)** với demo release (StrictMode bật): `tools/device_gate.sh` → dòng `strictmode frames com.app.plugin=` phải là `0`; `adb logcat -d | grep "onCreate main-thread ms="` ghi số vào commit message. So với trước (đo cùng emulator, cùng lệnh): cần giảm ≥ 80%.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/app/plugin/until/PreferenceHelper.kt src/main/java/com/app/plugin/app/AppPluginBase.kt src/main/java/com/app/plugin/brain/DecisionLogger.kt src/test/java/com/app/plugin/brain/DecisionLoggerAttachTest.kt
git commit -m "perf(startup): một lần ghi prefs lúc mở app; decision log đếm và flush trên luồng nền (StrictMode frames SDK 12 → 0)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 16: Kill switch theo module, phiên bản SDK trên Crashlytics, event `sdk_health`

**Files:**
- Create: `src/main/java/com/app/plugin/SdkKillSwitch.kt`
- Modify: `src/main/java/com/app/plugin/firbase/FirHelperConfig.kt` (khoá `sdk_kill_android`)
- Modify: `src/main/java/com/app/plugin/ads/AdsHelper.kt` (`isDisableAds` kiểm kill), `src/main/java/com/app/plugin/iap/IapHelper.kt` (`init` kiểm kill), `src/main/java/com/app/plugin/logevent/LogEventServer.kt` (`logEventToServer` kiểm kill)
- Modify: `src/main/java/com/app/plugin/app/AppPluginManager.kt` (custom key Crashlytics), `src/main/java/com/app/plugin/ads/AdSdkStartup.kt` (`sdk_health` sau khi mạng khởi tạo)
- Test: `src/test/java/com/app/plugin/SdkKillSwitchTest.kt`

**Interfaces:**
- Produces: `internal data class SdkKillSwitch(ads: Boolean = false, iap: Boolean = false, logevent: Boolean = false, belowVersion: String = "") { fun killsAds(v: String): Boolean; fun killsIap(v: String): Boolean; fun killsLogevent(v: String): Boolean; companion { val NONE; fun parse(json: String?): SdkKillSwitch; fun isBelow(version: String, floor: String): Boolean; @Volatile var current: SdkKillSwitch } }`

- [ ] **Step 1: Test**

```kotlin
package com.app.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Doc 60 G: kill switch cho từng module và cho một bản SDK hỏng, qua Remote Config `sdk_kill_android`. */
class SdkKillSwitchTest {
    @Test fun `khong co config thi khong tat gi`() {
        val k = SdkKillSwitch.parse(null)
        assertFalse(k.killsAds("1.4.0")); assertFalse(k.killsIap("1.4.0")); assertFalse(k.killsLogevent("1.4.0"))
    }

    @Test fun `tat theo module`() {
        val k = SdkKillSwitch.parse("""{"ads":true}""")
        assertTrue(k.killsAds("1.4.0")); assertFalse(k.killsIap("1.4.0"))
    }

    @Test fun `tat moi thu cho ban SDK cu hon nguong`() {
        val k = SdkKillSwitch.parse("""{"below_version":"1.4.1"}""")
        assertTrue(k.killsAds("1.4.0")); assertTrue(k.killsIap("1.3.9")); assertFalse(k.killsAds("1.4.1")); assertFalse(k.killsAds("2.0.0"))
    }

    @Test fun `so sanh semver`() {
        assertTrue(SdkKillSwitch.isBelow("1.3.10", "1.4.0"))
        assertFalse(SdkKillSwitch.isBelow("1.10.0", "1.4.0"))
        assertFalse(SdkKillSwitch.isBelow("x", "1.4.0"))
    }

    @Test fun `json hong thi khong tat gi`() = assertEquals(SdkKillSwitch.NONE, SdkKillSwitch.parse("{"))
}
```
Chạy → đỏ (`task16-red.log`).

- [ ] **Step 2: `SdkKillSwitch.kt`**

```kotlin
package com.app.plugin

import com.app.plugin.until.LogUtil
import com.google.gson.JsonParser

internal data class SdkKillSwitch(
    val ads: Boolean = false,
    val iap: Boolean = false,
    val logevent: Boolean = false,
    val belowVersion: String = ""
) {
    private fun versionKilled(v: String) = belowVersion.isNotBlank() && isBelow(v, belowVersion)
    fun killsAds(v: String) = ads || versionKilled(v)
    fun killsIap(v: String) = iap || versionKilled(v)
    fun killsLogevent(v: String) = logevent || versionKilled(v)

    companion object {
        val NONE = SdkKillSwitch()

        @Volatile
        var current: SdkKillSwitch = NONE

        fun parse(json: String?): SdkKillSwitch {
            if (json.isNullOrBlank()) return NONE
            return try {
                val o = JsonParser.parseString(json).asJsonObject
                SdkKillSwitch(
                    ads = o.get("ads")?.asBoolean ?: false,
                    iap = o.get("iap")?.asBoolean ?: false,
                    logevent = o.get("logevent")?.asBoolean ?: false,
                    belowVersion = o.get("below_version")?.asString ?: ""
                )
            } catch (e: Exception) {
                LogUtil.logE("sdk_kill_android hỏng, bỏ qua: ${e.message}")
                NONE
            }
        }

        fun isBelow(version: String, floor: String): Boolean {
            fun parts(s: String) = s.split('.').map { it.toIntOrNull() ?: return null }
            val a = parts(version) ?: return false
            val b = parts(floor) ?: return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x < y
            }
            return false
        }
    }
}
```
Chạy → xanh (`task16-green.log`).

- [ ] **Step 3: Nối**
- `FirHelperConfig`: snapshot thêm `sdkKill = remoteConfig.getString("sdk_kill_android")`; `applyRemoteCf`: `SdkKillSwitch.current = SdkKillSwitch.parse(snapshot.sdkKill)`; log khi khác NONE.
- `AdsHelper.isDisableAds(flag)`: dòng đầu `if (SdkKillSwitch.current.killsAds(MonetizationSdk.VERSION)) return true`.
- `IapHelper.init(context)`: dòng đầu `if (SdkKillSwitch.current.killsIap(MonetizationSdk.VERSION)) { LogUtil.logE("IapHelper: kill switch bật, không kết nối billing"); return }`.
- `LogEventServer.logEventToServer`: sau kiểm `logService` khởi tạo: `if (SdkKillSwitch.current.killsLogevent(BuildConfig.SDK_VERSION)) { onResult?.invoke(false); return }`.
- `AppPluginManager.initPlugin`, ngay sau `FirebaseApp.initializeApp(context)`:
```kotlin
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                .setCustomKey("appplugin_version", com.app.plugin.MonetizationSdk.VERSION)
```
(trong cùng `try`).
- `AdSdkStartup.startAdNetworks`, cuối hàm:
```kotlin
        FirHelper.instance.logEvent("sdk_health", android.os.Bundle().apply {
            putString("sdk_version", com.app.plugin.MonetizationSdk.VERSION)
            putString("consent_tier", ConsentState.tier().name)
            putString("networks", if (AdsHelper.isMaxSdkAvailable) "admob,meta,max" else "admob,meta")
            putLong("since_request_ms", System.currentTimeMillis() - adNetworksStartedAtMs)
        })
```

- [ ] **Step 4: Toàn bộ test** — `./gradlew :testDebugUnitTest > DOC/testing/task16-green.log 2>&1; echo exit=$?` → `exit=0`.

- [ ] **Step 5: Emulator (G7):** đặt `sdk_kill_android` = `{"ads":true}` trên Firebase console của app demo (hoặc tạm thời `adb shell` không được — dùng console), mở app: log `kill switch` và 0 request quảng cáo (`adb logcat -d | grep -c "ads .* load"` không tăng). Gỡ khoá sau khi đo. Crashlytics: gây một crash thử (`Crashlytics.getInstance().crash()`? — dùng nút có sẵn trong demo nếu có; nếu không, `adb shell am crash test.sdk.zd`) và xác nhận key `appplugin_version` trên console. Lưu `DOC/testing/task16-device.log`.

- [ ] **Step 6: CHANGELOG "Thêm". Commit**

```bash
git add src/main/java/com/app/plugin/SdkKillSwitch.kt src/main/java/com/app/plugin/firbase/FirHelperConfig.kt src/main/java/com/app/plugin/ads/AdsHelper.kt src/main/java/com/app/plugin/iap/IapHelper.kt src/main/java/com/app/plugin/logevent/LogEventServer.kt src/main/java/com/app/plugin/app/AppPluginManager.kt src/main/java/com/app/plugin/ads/AdSdkStartup.kt src/test/java/com/app/plugin/SdkKillSwitchTest.kt CHANGELOG.md
git commit -m "feat(ops): kill switch sdk_kill_android cho ads/iap/logevent và theo phiên bản; appplugin_version trên Crashlytics; event sdk_health

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Task 17: ABI dump khoá trong CI; đánh dấu `internal` các lớp không app nào dùng; chốt 1.4.0

**Files:**
- Create: `tools/api_dump.sh`, `api/appplugin.api`
- Modify: `.github/workflows/ci.yml` (bước `tools/api_dump.sh --check` trong job `verify`)
- Modify: các lớp trong `src/main/java/com/app/plugin/ads/` được xác nhận 0 tham chiếu ngoài (danh sách ứng viên bên dưới)
- Modify: `build.gradle.kts` (`sdkVersion = "1.4.0"`), `CHANGELOG.md`, `DOC/65_SDK_RELEASE_GRADE_CRITERIA.md` mục 3 (điền số đo mới)

- [ ] **Step 1: `tools/api_dump.sh`**

```bash
#!/usr/bin/env bash
# Dump API public của AAR (javap) vào api/appplugin.api; --check so với bản đã commit (cổng G8).
# Ghi chú: Kotlin `internal` vẫn là public trong bytecode (tên bị mangle), nên dump gồm cả chúng —
# vẫn bắt được mọi thay đổi ngoài ý muốn.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
./gradlew :assembleRelease -q > /tmp/api-dump-build.log 2>&1
tmp=$(mktemp -d)
unzip -q -o build/outputs/aar/appplugin-release.aar classes.jar -d "$tmp"
classes=$(unzip -Z1 "$tmp/classes.jar" | grep '\.class$' | grep -vE '\$[0-9]+|\$\$' | sed 's/\.class$//; s#/#.#g' | sort)
mkdir -p api
out=api/appplugin.api
"$JAVA_HOME/bin/javap" -public -cp "$tmp/classes.jar" $classes | grep -vE '^\s*$|^Compiled from' > "$out.new"
if [ "${1:-}" = "--check" ]; then
  if diff -u "$out" "$out.new" > /tmp/api-diff.txt; then echo "API unchanged ($(wc -l < "$out") lines)"; rm "$out.new"
  else cat /tmp/api-diff.txt; echo "::error::public API changed; run tools/api_dump.sh and commit api/appplugin.api"; exit 1; fi
else
  mv "$out.new" "$out"; echo "wrote $out ($(wc -l < "$out") lines)"
fi
```
`chmod +x tools/api_dump.sh; tools/api_dump.sh` → tạo `api/appplugin.api`. Đối chứng (Điều 6.7): đổi tạm tên một hàm public trong `RemoveAdsMask.kt` → `tools/api_dump.sh --check` phải `exit 1` với diff; hoàn tác.

- [ ] **Step 2: CI** — trong job `verify`, sau bước Gradle thêm:
```yaml
      - name: API surface unchanged
        run: tools/api_dump.sh --check
```

- [ ] **Step 3: Đánh dấu `internal` có kiểm chứng.** Với từng ứng viên, chỉ đổi khi lệnh in `0`:

```bash
for c in AdsEcmp AdsEcmps AdsPlBase AdsPlBaseBn AdsPlBaseFull AdsPlBaseNative AdsPlNtClBase AdsPlacementEcmpBase \
         BannerLoadGuard Full2DefaultCover SdkReadyQueue AdsOb4Native ObjectForShowAdWithViewParent ObjectForCustomeNativeAd \
         AdsLoadInnerCb LogEventServerHelper NtFullPortraitHelper; do
  n=$(grep -rlw "$c" ~/AndroidStudioProjects/speakerclean ~/AndroidStudioProjects/InvoiceMakerAI ~/AndroidStudioProjects/app-template \
      --include='*.kt' --include='*.java' 2>/dev/null | grep -v '/adsplugin/' | grep -v '/build/' | wc -l | tr -d ' ')
  echo "$c $n"
done
```
(`/adsplugin/` là bản chép SDK trong fork, không tính.) Lớp có `0`: thêm `internal` trước `class`/`object`. Test trong `src/test` cùng module vẫn thấy `internal`. Chạy `./gradlew :testDebugUnitTest :app:assembleRelease` → xanh; `tools/api_dump.sh` cập nhật dump.

- [ ] **Step 4: Chạy lại toàn bộ cổng Phase A+B**: `./gradlew :testDebugUnitTest` ×3, `tools/release_gate.sh`, `tools/device_gate.sh` (emulator API 37 và 26), kịch bản tay Task 7 Step 4, và các kịch bản device của Task 8, 9, 10, 15, 16. Điền lại cột "2026-09-24" của DOC/65 mục 3 thành cột ngày mới với số đo.

- [ ] **Step 5: `sdkVersion = "1.4.0"`, CHANGELOG `## [1.4.0] - <ngày>`, không tag. Commit**

```bash
git add tools/api_dump.sh api/appplugin.api .github/workflows/ci.yml build.gradle.kts CHANGELOG.md DOC/65_SDK_RELEASE_GRADE_CRITERIA.md
git add -u src/main/java
git commit -m "chore(release): 1.4.0 — ABI dump khoá trong CI; internal cho lớp không app nào dùng; điền lại cổng G1–G10

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

# Phase C — 2.0.0 (đề cương; viết plan riêng sau khi chủ dự án chốt doc 60 K và DOC/65 mục 7)

Không có task ở đây vì mỗi mục thay đổi API công khai và cần quyết định của chủ dự án. Cổng để đóng phase:

| Việc | Cổng |
|---|---|
| Tách `core` / `ads` / `ads-gma` / `ads-meta` / `ads-max` / `iap` / `brain` / `config-firebase` (doc 60 B.3) | app demo "tiện ích" chỉ khai `core + ads + ads-gma + ads-meta + iap` chạy được; APK release delta so với app trống ≤ 8 MB [D]; `MobileAds.initialize` trên emulator ≤ 10 s |
| `MonetizationSdk.init(application, Config.Builder)` không bắt kế thừa `AppPluginBase` | demo Java thuần chạy; `AppPluginBase` giữ lại với `@Deprecated` suốt 2.x |
| Tách `AdsHelper` thành `BannerController`, `OpenAdController`, `Full2Chain`, `InterstitialController`, `NativeController` | mọi file ≤ 800 dòng; `tools/api_dump.sh --check` chỉ khác ở lớp mới; 100% test cũ xanh |
| `explicitApi()` (khi AGP built-in Kotlin hỗ trợ; nếu không, giữ dump javap) | 0 cảnh báo explicit API |
| `resourcePrefix = "appplugin_"` cho mọi resource | lint `ResourceName` 0 lỗi |
| Ký config từ xa (Ed25519) cho `purchase_routing_android`, `paywall_config_android`, `brain_policy_android` | bản không ký bị bỏ; test vector |
| Play Integrity cho grant/claim (đưa `PlayIntegrityGuard` vào đường claim của BattlePass, RecurringGrants, Offerwall) | server từ chối token không hợp lệ (backend test) |
| Adapter GMA Next-Gen sau interface `AdNetworkAdapter` | demo chạy được với cả hai engine bằng một cờ |

---

## Self-review (đã chạy khi viết plan)

- **Spec coverage:** G1 → Task 3, 4, 6, 7. G2 → Task 5, 6, 15. G3 → Task 1, 2, 5, 6. G4 → Task 8, 9. G5 → Task 10, 11, 12. G6 → Task 13, 14. G7 → Task 16. G8 → Task 17 (mức 1.4), Phase C. G9 → Task 6, 7, 17. G10 → README chưa có task: thêm khi làm Task 17 nếu còn ngân sách, không chặn 1.4.0.
- **Placeholder:** không còn "TBD"/"tương tự Task N" ngoài Task 4 Step 5 (Meta/MAX) — ở đó đã ghi rõ constructor và dòng số của từng file.
- **Type consistency:** `AdsPolicy.interstitialVerdict(sessionCount, secondsSinceLast)` và `appOpenVerdict(sessionCount)` dùng thống nhất ở Task 10 Step 1/3/4; `AdsEcmps.getAdLoaded(maxAgeMillis, nowMillis)` ở Task 11; `ConsentState.TierStore`/`installStore`/`restore` ở Task 8 và 15; `AdSdkStartup.requestAnalytics` ở Task 9; `SdkKillSwitch.current` ở Task 16.
- **Rủi ro đã nêu:** Task 2 Step 5 (keep.xml của thư viện có được merge không — có bước đo và phương án B); Task 6 Step 5 (KVM trên runner); Task 10 (đổi hành vi mặc định — ghi CHANGELOG và DOC/65 mục 7).
