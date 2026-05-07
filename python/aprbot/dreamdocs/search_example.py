#!/usr/bin/env python3
"""
DreamDocs document search example.

Searches documents via the DreamDocs API.

Usage:
    python search_example.py --project-id 1 --status Ready

Options:
    --project-id      Filter by project (docset) ID
    --status          Filter by status name
    --doctype-id      Filter by doctype ID
    --folder          Filter by folder path
    --host            DreamDocs service URL (default: http://test.dreamdocs.ru:9000 or DOCS_HOST env)
    -u, --username    Username for JWT login (default: robot or DOCS_USER env)
    -p, --password    Password for JWT login (default: robotrobot or DOCS_PASS env)
"""

import argparse
import json
import os
import sys

from dreamdocs_client import DocsClient


def parse_args():
    parser = argparse.ArgumentParser(description='DreamDocs search example')
    parser.add_argument('--project-id', type=int, help='Project ID filter')
    parser.add_argument('--status', help='Status filter')
    parser.add_argument('--doctype-id', type=int, help='Doctype ID filter')
    parser.add_argument('--folder', help='Folder filter')
    parser.add_argument('--host', default=os.environ.get('DOCS_HOST'))
    parser.add_argument('-u', '--username', default=os.environ.get('DOCS_USER'))
    parser.add_argument('-p', '--password', default=os.environ.get('DOCS_PASS'))
    return parser.parse_args()


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
    client = DocsClient(args.host)

    print("1. Authenticating...")
    client.login(args.username, args.password)

    filters = {}
    if args.project_id:
        filters['docset_id'] = args.project_id
    if args.status:
        filters['statuses'] = [args.status]
    if args.doctype_id:
        filters['doctype_id'] = args.doctype_id
    if args.folder:
        filters['folder'] = args.folder

    print(f"\n2. Searching documents...")
    results = client.list_documents(filters, limit=20)
    print(f"   Found {len(results)} documents (showing first 20)")
    for doc in results:
        print(f"   - {doc.get('docname')} (status: {doc.get('status')}, type: {doc.get('doctype', {}).get('name')})")

    # 3. List docsets
    print("\n3. Available projects (docsets):")
    docsets = client.list_docsets()
    for ds in docsets:
        print(f"   - {ds.get('name')} (ID: {ds.get('id')})")


if __name__ == "__main__":
    main()
