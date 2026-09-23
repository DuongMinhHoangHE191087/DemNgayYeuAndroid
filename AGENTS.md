# AGENTS.md - InLove (DemNgayYeuAndroid)

## Project Context
- **Name**: InLove (Ứng dụng theo dõi kỷ niệm tình yêu, gợi ý quà tặng lãng mạn và trung tâm nhắc hẹn thông minh)
- **Tech Stack**:
  - Android Native with **Jetpack Compose** & **Material 3**
  - **Kotlin** with Coroutines & StateFlow/SharedFlow
  - **Architecture**: Clean Architecture / MVVM (Domain, Data, Presentation)
  - **Local Persistence**: Room Database
  - **Networking**: Retrofit, OkHttp, Moshi
  - **Backend / Cloud**: Firebase (Firestore, Firebase AI, App Check), Cloudinary
  - **Testing & Preview**: JUnit, Robolectric, Roborazzi (Screenshot testing for Compose)
  - **Target SDK**: 36 (Android 16), Min SDK: 24 (Android 7.0)

<!-- SKILLS_INDEX_START -->
## Available Skills & Triggers

- **codegraph** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/codegraph/SKILL.md)
  - (triggers: codegraph, code intelligence, ast, symbol, callers, callees, impact analysis)
- **brainstorming** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/brainstorming/SKILL.md)
  - (triggers: brainstorm, brainstorming, explore idea, design spec, feature design)
- **test-driven-development** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/test-driven-development/SKILL.md)
  - (triggers: tdd, test first, red green refactor, failing test)
- **requesting-code-review** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/requesting-code-review/SKILL.md)
  - (triggers: request code review, pre-merge review, pr review)
- **receiving-code-review** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/receiving-code-review/SKILL.md)
  - (triggers: receive review, address review comments, review feedback)
- **systematic-debugging** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/systematic-debugging/SKILL.md)
  - (triggers: systematic debug, root cause analysis, 5 whys, bug investigation)
- **writing-plans** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/writing-plans/SKILL.md)
  - (triggers: write plan, implementation plan, work breakdown)
- **executing-plans** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/executing-plans/SKILL.md)
  - (triggers: execute plan, implementation execution, plan runner)
- **android-clean-architecture** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/android-clean-architecture/SKILL.md)
  - (triggers: android architecture, usecase, repository, room, data layer, domain layer, viewModel, hilt, kmp)
- **compose-multiplatform-patterns** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/compose-multiplatform-patterns/SKILL.md)
  - (triggers: jetpack compose, compose ui, composable, preview, layout, modifier, theme)
- **mobile-design** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/mobile-design/SKILL.md)
  - (triggers: mobile design, touch target, UI, UX, material3, thumb zone, mobile animation, responsiveness)
- **kotlin-coroutines-flows** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/kotlin-coroutines-flows/SKILL.md)
  - (triggers: coroutine, flow, stateflow, sharedflow, suspend, async, lifecycleScope, viewModelScope)
- **kotlin-testing** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/kotlin-testing/SKILL.md)
  - (triggers: unit test, junit, roborazzi, robolectric, compose test, mockk, testing)
- **kotlin-patterns** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/kotlin-patterns/SKILL.md)
  - (triggers: kotlin patterns, idiom, extension function, sealed class, data class)
- **clean-code** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/clean-code/SKILL.md)
  - (triggers: refactor, clean code, code quality, naming, code style)
- **app-builder** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/app-builder/SKILL.md)
  - (triggers: app build, feature implementation, new screen, setup, scaffold)
- **tdd-master-workflow** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/tdd-master-workflow/SKILL.md)
  - (triggers: tdd, test driven, red green refactor)
- **common-session-retrospective** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/common/common-session-retrospective/SKILL.md)
  - (triggers: retrospective, learn, skill gap, session review, post-mortem)
- **common-mobile-ux-core** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/common/common-mobile-ux-core/SKILL.md)
  - (triggers: mobile ux, touch interaction, accessibility, mobile layout)
- **common-code-review** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/common/common-code-review/SKILL.md)
  - (triggers: code review, audit, security review, PR check)
- **impeccable** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/impeccable/SKILL.md)
  - (triggers: impeccable, ui design, polish, critique, audit, layout, typeset, colorize, animate, contrast, beautify, redesign)
- **android-ads-monetization** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/android-ads-monetization/SKILL.md)
  - (triggers: ads, admob, banner, interstitial, app open ads, monetization, gắn quảng cáo)
- **android-billing-subscription** (file:///d:/DMHoang/Project_GitHub/DemNgayYeuAndroid/.agents/skills/android-billing-subscription/SKILL.md)
  - (triggers: billing, in-app purchase, subscription, paywall, vip, gói đăng ký)
<!-- SKILLS_INDEX_END -->

## Development Guidelines
1. **Compose-First**: All UI must be written in Jetpack Compose using Material 3 guidelines. Touch targets must be at least 48dp.
2. **Clean Separation**: UI does not query Room or Network directly. All data access is channeled through UseCases and Repositories.
3. **State Management**: State is modeled via immutable state data classes exposed as `StateFlow` from ViewModels.
4. **Testing & Previews**: Use `@Preview` annotations for Compose components and Roborazzi / Robolectric for visual screenshot verification without emulator overhead.
