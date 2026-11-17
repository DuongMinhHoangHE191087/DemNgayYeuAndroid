#!/usr/bin/env node
'use strict';

// Sinh cặp khoá ES256 (P-256) để ký token entitlement.
//
//   node scripts/make-signing-key.js | firebase functions:secrets:set ENTITLEMENT_SIGNING_KEY
//
// stdout: CHỈ một dòng JSON bí mật {"kid","pem"} (đúng dạng parseSigningKey đọc). Khoá riêng không đi đâu khác:
//   không ghi tệp, không in ra stderr. Đừng chạy trong terminal được ghi lại và đừng dán vào chat/commit.
// stderr: kid và khoá công khai SPKI base64, để đưa vào EntitlementPublicKeys của app (không phải bí mật).
// kid = 8 byte đầu của SHA-256 của SPKI (DER), dạng hex.
const crypto = require('node:crypto');

function generateSigningKey() {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'P-256' });
  const spki = publicKey.export({ type: 'spki', format: 'der' });
  return {
    kid: crypto.createHash('sha256').update(spki).digest().subarray(0, 8).toString('hex'),
    pem: privateKey.export({ type: 'pkcs8', format: 'pem' }),
    spkiBase64: spki.toString('base64'),
  };
}

const secretJson = ({ kid, pem }) => JSON.stringify({ kid, pem });

if (require.main === module) {
  const key = generateSigningKey();
  process.stdout.write(`${secretJson(key)}\n`);
  process.stderr.write(`kid=${key.kid}\npublicKeySpkiBase64=${key.spkiBase64}\n`);
}

module.exports = { generateSigningKey, secretJson };
