#!/usr/bin/env python3
"""Lightweight lint for protobuf field-number stability rules.

Checks every `.proto` file under `taktx-shared/src/main/proto` and fails when:
  - a message reuses a field number,
  - a message reuses a field name,
  - a field uses a reserved number or name,
  - reserved declarations overlap within the same message.

The parser is intentionally simple and line-oriented so it can run in CI without
extra dependencies.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parent.parent
PROTO_ROOT = REPO_ROOT / "taktx-shared" / "src" / "main" / "proto"

MESSAGE_START_RE = re.compile(r"^\s*message\s+(?P<name>\w+)\s*\{")
ENUM_START_RE = re.compile(r"^\s*enum\s+(?P<name>\w+)\s*\{")
ONEOF_START_RE = re.compile(r"^\s*oneof\s+(?P<name>\w+)\s*\{")
RESERVED_RE = re.compile(r"^\s*reserved\s+(?P<body>.+);\s*$")
FIELD_RE = re.compile(
    r"^\s*(?:optional\s+|repeated\s+)?(?:map<[^>]+>|[A-Za-z_][\w.]*)\s+"
    r"(?P<name>[A-Za-z_]\w*)\s*=\s*(?P<number>\d+)\s*(?:\[[^]]*\])?;"
)
RANGE_RE = re.compile(r"^(?P<start>\d+)\s+to\s+(?P<end>\d+|max)$")


@dataclass
class ReservedRange:
    start: int
    end: int

    def contains(self, number: int) -> bool:
        return self.start <= number <= self.end

    def overlaps(self, other: "ReservedRange") -> bool:
        return self.start <= other.end and other.start <= self.end

    def __str__(self) -> str:
        if self.start == self.end:
            return str(self.start)
        if self.end >= sys.maxsize:
            return f"{self.start} to max"
        return f"{self.start} to {self.end}"


@dataclass
class MessageContext:
    name: str
    file_path: Path
    line_number: int
    field_numbers: dict[int, tuple[str, int]] = field(default_factory=dict)
    field_names: dict[str, int] = field(default_factory=dict)
    reserved_ranges: list[ReservedRange] = field(default_factory=list)
    reserved_names: dict[str, int] = field(default_factory=dict)

    def add_reserved_range(self, reserved_range: ReservedRange, line_number: int, errors: list[str]) -> None:
        for existing in self.reserved_ranges:
            if existing.overlaps(reserved_range):
                errors.append(
                    f"{self.file_path}:{line_number}: message {self.name} has overlapping reserved ranges "
                    f"{existing} and {reserved_range}"
                )
        for number, (field_name, field_line) in self.field_numbers.items():
            if reserved_range.contains(number):
                errors.append(
                    f"{self.file_path}:{line_number}: message {self.name} reserves field number {number} "
                    f"already used by field {field_name} at line {field_line}"
                )
        self.reserved_ranges.append(reserved_range)

    def add_reserved_name(self, name: str, line_number: int, errors: list[str]) -> None:
        if name in self.reserved_names:
            errors.append(
                f"{self.file_path}:{line_number}: message {self.name} repeats reserved field name {name!r} "
                f"(first declared at line {self.reserved_names[name]})"
            )
        if name in self.field_names:
            errors.append(
                f"{self.file_path}:{line_number}: message {self.name} reserves field name {name!r} "
                f"already used at line {self.field_names[name]}"
            )
        self.reserved_names[name] = line_number

    def add_field(self, name: str, number: int, line_number: int, errors: list[str]) -> None:
        if number in self.field_numbers:
            existing_name, existing_line = self.field_numbers[number]
            errors.append(
                f"{self.file_path}:{line_number}: message {self.name} reuses field number {number} for {name!r}; "
                f"already used by {existing_name!r} at line {existing_line}"
            )
        if name in self.field_names:
            errors.append(
                f"{self.file_path}:{line_number}: message {self.name} reuses field name {name!r}; "
                f"already declared at line {self.field_names[name]}"
            )
        if any(reserved.contains(number) for reserved in self.reserved_ranges):
            errors.append(
                f"{self.file_path}:{line_number}: message {self.name} uses reserved field number {number} for {name!r}"
            )
        if name in self.reserved_names:
            errors.append(
                f"{self.file_path}:{line_number}: message {self.name} uses reserved field name {name!r}"
            )
        self.field_numbers[number] = (name, line_number)
        self.field_names[name] = line_number


@dataclass
class StackEntry:
    kind: str
    payload: object


def strip_comments(line: str) -> str:
    return line.split("//", 1)[0]


def split_reserved_items(body: str) -> Iterable[str]:
    items: list[str] = []
    current: list[str] = []
    in_quotes = False
    for char in body:
        if char == '"':
            in_quotes = not in_quotes
            current.append(char)
            continue
        if char == "," and not in_quotes:
            token = "".join(current).strip()
            if token:
                items.append(token)
            current = []
            continue
        current.append(char)
    token = "".join(current).strip()
    if token:
        items.append(token)
    return items


def nearest_message(stack: list[StackEntry]) -> MessageContext | None:
    for entry in reversed(stack):
        if entry.kind == "message":
            return entry.payload  # type: ignore[return-value]
    return None


def parse_reserved_item(item: str) -> tuple[str, str | ReservedRange]:
    if item.startswith('"') and item.endswith('"') and len(item) >= 2:
        return ("name", item[1:-1])
    if item.isdigit():
        number = int(item)
        return ("range", ReservedRange(number, number))
    match = RANGE_RE.match(item)
    if match:
        start = int(match.group("start"))
        end_raw = match.group("end")
        end = sys.maxsize if end_raw == "max" else int(end_raw)
        return ("range", ReservedRange(start, end))
    raise ValueError(f"unsupported reserved item: {item}")


def lint_proto_file(file_path: Path) -> tuple[int, list[str]]:
    errors: list[str] = []
    stack: list[StackEntry] = []
    message_count = 0

    for line_number, raw_line in enumerate(file_path.read_text(encoding="utf-8").splitlines(), start=1):
        line = strip_comments(raw_line).strip()
        if not line:
            continue

        message_match = MESSAGE_START_RE.match(line)
        if message_match:
            stack.append(
                StackEntry("message", MessageContext(message_match.group("name"), file_path, line_number))
            )
            message_count += 1
            continue

        enum_match = ENUM_START_RE.match(line)
        if enum_match:
            stack.append(StackEntry("enum", enum_match.group("name")))
            continue

        oneof_match = ONEOF_START_RE.match(line)
        if oneof_match:
            stack.append(StackEntry("oneof", oneof_match.group("name")))
            continue

        if line == "}":
            if stack:
                stack.pop()
            continue

        message = nearest_message(stack)
        if message is None:
            continue

        reserved_match = RESERVED_RE.match(line)
        if reserved_match:
            for item in split_reserved_items(reserved_match.group("body")):
                try:
                    item_type, parsed = parse_reserved_item(item)
                except ValueError as exc:
                    errors.append(f"{file_path}:{line_number}: message {message.name} {exc}")
                    continue
                if item_type == "name":
                    message.add_reserved_name(parsed, line_number, errors)  # type: ignore[arg-type]
                else:
                    message.add_reserved_range(parsed, line_number, errors)  # type: ignore[arg-type]
            continue

        if stack and stack[-1].kind == "enum":
            continue

        field_match = FIELD_RE.match(line)
        if field_match:
            message.add_field(
                field_match.group("name"), int(field_match.group("number")), line_number, errors
            )

    return message_count, errors


def main() -> int:
    proto_files = sorted(PROTO_ROOT.glob("**/*.proto"))
    if not proto_files:
        print(f"No .proto files found under {PROTO_ROOT}", file=sys.stderr)
        return 1

    all_errors: list[str] = []
    total_messages = 0
    for proto_file in proto_files:
        message_count, errors = lint_proto_file(proto_file)
        total_messages += message_count
        all_errors.extend(errors)

    if all_errors:
        print("Proto field-number lint failed:")
        for error in all_errors:
            print(f"  - {error}")
        return 1

    print(
        f"Proto field-number lint passed for {len(proto_files)} files and {total_messages} messages under {PROTO_ROOT}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

