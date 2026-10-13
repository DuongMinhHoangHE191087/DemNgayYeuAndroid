Project: InLove (DemNgayYeuAndroid) — love-memory tracker, gift suggestions, smart reminders.
Stack today: Android native, Jetpack Compose + Material 3, Kotlin coroutines/StateFlow, Clean Architecture/MVVM,
Room, Retrofit/OkHttp/Moshi, Firebase (Firestore, Firebase AI, App Check), Cloudinary, JUnit/Robolectric/Roborazzi.
minSdk 24, target SDK 36.

Project rules (AGENTS.md):
1. Compose-first: all UI in Jetpack Compose + Material 3. Touch targets >= 48dp.
2. Clean separation: UI never queries Room or network directly; all data access goes through UseCases/Repositories.
3. State: immutable state data classes exposed as StateFlow from ViewModels.
4. Testing/previews: @Preview for Compose components; Roborazzi/Robolectric for screenshot verification.

Owner workflow rules:
- No commit/push/merge/PR unless the owner asks. Codex (you) stays read-only.
- Model routing for plan tasks: `haiku` (effort max) for routine work (read/write/rename files, boilerplate, simple tests, docs);
  `sonnet` (effort high) for hard or critical work (logic, concurrency, security, data, anything marked important).
  Opus never writes code.
- Task rules: one task = one reviewable change; file ownership is exclusive (no two tasks in the same wave own the same file);
  every logic task has a test or command that fails if the logic breaks; speculative scope must be dropped and noted as `skipped: X, add when Y`.
- Platform constraint of this plan: the owner develops on Windows (Claude Code) and has a Mac. Kotlin/Native Apple targets (iosArm64,
  iosSimulatorArm64, iosX64) can only be compiled/linked/tested on macOS, so tasks touching iosMain / Xcode / Swift must say host=mac;
  commonMain/androidMain/JVM tasks can be verified on host=win.
- Invariant: after every task the existing Android app still builds and its existing tests still pass ("Android always green").
- Goal of the whole plan: Kotlin/Compose Multiplatform version of InLove with a full-parity iOS app — all features, smooth beautiful
  animations, proper widgets (WidgetKit), notifications, store-ready (TestFlight/App Store).
