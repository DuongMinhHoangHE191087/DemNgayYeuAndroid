// M-04 spike probe. Run: npx -y node@22 spikes/entitlement-libs/probe.mjs
// Prints one "PROBE <key> <value>" line per observation; nothing here touches credentials.
import { createRequire } from "node:module";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const require = createRequire(path.join(here, "package.json"));
const out = (k, v) => console.log(`PROBE ${k} ${typeof v === "string" ? v : JSON.stringify(v)}`);
const pkgJson = (n) => JSON.parse(fs.readFileSync(path.join(here, "node_modules", n, "package.json"), "utf8"));

out("node_version", process.version);

// (a) require vs import, named exports, engines
const apple = ["SignedDataVerifier", "AppStoreServerAPIClient", "Environment", "APIException", "VerificationException", "VerificationStatus", "Status"];
const libs = [["@apple/app-store-server-library", apple], ["google-auth-library", ["GoogleAuth"]]];
let loadOk = true;
for (const [name, names] of libs) {
  const p = pkgJson(name);
  out(`${name}.version`, p.version);
  out(`${name}.engines`, p.engines ?? "none-declared");
  out(`${name}.type`, p.type ?? "commonjs(default)");
  let req = null, imp = null;
  try { req = require(name); out(`${name}.require`, "ok"); } catch (e) { out(`${name}.require`, `FAIL ${e.code ?? e.message}`); loadOk = false; }
  try { imp = await import(name); out(`${name}.import`, "ok"); } catch (e) { out(`${name}.import`, `FAIL ${e.code ?? e.message}`); loadOk = false; }
  for (const n of names) {
    const r = req && n in req, i = imp && (n in imp);
    out(`${name}.${n}`, `require=${!!r} import=${!!i}`);
    if (!r || !i) loadOk = false;
  }
}
out("load_ok", loadOk);

// (b) onMessagePublished options type has `retry`?
const fnRoot = path.join(here, "node_modules", "firebase-functions");
out("firebase-functions.version", pkgJson("firebase-functions").version);
const ps = fs.readFileSync(path.join(fnRoot, "lib/v2/providers/pubsub.d.ts"), "utf8");
const opts = fs.readFileSync(path.join(fnRoot, "lib/v2/options.d.ts"), "utf8");
const psIface = /export interface PubSubOptions extends options\.EventHandlerOptions \{[\s\S]*?\n\}/.exec(ps)?.[0] ?? "";
const ehIface = /export interface EventHandlerOptions extends Omit<GlobalOptions[\s\S]*?\n\}/.exec(opts)?.[0] ?? "";
out("PubSubOptions.extendsEventHandlerOptions", psIface.length > 0);
out("PubSubOptions.ownRetry", /\bretry\?:/.test(psIface));
out("EventHandlerOptions.retry", /\bretry\?: boolean/.test(ehIface));
out("onMessagePublished.overload_takes_PubSubOptions", /onMessagePublished<T = any>\(options: PubSubOptions/.test(ps));
out("retry_option_checked", /\bretry\?:/.test(psIface) || /\bretry\?: boolean/.test(ehIface));

// (c) ES256 raw (IEEE P1363) signature
const { privateKey, publicKey } = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const data = Buffer.from("header.payload");
const sig = crypto.createSign("SHA256").update(data).sign({ key: privateKey, dsaEncoding: "ieee-p1363" });
const verified = crypto.createVerify("SHA256").update(data).verify({ key: publicKey, dsaEncoding: "ieee-p1363" }, sig);
const viaVerify = crypto.verify("SHA256", data, { key: publicKey, dsaEncoding: "ieee-p1363" }, sig);
const tampered = crypto.verify("SHA256", Buffer.from("header.payloaX"), { key: publicKey, dsaEncoding: "ieee-p1363" }, sig);
out("es256.sigBytes", sig.length);
out("es256.verifyOk", verified && viaVerify);
out("es256.tamperedRejected", tampered === false);
out("es256_ieee_p1363_sign_ok", sig.length === 64 && verified && viaVerify && !tampered);

// (d) Apple Root CA G3 SHA-256 (downloaded from apple.com/certificateauthority)
const url = "https://www.apple.com/certificateauthority/AppleRootCA-G3.cer";
try {
  const res = await fetch(url);
  const der = Buffer.from(await res.arrayBuffer());
  const x = new crypto.X509Certificate(der);
  out("apple_root_g3.http", res.status);
  out("apple_root_g3.bytes", der.length);
  out("apple_root_g3.subject", x.subject.replace(/\n/g, ", "));
  out("apple_root_g3.selfIssued", x.subject === x.issuer);
  out("apple_root_g3.validTo", x.validTo);
  out("apple_root_g3.sha256", crypto.createHash("sha256").update(der).digest("hex").toUpperCase());
  out("apple_root_g3.sha256_x509fingerprint", x.fingerprint256.replace(/:/g, ""));
} catch (e) {
  out("apple_root_g3.error", String(e.message ?? e));
}

// (e) compiled verifier order + VerificationStatus members
const lib = require("@apple/app-store-server-library");
const { SignedDataVerifier, VerificationStatus } = lib;
const src = SignedDataVerifier.prototype.verifyAndDecodeNotification.toString();
const iJwt = src.indexOf("this.verifyJWT(");
const iNotif = src.indexOf("this.verifyNotification(");
out("verifyAndDecodeNotification.verifyJWT_index", iJwt);
out("verifyAndDecodeNotification.verifyNotification_index", iNotif);
out("verifyAndDecodeNotification.jwtBeforeNotification", iJwt >= 0 && iNotif > iJwt);
const vn = SignedDataVerifier.prototype.verifyNotification.toString();
const iBundle = vn.indexOf("INVALID_APP_IDENTIFIER"), iEnv = vn.indexOf("INVALID_ENVIRONMENT");
out("verifyNotification.bundleIdBeforeEnvironment", iBundle >= 0 && iEnv > iBundle);
const need = ["INVALID_APP_IDENTIFIER", "INVALID_ENVIRONMENT", "RETRYABLE_VERIFICATION_FAILURE"];
out("VerificationStatus.members", Object.keys(VerificationStatus).filter((k) => isNaN(Number(k))));
out("VerificationStatus.hasRequired", need.every((k) => k in VerificationStatus));
const vj = SignedDataVerifier.prototype.verifyJWT.toString();
out("verifyJWT.chainThenSignature", /INVALID_CHAIN_LENGTH/.test(vj) && vj.indexOf("verifyCertificateChain") < vj.indexOf("verify(") );
out("verifier_order_and_status_ok", iJwt >= 0 && iNotif > iJwt && iBundle >= 0 && iEnv > iBundle && need.every((k) => k in VerificationStatus));
