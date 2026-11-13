These tiny fixtures contain synthetic audio, not film or show content. Generate them
with FFmpeg from the repository root; FFmpeg is not needed to run the tests:

```sh
ffmpeg -f lavfi -i anullsrc=r=48000:cl=stereo -t 0.05 -c:a aac java/desktop-test-fixtures/src/main/resources/media/aac.mp4
ffmpeg -f lavfi -i anullsrc=r=48000:cl=stereo -t 0.05 -c:a aac java/desktop-test-fixtures/src/main/resources/media/aac.mkv
ffmpeg -f lavfi -i anullsrc=r=48000:cl=stereo -t 0.05 -c:a ac3 java/desktop-test-fixtures/src/main/resources/media/ac3.mp4
ffmpeg -f lavfi -i anullsrc=r=48000:cl=stereo -t 0.05 -map 0:a -map 0:a -c:a:0 aac -c:a:1 ac3 java/desktop-test-fixtures/src/main/resources/media/aac-and-ac3.mkv
```

The voice browser fixtures are one-second tones. They test media playback, rather than
impersonating a speech engine. A recording previews real JNI speech and validates its WAV
response; replay uses the fixed tone because Piper's waveform varies between calls.

```sh
ffmpeg -f lavfi -i sine=frequency=440:duration=1 -c:a libmp3lame java/desktop-test-fixtures/src/main/resources/media/voice-sample.mp3
ffmpeg -f lavfi -i sine=frequency=440:duration=1 -ar 22050 -ac 1 -c:a pcm_s16le java/desktop-test-fixtures/src/main/resources/media/voice-preview.wav
```
