"""Offline regression checks for preparation safety and selection behaviour."""
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import app
import batches
import openrouter
import phrases
import sample
import youtube


class PreparationTests(unittest.TestCase):
    def test_key_precedence_and_no_shell_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            config = home / '.config/openrouter'
            config.mkdir(parents=True)
            (config / 'env').write_text('export OPENROUTER_API_KEY="configured-key" # comment\n')
            (config / 'api-key').write_text('older-key')
            with patch.object(Path, 'home', return_value=home), patch.dict('os.environ', {}, clear=True):
                self.assertEqual(openrouter.api_key(), 'configured-key')
                with patch.dict('os.environ', {'OPENROUTER_API_KEY': 'environment-key'}):
                    self.assertEqual(openrouter.api_key(), 'environment-key')
                marker = home / 'must-not-exist'
                (config / 'env').write_text(f'OPENROUTER_API_KEY="$(touch {marker})"\n')
                self.assertEqual(openrouter.api_key(), f'$(touch {marker})')
                self.assertFalse(marker.exists())

    def test_sparse_sample_context_does_not_send_intervening_document(self):
        context = batches.context_indices([0, 999], 1000)
        self.assertEqual(len(context), 16)
        self.assertIn(1, context)
        self.assertIn(998, context)
        self.assertFalse(any(9 <= n <= 990 for n in context))
        self.assertFalse({0, 999} & set(context))

    def test_sample_covers_late_cues_and_unusual_material(self):
        cues = [{'line': {'id': str(i), 'source': 'ordinary source words'}} for i in range(100)]
        cues[45]['line']['source'] = 'long source ' * 20
        cues[64]['listeningCheck'] = 'Which word?'
        cues[38]['line']['continuesPrevious'] = True
        chosen = sample.select(cues, 10)
        self.assertEqual(len(set(chosen)), 10)
        self.assertTrue({0, 99, 45, 64, 38} <= set(chosen))
        self.assertEqual(sample.select([], 10), [])
        self.assertEqual(sample.select(cues, 2), [0, 99])
        with self.assertRaises(ValueError):
            sample.select(cues, 0)

    def test_length_alone_does_not_retry_but_broken_auxiliary_does(self):
        self.assertEqual(phrases.faults({'source': ['Je ne sais pas quoi faire maintenant.']}, 'fr'), [])
        self.assertTrue(phrases.faults({'source': ['Je suis', 'très content.']}, 'fr'))
        self.assertTrue(phrases.faults({'source': ['Mais', 'je suis content.']}, 'fr'))

    def test_app_accepts_empty_success_response(self):
        class Response(io.BytesIO):
            pass
        with patch.object(app, 'URL', 'http://localhost:8877'), patch('urllib.request.build_opener') as opener:
            opener.return_value.open.return_value = Response(b'')
            self.assertIsNone(app.call('POST', '/complete', {}))

    def test_youtube_rejects_other_hosts_and_removes_playlist(self):
        self.assertEqual(youtube.video_url('https://youtu.be/abcdefghijk?t=1&list=abc'),
                         'https://www.youtube.com/watch?v=abcdefghijk')
        for url in ['https://example.com/watch?v=abcdefghijk',
                    'https://youtube.com.evil.test/watch?v=abcdefghijk',
                    'https://youtube.com/playlist?list=abc']:
            with self.assertRaises(ValueError):
                youtube.video_url(url)

    def test_download_rejects_archive_inside_live_media(self):
        with tempfile.TemporaryDirectory() as directory:
            media = Path(directory) / 'media'
            argv = ['youtube.py', 'https://youtu.be/abcdefghijk', '--media-dir', str(media),
                    '--archive-dir', str(media / 'archive')]
            with patch.object(sys, 'argv', argv), patch('sys.stderr', new=io.StringIO()):
                with self.assertRaises(SystemExit):
                    youtube.main()
            self.assertFalse(media.exists())

    def test_download_keeps_originals_and_requires_language(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive, media = root / 'archive', root / 'media'
            calls = []
            def run(command, **kwargs):
                calls.append(command)
                if command[0] == 'yt-dlp':
                    (archive / 'video.mp4').write_bytes(b'original')
                    (archive / 'video.info.json').write_text(json.dumps({'title': 'Test', 'duration': 10}))
                    (archive / 'video.jpg').write_bytes(b'poster')
                else:
                    Path(command[-1]).write_bytes(b'live')
            argv = ['youtube.py', 'https://youtu.be/abcdefghijk', '--archive-dir', str(archive), '--media-dir', str(media)]
            with patch.object(sys, 'argv', argv), patch('shutil.which', return_value='/tool'), patch('subprocess.run', side_effect=run), patch('sys.stdout', new=io.StringIO()):
                youtube.main()
            self.assertEqual((archive / 'video.mp4').read_bytes(), b'original')
            self.assertEqual((media / 'poster.jpg').read_bytes(), b'poster')
            self.assertFalse((media / 'video.info.json').exists())
            formats = calls[0][calls[0].index('-f') + 1]
            self.assertTrue(all('language^=fr' in option for option in formats.split('/')))
            self.assertIn('--no-playlist', calls[0])
            with patch.object(sys, 'argv', argv), patch('shutil.which', return_value='/tool'):
                with self.assertRaises(SystemExit):
                    youtube.main()


if __name__ == '__main__':
    unittest.main()
