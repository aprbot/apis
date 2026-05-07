#!/usr/bin/env python3
"""
DreamDocs setup example.

Creates workspace, docset (project), and doctype if they don't exist.

Usage:
    python setup_example.py --create-docset "My Project"

Options:
    --create-docset   Name of docset to create (optional)
    --create-doctype  Name of doctype to create (optional)
    --host            DreamDocs service URL (default: http://test.dreamdocs.ru:9000 or DOCS_HOST env)
    -u, --username    Username for JWT login (default: robot or DOCS_USER env)
    -p, --password    Password for JWT login (default: robotrobot or DOCS_PASS env)
"""

import argparse
import os
import sys

from dreamdocs_client import DocsClient


def parse_args():
    parser = argparse.ArgumentParser(description='DreamDocs setup example')
    parser.add_argument('--create-docset', help='Docset name to create')
    parser.add_argument('--create-doctype', help='Doctype name to create')
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

    # 2. List workspaces
    print("\n2. Workspaces:")
    workspaces = client.list_workspaces()
    for ws in workspaces:
        print(f"   - {ws.get('name')} (slug: {ws.get('slug')})")

    # 3. List docsets
    print("\n3. Projects (docsets):")
    docsets = client.list_docsets()
    for ds in docsets:
        print(f"   - {ds.get('name')} (ID: {ds.get('id')})")

    # 4. Create docset if requested
    if args.create_docset:
        print(f"\n4. Creating docset: {args.create_docset}")
        try:
            new_ds = client.create_docset(args.create_docset)
            print(f"   Created: {new_ds.get('name')} (ID: {new_ds.get('id')})")
        except Exception as e:
            print(f"   Error: {e}")

    # 5. List doctypes
    print("\n5. Doctypes:")
    doctypes = client.list_doctypes()
    for dt in doctypes:
        print(f"   - {dt.get('name')} (ID: {dt.get('id')})")

    # 6. Create doctype if requested
    if args.create_doctype:
        print(f"\n6. Creating doctype: {args.create_doctype}")
        try:
            new_dt = client.create_doctype(args.create_doctype)
            print(f"   Created: {new_dt.get('name')} (ID: {new_dt.get('id')})")
        except Exception as e:
            print(f"   Error: {e}")


if __name__ == "__main__":
    main()
