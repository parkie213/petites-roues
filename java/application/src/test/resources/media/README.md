These tiny fixtures contain synthetic silence, not film or show content. Generate them
with FFmpeg from the repository root; FFmpeg is not needed to run the tests:

```sh
ffmpeg -f lavfi -i anullsrc=r=48000:cl=stereo -t 0.05 -c:a aac java/application/src/test/resources/media/aac.mp4
ffmpeg -f lavfi -i anullsrc=r=48000:cl=stereo -t 0.05 -c:a aac java/application/src/test/resources/media/aac.mkv
ffmpeg -f lavfi -i anullsrc=r=48000:cl=stereo -t 0.05 -c:a ac3 java/application/src/test/resources/media/ac3.mp4
ffmpeg -f lavfi -i anullsrc=r=48000:cl=stereo -t 0.05 -map 0:a -map 0:a -c:a:0 aac -c:a:1 ac3 java/application/src/test/resources/media/aac-and-ac3.mkv
```
