#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> [1/3] :core:test (kripto, format, TOTP, WebAuthn, DomainMatcher, VaultMerge)"
./gradlew :core:test

echo "==> [2/3] :app:testDebugUnitTest (Manifest güvenlik & politika testleri)"
./gradlew :app:testDebugUnitTest

echo "==> [3/3] extension npm test (WebCrypto & Kotlin->JS interop)"
(cd extension && npm test)

echo ""
echo "Tüm otomatik testler başarıyla geçti."
