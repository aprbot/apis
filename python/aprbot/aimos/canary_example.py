"""Transcribe speech or translate it into English with NVIDIA Canary."""

import argparse
import mimetypes
import os
from pathlib import Path

import requests


def transcribe(host, key, path, language, *, translate=False):
    operation = "translations" if translate else "transcriptions"
    with path.open("rb") as audio:
        response = requests.post(
            f"{host.rstrip('/')}/v1/audio/{operation}",
            headers={"Authorization": f"Bearer {key}"},
            data={"model": "nvidia/canary-1b-v2", "language": language},
            files={"file": (path.name, audio, mimetypes.guess_type(path.name)[0])},
            timeout=120,
        )
    response.raise_for_status()
    return response.json()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audio", type=Path)
    parser.add_argument("--language", required=True, help="Input language code")
    parser.add_argument("--host", required=True, help="AIMOS or DreamToken origin")
    parser.add_argument("--translate", action="store_true", help="Translate speech into English")
    args = parser.parse_args()
    print(transcribe(args.host, os.environ["CANARY_API_KEY"], args.audio, args.language,
                     translate=args.translate)["text"])
