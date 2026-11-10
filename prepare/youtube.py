#!/usr/bin/env python3
"""Download one YouTube video, supplied subtitles, metadata and poster; do not annotate or index.

Keep the untouched download under a fresh archive directory and put a browser-playable MP4
and one poster in a fresh media directory. Audio selection is restricted to the declared
source language. Provider language metadata is not listening verification.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import parse_qs, urlparse

from languages import THREE_LETTER, primary


def video_url(value):
    """Accept one YouTube video URL, dropping playlist and tracking parameters."""
    parsed = urlparse(value)
    host = (parsed.hostname or '').lower()
    if parsed.scheme != 'https' or parsed.username or parsed.password or parsed.port:
        raise ValueError('Use an https YouTube video URL')
    if host == 'youtu.be':
        ident = parsed.path.strip('/')
    elif host in {'youtube.com', 'www.youtube.com', 'm.youtube.com'}:
        ident = (parse_qs(parsed.query).get('v', [''])[0] if parsed.path == '/watch'
                 else parsed.path.removeprefix('/shorts/') if parsed.path.startswith('/shorts/') else '')
    else:
        ident = ''
    if not re.fullmatch(r'[A-Za-z0-9_-]{11}', ident):
        raise ValueError('Expected one YouTube video URL')
    return 'https://www.youtube.com/watch?v=' + ident


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('url')
    parser.add_argument('--media-dir', type=Path, required=True)
    parser.add_argument('--archive-dir', type=Path, required=True)
    parser.add_argument('--source', default='fr', help='source language, e.g. fr')
    parser.add_argument('--translation', default='en', help='also preserve supplied subtitles in this language')
    args = parser.parse_args()
    try:
        url = video_url(args.url)
    except ValueError as error:
        parser.error(str(error))
    if not all(re.fullmatch(r'[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*', x)
               for x in [args.source, args.translation]):
        parser.error('Use language tags such as fr, en or fr-CA')
    media_path, archive_path = args.media_dir.resolve(), args.archive_dir.resolve()
    if media_path.is_relative_to(archive_path) or archive_path.is_relative_to(media_path):
        parser.error('Media and archive directories must be separate')
    for name in ['yt-dlp', 'ffmpeg', 'ffprobe']:
        if not shutil.which(name):
            raise SystemExit(f'{name} is missing; run preflight.py --youtube first')
    for directory in [args.media_dir, args.archive_dir]:
        if directory.exists() and (not directory.is_dir() or any(directory.iterdir())):
            raise SystemExit(f'Use a fresh empty directory: {directory}')
    args.archive_dir.mkdir(parents=True, exist_ok=True)
    args.media_dir.mkdir(parents=True, exist_ok=True)
    lang = args.source.lower()
    formats = (f'bv[height<=720][ext=mp4]+ba[ext=m4a][language^={lang}]'
               f'/b[ext=mp4][language^={lang}]')
    subprocess.run(['yt-dlp', '--ignore-config', '--no-playlist', '-f', formats,
                    '--merge-output-format', 'mp4', '--write-info-json', '--write-subs',
                    '--sub-langs', ','.join(dict.fromkeys([args.source, args.translation])),
                    '--sub-format', 'vtt', '--write-thumbnail', '-o',
                    str(args.archive_dir / 'video.%(ext)s'), url], check=True)
    info = json.loads((args.archive_dir / 'video.info.json').read_text(encoding='utf-8'))
    original = args.archive_dir / 'video.mp4'
    if not original.is_file():
        raise SystemExit('No MP4 produced; original files remain in the archive')
    # The format filter chose the source-language track. Repair the container tag only;
    # never overwrite the preserved download or imply its speech has been verified.
    code = next((k for k, v in THREE_LETTER.items() if v == primary(args.source)), None)
    if not code:
        raise SystemExit('No ISO audio-tag mapping for this language; keep the archive and tag media manually')
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-i', str(original), '-map', '0',
                    '-c', 'copy', '-metadata:s:a:0', 'language=' + code,
                    str(args.media_dir / 'video.mp4')], check=True)
    poster = next((args.archive_dir / ('video.' + ext) for ext in ['jpg', 'jpeg', 'png', 'webp']
                   if (args.archive_dir / ('video.' + ext)).is_file()), None)
    if poster:
        shutil.copy2(poster, args.media_dir / ('poster' + poster.suffix))
    print(f'{info["title"]} — {info.get("duration", "unknown")} seconds — {info.get("channel", "unknown channel")}')
    print(f'Media: {args.media_dir / "video.mp4"}; poster: {"saved" if poster else "unavailable"}')
    print('Original video, supplied subtitles and metadata preserved in ' + str(args.archive_dir))
    print('Audio selected by provider language; check the speech before transcription.')
    print('Next: new_title.py, set shelf/sourceUrl, then explicit library Refresh.')


if __name__ == '__main__':
    main()
