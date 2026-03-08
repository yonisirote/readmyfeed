#!/usr/bin/env python3

import argparse
from pathlib import Path
import time


DEFAULT_TEXT = (
    "Good morning. Here are the top posts from your feed. "
    "First, OpenAI released a new model today. "
    "Second, Google announced updates at 10:30 a.m. "
    "Finally, remember to call Sam at 415-555-0123."
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate desktop test audio with the same Piper model used by the app.",
    )
    parser.add_argument(
        "--model-dir", required=True, help="Directory containing the Piper model files."
    )
    parser.add_argument(
        "--output",
        default="piper-desktop-test.wav",
        help="Output WAV file path.",
    )
    parser.add_argument("--text", default=DEFAULT_TEXT, help="Text to synthesize.")
    parser.add_argument(
        "--speed", type=float, default=1.0, help="Speech speed multiplier."
    )
    parser.add_argument("--speaker-id", type=int, default=0, help="Speaker ID.")
    parser.add_argument(
        "--num-threads", type=int, default=2, help="CPU threads for inference."
    )
    return parser.parse_args()


def main() -> int:
    import sherpa_onnx  # pyright: ignore[reportMissingImports]
    import soundfile as sf  # pyright: ignore[reportMissingImports]

    args = parse_args()
    model_dir = Path(args.model_dir).expanduser().resolve()

    model_path = model_dir / "en_US-ryan-medium.onnx"
    tokens_path = model_dir / "tokens.txt"
    data_dir = model_dir / "espeak-ng-data"

    missing_paths = [
        str(path) for path in (model_path, tokens_path, data_dir) if not path.exists()
    ]
    if missing_paths:
        raise FileNotFoundError(f"Missing required Piper files: {missing_paths}")

    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(model_path),
                tokens=str(tokens_path),
                data_dir=str(data_dir),
            ),
            provider="cpu",
            num_threads=args.num_threads,
            debug=False,
        ),
        max_num_sentences=1,
    )

    if not config.validate():
        raise ValueError("Invalid sherpa-onnx TTS configuration")

    tts = sherpa_onnx.OfflineTts(config)

    start_time = time.time()
    audio = tts.generate(args.text, sid=args.speaker_id, speed=args.speed)
    elapsed_seconds = time.time() - start_time

    if len(audio.samples) == 0:
        raise RuntimeError("TTS generation returned empty audio")

    output_path = Path(args.output).expanduser().resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(output_path, audio.samples, samplerate=audio.sample_rate, subtype="PCM_16")

    audio_duration = len(audio.samples) / audio.sample_rate
    real_time_factor = elapsed_seconds / audio_duration

    print(f"Saved WAV: {output_path}")
    print(f"Sample rate: {audio.sample_rate}")
    print(f"Elapsed seconds: {elapsed_seconds:.3f}")
    print(f"Audio duration: {audio_duration:.3f}")
    print(f"RTF: {real_time_factor:.3f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
