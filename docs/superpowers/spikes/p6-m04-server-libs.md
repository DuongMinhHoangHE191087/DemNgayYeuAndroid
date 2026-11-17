# P6 M-04 spike: server entitlement libraries

Probe: `npx -y node@22 spikes/entitlement-libs/probe.mjs` (ran on node v22.23.3; exact pins `@apple/app-store-server-library@3.1.0`, `google-auth-library@11.2.0`, `firebase-functions@7.4.0`, installed only inside `spikes/entitlement-libs`, `node_modules/` is git-ignored). Raw output = the `PROBE` lines; summary below is what was observed.

## (a) Loading and exports
- `@apple/app-store-server-library@3.1.0`: CommonJS (`main: dist/index.js`, no `type`, no `exports` map), `engines` not declared. Both `require()` and `import()` work. All of `SignedDataVerifier`, `AppStoreServerAPIClient`, `Environment`, `APIException`, `VerificationException`, `VerificationStatus`, `Status` are present in both.
- `google-auth-library@11.2.0`: CommonJS (`main: ./build/src/index.js`), `engines: {"node": ">=22"}`. Both `require()` and `import()` work; `GoogleAuth` present in both. Consequence: the functions runtime that uses it must be Node 22 or newer (it would not be declared-compatible with Node 20).
- `firebase-functions@7.4.0`: `engines: {"node": ">=18.0.0"}`, dual `require`/`import` exports map.

## (b) `retry` option (#111)
`lib/v2/providers/pubsub.d.ts`: `PubSubOptions extends options.EventHandlerOptions`, and `onMessagePublished(options: PubSubOptions, handler)` is an overload. `PubSubOptions` declares its own `retry?: boolean | Expression<boolean> | ResetValue` (pubsub.d.ts line 178) and `EventHandlerOptions` declares the same (options.d.ts line 192). So `onMessagePublished({ topic, retry: true }, handler)` type-checks. Behaviour on a deployed function (redelivery on thrown error) is UNVERIFIED (needs deploy).

## (c) ES256 raw signature (#107)
`crypto.createSign('SHA256').sign({ key, dsaEncoding: 'ieee-p1363' })` on a prime256v1 key gave exactly 64 bytes; `crypto.createVerify` and `crypto.verify('SHA256', ..., { dsaEncoding: 'ieee-p1363' }, sig)` both verified it, and a tampered payload was rejected. Usable for JWT ES256 (App Store Server API bearer token) without DER conversion.

## (d) Apple Root CA G3
Downloaded `https://www.apple.com/certificateauthority/AppleRootCA-G3.cer` (HTTP 200, 583 bytes DER). Subject `CN=Apple Root CA - G3, OU=Apple Certification Authority, O=Apple Inc., C=US`, self-issued, valid to 2039-04-30.
SHA-256 (of the DER file, equal to `X509Certificate.fingerprint256`): `63343ABFB89A6A03EBB57E9B3F5FA7BE7C4F5C756F3017B3A8C488C3653E9179`.
Pin this value in M-22a when bundling the root cert.

## (e) Verifier order and status codes (compiled `dist/jws_verification.js` of the installed 3.1.0)
- `verifyAndDecodeNotification` calls `this.verifyJWT(...)` first (index 84 in the function source) and `this.verifyNotification(bundleId, appAppleId, environment)` afterwards (index 1417).
- `verifyJWT` decodes and validates shape, requires an `x5c` chain of exactly 3 certs (`INVALID_CHAIN_LENGTH`), verifies the certificate chain against the supplied roots (`verifyCertificateChain`), then verifies the JWT signature (`jsonwebtoken.verify`). Caveat: for `Environment.XCODE` and `Environment.LOCAL_TESTING` it returns right after decoding, skipping chain and signature checks. Never build a production verifier with those environments.
- `verifyNotification` compares `bundleId` (and `appAppleId` in PRODUCTION) first -> `INVALID_APP_IDENTIFIER`, then `environment` -> `INVALID_ENVIRONMENT`.
- `VerificationStatus` members: OK, VERIFICATION_FAILURE, RETRYABLE_VERIFICATION_FAILURE, INVALID_APP_IDENTIFIER, INVALID_ENVIRONMENT, INVALID_CHAIN_LENGTH, INVALID_CERTIFICATE, FAILURE. `RETRYABLE_VERIFICATION_FAILURE` is thrown from the OCSP/online-check path (lines 302 and 310), so M-22a can return 5xx for it.
- A valid notification for another app therefore throws `VerificationException` with status `INVALID_APP_IDENTIFIER` only after signature validation succeeded, so M-22a can safely map it to 200.

## Not verified
- Comparison with `main` at https://github.com/apple/app-store-server-library-node/blob/main/jws_verification.ts (2026-10-13): not fetched (spike limited network use); only the installed 3.1.0 compiled code was inspected.
- No real signed notification was verified end to end (needs Apple credentials / real JWS); `retry` runtime behaviour on Cloud Functions not deployed.
- `scripts/ui/spike_requirements.json` has no key `p6-m04-server-libs`, so `spike_gate.py` rejects this file with "not in spike_requirements.json" until the table is updated (outside this task's owned files).

RESULT: PASS
METRIC node22_load_ok 1 >= 1
METRIC apple_root_g3_sha256_recorded 1 >= 1
METRIC retry_option_checked 1 >= 1
METRIC es256_ieee_p1363_sign_ok 1 >= 1
METRIC verifier_order_and_status_ok 1 >= 1
