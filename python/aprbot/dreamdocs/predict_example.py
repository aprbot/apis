#!/usr/bin/env python3
"""
DreamDocs API client example.

Usage:
    python predict_example.py -i <input.file> -o <output.dir>/ --project-id 1

Options:
    -i, --input       File to upload and recognize (required)
    -o, --output      Output directory for results (required)
    --host            DreamDocs service URL (default: http://test.dreamdocs.ru:9000 or DOCS_HOST env)
    -u, --username    Username for JWT login (default: robot or DOCS_USER env)
    -p, --password    Password for JWT login (default: robotrobot or DOCS_PASS env)
    --project-id      Project (docset) ID to upload to (required)
    --folder          Target folder in project (default: /)
"""

import argparse
import json
import os
import sys
from pathlib import Path

from dreamdocs_client import DocsClient, wait_for_file


def parse_args():
    parser = argparse.ArgumentParser(
        description='DreamDocs API client example: upload, wait, predict/extract fields.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument('-i', '--input', required=True, help='Input file to upload')
    parser.add_argument('-o', '--output', required=True, help='Output directory for results')
    parser.add_argument(
        '--host',
        default=os.environ.get('DOCS_HOST'),
        help='DreamDocs service URL',
    )
    parser.add_argument(
        '-u', '--username',
        default=os.environ.get('DOCS_USER'),
        help='Username for JWT login',
    )
    parser.add_argument(
        '-p', '--password',
        default=os.environ.get('DOCS_PASS'),
        help='Password for JWT login',
    )
    parser.add_argument('--project-id', type=int, help='Project (docset) ID')
    parser.add_argument('--folder', default='/', help='Target folder (default: /)')
    parser.add_argument(
        '--setup',
        action='store_true',
        help='Auto-create a docset (project) if none exists and --project-id is not given',
    )
    return parser.parse_args()


def _ensure_docset(client, project_id, setup):
    """Return docset ID, creating one if needed when setup=True."""
    if project_id:
        return project_id

    docsets = client.list_docsets()
    if isinstance(docsets, dict):
        docsets = docsets.get('results', [])

    if docsets:
        ds = docsets[0]
        print(f"   Using existing docset: {ds.get('name')} (ID: {ds.get('id')})")
        return ds.get('id')

    if not setup:
        raise SystemExit("No docsets found. Use --setup to create one or pass --project-id.")

    print("   No docsets found. Creating 'Demo Project' docset...")
    ds = client.create_docset("Demo Project", "Auto-created by API example")
    print(f"   Created docset: {ds.get('name')} (ID: {ds.get('id')})")
    return ds.get('id')


def main():
    args = parse_args()
    if not args.host:
        print("Error: --host or DOCS_HOST env var is required")
        sys.exit(1)
    if not args.username:
        print("Error: -u/--username or DOCS_USER env var is required")
        sys.exit(1)
    if not args.password:
        print("Error: -p/--password or DOCS_PASS env var is required")
        sys.exit(1)

    if not args.setup and not args.project_id:
        print("Error: --project-id is required (or use --setup to auto-create).")
        sys.exit(1)

    file_path = Path(args.input)
    if not file_path.exists():
        print(f"Error: File not found: {file_path}")
        sys.exit(1)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    client = DocsClient(args.host)

    # 1. Authenticate
    print("1. Authenticating...")
    login_result = client.login(args.username, args.password)
    print(f"   Logged in as: {login_result.get('user', {}).get('username', 'unknown')}")

    # 2. Ensure docset
    print("\n2. Checking docset (project)...")
    project_id = _ensure_docset(client, args.project_id, args.setup)

    # 3. Upload
    print(f"\n3. Uploading file to project {project_id}...")
    upload_result = client.upload_file(project_id, file_path, folder=args.folder)
    file_uuid = upload_result['file_uuid']
    print(f"   File UUID: {file_uuid}")

    # 4. Wait for processing
    print("\n4. Waiting for processing to complete...")
    file_info = wait_for_file(client, file_uuid, timeout=300)
    status = file_info.get('status', '')
    if status == 'Failed':
        print(f"   Processing failed!")
        sys.exit(1)
    print("   Processing complete!")

    # 5. Get documents and fields
    print("\n5. Retrieving documents and fields...")
    split_result = client.get_file_docs(file_uuid)
    docs = split_result.get('docs', [])
    if not docs:
        print("   No documents found in file.")
        return

    all_results = []
    for idx, doc in enumerate(docs, 1):
        doc_uuid = doc['doc_uuid']
        doc_info = client.get_doc(doc_uuid)
        doc_name = doc_info.get('docname', f'doc_{idx}')
        print(f"\n   Document {idx}: {doc_name}")
        print(f"   Type: {doc_info.get('doctype', {}).get('name', 'unknown')}")
        print(f"   Status: {doc_info.get('status', 'unknown')}")

        fields = []
        for block in doc_info.get('data', []):
            for row in block.get('fields', []):
                for field in row:
                    name = field.get('name', '')
                    value = field.get('value_norm', '') or field.get('value', '')
                    score = field.get('score', 0)
                    valid = field.get('valid', -1)
                    print(f"      {name}: {value} (score={score:.2f}, valid={valid})")
                    fields.append({
                        'name': name,
                        'value': value,
                        'score': score,
                        'valid': valid,
                    })

        all_results.append({
            'doc_uuid': doc_uuid,
            'docname': doc_name,
            'doctype': doc_info.get('doctype', {}).get('name'),
            'status': doc_info.get('status'),
            'fields': fields,
        })

    # Save JSON result
    result_file = output_dir / 'result.json'
    result_file.write_text(json.dumps(all_results, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"\n6. Results saved to: {result_file}")


if __name__ == "__main__":
    main()
