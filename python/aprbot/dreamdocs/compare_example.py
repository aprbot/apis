#!/usr/bin/env python3
"""
DreamDocs document compare example.

Usage:
    python compare_example.py --file1 <path1> --file2 <path2> --project-id 1

Options:
    --file1          First file path (required)
    --file2          Second file path (required)
    --project-id     Project ID for upload (default: 1)
    --level          Compare level: text or fields (default: text)
    --mode           Compare mode: all, differences, same (default: all)
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
    parser = argparse.ArgumentParser(description="DreamDocs compare example")
    parser.add_argument("--file1", required=True, help="First file path")
    parser.add_argument("--file2", required=True, help="Second file path")
    parser.add_argument("--project-id", type=int, default=1, help="Project ID")
    parser.add_argument("--level", default="text", choices=["text", "fields"], help="Compare level")
    parser.add_argument("--mode", default="all", choices=["all", "differences", "same"], help="Compare mode")
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

    file1_path = Path(args.file1)
    file2_path = Path(args.file2)
    if not file1_path.exists():
        print(f"Error: file1 not found: {file1_path}")
        sys.exit(1)
    if not file2_path.exists():
        print(f"Error: file2 not found: {file2_path}")
        sys.exit(1)

    client = DocsClient(args.host)

    print("1. Authenticating...")
    client.login(args.user, args.password)

    print(f"\n2. Uploading files to project {args.project_id}...")
    result1 = client.upload_file(args.project_id, file1_path)
    file1_uuid = result1["file_uuid"]
    print(f"   File 1 uploaded: {file1_uuid} ({file1_path.name})")

    result2 = client.upload_file(args.project_id, file2_path)
    file2_uuid = result2["file_uuid"]
    print(f"   File 2 uploaded: {file2_uuid} ({file2_path.name})")

    print("\n3. Waiting for files to be ready...")
    wait_for_file(client, file1_uuid)
    print(f"   File 1 ready")
    wait_for_file(client, file2_uuid)
    print(f"   File 2 ready")

    print(f"\n4. Comparing files (level={args.level}, mode={args.mode})...")
    compare_params = {"level": args.level, "mode": args.mode}
    result = client.submit_compare(file1_uuid, file2_uuid, compare_params=compare_params)
    task_uuid = result.get("task_uuid")
    print(f"   Task UUID: {task_uuid}")

    print("\n5. Getting comparison result...")
    final = client.get_compare_result(task_uuid, wait=1)
    status = final.get("status")
    print(f"   Status: {status}")

    if status == "SUCCESS":
        comparison = final.get("comparison", {})
        items = comparison.get("items", [])
        print(f"   Differences found: {len(items)}")
        for idx, item in enumerate(items, 1):
            print(f"\n   Difference {idx}:")
            for line in item.get("diff", []):
                print(f"      {line.rstrip()}")
    elif final.get("error_message"):
        print(f"   Error: {final['error_message']}")

    print("\nDone.")


if __name__ == "__main__":
    main()
