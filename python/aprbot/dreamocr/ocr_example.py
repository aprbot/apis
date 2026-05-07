#!/usr/bin/env python3
"""
DreamOCR API client example.

Usage:
    python ocr_example.py -i <input.file> -o <output.dir>/ [-p '{"lang":"rus"}']

Options:
    -i, --input     File to recognize (required)
    -o, --output    Output directory for results (required)
    -p, --params    OCR parameters as JSON string (default: {"ocr":"tesseract","lang":"rus"})
    --host          DreamOCR service URL (default: http://localhost:9900 or OCR_HOST env)
    -k, --key       API key (default: demo-key or OCR_KEY env)
"""

import argparse
import json
import os
import sys
from pathlib import Path

from dreamocr_client import OcrClient, wait_for_task


def parse_args():
    parser = argparse.ArgumentParser(
        description='DreamOCR API client example: upload, recognize, wait, download results.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument('-i', '--input', required=True, help='Input file to recognize')
    parser.add_argument('-o', '--output', required=True, help='Output directory for results')
    parser.add_argument(
        '-p', '--params',
        default='{"ocr":"tesseract","lang":"rus"}',
        help='OCR parameters as JSON string (default: %(default)s)',
    )
    parser.add_argument(
        '--host',
        default=os.environ.get('OCR_HOST'),
        help='DreamOCR service URL',
    )
    parser.add_argument(
        '-k', '--key',
        default=os.environ.get('OCR_KEY'),
        help='API key',
    )
    return parser.parse_args()


def main():
    args = parse_args()
    if not args.host:
        print("Error: --host or OCR_HOST env var is required")
        sys.exit(1)
    if not args.key:
        print("Error: -k/--key or OCR_KEY env var is required")
        sys.exit(1)

    file_path = Path(args.input)
    if not file_path.exists():
        print(f"Error: File not found: {file_path}")
        sys.exit(1)

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    try:
        params = json.loads(args.params)
    except json.JSONDecodeError as e:
        print(f"Error: Invalid JSON in params: {e}")
        sys.exit(1)

    client = OcrClient(args.host, args.key)

    print(f"Service: {args.host}")
    print(f"File: {file_path}")
    print(f"Output: {output_dir}")
    print(f"Params: {params}")

    # 1. Upload
    print("\n1. Uploading file...")
    upload_result = client.upload(file_path)
    upload_id = upload_result['upload_id']
    print(f"   Upload ID: {upload_id}")

    # 2. Recognize
    print("\n2. Starting recognition...")
    recognize_result = client.recognize(upload_id=upload_id, priority=0, **params)
    task_id = int(recognize_result['task_id'])
    print(f"   Task ID: {task_id}")

    # 3. Wait for completion
    print("\n3. Waiting for recognition to complete...")
    final_status = wait_for_task(client, task_id, timeout=300)
    print("   Recognition complete!")

    # 4. Download results
    print("\n4. Downloading results...")
    docs = final_status.get('docs', {})
    for fmt, path in docs.items():
        if not path:
            continue
        ext = fmt if fmt != 'txt' else 'txt'
        out_file = output_dir / f"result.{ext}"
        if client.download(path, out_file):
            print(f"   [{fmt}] -> {out_file} ({out_file.stat().st_size} bytes)")
        else:
            print(f"   [{fmt}] -> download failed")

    print(f"\nDone. Results saved to: {output_dir.absolute()}")


if __name__ == "__main__":
    main()
