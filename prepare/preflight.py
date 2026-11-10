#!/usr/bin/env python3
"""Check preparation tools, the running app and OpenRouter authentication; generate no content."""
import argparse
import shutil
import urllib.error
import urllib.request

import app
from openrouter import API, api_key


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--youtube', action='store_true', help='also require yt-dlp')
    args = parser.parse_args()
    failed = False
    for name in ['ffmpeg', 'ffprobe'] + (['yt-dlp'] if args.youtube else []):
        found = shutil.which(name)
        print(f'{name}: {found or "MISSING"}')
        failed |= not bool(found)
    try:
        app.call('GET', '/api/repositories')
        print('App: reachable')
    except SystemExit as error:
        print(f'App: {error}')
        failed = True
    try:
        request = urllib.request.Request(API + 'key', headers={'Authorization': 'Bearer ' + api_key()})
        with urllib.request.urlopen(request, timeout=20):
            print('OpenRouter: authenticated (no generation request)')
    except urllib.error.HTTPError as error:
        print(f'OpenRouter: HTTP {error.code}; check the configured key and account')
        failed = True
    except (SystemExit, OSError, ValueError):
        print('OpenRouter: key unavailable, invalid configuration, or network failure')
        failed = True
    raise SystemExit(1 if failed else 0)


if __name__ == '__main__':
    main()
