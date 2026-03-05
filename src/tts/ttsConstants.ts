// ---------------------------------------------------------------------------
// Model: Piper en_US-ryan-medium (single-speaker male, ~63 MB ONNX)
//
// To prepare the model zip:
//   1. Download the tar.bz2:
//      https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ryan-medium.tar.bz2
//   2. Extract it:   tar xjf vits-piper-en_US-ryan-medium.tar.bz2
//   3. Re-zip it:    cd vits-piper-en_US-ryan-medium && zip -r ../vits-piper-en_US-ryan-medium.zip .
//   4. Host the zip (GitHub release, S3, your CDN, etc.)
//   5. Set TTS_MODEL_ZIP_URL below to your hosted URL.
//
// HuggingFace repo (individual files):
//   https://huggingface.co/csukuangfj/vits-piper-en_US-ryan-medium
// ---------------------------------------------------------------------------

export const TTS_MODEL_ZIP_URL =
  'https://github.com/yonisirote/readmyfeed/releases/download/tts-model-v1/vits-piper-en_US-ryan-medium.zip';

export const TTS_MODEL_NAME = 'vits-piper-en_US-ryan-medium';
export const TTS_ONNX_FILENAME = 'en_US-ryan-medium.onnx';
export const TTS_TOKENS_FILENAME = 'tokens.txt';
export const TTS_ESPEAK_DIR_NAME = 'espeak-ng-data';

export const TTS_DEFAULT_SPEAKER_ID = 0;
export const TTS_DEFAULT_SPEED = 1.0;
