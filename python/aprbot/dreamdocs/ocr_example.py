#!/usr/bin/env python3
"""
DreamDocs OCR example.

Uploads a file via the normal document processing pipeline,
waits for completion, then extracts the OCR text layer and PDF.

Usage:
    python ocr_example.py -i /path/to/image.png --project-id 1 -o /tmp/ocr_out

Options:
    -i, --input      Input image/PDF file (required)
    -o, --output     Output directory for results (required)
    --project-id     Project ID for upload (default: 1)
    --host           DreamDocs URL (default: http://test.dreamdocs.ru:9000 or DOCS_HOST env)
    -u, --user       Username (default: robot or DOCS_USER env)
    -p, --password   Password (default: robotrobot or DOCS_PASS env)
"""

import argparse
import os
import sys
from pathlib import Path

from dreamdocs_client import DocsClient, wait_for_file


def parse_args():
    parser = argparse.ArgumentParser(description="DreamDocs OCR example")
    parser.add_argument("-i", "--input", required=True, help="Input image/PDF file")
    parser.add_argument("-o", "--output", required=True, help="Output directory")
    parser.add_argument("--project-id", type=int, default=1)
    parser.add_argument("--host", default=os.environ.get("DOCS_HOST"))
    parser.add_argument("-u", "--user", default=os.environ.get("DOCS_USER"))
    parser.add_argument("-p", "--password", default=os.environ.get("DOCS_PASS"))
    return parser.parse_args()


def main():
    args = parse_args()
    if not args.host:
        print("Error: --host or DOCS_HOST env var is required")
        sys.exit(1)
    if not args.user:
        print("Error: -u/--user or DOCS_USER env var is required")
        sys.exit(1)
    if not args.password:
        print("Error: -p/--password or DOCS_PASS env var is required")
        sys.exit(1)
    file_path = Path(args.input)
    if not file_path.exists():
        print(f"Error: File not found: {file_path}")
        sys.exit(1)

    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    client = DocsClient(args.host)

    print("1. Authenticating...")
    client.login(args.user, args.password)

    print(f"\n2. Uploading file to project {args.project_id}...")
    upload_result = client.upload_file(args.project_id, file_path)
    file_uuid = upload_result["file_uuid"]
    print(f"   File UUID: {file_uuid}")

    print("\n3. Waiting for processing to complete...")
    file_info = wait_for_file(client, file_uuid, timeout=300)
    print(f"   Status: {file_info['status']}")

    docs = file_info.get("docs", [])
    if not docs:
        print("\nError: No documents found after processing")
        sys.exit(1)

    doc_uuid = docs[0]["doc_uuid"]
    print(f"\n4. Retrieving OCR text for doc {doc_uuid}...")
    doc = client.get_doc(doc_uuid, include="ocr_text")
    ocr_text = doc.get("ocr_text") or ""
    print(f"   OCR text length: {len(ocr_text)} chars")

    text_path = out_dir / "ocr_text.txt"
    text_path.write_text(ocr_text, encoding="utf-8")
    print(f"   Saved text -> {text_path}")

    pdf_url = doc.get("pdf_url")
    if pdf_url:
        print(f"\n5. Downloading PDF...")
        pdf_path = out_dir / "result.pdf"
        client.download_pdf(doc_uuid, pdf_path)
        print(f"   Saved PDF -> {pdf_path} ({pdf_path.stat().st_size} bytes)")

    print("\nDone.")


if __name__ == "__main__":
    main()
