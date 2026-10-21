#!/usr/bin/env python3
"""Read-only CRC diagnostics, not an importer or a checksum acceptance policy."""

import argparse
import binascii
import glob
import hashlib
import json
from pathlib import Path
import re


MAX_BYTES = 32 * 1024 * 1024


def crc16_arc(data):
    """CRC-16/ARC: poly 8005 (reflected A001), init/xorout zero."""
    crc = 0
    for value in data:
        crc ^= value
        for _ in range(8):
            crc = (crc >> 1) ^ (0xA001 if crc & 1 else 0)
    return crc


def inspect(data):
    # Like FTN tools, stop at DOS EOF; do not decode/re-encode directory text.
    content = data.split(b"\x1a", 1)[0]
    first_break = re.search(rb"\r\n|\r|\n", content)
    if not first_break:
        raise ValueError("Missing header line terminator")
    header = content[:first_break.start()]
    match = re.search(rb":\s*([0-9]{1,5})\s*$", header)
    if not header.startswith(b";") or not match or int(match[1]) > 65535:
        raise ValueError("Missing or invalid decimal header CRC")
    expected = int(match[1])
    body = content[first_break.end():]
    lines = re.split(rb"\r\n|\r|\n", body)
    if lines[-1] == b"":
        lines.pop()
    canonical = b"".join(line + b"\r\n" for line in lines)
    values = {
        "xmodem_raw": binascii.crc_hqx(body, 0),
        "xmodem_crlf": binascii.crc_hqx(canonical, 0),
        "arc_raw": crc16_arc(body),
        "arc_crlf": crc16_arc(canonical),
    }
    return {
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "header_crc": expected,
        "crlf_count": content.count(b"\r\n"),
        "lf_count": content.count(b"\n"),
        "cr_count": content.count(b"\r"),
        **values,
        "matches": [name for name, value in values.items() if value == expected],
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", help="Files or quoted filename globs")
    args = parser.parse_args(argv)
    paths = set()
    for pattern in args.paths:
        matches = [Path(value) for value in glob.glob(pattern) if Path(value).is_file()]
        if not matches:
            parser.error(f"No files matched: {pattern}")
        paths.update(path.resolve() for path in matches)
    result = []
    for path in sorted(paths):
        try:
            with path.open("rb") as stream:
                data = stream.read(MAX_BYTES + 1)
            if len(data) > MAX_BYTES:
                raise ValueError("File exceeds 32 MiB diagnostic limit")
            result.append({"file": str(path), **inspect(data)})
        except (OSError, ValueError) as error:
            parser.error(f"{path}: {error}")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
