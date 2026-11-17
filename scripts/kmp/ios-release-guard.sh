#!/usr/bin/env bash
# Fails a Release iOS build that still carries test/empty/malformed AdMob ids or a non-https legal URL.
# Reads CONFIGURATION, ADMOB_APP_ID, ADMOB_BANNER_ID, LEGAL_BASE_URL from the environment.
# DEC-255: ADMOB_INTERSTITIAL_ID and ADMOB_AOA_ID are deliberately NOT checked.
set -u
[ "${CONFIGURATION:-}" = "Release" ] || exit 0

TEST_PUB='ca-app-pub-3940256099942544'
fail() { echo "ios-release-guard: $1"; exit 1; }

check_id() { # check_id <VAR_NAME> <value> <regex>
  case "$2" in *"$TEST_PUB"*) fail "$1" ;; esac
  [[ "$2" =~ $3 ]] || fail "$1"
}

check_id ADMOB_APP_ID "${ADMOB_APP_ID:-}" '^ca-app-pub-[0-9]{16}~[0-9]{10}$'
check_id ADMOB_BANNER_ID "${ADMOB_BANNER_ID:-}" '^ca-app-pub-[0-9]{16}/[0-9]{10}$'
case "${LEGAL_BASE_URL:-}" in https://*) ;; *) fail LEGAL_BASE_URL ;; esac
exit 0
