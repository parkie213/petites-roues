#!/usr/bin/env python3
"""Suggest cue IDs across a document for the three-stage sample; never modify the document.

The selection covers the timeline/reading order, a short line, a long line, a continuation
and an open check when present. These are structural hints, not automatic idiom detection:
review the printed lines and swap in a useful idiom or slang example before annotating.
"""
import argparse
from pathlib import Path

from batches import cues_of
from openrouter import load


def select(cues, count):
    """Return unique indices in reading order, with varied length and coverage across the title."""
    if count < 1:
        raise ValueError('Sample count must be positive')
    eligible = [i for i, c in enumerate(cues) if c['line']['source'].strip()]
    count = min(count, len(eligible))
    if not count:
        return []
    chosen = []

    def add(i):
        if i not in chosen and len(chosen) < count:
            chosen.append(i)

    # Reserve most of the sample for coverage rather than concentrating on unusual openings.
    coverage = max(2, count - 4) if count >= 2 else 1
    for n in range(coverage):
        add(eligible[round(n * (len(eligible) - 1) / max(1, coverage - 1))])
    add(min(eligible, key=lambda i: len(cues[i]['line']['source'])))
    add(max(eligible, key=lambda i: len(cues[i]['line']['source'])))
    for predicate in [lambda c: c['line'].get('continuesPrevious'),
                      lambda c: c.get('listeningCheck') or c['line'].get('textCheck')]:
        match = next((i for i in eligible if predicate(cues[i])), None)
        if match is not None:
            add(match)
    # Fill unused feature slots by choosing the cue furthest from those already selected.
    while len(chosen) < count:
        add(max((i for i in eligible if i not in chosen),
                key=lambda i: min(abs(i - j) for j in chosen)))
    return sorted(chosen)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--document', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True, help='new cue-ID file for --cues')
    parser.add_argument('--count', type=int, default=10)
    args = parser.parse_args()
    if args.count < 1:
        parser.error('--count must be positive')
    cues = cues_of(load(args.document))
    indices = select(cues, args.count)
    if not indices:
        raise SystemExit('No source cues: transcribe or read the text first')
    with args.out.open('x', encoding='utf-8') as output:
        output.write(''.join(cues[i]['line']['id'] + '\n' for i in indices))
    for i in indices:
        c = cues[i]
        print(f'{c["line"]["id"]} [{c.get("start", c.get("pageId", ""))}]: {c["line"]["source"]}')
    print('Review the selection for an idiom/slang example; use this file with --cues at each stage.')


if __name__ == '__main__':
    main()
