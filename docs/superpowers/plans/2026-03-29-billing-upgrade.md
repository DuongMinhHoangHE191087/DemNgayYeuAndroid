# Google Play Billing Upgrade & Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Google Play Billing Library across `:app` and `:appplugin` modules to version 8.0.0, enable automatic service reconnection, handle detailed sub-response codes, handle suspended subscriptions, and add in-app subscription management compliance links.

**Architecture:** Standardize `BillingClient` configuration across modules, leverage `BillingClient.Builder.enableAutoServiceReconnection()`, process granular error sub-codes in `BillingManager`, and add Google Play compliant subscription management UI links in Jetpack Compose paywalls.

**Tech Stack:** Kotlin, Google Play Billing KTX (v8.0.0), Jetpack Compose, Coroutines, StateFlow / SharedFlow.

**Spec:** Bounded In-Chat Design approved on 2026-03-29.

## Global Constraints

- Target SDK 36, Min SDK 24.
- All UI components built with Jetpack Compose & Material 3.
- `isVipUser` in `BillingManager.kt` remains the Single Source of Truth for VIP status in `:app`.
- Must acknowledge non-consumed purchases within 3 days.

## Review Focus

1. **Service Disconnection:** `BillingClient` loses connection during billing flow — verified that `enableAutoServiceReconnection()` automatically restores connection without crashing or stalling UI.
2. **Suspended Subscriptions:** User has a suspended subscription — verified `purchase.isSuspended()` is checked so VIP status is not mistakenly granted.
3. **Sub-response Code Handling:** Purchase fails due to insufficient funds or user ineligibility — verified user receives specific, localized error message rather than generic failure.
4. **Subscription Management Policy Compliance:** User taps "Manage Subscription" on Paywall UI — verified correct Google Play Subscriptions URL opens via `LocalUriHandler`.

---

### Task 1: Standardize Billing Library Dependency in `libs.versions.toml`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `appplugin/build.gradle.kts`

**Interfaces:**
- Consumes: None
- Produces: Updated `billingKtx` dependency version (`8.0.0`) used by `:app` and `:appplugin`

- [ ] **Step 1: Update version reference in `libs.versions.toml`**

Change `billingKtx = "7.1.1"` to `billingKtx = "8.0.0"` in `gradle/libs.versions.toml`.

- [ ] **Step 2: Update `appplugin/build.gradle.kts` to use version catalog dependency**

Ensure `:appplugin` references `libs.google.play.billing.ktx` or `libs.google.play.billing` consistently.

- [ ] **Step 3: Commit dependency changes**

```bash
git add gradle/libs.versions.toml appplugin/build.gradle.kts
git commit -m "build: update Google Play Billing Library to 8.0.0 across app and appplugin"
```

---

### Task 2: Upgrade `BillingManager.kt` with Auto-Reconnection & Sub-Response Codes

**Files:**
- Modify: `app/src/main/java/com/example/billing/BillingManager.kt`

**Interfaces:**
- Consumes: Updated Billing Library v8.0.0 API
- Produces: `BillingManager` with `enableAutoServiceReconnection()`, sub-code error messages, and `isSuspended()` handling

- [ ] **Step 1: Add `enableAutoServiceReconnection()` and update `BillingClient` builder**

In `BillingManager.kt`, update `BillingClient.newBuilder(context)` to include `.enableAutoServiceReconnection()`.

```kotlin
private val billingClient: BillingClient = BillingClient.newBuilder(context)
    .setListener(this)
    .enablePendingPurchases(
        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
    )
    .enableAutoServiceReconnection()
    .build()
```

- [ ] **Step 2: Simplify `onBillingServiceDisconnected()` and connection logic**

Remove manual `delay(3.seconds)` reconnection loop in `onBillingServiceDisconnected()` as `enableAutoServiceReconnection()` handles reconnection automatically.

- [ ] **Step 3: Add granular sub-response code handling in `onPurchasesUpdated()`**

Extract sub-response codes when `BillingResult` is not `OK`:

```kotlin
val errorMessage = when (billingResult.subResponseCode) {
    BillingClient.BillingResponseCode.PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS -> 
        "Thanh toán thất bại: Tài khoản không đủ số dư."
    BillingClient.BillingResponseCode.USER_INELIGIBLE -> 
        "Bạn không đủ điều kiện áp dụng ưu đãi này."
    else -> 
        "Thanh toán thất bại (${billingResult.responseCode}): ${billingResult.debugMessage}"
}
```

- [ ] **Step 4: Add `purchase.isSuspended()` check for subscriptions**

In `handlePurchase(purchase: Purchase)`:

```kotlin
if (purchase.isSuspended()) {
    _isVipUser.value = false
    billingScope.launch {
        _purchaseEvent.emit(PurchaseEvent.Error("Gói đăng ký của bạn đang bị tạm dừng. Vui lòng cập nhật phương thức thanh toán trên Google Play."))
    }
    return
}
```

- [ ] **Step 5: Commit `BillingManager.kt` changes**

```bash
git add app/src/main/java/com/example/billing/BillingManager.kt
git commit -m "feat(billing): enable auto-reconnection and sub-response code handling in BillingManager"
```

---

### Task 3: Add Subscription Management & Policy Links to Paywall UI

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/PaywallScreen.kt`
- Modify: `app/src/main/java/com/example/ui/components/VipSubscriptionDialog.kt`

**Interfaces:**
- Consumes: `LocalUriHandler` in Compose
- Produces: Compliance links for Google Play Subscriptions Center, Privacy Policy, and Terms of Service

- [ ] **Step 1: Add Subscription Management button in `PaywallScreen.kt`**

Add a text button using `LocalUriHandler` to open `https://play.google.com/store/account/subscriptions?package=${context.packageName}`.

```kotlin
val uriHandler = LocalUriHandler.current
TextButton(
    onClick = {
        uriHandler.openUri("https://play.google.com/store/account/subscriptions?package=${context.packageName}")
    }
) {
    Text("Quản lý & Hủy gói cước (Google Play)", color = Color.Gray, fontSize = 12.sp)
}
```

- [ ] **Step 2: Add Subscription Management button in `VipSubscriptionDialog.kt`**

Add the same compliance link in `VipSubscriptionDialog.kt` footer area.

- [ ] **Step 3: Commit Paywall UI changes**

```bash
git add app/src/main/java/com/example/ui/screens/PaywallScreen.kt app/src/main/java/com/example/ui/components/VipSubscriptionDialog.kt
git commit -m "feat(paywall): add subscription management link for Google Play policy compliance"
```

---

### Task 4: Sync VIP State with Plugin Layer & Verify Build

**Files:**
- Modify: `app/src/main/java/com/example/billing/BillingManager.kt`

**Interfaces:**
- Consumes: `BillingManager.isVipUser` StateFlow
- Produces: Synced VIP state across `:app` and `:appplugin` (`Entitlements` / `AdsBrain`)

- [ ] **Step 1: Sync VIP status update to `IapHelper` / `Entitlements`**

In `BillingManager.kt`, when `isVipUser` changes to `true`, notify `IapHelper` / `Entitlements` or `AdsBrain.onPurchase()` if available.

- [ ] **Step 2: Run Gradle Build verification**

Run `gradle_build` task to ensure all modules compile cleanly with zero errors.

- [ ] **Step 3: Commit final integration changes**

```bash
git add app/src/main/java/com/example/billing/BillingManager.kt
git commit -m "feat(billing): sync VIP state with plugin layer and complete billing upgrade"
```
