#!/usr/bin/env python3
"""
MyAI / Jarvis AI — Protocol Buffer Descriptor & Type Introspection Utility
Parses complete_local_grpc_definitions.proto and dumps all services, RPC methods,
messages, and enum definitions in human-readable or JSON format.
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Dict, List


SERVICE_RE = re.compile(r"service\s+([A-Za-z0-9_]+)\s*\{([^}]*)\}", re.MULTILINE | re.DOTALL)
RPC_RE = re.compile(
    r"rpc\s+([A-Za-z0-9_]+)\s*\(\s*(stream\s+)?([A-Za-z0-9_.]+)\s*\)\s*returns\s*\(\s*(stream\s+)?([A-Za-z0-9_.]+)\s*\)"
)
ENUM_RE = re.compile(r"enum\s+([A-Za-z0-9_]+)\s*\{([^}]*)\}", re.MULTILINE | re.DOTALL)
ENUM_VAL_RE = re.compile(r"([A-Z0-9_]+)\s*=\s*(\d+)\s*;")
MESSAGE_HEADER_RE = re.compile(r"message\s+([A-Za-z0-9_]+)\s*\{")
FIELD_RE = re.compile(
    r"^\s*(repeated\s+|optional\s+)?([A-Za-z0-9_.]+)\s+([A-Za-z0-9_]+)\s*=\s*(\d+)\s*;",
    re.MULTILINE,
)


def extract_balanced_blocks(source: str, keyword: str) -> List[Dict[str, str]]:
    pattern = re.compile(rf"\b{keyword}\s+([A-Za-z0-9_]+)\s*\{{")
    results: List[Dict[str, str]] = []
    pos = 0
    while True:
        match = pattern.search(source, pos)
        if not match:
            break
        name = match.group(1)
        brace_start = match.end() - 1
        depth = 0
        idx = brace_start
        while idx < len(source):
            ch = source[idx]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    body = source[brace_start + 1 : idx]
                    results.append({"name": name, "body": body})
                    pos = idx + 1
                    break
            idx += 1
        else:
            break
    return results


def parse_proto_schema(proto_path: Path) -> Dict[str, Any]:
    raw = proto_path.read_text(encoding="utf-8")
    # Strip single-line comments
    cleaned = re.sub(r"//[^\n]*", "", raw)

    pkg_match = re.search(r"package\s+([A-Za-z0-9_.]+)\s*;", cleaned)
    package_name = pkg_match.group(1) if pkg_match else "default"

    services: List[Dict[str, Any]] = []
    for svc_match in SERVICE_RE.finditer(cleaned):
        svc_name = svc_match.group(1)
        svc_body = svc_match.group(2)
        methods = []
        for rpc in RPC_RE.finditer(svc_body):
            methods.append(
                {
                    "name": rpc.group(1),
                    "clientStreaming": bool(rpc.group(2)),
                    "inputType": rpc.group(3),
                    "serverStreaming": bool(rpc.group(4)),
                    "outputType": rpc.group(5),
                }
            )
        services.append({"name": svc_name, "methods": methods})

    enums: List[Dict[str, Any]] = []
    for enum_match in ENUM_RE.finditer(cleaned):
        enum_name = enum_match.group(1)
        enum_body = enum_match.group(2)
        values = [
            {"name": val.group(1), "number": int(val.group(2))}
            for val in ENUM_VAL_RE.finditer(enum_body)
        ]
        enums.append({"name": enum_name, "values": values})

    messages: List[Dict[str, Any]] = []
    for block in extract_balanced_blocks(cleaned, "message"):
        fields = []
        for f_match in FIELD_RE.finditer(block["body"]):
            modifier = (f_match.group(1) or "").strip()
            fields.append(
                {
                    "label": modifier if modifier else "singular",
                    "type": f_match.group(2),
                    "name": f_match.group(3),
                    "tag": int(f_match.group(4)),
                }
            )
        messages.append({"name": block["name"], "fields": fields})

    return {
        "file": str(proto_path.name),
        "package": package_name,
        "serviceCount": len(services),
        "messageCount": len(messages),
        "enumCount": len(enums),
        "services": services,
        "enums": enums,
        "messages": messages,
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Dump all gRPC services, messages, and enums from complete_local_grpc_definitions.proto"
    )
    parser.add_argument(
        "--proto",
        default=str(Path(__file__).parent / "complete_local_grpc_definitions.proto"),
        help="Path to the .proto file",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit structured JSON output",
    )
    args = parser.parse_args()

    proto_file = Path(args.proto).resolve()
    if not proto_file.exists():
        print(f"[ERROR] Proto file not found: {proto_file}", file=sys.stderr)
        return 1

    schema = parse_proto_schema(proto_file)
    if args.json:
        print(json.dumps(schema, indent=2))
        return 0

    print("==================================================================")
    print(f" Proto Schema Summary: {schema['file']} (package: {schema['package']})")
    print(f" Services : {schema['serviceCount']}")
    print(f" Messages : {schema['messageCount']}")
    print(f" Enums    : {schema['enumCount']}")
    print("==================================================================")

    for svc in schema["services"]:
        print(f"\n[Service] {svc['name']}")
        for m in svc["methods"]:
            c_stream = "stream " if m["clientStreaming"] else ""
            s_stream = "stream " if m["serverStreaming"] else ""
            print(f"  - rpc {m['name']}({c_stream}{m['inputType']}) -> ({s_stream}{m['outputType']})")

    for msg in schema["messages"]:
        print(f"\n[Message] {msg['name']} ({len(msg['fields'])} fields)")
        for field in msg["fields"]:
            print(f"  - #{field['tag']}: {field['label']} {field['type']} {field['name']}")

    for en in schema["enums"]:
        print(f"\n[Enum] {en['name']} ({len(en['values'])} values)")
        for val in en["values"]:
            print(f"  - {val['name']} = {val['number']}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
