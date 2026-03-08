#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CACHE_ROOT="${XDG_CACHE_HOME:-$HOME/.cache}/readmyfeed-tts"
VENV_DIR="${CACHE_ROOT}/venv"
MODEL_DIR="${CACHE_ROOT}/vits-piper-en_US-ryan-medium"
OUTPUT_PATH="${1:-${PWD}/piper-desktop-test.wav}"
PLAY_AUDIO="${PLAY_AUDIO:-0}"

if [[ ! -x "${VENV_DIR}/bin/python" || ! -d "${MODEL_DIR}" ]]; then
  printf 'Desktop Piper test is not set up yet. Run %s/scripts/setup-piper-desktop-test.sh first.\n' "$(cd "${SCRIPT_DIR}/.." && pwd)" >&2
  exit 1
fi

"${VENV_DIR}/bin/python" "${SCRIPT_DIR}/piperDesktopTest.py" \
  --model-dir "${MODEL_DIR}" \
  --output "${OUTPUT_PATH}" \
  "${@:2}"

if [[ "${PLAY_AUDIO}" == "1" ]]; then
  if command -v ffplay >/dev/null 2>&1; then
    ffplay -autoexit -nodisp "${OUTPUT_PATH}"
  else
    printf 'Generated %s but ffplay is not installed, so it was not auto-played.\n' "${OUTPUT_PATH}"
  fi
fi
