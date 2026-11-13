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
import new_title
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

    def test_new_title_saves_the_app_skeleton_and_refuses_an_existing_document(self):
        skeleton = {'id': 'naruto-t02', 'title': 'Naruto T02', 'sourceLanguage': 'fr',
                    'comic': {'readingDirection': 'rtl', 'pages': []}}
        with tempfile.TemporaryDirectory() as directory:
            document = Path(directory) / 'comics/naruto/document.json'
            argv = ['new_title.py', '--document', str(document), '--media', 'Naruto-T02.cbz',
                    '--title', 'Naruto T02', '--volume', '2', '--ltr']
            with patch.object(sys, 'argv', argv), patch('sys.stdout', new=io.StringIO()) as out, \
                    patch.object(new_title, 'skeleton', return_value=skeleton) as ask:
                new_title.main()
            request = ask.call_args.args[0]
            self.assertEqual(request['media'], str(Path('Naruto-T02.cbz').resolve()))
            self.assertEqual((request['title'], request['id'], request['volume']), ('Naruto T02', None, 2))
            self.assertEqual((request['sourceLanguage'], request['leftToRight']), ('fr', True))
            self.assertEqual(json.loads(document.read_text()), skeleton)
            self.assertEqual(out.getvalue(), f'{document}: naruto-t02, referencing Naruto-T02.cbz\n')
            with patch.object(sys, 'argv', argv), patch.object(new_title, 'skeleton') as ask:
                with self.assertRaisesRegex(SystemExit, 'already exists'):
                    new_title.main()
            ask.assert_not_called()

    def test_skeleton_refusal_shows_the_apps_message_and_writes_nothing(self):
        refusal = SystemExit('POST /api/titles/skeleton: HTTP 409 {"error":"A title with document ID x already exists: X"}')
        with patch.object(app, 'call', side_effect=refusal):
            with self.assertRaises(SystemExit) as raised:
                app.skeleton({'media': '/m.mp4', 'title': 'X'})
        self.assertEqual(str(raised.exception), 'A title with document ID x already exists: X')
        with patch.object(app, 'call', side_effect=SystemExit('The app is not reachable at u (r); start it first')):
            with self.assertRaisesRegex(SystemExit, 'not reachable'):
                app.skeleton({})

    def test_open_batch_stops_a_rewrite_before_any_work(self):
        pending = [{'titleId': 'a', 'title': 'A', 'pending': 2, 'openBatch': 7},
                   {'titleId': 'b', 'title': 'B', 'pending': 1, 'openBatch': None}]
        with patch.object(app, 'call', return_value=pending):
            with self.assertRaisesRegex(SystemExit, 'batch 7 is open'):
                app.require_no_open_batch('a')
            app.require_no_open_batch('b')
            app.require_no_open_batch('unlisted')

    def test_import_reports_result_and_explains_a_conflict(self):
        with patch.object(app, 'call', return_value={'result': 'imported'}) as call:
            self.assertEqual(app.import_title('a'), 'imported')
            call.assert_called_once_with('POST', '/api/titles/a/import')
        refusal = SystemExit('POST /api/titles/a/import: HTTP 409 open batch')
        with patch.object(app, 'call', side_effect=refusal):
            with self.assertRaisesRegex(SystemExit, 'written but not imported.*HTTP 409 open batch'):
                app.import_title('a')

    def test_write_imports_before_review_requests_can_be_queued(self):
        with tempfile.TemporaryDirectory() as directory:
            path, run = Path(directory) / 'document.json', Path(directory)
            path.write_text(json.dumps({'video': {'cues': [{'line': {'id': 'c1', 'source': 'Salut'}}]}}))
            order = []
            with patch.object(app, 'call', side_effect=lambda m, p, b=None: order.append(p) or (
                    [] if p == '/api/review/pending' else {'problems': [], 'result': 'imported'})):
                batches.write(path, run, 't', {'c1': 'Hello'}, lambda line: True,
                              lambda line, answer: line.update(translation=answer))
            self.assertEqual(order, ['/api/review/pending', '/api/titles/t/validate', '/api/titles/t/import'])
            self.assertEqual(json.loads(path.read_text())['video']['cues'][0]['line']['translation'], 'Hello')
            with patch.object(app, 'call', return_value=[{'titleId': 't', 'openBatch': 3}]):
                with self.assertRaises(SystemExit):
                    batches.write(path, run, 't', {}, lambda line: True, lambda line, answer: None)

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
