#!/usr/bin/env python3
import os

results = {'bsl': [], 'apache': [], 'none': []}

for module in ['taktx-client', 'taktx-shared', 'taktx-client-quarkus', 'taktx-client-spring']:
    src = f'/Users/erichendriks/IdeaProjects/TaktX-engine2/{module}/src'
    for root, dirs, files in os.walk(src):
        for f in files:
            if not f.endswith('.java'):
                continue
            path = os.path.join(root, f)
            content = open(path).read(400)
            if 'Business Source License' in content:
                results['bsl'].append(path)
            elif 'Licensed under the Apache' in content:
                results['apache'].append(path)
            else:
                results['none'].append(path)

for k, v in results.items():
    print(f'{k}: {len(v)}')

if results['none']:
    print('\nFiles without any license header:')
    for p in results['none'][:5]:
        print(f'  {p}')
        print('  ' + open(p).read(100))

