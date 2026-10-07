#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EASYTIER_VERSION="v2.6.4"
EASYTIER_COMMIT="8428a89d2dabc94c97d370ec607c6ca142473626"
EASYTIER_SOURCE_DIR="${EASYTIER_SOURCE_DIR:-${PROJECT_ROOT}/build/easytier-${EASYTIER_VERSION}}"
EASYTIER_REPOSITORY="https://github.com/EasyTier/EasyTier.git"

if ! command -v git >/dev/null 2>&1; then
    echo "git is required to fetch the pinned EasyTier source." >&2
    exit 1
fi
if ! command -v rustc >/dev/null 2>&1 || ! command -v cargo >/dev/null 2>&1; then
    echo "Rust and Cargo are required. Install the stable Rust toolchain first." >&2
    exit 1
fi
if ! command -v rustup >/dev/null 2>&1; then
    echo "rustup is required to install Android Rust targets." >&2
    exit 1
fi

if [ -e "${EASYTIER_SOURCE_DIR}" ] && [ ! -d "${EASYTIER_SOURCE_DIR}/.git" ]; then
    echo "${EASYTIER_SOURCE_DIR} exists but is not an EasyTier Git checkout." >&2
    exit 1
fi
if [ ! -d "${EASYTIER_SOURCE_DIR}/.git" ]; then
    mkdir -p "$(dirname "${EASYTIER_SOURCE_DIR}")"
    git clone --depth 1 --branch "${EASYTIER_VERSION}" "${EASYTIER_REPOSITORY}" "${EASYTIER_SOURCE_DIR}"
fi

if [ -n "$(git -C "${EASYTIER_SOURCE_DIR}" status --porcelain)" ]; then
    echo "The EasyTier checkout has local changes; refusing to replace them." >&2
    exit 1
fi

git -C "${EASYTIER_SOURCE_DIR}" fetch --depth 1 origin "refs/tags/${EASYTIER_VERSION}"
git -C "${EASYTIER_SOURCE_DIR}" checkout --detach "${EASYTIER_COMMIT}"
ACTUAL_COMMIT="$(git -C "${EASYTIER_SOURCE_DIR}" rev-parse HEAD)"
if [ "${ACTUAL_COMMIT}" != "${EASYTIER_COMMIT}" ]; then
    echo "Expected EasyTier ${EASYTIER_VERSION} at ${EASYTIER_COMMIT}, got ${ACTUAL_COMMIT}." >&2
    exit 1
fi

if ! cargo ndk --version >/dev/null 2>&1; then
    cargo install --locked --version 3.5.4 cargo-ndk
fi

ANDROID_ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")
declare -A RUST_TARGETS=(
    [arm64-v8a]="aarch64-linux-android"
    [armeabi-v7a]="armv7-linux-androideabi"
    [x86]="i686-linux-android"
    [x86_64]="x86_64-linux-android"
)

for abi in "${ANDROID_ABIS[@]}"; do
    rust_target="${RUST_TARGETS[$abi]}"
    if ! rustup target list --installed | grep -Fxq "${rust_target}"; then
        rustup target add "${rust_target}"
    fi

    echo "Building EasyTier JNI and FFI for ${abi} (${rust_target})"
    (
        cd "${EASYTIER_SOURCE_DIR}/easytier-contrib/easytier-ffi"
        cargo ndk -t "${abi}" build --release
    )
    (
        cd "${EASYTIER_SOURCE_DIR}/easytier-contrib/easytier-android-jni"
        cargo ndk -t "${abi}" build --release
    )

    output_dir="${PROJECT_ROOT}/app/src/main/jniLibs/${abi}"
    mkdir -p "${output_dir}"
    cp "${EASYTIER_SOURCE_DIR}/target/${rust_target}/release/libeasytier_android_jni.so" "${output_dir}/"
    cp "${EASYTIER_SOURCE_DIR}/target/${rust_target}/release/libeasytier_ffi.so" "${output_dir}/"
done

echo "Built EasyTier ${EASYTIER_VERSION} native libraries in app/src/main/jniLibs/."
