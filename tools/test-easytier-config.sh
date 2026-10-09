#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATCH_HASH="$(sha256sum "${PROJECT_ROOT}/tools/native-patches/android-profile.patch" | cut -d ' ' -f1)"
SOURCE_DIR="${PROJECT_ROOT}/build/easytier-android-8428a89d2dabc94c97d370ec607c6ca142473626-${PATCH_HASH}"
if [ ! -f "${SOURCE_DIR}/.patch-ready" ]; then
    echo "Run bash tools/build-easytier-android.sh to prepare the patched source first." >&2
    exit 1
fi
mkdir -p "${SOURCE_DIR}/easytier/tests"
cp "${PROJECT_ROOT}/tools/native-patches/android-profile-tests.rs" "${SOURCE_DIR}/easytier/tests/android_profile.rs"
if [ "${1:-}" = "--android-fixtures" ]; then
    shift
    export ANDROID_TOML_FIXTURE_DIR="${PROJECT_ROOT}/app/build/generated-contracts"
    for fixture in full automatic credential; do
        if [ ! -f "${ANDROID_TOML_FIXTURE_DIR}/${fixture}.toml" ]; then
            echo "Run ./gradlew :app:testDebugUnitTest to generate Android encoder fixtures first." >&2
            exit 1
        fi
    done
    set -- "$@" -- --include-ignored
fi
cd "${SOURCE_DIR}"
# 上游 Linux 配置依赖 mold；配置契约测试使用系统 GNU 链接器。
export CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_RUSTFLAGS="${CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_RUSTFLAGS:--C link-arg=-fuse-ld=bfd}"
export CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_RUSTFLAGS="${CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_RUSTFLAGS:--C link-arg=-fuse-ld=bfd}"
cargo test --locked -p easytier --test android_profile "$@"
