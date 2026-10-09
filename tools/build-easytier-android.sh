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
if ! command -v "${PROTOC:-protoc}" >/dev/null 2>&1; then
    echo "protoc is required to generate Rust protobuf code. Install protobuf-compiler or set PROTOC to its executable path." >&2
    exit 1
fi
"${PROTOC:-protoc}" --version
PROTOC_INCLUDE_ARGS=()
if [ -n "${PROTOC_INCLUDE:-}" ]; then
    PROTOC_INCLUDE_ARGS+=("--proto_path=${PROTOC_INCLUDE}")
fi
if ! "${PROTOC:-protoc}" "${PROTOC_INCLUDE_ARGS[@]}" --descriptor_set_out=/dev/null \
    google/protobuf/duration.proto google/protobuf/timestamp.proto; then
    echo "Protocol Buffers standard definitions are required. Install libprotobuf-dev on Ubuntu/Debian, or set PROTOC_INCLUDE to the directory containing google/protobuf/*.proto." >&2
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

if ! git -C "${EASYTIER_SOURCE_DIR}" cat-file -e "${EASYTIER_COMMIT}^{commit}" 2>/dev/null; then
    git -C "${EASYTIER_SOURCE_DIR}" fetch --depth 1 origin "refs/tags/${EASYTIER_VERSION}"
fi
git -C "${EASYTIER_SOURCE_DIR}" checkout --detach "${EASYTIER_COMMIT}"
ACTUAL_COMMIT="$(git -C "${EASYTIER_SOURCE_DIR}" rev-parse HEAD)"
if [ "${ACTUAL_COMMIT}" != "${EASYTIER_COMMIT}" ]; then
    echo "Expected EasyTier ${EASYTIER_VERSION} at ${EASYTIER_COMMIT}, got ${ACTUAL_COMMIT}." >&2
    exit 1
fi

# 保持上游检出干净；补丁哈希隔离源码和 Cargo 构建缓存。
PATCH_FILE="${PROJECT_ROOT}/tools/native-patches/android-profile.patch"
PATCH_HASH="$(sha256sum "${PATCH_FILE}" | cut -d ' ' -f1)"
UPSTREAM_SOURCE_DIR="${EASYTIER_SOURCE_DIR}"
EASYTIER_SOURCE_DIR="${PROJECT_ROOT}/build/easytier-android-${EASYTIER_COMMIT}-${PATCH_HASH}"
if [ ! -f "${EASYTIER_SOURCE_DIR}/.patch-ready" ]; then
    mkdir -p "${EASYTIER_SOURCE_DIR}"
    git -C "${UPSTREAM_SOURCE_DIR}" archive "${EASYTIER_COMMIT}" | tar -x -C "${EASYTIER_SOURCE_DIR}"
    (cd "${EASYTIER_SOURCE_DIR}" && git apply --check "${PATCH_FILE}" && git apply "${PATCH_FILE}")
    touch "${EASYTIER_SOURCE_DIR}/.patch-ready"
fi

printf '%s\n' "${EASYTIER_SOURCE_DIR}" > "${PROJECT_ROOT}/build/easytier-android-source-path.txt"
if [ "${EASYTIER_PREPARE_ONLY:-0}" = "1" ]; then
    exit 0
fi

CARGO_NDK_VERSION="4.1.2"
INSTALLED_CARGO_NDK_VERSION=""
if command -v cargo-ndk >/dev/null 2>&1; then
    INSTALLED_CARGO_NDK_VERSION="$(cargo ndk --version 2>/dev/null | awk '{print $2}' || true)"
fi
if [ "${INSTALLED_CARGO_NDK_VERSION}" != "${CARGO_NDK_VERSION}" ]; then
    cargo install --locked --force --version "${CARGO_NDK_VERSION}" cargo-ndk
fi

ANDROID_ABIS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")
ANDROID_API_LEVEL="24"
declare -A RUST_TARGETS=(
    [arm64-v8a]="aarch64-linux-android"
    [armeabi-v7a]="armv7-linux-androideabi"
    [x86]="i686-linux-android"
    [x86_64]="x86_64-linux-android"
)

for abi in "${ANDROID_ABIS[@]}"; do
    rust_target="${RUST_TARGETS[$abi]}"
    clang_target="${rust_target}"
    if [ "${abi}" = "armeabi-v7a" ]; then
        clang_target="armv7a-linux-androideabi"
    fi
    installed_rust_targets="$(
        cd "${EASYTIER_SOURCE_DIR}"
        rustup target list --installed
    )"
    if ! grep -Fxq "${rust_target}" <<< "${installed_rust_targets}"; then
        (
            cd "${EASYTIER_SOURCE_DIR}"
            rustup target add "${rust_target}"
        )
    fi

    ndk_environment="$(cargo ndk-env -t "${abi}" -P "${ANDROID_API_LEVEL}")"
    bindgen_args="$(sed -n "s/^export BINDGEN_EXTRA_CLANG_ARGS_${rust_target//-/_}=\"\(.*\)\"$/\1/p" <<< "${ndk_environment}")"
    if [ -z "${bindgen_args}" ]; then
        echo "cargo-ndk did not provide bindgen arguments for ${abi}." >&2
        exit 1
    fi
    # bindgen 优先读取带连字符的 target 变量，补齐 cargo-ndk 4.1.2 遗漏的 API 版本。
    bindgen_args="${bindgen_args} --target=${clang_target}${ANDROID_API_LEVEL}"

    echo "Building EasyTier JNI and FFI for ${abi} (${rust_target}, API ${ANDROID_API_LEVEL})"
    (
        cd "${EASYTIER_SOURCE_DIR}"
        env "BINDGEN_EXTRA_CLANG_ARGS_${rust_target}=${bindgen_args}" \
            cargo ndk -t "${abi}" -P "${ANDROID_API_LEVEL}" build --release \
                -p easytier-ffi -p easytier-android-jni
        # Android 以局部符号加载共享库；JNI 必须声明 FFI 的 DT_NEEDED，不能依赖加载顺序。
        env "BINDGEN_EXTRA_CLANG_ARGS_${rust_target}=${bindgen_args}" \
            cargo ndk -t "${abi}" -P "${ANDROID_API_LEVEL}" rustc --release \
                -p easytier-android-jni -- \
                -L "native=${EASYTIER_SOURCE_DIR}/target/${rust_target}/release" \
                -l dylib=easytier_ffi
    )

    output_dir="${PROJECT_ROOT}/app/src/main/jniLibs/${abi}"
    mkdir -p "${output_dir}"
    cp "${EASYTIER_SOURCE_DIR}/target/${rust_target}/release/libeasytier_android_jni.so" "${output_dir}/"
    cp "${EASYTIER_SOURCE_DIR}/target/${rust_target}/release/libeasytier_ffi.so" "${output_dir}/"
done

echo "Built EasyTier ${EASYTIER_VERSION} native libraries in app/src/main/jniLibs/."
