#!/usr/bin/env bash

set -euo pipefail

MODEL_NAME="vits-piper-en_US-ryan-medium"
MODEL_TAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${MODEL_NAME}.tar.bz2"
CACHE_ROOT="${XDG_CACHE_HOME:-$HOME/.cache}/readmyfeed-tts"
VENV_DIR="${CACHE_ROOT}/venv"
MODEL_DIR="${CACHE_ROOT}/${MODEL_NAME}"
ARCHIVE_PATH="${CACHE_ROOT}/${MODEL_NAME}.tar.bz2"

mkdir -p "${CACHE_ROOT}"

if [[ ! -d "${VENV_DIR}" ]]; then
  python3 -m venv "${VENV_DIR}"
fi

"${VENV_DIR}/bin/python" -m pip install --upgrade pip
"${VENV_DIR}/bin/python" -m pip install sherpa-onnx soundfile

if [[ ! -f "${ARCHIVE_PATH}" ]]; then
  curl -fL "${MODEL_TAR_URL}" -o "${ARCHIVE_PATH}"
fi

if [[ ! -d "${MODEL_DIR}" ]]; then
  tar xjf "${ARCHIVE_PATH}" -C "${CACHE_ROOT}"
fi

printf '\nSetup complete.\n'
printf 'Model dir: %s\n' "${MODEL_DIR}"
printf 'Venv: %s\n' "${VENV_DIR}"
printf 'Run test: %s/scripts/run-piper-desktop-test.sh\n' "$(pwd)"
