"""
DreamDocs API client library.

Simple HTTP client for DreamDocs API v2.
"""

import time
import uuid
from pathlib import Path

import requests


class DocsClient:
    """Simple HTTP client for DreamDocs API v2."""

    def __init__(self, host: str):
        self.host = host.rstrip('/')
        self.session = requests.Session()
        self.access_token = None

    def _url(self, path: str) -> str:
        return f"{self.host}{path}"

    def _headers(self) -> dict:
        headers = {"Content-Type": "application/json"}
        if self.access_token:
            headers["Authorization"] = f"Bearer {self.access_token}"
        return headers

    def login(self, username: str, password: str) -> dict:
        """Authenticate and store JWT token."""
        response = self.session.post(
            self._url('/api/v2/user/login/'),
            json={"username": username, "password": password},
            timeout=30,
        )
        response.raise_for_status()
        result = response.json()
        self.access_token = result.get("access_token") or result.get("access", "").replace("Bearer ", "")
        return result

    # --- File & Document ---

    def upload_file(self, project_id: int, file_path: Path, folder: str = '/') -> dict:
        """Upload file to project and return file info with 'file_uuid'."""
        with open(file_path, 'rb') as f:
            response = self.session.post(
                self._url('/api/v2/file/'),
                headers={"Authorization": f"Bearer {self.access_token}"},
                data={
                    'docset_id': str(project_id),
                    'folder': folder,
                },
                files={'file': (file_path.name, f)},
                timeout=120,
            )
        response.raise_for_status()
        return response.json()

    def get_file(self, file_uuid: str) -> dict:
        """Get file info including processing status."""
        response = self.session.get(
            self._url(f'/api/v2/file/{file_uuid}/'),
            headers=self._headers(),
            timeout=30,
        )
        response.raise_for_status()
        return response.json()

    def get_file_docs(self, file_uuid: str) -> dict:
        """Get documents extracted from file (split results)."""
        response = self.session.get(
            self._url(f'/api/v2/file/{file_uuid}/split/'),
            headers=self._headers(),
            timeout=30,
        )
        response.raise_for_status()
        return response.json()

    def get_doc(self, doc_uuid: str, include: str = None) -> dict:
        """Get document with recognized fields."""
        params = {}
        if include:
            params['include'] = include
        response = self.session.get(
            self._url(f'/api/v2/doc/{doc_uuid}/'),
            headers=self._headers(),
            params=params,
            timeout=30,
        )
        response.raise_for_status()
        return response.json()

    def list_documents(self, filters: dict = None, limit: int = None, offset: int = None) -> list:
        """List documents with optional filters."""
        params = {}
        if limit:
            params['limit'] = limit
        if offset:
            params['offset'] = offset
        response = self.session.post(
            self._url('/api/v2/doc/'),
            headers=self._headers(),
            params=params,
            json=filters or {},
            timeout=30,
        )
        response.raise_for_status()
        data = response.json()
        return data.get('results', data) if isinstance(data, dict) else data

    # --- Download ---

    def download_pdf(self, doc_uuid: str, output_path: Path) -> Path:
        """Download PDF for a document."""
        response = self.session.get(
            self._url(f'/api/v2/doc/{doc_uuid}/download/'),
            headers=self._headers(),
            timeout=120,
            stream=True,
        )
        response.raise_for_status()
        output_path.write_bytes(response.content)
        return output_path

    # --- Compare ---

    def submit_compare(self, file1_uuid: str, file2_uuid: str,
                       compare_params: dict = None) -> dict:
        """Submit compare task via /api/v2/run/compare/."""
        task_uuid = str(uuid.uuid4())
        response = self.session.post(
            self._url('/api/v2/run/compare/'),
            headers=self._headers(),
            json={
                'task_uuid': task_uuid,
                'file1_uuid': file1_uuid,
                'file2_uuid': file2_uuid,
                'compare_params': compare_params or {},
            },
            timeout=30,
        )
        response.raise_for_status()
        return response.json()

    def get_compare_result(self, task_uuid: str, wait: int = 0) -> dict:
        """Get compare result by task UUID."""
        response = self.session.get(
            self._url(f'/api/v2/run/compare/{task_uuid}/'),
            headers=self._headers(),
            params={'wait': wait},
            timeout=120 if wait else 30,
        )
        response.raise_for_status()
        return response.json()

    # --- Setup (Workspace, Docset, Doctype) ---

    def list_workspaces(self) -> list:
        """List available workspaces."""
        response = self.session.get(
            self._url('/api/v2/workspace/'),
            headers=self._headers(),
            timeout=30,
        )
        response.raise_for_status()
        data = response.json()
        return data.get('results', data) if isinstance(data, dict) else data

    def list_docsets(self) -> list:
        """List available docsets (projects)."""
        response = self.session.get(
            self._url('/api/v2/docset/'),
            headers=self._headers(),
            timeout=30,
        )
        response.raise_for_status()
        data = response.json()
        return data.get('results', data) if isinstance(data, dict) else data

    def create_docset(self, name: str, description: str = "") -> dict:
        """Create a new docset (project)."""
        response = self.session.post(
            self._url('/api/v2/docset/'),
            headers=self._headers(),
            json={
                'name': name,
                'description': description,
            },
            timeout=30,
        )
        response.raise_for_status()
        return response.json()

    def list_doctypes(self) -> list:
        """List available doctypes."""
        response = self.session.get(
            self._url('/api/v2/doctype/'),
            headers=self._headers(),
            timeout=30,
        )
        response.raise_for_status()
        data = response.json()
        return data.get('results', data) if isinstance(data, dict) else data

    def create_doctype(self, name: str, description: str = "", lang: str = "en") -> dict:
        """Create a new doctype."""
        response = self.session.post(
            self._url('/api/v2/doctype/'),
            headers=self._headers(),
            json={
                'name': name,
                'description': description,
                'lang': lang,
            },
            timeout=30,
        )
        response.raise_for_status()
        return response.json()


def wait_for_file(client: DocsClient, file_uuid: str, timeout: int = 300, poll_interval: int = 4) -> dict:
    """Poll file status until documents are ready or failed."""
    start = time.time()
    while time.time() - start < timeout:
        file_info = client.get_file(file_uuid)
        status = file_info.get('status', '')
        if status in ('Ready', 'HasDocs', 'Failed'):
            return file_info
        print(f"  File status: {status} (waiting)...", flush=True)
        time.sleep(poll_interval)
    raise TimeoutError(f"File {file_uuid} did not finish processing within {timeout} seconds")


def wait_for_compare(client: DocsClient, task_uuid: str, timeout: int = 300, poll_interval: int = 4) -> dict:
    """Poll compare task until completed or failed."""
    start = time.time()
    while time.time() - start < timeout:
        result = client.get_compare_result(task_uuid)
        status = result.get('status', '')
        if status in ('SUCCESS', 'FAILED'):
            return result
        print(f"  Compare status: {status} (waiting)...", flush=True)
        time.sleep(poll_interval)
    raise TimeoutError(f"Compare task {task_uuid} did not finish within {timeout} seconds")
