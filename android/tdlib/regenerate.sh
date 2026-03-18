#!/usr/bin/env bash
set -euo pipefail

readonly DEFAULT_TDLIB_COMMIT="0ae923c493bceb75433de2682ba8ae29cc7bf88d"
readonly DEFAULT_NDK_VERSION="26.1.10909125"
readonly TDLIB_REPO_URL="https://github.com/tdlib/td.git"

usage() {
  cat <<'EOF'
Usage: regenerate.sh [--dry-run]

Build the pinned TDLib Android Java/JNI artifacts and copy them into android/tdlib.

Environment overrides:
  ANDROID_SDK_ROOT / ANDROID_HOME  Android SDK path (default: $HOME/Android/Sdk)
  TDLIB_NDK_VERSION                NDK version to use (default: 26.1.10909125)
  TDLIB_UPSTREAM_COMMIT            TDLib commit to build (default: pinned repo commit)
EOF
}

log() {
  printf '[tdlib] %s\n' "$*"
}

fail() {
  printf '[tdlib] %s\n' "$*" >&2
  exit 1
}

dry_run=false
if [[ $# -gt 1 ]]; then
  usage
  exit 1
fi

if [[ $# -eq 1 ]]; then
  case "$1" in
    --dry-run)
      dry_run=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
fi

if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  sdk_root="${ANDROID_SDK_ROOT}"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  sdk_root="${ANDROID_HOME}"
else
  sdk_root="${HOME}/Android/Sdk"
fi

readonly sdk_root
readonly ndk_version="${TDLIB_NDK_VERSION:-${DEFAULT_NDK_VERSION}}"
readonly tdlib_commit="${TDLIB_UPSTREAM_COMMIT:-${DEFAULT_TDLIB_COMMIT}}"
readonly script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly java_target_dir="${script_dir}/src/main/java/org/drinkless/tdlib"
readonly jni_target_dir="${script_dir}/src/main/jniLibs"
readonly license_target_file="${script_dir}/LICENSE_1_0.txt"

for tool in git install mktemp; do
  command -v "${tool}" >/dev/null 2>&1 || fail "Required tool '${tool}' is not installed."
done

[[ -d "${sdk_root}" ]] || fail "Android SDK directory not found at '${sdk_root}'."
[[ -d "${sdk_root}/ndk/${ndk_version}" ]] || fail "Android NDK '${ndk_version}' is not installed under '${sdk_root}/ndk'."

if [[ "${dry_run}" == true ]]; then
  log "Would build TDLib commit ${tdlib_commit} using SDK '${sdk_root}' and NDK '${ndk_version}'."
  log "Would refresh Java bindings in '${java_target_dir}'."
  log "Would refresh JNI libraries in '${jni_target_dir}'."
  exit 0
fi

workdir="$(mktemp -d "${TMPDIR:-/tmp}/readmyfeed-tdlib-XXXXXXXX")"
cleanup() {
  if [[ -d "${workdir}" ]]; then
    rm -rf "${workdir}"
  fi
}
trap cleanup EXIT

readonly workdir
readonly upstream_dir="${workdir}/td"
readonly upstream_android_dir="${upstream_dir}/example/android"

log "Cloning TDLib sources into '${upstream_dir}'."
git clone --filter=blob:none --no-checkout "${TDLIB_REPO_URL}" "${upstream_dir}"
git -C "${upstream_dir}" checkout --quiet "${tdlib_commit}"

log "Building OpenSSL for TDLib."
(
  cd "${upstream_android_dir}"
  ./build-openssl.sh "${sdk_root}" "${ndk_version}"
)

log "Building TDLib Java/JNI artifacts."
(
  cd "${upstream_android_dir}"
  ./build-tdlib.sh "${sdk_root}" "${ndk_version}" "third-party/openssl" "c++_static" "Java"
)

log "Refreshing generated Java bindings."
mkdir -p "${java_target_dir}"
for source_file in "${upstream_dir}/tdlib/java/org/drinkless/tdlib/"*.java; do
  install -m 0644 "${source_file}" "${java_target_dir}/"
done

log "Refreshing packaged license copy."
install -m 0644 "${upstream_dir}/LICENSE_1_0.txt" "${license_target_file}"

log "Refreshing JNI libraries."
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
  source_file="${upstream_dir}/tdlib/libs/${abi}/libtdjni.so"
  [[ -f "${source_file}" ]] || fail "Expected TDLib binary not found for ABI '${abi}'."
  install -D -m 0755 "${source_file}" "${jni_target_dir}/${abi}/libtdjni.so"
done

log "TDLib artifacts refreshed. JNI libraries remain local build artifacts and stay ignored by git."
