[MAJOR] F-03  
Problem: The first Mac embedding spike invokes a non-executable Gradle wrapper before the task that fixes its permission can run.  
Evidence: `git ls-files -s gradlew` reports mode `100644`. The Run Script executes `./gradlew` at [p1-foundation-build.md:293](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:293), while F-14 fixes the mode only after F-12, which itself depends on F-03 ([plan:540](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:540), [plan:533](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:533)).  
Fix: Move wrapper hygiene into a P0 prerequisite of F-03, or explicitly invoke `bash ./gradlew` until executable permission is established.

[MAJOR] F-06  
Problem: Copying `FloatingHeartsOverlay` into `iosMain` as specified introduces a JVM-only symbol and prevents the real-screen spike from compiling.  
Evidence: F-06 requires the copy at [p1-foundation-build.md:527](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:527), and the inventory calls it pure Compose at [plan:110](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:110). The source uses `System.nanoTime()` at [FloatingHeartsOverlay.kt:76](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/ui/components/FloatingHeartsOverlay.kt:76).  
Fix: Explicitly replace that particle-ID source with a Kotlin counter local to the spike, preserve the animation behavior, and require successful iOS compilation and launch before recording S5.

[MAJOR] F-04  
Problem: The chosen JVM fallback still selects an Android-only test, so it cannot deliver the promised Windows Room verification.  
Evidence: [p1-foundation-build.md:525](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:525) owns only `SpikeDbAndroidTest` under `androidHostTest`, using Robolectric and `ApplicationProvider`; the fallback changes `HOST_TEST_TASK` to the JVM target but retains `--tests 'com.example.spike.SpikeDbAndroidTest.insertAndReadBack'`. No NEW `jvmTest` implementation or JVM database builder is assigned.  
Fix: Add an owned NEW `SpikeDbJvmTest` and JVM builder, select the correct test for each fallback, and distinguish the general host-test task from the database-test task in `gate-tasks.env`.

[MAJOR] F-03  
Problem: F-03 and F-04 can execute in the same wave while both own `shared/build.gradle.kts`.  
Evidence: Both depend only on F-02 at [p1-foundation-build.md:524](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:524) and [plan:525](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:525); independent DAG calculation places both in wave 2. The existing lint passes because [_tools/plan-tools.mjs:103](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/_tools/plan-tools.mjs:103) skips ownership checks when both tasks have `shared-file:`.  
Fix: Add a dependency that serializes these mutations, or remove F-03’s ownership if it requires no build-file change; treat `shared-file:` as coordination metadata, not permission for concurrent editing.

[MAJOR] GENERAL  
Problem: The no-commit Windows-to-Mac fallback omits the new files needed to build the KMP and Xcode projects.  
Evidence: [p1-foundation-build.md:73](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:73) prescribes `git diff --binary main` followed by `git apply`, while F-02–F-09 create NEW `shared/`, `iosApp/`, and script files. This omission is reproducible now: the existing untracked `AppOpeningSplashScreen.kt` produces an empty `git diff --binary main -- <path>`.  
Fix: Define a complete source transfer that includes tracked changes and explicitly selected new files, with a manifest/hash comparison between machines; make the no-commit verification commands work against that snapshot without depending on `HEAD`.

[MAJOR] F-20  
Problem: The task completion checks do not enforce “Android always green” after every task, especially during P0 and Mac work.  
Evidence: F-02 runs Android assembly without the existing test suite; F-03, F-06, F-08 and F-09 omit both Android checks ([p1-foundation-build.md:523](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:523)–[plan:530](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:530)). F-20’s Mac gate runs only shared/iOS checks at [plan:546](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:546), despite the invariant in [_shared-context.md:50](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/_shared-context.md:50).  
Fix: Make `:app:assembleDebug :app:testDebugUnitTest` mandatory completion checks for every task, including P0, on the exact resulting source snapshot; establish the gate before tasks rely on it.

[MAJOR] F-24  
Problem: The shared Android target never explicitly enables the resource support required by the planned Compose Resources packaging.  
Evidence: The target skeleton at [p1-foundation-build.md:206](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:206) lacks `androidResources.enable`; F-24 adds only `compose.resources` and a raw file at [plan:550](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:550). JetBrains documents the Android-library resource opt-in in [resource setup](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html#resources-in-the-androidlibrary-target).  
Fix: Explicitly enable `androidResources` in `kotlin.android`, check the exact `build-smoke.txt` asset rather than any `composeResources/` directory, and add a runtime read assertion.

[MAJOR] F-19  
Problem: The proposed optional bootstrap policy loses the existing protection against SDK linkage errors during Android startup.  
Evidence: [InLoveApplication.kt:60](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/InLoveApplication.kt:60) and [InLoveApplication.kt:86](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/InLoveApplication.kt:86) catch `Throwable` around SDK initialization. F-19 moves these calls into optional steps, but `BootstrapRunner` catches only `Exception` at [p1-foundation-build.md:399](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:399); a `NoClassDefFoundError` therefore escapes.  
Fix: Explicitly preserve the existing SDK failure boundary in the Android adapters, rethrow cancellation, and add `optionalSdkLinkageErrorDoesNotAbortStartup` to the bootstrap tests.

[MAJOR] F-20  
Problem: Comparing schema checksums only before and after a gate run accepts historical schemas that were already changed by the task.  
Evidence: [p1-foundation-build.md:546](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:546) captures schema checksums around the build; it does not compare them with a pinned original baseline. The unstaged `git diff --exit-code` alternative also misses staged or committed changes, while the DoD requires byte-identical schemas at [plan:570](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:570).  
Fix: Pin checksums for versions 12, 13 and 14 before migration, verify them after every task regardless of commit/staging state, and add a negative test that alters a historical schema before invoking the gate.

[MAJOR] GENERAL  
Problem: Several tasks that modify Apple source sets are assigned `host=win`, contrary to the explicit platform constraint.  
Evidence: F-05 creates `iosMain/MainViewController.kt` at [p1-foundation-build.md:526](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:526); F-15 deletes `iosMain`, `iosTest`, and iOS resources at [plan:541](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:541); F-30 creates `iosMain/Subscription.kt` at [plan:554](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:554). All are marked Windows and lack Apple compilation in their acceptance commands.  
Fix: Assign these Apple changes to Mac tasks, splitting Windows build/script work where useful, and include the relevant iOS compilation or link check.

[MINOR] F-23  
Problem: F-23 instructs its executor to fix UI regressions while owning only screenshots and a review document.  
Evidence: The ownership column at [p1-foundation-build.md:549](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:549) lists four PNGs and one Markdown file, but the change says to repair UI code when a screenshot is classified as a regression. UI source ownership belongs to F-22 at [plan:548](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:548).  
Fix: Specify that a regression returns to F-22 for an owned correction and fresh Android verification before F-23 repeats comparison and records approved images.

[MINOR] GENERAL  
Problem: Two repository inventory claims are incorrect.  
Evidence: [p1-foundation-build.md:102](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:102) reports 50 test files in `app/src/test`; a recursive count finds 49 Kotlin test files there, with the additional instrumented test under `app/src/androidTest`. [Plan:110](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:110) reports 230 lines for `FloatingHeartsOverlay.kt`; the current file contains 256 lines.  
Fix: Correct the counts and distinguish local tests from the instrumented test; remove the “pure Compose” characterization because the file contains the JVM call identified above.

## Missing steps

- A pre-spike Mac bootstrap covering wrapper execution, JDK and Android SDK availability, and verification that Windows and Mac received the same complete source snapshot.
- An executable Room JVM fallback with owned NEW JVM test/builder files and concrete target-specific test selection.
- A concrete implementation handoff for S8 `CONDITIONAL-GO`: assign P4 task IDs for production UIKit input wrappers and require MQ-301/PF-037 verification for ordinary, secure and multiline inputs. U-09 currently describes another input spike; normalization must preserve marked text during editing.

VERDICT: FAIL

