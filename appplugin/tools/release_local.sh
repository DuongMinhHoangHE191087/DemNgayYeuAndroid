#!/usr/bin/env bash
# Phát hành chạy local (D33-240, không GitHub Actions): tag = sdkVersion -> notes từ CHANGELOG -> unit + lint -> POM check
# -> 4 AAR + SHA256SUMS -> gh release create -> (--publish) GitHub Packages. Dừng ở lỗi đầu tiên.
# Dùng: tools/release_local.sh [--dry-run] [--publish] [--skip-tests]
#   --dry-run     bỏ yêu cầu tag, không tạo GitHub Release, không publish (in lệnh sẽ chạy)
#   --publish     publish 4 artifact lên GitHub Packages; cần env GITHUB_ACTOR + GITHUB_TOKEN (PAT write:packages)
#   --skip-tests  bỏ bước unit + lint
# Credential chỉ đi qua env tới Gradle (gradle/publish.gradle đọc GITHUB_ACTOR/GITHUB_TOKEN); không in, không ghi file.
set -euo pipefail
cd "$(dirname "$0")/.."

DRY=0; PUB=0; SKIP=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY=1;; --publish) PUB=1;; --skip-tests) SKIP=1;;
    *) echo "usage: tools/release_local.sh [--dry-run] [--publish] [--skip-tests]" >&2; exit 2;;
  esac
done

ok() { echo "release_local: $1 OK"; }
fail() { echo "release_local: $1 FAIL${2:+ - $2}" >&2; exit 1; }

# (g, kiểm sớm) --publish cần credential trong env; chỉ kiểm có/không, không bao giờ in giá trị.
if [ "$PUB" -eq 1 ]; then
  if [ -z "${GITHUB_ACTOR:-}" ] || [ -z "${GITHUB_TOKEN:-}" ]; then
    fail credentials "--publish cần env GITHUB_ACTOR và GITHUB_TOKEN (PAT write:packages của chủ dự án); export rồi chạy lại"
  fi
  ok credentials
fi

# (a) sdkVersion và tag
VERSION=$(sed -n 's/^val sdkVersion = "\(.*\)"$/\1/p' build.gradle.kts)
[ -n "$VERSION" ] || fail version "không đọc được sdkVersion trong build.gradle.kts"
TAG="v$VERSION"
if [ "$DRY" -eq 0 ]; then
  [ -z "$(git status --porcelain)" ] || fail worktree "cây làm việc bẩn (git status --porcelain không rỗng); commit hoặc dọn rồi chạy lại"
  git tag --points-at HEAD | grep -qx "$TAG" || fail tag "HEAD không mang tag $TAG (sdkVersion $VERSION)"
fi
ok "tag $TAG"

# (b) release notes từ CHANGELOG
mkdir -p build
awk -v v="$VERSION" '
  $0 ~ "^## \\[" v "\\]" { on = 1; next }
  on && /^## \[/ { exit }
  on { print }
' CHANGELOG.md > build/release-notes.md
[ -s build/release-notes.md ] || fail notes "CHANGELOG.md không có mục [$VERSION]"
ok "notes build/release-notes.md"

# (c) unit + lint hai module, offline
if [ "$SKIP" -eq 0 ]; then
  ./gradlew --no-daemon --offline :testDebugUnitTest :lintDebug :appplugin-nextgen:testDebugUnitTest :appplugin-nextgen:lintDebug \
    || fail tests "unit hoặc lint đỏ"
  ok tests
else
  echo "release_local: tests SKIPPED (--skip-tests)"
fi

# (d) POM của 4 publication
./gradlew --no-daemon --offline publishAllPublicationsToLocalCheckRepository || fail pom "publish LocalCheck lỗi"
bash tools/pom_check.sh build/repo || fail pom "pom_check đỏ"
ok pom

# (e) 4 AAR + SHA256SUMS
./gradlew --no-daemon --offline :assembleRelease :appplugin-max:assembleRelease :appplugin-game:assembleRelease \
  :appplugin-nextgen:assembleRelease || fail aar "assembleRelease lỗi"
rm -rf build/release; mkdir -p build/release
cp build/outputs/aar/appplugin-release.aar "build/release/appplugin-$VERSION.aar" || fail aar "thiếu AAR appplugin"
cp appplugin-max/build/outputs/aar/appplugin-max-release.aar "build/release/appplugin-max-$VERSION.aar" || fail aar "thiếu AAR max"
cp appplugin-game/build/outputs/aar/appplugin-game-release.aar "build/release/appplugin-game-$VERSION.aar" || fail aar "thiếu AAR game"
cp appplugin-nextgen/build/outputs/aar/appplugin-nextgen-release.aar "build/release/appplugin-nextgen-$VERSION.aar" || fail aar "thiếu AAR nextgen"
(cd build/release && shasum -a 256 ./*.aar | sed 's# \./# #' > SHA256SUMS) || fail aar "SHA256SUMS lỗi"
ok "aar $(cd build/release && ls ./*.aar | sed 's#\./##' | tr '\n' ' ' | sed 's/ $//')"

# (f) GitHub Release: có sẵn đủ 4 AAR thì bỏ qua (chạy lại --publish được), thiếu asset thì FAIL, không bao giờ ghi đè
R=build/release
GH_CMD=(gh release create "$TAG" "$R/appplugin-$VERSION.aar" "$R/appplugin-max-$VERSION.aar" "$R/appplugin-game-$VERSION.aar"
  "$R/appplugin-nextgen-$VERSION.aar" "$R/SHA256SUMS" --title "appplugin $VERSION" --notes-file build/release-notes.md --verify-tag)
REMOTE_CMD='git ls-remote --exit-code --tags origin "$TAG"'
if [ "$DRY" -eq 1 ]; then
  echo "release_local: dry-run, sẽ kiểm: $REMOTE_CMD"
  echo "release_local: dry-run, sẽ chạy: ${GH_CMD[*]}"
  ok "github-release (dry-run)"
else
  git ls-remote --exit-code --tags origin "$TAG" >/dev/null 2>&1 || fail github-release "tag $TAG chưa push lên origin"
  if ASSETS=$(gh release view "$TAG" --json assets --jq '.assets[].name' 2>/dev/null); then
    MISSING=""
    for n in appplugin appplugin-max appplugin-game appplugin-nextgen; do
      printf '%s\n' "$ASSETS" | grep -qx "$n-$VERSION.aar" || MISSING="$MISSING $n-$VERSION.aar"
    done
    [ -z "$MISSING" ] || fail github-release "Release $TAG thiếu asset:$MISSING — xoá release rồi chạy lại (không ghi đè)"
    echo "release_local: gh release $TAG đã có đủ 4 AAR, bỏ qua tạo"
  else
    "${GH_CMD[@]}" || fail github-release "gh release create lỗi"
  fi
  ok github-release
fi

# (g) GitHub Packages: không ghi đè một version đã có
if [ "$PUB" -eq 1 ] && [ "$DRY" -eq 0 ]; then
  ./gradlew :publishReleasePublicationToGitHubPackagesRepository \
    :appplugin-max:publishReleasePublicationToGitHubPackagesRepository \
    :appplugin-game:publishReleasePublicationToGitHubPackagesRepository \
    :appplugin-nextgen:publishReleasePublicationToGitHubPackagesRepository || fail publish "publish Packages lỗi"
  ok publish
elif [ "$PUB" -eq 1 ]; then
  echo "release_local: dry-run, bỏ publish Packages"
else
  echo "release_local: chưa publish GitHub Packages; chủ dự án chạy lại kèm --publish với env GITHUB_ACTOR + GITHUB_TOKEN (PAT write:packages), cùng lúc tạo release"
fi
echo "release_local: DONE $TAG"
