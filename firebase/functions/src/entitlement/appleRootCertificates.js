'use strict';

const crypto = require('node:crypto');

// Apple Root CA - G3 (DER, base64), tải từ https://www.apple.com/certificateauthority/AppleRootCA-G3.cer (583 byte, hết hạn 2039-04-30).
// Dấu vân tay đã ghi ở docs/superpowers/spikes/p6-m04-server-libs.md; chủ dự án còn phải đối chiếu tay với trang của Apple (DECISIONS, p6 #11).
const APPLE_ROOT_CA_G3_BASE64 =
  'MIICQzCCAcmgAwIBAgIILcX8iNLFS5UwCgYIKoZIzj0EAwMwZzEbMBkGA1UEAwwSQXBwbGUgUm9vdCBDQSAtIEczMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwHhcNMTQwNDMwMTgxOTA2WhcNMzkwNDMwMTgxOTA2WjBnMRswGQYDVQQDDBJBcHBsZSBSb290IENBIC0gRzMxJjAkBgNVBAsMHUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MRMwEQYDVQQKDApBcHBsZSBJbmMuMQswCQYDVQQGEwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABJjpLz1AcqTtkyJygRMc3RCV8cWjTnHcFBbZDuWmBSp3ZHtfTjjTuxxEtX/1H7YyYl3J6YRbTzBPEVoA/VhYDKX1DyxNB0cTddqXl5dvMVztK517IDvYuVTZXpmkOlEKMaNCMEAwHQYDVR0OBBYEFLuw3qFYM4iapIqZ3r6966/ayySrMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMDA2gAMGUCMQCD6cHEFl4aXTQY2e3v9GwOAEZLuN+yRhHFD/3meoyhpmvOwgPUnPWTxnS4at+qIxUCMG1mihDK1A3UT82NQz60imOlM27jbdoXt2QfyFMm+YhidDkLF1vLUagM6BgD56KyKA==';
const APPLE_ROOT_CA_G3_SHA256 = '63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179';

// Trả mảng Buffer cho SignedDataVerifier; tự kiểm lại vân tay khi gọi để một byte bị sửa là lỗi ngay, không verify bằng gốc lạ.
function appleRootCertificates() {
  const der = Buffer.from(APPLE_ROOT_CA_G3_BASE64, 'base64');
  if (crypto.createHash('sha256').update(der).digest('hex') !== APPLE_ROOT_CA_G3_SHA256) {
    throw new Error('Apple Root CA G3 fingerprint mismatch');
  }
  return [der];
}

module.exports = { appleRootCertificates, APPLE_ROOT_CA_G3_SHA256 };
