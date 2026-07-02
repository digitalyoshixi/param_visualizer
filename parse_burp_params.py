#!/usr/bin/env python3
"""
Parse Burp Suite XML export files and collect all unique HTTP body parameter names.

For each <item> under <items>, decodes the <request> (base64 when indicated),
extracts the request body, and parses parameter names from common body formats:
  - application/x-www-form-urlencoded
  - application/json
  - multipart/form-data
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import sys
import xml.etree.ElementTree as ET
from email import policy
from email.parser import BytesParser
from typing import Iterable
from urllib.parse import parse_qsl


def decode_request(raw: str | None, is_base64: bool) -> bytes:
    if not raw:
        return b""
    text = raw.strip()
    if is_base64:
        return base64.b64decode(text)
    return text.encode("utf-8", errors="replace")


def split_headers_body(request_bytes: bytes) -> tuple[bytes, bytes]:
    for separator in (b"\r\n\r\n", b"\n\n"):
        if separator in request_bytes:
            headers, body = request_bytes.split(separator, 1)
            return headers, body
    return request_bytes, b""


def parse_content_type(headers: bytes) -> tuple[str, dict[str, str]]:
    header_text = headers.decode("utf-8", errors="replace")
    content_type = ""
    extra: dict[str, str] = {}

    for line in header_text.splitlines():
        if ":" not in line:
            continue
        name, value = line.split(":", 1)
        if name.strip().lower() != "content-type":
            continue
        parts = [part.strip() for part in value.split(";")]
        if parts:
            content_type = parts[0].lower()
        for part in parts[1:]:
            if "=" in part:
                key, val = part.split("=", 1)
                extra[key.strip().lower()] = val.strip().strip('"')
        break

    return content_type, extra


def collect_json_keys(value: object, prefix: str = "") -> set[str]:
    keys: set[str] = set()

    if isinstance(value, dict):
        for key, nested in value.items():
            full_key = f"{prefix}.{key}" if prefix else str(key)
            keys.add(full_key)
            keys.update(collect_json_keys(nested, full_key))
    elif isinstance(value, list):
        for index, nested in enumerate(value):
            full_key = f"{prefix}[{index}]"
            keys.update(collect_json_keys(nested, full_key))

    return keys


def extract_urlencoded_params(body: bytes) -> set[str]:
    text = body.decode("utf-8", errors="replace").strip()
    if not text:
        return set()
    return {name for name, _ in parse_qsl(text, keep_blank_values=True)}


def extract_json_params(body: bytes) -> set[str]:
    text = body.decode("utf-8", errors="replace").strip()
    if not text:
        return set()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        return set()
    return collect_json_keys(payload)


def extract_multipart_params(body: bytes, content_type_extra: dict[str, str]) -> set[str]:
    boundary = content_type_extra.get("boundary")
    if not boundary:
        return set()

    # Build a minimal MIME message so the stdlib parser can read parts reliably.
    mime_bytes = (
        f"Content-Type: multipart/form-data; boundary={boundary}\r\n\r\n".encode()
        + body
    )
    message = BytesParser(policy=policy.default).parsebytes(mime_bytes)

    names: set[str] = set()
    if not message.is_multipart():
        return names

    for part in message.iter_parts():
        disposition = part.get("Content-Disposition", "")
        match = re.search(r'name="([^"]+)"', disposition)
        if match:
            names.add(match.group(1))

    return names


def extract_body_params(request_bytes: bytes) -> set[str]:
    headers, body = split_headers_body(request_bytes)
    if not body.strip():
        return set()

    content_type, extra = parse_content_type(headers)

    if "application/json" in content_type or content_type.endswith("+json"):
        return extract_json_params(body)
    if "multipart/form-data" in content_type:
        return extract_multipart_params(body, extra)
    if (
        "application/x-www-form-urlencoded" in content_type
        or not content_type
        or content_type == "text/plain"
    ):
        # Burp exports often use urlencoded bodies; fall back when Content-Type is missing.
        return extract_urlencoded_params(body)

    # Unknown content type: try urlencoded first, then JSON.
    params = extract_urlencoded_params(body)
    if params:
        return params
    return extract_json_params(body)


def parse_burp_xml(path: str) -> set[str]:
    tree = ET.parse(path)
    root = tree.getroot()

    if root.tag != "items":
        raise ValueError(f"Expected root element <items>, got <{root.tag}>")

    all_params: set[str] = set()

    for item in root.findall("item"):
        request_el = item.find("request")
        if request_el is None or request_el.text is None:
            continue

        is_base64 = request_el.get("base64", "false").lower() == "true"
        request_bytes = decode_request(request_el.text, is_base64)
        all_params.update(extract_body_params(request_bytes))

    return all_params


def write_output(params: Iterable[str], output_path: str | None) -> None:
    sorted_params = sorted(params)
    if output_path:
        with open(output_path, "w", encoding="utf-8") as handle:
            for name in sorted_params:
                handle.write(f"{name}=a&")
    else:
        for name in sorted_params:
            print(name)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Extract unique HTTP body parameter names from a Burp Suite XML export."
    )
    parser.add_argument("input", help="Path to Burp Suite XML export file")
    parser.add_argument(
        "-o",
        "--output",
        help="Optional file to write parameter names (one per line). Prints to stdout if omitted.",
    )
    args = parser.parse_args()

    try:
        params = parse_burp_xml(args.input)
    except (ET.ParseError, ValueError, base64.binascii.Error) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1

    write_output(params, args.output)
    print(f"\n# Total unique body parameters: {len(params)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
