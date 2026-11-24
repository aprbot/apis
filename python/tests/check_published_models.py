"""Live checks for the published models. See docs/examples/canary-dolphin.md."""

import json
import os
from pathlib import Path
import subprocess
import sys


PYTHON = Path(__file__).resolve().parents[1] / "aprbot"


def test_canary_transcribes_reference_speech():
    result = subprocess.run(
        [sys.executable, str(PYTHON / "aimos/canary_example.py"),
         os.environ["CANARY_AUDIO"], "--language", os.environ["CANARY_LANGUAGE"],
         "--host", os.environ["AIMOS_HOST"]],
        check=True, capture_output=True, text=True, timeout=130,
    )
    expected = os.environ["CANARY_EXPECTED_TEXT"].strip().casefold()
    assert expected and expected in result.stdout.casefold()


def test_dolphin_recognizes_reference_document(tmp_path, monkeypatch):
    monkeypatch.syspath_prepend(str(PYTHON / "dreamocr"))
    from dreamocr_client import OcrClient, wait_for_task

    base = os.environ["AIMOS_HOST"].rstrip("/") + "/api/v2/models/aprbot/dolphin/dreamocr"
    client = OcrClient(base, os.environ["DOLPHIN_API_KEY"])
    source = Path(os.environ["DOLPHIN_DOCUMENT"])
    doctype = os.environ["DOLPHIN_DOCTYPE"]
    upload = client.upload(source)
    task = client.recognize(upload["upload_id"], doctype=doctype)
    result = wait_for_task(client, int(task["task_id"]), timeout=600)
    assert result["processed_pages"] > 0
    artifact = tmp_path / "result.json"
    assert client.download(result["json"], artifact)
    params = json.loads(artifact.read_bytes())["meta"]["params"]
    assert params["dolphin"]["document_type"]
    assert params["structured"]["selection_status"] == "match"
    expected = os.environ["DOLPHIN_EXPECTED_TEXT"].strip().casefold()
    values = params["dolphin"]["fields"].values()
    assert expected and any(expected in str(value).casefold() for value in values)
