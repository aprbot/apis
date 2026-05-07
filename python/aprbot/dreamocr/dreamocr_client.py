"""
DreamOCR API client library.

Simple HTTP client for DreamOCR service.
"""

import json
from enum import IntEnum
from pathlib import Path

import requests


class OcrStatus(IntEnum):
    LOADING = 10
    PAUSED = 15
    WORKING = 20
    FAILED = 30
    READY = 40
    ARCHIVED = 50


class OcrClient:
    """Simple HTTP client for DreamOCR service."""

    def __init__(self, host: str, key: str):
        self.host = host.rstrip('/')
        self.key = key

    def _url(self, path: str) -> str:
        return f"{self.host}{path}"

    def upload(self, file_path: Path) -> dict:
        """Upload file and return upload info with 'upload_id'."""
        with open(file_path, 'rb') as f:
            response = requests.post(
                self._url('/api/v1/upload/'),
                data={'key': self.key},
                files={'file': (file_path.name, f)},
                timeout=60,
            )
        if response.status_code != 200:
            raise RuntimeError(f"Upload failed: HTTP {response.status_code}: {response.text}")
        result = response.json()
        if 'error' in result:
            raise RuntimeError(f"Upload failed: {result['error']}")
        return result

    def recognize(self, upload_id: str, priority: int = 0, **params) -> dict:
        """Start recognition and return task info with 'task_id'."""
        response = requests.post(
            self._url('/api/v1/recognize/'),
            data={
                'key': self.key,
                'upload_id': upload_id,
                'params': json.dumps(params),
                'priority': priority,
            },
            timeout=30,
        )
        if response.status_code != 200:
            raise RuntimeError(f"Recognize failed: HTTP {response.status_code}: {response.text}")
        result = response.json()
        if 'error' in result:
            raise RuntimeError(f"Recognize failed: {result['error']}")
        return result

    def status(self, task_ids: list) -> list:
        """Get status for task ids. Returns list of status dicts."""
        response = requests.post(
            self._url('/api/v1/status/'),
            data={
                'key': self.key,
                'ids': json.dumps(task_ids),
            },
            timeout=30,
        )
        if response.status_code != 200:
            raise RuntimeError(f"Status failed: HTTP {response.status_code}: {response.text}")
        result = response.json()
        if 'error' in result:
            raise RuntimeError(f"Status failed: {result['error']}")
        data = result.get('data', result)
        return data if isinstance(data, list) else []

    def download(self, path: str, output_path: Path) -> bool:
        """Download a result file by relative path (e.g. '../files/...')."""
        url_path = path.lstrip('.')
        if not url_path.startswith('/'):
            url_path = '/' + url_path
        response = requests.get(
            self._url(url_path),
            params={'key': self.key},
            timeout=60,
        )
        if response.status_code == 200:
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_bytes(response.content)
            return True
        return False


def wait_for_task(client: OcrClient, task_id: int, timeout: int = 300, poll_interval: int = 4) -> dict:
    """Poll status until task completes or fails. Returns final status dict."""
    import time
    start = time.time()
    while time.time() - start < timeout:
        statuses = client.status([task_id])
        items = {s['task_id']: s for s in statuses if isinstance(s, dict) and 'task_id' in s}
        if task_id not in items:
            raise RuntimeError(f"Task {task_id} not found in status response")

        status = items[task_id]
        status_code = status.get('status', 0)

        if status_code == OcrStatus.READY:
            return status
        if status_code == OcrStatus.FAILED:
            error = status.get('error', 'Unknown error')
            raise RuntimeError(f"OCR task failed: {error}")
        if status_code >= OcrStatus.FAILED:
            error = status.get('error', 'Unknown error')
            raise RuntimeError(f"OCR task failed with status {status_code}: {error}")

        print(f"  Status: {status_code} (waiting)...", flush=True)
        time.sleep(poll_interval)

    raise TimeoutError(f"OCR task {task_id} did not complete within {timeout} seconds")
