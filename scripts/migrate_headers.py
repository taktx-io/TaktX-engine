#!/usr/bin/env python3
"""
Migrates all Java source file license headers in the four non-engine modules
from TaktX Business Source License v1.0 (and any other old headers) to Apache 2.0.
"""
import os
import re

APACHE_HEADER = """\
/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
"""

MODULES = [
    'taktx-client',
    'taktx-shared',
    'taktx-client-quarkus',
    'taktx-client-spring',
]

BASE = '/Users/erichendriks/IdeaProjects/TaktX-engine2'

# Matches any /* ... */ block at the very start of the file (before any package statement)
LEADING_BLOCK_COMMENT = re.compile(r'^\s*/\*.*?\*/\s*', re.DOTALL)

updated = 0
skipped = 0
added = 0

for module in MODULES:
    src = os.path.join(BASE, module, 'src')
    for root, dirs, files in os.walk(src):
        for f in files:
            if not f.endswith('.java'):
                continue
            path = os.path.join(root, f)
            original = open(path, encoding='utf-8').read()

            # Case 1: file starts with a block comment — replace it
            match = LEADING_BLOCK_COMMENT.match(original)
            if match:
                existing_header = match.group(0)
                # Skip if already Apache 2.0
                if 'Licensed under the Apache License' in existing_header:
                    skipped += 1
                    continue
                new_content = APACHE_HEADER + original[match.end():]
                open(path, 'w', encoding='utf-8').write(new_content)
                updated += 1
            else:
                # Case 2: no block comment at start — prepend Apache header
                new_content = APACHE_HEADER + original.lstrip('\n')
                open(path, 'w', encoding='utf-8').write(new_content)
                added += 1

print(f"Updated (replaced old header): {updated}")
print(f"Added   (no header existed):   {added}")
print(f"Skipped (already Apache 2.0):  {skipped}")
print(f"Total processed: {updated + added + skipped}")

