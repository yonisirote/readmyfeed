#!/usr/bin/env bash
#
# Downloads the Piper en_US-ryan-medium model from sherpa-onnx,
# re-packages it as a .zip, uploads it as a GitHub release asset
# using the `gh` CLI, and updates ttsConstants.ts with the URL.
#
# Prerequisites:
#   - gh CLI installed and authenticated (`gh auth login`)
#
# Usage:
#   ./scripts/prepare-tts-model.sh
#
set -euo pipefail

MODEL_NAME="vits-piper-en_US-ryan-medium"
RELEASE_TAG="tts-model-v1"
TAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${MODEL_NAME}.tar.bz2"
WORK_DIR="$(mktemp -d)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONSTANTS_FILE="${PROJECT_DIR}/src/tts/ttsConstants.ts"

# Check for gh CLI
if ! command -v gh &>/dev/null; then
  echo "❌ gh CLI not found. Install it: https://cli.github.com/"
  exit 1
fi

echo "⬇️  Downloading model from sherpa-onnx..."
curl -L --progress-bar -o "${WORK_DIR}/${MODEL_NAME}.tar.bz2" "${TAR_URL}"

echo "📦 Extracting tar.bz2..."
tar xjf "${WORK_DIR}/${MODEL_NAME}.tar.bz2" -C "${WORK_DIR}"

echo "🗜️  Creating zip..."
ZIP_OUTPUT="${WORK_DIR}/${MODEL_NAME}.zip"
(cd "${WORK_DIR}" && zip -r "${ZIP_OUTPUT}" "${MODEL_NAME}")

echo "🚀 Creating GitHub release and uploading..."
cd "${PROJECT_DIR}"

# Create release (or reuse existing tag)
if gh release view "${RELEASE_TAG}" &>/dev/null; then
  echo "   Release ${RELEASE_TAG} already exists, uploading asset (overwriting if present)..."
  gh release upload "${RELEASE_TAG}" "${ZIP_OUTPUT}" --clobber
else
  gh release create "${RELEASE_TAG}" "${ZIP_OUTPUT}" \
    --title "TTS Model: Piper en_US-ryan-medium" \
    --notes "Piper TTS model (VITS, en_US-ryan-medium) repackaged as zip for on-device download."
fi

# Get the download URL for the uploaded asset
REPO_URL="$(gh repo view --json url -q .url)"
DOWNLOAD_URL="${REPO_URL}/releases/download/${RELEASE_TAG}/${MODEL_NAME}.zip"

echo "📝 Updating ttsConstants.ts..."
sed -i "s|'YOUR_HOSTED_MODEL_ZIP_URL'|'${DOWNLOAD_URL}'|" "${CONSTANTS_FILE}"

echo "🧹 Cleaning up..."
rm -rf "${WORK_DIR}"

echo ""
echo "✅ Done!"
echo "   Release:  ${REPO_URL}/releases/tag/${RELEASE_TAG}"
echo "   Download: ${DOWNLOAD_URL}"
echo "   ttsConstants.ts updated with the URL."
